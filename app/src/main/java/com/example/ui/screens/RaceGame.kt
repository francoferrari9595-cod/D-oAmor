package com.example.ui.screens

import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.GlassCard
import com.example.ui.components.GradientButton
import com.example.ui.components.OptionChip
import com.example.ui.components.SoftButton
import com.example.ui.theme.GoldYellow
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

// ───────────────────── Carreras 3D (pseudo-3D estilo arcade) · hasta 6 humanos + bots ─────────────────────
//  · Cada teléfono simula su propio coche y publica su posición ~3 veces por segundo (subcolección "race").
//  · Los bots son deterministas (fórmula cerrada según el tiempo de carrera): todos ven los mismos bots.
internal const val SEG_LEN = 200f
internal const val ROAD_W = 2000f
internal const val CAM_H = 1000f
internal const val CAM_DEPTH = 0.84f
internal const val CAM_BACK = CAM_H * CAM_DEPTH
internal const val DRAW_N = 140
internal const val MAX_SPEED = 9500f
internal const val LAPS = 3

internal class RSeg(val curve: Float, val y: Float)

internal val RACE_TRACK: List<RSeg> by lazy {
  val segs = ArrayList<RSeg>()
  fun sec(n: Int, curve: Float, hill: Float) {
    val y0 = segs.lastOrNull()?.y ?: 0f
    for (i in 0 until n) {
      val t = i / n.toFloat()
      val ease = ((1.0 - cos(t * PI)) / 2.0).toFloat()
      val edge = sin(t * PI).toFloat().coerceIn(0f, 1f)
      segs.add(RSeg(curve * min(1f, edge * 2.2f), y0 + hill * ease))
    }
  }
  sec(90, 0f, 0f); sec(110, 2.6f, 0f); sec(60, 0f, 2600f); sec(90, -3.2f, 0f); sec(70, 0f, -2600f)
  sec(120, 4.2f, 0f); sec(60, 0f, 0f); sec(100, -2.4f, 1800f); sec(80, 3.4f, -1800f); sec(110, -4.4f, 0f)
  sec(80, 0f, 0f); sec(90, 2.0f, 0f); sec(60, -2.0f, 0f); sec(80, 0f, 0f)
  segs
}

/** Obstáculo fijo de la pista (igual para todos los teléfonos): 0 cono, 1 barrera, 2 pad de turbo. */
internal class ROb(val lane: Float, val type: Int)

internal val ROBS_BY_SEG: Map<Int, List<ROb>> = emptyMap() // Pista limpia: sin conos, barreras ni objetos que bloqueen la carrera.

/** Una sección de pista: cantidad de segmentos, curva (− izquierda / + derecha) y desnivel. */
private class RSec(val n: Int, val curve: Float, val hill: Float)

private fun buildTrack(spec: List<RSec>): List<RSeg> {
  val segs = ArrayList<RSeg>()
  for (sc in spec) {
    val y0 = segs.lastOrNull()?.y ?: 0f
    for (i in 0 until sc.n) {
      val t = i / sc.n.toFloat()
      val ease = ((1.0 - cos(t * PI)) / 2.0).toFloat()
      val edge = sin(t * PI).toFloat().coerceIn(0f, 1f)
      segs.add(RSeg(sc.curve * min(1f, edge * 2.2f), y0 + sc.hill * ease))
    }
  }
  return segs
}

private fun secs(vararg v: Float): List<RSec> = v.toList().chunked(3).map { RSec(it[0].toInt(), it[1], it[2]) }

/** Los 10 circuitos nuevos (se suman a los 3 originales). Cada uno tiene su propio carácter. */
private val EXTRA_TRACKS: List<List<RSeg>> by lazy {
  listOf(
    // Autopista nocturna: rectas largas y curvas suaves
    secs(150f, 0f, 0f, 120f, 1.6f, 0f, 100f, 0f, 1200f, 140f, -1.8f, 0f, 100f, 0f, -1200f, 160f, 2.2f, 0f, 120f, 0f, 0f, 130f, -2.0f, 0f, 100f, 1.4f, 0f, 120f, 0f, 0f),
    // Serpiente de montaña: curvas seguidas a izquierda y derecha
    secs(70f, 0f, 0f, 65f, 3.6f, 0f, 65f, -3.6f, 900f, 65f, 3.4f, 0f, 65f, -3.4f, -900f, 65f, 3.8f, 0f, 65f, -3.8f, 1200f, 65f, 3.2f, 0f, 65f, -3.2f, -1200f, 65f, 3.6f, 0f, 65f, -3.6f, 0f, 70f, 0f, 0f, 65f, 3.4f, 0f, 65f, -3.4f, 0f, 80f, 0f, 0f),
    // Óvalo veloz: dos grandes curvas y rectas rápidas
    secs(220f, 0f, 0f, 160f, 3.2f, 0f, 220f, 0f, 0f, 160f, 3.2f, 0f),
    // Costa sinuosa: curvas amplias y ondulaciones
    secs(90f, 0f, 0f, 100f, 2.0f, 600f, 100f, -2.2f, -600f, 110f, 2.4f, 900f, 100f, -1.8f, -900f, 100f, 2.6f, 0f, 90f, -2.4f, 500f, 100f, 1.8f, -500f, 110f, 0f, 0f),
    // Colinas rusas: grandes subidas y bajadas
    secs(80f, 0f, 0f, 90f, 1.2f, 3000f, 80f, 0f, -3000f, 90f, -1.6f, 2800f, 80f, 0f, -2800f, 100f, 2.0f, 3000f, 80f, 0f, -3000f, 100f, -1.8f, 0f, 100f, 0f, 2600f, 80f, 0f, -2600f),
    // Chicane urbana: eses cortos entre rectas
    secs(90f, 0f, 0f, 40f, 3.8f, 0f, 40f, -3.8f, 0f, 40f, 3.8f, 0f, 40f, -3.8f, 0f, 110f, 0f, 0f, 110f, 3.0f, 0f, 50f, 0f, 0f, 40f, -3.6f, 0f, 40f, 3.6f, 0f, 40f, -3.6f, 0f, 110f, 0f, 0f, 110f, 3.0f, 0f, 80f, 0f, 0f),
    // Desierto rápido: rectas enormes y dunas
    secs(220f, 0f, 0f, 150f, 2.2f, 600f, 200f, 0f, -600f, 160f, -2.4f, 500f, 200f, 0f, -500f, 150f, 2.0f, 0f, 140f, 0f, 0f, 140f, -1.6f, 0f),
    // Cañón: curvas cerradas con desniveles
    secs(80f, 0f, 0f, 100f, -3.0f, 2200f, 80f, 0f, 0f, 100f, 3.2f, -2200f, 70f, 0f, 0f, 110f, -3.4f, 1800f, 70f, 0f, -1800f, 100f, 3.0f, 0f, 80f, -2.4f, 2400f, 80f, 0f, -2400f),
    // Horquillas: curvas muy cerradas que exigen frenar
    secs(100f, 0f, 0f, 70f, 4.4f, 0f, 120f, 0f, 800f, 70f, -4.4f, 0f, 120f, 0f, -800f, 70f, 4.4f, 0f, 100f, 0f, 0f, 70f, -4.4f, 0f, 110f, 0f, 0f, 70f, 4.4f, 0f, 100f, 0f, 0f),
    // Gran Premio: el más largo, mezcla de todo
    secs(120f, 0f, 0f, 100f, 2.8f, 0f, 80f, 0f, 1600f, 90f, -3.0f, 0f, 70f, 0f, -1600f, 110f, 3.6f, 0f, 60f, 0f, 0f, 60f, -3.4f, 0f, 60f, 3.4f, 0f, 100f, 0f, 0f, 110f, -2.6f, 1400f, 90f, 3.0f, -1400f, 120f, 0f, 0f, 100f, -3.8f, 0f, 110f, 2.4f, 0f, 100f, 0f, 0f)
  ).map { buildTrack(it) }
}

/** Nombres de las pistas: las 3 originales y las 10 nuevas. */
internal val RACE_TRACK_NAMES = listOf(
  "Costa suave", "Montaña técnica", "Circuito urbano",
  "Autopista nocturna", "Serpiente de montaña", "Óvalo veloz", "Costa sinuosa", "Colinas rusas",
  "Chicane urbana", "Desierto rápido", "Cañón", "Horquillas", "Gran Premio"
)
internal val RACE_TRACK_MAX = RACE_TRACK_NAMES.size - 1

/** La longitud de cada pista puede ser distinta: la meta y los bots se calculan con la pista elegida, que viaja en el estado compartido. */
internal fun raceTrack(index: Int): List<RSeg> = when (val i = index.coerceIn(0, RACE_TRACK_MAX)) {
  1 -> RACE_TRACK.mapIndexed { k, s -> RSeg(s.curve * (if (k % 170 < 85) 1.38f else 0.82f), s.y * 1.35f) }
  2 -> RACE_TRACK.mapIndexed { k, s -> RSeg(s.curve * (if (k % 120 < 60) -0.92f else 1.12f), s.y * 0.48f) }
  0 -> RACE_TRACK
  else -> EXTRA_TRACKS[i - 3]
}

/**
 * Mapa esquemático de la pista como circuito cerrado: la curvatura acumulada se detrae de su tendencia y se usa como
 * variación del radio de un óvalo, así cada pista tiene su silueta y siempre cierra la vuelta. Devuelve pares x,y en 0..1.
 */
internal fun trackMapPoints(track: List<RSeg>, m: Int = 180): FloatArray {
  val n = track.size
  if (n < 8) return FloatArray(0)
  val cum = FloatArray(n + 1)
  for (i in 0 until n) cum[i + 1] = cum[i] + track[i].curve
  val h = FloatArray(n + 1) { cum[it] - cum[n] * it / n }
  val k = max(2, n / 40)
  val hs = FloatArray(m)
  for (i in 0 until m) {
    val j = (i.toLong() * n / m).toInt()
    var sum = 0f
    for (d in -k..k) sum += h[((j + d) % n + n) % n]
    hs[i] = sum / (2 * k + 1)
  }
  val mean = hs.average().toFloat()
  var amp = 0f
  for (i in 0 until m) { hs[i] -= mean; amp = max(amp, abs(hs[i])) }
  if (amp < 1e-4f) amp = 1f
  val xs = FloatArray(m); val ys = FloatArray(m)
  for (i in 0 until m) {
    val ang = (2.0 * PI * i / m - PI / 2).toFloat()
    val r = 1f + 0.38f * hs[i] / amp
    xs[i] = cos(ang) * r * 1.25f; ys[i] = sin(ang) * r * 0.95f
  }
  val minX = xs.minOrNull() ?: 0f; val maxX = xs.maxOrNull() ?: 1f; val minY = ys.minOrNull() ?: 0f; val maxY = ys.maxOrNull() ?: 1f
  val sx = maxX - minX; val sy = maxY - minY
  val sc = 1f / max(sx, sy)
  val out = FloatArray(m * 2)
  for (i in 0 until m) {
    out[2 * i] = (xs[i] - minX) * sc + (1f - sx * sc) / 2f
    out[2 * i + 1] = (ys[i] - minY) * sc + (1f - sy * sc) / 2f
  }
  return out
}

internal val HUMAN_COLS = listOf(
  Color(0xFF00E5FF), Color(0xFFFF4D8D), Color(0xFF7CFF6B), Color(0xFFFFD60A), Color(0xFFB388FF), Color(0xFFFF9500)
)
internal val BOT_COLS = listOf(
  Color(0xFFE0E0E0), Color(0xFF34C759), Color(0xFFAF52DE), Color(0xFFFF6D00), Color(0xFF2979FF), Color(0xFFFF1744), Color(0xFF8D6E63), Color(0xFF00BFA5)
)

/** Un coche que no es el mío: bot (fórmula cerrada) o humano remoto (posición recibida, suavizada). */
internal class RCar(val key: String, val name: String, var color: Color, val isBot: Boolean) {
  var total = 0f; var x = 0f; var speed = 0f; var finT = 0f
  var grid = 0f; var factor = 0.9f; var ph = 0f; var ph2 = 0f; var lane0 = 0f
  var shownTotal = 0f; var shownX = 0f; var recvMs = 0L
  fun pos(): Float = if (isBot) total else shownTotal
  fun lane(): Float = if (isBot) x else shownX
}

/** Partícula de pantalla (posición en fracciones del ancho/alto): 0 humo, 1 chispa, 2 polvo, 3 brasa de nitro. */
internal class RPart(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val maxLife: Float, val kind: Int, val size: Float)

internal class RStand(val name: String, val color: Color, val me: Boolean, val finT: Float, val dist: Float)

internal class RaceSim {
  var left = false; var right = false; var brake = false; var nitroOn = false
  var phase by mutableStateOf(0) // 0 menú, 1 cuenta atrás, 2 carrera, 3 meta
  var rid = -1
  var trackIndex = 0
  var track: List<RSeg> = RACE_TRACK
  /** Pista recorrida al revés (para mirar hacia atrás): curvas con el signo cambiado y en orden inverso. */
  var revTrack: List<RSeg> = RACE_TRACK.asReversed().map { RSeg(-it.curve, it.y) }
  /** Silueta del circuito para el minimapa (pares x,y en 0..1). */
  var mapPts: FloatArray = FloatArray(0)
  /** Mientras se mantiene tocada la franja de arriba al centro, la cámara mira hacia atrás. */
  var lookBack by mutableStateOf(false)
  var countdown = 3.2f
  var total = 0f; var x = 0f; var speed = 0f; var nitro = 1f
  var time = 0f; var finishTime = 0f
  var shake = 0f; var steer = 0f; var bg = 0f; var clock = 0f
  var postPub = 0
  var boostT = 0f; var prevAbs = 0
  var boostK = 0f; var smokeT = 0f
  val parts = ArrayList<RPart>()
  val hits = HashSet<Long>()
  fun boosting(): Boolean = phase == 2 && ((nitroOn && nitro > 0.02f) || boostT > 0f)
  var myName = "Tú"; var myColor = HUMAN_COLS[0]
  val cars = ArrayList<RCar>()
  val trackLen: Float get() = track.size * SEG_LEN
  val goal: Float get() = LAPS * trackLen
  /** Posición sobre la pista invertida que corresponde a mi posición real (cámara mirando hacia atrás). */
  fun backPos(): Float { val tl = trackLen; return tl - (total.coerceAtLeast(0f) % tl) }

  fun reset(newRid: Int, humans: List<String>, bots: Int, diff: Float, me: String, nameOf: (String) -> String, selectedTrack: Int = 0) {
    rid = newRid
    trackIndex = selectedTrack.coerceIn(0, RACE_TRACK_MAX)
    track = raceTrack(trackIndex)
    revTrack = track.asReversed().map { RSeg(-it.curve, it.y) }
    mapPts = trackMapPoints(track)
    lookBack = false
    phase = 1; countdown = 3.2f; speed = 0f; nitro = 1f; time = 0f; finishTime = 0f; shake = 0f; steer = 0f; postPub = 8
    left = false; right = false; brake = false; nitroOn = false
    boostT = 0f; hits.clear()
    cars.clear()
    val n = humans.size + bots
    val rows = (n + 1) / 2
    fun startOf(slot: Int) = (rows - 1 - slot / 2) * 200f
    fun laneOf(slot: Int) = if (slot % 2 == 0) -0.45f else 0.45f
    humans.forEachIndexed { i, uid ->
      if (uid == me) {
        total = startOf(i); x = laneOf(i); myColor = myCol ?: HUMAN_COLS[i % HUMAN_COLS.size]; myName = nameOf(uid)
      } else {
        val r = RCar(uid, nameOf(uid), HUMAN_COLS[i % HUMAN_COLS.size], false)
        r.total = startOf(i); r.shownTotal = r.total; r.x = laneOf(i); r.shownX = r.x
        cars.add(r)
      }
    }
    for (j in 0 until bots) {
      val slot = humans.size + j
      val b = RCar("bot$j", "Bot ${j + 1}", BOT_COLS[j % BOT_COLS.size], true)
      b.grid = startOf(slot); b.total = b.grid; b.lane0 = laneOf(slot); b.x = b.lane0
      b.factor = diff - j * 0.018f; b.ph = j * 1.7f; b.ph2 = j * 2.3f
      cars.add(b)
    }
    prevAbs = (total / SEG_LEN).toInt()
  }

  /** Recibe las posiciones publicadas por los otros teléfonos. */
  var myCol: Color? = null

  fun applyRemote(map: Map<String, Map<String, Any?>>, nowMs: Long) {
    for (c in cars) {
      if (c.isBot) continue
      val d = map[c.key] ?: continue
      if ((d["rid"] as? Number)?.toInt() != rid) continue
      c.total = (d["total"] as? Number)?.toFloat() ?: c.total
      c.x = (d["x"] as? Number)?.toFloat() ?: c.x
      c.speed = (d["speed"] as? Number)?.toFloat() ?: c.speed
      c.finT = (d["fin"] as? Number)?.toFloat() ?: 0f
      (d["col"] as? Number)?.toLong()?.let { c.color = Color(it.toInt()) }
      c.recvMs = nowMs
    }
  }

  private fun updateOthers(dt: Float, nowMs: Long) {
    val w = 0.7f
    for (b in cars) {
      if (b.isBot) {
        val t = time
        b.total = b.grid + MAX_SPEED * b.factor * (t + 0.04f * (cos(b.ph) - cos(w * t + b.ph)) / w)
        b.speed = MAX_SPEED * b.factor * (1f + 0.04f * sin(w * t + b.ph))
        val ramp = min(1f, t / 5f)
        b.x = (b.lane0 + 0.35f * ramp * sin(0.45f * t + b.ph2)).coerceIn(-0.85f, 0.85f)
        if (b.finT <= 0f && b.total >= goal) b.finT = max(0.1f, t)
      } else {
        val age = (nowMs - b.recvMs).coerceIn(0L, 900L) / 1000f
        val target = if (b.recvMs == 0L) b.total else b.total + b.speed * age
        b.shownTotal += (target - b.shownTotal) * min(1f, dt * 8f)
        b.shownX += (b.x - b.shownX) * min(1f, dt * 8f)
      }
    }
  }

  fun spawn(x: Float, y: Float, vx0: Float, kind: Int) {
    if (parts.size > 140) return
    val r = kotlin.random.Random
    val (vx, vy, life, sz) = when (kind) {
      0 -> listOf(r.nextFloat() * 0.1f - 0.05f, -0.04f - r.nextFloat() * 0.05f, 0.7f, 0.05f)
      1 -> listOf(vx0 + (r.nextFloat() - 0.5f) * 0.6f, -0.3f - r.nextFloat() * 0.5f, 0.45f, 0.012f)
      2 -> listOf((r.nextFloat() - 0.5f) * 0.2f, -0.06f - r.nextFloat() * 0.05f, 0.8f, 0.06f)
      else -> listOf((r.nextFloat() - 0.5f) * 0.1f, 0.25f + r.nextFloat() * 0.3f, 0.35f, 0.02f)
    }
    parts.add(RPart(x + (r.nextFloat() - 0.5f) * 0.05f, y, vx, vy, life, life, kind, sz))
  }

  fun standings(): List<RStand> {
    val l = ArrayList<RStand>()
    l.add(RStand(myName, myColor, true, if (phase == 3) finishTime else 0f, total))
    for (c in cars) l.add(RStand(c.name, c.color, false, c.finT, c.pos()))
    return l.sortedWith(compareBy<RStand>({ if (it.finT > 0f) 0 else 1 }, { if (it.finT > 0f) it.finT else -it.dist }))
  }
  fun rank(): Int = standings().indexOfFirst { it.me } + 1

  fun step(dt: Float, nowMs: Long) {
    clock += dt
    if (phase != 2 && lookBack) lookBack = false
    if (phase == 1) { countdown -= dt; if (countdown <= 0f) phase = 2; updateOthers(dt, nowMs); return }
    if (phase != 2 && phase != 3) return
    time += dt
    val n = track.size
    val seg = track[(total / SEG_LEN).toInt().coerceAtLeast(0) % n]
    val pct = speed / MAX_SPEED
    if (phase == 2) {
      val nitroActive = nitroOn && nitro > 0.02f
      val boosting = nitroActive || boostT > 0f
      boostT = max(0f, boostT - dt)
      val limit = MAX_SPEED * (if (boosting) 1.32f else 1f)
      val acc = (MAX_SPEED / 4.2f) * (if (boosting) 2.4f else 1f)
      if (nitroActive) nitro = max(0f, nitro - dt * 0.33f) else nitro = min(1f, nitro + dt * 0.04f)
      if (brake) speed -= MAX_SPEED / 1.5f * dt else if (speed < limit) speed += acc * dt else speed -= MAX_SPEED / 5f * dt
      // dirección progresiva: mantener = giro más cerrado; en curvas rápidas la inercia te empuja hacia afuera
      steer += ((if (left) -1f else if (right) 1f else 0f) - steer) * min(1f, dt * 7f)
      val grip = min(1f, pct * 6f + 0.15f)
      x += dt * 2.7f * (0.45f + pct * 0.8f) * steer * grip
      x -= dt * 2.1f * pct * pct * seg.curve * 0.30f
      if ((x < -1.05f || x > 1.05f) && speed > MAX_SPEED * 0.28f) {
        speed -= MAX_SPEED / 1.8f * dt; shake = 1f
        val sx = if (x < 0f) 0.30f else 0.70f
        for (q in 0 until 2) spawn(sx, 0.90f, if (x < 0f) 0.25f else -0.25f, 1)
        spawn(0.5f, 0.95f, 0f, 2)
      }
      boostK += ((if (boosting()) 1f else 0f) - boostK) * min(1f, dt * 5f)
      smokeT -= dt
      if (smokeT <= 0f) {
        smokeT = 0.045f
        if (abs(steer) > 0.55f && pct > 0.45f) { spawn(0.40f, 0.94f, 0f, 0); spawn(0.60f, 0.94f, 0f, 0) }
        if (boosting()) spawn(0.5f, 0.88f, 0f, 3)
      }
      x = x.coerceIn(-2.1f, 2.1f)
      speed = speed.coerceIn(0f, limit)
      total += speed * dt
      bg += seg.curve * pct * dt * 70f
      val absNow = (total / SEG_LEN).toInt()
      for (a in (prevAbs + 1)..absNow) {
        val list = ROBS_BY_SEG[a % track.size] ?: continue
        list.forEachIndexed { j, o ->
          val key = a.toLong() * 8 + j
          val reach = if (o.type == 1) 0.34f else if (o.type == 0) 0.17f else 0.24f
          if (!hits.contains(key) && abs(x - o.lane) < reach) {
            hits.add(key)
            when (o.type) {
              0 -> { speed *= 0.74f; shake = 1f; for (q in 0 until 6) spawn(0.5f, 0.85f, 0f, 1) }
              1 -> { speed *= 0.42f; shake = 1.4f; for (q in 0 until 12) spawn(0.5f, 0.85f, 0f, 1) }
              else -> { boostT = 1.5f; nitro = min(1f, nitro + 0.2f); speed = min(MAX_SPEED * 1.32f, speed + MAX_SPEED * 0.12f) }
            }
          }
        }
      }
      prevAbs = max(prevAbs, absNow)
    }
    updateOthers(dt, nowMs)
    if (phase == 2) {
      for (b in cars) {
        if (!b.isBot) continue
        val gap = b.total - total
        if (abs(gap) < 330f && abs(b.x - x) < 0.3f) {
          if (gap > 0f) speed = min(speed, b.speed * 0.9f)
          x += if (x >= b.x) 0.9f * dt else -0.9f * dt
          shake = 1f
        }
      }
    }
    shake = max(0f, shake - dt * 4f)
    boostK += ((if (boosting()) 1f else 0f) - boostK) * min(1f, dt * 2f) * (if (phase == 2) 0f else 1f)
    var pi = parts.size - 1
    while (pi >= 0) {
      val q = parts[pi]
      q.x += q.vx * dt; q.y += q.vy * dt; q.life -= dt
      if (q.kind == 1) q.vy += 1.1f * dt
      if (q.life <= 0f) parts.removeAt(pi)
      pi--
    }
    if (phase == 2 && total >= goal) { phase = 3; finishTime = time; postPub = 8 }
    if (phase == 3) { speed = max(0f, speed - MAX_SPEED / 2f * dt); total += speed * dt }
  }
}

/** Velocímetro: arco con marcas, zona roja, aguja y velocidad digital con marcha. */
@Composable
private fun RaceGauge(pct: Float, boost: Boolean) {
  val kmh = (pct * 240f).toInt().coerceAtLeast(0)
  val gear = (1 + (pct.coerceIn(0f, 1f) * 5.99f).toInt()).coerceIn(1, 6)
  Box(Modifier.size(104.dp), contentAlignment = Alignment.Center) {
    Canvas(Modifier.fillMaxSize()) {
      val c = Offset(size.width / 2f, size.height / 2f); val r = size.minDimension / 2f - 6f
      drawCircle(Color(0x99000000), r + 4f, c)
      val tl = Offset(c.x - r, c.y - r); val sz = Size(r * 2f, r * 2f)
      drawArc(Color(0x55FFFFFF), 150f, 240f, false, tl, sz, style = androidx.compose.ui.graphics.drawscope.Stroke(5f))
      drawArc(Color(0xFFFF3B3B), 150f + 240f * 0.76f, 240f * 0.24f, false, tl, sz, style = androidx.compose.ui.graphics.drawscope.Stroke(5f))
      for (i in 0..12) {
        val a = (150f + 240f * i / 12f) * (PI.toFloat() / 180f)
        val r1 = r - (if (i % 2 == 0) 13f else 8f)
        drawLine(Color.White.copy(alpha = 0.8f), Offset(c.x + cos(a) * r1, c.y + sin(a) * r1), Offset(c.x + cos(a) * (r - 3f), c.y + sin(a) * (r - 3f)), if (i % 2 == 0) 2.4f else 1.4f)
      }
      val f = (pct / 1.32f).coerceIn(0f, 1f)
      val na = (150f + 240f * f) * (PI.toFloat() / 180f)
      drawLine(if (boost) Color(0xFF00E5FF) else Color(0xFFFF5252), c, Offset(c.x + cos(na) * (r - 10f), c.y + sin(na) * (r - 10f)), 4f, androidx.compose.ui.graphics.StrokeCap.Round)
      drawCircle(Color(0xFF222222), 7f, c)
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 38.dp)) {
      Text("$kmh", color = Color(0xFF39FF88), fontWeight = FontWeight.Black, fontSize = 24.sp)
      Text("km/h · M$gear", color = Color.White, fontSize = 9.sp)
    }
  }
}

/** Minimapa semitransparente: silueta de la pista y un punto de color por cada piloto (el tuyo, blanco y más grande). */
@Composable
private fun RaceMiniMap(sim: RaceSim, modifier: Modifier = Modifier) {
  Canvas(modifier.size(78.dp)) {
    val pts = sim.mapPts
    val cnt = pts.size / 2
    if (cnt < 4) return@Canvas
    val pad = 9.dp.toPx(); val side = size.minDimension - pad * 2f
    fun pt(i: Int) = Offset(pad + pts[2 * i] * side, pad + pts[2 * i + 1] * side)
    drawRoundRect(Color(0x44000000), cornerRadius = CornerRadius(12.dp.toPx()))
    val path = Path()
    path.moveTo(pt(0).x, pt(0).y)
    for (i in 1 until cnt) { val q = pt(i); path.lineTo(q.x, q.y) }
    path.close()
    drawPath(path, Color(0x55FFFFFF), style = androidx.compose.ui.graphics.drawscope.Stroke(4.5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    drawPath(path, Color(0x889FB4FF), style = androidx.compose.ui.graphics.drawscope.Stroke(1.6.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    // línea de salida/meta
    val s0 = pt(0); drawCircle(Color(0xCCFFFFFF), 2.2.dp.toPx(), s0)
    val tl = sim.trackLen
    fun at(pos: Float): Offset {
      val u = (((pos % tl) + tl) % tl) / tl * cnt
      val i0 = floor(u).toInt() % cnt; val f = u - floor(u)
      val a = pt(i0); val b = pt((i0 + 1) % cnt)
      return Offset(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f)
    }
    for (b in sim.cars) drawCircle(b.color.copy(alpha = 0.95f), 3.dp.toPx(), at(b.pos()))
    val me = at(sim.total)
    drawCircle(Color.White, 5.2.dp.toPx(), me)
    drawCircle(sim.myColor, 3.4.dp.toPx(), me)
  }
}

@Composable
private fun RaceBtn(label: String, w: Int, onHold: (Boolean) -> Unit, color: Color = Color(0xAA1A0F2B)) {
  Box(
    Modifier.size(w.dp, 64.dp).clip(RoundedCornerShape(18.dp)).background(color).border(2.dp, Color(0xFFB388FF), RoundedCornerShape(18.dp))
      .pointerInput(Unit) { detectTapGestures(onPress = { onHold(true); tryAwaitRelease(); onHold(false) }) },
    contentAlignment = Alignment.Center
  ) { Text(label, color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp) }
}

@Composable
private fun StandRow(pos: Int, s: RStand, big: Boolean = false) {
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    Text("$pos", color = if (pos == 1) GoldYellow else Color.White, fontWeight = FontWeight.Black, fontSize = if (big) 15.sp else 11.sp, modifier = Modifier.width(if (big) 20.dp else 14.dp))
    Box(Modifier.size(if (big) 12.dp else 8.dp).clip(CircleShape).background(s.color))
    Text(
      s.name + if (big && s.finT > 0f) "  ${"%.1f".format(s.finT)} s" else "",
      color = if (s.me) GoldYellow else Color.White, fontWeight = if (s.me) FontWeight.Black else FontWeight.Medium,
      fontSize = if (big) 14.sp else 10.sp, maxLines = 1
    )
  }
}

@Composable
internal fun RaceGame(c: GameCtx) {
  val sim = remember { RaceSim() }
  val flow = remember { c.vm.gameState("race") }
  val st by flow.collectAsState(initial = null)
  var frame by remember { mutableStateOf(0) }
  var hud by remember { mutableStateOf(0) }
  var diff by remember { mutableStateOf(1) }
  val context = LocalContext.current
  val racePrefs = remember { context.getSharedPreferences("doamor_prefs", android.content.Context.MODE_PRIVATE) }
  var trackSel by remember { mutableStateOf(racePrefs.getInt("race_track", 0).coerceIn(0, RACE_TRACK_MAX)) }
  var carSel by remember { mutableStateOf(racePrefs.getInt("race_car_color", 0).coerceIn(0, 5)) }
  run {
    val rctx = androidx.compose.ui.platform.LocalContext.current
    val equipped = com.example.model.LocalWallet.equippedItem(rctx, com.example.model.Cat.CAR).takeIf { it.id != "car_cyan" }?.c1
    val selected = listOf(Color(0xFF00E5FF), Color(0xFFFF4D8D), Color(0xFF7CFF6B), Color(0xFFFFD60A), Color(0xFFB388FF), Color(0xFFFF9500))[carSel]
    sim.myCol = if (carSel == 0 && equipped != null) equipped else selected
  }
  var botsSel by remember { mutableStateOf(if (c.players.size <= 1) 3 else 0) }
  var best by remember { mutableStateOf(0f) }
  var ready by remember { mutableStateOf(false) }
  var seenRid by remember { mutableStateOf(0) }
  val isPlayer = c.players.contains(c.me)
  val diffs = listOf("😊 Fácil" to 0.80f, "🙂 Normal" to 0.88f, "🔥 Difícil" to 0.95f)
  val tagPaint = remember {
    android.graphics.Paint().apply {
      isAntiAlias = true; textAlign = android.graphics.Paint.Align.CENTER; color = android.graphics.Color.WHITE
      typeface = android.graphics.Typeface.DEFAULT_BOLD; setShadowLayer(5f, 0f, 2f, android.graphics.Color.BLACK)
    }
  }

  // Si ya hay una carrera vieja publicada, no se une a ella: solo a las que empiecen mientras estoy acá.
  LaunchedEffect(Unit) {
    val first = runCatching { flow.first() }.getOrNull()
    seenRid = (first?.get("rid") as? Number)?.toInt() ?: 0
    ready = true
  }
  val rid = (st?.get("rid") as? Number)?.toInt() ?: 0
  LaunchedEffect(rid, ready) {
    if (!ready || rid <= 0 || rid == seenRid) return@LaunchedEffect
    seenRid = rid
    val list = (st?.get("players") as? List<*>)?.mapNotNull { it as? String } ?: c.players
    val bots = ((st?.get("bots") as? Number)?.toInt() ?: 0).coerceIn(0, 6)
    val dIdx = ((st?.get("diff") as? Number)?.toInt() ?: 1).coerceIn(0, 2)
    val tIdx = ((st?.get("track") as? Number)?.toInt() ?: 0).coerceIn(0, RACE_TRACK_MAX)
    if (isPlayer && list.contains(c.me)) sim.reset(rid, list, bots, diffs[dIdx].second, c.me, { c.nameOf(it) }, tIdx)
  }

  // Bucle de simulación y dibujo.
  LaunchedEffect(Unit) {
    var last = 0L
    while (true) {
      androidx.compose.runtime.withFrameNanos { now ->
        val dt = if (last == 0L) 0.016f else ((now - last) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
        last = now
        sim.step(dt, System.currentTimeMillis())
        frame++
        if (frame % 6 == 0) hud++
      }
    }
  }
  // Recibe a los otros pilotos.
  LaunchedEffect(Unit) {
    c.vm.raceCars().collect { sim.applyRemote(it, System.currentTimeMillis()) }
  }
  // Publica mi posición ~3 veces por segundo.
  LaunchedEffect(Unit) {
    while (true) {
      delay(330)
      val live = sim.phase == 1 || sim.phase == 2 || (sim.phase == 3 && sim.postPub > 0)
      if (live && sim.rid > 0) {
        if (sim.phase == 3) sim.postPub--
        c.vm.publishRaceCar(
          mapOf(
            "rid" to sim.rid, "total" to sim.total.toDouble(), "x" to sim.x.toDouble(), "speed" to sim.speed.toDouble(),
            "fin" to (if (sim.phase == 3) sim.finishTime.toDouble() else 0.0), "name" to sim.myName, "col" to sim.myColor.toArgb().toLong(),
            "t" to System.currentTimeMillis()
          )
        )
      }
    }
  }
  LaunchedEffect(sim.phase) {
    if (sim.phase == 3) { if (best == 0f || sim.finishTime < best) best = sim.finishTime; if (sim.rank() == 1) c.vm.gameSfx(2) }
  }

  fun startRace() {
    val newRid = (System.currentTimeMillis() / 1000L).toInt()
    c.vm.publishGame("race", mapOf("rid" to newRid, "players" to c.players, "bots" to botsSel, "diff" to diff, "track" to trackSel))
  }

  if (sim.phase == 0) {
    val garageSpin by androidx.compose.animation.core.rememberInfiniteTransition(label = "garage").animateFloat(
      0f, 2f * PI.toFloat(), androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(9000, easing = androidx.compose.animation.core.LinearEasing)), label = "garageSpin"
    )
    GlassCard(modifier = Modifier.fillMaxWidth(), highlight = true) {
      Text("🏎️ KOMBO RACING", color = Color.White, fontWeight = FontWeight.Black, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, fontSize = 26.sp, letterSpacing = 1.sp)
      Text("Carreras callejeras 3D · $LAPS vueltas · hasta 6 pilotos y bots", color = Color(0xFFB9B0D0), fontSize = 12.sp, fontWeight = FontWeight.Bold)
      Spacer(Modifier.height(8.dp))
      // garaje: el auto gira para ver el color elegido
      Box(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(16.dp)).border(1.dp, Color(0x66B388FF), RoundedCornerShape(16.dp))) {
        Canvas(Modifier.fillMaxSize()) { drawCarShowcase(sim.myCol ?: HUMAN_COLS[0], garageSpin) }
      }
      Spacer(Modifier.height(8.dp))
      Text("Pilotos: " + c.players.joinToString(", ") { c.nameOf(it) }, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
      Spacer(Modifier.height(10.dp))
      Text("1 · PISTA", color = Color(0xFF00E5FF), fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
      Spacer(Modifier.height(4.dp))
      Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        RACE_TRACK_NAMES.forEachIndexed { i, name -> OptionChip(name, selected = (trackSel == i), onClick = { trackSel = i; racePrefs.edit().putInt("race_track", i).apply() }) }
      }
      // vista previa del circuito elegido
      val previewPts = remember(trackSel) { trackMapPoints(raceTrack(trackSel)) }
      Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Canvas(Modifier.size(86.dp).clip(RoundedCornerShape(14.dp)).background(Color(0x33000000))) {
          val cnt = previewPts.size / 2
          if (cnt >= 4) {
            val pad = 10.dp.toPx(); val side = size.minDimension - pad * 2f
            val path = Path()
            for (i in 0 until cnt) { val q = Offset(pad + previewPts[2 * i] * side, pad + previewPts[2 * i + 1] * side); if (i == 0) path.moveTo(q.x, q.y) else path.lineTo(q.x, q.y) }
            path.close()
            drawPath(path, Color(0x66FFFFFF), style = androidx.compose.ui.graphics.drawscope.Stroke(6.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            drawPath(path, Color(0xFF9FB4FF), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            drawCircle(Color.White, 3.dp.toPx(), Offset(pad + previewPts[0] * side, pad + previewPts[1] * side))
          }
        }
        Column {
          Text(RACE_TRACK_NAMES[trackSel.coerceIn(0, RACE_TRACK_MAX)], color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp)
          Text("${RACE_TRACK_NAMES.size} circuitos · deslizá las pistas para ver todas", color = Color(0xFFB9B0D0), fontSize = 11.sp)
        }
      }
      Spacer(Modifier.height(10.dp))
      Text("2 · COLOR DEL AUTO", color = Color(0xFF00E5FF), fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
      Spacer(Modifier.height(4.dp))
      Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(Color(0xFF00E5FF), Color(0xFFFF4D8D), Color(0xFF7CFF6B), Color(0xFFFFD60A), Color(0xFFB388FF), Color(0xFFFF9500)).forEachIndexed { i, col ->
          Box(Modifier.size(42.dp).clip(CircleShape).background(col).border(if (carSel == i) 3.dp else 1.dp, if (carSel == i) Color.White else Color(0x66FFFFFF), CircleShape).pointerInput(i) { detectTapGestures { carSel = i; racePrefs.edit().putInt("race_car_color", i).apply() } })
        }
      }
      Spacer(Modifier.height(10.dp))
      Text("3 · RIVALES", color = Color(0xFF00E5FF), fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
      Spacer(Modifier.height(4.dp))
      Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        diffs.forEachIndexed { i, d -> OptionChip(d.first, selected = (diff == i), onClick = { diff = i }) }
      }
      Spacer(Modifier.height(6.dp))
      Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Bots", color = Color(0xFFB9B0D0), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        val minBots = if (c.players.size <= 1) 1 else 0
        (minBots..6).forEach { n -> OptionChip("$n", selected = (botsSel == n), onClick = { botsSel = n }) }
      }
      Spacer(Modifier.height(6.dp))
      Text("Controles: mantené ◀ ▶ para girar · 🛑 frena · 🚀 nitro · mantené tocado arriba al centro para mirar atrás", color = Color(0xFFB9B0D0), fontSize = 11.sp)
      Spacer(Modifier.height(10.dp))
      if (isPlayer) {
        GradientButton("🏁 ¡A correr!", modifier = Modifier.fillMaxWidth(), onClick = { startRace() })
        if (c.players.size > 1) Text("Al tocar, todos los pilotos arrancan la cuenta atrás.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
      } else {
        Text("Estás mirando: esta carrera es para ${c.players.size} pilotos.", color = GoldYellow, fontSize = 12.sp)
      }
      if (best > 0f) Text("Mejor tiempo: ${"%.1f".format(best)} s", color = GoldYellow, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
    return
  }

  val hh = (gameAvail() - 120.dp).coerceIn(260.dp, 700.dp)
  Box(Modifier.fillMaxWidth().height(hh).clip(RoundedCornerShape(22.dp)).border(2.dp, Color(0x66B388FF), RoundedCornerShape(22.dp))) {
    Canvas(Modifier.fillMaxSize()) {
      frame
      drawRace(sim, tagPaint)
      drawRaceFx(sim.speed / MAX_SPEED, sim.boostK, sim.clock, sim.phase == 1)
    }
    hud
    val stand = sim.standings()
    val total = stand.size
    val speedPct = sim.speed / MAX_SPEED
    val boost = sim.boosting()
    // HUD superior: posición · vuelta · tiempo
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        RacePlate("POSICIÓN", "${sim.rank()}", if (sim.rank() == 1) GoldYellow else Color(0xFF00E5FF), valueSize = 30.sp, sub = "/$total")
        RacePlate("VUELTA", "${min(LAPS, (sim.total / sim.trackLen).toInt() + 1)}", Color(0xFFB388FF), valueSize = 20.sp, sub = "/$LAPS")
      }
      RacePlate("TIEMPO", "${"%.1f".format(sim.time)}", Color.White, valueSize = 20.sp, sub = "s")
      Column(horizontalAlignment = Alignment.End) { RaceMiniMap(sim) }
    }
    // Franja de arriba al centro: mantenela tocada para mirar hacia atrás (al soltar vuelve la vista de siempre).
    if (sim.phase == 2) {
      Box(
        Modifier.align(Alignment.TopCenter).fillMaxWidth(0.34f).height(58.dp)
          .pointerInput(Unit) { detectTapGestures(onPress = { sim.lookBack = true; tryAwaitRelease(); sim.lookBack = false }) }
      )
      if (sim.lookBack) {
        Text(
          "◀ MIRANDO ATRÁS", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp,
          modifier = Modifier.align(Alignment.TopCenter).padding(top = 62.dp).clip(RoundedCornerShape(50)).background(Color(0x99000000)).padding(horizontal = 10.dp, vertical = 3.dp)
        )
      }
    }
    // clasificación en vivo
    if (sim.phase != 3) Column(
      Modifier.align(Alignment.CenterStart).padding(start = 8.dp, top = 40.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x77000000)).padding(6.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp)
    ) { stand.take(6).forEachIndexed { i, s -> StandRow(i + 1, s) } }
    // velocímetro + luces de cambio + nitro
    if (sim.phase == 2) {
      Column(Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 80.dp), horizontalAlignment = Alignment.End) {
        RaceShiftLights(speedPct, boost)
        Spacer(Modifier.height(2.dp))
        RaceSpeedo(speedPct, boost)
      }
      RaceNitroBar(sim.nitro, boost, Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 84.dp).width(130.dp))
    }
    if (sim.phase == 1) RaceCountdown(sim.countdown, Modifier.align(Alignment.Center))
    if (sim.phase == 3) {
      RaceResult(sim.rank(), sim.finishTime, stand, onRematch = { startRace() }, onMenu = { sim.phase = 0 }, modifier = Modifier.align(Alignment.Center).padding(12.dp))
    }
    if (sim.phase == 1 || sim.phase == 2) {
      Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp, start = 10.dp, end = 10.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          RaceBtn("◀", 64, { sim.left = it })
          RaceBtn("▶", 64, { sim.right = it })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          RaceBtn("🛑", 56, { sim.brake = it }, Color(0xAA7A1020))
          RaceBtn("🚀", 64, { sim.nitroOn = it }, Color(0xAA0B5C7A))
        }
      }
    }
  }
}

