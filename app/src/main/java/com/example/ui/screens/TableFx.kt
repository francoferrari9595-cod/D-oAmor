package com.example.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** Reloj 0..1 que da vueltas cada 8 s: mueve brillos y destellos de las mesas. */
@Composable
internal fun rememberTableClock(): State<Float> =
  rememberInfiniteTransition(label = "tableFx").animateFloat(0f, 1f, infiniteRepeatable(tween(8000, easing = LinearEasing), RepeatMode.Restart), label = "tableClock")

/** Destellos flotantes y barrido de luz sobre una zona. */
private fun DrawScope.fxSparkles(left: Float, top: Float, w: Float, h: Float, t: Float, count: Int = 18) {
  for (i in 0 until count) {
    val ph = (t + i * 0.137f) % 1f
    val x = left + w * (((i * 37) % 100) / 100f)
    val y = top + h * (1f - ph)
    val a = sin(ph * PI.toFloat()).coerceIn(0f, 1f) * 0.55f
    drawCircle(Color(1f, 0.93f, 0.62f, a), 1.5f + (i % 3), Offset(x, y))
  }
  val sweepX = left + w * ((t * 1.4f) % 1.4f - 0.2f)
  drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color(0x22FFFFFF), Color.Transparent), sweepX - w * 0.12f, sweepX + w * 0.12f), Offset(left, top), Size(w, h))
}

/** Mesa de casino ovalada: madera, anillo dorado, paño con trama, foco de luz y destellos. */
internal fun DrawScope.drawLuxuryOval(t: Float) {
  val w = size.width; val h = size.height
  drawOval(Color(0x77000000), Offset(w * 0.01f, h * 0.10f), Size(w * 0.98f, h * 0.88f))
  drawOval(Brush.verticalGradient(listOf(Color(0xFF9C6B3A), Color(0xFF5A3A1C), Color(0xFF2B1A0B))), Offset(0f, h * 0.05f), Size(w, h * 0.88f))
  drawOval(Brush.sweepGradient(listOf(Color(0xFFFFE08A), Color(0xFFB8862D), Color(0xFFFFE08A), Color(0xFF8A5A14), Color(0xFFFFE08A)), Offset(w / 2f, h * 0.49f)), Offset(w * 0.035f, h * 0.085f), Size(w * 0.93f, h * 0.81f), style = Stroke(5f))
  drawOval(Brush.radialGradient(listOf(Color(0xFF22B56A), Color(0xFF0E7442), Color(0xFF053A22)), Offset(w * 0.5f, h * 0.45f), w * 0.62f), Offset(w * 0.05f, h * 0.10f), Size(w * 0.90f, h * 0.78f))
  for (i in 1..5) drawOval(Color(0x14FFFFFF), Offset(w * (0.05f + i * 0.045f), h * (0.10f + i * 0.04f)), Size(w * (0.90f - i * 0.09f), h * (0.78f - i * 0.08f)), style = Stroke(1.2f))
  drawOval(Color(0x55FFE08A), Offset(w * 0.12f, h * 0.17f), Size(w * 0.76f, h * 0.64f), style = Stroke(2.5f))
  drawOval(Brush.radialGradient(listOf(Color(0x55FFFFFF), Color.Transparent), Offset(w * 0.5f, h * 0.38f), w * 0.4f), Offset(w * 0.05f, h * 0.10f), Size(w * 0.90f, h * 0.78f))
  fxSparkles(w * 0.1f, h * 0.15f, w * 0.8f, h * 0.7f, t)
}

/** Fichas apiladas (una torre por cada ~50 del bote, hasta 6). */
internal fun DrawScope.drawChipStacks(cx: Float, cy: Float, pot: Int) {
  val stacks = (pot / 50 + if (pot > 0) 1 else 0).coerceIn(0, 6)
  val cols = listOf(Color(0xFFE53935), Color(0xFF1E88E5), Color(0xFF43A047), Color(0xFF212121), Color(0xFFFDD835), Color(0xFF8E24AA))
  for (s in 0 until stacks) {
    val x = cx + (s - (stacks - 1) / 2f) * 26f
    for (c in 0 until 4 + s % 3) {
      val y = cy - c * 5f
      drawOval(Color(0x66000000), Offset(x - 11f, y + 2f), Size(22f, 9f))
      drawOval(cols[(s + c) % cols.size], Offset(x - 11f, y - 3f), Size(22f, 9f))
      drawOval(Color(0xAAFFFFFF), Offset(x - 11f, y - 3f), Size(22f, 9f), style = Stroke(1.2f))
    }
  }
}

/** Mesa rectangular (Truco): marco de madera con filete dorado, paño con trama y destellos. */
internal fun DrawScope.drawLuxuryRect(t: Float) {
  val w = size.width; val h = size.height
  drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF9C6B3A), Color(0xFF3B2410))), Offset.Zero, Size(w, h), CornerRadius(40f))
  drawRoundRect(Brush.horizontalGradient(listOf(Color(0xFFFFE08A), Color(0xFFB8862D), Color(0xFFFFE08A))), Offset(7f, 7f), Size(w - 14f, h - 14f), CornerRadius(34f), style = Stroke(3f))
  drawRoundRect(Brush.radialGradient(listOf(Color(0xFF22B56A), Color(0xFF0E7442), Color(0xFF053A22)), Offset(w / 2f, h * 0.45f), w * 0.7f), Offset(11f, 11f), Size(w - 22f, h - 22f), CornerRadius(30f))
  var yy = 22f
  while (yy < h - 16f) { drawLine(Color(0x0FFFFFFF), Offset(16f, yy), Offset(w - 16f, yy), 1f); yy += 9f }
  drawRoundRect(Color(0x44FFE08A), Offset(24f, 24f), Size(w - 48f, h - 48f), CornerRadius(22f), style = Stroke(1.6f))
  drawCircle(Brush.radialGradient(listOf(Color(0x55FFFFFF), Color.Transparent), Offset(w / 2f, h * 0.35f), w * 0.4f), w * 0.4f, Offset(w / 2f, h * 0.35f))
  fxSparkles(10f, 10f, w - 20f, h - 20f, t, 14)
  // “pulso” suave del borde dorado
  val pulse = abs(sin(t * 2f * PI.toFloat())) * 0.35f
  drawRoundRect(Color(1f, 0.88f, 0.5f, pulse), Offset(7f, 7f), Size(w - 14f, h - 14f), CornerRadius(34f), style = Stroke(5f))
}
