package com.example.snake

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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.screens.GameCtx
import com.example.ui.screens.gameAvail
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

private class SnakeSettings(private val prefs: android.content.SharedPreferences, defaultNick: String) {
  var nick by mutableStateOf(prefs.getString("nick", null) ?: defaultNick)
  var color by mutableIntStateOf(prefs.getInt("color", 0))
  var bots by mutableIntStateOf(prefs.getInt("bots", 3))
  var best by mutableIntStateOf(prefs.getInt("best", 0))
  fun save() { prefs.edit().putString("nick", nick).putInt("color", color).putInt("bots", bots).putInt("best", best).apply() }
}

private object SnakeSfx {
  private var tg: ToneGenerator? = null
  private fun g(): ToneGenerator? { if (tg == null) tg = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 70) }.getOrNull(); return tg }
  fun eat() { g()?.startTone(ToneGenerator.TONE_PROP_BEEP, 50) }
  fun die() { g()?.startTone(ToneGenerator.TONE_SUP_ERROR, 260) }
  fun shoot() { g()?.startTone(ToneGenerator.TONE_PROP_ACK, 70) }
  fun release() { runCatching { tg?.release() }; tg = null }
}

private class SParticle(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val max: Float, val size: Float, val color: Color)
private class SnakeFx { val parts = ArrayList<SParticle>(); var shake = 0f }

/** Control táctil: joystick flotante (aparece donde apoyas el dedo), turbo y disparo. */
private class SCtrl {
  var ang = 0f; var has = false
  var boost = false; var fire = 0
  var joyActive = false; var ox = 0f; var oy = 0f; var kx = 0f; var ky = 0f
}

/**
 * VIBORITAS NEÓN — el Snake de siempre en versión nueva: siete modos, bots, multijugador en línea y un modo shooter.
 * Controles: joystick flotante (apoya el dedo en cualquier parte y arrastra), botón de turbo y, en el shooter, botón de disparo.
 */
@Composable
internal fun SnakeGame(c: GameCtx) {
  val ctx = LocalContext.current.applicationContext
  val st = remember { SnakeSettings(ctx.getSharedPreferences("snake_prefs", android.content.Context.MODE_PRIVATE), c.nameOf(c.me).takeIf { it != "Tú" }.orEmpty()) }
  var screen by remember { mutableStateOf("menu") }          // menu, local, host, client
  var mode by remember { mutableStateOf(SMode.CLASSIC) }
  var runId by remember { mutableIntStateOf(0) }
  val avail = (gameAvail() - 8.dp).coerceAtLeast(380.dp)
  val isHost = c.me == c.hostUid.ifBlank { c.firstUid }
  val snap by remember { c.vm.gameState("snake") }.collectAsState(initial = null)
  var lastTk by remember { mutableIntStateOf(-1) }
  var lastAt by remember { mutableLongStateOf(0L) }
  var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
  LaunchedEffect(snap) { val tk = (snap?.get("tk") as? Number)?.toInt() ?: -1; if (tk != lastTk) { lastTk = tk; lastAt = System.currentTimeMillis() } }
  LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }
  val live = snap != null && snap?.get("s") != null && snap?.get("ov") != true && now - lastAt < 4000L
  val liveMode = SMode.byId((snap?.get("mode") as? String).orEmpty())

  Box(Modifier.fillMaxWidth().height(avail).clip(RoundedCornerShape(16.dp))) {
    if (screen == "menu") {
      SnakeMenu(st, mode, { mode = it }, c.players.size, isHost, live, liveMode) { s -> st.save(); if (s == "client") mode = liveMode; runId++; screen = s }
    } else {
      androidx.compose.runtime.key(runId) {
        SnakeArena(screen, mode, st, c, onRestart = { runId++ }, onMenu = { screen = "menu" })
      }
    }
  }
}

@Composable
private fun SnakeMenu(st: SnakeSettings, mode: SMode, onMode: (SMode) -> Unit, humans: Int, isHost: Boolean, live: Boolean, liveMode: SMode, onStart: (String) -> Unit) {
  Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF070A1A), Color(0xFF161033), Color(0xFF0A1F2E))))) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Text("🐍 VIBORITAS NEÓN", fontSize = 26.sp, fontWeight = FontWeight.Black, color = Color(0xFF00E676), textAlign = TextAlign.Center)
      Text("Víboras reales: movimiento fluido, joystick y turbo", color = Color(0xFFB0BEC5), fontSize = 12.sp)
      OutlinedTextField(
        st.nick, { st.nick = it.take(14) }, singleLine = true, label = { Text("Apodo") }, modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedLabelColor = Color(0xFF00E676), unfocusedLabelColor = Color(0xFF90A4AE), focusedBorderColor = Color(0xFF00E676))
      )
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        SN_COLORS.forEachIndexed { i, col ->
          Box(Modifier.size(34.dp).clip(CircleShape).background(Color(col)).border(if (i == st.color) 3.dp else 1.dp, if (i == st.color) Color.White else Color(0x55FFFFFF), CircleShape).clickable { st.color = i })
        }
      }
      for (m in SMode.values()) {
        val on = m == mode
        Column(
          Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(if (on) Color(0xFF123B2E) else Color(0xFF14172B)).border(if (on) 2.dp else 1.dp, if (on) Color(0xFF00E676) else Color(0x33FFFFFF), RoundedCornerShape(16.dp))
            .clickable { onMode(m) }.padding(12.dp)
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(m.emoji, fontSize = 26.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
              Text(m.title, color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
              Text(m.desc, color = Color(0xFFB0BEC5), fontSize = 11.sp)
            }
            Text(if (m.multi) "👥" else "1P", color = Color(0xFF80CBC4), fontSize = 12.sp, fontWeight = FontWeight.Bold)
          }
        }
      }
      if (mode.multi) {
        Text("Bots: ${st.bots}", color = Color(0xFFB0BEC5), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          for (n in listOf(0, 1, 2, 3, 5)) {
            val on = st.bots == n
            Box(Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(10.dp)).background(if (on) Color(0xFF00E676) else Color(0xFF1E2340)).clickable { st.bots = n }, contentAlignment = Alignment.Center) {
              Text("$n", color = if (on) Color(0xFF00281A) else Color.White, fontWeight = FontWeight.Black)
            }
          }
        }
      }
      if (st.best > 0) Text("🏆 Récord en Clásico: ${st.best}", color = Color(0xFFFFD740), fontWeight = FontWeight.Bold, fontSize = 13.sp)
      Box(Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF00E676)).clickable { onStart("local") }, contentAlignment = Alignment.Center) {
        Text(if (mode.multi && st.bots == 0) "JUGAR (solo tú)" else "JUGAR ${mode.title.uppercase()}", color = Color(0xFF00281A), fontWeight = FontWeight.Black, fontSize = 16.sp)
      }
      if (mode.multi && humans > 1) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF102A44)).border(1.dp, Color(0x5540C4FF), RoundedCornerShape(14.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text("🌐 Multijugador · $humans jugadores en la mesa", color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp)
          if (isHost) {
            Box(Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF40C4FF)).clickable { onStart("host") }, contentAlignment = Alignment.Center) {
              Text("CREAR PARTIDA · ${mode.title}", color = Color(0xFF00243A), fontWeight = FontWeight.Black)
            }
          } else {
            Text(if (live) "¡Hay una partida de ${liveMode.title} abierta!" else "Esperando a que el anfitrión cree la partida…", color = Color(0xFFB0BEC5), fontSize = 12.sp)
            Box(Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(12.dp)).background(if (live) Color(0xFF40C4FF) else Color(0xFF546E7A)).clickable(enabled = live) { onStart("client") }, contentAlignment = Alignment.Center) {
              Text("UNIRSE A LA PARTIDA", color = Color(0xFF00243A), fontWeight = FontWeight.Black)
            }
          }
        }
      }
      Spacer(Modifier.height(8.dp))
    }
  }
}

@Composable
private fun SnakeArena(role: String, mode: SMode, st: SnakeSettings, c: GameCtx, onRestart: () -> Unit, onMenu: () -> Unit) {
  val world = remember { SnakeWorld(mode, if (role == "client" || mode == SMode.CLASSIC) 0 else st.bots, Random.Default, role == "client") }
  val fx = remember { SnakeFx() }
  val ctrl = remember { SCtrl() }
  val cam = remember { floatArrayOf(Float.NaN, Float.NaN) }
  var frame by remember { mutableIntStateOf(0) }
  var hud by remember { mutableStateOf(listOf<Triple<String, Int, Int>>()) }
  var myScore by remember { mutableIntStateOf(0) }; var myLen by remember { mutableIntStateOf(0) }; var myAmmo by remember { mutableIntStateOf(0) }
  var timeLeft by remember { mutableIntStateOf(0) }
  var iAmAlive by remember { mutableStateOf(true) }
  var overUi by remember { mutableStateOf(false) }
  var hint by remember { mutableStateOf(true) }
  var alphaHolder by remember { mutableStateOf(0f) }
  val guests = remember { if (role == "host") c.players.filter { it != c.me } else emptyList() }
  val inputs = remember { mutableStateMapOf<String, Map<String, Any?>>() }

  LaunchedEffect(Unit) { delay(7000); hint = false }
  if (role == "host") for (uid in guests) LaunchedEffect(uid) { c.vm.gameState("snakeIn_$uid").collect { m -> if (m != null) inputs[uid] = m } }
  var lastSnapAt by remember { mutableLongStateOf(0L) }
  if (role == "client") LaunchedEffect(Unit) {
    c.vm.gameState("snake").collect { m -> if (m != null && m["s"] != null) { world.applySnapshot(m); lastSnapAt = SystemClock.elapsedRealtime() } }
  }
  DisposableEffect(Unit) { onDispose { SnakeSfx.release() } }

  fun me(): SSnake? = world.snakes.firstOrNull { it.uid == (if (role == "local") "me" else c.me) }

  LaunchedEffect(Unit) {
    val rnd = Random(System.nanoTime().toInt())
    if (role != "client") {
      val color = SN_COLORS[st.color % SN_COLORS.size]
      val mine = world.addSnake(st.nick.ifBlank { "Tú" }, color, false, if (role == "local") "me" else c.me)
      if (role == "host") guests.forEachIndexed { i, uid -> world.addSnake(c.nameOf(uid), SN_COLORS[(i + 3) % SN_COLORS.size], false, uid) }
      if (mine.id < 0) return@LaunchedEffect
    }
    var last = 0L; var acc = 0f; var hudT = 0f; var sendT = 0f; var seenFire = 0; var sinceSnap = 0f; var stepCount = 0
    var seenSnapAt = 0L
    val seenGuestFire = HashMap<String, Int>(); var lastSent = ""; var sentAt = 0L; var bestSaved = st.best
    while (true) {
      androidx.compose.runtime.withFrameNanos { tn ->
        val dt = if (last == 0L) 0.016f else ((tn - last) / 1e9f).coerceAtMost(0.05f); last = tn
        val m = me()
        if (role != "client") {
          if (!world.over) {
            acc += dt * 1000f
            val tm = world.tickMs().toFloat()
            var steps = 0
            while (acc >= tm && steps < 3) {
              acc -= tm; steps++
              if (m != null && m.alive) {
                if (ctrl.has) world.turnTo(m, ctrl.ang)
                m.boosting = ctrl.boost
                if (ctrl.fire != seenFire) { seenFire = ctrl.fire; m.wantFire = true }
              }
              if (role == "host") for (g in world.snakes) {
                if (g.uid.isEmpty() || g.uid == c.me || !g.alive) continue
                val inp = inputs[g.uid] ?: continue
                if (((inp["h"] as? Number)?.toInt() ?: 0) == 1) (inp["a"] as? Number)?.let { world.turnTo(g, it.toFloat() / 1000f) }
                g.boosting = ((inp["b"] as? Number)?.toInt() ?: 0) == 1
                val f = (inp["f"] as? Number)?.toInt() ?: 0
                if (f != (seenGuestFire[g.uid] ?: f)) g.wantFire = true
                seenGuestFire[g.uid] = f
              }
              world.step()
              stepCount++
              if (role == "host" && stepCount % 2 == 0) c.vm.publishGame("snake", world.encode())
            }
            alphaHolder = (acc / tm).coerceIn(0f, 1f)
          }
        } else {
          if (lastSnapAt != seenSnapAt) { seenSnapAt = lastSnapAt; sinceSnap = 0f }
          sinceSnap += dt * 1000f
          alphaHolder = (sinceSnap / 70f).coerceIn(0f, 1f)
          sendT += dt
          if (sendT >= 0.06f) {
            sendT = 0f
            val key = "${(ctrl.ang * 20f).toInt()},${ctrl.boost},${ctrl.fire},${ctrl.has}"
            val nowMs = SystemClock.elapsedRealtime()
            if (key != lastSent || nowMs - sentAt > 1000L) {
              lastSent = key; sentAt = nowMs
              c.vm.publishGame("snakeIn_${c.me}", mapOf("a" to (ctrl.ang * 1000f).toLong(), "h" to (if (ctrl.has) 1L else 0L), "b" to (if (ctrl.boost) 1L else 0L), "f" to ctrl.fire.toLong(), "nm" to st.nick.ifBlank { "Jugador" }, "co" to st.color.toLong()))
            }
          }
        }
        // eventos → partículas y sonido
        for (e in world.events) {
          val col = Color(e.color)
          fun burst(n: Int, sp: Float, sz: Float) { for (i in 0 until n) { if (fx.parts.size > 300) break; val a = rnd.nextFloat() * 2f * PI.toFloat(); val v = sp * (0.4f + rnd.nextFloat() * 0.6f); val l = 0.4f + rnd.nextFloat() * 0.4f; fx.parts.add(SParticle(e.x, e.y, cos(a) * v, sin(a) * v, l, l, sz, col)) } }
          val near = m != null && abs(e.x - m.x) + abs(e.y - m.y) < 6f
          when (e.kind) {
            0 -> { burst(8, 4f, 0.16f); if (near) SnakeSfx.eat() }
            1 -> { burst(36, 7f, 0.22f); fx.shake = maxOf(fx.shake, if (near) 0.8f else 0.2f); SnakeSfx.die() }
            2 -> { burst(4, 3f, 0.12f); SnakeSfx.shoot() }
            3 -> burst(10, 5f, 0.15f)
            4 -> burst(14, 5f, 0.18f)
          }
        }
        world.events.clear()
        var pi = fx.parts.size - 1
        while (pi >= 0) { val p = fx.parts[pi]; p.life -= dt; if (p.life <= 0f) fx.parts.removeAt(pi) else { p.x += p.vx * dt; p.y += p.vy * dt; p.vx *= 0.94f; p.vy *= 0.94f }; pi-- }
        fx.shake = maxOf(0f, fx.shake - dt * 1.8f)
        hudT += dt
        if (hudT > 0.2f) {
          hudT = 0f
          myScore = m?.score ?: 0; myLen = m?.segs ?: 0; myAmmo = m?.ammo ?: 0
          timeLeft = if (world.timed) ((world.duration - world.tick) / 30).coerceAtLeast(0) else 0
          iAmAlive = m?.alive == true
          overUi = world.over
          hud = world.snakes.filter { it.alive || it.score > 0 }.sortedByDescending { it.score + (if (it.alive) it.segs else 0) }.take(5).map { Triple(it.name, it.score, it.color) }
          if (mode == SMode.CLASSIC && role == "local" && myScore > bestSaved) { bestSaved = myScore; st.best = myScore; st.save() }
        }
        frame++
      }
    }
  }

  val cellMode = world.mode
  Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF060914), Color(0xFF120C2A))))) {
    Canvas(
      Modifier.fillMaxSize().pointerInput(Unit) {
        val maxR = 58.dp.toPx(); val dead = 10.dp.toPx()
        awaitEachGesture {
          val down = awaitFirstDown(requireUnconsumed = false)
          ctrl.joyActive = true; ctrl.ox = down.position.x; ctrl.oy = down.position.y; ctrl.kx = ctrl.ox; ctrl.ky = ctrl.oy
          hint = false
          while (true) {
            val ev = awaitPointerEvent()
            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
            if (!ch.pressed) break
            var dx = ch.position.x - ctrl.ox; var dy = ch.position.y - ctrl.oy
            val d = sqrt(dx * dx + dy * dy)
            if (d > maxR) { val k = (d - maxR) / d; ctrl.ox += dx * k; ctrl.oy += dy * k; dx = ch.position.x - ctrl.ox; dy = ch.position.y - ctrl.oy }
            ctrl.kx = ch.position.x; ctrl.ky = ch.position.y
            if (d > dead) { ctrl.ang = atan2(dy, dx); ctrl.has = true }
            ch.consume()
          }
          ctrl.joyActive = false
        }
      }
    ) {
      val tick = frame
      if (tick < 0) return@Canvas
      drawSnakeBoard(world, fx, alphaHolder, me()?.id ?: -1, ctrl, cam)
    }
    // marcador
    Column(Modifier.align(Alignment.TopStart).padding(8.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x99000000)).padding(horizontal = 10.dp, vertical = 6.dp)) {
      Text("${cellMode.emoji} ${cellMode.title}", color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp)
      Text("Puntos $myScore · Largo $myLen", color = Color(0xFF69F0AE), fontWeight = FontWeight.Bold, fontSize = 12.sp)
      if (cellMode == SMode.SHOOTER) Text("🔫 Munición: $myAmmo", color = Color(0xFFFFD740), fontWeight = FontWeight.Bold, fontSize = 12.sp)
      if (cellMode == SMode.RUSH) Text("🍎 ${me()?.apples ?: 0} / 15", color = Color(0xFFFF8A80), fontWeight = FontWeight.Bold, fontSize = 12.sp)
      if (world.timed) Text("⏱ ${timeLeft / 60}:${(timeLeft % 60).toString().padStart(2, '0')}", color = Color.White, fontSize = 12.sp)
      if (role != "local") Text(if (role == "host") "🌐 Anfitrión" else "🌐 En línea", color = Color(0xFF80D8FF), fontSize = 11.sp)
    }
    Column(Modifier.align(Alignment.TopEnd).padding(8.dp).width(118.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x99000000)).padding(8.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
      hud.forEachIndexed { i, (n, p, col) ->
        Row { Text("${i + 1}. $n", color = Color(col), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f)); Text("$p", color = Color.White, fontSize = 10.sp) }
      }
    }
    Box(Modifier.align(Alignment.TopCenter).padding(top = 6.dp)) {
      Box(Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0x77000000)).clickable { onMenu() }.padding(horizontal = 12.dp, vertical = 4.dp)) { Text("☰", color = Color.White) }
    }
    // botones grandes a la derecha: turbo (mantener) y disparo
    if (!overUi) {
      Column(Modifier.align(Alignment.BottomEnd).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (cellMode == SMode.SHOOTER) SnakeHoldBtn("🔫", "DISPARO", Color(0xFFFF1744), 92.dp) { down -> if (down) ctrl.fire++ }
        if (cellMode != SMode.CLASSIC) SnakeHoldBtn("⚡", "TURBO", Color(0xFF00B0FF), 100.dp) { down -> ctrl.boost = down }
      }
      if (hint) Text("Apoya el dedo en cualquier parte y arrastra para guiar a tu víbora", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, textAlign = TextAlign.Center,
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 140.dp, start = 24.dp, end = 24.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x99000000)).padding(horizontal = 12.dp, vertical = 8.dp))
    }
    if (!iAmAlive && !overUi && (cellMode == SMode.SURVIVAL)) {
      Text("☠️ Estás fuera · mira el final", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Center).clip(RoundedCornerShape(12.dp)).background(Color(0x99000000)).padding(12.dp))
    }
    if (overUi) {
      val rank = world.snakes.sortedByDescending { it.score + (if (it.alive) it.segs else 0) }
      Box(Modifier.fillMaxSize().background(Color(0xBB000000)), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(24.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xF2101830)).border(2.dp, Color(0xFF00E676), RoundedCornerShape(20.dp)).padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(if (cellMode == SMode.CLASSIC) "¡Chocaste!" else "🏆 ${if (world.winner.isEmpty()) "Fin de la partida" else world.winner}", color = Color(0xFFFFD740), fontWeight = FontWeight.Black, fontSize = 22.sp, textAlign = TextAlign.Center)
          rank.take(6).forEachIndexed { i, s -> Text("${i + 1}. ${s.name} — ${s.score} pts${if (cellMode == SMode.SHOOTER) " · ${s.kills} bajas" else ""}", color = Color(s.color), fontWeight = FontWeight.Bold, fontSize = 14.sp) }
          Spacer(Modifier.height(6.dp))
          if (role != "client") Box(Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF00E676)).clickable { onRestart() }, contentAlignment = Alignment.Center) {
            Text("JUGAR OTRA VEZ", color = Color(0xFF00281A), fontWeight = FontWeight.Black)
          } else Text("Esperando que el anfitrión inicie otra partida…", color = Color(0xFFB0BEC5), fontSize = 12.sp)
          Box(Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF263238)).clickable { onMenu() }, contentAlignment = Alignment.Center) { Text("Menú", color = Color.White, fontWeight = FontWeight.Bold) }
        }
      }
    }
  }
}

/** Botón grande para mantener pulsado (turbo) o para disparar. */
@Composable
private fun SnakeHoldBtn(emoji: String, label: String, col: Color, size: Dp, onHold: (Boolean) -> Unit) {
  var down by remember { mutableStateOf(false) }
  Box(
    Modifier.size(size).clip(CircleShape)
      .background(Brush.radialGradient(listOf(col.copy(alpha = if (down) 0.95f else 0.7f), col.copy(alpha = if (down) 0.7f else 0.4f))))
      .border(3.dp, Color.White.copy(alpha = if (down) 1f else 0.75f), CircleShape)
      .pointerInput(Unit) { detectTapGestures(onPress = { down = true; onHold(true); tryAwaitRelease(); down = false; onHold(false) }) },
    contentAlignment = Alignment.Center
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Text(emoji, fontSize = (size.value * 0.34f).sp)
      Text(label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
    }
  }
}

private fun shadeC(c: Color, k: Float) = Color((c.red * k).coerceIn(0f, 1f), (c.green * k).coerceIn(0f, 1f), (c.blue * k).coerceIn(0f, 1f), 1f)

/** Punto [i] del cuerpo de la víbora interpolado entre el paso anterior y el actual. */
private fun snakePt(s: SSnake, i: Int, a: Float): Offset {
  val cx = s.cur[i * 2]; val cy = s.cur[i * 2 + 1]
  if (i * 2 + 1 < s.prev.size) {
    val px = s.prev[i * 2]; val py = s.prev[i * 2 + 1]
    if (abs(cx - px) < 3f && abs(cy - py) < 3f) return Offset(px + (cx - px) * a, py + (cy - py) * a)
  }
  return Offset(cx, cy)
}

private fun DrawScope.drawSnakeBoard(world: SnakeWorld, fx: SnakeFx, alpha: Float, myId: Int, ctrl: SCtrl, cam: FloatArray) {
  val W = world.W; val H = world.H
  val t = (SystemClock.uptimeMillis() % 100000L) / 1000f
  val viewW = min(W, 26f)
  val sc = size.width / viewW
  val viewH = size.height / sc
  // cámara: sigue a tu víbora con suavidad
  val me = world.snakes.firstOrNull { it.id == myId && it.alive && it.cur.size >= 2 }
  var tx = if (cam[0].isNaN()) W / 2f else cam[0]; var ty = if (cam[1].isNaN()) H / 2f else cam[1]
  if (me != null) {
    val hp = snakePt(me, 0, alpha)
    val hx = world.wv(hp.x, W); val hy = world.wv(hp.y, H)
    if (cam[0].isNaN() || abs(hx - tx) > viewW * 0.6f || abs(hy - ty) > viewH * 0.6f) { tx = hx; ty = hy } else { tx += (hx - tx) * 0.18f; ty += (hy - ty) * 0.18f }
  }
  cam[0] = tx; cam[1] = ty
  val shx = (Random.nextFloat() - 0.5f) * fx.shake * 0.7f; val shy = (Random.nextFloat() - 0.5f) * fx.shake * 0.7f
  val cx = (if (W <= viewW) W / 2f else tx.coerceIn(viewW / 2f, W - viewW / 2f)) + shx
  val cy = (if (H <= viewH) H / 2f else ty.coerceIn(viewH / 2f, H - viewH / 2f)) + shy
  drawRect(Brush.verticalGradient(listOf(Color(0xFF04060F), Color(0xFF0C0824))), Offset.Zero, size)
  withTransform({ translate(size.width / 2f - cx * sc, size.height / 2f - cy * sc); scale(sc, sc, Offset.Zero) }) {
    // suelo: arena oscura con puntos de neón y un círculo decorativo
    drawRect(Color(0xFF0A0E26), Offset.Zero, Size(W, H))
    drawRect(Brush.radialGradient(listOf(Color(0x2200B0FF), Color.Transparent), Offset(W / 2f, H / 2f), max(W, H) * 0.6f), Offset.Zero, Size(W, H))
    drawCircle(Color(0x0DFFFFFF), W * 0.22f, Offset(W / 2f, H / 2f), style = Stroke(0.12f))
    val gx0 = (((cx - viewW / 2f) / 2f).toInt() - 1).coerceAtLeast(0); val gx1 = (((cx + viewW / 2f) / 2f).toInt() + 1).coerceAtMost((W / 2f).toInt())
    val gy0 = (((cy - viewH / 2f) / 2f).toInt() - 1).coerceAtLeast(0); val gy1 = (((cy + viewH / 2f) / 2f).toInt() + 1).coerceAtMost((H / 2f).toInt())
    for (gx in gx0..gx1) for (gy in gy0..gy1) drawCircle(Color(0x1FFFFFFF), 0.07f, Offset(gx * 2f, gy * 2f))
    // zona de supervivencia
    if (world.mode == SMode.SURVIVAL) {
      val z = world.zone
      val red = Color(0xFFFF1744).copy(alpha = 0.22f + 0.08f * sin(t * 4f))
      drawRect(red, Offset.Zero, Size(W, z[1]))
      drawRect(red, Offset(0f, z[3]), Size(W, H - z[3]))
      drawRect(red, Offset(0f, z[1]), Size(z[0], z[3] - z[1]))
      drawRect(red, Offset(z[2], z[1]), Size(W - z[2], z[3] - z[1]))
      drawRect(Color(0xFFFF5252), Offset(z[0], z[1]), Size(z[2] - z[0], z[3] - z[1]), style = Stroke(0.16f))
    }
    // borde con neón (rosa si hay paredes mortales, cian si son portales)
    val edge = if (world.mode.walls) Color(0xFFFF4081) else Color(0xFF40C4FF)
    drawRect(edge.copy(alpha = 0.16f), Offset.Zero, Size(W, H), style = Stroke(0.8f))
    drawRect(edge, Offset.Zero, Size(W, H), style = Stroke(0.14f))
    // comida y munición
    for (p in world.pickups) {
      if (abs(p.x - cx) > viewW / 2f + 1f || abs(p.y - cy) > viewH / 2f + 1f) continue
      val c0 = Offset(p.x, p.y)
      val pulse = 1f + 0.12f * sin(t * 5f + p.x * 3f + p.y)
      when (p.kind) {
        0 -> {
          drawCircle(Color(0xFFFF1744).copy(alpha = 0.25f), 0.78f * pulse, c0)
          drawCircle(Brush.radialGradient(listOf(Color(0xFFFF8A80), Color(0xFFFF1744), Color(0xFFB71C1C)), Offset(c0.x - 0.1f, c0.y - 0.12f), 0.5f), 0.36f * pulse, c0)
          drawLine(Color(0xFF69F0AE), Offset(c0.x, c0.y - 0.3f), Offset(c0.x + 0.14f, c0.y - 0.46f), 0.08f, StrokeCap.Round)
        }
        1 -> {
          drawCircle(Color(0xFFFFD740).copy(alpha = 0.3f), 0.8f * pulse, c0)
          drawCircle(Color(0xFF1B1400), 0.38f, c0)
          drawCircle(Color(0xFFFFD740), 0.38f, c0, style = Stroke(0.07f))
          drawLine(Color(0xFFFFD740), Offset(c0.x - 0.08f, c0.y + 0.18f), Offset(c0.x - 0.08f, c0.y - 0.12f), 0.09f, StrokeCap.Round)
          drawLine(Color(0xFFFFD740), Offset(c0.x + 0.08f, c0.y + 0.18f), Offset(c0.x + 0.08f, c0.y - 0.12f), 0.09f, StrokeCap.Round)
        }
        else -> {
          drawCircle(Color(0xFFFFEA00).copy(alpha = 0.3f), 0.95f * pulse, c0)
          drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFFFFEA00), Color(0xFFFF8F00)), c0, 0.5f), 0.44f * pulse, c0)
        }
      }
    }
    // víboras: las demás primero y la tuya encima
    val order = world.snakes.filter { it.alive && it.cur.size >= 4 }.sortedBy { if (it.id == myId) 1 else 0 }
    for (s in order) {
      var sx = 0f; var sy = 0f
      if (!world.mode.walls) { val hp = snakePt(s, 0, alpha); sx = world.wv(hp.x, W) - hp.x; sy = world.wv(hp.y, H) - hp.y }
      drawSnake(s, alpha, sx, sy, t, s.id == myId, world)
      if (!world.mode.walls) {
        // el cuerpo atraviesa el portal: se dibuja también al otro lado
        val tail = snakePt(s, s.cur.size / 2 - 1, alpha)
        val tx0 = tail.x + sx; val ty0 = tail.y + sy
        val hx0 = snakePt(s, 0, alpha).x + sx; val hy0 = snakePt(s, 0, alpha).y + sy
        val ox = if (min(tx0, hx0) < 0f) W else if (max(tx0, hx0) > W) -W else 0f
        val oy = if (min(ty0, hy0) < 0f) H else if (max(ty0, hy0) > H) -H else 0f
        if (ox != 0f) drawSnake(s, alpha, sx + ox, sy, t, s.id == myId, world)
        if (oy != 0f) drawSnake(s, alpha, sx, sy + oy, t, s.id == myId, world)
        if (ox != 0f && oy != 0f) drawSnake(s, alpha, sx + ox, sy + oy, t, s.id == myId, world)
      }
    }
    // rumbo que marca tu dedo
    if (me != null && ctrl.has) {
      val hp = snakePt(me, 0, alpha)
      val hx = hp.x + (if (!world.mode.walls) world.wv(hp.x, W) - hp.x else 0f); val hy = hp.y + (if (!world.mode.walls) world.wv(hp.y, H) - hp.y else 0f)
      drawLine(Color.White.copy(alpha = 0.22f), Offset(hx + cos(ctrl.ang) * 1.3f, hy + sin(ctrl.ang) * 1.3f), Offset(hx + cos(ctrl.ang) * 3.2f, hy + sin(ctrl.ang) * 3.2f), 0.12f, StrokeCap.Round)
    }
    // balas
    for (b in world.bullets) {
      val dx = cos(b.ang); val dy = sin(b.ang)
      val head = Offset(b.x, b.y); val tail = Offset(b.x - dx * 1.7f, b.y - dy * 1.7f)
      drawLine(Color(0xFFFF9100).copy(alpha = 0.4f), tail, head, 0.5f, StrokeCap.Round)
      drawLine(Color(0xFFFFEA00), tail, head, 0.22f, StrokeCap.Round)
      drawCircle(Color.White, 0.16f, head)
    }
    // partículas
    for (p in fx.parts) {
      val a = (p.life / p.max).coerceIn(0f, 1f)
      drawCircle(p.color.copy(alpha = a), p.size * (0.4f + a) + 0.02f, Offset(p.x, p.y))
    }
  }
  // joystick flotante
  val R = 58.dp.toPx()
  if (ctrl.joyActive) {
    val o = Offset(ctrl.ox, ctrl.oy)
    drawCircle(Color(0x22FFFFFF), R, o); drawCircle(Color(0x77FFFFFF), R, o, style = Stroke(3f))
    var dx = ctrl.kx - ctrl.ox; var dy = ctrl.ky - ctrl.oy
    val d = sqrt(dx * dx + dy * dy)
    if (d > R) { dx = dx / d * R; dy = dy / d * R }
    drawCircle(Color(0xAAFFFFFF), R * 0.42f, Offset(ctrl.ox + dx, ctrl.oy + dy))
  } else {
    val o = Offset(size.width * 0.22f, size.height * 0.8f)
    drawCircle(Color(0x14FFFFFF), R, o); drawCircle(Color(0x33FFFFFF), R, o, style = Stroke(2f)); drawCircle(Color(0x33FFFFFF), R * 0.42f, o)
  }
  drawRect(Brush.radialGradient(listOf(Color.Transparent, Color(0x88000000)), Offset(size.width / 2f, size.height / 2f), max(size.width, size.height) * 0.75f))
}

/** Dibuja una víbora: cuerpo en forma de tubo que se afina hacia la cola, escamas, cabeza con ojos y lengua bífida. */
private fun DrawScope.drawSnake(s: SSnake, a: Float, sx: Float, sy: Float, t: Float, isMe: Boolean, world: SnakeWorld) {
  val n = s.cur.size / 2
  if (n < 2) return
  val xs = FloatArray(n); val ys = FloatArray(n)
  for (i in 0 until n) { val p = snakePt(s, i, a); xs[i] = p.x + sx; ys[i] = p.y + sy }
  val col = Color(s.color)
  val r = 0.46f * world.thick(s)
  fun tw(i: Int) = 0.38f + 0.62f * min(1f, (n - i).toFloat() / (n * 0.4f))
  // resplandor
  var i = n - 1
  while (i >= 1) {
    val p0 = Offset(xs[i], ys[i]); val p1 = Offset(xs[i - 1], ys[i - 1])
    if ((p0 - p1).getDistance() < 2f) drawLine(col.copy(alpha = if (s.fast) 0.30f else 0.16f), p0, p1, r * 2f * tw(i) * 1.9f, StrokeCap.Round)
    i -= 2
  }
  // cuerpo con bandas
  i = n - 1
  while (i >= 1) {
    val p0 = Offset(xs[i], ys[i]); val p1 = Offset(xs[i - 1], ys[i - 1])
    if ((p0 - p1).getDistance() < 2f) {
      val band = (i / 3) % 2 == 0
      drawLine(shadeC(col, if (band) 0.92f else 0.66f), p0, p1, r * 2f * tw(i), StrokeCap.Round)
    }
    i--
  }
  // brillo del lomo y dibujo de escamas
  i = n - 1
  while (i >= 1) {
    val w = r * 2f * tw(i)
    val p0 = Offset(xs[i], ys[i])
    if (i % 2 == 0) drawCircle(Color.White.copy(alpha = 0.17f), w * 0.2f, Offset(p0.x - w * 0.12f, p0.y - w * 0.16f))
    if (i % 4 == 2) drawCircle(shadeC(col, 0.4f).copy(alpha = 0.55f), w * 0.26f, p0)
    i--
  }
  if (s.fast) for (k in 0 until min(n, 8)) drawCircle(Color.White.copy(alpha = 0.10f), r * 1.7f, Offset(xs[k], ys[k]))
  // cabeza
  val h = Offset(xs[0], ys[0]); val nk = Offset(xs[1], ys[1])
  var fx0 = h.x - nk.x; var fy0 = h.y - nk.y
  val fl = sqrt(fx0 * fx0 + fy0 * fy0)
  if (fl < 1e-4f) { fx0 = cos(s.ang); fy0 = sin(s.ang) } else { fx0 /= fl; fy0 /= fl }
  val sx0 = -fy0; val sy0 = fx0
  val hr = r * 1.18f
  drawCircle(col.copy(alpha = 0.28f), hr * 1.9f, h)
  drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.8f), col, shadeC(col, 0.55f)), Offset(h.x - hr * 0.25f, h.y - hr * 0.3f), hr * 1.5f), hr, h)
  val snout = Offset(h.x + fx0 * hr * 0.55f, h.y + fy0 * hr * 0.55f)
  drawCircle(shadeC(col, 0.95f), hr * 0.78f, snout)
  for (sg in listOf(-1f, 1f)) drawCircle(Color(0x88000000), hr * 0.07f, Offset(snout.x + fx0 * hr * 0.5f + sx0 * sg * hr * 0.2f, snout.y + fy0 * hr * 0.5f + sy0 * sg * hr * 0.2f))
  for (sg in listOf(-1f, 1f)) {
    val e = Offset(h.x + fx0 * hr * 0.2f + sx0 * sg * hr * 0.56f, h.y + fy0 * hr * 0.2f + sy0 * sg * hr * 0.56f)
    drawCircle(Color(0xFFFFF59D), hr * 0.30f, e)
    drawCircle(Color(0xFF0A0A0A), hr * 0.16f, Offset(e.x + fx0 * hr * 0.07f, e.y + fy0 * hr * 0.07f))
    drawCircle(Color.White, hr * 0.07f, Offset(e.x - hr * 0.06f, e.y - hr * 0.08f))
  }
  // lengua bífida que sale y entra
  if (sin(t * 7f + s.id * 1.7f) > 0.35f) {
    val base = Offset(h.x + fx0 * hr * 1.3f, h.y + fy0 * hr * 1.3f); val mid = Offset(h.x + fx0 * hr * 1.9f, h.y + fy0 * hr * 1.9f)
    drawLine(Color(0xFFFF1744), base, mid, hr * 0.09f, StrokeCap.Round)
    for (sg in listOf(-1f, 1f)) drawLine(Color(0xFFFF1744), mid, Offset(h.x + fx0 * hr * 2.4f + sx0 * sg * hr * 0.28f, h.y + fy0 * hr * 2.4f + sy0 * sg * hr * 0.28f), hr * 0.07f, StrokeCap.Round)
  }
  if (isMe) drawCircle(Color.White.copy(alpha = 0.5f + 0.2f * sin(t * 4f)), hr * 1.55f, h, style = Stroke(0.07f))
}
