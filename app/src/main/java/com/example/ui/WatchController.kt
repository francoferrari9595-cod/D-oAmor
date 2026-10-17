package com.example.ui

import android.annotation.SuppressLint
import android.app.Application
import android.view.ViewGroup
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
  private val ua = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
  private val pollJs = "(function(){var v=document.querySelector('video');var e=document.querySelector('.ytp-error');if(e&&e.offsetParent!==null){return 'ERR';}if(!v){return '';}return v.currentTime+'|'+(v.duration||0)+'|'+(v.paused?0:1);})()"

  private var web: WebView? = null
  private var doc: Map<String, Any>? = null
  private var appliedSeq = -1L
  private var firstApply = true

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
        delay(500)
        tick()
      }
    }
  }

  @SuppressLint("SetJavaScriptEnabled")
  fun webView(): WebView {
    val existing = web
    if (existing != null) {
      (existing.parent as? ViewGroup)?.removeView(existing)
      return existing
    }
    val wv = WebView(app)
    wv.settings.javaScriptEnabled = true
    wv.settings.domStorageEnabled = true
    wv.settings.mediaPlaybackRequiresUserGesture = false
    wv.settings.userAgentString = ua
    wv.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
    wv.webChromeClient = WebChromeClient()
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
    }
    _controller.value = (d?.get("by") as? String) ?: ""
    _playing.value = (d?.get("playing") as? Boolean) ?: false
  }

  private fun embedUrl(id: String): String =
    "https://www.youtube.com/embed/$id?autoplay=0&controls=0&playsinline=1&rel=0&modestbranding=1&fs=0&disablekb=1&iv_load_policy=3&origin=https%3A%2F%2Fsinijoss.app"

  private fun js(script: String) { web?.evaluateJavascript(script, null) }
  private fun seekJs(sec: Double) = js("(function(){var v=document.querySelector('video');if(v){v.currentTime=$sec;}})()")
  private fun playJs() = js("(function(){var v=document.querySelector('video');if(v){var p=v.play();if(p&&p.catch){p.catch(function(){});}}})()")
  private fun pauseJs() = js("(function(){var v=document.querySelector('video');if(v){v.pause();}})()")

  private fun tick() {
    val wv = web ?: return
    val d = doc ?: return
    val id = _vid.value
    if (id.isEmpty()) return
    if (wv.tag != id) {
      wv.tag = id
      wv.loadUrl(embedUrl(id), mapOf("Referer" to referer))
      return
    }
    wv.evaluateJavascript(pollJs) { raw ->
      val r = raw?.trim('"') ?: ""
      if (r == "ERR") {
        _error.value = 152
      } else if (r.contains("|")) {
        val p = r.split("|")
        val cur = p.getOrNull(0)?.toDoubleOrNull() ?: 0.0
        val dur = p.getOrNull(1)?.toDoubleOrNull() ?: 0.0
        val localPlaying = p.getOrNull(2) == "1"
        _time.value = cur
        if (dur > 0 && !dur.isNaN()) {
          _duration.value = dur
          _ready.value = true
          _error.value = 0
          converge(d, cur, localPlaying)
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
        val elapsed = if (playing) ((System.currentTimeMillis() - t) / 1000.0).coerceIn(0.0, 6.0) else 0.0
        seekJs(pos + elapsed)
      }
    }
    // Play y pausa siempre convergen al estado compartido.
    if (playing && !localPlaying) playJs()
    else if (!playing && localPlaying) pauseJs()
  }

  private fun publish(play: Boolean, pos: Double, newVid: String? = null) {
    val seq = ((doc?.get("seq") as? Number)?.toLong() ?: 0L) + 1
    publishDoc(mapOf(
      "vid" to (newVid ?: _vid.value), "playing" to play, "pos" to pos,
      "t" to System.currentTimeMillis(), "by" to (myUid() ?: ""), "seq" to seq
    ))
  }

  fun load(id: String) { publish(false, 0.0, id) }
  fun play() { playJs(); publish(true, _time.value) }
  fun pause() { pauseJs(); publish(false, _time.value) }
  fun seek(sec: Double) { seekJs(sec); publish(_playing.value, sec) }
  fun skip(delta: Double) { seek((_time.value + delta).coerceAtLeast(0.0)) }

  fun release() {
    web?.destroy()
    web = null
  }
}
