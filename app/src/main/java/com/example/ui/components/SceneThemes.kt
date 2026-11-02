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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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
      "Escenas con imagen de fondo y movimiento detrás de toda la app: ciudad neón, océano, bosque, aurora, espacio y más.",
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
