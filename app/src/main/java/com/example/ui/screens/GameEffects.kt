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
