package com.example.agar

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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.firestore.GameOp
import com.example.ui.screens.GameCtx
import com.example.ui.screens.gameAvail
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random

/** Preferencias y ajustes del jugador (se guardan en SharedPreferences propio del módulo). */
private class AgarSettings(private val prefs: android.content.SharedPreferences, defaultNick: String) {
  var nick by mutableStateOf(prefs.getString("nick", null) ?: defaultNick)
  var color by mutableIntStateOf(prefs.getInt("color", 3))
  var dark by mutableStateOf(prefs.getBoolean("dark", false))
  var mass by mutableStateOf(prefs.getBoolean("mass", true))
  var grid by mutableStateOf(prefs.getBoolean("grid", true))
  var fx by mutableStateOf(prefs.getBoolean("fx", true))
  var bots by mutableIntStateOf(prefs.getInt("bots", 24))
  var best by mutableIntStateOf(prefs.getInt("best", 0))
  fun save() {
    prefs.edit().putString("nick", nick).putInt("color", color).putBoolean("dark", dark).putBoolean("mass", mass).putBoolean("grid", grid)
      .putBoolean("fx", fx).putInt("bots", bots).putInt("best", best).apply()
  }
}

private class Ctrl { var eject = false; var split = 0; var respawn = 0 }

/**
 * CÉLULAS ARENA — juego de células estilo Agar.io hecho por nosotros.
 *  · ARENA PERSISTENTE: un mundo que no se detiene. Entras y sales cuando quieras; los demás de la sala pueden unirse en cualquier momento.
 *    Un jugador cualquiera hace de «servidor» (el servidor de Firebase decide quién con un candado); si se va, otro toma el relevo
 *    con el mundo tal cual estaba (células, comida, virus y bots).
 *  · Práctica local (solo o contra bots) y espectador.
 */
@Composable
internal fun AgarGame(c: GameCtx) {
  val ctx = LocalContext.current.applicationContext
  val st = remember { AgarSettings(ctx.getSharedPreferences("agar_prefs", android.content.Context.MODE_PRIVATE), c.nameOf(c.me).takeIf { it != "Tú" }.orEmpty()) }
  var mode by remember { mutableStateOf("menu") }      // menu, local, spec, arena
  var runId by remember { mutableIntStateOf(0) }
  val avail = (gameAvail() - 8.dp).coerceAtLeast(380.dp)
  val arena by remember { c.vm.gameState("agar") }.collectAsState(initial = null)
  val lock by remember { c.vm.gameState("agarLock") }.collectAsState(initial = null)
  val offset by remember { c.vm.serverOffsetFlow() }.collectAsState(initial = 0L)
  var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }
  LaunchedEffect(Unit) { while (true) { delay(1000); tick = System.currentTimeMillis() } }
  val beat = (lock?.get("beat") as? Number)?.toLong() ?: 0L
  val live = lock != null && (lock?.get("owner") as? String).orEmpty().isNotEmpty() && (tick + offset - beat) < 7000L
  val online = if (live) ((arena?.get("h") as? List<*>)?.count { (it as? String)?.split("|")?.getOrNull(1) == "1" } ?: 0) else 0

  Box(Modifier.fillMaxWidth().height(avail).clip(RoundedCornerShape(16.dp))) {
    if (mode == "menu") {
      AgarMenu(st, online, live) { m -> st.save(); runId++; mode = m }
    } else {
      androidx.compose.runtime.key(runId) {
        AgarArena(mode, st, c, mode == "spec", onReplay = { runId++; mode = if (mode == "spec") "spec" else "local" }, onMenu = { mode = "menu" })
      }
    }
  }
}

@Composable
private fun AgarMenu(st: AgarSettings, online: Int, live: Boolean, onStart: (String) -> Unit) {
  val ink = if (st.dark) Color.White else Color(0xFF263238)
  val sub = if (st.dark) Color(0xFFB0BEC5) else Color(0xFF546E7A)
  Box(Modifier.fillMaxSize().background(if (st.dark) Color(0xFF0E0F1A) else Color(0xFFF2FBFF))) {
    Canvas(Modifier.fillMaxSize()) {
      val step = 50.dp.toPx(); var x = 0f
      val lc = if (st.dark) Color(0xFF1B1D30) else Color(0xFFDDE7EC)
      while (x < size.width) { drawLine(lc, Offset(x, 0f), Offset(x, size.height), 1f); x += step }
      var y = 0f
      while (y < size.height) { drawLine(lc, Offset(0f, y), Offset(size.width, y), 1f); y += step }
      val deco = listOf(Triple(0.15f, 0.1f, 0.07f), Triple(0.9f, 0.18f, 0.05f), Triple(0.82f, 0.88f, 0.09f), Triple(0.08f, 0.82f, 0.045f))
      deco.forEachIndexed { i, (fx, fy, fr) ->
        val col = A_COLORS[(i * 3 + 1) % A_COLORS.size]; val r = size.width * fr; val ctr = Offset(size.width * fx, size.height * fy)
        drawCircle(androidx.compose.ui.graphics.Brush.radialGradient(0f to lighten(col, 0.5f), 0.5f to col, 1f to darken(col, 0.65f), center = Offset(ctr.x - r * 0.3f, ctr.y - r * 0.3f), radius = r * 1.4f), r, ctr)
        drawCircle(darken(col, 0.7f), r, ctr, style = Stroke(r * 0.06f))
      }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
      Text("CÉLULAS ARENA", fontSize = 30.sp, fontWeight = FontWeight.Black, color = A_COLORS[st.color], textAlign = TextAlign.Center)
      Text("Come, crece y no te dejes comer", color = sub, fontSize = 13.sp)
      Spacer(Modifier.height(12.dp))
      Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(if (st.dark) Color(0xEE1A1C2E) else Color.White).border(1.dp, Color(0x33000000), RoundedCornerShape(18.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        OutlinedTextField(
          st.nick, { st.nick = it.take(15) }, singleLine = true, label = { Text("Apodo") }, modifier = Modifier.fillMaxWidth(),
          colors = OutlinedTextFieldDefaults.colors(focusedTextColor = ink, unfocusedTextColor = ink)
        )
        Text("Color de tu célula", fontSize = 12.sp, color = sub, fontWeight = FontWeight.Bold)
        for (rowStart in listOf(0, 6)) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          for (i in rowStart until rowStart + 6) {
            val col = A_COLORS[i]
            Box(
              Modifier.size(38.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Brush.radialGradient(0f to lighten(col, 0.5f), 0.5f to col, 1f to darken(col, 0.7f)))
                .border(if (i == st.color) 3.dp else 1.dp, if (i == st.color) ink else darken(col, 0.7f), CircleShape).clickable { st.color = i }
            )
          }
        }
        Text("Bots (para la arena nueva y la práctica; 0 = solo humanos)", fontSize = 12.sp, color = sub, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          for (n in listOf(0, 8, 16, 24, 40)) {
            val on = st.bots == n
            Box(
              Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(10.dp)).background(if (on) Color(0xFF34C759) else (if (st.dark) Color(0xFF2A2D45) else Color(0xFFECEFF1))).clickable { st.bots = n },
              contentAlignment = Alignment.Center
            ) { Text(if (n == 0) "Solo" else "$n", color = if (on) Color.White else ink, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
          }
        }
        AgarToggle("Efectos especiales", st.fx, ink) { st.fx = it }
        AgarToggle("Mostrar masa", st.mass, ink) { st.mass = it }
        AgarToggle("Cuadrícula", st.grid, ink) { st.grid = it }
        AgarToggle("Tema oscuro", st.dark, ink) { st.dark = it }
      }
      Spacer(Modifier.height(12.dp))
      Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF007AFF)).clickable { onStart("arena") }.padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Text("🌐 ENTRAR A LA ARENA", color = Color.White, fontWeight = FontWeight.Black, fontSize = 19.sp)
        Text(if (live) "Arena abierta · $online jugador${if (online == 1) "" else "es"} dentro" else "Siempre abierta: entra cuando quieras y los demás se unen", color = Color(0xCCFFFFFF), fontSize = 11.sp)
      }
      Spacer(Modifier.height(8.dp))
      Box(Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF34C759)).clickable { onStart("local") }, contentAlignment = Alignment.Center) {
        Text(if (st.bots == 0) "PRÁCTICA SOLO (sin conexión)" else "PRÁCTICA CON ${st.bots} BOTS (sin conexión)", color = Color.White, fontWeight = FontWeight.Black, fontSize = 13.sp)
      }
      Spacer(Modifier.height(8.dp))
      Box(Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF32ADE6)).clickable { onStart("spec") }, contentAlignment = Alignment.Center) {
        Text("ESPECTAR BOTS", color = Color.White, fontWeight = FontWeight.Black, fontSize = 13.sp)
      }
      Spacer(Modifier.height(8.dp))
      if (st.best > 0) Text("🏆 Tu mejor masa: ${st.best}", color = ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
      Text("Arrastra el dedo para moverte · DIVIDIR lanza media célula · el botón azul expulsa masa (alimenta virus)", color = sub, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
    }
  }
}

@Composable
private fun AgarToggle(label: String, on: Boolean, ink: Color, set: (Boolean) -> Unit) {
  Row(Modifier.fillMaxWidth().clickable { set(!on) }, verticalAlignment = Alignment.CenterVertically) {
    Text(label, Modifier.weight(1f), color = ink, fontSize = 14.sp)
    Box(Modifier.width(44.dp).height(24.dp).clip(CircleShape).background(if (on) Color(0xFF34C759) else Color(0xFF9E9E9E)).padding(3.dp)) {
      Box(Modifier.size(18.dp).offset(x = if (on) 20.dp else 0.dp).clip(CircleShape).background(Color.White))
    }
  }
}

@Composable
private fun AgarArena(role: String, st: AgarSettings, c: GameCtx, spectate: Boolean, onReplay: () -> Unit, onMenu: () -> Unit) {
  fun freshMirror() = AgarWorld(A_ARENA, 0, aPellets(A_ARENA), 0, Random(1), 12, true)
  var world by remember { mutableStateOf(if (role == "arena") freshMirror() else AgarWorld(A_ARENA, if (spectate && st.bots == 0) 24 else st.bots)) }
  var simulator by remember { mutableStateOf(false) }
  val cam = remember { AgarCam() }
  val fx = remember { FxState() }
  val pt = remember { AgarPaints() }
  val ctrl = remember { Ctrl() }
  var frame by remember { mutableIntStateOf(0) }
  var score by remember { mutableIntStateOf(0) }
  var board by remember { mutableStateOf<List<Triple<String, Int, Boolean>>>(emptyList()) }
  var online by remember { mutableIntStateOf(0) }
  var dead by remember { mutableStateOf(false) }
  var toast by remember { mutableStateOf("") }
  var toastT by remember { mutableFloatStateOf(0f) }
  var joyOrigin by remember { mutableStateOf<Offset?>(null) }
  var joyKnob by remember { mutableStateOf(Offset.Zero) }
  val jx = remember { mutableFloatStateOf(0f) }; val jy = remember { mutableFloatStateOf(0f) }; val jm = remember { mutableFloatStateOf(0f) }
  var canvasW by remember { mutableFloatStateOf(1080f) }; var canvasH by remember { mutableFloatStateOf(1800f) }
  val members by c.vm.memberUids.collectAsState()
  val offset by remember { c.vm.serverOffsetFlow() }.collectAsState(initial = 0L)
  val offsetNow by androidx.compose.runtime.rememberUpdatedState(offset)
  val lock by remember { c.vm.gameState("agarLock") }.collectAsState(initial = null)
  val lockNow by androidx.compose.runtime.rememberUpdatedState(lock)
  val membersNow by androidx.compose.runtime.rememberUpdatedState(members)
  val inputs = remember { mutableStateMapOf<String, Map<String, Any?>>() }
  val recvAt = remember { HashMap<String, Long>() }; val firstSeen = remember { HashSet<String>() }   // cuándo llegó la última señal de cada jugador (reloj local)
  val lastSplit = remember { HashMap<String, Int>() }; val lastResp = remember { HashMap<String, Int>() }; val lastSeen = remember { HashMap<String, Long>() }
  var lastSnap by remember { mutableStateOf(0L) }

  fun nowSrv() = System.currentTimeMillis() + offsetNow
  fun me(): AgarPlayer? = when (role) { "arena" -> world.players.firstOrNull { it.uid == c.me }; "spec" -> null; else -> world.human }

  fun promote() {
    val mir = world
    val sim = AgarWorld(A_ARENA, if (mir.players.isEmpty()) st.bots else 0, aPellets(A_ARENA), 16, Random.Default, 12, false, true)
    if (mir.players.isNotEmpty()) sim.loadFrom(mir)
    sim.fxOn = st.fx
    lastSeen.clear(); val now = System.currentTimeMillis()
    for (p in sim.players) if (p.uid.isNotEmpty()) lastSeen[p.uid] = now
    world = sim; simulator = true
  }
  fun demote() { world = freshMirror().also { it.fxOn = st.fx }; simulator = false }

  // el servidor de Firebase decide quién simula: quien libera o deja caducar el candado, lo reclama el primero que llegue
  if (role == "arena") LaunchedEffect(Unit) {
    c.vm.gameState("agar").collect { m ->
      if (m != null && m["c"] != null && !simulator) { world.applySnapshot(m); lastSnap = SystemClock.elapsedRealtime() }
    }
  }
  if (role == "arena" && simulator) for (uid in members) androidx.compose.runtime.key(uid) {
    LaunchedEffect(uid) {
      c.vm.gameState("agarIn_$uid").collect { m ->
        if (m != null) {
          // La presencia se mide con el reloj de ESTE dispositivo (no con el de cada celular): si llegó una señal nueva, está conectado.
          // Solo el primer dato guardado (que puede ser de una sesión vieja) se filtra por antigüedad, con margen amplio por relojes distintos.
          val ts = (m["ts"] as? Number)?.toLong() ?: 0L
          val firstOne = firstSeen.add(uid)
          if (!firstOne || kotlin.math.abs(nowSrv() - ts) < 45_000L) recvAt[uid] = System.currentTimeMillis()
          inputs[uid] = m
        }
      }
    }
  }
  if (role == "arena") DisposableEffect(Unit) {
    onDispose {
      if (simulator) c.vm.gameTransact("agarLock", true, { (it["owner"] as? String) == c.me }, { mapOf("owner" to "") }) { _, _ -> }
      else c.vm.publishGame("agarIn_${c.me}", mapOf("bye" to true, "ts" to nowSrv()))
    }
  }

  LaunchedEffect(Unit) {
    val rnd = Random(System.nanoTime().toInt())
    world.fxOn = st.fx
    if (role == "local") { val hp = world.human; hp.name = st.nick.ifBlank { "Célula" }; hp.color = st.color; world.spawn(hp) }
    cam.x = world.size / 2f; cam.y = world.size / 2f
    var last = 0L; var hudT = 0f; var pubT = 0f; var sendT = 0f; var dangerT = 0f; var presT = 0f; var beatT = 0f; var electT = 1.2f
    var everAlive = false; var wasAlive = false; var seenSplit = 0; var lastKills = 0; var milestone = 0
    var lastSent = ""; var sentAt = 0L; var claiming = false
    var lastBeatSeen = Long.MIN_VALUE; var lastBeatAt = SystemClock.elapsedRealtime()
    val jitter = (c.me.hashCode() and 0xFF) / 255f
    while (true) {
      androidx.compose.runtime.withFrameNanos { tn ->
        val dt = if (last == 0L) 0.016f else ((tn - last) / 1e9f).coerceAtMost(0.05f); last = tn
        cam.animT += dt
        val w = world
        val m = me()
        val alive = m != null && m.alive && m.cells.isNotEmpty()
        val cx = if (alive) m!!.cx() else cam.x; val cy = if (alive) m!!.cy() else cam.y
        var tgx = cx; var tgy = cy
        if (alive && jm.floatValue > 0.08f) { tgx = cx + jx.floatValue * jm.floatValue * 700f; tgy = cy + jy.floatValue * jm.floatValue * 700f }
        if (role == "arena" && !simulator) {
          // cliente: recibe, suaviza, manda su dedo y vigila el candado
          w.smooth(dt)
          sendT += dt
          if (sendT >= 0.1f) {
            sendT = 0f
            val key = "${tgx.toInt() / 6},${tgy.toInt() / 6},${ctrl.split},${ctrl.eject},${ctrl.respawn}"
            val nowMs = SystemClock.elapsedRealtime()
            if (key != lastSent || nowMs - sentAt > 1000L) {
              lastSent = key; sentAt = nowMs
              c.vm.publishGame("agarIn_${c.me}", mapOf("tx" to tgx.toLong(), "ty" to tgy.toLong(), "sp" to ctrl.split.toLong(), "ej" to ctrl.eject, "rs" to ctrl.respawn.toLong(), "nm" to st.nick.ifBlank { "Célula" }, "co" to st.color.toLong(), "ts" to nowSrv()))
            }
          }
          electT -= dt
          if (electT <= 0f && !claiming) {
            electT = 0.6f + jitter
            val lk = lockNow
            val owner = (lk?.get("owner") as? String).orEmpty()
            val beat = (lk?.get("beat") as? Number)?.toLong() ?: 0L
            // el dueño está vivo mientras su «latido» siga cambiando: se mide con el reloj local, sin comparar relojes entre celulares
            if (beat != lastBeatSeen) { lastBeatSeen = beat; lastBeatAt = SystemClock.elapsedRealtime() }
            if (owner.isEmpty() || SystemClock.elapsedRealtime() - lastBeatAt > 6000L) {
              claiming = true
              val seenBeat = lastBeatSeen
              c.vm.gameTransact("agarLock", true,
                { cur -> val o = (cur["owner"] as? String).orEmpty(); val b = (cur["beat"] as? Number)?.toLong() ?: 0L; o.isEmpty() || o == c.me || b == seenBeat },
                { mapOf("owner" to c.me, "beat" to GameOp.ServerTime) }
              ) { ok, _ -> claiming = false; if (ok && !simulator) promote() }
            }
          }
        } else {
          // simulador (práctica local, espectador o anfitrión de la arena)
          val mp = me()
          if (mp != null && mp.alive) {
            mp.tx = tgx; mp.ty = tgy
            if (ctrl.split != seenSplit) { seenSplit = ctrl.split; mp.wantSplit = true }
            mp.ejectHeld = ctrl.eject
          }
          if (role == "arena") {
            presT -= dt
            if (presT <= 0f) {
              presT = 0.25f
              val nowS = System.currentTimeMillis()
              if (w.players.none { it.uid == c.me }) { val np = w.newPlayer(st.nick.ifBlank { "Célula" }, st.color, c.me); w.spawn(np) }
              for (uid in membersNow) {
                if (uid == c.me) continue
                val inp = inputs[uid]
                val bye = inp?.get("bye") == true
                val present = inp != null && !bye && nowS - (recvAt[uid] ?: 0L) < 8000L
                val existing = w.players.firstOrNull { it.uid == uid }
                if (present && inp != null) {
                  lastSeen[uid] = nowS
                  if (existing == null) {
                    val np = w.newPlayer(((inp["nm"] as? String) ?: c.nameOf(uid)).take(15), ((inp["co"] as? Number)?.toInt() ?: 0).coerceIn(0, 11), uid)
                    lastResp[uid] = (inp["rs"] as? Number)?.toInt() ?: 0; lastSplit[uid] = (inp["sp"] as? Number)?.toInt() ?: 0
                    w.spawn(np)
                  }
                } else if (existing != null && (bye || nowS - (lastSeen[uid] ?: 0L) > 10000L)) { w.removePlayer(existing); lastSeen.remove(uid) }
              }
            }
            for (gp in w.players) {
              if (gp.uid.isEmpty() || gp.uid == c.me) continue
              val inp = inputs[gp.uid] ?: continue
              (inp["nm"] as? String)?.let { if (it != gp.name) gp.name = it.take(15) }
              (inp["co"] as? Number)?.let { gp.color = it.toInt().coerceIn(0, 11) }
              if (gp.alive) {
                (inp["tx"] as? Number)?.let { gp.tx = it.toFloat() }; (inp["ty"] as? Number)?.let { gp.ty = it.toFloat() }
                val sp = (inp["sp"] as? Number)?.toInt() ?: 0
                if (sp != (lastSplit[gp.uid] ?: sp)) gp.wantSplit = true
                lastSplit[gp.uid] = sp
                gp.ejectHeld = inp["ej"] == true
                lastResp[gp.uid] = (inp["rs"] as? Number)?.toInt() ?: 0
              } else {
                val rs = (inp["rs"] as? Number)?.toInt() ?: 0
                if (rs != (lastResp[gp.uid] ?: rs)) w.spawn(gp)
                lastResp[gp.uid] = rs
                lastSplit[gp.uid] = (inp["sp"] as? Number)?.toInt() ?: 0
              }
            }
          }
          w.update(dt)
          if (role == "arena") {
            pubT += dt
            if (pubT >= 0.2f) { pubT = 0f; c.vm.publishGame("agar", w.encode()) }
            beatT += dt
            if (beatT >= 1f) {
              beatT = 0f
              c.vm.gameTransact("agarLock", true, { (it["owner"] as? String) == c.me }, { mapOf("beat" to GameOp.ServerTime) }) { ok, err -> if (!ok && err == null) demote() }
            }
          }
        }
        // cámara: sigue a tu célula (o al líder) con zoom según el tamaño total
        val follow: AgarPlayer? = if (alive) m else w.leaderboard(1).firstOrNull()
        if (follow != null && follow.cells.isNotEmpty()) {
          val k = 1f - (1f - 0.12f).pow(dt * 60f)
          cam.x += (follow.cx() - cam.x) * k; cam.y += (follow.cy() - cam.y) * k
          val base = min(64f / max(follow.totalSize, 1f), 1f).pow(0.4f) * (min(canvasW, canvasH) / 700f)
          cam.scale += (base - cam.scale) * (1f - (1f - 0.06f).pow(dt * 60f))
        }
        // efectos
        if (st.fx) {
          val hw = canvasW / 2f / cam.scale + 80f; val hh = canvasH / 2f / cam.scale + 80f
          fx.consume(w, rnd) { x, y -> x > cam.x - hw && x < cam.x + hw && y > cam.y - hh && y < cam.y + hh }
          fx.step(dt)
          cam.shx = (rnd.nextFloat() - 0.5f) * fx.shake * 30f; cam.shy = (rnd.nextFloat() - 0.5f) * fx.shake * 30f
          dangerT -= dt
          if (dangerT <= 0f) {
            dangerT = 0.1f
            var target = 0f
            if (alive) {
              val mine = m!!.cells.maxByOrNull { it.mass }!!
              for (q in w.players) if (q !== m && q.alive) for (cc in q.cells) {
                if (cc.mass >= mine.mass * A_EAT_RATIO) { val d = kotlin.math.hypot(cc.x - mine.x, cc.y - mine.y) - cc.size - mine.size; target = max(target, (1f - d / 650f).coerceIn(0f, 1f)) }
              }
            }
            fx.danger += (target - fx.danger) * 0.5f
          }
        } else { w.fx.clear(); cam.shx = 0f; cam.shy = 0f }
        // muerte
        if (alive) { everAlive = true; wasAlive = true }
        else if (wasAlive && !spectate) {
          wasAlive = false
          if (st.fx) { fx.burst(cam.x, cam.y, A_COLORS[st.color], 46, 420f, 10f, rnd); fx.ring(cam.x, cam.y, 420f, A_COLORS[st.color], 20f, 0.8f); fx.shake = 0.8f; fx.flash = 1f; fx.flashColor = Color(0xFFFF1744) }
        }
        if (!spectate && everAlive && !alive && !dead) dead = true
        if (alive && dead) dead = false
        hudT += dt
        if (toastT > 0f) toastT -= dt
        if (hudT > 0.25f) {
          hudT = 0f
          val sc = if (alive) m!!.totalMass.roundToInt() else 0
          score = sc
          board = w.leaderboard(10).map { Triple(it.name, it.totalMass.roundToInt(), it === m) }
          online = w.players.count { !it.bot && it.uid.isNotEmpty() && it.alive }
          if (!spectate && alive && sc > st.best) { st.best = sc }
          if (alive) {
            for (ms in listOf(100, 250, 500, 1000, 2500, 5000, 10000)) if (sc >= ms && milestone < ms) { milestone = ms; toast = "¡Masa $ms!"; toastT = 1.8f }
            if (m!!.kills > lastKills) { toast = if (m.lastKill.isNotEmpty()) "¡Devoraste a ${m.lastKill}!" else "¡Devoraste a otro jugador!"; toastT = 2f; if (st.fx) { fx.flash = 0.5f; fx.flashColor = Color.White } }
            lastKills = m.kills
          }
        }
        frame++
      }
    }
  }

  Box(Modifier.fillMaxSize()) {
    Canvas(
      Modifier.fillMaxSize().pointerInput(spectate) {
        if (spectate) return@pointerInput
        awaitEachGesture {
          val down = awaitFirstDown(requireUnconsumed = false)
          val origin = down.position
          joyOrigin = origin; joyKnob = origin
          val maxR = 70.dp.toPx()
          while (true) {
            val ev = awaitPointerEvent()
            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
            if (!ch.pressed) break
            val v = ch.position - origin
            val len = v.getDistance()
            jm.floatValue = (len / maxR).coerceIn(0f, 1f)
            if (len > 0.01f) { jx.floatValue = v.x / len; jy.floatValue = v.y / len }
            joyKnob = if (len > maxR) origin + v * (maxR / len) else ch.position
            ch.consume()
          }
          joyOrigin = null; jm.floatValue = 0f
        }
      }
    ) {
      canvasW = size.width; canvasH = size.height
      val tick = frame
      if (tick < 0) return@Canvas
      drawArena(world, cam, fx, st.dark, st.grid, st.mass, st.fx, pt, me()?.id ?: -1)
      joyOrigin?.let { o ->
        drawCircle(Color.White.copy(alpha = 0.12f), 70.dp.toPx(), o)
        drawCircle(Color.Black.copy(alpha = 0.2f), 70.dp.toPx(), o, style = Stroke(2.dp.toPx()))
        drawCircle(Color.White.copy(alpha = 0.6f), 26.dp.toPx(), joyKnob)
        drawCircle(Color.Black.copy(alpha = 0.3f), 26.dp.toPx(), joyKnob, style = Stroke(2.dp.toPx()))
      }
    }
    val ink = if (st.dark) Color.White else Color(0xFF1B1B1B)
    Column(
      Modifier.align(Alignment.TopEnd).padding(8.dp).width(150.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x77000000)).padding(8.dp),
      verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
      Text("Clasificación", color = Color.White, fontWeight = FontWeight.Black, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
      board.forEachIndexed { i, (n, m, mine) ->
        Row {
          Text("${i + 1}. $n", color = if (mine) Color(0xFFFFEB3B) else Color.White, fontSize = 11.sp, fontWeight = if (mine) FontWeight.Black else FontWeight.Normal, maxLines = 1, modifier = Modifier.weight(1f))
          Text("$m", color = Color(0xFFCFD8DC), fontSize = 10.sp)
        }
      }
    }
    Row(Modifier.align(Alignment.TopStart).padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Box(Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0x77000000)).clickable { onMenu() }.padding(horizontal = 10.dp, vertical = 6.dp)) { Text("☰", color = Color.White, fontSize = 15.sp) }
      if (!spectate) Box(Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0x77000000)).padding(horizontal = 10.dp, vertical = 6.dp)) {
        Text("Masa: $score", color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp)
      } else Text("👀 Espectando", color = ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
      if (role == "arena") Text("🌐 Arena · 👥 $online", color = ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
    if (toastT > 0f) Box(Modifier.align(Alignment.TopCenter).padding(top = 64.dp)) {
      val sc = (0.8f + 0.2f * min(1f, toastT * 3f))
      Text(toast, color = Color.White, fontWeight = FontWeight.Black, fontSize = (20f * sc).sp, modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(Color(0x99000000)).padding(horizontal = 16.dp, vertical = 8.dp))
    }
    if (!spectate && !dead) {
      var ejectDown by remember { mutableStateOf(false) }
      Column(Modifier.align(Alignment.BottomEnd).padding(14.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
          Modifier.size(62.dp).clip(CircleShape).background(if (ejectDown) Color(0xDD1565C0) else Color(0x991E88E5)).border(2.dp, Color.White.copy(alpha = 0.7f), CircleShape)
            .pointerInput(Unit) { detectTapGestures(onPress = { ejectDown = true; ctrl.eject = true; tryAwaitRelease(); ejectDown = false; ctrl.eject = false }) },
          contentAlignment = Alignment.Center
        ) { Text("●", color = Color.White, fontSize = 22.sp) }
        Box(
          Modifier.size(84.dp).clip(CircleShape).background(Color(0xBBE53935)).border(2.dp, Color.White.copy(alpha = 0.8f), CircleShape)
            .pointerInput(Unit) { detectTapGestures(onPress = { ctrl.split++; tryAwaitRelease() }) },
          contentAlignment = Alignment.Center
        ) { Text("DIVIDIR", color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp) }
      }
    }
    if (role == "arena" && world.players.isEmpty()) {
      Box(Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.Center) { Text("Entrando a la arena…", color = Color.White, fontWeight = FontWeight.Bold) }
    }
    if (dead) {
      val m = me()
      Box(Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.Center) {
        Column(
          Modifier.padding(24.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xF2FFFFFF)).padding(20.dp),
          horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Text(if (m?.killedBy != null) "¡${m.killedBy} te comió!" else "Te comieron", color = Color(0xFFC62828), fontWeight = FontWeight.Black, fontSize = 22.sp, textAlign = TextAlign.Center)
          if (m != null) {
            val secs = (m.diedAt - m.bornAt).roundToInt().coerceAtLeast(0)
            Text("Masa máxima: ${m.maxMass.roundToInt()}", color = Color(0xFF263238), fontWeight = FontWeight.Bold)
            Text("Tiempo con vida: ${secs / 60}:${(secs % 60).toString().padStart(2, '0')}", color = Color(0xFF263238))
            Text("Comida: ${m.pellets} · Células comidas: ${m.kills}", color = Color(0xFF263238))
          }
          if (st.best > 0) Text("🏆 Mejor masa: ${st.best}", color = Color(0xFF455A64), fontSize = 13.sp)
          Spacer(Modifier.height(6.dp))
          Box(
            Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF34C759)).clickable {
              when (role) { "arena" -> { if (simulator) { me()?.let { world.spawn(it) } } else ctrl.respawn++; dead = false }; else -> onReplay() }
            }, contentAlignment = Alignment.Center
          ) { Text("JUGAR DE NUEVO", color = Color.White, fontWeight = FontWeight.Black) }
          Box(Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFECEFF1)).clickable { onMenu() }, contentAlignment = Alignment.Center) {
            Text("Menú", color = Color(0xFF37474F), fontWeight = FontWeight.Bold)
          }
        }
      }
    }
  }
}
