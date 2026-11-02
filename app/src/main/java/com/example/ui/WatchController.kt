package com.example.ui

import kotlin.math.abs
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.webkit.WebChromeClient
import android.webkit.WebView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Reproductor de YouTube compartido. Vive en el ViewModel para que el video siga sonando
 * (y se vea en un mini reproductor) mientras navegas por otras pantallas.
 * Regla de sincronía: el documento de la pareja manda; este controlador converge SIEMPRE
 * el estado local (play/pausa/posición) hacia ese estado.
 */
class WatchController(
  private val app: Application,
  private val scope: CoroutineScope,
  private val docFlow: Flow<Map<String, Any>?>,
  private val publishDoc: (Map<String, Any?>) -> Unit,
  private val myUid: () -> String?
) {
  private val referer = "https://sinijoss.app/"
  // Consulta el reproductor mediante la API oficial de YouTube (el video vive dentro de un iframe,
  // por lo que buscar un elemento <video> en el documento padre no funciona de forma fiable).
  private val pollJs = "(function(){if(window.sinijossError){return 'ERR';}var p=window.sinijossPlayer;if(!p||!window.sinijossPlayerReady){return '';}try{var s=p.getPlayerState();return p.getCurrentTime()+'|'+(p.getDuration()||0)+'|'+(s===1?1:0)+'|'+(s===0?1:0);}catch(e){return '';}})()"

  private var web: WebView? = null
  private var fullscreenView: View? = null
  private var fullscreenContainer: FrameLayout? = null
  private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null
  private var previousSystemUiVisibility: Int = 0
  private var doc: Map<String, Any>? = null
  private var appliedSeq = -1L
  private var firstApply = true
  private var useMobile = false
  // 0 = IFrame API con origen youtube.com, 1 = youtube-nocookie, 2 = página móvil. Si uno falla se pasa al siguiente solo.
  private var embedMode = 0
  private var browsingYoutube = false
  private val _browsing = MutableStateFlow(false)
  val browsing: StateFlow<Boolean> = _browsing.asStateFlow()
  private var advancing = false
  private var lastHeartbeat = 0L
  private var lastDriftFix = 0L
  private var lastPolledPlaying: Boolean? = null
  private val _queue = MutableStateFlow<List<String>>(emptyList())
  val queue: StateFlow<List<String>> = _queue.asStateFlow()

  private val _vid = MutableStateFlow("")
  val vid: StateFlow<String> = _vid.asStateFlow()
  private val _time = MutableStateFlow(0.0)
  val time: StateFlow<Double> = _time.asStateFlow()
  private val _duration = MutableStateFlow(0.0)
  val duration: StateFlow<Double> = _duration.asStateFlow()
  private val _ready = MutableStateFlow(false)
  val ready: StateFlow<Boolean> = _ready.asStateFlow()
  private val _error = MutableStateFlow(0)
  val error: StateFlow<Int> = _error.asStateFlow()
  private val _playing = MutableStateFlow(false)
  val playing: StateFlow<Boolean> = _playing.asStateFlow()
  private val _controller = MutableStateFlow("")
  val controller: StateFlow<String> = _controller.asStateFlow()

  fun start() {
    scope.launch { docFlow.collect { d -> onDoc(d) } }
    scope.launch {
      while (true) {
        delay(1000)
        tick()
      }
    }
  }

  @SuppressLint("SetJavaScriptEnabled")
  fun webView(ctx: android.content.Context): WebView {
    val existing = web
    if (existing != null) {
      (existing.parent as? ViewGroup)?.removeView(existing)
      (existing.context as? android.content.MutableContextWrapper)?.baseContext = ctx
      return existing
    }
    val wv = WebView(android.content.MutableContextWrapper(ctx))
    wv.settings.javaScriptEnabled = true
    wv.settings.domStorageEnabled = true
    wv.settings.mediaPlaybackRequiresUserGesture = false
    // Facilita completar verificaciones legítimas de YouTube dentro del reproductor:
    // zoom por pellizco, página adaptada al ancho y contenido desplazable.
    wv.settings.setSupportZoom(true)
    wv.settings.builtInZoomControls = true
    wv.settings.displayZoomControls = false
    wv.settings.useWideViewPort = true
    wv.settings.loadWithOverviewMode = true
    wv.settings.setSupportMultipleWindows(false)
    wv.settings.loadsImagesAutomatically = true
    wv.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
    wv.setLayerType(View.LAYER_TYPE_HARDWARE, null)
    // User-Agent de Chrome móvil (sin la marca "; wv" del WebView): YouTube/Google lo trata como un navegador
    // normal y casi nunca pide el captcha. Cookies habilitadas para recordar la verificación si aparece.
    wv.settings.userAgentString = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
    runCatching {
      val cm = android.webkit.CookieManager.getInstance()
      cm.setAcceptCookie(true)
      cm.setAcceptThirdPartyCookies(wv, true)
    }
    wv.isVerticalScrollBarEnabled = true
    wv.isHorizontalScrollBarEnabled = true
    wv.isFocusableInTouchMode = true
    wv.webChromeClient = object : WebChromeClient() {
      override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
        if (view == null) { callback?.onCustomViewHidden(); return }
        if (fullscreenView != null) { callback?.onCustomViewHidden(); return }
        val activity = (wv.context as? android.content.MutableContextWrapper)?.baseContext as? Activity
          ?: (ctx as? Activity)
        val decor = activity?.window?.decorView as? ViewGroup ?: run { callback?.onCustomViewHidden(); return }
        fullscreenView = view
        fullscreenCallback = callback
        fullscreenContainer = FrameLayout(decor.context).apply {
          setBackgroundColor(Color.BLACK)
          addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        previousSystemUiVisibility = decor.systemUiVisibility
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        decor.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
          View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
          View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
        decor.addView(fullscreenContainer, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
      }

      override fun onHideCustomView() { hideFullscreenView() }
    }
    wv.webViewClient = object : android.webkit.WebViewClient() {
      override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
        val u = request?.url?.toString() ?: return true
        // Se queda dentro del WebView (vista móvil); bloquea intents hacia la app de YouTube.
        return !(u.startsWith("http://") || u.startsWith("https://"))
      }
    }
    wv.setBackgroundColor(android.graphics.Color.BLACK)
    web = wv
    return wv
  }

  private fun onDoc(d: Map<String, Any>?) {
    doc = d
    val v = (d?.get("vid") as? String) ?: ""
    if (v != _vid.value) {
      _vid.value = v
      _ready.value = false
      _error.value = 0
      _time.value = 0.0
      appliedSeq = -1L
      firstApply = true
      advancing = false
      lastPolledPlaying = null
      embedMode = 0
      if (v.isEmpty()) { web?.tag = null; web?.loadUrl("about:blank") }
    }
    _queue.value = (d?.get("queue") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
    _controller.value = (d?.get("by") as? String) ?: ""
    _playing.value = (d?.get("playing") as? Boolean) ?: false
  }

  private fun embedHtml(id: String, nocookie: Boolean): String {
    val origin = if (nocookie) "https://www.youtube-nocookie.com" else "https://www.youtube.com"
    val hostLine = if (nocookie) "host:'https://www.youtube-nocookie.com'," else ""
    return """
    <!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1">
    <meta name="referrer" content="strict-origin-when-cross-origin">
    <style>html,body{margin:0;padding:0;width:100%;height:100%;background:#000;overflow:hidden}body{position:relative}#player{position:absolute;inset:0;width:100%;height:100%;background:#000}#player iframe{position:absolute;inset:0;width:100%!important;height:100%!important;border:0}</style></head>
    <body><div id="player"></div><script>
    var sinijossPlayer=null, sinijossPlayerReady=false, sinijossError=false;
    var tag=document.createElement('script');tag.src='https://www.youtube.com/iframe_api';
    document.head.appendChild(tag);
    function onYouTubeIframeAPIReady(){sinijossPlayer=new YT.Player('player',{
      $hostLine
      width:'100%',height:'100%',videoId:'$id',
      playerVars:{autoplay:1,controls:1,playsinline:1,rel:0,fs:1,enablejsapi:1,iv_load_policy:3,origin:'$origin'},
      events:{onReady:function(){sinijossPlayerReady=true;},onError:function(){sinijossError=true;}}
    });}
    </script></body></html>
  """.trimIndent()
  }

  private fun js(script: String) { web?.evaluateJavascript(script, null) }
  private fun seekJs(sec: Double) = js("(function(){var p=window.sinijossPlayer;if(p&&window.sinijossPlayerReady){p.seekTo($sec,true);}})()")
  private fun playJs() = js("(function(){var p=window.sinijossPlayer;if(p&&window.sinijossPlayerReady){p.playVideo();}})()")
  private fun pauseJs() = js("(function(){var p=window.sinijossPlayer;if(p&&window.sinijossPlayerReady){p.pauseVideo();}})()")

  private fun tick() {
    val wv = web ?: return
    // En modo exploración dejamos que YouTube gestione navegación, búsqueda y recomendaciones.
    // No se aplica sincronización de reproducción mientras el usuario navega por la página.
    if (browsingYoutube) return
    val d = doc ?: return
    val id = _vid.value
    if (id.isEmpty()) return
    if (wv.tag != id) {
      wv.tag = id
      if (useMobile || embedMode >= 2) {
        wv.loadUrl("https://m.youtube.com/watch?v=$id&app=m&autoplay=1")
      } else {
        // HTML contenedor + API oficial con origen real de YouTube (evita el error 152/153 del origen falso).
        val base = if (embedMode == 1) "https://www.youtube-nocookie.com/" else "https://www.youtube.com/"
        wv.loadDataWithBaseURL(base, embedHtml(id, embedMode == 1), "text/html", "UTF-8", null)
      }
      return
    }
    wv.evaluateJavascript(pollJs) { raw ->
      val r = raw?.trim('"') ?: ""
      if (r == "ERR") {
        // Si este método de reproducción falla, se prueba el siguiente automáticamente.
        if (embedMode < 2 && !useMobile) { embedMode++; wv.tag = null; _error.value = 0 } else _error.value = 152
      } else if (r.contains("|")) {
        val p = r.split("|")
        val cur = p.getOrNull(0)?.toDoubleOrNull() ?: 0.0
        val dur = p.getOrNull(1)?.toDoubleOrNull() ?: 0.0
        val localPlaying = p.getOrNull(2) == "1"
        val ended = p.getOrNull(3) == "1"
        if (ended && !advancing && _queue.value.isNotEmpty() && ((doc?.get("by") as? String) ?: "") == (myUid() ?: "")) { advancing = true; next() }
        _time.value = cur
        if (dur > 0 && !dur.isNaN()) {
          _duration.value = dur
          _ready.value = true
          _error.value = 0
          // Sincronizar solo ante acciones explícitas (seq nuevo). No hacer seek/play/pause
          // en cada sondeo: eso provoca cortes de audio y tirones en YouTube.
          val seq = (d["seq"] as? Number)?.toLong() ?: 0L
          val controllerUid = (d["by"] as? String) ?: ""
          val previousLocalPlaying = lastPolledPlaying
          val mine = controllerUid == (myUid() ?: "")
          val docPlaying = (d["playing"] as? Boolean) ?: false
          if (seq != appliedSeq) {
            converge(d, cur, localPlaying)
          } else if (mine && previousLocalPlaying != null && localPlaying != previousLocalPlaying) {
            // Si quien controla usa los controles nativos del reproductor, publicar el cambio
            // una sola vez cuando realmente cambie play/pausa.
            publish(localPlaying, cur)
          } else if (mine) {
            // Latido: quien controla publica su posición cada ~4 s (mismo seq) para que los demás se mantengan alineados.
            val now = System.currentTimeMillis()
            if (localPlaying && docPlaying && now - lastHeartbeat > 4000L) { lastHeartbeat = now; heartbeat(cur) }
          } else {
            // Seguidores: play/pausa igual al estado compartido y corrección de desfase > 2,5 s.
            if (docPlaying && !localPlaying) playJs() else if (!docPlaying && localPlaying) pauseJs()
            if (docPlaying) {
              val pos = (d["pos"] as? Number)?.toDouble() ?: 0.0
              val t = (d["t"] as? Number)?.toLong() ?: 0L
              val expected = pos + ((System.currentTimeMillis() - t) / 1000.0).coerceIn(0.0, 6 * 3600.0)
              val now = System.currentTimeMillis()
              if (localPlaying && abs(cur - expected) > 2.5 && now - lastDriftFix > 6000L) { lastDriftFix = now; seekJs(expected) }
            }
          }
          lastPolledPlaying = localPlaying
        }
      }
    }
  }

  private fun converge(d: Map<String, Any>, cur: Double, localPlaying: Boolean) {
    val seq = (d["seq"] as? Number)?.toLong() ?: 0L
    val by = (d["by"] as? String) ?: ""
    val pos = (d["pos"] as? Number)?.toDouble() ?: 0.0
    val t = (d["t"] as? Number)?.toLong() ?: 0L
    val playing = (d["playing"] as? Boolean) ?: false
    if (seq != appliedSeq) {
      val own = by == (myUid() ?: "") && !firstApply
      appliedSeq = seq
      firstApply = false
      if (!own) {
        val elapsed = if (playing) ((System.currentTimeMillis() - t) / 1000.0).coerceIn(0.0, 6 * 3600.0) else 0.0
        seekJs(pos + elapsed)
      }
    }
    // Play y pausa siempre convergen al estado compartido.
    if (playing && !localPlaying) playJs()
    else if (!playing && localPlaying) pauseJs()
  }

  private fun heartbeat(pos: Double) {
    val d = doc ?: return
    val seq = (d["seq"] as? Number)?.toLong() ?: 0L
    publishDoc(mapOf(
      "vid" to _vid.value, "playing" to true, "pos" to pos, "t" to System.currentTimeMillis(),
      "by" to (myUid() ?: ""), "seq" to seq, "queue" to _queue.value
    ))
  }

  private fun publish(play: Boolean, pos: Double, newVid: String? = null, newQueue: List<String>? = null) {
    val seq = ((doc?.get("seq") as? Number)?.toLong() ?: 0L) + 1
    publishDoc(mapOf(
      "vid" to (newVid ?: _vid.value), "playing" to play, "pos" to pos,
      "t" to System.currentTimeMillis(), "by" to (myUid() ?: ""), "seq" to seq,
      "queue" to (newQueue ?: _queue.value)
    ))
  }

  fun setVolume(f: Double) = js("(function(){var v=document.querySelector('video');if(v){v.volume=$f;}})()")

  fun load(id: String) {
    browsingYoutube = false
    _browsing.value = false
    web?.tag = null
    publish(true, 0.0, id)
  }
  fun browseYoutube() {
    browsingYoutube = true
    _browsing.value = true
    web?.tag = "youtube-browse"
    web?.loadUrl("https://m.youtube.com/")
  }
  fun searchYoutube(query: String) {
    val q = query.trim()
    if (q.isEmpty()) return
    browsingYoutube = true
    _browsing.value = true
    web?.tag = "youtube-browse-search"
    web?.loadUrl("https://m.youtube.com/results?search_query=" + android.net.Uri.encode(q))
  }
  fun returnToSharedVideo() {
    browsingYoutube = false
    _browsing.value = false
    web?.tag = null
  }
  fun enqueue(id: String) { publish(_playing.value, _time.value, null, _queue.value + id) }
  fun next() {
    val q = _queue.value
    if (q.isEmpty()) return
    publish(true, 0.0, q.first(), q.drop(1))
  }
  fun stop() { browsingYoutube = false; _browsing.value = false; publish(false, 0.0, "", _queue.value) }
  fun toggleMobile() { useMobile = !useMobile; web?.tag = null }
  fun usingMobile(): Boolean = useMobile
  fun reloadPlayer() { embedMode = 0; useMobile = false; web?.tag = null; _error.value = 0 }
  fun play() { publish(true, _time.value) }
  fun pause() { publish(false, _time.value) }
  fun seek(sec: Double) { seekJs(sec); publish(_playing.value, sec) }
  fun skip(delta: Double) { seek((_time.value + delta).coerceAtLeast(0.0)) }

  private fun hideFullscreenView() {
    val context = web?.context as? android.content.MutableContextWrapper
    val activity = context?.baseContext as? Activity
    val decor = activity?.window?.decorView as? ViewGroup
    fullscreenContainer?.let { container ->
      (container.parent as? ViewGroup)?.removeView(container)
      container.removeAllViews()
    }
    fullscreenContainer = null
    fullscreenView = null
    fullscreenCallback?.onCustomViewHidden()
    fullscreenCallback = null
    if (activity != null && decor != null) {
      activity.window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
      decor.systemUiVisibility = previousSystemUiVisibility
    }
  }

  fun release() {
    runCatching { hideFullscreenView() }
    web?.destroy()
    web = null
  }
}
