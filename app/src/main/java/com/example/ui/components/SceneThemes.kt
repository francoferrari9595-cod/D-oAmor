package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

// ═══════════════════════════════════════════════════════════════════════════════
//  TEMAS VISUALES CON ESCENA: fondo ilustrado (cielo, montañas, ciudad, mar…) + movimiento.
//  Se eligen en Configuración → «Temas visuales». Todo se dibuja con Canvas (sin imágenes pesadas)
//  y cada escena es un bucle perfecto: `ph` va de 0 a 2π y vuelve a empezar sin saltos.
// ═══════════════════════════════════════════════════════════════════════════════

private const val TWO_PI = 6.2831855f

private fun hr(i: Int, k: Int): Float {
  val x = sin(i * 12.9898 + k * 78.233) * 43758.5453
  return (x - floor(x)).toFloat()
}

/** Avance lineal con vuelta: devuelve 0..1 que se repite `n` veces por ciclo de `ph`. */
private fun loop(ph: Float, n: Int, offset: Float = 0f): Float {
  val v = (ph / TWO_PI * n + offset) % 1f
  return if (v < 0f) v + 1f else v
}

private fun DrawScope.sky(top: Color, mid: Color, bottom: Color, endY: Float = size.height) {
  drawRect(Brush.verticalGradient(listOf(top, mid, bottom), 0f, endY), Offset.Zero, Size(size.width, size.height))
}

private fun DrawScope.glow(c: Offset, r: Float, col: Color, a: Float) {
  drawCircle(Brush.radialGradient(listOf(col.copy(alpha = a), Color.Transparent), c, r), r, c)
}

/** Cordillera con paralaje: `shift` en 0..1 repetido; la onda es periódica en el ancho para que no salte. */
private fun DrawScope.ridge(baseY: Float, amp: Float, shift: Float, col: Color, seed: Int, jag: Float = 1f) {
  val w = size.width; val h = size.height
  val p = Path(); p.moveTo(0f, h)
  var x = 0f
  while (x <= w + 12f) {
    val u = x / w + shift
    val y = baseY - amp * (0.55f + 0.30f * sin(u * TWO_PI * 2f + seed) + 0.18f * jag * sin(u * TWO_PI * 5f + seed * 1.7f) + 0.08f * jag * sin(u * TWO_PI * 11f + seed * 3.1f))
    p.lineTo(x, y)
    x += 12f
  }
  p.lineTo(w, h); p.close()
  drawPath(p, col)
}

private fun DrawScope.star(i: Int, ph: Float, yMax: Float, tintA: Color = Color.White) {
  val x = hr(i, 1) * size.width; val y = hr(i, 2) * yMax
  val tw = 0.35f + 0.65f * abs(sin(ph * (1 + i % 3) + i))
  drawCircle(tintA.copy(alpha = 0.9f * tw), 0.8f + hr(i, 3) * 1.8f, Offset(x, y))
}

// ───────────────────────── 1. Ciudad neón de noche ─────────────────────────
private fun DrawScope.sceneCity(ph: Float) {
  val w = size.width; val h = size.height
  sky(Color(0xFF07051E), Color(0xFF2B0F57), Color(0xFFFF4D8D), h * 0.72f)
  for (i in 0 until 60) star(i, ph, h * 0.4f)
  glow(Offset(w * 0.75f, h * 0.2f), w * 0.35f, Color(0xFFB388FF), 0.35f)
  drawCircle(Color(0xFFFFF3C4), w * 0.055f, Offset(w * 0.75f, h * 0.2f))
  // capas de edificios con paralaje horizontal lento
  for (layer in 0..2) {
    val bw = w * (0.10f + layer * 0.03f)
    val base = h * (0.78f - layer * 0.04f)
    val col = when (layer) { 0 -> Color(0xFF1C0F3D); 1 -> Color(0xFF140A30); else -> Color(0xFF0B0620) }
    val period = 6
    val scroll = (loop(ph, 1) * bw * period * (1 + layer)) % (bw * period)
    var x = -bw * period + scroll
    var idx = 0
    while (x < w + bw) {
      val k = (idx % period) + layer * 50
      val bh = h * (0.12f + 0.26f * hr(k, 4)) * (1f + layer * 0.15f)
      drawRect(col, Offset(x, base - bh), Size(bw * 0.96f, bh + h))
      if (layer < 2) {
        for (wy in 0 until (bh / 22f).toInt()) for (wx in 0 until 3) {
          val on = hr(k * 7 + wy, wx + 9) > (if (layer == 0) 0.45f else 0.6f)
          if (on) drawRect((if (hr(k, wx + wy) > 0.8f) Color(0xFF37E6FF) else Color(0xFFFFD166)).copy(alpha = 0.55f), Offset(x + bw * 0.12f + wx * bw * 0.28f, base - bh + 10f + wy * 22f), Size(bw * 0.14f, 9f))
        }
      }
      x += bw; idx++
    }
  }
  // cartel de neón que parpadea
  val fl = if (sin(ph * 9f) > -0.55f) 1f else 0.25f
  drawRect(Color(0xFFFF2E93).copy(alpha = 0.9f * fl), Offset(w * 0.12f, h * 0.42f), Size(w * 0.26f, 10f))
  glow(Offset(w * 0.25f, h * 0.425f), w * 0.22f, Color(0xFFFF2E93), 0.35f * fl)
  // avenida con autos (luces)
  drawRect(Brush.verticalGradient(listOf(Color(0xFF12082A), Color(0xFF05020F)), h * 0.88f, h), Offset(0f, h * 0.88f), Size(w, h * 0.12f))
  for (i in 0 until 7) {
    val dir = if (i % 2 == 0) 1f else -1f
    val x = ((loop(ph, 2 + i % 2, i * 0.17f) * (w + 120f)) - 60f).let { if (dir > 0) it else w - it }
    val y = h * (0.915f + (i % 3) * 0.025f)
    val c = if (dir > 0) Color(0xFFFF2D3F) else Color(0xFFFFF3C4)
    drawLine(c.copy(alpha = 0.25f), Offset(x - dir * 40f, y), Offset(x, y), 5f, StrokeCap.Round)
    drawCircle(c, 3.2f, Offset(x, y)); glow(Offset(x, y), 26f, c, 0.5f)
  }
}

// ───────────────────────── 2. Atardecer retro (synthwave) ─────────────────────────
private fun DrawScope.sceneSynth(ph: Float) {
  val w = size.width; val h = size.height
  val hor = h * 0.58f
  sky(Color(0xFF12003D), Color(0xFF7A1BA8), Color(0xFFFF6B9D), hor)
  for (i in 0 until 40) star(i, ph, hor * 0.7f)
  val sc = Offset(w * 0.5f, hor - w * 0.04f)
  glow(sc, w * 0.55f, Color(0xFFFF4D8D), 0.45f)
  drawCircle(Brush.verticalGradient(listOf(Color(0xFFFFF176), Color(0xFFFF4D8D)), sc.y - w * 0.26f, sc.y + w * 0.26f), w * 0.26f, sc)
  // persianas del sol
  for (k in 0 until 7) {
    val y = sc.y + w * 0.02f + k * w * 0.034f
    drawRect(Color(0xFF7A1BA8).copy(alpha = 0.9f), Offset(sc.x - w * 0.27f, y), Size(w * 0.54f, 2f + k * 1.6f))
  }
  ridge(hor, h * 0.11f, loop(ph, 1) * 0f + 0.1f, Color(0xFF2A0A55), 3, 1.4f)
  drawRect(Color(0xFF14002E), Offset(0f, hor), Size(w, h - hor))
  // rejilla en perspectiva que avanza
  val gc = Color(0xFFFF4DD2)
  for (i in -14..14) {
    drawLine(gc.copy(alpha = 0.55f), Offset(w * 0.5f + i * w * 0.015f, hor), Offset(w * 0.5f + i * w * 0.24f, h), 1.6f)
  }
  for (k in 0 until 14) {
    val q = ((k + loop(ph, 1) * 14f) % 14f) / 14f
    val y = hor + (h - hor) * q * q
    drawLine(gc.copy(alpha = 0.15f + 0.6f * q), Offset(0f, y), Offset(w, y), 1f + 2.2f * q)
  }
  glow(Offset(w * 0.5f, hor), w * 0.7f, Color(0xFFFF4DD2), 0.18f)
}

// ───────────────────────── 3. Océano profundo ─────────────────────────
private fun DrawScope.sceneDeep(ph: Float) {
  val w = size.width; val h = size.height
  sky(Color(0xFF0A7CA8), Color(0xFF05406B), Color(0xFF020B1F))
  // rayos de luz
  for (i in 0 until 6) {
    val x0 = w * (0.1f + 0.16f * i) + sin(ph + i) * w * 0.03f
    val p = Path(); p.moveTo(x0 - 16f, 0f); p.lineTo(x0 + 16f, 0f); p.lineTo(x0 + w * 0.12f + 30f, h * 0.9f); p.lineTo(x0 + w * 0.12f - 50f, h * 0.9f); p.close()
    drawPath(p, Brush.verticalGradient(listOf(Color(0x40BFF3FF), Color.Transparent), 0f, h * 0.9f))
  }
  // fondo marino
  ridge(h * 0.95f, h * 0.12f, 0.2f, Color(0xFF031428), 5, 0.8f)
  // peces que cruzan
  for (i in 0 until 9) {
    val dir = if (i % 2 == 0) 1f else -1f
    val t = loop(ph, 1 + i % 2, hr(i, 1))
    val x = if (dir > 0) -80f + t * (w + 160f) else w + 80f - t * (w + 160f)
    val y = h * (0.2f + 0.6f * hr(i, 2)) + sin(ph * 3f + i) * 10f
    val s = 14f + 22f * hr(i, 3)
    val col = if (i % 3 == 0) Color(0xFFFFB347) else if (i % 3 == 1) Color(0xFF7DF9FF) else Color(0xFFFF6B8B)
    val body = Path()
    body.moveTo(x + dir * s, y)
    body.quadraticBezierTo(x, y - s * 0.55f, x - dir * s, y)
    body.quadraticBezierTo(x, y + s * 0.55f, x + dir * s, y)
    drawPath(body, col.copy(alpha = 0.85f))
    val tail = Path(); tail.moveTo(x - dir * s * 0.9f, y); tail.lineTo(x - dir * s * 1.6f, y - s * 0.5f); tail.lineTo(x - dir * s * 1.6f, y + s * 0.5f); tail.close()
    drawPath(tail, col.copy(alpha = 0.8f))
    drawCircle(Color.White, 2f, Offset(x + dir * s * 0.55f, y - s * 0.1f))
  }
  // burbujas ascendentes
  for (i in 0 until 36) {
    val t = loop(ph, 1 + i % 3, hr(i, 4))
    val x = hr(i, 5) * w + sin(ph * 2f + i) * 12f
    val y = h * (1.05f - t * 1.15f)
    val r = 2f + hr(i, 6) * 9f
    drawCircle(Color.White.copy(alpha = 0.55f * (1f - t * 0.5f)), r, Offset(x, y), style = Stroke(1.4f))
    drawCircle(Color(0x33BFF3FF), r, Offset(x, y))
  }
}

// ───────────────────────── 4. Bosque de luciérnagas ─────────────────────────
private fun DrawScope.sceneForest(ph: Float) {
  val w = size.width; val h = size.height
  sky(Color(0xFF020A12), Color(0xFF0B2A2A), Color(0xFF0E3B2E), h * 0.8f)
  for (i in 0 until 40) star(i, ph, h * 0.35f)
  glow(Offset(w * 0.3f, h * 0.18f), w * 0.4f, Color(0xFFBFE9D0), 0.22f)
  drawCircle(Color(0xFFE9F7E0), w * 0.05f, Offset(w * 0.3f, h * 0.18f))
  // árboles en capas (triángulos de pino)
  for (layer in 0..2) {
    val col = when (layer) { 0 -> Color(0xFF0C3B2D); 1 -> Color(0xFF082B22); else -> Color(0xFF041A14) }
    val n = 7 + layer * 2
    val sh = loop(ph, 1) * 0f
    for (k in 0 until n) {
      val x = (k + 0.5f + hr(k + layer * 9, 1) * 0.4f) / n * w + sh
      val th = h * (0.30f + 0.16f * hr(k + layer * 5, 2)) * (1f + layer * 0.12f)
      val base = h * (0.80f + layer * 0.06f)
      val bw = th * 0.38f
      val p = Path(); p.moveTo(x, base - th); p.lineTo(x + bw, base); p.lineTo(x - bw, base); p.close()
      drawPath(p, col)
      val p2 = Path(); p2.moveTo(x, base - th * 0.72f); p2.lineTo(x + bw * 1.15f, base - th * 0.12f); p2.lineTo(x - bw * 1.15f, base - th * 0.12f); p2.close()
      drawPath(p2, col)
    }
  }
  // niebla
  for (i in 0 until 3) {
    val x = ((loop(ph, 1, i * 0.33f)) * (w * 1.6f)) - w * 0.3f
    drawOval(Color(0x1AFFFFFF), Offset(x - w * 0.35f, h * (0.72f + i * 0.05f)), Size(w * 0.7f, h * 0.07f))
  }
  // luciérnagas
  for (i in 0 until 38) {
    val x = hr(i, 1) * w + sin(ph * (1 + i % 2) + i * 1.3f) * w * 0.06f
    val y = h * (0.45f + 0.5f * hr(i, 2)) + cos(ph * (1 + i % 3) + i) * h * 0.03f
    val b = 0.2f + 0.8f * abs(sin(ph * (1 + i % 3) + i * 2.1f))
    glow(Offset(x, y), 20f, Color(0xFFD4FF5A), 0.55f * b)
    drawCircle(Color(0xFFF5FFB0).copy(alpha = b), 2.2f, Offset(x, y))
  }
}

// ───────────────────────── 5. Montañas nevadas ─────────────────────────
private fun DrawScope.sceneSnow(ph: Float) {
  val w = size.width; val h = size.height
  sky(Color(0xFF123A6B), Color(0xFF5B8FC7), Color(0xFFE6D9F2), h * 0.62f)
  glow(Offset(w * 0.8f, h * 0.2f), w * 0.4f, Color(0xFFFFE9B0), 0.5f)
  drawCircle(Color(0xFFFFF3C4), w * 0.045f, Offset(w * 0.8f, h * 0.2f))
  // nubes lentas
  for (i in 0 until 4) {
    val x = ((loop(ph, 1, i * 0.25f)) * (w + 260f)) - 130f
    val y = h * (0.12f + 0.07f * i)
    for (k in 0 until 4) drawCircle(Color(0x55FFFFFF), 24f + k * 5f, Offset(x + k * 30f, y + (k % 2) * 6f))
  }
  ridge(h * 0.60f, h * 0.20f, 0.05f, Color(0xFF8FA8D0), 2, 1.6f)
  ridge(h * 0.70f, h * 0.17f, 0.35f, Color(0xFFB9C9E6), 7, 1.2f)
  ridge(h * 0.82f, h * 0.12f, 0.62f, Color(0xFFEAF2FF), 4, 0.9f)
  drawRect(Color(0xFFF4F8FF), Offset(0f, h * 0.9f), Size(w, h * 0.1f))
  // pinos
  for (k in 0 until 8) {
    val x = (k + 0.4f) / 8f * w; val base = h * 0.93f; val th = h * (0.10f + 0.05f * hr(k, 1))
    val p = Path(); p.moveTo(x, base - th); p.lineTo(x + th * 0.3f, base); p.lineTo(x - th * 0.3f, base); p.close()
    drawPath(p, Color(0xFF1F5B46))
  }
  // nieve
  for (i in 0 until 90) {
    val t = loop(ph, 2 + i % 3, hr(i, 1))
    val x = hr(i, 2) * w + sin(ph * 2f + i) * 18f
    val y = -10f + t * (h + 20f)
    drawCircle(Color.White.copy(alpha = 0.85f), 1.4f + hr(i, 3) * 2.8f, Offset(x, y))
  }
}

// ───────────────────────── 6. Sakura (Japón al atardecer) ─────────────────────────
private fun DrawScope.sceneSakura(ph: Float) {
  val w = size.width; val h = size.height
  sky(Color(0xFF3A1B6B), Color(0xFFE85D9A), Color(0xFFFFC38A), h * 0.7f)
  glow(Offset(w * 0.28f, h * 0.3f), w * 0.5f, Color(0xFFFFE0B8), 0.5f)
  drawCircle(Color(0xFFFFF3E0), w * 0.09f, Offset(w * 0.28f, h * 0.3f))
  ridge(h * 0.66f, h * 0.14f, 0.15f, Color(0xFF5A2A6E), 1, 0.7f)
  // torii
  val tx = w * 0.72f; val ty = h * 0.72f; val tw = w * 0.2f; val th = h * 0.2f
  val red = Color(0xFFC62828)
  drawRect(red, Offset(tx - tw * 0.42f, ty - th), Size(tw * 0.07f, th))
  drawRect(red, Offset(tx + tw * 0.35f, ty - th), Size(tw * 0.07f, th))
  drawRect(red, Offset(tx - tw * 0.55f, ty - th * 1.02f), Size(tw * 1.1f, th * 0.09f))
  drawRect(red, Offset(tx - tw * 0.45f, ty - th * 0.82f), Size(tw * 0.9f, th * 0.06f))
  drawRect(Color(0xFF1A0B2E), Offset(0f, h * 0.84f), Size(w, h * 0.16f))
  // rama con flores arriba
  val branch = Path(); branch.moveTo(w * 1.02f, h * 0.02f); branch.quadraticBezierTo(w * 0.7f, h * 0.1f, w * 0.45f, h * 0.06f)
  drawPath(branch, Color(0xFF2A1218), style = Stroke(10f, cap = StrokeCap.Round))
  for (i in 0 until 26) {
    val t = i / 26f
    val x = w * (1.0f - 0.55f * t) + sin(i * 3f) * 18f
    val y = h * (0.03f + 0.07f * sin(t * 3f)) + cos(i * 2f) * 14f
    drawCircle(Color(0xFFFFB7D5).copy(alpha = 0.95f), 9f + hr(i, 2) * 6f, Offset(x, y))
    drawCircle(Color(0xFFFF8FB8), 3f, Offset(x, y))
  }
  // pétalos cayendo (viento)
  for (i in 0 until 55) {
    val t = loop(ph, 1 + i % 3, hr(i, 1))
    val x = w * 1.05f - t * w * 1.2f + hr(i, 2) * w * 0.3f + sin(ph * 3f + i) * 24f
    val y = -10f + t * h * 1.05f
    val s = 5f + hr(i, 4) * 6f
    drawOval(Color(0xFFFFB7D5).copy(alpha = 0.9f), Offset(x, y), Size(s * 1.5f, s))
  }
}

// ───────────────────────── 7. Lluvia nocturna ─────────────────────────
private fun DrawScope.sceneRain(ph: Float) {
  val w = size.width; val h = size.height
  sky(Color(0xFF05070F), Color(0xFF14203A), Color(0xFF1E3358), h * 0.8f)
  // ventanas de edificios lejanos desenfocadas
  for (i in 0 until 26) {
    val x = hr(i, 1) * w; val y = h * (0.35f + 0.4f * hr(i, 2))
    val c = when (i % 4) { 0 -> Color(0xFFFFD166); 1 -> Color(0xFF37E6FF); 2 -> Color(0xFFFF4D8D); else -> Color(0xFFB388FF) }
    glow(Offset(x, y), 30f + hr(i, 3) * 30f, c, 0.28f)
    drawCircle(c.copy(alpha = 0.5f), 4f, Offset(x, y))
  }
  // relámpago ocasional
  val lf = (sin(ph * 2f) * sin(ph * 2f + 1.1f)).let { if (it > 0.96f) (it - 0.96f) / 0.04f else 0f }
  if (lf > 0f) drawRect(Color.White.copy(alpha = 0.25f * lf), Offset.Zero, Size(w, h))
  // suelo mojado con reflejos
  drawRect(Brush.verticalGradient(listOf(Color(0xFF0B1224), Color(0xFF05070F)), h * 0.84f, h), Offset(0f, h * 0.84f), Size(w, h * 0.16f))
  // gotas
  for (i in 0 until 120) {
    val t = loop(ph, 3 + i % 4, hr(i, 1))
    val x = hr(i, 2) * w
    val y = -30f + t * (h * 0.9f + 30f)
    drawLine(Color(0xFFBFD4FF).copy(alpha = 0.45f), Offset(x, y), Offset(x - 5f, y + 22f + 14f * hr(i, 3)), 1.4f)
  }
  // ondas en el suelo
  for (i in 0 until 14) {
    val t = loop(ph, 4, hr(i, 6))
    val x = hr(i, 7) * w; val y = h * (0.87f + 0.1f * hr(i, 8))
    drawOval(Color(0xFFBFD4FF).copy(alpha = 0.5f * (1f - t)), Offset(x - 26f * t, y - 5f * t), Size(52f * t, 10f * t), style = Stroke(1.3f))
  }
}

// ───────────────────────── 8. Desierto ─────────────────────────
private fun DrawScope.sceneDesert(ph: Float) {
  val w = size.width; val h = size.height
  sky(Color(0xFF3A1C71), Color(0xFFD76D77), Color(0xFFFFAF7B), h * 0.6f)
  val sc = Offset(w * 0.5f, h * 0.5f)
  glow(sc, w * 0.6f, Color(0xFFFFE08A), 0.5f)
  drawCircle(Color(0xFFFFF0B3), w * 0.12f, sc)
  // dunas con ondulación
  for (layer in 0..3) {
    val col = when (layer) { 0 -> Color(0xFFE9A15B); 1 -> Color(0xFFD7863F); 2 -> Color(0xFFB76A2F); else -> Color(0xFF8A4A20) }
    ridge(h * (0.62f + layer * 0.09f), h * (0.08f + layer * 0.015f), 0.1f + layer * 0.27f + sin(ph) * 0.004f, col, layer + 11, 0.4f)
  }
  // cactus
  val cx = w * 0.2f; val cy = h * 0.80f
  val cac = Color(0xFF1F4D2B)
  drawRect(cac, Offset(cx, cy - h * 0.1f), Size(w * 0.025f, h * 0.1f))
  drawRect(cac, Offset(cx - w * 0.03f, cy - h * 0.07f), Size(w * 0.02f, h * 0.035f))
  drawRect(cac, Offset(cx + w * 0.035f, cy - h * 0.085f), Size(w * 0.02f, h * 0.035f))
  // polvo arrastrado por el viento
  for (i in 0 until 50) {
    val t = loop(ph, 2 + i % 2, hr(i, 1))
    val x = -20f + t * (w + 40f)
    val y = h * (0.7f + 0.28f * hr(i, 2)) + sin(ph * 3f + i) * 8f
    drawCircle(Color(0xFFFFE0B2).copy(alpha = 0.25f), 1.5f + hr(i, 3) * 3f, Offset(x, y))
  }
  // aves
  for (i in 0 until 3) {
    val t = loop(ph, 1, i * 0.3f)
    val x = t * (w + 80f) - 40f; val y = h * (0.15f + 0.05f * i) + sin(ph * 4f + i) * 6f
    val wing = sin(ph * 14f + i) * 5f
    drawLine(Color(0xFF2A1033), Offset(x - 9f, y - wing), Offset(x, y), 2f)
    drawLine(Color(0xFF2A1033), Offset(x, y), Offset(x + 9f, y - wing), 2f)
  }
}

// ───────────────────────── 9. Espacio profundo ─────────────────────────
private fun DrawScope.sceneSpace(ph: Float) {
  val w = size.width; val h = size.height
  drawRect(Color(0xFF02010A), Offset.Zero, Size(w, h))
  glow(Offset(w * 0.25f, h * 0.3f), w * 0.7f, Color(0xFF7C4DFF), 0.30f + 0.05f * sin(ph))
  glow(Offset(w * 0.8f, h * 0.65f), w * 0.6f, Color(0xFF00E5FF), 0.20f + 0.05f * sin(ph + 2f))
  glow(Offset(w * 0.5f, h * 0.9f), w * 0.5f, Color(0xFFFF4D8D), 0.18f)
  for (i in 0 until 110) {
    val layer = i % 3
    val t = loop(ph, 1 + layer, hr(i, 1))
    val x = hr(i, 2) * w
    val y = t * h
    val tw = 0.4f + 0.6f * abs(sin(ph * 2f + i))
    drawCircle(Color.White.copy(alpha = 0.8f * tw), 0.6f + layer * 0.7f, Offset(x, y))
  }
  // planeta con anillo
  val pc = Offset(w * 0.72f, h * 0.24f); val pr = w * 0.13f
  drawCircle(Brush.radialGradient(listOf(Color(0xFFFFC27A), Color(0xFFC9692B), Color(0xFF4A1E0B)), Offset(pc.x - pr * 0.4f, pc.y - pr * 0.4f), pr * 1.5f), pr, pc)
  drawOval(Color(0xFFFFE0B2).copy(alpha = 0.7f), Offset(pc.x - pr * 1.7f, pc.y - pr * 0.28f), Size(pr * 3.4f, pr * 0.56f), style = Stroke(5f))
  // luna pequeña orbitando
  val ox = pc.x + cos(ph) * pr * 1.9f; val oy = pc.y + sin(ph) * pr * 0.5f
  drawCircle(Color(0xFFD7E3FF), pr * 0.14f, Offset(ox, oy))
  // estrellas fugaces
  for (i in 0 until 3) {
    val t = loop(ph, 2, i * 0.33f)
    if (t < 0.25f) {
      val k = t / 0.25f
      val sx = w * (0.1f + 0.3f * i) + k * w * 0.4f; val sy = h * (0.08f + 0.1f * i) + k * h * 0.25f
      drawLine(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 1f - k)), Offset(sx - 70f, sy - 40f), Offset(sx, sy)), Offset(sx - 70f, sy - 40f), Offset(sx, sy), 2.6f)
    }
  }
}

// ───────────────────────── 10. Volcán ─────────────────────────
private fun DrawScope.sceneVolcano(ph: Float) {
  val w = size.width; val h = size.height
  sky(Color(0xFF14040A), Color(0xFF5E1010), Color(0xFFFF6A1A), h * 0.7f)
  val vx = w * 0.5f; val vy = h * 0.42f
  glow(Offset(vx, vy), w * 0.7f, Color(0xFFFF6A00), 0.45f + 0.08f * sin(ph * 2f))
  // humo
  for (i in 0 until 6) {
    val t = loop(ph, 1, i / 6f)
    drawCircle(Color(0xFF2A1A1A).copy(alpha = 0.55f * (1f - t)), w * (0.05f + 0.12f * t), Offset(vx + sin(i * 2f) * 20f * t + t * 30f, vy - t * h * 0.35f))
  }
  val v = Path(); v.moveTo(vx - w * 0.5f, h); v.lineTo(vx - w * 0.07f, vy); v.lineTo(vx + w * 0.07f, vy); v.lineTo(vx + w * 0.5f, h); v.close()
  drawPath(v, Brush.verticalGradient(listOf(Color(0xFF2A0D0D), Color(0xFF120505)), vy, h))
  // ríos de lava
  for (k in 0 until 3) {
    val p = Path(); p.moveTo(vx + (k - 1) * w * 0.03f, vy)
    p.quadraticBezierTo(vx + (k - 1) * w * 0.2f, h * 0.65f, vx + (k - 1) * w * 0.38f, h)
    drawPath(p, Color(0xFFFF8A1A).copy(alpha = 0.75f + 0.2f * sin(ph * 3f + k)), style = Stroke(6f, cap = StrokeCap.Round))
  }
  drawRect(Color(0xFF0A0303), Offset(0f, h * 0.92f), Size(w, h * 0.08f))
  // brasas
  for (i in 0 until 70) {
    val t = loop(ph, 1 + i % 3, hr(i, 1))
    val x = vx + (hr(i, 2) - 0.5f) * w * 0.5f + sin(ph * 2f + i) * 16f * t + (hr(i, 5) - 0.5f) * w * 0.6f * t
    val y = vy - t * h * 0.5f
    drawCircle(Color(0xFFFFB347).copy(alpha = 1f - t), 1.4f + hr(i, 3) * 2.6f * (1f - t), Offset(x, y))
  }
}

// ───────────────────────── 11. Aurora boreal ─────────────────────────
private fun DrawScope.sceneBoreal(ph: Float) {
  val w = size.width; val h = size.height
  sky(Color(0xFF020817), Color(0xFF07243A), Color(0xFF0B3B4A), h * 0.75f)
  for (i in 0 until 60) star(i, ph, h * 0.5f)
  val cols = listOf(Color(0xFF00E5A0), Color(0xFF37E6FF), Color(0xFFB388FF), Color(0xFF7CFF6B))
  for (b in 0 until 4) {
    val p = Path(); p.moveTo(0f, h * 0.62f)
    var x = 0f
    while (x <= w + 16f) {
      val u = x / w
      p.lineTo(x, h * (0.20f + b * 0.07f) + sin(u * TWO_PI * 1f + ph + b * 1.4f) * h * 0.05f + sin(u * TWO_PI * 3f - ph * 2f + b) * h * 0.02f)
      x += 14f
    }
    p.lineTo(w, h * 0.66f); p.close()
    drawPath(p, Brush.verticalGradient(listOf(cols[b].copy(alpha = 0.55f), Color.Transparent), h * 0.12f, h * 0.7f))
  }
  ridge(h * 0.86f, h * 0.10f, 0.3f, Color(0xFF061A2B), 6, 1.2f)
  drawRect(Color(0xFFDDEFFF), Offset(0f, h * 0.91f), Size(w, h * 0.09f))
  // cabaña con luz
  drawRect(Color(0xFF2A1A12), Offset(w * 0.18f, h * 0.855f), Size(w * 0.1f, h * 0.06f))
  drawRect(Color(0xFFFFD166), Offset(w * 0.215f, h * 0.87f), Size(w * 0.025f, h * 0.025f))
  val roof = Path(); roof.moveTo(w * 0.17f, h * 0.855f); roof.lineTo(w * 0.23f, h * 0.82f); roof.lineTo(w * 0.29f, h * 0.855f); roof.close()
  drawPath(roof, Color(0xFFEAF4FF))
}

// ───────────────────────── 12. Casino Vegas ─────────────────────────
private fun DrawScope.sceneVegas(ph: Float) {
  val w = size.width; val h = size.height
  sky(Color(0xFF12020A), Color(0xFF4A0820), Color(0xFF12020A))
  glow(Offset(w * 0.5f, h * 0.4f), w * 0.8f, Color(0xFFFF2E63), 0.25f)
  // marco de bombillas que corren
  val n = 26
  for (i in 0 until n) {
    val on = ((i - (ph / TWO_PI * n * 2f).toInt()) % 3 + 3) % 3 == 0
    val c = if (on) Color(0xFFFFE08A) else Color(0xFF6B4A00)
    val x = w * (i + 0.5f) / n
    drawCircle(c, 4.5f, Offset(x, 14f)); drawCircle(c, 4.5f, Offset(x, h - 14f))
    if (on) { glow(Offset(x, 14f), 16f, Color(0xFFFFE08A), 0.6f); glow(Offset(x, h - 14f), 16f, Color(0xFFFFE08A), 0.6f) }
  }
  val m = 30
  for (i in 0 until m) {
    val on = ((i + (ph / TWO_PI * m * 2f).toInt()) % 3 + 3) % 3 == 0
    val c = if (on) Color(0xFFFFE08A) else Color(0xFF6B4A00)
    val y = h * (i + 0.5f) / m
    drawCircle(c, 4f, Offset(14f, y)); drawCircle(c, 4f, Offset(w - 14f, y))
  }
  // fichas, dados y cartas flotando
  for (i in 0 until 16) {
    val t = loop(ph, 1 + i % 3, hr(i, 1))
    val x = hr(i, 2) * w * 0.8f + w * 0.1f + sin(ph * 2f + i) * 20f
    val y = h * (1.1f - t * 1.25f)
    val s = 12f + 14f * hr(i, 3)
    when (i % 3) {
      0 -> { drawCircle(Color(0xFFD62839), s, Offset(x, y)); drawCircle(Color.White, s * 0.78f, Offset(x, y), style = Stroke(2.5f)); drawCircle(Color(0xFFFFE08A), s * 0.3f, Offset(x, y)) }
      1 -> { drawRect(Color.White.copy(alpha = 0.92f), Offset(x - s * 0.7f, y - s * 0.7f), Size(s * 1.4f, s * 1.4f)); drawCircle(Color.Black, s * 0.14f, Offset(x - s * 0.3f, y - s * 0.3f)); drawCircle(Color.Black, s * 0.14f, Offset(x + s * 0.3f, y + s * 0.3f)); drawCircle(Color.Black, s * 0.14f, Offset(x, y)) }
      else -> { drawRect(Color.White.copy(alpha = 0.92f), Offset(x - s * 0.6f, y - s * 0.85f), Size(s * 1.2f, s * 1.7f)); drawCircle(Color(0xFFD62839), s * 0.28f, Offset(x, y)) }
    }
  }
}


// ═══════════════════════════════════════════════════════════════════════════════
//  10 TEMAS NUEVOS (con más movimiento)
// ═══════════════════════════════════════════════════════════════════════════════

// ───────────────────────── 13. Nebulosa cósmica ─────────────────────────
private fun DrawScope.sceneNebula(ph: Float) {
  val w = size.width; val h = size.height
  sky(Color(0xFF05021A), Color(0xFF150B40), Color(0xFF2B1055))
  val cols = listOf(Color(0xFFFF4DA6), Color(0xFF6C5CE7), Color(0xFF00D2FF), Color(0xFFB388FF), Color(0xFFFF8A65), Color(0xFF2EE6A8))
  for (i in 0 until 8) {
    val a = ph + i * 0.8f
    val c = Offset(w * (0.5f + 0.40f * sin(a + i)), h * (0.12f + 0.76f * hr(i, 4)) + 26f * cos(a * 2f + i))
    glow(c, w * (0.38f + 0.10f * sin(ph * 3f + i)), cols[i % cols.size], 0.20f)
  }
  for (i in 0 until 110) star(i, ph * 2f, h)
  val t = loop(ph, 3)
  if (t < 0.28f) {
    val k = t / 0.28f
    val x = w * (1.05f - 0.6f * k); val y = h * (0.10f + 0.30f * k)
    drawLine(Color.White.copy(alpha = 1f - k), Offset(x, y), Offset(x + 110f, y - 55f), 3f, StrokeCap.Round)
  }
}

// ───────────────────────── 14. Lluvia digital ─────────────────────────
private fun DrawScope.sceneMatrix(ph: Float) {
  val w = size.width; val h = size.height
  sky(Color(0xFF000A04), Color(0xFF001A0A), Color(0xFF00110A))
  val cols = 22; val cw = w / cols
  val tick = (ph * 60f / TWO_PI).toInt()
  for (i in 0 until cols) {
    val x = (i + 0.5f) * cw
    val head = loop(ph, 6 + i % 5, hr(i, 1)) * (h + 360f) - 180f
    for (j in 0 until 16) {
      val y = head - j * 24f
      if (y < -20f || y > h + 20f) continue
      val a = 1f - j / 16f
      val col = if (j == 0) Color(0xFFD7FFE3) else Color(0xFF19F56B)
      val fl = 0.55f + 0.45f * hr(i * 31 + j, tick)
      drawRoundRect(col.copy(alpha = (0.15f + 0.75f * a) * fl), Offset(x - cw * 0.22f, y), Size(cw * 0.44f, 18f), CornerRadius(3f))
    }
  }
}

// ───────────────────────── 15. Medusas de luz ─────────────────────────
private fun DrawScope.sceneJelly(ph: Float) {
  val w = size.width; val h = size.height
  sky(Color(0xFF020B2A), Color(0xFF07206B), Color(0xFF0B4A8A))
  for (k in 0 until 4) {
    val x = w * (0.1f + 0.28f * k) + sin(ph + k) * 30f
    val p = Path(); p.moveTo(x - 10f, 0f); p.lineTo(x + 10f, 0f); p.lineTo(x + 90f, h); p.lineTo(x - 60f, h); p.close()
    drawPath(p, Brush.verticalGradient(listOf(Color(0x2266D9FF), Color.Transparent), 0f, h))
  }
  for (i in 0 until 40) {
    val t = loop(ph, 2 + i % 3, hr(i, 1))
    drawCircle(Color(0x66BFE9FF), 1.5f + hr(i, 3) * 3f, Offset(hr(i, 2) * w + sin(ph * 2f + i) * 10f, h * (1.05f - 1.1f * t)), style = Stroke(1.2f))
  }
  val jc = listOf(Color(0xFFFF6FD8), Color(0xFF7CF7FF), Color(0xFFB388FF), Color(0xFFFFB86C), Color(0xFF7CFFB2))
  for (i in 0 until 7) {
    val col = jc[i % jc.size]
    val r = w * (0.07f + 0.05f * hr(i, 5))
    val cx = w * (0.12f + 0.76f * hr(i, 1)) + sin(ph + i * 1.7f) * w * 0.05f
    val cy = h * (0.15f + 0.65f * hr(i, 2)) + cos(ph * 2f + i) * h * 0.04f
    val pulse = 1f + 0.12f * sin(ph * 12f + i)
    glow(Offset(cx, cy), r * 3.2f, col, 0.28f)
    for (k in 0 until 6) {
      val bx = cx - r * 0.7f + k * r * 0.28f
      var px = bx; var py = cy
      for (st in 1..9) {
        val nx = bx + sin(ph * 6f + st * 0.7f + k + i) * r * 0.22f * (st / 9f)
        val ny = cy + st * r * 0.34f * pulse
        drawLine(col.copy(alpha = 0.55f * (1f - st / 10f)), Offset(px, py), Offset(nx, ny), 2.2f, StrokeCap.Round)
        px = nx; py = ny
      }
    }
    drawArc(col.copy(alpha = 0.85f), 180f, 180f, true, Offset(cx - r, cy - r * pulse), Size(r * 2f, r * 2f * pulse))
    drawArc(Color.White.copy(alpha = 0.35f), 200f, 70f, false, Offset(cx - r * 0.7f, cy - r * 0.8f * pulse), Size(r * 1.4f, r * 1.4f * pulse), style = Stroke(3f, cap = StrokeCap.Round))
  }
}

// ───────────────────────── 16. Fuegos artificiales ─────────────────────────
private fun DrawScope.sceneFireworks(ph: Float) {
  val w = size.width; val h = size.height
  sky(Color(0xFF050818), Color(0xFF14123A), Color(0xFF2A1A4A), h * 0.9f)
  for (i in 0 until 70) star(i, ph * 2f, h * 0.7f)
  val fc = listOf(Color(0xFFFF5C8A), Color(0xFFFFD166), Color(0xFF5CE1FF), Color(0xFF9DFF7A), Color(0xFFC792FF), Color(0xFFFF8A4C))
  for (f in 0 until 6) {
    val t = loop(ph, 4, f / 6f)
    val rep = floor(ph / TWO_PI * 4f + f / 6f).toInt() % 4
    val bx = w * (0.14f + 0.72f * hr(f * 7 + rep, 1)); val by = h * (0.14f + 0.30f * hr(f * 7 + rep, 2))
    val col = fc[(f + rep) % fc.size]
    if (t < 0.22f) {
      val k = t / 0.22f
      val y = h * 0.86f + (by - h * 0.86f) * k
      drawLine(col.copy(alpha = 0.9f), Offset(bx, y), Offset(bx, y + 40f), 3f, StrokeCap.Round)
      glow(Offset(bx, y), 16f, col, 0.6f)
    } else {
      val k = (t - 0.22f) / 0.78f
      val rad = w * 0.30f * (1f - (1f - k) * (1f - k))
      val a = (1f - k).coerceIn(0f, 1f)
      glow(Offset(bx, by), rad * 1.2f + 1f, col, 0.18f * a)
      for (sp in 0 until 28) {
        val ang = sp * TWO_PI / 28f + f
        drawCircle(col.copy(alpha = a), 3f * a + 0.8f, Offset(bx + cos(ang) * rad, by + sin(ang) * rad + k * k * 60f))
        drawCircle(col.copy(alpha = a * 0.4f), 2f * a + 0.6f, Offset(bx + cos(ang) * rad * 0.86f, by + sin(ang) * rad * 0.86f + k * k * 50f))
      }
    }
  }
  for (k in 0 until 14) {
    val bw = w / 14f; val bh = h * (0.06f + 0.10f * hr(k, 3))
    drawRect(Color(0xFF0A0A18), Offset(k * bw, h - bh), Size(bw + 1f, bh))
    for (q in 0 until 3) if (hr(k, 10 + q) > 0.5f) drawRect(Color(0xCCFFD98A), Offset(k * bw + bw * 0.25f, h - bh + 10f + q * 18f), Size(6f, 8f))
  }
}

// ───────────────────────── 17. Tormenta eléctrica ─────────────────────────
private fun DrawScope.sceneStorm(ph: Float) {
  val w = size.width; val h = size.height
  val fl = ((sin(ph * 3f) - 0.93f) / 0.07f).coerceIn(0f, 1f)
  sky(Color(0xFF0B1020), Color(0xFF1B2540), Color(0xFF2C3B5A))
  if (fl > 0f) drawRect(Color(0xFFCFE4FF).copy(alpha = 0.28f * fl))
  for (i in 0 until 7) {
    val x = loop(ph, 1, i / 7f) * (w + 400f) - 200f; val y = h * (0.06f + 0.20f * hr(i, 1))
    for (k in 0 until 6) drawCircle(Color(0xD90E1428), 60f + 14f * k, Offset(x + k * 52f, y + (k % 2) * 16f))
  }
  if (fl > 0.05f) {
    val kk = (ph * 3f / TWO_PI).toInt()
    var x = w * (0.2f + 0.6f * hr(kk, 5)); var y = h * 0.18f
    val p = Path(); p.moveTo(x, y)
    for (st in 0 until 9) { x += (hr(kk * 13 + st, 6) - 0.5f) * 70f; y += h * 0.07f; p.lineTo(x, y) }
    drawPath(p, Color(0xFF9CC8FF).copy(alpha = 0.5f * fl), style = Stroke(14f, cap = StrokeCap.Round))
    drawPath(p, Color.White.copy(alpha = fl), style = Stroke(5f, cap = StrokeCap.Round))
  }
  ridge(h * 0.95f, h * 0.08f, 0.2f, Color(0xFF060A14), 3)
  for (i in 0 until 110) {
    val t = loop(ph, 14 + i % 6, hr(i, 1))
    val x = hr(i, 2) * (w + 120f) + 60f - t * 60f; val y = -20f + t * (h + 40f)
    drawLine(Color(0x99BFD4FF), Offset(x, y), Offset(x - 8f, y + 24f), 1.6f)
  }
}

// ───────────────────────── 18. Otoño dorado ─────────────────────────
private fun DrawScope.sceneAutumn(ph: Float) {
  val w = size.width; val h = size.height
  sky(Color(0xFF4A2A5C), Color(0xFFE58A4B), Color(0xFFFFD58A), h * 0.7f)
  glow(Offset(w * 0.3f, h * 0.60f), w * 0.6f, Color(0xFFFFE0A0), 0.5f)
  drawCircle(Color(0xFFFFF0C8), w * 0.07f, Offset(w * 0.3f, h * 0.56f))
  ridge(h * 0.66f, h * 0.18f, 0.1f, Color(0xFF8A4B2A), 3, 1.1f)
  ridge(h * 0.76f, h * 0.15f, 0.4f, Color(0xFF6A3320), 8, 1.0f)
  ridge(h * 0.88f, h * 0.10f, 0.7f, Color(0xFF3F1F14), 5, 0.8f)
  val lc = listOf(Color(0xFFFF8F1F), Color(0xFFE53935), Color(0xFFFFC107), Color(0xFFBF360C), Color(0xFFFF6F00))
  for (i in 0 until 48) {
    val t = loop(ph, 1 + i % 3, hr(i, 1))
    val x = hr(i, 2) * w + sin(ph * 2f + i) * 40f - t * 50f
    val y = -20f + t * (h + 40f); val r = 5f + hr(i, 3) * 7f
    rotate(t * 720f + i * 30f, Offset(x, y)) {
      drawOval(lc[i % lc.size].copy(alpha = 0.92f), Offset(x - r, y - r * 0.55f), Size(r * 2f, r * 1.1f))
    }
  }
}

// ───────────────────────── 19. Arcade retro ─────────────────────────
private val INVADER_A = listOf("..X.....X..", "...X...X...", "..XXXXXXX..", ".XX.XXX.XX.", "XXXXXXXXXXX", "X.XXXXXXX.X", "X.X.....X.X", "...XX.XX...")
private val INVADER_B = listOf("..X.....X..", "X..X...X..X", "X.XXXXXXX.X", "XXX.XXX.XXX", "XXXXXXXXXXX", ".XXXXXXXXX.", "..X.....X..", ".X.......X.")

private fun DrawScope.sceneArcade(ph: Float) {
  val w = size.width; val h = size.height
  sky(Color(0xFF0A0420), Color(0xFF1B0B3E), Color(0xFF2B0F54))
  for (i in 0 until 70) {
    val sp = 1 + i % 3
    drawRect(Color.White.copy(alpha = 0.25f + 0.25f * sp), Offset(hr(i, 2) * w, loop(ph, 6 * sp, hr(i, 1)) * h), Size(2f * sp, 2f * sp))
  }
  val frame = if (sin(ph * 10f) > 0f) INVADER_A else INVADER_B
  val ps = w / 62f
  val gx = sin(ph) * w * 0.08f
  val inv = listOf(Color(0xFF39FF14), Color(0xFFFF2E93), Color(0xFF00E5FF))
  for (r in 0 until 3) for (c in 0 until 4) {
    val ox = w * (0.08f + c * 0.22f) + gx; val oy = h * (0.10f + r * 0.09f) + cos(ph * 2f) * 6f
    for ((yy, row) in frame.withIndex()) for (xx in row.indices) if (row[xx] == 'X')
      drawRect(inv[r].copy(alpha = 0.85f), Offset(ox + xx * ps, oy + yy * ps), Size(ps - 1f, ps - 1f))
  }
  val shipX = w * (0.5f + 0.3f * sin(ph * 2f))
  val t = loop(ph, 8)
  val x0 = w * (0.5f + 0.3f * sin((ph - t * TWO_PI / 8f) * 2f))
  drawRect(Color(0xFFFFF176), Offset(x0 - 2f, h * 0.88f - t * h * 0.78f), Size(4f, 16f))
  drawRect(Color(0xFF00E5FF), Offset(shipX - ps * 3f, h * 0.9f), Size(ps * 6f, ps * 1.6f))
  drawRect(Color(0xFF00E5FF), Offset(shipX - ps, h * 0.9f - ps * 1.4f), Size(ps * 2f, ps * 1.4f))
  drawRect(Color(0x5500E5FF), Offset(0f, h * 0.935f), Size(w, 2f))
}

// ───────────────────────── 20. Farolitos flotantes ─────────────────────────
private fun DrawScope.sceneLanterns(ph: Float) {
  val w = size.width; val h = size.height
  sky(Color(0xFF080A2E), Color(0xFF1C1B5A), Color(0xFF3B2A6E), h * 0.8f)
  for (i in 0 until 80) star(i, ph * 2f, h * 0.6f)
  glow(Offset(w * 0.78f, h * 0.16f), w * 0.3f, Color(0xFFFFF3C4), 0.35f)
  drawCircle(Color(0xFFFFF6D6), w * 0.05f, Offset(w * 0.78f, h * 0.16f))
  drawRect(Brush.verticalGradient(listOf(Color(0xFF14123A), Color(0xFF060619)), h * 0.82f, h), Offset(0f, h * 0.82f), Size(w, h * 0.18f))
  for (i in 0 until 34) {
    val depth = hr(i, 4)
    val t = loop(ph, 1, hr(i, 1))
    val x = hr(i, 2) * w + sin(ph * 2f + i) * (10f + 14f * depth)
    val y = h * (0.95f - 1.1f * t)
    val s = 9f + 16f * depth
    val flick = 0.85f + 0.15f * sin(ph * 14f + i)
    glow(Offset(x, y), s * 3.2f, Color(0xFFFFB347), 0.45f * flick)
    drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFFFE08A), Color(0xFFFF8A3D)), y - s, y + s), Offset(x - s * 0.7f, y - s), Size(s * 1.4f, s * 1.9f), CornerRadius(s * 0.3f))
    if (y < h * 0.82f) drawCircle(Color(0x33FFB347), s * 0.8f, Offset(x, h * 0.82f + (h * 0.82f - y) * 0.12f))
  }
}

// ───────────────────────── 21. Autopista neón ─────────────────────────
private fun DrawScope.sceneHighway(ph: Float) {
  val w = size.width; val h = size.height; val hor = h * 0.40f
  sky(Color(0xFF12052E), Color(0xFF6A1B7A), Color(0xFFFF7A59), hor)
  glow(Offset(w * 0.5f, hor), w * 0.55f, Color(0xFFFFB06B), 0.45f)
  drawCircle(Brush.verticalGradient(listOf(Color(0xFFFFE066), Color(0xFFFF4D8D)), hor - w * 0.2f, hor), w * 0.2f, Offset(w * 0.5f, hor - w * 0.04f))
  for (k in 0 until 16) {
    val bw = w / 16f; val bh = h * (0.03f + 0.07f * hr(k, 1))
    drawRect(Color(0xFF1A0838), Offset(k * bw, hor - bh), Size(bw + 1f, bh))
  }
  drawRect(Brush.verticalGradient(listOf(Color(0xFF15091F), Color(0xFF0A0510)), hor, h), Offset(0f, hor), Size(w, h - hor))
  val topW = w * 0.06f; val botW = w * 1.25f
  fun roadW(f: Float) = topW + (botW - topW) * f
  fun fOf(q: Float) = q * q
  val road = Path()
  road.moveTo(w / 2f - topW / 2f, hor); road.lineTo(w / 2f + topW / 2f, hor); road.lineTo(w / 2f + botW / 2f, h); road.lineTo(w / 2f - botW / 2f, h); road.close()
  drawPath(road, Color(0xFF1B0F2E))
  drawLine(Color(0xFFFF2E93), Offset(w / 2f - topW / 2f, hor), Offset(w / 2f - botW / 2f, h), 5f)
  drawLine(Color(0xFF00E5FF), Offset(w / 2f + topW / 2f, hor), Offset(w / 2f + botW / 2f, h), 5f)
  for (lane in listOf(-0.17f, 0.17f)) for (k in 0 until 12) {
    val f = fOf(loop(ph, 40, k / 12f)); val y = hor + (h - hor) * f
    val x = w / 2f + lane * roadW(f)
    drawLine(Color.White.copy(alpha = 0.25f + 0.6f * f), Offset(x, y), Offset(x + lane * 30f * f, y + (h - hor) * 0.06f * (f + 0.15f)), 1.5f + 6f * f, StrokeCap.Round)
  }
  for (k in 0 until 8) {
    val f = fOf(loop(ph, 24, k / 8f)); val y = hor + (h - hor) * f
    val col = if (k % 2 == 0) Color(0xFFFF2E93) else Color(0xFF00E5FF)
    for (side in listOf(-1f, 1f)) {
      val x = w / 2f + side * (roadW(f) / 2f + 10f + 26f * f)
      glow(Offset(x, y - 20f * f), 14f + 40f * f, col, 0.55f)
      drawCircle(Color.White, 1.5f + 4f * f, Offset(x, y - 20f * f))
    }
  }
  for (i in 0 until 6) {
    val lane = listOf(-0.34f, 0f, 0.34f)[i % 3]
    val oncoming = i % 2 == 0
    val t = loop(ph, 4 + i % 3, i * 0.19f)
    val f = fOf(if (oncoming) t else 1f - t)
    val y = hor + (h - hor) * f; val x = w / 2f + lane * roadW(f)
    val cw = 6f + 70f * f
    val lightCol = if (oncoming) Color(0xFFFFF3C4) else Color(0xFFFF3B3B)
    drawRoundRect(Color(0xFF0B0612), Offset(x - cw / 2f, y - cw * 0.35f), Size(cw, cw * 0.4f), CornerRadius(cw * 0.1f))
    for (sd in listOf(-0.36f, 0.36f)) {
      glow(Offset(x + sd * cw, y - cw * 0.17f), cw * 0.7f + 4f, lightCol, 0.7f)
      drawCircle(lightCol, cw * 0.06f + 1f, Offset(x + sd * cw, y - cw * 0.17f))
    }
  }
}

// ───────────────────────── 22. Galaxia espiral ─────────────────────────
private fun DrawScope.sceneGalaxy(ph: Float) {
  val w = size.width; val h = size.height
  sky(Color(0xFF02030F), Color(0xFF0A0B2A), Color(0xFF150A3A))
  for (i in 0 until 90) star(i, ph * 2f, h)
  val c = Offset(w * 0.5f, h * 0.42f)
  glow(c, w * 0.55f, Color(0xFF7B4DFF), 0.28f)
  glow(c, w * 0.18f, Color(0xFFFFE9B0), 0.55f)
  for (i in 0 until 240) {
    val arm = i % 3
    val r = w * 0.04f + hr(i, 1) * w * 0.46f
    val ang = r / w * 7.5f + arm * 2.0944f + ph + (hr(i, 2) - 0.5f) * 0.5f
    val k = (r / (w * 0.5f)).coerceIn(0f, 1f)
    val col = if (k < 0.3f) Color(0xFFFFE9B0) else if (k < 0.65f) Color(0xFFB9A6FF) else Color(0xFF7FB6FF)
    drawCircle(col.copy(alpha = 0.55f + 0.3f * hr(i, 3)), 0.9f + hr(i, 4) * 1.9f, Offset(c.x + cos(ang) * r, c.y + sin(ang) * r * 0.55f))
  }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  API pública
// ═══════════════════════════════════════════════════════════════════════════════

/** Dibuja la escena `name` (nombre de BgStyle) en el instante `ph` (0..2π). Devuelve false si no es una escena. */
fun DrawScope.drawSceneByName(name: String, ph: Float): Boolean {
  when (name) {
    "CITY" -> sceneCity(ph)
    "SYNTH" -> sceneSynth(ph)
    "DEEP" -> sceneDeep(ph)
    "FOREST" -> sceneForest(ph)
    "SNOW" -> sceneSnow(ph)
    "SAKURA" -> sceneSakura(ph)
    "RAIN" -> sceneRain(ph)
    "DESERT" -> sceneDesert(ph)
    "SPACE" -> sceneSpace(ph)
    "VOLCANO" -> sceneVolcano(ph)
    "BOREAL" -> sceneBoreal(ph)
    "VEGAS" -> sceneVegas(ph)
    "NEBULA" -> sceneNebula(ph)
    "MATRIX" -> sceneMatrix(ph)
    "JELLY" -> sceneJelly(ph)
    "FIREWORKS" -> sceneFireworks(ph)
    "STORM" -> sceneStorm(ph)
    "AUTUMN" -> sceneAutumn(ph)
    "ARCADE" -> sceneArcade(ph)
    "LANTERNS" -> sceneLanterns(ph)
    "HIGHWAY" -> sceneHighway(ph)
    "GALAXY" -> sceneGalaxy(ph)
    else -> return false
  }
  // viñeta suave para que el texto de los menús se lea siempre
  drawRect(Brush.verticalGradient(listOf(Color(0x66000000), Color(0x22000000), Color(0x88000000))), Offset.Zero, Size(size.width, size.height))
  return true
}

/** Selector de temas visuales (miniaturas con la escena). Se usa en Configuración. */
@Composable
fun SceneThemePicker() {
  val ctx = LocalContext.current
  remember { BackgroundPrefs.ensureLoaded(ctx) }
  GlassCard(modifier = Modifier.fillMaxWidth(), highlight = true) {
    Text("🖼️ Temas visuales", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
    Text(
      "Escenas con imagen de fondo y movimiento detrás de toda la app: ciudad neón, océano, bosque, aurora, espacio, fuegos artificiales, tormenta, autopista y más.",
      color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp
    )
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      BgStyle.values().filter { it.isScene }.forEach { b ->
        val sel = BackgroundPrefs.style == b
        Column(
          Modifier.width(92.dp).clip(RoundedCornerShape(14.dp))
            .border(if (sel) 2.5.dp else 1.dp, if (sel) Color(0xFFFFD60A) else Color(0x44FFFFFF), RoundedCornerShape(14.dp))
            .clickable { BackgroundPrefs.set(ctx, b) },
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Canvas(Modifier.width(92.dp).height(128.dp)) { drawSceneByName(b.name, 1.1f + b.ordinal * 0.35f) }
          Text(
            "${b.emoji} ${b.label}", color = if (sel) Color(0xFFFFD60A) else Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
            maxLines = 2, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
          )
        }
      }
    }
    Spacer(Modifier.height(6.dp))
    Text(
      if (BackgroundPrefs.style.isScene) "Tema activo: ${BackgroundPrefs.style.emoji} ${BackgroundPrefs.style.label}" else "Tema activo: fondo animado «${BackgroundPrefs.style.label}»",
      color = Color(0xFFFFD60A), fontSize = 11.sp, fontWeight = FontWeight.Bold
    )
  }
}
