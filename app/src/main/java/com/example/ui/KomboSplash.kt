package com.example.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.AuroraBgBottom
import com.example.ui.theme.AuroraBgTop
import com.example.ui.theme.AuroraCoral
import com.example.ui.theme.AuroraCyan
import com.example.ui.theme.AuroraPink
import com.example.ui.theme.AuroraViolet
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** Duración total de la presentación de entrada. */
const val KOMBO_SPLASH_MS = 8000

private fun seg(p: Float, a: Float, b: Float): Float = ((p - a) / (b - a)).coerceIn(0f, 1f)
private fun easeOut(x: Float): Float { val k = 1f - x; return 1f - k * k * k }
private fun easeOutBack(x: Float): Float {
  val c1 = 1.70158f; val c3 = c1 + 1f
  val k = x - 1f
  return 1f + c3 * k * k * k + c1 * k * k
}

/**
 * Presentación de 8 segundos: destello, ondas de choque, rayos giratorios, chispas,
 * logo con rebote, título letra por letra y eslogan. Al terminar llama a [onFinished].
 */
@Composable
fun KomboSplash(onFinished: () -> Unit) {
  val t = remember { Animatable(0f) }
  LaunchedEffect(Unit) {
    t.animateTo(1f, tween(KOMBO_SPLASH_MS, easing = LinearEasing))
    onFinished()
  }
  val p = t.value
  val fadeOut = 1f - seg(p, 0.90f, 1f)
  val title = "KOMBO ARENA"

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Brush.verticalGradient(listOf(AuroraBgTop, AuroraBgBottom)))
      .graphicsLayer { alpha = fadeOut }
      // Mientras dura la presentación no deja pasar toques a la pantalla de abajo.
      .pointerInput(Unit) { awaitPointerEventScope { while (true) { awaitPointerEvent().changes.forEach { it.consume() } } } },
    contentAlignment = Alignment.Center
  ) {
    // Efectos de fondo: resplandor, ondas, rayos y chispas.
    Canvas(Modifier.fillMaxSize()) {
      val c = Offset(size.width / 2f, size.height * 0.40f)
      val maxR = max(size.width, size.height)

      // Resplandor central que respira.
      val glowIn = easeOut(seg(p, 0f, 0.25f))
      val pulse = 0.85f + 0.15f * sin(p * 2f * PI.toFloat() * 4f)
      drawCircle(
        brush = Brush.radialGradient(
          listOf(AuroraViolet.copy(alpha = 0.55f * glowIn), AuroraPink.copy(alpha = 0.22f * glowIn), Color.Transparent),
          center = c, radius = size.width * 0.75f * pulse
        ),
        radius = size.width * 0.75f * pulse, center = c
      )

      // Rayos de luz que giran detrás del logo.
      val rayAlpha = easeOut(seg(p, 0.08f, 0.35f)) * (1f - seg(p, 0.80f, 0.95f))
      val rot = p * 70f
      val rays = 14
      for (i in 0 until rays) {
        val a = ((i * 360f / rays) + rot) * (PI.toFloat() / 180f)
        val half = 0.055f
        val len = maxR * 0.9f
        val p1 = Offset(c.x + cos(a - half) * len, c.y + sin(a - half) * len)
        val p2 = Offset(c.x + cos(a + half) * len, c.y + sin(a + half) * len)
        val path = androidx.compose.ui.graphics.Path().apply { moveTo(c.x, c.y); lineTo(p1.x, p1.y); lineTo(p2.x, p2.y); close() }
        drawPath(
          path = path,
          brush = Brush.radialGradient(
            listOf((if (i % 2 == 0) AuroraPink else AuroraCyan).copy(alpha = 0.22f * rayAlpha), Color.Transparent),
            center = c, radius = len
          )
        )
      }

      // Tres ondas de choque que salen del logo.
      for (k in 0 until 3) {
        val s = seg(p, 0.10f + k * 0.07f, 0.50f + k * 0.07f)
        if (s > 0f && s < 1f) {
          val r = easeOut(s) * maxR * 0.7f
          drawCircle(
            color = (if (k == 1) AuroraCyan else AuroraPink).copy(alpha = (1f - s) * 0.75f),
            radius = r, center = c, style = Stroke(width = 6f * (1f - s) + 1f)
          )
        }
      }

      // Destello inicial.
      val flash = 1f - seg(p, 0.08f, 0.20f)
      if (p in 0.08f..0.20f) drawRect(Color.White.copy(alpha = 0.35f * flash))

      // Chispas que salen en todas direcciones.
      val sparks = 48
      for (i in 0 until sparks) {
        val start = 0.12f + (i % 6) * 0.015f
        val s = seg(p, start, start + 0.55f)
        if (s > 0f && s < 1f) {
          val a = i * 2.39996f // ángulo áureo: reparto uniforme
          val speed = maxR * (0.18f + 0.5f * ((i * 37) % 100) / 100f)
          val r = 120f + easeOut(s) * speed
          val pos = Offset(c.x + cos(a) * r, c.y + sin(a) * r)
          val col = when (i % 4) { 0 -> AuroraPink; 1 -> AuroraCyan; 2 -> AuroraCoral; else -> Color.White }
          drawCircle(col.copy(alpha = (1f - s) * 0.9f), radius = 2.5f + 4f * (1f - s), center = pos)
        }
      }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.graphicsLayer { translationY = -size.height * 0.10f }) {
      // Logo: el mismo icono de la app dentro de un disco con degradado y aro de luz.
      val logoP = seg(p, 0.08f, 0.40f)
      val logoScale = if (logoP <= 0f) 0f else 0.15f + 0.85f * easeOutBack(logoP)
      val breathe = 1f + 0.03f * sin(p * 2f * PI.toFloat() * 3f) * seg(p, 0.40f, 0.5f)
      Box(
        modifier = Modifier
          .size(190.dp)
          .graphicsLayer {
            scaleX = logoScale * breathe; scaleY = logoScale * breathe
            alpha = easeOut(seg(p, 0.08f, 0.22f))
            rotationZ = (1f - easeOut(logoP)) * -25f
          }
          .background(Brush.linearGradient(listOf(AuroraViolet, AuroraPink, AuroraCoral)), CircleShape),
        contentAlignment = Alignment.Center
      ) {
        Canvas(Modifier.fillMaxSize()) {
          drawCircle(Color.White.copy(alpha = 0.85f), radius = size.minDimension / 2f - 3f, style = Stroke(width = 5f))
          drawCircle(Color.Black.copy(alpha = 0.18f), radius = size.minDimension / 2f - 14f)
        }
        Image(painter = painterResource(R.drawable.ic_launcher_foreground), contentDescription = "KOMBO ARENA", modifier = Modifier.size(190.dp))
      }

      Spacer(Modifier.height(34.dp))

      // Título letra por letra con degradado de color.
      Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
        title.forEachIndexed { i, ch ->
          val a = 0.30f + i * 0.028f
          val s = seg(p, a, a + 0.14f)
          val col = lerp(Color.White, if (i < 5) AuroraPink else AuroraCoral, 0.35f + 0.5f * (i / (title.length - 1f)))
          Text(
            text = ch.toString(),
            modifier = Modifier.graphicsLayer {
              alpha = easeOut(s)
              translationY = (1f - easeOutBack(s)) * 70f
              val sc = 0.6f + 0.4f * easeOut(s)
              scaleX = sc; scaleY = sc
            },
            style = TextStyle(
              color = col, fontSize = 38.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp,
              shadow = Shadow(color = AuroraPink.copy(alpha = 0.9f), offset = Offset(0f, 4f), blurRadius = 26f)
            )
          )
        }
      }

      Spacer(Modifier.height(14.dp))

      // Línea de luz bajo el título.
      val lineP = easeOut(seg(p, 0.55f, 0.72f))
      Box(
        Modifier
          .size(width = (220f * lineP).dp, height = 3.dp)
          .background(Brush.horizontalGradient(listOf(Color.Transparent, AuroraCyan, AuroraPink, Color.Transparent)))
      )

      Spacer(Modifier.height(14.dp))

      Text(
        text = "JUEGA · COMPITE · GANA",
        modifier = Modifier.graphicsLayer {
          alpha = easeOut(seg(p, 0.62f, 0.80f))
          translationY = (1f - easeOut(seg(p, 0.62f, 0.80f))) * 24f
        },
        style = TextStyle(color = Color(0xFFE2D8F5), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 5.sp)
      )
    }
  }
}
