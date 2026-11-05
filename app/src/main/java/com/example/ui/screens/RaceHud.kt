package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.GradientButton
import com.example.ui.components.SoftButton
import com.example.ui.theme.GoldYellow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

// ───────────────────── HUD de carreras estilo arcade profesional ─────────────────────
// Placas inclinadas, velocímetro con degradé y luces de cambio, barra de nitro, semáforo de salida y panel de resultados.

/** Forma de placa inclinada (paralelogramo), típica de los HUD de carreras. */
internal val RaceSkew = GenericShape { sz, _ ->
  val k = sz.height * 0.30f
  moveTo(k, 0f); lineTo(sz.width, 0f); lineTo(sz.width - k, sz.height); lineTo(0f, sz.height); close()
}

internal fun ordinalEs(n: Int): String = "$n" + "º"

/** Placa inclinada: etiqueta pequeña arriba y valor grande en cursiva. */
@Composable
internal fun RacePlate(label: String, value: String, accent: Color, modifier: Modifier = Modifier, valueSize: TextUnit = 24.sp, sub: String? = null) {
  Box(
    modifier.clip(RaceSkew).background(Brush.horizontalGradient(listOf(Color(0xDD0B0818), Color(0x990B0818))))
      .border(1.dp, accent.copy(alpha = 0.75f), RaceSkew).padding(horizontal = 18.dp, vertical = 5.dp)
  ) {
    Column {
      Text(label, color = accent, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp, maxLines = 1)
      Row(verticalAlignment = Alignment.Bottom) {
        Text(value, color = Color.White, fontSize = valueSize, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, maxLines = 1)
        if (sub != null) Text(sub, color = Color(0xFFB9B0D0), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 3.dp, bottom = 3.dp), maxLines = 1)
      }
    }
  }
}

/** Factor de tamaño del velocímetro: 0,45 por lado = 80 % menos de superficie que el original (156×120 dp). */
private const val SPEEDO_SCALE = 0.45f

/** Velocímetro compacto: arco con degradé, velocidad digital y marcha. */
@Composable
internal fun RaceSpeedo(pct: Float, boost: Boolean, modifier: Modifier = Modifier) {
  val p = pct.coerceIn(0f, 1.3f)
  val kmh = (p * 240f).toInt()
  val gear = (1 + (p.coerceIn(0f, 1f) * 5.99f).toInt()).coerceIn(1, 6)
  val sc = SPEEDO_SCALE
  Box(modifier.size((156 * sc).dp, (120 * sc).dp), contentAlignment = Alignment.BottomCenter) {
    Canvas(Modifier.fillMaxSize()) {
      val c = Offset(size.width / 2f, size.height * 0.62f)
      val r = size.width * 0.40f
      val tl = Offset(c.x - r, c.y - r); val sz = Size(r * 2f, r * 2f)
      drawArc(Color(0x55FFFFFF), 150f, 240f, false, tl, sz, style = Stroke(16f * sc, cap = StrokeCap.Round))
      val frac = p.coerceIn(0f, 1f)
      rotate(150f, c) {
        drawArc(
          brush = Brush.sweepGradient(
            0f to Color(0xFF00E5FF), 0.33f to Color(0xFF39FF88), 0.52f to Color(0xFFFFD60A), 0.667f to Color(0xFFFF3B3B), 1f to Color(0xFFFF3B3B),
            center = c
          ),
          startAngle = 0f, sweepAngle = 240f * frac, useCenter = false, topLeft = tl, size = sz, style = Stroke(12f * sc, cap = StrokeCap.Round)
        )
      }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 2.dp)) {
      Text("$kmh", color = if (boost) Color(0xFF00E5FF) else Color.White, fontSize = 17.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic)
      Text("M$gear", color = Color(0xFFB9B0D0), fontSize = 7.sp, fontWeight = FontWeight.Bold)
    }
  }
}

/** Luces de cambio (pequeñas): se encienden de verde a rojo según la velocidad; en nitro todas se vuelven cian. */
@Composable
internal fun RaceShiftLights(pct: Float, boost: Boolean, modifier: Modifier = Modifier) {
  val lit = (pct.coerceIn(0f, 1f) * 10f).toInt()
  Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
    for (i in 0 until 10) {
      val col = if (boost) Color(0xFF00E5FF) else if (i < 4) Color(0xFF39FF88) else if (i < 7) Color(0xFFFFD60A) else Color(0xFFFF3B3B)
      Box(Modifier.size(width = 7.dp, height = 3.dp).clip(RoundedCornerShape(1.dp)).background(if (i < lit || (boost && pct > 0.05f)) col else Color(0x33FFFFFF)))
    }
  }
}

/** Barra horizontal de nitro con pulso cuando está activo. */
@Composable
internal fun RaceNitroBar(nitro: Float, active: Boolean, modifier: Modifier = Modifier) {
  val pulse by rememberInfiniteTransition(label = "nitro").animateFloat(0.65f, 1f, infiniteRepeatable(tween(220), RepeatMode.Reverse), label = "nitroPulse")
  Column(modifier) {
    Row(Modifier.fillMaxWidth()) {
      Text("NITRO", color = Color(0xFF00E5FF), fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
      Spacer(Modifier.weight(1f))
      Text("${(nitro.coerceIn(0f, 1f) * 100).toInt()}%", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
    Spacer(Modifier.height(2.dp))
    Box(Modifier.fillMaxWidth().height(11.dp).clip(RaceSkew).background(Color(0x99000000)).border(1.dp, Color(0x66FFFFFF), RaceSkew)) {
      Box(
        Modifier.fillMaxHeight().fillMaxWidth(nitro.coerceIn(0.02f, 1f)).graphicsLayer { alpha = if (active) pulse else 1f }
          .background(Brush.horizontalGradient(listOf(Color(0xFF00E5FF), Color(0xFF7C4DFF))))
      )
    }
  }
}

/** Semáforo de salida: 3 luces rojas que se van encendiendo y verde al arrancar, con el número «rebotando». */
@Composable
internal fun RaceCountdown(countdown: Float, modifier: Modifier = Modifier) {
  val cd = kotlin.math.ceil(countdown.toDouble()).toInt()
  val frac = countdown - floor(countdown)
  val go = cd <= 0
  val reds = if (go) 0 else (4 - cd).coerceIn(1, 3)
  Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
    Row(
      Modifier.clip(RoundedCornerShape(16.dp)).background(Color(0xCC000000)).border(1.dp, Color(0x44FFFFFF), RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 10.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      for (i in 0 until 3) {
        val col = if (go) Color(0xFF39FF88) else if (i < reds) Color(0xFFFF2E4D) else Color(0xFF3A1F2A)
        Box(Modifier.size(28.dp).clip(CircleShape).background(col))
      }
    }
    Spacer(Modifier.height(6.dp))
    Text(
      if (go) "¡YA!" else "$cd", color = if (go) Color(0xFF39FF88) else Color.White, fontSize = 92.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic,
      modifier = Modifier.graphicsLayer { val k = if (go) 1.15f else 1f + 0.5f * frac; scaleX = k; scaleY = k }
    )
  }
}

/** Panel de resultados: entra con rebote; podio con medallas y botones de revancha / menú. */
@Composable
internal fun RaceResult(rank: Int, time: Float, stand: List<RStand>, onRematch: () -> Unit, onMenu: () -> Unit, modifier: Modifier = Modifier) {
  val enter = remember { Animatable(0f) }
  LaunchedEffect(Unit) { enter.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) }
  val win = rank == 1
  val edge = if (win) GoldYellow else Color(0xFFB388FF)
  Column(
    modifier.graphicsLayer { val k = 0.8f + 0.2f * enter.value; scaleX = k; scaleY = k; alpha = enter.value.coerceIn(0f, 1f) }
      .clip(RoundedCornerShape(22.dp)).background(Brush.verticalGradient(listOf(Color(0xF0160A2E), Color(0xF00B0818))))
      .border(2.dp, edge, RoundedCornerShape(22.dp)).padding(horizontal = 20.dp, vertical = 16.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Text(if (win) "🏆 ¡VICTORIA!" else "🏁 META", color = edge, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, fontSize = 24.sp, letterSpacing = 2.sp)
    Text(ordinalEs(rank), color = Color.White, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, fontSize = 64.sp, lineHeight = 64.sp)
    Text("Tu tiempo ${"%.1f".format(time)} s", color = Color(0xFFD9D0F0), fontSize = 13.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(10.dp))
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
      stand.forEachIndexed { i, s ->
        Row(
          Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (s.me) Color(0x33FFD60A) else Color(0x14FFFFFF)).padding(horizontal = 10.dp, vertical = 5.dp),
          verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Text(when (i) { 0 -> "🥇"; 1 -> "🥈"; 2 -> "🥉"; else -> "${i + 1}" }, fontSize = 15.sp, modifier = Modifier.width(24.dp), textAlign = TextAlign.Center, color = Color.White)
          Box(Modifier.size(10.dp).clip(CircleShape).background(s.color))
          Text(s.name, color = if (s.me) GoldYellow else Color.White, fontWeight = if (s.me) FontWeight.Black else FontWeight.Medium, fontSize = 13.sp, maxLines = 1, modifier = Modifier.weight(1f))
          if (s.finT > 0f) Text("${"%.1f".format(s.finT)} s", color = Color(0xFFB9B0D0), fontSize = 12.sp)
        }
      }
    }
    Spacer(Modifier.height(12.dp))
    GradientButton("🏁 Revancha", onClick = onRematch, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(6.dp))
    SoftButton("Menú", onClick = onMenu)
  }
}
