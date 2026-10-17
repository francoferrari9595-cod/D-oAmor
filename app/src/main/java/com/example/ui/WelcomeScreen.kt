package com.example.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.AuroraBackground
import com.example.ui.components.GradientButton
import com.example.ui.components.SiniJossTitle
import com.example.ui.theme.LocalDuoPalette
import kotlin.math.sin

/** Pantalla de ingreso con corazón 3D animado y la marca SINI&JOSS. */
@Composable
fun WelcomeScreen(onContinue: () -> Unit) {
  val p = LocalDuoPalette.current
  val t = rememberInfiniteTransition(label = "welcome")
  val sway by t.animateFloat(-24f, 24f, infiniteRepeatable(tween(2800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "sway")
  val beat by t.animateFloat(0.94f, 1.08f, infiniteRepeatable(tween(850, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "beat")
  val drift by t.animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart), label = "drift")
  val appear = remember { Animatable(0f) }
  LaunchedEffect(Unit) { appear.animateTo(1f, tween(1300, easing = FastOutSlowInEasing)) }

  AuroraBackground {
    Canvas(modifier = Modifier.fillMaxSize()) {
      for (i in 0 until 16) {
        val prog = (drift + i / 16f) % 1f
        val x = size.width * (((i * 37) % 100) / 100f) + (sin(prog * 6.2831855f + i) * 22f)
        val y = size.height * (1f - prog)
        drawCircle(p.accentA.copy(alpha = 0.30f * (1f - prog)), 5f + (i % 4) * 4f, Offset(x, y))
      }
    }
    Column(
      modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(28.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      Canvas(
        modifier = Modifier
          .size(200.dp)
          .graphicsLayer {
            rotationY = sway
            cameraDistance = 14f * density
            scaleX = beat * appear.value
            scaleY = beat * appear.value
            alpha = appear.value
          }
      ) {
        val w = size.width
        val h = size.height
        drawOval(Color(0x55000000), Offset(w * 0.2f, h * 0.88f), Size(w * 0.6f, h * 0.08f))
        val heart = Path()
        heart.moveTo(w * 0.5f, h * 0.88f)
        heart.cubicTo(w * -0.05f, h * 0.52f, w * 0.05f, h * 0.06f, w * 0.30f, h * 0.10f)
        heart.cubicTo(w * 0.44f, h * 0.12f, w * 0.5f, h * 0.24f, w * 0.5f, h * 0.27f)
        heart.cubicTo(w * 0.5f, h * 0.24f, w * 0.56f, h * 0.12f, w * 0.70f, h * 0.10f)
        heart.cubicTo(w * 0.95f, h * 0.06f, w * 1.05f, h * 0.52f, w * 0.5f, h * 0.88f)
        heart.close()
        drawPath(heart, Brush.radialGradient(listOf(Color.White, p.accentB, p.accentA, p.accentA.copy(alpha = 0.55f)), center = Offset(w * 0.36f, h * 0.28f), radius = w * 0.85f))
        drawOval(Color(0x88FFFFFF), Offset(w * 0.22f, h * 0.16f), Size(w * 0.18f, h * 0.10f))
      }
      Spacer(Modifier.height(18.dp))
      SiniJossTitle(34.sp, Modifier.graphicsLayer { alpha = appear.value })
      Spacer(Modifier.height(10.dp))
      Text(
        "Dos corazones, una sola pantalla.\nChat, llamadas, cine, juegos y mucho amor a distancia.",
        color = Color(0xFFE2D8F5), fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.graphicsLayer { alpha = appear.value }
      )
      Spacer(Modifier.height(32.dp))
      GradientButton("Entrar 💖", onClick = onContinue, modifier = Modifier.fillMaxWidth(), height = 58.dp)
    }
  }
}
