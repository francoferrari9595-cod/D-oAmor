package com.example.soccer

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.Canvas as KitCanvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.screens.GameCtx
import com.example.ui.screens.gameAvail
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

private val SLEVELS = listOf("Principiante", "Aficionado", "Profesional", "Leyenda")

private class SoccerPrefs(private val prefs: android.content.SharedPreferences, defaultNick: String) {
  var nick by mutableStateOf(prefs.getString("nick", null) ?: defaultNick)
  var size by mutableIntStateOf(prefs.getInt("size", 2))
  var level by mutableIntStateOf(prefs.getInt("level", 1))
  var half by mutableIntStateOf(prefs.getInt("half", 90))
  var myKit by mutableIntStateOf(prefs.getInt("myKit", 0)); var rivalKit by mutableIntStateOf(prefs.getInt("rivalKit", 2))
  var wins by mutableIntStateOf(prefs.getInt("wins", 0)); var draws by mutableIntStateOf(prefs.getInt("draws", 0)); var losses by mutableIntStateOf(prefs.getInt("losses", 0))
  fun save() { prefs.edit().putString("nick", nick).putInt("size", size).putInt("level", level).putInt("half", half).putInt("myKit", myKit).putInt("rivalKit", rivalKit).putInt("wins", wins).putInt("draws", draws).putInt("losses", losses).apply() }
}

private object SoccerSfx {
  private var tg: ToneGenerator? = null
  private fun g(): ToneGenerator? { if (tg == null) tg = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 78) }.getOrNull(); return tg }
  fun kick() { g()?.startTone(ToneGenerator.TONE_PROP_BEEP, 40) }
  fun whistle() { g()?.startTone(ToneGenerator.TONE_PROP_BEEP2, 260) }
  fun goal() { g()?.startTone(ToneGenerator.TONE_CDMA_CONFIRM, 600) }
  fun save() { g()?.startTone(ToneGenerator.TONE_PROP_ACK, 90) }
  fun release() { runCatching { tg?.release() }; tg = null }
}

private class SCtrl {
  var jx = 0f; var jy = 0f; var sprint = false
  var charging = false; var chargeStart = 0L
  var passCharging = false; var passStart = 0L; var passPower = 0.5f
  var hold = false
  var shotSeq = 0; var shotPower = 0.5f; var passSeq = 0; var tackleSeq = 0; var switchSeq = 0; var lobSeq = 0; var thSeq = 0
  var joyOrigin by mutableStateOf<Offset?>(null); var joyKnob by mutableStateOf(Offset.Zero)
  fun passCharge(): Float = if (passCharging) ((SystemClock.elapsedRealtime() - passStart) / 850f).coerceIn(0f, 1f) else 0f
  fun charge(): Float = if (charging) ((SystemClock.elapsedRealtime() - chargeStart) / 850f).coerceIn(0f, 1f) else 0f
}

/**
 * FÚTBOL PRO — partido 3D con estadio y público. Contra bots, uno contra uno o con amigos en línea (equipos de 1 a 4 jugadores + portero).
 * Controles: joystick para moverte, TIRO (mantén para más potencia), PASE, ENTRADA, CORRER y CAMBIO de jugador.
 */
@Composable
internal fun SoccerGame(c: GameCtx) {
  val ctx = LocalContext.current.applicationContext
  val st = remember { SoccerPrefs(ctx.getSharedPreferences("soccer_prefs", android.content.Context.MODE_PRIVATE), c.nameOf(c.me).takeIf { it != "Tú" }.orEmpty()) }
  var screen by remember { mutableStateOf("menu") }
  var runId by remember { mutableIntStateOf(0) }
  var joinTs by remember { mutableIntStateOf(2) }; var joinHs by remember { mutableIntStateOf(90) }
  val avail = (gameAvail() - 8.dp).coerceAtLeast(380.dp)
  val isHost = c.me == c.hostUid.ifBlank { c.firstUid }
  val snap by remember { c.vm.gameState("soccer") }.collectAsState(initial = null)
  var lastHb by remember { mutableLongStateOf(-1L) }; var lastAt by remember { mutableLongStateOf(0L) }
  var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
  LaunchedEffect(snap) { val hb = (snap?.get("hb") as? Number)?.toLong() ?: -1L; if (hb != lastHb) { lastHb = hb; lastAt = System.currentTimeMillis() } }
  LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }
  val phase = (snap?.get("sc") as? List<*>)?.getOrNull(2) as? Number
  val live = snap != null && snap?.get("pl") != null && phase?.toInt() != PH_OVER && now - lastAt < 4000L
  val liveTs = ((snap?.get("ts") as? Number)?.toInt() ?: 2).coerceIn(1, 4); val liveHs = (snap?.get("hs") as? Number)?.toInt() ?: 90

  val playing = screen != "menu"
  val hostCtx = LocalContext.current
  if (playing) ForceLandscape(hostCtx)
  Box(Modifier.fillMaxWidth().height(avail).clip(RoundedCornerShape(16.dp))) {
    if (!playing) {
      SoccerMenu(st, c.players.size, isHost, live, liveTs) { s -> st.save(); if (s == "client") { joinTs = liveTs; joinHs = liveHs }; runId++; screen = s }
    } else {
      Box(Modifier.fillMaxSize().background(Color(0xFF0B1220)), contentAlignment = Alignment.Center) { Text("⚽ Partido en pantalla completa (horizontal)", color = Color(0xFFB0BEC5), fontSize = 13.sp, textAlign = TextAlign.Center) }
    }
  }
  if (playing) {
    // el partido se juega en una ventana de pantalla completa y en horizontal: la cancha se ve grande y nada se sale de la pantalla
    Dialog(onDismissRequest = { screen = "menu" }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
      ImmersiveDialogWindow()
      Box(Modifier.fillMaxSize().background(Color.Black)) {
        androidx.compose.runtime.key(runId) {
          SoccerArena(screen, if (screen == "client") joinTs else st.size, if (screen == "client") joinHs else st.half, st, c, onRestart = { runId++ }, onMenu = { screen = "menu" })
        }
      }
    }
  }
}

private fun Context.findActivity(): Activity? { var cx: Context = this; while (cx is ContextWrapper) { if (cx is Activity) return cx; cx = cx.baseContext }; return null }

/** Mientras dura el partido, la pantalla se pone en horizontal; al salir vuelve a como estaba. */
@Composable
private fun ForceLandscape(ctx: Context) {
  DisposableEffect(Unit) {
    val act = ctx.findActivity()
    val old = act?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    act?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    onDispose { act?.requestedOrientation = old }
  }
}

/** Ventana del diálogo a pantalla completa, sin barras del sistema y usando el área de la muesca. */
@Composable
private fun ImmersiveDialogWindow() {
  val view = LocalView.current
  SideEffect {
    try {
      val win = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
      win.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
      WindowCompat.setDecorFitsSystemWindows(win, false)
      if (android.os.Build.VERSION.SDK_INT >= 28) { val lp = win.attributes; lp.layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES; win.attributes = lp }
      val ctl = WindowCompat.getInsetsController(win, view)
      ctl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
      ctl.hide(WindowInsetsCompat.Type.systemBars())
    } catch (e: Throwable) { }
  }
}

@Composable
private fun SoccerMenu(st: SoccerPrefs, humans: Int, isHost: Boolean, live: Boolean, liveTs: Int, onStart: (String) -> Unit) {
  Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF04100A), Color(0xFF0B2B17), Color(0xFF0A1F33))))) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Text("⚽ FÚTBOL PRO", fontSize = 28.sp, fontWeight = FontWeight.Black, color = Color(0xFF69F0AE))
      Text("Cámara de transmisión · estadio con público · porteros · en línea", color = Color(0xFFB0BEC5), fontSize = 12.sp)
      OutlinedTextField(
        st.nick, { st.nick = it.take(10) }, singleLine = true, label = { Text("Apodo") }, modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedLabelColor = Color(0xFF69F0AE), unfocusedLabelColor = Color(0xFF90A4AE), focusedBorderColor = Color(0xFF69F0AE))
      )
      Text("Formato del partido", color = Color(0xFF69F0AE), fontWeight = FontWeight.Bold, fontSize = 12.sp)
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (n in 1..4) { val on = st.size == n; Box(Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(10.dp)).background(if (on) Color(0xFF00E676) else Color(0xFF14262E)).clickable { st.size = n }, contentAlignment = Alignment.Center) { Text("${n}v$n", color = if (on) Color(0xFF00281A) else Color.White, fontWeight = FontWeight.Black) } }
      }
      Text("Nivel de los bots", color = Color(0xFF69F0AE), fontWeight = FontWeight.Bold, fontSize = 12.sp)
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SLEVELS.forEachIndexed { i, n -> val on = st.level == i; Box(Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(10.dp)).background(if (on) Color(0xFF00E5FF) else Color(0xFF14262E)).clickable { st.level = i }, contentAlignment = Alignment.Center) { Text(n, color = if (on) Color(0xFF00232B) else Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp, textAlign = TextAlign.Center) } }
      }
      Text("Tu equipo", color = Color(0xFF69F0AE), fontWeight = FontWeight.Bold, fontSize = 12.sp)
      KitPicker(st.myKit) { st.myKit = it }
      Text("Rival", color = Color(0xFF69F0AE), fontWeight = FontWeight.Bold, fontSize = 12.sp)
      KitPicker(st.rivalKit) { st.rivalKit = it }
      Text("Duración de cada tiempo", color = Color(0xFF69F0AE), fontWeight = FontWeight.Bold, fontSize = 12.sp)
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for ((lab, v) in listOf("1:00" to 60, "1:30" to 90, "2:00" to 120)) { val on = st.half == v; Box(Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(10.dp)).background(if (on) Color(0xFFFFD740) else Color(0xFF14262E)).clickable { st.half = v }, contentAlignment = Alignment.Center) { Text(lab, color = if (on) Color(0xFF2B2000) else Color.White, fontWeight = FontWeight.Black) } }
      }
      if (st.wins + st.draws + st.losses > 0) Text("Contra bots: ${st.wins} ganados · ${st.draws} empates · ${st.losses} perdidos", color = Color(0xFFFFD740), fontWeight = FontWeight.Bold, fontSize = 12.sp)
      Box(Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF00C853)).clickable { onStart("local") }, contentAlignment = Alignment.Center) { Text("JUGAR CONTRA BOTS", color = Color(0xFF00281A), fontWeight = FontWeight.Black, fontSize = 16.sp) }
      if (humans > 1) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF102A44)).border(1.dp, Color(0x5540C4FF), RoundedCornerShape(14.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text("🌐 En línea · $humans jugadores en la mesa", color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp)
          Text("Los jugadores se reparten entre azul y rojo; los puestos libres los ocupan bots.", color = Color(0xFFB0BEC5), fontSize = 11.sp)
          if (isHost) Box(Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF40C4FF)).clickable { onStart("host") }, contentAlignment = Alignment.Center) { Text("CREAR PARTIDA ${st.size}v${st.size}", color = Color(0xFF00243A), fontWeight = FontWeight.Black) }
          else {
            Text(if (live) "¡Hay un partido de ${liveTs}v$liveTs abierto!" else "Esperando a que el anfitrión cree el partido…", color = Color(0xFFB0BEC5), fontSize = 12.sp)
            Box(Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(12.dp)).background(if (live) Color(0xFF40C4FF) else Color(0xFF546E7A)).clickable(enabled = live) { onStart("client") }, contentAlignment = Alignment.Center) { Text("UNIRSE AL PARTIDO", color = Color(0xFF00243A), fontWeight = FontWeight.Black) }
          }
        }
      }
      Text("El partido se juega en pantalla completa y en horizontal · Joystick: moverte y APUNTAR (flecha amarilla); llévalo al borde para correr · TIRO y PASE: mantén apretado para más potencia (pase al máximo = por alto) · ROBAR: mantén apretado y le quitas la pelota directo · 🔁 cambia de jugador", color = Color(0xFF90A4AE), fontSize = 11.sp, textAlign = TextAlign.Center)
    }
  }
}

/** Camiseta en miniatura con el diseño del equipo. */
@Composable
private fun KitIcon(k: SKit, dp: Int) {
  KitCanvas(Modifier.size(dp.dp)) {
    val w = size.width; val h = size.height
    val x0 = w * 0.12f; val tw = w * 0.76f; val y0 = h * 0.10f; val th = h * 0.80f
    drawRect(k.shirt, Offset(x0, y0), androidx.compose.ui.geometry.Size(tw, th))
    when (k.pattern) {
      1 -> for (i in 0 until 5 step 2) drawRect(k.shirt2, Offset(x0 + tw * i / 5f, y0), androidx.compose.ui.geometry.Size(tw / 5f, th))
      2 -> drawRect(k.shirt2, Offset(x0, y0 + th * 0.34f), androidx.compose.ui.geometry.Size(tw, th * 0.30f))
      3 -> { val sash = androidx.compose.ui.graphics.Path().apply { moveTo(x0, y0 + th * 0.05f); lineTo(x0 + tw * 0.4f, y0); lineTo(x0 + tw, y0 + th * 0.65f); lineTo(x0 + tw, y0 + th * 0.95f); close() }; drawPath(sash, k.shirt2) }
      4 -> for (i in 0 until 4 step 2) drawRect(k.shirt2, Offset(x0, y0 + th * i / 4f), androidx.compose.ui.geometry.Size(tw, th / 4f))
      5 -> drawRect(k.shirt2, Offset(w / 2f, y0), androidx.compose.ui.geometry.Size(tw / 2f, th))
      6 -> { drawRect(k.shirt2, Offset(x0, y0), androidx.compose.ui.geometry.Size(tw * 0.14f, th)); drawRect(k.shirt2, Offset(x0 + tw * 0.86f, y0), androidx.compose.ui.geometry.Size(tw * 0.14f, th)) }
    }
    drawRect(Color(0x66FFFFFF), Offset(x0, y0), androidx.compose.ui.geometry.Size(tw, th), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f))
  }
}

/** Selector de selecciones y clubes: pestañas y una fila deslizable con la camiseta de cada uno. */
@Composable
private fun KitPicker(selected: Int, onSelect: (Int) -> Unit) {
  var clubs by remember { mutableStateOf(kitAt(selected).club) }
  val list = remember(clubs) { ALL_KITS.withIndex().filter { it.value.club == clubs } }
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    for ((lab, v) in listOf("Selecciones" to false, "Clubes" to true)) {
      val on = clubs == v
      Box(Modifier.weight(1f).height(34.dp).clip(RoundedCornerShape(10.dp)).background(if (on) Color(0xFF69F0AE) else Color(0xFF14262E)).clickable { clubs = v }, contentAlignment = Alignment.Center) { Text(lab, color = if (on) Color(0xFF00281A) else Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
    }
  }
  LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    items(list) { iv ->
      val on = iv.index == selected
      Column(Modifier.width(70.dp).clip(RoundedCornerShape(12.dp)).background(if (on) Color(0xFF1B5E20) else Color(0xFF14262E)).border(2.dp, if (on) Color(0xFF69F0AE) else Color.Transparent, RoundedCornerShape(12.dp)).clickable { onSelect(iv.index) }.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        KitIcon(iv.value, 40)
        Text(iv.value.name, color = Color.White, fontSize = 9.sp, maxLines = 2, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
      }
    }
  }
}

@Composable
private fun SoccerArena(role: String, teamSize: Int, halfSec: Int, st: SoccerPrefs, c: GameCtx, onRestart: () -> Unit, onMenu: () -> Unit) {
  val world = remember { SoccerWorld(teamSize, st.level, halfSec.toFloat(), Random.Default, role == "client").also { w0 -> if (role != "client") w0.setKits(st.myKit, st.rivalKit) } }
  var n0 by remember { mutableStateOf("") }; var n1 by remember { mutableStateOf("") }
  val fx = remember { SFx() }
  val pt = remember { SPaints() }
  val ctrl = remember { SCtrl() }
  var frame by remember { mutableIntStateOf(0) }
  var s0 by remember { mutableIntStateOf(0) }; var s1 by remember { mutableIntStateOf(0) }
  var minute by remember { mutableIntStateOf(1) }; var halfUi by remember { mutableIntStateOf(1) }
  var banner by remember { mutableStateOf("") }; var phaseUi by remember { mutableIntStateOf(PH_KICKOFF) }
  var holdUi by remember { mutableStateOf(false) }
  var stamina by remember { mutableFloatStateOf(1f) }; var chargeUi by remember { mutableFloatStateOf(0f) }
  var spectator by remember { mutableStateOf(false) }
  var overResult by remember { mutableIntStateOf(-2) }
  var gotSnap by remember { mutableStateOf(false) }
  var canvasW by remember { mutableFloatStateOf(1080f) }
  var scored by remember { mutableStateOf(false) }
  val guests = remember { if (role == "host") c.players.filter { it != c.me } else emptyList() }
  val inputs = remember { mutableStateMapOf<String, Map<String, Any?>>() }

  if (role == "host") for (uid in guests) LaunchedEffect(uid) { c.vm.gameState("soccerIn_$uid").collect { m -> if (m != null) inputs[uid] = m } }
  if (role == "client") LaunchedEffect(Unit) { c.vm.gameState("soccer").collect { m -> if (m != null && m["pl"] != null) { world.applySnapshot(m); gotSnap = true } } }
  if (role == "host") DisposableEffect(Unit) { onDispose { c.vm.publishGame("soccer", emptyMap()) } }
  DisposableEffect(Unit) { onDispose { SoccerSfx.release() } }

  fun myUid() = if (role == "local") "me" else c.me
  fun myPlayer(): SPlayer? = if (role == "client") world.players.firstOrNull { it.uid == c.me && it.human } else world.controlled(myUid())

  LaunchedEffect(Unit) {
    val rnd = Random(System.nanoTime().toInt())
    if (role == "local") world.assignHuman("me", st.nick.ifBlank { "Tú" }, 0)
    if (role == "host") {
      val all = listOf(c.me) + guests
      all.forEachIndexed { i, uid -> world.assignHuman(uid, if (uid == c.me) st.nick.ifBlank { "Tú" } else c.nameOf(uid), i % 2) }
    }
    var last = 0L; var hudT = 0f; var pubT = 0f; var sendT = 0f; var hb = 0L; var t = 0f
    var seenShot = 0; var seenPass = 0; var seenTackle = 0; var seenSwitch = 0; var seenLob = 0; var seenTh = 0
    val gLob = HashMap<String, Int>(); val gTh = HashMap<String, Int>()
    val gShot = HashMap<String, Int>(); val gPass = HashMap<String, Int>(); val gTk = HashMap<String, Int>(); val gSw = HashMap<String, Int>()
    var lastKey = ""; var sentAt = 0L
    while (true) {
      androidx.compose.runtime.withFrameNanos { tn ->
        val dt = if (last == 0L) 0.016f else ((tn - last) / 1e9f).coerceAtMost(0.05f); last = tn
        t += dt
        val me = myPlayer()
        if (role == "client") {
          world.extrapolate(dt)
          sendT += dt
          if (sendT >= 0.05f) {
            sendT = 0f
            val key = "${(ctrl.jx * 10).toInt()},${(ctrl.jy * 10).toInt()},${ctrl.sprint},${ctrl.shotSeq},${ctrl.passSeq},${(ctrl.passPower * 10).toInt()},${ctrl.tackleSeq},${ctrl.switchSeq},${ctrl.lobSeq},${ctrl.thSeq},${ctrl.hold}"
            val nowMs = SystemClock.elapsedRealtime()
            if (key != lastKey || nowMs - sentAt > 800L) { lastKey = key; sentAt = nowMs; c.vm.publishGame("soccerIn_${c.me}", mapOf("jx" to ctrl.jx.toDouble(), "jy" to ctrl.jy.toDouble(), "sp" to ctrl.sprint, "pw" to ctrl.shotPower.toDouble(), "sh" to ctrl.shotSeq.toLong(), "ps" to ctrl.passSeq.toLong(), "pp" to ctrl.passPower.toDouble(), "tk" to ctrl.tackleSeq.toLong(), "sw" to ctrl.switchSeq.toLong(), "lb" to ctrl.lobSeq.toLong(), "th" to ctrl.thSeq.toLong(), "hd" to ctrl.hold)) }
          }
        } else {
          val uid = myUid()
          world.setInput(uid, ctrl.jx, ctrl.jy, ctrl.sprint)
          world.setHold(uid, ctrl.hold)
          if (ctrl.shotSeq != seenShot) { seenShot = ctrl.shotSeq; world.shootRelease(uid, ctrl.shotPower) }
          if (ctrl.passSeq != seenPass) { seenPass = ctrl.passSeq; world.passPress(uid, ctrl.passPower) }
          if (ctrl.tackleSeq != seenTackle) { seenTackle = ctrl.tackleSeq; world.tacklePress(uid) }
          if (ctrl.switchSeq != seenSwitch) { seenSwitch = ctrl.switchSeq; world.switchPress(uid) }
          if (ctrl.lobSeq != seenLob) { seenLob = ctrl.lobSeq; world.lobPress(uid) }
          if (ctrl.thSeq != seenTh) { seenTh = ctrl.thSeq; world.throughPress(uid) }
          if (role == "host") for (g in guests) {
            val inp = inputs[g] ?: continue
            world.setInput(g, (inp["jx"] as? Number)?.toFloat() ?: 0f, (inp["jy"] as? Number)?.toFloat() ?: 0f, inp["sp"] == true)
            world.setHold(g, inp["hd"] == true)
            val sh = (inp["sh"] as? Number)?.toInt() ?: 0; if (sh != (gShot[g] ?: sh)) world.shootRelease(g, (inp["pw"] as? Number)?.toFloat() ?: 0.5f); gShot[g] = sh
            val ps = (inp["ps"] as? Number)?.toInt() ?: 0; if (ps != (gPass[g] ?: ps)) world.passPress(g, (inp["pp"] as? Number)?.toFloat() ?: -1f); gPass[g] = ps
            val tk = (inp["tk"] as? Number)?.toInt() ?: 0; if (tk != (gTk[g] ?: tk)) world.tacklePress(g); gTk[g] = tk
            val sw = (inp["sw"] as? Number)?.toInt() ?: 0; if (sw != (gSw[g] ?: sw)) world.switchPress(g); gSw[g] = sw
            val lb = (inp["lb"] as? Number)?.toInt() ?: 0; if (lb != (gLob[g] ?: lb)) world.lobPress(g); gLob[g] = lb
            val th = (inp["th"] as? Number)?.toInt() ?: 0; if (th != (gTh[g] ?: th)) world.throughPress(g); gTh[g] = th
          }
          world.update(dt)
          if (role == "host") { pubT += dt; if (pubT >= 0.066f) { pubT = 0f; hb++; c.vm.publishGame("soccer", world.encode() + mapOf("hb" to hb)) } }
        }
        // eventos
        for (e in world.events) {
          when (e.kind) {
            0 -> {
              for (i in 0 until 4) fx.parts.add(SPart(e.x, 0.1f, e.y, (rnd.nextFloat() - 0.5f) * 3f, 0.8f + rnd.nextFloat(), (rnd.nextFloat() - 0.5f) * 3f, 0.5f, 0.5f, 0.07f, Color(0xFFCFD8DC)))
              fx.rings.add(floatArrayOf(e.x, e.y, 0f, 0.45f, 0.5f + e.power))
              if (e.power > 0.7f) {
                // tiro fuerte: onda grande, chispas de fuego, sacudida y golpe de zoom
                fx.rings.add(floatArrayOf(e.x, e.y, 0f, 0.7f, 1.6f))
                for (i in 0 until 26) { val a = rnd.nextFloat() * 2f * PI.toFloat(); val sp2 = 3f + rnd.nextFloat() * 9f; fx.parts.add(SPart(e.x, 0.3f, e.y, cos(a) * sp2, 1.5f + rnd.nextFloat() * 3f, sin(a) * sp2, 0.6f, 0.6f, 0.10f, listOf(Color(0xFFFFD740), Color(0xFFFF6D00), Color.White)[i % 3])) }
                fx.shake = max(fx.shake, 0.55f); fx.zoom = max(fx.zoom, 1.14f)
              }
              SoccerSfx.kick()
            }
            1 -> { fx.rings.add(floatArrayOf(e.x, e.y, 0f, 0.4f, 0.8f)); for (i in 0 until 6) fx.parts.add(SPart(e.x, 0.1f, e.y, (rnd.nextFloat() - 0.5f) * 4f, 1f + rnd.nextFloat(), (rnd.nextFloat() - 0.5f) * 4f, 0.6f, 0.6f, 0.09f, Color(0xFF8D6E63))); fx.shake = max(fx.shake, 0.2f) }
            2 -> {
              fx.goalT = 3.4f; fx.goalTeam = e.team; fx.flash = 1f; fx.shake = 0.9f; SoccerSfx.goal()
              for (i in 0 until 90) { val a = rnd.nextFloat() * 2f * PI.toFloat(); val sp = 2f + rnd.nextFloat() * 7f; fx.parts.add(SPart(e.x, 1.5f, e.y, cos(a) * sp, 4f + rnd.nextFloat() * 6f, sin(a) * sp, 2.2f, 2.2f, 0.13f, listOf(Color(0xFFFFD740), Color(0xFFFF4081), Color(0xFF40C4FF), Color(0xFF69F0AE), Color.White)[i % 5])) }
            }
            3 -> { fx.shake = max(fx.shake, 0.3f); SoccerSfx.save() }
            4 -> { fx.shake = max(fx.shake, 0.4f); SoccerSfx.kick() }
            5 -> SoccerSfx.whistle()
          }
        }
        world.events.clear()
        var pi = fx.parts.size - 1
        while (pi >= 0) { val p = fx.parts[pi]; p.life -= dt; if (p.life <= 0f) fx.parts.removeAt(pi) else { p.x += p.vx * dt; p.z += p.vz * dt; p.vh -= 9f * dt; p.h = max(0f, p.h + p.vh * dt); if (p.h == 0f) { p.vh *= -0.3f; p.vx *= 0.8f; p.vz *= 0.8f } }; pi-- }
        fx.goalT = max(0f, fx.goalT - dt); fx.shake = max(0f, fx.shake - dt * 1.6f); fx.flash = max(0f, fx.flash - dt * 1.5f)
        fx.zoom += ((if (fx.goalT > 0f) 1.35f else 1f) - fx.zoom) * min(1f, dt * 3f)
        val b = world.ball
        val spd = hypot(b.vx, b.vy)
        fx.roll += spd * dt * 0.9f
        fx.spinBall(b, dt)
        run { var ri = fx.rings.size - 1; while (ri >= 0) { val rg = fx.rings[ri]; rg[2] += dt; if (rg[2] >= rg[3]) fx.rings.removeAt(ri); ri-- } }
        fx.trail.add(floatArrayOf(b.x, b.y, b.z)); if (fx.trail.size > (if (spd > 13f) 12 else 0)) while (fx.trail.size > (if (spd > 13f) 12 else 0)) fx.trail.removeAt(0)
        // cámara: sigue el balón con un poco de ventaja hacia donde ataca tu equipo
        val team = me?.team ?: 0
        val look = sDir(team) * 6f
        val tx = (b.x * 0.85f).coerceIn(-S_HW + 8f, S_HW - 8f); val tz = (b.y * 0.85f + (me?.y ?: b.y) * 0.15f + look).coerceIn(-S_HL + 4f, S_HL - 4f)
        if (!fx.init) { fx.camX = tx; fx.camZ = tz; fx.init = true }
        val k = min(1f, dt * (3f + spd * 0.12f))
        fx.camX += (tx - fx.camX) * k; fx.camZ += (tz - fx.camZ) * k
        hudT += dt
        if (hudT > 0.1f) {
          hudT = 0f
          s0 = world.score[0]; s1 = world.score[1]; halfUi = world.half; phaseUi = world.phase
          minute = (world.time / world.totalTime * 90f).toInt().coerceIn(0, 90) + 1
          n0 = world.kits.first.short; n1 = world.kits.second.short
          banner = if (world.phase == PH_GOAL) "¡GOOOL! ${(if (world.scorerTeam == 1) world.kits.second else world.kits.first).name.uppercase()}" else world.msg
          stamina = me?.stamina ?: 1f; chargeUi = maxOf(ctrl.charge(), ctrl.passCharge()); spectator = me == null
          overResult = world.winner
          if (!scored && world.phase == PH_OVER && role == "local") { scored = true; if (world.winner == 0) st.wins++ else if (world.winner == 1) st.losses++ else st.draws++; st.save() }
        }
        frame++
      }
    }
  }

  val meNow = myPlayer()
  val flipNow = (meNow?.team ?: 0) == 1
  Box(Modifier.fillMaxSize().background(Color.Black)) {
    Canvas(
      Modifier.fillMaxSize().pointerInput(Unit) {
        awaitEachGesture {
          val first = awaitFirstDown(requireUnconsumed = false)
          if (first.position.x > size.width * 0.58f) { while (true) { val ev = awaitPointerEvent(); if (ev.changes.all { !it.pressed }) break }; return@awaitEachGesture }
          val origin = first.position; ctrl.joyOrigin = origin; ctrl.joyKnob = origin
          val maxR = 62.dp.toPx()
          first.consume()
          while (true) {
            val ev = awaitPointerEvent()
            val ch = ev.changes.firstOrNull { it.id == first.id } ?: break
            if (!ch.pressed) break
            val v = ch.position - origin; val len = v.getDistance(); val mg = min(1f, len / maxR)
            ctrl.jx = if (len > 0.01f) v.x / len * mg else 0f; ctrl.jy = if (len > 0.01f) v.y / len * mg else 0f
            ctrl.joyKnob = if (len > maxR) origin + v * (maxR / len) else ch.position
            ch.consume()
          }
          ctrl.jx = 0f; ctrl.jy = 0f; ctrl.joyOrigin = null
        }
      }
    ) {
      canvasW = size.width
      val tick = frame
      if (tick < 0) return@Canvas
      drawSoccer(world, fx, pt, flipNow, meNow?.id ?: -1, SystemClock.uptimeMillis() / 1000f, maxOf(ctrl.charge(), ctrl.passCharge()), ctrl.jx, ctrl.jy)
      drawRadar(world, flipNow, 10f, 10f, size.width * 0.17f, meNow?.id ?: -1)
      ctrl.joyOrigin?.let { o ->
        val running = hypot(ctrl.jx, ctrl.jy) > 0.92f
        drawCircle(Color.White.copy(alpha = 0.10f), 62.dp.toPx(), o)
        drawCircle(if (running) Color(0xFFFFB300) else Color.White.copy(alpha = 0.35f), 62.dp.toPx(), o, style = androidx.compose.ui.graphics.drawscope.Stroke(if (running) 4.dp.toPx() else 2.dp.toPx()))
        drawCircle(if (running) Color(0xFFFFB300).copy(alpha = 0.8f) else Color.White.copy(alpha = 0.6f), 24.dp.toPx(), ctrl.joyKnob)
      }
    }
    // marcador
    Row(Modifier.align(Alignment.TopCenter).padding(top = 8.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xAA000000)).border(1.dp, Color(0x55FFFFFF), RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
      Text(n0, color = world.kits.first.shirt.let { if ((it.red + it.green + it.blue) / 3f < 0.25f) world.kits.first.shirt2 else it }, fontWeight = FontWeight.Black, fontSize = 12.sp)
      Text(" $s0 ", color = Color.White, fontWeight = FontWeight.Black, fontSize = 26.sp)
      Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("$minute'", color = Color(0xFFFFE082), fontWeight = FontWeight.Black, fontSize = 13.sp); Text("${halfUi}T", color = Color(0xFFB0BEC5), fontSize = 10.sp) }
      Text(" $s1 ", color = Color.White, fontWeight = FontWeight.Black, fontSize = 26.sp)
      Text(n1, color = world.kits.second.shirt.let { if ((it.red + it.green + it.blue) / 3f < 0.25f) world.kits.second.shirt2 else it }, fontWeight = FontWeight.Black, fontSize = 12.sp)
    }
    Box(Modifier.align(Alignment.TopEnd).padding(10.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x77000000)).clickable { onMenu() }.padding(horizontal = 12.dp, vertical = 6.dp)) { Text("☰", color = Color.White) }
    if (banner.isNotEmpty() && phaseUi != PH_PLAY) Text(banner, color = if (phaseUi == PH_GOAL) Color(0xFFFFD740) else Color.White, fontWeight = FontWeight.Black, fontSize = if (phaseUi == PH_GOAL) 38.sp else 20.sp, textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.Center).padding(bottom = 60.dp).clip(RoundedCornerShape(14.dp)).background(Color(0x88000000)).padding(horizontal = 18.dp, vertical = 8.dp))
    if (!spectator && phaseUi != PH_OVER) {
      Box(Modifier.align(Alignment.BottomStart).padding(start = 30.dp, bottom = 10.dp).width(130.dp).height(8.dp).clip(RoundedCornerShape(5.dp)).background(Color(0x88000000))) { Box(Modifier.fillMaxSize(stamina.coerceIn(0f, 1f)).background(if (stamina > 0.3f) Color(0xFF69F0AE) else Color(0xFFFF5252))) }
      // TIRO: toque = tiro rápido · mantener = más potencia
      Box(
        Modifier.align(Alignment.BottomEnd).padding(end = 34.dp, bottom = 26.dp).size(112.dp).clip(CircleShape).background(Color(0xCCFF6D00)).border(3.dp, Color.White.copy(alpha = 0.9f), CircleShape).pointerInput(Unit) {
          detectTapGestures(onPress = { ctrl.charging = true; ctrl.chargeStart = SystemClock.elapsedRealtime(); tryAwaitRelease(); ctrl.shotPower = max(0.2f, ctrl.charge()); ctrl.charging = false; ctrl.shotSeq++ })
        }, contentAlignment = Alignment.Center
      ) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("TIRO", color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp); if (ctrl.charging) Text("${(chargeUi * 100).toInt()}%", color = Color(0xFFFFE082), fontSize = 12.sp, fontWeight = FontWeight.Bold) } }
      // PASE: toque = pase al compañero hacia donde apuntas · mantener = más fuerte (al máximo sale por alto)
      Box(
        Modifier.align(Alignment.BottomEnd).padding(end = 166.dp, bottom = 32.dp).size(88.dp).clip(CircleShape).background(Color(0xCC1E88E5)).border(3.dp, Color.White.copy(alpha = 0.9f), CircleShape).pointerInput(Unit) {
          detectTapGestures(onPress = { ctrl.passCharging = true; ctrl.passStart = SystemClock.elapsedRealtime(); tryAwaitRelease(); ctrl.passPower = max(0.15f, ctrl.passCharge()); ctrl.passCharging = false; ctrl.passSeq++ })
        }, contentAlignment = Alignment.Center
      ) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("PASE", color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp); if (ctrl.passCharging) Text("${(chargeUi * 100).toInt()}%", color = Color(0xFFFFE082), fontSize = 11.sp, fontWeight = FontWeight.Bold) } }
      // ROBAR: mantener apretado = corres a quien tiene la pelota y se la quitas directo · toque = barrida
      Box(
        Modifier.align(Alignment.BottomEnd).padding(end = 46.dp, bottom = 156.dp).size(84.dp).clip(CircleShape).background(if (holdUi) Color(0xFFE53935) else Color(0xCC2E7D32)).border(3.dp, Color.White.copy(alpha = 0.9f), CircleShape).pointerInput(Unit) {
          detectTapGestures(onPress = { val t0 = SystemClock.elapsedRealtime(); ctrl.hold = true; holdUi = true; tryAwaitRelease(); ctrl.hold = false; holdUi = false; if (SystemClock.elapsedRealtime() - t0 < 220L) ctrl.tackleSeq++ })
        }, contentAlignment = Alignment.Center
      ) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("🦵", fontSize = 22.sp); Text("ROBAR", color = Color.White, fontWeight = FontWeight.Black, fontSize = 11.sp) } }
      // CAMBIO de jugador
      Box(Modifier.align(Alignment.BottomEnd).padding(end = 150.dp, bottom = 138.dp).size(52.dp).clip(CircleShape).background(Color(0xAA000000)).border(2.dp, Color.White.copy(alpha = 0.8f), CircleShape).pointerInput(Unit) { detectTapGestures(onPress = { ctrl.switchSeq++; tryAwaitRelease() }) }, contentAlignment = Alignment.Center) { Text("🔁", fontSize = 20.sp) }
    }
    if (spectator && !(role == "client" && !gotSnap)) Text("👀 Mirando el partido", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x88000000)).padding(horizontal = 12.dp, vertical = 6.dp))
    if (role == "client" && !gotSnap) Box(Modifier.fillMaxSize().background(Color(0xCC000000)), contentAlignment = Alignment.Center) { Text("Conectando con el partido…", color = Color.White, fontWeight = FontWeight.Bold) }
    if (phaseUi == PH_OVER) {
      val my = meNow?.team ?: 0
      val title = if (overResult == -1) "🤝 Empate" else if (spectator) "Fin del partido" else if (overResult == my) "🏆 ¡VICTORIA!" else "Derrota"
      Box(Modifier.fillMaxSize().background(Color(0xCC000000)), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(24.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xF2101830)).border(2.dp, Color(0xFF69F0AE), RoundedCornerShape(20.dp)).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(title, color = Color(0xFFFFD740), fontWeight = FontWeight.Black, fontSize = 26.sp)
          Text("$n0  $s0  —  $s1  $n1", color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp)
          if (role != "client") Box(Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF00C853)).clickable { onRestart() }, contentAlignment = Alignment.Center) { Text("REVANCHA", color = Color(0xFF00281A), fontWeight = FontWeight.Black) }
          else Text("Esperando la revancha…", color = Color(0xFFB0BEC5), fontSize = 12.sp)
          Box(Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF263238)).clickable { onMenu() }, contentAlignment = Alignment.Center) { Text("Menú", color = Color.White, fontWeight = FontWeight.Bold) }
        }
      }
    }
  }
}

@Composable
private fun RoundBtn(label: String, dp: Int, onTap: () -> Unit) {
  Box(Modifier.size(dp.dp).clip(CircleShape).background(Color(0xAA000000)).border(2.dp, Color.White.copy(alpha = 0.8f), CircleShape).pointerInput(Unit) { detectTapGestures(onPress = { onTap(); tryAwaitRelease() }) }, contentAlignment = Alignment.Center) { Text(label, fontSize = 20.sp) }
}
