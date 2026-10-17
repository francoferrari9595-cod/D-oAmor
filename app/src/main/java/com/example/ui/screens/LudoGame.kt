package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.lerp
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.ui.components.GlassCard
import com.example.ui.components.GradientButton
import com.example.ui.components.SoftButton
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

// ---------------- Datos del tablero ----------------
private val LOOP: List<Pair<Int, Int>> = listOf(
  1 to 6, 2 to 6, 3 to 6, 4 to 6, 5 to 6,
  6 to 5, 6 to 4, 6 to 3, 6 to 2, 6 to 1, 6 to 0,
  7 to 0, 8 to 0,
  8 to 1, 8 to 2, 8 to 3, 8 to 4, 8 to 5,
  9 to 6, 10 to 6, 11 to 6, 12 to 6, 13 to 6, 14 to 6,
  14 to 7, 14 to 8,
  13 to 8, 12 to 8, 11 to 8, 10 to 8, 9 to 8,
  8 to 9, 8 to 10, 8 to 11, 8 to 12, 8 to 13, 8 to 14,
  7 to 14, 6 to 14,
  6 to 13, 6 to 12, 6 to 11, 6 to 10, 6 to 9,
  5 to 8, 4 to 8, 3 to 8, 2 to 8, 1 to 8, 0 to 8,
  0 to 7, 0 to 6
)
private val START = intArrayOf(0, 13, 26, 39)
private val SAFE_STAR = setOf(8, 21, 34, 47)
private val SAFE_ALL = setOf(0, 13, 26, 39, 8, 21, 34, 47)
private val HOME_COL: List<List<Pair<Int, Int>>> = listOf(
  listOf(1 to 7, 2 to 7, 3 to 7, 4 to 7, 5 to 7),
  listOf(7 to 1, 7 to 2, 7 to 3, 7 to 4, 7 to 5),
  listOf(13 to 7, 12 to 7, 11 to 7, 10 to 7, 9 to 7),
  listOf(7 to 13, 7 to 12, 7 to 11, 7 to 10, 7 to 9)
)
private val SEAT_COLORS = listOf(Color(0xFFFF4D4D), Color(0xFF3DDC84), Color(0xFFFFC83D), Color(0xFF3DA9FF))
private val SEAT_NAMES = listOf("Rojo", "Verde", "Amarillo", "Azul")

// ---------------- Estilos de tablero y fichas 3D ----------------
private class LudoStyle(val name: String, val bg: Color, val cell: Color, val inner: Color, val slab: Color)

private val LUDO_STYLES = listOf(
  LudoStyle("Clásico", Color(0xFF1B1030), Color(0xFF33235A), Color(0xFF120A22), Color(0xFF0A0614)),
  LudoStyle("Madera", Color(0xFF6B4423), Color(0xFFD9B27C), Color(0xFF4A2E15), Color(0xFF2E1B0B)),
  LudoStyle("Neón", Color(0xFF04050D), Color(0xFF0F1B3A), Color(0xFF060A1A), Color(0xFF000000)),
  LudoStyle("Cristal", Color(0xFF0E3A4F), Color(0xFF2B7F9E), Color(0xFF0A2533), Color(0xFF05161F)),
  LudoStyle("Espacio", Color(0xFF080019), Color(0xFF241553), Color(0xFF0C0230), Color(0xFF03000C)),
  LudoStyle("Caramelo", Color(0xFFFFB3D9), Color(0xFFFFF0F7), Color(0xFFFF8CC6), Color(0xFFD46AA3))
)
private val SHAPE_NAMES = listOf("Peón", "Cono", "Corazón", "Estrella", "Corona", "Diamante", "Esfera")

private fun shade(c: Color, f: Float): Color = if (f >= 0f) lerp(c, Color.White, f) else lerp(c, Color.Black, -f)

private fun DrawScope.drawPiece(shape: Int, color: Color) {
  val w = size.width
  val h = size.height
  // sombra proyectada
  drawOval(Color(0x66000000), Offset(w * 0.12f, h * 0.80f), Size(w * 0.76f, h * 0.16f))
  drawOval(shade(color, -0.6f), Offset(w * 0.12f, h * 0.73f), Size(w * 0.76f, h * 0.15f))
  drawOval(shade(color, 0.2f), Offset(w * 0.12f, h * 0.71f), Size(w * 0.76f, h * 0.13f))
  val body = Brush.horizontalGradient(listOf(shade(color, 0.45f), color, shade(color, -0.45f)), startX = w * 0.1f, endX = w * 0.9f)
  val path = Path()
  when (shape) {
    1 -> { // cono
      path.moveTo(w * 0.5f, h * 0.08f); path.lineTo(w * 0.86f, h * 0.86f); path.lineTo(w * 0.14f, h * 0.86f); path.close()
      drawPath(path, body)
    }
    2 -> { // corazón
      path.moveTo(w * 0.5f, h * 0.86f)
      path.cubicTo(w * 0.0f, h * 0.52f, w * 0.08f, h * 0.12f, w * 0.32f, h * 0.16f)
      path.cubicTo(w * 0.44f, h * 0.18f, w * 0.5f, h * 0.28f, w * 0.5f, h * 0.30f)
      path.cubicTo(w * 0.5f, h * 0.28f, w * 0.56f, h * 0.18f, w * 0.68f, h * 0.16f)
      path.cubicTo(w * 0.92f, h * 0.12f, w * 1.0f, h * 0.52f, w * 0.5f, h * 0.86f)
      path.close()
      drawPath(path, Brush.radialGradient(listOf(shade(color, 0.5f), color, shade(color, -0.5f)), center = Offset(w * 0.38f, h * 0.30f), radius = w * 0.8f))
    }
    3 -> { // estrella
      for (i in 0 until 10) {
        val a = Math.toRadians(-90.0 + i * 36.0)
        val r = if (i % 2 == 0) 0.46f else 0.2f
        val x = w * 0.5f + (kotlin.math.cos(a) * r * w).toFloat()
        val y = h * 0.48f + (kotlin.math.sin(a) * r * h * 0.9f).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
      }
      path.close()
      drawPath(path, Brush.radialGradient(listOf(shade(color, 0.55f), color, shade(color, -0.45f)), center = Offset(w * 0.4f, h * 0.35f), radius = w * 0.7f))
    }
    4 -> { // corona
      path.moveTo(w * 0.14f, h * 0.84f); path.lineTo(w * 0.08f, h * 0.28f); path.lineTo(w * 0.32f, h * 0.52f)
      path.lineTo(w * 0.5f, h * 0.18f); path.lineTo(w * 0.68f, h * 0.52f); path.lineTo(w * 0.92f, h * 0.28f)
      path.lineTo(w * 0.86f, h * 0.84f); path.close()
      drawPath(path, body)
      drawCircle(Color(0xFFFFE066), w * 0.06f, Offset(w * 0.5f, h * 0.2f))
    }
    5 -> { // diamante
      path.moveTo(w * 0.5f, h * 0.08f); path.lineTo(w * 0.9f, h * 0.38f); path.lineTo(w * 0.5f, h * 0.88f); path.lineTo(w * 0.1f, h * 0.38f); path.close()
      drawPath(path, body)
      val facet = Path()
      facet.moveTo(w * 0.1f, h * 0.38f); facet.lineTo(w * 0.9f, h * 0.38f); facet.lineTo(w * 0.5f, h * 0.88f); facet.close()
      drawPath(facet, Color(0x22000000))
    }
    6 -> { // esfera
      drawCircle(Brush.radialGradient(listOf(shade(color, 0.6f), color, shade(color, -0.55f)), center = Offset(w * 0.38f, h * 0.34f), radius = w * 0.62f), w * 0.4f, Offset(w * 0.5f, h * 0.5f))
    }
    else -> { // peón clásico
      drawOval(shade(color, -0.35f), Offset(w * 0.16f, h * 0.70f), Size(w * 0.68f, h * 0.18f))
      path.moveTo(w * 0.26f, h * 0.78f); path.lineTo(w * 0.40f, h * 0.46f); path.lineTo(w * 0.60f, h * 0.46f); path.lineTo(w * 0.74f, h * 0.78f); path.close()
      drawPath(path, body)
      drawCircle(Brush.radialGradient(listOf(shade(color, 0.6f), color, shade(color, -0.5f)), center = Offset(w * 0.42f, h * 0.2f), radius = w * 0.3f), w * 0.22f, Offset(w * 0.5f, h * 0.3f))
    }
  }
  // brillo especular y reflejo lateral
  drawOval(Color(0x88FFFFFF), Offset(w * 0.30f, h * 0.18f), Size(w * 0.16f, h * 0.12f))
  drawOval(Color(0x33FFFFFF), Offset(w * 0.66f, h * 0.30f), Size(w * 0.07f, h * 0.34f))
}

// ---------------- Lectura segura del estado ----------------
private fun Map<String, Any>?.gi(k: String, d: Int = 0): Int = (this?.get(k) as? Number)?.toInt() ?: d
private fun Map<String, Any>?.gl(k: String, d: Long = 0L): Long = (this?.get(k) as? Number)?.toLong() ?: d
private fun Map<String, Any>?.gs(k: String, d: String = ""): String = (this?.get(k) as? String) ?: d
private fun Map<String, Any>?.gstrs(k: String, size: Int): List<String> {
  val l = (this?.get(k) as? List<*>)?.map { (it as? String) ?: "" } ?: emptyList()
  return if (l.size >= size) l.take(size) else l + List(size - l.size) { "" }
}
private fun Map<String, Any>?.gints(k: String, size: Int, d: Int): List<Int> {
  val l = (this?.get(k) as? List<*>)?.map { (it as? Number)?.toInt() ?: d } ?: emptyList()
  return if (l.size >= size) l.take(size) else l + List(size - l.size) { d }
}

// ---------------- Reglas ----------------
private fun globalCell(seat: Int, p: Int): Int = if (p in 0..50) (START[seat] + p) % 52 else -1

private fun yardSlot(seat: Int, t: Int): Pair<Float, Float> {
  val bx = if (seat == 1 || seat == 2) 9f else 0f
  val by = if (seat == 2 || seat == 3) 9f else 0f
  val sx = if (t % 2 == 0) 2f else 4f
  val sy = if (t / 2 == 0) 2f else 4f
  return Pair(bx + sx, by + sy)
}

private fun tokenPoint(seat: Int, t: Int, p: Int): Pair<Float, Float> {
  if (p < 0) return yardSlot(seat, t)
  if (p <= 50) {
    val c = LOOP[(START[seat] + p) % 52]
    return Pair(c.first + 0.5f, c.second + 0.5f)
  }
  if (p <= 55) {
    val c = HOME_COL[seat][p - 51]
    return Pair(c.first + 0.5f, c.second + 0.5f)
  }
  val spread = (t - 1.5f) * 0.14f
  return when (seat) {
    0 -> Pair(6.8f, 7.5f + spread)
    1 -> Pair(7.5f + spread, 6.8f)
    2 -> Pair(8.2f, 7.5f + spread)
    else -> Pair(7.5f + spread, 8.2f)
  }
}

// Modos: 0 Clásico, 1 Rápido (2 fichas ya en juego, gana quien lleve UNA a la meta),
// 2 Salida libre (cualquier número saca ficha), 3 Pacífico (sin capturas).
private val MODE_NAMES = listOf("Clásico", "Rápido", "Salida libre", "Pacífico")
private val MODE_DESC = listOf(
  "4 fichas, necesitas un 6 para salir. Gana quien lleve las 4 a la meta.",
  "2 fichas ya en el tablero. Gana quien lleve UNA a la meta primero.",
  "4 fichas y cualquier número las saca de la base. Partidas más ágiles.",
  "Sin capturas: nadie se come a nadie. Ideal para jugar relajados."
)

private fun ludoMovable(pos: List<Int>, seat: Int, dice: Int, mode: Int): List<Int> {
  val out = ArrayList<Int>()
  for (t in 0..3) {
    val p = pos[seat * 4 + t]
    val ok = when {
      p == -2 -> false
      p == -1 -> dice == 6 || mode == 2
      p >= 56 -> false
      else -> p + dice <= 56
    }
    if (ok) out.add(t)
  }
  return out
}

private class LudoMove(val pos: List<Int>, val captured: Boolean, val home: Boolean)

private fun ludoApply(pos: List<Int>, seat: Int, t: Int, dice: Int, mode: Int): LudoMove {
  val np = pos.toMutableList()
  val idx = seat * 4 + t
  val old = np[idx]
  val newP = if (old == -1) 0 else old + dice
  np[idx] = newP
  var captured = false
  val g = globalCell(seat, newP)
  if (mode != 3 && g >= 0 && !SAFE_ALL.contains(g)) {
    for (s in 0..3) {
      if (s == seat) continue
      for (k in 0..3) {
        val q = np[s * 4 + k]
        if (q in 0..50 && (START[s] + q) % 52 == g) {
          np[s * 4 + k] = -1
          captured = true
        }
      }
    }
  }
  return LudoMove(np, captured, newP == 56)
}

private fun ludoWinner(pos: List<Int>, owner: List<String>, mode: Int): Int {
  for (s in 0..3) {
    if (owner[s].isEmpty()) continue
    val active = (0..3).filter { pos[s * 4 + it] != -2 }
    val done = active.count { pos[s * 4 + it] == 56 }
    if (active.isNotEmpty() && done >= (if (mode == 1) 1 else active.size)) return s
  }
  return -1
}

private fun ludoNextSeat(owner: List<String>, cur: Int): Int {
  for (k in 1..4) {
    val s = (cur + k) % 4
    if (owner[s].isNotEmpty()) return s
  }
  return cur
}

private fun botPick(pos: List<Int>, seat: Int, dice: Int, moves: List<Int>): Int {
  var best = moves[0]
  var bestScore = Int.MIN_VALUE
  for (t in moves) {
    val p = pos[seat * 4 + t]
    val np = if (p == -1) 0 else p + dice
    var score = np
    if (np == 56) score += 100
    if (p == -1) score += 40
    val g = globalCell(seat, np)
    if (g >= 0) {
      if (SAFE_ALL.contains(g)) score += 15
      else {
        for (s in 0..3) {
          if (s == seat) continue
          for (k in 0..3) {
            val q = pos[s * 4 + k]
            if (q in 0..50 && (START[s] + q) % 52 == g) score += 70
          }
        }
      }
    }
    if (score > bestScore) { bestScore = score; best = t }
  }
  return best
}

private fun ludoState(owner: List<String>, pos: List<Int>, turn: Int, dice: Int, rollId: Long, winner: Int, msg: String, mode: Int = 0): Map<String, Any?> =
  mapOf("owner" to owner, "pos" to pos, "turn" to turn, "dice" to dice, "rollId" to rollId, "winner" to winner, "msg" to msg, "mode" to mode)

// ---------------- Dibujo ----------------
private fun DrawScope.drawLudoBoard(cell: Float, owner: List<String>, style: LudoStyle) {
  drawRect(style.bg)
  val corner = CornerRadius(cell * 0.45f, cell * 0.45f)
  val yardBase = listOf(0 to 0, 9 to 0, 9 to 9, 0 to 9)
  for (s in 0..3) {
    val active = owner[s].isNotEmpty()
    val col = SEAT_COLORS[s].copy(alpha = if (active) 0.9f else 0.25f)
    val bx = yardBase[s].first
    val by = yardBase[s].second
    drawRoundRect(col, Offset(bx * cell, by * cell), Size(6 * cell, 6 * cell), corner)
    drawRoundRect(style.inner, Offset((bx + 1) * cell, (by + 1) * cell), Size(4 * cell, 4 * cell), corner)
    for (t in 0..3) {
      val sl = yardSlot(s, t)
      drawCircle(col.copy(alpha = 0.35f), cell * 0.42f, Offset(sl.first * cell, sl.second * cell))
    }
  }
  val rad = CornerRadius(cell * 0.18f, cell * 0.18f)
  for (i in LOOP.indices) {
    val cx = LOOP[i].first
    val cy = LOOP[i].second
    var color = style.cell
    for (s in 0..3) if (START[s] == i) color = SEAT_COLORS[s].copy(alpha = 0.85f)
    drawRoundRect(color, Offset(cx * cell + 1.5f, cy * cell + 1.5f), Size(cell - 3f, cell - 3f), rad)
    if (SAFE_STAR.contains(i)) drawCircle(Color(0x99FFFFFF), cell * 0.16f, Offset((cx + 0.5f) * cell, (cy + 0.5f) * cell))
  }
  for (s in 0..3) {
    for (hc in HOME_COL[s]) {
      drawRoundRect(SEAT_COLORS[s].copy(alpha = 0.75f), Offset(hc.first * cell + 1.5f, hc.second * cell + 1.5f), Size(cell - 3f, cell - 3f), rad)
    }
  }
  fun tri(ax: Float, ay: Float, bx: Float, by: Float, color: Color) {
    val path = Path()
    path.moveTo(ax * cell, ay * cell)
    path.lineTo(bx * cell, by * cell)
    path.lineTo(7.5f * cell, 7.5f * cell)
    path.close()
    drawPath(path, color)
  }
  tri(6f, 6f, 6f, 9f, SEAT_COLORS[0])
  tri(6f, 6f, 9f, 6f, SEAT_COLORS[1])
  tri(9f, 6f, 9f, 9f, SEAT_COLORS[2])
  tri(6f, 9f, 9f, 9f, SEAT_COLORS[3])
}

@Composable
private fun LudoToken(seat: Int, t: Int, p: Int, group: Int, cellPx: Float, movable: Boolean, pulse: Float, shape: Int, onTap: () -> Unit) {
  val target = tokenPoint(seat, t, p)
  val gx = target.first + group * 0.16f
  val gy = target.second - group * 0.12f
  val ax by animateFloatAsState(gx, tween(450, easing = FastOutSlowInEasing), label = "tokenX")
  val ay by animateFloatAsState(gy, tween(450, easing = FastOutSlowInEasing), label = "tokenY")
  val wCells = 0.82f
  val hCells = 1.1f
  val wDp = with(LocalDensity.current) { (cellPx * wCells).toDp() }
  val hDp = with(LocalDensity.current) { (cellPx * hCells).toDp() }
  Box(
    modifier = Modifier
      .offset { IntOffset(((ax - wCells / 2f) * cellPx).roundToInt(), ((ay - 0.78f) * cellPx).roundToInt()) }
      .size(wDp, hDp)
      .zIndex(if (movable) 3f else 1f + ay / 20f)
      .graphicsLayer {
        val sc = if (movable) pulse else 1f
        scaleX = sc
        scaleY = sc
      }
      .clickable(enabled = movable) { onTap() }
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      if (movable) drawCircle(Color(0x55FFFFFF), size.width * 0.62f, Offset(size.width * 0.5f, size.height * 0.82f))
      drawPiece(shape, SEAT_COLORS[seat])
    }
  }
}

@Composable
private fun LudoBoard(owner: List<String>, pos: List<Int>, tappable: Set<Int>, style: LudoStyle, shapes: List<Int>, onTap: (Int) -> Unit) {
  val pulse = rememberPalettePulse()
  BoxWithConstraints(modifier = Modifier.fillMaxWidth(0.94f).aspectRatio(1f).padding(bottom = 6.dp)) {
    val cellPx = with(LocalDensity.current) { maxWidth.toPx() } / 15f
    Box(
      modifier = Modifier.fillMaxSize().graphicsLayer {
        rotationX = 38f
        cameraDistance = 16f * density
        transformOrigin = TransformOrigin(0.5f, 0.85f)
      }
    ) {
      // grosor del tablero (losa) y sombra
      Box(modifier = Modifier.fillMaxSize().offset(y = 12.dp).clip(RoundedCornerShape(24.dp)).background(style.slab))
      Canvas(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp))) { drawLudoBoard(size.width / 15f, owner, style) }
      val seen = HashMap<Pair<Int, Int>, Int>()
      for (seat in 0..3) {
        if (owner[seat].isEmpty()) continue
        for (t in 0..3) {
          val idx = seat * 4 + t
          val p = pos[idx]
          if (p == -2) continue
          val pt = tokenPoint(seat, t, p)
          val keyPos = Pair((pt.first * 10f).roundToInt(), (pt.second * 10f).roundToInt())
          val group = seen[keyPos] ?: 0
          seen[keyPos] = group + 1
          key(idx) {
            LudoToken(seat, t, p, group, cellPx, tappable.contains(idx), pulse, shapes.getOrElse(seat) { 0 }) { onTap(idx) }
          }
        }
      }
    }
  }
}

private val DICE_FACES = listOf(
  floatArrayOf(1f, 0f, 0f, 1f, 1f, 0f, 0f, 0f, 1f, 0f),
  floatArrayOf(6f, 0f, 0f, -1f, -1f, 0f, 0f, 0f, 1f, 0f),
  floatArrayOf(3f, 1f, 0f, 0f, 0f, 0f, -1f, 0f, 1f, 0f),
  floatArrayOf(4f, -1f, 0f, 0f, 0f, 0f, 1f, 0f, 1f, 0f),
  floatArrayOf(2f, 0f, 1f, 0f, 1f, 0f, 0f, 0f, 0f, -1f),
  floatArrayOf(5f, 0f, -1f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
)

@Composable
private fun Dice3D(value: Int, rollId: Long, sizeDp: androidx.compose.ui.unit.Dp) {
  val prog = remember { Animatable(1f) }
  LaunchedEffect(rollId) {
    if (rollId > 0L) {
      prog.snapTo(0f)
      prog.animateTo(1f, tween(1400, easing = FastOutSlowInEasing))
    }
  }
  val v = if (value in 1..6) value else 1
  val baseRx = when (v) { 2 -> 90f; 5 -> -90f; else -> 0f }
  val baseRy = when (v) { 6 -> 180f; 3 -> -90f; 4 -> 90f; else -> 0f }
  val p = prog.value
  Box(modifier = Modifier.size(sizeDp * 1.5f), contentAlignment = Alignment.Center) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val rad = Math.PI.toFloat() / 180f
      val spin = 1f - p
      val ry = (baseRy + spin * 540f) * rad
      val rx = (baseRx + spin * 720f) * rad
      val rz = (spin * 360f) * rad
      val bounce = kotlin.math.abs(kotlin.math.sin(p * Math.PI.toFloat() * 3f)) * spin * size.height * 0.28f
      val sc = size.minDimension * 0.24f
      val cxp = size.width / 2f
      val cyp = size.height * 0.56f - bounce
      // sombra sobre la mesa
      drawOval(Color(0x55000000), Offset(cxp - sc * 1.2f * (1f - bounce / size.height), size.height * 0.84f), Size(sc * 2.4f * (1f - bounce / size.height), sc * 0.55f))
      fun tf(x: Float, y: Float, z: Float): FloatArray {
        var px = x; var py = y; var pz = z
        // Y
        var nx = px * kotlin.math.cos(ry) + pz * kotlin.math.sin(ry); var nz = -px * kotlin.math.sin(ry) + pz * kotlin.math.cos(ry); px = nx; pz = nz
        // X
        var ny = py * kotlin.math.cos(rx) - pz * kotlin.math.sin(rx); nz = py * kotlin.math.sin(rx) + pz * kotlin.math.cos(rx); py = ny; pz = nz
        // Z
        nx = px * kotlin.math.cos(rz) - py * kotlin.math.sin(rz); ny = px * kotlin.math.sin(rz) + py * kotlin.math.cos(rz); px = nx; py = ny
        // inclinación de cámara
        val vy = 0.45f; val vx = -0.42f
        nx = px * kotlin.math.cos(vy) + pz * kotlin.math.sin(vy); nz = -px * kotlin.math.sin(vy) + pz * kotlin.math.cos(vy); px = nx; pz = nz
        ny = py * kotlin.math.cos(vx) - pz * kotlin.math.sin(vx); nz = py * kotlin.math.sin(vx) + pz * kotlin.math.cos(vx); py = ny; pz = nz
        return floatArrayOf(px, py, pz)
      }
      fun proj(a: FloatArray): Offset {
        val k = 1f / (1f - a[2] * 0.10f)
        return Offset(cxp + a[0] * sc * k, cyp - a[1] * sc * k)
      }
      val visible = ArrayList<Pair<Float, FloatArray>>()
      for (f in DICE_FACES) {
        val n = tf(f[1], f[2], f[3])
        if (n[2] > 0f) visible.add(Pair(n[2], f))
      }
      visible.sortBy { it.first }
      for ((nz, f) in visible) {
        val nr = tf(f[1], f[2], f[3])
        val light = (nr[0] * -0.35f + nr[1] * 0.55f + nr[2] * 0.75f).coerceIn(0f, 1f)
        val br = 0.62f + 0.38f * light
        val face = Color(0.98f * br, 0.97f * br, 0.93f * br)
        val corners = listOf(Pair(-1f, -1f), Pair(1f, -1f), Pair(1f, 1f), Pair(-1f, 1f)).map { (a, b) ->
          proj(tf(f[1] + f[4] * a + f[7] * b, f[2] + f[5] * a + f[8] * b, f[3] + f[6] * a + f[9] * b))
        }
        val path = Path()
        path.moveTo(corners[0].x, corners[0].y)
        for (i in 1..3) path.lineTo(corners[i].x, corners[i].y)
        path.close()
        drawPath(path, face)
        drawPath(path, Color(0x33000000), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f))
        val dots = when (f[0].toInt()) {
          1 -> listOf(4); 2 -> listOf(0, 8); 3 -> listOf(0, 4, 8); 4 -> listOf(0, 2, 6, 8)
          5 -> listOf(0, 2, 4, 6, 8); else -> listOf(0, 2, 3, 5, 6, 8)
        }
        for (d in dots) {
          val uu = ((d % 3) - 1) * 0.52f
          val vv = (1 - (d / 3)) * 0.52f
          val c3 = tf(f[1] + f[4] * uu + f[7] * vv, f[2] + f[5] * uu + f[8] * vv, f[3] + f[6] * uu + f[9] * vv)
          val cp = proj(c3)
          val r = sc * 0.16f * (0.55f + 0.45f * nz)
          val pc = if (f[0].toInt() == 1) Color(0xFFE53935) else Color(0xFF1B1030)
          drawCircle(pc, r, cp)
          drawCircle(Color(0x55FFFFFF), r * 0.35f, Offset(cp.x - r * 0.3f, cp.y - r * 0.3f))
        }
      }
    }
  }
}

// ---------------- Pantalla del juego ----------------
@Composable
internal fun LudoGame(c: GameCtx) {
  val flow = remember { c.vm.gameState("ludo") }
  val st by flow.collectAsState(initial = null)
  val started = (st?.get("pos") as? List<*>)?.size == 16
  val owner = st.gstrs("owner", 4)
  val pos = st.gints("pos", 16, -1)
  val turn = st.gi("turn").coerceIn(0, 3)
  val dice = st.gi("dice")
  val rollId = st.gl("rollId")
  val winner = st.gi("winner", -1)
  val msg = st.gs("msg")
  val mode = st.gi("mode")
  var modeSel by remember { mutableStateOf(0) }
  val ver = st.gl("t")
  val isHost = c.me == c.firstUid

  var rolling by remember { mutableStateOf(false) }
  var styleIdx by remember { mutableStateOf(0) }
  val shapeFlow = remember { c.vm.gameState("ludoShapes") }
  val shapeDoc by shapeFlow.collectAsState(initial = null)
  @Suppress("UNCHECKED_CAST")
  val shapeMap = (shapeDoc?.get("s") as? Map<String, Any?>) ?: emptyMap()
  val seatShapes = (0..3).map { sIdx -> if (owner[sIdx] == "bot" || owner[sIdx].isEmpty()) sIdx % 7 else ((shapeMap[owner[sIdx]] as? Number)?.toInt() ?: 0) }
  var seenRoll by remember { mutableStateOf(rollId) }
  var face by remember { mutableStateOf(1) }

  LaunchedEffect(rollId) {
    if (rollId != seenRoll) {
      rolling = true
      for (i in 0 until 18) {
        face = (1..6).random()
        delay(75)
      }
      rolling = false
      seenRoll = rollId
    }
  }

  fun seatName(s: Int): String = when {
    owner[s] == "bot" -> "Bot ${SEAT_NAMES[s]}"
    owner[s].isNotEmpty() -> "${c.nameOf(owner[s])} (${SEAT_NAMES[s]})"
    else -> ""
  }

  fun publish(m: Map<String, Any?>) = c.vm.publishGame("ludo", m)

  fun doRoll() {
    val d = (1..6).random()
    publish(ludoState(owner, pos, turn, d, rollId + 1, -1, "${seatName(turn)} sacó $d", mode))
    c.vm.gameSfx(0)
  }

  fun doPass() {
    publish(ludoState(owner, pos, ludoNextSeat(owner, turn), 0, rollId, -1, "${seatName(turn)} no puede mover", mode))
  }

  fun doMove(t: Int) {
    val res = ludoApply(pos, turn, t, dice, mode)
    val won = ludoWinner(res.pos, owner, mode) >= 0
    val extra = dice == 6 || res.captured || res.home
    val next = if (won || extra) turn else ludoNextSeat(owner, turn)
    val text = when {
      won -> "🏆 ¡${seatName(turn)} ganó la partida!"
      res.captured -> "${seatName(turn)} capturó una ficha 💥"
      res.home -> "${seatName(turn)} llevó una ficha a la meta 🏁"
      else -> "${seatName(turn)} movió una ficha"
    }
    publish(ludoState(owner, res.pos, next, 0, rollId, if (won) turn else -1, text, mode))
    c.vm.gameSfx(if (res.captured || res.home || won) 2 else 1)
  }

  fun startNew(players: Int) {
    val ow = mutableListOf("", "", "", "")
    ow[0] = c.uidAt(0)
    ow[2] = c.uidAt(1)
    if (players >= 3) ow[1] = "bot"
    if (players >= 4) ow[3] = "bot"
    val nt = if (modeSel == 1) 2 else 4
    val startPos = List(16) { i -> if (i % 4 >= nt) -2 else if (modeSel == 1) 0 else -1 }
    publish(ludoState(ow, startPos, 0, 0, rollId + 1, -1, "¡Empieza ${if (ow[0] == c.me) "Tú" else c.partnerName}! ${if (modeSel == 2 || modeSel == 1) "¡A jugar!" else "Saca un 6 para sacar una ficha"}", modeSel))
  }

  // Motor: bots (los mueve el teléfono anfitrión), pase automático y jugada única.
  LaunchedEffect(rollId, dice, turn, winner, started, ver) {
    if (!started || winner >= 0) return@LaunchedEffect
    val seatOwner = owner[turn]
    val driver = if (seatOwner == "bot") isHost else seatOwner == c.me
    if (!driver) return@LaunchedEffect
    if (dice == 0) {
      if (seatOwner == "bot") {
        delay(1100)
        doRoll()
      }
      return@LaunchedEffect
    }
    delay(1700)
    val moves = ludoMovable(pos, turn, dice, mode)
    if (moves.isEmpty()) {
      delay(300)
      doPass()
    } else if (seatOwner == "bot") {
      doMove(botPick(pos, turn, dice, moves))
    } else if (moves.size == 1) {
      doMove(moves[0])
    }
  }

  val myTurn = started && winner < 0 && owner[turn] == c.me
  val movesNow = if (started && winner < 0 && dice > 0) ludoMovable(pos, turn, dice, mode) else emptyList()
  val tappable: Set<Int> = if (myTurn && !rolling) movesNow.map { turn * 4 + it }.toSet() else emptySet()
  val shownDice = if (rolling) face else dice

  if (!started || winner >= 0) {
    if (started && winner >= 0) {
      ResultBanner("🏆 ¡Ganó ${seatName(winner)}!")
      Spacer(Modifier.height(12.dp))
    }
    GlassCard(modifier = Modifier.fillMaxWidth(), highlight = true) {
      Text("Nueva partida de Ludo", fontWeight = FontWeight.ExtraBold, color = Color.White, fontSize = 18.sp)
      Text(
        "Saca un 6 para sacar una ficha, captura a los rivales y llega primero a la meta. Elige cuántos jugadores:",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      Spacer(Modifier.height(8.dp))
      Text("Modo de juego", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
      Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        MODE_NAMES.forEachIndexed { i, nm ->
          if (modeSel == i) GradientButton(nm, onClick = { modeSel = i }, height = 38.dp) else SoftButton(nm, onClick = { modeSel = i })
        }
      }
      Text(MODE_DESC[modeSel], color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
      Spacer(Modifier.height(10.dp))
      GradientButton("👫 Solo los dos", onClick = { startNew(2) }, modifier = Modifier.fillMaxWidth())
      Spacer(Modifier.height(8.dp))
      GradientButton("🤖 Los dos + 1 bot (3 jugadores)", onClick = { startNew(3) }, modifier = Modifier.fillMaxWidth())
      Spacer(Modifier.height(8.dp))
      GradientButton("🤖🤖 Los dos + 2 bots (4 jugadores)", onClick = { startNew(4) }, modifier = Modifier.fillMaxWidth())
    }
    Spacer(Modifier.height(12.dp))
  }

  if (started) {
    // Jugadores
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      for (s in 0..3) {
        if (owner[s].isEmpty()) continue
        val done = (0..3).count { pos[s * 4 + it] == 56 }
        val totalT = (0..3).count { pos[s * 4 + it] != -2 }
        val active = s == turn && winner < 0
        GlassCard(
          modifier = Modifier.weight(1f).graphicsLayer { scaleX = if (active) 1.04f else 1f; scaleY = if (active) 1.04f else 1f },
          highlight = active, corner = 16.dp, contentPadding = PaddingValues(8.dp)
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(14.dp).clip(CircleShape).background(SEAT_COLORS[s]))
            Spacer(Modifier.width(6.dp))
            Text(if (owner[s] == "bot") "Bot" else c.nameOf(owner[s]), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
          }
          Text("🏁 $done/$totalT", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
      }
    }
    Spacer(Modifier.height(10.dp))
    GlassCard(modifier = Modifier.fillMaxWidth(), highlight = myTurn, corner = 16.dp, contentPadding = PaddingValues(12.dp)) {
      Text(
        when {
          winner >= 0 -> "Partida terminada"
          myTurn && dice == 0 -> "🎲 ¡Tu turno! Tira el dado"
          myTurn && tappable.size > 1 -> "Elige una ficha que brilla"
          myTurn -> "Moviendo…"
          else -> "Turno de ${seatName(turn)}"
        },
        color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp
      )
      if (msg.isNotEmpty()) Text(msg, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    }
    Spacer(Modifier.height(12.dp))
    LudoBoard(owner, pos, tappable, LUDO_STYLES[styleIdx], seatShapes) { idx -> if (idx / 4 == turn && myTurn && !rolling) doMove(idx % 4) }
    Spacer(Modifier.height(14.dp))
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
      Dice3D(dice, rollId, 60.dp)
      GradientButton(
        "🎲 Tirar dado",
        onClick = { doRoll() },
        enabled = myTurn && dice == 0 && !rolling,
        modifier = Modifier.weight(1f)
      )
    }
    if (tappable.isNotEmpty()) {
      Spacer(Modifier.height(8.dp))
      Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        movesNow.forEach { t ->
          GradientButton(
            if (pos[turn * 4 + t] == -1) "Sacar ficha ${t + 1}" else "Mover ficha ${t + 1}",
            onClick = { if (myTurn && !rolling) doMove(t) }, height = 46.dp
          )
        }
      }
    }
    Spacer(Modifier.height(10.dp))
    Text("Tablero 3D", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      LUDO_STYLES.forEachIndexed { i, st2 ->
        if (styleIdx == i) GradientButton(st2.name, onClick = { styleIdx = i }, height = 38.dp)
        else SoftButton(st2.name, onClick = { styleIdx = i })
      }
    }
    Spacer(Modifier.height(6.dp))
    Text("Mi ficha", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      SHAPE_NAMES.forEachIndexed { i, nm ->
        val mine = ((shapeMap[c.me] as? Number)?.toInt() ?: 0) == i
        if (mine) GradientButton(nm, onClick = { c.vm.patchGame("ludoShapes", mapOf("s.${c.me}" to i)) }, height = 38.dp)
        else SoftButton(nm, onClick = { c.vm.patchGame("ludoShapes", mapOf("s.${c.me}" to i)) })
      }
    }
    Spacer(Modifier.height(10.dp))
    SoftButton("Nueva partida", onClick = {
      publish(ludoState(listOf("", "", "", ""), emptyList<Int>(), 0, 0, rollId, -1, "", mode))
    }, modifier = Modifier.fillMaxWidth())
  }
}
