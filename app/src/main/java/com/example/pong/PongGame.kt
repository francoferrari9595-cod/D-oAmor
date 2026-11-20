package com.example.pong

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.screens.GameCtx
import com.example.ui.screens.gameAvail
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

private val LEVELS = listOf("Principiante", "Aficionado", "Profesional", "Campeón")

private class PongPrefs(private val prefs: android.content.SharedPreferences) {
  var level by mutableIntStateOf(prefs.getInt("level", 1))
  var target by mutableIntStateOf(prefs.getInt("target", 11))
  var wins by mutableIntStateOf(prefs.getInt("wins", 0))
  var losses by mutableIntStateOf(prefs.getInt("losses", 0))
  fun save() { prefs.edit().putInt("level", level).putInt("target", target).putInt("wins", wins).putInt("losses", losses).apply() }
}

private object PongSfx {
  private var tg: ToneGenerator? = null
  private fun g(): ToneGenerator? { if (tg == null) tg = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 75) }.getOrNull(); return tg }
  fun bounce() { g()?.startTone(ToneGenerator.TONE_PROP_BEEP, 35) }
  fun hit() { g()?.startTone(ToneGenerator.TONE_PROP_ACK, 55) }
  fun point() { g()?.startTone(ToneGenerator.TONE_PROP_BEEP2, 220) }
  fun release() { runCatching { tg?.release() }; tg = null }
}

private class PPart(var x: Float, var y: Float, var z: Float, var vx: Float, var vy: Float, var vz: Float, var life: Float, val max: Float, val size: Float, val color: Color)
private class PRing(val x: Float, val z: Float, var age: Float)
private class PongFx {
  val parts = ArrayList<PPart>(); val rings = ArrayList<PRing>()
  val trail = ArrayList<FloatArray>()
  var shake = 0f; var flash = 0f; var popT = 0f; var popText = ""; var popColor = Color.White
}
private class Touch { var active = false; var x = 0f; var y = 0f; var tap = 0 }

/**
 * PING PONG 3D PRO — mesa de medidas reales con física completa (efecto, topspin, backspin), IA de 4 niveles y duelo en línea.
 * Controles: arrastra el dedo por la zona inferior para mover la pala; mueve rápido para golpear más fuerte y hacia arriba para dar efecto.
 * Toca para sacar.
 */
@Composable
internal fun PongGame(c: GameCtx) {
  val ctx = LocalContext.current.applicationContext
  val pp = remember { PongPrefs(ctx.getSharedPreferences("pong_prefs", android.content.Context.MODE_PRIVATE)) }
  var screen by remember { mutableStateOf("menu") }          // menu, ai, host, client, watch
  var opp by remember { mutableStateOf("") }
  var runId by remember { mutableIntStateOf(0) }
  val avail = (gameAvail() - 8.dp).coerceAtLeast(380.dp)
  val isHost = c.me == c.hostUid.ifBlank { c.firstUid }
  val snap by remember { c.vm.gameState("pong") }.collectAsState(initial = null)
  var lastHb by remember { mutableLongStateOf(-1L) }
  var lastAt by remember { mutableLongStateOf(0L) }
  var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
  LaunchedEffect(snap) { val hb = (snap?.get("hb") as? Number)?.toLong() ?: -1L; if (hb != lastHb) { lastHb = hb; lastAt = System.currentTimeMillis() } }
  LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }
  val live = snap != null && snap?.get("b") != null && now - lastAt < 4000L
  val liveOpp = (snap?.get("opp") as? String).orEmpty()

  Box(Modifier.fillMaxWidth().height(avail).clip(RoundedCornerShape(16.dp))) {
    if (screen == "menu") {
      PongMenu(pp, c, isHost, live, liveOpp, opp, { opp = it }) { s -> pp.save(); runId++; screen = s }
    } else {
      androidx.compose.runtime.key(runId) {
        PongArena(screen, pp, c, if (screen == "host") opp else liveOpp, onRestart = { runId++ }, onMenu = { screen = "menu" })
      }
    }
  }
}

@Composable
private fun PongMenu(pp: PongPrefs, c: GameCtx, isHost: Boolean, live: Boolean, liveOpp: String, opp: String, onOpp: (String) -> Unit, onStart: (String) -> Unit) {
  val others = c.players.filter { it != c.me }
  LaunchedEffect(others.size) { if (opp.isEmpty() && others.isNotEmpty()) onOpp(others.first()) }
  Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF05070F), Color(0xFF12103A), Color(0xFF0A2A44))))) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Text("🏓 PING PONG 3D PRO", fontSize = 26.sp, fontWeight = FontWeight.Black, color = Color(0xFFFFD740), textAlign = TextAlign.Center)
      Text("Mesa reglamentaria · efecto · saque oficial", color = Color(0xFFB0BEC5), fontSize = 12.sp)
      Spacer(Modifier.height(4.dp))
      Text("Nivel de la IA", color = Color(0xFF80D8FF), fontWeight = FontWeight.Bold, fontSize = 12.sp)
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        LEVELS.forEachIndexed { i, n ->
          val on = pp.level == i
          Box(Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(10.dp)).background(if (on) Color(0xFF00E5FF) else Color(0xFF1B2140)).clickable { pp.level = i }, contentAlignment = Alignment.Center) {
            Text(n, color = if (on) Color(0xFF00232B) else Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp, textAlign = TextAlign.Center)
          }
        }
      }
      Text("Puntos por partido", color = Color(0xFF80D8FF), fontWeight = FontWeight.Bold, fontSize = 12.sp)
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (n in listOf(7, 11, 21)) {
          val on = pp.target == n
          Box(Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(10.dp)).background(if (on) Color(0xFFFFD740) else Color(0xFF1B2140)).clickable { pp.target = n }, contentAlignment = Alignment.Center) {
            Text("$n", color = if (on) Color(0xFF2B2000) else Color.White, fontWeight = FontWeight.Black)
          }
        }
      }
      Box(Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF00E676)).clickable { onStart("ai") }, contentAlignment = Alignment.Center) {
        Text("JUGAR CONTRA LA IA", color = Color(0xFF00281A), fontWeight = FontWeight.Black, fontSize = 16.sp)
      }
      if (c.players.size > 1) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF102A44)).border(1.dp, Color(0x5540C4FF), RoundedCornerShape(14.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("🌐 Duelo en línea", color = Color.White, fontWeight = FontWeight.Black)
          if (isHost) {
            Text("Elige a tu rival (los demás pueden mirar)", color = Color(0xFFB0BEC5), fontSize = 12.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              for (u in others) {
                val on = u == opp
                Box(Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(10.dp)).background(if (on) Color(0xFF40C4FF) else Color(0xFF1B2140)).clickable { onOpp(u) }, contentAlignment = Alignment.Center) {
                  Text(c.nameOf(u), color = if (on) Color(0xFF00243A) else Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                }
              }
            }
            Box(Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF40C4FF)).clickable(enabled = opp.isNotEmpty()) { onStart("host") }, contentAlignment = Alignment.Center) {
              Text("RETAR A ${c.nameOf(opp).uppercase()}", color = Color(0xFF00243A), fontWeight = FontWeight.Black)
            }
          } else {
            val mine = live && liveOpp == c.me
            Text(if (!live) "Esperando a que el anfitrión cree un duelo…" else if (mine) "¡Te retaron a un duelo!" else "Hay un duelo en curso: puedes mirarlo", color = Color(0xFFB0BEC5), fontSize = 12.sp)
            Box(Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(12.dp)).background(if (live) Color(0xFF40C4FF) else Color(0xFF546E7A)).clickable(enabled = live) { onStart(if (mine) "client" else "watch") }, contentAlignment = Alignment.Center) {
              Text(if (mine) "ACEPTAR EL DUELO" else "MIRAR LA PARTIDA", color = Color(0xFF00243A), fontWeight = FontWeight.Black)
            }
          }
        }
      }
      if (pp.wins + pp.losses > 0) Text("Contra la IA: ${pp.wins} ganados · ${pp.losses} perdidos", color = Color(0xFFFFD740), fontWeight = FontWeight.Bold, fontSize = 12.sp)
      Text("Arrastra el dedo: izquierda/derecha mueve la pala y arriba/abajo la acerca a la red o la aleja (así alcanzas las pelotas cortas) · desliza rápido hacia adelante para pegar con potencia · desliza hacia un costado al pegar para mandar la pelota y darle efecto hacia ese lado · toca para sacar", color = Color(0xFF90A4AE), fontSize = 11.sp, textAlign = TextAlign.Center)
    }
  }
}

@Composable
private fun PongArena(role: String, pp: PongPrefs, c: GameCtx, opp: String, onRestart: () -> Unit, onMenu: () -> Unit) {
  val world = remember { PongWorld(role != "ai" && role != "host", pp.level, pp.target).also { it.humanB = role == "host" } }
  val fx = remember { PongFx() }
  val touch = remember { Touch() }
  var frame by remember { mutableIntStateOf(0) }
  var sA by remember { mutableIntStateOf(0) }; var sB by remember { mutableIntStateOf(0) }
  var state by remember { mutableIntStateOf(0) }; var serverSide by remember { mutableIntStateOf(0) }
  var message by remember { mutableStateOf("") }
  var winnerSide by remember { mutableIntStateOf(-1) }
  var popOn by remember { mutableStateOf(false) }; var popStr by remember { mutableStateOf("") }; var popCol by remember { mutableStateOf(Color.White) }; var popSize by remember { mutableFloatStateOf(30f) }
  var canvasW by remember { mutableFloatStateOf(1080f) }; var canvasH by remember { mutableFloatStateOf(1800f) }
  val flip = role == "client"
  val inputs = remember { mutableStateOf<Map<String, Any?>?>(null) }
  var scored by remember { mutableStateOf(false) }

  if (role == "host" && opp.isNotEmpty()) LaunchedEffect(opp) { c.vm.gameState("pongIn_$opp").collect { m -> if (m != null) inputs.value = m } }
  if (role == "client" || role == "watch") LaunchedEffect(Unit) { c.vm.gameState("pong").collect { m -> if (m != null && m["b"] != null) world.applySnapshot(m) } }
  if (role == "host") DisposableEffect(Unit) { onDispose { c.vm.publishGame("pong", emptyMap()) } }
  DisposableEffect(Unit) { onDispose { PongSfx.release() } }

  LaunchedEffect(Unit) {
    val rnd = Random(System.nanoTime().toInt())
    var last = 0L; var hudT = 0f; var pubT = 0f; var sendT = 0f; var hb = 0L
    var lpx = 0f; var lpz = -1.65f; var lvx = 0f; var lvf = 0f
    var seenTap = 0; var seenSv = 0; var svCount = 0
    var lastInX = 0f; var lastInY = 1.05f
    var bPosX = 0f; var bPosZ = 1.65f
    while (true) {
      androidx.compose.runtime.withFrameNanos { tn ->
        val dt = if (last == 0L) 0.016f else ((tn - last) / 1e9f).coerceAtMost(0.05f); last = tn
        val w = world
        // pala del jugador local (dedo → mesa)
        if (role != "watch") {
          if (touch.active) {
            val tx = ((touch.x / canvasW - 0.5f) * 2.5f).coerceIn(-1.25f, 1.25f)
            // dedo arriba = la pala avanza hasta casi la red; dedo abajo = se aleja al fondo de la mesa
            val tz = -(0.15f + 1.80f * ((touch.y / canvasH - 0.30f) / 0.62f).coerceIn(0f, 1f))
            val nx = lpx + (tx - lpx) * min(1f, dt * 26f); val nz = lpz + (tz - lpz) * min(1f, dt * 26f)
            val nvx = ((nx - lpx) / dt).coerceIn(-10f, 10f); val nvf = ((nz - lpz) / dt).coerceIn(-10f, 10f)
            lvx += (nvx - lvx) * min(1f, dt * 18f); lvf += (nvf - lvf) * min(1f, dt * 18f)
            lpx = nx; lpz = nz
          } else { lvx *= 0.8f; lvf *= 0.8f }
        }
        when (role) {
          "ai" -> {
            w.ax = lpx; w.az = lpz; w.avx = lvx; w.avf = lvf
            if (touch.tap != seenTap) { seenTap = touch.tap; if (w.state == ST_SERVE && w.server == 0) w.serve(0, lpx * 0.15f) }
            w.step(dt)
          }
          "host" -> {
            w.ax = lpx; w.az = lpz; w.avx = lvx; w.avf = lvf
            val inp = inputs.value
            if (inp != null) {
              val px = (inp["px"] as? Number)?.toFloat() ?: 0f; val pz = (inp["pz"] as? Number)?.toFloat() ?: 1.65f
              val nbx = bPosX + (px - bPosX) * min(1f, dt * 22f); val nbz = bPosZ + (pz.coerceIn(0.15f, 1.95f) - bPosZ) * min(1f, dt * 22f)
              w.bvx += (((nbx - bPosX) / dt).coerceIn(-10f, 10f) - w.bvx) * min(1f, dt * 16f)
              w.bvf += (((-(nbz - bPosZ)) / dt).coerceIn(-10f, 10f) - w.bvf) * min(1f, dt * 16f)
              bPosX = nbx; bPosZ = nbz; w.bx = nbx; w.bz = nbz
              val sv = (inp["sv"] as? Number)?.toInt() ?: 0
              if (sv != seenSv) { seenSv = sv; if (w.state == ST_SERVE && w.server == 1) w.serve(1, 0f) }
            }
            if (touch.tap != seenTap) { seenTap = touch.tap; if (w.state == ST_SERVE && w.server == 0) w.serve(0, lpx * 0.15f) }
            w.step(dt)
            pubT += dt
            if (pubT >= 0.05f) { pubT = 0f; hb++; c.vm.publishGame("pong", w.encode() + mapOf("opp" to opp, "hb" to hb)) }
          }
          "client" -> {
            // la pala del jugador B se ve al instante; el resto llega del anfitrión
            w.bx = -lpx; w.bz = -lpz; w.by = w.followY(w.by, 1, dt)
            if (touch.tap != seenTap) { seenTap = touch.tap; svCount++ }
            w.step(dt)
            sendT += dt
            if (sendT >= 0.033f) { sendT = 0f; c.vm.publishGame("pongIn_${c.me}", mapOf("px" to (-lpx).toDouble(), "pz" to (-lpz).toDouble(), "sv" to svCount.toLong(), "ts" to System.currentTimeMillis())) }
          }
          else -> w.step(dt)
        }
        // eventos → efectos
        for (e in w.events) {
          when (e.kind) {
            0 -> { fx.rings.add(PRing(e.x, e.z, 0f)); for (i in 0 until 4) fx.parts.add(PPart(e.x, e.y, e.z, (rnd.nextFloat() - 0.5f) * 0.8f, 0.6f + rnd.nextFloat() * 0.6f, (rnd.nextFloat() - 0.5f) * 0.8f, 0.5f, 0.5f, 0.012f, Color.White)); PongSfx.bounce() }
            1 -> {
              val col = if (e.z < 0f) Color(0xFFFF5252) else Color(0xFF40C4FF)
              for (i in 0 until (if (e.power > 8.5f) 22 else 10)) { val a = rnd.nextFloat() * 2f * PI.toFloat(); val s = 0.8f + rnd.nextFloat() * 2.2f; fx.parts.add(PPart(e.x, e.y, e.z, cos(a) * s, sin(a) * s + 0.8f, (rnd.nextFloat() - 0.5f) * 2f, 0.55f, 0.55f, 0.014f, col)) }
              if (e.power > 8.5f) { fx.shake = 0.6f; fx.flash = 0.35f }
              PongSfx.hit()
            }
            2 -> { for (i in 0 until 6) fx.parts.add(PPart(e.x, e.y, e.z, (rnd.nextFloat() - 0.5f) * 1.5f, rnd.nextFloat(), (rnd.nextFloat() - 0.5f), 0.5f, 0.5f, 0.012f, Color.White)) }
            3 -> {
              val mineWon = if (flip) e.power >= 0.5f else e.power < 0.5f
              fx.popText = if (role == "watch") "¡PUNTO!" else if (mineWon) "¡PUNTO!" else "PUNTO RIVAL"
              fx.popColor = if (role == "watch" || mineWon) Color(0xFF69F0AE) else Color(0xFFFF8A80); fx.popT = 1.4f
              for (i in 0 until 26) { val a = rnd.nextFloat() * 2f * PI.toFloat(); val s = 1f + rnd.nextFloat() * 2.5f; fx.parts.add(PPart(0f, P_TY + 0.3f, 0f, cos(a) * s, 2f + rnd.nextFloat() * 2f, sin(a) * s, 1.1f, 1.1f, 0.016f, listOf(Color(0xFFFFD740), Color(0xFFFF4081), Color(0xFF40C4FF), Color(0xFF69F0AE))[i % 4])) }
              PongSfx.point()
            }
          }
        }
        w.events.clear()
        var i = fx.parts.size - 1
        while (i >= 0) { val p = fx.parts[i]; p.life -= dt; if (p.life <= 0f) fx.parts.removeAt(i) else { p.vy -= 6f * dt; p.x += p.vx * dt; p.y += p.vy * dt; p.z += p.vz * dt }; i-- }
        i = fx.rings.size - 1; while (i >= 0) { fx.rings[i].age += dt; if (fx.rings[i].age > 0.5f) fx.rings.removeAt(i); i-- }
        fx.shake = max(0f, fx.shake - dt * 2.2f); fx.flash = max(0f, fx.flash - dt * 2f); fx.popT = max(0f, fx.popT - dt)
        // estela de la pelota
        fx.trail.add(floatArrayOf(w.b.x, w.b.y, w.b.z)); if (fx.trail.size > 14) fx.trail.removeAt(0)
        hudT += dt
        if (hudT > 0.1f) {
          hudT = 0f
          popOn = fx.popT > 0f; popStr = fx.popText; popCol = fx.popColor; popSize = 30f + 12f * min(1f, fx.popT)
          sA = w.scoreA; sB = w.scoreB; state = w.state; serverSide = w.server; message = w.msg; winnerSide = w.winner
          if (!scored && w.state == ST_OVER && role == "ai") { scored = true; if (w.winner == 0) pp.wins++ else pp.losses++; pp.save() }
        }
        frame++
      }
    }
  }

  val mySide = if (flip) 1 else 0
  val myScore = if (flip) sB else sA; val rivalScore = if (flip) sA else sB
  val myServe = serverSide == mySide && role != "watch"
  Box(Modifier.fillMaxSize().background(Color(0xFF05070F))) {
    Canvas(
      Modifier.fillMaxSize().pointerInput(role) {
        if (role == "watch") return@pointerInput
        awaitEachGesture {
          val down = awaitFirstDown(requireUnconsumed = false)
          touch.active = true; touch.x = down.position.x; touch.y = down.position.y
          var moved = false
          while (true) {
            val ev = awaitPointerEvent()
            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
            if (!ch.pressed) break
            touch.x = ch.position.x; touch.y = ch.position.y
            if ((ch.position - down.position).getDistance() > 24f) moved = true
            ch.consume()
          }
          touch.active = false
          if (!moved) touch.tap++
        }
      }
    ) {
      canvasW = size.width; canvasH = size.height
      val tick = frame
      if (tick < 0) return@Canvas
      drawPongScene(world, fx, flip)
    }
    // marcador
    Row(Modifier.align(Alignment.TopCenter).padding(top = 10.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xAA000000)).border(1.dp, Color(0x55FFFFFF), RoundedCornerShape(16.dp)).padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(if (role == "watch") c.nameOf(c.me).let { "J1" } else "TÚ", color = Color(0xFFFF8A80), fontWeight = FontWeight.Bold, fontSize = 11.sp)
        Text("$myScore", color = Color.White, fontWeight = FontWeight.Black, fontSize = 34.sp)
        Text(if (myServe) "● SAQUE" else "", color = Color(0xFFFFD740), fontSize = 9.sp)
      }
      Text("  —  ", color = Color(0xFF90A4AE), fontSize = 24.sp)
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(if (role == "ai") "IA ${LEVELS[pp.level]}" else if (role == "watch") "J2" else "RIVAL", color = Color(0xFF80D8FF), fontWeight = FontWeight.Bold, fontSize = 11.sp)
        Text("$rivalScore", color = Color.White, fontWeight = FontWeight.Black, fontSize = 34.sp)
        Text(if (serverSide != mySide && role != "watch") "● SAQUE" else "", color = Color(0xFFFFD740), fontSize = 9.sp)
      }
    }
    Box(Modifier.align(Alignment.TopStart).padding(10.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x77000000)).clickable { onMenu() }.padding(horizontal = 12.dp, vertical = 6.dp)) { Text("☰", color = Color.White) }
    if (state == ST_RALLY && max(sA, sB) >= world.target - 1 && abs(sA - sB) >= 0) Text("🔥 PELOTA DE PARTIDO", color = Color(0xFFFFD740), fontWeight = FontWeight.Black, fontSize = 12.sp, modifier = Modifier.align(Alignment.TopCenter).padding(top = 112.dp))
    if (popOn) Text(popStr, color = popCol, fontWeight = FontWeight.Black, fontSize = popSize.sp, modifier = Modifier.align(Alignment.Center))
    if (state == ST_POINT && message.isNotEmpty()) Text(message, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.align(Alignment.Center).padding(top = 70.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x99000000)).padding(horizontal = 12.dp, vertical = 6.dp))
    if (state == ST_SERVE && myServe) Text("Toca la pantalla para SACAR", color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp, modifier = Modifier.align(Alignment.Center).padding(top = 40.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xAA000000)).padding(horizontal = 14.dp, vertical = 8.dp))
    if (state == ST_SERVE && !myServe && role != "watch") Text("Saca tu rival…", color = Color(0xFFB0BEC5), fontSize = 13.sp, modifier = Modifier.align(Alignment.Center).padding(top = 40.dp))
    if (state == ST_OVER) {
      val iWon = if (role == "watch") true else (winnerSide == mySide)
      Box(Modifier.fillMaxSize().background(Color(0xCC000000)), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(24.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xF2101830)).border(2.dp, if (iWon) Color(0xFF00E676) else Color(0xFFFF5252), RoundedCornerShape(20.dp)).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(if (role == "watch") "Fin del partido" else if (iWon) "🏆 ¡GANASTE!" else "Perdiste esta vez", color = if (iWon) Color(0xFFFFD740) else Color(0xFFFF8A80), fontWeight = FontWeight.Black, fontSize = 24.sp)
          Text("$myScore  —  $rivalScore", color = Color.White, fontWeight = FontWeight.Black, fontSize = 30.sp)
          if (role == "ai" || role == "host") Box(Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF00E676)).clickable { onRestart() }, contentAlignment = Alignment.Center) { Text("REVANCHA", color = Color(0xFF00281A), fontWeight = FontWeight.Black) }
          else Text("Esperando la revancha…", color = Color(0xFFB0BEC5), fontSize = 12.sp)
          Box(Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF263238)).clickable { onMenu() }, contentAlignment = Alignment.Center) { Text("Menú", color = Color.White, fontWeight = FontWeight.Bold) }
        }
      }
    }
  }
}

/** Cámara en perspectiva: mira la mesa desde atrás y algo por encima. [flip] da la vista del jugador del otro lado. */
private class PCam(val w: Float, val h: Float, val flip: Boolean, shx: Float, shy: Float, val camX: Float) {
  private val camY = 2.3f; private val camZ = -2.6f; private val cosP = 0.7518f; private val sinP = 0.6594f
  val F = w * 1.0f
  private val cx = w / 2f + shx; private val cy = h * 0.60f + shy
  var depth = 1f
  fun p(x0: Float, y: Float, z0: Float): Offset {
    val x = if (flip) -x0 else x0; val z = if (flip) -z0 else z0
    val dx = x - camX; val dy = y - camY; val dz = z - camZ
    val d = (-dy * sinP + dz * cosP).coerceAtLeast(0.25f)
    val uy = dy * cosP + dz * sinP
    depth = d
    return Offset(cx + F * dx / d, cy - F * uy / d)
  }
}

private fun DrawScope.drawPongScene(world: PongWorld, fx: PongFx, flip: Boolean) {
  val w = size.width; val h = size.height
  val t = (SystemClock.uptimeMillis() % 100000L) / 1000f
  val shx = (Random.nextFloat() - 0.5f) * fx.shake * 14f; val shy = (Random.nextFloat() - 0.5f) * fx.shake * 14f
  val cam = PCam(w, h, flip, shx, shy, world.b.x * 0.10f * (if (flip) -1f else 1f))
  // pabellón: pared oscura, focos y público
  drawRect(Brush.verticalGradient(listOf(Color(0xFF05060E), Color(0xFF0E1230), Color(0xFF1A1A3C)), 0f, h * 0.55f), Offset.Zero, Size(w, h))
  for (i in 0 until 3) {
    val bx = w * (0.2f + 0.3f * i); val sway = sin(t * 0.5f + i * 2f) * w * 0.03f
    val beam = Path().apply { moveTo(bx - 8f, 0f); lineTo(bx + 8f, 0f); lineTo(bx + sway + w * 0.20f, h * 0.55f); lineTo(bx + sway - w * 0.20f, h * 0.55f); close() }
    drawPath(beam, Brush.verticalGradient(listOf(Color(0x33FFF3D6), Color.Transparent), 0f, h * 0.55f))
  }
  val crowd = listOf(Color(0xFF3B1D2A), Color(0xFF1D2F4A), Color(0xFF2E3B1D), Color(0xFF4A3A1D), Color(0xFF2A1D4A))
  for (row in 0 until 2) for (i in 0 until 30) {
    val px = w * ((i + row * 0.5f) / 29f); val py = h * (0.15f + 0.045f * row) + (i % 3) * 3f
    val cc = crowd[(i * 3 + row) % crowd.size]
    drawCircle(Color(0xFF1B1620), 11f - row, Offset(px, py)); drawRect(cc, Offset(px - 15f, py + 8f), Size(30f, 34f))
    if ((i * 7 + row * 3 + (t * 2f).toInt()) % 13 == 0) drawCircle(Color(0xCCFFFFFF), 2.2f, Offset(px + 6f, py - 12f))
  }
  // vallas publicitarias en el fondo y a los lados
  run {
    val cols = listOf(Color(0xFF0D47A1), Color(0xFFB71C1C), Color(0xFF1B5E20), Color(0xFFF9A825))
    for (k in 0 until 8) {
      val x0 = -4.4f + k * 1.1f
      val a = cam.p(x0, 0f, 6.2f); val b2 = cam.p(x0 + 1.1f, 0f, 6.2f); val c2 = cam.p(x0 + 1.1f, 0.85f, 6.2f); val d2 = cam.p(x0, 0.85f, 6.2f)
      val bp = Path().apply { moveTo(a.x, a.y); lineTo(b2.x, b2.y); lineTo(c2.x, c2.y); lineTo(d2.x, d2.y); close() }
      drawPath(bp, cols[k % 4]); drawPath(bp, Color(0x55FFFFFF), style = Stroke(1.5f))
      val m = cam.p(x0 + 0.55f, 0.42f, 6.2f); drawLine(Color(0xCCFFFFFF), Offset(m.x - cam.F * 0.18f / cam.depth, m.y), Offset(m.x + cam.F * 0.18f / cam.depth, m.y), 3f)
    }
  }
  // suelo de parqué deportivo (tablones en perspectiva)
  run {
    val f0 = cam.p(-4.4f, 0f, -4f); val f1 = cam.p(4.4f, 0f, -4f); val f2 = cam.p(4.4f, 0f, 6.2f); val f3 = cam.p(-4.4f, 0f, 6.2f)
    val fp = Path().apply { moveTo(f0.x, f0.y); lineTo(f1.x, f1.y); lineTo(f2.x, f2.y); lineTo(f3.x, f3.y); close() }
    drawPath(fp, Brush.verticalGradient(listOf(Color(0xFF2B1D12), Color(0xFF6B4A2B), Color(0xFF8A6238)), f3.y, f0.y))
    for (i in -11..11) drawLine(Color(0x33000000), cam.p(i * 0.4f, 0f, -4f), cam.p(i * 0.4f, 0f, 6.2f), 1.2f)
    for (j in -4..11) drawLine(Color(0x22000000), cam.p(-4.4f, 0f, j * 0.9f), cam.p(4.4f, 0f, j * 0.9f), 1f)
    // zona de juego pintada (rectángulo reglamentario)
    val a = cam.p(-2.6f, 0f, -3.4f); val b2 = cam.p(2.6f, 0f, -3.4f); val c2 = cam.p(2.6f, 0f, 4.2f); val d2 = cam.p(-2.6f, 0f, 4.2f)
    val zp = Path().apply { moveTo(a.x, a.y); lineTo(b2.x, b2.y); lineTo(c2.x, c2.y); lineTo(d2.x, d2.y); close() }
    drawPath(zp, Color(0x331565C0)); drawPath(zp, Color(0x66FFFFFF), style = Stroke(2f))
  }
  // sombra de la mesa
  run {
    val sh = Path(); val c0 = cam.p(-P_HW - 0.12f, 0f, -P_HL - 0.12f); sh.moveTo(c0.x, c0.y)
    for (q in listOf(cam.p(P_HW + 0.12f, 0f, -P_HL - 0.12f), cam.p(P_HW + 0.12f, 0f, P_HL + 0.12f), cam.p(-P_HW - 0.12f, 0f, P_HL + 0.12f))) sh.lineTo(q.x, q.y)
    sh.close(); drawPath(sh, Color(0x77000000))
  }
  // patas metálicas con travesaño
  for (sx in listOf(-1f, 1f)) for (sz in listOf(-1f, 1f)) {
    val top = cam.p(sx * (P_HW - 0.12f), P_TY - 0.05f, sz * (P_HL - 0.25f)); val d1 = cam.depth
    val bot = cam.p(sx * (P_HW - 0.12f), 0f, sz * (P_HL - 0.25f))
    drawLine(Color(0xFF8E99A6), top, bot, cam.F * 0.045f / d1, StrokeCap.Round)
    drawLine(Color(0x55FFFFFF), Offset(top.x - 1.5f, top.y), Offset(bot.x - 1.5f, bot.y), cam.F * 0.012f / d1)
  }
  for (sz in listOf(-1f, 1f)) drawLine(Color(0xFF6E7884), cam.p(-(P_HW - 0.12f), 0.22f, sz * (P_HL - 0.25f)), cam.p(P_HW - 0.12f, 0.22f, sz * (P_HL - 0.25f)), 4f)
  // tablero con grosor
  val c1 = cam.p(-P_HW, P_TY, -P_HL); val c2 = cam.p(P_HW, P_TY, -P_HL); val c3 = cam.p(P_HW, P_TY, P_HL); val c4 = cam.p(-P_HW, P_TY, P_HL)
  val edgeA = cam.p(-P_HW, P_TY - 0.03f, -P_HL); val edgeB = cam.p(P_HW, P_TY - 0.03f, -P_HL)
  val side = Path().apply { moveTo(c1.x, c1.y); lineTo(c2.x, c2.y); lineTo(edgeB.x, edgeB.y); lineTo(edgeA.x, edgeA.y); close() }
  drawPath(side, Color(0xFF06214A))
  val top = Path().apply { moveTo(c1.x, c1.y); lineTo(c2.x, c2.y); lineTo(c3.x, c3.y); lineTo(c4.x, c4.y); close() }
  drawPath(top, Brush.verticalGradient(listOf(Color(0xFF2A6FD6), Color(0xFF123F9C), Color(0xFF0A2A78)), c4.y, c1.y))
  // brillo especular del foco principal sobre la mesa
  run {
    val g0 = cam.p(-P_HW * 0.6f, P_TY, -P_HL * 0.7f); val g1 = cam.p(P_HW * 0.2f, P_TY, -P_HL * 0.7f); val g2 = cam.p(P_HW * 0.4f, P_TY, P_HL * 0.8f); val g3 = cam.p(-P_HW * 0.4f, P_TY, P_HL * 0.8f)
    val gp = Path().apply { moveTo(g0.x, g0.y); lineTo(g1.x, g1.y); lineTo(g2.x, g2.y); lineTo(g3.x, g3.y); close() }
    drawPath(gp, Brush.verticalGradient(listOf(Color(0x00FFFFFF), Color(0x2EFFFFFF), Color(0x00FFFFFF)), g3.y, g0.y))
  }
  // brillo que sigue a la pelota
  run {
    val gl = cam.p(world.b.x, P_TY, world.b.z); val gd = cam.depth
    drawCircle(Brush.radialGradient(listOf(Color(0x33FFFFFF), Color.Transparent), gl, cam.F * 0.4f / gd), cam.F * 0.4f / gd, gl)
  }
  val line = Color(0xF2FFFFFF)
  drawPath(top, line, style = Stroke(3.5f))
  // líneas reglamentarias: central (dobles) y de fondo
  drawLine(Color(0xAAFFFFFF), cam.p(0f, P_TY, -P_HL), cam.p(0f, P_TY, P_HL), 2f)
  for (sz in listOf(-1f, 1f)) drawLine(Color(0xCCFFFFFF), cam.p(-P_HW, P_TY, sz * P_HL * 0.997f), cam.p(P_HW, P_TY, sz * P_HL * 0.997f), 4f)
  for (sx in listOf(-1f, 1f)) drawLine(Color(0xCCFFFFFF), cam.p(sx * P_HW * 0.997f, P_TY, -P_HL), cam.p(sx * P_HW * 0.997f, P_TY, P_HL), 4f)
  // anillos de bote
  for (r in fx.rings) {
    val k = r.age / 0.5f; val rad = 0.03f + 0.18f * k
    val ring = Path()
    for (i in 0..24) { val a = i * 2f * PI.toFloat() / 24f; val q = cam.p(r.x + cos(a) * rad, P_TY + 0.002f, r.z + sin(a) * rad); if (i == 0) ring.moveTo(q.x, q.y) else ring.lineTo(q.x, q.y) }
    drawPath(ring, Color.White.copy(alpha = (1f - k) * 0.8f), style = Stroke(2.5f))
  }
  // pala lejana (la del fondo de la vista)
  val farIsB = !flip
  fun paddleOf(isB: Boolean) = if (isB) Triple(world.bx, world.by, world.bvx) else Triple(world.ax, world.ay, world.avx)
  fun paddleZ(isB: Boolean) = if (isB) world.bz else world.az
  fun drawPaddle(isB: Boolean) {
    val (px, py, pvx) = paddleOf(isB)
    val pz = paddleZ(isB)
    val ctr = cam.p(px, py, pz); val d = cam.depth
    val r = cam.F * P_PR / d * 1.12f
    val red = if (isB) Color(0xFF263238) else Color(0xFFD50000)
    val rim = if (isB) Color(0xFF00E5FF) else Color(0xFFFFD740)
    val hb = cam.p(px, py - P_PR * 1.05f, pz); val hb2 = cam.p(px - pvx * 0.01f, py - P_PR * 2.0f, pz)
    drawLine(Color(0xFF8D6E63), hb, hb2, r * 0.34f, StrokeCap.Round)
    drawCircle(Color.Black.copy(alpha = 0.25f), r * 1.1f, Offset(ctr.x + r * 0.08f, ctr.y + r * 0.12f))
    drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.55f), red, Color(red.red * 0.5f, red.green * 0.5f, red.blue * 0.5f, 1f)), Offset(ctr.x - r * 0.3f, ctr.y - r * 0.35f), r * 1.3f), r, ctr)
    drawCircle(rim, r, ctr, style = Stroke(max(2.5f, r * 0.09f)))
    // goma con textura de puntitos y brillo
    for (q in 0 until 7) { val an = q * 0.9f; val rr = r * (0.25f + 0.1f * (q % 3)); drawCircle(Color.Black.copy(alpha = 0.14f), r * 0.05f, Offset(ctr.x + cos(an) * rr, ctr.y + sin(an) * rr)) }
    drawArc(Color.White.copy(alpha = 0.28f), 200f, 70f, false, Offset(ctr.x - r * 0.78f, ctr.y - r * 0.78f), Size(r * 1.56f, r * 1.56f), style = Stroke(max(2f, r * 0.07f), cap = StrokeCap.Round))
  }
  drawPaddle(farIsB)
  // red
  run {
    val l0 = cam.p(-P_HW - 0.1f, P_TY, 0f); val l1 = cam.p(-P_HW - 0.1f, P_TY + P_NET, 0f); val r0 = cam.p(P_HW + 0.1f, P_TY, 0f); val r1 = cam.p(P_HW + 0.1f, P_TY + P_NET, 0f)
    val netP = Path().apply { moveTo(l0.x, l0.y); lineTo(l1.x, l1.y); lineTo(r1.x, r1.y); lineTo(r0.x, r0.y); close() }
    drawPath(netP, Color(0x55FFFFFF))
    for (i in 1 until 24) { val xx = -P_HW - 0.1f + (P_HW * 2f + 0.2f) * i / 24f; drawLine(Color(0x44FFFFFF), cam.p(xx, P_TY, 0f), cam.p(xx, P_TY + P_NET, 0f), 1f) }
    for (j in 1..3) { val yy = P_TY + P_NET * j / 4f; drawLine(Color(0x44FFFFFF), cam.p(-P_HW - 0.1f, yy, 0f), cam.p(P_HW + 0.1f, yy, 0f), 1f) }
    drawLine(Color.White, l1, r1, 5f, StrokeCap.Round)
    drawLine(Color(0xFFB0BEC5), l0, l1, 9f, StrokeCap.Round); drawLine(Color(0xFFB0BEC5), r0, r1, 9f, StrokeCap.Round)
    drawCircle(Color(0xFFECEFF1), 6f, l1); drawCircle(Color(0xFFECEFF1), 6f, r1)
  }
  // pelota: sombra, estela y cuerpo
  val b = world.b
  run {
    val sp = cam.p(b.x, P_TY + 0.001f, b.z); val sd = cam.depth
    val onTable = abs(b.x) <= P_HW && abs(b.z) <= P_HL
    val sc = if (onTable) sp else cam.p(b.x, 0.001f, b.z)
    val sr = cam.F * P_R * 2.2f / cam.depth * (1f + (b.y - P_TY).coerceAtLeast(0f) * 0.4f)
    drawOval(Color(0x66000000), Offset(sc.x - sr, sc.y - sr * 0.45f), Size(sr * 2f, sr * 0.9f))
  }
  for ((i, tp) in fx.trail.withIndex()) {
    val a = i.toFloat() / fx.trail.size
    val q = cam.p(tp[0], tp[1], tp[2]); val rr = cam.F * P_R * 1.7f / cam.depth * (0.4f + 0.6f * a)
    drawCircle(Color(0xFFFFFFFF).copy(alpha = 0.22f * a), rr, q)
  }
  val bc = cam.p(b.x, b.y, b.z); val br = cam.F * P_R * 1.8f / cam.depth
  drawCircle(Color(0xFFFFFFFF).copy(alpha = 0.14f), br * 2.0f, bc)
  drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFFF5F0E6), Color(0xFFB8B2A6)), Offset(bc.x - br * 0.3f, bc.y - br * 0.3f), br * 1.4f), br, bc)
  // marca que gira con el efecto
  val ang = t * (b.wx + b.wy) * 0.08f
  drawLine(Color(0x66BF360C), Offset(bc.x + cos(ang) * br * 0.7f, bc.y + sin(ang) * br * 0.7f), Offset(bc.x - cos(ang) * br * 0.7f, bc.y - sin(ang) * br * 0.7f), max(1.5f, br * 0.12f))
  // pala cercana
  drawPaddle(!farIsB)
  // partículas
  for (p in fx.parts) {
    val a = (p.life / p.max).coerceIn(0f, 1f)
    val q = cam.p(p.x, p.y, p.z)
    drawCircle(p.color.copy(alpha = a), max(1.5f, cam.F * p.size / cam.depth * 1.6f * a), q)
  }
  if (fx.flash > 0.01f) drawRect(Color.White.copy(alpha = 0.18f * fx.flash))
  // viñeta
  drawRect(Brush.radialGradient(listOf(Color.Transparent, Color(0x99000000)), Offset(w / 2f, h * 0.55f), max(w, h) * 0.8f))
}
