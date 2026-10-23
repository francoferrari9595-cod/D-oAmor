package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.GlassCard
import com.example.ui.components.GradientButton
import com.example.ui.components.SoftButton
import com.example.ui.theme.GoldYellow
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

// Carta = tipo*10 + color. Tipos: 0 órgano, 1 virus, 2 medicina, 3 tratamiento.
// Colores: 0 corazón (rojo), 1 estómago (verde), 2 cerebro (azul), 3 hueso (amarillo), 4 multicolor.
// Tratamientos (color): 0 trasplante, 1 ladrón de órganos, 2 contagio, 3 guante de látex, 4 error médico.
private val V_COLORS = listOf(Color(0xFFE53935), Color(0xFF43A047), Color(0xFF1E88E5), Color(0xFFFDD835), Color(0xFF9C27B0))
private val V_ORGAN = listOf("❤️", "🫃", "🧠", "🦴", "🌈")
private val V_ORGAN_NAME = listOf("Corazón", "Estómago", "Cerebro", "Hueso", "Órgano comodín")
private val V_TREAT = listOf("🔄", "🕵️", "☣️", "🧤", "🩺")
private val V_TREAT_NAME = listOf("Trasplante", "Ladrón de órganos", "Contagio", "Guante de látex", "Error médico")
private val V_TREAT_HELP = listOf(
  "Intercambia un órgano entre dos jugadores",
  "Roba un órgano de un rival",
  "Pasa tus virus a órganos libres rivales",
  "Intercambia todos tus órganos y sus cartas asociadas con los de un rival",
  "Cambia todo tu cuerpo por el de otro"
)

private fun vType(c: Int) = c / 10
private fun vColor(c: Int) = c % 10

private fun virusDeck(): List<Int> {
  val d = mutableListOf<Int>()
  for (c in 0..3) repeat(5) { d.add(c) }
  d.add(4)
  for (c in 0..3) repeat(4) { d.add(10 + c) }
  d.add(14)
  for (c in 0..3) repeat(4) { d.add(20 + c) }
  repeat(4) { d.add(24) }
  repeat(3) { d.add(30) }; repeat(3) { d.add(31) }; repeat(2) { d.add(32) }; d.add(33); d.add(34)
  return d.shuffled()
}

private class Slot(val organ: Int, val atts: List<Int>) {
  val viruses get() = atts.count { vType(it) == 1 }
  val meds get() = atts.count { vType(it) == 2 }
  val immune get() = meds >= 2
  val healthy get() = viruses == 0
  fun enc(): String = organ.toString() + ":" + atts.joinToString(",")
}

private fun decSlot(s: String): Slot {
  val p = s.split(":")
  val organ = p[0].toIntOrNull() ?: 0
  val atts = if (p.size > 1 && p[1].isNotEmpty()) p[1].split(",").mapNotNull { it.toIntOrNull() } else emptyList()
  return Slot(organ, atts)
}

/**
 * Regla de infección: el VIRUS ARCOÍRIS (color 4) infecta un órgano de CUALQUIER color,
 * y cualquier virus puede infectar al órgano comodín. Un virus normal solo infecta su color.
 */
internal fun virusMatches(organ: Int, virus: Int): Boolean {
  val o = vColor(organ); val v = vColor(virus)
  return v == 4 || o == 4 || o == v
}

private fun isMultiVirus(card: Int) = vType(card) == 1 && vColor(card) == 4
/** Las tres cartas arcoíris: cuerpo (órgano) multicolor, virus multicolor y medicina multicolor. */
private fun isRainbowCard(card: Int) = vType(card) in 0..2 && vColor(card) == 4
private fun rainbowTitle(card: Int) = when (vType(card)) {
  0 -> "🌈 ¡Cuerpo arcoíris! Cuenta como cualquier color"
  1 -> "🌈 ¡Virus arcoíris! Infecta cualquier órgano"
  else -> "🌈 ¡Medicina arcoíris! Cura cualquier órgano"
}

private val RAINBOW = listOf(Color(0xFFFF3B30), Color(0xFFFF9500), Color(0xFFFFD60A), Color(0xFF34C759), Color(0xFF32ADE6), Color(0xFFAF52DE), Color(0xFFFF3B30))

private fun hasWon(body: List<Slot>): Boolean {
  val healthy = body.filter { it.healthy }
  return healthy.map { vColor(it.organ) }.distinct().size >= 4
}

private fun vCardDrawable(card: Int): Int {
  val t = vType(card)
  val col = vColor(card)
  return when (t) {
    0 -> when (col) {
      0 -> com.example.R.drawable.card_15_organo_corazon
      1 -> com.example.R.drawable.card_12_organo_estomago
      2 -> com.example.R.drawable.card_13_organo_cerebro
      3 -> com.example.R.drawable.card_14_organo_hueso
      else -> com.example.R.drawable.card_11_organo_cuerpo
    }
    1 -> when (col) {
      0 -> com.example.R.drawable.card_01_virus_rojo
      1 -> com.example.R.drawable.card_02_virus_verde
      2 -> com.example.R.drawable.card_03_virus_azul
      3 -> com.example.R.drawable.card_04_virus_amarillo
      else -> com.example.R.drawable.card_21_virus_multicolor
    }
    2 -> when (col) {
      0 -> com.example.R.drawable.card_06_medicina_botiquin
      1 -> com.example.R.drawable.card_07_medicina_jarabe
      2 -> com.example.R.drawable.card_08_medicina_pastillas
      3 -> com.example.R.drawable.card_09_medicina_vendas
      else -> com.example.R.drawable.card_10_medicina_jeringa
    }
    else -> when (col) {
      0 -> com.example.R.drawable.card_19_tratamiento_transplante
      1 -> com.example.R.drawable.card_20_tratamiento_ladron
      2 -> com.example.R.drawable.card_18_tratamiento_contagio
      3 -> com.example.R.drawable.card_16_tratamiento_intercambio
      else -> com.example.R.drawable.card_17_tratamiento_robo
    }
  }
}

// ===================== COMPONENTES VISUALES =====================

/** Dorso de carta dibujado: virus sonriente sobre rayos, a juego con las cartas del juego. */
@Composable
private fun VCardBack(modifier: Modifier = Modifier) {
  Canvas(modifier) {
    val c = center
    val r = size.width * 0.24f
    drawRect(Brush.radialGradient(listOf(Color(0xFF8A3FD1), Color(0xFF2A0F4A), Color(0xFF14082A)), center = c, radius = size.height * 0.75f))
    for (i in 0 until 14) {
      val a = i * (6.2831855f / 14f)
      drawLine(
        Color(0x33FFFFFF),
        Offset(c.x + cos(a) * r * 1.5f, c.y + sin(a) * r * 1.5f),
        Offset(c.x + cos(a) * size.height * 0.7f, c.y + sin(a) * size.height * 0.7f),
        strokeWidth = size.width * 0.025f
      )
    }
    for (i in 0 until 8) {
      val a = i * (6.2831855f / 8f) + 0.39f
      val e = Offset(c.x + cos(a) * r * 1.6f, c.y + sin(a) * r * 1.6f)
      drawLine(Color(0xFFB57BFF), Offset(c.x + cos(a) * r * 0.9f, c.y + sin(a) * r * 0.9f), e, strokeWidth = size.width * 0.045f)
      drawCircle(Color(0xFFE0C2FF), size.width * 0.05f, e)
    }
    drawCircle(
      Brush.radialGradient(listOf(Color(0xFFD2A2FF), Color(0xFF7A33C9)), center = Offset(c.x - r * 0.3f, c.y - r * 0.3f), radius = r * 1.4f),
      r, c
    )
    drawCircle(Color(0xFF1A0833), r * 0.10f, Offset(c.x - r * 0.35f, c.y - r * 0.15f))
    drawCircle(Color(0xFF1A0833), r * 0.10f, Offset(c.x + r * 0.35f, c.y - r * 0.15f))
    drawArc(
      Color.White, startAngle = 15f, sweepAngle = 150f, useCenter = false,
      topLeft = Offset(c.x - r * 0.5f, c.y - r * 0.1f), size = Size(r, r * 0.7f), style = Stroke(width = size.width * 0.03f)
    )
    val inset = size.width * 0.07f
    drawRoundRect(
      Color(0xAAFFFFFF), topLeft = Offset(inset, inset), size = Size(size.width - inset * 2, size.height - inset * 2),
      cornerRadius = CornerRadius(size.width * 0.10f), style = Stroke(width = size.width * 0.03f)
    )
  }
}

/** Efecto cristal arcoíris: tinte de colores que se desplaza, brillo superior, barrido de luz, borde y destellos. */
@Composable
private fun Modifier.rainbowGlass(on: Boolean, corner: Float = 0.14f): Modifier {
  if (!on) return this
  val phase = rememberInfiniteTransition(label = "rainbowGlass")
    .animateFloat(0f, 1f, infiniteRepeatable(tween(3600, easing = LinearEasing), RepeatMode.Restart), label = "rainbowGlassPhase")
  return this.drawWithContent {
    drawContent()
    val w = size.width; val h = size.height
    val ph = phase.value
    val r = CornerRadius(w * corner)
    // tinte de cristal
    drawRect(Brush.linearGradient(RAINBOW.map { it.copy(alpha = 0.26f) }, start = Offset(w * (ph * 2f - 1f), 0f), end = Offset(w * ph * 2f, h), tileMode = TileMode.Mirror))
    // brillo de vidrio en la parte alta
    drawRect(Brush.verticalGradient(listOf(Color(0x66FFFFFF), Color.Transparent), endY = h * 0.42f))
    // barrido de luz
    val x = w * (ph * 2.4f - 0.7f)
    drawRect(Brush.linearGradient(listOf(Color.Transparent, Color(0x88FFFFFF), Color.Transparent), start = Offset(x - w * 0.35f, 0f), end = Offset(x + w * 0.35f, h * 0.7f)))
    // borde de arcoíris
    drawRoundRect(Brush.linearGradient(RAINBOW, start = Offset(w * (ph * 2f - 1f), 0f), end = Offset(w * ph * 2f, h), tileMode = TileMode.Mirror), cornerRadius = r, style = Stroke(width = w * 0.07f))
    // destellos
    for (k in 0 until 4) {
      val a = (sin((ph * 6.2831855f) + k * 1.7f) * 0.5f + 0.5f)
      val cx = w * (0.2f + 0.6f * ((k * 37) % 100) / 100f)
      val cy = h * (0.15f + 0.7f * ((k * 61) % 100) / 100f)
      drawCircle(Color.White.copy(alpha = 0.85f * a), w * 0.028f * (0.6f + a), Offset(cx, cy))
      drawLine(Color.White.copy(alpha = 0.6f * a), Offset(cx - w * 0.06f * a, cy), Offset(cx + w * 0.06f * a, cy), strokeWidth = 1.5f)
      drawLine(Color.White.copy(alpha = 0.6f * a), Offset(cx, cy - w * 0.06f * a), Offset(cx, cy + w * 0.06f * a), strokeWidth = 1.5f)
    }
  }
}

@Composable
private fun VCard(
  card: Int,
  w: Dp = 64.dp,
  selected: Boolean = false,
  hidden: Boolean = false,
  marked: Boolean = false,
  glow: Boolean = false,
  onClick: (() -> Unit)? = null
) {
  val h = w * 1.45f
  val shape = RoundedCornerShape(w * 0.14f)
  val lift by animateFloatAsState(if (selected) -22f else 0f, spring(0.6f, 420f), label = "vLift")
  val grow by animateFloatAsState(if (selected) 1.08f else 1f, spring(0.6f, 420f), label = "vGrow")
  val multi = !hidden && isRainbowCard(card)
  val rim = when {
    marked -> Color(0xFFFF4D6D)
    selected -> GoldYellow
    glow -> Color(0xFFFFE08A)
    else -> Color(0x99000000)
  }
  Box(
    modifier = Modifier
      .graphicsLayer { translationY = lift * density; scaleX = grow; scaleY = grow }
      .size(w, h)
      .shadow(if (selected) 14.dp else 4.dp, shape, clip = false, spotColor = if (selected) GoldYellow else Color.Black)
      .clip(shape)
      .border(BorderStroke(if (selected || glow || marked) 3.dp else 2.dp, rim), shape)
      .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
    contentAlignment = Alignment.Center
  ) {
    if (hidden) {
      VCardBack(Modifier.matchParentSize())
    } else {
      Image(
        painter = painterResource(id = vCardDrawable(card)),
        contentDescription = when (vType(card)) { 0 -> V_ORGAN_NAME[vColor(card)]; 1 -> if (multi) "Virus arcoíris" else "Virus"; 2 -> if (multi) "Medicina arcoíris" else "Medicina"; else -> V_TREAT_NAME[vColor(card)] },
        modifier = Modifier.matchParentSize().rainbowGlass(multi),
        contentScale = ContentScale.FillBounds
      )
    }
    if (marked) {
      Box(Modifier.matchParentSize().background(Color(0x66FF1744)), contentAlignment = Alignment.Center) {
        Text("✖", color = Color.White, fontWeight = FontWeight.Black, fontSize = (w.value * 0.4f).sp)
      }
    }
  }
}

/** Carta pequeña (virus / medicina) que se apila junto a un órgano; entra con un rebote. */
@Composable
private fun VMini(card: Int, w: Dp, modifier: Modifier = Modifier) {
  val shape = RoundedCornerShape(w * 0.16f)
  val s = remember { Animatable(0f) }
  LaunchedEffect(Unit) { s.animateTo(1f, spring(0.45f, 300f)) }
  val rim = if (vType(card) == 1) Color(0xFFFF2D55) else Color(0xFF34C759)
  Box(
    modifier = modifier
      .size(w, w * 1.45f)
      .graphicsLayer { scaleX = s.value; scaleY = s.value; rotationZ = (1f - s.value) * 25f }
      .shadow(3.dp, shape, clip = false)
      .clip(shape)
      .border(BorderStroke(1.5.dp, rim), shape)
  ) {
    Image(painterResource(id = vCardDrawable(card)), contentDescription = null, modifier = Modifier.matchParentSize().rainbowGlass(isRainbowCard(card), 0.16f), contentScale = ContentScale.FillBounds)
  }
}

/** Órgano con sus cartas pegadas: halo rojo si está infectado, cian si está inmunizado, dorado si es objetivo válido. */
@Composable
private fun VBodySlot(slot: Slot, w: Dp, picked: Boolean, glow: Boolean, pulse: State<Float>, onClick: (() -> Unit)?) {
  Box(Modifier.width(w).height(w * 1.45f)) {
    VCard(slot.organ, w, selected = picked, glow = glow, onClick = onClick)
    val auraColor = when {
      slot.immune -> Color(0xFF37E6FF)
      !slot.healthy -> Color(0xFFFF2D55)
      glow -> Color(0xFFFFD166)
      else -> Color.Transparent
    }
    if (auraColor != Color.Transparent) {
      Box(Modifier.matchParentSize().drawBehind {
        drawRoundRect(
          auraColor.copy(alpha = 0.25f + 0.55f * pulse.value), cornerRadius = CornerRadius(size.width * 0.14f),
          style = Stroke(width = 3.dp.toPx())
        )
      })
    }
    slot.atts.forEachIndexed { i, a ->
      VMini(a, w * 0.5f, Modifier.align(Alignment.TopEnd).offset(x = w * 0.08f, y = (w * 0.26f) * i))
    }
    if (slot.immune) Text("🛡️", fontSize = 13.sp, modifier = Modifier.align(Alignment.BottomStart).padding(2.dp))
  }
}

/** Mano rival: dorsos en abanico con un balanceo lentísimo, como si viéramos sus manos. */
@Composable
private fun VOpponentFan(count: Int, seed: Int, compact: Boolean = false) {
  if (count <= 0) return
  val cw = if (compact) 15.dp else 22.dp
  val step = if (compact) 7.dp else 11.dp
  val t = rememberInfiniteTransition(label = "fan")
  val ph = t.animateFloat(0f, 6.2831855f, infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Restart), label = "fanPh")
  Box(Modifier.width(cw + step * (count - 1)).height(cw * 1.45f + 10.dp)) {
    repeat(count) { i ->
      Box(
        Modifier
          .offset(x = step * i, y = 5.dp)
          .graphicsLayer {
            val mid = (count - 1) / 2f
            transformOrigin = TransformOrigin(0.5f, 1f)
            rotationZ = (i - mid) * 7f + sin(ph.value + i * 1.1f + seed) * 2.6f
            translationY = (abs(i - mid) * 1.2f + cos(ph.value * 1f + i * 0.8f + seed) * 1.6f) * density
          }
          .size(cw, cw * 1.45f)
          .shadow(2.dp, RoundedCornerShape(4.dp), clip = false)
          .clip(RoundedCornerShape(4.dp))
          .border(1.dp, Color(0x99FFFFFF), RoundedCornerShape(4.dp))
      ) { VCardBack(Modifier.matchParentSize()) }
    }
  }
}

/** Cabecera de cada jugador: avatar, nombre, cartas en mano, progreso y su mano en abanico. */
@Composable
private fun VSeatHeader(
  name: String, isBot: Boolean, isMe: Boolean, active: Boolean, handCount: Int, healthyColors: Int,
  pulse: State<Float>, seed: Int, targetGlow: Boolean, onClick: (() -> Unit)?, compact: Boolean = false
) {
  val shape = RoundedCornerShape(16.dp)
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(shape)
      .background(if (active) Brush.horizontalGradient(listOf(Color(0x33FFD166), Color(0x0AFFD166))) else Brush.horizontalGradient(listOf(Color(0x22FFFFFF), Color(0x08FFFFFF))))
      .drawBehind {
        if (active || targetGlow) drawRoundRect(
          Color(0xFFFFD166).copy(alpha = 0.35f + 0.5f * pulse.value), cornerRadius = CornerRadius(16.dp.toPx()), style = Stroke(width = 1.5.dp.toPx())
        )
      }
      .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
      .padding(horizontal = 8.dp, vertical = 5.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    Box(
      Modifier.size(30.dp).clip(CircleShape).background(
        Brush.linearGradient(if (isBot) listOf(Color(0xFF5C6BC0), Color(0xFF283593)) else listOf(Color(0xFFFF4D8D), Color(0xFF9D6BFF)))
      ),
      contentAlignment = Alignment.Center
    ) { Text(if (isBot) "🤖" else (name.firstOrNull()?.uppercase() ?: "?"), color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp) }
    Column(Modifier.weight(1f)) {
      Text(
        "$name${if (isMe) " (tú)" else ""}${if (active) "  ▶" else ""}",
        color = if (active) GoldYellow else Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
      )
      Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        for (k in 0 until 4) {
          Box(Modifier.size(7.dp).clip(CircleShape).background(if (k < healthyColors) Color(0xFF3DDC84) else Color(0x33FFFFFF)))
        }
        Text("  🃏 $handCount", color = Color(0xFFD7CCEB), fontSize = 11.sp)
      }
    }
    if (!isMe) VOpponentFan(handCount, seed, compact)
  }
}

/** Mazo (con contador), mensaje de la última jugada y pila de descarte. */
@Composable
private fun VPiles(deckCount: Int, top: Int?, msg: String) {
  Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    Box(Modifier.width(52.dp).height(60.dp)) {
      repeat(3) { i ->
        Box(
          Modifier.offset(x = (i * 2).dp, y = (-i * 2).dp).size(36.dp, 52.dp).padding(top = 6.dp)
            .clip(RoundedCornerShape(6.dp)).border(1.dp, Color(0x99FFFFFF), RoundedCornerShape(6.dp))
        ) { VCardBack(Modifier.matchParentSize()) }
      }
      Text("$deckCount", color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp,
        modifier = Modifier.align(Alignment.BottomEnd).clip(RoundedCornerShape(50)).background(Color(0xCC000000)).padding(horizontal = 6.dp, vertical = 1.dp))
    }
    Box(
      Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(Color(0x55000000))
        .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(14.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
      contentAlignment = Alignment.Center
    ) { Text(msg, color = Color(0xFFEDE7F6), fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis) }
    Box(Modifier.size(38.dp, 55.dp), contentAlignment = Alignment.Center) {
      if (top != null) VCard(top, 38.dp)
      else Box(Modifier.matchParentSize().border(1.5.dp, Color(0x44FFFFFF), RoundedCornerShape(6.dp)))
    }
  }
}

/**
 * Transición al jugar una carta: sale de la mano de quien la jugó, crece al centro de la mesa,
 * se asienta y vuela a la pila de descarte desvaneciéndose. El virus arcoíris lanza ondas de colores.
 */
@Composable
private fun VFlyFx(card: Int, byMe: Boolean, trigger: Int, modifier: Modifier = Modifier) {
  val prog = remember { Animatable(1f) }
  val ctx = LocalContext.current
  LaunchedEffect(trigger) {
    if (trigger > 0 && card >= 0) {
      com.example.audio.GameSfx.play(ctx, "sfx_card", 0.7f)
      prog.snapTo(0f)
      prog.animateTo(1f, tween(1800, easing = LinearEasing))
    }
  }
  val p = prog.value
  if (p >= 1f) return
  val multi = isRainbowCard(card)
  BoxWithConstraints(modifier) {
    val originY = if (byMe) maxHeight * 0.6f else -maxHeight * 0.6f
    val flyX = maxWidth * 0.32f
    val flyY = -maxHeight * 0.42f
    if (multi) {
      Canvas(Modifier.matchParentSize()) {
        val b = ((p - 0.25f) / 0.5f).coerceIn(0f, 1f)
        if (b > 0f && b < 1f) {
          RAINBOW.forEachIndexed { i, col ->
            drawCircle(col.copy(alpha = 0.6f * (1f - b)), size.minDimension * (0.16f + 0.6f * b) + i * 7f, center, style = Stroke(width = 5f))
          }
          // chispas que salen disparadas del centro
          for (k in 0 until 18) {
            val ang = k * (6.2831855f / 18f)
            val d = size.minDimension * (0.10f + 0.55f * b)
            drawCircle(RAINBOW[k % 6].copy(alpha = 0.9f * (1f - b)), 5f * (1f - b) + 2f, Offset(center.x + cos(ang) * d, center.y + sin(ang) * d))
          }
        }
      }
    }
    Column(
      Modifier
        .align(Alignment.Center)
        .graphicsLayer {
          val a = FastOutSlowInEasing.transform((p / 0.32f).coerceIn(0f, 1f))
          val settle = ((p - 0.32f) / 0.22f).coerceIn(0f, 1f)
          val out = FastOutSlowInEasing.transform(((p - 0.80f) / 0.20f).coerceIn(0f, 1f))
          translationY = originY.toPx() * (1f - a) + flyY.toPx() * out
          translationX = flyX.toPx() * out
          val sc = 0.45f + 0.75f * a - 0.12f * settle - 0.55f * out
          scaleX = sc; scaleY = sc
          rotationZ = (1f - a) * (if (byMe) -14f else 14f) + sin(p * 9f) * 1.5f * (1f - out)
          alpha = (p / 0.08f).coerceIn(0f, 1f) * (1f - out)
          cameraDistance = 16f * density
          rotationX = (1f - a) * 25f
        },
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      VCard(card, 104.dp)
      if (multi) {
        Spacer(Modifier.height(6.dp))
        Text(rainbowTitle(card), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, textAlign = TextAlign.Center,
          modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0xCC000000)).padding(horizontal = 10.dp, vertical = 4.dp))
      }
    }
  }
}

/** Valida visualmente (para resaltar objetivos) si la carta seleccionada puede ir sobre ese órgano. */
private fun vTargetable(card: Int, me: Int, p: Int, slot: Slot): Boolean {
  val col = vColor(card)
  return when (vType(card)) {
    1 -> p != me && !slot.immune && virusMatches(slot.organ, card)
    2 -> !slot.immune && (col == 4 || vColor(slot.organ) == col)
    3 -> when (col) { 0 -> !slot.immune; 1 -> p != me && !slot.immune; 3, 4 -> p != me; else -> false }
    else -> false
  }
}

/** Carta de la mano: entra repartida desde abajo, se abre en abanico y se levanta al seleccionarla. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VHandCard(
  card: Int, w: Dp, index: Int, count: Int, selected: Boolean, marked: Boolean, enabled: Boolean,
  onClick: () -> Unit, onLong: () -> Unit
) {
  val enter = remember { Animatable(0f) }
  LaunchedEffect(Unit) {
    delay(index * 90L)
    enter.animateTo(1f, spring(0.7f, 260f))
  }
  Box(
    Modifier
      .padding(horizontal = 3.dp)
      .graphicsLayer {
        val mid = (count - 1) / 2f
        transformOrigin = TransformOrigin(0.5f, 1.3f)
        rotationZ = if (selected) 0f else (index - mid) * 5f
        translationY = (1f - enter.value) * 170f * density + (if (selected) 0f else abs(index - mid) * 4f * density)
        alpha = enter.value.coerceIn(0f, 1f)
      }
      .combinedClickable(enabled = enabled, onClick = onClick, onLongClick = onLong)
  ) { VCard(card, w, selected = selected, marked = marked) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun VirusGame(c: GameCtx) {
  val flow = remember { c.vm.gameState("virus") }
  val st by flow.collectAsState(initial = null)
  fun ints(k: String) = (st?.get(k) as? List<*>)?.mapNotNull { (it as? Number)?.toInt() } ?: emptyList()
  fun strs(k: String) = (st?.get(k) as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
  val started = (st?.get("deck") as? List<*>) != null && strs("seats").isNotEmpty()
  val seats = strs("seats")
  val n = seats.size
  val deck = ints("deck")
  val disc = ints("disc")
  val hands = (0 until n).map { ints("h$it") }
  val bodies = (0 until n).map { strs("b$it").map { s -> decSlot(s) } }
  val turn = (st?.get("turn") as? Number)?.toInt() ?: 0
  val winner = (st?.get("winner") as? Number)?.toInt() ?: -1
  val msg = (st?.get("msg") as? String) ?: ""
  // Última carta jugada (para la animación de transición en todos los teléfonos).
  val lastCard = (st?.get("lc") as? Number)?.toInt() ?: -1
  val lastBy = (st?.get("lb") as? Number)?.toInt() ?: -1
  val lastSeq = (st?.get("ls") as? Number)?.toLong() ?: 0L
  val myIdx = seats.indexOf(c.me)
  val myTurn = started && winner < 0 && turn == myIdx
  var lastTurnForSound by remember { mutableStateOf<Int?>(null) }
  val turnSoundContext = LocalContext.current
  LaunchedEffect(myTurn, turn, started, winner) {
    if (myTurn && started && winner < 0 && lastTurnForSound != turn) com.example.audio.GameSfx.play(turnSoundContext, "sfx_countdown_tick", 0.32f)
    lastTurnForSound = if (myTurn) turn else null
  }
  var sel by remember { mutableStateOf(-1) }
  // Seleccionar por posición, no por código de carta: el mazo contiene copias
  // del mismo tipo/color y compararlas solo por valor marcaba varias a la vez.
  var selIndex by remember { mutableStateOf(-1) }
  val picks = remember { mutableStateListOf<Pair<Int, Int>>() }
  val discardSel = remember { mutableStateListOf<Int>() }
  var botsSel by remember { mutableStateOf(1) }
  LaunchedEffect(turn, winner, hands.getOrNull(myIdx)?.size) { sel = -1; selIndex = -1; picks.clear(); discardSel.clear() }

  // Latido compartido: halos de turno, objetivos válidos y órganos infectados respiran juntos.
  val pulseT = rememberInfiniteTransition(label = "vPulse")
  val pulse = pulseT.animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "vPulseValue")

  // Disparo de la animación "carta jugada": solo con jugadas nuevas, nunca al entrar a una partida en curso.
  var fxTrigger by remember { mutableStateOf(0) }
  var fxCard by remember { mutableStateOf(-1) }
  var fxByMe by remember { mutableStateOf(false) }
  var seenSeq by remember { mutableStateOf<Long?>(null) }
  LaunchedEffect(lastSeq, started) {
    if (!started) return@LaunchedEffect
    val seen = seenSeq
    if (seen == null) {
      seenSeq = lastSeq
    } else if (lastSeq != seen) {
      seenSeq = lastSeq
      if (lastCard >= 0) { fxCard = lastCard; fxByMe = lastBy == myIdx; fxTrigger += 1 }
    }
  }

  fun save(h: List<List<Int>>, b: List<List<Slot>>, d: List<Int>, dc: List<Int>, t: Int, w: Int, m: String, lc: Int = -1, lb: Int = -1) {
    val map = mutableMapOf<String, Any?>(
      "seats" to seats, "deck" to d, "disc" to dc, "turn" to t, "winner" to w, "msg" to m,
      "lc" to lc, "lb" to lb, "ls" to System.currentTimeMillis()
    )
    for (i in 0 until n) { map["h$i"] = h[i]; map["b$i"] = b[i].map { it.enc() } }
    c.vm.publishGame("virus", map)
  }

  fun refill(h: MutableList<MutableList<Int>>, d: MutableList<Int>, dc: MutableList<Int>, who: Int) {
    while (h[who].size < 3) {
      if (d.isEmpty()) { if (dc.isEmpty()) break; d.addAll(dc.shuffled()); dc.clear() }
      h[who].add(d.removeAt(0))
    }
  }

  fun finish(h: MutableList<MutableList<Int>>, b: List<List<Slot>>, d: MutableList<Int>, dc: MutableList<Int>, who: Int, text: String, played: Int = -1) {
    refill(h, d, dc, who)
    val win = (0 until n).firstOrNull { hasWon(b[it]) } ?: -1
    var next = (who + 1) % n
    // Quien se quedó sin cartas (guante de látex) solo roba 3 y pasa.
    var guard = 0
    while (win < 0 && h[next].isEmpty() && guard < n) {
      refill(h, d, dc, next); next = (next + 1) % n; guard++
    }
    save(h, b, d, dc, next, win, if (win >= 0) "🏆 ${if (seats[win] == "bot") "Un bot" else c.nameOf(seats[win])} completó su cuerpo" else text, played, who)
    if (win >= 0) c.vm.gameSfx(2)
    sel = -1; selIndex = -1; picks.clear()
  }

  fun who(i: Int) = if (seats[i] == "bot") "Bot ${i - 1}" else c.nameOf(seats[i])

  // Ejecuta la carta `card` jugada por `me` sobre los objetivos; devuelve false si no es válida.
  fun play(me: Int, card: Int, targets: List<Pair<Int, Int>>): Boolean {
    val h = hands.map { it.toMutableList() }.toMutableList()
    val b = bodies.map { it.toMutableList() }
    val d = deck.toMutableList(); val dc = disc.toMutableList()
    val t = vType(card); val col = vColor(card)
    h[me].remove(card)
    var text = "${who(me)} jugó ${when (t) { 0 -> V_ORGAN_NAME[col]; 1 -> if (isMultiVirus(card)) "el Virus arcoíris 🌈" else "Virus"; 2 -> "Medicina"; else -> V_TREAT_NAME[col] }}"
    when (t) {
      0 -> {
        if (b[me].any { vColor(it.organ) == col }) return false
        b[me].add(Slot(card, emptyList()))
      }
      1 -> {
        val tg = targets.firstOrNull() ?: return false
        if (tg.first == me) return false
        val s = b[tg.first].getOrNull(tg.second) ?: return false
        // El virus arcoíris infecta cualquier órgano sin importar el color.
        if (s.immune || !virusMatches(s.organ, card)) return false
        if (s.viruses >= 1) {
          dc.add(s.organ); dc.addAll(s.atts); dc.add(card); b[tg.first].removeAt(tg.second); text += " y destruyó un órgano"
        } else if (s.meds == 1) {
          val m = s.atts.first { vType(it) == 2 }
          dc.add(m); dc.add(card); b[tg.first][tg.second] = Slot(s.organ, s.atts - m)
        } else b[tg.first][tg.second] = Slot(s.organ, s.atts + card)
      }
      2 -> {
        val tg = targets.firstOrNull() ?: return false
        val targetPlayer = tg.first
        val s = b[targetPlayer].getOrNull(tg.second) ?: return false
        // La medicina multicolor (24) puede colocarse sobre órganos de cualquier color.
        // Las medicinas normales solo coinciden con su color; dos medicinas inmunizan el órgano.
        if (s.immune || (col != 4 && vColor(s.organ) != col)) return false
        if (s.viruses >= 1) {
          val v = s.atts.first { vType(it) == 1 }
          dc.add(v); dc.add(card); b[targetPlayer][tg.second] = Slot(s.organ, s.atts - v)
        } else b[targetPlayer][tg.second] = Slot(s.organ, s.atts + card)
      }
      else -> when (col) {
        0 -> { // trasplante
          if (targets.size < 2) return false
          val (a, bb) = targets
          if (a.first == bb.first) return false
          val sa = b[a.first].getOrNull(a.second) ?: return false
          val sb = b[bb.first].getOrNull(bb.second) ?: return false
          if (sa.immune || sb.immune) return false
          if (b[a.first].any { it !== sa && vColor(it.organ) == vColor(sb.organ) }) return false
          if (b[bb.first].any { it !== sb && vColor(it.organ) == vColor(sa.organ) }) return false
          b[a.first][a.second] = sb; b[bb.first][bb.second] = sa
        }
        1 -> { // ladrón de órganos
          val tg = targets.firstOrNull() ?: return false
          if (tg.first == me) return false
          val s = b[tg.first].getOrNull(tg.second) ?: return false
          if (s.immune || b[me].any { vColor(it.organ) == vColor(s.organ) }) return false
          b[tg.first].removeAt(tg.second); b[me].add(s)
        }
        2 -> { // contagio automático
          var moved = 0
          for (i in b[me].indices) {
            val s = b[me][i]
            for (v in s.atts.filter { vType(it) == 1 }) {
              var done = false
              for (p in 0 until n) {
                if (p == me || done) continue
                for (j in b[p].indices) {
                  val o = b[p][j]
                  if (o.atts.isEmpty() && virusMatches(o.organ, v)) {
                    b[p][j] = Slot(o.organ, listOf(v))
                    b[me][i] = Slot(b[me][i].organ, b[me][i].atts - v)
                    moved++; done = true; break
                  }
                }
              }
            }
          }
          text += " ($moved virus movidos)"
        }
        3 -> { // guante de látex: intercambia todo el cuerpo con un rival elegido.
          val tg = targets.firstOrNull() ?: return false
          val targetPlayer = tg.first
          if (targetPlayer == me || targetPlayer !in 0 until n) return false
          // El intercambio completo no se permite si alguno de los cuerpos tiene un órgano inmune.
          if (b[me].any { it.immune } || b[targetPlayer].any { it.immune }) return false
          val mine = b[me].toList()
          b[me].clear(); b[me].addAll(b[targetPlayer])
          b[targetPlayer].clear(); b[targetPlayer].addAll(mine)
          text += " e intercambió todos los órganos con ${who(targetPlayer)}"
        }
        else -> { // error médico
          val tg = targets.firstOrNull() ?: return false
          if (tg.first == me) return false
          val mine = b[me].toList(); b[me].clear(); b[me].addAll(b[tg.first]); b[tg.first].clear(); b[tg.first].addAll(mine)
        }
      }
    }
    if (t != 0 && !(t == 1 || t == 2)) dc.add(card) else if (t == 0) Unit
    finish(h, b, d, dc, me, text, card)
    return true
  }

  fun discard(me: Int, cards: List<Int>) {
    val h = hands.map { it.toMutableList() }.toMutableList()
    val dc = disc.toMutableList()
    cards.forEach { h[me].remove(it); dc.add(it) }
    finish(h, bodies.map { it.toList() }, deck.toMutableList(), dc, me, "${who(me)} descartó ${cards.size} carta(s)", cards.firstOrNull() ?: -1)
  }

  fun start() {
    val seatList = c.players.take(6).toMutableList()
    repeat(maxOf(botsSel, 2 - seatList.size).coerceAtMost(6 - seatList.size)) { seatList.add("bot") }
    val d = virusDeck().toMutableList()
    val map = mutableMapOf<String, Any?>("seats" to seatList, "disc" to emptyList<Int>(), "turn" to 0, "winner" to -1, "msg" to "¡Empieza el contagio!", "lc" to -1, "lb" to -1, "ls" to 0L)
    for (i in seatList.indices) { map["h$i"] = List(3) { d.removeAt(0) }; map["b$i"] = emptyList<String>() }
    map["deck"] = d
    c.vm.publishGame("virus", map)
    c.vm.gameSfx(1)
  }

  // Bots: los mueve el teléfono anfitrión con una estrategia simple pero válida.
  LaunchedEffect(turn, winner, started, msg) {
    if (!started || winner >= 0 || seats.getOrNull(turn) != "bot" || c.me != c.firstUid) return@LaunchedEffect
    delay(1600)
    val me = turn
    val hand = hands[me]
    // 1) órgano nuevo, 2) medicina útil, 3) virus a un rival, 4) descartar
    for (card in hand.filter { vType(it) == 0 }) if (play(me, card, emptyList())) return@LaunchedEffect
    for (card in hand.filter { vType(it) == 2 }) for (j in bodies[me].indices) if (play(me, card, listOf(Pair(me, j)))) return@LaunchedEffect
    for (card in hand.filter { vType(it) == 1 }) for (p in 0 until n) if (p != me) for (j in bodies[p].indices) if (play(me, card, listOf(Pair(p, j)))) return@LaunchedEffect
    for (card in hand.filter { vType(it) == 3 && vColor(it) == 2 }) if (play(me, card, emptyList())) return@LaunchedEffect
    discard(me, hand.take(if (hand.size > 2) 2 else hand.size).ifEmpty { return@LaunchedEffect })
  }

  // ---------------- Sala previa ----------------
  if (!started) {
    GlassCard(modifier = Modifier.fillMaxWidth(), highlight = true) {
      Text("🦠 VIRUS!", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
      Spacer(Modifier.height(4.dp))
      Text("De 2 a 6 jugadores. Reúne 4 órganos sanos de distinto color. Juega una carta o descarta y roba hasta 3. Puedes usar medicina para curar también a otro jugador; dos medicinas inmunizan un órgano.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
      Spacer(Modifier.height(14.dp))
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.graphicsLayer { translationY = (pulse.value - 0.5f) * 10f * density; rotationZ = -5f }) { VCard(14, 84.dp) }
        Box(Modifier.graphicsLayer { translationY = (0.5f - pulse.value) * 10f * density; rotationZ = 4f }) { VCard(24, 84.dp) }
        Box(Modifier.graphicsLayer { translationY = (pulse.value - 0.5f) * 8f * density; rotationZ = -3f }) { VCard(4, 84.dp) }
      }
      Spacer(Modifier.height(10.dp))
      Text("🌈 El Virus arcoíris infecta cualquier órgano, sin importar su color. La Medicina multicolor cura o protege a cualquiera, y el Órgano comodín cuenta como cuerpo de cualquier color.", color = GoldYellow, fontSize = 11.sp, lineHeight = 15.sp)
    }
    Spacer(Modifier.height(10.dp))
    Text("Jugadores humanos: ${c.players.size.coerceAtMost(6)} · bots hasta completar 6", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
    Spacer(Modifier.height(4.dp))
    Text("Bots en la mesa", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      listOf(0, 1, 2, 3, 4).forEach { k -> if (botsSel == k) GradientButton("$k", onClick = { botsSel = k }, height = 38.dp) else SoftButton("$k bots", onClick = { botsSel = k }) }
    }
    Spacer(Modifier.height(10.dp))
    GradientButton("Repartir cartas", modifier = Modifier.fillMaxWidth(), onClick = { start() })
    return
  }

  // ---------------- Mesa ----------------
  Box(Modifier.fillMaxWidth()) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .graphicsLayer { rotationX = 4f; cameraDistance = 18f * density }
        .shadow(10.dp, RoundedCornerShape(24.dp), clip = false)
        .clip(RoundedCornerShape(24.dp))
        .background(Brush.verticalGradient(listOf(Color(0xFF8B5E34), Color(0xFF4A2E16))))
        .padding(5.dp)
        .clip(RoundedCornerShape(20.dp))
        .background(Brush.radialGradient(listOf(Color(0xFF16705A), Color(0xFF0B3F31), Color(0xFF062219)), radius = 900f))
        .padding(8.dp)
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        VPiles(deck.size, disc.lastOrNull(), msg)
        val compact = n >= 3
        @Composable
        fun SeatBlock(p: Int, seatModifier: Modifier) {
          Column(seatModifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
          val active = p == turn && winner < 0
          val wholeBodyTarget = myTurn && sel >= 0 && vType(sel) == 3 && (vColor(sel) == 3 || vColor(sel) == 4) && p != myIdx
          VSeatHeader(
            name = who(p), isBot = seats[p] == "bot", isMe = p == myIdx, active = active, handCount = hands[p].size,
            healthyColors = bodies[p].filter { it.healthy }.map { vColor(it.organ) }.distinct().size.coerceAtMost(4),
            pulse = pulse, seed = p * 3, targetGlow = wholeBodyTarget,
            onClick = if (wholeBodyTarget) ({ picks.clear(); picks.add(Pair(p, 0)); Unit }) else null,
            compact = compact
          )
          BoxWithConstraints(Modifier.fillMaxWidth()) {
            val visibleOrgans = bodies[p].size.coerceAtLeast(1)
            val gapW = if (compact) 6.dp else 10.dp
            val cardWidth = ((maxWidth - gapW * (visibleOrgans - 1) - 10.dp) / visibleOrgans).coerceIn(if (compact) 22.dp else 34.dp, 58.dp)
            Row(modifier = Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(gapW)) {
              if (bodies[p].isEmpty()) {
                Box(Modifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(10.dp)).border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                  Text("— cuerpo vacío —", color = Color(0x99FFFFFF), fontSize = 12.sp)
                }
              }
              bodies[p].forEachIndexed { j, slot ->
                VBodySlot(
                  slot = slot, w = cardWidth, picked = picks.contains(Pair(p, j)),
                  glow = myTurn && sel >= 0 && vTargetable(sel, myIdx, p, slot), pulse = pulse,
                  onClick = {
                    if (myTurn && sel >= 0) { if (picks.contains(Pair(p, j))) picks.remove(Pair(p, j)) else if (picks.size < 2) picks.add(Pair(p, j)) }
                  }
                )
              }
            }
          }
          }
        }
        // Con 3 o más jugadores los asientos se acomodan en dos columnas: se ve toda la mesa sin deslizar.
        if (!compact) {
          for (p in 0 until n) SeatBlock(p, Modifier.fillMaxWidth())
        } else {
          (0 until n).chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              pair.forEach { p -> SeatBlock(p, Modifier.weight(1f)) }
              if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
          }
        }
      }
    }
    VFlyFx(fxCard, fxByMe, fxTrigger, Modifier.matchParentSize())
  }
  Spacer(Modifier.height(8.dp))
  if (winner >= 0) {
    ResultBanner("🏆 ¡Ganó ${who(winner)}!")
    Spacer(Modifier.height(6.dp))
    GradientButton("Nueva partida", modifier = Modifier.fillMaxWidth(), onClick = { start() })
    return
  }
  TurnBanner(if (myTurn) "Tu turno: juega una carta o descarta" else "Turno de ${who(turn)}…", myTurn)
  Spacer(Modifier.height(6.dp))
  if (myIdx >= 0) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
      val handSize = hands[myIdx].size.coerceAtLeast(1)
      val handCardWidth = ((maxWidth - 12.dp * handSize) / handSize).coerceIn(52.dp, 84.dp)
      Row(modifier = Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 8.dp), horizontalArrangement = Arrangement.Center) {
        hands[myIdx].forEachIndexed { index, card ->
          VHandCard(
            card = card, w = handCardWidth, index = index, count = hands[myIdx].size,
            selected = selIndex == index, marked = discardSel.contains(index), enabled = myTurn,
            onClick = {
              if (selIndex == index) { sel = -1; selIndex = -1 }
              else { sel = card; selIndex = index }
              picks.clear()
            },
            onLong = {
              if (discardSel.contains(index)) discardSel.remove(index)
              else if (discardSel.size < 3) discardSel.add(index)
              sel = -1; selIndex = -1; picks.clear()
            }
          )
        }
      }
    }
    if (myTurn) {
      Text("Toca una carta para jugarla; mantén presionadas hasta 3 para elegir el descarte.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
      Spacer(Modifier.height(6.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        // Un toque descarta una carta; mantener presionado descarta tres seleccionadas o completa hasta tres.
        Box(modifier = Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(14.dp))
          .background(Brush.horizontalGradient(listOf(Color(0xFF72501C), Color(0xFFB8862D))))
          .border(1.dp, Color(0x44FFFFFF), RoundedCornerShape(14.dp))
          .combinedClickable(
            enabled = hands[myIdx].isNotEmpty(),
            onClick = {
              val card = hands[myIdx].getOrNull(selIndex)
              if (card != null) discard(myIdx, listOf(card))
              else if (hands[myIdx].isNotEmpty()) discard(myIdx, listOf(hands[myIdx].first()))
            },
            onLongClick = {
              val hand = hands[myIdx]
              val chosen = if (discardSel.size >= 3) discardSel.sorted().take(3).mapNotNull { hand.getOrNull(it) }
                else (listOfNotNull(hand.getOrNull(selIndex)) + hand.filterIndexed { i, _ -> i != selIndex }).take(3)
              if (chosen.isNotEmpty()) discard(myIdx, chosen)
              discardSel.clear()
            }
          ), contentAlignment = Alignment.Center) {
          Text("Descartar una · mantener para 3", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 4.dp))
        }
      }
    }
    if (myTurn && sel >= 0) {
      val t = vType(sel)
      Spacer(Modifier.height(6.dp))
      Text(
        when (t) {
          0 -> "Órgano: se pone en tu cuerpo."
          1 -> if (isMultiVirus(sel)) "🌈 Virus arcoíris: toca CUALQUIER órgano rival, de cualquier color, para infectarlo." else "Virus: toca un órgano rival del mismo color (o comodín) para infectarlo."
          2 -> if (vColor(sel) == 4) "Medicina multicolor: toca cualquier órgano de cualquier color para curarlo o añadirle protección." else "Medicina: toca un órgano propio o de otro jugador del mismo color para curarlo."
          else -> V_TREAT_HELP[vColor(sel)] + when (vColor(sel)) { 0 -> " (toca dos órganos de jugadores distintos)"; 1 -> " (toca el órgano de un rival)"; 3, 4 -> " (toca el nombre o un órgano del rival)"; else -> "" }
        }, color = GoldYellow, fontSize = 12.sp
      )
      Spacer(Modifier.height(6.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        GradientButton("Jugar carta", modifier = Modifier.fillMaxWidth(), onClick = {
          if (!play(myIdx, sel, picks.toList())) c.vm.gameSfx(0)
        })
      }
    }
  }
}
