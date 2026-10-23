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

/** Pantalla de ingreso con el mando de juegos animado y la marca Sini⚡ Joss. */
@Composable
fun WelcomeScreen(onContinue: () -> Unit, onGuest: () -> Unit = {}) {
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
        // Anillos de latido y chispas que orbitan despacio alrededor del corazón.
        val center = Offset(w * 0.5f, h * 0.5f)
        for (k in 0 until 3) {
          val ph = (drift * 2f + k / 3f) % 1f
          drawCircle(p.accentA.copy(alpha = 0.34f * (1f - ph)), radius = w * (0.45f + 0.55f * ph), center = center, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f))
        }
        for (k in 0 until 7) {
          val ang = (drift * 6.2831855f * (if (k % 2 == 0) 1f else -1f)) + k * 0.8975979f
          val rad = w * (0.62f + 0.06f * (k % 3))
          drawCircle(p.accentB.copy(alpha = 0.85f), 3f + (k % 3) * 1.6f, Offset(center.x + kotlin.math.cos(ang) * rad, center.y + sin(ang) * rad * 0.82f))
        }
        drawOval(Color(0x55000000), Offset(w * 0.2f, h * 0.88f), Size(w * 0.6f, h * 0.08f))
        // Logo: mando de juegos con rayo central, d-pad y cuatro botones de colores.
        val body = Path()
        body.moveTo(w * 0.26f, h * 0.32f)
        body.cubicTo(w * 0.10f, h * 0.32f, w * 0.02f, h * 0.52f, w * 0.03f, h * 0.70f)
        body.cubicTo(w * 0.04f, h * 0.86f, w * 0.14f, h * 0.90f, w * 0.22f, h * 0.84f)
        body.cubicTo(w * 0.30f, h * 0.78f, w * 0.34f, h * 0.70f, w * 0.40f, h * 0.68f)
        body.lineTo(w * 0.60f, h * 0.68f)
        body.cubicTo(w * 0.66f, h * 0.70f, w * 0.70f, h * 0.78f, w * 0.78f, h * 0.84f)
        body.cubicTo(w * 0.86f, h * 0.90f, w * 0.96f, h * 0.86f, w * 0.97f, h * 0.70f)
        body.cubicTo(w * 0.98f, h * 0.52f, w * 0.90f, h * 0.32f, w * 0.74f, h * 0.32f)
        body.close()
        // gatillos
        drawRoundRect(p.accentB.copy(alpha = 0.9f), Offset(w * 0.18f, h * 0.24f), Size(w * 0.22f, h * 0.10f), androidx.compose.ui.geometry.CornerRadius(w * 0.05f))
        drawRoundRect(p.accentB.copy(alpha = 0.9f), Offset(w * 0.60f, h * 0.24f), Size(w * 0.22f, h * 0.10f), androidx.compose.ui.geometry.CornerRadius(w * 0.05f))
        drawPath(body, Brush.linearGradient(listOf(Color.White, p.accentB, p.accentA), start = Offset(w * 0.2f, h * 0.3f), end = Offset(w * 0.8f, h * 0.9f)))
        drawPath(body, Color(0x33000000), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
        drawOval(Color(0x77FFFFFF), Offset(w * 0.16f, h * 0.36f), Size(w * 0.42f, h * 0.10f))
        // d-pad
        val dp = Color(0xFF2A1240)
        drawRoundRect(dp, Offset(w * 0.15f, h * 0.505f), Size(w * 0.20f, h * 0.065f), androidx.compose.ui.geometry.CornerRadius(w * 0.02f))
        drawRoundRect(dp, Offset(w * 0.222f, h * 0.44f), Size(w * 0.065f, h * 0.20f), androidx.compose.ui.geometry.CornerRadius(w * 0.02f))
        // botones
        val bc = Offset(w * 0.77f, h * 0.54f)
        val br = w * 0.065f
        drawCircle(Color(0xFFFF5A8A), br * 0.8f, Offset(bc.x, bc.y - br * 1.35f))
        drawCircle(Color(0xFFFFD166), br * 0.8f, Offset(bc.x + br * 1.35f, bc.y))
        drawCircle(Color(0xFF37E6FF), br * 0.8f, Offset(bc.x, bc.y + br * 1.35f))
        drawCircle(Color(0xFF3DDC84), br * 0.8f, Offset(bc.x - br * 1.35f, bc.y))
        // rayo central
        val bolt = Path()
        bolt.moveTo(w * 0.53f, h * 0.36f)
        bolt.lineTo(w * 0.43f, h * 0.55f)
        bolt.lineTo(w * 0.50f, h * 0.55f)
        bolt.lineTo(w * 0.46f, h * 0.70f)
        bolt.lineTo(w * 0.58f, h * 0.50f)
        bolt.lineTo(w * 0.51f, h * 0.50f)
        bolt.close()
        drawPath(bolt, Brush.verticalGradient(listOf(Color(0xFFFFF3A0), Color(0xFFFFB300)), startY = h * 0.36f, endY = h * 0.70f))
      }
      Spacer(Modifier.height(18.dp))
      SiniJossTitle(34.sp, Modifier.graphicsLayer { alpha = appear.value })
      Spacer(Modifier.height(10.dp))
      Text(
        "Dos jugadores, una sola pantalla.\nChat, llamadas, cine, juegos y mucha diversión a distancia.",
        color = Color(0xFFE2D8F5), fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.graphicsLayer { alpha = appear.value }
      )
      Spacer(Modifier.height(32.dp))
      GradientButton("Entrar 🎮", onClick = onContinue, modifier = Modifier.fillMaxWidth(), height = 58.dp)
      Spacer(Modifier.height(10.dp))
      com.example.ui.components.SoftButton("🎟️ Tengo un código de invitado", onClick = onGuest, modifier = Modifier.fillMaxWidth())
    }
  }
}
