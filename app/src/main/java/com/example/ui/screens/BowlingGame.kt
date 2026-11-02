package com.example.ui.screens

import android.media.AudioAttributes
import com.example.model.BOWL_BALLS
import com.example.model.BowlBall
import com.example.model.Cosmetics
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.GlassCard
import com.example.ui.components.GradientButton
import com.example.ui.components.SoftButton
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ═════════════════════════════════════════════════════════════════════════════
// BOWLING 3D — hecho desde cero.
//  · Física propia en 2D (bola y 10 bolos como discos con masa, rebotes y rozamiento), idéntica en todos los
//    dispositivos: quien tira publica los parámetros y cada pantalla repite el mismo lanzamiento.
//  · Puntuación oficial de 10 frames (chuza, spare, décimo frame con hasta 3 tiros).
//  · Escena en perspectiva real con cámara que sigue a la bola, luces de neón, chispas y sonidos sintetizados.
// ═════════════════════════════════════════════════════════════════════════════

private const val BY0 = 18.29          // distancia de la línea de falta al bolo cabeza (m)
private const val BROW = 0.264         // separación entre filas de bolos (m)
private const val BSP = 0.3048         // separación lateral entre bolos (m)
private const val BHALF = 0.53         // mitad del ancho de la pista (m)
private const val BRB = 0.108          // radio de la bola (m)
private const val BRP = 0.06           // radio de un bolo en pie (m)
private const val BRF = 0.122          // radio de un bolo caído (m)
private const val BMB = 6.0            // masa de la bola (kg)
private const val BMP = 1.5            // masa de un bolo (kg)
private const val BDAMP = 1.35         // rozamiento de los bolos con la pista (1/s)
private const val BEBP = 0.62          // restitución bola-bolo
private const val BEPP = 0.64          // restitución bolo-bolo
private const val BHITJ = 0.35         // impulso mínimo para tumbar un bolo
private const val BFULL = 0x3FF        // los diez bolos en pie

private val BPIN_POS: List<Pair<Double, Double>> = buildList {
  for (k in 0 until 4) for (i in 0..k) add(Pair((i - k / 2.0) * BSP, BY0 + k * BROW))
}

private fun bowlBits(mask: Int): Int = Integer.bitCount(mask and BFULL)

/** Resultado completo de un lanzamiento: cuadros de animación a 60 fps y bolos que quedan en pie. */
private class BowlSim(
  val frames: Int,
  val ballX: FloatArray, val ballY: FloatArray, val ballRot: FloatArray,
  val pinX: FloatArray, val pinY: FloatArray,   // frames * 10
  val hitT: FloatArray, val dirX: FloatArray,
  val events: List<FloatArray>,                  // t, x, y, impulso
  val startMask: Int, val endMask: Int, val gutter: Boolean, val duration: Float
)

private fun bowlSimulate(x0In: Double, angIn: Double, hook: Double, power: Double, seed: Int, mask: Int, bump: Boolean): BowlSim {
  val rnd = java.util.Random(seed.toLong())
  val x0 = x0In + (rnd.nextDouble() - 0.5) * 0.016
  val ang = angIn + (rnd.nextDouble() - 0.5) * 0.004
  val v = 5.6 + 3.0 * power
  var bx = x0
  var by = 0.0
  var bvx = v * sin(ang)
  var bvy = v * cos(ang)
  var travelled = 0.0
  val px = DoubleArray(10) { BPIN_POS[it].first }
  val py = DoubleArray(10) { BPIN_POS[it].second }
  val vx = DoubleArray(10)
  val vy = DoubleArray(10)
  val here = BooleanArray(10) { ((mask shr it) and 1) == 1 }
  val fallen = BooleanArray(10)
  val hitT = FloatArray(10) { -1f }
  val dirX = FloatArray(10)
  var gut = false
  val dt = 1.0 / 120.0
  val maxFrames = 560
  val bX = FloatArray(maxFrames); val bY = FloatArray(maxFrames); val bR = FloatArray(maxFrames)
  val pX = FloatArray(maxFrames * 10); val pY = FloatArray(maxFrames * 10)
  val events = ArrayList<FloatArray>()
  var frames = 0
  var rest = 0
  var t = 0.0
  for (step in 0 until 1080) {
    t = step * dt
    // ── bola ──
    if (!gut) {
      if (by > 11.0) bvx += hook * 0.9 * minOf(1.0, (by - 11.0) / 7.0) * dt
      if (abs(bx) > BHALF) {
        if (bump) { bx = if (bx > 0) BHALF else -BHALF; bvx = -bvx * 0.5 }
        else { gut = true; bvx = 0.0; bx = if (bx > 0) 0.61 else -0.61 }
      }
    }
    bvy -= 0.12 * dt
    val bxo = bx; val byo = by
    bx += bvx * dt; by += bvy * dt
    if (by > BY0 + 1.7) { by = BY0 + 1.7; bvx = 0.0; bvy = 0.0 }
    travelled += hypot(bx - bxo, by - byo)
    // ── bolos: avance y rozamiento ──
    val damp = maxOf(0.0, 1.0 - BDAMP * dt)
    for (i in 0 until 10) {
      if (!here[i]) continue
      px[i] += vx[i] * dt; py[i] += vy[i] * dt
      vx[i] *= damp; vy[i] *= damp
      if (abs(px[i]) > 0.62) { px[i] = if (px[i] > 0) 0.62 else -0.62; vx[i] = 0.0; fallen[i] = true }
      if (py[i] > BY0 + 1.05) { py[i] = BY0 + 1.05; vy[i] = 0.0; vx[i] *= 0.5; fallen[i] = true }
    }
    // ── choque bola-bolo ──
    if (!gut && by > BY0 - 0.6) {
      for (i in 0 until 10) {
        if (!here[i]) continue
        val rp = if (fallen[i]) BRF else BRP
        val dx = px[i] - bx; val dy = py[i] - by
        val dist = hypot(dx, dy); val mind = BRB + rp
        if (dist < mind && dist > 1e-6) {
          val nx = dx / dist; val ny = dy / dist
          val vn = (vx[i] - bvx) * nx + (vy[i] - bvy) * ny
          if (vn < 0) {
            val j = -(1 + BEBP) * vn / (1 / BMB + 1 / BMP)
            bvx -= j / BMB * nx; bvy -= j / BMB * ny
            vx[i] += j / BMP * nx; vy[i] += j / BMP * ny
            if (j > BHITJ && !fallen[i]) { fallen[i] = true; hitT[i] = t.toFloat(); dirX[i] = if (nx >= 0) 1f else -1f }
            if (j > 0.5) events.add(floatArrayOf(t.toFloat(), px[i].toFloat(), py[i].toFloat(), j.toFloat()))
          }
          val ov = mind - dist
          px[i] += nx * ov * 0.9; py[i] += ny * ov * 0.9
        }
      }
    }
    // ── choque bolo-bolo ──
    for (i in 0 until 10) {
      if (!here[i]) continue
      for (k in i + 1 until 10) {
        if (!here[k]) continue
        val ri = if (fallen[i]) BRF else BRP
        val rk = if (fallen[k]) BRF else BRP
        val dx = px[k] - px[i]; val dy = py[k] - py[i]
        val dist = hypot(dx, dy); val mind = ri + rk
        if (dist < mind && dist > 1e-6) {
          val nx = dx / dist; val ny = dy / dist
          val vn = (vx[k] - vx[i]) * nx + (vy[k] - vy[i]) * ny
          if (vn < 0) {
            val j = -(1 + BEPP) * vn / (2 / BMP)
            vx[i] -= j / BMP * nx; vy[i] -= j / BMP * ny
            vx[k] += j / BMP * nx; vy[k] += j / BMP * ny
            if (j > BHITJ && !fallen[i]) { fallen[i] = true; hitT[i] = t.toFloat(); dirX[i] = if (-nx >= 0) 1f else -1f }
            if (j > BHITJ && !fallen[k]) { fallen[k] = true; hitT[k] = t.toFloat(); dirX[k] = if (nx >= 0) 1f else -1f }
            if (j > 0.5) events.add(floatArrayOf(t.toFloat(), ((px[i] + px[k]) / 2).toFloat(), ((py[i] + py[k]) / 2).toFloat(), j.toFloat()))
          }
          val ov = (mind - dist) / 2
          px[i] -= nx * ov; py[i] -= ny * ov; px[k] += nx * ov; py[k] += ny * ov
        }
      }
    }
    // ── registro a 60 fps ──
    if (step % 2 == 0 && frames < maxFrames) {
      bX[frames] = bx.toFloat(); bY[frames] = by.toFloat(); bR[frames] = (travelled / BRB).toFloat()
      for (i in 0 until 10) { pX[frames * 10 + i] = px[i].toFloat(); pY[frames * 10 + i] = py[i].toFloat() }
      frames++
    }
    // ── fin: todo quieto con la bola ya en el foso ──
    var moving = 0.0
    for (i in 0 until 10) if (here[i]) moving = maxOf(moving, hypot(vx[i], vy[i]))
    if (by > BY0 + 1.6 && moving < 0.03) rest++ else rest = 0
    if (rest > 30) break
  }
  var end = 0
  for (i in 0 until 10) if (here[i] && !fallen[i]) end = end or (1 shl i)
  return BowlSim(frames, bX, bY, bR, pX, pY, hitT, dirX, events, mask and BFULL, end, gut, (frames - 1) / 60f)
}

/** Trayectoria prevista (sin ruido) para la guía de puntería: pares x, y cada 0,2 m aprox. */
private fun bowlPredict(x0: Double, ang: Double, hook: Double, power: Double, bump: Boolean): List<Pair<Float, Float>> {
  val out = ArrayList<Pair<Float, Float>>()
  val v = 5.6 + 3.0 * power
  var bx = x0; var by = 0.0; var bvx = v * sin(ang); var bvy = v * cos(ang)
  val dt = 1.0 / 60.0
  var last = -1.0
  var n = 0
  while (by < BY0 - 0.3 && n < 900) {
    if (by > 11.0) bvx += hook * 0.9 * minOf(1.0, (by - 11.0) / 7.0) * dt
    if (abs(bx) > BHALF) { if (bump) { bx = if (bx > 0) BHALF else -BHALF; bvx = -bvx * 0.5 } else break }
    bvy -= 0.12 * dt
    bx += bvx * dt; by += bvy * dt
    if (by - last >= 0.2) { out.add(Pair(bx.toFloat(), by.toFloat())); last = by }
    n++
  }
  return out
}

// ───────────────────────────── Puntuación oficial ─────────────────────────────

private class BowlFrame(val marks: List<String>, val total: Int?)
private class BowlProg(val frame: Int, val ball: Int, val finished: Boolean)

private fun bowlMark(n: Int): String = if (n == 0) "-" else "$n"

/** Marcas y puntaje acumulado de los 10 frames a partir de la lista de pines derribados por tiro. */
private fun bowlFrames(r: List<Int>): List<BowlFrame> {
  val frames = ArrayList<BowlFrame>()
  var i = 0
  var cum = 0
  for (f in 0 until 10) {
    if (i >= r.size) { frames.add(BowlFrame(emptyList(), null)); continue }
    if (f < 9) {
      if (r[i] == 10) {
        val n1 = r.getOrNull(i + 1); val n2 = r.getOrNull(i + 2)
        val tot = if (n1 != null && n2 != null) { cum += 10 + n1 + n2; cum } else null
        frames.add(BowlFrame(listOf("X"), tot)); i += 1
      } else {
        val a = r[i]; val b = r.getOrNull(i + 1)
        if (b == null) { frames.add(BowlFrame(listOf(bowlMark(a)), null)); i += 1 }
        else if (a + b == 10) {
          val n = r.getOrNull(i + 2)
          val tot = if (n != null) { cum += 10 + n; cum } else null
          frames.add(BowlFrame(listOf(bowlMark(a), "/"), tot)); i += 2
        } else {
          cum += a + b
          frames.add(BowlFrame(listOf(bowlMark(a), bowlMark(b)), cum)); i += 2
        }
      }
    } else {
      val rest = r.drop(i)
      val a = rest[0]; val b = rest.getOrNull(1); val c3 = rest.getOrNull(2)
      val marks = ArrayList<String>()
      marks.add(if (a == 10) "X" else bowlMark(a))
      if (b != null) marks.add(if (a == 10) (if (b == 10) "X" else bowlMark(b)) else if (a + b == 10) "/" else bowlMark(b))
      if (c3 != null && b != null) {
        marks.add(
          when {
            a == 10 && b == 10 -> if (c3 == 10) "X" else bowlMark(c3)
            a == 10 -> if (b + c3 == 10) "/" else bowlMark(c3)
            else -> if (c3 == 10) "X" else bowlMark(c3)
          }
        )
      }
      val complete = rest.size >= 3 || (rest.size == 2 && a < 10 && a + (b ?: 0) < 10)
      frames.add(BowlFrame(marks, if (complete) cum + rest.sum() else null))
    }
  }
  return frames
}

private fun bowlTotal(r: List<Int>): Int = bowlFrames(r).lastOrNull { it.total != null }?.total ?: 0

/** En qué frame está el jugador, qué número de tiro sigue (0 = primero) y si ya terminó sus 10 frames. */
private fun bowlProg(r: List<Int>): BowlProg {
  var i = 0
  for (f in 0 until 9) {
    if (i >= r.size) return BowlProg(f, 0, false)
    if (r[i] == 10) { i += 1; continue }
    if (i + 1 >= r.size) return BowlProg(f, 1, false)
    i += 2
  }
  val rest = r.drop(i)
  return when {
    rest.isEmpty() -> BowlProg(9, 0, false)
    rest.size == 1 -> BowlProg(9, 1, false)
    rest.size == 2 -> if (rest[0] == 10 || rest[0] + rest[1] == 10) BowlProg(9, 2, false) else BowlProg(9, 2, true)
    else -> BowlProg(9, 3, true)
  }
}

private class BowlLane(val name: String, val hookK: Double, val hint: String)
private val BOWL_LANES = listOf(
  BowlLane("Normal", 1.0, "Efecto estándar"),
  BowlLane("Aceitada", 0.45, "Pista resbalosa: la bola curva poco"),
  BowlLane("Efecto extremo", 1.8, "La bola curva muchísimo")
)

/** Pantalla LED sobre la pista: baja desde arriba al cambiar de turno y muestra jugador, frame y puntos. */
@Composable
private fun BowlLedBoard(name: String, frame: Int, ball: Int, total: Int, text: String, key: Int, modifier: Modifier = Modifier) {
  val slide = remember { Animatable(0f) }
  LaunchedEffect(key, name) { slide.snapTo(0f); slide.animateTo(1f, spring(0.55f, 180f)) }
  val blink by rememberInfiniteTransition(label = "led").animateFloat(0.6f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "ledB")
  val led = Color(0xFF39FF88)
  Column(
    modifier.graphicsLayer { translationY = (slide.value - 1f) * 190f }
      .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)).background(Color(0xE6050A07))
      .border(1.5.dp, led.copy(alpha = 0.55f), RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
      .padding(horizontal = 14.dp, vertical = 6.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Text(name.uppercase(), color = led.copy(alpha = blink), fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 15.sp, maxLines = 1)
    Text("FRAME ${frame.coerceIn(1, 10)} · TIRO ${ball.coerceAtLeast(1)}   |   PTS $total", color = Color(0xFFFFC857), fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    if (text.isNotBlank()) Text(text, color = Color(0xFFFF6B8A), fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 13.sp, maxLines = 1)
  }
}

// ───────────────────────────── Sonidos sintetizados ─────────────────────────────

/** Sonidos del bowling generados por código (sin archivos): bola rodando y bolos cayendo. */
private object BowlSfx {
  private const val SR = 22050

  private fun noiseBuffer(seconds: Float, shape: (Float) -> Float, lowpass: Float, tone: Float, toneHz: Float): ShortArray {
    val n = (SR * seconds).toInt()
    val out = ShortArray(n)
    val rnd = java.util.Random(7)
    var lp = 0f
    for (i in 0 until n) {
      val t = i.toFloat() / SR
      lp += (rnd.nextFloat() * 2f - 1f - lp) * lowpass
      val s = (lp * (1f - tone) + sin(2.0 * PI * toneHz * t).toFloat() * tone) * shape(t / seconds)
      out[i] = (s.coerceIn(-1f, 1f) * 30000f).toInt().toShort()
    }
    return out
  }

  /** Bola rodando por la madera: ruido grave que se hace más agudo al acercarse a los bolos. */
  val rumble: ShortArray by lazy { noiseBuffer(2.6f, { x -> (if (x < 0.1f) x * 10f else 1f) * (0.55f + 0.45f * x) * (if (x > 0.92f) (1f - x) * 12f else 1f) }, 0.06f, 0.18f, 52f) }

  /** Estruendo de bolos: golpe seco, madera chocando y cola que se apaga. */
  val crash: ShortArray by lazy {
    val n = (SR * 1.6f).toInt()
    val out = ShortArray(n)
    val rnd = java.util.Random(11)
    var lp = 0f
    for (i in 0 until n) {
      val t = i.toFloat() / SR
      lp += (rnd.nextFloat() * 2f - 1f - lp) * 0.55f
      val body = lp * kotlin.math.exp(-t * 3.2f)
      val clack = (sin(2.0 * PI * (900 + 400 * sin(t * 30.0)) * t).toFloat() + sin(2.0 * PI * 2300 * t).toFloat() * 0.5f) *
        kotlin.math.exp(-t * 9f) * (0.5f + 0.5f * sin(t * 70f))
      out[i] = ((body * 0.75f + clack * 0.35f).coerceIn(-1f, 1f) * 30000f).toInt().toShort()
    }
    out
  }

  /** Golpe corto de un bolo contra otro. */
  val knock: ShortArray by lazy { noiseBuffer(0.12f, { x -> (1f - x) * (1f - x) }, 0.7f, 0.55f, 1500f) }

  /** Fanfarria corta para chuza o spare. */
  val fanfare: ShortArray by lazy {
    val notes = floatArrayOf(523.25f, 659.25f, 783.99f, 1046.5f)
    val per = (SR * 0.14f).toInt()
    val out = ShortArray(per * notes.size + SR / 4)
    for (k in notes.indices) for (i in 0 until per) {
      val t = i.toFloat() / SR
      val env = (1f - i.toFloat() / per).coerceAtLeast(0f)
      out[k * per + i] = (sin(2.0 * PI * notes[k] * t).toFloat() * env * 18000f).toInt().toShort()
    }
    for (i in 0 until SR / 4) {
      val t = i.toFloat() / SR
      out[per * notes.size + i] = (sin(2.0 * PI * 1318.5 * t).toFloat() * (1f - i.toFloat() / (SR / 4)) * 16000f).toInt().toShort()
    }
    out
  }

  /** Golpe seco de la bola al caer sobre la madera al soltarla. */
  val release: ShortArray by lazy { noiseBuffer(0.35f, { x -> kotlin.math.exp(-x * 7f) * (if (x < 0.02f) x * 50f else 1f) }, 0.12f, 0.6f, 70f) }

  /** Canaleta: la bola cae y rueda con un eco metálico hueco. */
  val gutter: ShortArray by lazy { noiseBuffer(1.4f, { x -> (1f - x) * (0.6f + 0.4f * sin(x * 38f)) }, 0.10f, 0.35f, 180f) }

  /** Bocina de chuza: dos notas graves y brillantes. */
  val horn: ShortArray by lazy {
    val n = (SR * 1.0f).toInt(); val out = ShortArray(n)
    for (i in 0 until n) {
      val t = i.toFloat() / SR
      val f = if (t < 0.4f) 311.1f else 415.3f
      val env = (if (t < 0.02f) t * 50f else 1f) * (1f - t / 1.0f).coerceAtLeast(0f)
      val s = (sin(2.0 * PI * f * t) + 0.5 * sin(2.0 * PI * f * 2 * t) + 0.25 * sin(2.0 * PI * f * 3 * t)).toFloat() * env
      out[i] = (s.coerceIn(-1f, 1f) * 17000f).toInt().toShort()
    }
    out
  }

  /** Aplausos: ruido filtrado con palmas rápidas. */
  val applause: ShortArray by lazy {
    val n = (SR * 1.8f).toInt(); val out = ShortArray(n); val rnd = java.util.Random(21); var lp = 0f
    for (i in 0 until n) {
      val t = i.toFloat() / SR
      lp += (rnd.nextFloat() * 2f - 1f - lp) * 0.5f
      val clap = (0.45f + 0.55f * kotlin.math.abs(sin(t * 47f))) * (if (t < 0.15f) t / 0.15f else 1f) * (1f - t / 1.8f).coerceAtLeast(0f)
      out[i] = ((lp * clap).coerceIn(-1f, 1f) * 20000f).toInt().toShort()
    }
    out
  }

  /** Campanita de spare / bonus. */
  val chime: ShortArray by lazy {
    val n = (SR * 0.9f).toInt(); val out = ShortArray(n)
    for (i in 0 until n) {
      val t = i.toFloat() / SR
      val s = (sin(2.0 * PI * 1568.0 * t) + 0.6 * sin(2.0 * PI * 2093.0 * t) + 0.3 * sin(2.0 * PI * 3136.0 * t)).toFloat() * kotlin.math.exp(-t * 4.5f)
      out[i] = (s.coerceIn(-1f, 1f) * 15000f).toInt().toShort()
    }
    out
  }

  /** Bip del marcador LED al cambiar de jugador. */
  val ledBeep: ShortArray by lazy {
    val n = (SR * 0.16f).toInt(); val out = ShortArray(n)
    for (i in 0 until n) {
      val t = i.toFloat() / SR
      val f = if (t < 0.08f) 880.0 else 1320.0
      out[i] = (sin(2.0 * PI * f * t).toFloat() * (1f - i.toFloat() / n) * 12000f).toInt().toShort()
    }
    out
  }

  /** Reproduce un buffer una vez; es seguro lanzarlo desde varias corrutinas a la vez. */
  suspend fun play(data: ShortArray, volume: Float) {
    var track: AudioTrack? = null
    try {
      track = AudioTrack.Builder()
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(SR).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
        .setBufferSizeInBytes(data.size * 2)
        .setTransferMode(AudioTrack.MODE_STATIC)
        .build()
      track.write(data, 0, data.size)
      track.setVolume(volume.coerceIn(0f, 1f))
      track.play()
      delay(data.size * 1000L / SR + 120L)
    } catch (e: kotlinx.coroutines.CancellationException) {
      throw e
    } catch (e: Exception) {
      // sin audio disponible: el juego sigue funcionando en silencio
    } finally {
      runCatching { track?.stop() }
      runCatching { track?.release() }
    }
  }
}

// ───────────────────────────── Escena 3D ─────────────────────────────

/** Cámara de perspectiva: mira hacia +y desde (x, y, h) inclinada hacia abajo `pitch` radianes. */
private class BowlCam(val x: Float, val y: Float, val h: Float, val pitch: Float, val f: Float, val cx: Float, val cy: Float) {
  private val cp = cos(pitch)
  private val sp = sin(pitch)
  fun depth(py: Float, pz: Float): Float = (py - y) * cp - (pz - h) * sp
  fun p(px: Float, py: Float, pz: Float): Offset? {
    val rx = px - x; val ry = py - y; val rz = pz - h
    val z = ry * cp - rz * sp
    if (z < 0.3f) return null
    val up = ry * sp + rz * cp
    return Offset(cx + f * rx / z, cy - f * up / z)
  }
  /** Píxeles por metro a esa profundidad. */
  fun scale(py: Float, pz: Float): Float = f / maxOf(0.3f, depth(py, pz))
}

private fun blerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

private val PIN_PROFILE = listOf(
  0.00f to 0.62f, 0.05f to 0.70f, 0.18f to 0.95f, 0.34f to 1.00f, 0.48f to 0.86f, 0.60f to 0.55f,
  0.70f to 0.42f, 0.80f to 0.50f, 0.90f to 0.54f, 0.97f to 0.40f, 1.00f to 0.00f
)

/** Semiancho relativo (0..1) de la silueta del bolo a la altura relativa v (0 = base, 1 = tope). */
private fun pinHalfWidth(v: Float): Float {
  for (k in 1 until PIN_PROFILE.size) {
    val (v1, w1) = PIN_PROFILE[k]
    if (v <= v1) {
      val (v0, w0) = PIN_PROFILE[k - 1]
      return blerp(w0, w1, (v - v0) / (v1 - v0))
    }
  }
  return 0f
}

private fun pinSlice(cx: Float, baseY: Float, w: Float, h: Float, v0: Float, v1: Float): Path {
  val path = Path()
  val steps = 14
  for (s in 0..steps) {
    val v = blerp(v0, v1, s / steps.toFloat())
    val px = cx + pinHalfWidth(v) * w / 2f
    val py = baseY - v * h
    if (s == 0) path.moveTo(px, py) else path.lineTo(px, py)
  }
  for (s in steps downTo 0) {
    val v = blerp(v0, v1, s / steps.toFloat())
    path.lineTo(cx - pinHalfWidth(v) * w / 2f, baseY - v * h)
  }
  path.close()
  return path
}

private class BowlPinDraw(val x: Float, val y: Float, val tilt: Float, val dir: Float, val alpha: Float, val lift: Float)

private fun DrawScope.drawBowlScene(
  sim: BowlSim?, t: Float, standing: Int, aim: FloatArray, aimPath: List<Pair<Float, Float>>, showAim: Boolean,
  camBlend: Float, sweep: Float, drop: Float, flash: Float, hue: Float, ball: BowlBall = BOWL_BALLS[0]
) {
  val w = size.width
  val h = size.height
  drawRect(Brush.verticalGradient(listOf(Color(0xFF05010F), Color(0xFF1B0A3C), Color(0xFF0A0420))))

  // ── estado de la bola y los bolos en el instante t ──
  var bxNow = aim[0]; var byNow = 0f; var brot = 0f
  var ballVisible = showAim
  var f0 = 0; var f1 = 0; var fr = 0f
  if (sim != null && sim.frames > 0) {
    val idx = (t * 60f).coerceIn(0f, (sim.frames - 1).toFloat())
    f0 = idx.toInt(); f1 = minOf(f0 + 1, sim.frames - 1); fr = idx - f0
    bxNow = blerp(sim.ballX[f0], sim.ballX[f1], fr)
    byNow = blerp(sim.ballY[f0], sim.ballY[f1], fr)
    brot = blerp(sim.ballRot[f0], sim.ballRot[f1], fr)
    ballVisible = byNow < BY0.toFloat() + 0.95f
  }
  val camY = if (sim != null) (byNow - 3.0f).coerceIn(-2.6f, 13.6f) else blerp(-2.6f, 13.6f, camBlend)
  val camX = (if (sim != null) bxNow else aim[0]) * 0.35f
  val prog = ((camY + 2.6f) / 16.2f).coerceIn(0f, 1f)
  val camH = 1.45f - 0.55f * prog
  val look = 8f - 3f * prog
  val cam = BowlCam(camX, camY, camH, atan2(camH, look), 1.9f * w, w / 2f, h * 0.52f)

  // temblor de cámara con los impactos fuertes
  var shake = 0f
  if (sim != null) for (ev in sim.events) {
    val dtE = abs(t - ev[0])
    if (dtE < 0.25f && ev[3] > 1.0f) shake += ev[3] * 0.9f * (1f - dtE / 0.25f)
  }
  shake = shake.coerceAtMost(9f)
  val sx = sin(t * 95f) * shake
  val sy = cos(t * 83f) * shake * 0.6f

  translate(sx, sy) {
    fun quad(x0: Float, x1: Float, y0: Float, y1: Float, z: Float, c: Color) {
      val a = cam.p(x0, y0, z); val b = cam.p(x1, y0, z); val c2 = cam.p(x1, y1, z); val d = cam.p(x0, y1, z)
      if (a == null || b == null || c2 == null || d == null) return
      val path = Path().apply { moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c2.x, c2.y); lineTo(d.x, d.y); close() }
      drawPath(path, c)
    }
    val neon = Color.hsv((hue * 360f) % 360f, 0.75f, 1f)
    val yStart = maxOf(-4.5f, cam.y + 0.45f)
    val yEnd = 20.6f
    val half = BHALF.toFloat()

    // ── sala de bolos: paredes con paneles, franja de neón, luces de techo y mueble de retorno de bolas ──
    run {
      val wallX = 1.95f
      var wy = maxOf(-4.5f, cam.y + 0.45f)
      var idx = 0
      while (wy < 21f) {
        val wy2 = wy + 1.6f
        val sh = ((wy - cam.y) / 26f).coerceIn(0f, 1f)
        for (side in listOf(-1f, 1f)) {
          val xx = side * wallX
          val a = cam.p(xx, wy, 0f); val b = cam.p(xx, wy2, 0f); val c3 = cam.p(xx, wy2, 3.2f); val d3 = cam.p(xx, wy, 3.2f)
          if (a != null && b != null && c3 != null && d3 != null) {
            val panel = if (idx % 2 == 0) lerp(Color(0xFF2A1A4E), Color(0xFF0C0620), sh) else lerp(Color(0xFF221440), Color(0xFF0A051A), sh)
            drawPath(Path().apply { moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c3.x, c3.y); lineTo(d3.x, d3.y); close() }, panel)
          }
          val n1 = cam.p(xx, wy, 1.25f); val n2 = cam.p(xx, wy2, 1.25f)
          if (n1 != null && n2 != null) {
            drawLine(neon.copy(alpha = 0.18f), n1, n2, 9f); drawLine(neon.copy(alpha = 0.9f), n1, n2, 2.2f)
          }
          // lámpara de pared
          if (idx % 2 == 0) {
            val l0 = cam.p(xx, wy + 0.8f, 2.0f)
            if (l0 != null) drawCircle(Brush.radialGradient(listOf(Color(0x55FFE9B0), Color.Transparent), l0, 34f * cam.scale(wy + 0.8f, 2.0f) / 30f + 6f), 34f * cam.scale(wy + 0.8f, 2.0f) / 30f + 6f, l0)
          }
        }
        // luces del techo
        val t0 = cam.p(0f, wy + 0.8f, 3.2f)
        if (t0 != null) {
          val rr = 60f * cam.scale(wy + 0.8f, 3.2f) / 40f + 8f
          drawCircle(Brush.radialGradient(listOf(Color(0x66FFF1CC), Color.Transparent), t0, rr), rr, t0)
        }
        wy = wy2; idx++
      }
      // mueble de retorno de bolas junto a la línea de falta
      val ra = cam.p(-0.95f, 0.2f, 0.65f); val rb = cam.p(0.95f, 0.2f, 0.65f)
      if (ra != null && rb != null) drawLine(Color(0xFF3A2A66), ra, rb, 6f, StrokeCap.Round)
    }
    // pista por tramos (cada tramo con su sombreado según la distancia)
    var y0 = yStart
    while (y0 < yEnd) {
      val y1 = minOf(yEnd, y0 + 0.5f + maxOf(0f, y0 - cam.y) * 0.04f)
      val shade = ((y0 - cam.y) / 24f).coerceIn(0f, 1f)
      val lane = if (y0 < 0f) lerp(Color(0xFFE2B572), Color(0xFFB98445), shade) else lerp(Color(0xFFD9A15C), Color(0xFF8A5527), shade)
      quad(-half, half, y0, y1 + 0.02f, 0f, lane)
      quad(-0.66f, -half, y0, y1 + 0.02f, -0.02f, lerp(Color(0xFF2A2A33), Color(0xFF0B0B10), shade))
      quad(half, 0.66f, y0, y1 + 0.02f, -0.02f, lerp(Color(0xFF2A2A33), Color(0xFF0B0B10), shade))
      quad(-4f, -0.66f, y0, y1 + 0.02f, -0.03f, lerp(Color(0xFF16102B), Color(0xFF07040F), shade))
      quad(0.66f, 4f, y0, y1 + 0.02f, -0.03f, lerp(Color(0xFF16102B), Color(0xFF07040F), shade))
      y0 = y1
    }
    // tablas de madera
    val laneStart = maxOf(0f, yStart)
    for (k in -16..16) {
      val x = k * (2f * half / 32f)
      val a = cam.p(x, laneStart, 0f); val b = cam.p(x, yEnd, 0f)
      if (a != null && b != null) drawLine(Color(0x24000000), a, b, 1f)
    }
    // tablas alternadas y brillo barnizado de la pista
    for (k in -16 until 16 step 2) {
      val xa = k * (2f * half / 32f); val xb = (k + 1) * (2f * half / 32f)
      var yy = laneStart
      while (yy < yEnd) {
        val y2 = minOf(yEnd, yy + 1.2f + maxOf(0f, yy - cam.y) * 0.1f)
        quad(xa, xb, yy, y2, 0.001f, Color(0x0F000000))
        yy = y2
      }
    }
    run {
      val gl = cam.p(-0.12f, laneStart, 0f); val gr = cam.p(0.12f, laneStart, 0f)
      val tl = cam.p(-0.12f, yEnd, 0f); val tr = cam.p(0.12f, yEnd, 0f)
      if (gl != null && gr != null && tl != null && tr != null) {
        val path = Path().apply { moveTo(gl.x, gl.y); lineTo(gr.x, gr.y); lineTo(tr.x, tr.y); lineTo(tl.x, tl.y); close() }
        drawPath(path, Brush.verticalGradient(listOf(Color(0x00FFFFFF), Color(0x33FFFFFF), Color(0x1AFFFFFF)), startY = tl.y, endY = gl.y))
      }
    }
    // línea de falta, flechas y puntos de mira
    val foulA = cam.p(-half, 0f, 0f); val foulB = cam.p(half, 0f, 0f)
    if (foulA != null && foulB != null) drawLine(Color(0xFFE53935), foulA, foulB, 3f)
    for (i in 0..6) {
      val ax = (i - 3) * 0.14f
      val ay = 3.9f + (3 - abs(i - 3)) * 0.38f
      val a = cam.p(ax, ay, 0f); val b = cam.p(ax - 0.035f, ay + 0.55f, 0f); val c2 = cam.p(ax + 0.035f, ay + 0.55f, 0f)
      if (a != null && b != null && c2 != null) {
        val tri = Path().apply { moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c2.x, c2.y); close() }
        drawPath(tri, Color(0x66402200))
      }
    }
    for (i in 0..6) {
      val c0 = cam.p((i - 3) * 0.14f, 2.2f, 0f)
      if (c0 != null) drawCircle(Color(0x66402200), 2.5f * cam.scale(2.2f, 0f) / 40f + 1.2f, c0)
    }
    // zona de los bolos y foso
    quad(-half, half, (BY0 - 0.35).toFloat(), (BY0 + 1.0).toFloat(), 0.001f, Color(0x16FFFFFF))
    quad(-0.7f, 0.7f, (BY0 + 1.0).toFloat(), (BY0 + 2.4).toFloat(), -0.01f, Color(0xFF040308))
    run {
      val by = (BY0 + 2.4).toFloat()
      val a = cam.p(-0.7f, by, 0f); val b = cam.p(0.7f, by, 0f); val c2 = cam.p(0.7f, by, 1.6f); val d = cam.p(-0.7f, by, 1.6f)
      if (a != null && b != null && c2 != null && d != null) {
        val path = Path().apply { moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c2.x, c2.y); lineTo(d.x, d.y); close() }
        drawPath(path, Brush.verticalGradient(listOf(Color(0xFF1A1030), Color(0xFF05020A)), startY = d.y, endY = a.y))
        drawLine(neon.copy(alpha = 0.9f), d, c2, 3f)
      }
    }
    // tiras de neón a los lados de la pista
    for (side in listOf(-1f, 1f)) {
      val a = cam.p(side * 0.82f, yStart, 0.03f); val b = cam.p(side * 0.82f, yEnd, 0.03f)
      if (a != null && b != null) {
        drawLine(neon.copy(alpha = 0.10f), a, b, 16f, StrokeCap.Round)
        drawLine(neon.copy(alpha = 0.25f), a, b, 8f, StrokeCap.Round)
        drawLine(neon.copy(alpha = 0.95f), a, b, 2.5f, StrokeCap.Round)
      }
    }
    // foco sobre los bolos
    val spot = cam.p(0f, (BY0 + 0.35).toFloat(), 0.2f)
    if (spot != null) {
      val rs = cam.scale((BY0 + 0.35).toFloat(), 0.2f) * 1.5f
      drawCircle(Brush.radialGradient(listOf(Color(0x66FFFFFF), Color.Transparent), center = spot, radius = rs), rs, spot)
    }

    // guía de puntería (solo cuando te toca)
    if (showAim && sim == null) {
      aimPath.forEachIndexed { i, pt ->
        val p = cam.p(pt.first, pt.second, 0.01f)
        if (p != null) drawCircle(neon.copy(alpha = (0.85f - i * 0.012f).coerceAtLeast(0.2f)), 3.2f * cam.scale(pt.second, 0f) / 30f + 1.4f, p)
      }
    }

    // ── bolos ordenados de lejos a cerca ──
    val pins = ArrayList<BowlPinDraw>()
    for (i in 0 until 10) {
      val present = if (sim != null) ((sim.startMask shr i) and 1) == 1 else ((standing shr i) and 1) == 1
      if (!present) continue
      if (sim != null && sim.frames > 0) {
        val x = blerp(sim.pinX[f0 * 10 + i], sim.pinX[f1 * 10 + i], fr)
        val y = blerp(sim.pinY[f0 * 10 + i], sim.pinY[f1 * 10 + i], fr)
        val ht = sim.hitT[i]
        val tilt = if (ht >= 0f && t >= ht) minOf(1f, (t - ht) / 0.30f) else 0f
        val knockedDown = ((sim.endMask shr i) and 1) == 0
        val a = if (knockedDown) 1f - sweep else 1f
        pins.add(BowlPinDraw(x, y + (if (knockedDown) sweep * 0.9f else 0f), tilt, sim.dirX[i], a, 0f))
      } else {
        pins.add(BowlPinDraw(BPIN_POS[i].first.toFloat(), BPIN_POS[i].second.toFloat(), 0f, 1f, drop.coerceIn(0f, 1f), (1f - drop.coerceIn(0f, 1f)) * 1.3f))
      }
    }
    pins.sortByDescending { it.y }

    fun drawPin(pd: BowlPinDraw) {
      if (pd.alpha <= 0.01f) return
      val base = cam.p(pd.x, pd.y, pd.lift) ?: return
      val topP = cam.p(pd.x, pd.y, pd.lift + 0.38f) ?: return
      val hpx = base.y - topP.y
      if (hpx < 2f) return
      val wpx = hpx * 0.30f
      val shadowP = cam.p(pd.x, pd.y, 0f)
      if (shadowP != null) drawOval(Color(0x33FFFFFF).copy(alpha = 0.12f * pd.alpha), Offset(shadowP.x - wpx * 0.28f, shadowP.y), Size(wpx * 0.56f, hpx * 0.42f))
      if (shadowP != null) drawOval(Color(0x55000000).copy(alpha = 0.33f * pd.alpha), Offset(shadowP.x - wpx * 0.75f, shadowP.y - hpx * 0.05f), Size(wpx * 1.5f, hpx * 0.10f))
      rotate(pd.tilt * 90f * pd.dir, base) {
        val body = pinSlice(base.x, base.y, wpx, hpx, 0f, 1f)
        drawPath(
          body, Brush.horizontalGradient(
            listOf(Color(0xFF8C93A6), Color(0xFFE4E7EF), Color(0xFFFFFFFF), Color(0xFFD5D9E3), Color(0xFF7E859A)),
            startX = base.x - wpx / 2f, endX = base.x + wpx / 2f
          ),
          alpha = pd.alpha
        )
        // reflejo ambiente del techo en el cuerpo del bolo
        drawPath(pinSlice(base.x - wpx * 0.12f, base.y, wpx * 0.5f, hpx, 0.22f, 0.5f), Color(0x33AEEBFF), alpha = pd.alpha)
        drawPath(pinSlice(base.x, base.y, wpx, hpx, 0.64f, 0.69f), Color(0xFFD7263D), alpha = pd.alpha)
        drawPath(pinSlice(base.x, base.y, wpx, hpx, 0.73f, 0.78f), Color(0xFFD7263D), alpha = pd.alpha)
        drawPath(body, Brush.horizontalGradient(listOf(Color.Transparent, Color(0x00000000), Color(0x55202040)), startX = base.x - wpx / 2f, endX = base.x + wpx / 2f), alpha = pd.alpha)
        drawLine(Color(0x99FFFFFF), Offset(base.x - wpx * 0.18f, base.y - hpx * 0.14f), Offset(base.x - wpx * 0.2f, base.y - hpx * 0.58f), wpx * 0.07f, StrokeCap.Round, alpha = pd.alpha)
        drawPath(body, Color(0x44000000), alpha = pd.alpha, style = Stroke(width = 1f))
      }
    }

    fun drawBall() {
      val c0 = cam.p(bxNow, byNow, BRB.toFloat()) ?: return
      val r = BRB.toFloat() * cam.scale(byNow, BRB.toFloat())
      val sh = cam.p(bxNow, byNow, 0f)
      if (sh != null) drawOval(Color(0x66000000), Offset(sh.x - r * 1.1f, sh.y - r * 0.18f), Size(r * 2.2f, r * 0.36f))
      drawOval(Color(0x2AFFFFFF), Offset(c0.x - r * 0.5f, c0.y + r * 0.95f), Size(r, r * 0.9f))
      if (sim != null) for (k in 1..7) {
        val fi = f0 - k * 2
        if (fi < 0) break
        val tp = cam.p(sim.ballX[fi], sim.ballY[fi], BRB.toFloat())
        if (tp != null) drawCircle(neon.copy(alpha = 0.10f * (8 - k) / 7f), r * (1f - k * 0.05f), tp)
      }
      drawCircle(
        Brush.radialGradient(listOf(ball.hi, ball.mid, ball.lo), center = Offset(c0.x - r * 0.35f, c0.y - r * 0.4f), radius = r * 1.6f),
        r, c0
      )
      for (k in 0 until 3) {
        val ang = brot + k * 2.094f
        val hole = Offset(c0.x - r * 0.18f + cos(ang) * r * 0.42f, c0.y - r * 0.28f + sin(ang) * r * 0.26f)
        drawCircle(Color(0xFF05061A), r * 0.09f, hole)
      }
      drawRoundRect(Color(0x55FFFFFF), Offset(c0.x - r * 0.62f, c0.y - r * 0.72f), Size(r * 0.62f, r * 0.34f), androidx.compose.ui.geometry.CornerRadius(r * 0.1f))
      drawArc(Color(0x33FFFFFF), 200f, 120f, false, Offset(c0.x - r * 0.9f, c0.y - r * 0.9f), Size(r * 1.8f, r * 1.8f), style = Stroke(r * 0.12f))
      drawCircle(Color(0x66FFFFFF), r * 0.17f, Offset(c0.x - r * 0.38f, c0.y - r * 0.44f))
      drawCircle(Color(0xCCFFFFFF), r * 0.06f, Offset(c0.x - r * 0.42f, c0.y - r * 0.5f))
      drawCircle(Brush.radialGradient(listOf(Color.Transparent, Color(0x55000000)), center = c0, radius = r), r, c0)
      drawCircle(neon.copy(alpha = 0.35f), r * 1.04f, c0, style = Stroke(width = 1.6f))
    }

    for (pd in pins) if (pd.y > byNow || !ballVisible) drawPin(pd)
    if (ballVisible) drawBall()
    for (pd in pins) if (ballVisible && pd.y <= byNow) drawPin(pd)

    // chispas en cada impacto fuerte
    if (sim != null) for (ev in sim.events) {
      val age = (t - ev[0]) / 0.45f
      if (age in 0f..1f && ev[3] > 1.2f) {
        val c0 = cam.p(ev[1], ev[2], 0.12f) ?: continue
        for (s in 0 until 7) {
          val a = s * 0.9f + ev[0] * 13f
          val d = age * 44f * (0.5f + (s % 3) * 0.3f)
          drawCircle(Color(0xFFFFE9A8).copy(alpha = 1f - age), 2.6f * (1f - age) + 0.8f, Offset(c0.x + cos(a) * d, c0.y + sin(a) * d - age * 10f))
        }
      }
    }
  }

  // viñeta y destello de chuza
  drawRect(Brush.radialGradient(listOf(Color.Transparent, Color(0x99000000)), center = Offset(w / 2f, h / 2f), radius = maxOf(w, h) * 0.78f))
  if (flash > 0f) drawRect(Color.White.copy(alpha = flash * 0.5f))
}

@Composable
private fun BowlScene(
  sim: BowlSim?, timeProvider: () -> Float, standing: Int, aim: FloatArray, aimPath: List<Pair<Float, Float>>, showAim: Boolean,
  camBlend: () -> Float, sweep: () -> Float, drop: () -> Float, flash: () -> Float,
  ball: BowlBall = BOWL_BALLS[0],
  modifier: Modifier = Modifier, onDrag: (Float, Float) -> Unit
) {
  val hue by rememberInfiniteTransition(label = "bowlHue").animateFloat(
    initialValue = 0f, targetValue = 1f, animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "bowlHueV"
  )
  Canvas(
    modifier.pointerInput(showAim) {
      if (showAim) detectHorizontalDragGestures { change, dx -> change.consume(); onDrag(dx, size.width.toFloat()) }
    }
  ) {
    drawBowlScene(sim, timeProvider(), standing, aim, aimPath, showAim, camBlend(), sweep(), drop(), flash(), hue, ball)
  }
}

// ───────────────────────────── Marcador ─────────────────────────────


@Composable
private fun BowlRoundBtn(label: String, onClick: () -> Unit) {
  Box(
    Modifier.size(42.dp).clip(CircleShape).background(Color(0xAA1A0F2B)).border(2.dp, Color(0xFFB388FF), CircleShape).clickable { onClick() },
    contentAlignment = Alignment.Center
  ) { Text(label, color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp) }
}

/** Medidor de fuerza vertical: barra de segmentos que van de verde a rojo, con icono de bola; se arrastra con el dedo. */
@Composable
private fun BowlPowerMeter(value: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
  Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
    Text("💥", fontSize = 16.sp)
    Box(
      Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0x99000000)).border(2.dp, Color(0xFFB388FF), RoundedCornerShape(14.dp))
        .pointerInput(Unit) {
          awaitEachGesture {
            val down = awaitFirstDown()
            fun set(y: Float) { onChange((1f - y / size.height.toFloat()).coerceIn(0f, 1f).let { 0.3f + it * 0.7f }) }
            set(down.position.y)
            while (true) {
              val ev = awaitPointerEvent(); val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
              if (!ch.pressed) break
              set(ch.position.y); ch.consume()
            }
          }
        }
    ) {
      Canvas(Modifier.fillMaxSize()) {
        val segs = 12; val gap = 3.dp.toPx(); val h = (size.height - gap * (segs + 1)) / segs
        val lit = (((value - 0.3f) / 0.7f).coerceIn(0f, 1f) * segs).let { kotlin.math.ceil(it.toDouble()).toInt().coerceAtLeast(1) }
        for (i in 0 until segs) {
          val y = size.height - gap - (i + 1) * (h + gap) + gap
          val t = i / (segs - 1f)
          val col = androidx.compose.ui.graphics.lerp(Color(0xFF39FF88), Color(0xFFFF3D57), t)
          drawRoundRect(if (i < lit) col else col.copy(alpha = 0.15f), Offset(gap * 1.5f, y), androidx.compose.ui.geometry.Size(size.width - gap * 3f, h), androidx.compose.ui.geometry.CornerRadius(5f, 5f))
        }
      }
    }
    Text("${(value * 100).toInt()}%", color = Color.White, fontWeight = FontWeight.Black, fontSize = 11.sp)
  }
}

/** Dial de efecto: una bola con flecha curva; arrastra a izquierda/derecha para curvar la bola. */
@Composable
private fun BowlHookDial(value: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
  Column(modifier.pointerInput(Unit) {
    awaitEachGesture {
      val down = awaitFirstDown()
      fun set(x: Float) { onChange(((x / size.width.toFloat()) * 2f - 1f).coerceIn(-1f, 1f)) }
      set(down.position.x)
      while (true) {
        val ev = awaitPointerEvent(); val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
        if (!ch.pressed) break
        set(ch.position.x); ch.consume()
      }
    }
  }, horizontalAlignment = Alignment.CenterHorizontally) {
    Canvas(Modifier.weight(1f).fillMaxWidth()) {
      val c = Offset(size.width / 2f, size.height / 2f); val r = size.minDimension / 2f - 4f
      drawCircle(Color(0xAA1A0F2B), r, c); drawCircle(Color(0xFFB388FF), r, c, style = androidx.compose.ui.graphics.drawscope.Stroke(3f))
      drawArc(Color(0x33FFFFFF), 200f, 140f, false, c - Offset(r * 0.72f, r * 0.72f), androidx.compose.ui.geometry.Size(r * 1.44f, r * 1.44f), style = androidx.compose.ui.graphics.drawscope.Stroke(6f))
      val sweep = value * 70f
      drawArc(Color(0xFFFFC857), 270f, sweep, false, c - Offset(r * 0.72f, r * 0.72f), androidx.compose.ui.geometry.Size(r * 1.44f, r * 1.44f), style = androidx.compose.ui.graphics.drawscope.Stroke(8f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
      val a = Math.toRadians((270f + sweep).toDouble())
      drawCircle(Color.White, 9f, Offset(c.x + (r * 0.72f * kotlin.math.cos(a)).toFloat(), c.y + (r * 0.72f * kotlin.math.sin(a)).toFloat()))
      drawCircle(Brush.radialGradient(listOf(Color(0xFF7886FF), Color(0xFF1D2A8C)), c, r * 0.4f), r * 0.32f, c)
    }
    Text(if (value < -0.15f) "↶ Curva izq." else if (value > 0.15f) "Curva der. ↷" else "Recta", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp)
  }
}

@Composable
private fun BowlBallPicker(selected: Int, tick: Int, onPick: (Int) -> Unit) {
  val ctx = LocalContext.current
  val owned = remember(tick) { Cosmetics.ownedBalls(ctx) }
  Text("Tu bola (las bloqueadas se compran en la Tienda de juegos)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
  Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    BOWL_BALLS.forEach { b ->
      val ok = b.id in owned
      val label = (if (ok) b.emoji else "🔒") + " " + b.name
      if (selected == b.id) GradientButton(label, onClick = {}, height = 38.dp)
      else SoftButton(label, onClick = { if (ok) onPick(b.id) }, enabled = ok)
    }
  }
}

@Composable
private fun BowlScoreboard(names: List<String>, rolls: List<List<Int>>, active: Int) {
  Column(Modifier.horizontalScroll(rememberScrollState())) {
    Row {
      Spacer(Modifier.width(78.dp))
      for (f in 1..10) {
        Box(Modifier.width(if (f == 10) 50.dp else 36.dp), contentAlignment = Alignment.Center) {
          Text("$f", color = Color(0x99FFFFFF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
      }
      Box(Modifier.width(46.dp), contentAlignment = Alignment.Center) { Text("TOTAL", color = Color(0xFFFFC857), fontSize = 10.sp, fontWeight = FontWeight.Black) }
    }
    names.forEachIndexed { i, name ->
      val frames = bowlFrames(rolls.getOrElse(i) { emptyList() })
      val isActive = i == active
      Row(
        Modifier.padding(vertical = 2.dp).clip(RoundedCornerShape(10.dp))
          .background(if (isActive) Color(0x33FFC857) else Color(0x14FFFFFF)),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Box(Modifier.width(78.dp).padding(horizontal = 6.dp)) {
          Text(name, color = if (isActive) Color(0xFFFFC857) else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        frames.forEachIndexed { fi, fr ->
          Column(
            Modifier.width(if (fi == 9) 50.dp else 36.dp).height(42.dp).border(0.5.dp, Color(0x33FFFFFF)),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Text(fr.marks.joinToString(" "), color = if (fr.marks.contains("X") || fr.marks.contains("/")) Color(0xFFFF6B8A) else Color(0xCCFFFFFF), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(fr.total?.toString() ?: "", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
          }
        }
        Box(Modifier.width(46.dp), contentAlignment = Alignment.Center) {
          Text("${bowlTotal(rolls.getOrElse(i) { emptyList() })}", color = Color(0xFFFFC857), fontSize = 16.sp, fontWeight = FontWeight.Black)
        }
      }
    }
  }
}

@Composable
private fun BowlBanner(id: Int, text: String, color: Color, onDone: () -> Unit, modifier: Modifier = Modifier) {
  val s = remember { Animatable(0.2f) }
  val a = remember { Animatable(0f) }
  LaunchedEffect(id) {
    launch { a.animateTo(1f, tween(140)) }
    s.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 330f))
    delay(1300)
    a.animateTo(0f, tween(320))
    onDone()
  }
  val shape = RoundedCornerShape(50)
  Box(
    modifier.graphicsLayer { scaleX = s.value; scaleY = s.value; alpha = a.value }
      .clip(shape).background(color).border(2.dp, Color.White, shape).padding(horizontal = 22.dp, vertical = 10.dp)
  ) { Text(text, color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp) }
}

// ───────────────────────────── Juego ─────────────────────────────

@Composable
internal fun BowlingGame(c: GameCtx) {
  val ctx = LocalContext.current
  val flow = remember { c.vm.gameState("bowl") }
  val st by flow.collectAsState(initial = null)
  fun n(k: String, d: Int = 0) = (st?.get(k) as? Number)?.toInt() ?: d
  fun dbl(k: String, def: Double = 0.0) = (st?.get(k) as? Number)?.toDouble() ?: def
  val seats = (st?.get("seats") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
  val started = seats.isNotEmpty()
  val rolls = (0 until 4).map { i -> (st?.get("r$i") as? List<*>)?.mapNotNull { (it as? Number)?.toInt() } ?: emptyList() }
  val turn = n("turn")
  val mask = n("mask", BFULL)
  val tid = n("tid")
  val bump = (st?.get("bump") as? Boolean) ?: false
  val laneIdx = n("lane").coerceIn(0, BOWL_LANES.size - 1)
  val laneK = BOWL_LANES[laneIdx].hookK
  val ballIdx = n("tball").coerceIn(0, BOWL_BALLS.size - 1)
  val gameOver = (st?.get("done") as? Boolean) ?: false
  val msg = (st?.get("msg") as? String) ?: ""

  var botsSel by remember { mutableStateOf(1) }
  var bumpSel by remember { mutableStateOf(false) }
  var laneSel by remember { mutableStateOf(0) }
  var myBall by remember { mutableStateOf(Cosmetics.selectedBall(ctx)) }
  var ownedTick by remember { mutableStateOf(0) }
  var doneTid by remember { mutableStateOf(-1) }
  var primed by remember { mutableStateOf(false) }
  var shownRolls by remember { mutableStateOf<List<List<Int>>>(emptyList()) }
  var shownMask by remember { mutableStateOf(BFULL) }
  var animSim by remember { mutableStateOf<BowlSim?>(null) }
  val playT = remember { Animatable(0f) }
  val camBlend = remember { Animatable(0f) }
  val sweep = remember { Animatable(0f) }
  val drop = remember { Animatable(1f) }
  val flash = remember { Animatable(0f) }
  val enter = remember { Animatable(0f) }
  var bannerId by remember { mutableStateOf(0) }
  var bannerText by remember { mutableStateOf<String?>(null) }
  var bannerColor by remember { mutableStateOf(Color(0xFFE53935)) }
  var aimX by remember { mutableStateOf(-0.10f) }
  var aimAng by remember { mutableStateOf(0f) }
  var aimHook by remember { mutableStateOf(0.4f) }
  var aimPower by remember { mutableStateOf(0.7f) }
  var pending by remember { mutableStateOf(-1) }
  val animating = started && doneTid != tid

  fun nameAt(i: Int): String = if (seats.getOrNull(i) == "bot") "Bot ${seats.take(i + 1).count { it == "bot" }}" else c.nameOf(seats.getOrElse(i) { "" })
  val names = seats.indices.map { nameAt(it) }

  LaunchedEffect(started) {
    enter.snapTo(0f)
    enter.animateTo(1f, tween(450, easing = FastOutSlowInEasing))
  }

  /** Publica un lanzamiento: calcula la física, la puntuación, el próximo turno y deja todo listo para animarlo. */
  fun doThrow(seat: Int, x0: Double, ang: Double, hook: Double, power: Double) {
    if (seat !in seats.indices || gameOver || animating) return
    val allRolls = (0 until 4).map { rolls[it].toMutableList() }
    val before = bowlProg(allRolls[seat])
    val seed = (System.nanoTime() and 0x7FFFFFFFL).toInt()
    val sim = bowlSimulate(x0, ang, hook * laneK, power, seed, mask, bump)
    val end = sim.endMask
    val knocked = bowlBits(mask) - bowlBits(end)
    allRolls[seat].add(knocked)
    val after = bowlProg(allRolls[seat])
    val stay = !after.finished && after.frame == before.frame
    val nextMask = if (end == 0 || !stay) BFULL else end
    var nextSeat = seat
    var over = false
    if (!stay) {
      var found = -1
      for (k in 1..seats.size) {
        val s = (seat + k) % seats.size
        if (!bowlProg(allRolls[s]).finished) { found = s; break }
      }
      if (found == -1) over = true else nextSeat = found
    }
    val label = when {
      end == 0 && mask == BFULL -> "¡CHUZA!"
      end == 0 -> "¡SPARE!"
      sim.gutter && knocked == 0 -> "canaleta"
      knocked == 0 -> "sin bolos"
      else -> "$knocked ${if (knocked == 1) "bolo" else "bolos"}"
    }
    var text = "${nameAt(seat)}: $label"
    if (over) {
      val best = seats.indices.maxByOrNull { bowlTotal(allRolls[it]) } ?: 0
      text = "🏆 ¡Ganó ${nameAt(best)} con ${bowlTotal(allRolls[best])} puntos!"
    }
    val data = mutableMapOf<String, Any?>(
      "seats" to seats, "turn" to nextSeat, "mask" to nextMask, "tid" to (tid + 1), "bump" to bump, "done" to over, "msg" to text,
      "tseat" to seat, "tx" to x0, "ta" to ang, "th" to hook, "tp" to power, "tseed" to seed, "tmask" to mask, "tres" to end,
      "lane" to laneIdx, "tball" to (if (seats.getOrNull(seat) == c.me) myBall else (seats.getOrNull(seat)?.let { (seat + 1) % BOWL_BALLS.size } ?: 0))
    )
    for (i in 0 until 4) data["r$i"] = allRolls[i]
    c.vm.publishGame("bowl", data)
  }

  // Cada lanzamiento se anima igual en todos los dispositivos a partir del estado compartido.
  LaunchedEffect(tid, started) {
    if (!started) { primed = false; return@LaunchedEffect }
    val first = !primed
    primed = true
    if (first || tid <= 0 || tid == doneTid || n("tseat", -1) < 0) {
      shownRolls = rolls; shownMask = mask; animSim = null; doneTid = tid
      return@LaunchedEffect
    }
    val sim = bowlSimulate(dbl("tx"), dbl("ta"), dbl("th") * laneK, dbl("tp"), n("tseed"), n("tmask", BFULL), bump)
    animSim = sim
    sweep.snapTo(0f); flash.snapTo(0f); playT.snapTo(0f)
    launch { BowlSfx.play(BowlSfx.release, 0.7f) }
    launch { BowlSfx.play(BowlSfx.ledBeep, 0.35f) }
    launch { delay(250); BowlSfx.play(BowlSfx.rumble, 0.5f) }
    if (sim.gutter) launch { delay(600); BowlSfx.play(BowlSfx.gutter, 0.6f) }
    val hits = sim.events.filter { it[3] > 1.0f }
    if (hits.isNotEmpty()) {
      launch { delay((hits[0][0] * 1000).toLong()); BowlSfx.play(BowlSfx.crash, 0.85f) }
      launch {
        var last = hits[0][0]
        for (ev in hits.drop(1).take(14)) {
          delay(((ev[0] - last) * 1000).toLong().coerceAtLeast(0L)); last = ev[0]
          launch { BowlSfx.play(BowlSfx.knock, 0.45f) }
        }
      }
    }
    playT.animateTo(sim.duration, tween((sim.duration * 1000).toInt().coerceAtLeast(300), easing = LinearEasing))
    val resultEnd = n("tres", sim.endMask)
    val knocked = bowlBits(sim.startMask) - bowlBits(resultEnd)
    val strike = resultEnd == 0 && sim.startMask == BFULL
    val spare = resultEnd == 0 && sim.startMask != BFULL
    bannerId += 1
    when {
      strike -> { bannerText = "🎳 ¡CHUZA!"; bannerColor = Color(0xFFE53935) }
      spare -> { bannerText = "✨ ¡SPARE!"; bannerColor = Color(0xFF8E24AA) }
      sim.gutter && knocked == 0 -> { bannerText = "Canaleta 😬"; bannerColor = Color(0xFF546E7A) }
      knocked == 0 -> { bannerText = "Sin bolos"; bannerColor = Color(0xFF546E7A) }
      else -> { bannerText = "$knocked ${if (knocked == 1) "bolo" else "bolos"}"; bannerColor = Color(0xFF1E88E5) }
    }
    if (strike || spare) {
      launch { BowlSfx.play(BowlSfx.fanfare, 0.7f) }
      if (strike) { launch { BowlSfx.play(BowlSfx.horn, 0.8f) }; launch { delay(300); BowlSfx.play(BowlSfx.applause, 0.8f) } }
      else launch { BowlSfx.play(BowlSfx.chime, 0.8f); delay(200); BowlSfx.play(BowlSfx.applause, 0.5f) }
      launch { flash.snapTo(1f); flash.animateTo(0f, tween(800)) }
    }
    delay(500)
    sweep.animateTo(1f, tween(600))
    val reset = mask == BFULL && resultEnd != BFULL
    shownRolls = rolls; shownMask = mask
    animSim = null
    if (reset) { drop.snapTo(0f); launch { drop.animateTo(1f, tween(650, easing = FastOutSlowInEasing)) } } else drop.snapTo(1f)
    camBlend.snapTo(1f)
    launch { camBlend.animateTo(0f, tween(900, easing = FastOutSlowInEasing)) }
    doneTid = tid
  }

  // Los bots tiran desde el primer dispositivo, apuntando al bolsillo (o al bolo restante) con algo de error.
  LaunchedEffect(tid, turn, animating, gameOver, started) {
    if (!started || gameOver || animating || seats.getOrNull(turn) != "bot" || c.me != c.firstUid) return@LaunchedEffect
    delay(1200)
    val rnd = java.util.Random()
    if (mask == BFULL) {
      val side = if (rnd.nextBoolean()) 1.0 else -1.0
      doThrow(turn, -0.10 * side + rnd.nextGaussian() * 0.045, rnd.nextGaussian() * 0.004, 0.45 * side, 0.65 + rnd.nextDouble() * 0.3)
    } else {
      var target = -1
      for (i in 0 until 10) if (((mask shr i) and 1) == 1 && (target == -1 || BPIN_POS[i].second < BPIN_POS[target].second - 1e-6 ||
          (abs(BPIN_POS[i].second - BPIN_POS[target].second) < 1e-6 && abs(BPIN_POS[i].first) < abs(BPIN_POS[target].first)))) target = i
      val tx = BPIN_POS[target].first
      val sx = (tx * 0.5).coerceIn(-0.4, 0.4)
      doThrow(turn, sx + rnd.nextGaussian() * 0.03, atan2(tx - sx, BY0) + rnd.nextGaussian() * 0.003, 0.0, 0.7 + rnd.nextDouble() * 0.2)
    }
  }

  if (!started) {
    Column(Modifier.graphicsLayer { alpha = enter.value; translationY = (1f - enter.value) * 30f }) {
      GlassCard(modifier = Modifier.fillMaxWidth(), highlight = true) {
        Text("🎳 Bowling 3D", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
        Text(
          "Mové la bola arrastrando sobre la pista, elegí ángulo, efecto y fuerza, y lanzá. Diez frames: chuza = 10 más los dos tiros siguientes, spare = 10 más el siguiente. Los bolos chocan entre sí con física real.",
          color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp
        )
      }
      Spacer(Modifier.height(10.dp))
      Text("Bots", color = Color.White, fontWeight = FontWeight.Bold)
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (0..3).forEach { k -> if (botsSel == k) GradientButton("$k bots", onClick = { botsSel = k }, height = 38.dp) else SoftButton("$k bots", onClick = { botsSel = k }) }
      }
      Spacer(Modifier.height(8.dp))
      Text("Pista", color = Color.White, fontWeight = FontWeight.Bold)
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (!bumpSel) GradientButton("Con canaletas", onClick = { bumpSel = false }, height = 38.dp) else SoftButton("Con canaletas", onClick = { bumpSel = false })
        if (bumpSel) GradientButton("Con barandas", onClick = { bumpSel = true }, height = 38.dp) else SoftButton("Con barandas", onClick = { bumpSel = true })
      }
      Spacer(Modifier.height(8.dp))
      Text("Efecto de la pista", color = Color.White, fontWeight = FontWeight.Bold)
      Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        BOWL_LANES.forEachIndexed { i, l -> if (laneSel == i) GradientButton(l.name, onClick = { laneSel = i }, height = 38.dp) else SoftButton(l.name, onClick = { laneSel = i }) }
      }
      Text(BOWL_LANES[laneSel].hint, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
      Spacer(Modifier.height(8.dp))
      BowlBallPicker(myBall, ownedTick) { myBall = it; Cosmetics.selectBall(ctx, it); ownedTick++ }
      Spacer(Modifier.height(10.dp))
      GradientButton("🎳 Empezar a jugar", modifier = Modifier.fillMaxWidth(), onClick = {
        val list = c.players.take(4).toMutableList()
        repeat(botsSel.coerceAtMost(4 - list.size)) { list.add("bot") }
        if (list.isEmpty()) list.add("bot")
        val data = mutableMapOf<String, Any?>(
          "seats" to list, "turn" to 0, "mask" to BFULL, "tid" to (tid + 1), "bump" to bumpSel, "lane" to laneSel, "done" to false, "msg" to "¡Primer tiro!",
          "tseat" to -1, "tx" to 0.0, "ta" to 0.0, "th" to 0.0, "tp" to 0.7, "tseed" to 0, "tmask" to BFULL, "tres" to BFULL
        )
        for (i in 0 until 4) data["r$i"] = emptyList<Int>()
        c.vm.publishGame("bowl", data)
      })
    }
    return
  }

  val myTurn = !gameOver && !animating && seats.getOrNull(turn) == c.me
  // Lo que apunta el jugador que tiene el turno se ve en vivo en los demás teléfonos, antes de lanzar.
  val liveAll by remember { c.vm.liveStates() }.collectAsState(initial = emptyMap())
  val throwerUid = seats.getOrNull(turn) ?: ""
  val remote = liveAll[throwerUid]?.takeIf { throwerUid != c.me && throwerUid != "bot" && (it["g"] as? String) == "bowl" && (it["tid"] as? Number)?.toInt() == tid }
  val remoteOn = !myTurn && !animating && !gameOver && remote != null
  fun rf(k: String, d: Float) = (remote?.get(k) as? Number)?.toFloat() ?: d
  val ex by androidx.compose.animation.core.animateFloatAsState(if (remoteOn) rf("x", 0f) else aimX, androidx.compose.animation.core.tween(260), label = "rx")
  val eAng by androidx.compose.animation.core.animateFloatAsState(if (remoteOn) rf("ang", 0f) else aimAng, androidx.compose.animation.core.tween(260), label = "ra")
  val eHook by androidx.compose.animation.core.animateFloatAsState(if (remoteOn) rf("hook", 0.4f) else aimHook, androidx.compose.animation.core.tween(260), label = "rh")
  val ePow by androidx.compose.animation.core.animateFloatAsState(if (remoteOn) rf("pow", 0.7f) else aimPower, androidx.compose.animation.core.tween(260), label = "rp")
  LaunchedEffect(myTurn, tid) {
    while (myTurn) {
      c.vm.publishLive(mapOf("g" to "bowl", "tid" to tid, "x" to aimX.toDouble(), "ang" to aimAng.toDouble(), "hook" to aimHook.toDouble(), "pow" to aimPower.toDouble()))
      delay(250)
    }
  }
  val aim = remember(ex) { floatArrayOf(ex) }
  val aimPath = remember(ex, eAng, eHook, ePow, bump, laneK) { bowlPredict(ex.toDouble(), eAng.toDouble(), eHook.toDouble() * laneK, ePow.toDouble(), bump) }
  val shownR = if (shownRolls.isEmpty()) rolls else shownRolls
  val sliderColors = SliderDefaults.colors(thumbColor = Color(0xFF38BDF8), activeTrackColor = Color(0xFF38BDF8), inactiveTrackColor = Color(0x33FFFFFF))

  Column(Modifier.graphicsLayer { alpha = enter.value; translationY = (1f - enter.value) * 30f }) {
    Box(
      Modifier.fillMaxWidth().height((gameAvail() * 0.56f).coerceIn(260.dp, 720.dp)).clip(RoundedCornerShape(22.dp)).border(2.dp, Color(0x66B388FF), RoundedCornerShape(22.dp))
    ) {
      BowlScene(
        sim = animSim, timeProvider = { playT.value }, standing = if (animSim == null) shownMask else mask, aim = aim, aimPath = aimPath,
        showAim = myTurn || remoteOn, camBlend = { camBlend.value }, sweep = { sweep.value }, drop = { drop.value }, flash = { flash.value },
        ball = BOWL_BALLS[(if (animating) ballIdx else myBall).coerceIn(0, BOWL_BALLS.size - 1)],
        modifier = Modifier.fillMaxSize(),
        onDrag = { dx, wpx -> aimX = (aimX + dx / wpx * 1.3f).coerceIn(-0.45f, 0.45f) }
      )
      val ledSeat = turn.coerceIn(0, (seats.size - 1).coerceAtLeast(0))
      val ledProg = bowlProg(shownR.getOrElse(ledSeat) { emptyList() })
      BowlLedBoard(
        name = names.getOrElse(ledSeat) { "" }, frame = ledProg.frame + 1, ball = ledProg.ball + 1,
        total = bowlTotal(shownR.getOrElse(ledSeat) { emptyList() }), text = if (animating) "" else msg, key = tid,
        modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(0.92f)
      )
      Text(
        if (animating) "🎳 ¡Rueda la bola!" else if (gameOver) "Fin de la partida" else if (myTurn) "Tu turno · arrastrá para mover la bola" else if (remoteOn) "👀 ${nameAt(turn)} está apuntando…" else "Turno de ${nameAt(turn)}",
        color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.Center,
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp).clip(RoundedCornerShape(50)).background(Color(0x99000000)).padding(horizontal = 14.dp, vertical = 5.dp)
      )
      if (myTurn && !animating && !gameOver) {
        BowlPowerMeter(aimPower, { aimPower = it }, Modifier.align(Alignment.CenterStart).padding(start = 8.dp, top = 70.dp, bottom = 50.dp).width(46.dp).fillMaxHeight())
        BowlHookDial(aimHook, { aimHook = it }, Modifier.align(Alignment.CenterEnd).padding(end = 8.dp).size(92.dp))
        Row(Modifier.align(Alignment.BottomStart).padding(start = 10.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
          BowlRoundBtn("◀") { aimAng = (aimAng - 0.008f).coerceIn(-0.06f, 0.06f) }
          BowlRoundBtn("▶") { aimAng = (aimAng + 0.008f).coerceIn(-0.06f, 0.06f) }
        }
        Box(
          Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp).size(84.dp, 56.dp).clip(RoundedCornerShape(28.dp))
            .background(if (pending != tid) Brush.horizontalGradient(listOf(Color(0xFFFF4D8D), Color(0xFFFF9A3C))) else Brush.horizontalGradient(listOf(Color(0xFF555555), Color(0xFF777777))))
            .border(2.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(28.dp))
            .clickable(enabled = pending != tid) {
              pending = tid
              doThrow(turn, aimX.toDouble(), aimAng.toDouble(), aimHook.toDouble(), aimPower.toDouble())
            },
          contentAlignment = Alignment.Center
        ) { Text("🎳 TIRAR", color = Color.White, fontWeight = FontWeight.Black, fontSize = 13.sp) }
        val owned = remember(ownedTick) { Cosmetics.ownedBalls(ctx).sorted() }
        val bb = BOWL_BALLS[myBall.coerceIn(0, BOWL_BALLS.size - 1)]
        Box(
          Modifier.align(Alignment.BottomEnd).padding(end = 10.dp, bottom = 8.dp).size(46.dp).clip(CircleShape)
            .background(androidx.compose.ui.graphics.Brush.radialGradient(listOf(bb.hi, bb.mid, bb.lo))).border(2.dp, Color.White.copy(alpha = 0.8f), CircleShape)
            .clickable { val nx = owned[(owned.indexOf(myBall).coerceAtLeast(0) + 1) % owned.size]; myBall = nx; Cosmetics.selectBall(ctx, nx); ownedTick++ },
          contentAlignment = Alignment.Center
        ) { Text("🔄", fontSize = 14.sp) }
      }
      val bt = bannerText
      if (bt != null) androidx.compose.runtime.key(bannerId) {
        BowlBanner(bannerId, bt, bannerColor, { bannerText = null }, Modifier.align(Alignment.Center))
      }
    }
    Spacer(Modifier.height(8.dp))
    if (gameOver && !animating) {
      ResultBanner(msg)
      GradientButton("Revancha", modifier = Modifier.fillMaxWidth(), onClick = {
        val data = mutableMapOf<String, Any?>(
          "seats" to seats, "turn" to 0, "mask" to BFULL, "tid" to (tid + 1), "bump" to bump, "lane" to laneIdx, "done" to false, "msg" to "¡Revancha!",
          "tseat" to -1, "tx" to 0.0, "ta" to 0.0, "th" to 0.0, "tp" to 0.7, "tseed" to 0, "tmask" to BFULL, "tres" to BFULL
        )
        for (i in 0 until 4) data["r$i"] = emptyList<Int>()
        c.vm.publishGame("bowl", data)
      })
    } else if (myTurn) {
      Text("Arrastra sobre la pista para mover la bola · fuerza y efecto a los lados", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    } else {
      Text(if (animating) "…" else msg, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
    Spacer(Modifier.height(8.dp))
    GlassCard(modifier = Modifier.fillMaxWidth(), corner = 20.dp) {
      BowlScoreboard(names, shownR, if (animating || gameOver) -1 else turn)
    }
  }
}
