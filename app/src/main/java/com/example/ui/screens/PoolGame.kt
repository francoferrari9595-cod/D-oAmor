package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.GlassCard
import com.example.ui.components.GradientButton
import com.example.ui.components.SoftButton
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive

// ═════════════════════════════════════════════════════════════════════════════
// POOL — hecho desde cero.
//  · Física 2D en metros (mesa de 9 pies) con bolas que ruedan y giran en 3D, bandas, troneras y efecto
//    (retro / seguido / lateral). Todas las pantallas repiten el mismo tiro a partir de los parámetros publicados.
//  · Bola 8 (grupos, 8 final, faltas, bola en mano) y bola 9 (orden numérico), con formatos
//    1 vs Bot, 1 vs 1 y 2 vs 2 (tu pareja y vos contra dos bots).
// ═════════════════════════════════════════════════════════════════════════════

private const val PR = 0.0345         // radio de una bola (m): más grandes para distinguirlas bien en pantallas chicas
private const val PD = 2 * PR
private const val PHW = 0.635         // mitad del ancho jugable (m)
private const val PHL = 1.27          // mitad del largo jugable (m)
private const val P_E_BALL = 0.96     // restitución bola-bola
private const val P_E_CUSH = 0.86     // restitución bola-banda
private const val P_DT = 1.0 / 480.0
/** Repetición de embocadas: se muestra si en un mismo tiro cayeron al menos esta cantidad de bolas (sin contar la blanca). */
private const val POOL_REPLAY_MIN = 2
private const val POOL_REPLAY_PRE = 1.2f    // segundos de antes de caer
private const val POOL_REPLAY_POST = 0.4f   // segundos de después
private const val POOL_REPLAY_SPEED = 0.7f  // cámara lenta
private const val POOL_REPLAY_MAX = 4
private val POOL_LOGO_PAINT: android.graphics.Paint by lazy {
  android.graphics.Paint().apply {
    isAntiAlias = true; textAlign = android.graphics.Paint.Align.CENTER; color = 0x1EFFFFFF
    typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
  }
}

private class PPocket(val x: Double, val y: Double, val r: Double)
private val POOL_POCKETS = listOf(
  PPocket(-PHW - 0.012, -PHL - 0.012, 0.092), PPocket(PHW + 0.012, -PHL - 0.012, 0.092),
  PPocket(-PHW - 0.012, PHL + 0.012, 0.092), PPocket(PHW + 0.012, PHL + 0.012, 0.092),
  PPocket(-PHW - 0.028, 0.0, 0.084), PPocket(PHW + 0.028, 0.0, 0.084)
)

private val POOL_COLORS = listOf(
  Color(0xFFF2EFE6), Color(0xFFF9C623), Color(0xFF1F51B8), Color(0xFFD32F2F), Color(0xFF6A1B9A),
  Color(0xFFF57C00), Color(0xFF1B8A3A), Color(0xFF7B1E1E), Color(0xFF111111)
)
private fun poolColor(id: Int): Color = if (id <= 8) POOL_COLORS[id] else POOL_COLORS[id - 8]

/** Posiciones iniciales de la mesa: triángulo de 15 bolas (bola 8) o rombo de 9 (bola 9) y la blanca en el cabezal. */
private fun poolRack(mode: Int, seed: Int): Pair<DoubleArray, DoubleArray> {
  val px = DoubleArray(16) { 0.0 }
  val py = DoubleArray(16) { 0.0 }
  val rows: List<List<Int>> = if (mode == 8) {
    listOf(listOf(1), listOf(9, 2), listOf(3, 8, 10), listOf(11, 4, 12, 5), listOf(6, 13, 7, 14, 15))
  } else {
    val others = mutableListOf(2, 3, 4, 5, 6, 7, 8)
    others.shuffle(java.util.Random(seed.toLong()))
    listOf(listOf(1), listOf(others[0], others[1]), listOf(others[2], 9, others[3]), listOf(others[4], others[5]), listOf(others[6]))
  }
  val dy = PD * sqrt(3.0) / 2 + 0.0006
  rows.forEachIndexed { r, row ->
    row.forEachIndexed { i, b ->
      px[b] = (i - (row.size - 1) / 2.0) * (PD + 0.0008)
      py[b] = 0.635 + r * dy
    }
  }
  px[0] = 0.0; py[0] = -0.635
  return Pair(px, py)
}

private fun poolAliveMask(mode: Int): Int = if (mode == 8) 0xFFFF else (0x3FF or 1)

/** Resultado de un tiro: cuadros a 60 fps con posición y orientación de cada bola, y lo que ocurrió. */
private class PoolSim(
  val frames: Int, val bx: FloatArray, val by: FloatArray, val rot: FloatArray,
  val pocketT: FloatArray, val pocketIdx: IntArray,
  val events: List<FloatArray>,                  // tipo (0 bola, 1 banda, 2 tronera), t, x, y, fuerza
  val first: Int, val railAfter: Boolean, val pocketed: List<Int>,
  val finalX: DoubleArray, val finalY: DoubleArray, val finalAlive: Int, val finalRot: FloatArray, val duration: Float, val startMask: Int
)

private fun identityRot(): FloatArray {
  val a = FloatArray(16 * 9)
  for (i in 0 until 16) { a[i * 9] = 1f; a[i * 9 + 4] = 1f; a[i * 9 + 8] = 1f }
  return a
}

/** Gira la orientación `m` (9 valores, fila a fila, desde `o`) un ángulo `th` alrededor del eje horizontal (ax, ay, 0). */
private fun rotateBall(m: FloatArray, o: Int, ax: Double, ay: Double, th: Double) {
  val c = cos(th); val s = sin(th); val k = 1 - c
  val r00 = c + k * ax * ax; val r01 = k * ax * ay; val r02 = s * ay
  val r10 = k * ax * ay; val r11 = c + k * ay * ay; val r12 = -s * ax
  val r20 = -s * ay; val r21 = s * ax; val r22 = c
  val o0 = DoubleArray(9) { m[o + it].toDouble() }
  for (col in 0 until 3) {
    m[o + col] = (r00 * o0[col] + r01 * o0[3 + col] + r02 * o0[6 + col]).toFloat()
    m[o + 3 + col] = (r10 * o0[col] + r11 * o0[3 + col] + r12 * o0[6 + col]).toFloat()
    m[o + 6 + col] = (r20 * o0[col] + r21 * o0[3 + col] + r22 * o0[6 + col]).toFloat()
  }
}

private fun poolSimulate(
  px0: DoubleArray, py0: DoubleArray, aliveMask: Int, angle: Double, power: Double, sx: Double, sy: Double, rot0: FloatArray
): PoolSim {
  val x = px0.copyOf(); val y = py0.copyOf()
  val vx = DoubleArray(16); val vy = DoubleArray(16)
  val alive = BooleanArray(16) { ((aliveMask shr it) and 1) == 1 }
  val v0 = 0.9 + 7.6 * power
  vx[0] = v0 * sin(angle); vy[0] = v0 * cos(angle)
  var topspin = sy * v0 * 0.5
  var side = sx
  val cdx = sin(angle); val cdy = cos(angle)
  var first = -1
  var railAfter = false
  val pocketed = ArrayList<Int>()
  val pocketT = FloatArray(16) { -1f }
  val pocketIdx = IntArray(16) { -1 }
  val events = ArrayList<FloatArray>()
  val maxFrames = 960
  val fx = FloatArray(maxFrames * 16); val fy = FloatArray(maxFrames * 16); val fr = FloatArray(maxFrames * 16 * 9)
  val rot = rot0.copyOf()
  val lastX = x.copyOf(); val lastY = y.copyOf()
  var frames = 0
  var quiet = 0
  var tEnd = 0.0
  val steps = (16.0 / P_DT).toInt()
  for (step in 0 until steps) {
    val t = step * P_DT
    tEnd = t
    for (i in 0 until 16) {
      if (!alive[i]) continue
      x[i] += vx[i] * P_DT; y[i] += vy[i] * P_DT
      val sp = hypot(vx[i], vy[i])
      if (sp > 0) {
        val ns = max(0.0, sp - (0.12 + 0.09 * sp) * P_DT)   // rodadura liviana: las bolas deslizan largo, como en una mesa real
        val k = ns / sp
        vx[i] *= k; vy[i] *= k
      }
      if (sp < 0.004) { vx[i] = 0.0; vy[i] = 0.0 }
    }
    // troneras
    for (i in 0 until 16) {
      if (!alive[i]) continue
      for ((pi, p) in POOL_POCKETS.withIndex()) {
        if (hypot(x[i] - p.x, y[i] - p.y) < p.r) {
          alive[i] = false; vx[i] = 0.0; vy[i] = 0.0
          pocketed.add(i); pocketT[i] = t.toFloat(); pocketIdx[i] = pi
          events.add(floatArrayOf(2f, t.toFloat(), x[i].toFloat(), y[i].toFloat(), 1f))
          break
        }
      }
    }
    // bandas
    for (i in 0 until 16) {
      if (!alive[i]) continue
      val inCorner = abs(y[i]) > PHL - 0.11 && abs(x[i]) > PHW - 0.11
      val inSide = abs(y[i]) < 0.074
      if (!inCorner && !inSide) {
        var hit = false
        if (x[i] < -PHW + PR) { x[i] = -PHW + PR; vx[i] = -vx[i] * P_E_CUSH; vy[i] *= 0.985; hit = true }
        else if (x[i] > PHW - PR) { x[i] = PHW - PR; vx[i] = -vx[i] * P_E_CUSH; vy[i] *= 0.985; hit = true }
        if (hit) {
          if (i == 0 && abs(side) > 0.01) { vy[0] += side * 0.18 * abs(vx[0]); side *= 0.5 }
          if (first != -1) railAfter = true
          events.add(floatArrayOf(1f, t.toFloat(), x[i].toFloat(), y[i].toFloat(), abs(vx[i]).toFloat()))
        }
      }
      if (!(abs(x[i]) > PHW - 0.11 && abs(y[i]) > PHL - 0.11)) {
        var hit = false
        if (y[i] < -PHL + PR) { y[i] = -PHL + PR; vy[i] = -vy[i] * P_E_CUSH; vx[i] *= 0.985; hit = true }
        else if (y[i] > PHL - PR) { y[i] = PHL - PR; vy[i] = -vy[i] * P_E_CUSH; vx[i] *= 0.985; hit = true }
        if (hit) {
          if (i == 0 && abs(side) > 0.01) { vx[0] -= side * 0.18 * abs(vy[0]); side *= 0.5 }
          if (first != -1) railAfter = true
          events.add(floatArrayOf(1f, t.toFloat(), x[i].toFloat(), y[i].toFloat(), abs(vy[i]).toFloat()))
        }
      }
    }
    // mandíbulas de las troneras: nada sale de la mesa
    for (i in 0 until 16) {
      if (!alive[i]) continue
      if (x[i] < -PHW - 0.06) { x[i] = -PHW - 0.06; vx[i] = abs(vx[i]) * 0.4 }
      else if (x[i] > PHW + 0.06) { x[i] = PHW + 0.06; vx[i] = -abs(vx[i]) * 0.4 }
      if (y[i] < -PHL - 0.06) { y[i] = -PHL - 0.06; vy[i] = abs(vy[i]) * 0.4 }
      else if (y[i] > PHL + 0.06) { y[i] = PHL + 0.06; vy[i] = -abs(vy[i]) * 0.4 }
    }
    // bola contra bola
    for (a in 0 until 16) {
      if (!alive[a]) continue
      for (b in a + 1 until 16) {
        if (!alive[b]) continue
        val dx = x[b] - x[a]; val dy = y[b] - y[a]
        val d = hypot(dx, dy)
        if (d < PD && d > 1e-9) {
          val nx = dx / d; val ny = dy / d
          val vn = (vx[b] - vx[a]) * nx + (vy[b] - vy[a]) * ny
          if (vn < 0) {
            val j = -(1 + P_E_BALL) * vn / 2
            vx[a] -= j * nx; vy[a] -= j * ny; vx[b] += j * nx; vy[b] += j * ny
            if (a == 0) {
              if (first == -1) first = b
              if (abs(topspin) > 0.01) {
                val k = topspin * 0.20 * abs(j) / max(v0, 1e-6)
                vx[0] += cdx * k * v0 * 0.6; vy[0] += cdy * k * v0 * 0.6
                topspin *= 0.4
              }
            }
            events.add(floatArrayOf(0f, t.toFloat(), ((x[a] + x[b]) / 2).toFloat(), ((y[a] + y[b]) / 2).toFloat(), abs(j).toFloat()))
          }
          val ov = (PD - d) / 2
          x[a] -= nx * ov; y[a] -= ny * ov; x[b] += nx * ov; y[b] += ny * ov
        }
      }
    }
    // registro a 60 fps con la orientación de cada bola (rodar sin deslizar)
    if (step % 8 == 0 && frames < maxFrames) {
      for (i in 0 until 16) {
        if (alive[i]) {
          val ddx = x[i] - lastX[i]; val ddy = y[i] - lastY[i]
          val dist = hypot(ddx, ddy)
          if (dist > 1e-7) rotateBall(rot, i * 9, -ddy / dist, ddx / dist, dist / PR)
        }
        lastX[i] = x[i]; lastY[i] = y[i]
        fx[frames * 16 + i] = x[i].toFloat(); fy[frames * 16 + i] = y[i].toFloat()
        for (q in 0 until 9) fr[(frames * 16 + i) * 9 + q] = rot[i * 9 + q]
      }
      frames++
    }
    var moving = 0.0
    for (i in 0 until 16) if (alive[i]) moving = max(moving, hypot(vx[i], vy[i]))
    if (moving < 0.006) quiet++ else quiet = 0
    if (quiet > 120) break
  }
  var mask = 0
  for (i in 0 until 16) if (alive[i]) mask = mask or (1 shl i)
  return PoolSim(frames, fx, fy, fr, pocketT, pocketIdx, events, first, railAfter, pocketed, x, y, mask, rot, max(0.1f, (frames - 1) / 60f), aliveMask)
}

// ───────────────────────────── Reglas ─────────────────────────────

private fun groupBalls(g: Int): IntRange = if (g == 0) 1..7 else 9..15

private class PoolRuling(
  val foul: Boolean, val reason: String, val winner: Int, val cont: Boolean,
  val groups: IntArray, val respot9: Boolean
)

/**
 * Aplica las reglas de bola 8 o bola 9 a un tiro. `groups[team]`: -1 sin grupo, 0 lisas, 1 rayadas.
 * `winner` es el equipo ganador o -1.
 */
private fun poolRules(mode: Int, groups: IntArray, team: Int, aliveBefore: Int, sim: PoolSim): PoolRuling {
  val pocketed = sim.pocketed.filter { it != 0 }
  val cuePocketed = sim.pocketed.contains(0)
  val live = (1..15).filter { ((aliveBefore shr it) and 1) == 1 }
  var foul = false
  var reason = ""
  if (sim.first == -1) { foul = true; reason = "no tocó ninguna bola" }
  else {
    val legal = if (mode == 8) {
      val g = groups[team]
      if (g == -1) sim.first != 8
      else {
        val cleared = groupBalls(g).none { live.contains(it) }
        groupBalls(g).contains(sim.first) || (sim.first == 8 && cleared)
      }
    } else sim.first == (live.minOrNull() ?: 0)
    if (!legal) { foul = true; reason = "tocó primero una bola que no correspondía" }
  }
  if (!foul && pocketed.isEmpty() && !sim.railAfter) { foul = true; reason = "ninguna bola tocó banda" }
  if (cuePocketed) { foul = true; reason = "embocó la blanca" }
  val other = 1 - team
  val ng = groups.copyOf()
  if (mode == 8) {
    if (pocketed.contains(8)) {
      val g = groups[team]
      val cleared = g != -1 && groupBalls(g).none { live.contains(it) }
      return if (cleared && !foul) PoolRuling(false, "", team, false, ng, false)
      else PoolRuling(true, if (reason.isEmpty()) "embocó la 8 antes de tiempo" else reason, other, false, ng, false)
    }
    if (groups[team] == -1 && !foul && pocketed.isNotEmpty()) {
      val g = if (pocketed[0] in 1..7) 0 else 1
      ng[team] = g; ng[other] = 1 - g
    }
    val g = ng[team]
    val mine = pocketed.filter { g != -1 && groupBalls(g).contains(it) }
    return PoolRuling(foul, reason, -1, !foul && mine.isNotEmpty(), ng, false)
  }
  if (pocketed.contains(9)) {
    if (!foul) return PoolRuling(false, "", team, false, ng, false)
    return PoolRuling(true, reason, -1, false, ng, true)
  }
  return PoolRuling(foul, reason, -1, !foul && pocketed.isNotEmpty(), ng, false)
}

// ───────────────────────────── Bot (bola fantasma) ─────────────────────────────

private fun segClear(px: DoubleArray, py: DoubleArray, alive: Int, ax: Double, ay: Double, bx: Double, by: Double, ignore: Set<Int>): Boolean {
  val dx = bx - ax; val dy = by - ay
  val l2 = dx * dx + dy * dy
  if (l2 == 0.0) return true
  for (i in 0 until 16) {
    if (ignore.contains(i) || ((alive shr i) and 1) == 0) continue
    val t = max(0.0, min(1.0, ((px[i] - ax) * dx + (py[i] - ay) * dy) / l2))
    if (hypot(px[i] - (ax + t * dx), py[i] - (ay + t * dy)) < PD * 0.98) return false
  }
  return true
}

private class PoolPlan(val angle: Double, val power: Double, val target: Int, val score: Double)

/** Elige un tiro: para cada bola objetivo y cada tronera, calcula dónde tiene que pegar la blanca y descarta los tiros bloqueados. */
private fun poolChoose(px: DoubleArray, py: DoubleArray, alive: Int, targets: List<Int>, cx: Double, cy: Double): PoolPlan {
  var best: PoolPlan? = null
  for (tb in targets) {
    if (((alive shr tb) and 1) == 0) continue
    val bx = px[tb]; val by = py[tb]
    for (p in POOL_POCKETS) {
      val dx = p.x - bx; val dy = p.y - by
      val d = hypot(dx, dy)
      val ux = dx / d; val uy = dy / d
      val gx = bx - ux * PD; val gy = by - uy * PD
      val vx = gx - cx; val vy = gy - cy
      val dc = hypot(vx, vy)
      if (dc < 1e-6) continue
      val cut = Math.toDegrees(acos(((vx * ux + vy * uy) / dc).coerceIn(-1.0, 1.0)))
      if (cut > 68) continue
      if (!segClear(px, py, alive, cx, cy, gx, gy, setOf(0, tb))) continue
      if (!segClear(px, py, alive, bx, by, p.x, p.y, setOf(0, tb))) continue
      val score = dc + d * 1.2 + cut * 0.02
      if (best == null || score < best.score) best = PoolPlan(atan2(vx, vy), min(1.0, 0.28 + 0.18 * (dc + d) + cut * 0.004), tb, score)
    }
  }
  if (best != null) return best
  // sin tiro limpio: pegarle a la bola objetivo más cercana
  val cand = targets.filter { ((alive shr it) and 1) == 1 }.ifEmpty { (1..15).filter { ((alive shr it) and 1) == 1 } }
  val tb = cand.minByOrNull { hypot(px[it] - cx, py[it] - cy) } ?: 1
  return PoolPlan(atan2(px[tb] - cx, py[tb] - cy), 0.55, tb, 99.0)
}

/** Bolas que le corresponde embocar al equipo en este momento. */
private fun poolTargets(mode: Int, groups: IntArray, team: Int, alive: Int): List<Int> {
  val live = (1..15).filter { ((alive shr it) and 1) == 1 }
  if (mode == 9) return listOf(live.minOrNull() ?: 9)
  val g = groups[team]
  if (g == -1) return live.filter { it != 8 }
  val mine = live.filter { groupBalls(g).contains(it) }
  return mine.ifEmpty { listOf(8) }
}


// ───────────────────────────── Dibujo: mesa, bolas 3D, taco y guía ─────────────────────────────

private class PoolView(val sc: Float, val cx: Float, val cy: Float)

private fun poolView(size: Size): PoolView {
  val sc = min(size.width / (2 * PHW + 0.24), size.height / (2 * PHL + 0.24)).toFloat()
  return PoolView(sc, size.width / 2f, size.height / 2f)
}
private fun PoolView.toX(x: Double): Float = cx + (x * sc).toFloat()
private fun PoolView.toY(y: Double): Float = cy - (y * sc).toFloat()

// Pieles equipadas en la tienda (se leen al abrir el juego)
private var skinFelt1 = Color(0xFF1FA85A); private var skinFelt2 = Color(0xFF0C5E34)
private var skinCue1 = Color(0xFFE6C48A); private var skinCue2 = Color(0xFF3B2412)

private fun DrawScope.drawPoolTable(v: PoolView, hue: Float) {
  val sc = v.sc
  val frameW = (2 * PHW + 0.24).toFloat() * sc
  val frameH = (2 * PHL + 0.24).toFloat() * sc
  val fl = v.cx - frameW / 2f
  val ft = v.cy - frameH / 2f
  // marco de madera
  drawRoundRect(Brush.linearGradient(listOf(Color(0xFF8A5530), Color(0xFF4A2A14), Color(0xFF6E4222)), Offset(fl, ft), Offset(fl + frameW, ft + frameH)), Offset(fl, ft), Size(frameW, frameH), CornerRadius(0.07f * sc))
  drawRoundRect(Color(0x44FFFFFF), Offset(fl + 1.5f, ft + 1.5f), Size(frameW - 3f, frameH - 3f), CornerRadius(0.07f * sc), style = Stroke(width = 1.5f))
  // veta de la madera y filete dorado de neón
  for (i in 0 until 30) {
    val yy = ft + frameH * (i / 30f)
    drawLine(Color(0x16000000), Offset(fl + 4f, yy), Offset(fl + frameW - 4f, yy + 3f), 1.2f)
  }
  drawRoundRect(Color(0x66FFC857), Offset(fl + 0.014f * sc, ft + 0.014f * sc), Size(frameW - 0.028f * sc, frameH - 0.028f * sc), CornerRadius(0.06f * sc), style = Stroke(width = 2.4f))
  drawRoundRect(Color(0x22FFC857), Offset(fl + 0.004f * sc, ft + 0.004f * sc), Size(frameW - 0.008f * sc, frameH - 0.008f * sc), CornerRadius(0.07f * sc), style = Stroke(width = 6f))
  // paño: banda de banda (cushion) y centro jugable
  val clothL = v.toX(-PHW - 0.075); val clothT = v.toY(PHL + 0.075)
  val clothW = ((2 * PHW + 0.15) * sc).toFloat(); val clothH = ((2 * PHL + 0.15) * sc).toFloat()
  drawRoundRect(androidx.compose.ui.graphics.lerp(skinFelt2, Color.Black, 0.25f), Offset(clothL, clothT), Size(clothW, clothH), CornerRadius(0.03f * sc))
  val pl = v.toX(-PHW); val pt = v.toY(PHL)
  drawRect(
    Brush.radialGradient(listOf(skinFelt1, androidx.compose.ui.graphics.lerp(skinFelt1, skinFelt2, 0.5f), skinFelt2), center = Offset(v.cx, v.cy), radius = (PHL * sc).toFloat() * 1.2f),
    Offset(pl, pt), Size((2 * PHW * sc).toFloat(), (2 * PHL * sc).toFloat())
  )
  // trama sutil del paño
  for (i in 0 until 90) {
    val sx = ((i * 7919) % 1000) / 1000f
    val sy = ((i * 104729) % 997) / 997f
    drawCircle(Color(0x0CFFFFFF), 0.012f * sc, Offset(pl + (2 * PHW * sc).toFloat() * sx, pt + (2 * PHL * sc).toFloat() * sy))
  }
  // marca de agua de la casa, girada sobre el paño
  drawIntoCanvas { cv ->
    val nc = cv.nativeCanvas
    POOL_LOGO_PAINT.textSize = 0.15f * sc
    nc.save(); nc.rotate(-90f, v.cx, v.cy)
    nc.drawText("KOMBO ARENA", v.cx, v.cy + 0.05f * sc, POOL_LOGO_PAINT)
    nc.restore()
  }
  // bisel de la banda
  drawRect(Color(0x33FFFFFF), Offset(pl, pt), Size((2 * PHW * sc).toFloat(), (2 * PHL * sc).toFloat()), style = Stroke(width = 2.5f))
  drawRect(Color(0x55000000), Offset(pl - 3f, pt - 3f), Size((2 * PHW * sc).toFloat() + 6f, (2 * PHL * sc).toFloat() + 6f), style = Stroke(width = 4f))
  // línea del cabezal y punto del pie
  drawLine(Color(0x22FFFFFF), Offset(pl, v.toY(-0.635)), Offset(pl + (2 * PHW * sc).toFloat(), v.toY(-0.635)), 1.5f)
  drawCircle(Color(0x33FFFFFF), 0.007f * sc, Offset(v.toX(0.0), v.toY(0.635)))
  drawCircle(Color(0x33FFFFFF), 0.007f * sc, Offset(v.toX(0.0), v.toY(-0.635)))
  // diamantes
  val diaCol = Color(0xFFF1E3C4)
  for (k in listOf(-0.9525, -0.635, -0.3175, 0.3175, 0.635, 0.9525)) {
    drawCircle(diaCol, 0.0085f * sc, Offset(v.toX(-PHW - 0.12 + 0.0), v.toY(k)))
    drawCircle(diaCol, 0.0085f * sc, Offset(v.toX(PHW + 0.12 - 0.0), v.toY(k)))
  }
  for (k in listOf(-0.3175, 0.0, 0.3175)) {
    drawCircle(diaCol, 0.0085f * sc, Offset(v.toX(k), v.toY(PHL + 0.12)))
    drawCircle(diaCol, 0.0085f * sc, Offset(v.toX(k), v.toY(-PHL - 0.12)))
  }
  // troneras
  for (p in POOL_POCKETS) {
    val c = Offset(v.toX(p.x), v.toY(p.y))
    val r = (p.r * 0.86).toFloat() * sc
    drawCircle(Brush.radialGradient(listOf(Color(0xFF000000), Color(0xFF0A0A0A), Color(0xFF3A2414)), center = c, radius = r), r, c)
    drawCircle(Brush.sweepGradient(listOf(Color(0xFFFFE9A8), Color(0xFF9C7A33), Color(0xFFFFE9A8), Color(0xFF7A5A22), Color(0xFFFFE9A8)), c), r * 1.04f, c, style = Stroke(width = 0.02f * sc))
  }
  // luz cenital sobre el centro
  drawCircle(Brush.radialGradient(listOf(Color(0x33FFFFFF), Color.Transparent), center = Offset(v.cx, v.cy - 0.2f * sc), radius = 1.4f * sc), 1.4f * sc, Offset(v.cx, v.cy - 0.2f * sc))
}

/** Dibuja una bola como esfera: sombra, color, franja o puntos según su orientación 3D, número y brillo. */
private fun DrawScope.drawPoolBall(c: Offset, r: Float, id: Int, m: FloatArray, o: Int, alpha: Float, paint: android.graphics.Paint) {
  if (alpha <= 0.02f) return
  drawCircle(Color(0x66000000).copy(alpha = 0.4f * alpha), r * 1.02f, Offset(c.x + r * 0.16f, c.y + r * 0.2f))
  val base = if (id >= 9) Color(0xFFF2EFE6) else poolColor(id)
  drawCircle(base.copy(alpha = alpha), r, c)
  if (id >= 9) {
    // franja: familia de círculos paralelos al ecuador de la bola, solo la parte visible
    val ax = m[o + 1]; val ay = m[o + 4]; val az = m[o + 7]
    var ux: Float; var uy: Float; var uz: Float
    if (abs(az) < 0.9f) { ux = ay; uy = -ax; uz = 0f } else { ux = 0f; uy = az; uz = -ay }
    val ul = sqrt(ux * ux + uy * uy + uz * uz)
    ux /= ul; uy /= ul; uz /= ul
    val vx = ay * uz - az * uy; val vy = az * ux - ax * uz; val vz = ax * uy - ay * ux
    val col = poolColor(id).copy(alpha = alpha)
    var tt = -0.52f
    while (tt <= 0.521f) {
      val rho = sqrt(1f - tt * tt)
      var prev: Offset? = null
      for (k in 0..36) {
        val phi = k * (2f * PI.toFloat() / 36f)
        val cp = cos(phi); val sp = sin(phi)
        val pz = tt * az + rho * (cp * uz + sp * vz)
        if (pz > 0.02f) {
          val pxn = tt * ax + rho * (cp * ux + sp * vx)
          val pyn = tt * ay + rho * (cp * uy + sp * vy)
          val pt = Offset(c.x + pxn * r, c.y - pyn * r)
          if (prev != null) drawLine(col, prev, pt, r * 0.17f)
          prev = pt
        } else prev = null
      }
      tt += 0.104f
    }
  }
  // puntos con número en dos polos opuestos de la bola
  for (sign in listOf(1f, -1f)) {
    val nz = sign * m[o + 8]
    if (nz > 0.12f) {
      val sc = Offset(c.x + sign * m[o + 2] * r, c.y - sign * m[o + 5] * r)
      if (id == 0) {
        drawCircle(Color(0xFFD32F2F).copy(alpha = alpha), r * 0.14f * nz, sc)
      } else {
        drawCircle(Color.White.copy(alpha = alpha), r * 0.42f * nz.coerceAtLeast(0.35f), sc)
        if (nz > 0.55f) {
          paint.textSize = r * 0.62f * nz
          paint.alpha = (255 * alpha).toInt()
          drawIntoCanvas { it.nativeCanvas.drawText("$id", sc.x, sc.y + paint.textSize * 0.35f, paint) }
        }
      }
    }
  }
  // volumen: sombreado en los bordes y brillo
  drawCircle(Brush.radialGradient(listOf(Color.Transparent, Color.Transparent, Color(0x55000000).copy(alpha = 0.33f * alpha)), center = c, radius = r), r, c)
  val hl = Offset(c.x - r * 0.36f, c.y - r * 0.4f)
  drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.85f * alpha), Color.Transparent), center = hl, radius = r * 0.55f), r * 0.55f, hl)
  // reflejo de la lámpara y luz rebotada del paño (aspecto de resina pulida)
  drawOval(Color.White.copy(alpha = 0.55f * alpha), Offset(c.x - r * 0.5f, c.y - r * 0.62f), Size(r * 0.46f, r * 0.2f))
  drawCircle(Brush.radialGradient(listOf(skinFelt1.copy(alpha = 0.28f * alpha), Color.Transparent), center = Offset(c.x + r * 0.3f, c.y + r * 0.6f), radius = r * 0.6f), r * 0.6f, Offset(c.x + r * 0.3f, c.y + r * 0.6f))
}

private class PoolTrace(val endX: Double, val endY: Double, val hit: Int)

/** Hasta dónde llega la línea de puntería: primera bola que toca (bola fantasma) o la banda. */
private fun poolTrace(px: DoubleArray, py: DoubleArray, alive: Int, cx: Double, cy: Double, angle: Double): PoolTrace {
  val dx = sin(angle); val dy = cos(angle)
  var tBall = Double.MAX_VALUE
  var hit = -1
  for (i in 1..15) {
    if (((alive shr i) and 1) == 0) continue
    val rx = px[i] - cx; val ry = py[i] - cy
    val b = rx * dx + ry * dy
    if (b <= 0) continue
    val disc = b * b - (rx * rx + ry * ry - PD * PD)
    if (disc < 0) continue
    val t = b - sqrt(disc)
    if (t > 0 && t < tBall) { tBall = t; hit = i }
  }
  var tc = Double.MAX_VALUE
  if (dx > 1e-9) tc = min(tc, (PHW - PR - cx) / dx) else if (dx < -1e-9) tc = min(tc, (-PHW + PR - cx) / dx)
  if (dy > 1e-9) tc = min(tc, (PHL - PR - cy) / dy) else if (dy < -1e-9) tc = min(tc, (-PHL + PR - cy) / dy)
  val useBall = hit != -1 && tBall < tc
  val t = if (useBall) tBall else tc
  return PoolTrace(cx + dx * t, cy + dy * t, if (useBall) hit else -1)
}

private fun DrawScope.drawPoolCue(v: PoolView, cx: Double, cy: Double, angle: Double, gap: Double) {
  val dx = sin(angle); val dy = cos(angle)
  val tip = Offset(v.toX(cx - dx * (PR + gap)), v.toY(cy - dy * (PR + gap)))
  fun along(m: Double) = Offset(v.toX(cx - dx * (PR + gap + m)), v.toY(cy - dy * (PR + gap + m)))
  val sh = Offset(3f, 4f)
  val butt = along(1.45)
  drawLine(Color(0x44000000), tip + sh, butt + sh, 7.dp.toPx(), StrokeCap.Round)
  drawLine(skinCue1, along(0.02), along(0.7), 3.6.dp.toPx(), StrokeCap.Round)
  drawLine(skinCue2, along(0.7), butt, 5.6.dp.toPx(), StrokeCap.Round)
  drawLine(Color(0xFFFFC857), along(0.72), along(0.78), 5.8.dp.toPx(), StrokeCap.Butt)
  drawLine(Color(0xFFFF4D8D), along(1.0), along(1.1), 5.8.dp.toPx(), StrokeCap.Butt)
  drawLine(Color(0xFFF4F4F4), tip, along(0.022), 3.8.dp.toPx(), StrokeCap.Butt)
  drawLine(Color(0xFF4FC3F7), tip, along(0.007), 3.8.dp.toPx(), StrokeCap.Butt)
}

private fun DrawScope.drawPoolScene(
  v: PoolView, sim: PoolSim?, t: Float, strike: Float, px: DoubleArray, py: DoubleArray, alive: Int, restRot: FloatArray,
  numberPaint: android.graphics.Paint, aimOn: Boolean, aimAngle: Double, shotAngle: Double, power: Double, handPos: Offset?, placing: Boolean, hue: Float,
  focus: Int = -1
) {
  drawRect(Brush.verticalGradient(listOf(Color(0xFF0D0716), Color(0xFF1A0F2B))))
  // posiciones y orientaciones en el instante t
  val bx = DoubleArray(16) { px[it] }
  val by = DoubleArray(16) { py[it] }
  var rotArr = restRot
  var rotOff = 0
  var visible = alive
  val sinkA = FloatArray(16) { 1f }
  if (sim != null && sim.frames > 0) {
    val f = (t * 60f).coerceIn(0f, (sim.frames - 1).toFloat()).toInt()
    for (i in 0 until 16) { bx[i] = sim.bx[f * 16 + i].toDouble(); by[i] = sim.by[f * 16 + i].toDouble() }
    rotArr = sim.rot; rotOff = f * 16 * 9
    visible = sim.startMask
    for (i in 0 until 16) {
      if (sim.pocketT[i] >= 0f && t >= sim.pocketT[i]) {
        val p = ((t - sim.pocketT[i]) / 0.28f).coerceIn(0f, 1f)
        if (p >= 1f) visible = visible and (1 shl i).inv()
        sinkA[i] = 1f - p
        val pk = POOL_POCKETS[sim.pocketIdx[i].coerceIn(0, 5)]
        bx[i] = bx[i] + (pk.x - bx[i]) * p; by[i] = by[i] + (pk.y - by[i]) * p
      }
    }
  } else {
    // en reposo solo se ve lo que está vivo; la blanca de "bola en mano" se dibuja aparte
    visible = alive
  }
  // Vista general fija: la cámara NUNCA se acerca ni se mueve, para no perder el panorama de toda la mesa.
  withTransform({ }) {
  drawPoolTable(v, hue)
  // guía de puntería
  if (aimOn && sim == null) {
    val cxw = handPos?.x?.toDouble() ?: bx[0]
    val cyw = handPos?.y?.toDouble() ?: by[0]
    val tr = poolTrace(bx, by, alive, cxw, cyw, aimAngle)
    val a = Offset(v.toX(cxw), v.toY(cyw)); val b = Offset(v.toX(tr.endX), v.toY(tr.endY))
    val len = hypot(b.x - a.x, b.y - a.y)
    var d = PR.toFloat() * v.sc * 1.4f
    while (d < len) {
      val k = d / len
      drawCircle(lerp(Color(0xCCFFFFFF), Color(0xFFFF5252), power.toFloat().coerceIn(0f, 1f) * 0.8f), 2.2f, Offset(a.x + (b.x - a.x) * k, a.y + (b.y - a.y) * k))
      d += 11f
    }
    drawCircle(Color(0xAAFFFFFF), PR.toFloat() * v.sc, b, style = Stroke(width = 1.8f))
    if (tr.hit != -1) {
      val ox = bx[tr.hit] - tr.endX; val oy = by[tr.hit] - tr.endY
      val ol = hypot(ox, oy)
      if (ol > 1e-6) {
        val e = Offset(v.toX(bx[tr.hit] + ox / ol * 0.35), v.toY(by[tr.hit] + oy / ol * 0.35))
        drawLine(Color(0xFFFFE08A), Offset(v.toX(bx[tr.hit]), v.toY(by[tr.hit])), e, 2.2f, StrokeCap.Round)
        // hacia dónde se desvía la blanca después del choque
        val nxn = -ox / ol; val nyn = -oy / ol
        val ddx = sin(aimAngle); val ddy = cos(aimAngle)
        val dot = ddx * nxn + ddy * nyn
        val tdx = ddx - dot * nxn; val tdy = ddy - dot * nyn
        val tl = hypot(tdx, tdy)
        if (tl > 0.05) drawLine(Color(0x9938BDF8), Offset(v.toX(tr.endX), v.toY(tr.endY)), Offset(v.toX(tr.endX + tdx / tl * 0.22), v.toY(tr.endY + tdy / tl * 0.22)), 2.2f, StrokeCap.Round)
      }
    }
  }
  // bolas ordenadas de arriba abajo en pantalla (las de abajo tapan a las de arriba)
  val order = (0 until 16).filter { ((visible shr it) and 1) == 1 }.sortedByDescending { by[it] }
  val r = PR.toFloat() * v.sc
  for (i in order) {
    if (i == 0 && handPos != null && sim == null) continue
    val rr = r * (0.55f + 0.45f * sinkA[i])
    drawPoolBall(Offset(v.toX(bx[i]), v.toY(by[i])), rr, i, rotArr, rotOff + i * 9, if (sinkA[i] < 1f) sinkA[i] else 1f, numberPaint)
  }
  if (handPos != null && sim == null) {
    val c0 = Offset(v.toX(handPos.x.toDouble()), v.toY(handPos.y.toDouble()))
    drawPoolBall(c0, r, 0, restRot, 0, if (placing) 0.65f else 1f, numberPaint)
    if (placing) drawCircle(Color(0xCCFFE08A), r * 1.5f, c0, style = Stroke(width = 2f))
  }
  // efectos de embocada: onda, destello y chispas en la tronera
  if (sim != null) for (i in 0 until 16) {
    val p0 = sim.pocketT[i]
    if (p0 >= 0f && t >= p0 && t < p0 + 0.7f) {
      val k = (t - p0) / 0.7f
      val pk = POOL_POCKETS[sim.pocketIdx[i].coerceIn(0, 5)]
      val pc = Offset(v.toX(pk.x), v.toY(pk.y))
      val col = if (i == 0) Color(0xFFFF5252) else poolColor(i)
      drawCircle(col.copy(alpha = (1f - k) * 0.6f), r * (1.2f + 3.2f * k), pc, style = Stroke(width = 3.5f * (1f - k) + 1f))
      drawCircle(Color.White.copy(alpha = (1f - k) * 0.35f), r * (0.8f + 1.6f * k), pc)
      for (q in 0 until 10) {
        val ang = q * 0.628f + i
        val dd = r * (1.3f + 3.8f * k)
        drawCircle(col.copy(alpha = (1f - k) * 0.9f), 2.6f * (1f - k) + 0.8f, Offset(pc.x + cos(ang) * dd, pc.y + sin(ang) * dd))
      }
    }
  }
  // repetición: estela y aro pulsante sobre la bola que se emboca (sin mover la cámara)
  if (focus >= 0 && sim != null && sim.frames > 0) {
    val f = (t * 60f).coerceIn(0f, (sim.frames - 1).toFloat()).toInt()
    val f0 = max(0, f - 36)
    var prevPt: Offset? = null
    for (q in f0..f) {
      val cur = Offset(v.toX(sim.bx[q * 16 + focus].toDouble()), v.toY(sim.by[q * 16 + focus].toDouble()))
      val pp = prevPt
      if (pp != null) drawLine(poolColor(focus).copy(alpha = ((q - f0).toFloat() / (f - f0 + 1)) * 0.85f), pp, cur, r * 0.55f, StrokeCap.Round)
      prevPt = cur
    }
    val fc = Offset(v.toX(bx[focus]), v.toY(by[focus]))
    drawCircle(Color(0xFFFFE08A), r * 1.9f * (1f + 0.18f * sin(t * 18f)), fc, style = Stroke(width = 3f))
  }
  // taco: se retira según la fuerza y golpea al disparar
  if (aimOn && sim == null && !placing) {
    val cxw = handPos?.x?.toDouble() ?: bx[0]
    val cyw = handPos?.y?.toDouble() ?: by[0]
    drawPoolCue(v, cxw, cyw, aimAngle, 0.012 + power * 0.22)
  } else if (sim != null && strike < 1f) {
    drawPoolCue(v, bx[0], by[0], shotAngle, 0.012 + (1f - strike) * 0.2)
  }
  }
  drawRect(Brush.radialGradient(listOf(Color.Transparent, Color(0x88000000)), center = Offset(v.cx, v.cy), radius = max(size.width, size.height) * 0.75f))
}

@Composable
private fun PoolMiniBall(id: Int, alive: Boolean, size: androidx.compose.ui.unit.Dp = 20.dp) {
  val base = if (id >= 9) Color(0xFFF2EFE6) else poolColor(id)
  Box(
    Modifier.size(size).graphicsLayer { alpha = if (alive) 1f else 0.25f }.clip(CircleShape).background(base)
      .border(if (id >= 9) 3.dp else 0.dp, if (id >= 9) poolColor(id) else Color.Transparent, CircleShape),
    contentAlignment = Alignment.Center
  ) {
    Box(Modifier.size(size * 0.62f).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
      Text("$id", color = Color.Black, fontSize = 8.sp, fontWeight = FontWeight.Black)
    }
  }
}

@Composable
private fun PoolPop(id: Int, text: String, color: Color, onDone: () -> Unit, modifier: Modifier = Modifier) {
  val s = remember { Animatable(0.3f) }
  val a = remember { Animatable(0f) }
  LaunchedEffect(id) {
    launch { a.animateTo(1f, tween(120)) }
    s.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 380f))
    delay(1500)
    a.animateTo(0f, tween(300))
    onDone()
  }
  val shape = RoundedCornerShape(50)
  Box(
    modifier.graphicsLayer { scaleX = s.value; scaleY = s.value; alpha = a.value }
      .clip(shape).background(color).border(2.dp, Color.White, shape).padding(horizontal = 16.dp, vertical = 8.dp)
  ) { Text(text, color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp, textAlign = TextAlign.Center) }
}

// ───────────────────────────── Juego ─────────────────────────────

private val POOL_TEAM_COLORS = listOf(Color(0xFF38BDF8), Color(0xFFFF8A65))

@Composable
internal fun PoolGame(c: GameCtx) {
  run {
    val pctx = androidx.compose.ui.platform.LocalContext.current
    val fe = com.example.model.LocalWallet.equippedItem(pctx, com.example.model.Cat.FELT)
    val cu = com.example.model.LocalWallet.equippedItem(pctx, com.example.model.Cat.CUE)
    skinFelt1 = fe.c1; skinFelt2 = fe.c2; skinCue1 = cu.c1; skinCue2 = cu.c2
  }
  val flow = remember { c.vm.gameState("pool") }
  val st by flow.collectAsState(initial = null)
  fun n(k: String, d: Int = 0) = (st?.get(k) as? Number)?.toInt() ?: d
  fun dbl(k: String, d: Double = 0.0) = (st?.get(k) as? Number)?.toDouble() ?: d
  fun dList(k: String) = (st?.get(k) as? List<*>)?.mapNotNull { (it as? Number)?.toDouble() } ?: emptyList()
  val seats = (st?.get("seats") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
  val started = seats.isNotEmpty()
  val mode = n("mode", 8)
  val pxList = dList("px"); val pyList = dList("py")
  val px = DoubleArray(16) { pxList.getOrElse(it) { 0.0 } }
  val py = DoubleArray(16) { pyList.getOrElse(it) { 0.0 } }
  val alive = n("alive")
  val groups = intArrayOf(n("g0", -1), n("g1", -1))
  val turn = n("turn")
  val hand = (st?.get("hand") as? Boolean) ?: false
  val sid = n("sid")
  val done = (st?.get("done") as? Boolean) ?: false
  val winnerTeam = n("winner", -1)
  val msg = (st?.get("msg") as? String) ?: ""
  val fmt = n("fmt")

  var modeSel by remember { mutableStateOf(8) }
  var doneSid by remember { mutableStateOf(-1) }
  var primed by remember { mutableStateOf(false) }
  var shownX by remember { mutableStateOf(DoubleArray(16)) }
  var shownY by remember { mutableStateOf(DoubleArray(16)) }
  var shownAlive by remember { mutableStateOf(0) }
  var restRot by remember { mutableStateOf(identityRot()) }
  var animSim by remember { mutableStateOf<PoolSim?>(null) }
  val playT = remember { Animatable(0f) }
  val strike = remember { Animatable(1f) }
  var aimAngle by remember { mutableStateOf(0.0) }
  var power by remember { mutableStateOf(0.55f) }
  var spin by remember { mutableStateOf(Offset.Zero) }
  var handPos by remember { mutableStateOf<Offset?>(null) }
  var placing by remember { mutableStateOf(false) }
  var popId by remember { mutableStateOf(0) }
  var popText by remember { mutableStateOf<String?>(null) }
  var popColor by remember { mutableStateOf(Color(0xFF1E88E5)) }
  var pending by remember { mutableStateOf(-1) }
  var botsSel by remember { mutableStateOf(if (c.players.size <= 1) 1 else 0) }
  var replayInfo by remember { mutableStateOf<Triple<Int, Int, Int>?>(null) } // (n.º, total, bola)
  var skipReplay by remember { mutableStateOf(false) }
  val enter = remember { Animatable(0f) }
  val animating = started && primed && doneSid != sid
  val numberPaint = remember {
    android.graphics.Paint().apply {
      isAntiAlias = true; textAlign = android.graphics.Paint.Align.CENTER
      color = android.graphics.Color.BLACK; typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
  }
  fun nameAt(i: Int): String = if (seats.getOrNull(i) == "bot") "Bot ${seats.take(i + 1).count { it == "bot" }}" else c.nameOf(seats.getOrElse(i) { "" })
  fun teamName(t: Int): String = seats.indices.filter { it % 2 == t }.joinToString(" y ") { nameAt(it) }
  val myTurn = started && !done && !animating && seats.getOrNull(turn) == c.me
  val turnTeam = turn % 2

  LaunchedEffect(started) { enter.snapTo(0f); enter.animateTo(1f, tween(450, easing = FastOutSlowInEasing)) }
  // Si me toca con bola en mano, primero hay que colocar la blanca.
  LaunchedEffect(sid, myTurn, hand) {
    if (myTurn && (hand || (alive and 1) == 0)) { placing = true; handPos = Offset(0f, -0.635f) } else { placing = false; handPos = null }
    // apuntar hacia la bola objetivo más cercana para empezar con una guía útil
    if (myTurn) {
      val tg = poolTargets(mode, groups, turnTeam, alive).filter { ((alive shr it) and 1) == 1 }
      val bi = tg.minByOrNull { hypot(px[it] - px[0], py[it] - py[0]) }
      if (bi != null && !(hand || (alive and 1) == 0)) aimAngle = atan2(px[bi] - px[0], py[bi] - py[0])
    }
  }

  /** Dispara: si hay bola en mano, (cueX, cueY) es donde se colocó la blanca. */
  fun doShot(seat: Int, cueX: Double, cueY: Double, angle: Double, pw: Double, sx: Double, sy: Double) {
    if (seat !in seats.indices || done || animating) return
    val team = seat % 2
    val bpx = px.copyOf(); val bpy = py.copyOf()
    var balive = alive
    if (hand || (balive and 1) == 0) { bpx[0] = cueX; bpy[0] = cueY; balive = balive or 1 }
    val sim = poolSimulate(bpx, bpy, balive, angle, pw, sx, sy, restRot)
    val rules = poolRules(mode, groups, team, balive, sim)
    var newAlive = sim.finalAlive
    val npx = sim.finalX.copyOf(); val npy = sim.finalY.copyOf()
    if (rules.respot9) { npx[9] = 0.0; npy[9] = 0.635; newAlive = newAlive or (1 shl 9) }
    val pocketedNow = sim.pocketed.filter { it != 0 }
    var nextSeat = seat
    var newHand = false
    var text: String
    if (rules.winner >= 0) {
      text = "🏆 ¡Ganó el equipo de ${teamName(rules.winner)}!" + if (rules.reason.isNotEmpty()) " (${nameAt(seat)} ${rules.reason})" else ""
    } else {
      val got = if (pocketedNow.isEmpty()) "" else " · embocó " + pocketedNow.joinToString(", ") { "la $it" }
      if (rules.foul) {
        nextSeat = (seat + 1) % seats.size; newHand = true
        text = "⚠️ FALTA de ${nameAt(seat)}: ${rules.reason}$got. Bola en mano para ${nameAt(nextSeat)}"
      } else if (rules.cont) text = "${nameAt(seat)}$got y sigue tirando"
      else {
        nextSeat = (seat + 1) % seats.size
        text = "${nameAt(seat)}: sin embocar$got. Turno de ${nameAt(nextSeat)}"
      }
    }
    val data = mutableMapOf<String, Any?>(
      "seats" to seats, "mode" to mode, "fmt" to fmt,
      "px" to npx.toList(), "py" to npy.toList(), "alive" to newAlive,
      "g0" to rules.groups[0], "g1" to rules.groups[1], "turn" to nextSeat, "hand" to newHand,
      "sid" to (sid + 1), "tseat" to seat, "done" to (rules.winner >= 0), "winner" to rules.winner, "msg" to text,
      "bpx" to bpx.toList(), "bpy" to bpy.toList(), "balive" to balive,
      "ang" to angle, "pow" to pw, "sx" to sx, "sy" to sy, "foul" to rules.foul, "reason" to rules.reason
    )
    c.vm.publishGame("pool", data)
  }

  // Cada tiro se anima igual en todos los dispositivos a partir del estado compartido.
  LaunchedEffect(sid, started) {
    if (!started) { primed = false; return@LaunchedEffect }
    val first = !primed
    primed = true
    if (first || sid <= 0 || sid == doneSid || n("tseat", -1) < 0) {
      shownX = px; shownY = py; shownAlive = alive; animSim = null; doneSid = sid
      return@LaunchedEffect
    }
    val bl = dList("bpx"); val bm = dList("bpy")
    val sim = poolSimulate(
      DoubleArray(16) { bl.getOrElse(it) { 0.0 } }, DoubleArray(16) { bm.getOrElse(it) { 0.0 } }, n("balive"),
      dbl("ang"), dbl("pow"), dbl("sx"), dbl("sy"), restRot
    )
    animSim = sim
    playT.snapTo(0f); strike.snapTo(0f)
    strike.animateTo(1f, tween(170, easing = FastOutSlowInEasing))
    // un sonido por cada choque, banda y tronera, sincronizado con el movimiento
    val sorted = sim.events.sortedBy { it[1] }.filter { it[0] == 2f || it[4] > 0.25f }.take(36)
    launch {
      var last = 0f
      for (ev in sorted) {
        delay(((ev[1] - last) * 1000).toLong().coerceAtLeast(0L)); last = ev[1]
        val vol = (0.25f + ev[4] * 0.22f).coerceAtMost(0.9f)
        launch { if (ev[0] == 2f) SynthSfx.play(SynthSfx.pocketDrop, 0.9f) else if (ev[0] == 0f) SynthSfx.play(SynthSfx.click, vol) else SynthSfx.play(SynthSfx.thud, vol * 0.6f) }
      }
    }
    playT.animateTo(sim.duration, tween((sim.duration * 1000).toInt().coerceAtLeast(300), easing = LinearEasing))
    // Repetición corta (en cámara lenta y con la mesa completa) de cada bola embocada cuando cayó más de una.
    val shots = sim.pocketed.filter { it != 0 }.sortedBy { sim.pocketT[it] }
    if (shots.size >= POOL_REPLAY_MIN) {
      skipReplay = false
      delay(450)
      for ((k, b) in shots.take(POOL_REPLAY_MAX).withIndex()) {
        if (skipReplay) break
        val tp = sim.pocketT[b]
        val t0 = max(0f, tp - POOL_REPLAY_PRE)
        val t1 = min(sim.duration, tp + POOL_REPLAY_POST)
        replayInfo = Triple(k + 1, min(shots.size, POOL_REPLAY_MAX), b)
        playT.snapTo(t0)
        coroutineScope {
          val anim = launch { playT.animateTo(t1, tween(((t1 - t0) / POOL_REPLAY_SPEED * 1000).toInt().coerceAtLeast(300), easing = LinearEasing)) }
          launch {
            delay(((tp - t0) / POOL_REPLAY_SPEED * 1000).toLong().coerceAtLeast(0L))
            SynthSfx.play(SynthSfx.pocketDrop, 0.85f)
          }
          while (anim.isActive) { if (skipReplay) anim.cancel(); delay(40) }
        }
        delay(220)
      }
      replayInfo = null
      skipReplay = false
    }
    // al terminar se toma como verdad el estado publicado
    shownX = px; shownY = py; shownAlive = alive; restRot = sim.finalRot; animSim = null
    val foul = (st?.get("foul") as? Boolean) ?: false
    val reason = (st?.get("reason") as? String) ?: ""
    popId += 1
    when {
      done -> { popText = "🏆 ¡Fin de la partida!"; popColor = Color(0xFFF9A825); c.vm.gameSfx(2) }
      foul -> { popText = "⚠️ FALTA: $reason"; popColor = Color(0xFFD32F2F); c.vm.gameSfx(0) }
      sim.pocketed.isNotEmpty() -> { popText = "🎱 ¡Embocada!"; popColor = Color(0xFF2E7D32); c.vm.gameSfx(1) }
      else -> { popText = null }
    }
    doneSid = sid
  }

  // Los bots tiran desde el primer dispositivo; con bola en mano eligen la mejor posición entre varias.
  LaunchedEffect(sid, turn, animating, done, started) {
    if (!started || done || animating || seats.getOrNull(turn) != "bot" || c.me != c.firstUid) return@LaunchedEffect
    delay(1500)
    val rnd = java.util.Random()
    val team = turn % 2
    val targets = poolTargets(mode, groups, team, alive)
    var cx = px[0]; var cy = py[0]
    val needHand = hand || (alive and 1) == 0
    if (needHand) {
      var bestScore = Double.MAX_VALUE; cx = 0.0; cy = -0.635
      repeat(16) {
        val tx = (rnd.nextDouble() * 2 - 1) * (PHW - 0.08)
        val ty = -PHL + 0.08 + rnd.nextDouble() * (PHL * 0.9)
        val free = (1..15).none { ((alive shr it) and 1) == 1 && hypot(px[it] - tx, py[it] - ty) < PD * 1.3 }
        if (free) {
          val plan = poolChoose(px, py, alive or 1, targets, tx, ty)
          if (plan.score < bestScore) { bestScore = plan.score; cx = tx; cy = ty }
        }
      }
    }
    val plan = poolChoose(px, py, alive or 1, targets, cx, cy)
    doShot(turn, cx, cy, plan.angle + rnd.nextGaussian() * 0.0035, (plan.power + rnd.nextGaussian() * 0.05).coerceIn(0.12, 1.0), 0.0, 0.0)
  }

  if (!started) {
    Column(Modifier.graphicsLayer { alpha = enter.value; translationY = (1f - enter.value) * 30f }) {
      GlassCard(modifier = Modifier.fillMaxWidth(), highlight = true) {
        Text("🎱 Pool", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
        Text(
          "Mesa de 9 pies con física real: las bolas ruedan, chocan, rebotan en las bandas y caen en las troneras. " +
            "Bola 8: lisas contra rayadas y la 8 al final. Bola 9: siempre pegás primero a la bola más baja y la 9 gana. " +
            "Apuntá arrastrando sobre la mesa, elegí fuerza y efecto, y disparás.",
          color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp
        )
      }
      Spacer(Modifier.height(10.dp))
      Text("Juego", color = Color.White, fontWeight = FontWeight.Bold)
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(8, 9).forEach { m -> if (modeSel == m) GradientButton("Bola $m", onClick = { modeSel = m }, height = 38.dp) else SoftButton("Bola $m", onClick = { modeSel = m }) }
      }
      Spacer(Modifier.height(8.dp))
      Text("Jugadores", color = Color.White, fontWeight = FontWeight.Bold)
      Text("Humanos en la mesa: " + c.players.take(6).joinToString(", ") { c.nameOf(it) }, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
      val humansN = c.players.take(6).size
      val minBots = if (humansN <= 1) 1 else 0
      val maxBots = (8 - humansN).coerceAtLeast(minBots)
      Text("Bots", color = Color.White, fontWeight = FontWeight.Bold)
      Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (minBots..maxBots).forEach { n -> if (botsSel.coerceIn(minBots, maxBots) == n) GradientButton("$n", onClick = { botsSel = n }, height = 38.dp) else SoftButton("$n", onClick = { botsSel = n }) }
      }
      Text("Se juega por equipos: los asientos pares contra los impares, y los turnos rotan por la mesa.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
      Spacer(Modifier.height(10.dp))
      GradientButton("🎱 Armar la mesa y empezar", modifier = Modifier.fillMaxWidth(), onClick = {
        val humans = c.players.take(6).ifEmpty { listOf(c.me) }
        val minB = if (humans.size <= 1) 1 else 0
        val nBots = botsSel.coerceIn(minB, (8 - humans.size).coerceAtLeast(minB))
        val list: List<String> = humans + List(nBots) { "bot" }
        val fmtSel = nBots
        val rack = poolRack(modeSel, (sid * 31 + 7))
        val data = mutableMapOf<String, Any?>(
          "seats" to list, "mode" to modeSel, "fmt" to fmtSel, "px" to rack.first.toList(), "py" to rack.second.toList(),
          "alive" to poolAliveMask(modeSel), "g0" to -1, "g1" to -1, "turn" to 0, "hand" to false, "sid" to (sid + 1), "tseat" to -1,
          "done" to false, "winner" to -1, "msg" to "¡Rompe ${if (list[0] == "bot") "el bot" else c.nameOf(list[0])}!",
          "bpx" to rack.first.toList(), "bpy" to rack.second.toList(), "balive" to poolAliveMask(modeSel),
          "ang" to 0.0, "pow" to 0.5, "sx" to 0.0, "sy" to 0.0, "foul" to false, "reason" to ""
        )
        c.vm.publishGame("pool", data)
      })
    }
    return
  }

  val hue = 0f
  val sx = if (animating) shownX else px
  val sy = if (animating) shownY else py
  val sRest = if (animating) shownAlive else alive
  // Puntería del jugador con turno, en vivo para todos (antes de que dispare).
  val liveAll by remember { c.vm.liveStates() }.collectAsState(initial = emptyMap())
  val throwerUid = seats.getOrNull(turn) ?: ""
  val remote = liveAll[throwerUid]?.takeIf { throwerUid != c.me && throwerUid != "bot" && (it["g"] as? String) == "pool" && (it["sid"] as? Number)?.toInt() == sid }
  val remoteOn = started && !done && !animating && !myTurn && remote != null
  val rAng = (remote?.get("ang") as? Number)?.toDouble() ?: 0.0
  val rPow = (remote?.get("pow") as? Number)?.toFloat() ?: 0.5f
  val rHx = (remote?.get("hx") as? Number)?.toFloat()
  val rHand = if (rHx != null) Offset(rHx, (remote?.get("hy") as? Number)?.toFloat() ?: 0f) else null
  LaunchedEffect(myTurn, sid) {
    while (myTurn) {
      val hp = handPos
      c.vm.publishLive(mapOf("g" to "pool", "sid" to sid, "ang" to aimAngle, "pow" to power.toDouble(), "hx" to hp?.x?.toDouble(), "hy" to hp?.y?.toDouble()))
      delay(220)
    }
  }
  val canAim = myTurn && !placing
  val canPlace = myTurn && placing
  val needHandNow = hand || (alive and 1) == 0

  Column(Modifier.graphicsLayer { alpha = enter.value; translationY = (1f - enter.value) * 30f }) {
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
      Text("🎱 POOL · BOLA $mode", color = Color(0xFFFFC857), fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 2.sp)
      Text("KOMBO ARENA", color = Color(0x88FFFFFF), fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 3.sp)
    }
    // marcador por equipo
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      for (team in 0..1) {
        val active = !animating && !done && turnTeam == team
        val border by animateColorAsState(if (active) POOL_TEAM_COLORS[team] else Color(0x33FFFFFF), tween(250), label = "poolTeam")
        Column(
          Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
            .background(Brush.horizontalGradient(if (active) listOf(POOL_TEAM_COLORS[team].copy(alpha = 0.32f), Color(0x14FFFFFF)) else listOf(Color(0x12FFFFFF), Color(0x0AFFFFFF))))
            .border(2.dp, border, RoundedCornerShape(14.dp)).padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
          Text(teamName(team), color = POOL_TEAM_COLORS[team], fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 2)
          if (mode == 8) {
            val g = groups[team]
            if (g == -1) Text("Mesa abierta", color = Color(0xCCFFFFFF), fontSize = 11.sp)
            else {
              Text(if (g == 0) "Lisas" else "Rayadas", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
              Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                groupBalls(g).forEach { PoolMiniBall(it, ((alive shr it) and 1) == 1, 17.dp) }
              }
            }
          } else {
            val nextBall = (1..9).firstOrNull { ((alive shr it) and 1) == 1 }
            if (nextBall != null) Row(verticalAlignment = Alignment.CenterVertically) {
              Text("Próxima: ", color = Color(0xCCFFFFFF), fontSize = 11.sp); PoolMiniBall(nextBall, true, 18.dp)
            }
          }
        }
      }
    }
    Spacer(Modifier.height(8.dp))
    BoxWithConstraints(Modifier.fillMaxWidth()) {
      // La mesa se ajusta al alto disponible de la pantalla (no hace falta bajar) y a la derecha van fuerza, efecto y disparo.
      val ratio = ((2 * PHL + 0.24) / (2 * PHW + 0.24)).toFloat()
      val ctrlW = 84.dp
      val maxTableH = gameAvail() * 0.58f
      val tableW = minOf(maxWidth - ctrlW - 8.dp, maxTableH / ratio)
      val tableH = tableW * ratio
      val frame = RoundedCornerShape(20.dp)
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Top) {
      Box(Modifier.width(tableW).height(tableH).shadow(14.dp, frame).clip(frame).border(2.5.dp, Brush.verticalGradient(listOf(Color(0xFFFFC857), Color(0xFFFF4D8D), Color(0xFF9D6BFF))), frame)) {
        val hueNow = 0f
        Canvas(
          Modifier.fillMaxSize().pointerInput(canAim, canPlace, sid) {
            if (!canAim && !canPlace) return@pointerInput
            awaitEachGesture {
              val down = awaitFirstDown()
              val v = poolView(Size(size.width.toFloat(), size.height.toFloat()))
              fun world(p: Offset) = Offset(((p.x - v.cx) / v.sc), -((p.y - v.cy) / v.sc))
              fun aimAt(p: Offset) {
                val w = world(p)
                if (canPlace) {
                  val nx = w.x.coerceIn((-PHW + PR).toFloat(), (PHW - PR).toFloat())
                  val ny = w.y.coerceIn((-PHL + PR).toFloat(), (PHL - PR).toFloat())
                  val free = (1..15).none { ((alive shr it) and 1) == 1 && hypot(px[it] - nx, py[it] - ny) < PD * 1.02 }
                  if (free) handPos = Offset(nx, ny)
                } else {
                  val cue = handPos ?: Offset(px[0].toFloat(), py[0].toFloat())
                  if (hypot((w.x - cue.x).toDouble(), (w.y - cue.y).toDouble()) > 0.03) aimAngle = atan2((w.x - cue.x).toDouble(), (w.y - cue.y).toDouble())
                }
              }
              aimAt(down.position)
              while (true) {
                val ev = awaitPointerEvent()
                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                if (!ch.pressed) break
                aimAt(ch.position)
                ch.consume()
              }
              if (canPlace) placing = false
            }
          }
        ) {
          drawPoolScene(
            poolView(size), animSim, playT.value, strike.value, sx, sy, sRest, restRot, numberPaint,
            canAim || remoteOn, if (remoteOn) rAng else aimAngle, dbl("ang"), if (remoteOn) rPow.toDouble() else power.toDouble(), if (remoteOn) (if (needHandNow) rHand else null) else if (needHandNow && (canAim || canPlace)) handPos else null, canPlace, hueNow,
            replayInfo?.third ?: -1
          )
        }
        val rp = replayInfo
        if (rp != null) {
          Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { skipReplay = true } }) {
            Box(Modifier.fillMaxWidth().height(34.dp).align(Alignment.TopCenter).background(Color(0xCC000000)))
            Box(Modifier.fillMaxWidth().height(34.dp).align(Alignment.BottomCenter).background(Color(0xCC000000)))
            Row(Modifier.align(Alignment.TopCenter).padding(top = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFFF1744)))
              Text("REPETICIÓN ${rp.first}/${rp.second} · bola ${rp.third}", color = Color.White, fontWeight = FontWeight.Black, fontSize = 13.sp)
            }
            Text("Tocá para saltar ▶▶", color = Color(0xCCFFFFFF), fontSize = 11.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 9.dp))
          }
        }
        val pt = popText
        if (pt != null) key(popId) { PoolPop(popId, pt, popColor, { popText = null }, Modifier.align(Alignment.TopCenter).padding(top = 14.dp)) }
        if (done && !animating) GameConfetti(Modifier.fillMaxSize())
        Text(
          if (replayInfo != null) "🎥 Repetición" else if (animating) "🎱 ¡Rueda la bola!" else if (done) "Fin de la partida" else if (canPlace) "Tocá la mesa para colocar la blanca"
          else if (canAim) "Tu turno · arrastrá para apuntar" else if (remoteOn) "👀 ${nameAt(turn)} está apuntando…" else "Turno de ${nameAt(turn)}",
          color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.Center,
          modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp).clip(RoundedCornerShape(50)).background(Color(0x99000000)).padding(horizontal = 12.dp, vertical = 4.dp)
        )
      }
      Spacer(Modifier.width(8.dp))
      // ---- Controles a la derecha: barra de fuerza, efecto sobre la blanca y disparo ----
      Column(
        Modifier.width(ctrlW).height(tableH).graphicsLayer { alpha = if (myTurn && !done) 1f else 0.35f },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Text("Fuerza ${(power * 100).toInt()}%", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        Box(
          Modifier.weight(1f).width(40.dp).clip(RoundedCornerShape(20.dp)).background(Color(0x33FFFFFF))
            .pointerInput(myTurn, done) {
              if (!myTurn || done) return@pointerInput
              awaitEachGesture {
                val down = awaitFirstDown()
                fun setP(y: Float) { power = (1f - y / size.height.toFloat()).coerceIn(0.1f, 1f) }
                setP(down.position.y)
                while (true) {
                  val ev = awaitPointerEvent()
                  val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                  if (!ch.pressed) break
                  setP(ch.position.y); ch.consume()
                }
              }
            },
          contentAlignment = Alignment.BottomCenter
        ) {
          Box(
            Modifier.fillMaxWidth().fillMaxHeight(power.coerceIn(0.05f, 1f)).clip(RoundedCornerShape(20.dp))
              .background(Brush.verticalGradient(listOf(Color(0xFFFF5252), Color(0xFFFFC107), Color(0xFF38BDF8))))
          )
        }
        // selector de efecto: toca cualquier punto de la blanca
        Box(
          Modifier.size(64.dp).clip(CircleShape).background(Brush.radialGradient(listOf(Color.White, Color(0xFFD9D4C4)))).border(2.dp, Color(0x66000000), CircleShape)
            .pointerInput(myTurn, done) {
              if (!myTurn || done) return@pointerInput
              awaitEachGesture {
                val down = awaitFirstDown()
                fun setSpin(p: Offset) {
                  val rr = size.width / 2f
                  var dx = (p.x - rr) / rr; var dy = (p.y - rr) / rr
                  val l = hypot(dx.toDouble(), dy.toDouble()).toFloat()
                  if (l > 0.78f) { dx = dx / l * 0.78f; dy = dy / l * 0.78f }
                  spin = Offset(dx, -dy)
                }
                setSpin(down.position)
                while (true) {
                  val ev = awaitPointerEvent()
                  val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                  if (!ch.pressed) break
                  setSpin(ch.position); ch.consume()
                }
              }
            },
          contentAlignment = Alignment.Center
        ) {
          Canvas(Modifier.fillMaxSize()) {
            drawLine(Color(0x33000000), Offset(size.width / 2f, 6f), Offset(size.width / 2f, size.height - 6f), 1.5f)
            drawLine(Color(0x33000000), Offset(6f, size.height / 2f), Offset(size.width - 6f, size.height / 2f), 1.5f)
            drawCircle(Color(0xFFD32F2F), 5.dp.toPx(), Offset(size.width / 2f + spin.x * size.width / 2f, size.height / 2f - spin.y * size.height / 2f))
          }
        }
        if (needHandNow) SoftButton(if (placing) "…" else "↔ Blanca", onClick = { placing = true }, modifier = Modifier.fillMaxWidth(), enabled = myTurn && !done)
        GradientButton(
          "🎱 Tirar", modifier = Modifier.fillMaxWidth(), enabled = canAim && pending != sid && !done, height = 50.dp,
          onClick = {
            pending = sid
            val cue = handPos ?: Offset(px[0].toFloat(), py[0].toFloat())
            doShot(turn, cue.x.toDouble(), cue.y.toDouble(), aimAngle, power.toDouble(), spin.x.toDouble() * 0.8, spin.y.toDouble() * 0.8)
          }
        )
      }
      }
    }
    Spacer(Modifier.height(8.dp))
    Text(if (animating) "…" else msg, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    if (done && !animating) {
      Spacer(Modifier.height(8.dp))
      GradientButton("🔁 Revancha", modifier = Modifier.fillMaxWidth(), onClick = {
        val rack = poolRack(mode, sid * 31 + 11)
        val data = mutableMapOf<String, Any?>(
          "seats" to seats, "mode" to mode, "fmt" to fmt, "px" to rack.first.toList(), "py" to rack.second.toList(),
          "alive" to poolAliveMask(mode), "g0" to -1, "g1" to -1, "turn" to 0, "hand" to false, "sid" to (sid + 1), "tseat" to -1,
          "done" to false, "winner" to -1, "msg" to "¡Revancha!", "bpx" to rack.first.toList(), "bpy" to rack.second.toList(),
          "balive" to poolAliveMask(mode), "ang" to 0.0, "pow" to 0.5, "sx" to 0.0, "sy" to 0.0, "foul" to false, "reason" to ""
        )
        c.vm.publishGame("pool", data)
      })
    }
  }
}
