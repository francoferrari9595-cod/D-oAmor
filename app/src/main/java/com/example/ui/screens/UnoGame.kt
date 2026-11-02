package com.example.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.GlassCard
import com.example.ui.components.GradientButton
import com.example.ui.components.SoftButton
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ───────────────────────────── Reglas y mazo ─────────────────────────────
// Codificación de carta: color * 20 + tipo.
// Colores 0..3 = rojo, amarillo, verde, azul. Color 4 = comodines.
// Tipos 0..9 = números, 10 = bloqueo, 11 = cambio de sentido, 12 = +2, 13 = comodín de color, 14 = +4.

private val UNO_COLORS = listOf(Color(0xFFE53935), Color(0xFFFDD835), Color(0xFF43A047), Color(0xFF1E88E5))
private val UNO_COLOR_NAMES = listOf("Rojo", "Amarillo", "Verde", "Azul")
private val UNO_COLOR_KEYS = listOf("red", "yellow", "green", "blue")
private val UNO_SEAT_HUES = listOf(
  Color(0xFF8E24AA), Color(0xFF00897B), Color(0xFFF4511E),
  Color(0xFF3949AB), Color(0xFFB0A800), Color(0xFF6D4C41)
)

/** Las PNG recortadas originales siguen en res/drawable-nodpi. Son recortes de una hoja de sprites y varias
 *  no coinciden con su nombre, por eso las caras se dibujan con Compose. Poné true para usarlas igualmente. */
private const val UNO_USE_CROPPED_IMAGES = false

private val UNO_TABLE_H = 330.dp
private val UNO_PILE_W = 54.dp

private fun unoColor(card: Int) = card / 20
private fun unoType(card: Int) = card % 20

private fun unoDeck(decks: Int = 1): List<Int> {
  val d = mutableListOf<Int>()
  for (c in 0..3) { d.add(c * 20); for (t in 1..12) { d.add(c * 20 + t); d.add(c * 20 + t) } }
  repeat(4) { d.add(4 * 20 + 13); d.add(4 * 20 + 14) }
  val selected = if (decks == 2) d.toList() + d.toList() else d.toList()
  return selected.shuffled()
}

private fun unoSymbol(card: Int): String = when (val t = unoType(card)) { in 0..9 -> "$t"; 10 -> "⊘"; 11 -> "⇄"; 12 -> "+2"; 13 -> "🌈"; else -> "+4" }
private fun unoPoints(card: Int): Int = when (unoType(card)) { in 0..9 -> unoType(card); in 10..12 -> 20; else -> 50 }

/** Avanza `steps` posiciones desde `from` respetando el sentido (1 horario, -1 antihorario). */
private fun unoStep(from: Int, steps: Int, dir: Int, total: Int): Int {
  if (total <= 0) return 0
  return (((from + dir * steps) % total) + total) % total
}

private fun unoImageRes(ctx: android.content.Context, card: Int): Int {
  val color = unoColor(card)
  val type = unoType(card)
  val base = if (color == 4) "uno_wild_$type" else "uno_${UNO_COLOR_KEYS[color.coerceIn(0, 3)]}_$type"
  val names = mutableListOf(base)
  if (color == 3 && type == 11) names.add("uno_blue_11_0")
  if (color == 3 && type == 12) names.add("uno_blue_12_1")
  for (n in names) {
    val id = ctx.resources.getIdentifier(n, "drawable", ctx.packageName)
    if (id != 0) return id
  }
  return 0
}

// ───────────────────────────── Cartas dibujadas ─────────────────────────────

private fun DrawScope.unoGlyph(type: Int, c: Offset, r: Float, col: Color) {
  val sw = r * 0.30f
  if (type == 10) {
    drawCircle(col, radius = r, center = c, style = Stroke(width = sw))
    val d = r * 0.707f
    drawLine(col, Offset(c.x - d, c.y + d), Offset(c.x + d, c.y - d), strokeWidth = sw, cap = StrokeCap.Round)
  } else if (type == 11) {
    val yTop = c.y - r * 0.42f
    val yBot = c.y + r * 0.42f
    val x0 = c.x - r * 0.85f
    val x1 = c.x + r * 0.85f
    val head = r * 0.45f
    drawLine(col, Offset(x0, yTop), Offset(x1, yTop), strokeWidth = sw, cap = StrokeCap.Round)
    drawLine(col, Offset(x1, yTop), Offset(x1 - head, yTop - head), strokeWidth = sw, cap = StrokeCap.Round)
    drawLine(col, Offset(x1, yTop), Offset(x1 - head, yTop + head), strokeWidth = sw, cap = StrokeCap.Round)
    drawLine(col, Offset(x1, yBot), Offset(x0, yBot), strokeWidth = sw, cap = StrokeCap.Round)
    drawLine(col, Offset(x0, yBot), Offset(x0 + head, yBot - head), strokeWidth = sw, cap = StrokeCap.Round)
    drawLine(col, Offset(x0, yBot), Offset(x0 + head, yBot + head), strokeWidth = sw, cap = StrokeCap.Round)
  }
}

private val UNO_TEXT_SHADOW = TextStyle(shadow = Shadow(Color(0x99000000), Offset(2f, 3f), 2f))

@Composable
private fun UnoCardBack(w: Dp, modifier: Modifier = Modifier) {
  val h = w * 1.45f
  val outer = RoundedCornerShape(w * 0.13f)
  Box(modifier.size(w, h).shadow(2.dp, outer).background(Color.White, outer).padding(w * 0.05f)) {
    Box(
      Modifier.fillMaxSize().clip(RoundedCornerShape(w * 0.09f))
        .background(Brush.verticalGradient(listOf(Color(0xFF2A2A33), Color(0xFF0E0E12)))),
      contentAlignment = Alignment.Center
    ) {
      Canvas(Modifier.fillMaxSize()) {
        rotate(-24f, Offset(size.width / 2f, size.height / 2f)) {
          drawOval(Color(0xFFE53935), Offset(size.width * 0.08f, size.height * 0.20f), Size(size.width * 0.84f, size.height * 0.60f))
        }
      }
      Text(
        "UNO", color = Color(0xFFFFD600), fontWeight = FontWeight.Black, fontSize = (w.value * 0.27f).sp,
        modifier = Modifier.rotate(-24f), style = UNO_TEXT_SHADOW
      )
    }
  }
}

@Composable
private fun UnoCardFace(card: Int, w: Dp, modifier: Modifier = Modifier) {
  val ctx = LocalContext.current
  val color = unoColor(card)
  val type = unoType(card)
  val h = w * 1.45f
  val outer = RoundedCornerShape(w * 0.13f)
  val rid = remember(card) { if (UNO_USE_CROPPED_IMAGES) unoImageRes(ctx, card) else 0 }
  if (rid != 0) {
    Image(
      painter = painterResource(rid), contentDescription = "Carta UNO ${unoSymbol(card)}",
      modifier = modifier.size(w, h).clip(outer).border(1.dp, Color(0x55000000), outer), contentScale = ContentScale.FillBounds
    )
  } else {
    val isWild = color == 4
    val base = if (isWild) Color(0xFF15151B) else UNO_COLORS[color.coerceIn(0, 3)]
    val label = when (type) { in 0..9 -> "$type"; 12 -> "+2"; 14 -> "+4"; else -> "" }
    Box(modifier.size(w, h).shadow(3.dp, outer).background(Color.White, outer).padding(w * 0.05f)) {
      Box(Modifier.fillMaxSize().clip(RoundedCornerShape(w * 0.09f)).background(base), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
          val cw = size.width
          val ch = size.height
          val tl = Offset(cw * 0.10f, ch * 0.16f)
          val sz = Size(cw * 0.80f, ch * 0.68f)
          rotate(-24f, Offset(cw / 2f, ch / 2f)) {
            if (isWild) {
              val p = Path().apply { addOval(Rect(tl, sz)) }
              clipPath(p) {
                drawRect(UNO_COLORS[0], Offset(0f, 0f), Size(cw / 2f, ch / 2f))
                drawRect(UNO_COLORS[1], Offset(cw / 2f, 0f), Size(cw / 2f, ch / 2f))
                drawRect(UNO_COLORS[2], Offset(0f, ch / 2f), Size(cw / 2f, ch / 2f))
                drawRect(UNO_COLORS[3], Offset(cw / 2f, ch / 2f), Size(cw / 2f, ch / 2f))
              }
              drawOval(Color.White, tl, sz, style = Stroke(width = cw * 0.03f))
            } else {
              drawOval(Color.White, tl, sz)
            }
          }
          if (type == 10 || type == 11) {
            unoGlyph(type, Offset(cw / 2f, ch / 2f), cw * 0.25f, base)
            unoGlyph(type, Offset(cw * 0.21f, ch * 0.14f), cw * 0.085f, Color.White)
            unoGlyph(type, Offset(cw * 0.79f, ch * 0.86f), cw * 0.085f, Color.White)
          }
        }
        if (label.isNotEmpty()) {
          val centerColor = if (type == 14) Color.White else base
          val centerSize = if (label.length > 1) 0.44f else 0.60f
          Text(
            label, color = centerColor, fontWeight = FontWeight.Black, fontSize = (w.value * centerSize).sp,
            textDecoration = if (type == 6 || type == 9) TextDecoration.Underline else TextDecoration.None,
            style = UNO_TEXT_SHADOW, modifier = Modifier.align(Alignment.Center)
          )
          Text(
            label, color = Color.White, fontWeight = FontWeight.Black, fontSize = (w.value * 0.19f).sp,
            modifier = Modifier.align(Alignment.TopStart).padding(start = w * 0.05f, top = w * 0.02f)
          )
          Text(
            label, color = Color.White, fontWeight = FontWeight.Black, fontSize = (w.value * 0.19f).sp,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = w * 0.05f, bottom = w * 0.02f).rotate(180f)
          )
        }
      }
    }
  }
}

// ───────────────────────────── Mesa, asientos y efectos ─────────────────────────────

private data class UnoFly(val id: Int, val card: Int, val from: DpOffset, val to: DpOffset, val spin: Float)
private data class UnoPill(val id: Int, val text: String, val color: Color)

@Composable
private fun UnoFlyingCard(fly: UnoFly, onDone: () -> Unit) {
  val p = remember { Animatable(0f) }
  LaunchedEffect(fly.id) {
    p.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
    onDone()
  }
  val t = p.value
  val cw = UNO_PILE_W
  val ch = cw * 1.45f
  val x = fly.from.x + (fly.to.x - fly.from.x) * t
  val arc = (sin(t.toDouble() * PI).toFloat() * 28f).dp
  val y = fly.from.y + (fly.to.y - fly.from.y) * t - arc
  Box(
    Modifier.offset(x - cw / 2, y - ch / 2).graphicsLayer {
      rotationZ = (1f - t) * fly.spin
      val s = 0.78f + 0.22f * t
      scaleX = s
      scaleY = s
    }
  ) {
    if (fly.card >= 0) UnoCardFace(fly.card, cw) else UnoCardBack(cw)
  }
}

@Composable
private fun UnoPillView(pill: UnoPill, onDone: () -> Unit, modifier: Modifier = Modifier) {
  val s = remember { Animatable(0.3f) }
  val a = remember { Animatable(0f) }
  LaunchedEffect(pill.id) {
    launch { a.animateTo(1f, tween(160)) }
    s.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 380f))
    delay(900)
    a.animateTo(0f, tween(300))
    onDone()
  }
  val shape = RoundedCornerShape(50)
  Box(
    modifier.graphicsLayer { scaleX = s.value; scaleY = s.value; alpha = a.value }
      .clip(shape).background(pill.color).border(2.dp, Color.White, shape)
      .padding(horizontal = 18.dp, vertical = 8.dp)
  ) {
    Text(pill.text, color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp, style = UNO_TEXT_SHADOW)
  }
}

@Composable
private fun UnoConfetti(modifier: Modifier = Modifier) {
  val t by rememberInfiniteTransition(label = "confetti").animateFloat(
    initialValue = 0f, targetValue = 1f,
    animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing)), label = "confettiT"
  )
  val cols = UNO_COLORS + Color.White
  Canvas(modifier) {
    for (i in 0 until 46) {
      val seed = ((i * 7919) % 1000) / 1000f
      val seed2 = ((i * 104729) % 997) / 997f
      val x = size.width * seed
      val y = ((t * (0.6f + seed2) + seed2) % 1f) * (size.height + 40f) - 20f
      rotate(t * 360f * (1f + seed2), Offset(x, y)) {
        drawRect(cols[i % cols.size], Offset(x, y), Size(8.dp.toPx(), 12.dp.toPx()))
      }
    }
  }
}

@Composable
private fun UnoSeat(
  name: String, count: Int, uno: Boolean, points: Int, active: Boolean, won: Boolean,
  hue: Color, compact: Boolean, modifier: Modifier = Modifier
) {
  val pulse = rememberInfiniteTransition(label = "seatPulse")
  val glow by pulse.animateFloat(
    initialValue = 0f, targetValue = 1f,
    animationSpec = infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "seatGlow"
  )
  val bump by animateFloatAsState(if (active) 1.1f else 1f, spring(dampingRatio = 0.5f), label = "seatBump")
  val ring = if (active || won) Color(0xFFFFD166) else Color(0x88FFFFFF)
  val avatarSize = if (compact) 34.dp else 40.dp
  val avatar: @Composable () -> Unit = {
    Box(
      Modifier.size(avatarSize)
        .graphicsLayer { scaleX = bump; scaleY = bump }
        .drawBehind {
          if (active) drawCircle(Color(0xFFFFD166).copy(alpha = 0.20f + 0.35f * glow), radius = size.minDimension * 0.78f)
        }
        .clip(CircleShape)
        .background(Brush.linearGradient(listOf(hue, hue.copy(alpha = 0.55f))))
        .border(2.dp, ring, CircleShape),
      contentAlignment = Alignment.Center
    ) {
      Text(if (won) "🏆" else name.trim().take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Black, fontSize = if (compact) 15.sp else 18.sp)
    }
  }
  if (compact) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
      avatar()
      Spacer(Modifier.width(8.dp))
      Column {
        Text(name, color = if (active) Color(0xFFFFD166) else Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("$count cartas · $points pts", color = Color(0xCCFFFFFF), fontSize = 11.sp)
      }
      if (uno) { Spacer(Modifier.width(8.dp)); UnoBadge() }
    }
  } else {
    Column(modifier.width(80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
      avatar()
      Text(name, color = if (active) Color(0xFFFFD166) else Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
      val shown = minOf(count, 7)
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(22.dp + 8.dp * (shown - 1).coerceAtLeast(0)).height(33.dp)) {
          for (k in 0 until shown) {
            UnoCardBack(22.dp, Modifier.offset(x = 8.dp * k).rotate((k - (shown - 1) / 2f) * 5f))
          }
        }
        Spacer(Modifier.width(4.dp))
        Text("×$count", color = Color.White, fontWeight = FontWeight.Black, fontSize = 11.sp)
      }
      if (uno) UnoBadge() else Text(if (active) "jugando…" else "$points pts", color = if (active) Color(0xFFFFD166) else Color(0xCCFFFFFF), fontSize = 10.sp)
    }
  }
}

@Composable
private fun UnoBadge() {
  Box(Modifier.clip(RoundedCornerShape(50)).background(Color(0xFFE53935)).border(1.dp, Color.White, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 1.dp)) {
    Text("¡UNO!", color = Color.White, fontWeight = FontWeight.Black, fontSize = 10.sp)
  }
}

@Composable
private fun UnoTable(
  total: Int, myIdx: Int, turn: Int,
  names: List<String>, handSizes: List<Int>, uno: List<Boolean>, points: List<Int>,
  top: Int?, under: Int?, curColor: Int, dir: Int, deckCount: Int,
  seq: Int, act: Int, actor: Int, winner: Int,
  canDraw: Boolean, onDraw: () -> Unit,
  sfx: (String, Float) -> Unit, startSfx: (Int) -> Unit
) {
  BoxWithConstraints(Modifier.fillMaxWidth().height(UNO_TABLE_H + 44.dp)) {
    val wv = maxWidth.value
    val cx = wv / 2f
    val cy = UNO_TABLE_H.value / 2f
    val deckC = DpOffset((cx - 40f).dp, (cy + 2f).dp)
    val discC = DpOffset((cx + 40f).dp, (cy + 2f).dp)

    fun seatCenter(idx: Int): DpOffset {
      val rel = (((idx - myIdx) % total) + total) % total
      if (rel == 0) return DpOffset(cx.dp, UNO_TABLE_H + 22.dp)
      val ang = (2.0 * PI * rel / total).toFloat()
      val rx = wv / 2f - 48f
      val ry = UNO_TABLE_H.value / 2f - 46f
      return DpOffset((cx - rx * sin(ang)).dp, (cy + ry * cos(ang)).dp)
    }

    var lastSeq by remember { mutableStateOf<Int?>(null) }
    var fly by remember { mutableStateOf<UnoFly?>(null) }
    var flyId by remember { mutableStateOf(0) }
    var pill by remember { mutableStateOf<UnoPill?>(null) }
    var pillId by remember { mutableStateOf(0) }

    // Una sola fuente de animaciones y sonidos: el estado compartido. Así los ven y oyen todos los jugadores.
    LaunchedEffect(seq) {
      val prev = lastSeq
      lastSeq = seq
      if (prev == null) { if (act == 5) startSfx(1); return@LaunchedEffect }
      if (seq == prev) return@LaunchedEffect
      if (act == 5) { startSfx(1); return@LaunchedEffect }
      if (prev <= 0 || actor !in 0 until total) return@LaunchedEffect
      when (act) {
        1, 6 -> {
          val card = top ?: -1
          flyId += 1
          fly = UnoFly(flyId, card, seatCenter(actor), discC, ((actor * 37) % 21 - 10).toFloat())
          val special = card >= 0 && unoType(card) in 10..14
          sfx(if (special) "sfx_truco_card" else "sfx_card", 0.5f)
          if (card >= 0) {
            val p = when (unoType(card)) {
              10 -> UnoPill(0, "🚫 ¡BLOQUEO!", Color(0xFFD81B60))
              11 -> UnoPill(0, "🔄 ¡CAMBIO DE SENTIDO!", Color(0xFF3949AB))
              12 -> UnoPill(0, "+2 ¡ROBA DOS!", Color(0xFFEF6C00))
              14 -> UnoPill(0, "+4 ¡ROBA CUATRO!", Color(0xFF8E24AA))
              13 -> UnoPill(0, "🌈 ¡COLOR NUEVO!", Color(0xFF00897B))
              else -> null
            }
            if (p != null) { pillId += 1; pill = p.copy(id = pillId) }
          }
          if (act == 6) { delay(520); startSfx(2) }
        }
        2 -> {
          flyId += 1
          fly = UnoFly(flyId, -1, deckC, seatCenter(actor), 0f)
          sfx("sfx_truco_card", 0.35f)
        }
        3 -> sfx("sfx_countdown_tick", 0.2f)
        4 -> {
          pillId += 1
          pill = UnoPill(pillId, "🔔 ¡UNO!", Color(0xFFE53935))
          sfx("sfx_countdown_tick", 0.5f)
          startSfx(0)
        }
        else -> {}
      }
    }

    val pulse by rememberInfiniteTransition(label = "tablePulse").animateFloat(
      initialValue = 0f, targetValue = 1f,
      animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "tablePulseV"
    )
    val ringRot by rememberInfiniteTransition(label = "ring").animateFloat(
      initialValue = 0f, targetValue = 360f,
      animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "ringV"
    )
    val colorNow by animateColorAsState(UNO_COLORS[curColor.coerceIn(0, 3)], tween(500), label = "colorNow")
    val pop = remember { Animatable(1f) }
    LaunchedEffect(seq) {
      pop.snapTo(1.14f)
      pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 300f))
    }

    // Paño de juego: borde de madera, fieltro verde con luz central y filete dorado.
    val tableShape = RoundedCornerShape(50)
    Box(
      Modifier.fillMaxWidth().height(UNO_TABLE_H)
        .shadow(10.dp, tableShape)
        .clip(tableShape)
        .background(Brush.verticalGradient(listOf(Color(0xFF7A4A24), Color(0xFF3E2410), Color(0xFF5E3718))))
        .padding(9.dp)
    ) {
      Box(
        Modifier.fillMaxSize().clip(tableShape)
          .background(Brush.radialGradient(listOf(Color(0xFF1E8F58), Color(0xFF136B41), Color(0xFF0A3D26))))
          .border(2.dp, Color(0x99FFD166), tableShape)
          .drawBehind {
            drawCircle(Color(0x14FFFFFF), radius = size.minDimension * 0.30f, style = Stroke(width = 2.dp.toPx()))
            drawCircle(Color(0x0DFFFFFF), radius = size.minDimension * 0.44f, style = Stroke(width = 1.dp.toPx()))
          }
      ) {
        Text(
          "UNO", color = Color(0x12FFFFFF), fontWeight = FontWeight.Black, fontSize = 74.sp,
          modifier = Modifier.align(Alignment.Center).rotate(-14f)
        )
      }
    }

    // Anillo de sentido de juego, con el color vigente.
    Canvas(
      Modifier.offset((cx - 85f).dp, (cy + 2f - 85f).dp).size(170.dp)
        .graphicsLayer {
          rotationZ = if (dir >= 0) ringRot else -ringRot
          scaleX = if (dir >= 0) 1f else -1f
        }
    ) {
      val r = size.minDimension / 2f - 6.dp.toPx()
      val sw = 4.dp.toPx()
      val col = colorNow.copy(alpha = 0.55f)
      val tlr = Offset(size.width / 2f - r, size.height / 2f - r)
      val sizer = Size(2f * r, 2f * r)
      for (start in listOf(20f, 200f)) {
        drawArc(col, startAngle = start, sweepAngle = 130f, useCenter = false, topLeft = tlr, size = sizer, style = Stroke(width = sw, cap = StrokeCap.Round))
        val a = ((start + 130f) * PI / 180.0).toFloat()
        val p = Offset(size.width / 2f + r * cos(a), size.height / 2f + r * sin(a))
        val tan = Offset(-sin(a), cos(a))
        val nor = Offset(cos(a), sin(a))
        val tip = Offset(p.x + tan.x * 11.dp.toPx(), p.y + tan.y * 11.dp.toPx())
        val b1 = Offset(p.x + nor.x * 8.dp.toPx(), p.y + nor.y * 8.dp.toPx())
        val b2 = Offset(p.x - nor.x * 8.dp.toPx(), p.y - nor.y * 8.dp.toPx())
        val head = Path().apply { moveTo(tip.x, tip.y); lineTo(b1.x, b1.y); lineTo(b2.x, b2.y); close() }
        drawPath(head, col)
      }
    }

    // Mazo de robo.
    Box(
      Modifier.offset(deckC.x - UNO_PILE_W / 2, deckC.y - UNO_PILE_W * 1.45f / 2)
        .size(UNO_PILE_W, UNO_PILE_W * 1.45f)
        .drawBehind {
          if (canDraw) drawCircle(Color(0xFFFFD166).copy(alpha = 0.18f + 0.30f * pulse), radius = size.maxDimension * 0.75f)
        }
        .clickable(enabled = canDraw) { onDraw() }
    ) {
      for (k in 0 until 3) UnoCardBack(UNO_PILE_W, Modifier.offset(x = (-k * 1.5f).dp, y = (-k * 1.5f).dp))
    }
    Text(
      "Mazo · $deckCount", color = Color(0xDDFFFFFF), fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
      modifier = Modifier.offset(deckC.x - 40.dp, deckC.y + 46.dp).width(80.dp)
    )

    // Pila de descarte con el color vigente.
    Box(
      Modifier.offset(discC.x - UNO_PILE_W / 2, discC.y - UNO_PILE_W * 1.45f / 2)
        .size(UNO_PILE_W, UNO_PILE_W * 1.45f)
        .drawBehind { drawCircle(colorNow.copy(alpha = 0.38f), radius = size.maxDimension * 0.8f) }
    ) {
      if (under != null) UnoCardFace(under, UNO_PILE_W, Modifier.offset(x = (-3).dp).rotate(-9f))
      if (top != null) UnoCardFace(top, UNO_PILE_W, Modifier.graphicsLayer { rotationZ = 5f; scaleX = pop.value; scaleY = pop.value })
    }
    Row(
      Modifier.offset(discC.x - 45.dp, discC.y + 46.dp).width(90.dp),
      horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
    ) {
      Box(Modifier.size(9.dp).clip(CircleShape).background(colorNow).border(1.dp, Color.White, CircleShape))
      Spacer(Modifier.width(4.dp))
      Text(UNO_COLOR_NAMES[curColor.coerceIn(0, 3)], color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }

    // Rivales sentados alrededor de la mesa.
    for (i in 0 until total) {
      if (i != myIdx) {
        val pos = seatCenter(i)
        UnoSeat(
          name = names[i], count = handSizes[i], uno = uno[i] && handSizes[i] == 1, points = points[i],
          active = i == turn && winner < 0, won = winner == i, hue = UNO_SEAT_HUES[i % UNO_SEAT_HUES.size], compact = false,
          modifier = Modifier.offset(pos.x - 40.dp, pos.y - 44.dp)
        )
      }
    }
    // Mi lugar, en el borde inferior de la mesa.
    val me = seatCenter(myIdx)
    UnoSeat(
      name = "Vos", count = handSizes[myIdx], uno = uno[myIdx] && handSizes[myIdx] == 1, points = points[myIdx],
      active = myIdx == turn && winner < 0, won = winner == myIdx, hue = UNO_SEAT_HUES[myIdx % UNO_SEAT_HUES.size], compact = true,
      modifier = Modifier.offset(me.x - 110.dp, me.y - 20.dp).width(220.dp)
    )

    // La carta que vuela desde quien juega (o hacia quien roba).
    val f = fly
    if (f != null) key(f.id) { UnoFlyingCard(f) { fly = null } }

    val pl = pill
    if (pl != null) key(pl.id) {
      UnoPillView(pl, { pill = null }, Modifier.align(Alignment.TopCenter).offset(y = UNO_TABLE_H / 2 + 76.dp))
    }

    if (winner >= 0) UnoConfetti(Modifier.fillMaxSize())
  }
}

// ───────────────────────────── Mano del jugador ─────────────────────────────

@Composable
private fun UnoHandCard(
  card: Int, x: Dp, y: Dp, rot: Float, w: Dp, playable: Boolean, dim: Boolean, appearDelay: Long, onClick: () -> Unit
) {
  var shown by remember { mutableStateOf(false) }
  LaunchedEffect(Unit) {
    if (appearDelay > 0L) delay(appearDelay)
    shown = true
  }
  val appear by animateFloatAsState(if (shown) 1f else 0f, spring(dampingRatio = 0.62f, stiffness = 260f), label = "handAppear")
  val ax by animateDpAsState(x, spring(stiffness = 350f), label = "handX")
  val ay by animateDpAsState(y, spring(stiffness = 420f), label = "handY")
  val shape = RoundedCornerShape(w * 0.13f)
  Box(
    Modifier.offset(ax, ay)
      .graphicsLayer {
        rotationZ = rot
        alpha = appear.coerceIn(0f, 1f)
        translationY = (1f - appear) * 120f
        val s = 0.7f + 0.3f * appear
        scaleX = s
        scaleY = s
      }
      .clickable(enabled = playable) { onClick() }
  ) {
    UnoCardFace(card, w)
    if (playable) Box(Modifier.matchParentSize().border(2.dp, Color(0xFFFFD166), shape))
    else if (dim) Box(Modifier.matchParentSize().clip(shape).background(Color(0x66000000)))
  }
}

@Composable
private fun UnoHand(hand: List<Int>, playable: List<Boolean>, dim: Boolean, dealing: Boolean, onPlay: (Int) -> Unit) {
  BoxWithConstraints(Modifier.fillMaxWidth()) {
    val avail = maxWidth
    val n = hand.size
    val cw = if (n > 18) 54.dp else 62.dp
    val ch = cw * 1.45f
    val maxStep = cw * 0.72f
    val minStep = 22.dp
    val raw = if (n > 1) (avail - cw) / (n - 1) else maxStep
    val step = minOf(maxOf(raw, minStep), maxStep)
    val contentW = cw + step * (n - 1).coerceAtLeast(0)
    val fits = contentW <= avail
    val boxW = maxOf(contentW, avail)
    val startX = (boxW - contentW) / 2
    Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
      Box(Modifier.width(boxW).height(ch + 44.dp)) {
        val seen = HashMap<Int, Int>()
        hand.forEachIndexed { i, card ->
          val occ = seen[card] ?: 0
          seen[card] = occ + 1
          key(card * 1000 + occ) {
            val ang = if (fits && n > 1) (i - (n - 1) / 2f) * minOf(5f, 36f / (n - 1)) else 0f
            val arcY = if (fits) (ang * ang * 0.05f).dp else 0.dp
            val can = playable.getOrElse(i) { false }
            UnoHandCard(
              card = card, x = startX + step * i, y = 30.dp + arcY - (if (can) 16.dp else 0.dp), rot = ang, w = cw,
              playable = can, dim = dim && !can, appearDelay = if (dealing) minOf(i, 10) * 55L else 0L,
              onClick = { onPlay(i) }
            )
          }
        }
      }
    }
  }
}

// ───────────────────────────── Sala previa ─────────────────────────────

@Composable
private fun UnoDeckTile(title: String, sub: String, stacks: Int, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
  val border by animateColorAsState(if (selected) Color(0xFFFFD166) else Color(0x44FFFFFF), tween(250), label = "tileBorder")
  val sc by animateFloatAsState(if (selected) 1.03f else 1f, spring(dampingRatio = 0.55f), label = "tileScale")
  val shape = RoundedCornerShape(18.dp)
  Column(
    modifier.graphicsLayer { scaleX = sc; scaleY = sc }
      .clip(shape)
      .background(if (selected) Color(0x33FFD166) else Color(0x14FFFFFF))
      .border(2.dp, border, shape)
      .clickable { onClick() }
      .padding(12.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Box(Modifier.height(66.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
      repeat(stacks) { k ->
        UnoCardBack(34.dp, Modifier.offset(x = ((k * 14) - (stacks - 1) * 7).dp).rotate(if (stacks == 1) 0f else (k * 10 - 5).toFloat()))
      }
    }
    Text(title, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
    Text(sub, color = if (selected) Color(0xFFFFD166) else Color(0xCCFFFFFF), fontWeight = FontWeight.Bold, fontSize = 12.sp)
  }
}

@Composable
private fun UnoColorOrb(col: Int, onClick: () -> Unit) {
  var shown by remember { mutableStateOf(false) }
  LaunchedEffect(Unit) { delay(col * 70L); shown = true }
  val sc by animateFloatAsState(if (shown) 1f else 0f, spring(dampingRatio = 0.45f, stiffness = 300f), label = "orb")
  Box(
    Modifier.size(54.dp).graphicsLayer { scaleX = sc; scaleY = sc }
      .shadow(6.dp, CircleShape).clip(CircleShape)
      .background(Brush.radialGradient(listOf(UNO_COLORS[col].copy(alpha = 0.8f), UNO_COLORS[col])))
      .border(3.dp, Color.White, CircleShape)
      .clickable { onClick() },
    contentAlignment = Alignment.Center
  ) {
    Text(UNO_COLOR_NAMES[col].take(1), color = if (col == 1) Color.Black else Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
  }
}

// ───────────────────────────── Juego ─────────────────────────────

@Composable
internal fun UnoGame(c: GameCtx) {
  val cardSoundContext = LocalContext.current
  val flow = remember { c.vm.gameState("uno") }
  val st by flow.collectAsState(initial = null)
  val humanCount = c.players.size.coerceIn(1, 6)
  val botCount = c.lobbyBots.coerceIn(0, 6 - humanCount)
  val total = (humanCount + botCount).coerceIn(1, 6)
  val host = c.me == c.hostUid
  var selectedDecks by remember { mutableStateOf(1) }
  fun ints(k: String): List<Int> = (st?.get(k) as? List<*>)?.mapNotNull { (it as? Number)?.toInt() } ?: emptyList()
  fun num(k: String, default: Int = 0): Int = (st?.get(k) as? Number)?.toInt() ?: default
  fun bool(k: String): Boolean = st?.get(k) as? Boolean ?: false
  fun hands(): List<List<Int>> = (0 until 6).map { ints("h$it") }
  fun flags(): List<Boolean> = (0 until 6).map { bool("u$it") }
  fun scores(): List<Int> = (0 until 6).map { num("sc$it") }
  // Mazos de la partida en curso: si ya hay estado guardado manda siempre el guardado, así las rondas nuevas conservan la elección.
  val hasSavedDecks = st?.containsKey("decks") == true
  val savedDecks = if (hasSavedDecks) (if (num("decks", 1) == 2) 2 else 1) else selectedDecks
  LaunchedEffect(hasSavedDecks, savedDecks) { if (hasSavedDecks) selectedDecks = savedDecks }
  val allHands = hands()
  val hand = allHands.getOrElse(c.myIdx) { emptyList() }
  val deck = ints("deck")
  val disc = ints("disc")
  val turn = num("turn")
  val curColor = num("color")
  val winner = num("winner", -1)
  val drew = bool("drew")
  val unoFlags = flags()
  val points = scores()
  val message = st?.get("msg") as? String ?: ""
  val curDir = if (num("dir", 1) < 0) -1 else 1
  val seq = num("seq")
  val act = num("act")
  val actor = num("actor", -1)
  val started = disc.isNotEmpty() && num("total", 0) in 1..6
  val myTurn = started && winner < 0 && turn == c.myIdx && c.me in c.players
  var wildCard by remember { mutableStateOf<Int?>(null) }
  var lastTurnForSound by remember { mutableStateOf<Int?>(null) }
  LaunchedEffect(myTurn, started, winner) {
    if (started && winner < 0 && myTurn && lastTurnForSound != turn) {
      com.example.audio.GameSfx.play(cardSoundContext, "sfx_countdown_tick", 0.32f)
    }
    lastTurnForSound = if (myTurn) turn else null
    if (!myTurn) wildCard = null
  }

  fun name(i: Int): String = if (i < humanCount) c.nameOfIdx(i) else "Bot ${i - humanCount + 1}"
  fun playable(card: Int, pile: List<Int> = disc, color: Int = curColor): Boolean {
    if (pile.isEmpty()) return false
    val top = pile.last()
    return unoColor(card) == 4 || unoColor(card) == color || (unoType(card) == unoType(top) && unoColor(top) != 4)
  }
  fun save(
    h: List<List<Int>>, d: List<Int>, pile: List<Int>, t: Int, color: Int, w: Int, drewNow: Boolean, flagsNow: List<Boolean>, msg: String,
    scoreNow: List<Int> = points, decks: Int = savedDecks, dirNow: Int = curDir, actNow: Int = 0, actorNow: Int = -1
  ) {
    val data = mutableMapOf<String, Any>(
      "deck" to d, "disc" to pile, "turn" to t, "color" to color, "winner" to w, "drew" to drewNow, "total" to total,
      "msg" to msg, "decks" to decks, "dir" to dirNow, "seq" to (seq + 1), "act" to actNow, "actor" to actorNow
    )
    for (i in 0 until 6) { data["h$i"] = h.getOrElse(i) { emptyList() }; data["u$i"] = flagsNow.getOrElse(i) { false }; data["sc$i"] = scoreNow.getOrElse(i) { 0 } }
    c.vm.publishGame("uno", data)
  }
  fun drawFrom(d: MutableList<Int>, pile: MutableList<Int>, count: Int): List<Int> {
    val out = mutableListOf<Int>()
    repeat(count) {
      if (d.isEmpty() && pile.size > 1) { val top = pile.removeAt(pile.lastIndex); d.addAll(pile.shuffled()); pile.clear(); pile.add(top) }
      if (d.isNotEmpty()) out.add(d.removeAt(0))
    }
    return out
  }
  fun deal(keepScore: Boolean) {
    if (!c.players.contains(c.me)) return
    // Entre rondas se reutiliza el mazo guardado; solo la primera mano usa la elección de la sala.
    val decksNow = if (keepScore) savedDecks else selectedDecks
    val d = unoDeck(decksNow).toMutableList()
    val h = MutableList(6) { mutableListOf<Int>() }
    repeat(total) { i -> repeat(7) { h[i].add(d.removeAt(0)) } }
    var first = d.removeAt(0)
    while (unoColor(first) == 4 || unoType(first) > 9) { d.add(first); first = d.removeAt(0) }
    save(
      h, d, listOf(first), 0, unoColor(first), -1, false, List(6) { false },
      "¡Empieza UNO con $total participantes · ${if (decksNow == 1) 108 else 216} cartas!",
      if (keepScore) points else List(6) { 0 }, decksNow, 1, 5, -1
    )
    // El sonido de inicio lo reproduce la mesa de todos los jugadores a partir del estado compartido.
  }
  fun executePlay(player: Int, card: Int, chosenColor: Int) {
    if (player !in 0 until total || !playable(card)) return
    val h = allHands.map { it.toMutableList() }.toMutableList()
    val d = deck.toMutableList(); val pile = disc.toMutableList(); val f = unoFlags.toMutableList(); val sc = points.toMutableList()
    if (!h[player].remove(card)) return
    pile.add(card)
    val type = unoType(card); val color = if (unoColor(card) == 4) chosenColor.coerceIn(0, 3) else unoColor(card)
    var newDir = curDir
    var next = unoStep(player, 1, newDir, total)
    var msg = "${name(player)} jugó ${unoSymbol(card)}"
    when (type) {
      // Bloqueo: el siguiente pierde el turno.
      10 -> { val skipped = next; next = unoStep(player, 2, newDir, total); msg += " · ${name(skipped)} pierde turno" }
      // Cambio de sentido: se invierte la dirección y se conserva para el resto de la ronda. Con 2 jugadores actúa como bloqueo.
      11 -> { newDir = -curDir; next = if (total == 2) player else unoStep(player, 1, newDir, total); msg += " · cambia el sentido" }
      // +2: el siguiente roba dos y pierde el turno.
      12 -> { val victim = next; h[victim].addAll(drawFrom(d, pile, 2)); f[victim] = false; next = unoStep(victim, 1, newDir, total); msg += " · ${name(victim)} roba 2" }
      // +4: el siguiente roba cuatro y pierde el turno (el color lo eligió quien jugó).
      14 -> { val victim = next; h[victim].addAll(drawFrom(d, pile, 4)); f[victim] = false; next = unoStep(victim, 1, newDir, total); msg += " · ${name(victim)} roba 4" }
    }
    val auto = player >= humanCount // los bots dicen UNO solos
    if (h[player].size == 1 && !f[player] && !auto) { h[player].addAll(drawFrom(d, pile, 2)); msg += " · olvidó decir UNO y roba 2" }
    f[player] = h[player].size == 1 && (f[player] || auto)
    if (h[player].isEmpty()) {
      val earned = h.take(total).flatten().sumOf { unoPoints(it) }
      sc[player] += earned
      save(h, d, pile, next, color, player, false, f, "🏆 ${name(player)} ganó la ronda (+$earned)", sc, savedDecks, newDir, 6, player)
    } else {
      save(h, d, pile, next, color, -1, false, f, msg, sc, savedDecks, newDir, 1, player)
    }
    wildCard = null
  }
  fun drawPlayer(player: Int, isHumanAction: Boolean) {
    if (player != turn || winner >= 0 || (isHumanAction && drew)) return
    val h = allHands.map { it.toMutableList() }.toMutableList(); val d = deck.toMutableList(); val pile = disc.toMutableList()
    val f = unoFlags.toMutableList()
    val got = drawFrom(d, pile, 1); h[player].addAll(got); f[player] = false
    val can = got.any { playable(it, pile, curColor) }
    save(h, d, pile, if (can) player else unoStep(player, 1, curDir, total), curColor, -1, can, f, "${name(player)} robó una carta", points, savedDecks, curDir, 2, player)
  }
  fun passPlayer(player: Int) {
    if (player == turn && drew && winner < 0) save(allHands, deck, disc, unoStep(player, 1, curDir, total), curColor, -1, false, unoFlags, "${name(player)} pasó", points, savedDecks, curDir, 3, player)
  }
  fun sayUno() {
    if (myTurn && hand.size == 2 && !unoFlags[c.myIdx]) {
      val f = unoFlags.toMutableList(); f[c.myIdx] = true
      save(allHands, deck, disc, turn, curColor, -1, drew, f, "🔔 ${name(c.myIdx)} dijo ¡UNO!", points, savedDecks, curDir, 4, c.myIdx)
    }
  }

  // El anfitrión es el único que ejecuta turnos de bots, evitando dobles jugadas en Firestore.
  LaunchedEffect(started, turn, winner, disc, deck, allHands, drew) {
    if (host && started && winner < 0 && turn >= humanCount && turn < total) {
      delay((900..1500).random().toLong())
      val botHand = allHands[turn]
      val choice = botHand.firstOrNull { playable(it) && unoColor(it) != 4 } ?: botHand.firstOrNull { playable(it) }
      if (choice != null) {
        val bestColor = (0..3).maxByOrNull { col -> botHand.count { unoColor(it) == col } } ?: 0
        executePlay(turn, choice, if (unoColor(choice) == 4) bestColor else unoColor(choice))
      } else {
        drawPlayer(turn, false)
      }
    }
  }

  Crossfade(targetState = started, animationSpec = tween(450), label = "unoPhase") { isStarted ->
    if (!isStarted) {
      Column(Modifier.fillMaxWidth()) {
        GlassCard(Modifier.fillMaxWidth(), highlight = true) {
          Text("🃏 UNO · hasta 6 jugadores", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
          Text("Jugá solo contra bots o con hasta seis personas. Colores, números, bloqueos, cambio de sentido, +2, +4 y comodines.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
          Text("Participantes: $humanCount humanos + $botCount bots", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
          Spacer(Modifier.height(10.dp))
          Text("Cantidad de mazos", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
          Spacer(Modifier.height(6.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            UnoDeckTile("1 mazo", "108 cartas", 1, selectedDecks == 1, { selectedDecks = 1 }, Modifier.weight(1f))
            UnoDeckTile("2 mazos", "216 cartas", 2, selectedDecks == 2, { selectedDecks = 2 }, Modifier.weight(1f))
          }
          Spacer(Modifier.height(6.dp))
          Text("Seleccionado: ${if (selectedDecks == 1) "108 cartas" else "216 cartas"} · se mantiene en todas las rondas", color = Color(0xFFFFD166), fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        Spacer(Modifier.height(10.dp))
        GradientButton("Repartir 7 cartas · ${if (selectedDecks == 1) "108" else "216"} cartas", onClick = { deal(false) }, modifier = Modifier.fillMaxWidth(), enabled = c.players.contains(c.me))
      }
    } else {
      val canDraw = myTurn && !drew && winner < 0
      Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
          "🃏 UNO · $total jugadores · ${if (savedDecks == 1) "1 mazo · 108" else "2 mazos · 216"} cartas",
          color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, modifier = Modifier.padding(bottom = 8.dp)
        )
        UnoTable(
          total = total, myIdx = c.myIdx, turn = turn,
          names = (0 until 6).map { name(it) }, handSizes = allHands.map { it.size }, uno = unoFlags, points = points,
          top = disc.lastOrNull(), under = if (disc.size > 1) disc[disc.size - 2] else null,
          curColor = curColor, dir = curDir, deckCount = deck.size,
          seq = seq, act = act, actor = actor, winner = winner,
          canDraw = canDraw, onDraw = { drawPlayer(c.myIdx, true) },
          sfx = { n, v -> com.example.audio.GameSfx.play(cardSoundContext, n, v) },
          startSfx = { k -> c.vm.gameSfx(k) }
        )
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
        if (winner >= 0) {
          ResultBanner("🏆 ¡${name(winner)} ganó la ronda! ${points[winner]} puntos")
          if (c.players.contains(c.me)) GradientButton("Siguiente ronda", onClick = { deal(true) }, modifier = Modifier.fillMaxWidth())
        } else {
          if (myTurn) {
            TurnBanner(if (drew) "Jugá la carta robada o pasá" else "Tu turno: jugá una carta o robá", true)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
              GradientButton("¡UNO!", onClick = { sayUno() }, enabled = hand.size == 2 && !unoFlags[c.myIdx], modifier = Modifier.weight(1f))
              SoftButton("Robar", onClick = { drawPlayer(c.myIdx, true) }, enabled = !drew, modifier = Modifier.weight(1f))
              SoftButton("Pasar", onClick = { passPlayer(c.myIdx) }, enabled = drew, modifier = Modifier.weight(1f))
            }
            val chosen = wildCard
            if (chosen != null) {
              Spacer(Modifier.height(8.dp))
              GlassCard(Modifier.fillMaxWidth(), highlight = true, corner = 18.dp) {
                Text("Elegí el nuevo color", color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                  (0..3).forEach { col -> UnoColorOrb(col) { executePlay(c.myIdx, chosen, col) } }
                }
                Spacer(Modifier.height(6.dp))
                SoftButton("Cancelar", onClick = { wildCard = null }, modifier = Modifier.fillMaxWidth())
              }
            }
          } else {
            TurnBanner("Turno de ${name(turn)}… · podés planear tu próxima jugada", false)
          }
          // La mano permanece visible durante toda la ronda, pero solo se puede jugar en tu turno.
          Text("Tus cartas (${hand.size})", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
          val playableFlags = hand.mapIndexed { i, card -> myTurn && playable(card) && (!drew || i == hand.lastIndex) }
          UnoHand(hand = hand, playable = playableFlags, dim = myTurn, dealing = act == 5) { index ->
            val card = hand.getOrNull(index)
            if (card != null && myTurn && playableFlags.getOrElse(index) { false }) {
              if (unoColor(card) == 4) wildCard = card else executePlay(c.myIdx, card, unoColor(card))
            }
          }
        }
      }
    }
  }
}
