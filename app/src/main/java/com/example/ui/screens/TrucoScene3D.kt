package com.example.ui.screens

import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Mesa de Truco 100 % en 3D (proyección en perspectiva propia sobre Canvas): mesa de madera con paño, jugadores con cabeza, torso y brazos
 * sentados alrededor, cartas como planos 3D con su imagen real (se ven de espaldas hasta que se dan vuelta al caer sobre el paño),
 * y animaciones: cada mano toma la carta, la lanza en arco, la carta gira y cae con un golpe y una onda sobre el paño.
 */

private const val T3_TY = 0.75f          // altura del tablero
private const val T3_HALF = 0.72f        // medio lado de la mesa
private const val T3_DUR = 620L          // duración del lanzamiento de una carta (ms)
private const val T3_CW = 0.150f         // tamaño de las cartas sobre el paño (grandes para distinguirlas bien)
private const val T3_CH = 0.233f

private class T3Pose(var x: Float, var y: Float, var z: Float, var yaw: Float, var pitch: Float, var roll: Float)
private class T3Item(val d: Float, val f: () -> Unit)

private class T3Cam(val w: Float, val h: Float) {
  val ex = 0f; val ey = 1.85f; val ez = -1.20f
  private val ca = cos(0.80f); private val sa = sin(0.80f)
  val f = w * 0.58f; val cx = w / 2f; val cy = h * 0.60f
  fun depth(x: Float, y: Float, z: Float): Float = -(y - ey) * sa + (z - ez) * ca
  fun p(x: Float, y: Float, z: Float, out: FloatArray): Boolean {
    val dx = x - ex; val dy = y - ey; val dz = z - ez
    val yc = dy * ca + dz * sa; val zc = -dy * sa + dz * ca
    if (zc < 0.08f) return false
    out[0] = cx + f * dx / zc; out[1] = cy - f * yc / zc
    return true
  }
}

private class T3Scene(
  val n: Int, val turn: Int, val cards: List<List<Int>>, val hand: List<Int>, val myTurn: Boolean,
  val name: (Int) -> String, val res: (Int) -> Int, val held: (Int) -> Int, val bmp: (Int) -> android.graphics.Bitmap?,
  val zoneOf: (Int) -> Int, val starts: HashMap<Long, Long>, val tapPose: FloatArray, val handQuads: Array<FloatArray?>, val handPoses: Array<FloatArray>,
  val paint: Paint, val textPaint: Paint, val mySeat: Int
)

private fun t3Lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
private fun t3Ease(t: Float): Float { val c = t.coerceIn(0f, 1f); return c * c * (3f - 2f * c) }
private fun t3LerpAngle(a: Float, b: Float, t: Float): Float { var d = (b - a) % (2f * PI.toFloat()); if (d > PI.toFloat()) d -= 2f * PI.toFloat(); if (d < -PI.toFloat()) d += 2f * PI.toFloat(); return a + d * t }

/** Posición (x, z, giro) de cada asiento: 0 yo, 1 derecha, 2 enfrente, 3 izquierda. */
private fun t3Seat(zone: Int): FloatArray = when (zone) {
  1 -> floatArrayOf(0.98f, 0.02f, -PI.toFloat() / 2f)
  2 -> floatArrayOf(0f, 1.22f, PI.toFloat())
  3 -> floatArrayOf(-0.98f, 0.02f, PI.toFloat() / 2f)
  else -> floatArrayOf(0f, -1.45f, 0f)
}

/** Punto en coordenadas locales del asiento (derecha, altura, adelante) → mundo. */
private fun t3Local(seat: FloatArray, sx: Float, sy: Float, sz: Float): FloatArray {
  val yaw = seat[2]
  val rx = cos(yaw); val rz = -sin(yaw); val fx = sin(yaw); val fz = cos(yaw)
  return floatArrayOf(seat[0] + rx * sx + fx * sz, sy, seat[1] + rz * sx + fz * sz)
}

private fun t3Land(zone: Int, i: Int, card: Int): T3Pose {
  val jx = ((card * 17) % 7 - 3) * 0.006f; val jy = (((card * 37) % 13) - 6) * 0.03f
  val (x, z) = when (zone) {
    0 -> (-0.19f + 0.19f * i) to -0.27f
    2 -> (-0.19f + 0.19f * i) to 0.27f
    1 -> 0.28f to (-0.19f + 0.19f * i)
    else -> -0.28f to (-0.19f + 0.19f * i)
  }
  return T3Pose(x + jx, T3_TY + 0.004f + i * 0.002f, z, jy, 0f, 0f)
}

/** Pose de una carta que se sostiene en la mano de un rival (de espaldas hacia mí). */
private fun t3HeldPose(zone: Int, slot: Int, count: Int): T3Pose {
  val seat = t3Seat(zone); val off = slot - (count - 1) / 2f
  val p = t3Local(seat, off * 0.080f, 1.00f - abs(off) * 0.012f, 0.27f)
  return T3Pose(p[0], p[1], p[2], seat[2], -1.36f, -off * 0.20f)
}

private fun t3InQuad(q: FloatArray, px: Float, py: Float): Boolean {
  var pos = false; var neg = false
  for (i in 0 until 4) {
    val ax = q[i * 2]; val ay = q[i * 2 + 1]; val bx = q[((i + 1) % 4) * 2]; val by = q[((i + 1) % 4) * 2 + 1]
    val cr = (bx - ax) * (py - ay) - (by - ay) * (px - ax)
    if (cr > 0f) pos = true else if (cr < 0f) neg = true
  }
  return !(pos && neg)
}

private fun DrawScope.drawScene3D(s: T3Scene, tMs: Long) {
  val w = size.width; val h = size.height
  val cam = T3Cam(w, h)
  val o = FloatArray(2)
  val tSec = tMs / 1000f
  val mat = Matrix()

  fun poly(vararg v: Float): Path? {
    val path = Path(); var i = 0
    while (i < v.size) {
      if (!cam.p(v[i], v[i + 1], v[i + 2], o)) return null
      if (i == 0) path.moveTo(o[0], o[1]) else path.lineTo(o[0], o[1])
      i += 3
    }
    path.close(); return path
  }
  fun pt(x: Float, y: Float, z: Float): Offset? = if (cam.p(x, y, z, o)) Offset(o[0], o[1]) else null
  fun pxs(r: Float, z: Float): Float = cam.f * r / max(0.1f, z)

  // ── sala: pared, suelo y alfombra bajo la mesa ──
  drawRect(Brush.verticalGradient(listOf(Color(0xFF0B0705), Color(0xFF2A1A11), Color(0xFF140B07)), 0f, h))
  run {
    val rug = Path()
    for (k in 0 until 48) {
      val a = k * 2f * PI.toFloat() / 48f
      val c = pt(cos(a) * 2.2f, 0f, sin(a) * 2.2f) ?: return@run
      if (k == 0) rug.moveTo(c.x, c.y) else rug.lineTo(c.x, c.y)
    }
    rug.close()
    drawPath(rug, Brush.radialGradient(listOf(Color(0xFF5A1F2B), Color(0xFF2B0E16)), Offset(cam.cx, cam.cy + h * 0.05f), w * 0.9f))
    drawPath(rug, Color(0x66E0B25A), style = Stroke(3f))
  }
  drawCircle(Brush.radialGradient(listOf(Color(0x55FFD79A), Color.Transparent), Offset(cam.cx, cam.cy - h * 0.08f), w * 0.95f), w * 0.95f, Offset(cam.cx, cam.cy - h * 0.08f))

  // ── jugadores sentados (cuerpo y cabeza van detrás de la mesa) ──
  val skins = listOf(Color(0xFFE8B98F), Color(0xFFC98E62), Color(0xFFF1CBA6), Color(0xFFB07A53))
  val hairs = listOf(Color(0xFF2B1B14), Color(0xFF5A3A22), Color(0xFF8A8A8A), Color(0xFF1A1A1A))
  fun shade(c: Color, k: Float) = Color((c.red * k).coerceIn(0f, 1f), (c.green * k).coerceIn(0f, 1f), (c.blue * k).coerceIn(0f, 1f), c.alpha)

  fun box(cx: Float, cy: Float, cz: Float, hw: Float, hh: Float, hd: Float, yaw: Float, base: Color) {
    val cs = cos(yaw); val sn = sin(yaw)
    fun corner(i: Int): FloatArray {
      val lx = if (i and 1 != 0) hw else -hw; val ly = if (i and 2 != 0) hh else -hh; val lz = if (i and 4 != 0) hd else -hd
      return floatArrayOf(cx + lx * cs + lz * sn, cy + ly, cz - lx * sn + lz * cs)
    }
    val faces = arrayOf(intArrayOf(1, 3, 7, 5), intArrayOf(0, 4, 6, 2), intArrayOf(2, 6, 7, 3), intArrayOf(4, 5, 7, 6), intArrayOf(0, 2, 3, 1))
    val normals = arrayOf(floatArrayOf(1f, 0f, 0f), floatArrayOf(-1f, 0f, 0f), floatArrayOf(0f, 1f, 0f), floatArrayOf(0f, 0f, 1f), floatArrayOf(0f, 0f, -1f))
    val order = faces.indices.sortedByDescending { fi -> val c0 = corner(faces[fi][0]); cam.depth(c0[0], c0[1], c0[2]) }
    for (fi in order) {
      val nl = normals[fi]
      val nx = nl[0] * cs + nl[2] * sn; val ny = nl[1]; val nz = -nl[0] * sn + nl[2] * cs
      val c0 = corner(faces[fi][0])
      if (nx * (cam.ex - c0[0]) + ny * (cam.ey - c0[1]) + nz * (cam.ez - c0[2]) <= 0f) continue
      val k = 0.55f + 0.45f * max(0f, nx * 0.25f + ny * 0.85f + nz * -0.45f)
      val a = corner(faces[fi][0]); val b = corner(faces[fi][1]); val c = corner(faces[fi][2]); val d = corner(faces[fi][3])
      val path = poly(a[0], a[1], a[2], b[0], b[1], b[2], c[0], c[1], c[2], d[0], d[1], d[2]) ?: continue
      drawPath(path, shade(base, k))
    }
  }
  fun sphere(x: Float, y: Float, z: Float, r: Float, base: Color) {
    val c = pt(x, y, z) ?: return
    val rr = pxs(r, cam.depth(x, y, z))
    drawCircle(Brush.radialGradient(listOf(shade(base, 1.25f), base, shade(base, 0.55f)), Offset(c.x - rr * 0.35f, c.y - rr * 0.4f), rr * 1.5f), rr, c)
  }
  fun limb(a: FloatArray, b: FloatArray, thick: Float, color: Color) {
    val pa = pt(a[0], a[1], a[2]) ?: return; val pb = pt(b[0], b[1], b[2]) ?: return
    val th = pxs(thick, cam.depth((a[0] + b[0]) / 2f, (a[1] + b[1]) / 2f, (a[2] + b[2]) / 2f))
    drawLine(shade(color, 0.6f), Offset(pa.x + 1.5f, pa.y + 1.5f), Offset(pb.x + 1.5f, pb.y + 1.5f), th, StrokeCap.Round)
    drawLine(color, pa, pb, th, StrokeCap.Round)
  }
  /** Mano con palma, cuatro dedos con dos falanges y pulgar; sostiene las cartas con los dedos curvados. */
  fun fingerHand(hd: FloatArray, yaw: Float, side: Float, skin: Color, thumbOnly: Boolean = false) {
    val fx = sin(yaw); val fz = cos(yaw); val rx = cos(yaw); val rz = -sin(yaw)
    fun at(a: Float, l: Float, u: Float) = floatArrayOf(hd[0] + fx * a + rx * l, hd[1] + u, hd[2] + fz * a + rz * l)
    val sk2 = shade(skin, 0.92f)
    if (!thumbOnly) {
      limb(at(-0.030f, 0f, 0f), at(0.022f, 0f, -0.004f), 0.066f, skin)
      for (k in 0 until 4) {
        val l = -0.027f + k * 0.018f
        val len = if (k == 1 || k == 2) 1.12f else 0.9f
        val b = at(0.026f, l, -0.002f); val m = at(0.026f + 0.034f * len, l, -0.008f); val e = at(0.026f + 0.054f * len, l, -0.027f)
        limb(b, m, 0.017f, skin); limb(m, e, 0.015f, sk2)
      }
    }
    val tb = at(0.004f, side * 0.036f, 0.006f); val tm = at(0.032f, side * 0.043f, 0.014f); val te = at(0.054f, side * 0.032f, 0.012f)
    limb(tb, tm, 0.021f, skin); limb(tm, te, 0.017f, sk2)
  }
  fun arm(seat: FloatArray, side: Float, hand: FloatArray, sleeve: Color, skin: Color) {
    val sh = t3Local(seat, side * 0.23f, 1.20f, 0.0f)
    val el = floatArrayOf((sh[0] + hand[0]) / 2f, min(sh[1], hand[1]) - 0.08f, (sh[2] + hand[2]) / 2f)
    val out = t3Local(seat, side * 0.10f, 0f, 0f)
    el[0] += (out[0] - seat[0]); el[2] += (out[2] - seat[1])
    limb(sh, el, 0.075f, sleeve); limb(el, hand, 0.062f, sleeve)
    fingerHand(hand, seat[2], side, skin)
  }

  val zonesInUse = (0 until s.n).filter { s.zoneOf(it) != 0 }.sortedByDescending { sd -> val st = t3Seat(s.zoneOf(sd)); cam.depth(st[0], 1.2f, st[1]) }
  for (seat in zonesInUse) {
    val zone = s.zoneOf(seat); val st = t3Seat(zone)
    val team = seat % 2 == s.mySeat % 2
    val shirt = if (team) Color(0xFF2E6FB7) else Color(0xFFB3382C)
    val skin = skins[seat % 4]; val hair = hairs[(seat + 1) % 4]
    val yaw = st[2]
    val bob = if (seat == s.turn) sin(tSec * 3f + seat) * 0.008f else 0f
    // respaldo de la silla
    val chair = t3Local(st, 0f, 0f, -0.20f)
    box(chair[0], 1.0f, chair[2], 0.27f, 0.45f, 0.035f, yaw, Color(0xFF2A1810))
    // torso y cuello
    val tp = t3Local(st, 0f, 0f, -0.02f)
    box(tp[0], 1.0f + bob, tp[2], 0.20f, 0.26f, 0.11f, yaw, shirt)
    val neck = t3Local(st, 0f, 0f, 0f)
    box(neck[0], 1.29f + bob, neck[2], 0.045f, 0.04f, 0.045f, yaw, skin)
    // cabeza
    val hp = t3Local(st, 0f, 0f, 0.0f)
    val hx = hp[0]; val hy = 1.42f + bob; val hz = hp[2]
    sphere(hx, hy, hz, 0.115f, skin)
    val fx = sin(yaw); val fz = cos(yaw)
    val rx = cos(yaw); val rz = -sin(yaw)
    val tcx = cam.ex - hx; val tcy = cam.ey - hy; val tcz = cam.ez - hz
    val tl = sqrt(tcx * tcx + tcy * tcy + tcz * tcz)
    val facing = (fx * tcx + fz * tcz) / tl
    val hc = pt(hx, hy, hz)
    if (hc != null) {
      val rr = pxs(0.115f, cam.depth(hx, hy, hz))
      if (facing < -0.05f) drawCircle(hair, rr * 1.02f, hc) else drawArc(hair, 180f, 180f, true, Offset(hc.x - rr * 1.04f, hc.y - rr * 1.06f), Size(rr * 2.08f, rr * 1.5f))
      if (facing > 0.1f) {
        for (sd in listOf(-1f, 1f)) {
          val e = pt(hx + fx * 0.105f + rx * sd * 0.042f, hy + 0.012f, hz + fz * 0.105f + rz * sd * 0.042f)
          if (e != null) drawCircle(Color(0xFF2B1B14), rr * 0.10f, e)
        }
        val m0 = pt(hx + fx * 0.108f - rx * 0.025f, hy - 0.05f, hz + fz * 0.108f - rz * 0.025f); val m1 = pt(hx + fx * 0.108f + rx * 0.025f, hy - 0.05f, hz + fz * 0.108f + rz * 0.025f)
        if (m0 != null && m1 != null) drawLine(Color(0xFF8A5A44), m0, m1, rr * 0.08f, StrokeCap.Round)
      }
    }
  }

  // ── mesa: patas, faldón, borde de madera y paño ──
  val T = T3_HALF
  for (lx in listOf(-0.62f, 0.62f)) for (lz in listOf(0.62f, -0.62f)) {
    val a = pt(lx, 0.70f, lz); val b = pt(lx, 0.0f, lz)
    if (a != null && b != null) {
      val th = pxs(0.075f, cam.depth(lx, 0.4f, lz))
      drawLine(Color(0xFF241209), a, b, th, StrokeCap.Round)
      drawLine(Color(0x33FFFFFF), Offset(a.x - th * 0.25f, a.y), Offset(b.x - th * 0.25f, b.y), th * 0.2f)
    }
  }
  poly(-T, T3_TY, -T, T, T3_TY, -T, T, T3_TY - 0.075f, -T, -T, T3_TY - 0.075f, -T)?.let { drawPath(it, Brush.verticalGradient(listOf(Color(0xFF4A2A14), Color(0xFF24120A)), 0f, h)) }
  poly(-T, T3_TY, -T, T, T3_TY, -T, T, T3_TY, T, -T, T3_TY, T)?.let {
    drawPath(it, Brush.verticalGradient(listOf(Color(0xFF7A4A28), Color(0xFF4A2A14), Color(0xFF6B4226)), cam.cy - h * 0.25f, cam.cy + h * 0.22f))
    drawPath(it, Color(0x55FFE0B0), style = Stroke(2f))
  }
  val F = T - 0.07f
  val feltPath = poly(-F, T3_TY, -F, F, T3_TY, -F, F, T3_TY, F, -F, T3_TY, F)
  if (feltPath != null) {
    drawPath(feltPath, Brush.verticalGradient(listOf(Color(0xFF1B6B3A), Color(0xFF1F7A43), Color(0xFF13592F)), cam.cy - h * 0.24f, cam.cy + h * 0.20f))
    val lc = pt(0f, T3_TY, 0f)
    if (lc != null) {
      val lr = pxs(0.95f, cam.depth(0f, T3_TY, 0f))
      drawPath(feltPath, Brush.radialGradient(listOf(Color(0x66FFF0C8), Color.Transparent), lc, lr))
      drawPath(feltPath, Brush.radialGradient(listOf(Color.Transparent, Color(0x66000000)), lc, lr * 1.5f))
    }
    drawPath(feltPath, Color(0x77000000), style = Stroke(2.5f))
    // costura dorada del paño
    poly(-F + 0.03f, T3_TY, -F + 0.03f, F - 0.03f, T3_TY, -F + 0.03f, F - 0.03f, T3_TY, F - 0.03f, -F + 0.03f, T3_TY, F - 0.03f)?.let { drawPath(it, Color(0x33FFE08A), style = Stroke(1.5f)) }
  }

  // ── ondas de golpe cuando una carta cae ──
  val nowMs = tMs
  for (seat in 0 until s.n) {
    val zone = s.zoneOf(seat); val list = s.cards.getOrElse(seat) { emptyList() }
    list.forEachIndexed { i, card ->
      val st = s.starts[(seat * 1000L + i) * 100000L + card] ?: 0L
      if (st == 0L) return@forEachIndexed
      val dt = nowMs - st - T3_DUR
      if (dt in 0..480) {
        val lp = t3Land(zone, i, card); val k = dt / 480f
        val c = pt(lp.x, T3_TY + 0.003f, lp.z)
        if (c != null) {
          val rr = pxs(0.05f + 0.20f * k, cam.depth(lp.x, T3_TY, lp.z))
          drawOval(Color(0xFFFFF3C4).copy(alpha = 0.55f * (1f - k)), Offset(c.x - rr, c.y - rr * 0.45f), Size(rr * 2f, rr * 0.9f), style = Stroke(3f))
          for (sp in 0 until 6) {
            val a = sp * 1.047f + card
            val sx = c.x + cos(a) * rr * 1.1f; val sy = c.y + sin(a) * rr * 0.5f - 18f * k
            drawCircle(Color(0xFFFFE08A).copy(alpha = 0.8f * (1f - k)), 2.5f, Offset(sx, sy))
          }
        }
      }
    }
  }

  // ── dibujo de una carta como plano 3D con la imagen real ──
  fun drawCard(p: T3Pose, cw: Float, ch: Float, res: Int, card: Int, store: FloatArray? = null) {
    val hw = cw / 2f; val hh = ch / 2f
    val cr = cos(p.roll); val sr = sin(p.roll); val cp = cos(p.pitch); val sp = sin(p.pitch); val cyw = cos(p.yaw); val syw = sin(p.yaw)
    val xs = FloatArray(8)
    val us = floatArrayOf(-hw, hw, hw, -hw); val vs = floatArrayOf(hh, hh, -hh, -hh)
    for (k in 0 until 4) {
      val u0 = us[k]; val v0 = vs[k]
      val u = u0 * cr - v0 * sr; val v = u0 * sr + v0 * cr
      val y1 = -v * sp; val z1 = v * cp
      val x = u * cyw + z1 * syw; val z = -u * syw + z1 * cyw
      if (!cam.p(p.x + x, p.y + y1, p.z + z, o)) return
      xs[k * 2] = o[0]; xs[k * 2 + 1] = o[1]
    }
    store?.let { for (k in 0 until 8) it[k] = xs[k] }
    val nx = sp * syw; val ny = cp; val nz = sp * cyw
    val faceUp = nx * (cam.ex - p.x) + ny * (cam.ey - p.y) + nz * (cam.ez - p.z) > 0f
    val light = (0.60f + 0.40f * max(0f, nx * 0.2f + ny * 0.85f + nz * -0.45f)).coerceIn(0f, 1f)
    val path = Path().apply { moveTo(xs[0], xs[1]); lineTo(xs[2], xs[3]); lineTo(xs[4], xs[5]); lineTo(xs[6], xs[7]); close() }
    fun bil(a: Float, b: Float): Offset = Offset(
      (1 - a) * (1 - b) * xs[0] + a * (1 - b) * xs[2] + a * b * xs[4] + (1 - a) * b * xs[6],
      (1 - a) * (1 - b) * xs[1] + a * (1 - b) * xs[3] + a * b * xs[5] + (1 - a) * b * xs[7]
    )
    val bmp = if (faceUp && res != 0) s.bmp(res) else null
    if (faceUp && bmp != null) {
      val bw = bmp.width.toFloat(); val bh = bmp.height.toFloat()
      mat.setPolyToPoly(floatArrayOf(0f, 0f, bw, 0f, bw, bh, 0f, bh), 0, xs, 0, 4)
      drawContext.canvas.nativeCanvas.drawBitmap(bmp, mat, s.paint)
      drawPath(path, Color.Black.copy(alpha = (1f - light) * 0.55f))
      drawPath(path, Color(0x88000000), style = Stroke(1.4f))
    } else if (faceUp) {
      drawPath(path, Color(0xFFFAF6EA)); drawPath(path, Color(0x88000000), style = Stroke(1.4f))
    } else {
      drawPath(path, Brush.linearGradient(listOf(Color(0xFF8A2020), Color(0xFF3B0A0A)), bil(0f, 0f), bil(1f, 1f)))
      val inner = Path().apply { val a = bil(0.14f, 0.10f); moveTo(a.x, a.y); val b = bil(0.86f, 0.10f); lineTo(b.x, b.y); val c = bil(0.86f, 0.90f); lineTo(c.x, c.y); val d = bil(0.14f, 0.90f); lineTo(d.x, d.y); close() }
      drawPath(inner, Color(0xAAFFE08A), style = Stroke(1.6f))
      val dm = Path().apply { val a = bil(0.5f, 0.30f); moveTo(a.x, a.y); val b = bil(0.72f, 0.5f); lineTo(b.x, b.y); val c = bil(0.5f, 0.70f); lineTo(c.x, c.y); val d = bil(0.28f, 0.5f); lineTo(d.x, d.y); close() }
      drawPath(dm, Color(0x88FFE08A), style = Stroke(1.4f))
      drawPath(path, Color.Black.copy(alpha = (1f - light) * 0.4f))
      drawPath(path, Color(0xCCFFE08A), style = Stroke(1.6f))
    }
  }
  fun shadowOf(p: T3Pose, cw: Float, ch: Float, alpha: Float) {
    val cyw = cos(p.yaw); val syw = sin(p.yaw)
    val hw = cw / 2f; val hh = ch / 2f
    val sx = floatArrayOf(-hw, hw, hw, -hw); val sz = floatArrayOf(hh, hh, -hh, -hh)
    val v = FloatArray(12)
    for (k in 0 until 4) { v[k * 3] = p.x + 0.02f + sx[k] * cyw + sz[k] * syw; v[k * 3 + 1] = T3_TY + 0.002f; v[k * 3 + 2] = p.z - 0.015f - sx[k] * syw + sz[k] * cyw }
    poly(*v)?.let { drawPath(it, Color.Black.copy(alpha = alpha)) }
  }

  // ── cartas sobre el paño, manos y cartas que sostienen los rivales (ordenadas por distancia) ──
  val items = ArrayList<T3Item>()
  for (seat in 0 until s.n) {
    val zone = s.zoneOf(seat); if (zone == 0) continue
    val st = t3Seat(zone); val team = seat % 2 == s.mySeat % 2
    val sleeve = if (team) Color(0xFF2E6FB7) else Color(0xFFB3382C); val skin = skins[seat % 4]
    val list = s.cards.getOrElse(seat) { emptyList() }
    val kHeld = s.held(seat).coerceIn(0, 3)
    // mano que juega: sigue a la última carta lanzada y vuelve a su sitio
    var handAnim: FloatArray? = null
    if (list.isNotEmpty()) {
      val i = list.size - 1; val card = list[i]
      val stt = s.starts[(seat * 1000L + i) * 100000L + card] ?: 0L
      if (stt != 0L) {
        val dt = nowMs - stt
        val land = t3Land(zone, i, card); val start = t3HeldPose(zone, 1, kHeld + 1)
        val rest = t3Local(st, 0.10f, 0.90f, 0.24f)
        val back = t3Local(st, 0f, 0f, -0.05f)
        if (dt < T3_DUR) {
          val t = t3Ease(dt / T3_DUR.toFloat())
          handAnim = floatArrayOf(t3Lerp(start.x, land.x, t) + (back[0] - st[0]), t3Lerp(start.y, land.y, t) + 0.20f * sin(PI.toFloat() * t) + 0.02f, t3Lerp(start.z, land.z, t) + (back[2] - st[1]))
        } else if (dt < T3_DUR + 520L) {
          val t = t3Ease((dt - T3_DUR) / 520f)
          handAnim = floatArrayOf(t3Lerp(land.x + (back[0] - st[0]), rest[0], t), t3Lerp(land.y + 0.02f, rest[1], t), t3Lerp(land.z + (back[2] - st[1]), rest[2], t))
        }
      }
    }
    val sway = sin(tSec * 1.7f + seat) * 0.006f
    val leftHand = t3Local(st, -0.10f, 0.90f + sway, 0.24f)
    val rightHand = handAnim ?: t3Local(st, 0.10f, 0.90f - sway, 0.24f)
    val dArm = cam.depth(st[0], 1.0f, st[1]) - 0.05f
    items.add(T3Item(dArm) { arm(st, -1f, leftHand, sleeve, skin); arm(st, 1f, rightHand, sleeve, skin) })
    // cartas que sostiene (de espaldas)
    for (slot in 0 until kHeld) {
      val hp = t3HeldPose(zone, slot, kHeld)
      items.add(T3Item(cam.depth(hp.x, hp.y, hp.z) - 0.02f + slot * 0.001f) { drawCard(hp, 0.135f, 0.21f, 0, 0) })
    }
    // cartas jugadas
    list.forEachIndexed { i, card ->
      val stt = s.starts[(seat * 1000L + i) * 100000L + card] ?: 0L
      val land = t3Land(zone, i, card)
      val p = T3Pose(land.x, land.y, land.z, land.yaw, 0f, 0f)
      var t = 1f
      if (stt != 0L) {
        val dt = nowMs - stt
        if (dt < T3_DUR) {
          t = max(0f, dt / T3_DUR.toFloat()); val e = t3Ease(t)
          val start = t3HeldPose(zone, 1, kHeld + 1)
          p.x = t3Lerp(start.x, land.x, e); p.z = t3Lerp(start.z, land.z, e)
          p.y = t3Lerp(start.y, land.y, e) + 0.20f * sin(PI.toFloat() * t)
          p.yaw = t3LerpAngle(start.yaw, land.yaw, e) + (1f - e) * 0.6f
          p.pitch = t3Lerp(start.pitch, 0f, e); p.roll = t3Lerp(start.roll, 0f, e)
        } else if (dt < T3_DUR + 260L) {
          val k = (dt - T3_DUR) / 260f
          p.y += 0.012f * (1f - k) * abs(sin(k * 9f))
        }
      }
      val dep = cam.depth(p.x, p.y, p.z)
      items.add(T3Item(dep) {
        shadowOf(p, T3_CW, T3_CH, 0.38f / (1f + (p.y - T3_TY) * 9f))
        drawCard(p, T3_CW, T3_CH, s.res(card), card)
      })
    }
  }
  // cartas propias ya jugadas
  run {
    val list = s.cards.getOrElse(s.mySeat) { emptyList() }
    list.forEachIndexed { i, card ->
      val stt = s.starts[(s.mySeat * 1000L + i) * 100000L + card] ?: 0L
      val land = t3Land(0, i, card)
      val p = T3Pose(land.x, land.y, land.z, land.yaw, 0f, 0f)
      if (stt != 0L) {
        val dt = nowMs - stt
        if (dt < T3_DUR) {
          val t = max(0f, dt / T3_DUR.toFloat()); val e = t3Ease(t)
          val sx = s.tapPose[0]; val sy = s.tapPose[1]; val sz = s.tapPose[2]
          p.x = t3Lerp(sx, land.x, e); p.z = t3Lerp(sz, land.z, e)
          p.y = t3Lerp(sy, land.y, e) + 0.22f * sin(PI.toFloat() * t)
          p.yaw = t3LerpAngle(s.tapPose[3], land.yaw, e) + (1f - e) * 0.5f
          p.pitch = t3Lerp(s.tapPose[4], 0f, e); p.roll = t3Lerp(s.tapPose[5], 0f, e)
        } else if (dt < T3_DUR + 260L) {
          val k = (dt - T3_DUR) / 260f
          p.y += 0.012f * (1f - k) * abs(sin(k * 9f))
        }
      }
      items.add(T3Item(cam.depth(p.x, p.y, p.z)) {
        shadowOf(p, T3_CW * 1.1f, T3_CH * 1.1f, 0.38f / (1f + (p.y - T3_TY) * 9f))
        drawCard(p, T3_CW * 1.1f, T3_CH * 1.1f, s.res(card), card)
      })
    }
  }
  for (item in items.sortedByDescending { it.d }) item.f()

  // ── mis manos y mi abanico de cartas (lo más cercano a la cámara) ──
  val k = s.hand.size
  val myTeamSleeve = Color(0xFF2E6FB7); val mySkin = Color(0xFFE8B98F)
  val lift = if (s.myTurn) 0.05f + 0.008f * sin(tSec * 4f) else 0f
  val leftHand = floatArrayOf(-0.20f - 0.055f * max(0, k - 1), 0.99f + lift * 0.5f, -0.95f)
  val rightHand = floatArrayOf(0.20f + 0.055f * max(0, k - 1), 0.99f + lift * 0.5f, -0.95f)
  // la mano que acaba de lanzar una carta sale con ella
  var throwHand: FloatArray? = null
  run {
    val list = s.cards.getOrElse(s.mySeat) { emptyList() }
    if (list.isNotEmpty()) {
      val i = list.size - 1; val stt = s.starts[(s.mySeat * 1000L + i) * 100000L + list[i]] ?: 0L
      if (stt != 0L) {
        val dt = nowMs - stt; val land = t3Land(0, i, list[i])
        if (dt < T3_DUR) { val e = t3Ease(dt / T3_DUR.toFloat()); throwHand = floatArrayOf(t3Lerp(s.tapPose[0], land.x, e) + 0.03f, t3Lerp(s.tapPose[1], land.y, e) + 0.22f * sin(PI.toFloat() * e) - 0.02f, t3Lerp(s.tapPose[2], land.z, e) - 0.06f) }
        else if (dt < T3_DUR + 520L) { val e = t3Ease((dt - T3_DUR) / 520f); throwHand = floatArrayOf(t3Lerp(land.x + 0.03f, rightHand[0], e), t3Lerp(land.y, rightHand[1], e), t3Lerp(land.z - 0.06f, rightHand[2], e)) }
      }
    }
  }
  val rh = throwHand ?: rightHand
  val meSeat = floatArrayOf(0f, -1.40f, 0f)   // mi asiento queda detrás de la cámara: solo se ven mis brazos
  arm(meSeat, -1f, leftHand, myTeamSleeve, mySkin)
  arm(meSeat, 1f, rh, myTeamSleeve, mySkin)
  for (i in s.handQuads.indices) s.handQuads[i] = null
  s.hand.forEachIndexed { idx, card ->
    val off = idx - (k - 1) / 2f
    val p = T3Pose(off * 0.21f, 1.10f + lift - abs(off) * 0.012f, -0.95f - abs(off) * 0.01f, off * 0.05f, -1.08f, -off * 0.15f)
    if (idx < s.handPoses.size) { s.handPoses[idx][0] = p.x; s.handPoses[idx][1] = p.y; s.handPoses[idx][2] = p.z; s.handPoses[idx][3] = p.yaw; s.handPoses[idx][4] = p.pitch; s.handPoses[idx][5] = p.roll }
    val q = FloatArray(8)
    drawCard(p, 0.19f, 0.295f, s.res(card), card, q)
    if (idx < s.handQuads.size) s.handQuads[idx] = q
    if (s.myTurn) {
      val path = Path().apply { moveTo(q[0], q[1]); lineTo(q[2], q[3]); lineTo(q[4], q[5]); lineTo(q[6], q[7]); close() }
      drawPath(path, Color(0xFFFFD166).copy(alpha = 0.85f), style = Stroke(3.5f))
    }
  }
  // dedos y pulgares sobre las cartas
  fingerHand(leftHand, 0f, -1f, mySkin, true); fingerHand(rh, 0f, 1f, mySkin, true)

  // ── cartelitos con los nombres ──
  s.textPaint.textSize = 12.sp.toPx()
  val pillH = 22.dp.toPx()
  for (seat in 0 until s.n) {
    val zone = s.zoneOf(seat); if (zone == 0) continue
    val st = t3Seat(zone); val hp = t3Local(st, 0f, 0f, 0f)
    val c = pt(hp[0], 1.62f, hp[2]) ?: continue
    val label = s.name(seat); val tw = s.textPaint.measureText(label) + 22f
    val active = seat == s.turn
    drawRoundRect(if (active) Color(0xFFFFD166) else Color(0xAA000000), Offset(c.x - tw / 2f, c.y - pillH * 0.75f), Size(tw, pillH), CornerRadius(pillH / 2f))
    s.textPaint.color = if (active) android.graphics.Color.rgb(4, 18, 10) else android.graphics.Color.rgb(255, 209, 102)
    drawContext.canvas.nativeCanvas.drawText(label, c.x, c.y + pillH * 0.05f, s.textPaint)
  }
}

@Composable
internal fun TrucoTable3D(
  n: Int, mySeat: Int, turn: Int, cardsBySeat: List<List<Int>>, myHand: List<Int>, myTurn: Boolean,
  nameOf: (Int) -> String, resOf: (Int) -> Int, handCount: (Int) -> Int, onPlay: (Int) -> Unit
) {
  val ctx = LocalContext.current
  val bmps = remember { HashMap<Int, android.graphics.Bitmap>() }
  val failed = remember { HashSet<Int>() }
  fun bmpOf(res: Int): android.graphics.Bitmap? {
    bmps[res]?.let { return it }
    if (res == 0 || failed.contains(res)) return null
    return try {
      val opt = BitmapFactory.Options().apply { inSampleSize = 2 }
      val b = BitmapFactory.decodeResource(ctx.resources, res, opt)
      if (b == null) { failed.add(res); null } else { bmps[res] = b; b }
    } catch (e: Throwable) { failed.add(res); null }
  }
  val four = n >= 4
  fun zoneOf(seat: Int): Int { val r = ((seat - mySeat) % n + n) % n; return if (!four) (if (r == 0) 0 else 2) else r }
  // marca de inicio de cada carta jugada (para animarla una sola vez)
  val starts = remember { HashMap<Long, Long>() }
  val first = remember { booleanArrayOf(true) }
  val prevTotal = remember { IntArray(1) }
  val clockNow = System.nanoTime() / 1_000_000L
  var total = 0
  for (l in cardsBySeat) total += l.size
  if (total < prevTotal[0]) starts.clear()
  cardsBySeat.forEachIndexed { sIdx, l -> l.forEachIndexed { i, c -> val key = (sIdx * 1000L + i) * 100000L + c; if (!starts.containsKey(key)) starts[key] = if (first[0]) 0L else clockNow } }
  first[0] = false; prevTotal[0] = total

  val tapPose = remember { floatArrayOf(0f, 1.10f, -0.93f, 0f, -1.08f, 0f) }
  val handQuads = remember { arrayOfNulls<FloatArray>(3) }
  val handPoses = remember { Array(3) { FloatArray(6) } }
  val paint = remember { Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG) }
  val textPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; isFakeBoldText = true } }
  val turnRef = rememberUpdatedState(myTurn)
  val playRef = rememberUpdatedState(onPlay)
  var tick by remember { mutableLongStateOf(0L) }
  LaunchedEffect(Unit) { while (true) { withFrameMillis { tick = it } } }

  BoxWithConstraints(Modifier.fillMaxWidth()) {
    val H = (maxWidth * 1.18f).coerceIn(400.dp, 520.dp)
    Canvas(
      Modifier.fillMaxWidth().height(H).clip(RoundedCornerShape(20.dp)).pointerInput(Unit) {
        detectTapGestures { pos ->
          if (!turnRef.value) return@detectTapGestures
          for (idx in handQuads.indices.reversed()) {
            val q = handQuads[idx] ?: continue
            if (t3InQuad(q, pos.x, pos.y)) {
              val hp = handPoses[idx]
              for (j in 0 until 6) tapPose[j] = hp[j]
              playRef.value(idx)
              break
            }
          }
        }
      }
    ) {
      val frame = tick   // fuerza el redibujado en cada cuadro
      val scene = T3Scene(n, turn, cardsBySeat, myHand, myTurn, nameOf, resOf, handCount, { bmpOf(it) }, { zoneOf(it) }, starts, tapPose, handQuads, handPoses, paint, textPaint, mySeat)
      drawScene3D(scene, System.nanoTime() / 1_000_000L + frame * 0L)
    }
  }
}
