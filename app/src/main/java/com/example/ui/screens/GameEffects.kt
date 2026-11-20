package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.CoupleViewModel
import com.example.ui.components.GlassCard
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.LocalDuoPalette
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Texto/emoji que "salta" con efecto elástico cada vez que cambia. */
@Composable
fun PopText(text: String, fontSize: TextUnit, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
  val scale = remember { Animatable(1f) }
  LaunchedEffect(text) {
    if (text.isNotEmpty() && text != "…" && text != "✅") {
      scale.snapTo(0.2f)
      scale.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMediumLow))
    } else {
      scale.snapTo(1f)
    }
  }
  Text(
    text = text,
    fontSize = fontSize,
    color = color,
    fontWeight = FontWeight.Bold,
    textAlign = TextAlign.Center,
    modifier = modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value }
  )
}

private class ConfettiP(val x: Float, val delay: Float, val rot: Float, val size: Float, val color: Color, val speed: Float)

/** Lluvia de confeti para celebrar una victoria. */
@Composable
fun ConfettiBurst(modifier: Modifier = Modifier) {
  val palette = listOf(Color(0xFFFF4D8D), Color(0xFFFFC857), Color(0xFF37E6FF), Color(0xFF9D6BFF), Color(0xFF3DDC84), Color(0xFFFF8A5B))
  val particles = remember {
    List(60) {
      ConfettiP(
        x = Random.nextFloat(), delay = Random.nextFloat(), rot = Random.nextFloat() * 360f,
        size = 8f + Random.nextFloat() * 10f, color = palette[Random.nextInt(palette.size)], speed = 0.6f + Random.nextFloat() * 0.8f
      )
    }
  }
  val transition = rememberInfiniteTransition(label = "confetti")
  val prog by transition.animateFloat(
    initialValue = 0f, targetValue = 1f,
    animationSpec = infiniteRepeatable(tween(2800, easing = LinearEasing), RepeatMode.Restart),
    label = "confettiProgress"
  )
  Canvas(modifier = modifier) {
    particles.forEach { p ->
      val y = ((prog * p.speed + p.delay) % 1f) * size.height
      val x = p.x * size.width + sin(prog * 6.2831855f * p.speed + p.rot) * 22f
      rotate(degrees = p.rot + prog * 360f * p.speed, pivot = Offset(x, y)) {
        drawRect(color = p.color, topLeft = Offset(x, y), size = Size(p.size, p.size * 0.6f))
      }
    }
  }
}

/** Banner de resultado con trofeo rebotando y confeti. */
@Composable
fun ResultBanner(text: String) {
  val transition = rememberInfiniteTransition(label = "trophy")
  val bounce by transition.animateFloat(
    initialValue = 0f, targetValue = -10f,
    animationSpec = infiniteRepeatable(tween(600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
    label = "bounce"
  )
  GlassCard(modifier = Modifier.fillMaxWidth(), highlight = true, corner = 22.dp) {
    Box(modifier = Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) {
      ConfettiBurst(Modifier.fillMaxSize())
      Text(
        text = text, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, textAlign = TextAlign.Center,
        modifier = Modifier.graphicsLayer { translationY = bounce }
      )
    }
  }
}

/** Carta que gira en 3D al destaparse. */
@Composable
fun FlipCard(
  faceUp: Boolean,
  frontText: String,
  frontBrush: Brush,
  backBrush: Brush,
  modifier: Modifier = Modifier
) {
  val rot by animateFloatAsState(if (faceUp) 180f else 0f, tween(450, easing = FastOutSlowInEasing), label = "flip")
  Box(
    modifier = modifier.graphicsLayer { rotationY = rot; cameraDistance = 14f * density },
    contentAlignment = Alignment.Center
  ) {
    if (rot <= 90f) {
      Box(
        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)).background(backBrush)
          .border(BorderStroke(1.dp, GlassBorder), RoundedCornerShape(18.dp)),
        contentAlignment = Alignment.Center
      ) { Text("?", fontSize = 32.sp, color = Color.White, fontWeight = FontWeight.Black) }
    } else {
      Box(
        modifier = Modifier.fillMaxSize().graphicsLayer { rotationY = 180f }.clip(RoundedCornerShape(18.dp)).background(frontBrush)
          .border(BorderStroke(1.dp, GlassBorder), RoundedCornerShape(18.dp)),
        contentAlignment = Alignment.Center
      ) { Text(frontText, fontSize = 32.sp, color = Color.White, fontWeight = FontWeight.Black) }
    }
  }
}

/** Casilla de Conecta 4: la ficha cae desde arriba con rebote. */
@Composable
fun C4Cell(value: Int, row: Int, colors: List<Color>, modifier: Modifier = Modifier) {
  val prog = remember { Animatable(if (value != 0) 1f else 0f) }
  LaunchedEffect(value) {
    if (value == 0) {
      prog.snapTo(0f)
    } else if (prog.value < 1f) {
      prog.snapTo(0f)
      prog.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 180f))
    }
  }
  Box(modifier = modifier) {
    Box(modifier = Modifier.fillMaxSize().clip(CircleShape).background(colors[0]))
    Box(
      modifier = Modifier
        .fillMaxSize()
        .graphicsLayer {
          translationY = -(1f - prog.value) * size.height * (row + 1)
          alpha = if (value == 0) 0f else 1f
        }
        .clip(CircleShape)
        .background(colors[value.coerceIn(0, colors.lastIndex)])
    )
  }
}

/** Aviso si el juego no pudo sincronizarse con tu pareja. */
@Composable
fun GameErrorBanner(vm: CoupleViewModel) {
  val err by vm.gameError.collectAsState()
  val message = err
  if (message != null) {
    GlassCard(modifier = Modifier.fillMaxWidth(), corner = 16.dp) {
      Text("⚠️ No se pudo enviar la jugada: $message", color = Color(0xFFFF9AA8), fontSize = 12.sp)
    }
  }
}

@Composable
fun rememberPalettePulse(): Float {
  val t = rememberInfiniteTransition(label = "pulse")
  val v by t.animateFloat(
    initialValue = 1f, targetValue = 1.06f,
    animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
    label = "pulseValue"
  )
  return v
}


/** Pincel de neón: un arcoíris suave que recorre el borde del tablero (los colores giran). */
@Composable
fun rememberNeonBrush(periodMs: Int = 7000, saturation: Float = 0.65f): Brush {
  val t = rememberInfiniteTransition(label = "neon")
  val ph by t.animateFloat(0f, 360f, infiniteRepeatable(tween(periodMs, easing = LinearEasing)), label = "neonPh")
  return Brush.sweepGradient(List(7) { Color.hsv((ph + it * 60f) % 360f, saturation, 1f) })
}

/** Destellos que flotan despacio sobre el tablero (no tocan nada: solo decoran). */
@Composable
fun AmbientSparkles(
  modifier: Modifier = Modifier,
  colors: List<Color> = listOf(Color.White, Color(0xFFFFE08A), Color(0xFF7CF7FF)),
  count: Int = 26
) {
  val t = rememberInfiniteTransition(label = "sparkles")
  val clock by t.animateFloat(0f, 1f, infiniteRepeatable(tween(16000, easing = LinearEasing)), label = "sparkClock")
  Canvas(modifier) {
    val w = size.width; val h = size.height
    val twoPi = 6.2831855f
    val px = 1.dp.toPx()
    for (i in 0 until count) {
      val k = 1 + (i % 3)
      val sx = ((i * 7919) % 1000) / 1000f
      val sy0 = ((i * 104729) % 997) / 997f
      val y = (((sy0 - clock * k) % 1f) + 1f) % 1f
      val x = sx + 0.025f * sin(clock * twoPi * 2f + i)
      val tw = 0.5f + 0.5f * sin(clock * twoPi * 6f * k + i * 1.7f)
      val c = colors[i % colors.size]
      val r = (1.4f + (i % 3) * 0.9f) * px
      val ctr = Offset(x * w, y * h)
      drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.45f * tw), Color.Transparent), ctr, r * 5f), r * 5f, ctr)
      drawCircle(c.copy(alpha = 0.35f + 0.6f * tw), r, ctr)
      // destello en cruz (+), nunca en diagonal
      drawLine(c.copy(alpha = 0.5f * tw), Offset(ctr.x - r * 3.2f, ctr.y), Offset(ctr.x + r * 3.2f, ctr.y), 0.9f * px)
      drawLine(c.copy(alpha = 0.5f * tw), Offset(ctr.x, ctr.y - r * 3.2f), Offset(ctr.x, ctr.y + r * 3.2f), 0.9f * px)
    }
  }
}

/** Estallido de impacto: onda expansiva + chispas radiales. Se dispara una vez al entrar a la composición. */
@Composable
fun ImpactBurst(modifier: Modifier = Modifier, colors: List<Color> = listOf(Color(0xFFFFE08A), Color(0xFFFF8A3D), Color.White), sparks: Int = 14) {
  val prog = remember { Animatable(0f) }
  LaunchedEffect(Unit) { prog.animateTo(1f, tween(750, easing = FastOutSlowInEasing)) }
  Canvas(modifier) {
    val k = prog.value
    if (k >= 1f) return@Canvas
    val c = Offset(size.width / 2f, size.height / 2f)
    val rMax = minOf(size.width, size.height) / 2f
    val a = 1f - k
    drawCircle(Color.White.copy(alpha = 0.5f * a), rMax * 0.35f * (1f + k), c)
    drawCircle(colors[0].copy(alpha = 0.9f * a), rMax * (0.25f + 0.75f * k), c, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 5.dp.toPx() * a + 1f))
    drawCircle(colors[1 % colors.size].copy(alpha = 0.6f * a), rMax * (0.15f + 0.55f * k), c, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx() * a + 0.8f))
    for (i in 0 until sparks) {
      val ang = i * (6.2831855f / sparks) + 0.3f
      val d0 = rMax * (0.25f + 0.55f * k); val d1 = rMax * (0.45f + 0.55f * k)
      drawLine(colors[i % colors.size].copy(alpha = a), Offset(c.x + cos(ang) * d0, c.y + sin(ang) * d0), Offset(c.x + cos(ang) * d1, c.y + sin(ang) * d1), 2.4.dp.toPx() * a + 0.8f, androidx.compose.ui.graphics.StrokeCap.Round)
    }
  }
}
