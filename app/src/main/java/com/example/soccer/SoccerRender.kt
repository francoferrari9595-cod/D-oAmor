package com.example.soccer

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

internal class SPart(var x: Float, var h: Float, var z: Float, var vx: Float, var vh: Float, var vz: Float, var life: Float, val max: Float, val size: Float, val color: Color)
internal class SFx {
  val parts = ArrayList<SPart>()
  val trail = ArrayList<FloatArray>()
  val rings = ArrayList<FloatArray>()           // ondas de golpe en el césped: x, y, edad, duración, fuerza
  var shake = 0f; var flash = 0f; var zoom = 1f; var goalT = 0f; var goalTeam = 0
  var camX = 0f; var camZ = 0f; var init = false
  var roll = 0f
  /** Orientación 3D del balón (matriz 3×3 por filas, ejes: x, altura, y). */
  val rot = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)

  /** El balón gira según cómo rueda y el efecto que lleva (se ven las manchas dar vueltas). */
  fun spinBall(b: SBall, dt: Float) {
    val r = 0.22f
    val wx = b.vy / r; val wh = b.curl * 70f; val wy = -b.vx / r
    val m = sqrt(wx * wx + wh * wh + wy * wy)
    if (m < 0.01f) return
    val ang = min(m * dt, 0.7f)
    val u1 = wx / m; val u2 = wh / m; val u3 = wy / m
    val c = cos(ang); val s = sin(ang); val k = 1f - c
    val rw = floatArrayOf(
      c + u1 * u1 * k, u1 * u2 * k - u3 * s, u1 * u3 * k + u2 * s,
      u2 * u1 * k + u3 * s, c + u2 * u2 * k, u2 * u3 * k - u1 * s,
      u3 * u1 * k - u2 * s, u3 * u2 * k + u1 * s, c + u3 * u3 * k
    )
    val old = rot.copyOf()
    for (i in 0 until 3) for (j in 0 until 3) rot[i * 3 + j] = rw[i * 3] * old[j] + rw[i * 3 + 1] * old[3 + j] + rw[i * 3 + 2] * old[6 + j]
  }
}

internal class SPaints {
  val fill = Paint().apply { isAntiAlias = true; textAlign = Paint.Align.CENTER; color = android.graphics.Color.WHITE; typeface = Typeface.DEFAULT_BOLD }
  val stroke = Paint().apply { isAntiAlias = true; textAlign = Paint.Align.CENTER; style = Paint.Style.STROKE; color = android.graphics.Color.BLACK; typeface = Typeface.DEFAULT_BOLD }
}

internal val S_TEAM_COL = listOf(Color(0xFF1E88E5), Color(0xFFE53935))
private fun SoccerWorld.kitOf(team: Int): SKit = if (team == 0) kits.first else kits.second
internal val S_GK_COL = listOf(Color(0xFFFFEB3B), Color(0xFF43A047))
private val CROWD = listOf(Color(0xFFE53935), Color(0xFF1E88E5), Color(0xFFFFFFFF), Color(0xFFFFD740), Color(0xFF8E24AA), Color(0xFF43A047))
private val SKINS = listOf(Color(0xFFF1C9A0), Color(0xFFD9A06F), Color(0xFFB57A52), Color(0xFF8D5A3B), Color(0xFFE8B98F))
private val HAIRS = listOf(Color(0xFF2B1B14), Color(0xFF5A3A22), Color(0xFF1A1A1A), Color(0xFFB8860B), Color(0xFF6B4226))

private fun shade(c: Color, k: Float) = Color((c.red * k).coerceIn(0f, 1f), (c.green * k).coerceIn(0f, 1f), (c.blue * k).coerceIn(0f, 1f), c.alpha)

/**
 * Cámara de transmisión 3D: lateral y elevada, con la cancha en horizontal. Sigue el balón a lo largo Y A LO ANCHO de la cancha.
 * Tu equipo siempre ataca hacia la DERECHA ([flip] gira la vista 180° para el equipo que ataca hacia y negativo).
 * Los polígonos se recortan contra el plano cercano para que nada se deforme cuando queda detrás de la cámara.
 */
internal class SCam(val w: Float, val h: Float, val flip: Boolean, focusLat: Float, focusLen: Float, val zoom: Float, shx: Float, shy: Float) {
  private val camH = 24f; private val back = 34f
  private val ang = atan2(camH, back)
  val sinP = sin(ang); val cosP = cos(ang)
  val F = h * 2.4f * zoom
  private val cx = w / 2f + shx; private val cy = h * 0.56f + shy
  private val fl = if (flip) -focusLen else focusLen
  private val fz = if (flip) -focusLat else focusLat
  var depth = 1f
  private val tmp = FloatArray(3)

  /** Coordenadas de cámara: [derecha, arriba, profundidad]. x0 = ancho, hgt = altura, y0 = largo. */
  fun cam(x0: Float, hgt: Float, y0: Float, out: FloatArray) {
    val sx = if (flip) -y0 else y0; val sz = if (flip) -x0 else x0
    val dx = sx - fl; val dy = hgt - camH; val dz = sz - fz + back
    out[0] = dx; out[1] = dy * cosP + dz * sinP; out[2] = -dy * sinP + dz * cosP
  }
  fun p(x0: Float, hgt: Float, y0: Float): Offset {
    cam(x0, hgt, y0, tmp)
    depth = tmp[2].coerceAtLeast(0.6f)
    return Offset(cx + F * tmp[0] / depth, cy - F * tmp[1] / depth)
  }
  fun scale(): Float = F / depth
  fun depthOf(x0: Float, hgt: Float, y0: Float): Float { cam(x0, hgt, y0, tmp); return tmp[2] }
  fun visible(x0: Float, hgt: Float, y0: Float): Boolean { cam(x0, hgt, y0, tmp); return tmp[2] > 2f }
  private fun scr(c: FloatArray) = Offset(cx + F * c[0] / c[2], cy - F * c[1] / c[2])

  /** Polígono (triples x0, hgt, y0) recortado contra el plano cercano; null si queda detrás de la cámara. */
  fun poly(v: FloatArray): Path? {
    val n = v.size / 3
    val pts = ArrayList<FloatArray>(n)
    for (i in 0 until n) { val c = FloatArray(3); cam(v[i * 3], v[i * 3 + 1], v[i * 3 + 2], c); pts.add(c) }
    val near = 1.0f
    val out = ArrayList<FloatArray>(n + 2)
    for (i in pts.indices) {
      val a = pts[i]; val b = pts[(i + 1) % pts.size]
      val ia = a[2] >= near; val ib = b[2] >= near
      if (ia) out.add(a)
      if (ia != ib) { val t = (near - a[2]) / (b[2] - a[2]); out.add(floatArrayOf(a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, near)) }
    }
    if (out.size < 3) return null
    val path = Path()
    for ((i, c) in out.withIndex()) { val o = scr(c); if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y) }
    path.close(); return path
  }

  /** Segmento recortado: devuelve [x0, y0, x1, y1, escala px/m] o null. */
  fun seg(ax: Float, ah: Float, ay: Float, bx: Float, bh: Float, by: Float): FloatArray? {
    val a = FloatArray(3); val b = FloatArray(3)
    cam(ax, ah, ay, a); cam(bx, bh, by, b)
    val near = 1.0f
    if (a[2] < near && b[2] < near) return null
    if (a[2] < near) { val t = (near - a[2]) / (b[2] - a[2]); a[0] += (b[0] - a[0]) * t; a[1] += (b[1] - a[1]) * t; a[2] = near }
    if (b[2] < near) { val t = (near - b[2]) / (a[2] - b[2]); b[0] += (a[0] - b[0]) * t; b[1] += (a[1] - b[1]) * t; b[2] = near }
    val oa = scr(a); val ob = scr(b)
    return floatArrayOf(oa.x, oa.y, ob.x, ob.y, F / ((a[2] + b[2]) / 2f))
  }
}

private fun DrawScope.txt(pt: SPaints, s: String, x: Float, y: Float, size: Float, color: Int = android.graphics.Color.WHITE) {
  drawIntoCanvas { cv ->
    pt.fill.textSize = size; pt.stroke.textSize = size; pt.stroke.strokeWidth = max(2f, size * 0.16f); pt.fill.color = color
    cv.nativeCanvas.drawText(s, x, y, pt.stroke); cv.nativeCanvas.drawText(s, x, y, pt.fill); pt.fill.color = android.graphics.Color.WHITE
  }
}


/** Dibuja el estadio 3D completo: cielo, focos, gradas con público, césped, líneas, porterías con red, jugadores y balón en 3D y efectos. */
internal fun DrawScope.drawSoccer(world: SoccerWorld, fx: SFx, pt: SPaints, flip: Boolean, myId: Int, t: Float, charge: Float, aimJx: Float = 0f, aimJy: Float = 0f) {
  val w = size.width; val h = size.height
  val shx = (kotlin.random.Random.nextFloat() - 0.5f) * fx.shake * 16f; val shy = (kotlin.random.Random.nextFloat() - 0.5f) * fx.shake * 16f
  val cam = SCam(w, h, flip, fx.camX, fx.camZ, fx.zoom, shx, shy)
  // cielo nocturno
  drawRect(Brush.verticalGradient(listOf(Color(0xFF02040C), Color(0xFF0B1230), Color(0xFF1B2A58)), 0f, h * 0.55f), Offset.Zero, Size(w, h))
  for (i in 0 until 5) {
    val bx = w * (0.08f + 0.21f * i); val sway = sin(t * 0.6f + i) * w * 0.02f
    val beam = Path().apply { moveTo(bx - 10f, 0f); lineTo(bx + 10f, 0f); lineTo(bx + sway + w * 0.16f, h * 0.6f); lineTo(bx + sway - w * 0.16f, h * 0.6f); close() }
    drawPath(beam, Brush.verticalGradient(listOf(Color(0x30FFFFFF), Color.Transparent), 0f, h * 0.6f))
  }
  val hw = S_HW + 7f; val hl = S_HL + 7f
  // gradas inclinadas con público
  fun stand(x0: Float, z0: Float, x1: Float, z1: Float, nx: Float, nz: Float, steps: Int) {
    val path = cam.poly(floatArrayOf(x0, 0f, z0, x1, 0f, z1, x1 + nx * 12f, 12f, z1 + nz * 12f, x0 + nx * 12f, 12f, z0 + nz * 12f)) ?: return
    drawPath(path, Brush.verticalGradient(listOf(Color(0xFF1A2146), Color(0xFF0A0E22)), 0f, h * 0.55f))
    for (r in 0 until 7) for (k in 0 until steps) {
      val u = (k + 0.5f) / steps; val v = (r + 0.5f) / 7f
      val gx = x0 + (x1 - x0) * u + nx * 12f * v; val gz = z0 + (z1 - z0) * u + nz * 12f * v; val gh = 12f * v
      if (!cam.visible(gx, gh + 0.9f, gz)) continue
      val q = cam.p(gx, gh + 0.9f, gz)
      if (q.x < -20f || q.x > w + 20f || q.y < -20f || q.y > h + 20f) continue
      val sc = cam.scale()
      val bob = sin(t * 3f + k * 0.7f + r) * sc * 0.14f + (if (fx.goalT > 0f) sin(t * 14f + k) * sc * 0.5f else 0f)
      val col = CROWD[(k * 7 + r * 13) % CROWD.size]
      drawCircle(col.copy(alpha = 0.9f), max(1.5f, sc * 0.55f), Offset(q.x, q.y - bob))
    }
  }
  stand(-hw, -hl, -hw, hl, -1f, 0f, 30); stand(hw, -hl, hw, hl, 1f, 0f, 30)
  stand(-hw, hl, hw, hl, 0f, 1f, 22); stand(-hw, -hl, hw, -hl, 0f, -1f, 22)
  // torres de focos en las esquinas
  for (sx in listOf(-1f, 1f)) for (sy in listOf(-1f, 1f)) {
    val px = sx * (hw + 2f); val py = sy * (hl + 2f)
    val s = cam.seg(px, 0f, py, px, 30f, py)
    if (s != null) {
      drawLine(Color(0xFF2B3354), Offset(s[0], s[1]), Offset(s[2], s[3]), max(3f, s[4] * 0.5f), StrokeCap.Round)
      if (cam.visible(px, 30f, py)) {
        val top = cam.p(px, 30f, py); val sc = cam.scale()
        drawCircle(Brush.radialGradient(listOf(Color(0xAAFFF3C4), Color.Transparent), top, sc * 5f), sc * 5f, top)
        for (a in 0 until 3) for (b in 0 until 2) drawCircle(Color(0xFFFFF8E1), max(2f, sc * 0.5f), Offset(top.x + (a - 1) * sc * 1.2f, top.y + (b - 0.5f) * sc * 1.0f))
      }
    }
  }
  // césped con franjas de corte
  val gm = 6f
  val stripes = 16
  for (i in 0 until stripes) {
    val z0 = -S_HL - gm + (2f * (S_HL + gm)) * i / stripes; val z1 = -S_HL - gm + (2f * (S_HL + gm)) * (i + 1) / stripes
    val path = cam.poly(floatArrayOf(-S_HW - gm, 0f, z0, S_HW + gm, 0f, z0, S_HW + gm, 0f, z1, -S_HW - gm, 0f, z1)) ?: continue
    drawPath(path, if (i % 2 == 0) Color(0xFF2E7D32) else Color(0xFF3B9440))
  }
  // luz de los focos sobre el césped y oscurecimiento hacia los bordes
  run {
    val c = cam.p(fx.camX * 0.9f, 0f, fx.camZ); val sc = cam.scale()
    drawCircle(Brush.radialGradient(listOf(Color(0x40FFF8D6), Color.Transparent), c, sc * 34f), sc * 34f, c)
  }
  // líneas
  val lc = Color(0xE6FFFFFF)
  fun line(x0: Float, z0: Float, x1: Float, z1: Float) {
    val s = cam.seg(x0, 0.02f, z0, x1, 0.02f, z1) ?: return
    drawLine(lc, Offset(s[0], s[1]), Offset(s[2], s[3]), max(1.5f, s[4] * 0.13f))
  }
  line(-S_HW, -S_HL, S_HW, -S_HL); line(-S_HW, S_HL, S_HW, S_HL); line(-S_HW, -S_HL, -S_HW, S_HL); line(S_HW, -S_HL, S_HW, S_HL); line(-S_HW, 0f, S_HW, 0f)
  for (end in listOf(-1f, 1f)) {
    val ey = end * S_HL
    line(-20.16f, ey, -20.16f, ey - end * 16.5f); line(20.16f, ey, 20.16f, ey - end * 16.5f); line(-20.16f, ey - end * 16.5f, 20.16f, ey - end * 16.5f)
    line(-9.16f, ey, -9.16f, ey - end * 5.5f); line(9.16f, ey, 9.16f, ey - end * 5.5f); line(-9.16f, ey - end * 5.5f, 9.16f, ey - end * 5.5f)
    if (cam.visible(0f, 0.02f, ey - end * 11f)) { val sp = cam.p(0f, 0.02f, ey - end * 11f); drawCircle(lc, max(2f, cam.scale() * 0.2f), sp) }
    // semicírculo del área
    var pa = -0.93f
    for (i in 1..16) { val a = -0.93f + 1.86f * i / 16f; line(sin(pa) * 9.15f, ey - end * 11f - end * cos(pa) * 9.15f, sin(a) * 9.15f, ey - end * 11f - end * cos(a) * 9.15f); pa = a }
  }
  for (i in 0 until 40) {
    val a0 = i * 2f * PI.toFloat() / 40f; val a1 = (i + 1) * 2f * PI.toFloat() / 40f
    line(cos(a0) * 9.15f, sin(a0) * 9.15f, cos(a1) * 9.15f, sin(a1) * 9.15f)
  }
  if (cam.visible(0f, 0.02f, 0f)) { val c0 = cam.p(0f, 0.02f, 0f); drawCircle(lc, max(2f, cam.scale() * 0.2f), c0) }
  // vallas publicitarias con luces LED
  for (side in listOf(-1f, 1f)) {
    val x = side * (S_HW + 1.6f)
    for (k in 0 until 16) {
      val z0 = -S_HL + k * (2f * S_HL / 16f); val z1 = z0 + 2f * S_HL / 16f - 0.4f
      val path = cam.poly(floatArrayOf(x, 0f, z0, x, 1.0f, z0, x, 1.0f, z1, x, 0f, z1)) ?: continue
      val lit = ((t * 2f + k) % 16f) < 2f
      drawPath(path, if (lit) Color(0xFFFFD740) else listOf(Color(0xFF1565C0), Color(0xFFC62828), Color(0xFF2E7D32))[k % 3])
    }
  }
  for (end in listOf(-1f, 1f)) {
    val y = end * (S_HL + 1.6f)
    for (k in 0 until 8) {
      val x0 = -S_HW + k * (2f * S_HW / 8f); val x1 = x0 + 2f * S_HW / 8f - 0.4f
      val path = cam.poly(floatArrayOf(x0, 0f, y, x0, 1.0f, y, x1, 1.0f, y, x1, 0f, y)) ?: continue
      drawPath(path, listOf(Color(0xFF1565C0), Color(0xFFC62828), Color(0xFF2E7D32), Color(0xFFF9A825))[k % 4])
    }
  }
  // banderines de córner
  for (sx in listOf(-1f, 1f)) for (sy in listOf(-1f, 1f)) {
    val px = sx * S_HW; val py = sy * S_HL
    val s = cam.seg(px, 0f, py, px, 1.6f, py) ?: continue
    drawLine(Color.White, Offset(s[0], s[1]), Offset(s[2], s[3]), max(2f, s[4] * 0.05f), StrokeCap.Round)
    val wave = sin(t * 5f + px) * 0.12f
    val fl = cam.poly(floatArrayOf(px, 1.6f, py, px, 1.25f, py, px - sx * 0.5f * (1f + wave), 1.42f, py + wave)) ?: continue
    drawPath(fl, Color(0xFFFFEB3B))
  }
  // ondas de golpe sobre el césped
  for (rg in fx.rings) {
    val k = (rg[2] / rg[3]).coerceIn(0f, 1f); val rad = 0.5f + rg[4] * 3.2f * k
    var prev: Offset? = null
    for (i in 0..24) {
      val a = i * 2f * PI.toFloat() / 24f
      if (!cam.visible(rg[0] + cos(a) * rad, 0.03f, rg[1] + sin(a) * rad)) { prev = null; continue }
      val o = cam.p(rg[0] + cos(a) * rad, 0.03f, rg[1] + sin(a) * rad)
      prev?.let { drawLine(Color(0xFFFFF3C4).copy(alpha = 0.7f * (1f - k)), it, o, max(2f, cam.scale() * 0.10f * (1f - k) + 1f)) }
      prev = o
    }
  }
  // sombras
  for (p in world.players) { if (!cam.visible(p.x, 0f, p.y)) continue; val q = cam.p(p.x, 0f, p.y); val s = cam.scale(); drawOval(Color(0x66000000), Offset(q.x - s * 0.55f, q.y - s * 0.17f), Size(s * 1.1f, s * 0.34f)) }
  run { val bb = world.ball; if (cam.visible(bb.x, 0f, bb.y)) { val q = cam.p(bb.x, 0f, bb.y); val s = cam.scale(); val k = 1f / (1f + bb.z * 0.3f); drawOval(Color(0x77000000), Offset(q.x - s * 0.26f * k, q.y - s * 0.08f * k), Size(s * 0.52f * k, s * 0.16f * k)) } }
  // anillo y flecha de puntería del jugador controlado
  val me = if (myId >= 0) world.players.getOrNull(myId) else null
  if (me != null && cam.visible(me.x, 0.03f, me.y)) {
    val q = cam.p(me.x, 0.03f, me.y); val s = cam.scale(); val pulse = 1f + 0.06f * sin(t * 6f)
    drawOval(Color(0xFFFFEB3B), Offset(q.x - s * 0.8f * pulse, q.y - s * 0.28f * pulse), Size(s * 1.6f * pulse, s * 0.56f * pulse), style = Stroke(max(2f, s * 0.06f)))
    val am = hypot(aimJx, aimJy)
    if (am > 0.25f) {
      val d = sDir(me.team); val ax = -aimJy * d / am; val ay = aimJx * d / am
      val len = 7f + 16f * max(charge, 0.15f)
      val e = cam.p(me.x + ax * len, 0.03f, me.y + ay * len)
      drawLine(Color(0xCCFFEB3B), q, e, max(2.5f, s * 0.07f), StrokeCap.Round)
      drawCircle(Color(0xFFFFEB3B), max(4f, s * 0.12f), e)
    }
    if (charge > 0.02f) drawArc(Color(0xFFFF5252), -90f, 360f * charge, false, Offset(q.x - s * 1.1f, q.y - s * 0.4f), Size(s * 2.2f, s * 0.8f), style = Stroke(max(3f, s * 0.1f), cap = StrokeCap.Round))
  }
  // porterías: lo que queda detrás del plano de juego
  fun goal(end: Float, front: Boolean) {
    val gz = end * S_HL; val dep = 2.4f; val bz = gz + end * dep
    val swayAmp = if (fx.goalT > 0f && sDir(fx.goalTeam) == end) 0.45f * sin(t * 16f) * (fx.goalT / 3.4f) else 0f
    val gh = S_GH
    if (!front) {
      val back = cam.poly(floatArrayOf(-S_GW, 0f, bz, S_GW, 0f, bz + swayAmp * end, S_GW, gh * 0.8f, bz + swayAmp * end, -S_GW, gh * 0.8f, bz)) ?: return
      drawPath(back, Color(0x2AFFFFFF))
      val net = Color(0x55FFFFFF)
      for (i in 0..12) { val u = -S_GW + 2f * S_GW * i / 12f; cam.seg(u, 0f, bz, u, gh * 0.8f, bz)?.let { drawLine(net, Offset(it[0], it[1]), Offset(it[2], it[3]), 1f) } }
      for (j in 1..4) { val v = gh * 0.8f * j / 5f; cam.seg(-S_GW, v, bz, S_GW, v, bz)?.let { drawLine(net, Offset(it[0], it[1]), Offset(it[2], it[3]), 1f) } }
      for (sx in listOf(-S_GW, S_GW)) {
        cam.seg(sx, gh, gz, sx, gh * 0.8f, bz)?.let { drawLine(net, Offset(it[0], it[1]), Offset(it[2], it[3]), 1.3f) }
        cam.seg(sx, 0f, gz, sx, 0f, bz)?.let { drawLine(net, Offset(it[0], it[1]), Offset(it[2], it[3]), 1.3f) }
        for (j in 1..4) { val f = j / 5f; cam.seg(sx, gh - (gh * 0.2f) * f, gz + end * dep * f, sx, 0f, gz + end * dep * f)?.let { drawLine(net, Offset(it[0], it[1]), Offset(it[2], it[3]), 1f) } }
      }
      for (i in 1..5) { val f = i / 6f; cam.seg(-S_GW, gh - gh * 0.2f * f, gz + end * dep * f, S_GW, gh - gh * 0.2f * f, gz + end * dep * f)?.let { drawLine(net, Offset(it[0], it[1]), Offset(it[2], it[3]), 1f) } }
    } else {
      for (sx in listOf(-S_GW, S_GW)) cam.seg(sx, 0f, gz, sx, gh, gz)?.let {
        val th = max(4f, it[4] * 0.13f)
        drawLine(Color(0xFFB0BEC5), Offset(it[0] + th * 0.2f, it[1]), Offset(it[2] + th * 0.2f, it[3]), th, StrokeCap.Round)
        drawLine(Color.White, Offset(it[0], it[1]), Offset(it[2], it[3]), th * 0.8f, StrokeCap.Round)
      }
      cam.seg(-S_GW, gh, gz, S_GW, gh, gz)?.let { drawLine(Color.White, Offset(it[0], it[1]), Offset(it[2], it[3]), max(4f, it[4] * 0.12f), StrokeCap.Round) }
    }
  }
  for (end in listOf(-1f, 1f)) goal(end, false)
  // jugadores y balón ordenados por profundidad (de lejos a cerca)
  data class Dr(val kind: Int, val idx: Int, val depth: Float)
  val order = ArrayList<Dr>()
  for ((i, p) in world.players.withIndex()) order.add(Dr(0, i, cam.depthOf(p.x, 0f, p.y)))
  order.add(Dr(1, 0, cam.depthOf(world.ball.x, world.ball.z, world.ball.y)))
  order.sortByDescending { it.depth }
  for (d in order) {
    if (d.kind == 1) drawBall3D(world, cam, fx, t) else if (d.depth > 1.2f) drawPlayer3D(world, world.players[d.idx], cam, pt, t, d.idx == myId)
  }
  for (end in listOf(-1f, 1f)) goal(end, true)
  // compañeros que quedan fuera de la pantalla: punto de color en el borde
  for (p in world.players) {
    if (p.isGK && p.team != (if (flip) 1 else 0)) continue
    val q = cam.p(p.x, 0f, p.y)
    if (cam.visible(p.x, 0f, p.y) && q.x in 0f..w && q.y in 0f..h) continue
    val ex = q.x.coerceIn(30f, w - 30f); val ey = q.y.coerceIn(80f, h - 30f)
    val kc = world.kitOf(p.team)
    drawCircle(Color.Black, 11f, Offset(ex, ey)); drawCircle(if (p.isGK) kc.gk else kc.shirt, 8.5f, Offset(ex, ey))
  }
  // partículas y confeti
  for (q in fx.parts) { if (!cam.visible(q.x, q.h, q.z)) continue; val a = (q.life / q.max).coerceIn(0f, 1f); val o = cam.p(q.x, q.h, q.z); drawCircle(q.color.copy(alpha = a), max(1.5f, cam.scale() * q.size * a), o) }
  // viñeta y destello de gol
  drawRect(Brush.radialGradient(listOf(Color.Transparent, Color(0x88000000)), Offset(w / 2f, h * 0.5f), max(w, h) * 0.75f))
  if (fx.flash > 0.01f) drawRect(Color.White.copy(alpha = 0.3f * fx.flash))
}

private val DOD = run {
  val p = 1.618f; val n = sqrt(1f + p * p)
  val l = ArrayList<Float>()
  for (a in listOf(-1f, 1f)) for (b in listOf(-1f, 1f)) { l.add(0f); l.add(a / n); l.add(b * p / n) }
  for (a in listOf(-1f, 1f)) for (b in listOf(-1f, 1f)) { l.add(a / n); l.add(b * p / n); l.add(0f) }
  for (a in listOf(-1f, 1f)) for (b in listOf(-1f, 1f)) { l.add(a * p / n); l.add(0f); l.add(b / n) }
  l.toFloatArray()
}

/** Balón 3D: esfera con sombreado, manchas que giran según rueda y el efecto, estela de fuego en tiros fuertes. */
private fun DrawScope.drawBall3D(world: SoccerWorld, cam: SCam, fx: SFx, t: Float) {
  val b = world.ball
  if (!cam.visible(b.x, b.z + 0.22f, b.y)) return
  val q = cam.p(b.x, b.z + 0.22f, b.y); val s = cam.scale(); val r = max(5f, s * 0.24f)
  val spd = hypot(b.vx, b.vy)
  val hot = spd > 19f
  for ((i, tp) in fx.trail.withIndex()) {
    if (!cam.visible(tp[0], tp[2] + 0.22f, tp[1])) continue
    val a = (i + 1f) / fx.trail.size; val o = cam.p(tp[0], tp[2] + 0.22f, tp[1])
    val col = if (hot) Color(1f, 0.55f + 0.4f * a, 0.1f + 0.5f * a) else Color.White
    drawCircle(col.copy(alpha = (if (hot) 0.55f else 0.25f) * a), r * (0.35f + 0.8f * a), o)
  }
  if (spd > 12f && fx.trail.size > 1) {
    val tp = fx.trail.first()
    if (cam.visible(tp[0], tp[2] + 0.22f, tp[1])) { val o = cam.p(tp[0], tp[2] + 0.22f, tp[1]); drawLine(Color.White.copy(alpha = 0.18f), o, q, r * 1.4f, StrokeCap.Round) }
  }
  if (hot) drawCircle(Brush.radialGradient(listOf(Color(0x88FFB74D), Color.Transparent), q, r * 3.2f), r * 3.2f, q)
  drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFFE6E6E6), Color(0xFF8F8F8F)), Offset(q.x - r * 0.35f, q.y - r * 0.4f), r * 1.5f), r, q)
  // manchas pentagonales de la pelota, rotadas con la orientación 3D
  val tcx = if (cam.flip) cam.cosP else -cam.cosP; val tch = cam.sinP
  val m = fx.rot
  for (k in 0 until 12) {
    val vx = DOD[k * 3]; val vh = DOD[k * 3 + 1]; val vy = DOD[k * 3 + 2]
    val rx = m[0] * vx + m[1] * vh + m[2] * vy; val rh = m[3] * vx + m[4] * vh + m[5] * vy; val ry = m[6] * vx + m[7] * vh + m[8] * vy
    val vis = rx * tcx + rh * tch
    if (vis < 0.08f) continue
    val sxr = if (cam.flip) -ry else ry
    val syu = rh * cam.cosP + (if (cam.flip) -rx else rx) * cam.sinP
    val c = Offset(q.x + sxr * r * 0.92f, q.y - syu * r * 0.92f)
    val rad = r * 0.30f * (0.35f + 0.65f * vis)
    val pent = Path()
    for (j in 0 until 5) { val a = j * 2f * PI.toFloat() / 5f + k; val px = c.x + cos(a) * rad; val py = c.y + sin(a) * rad * (0.55f + 0.45f * vis); if (j == 0) pent.moveTo(px, py) else pent.lineTo(px, py) }
    pent.close()
    drawPath(pent, Color(0xFF1F1F1F))
  }
  drawCircle(Brush.radialGradient(listOf(Color.Transparent, Color(0x55000000)), q, r), r, q)
  drawCircle(Color.White.copy(alpha = 0.75f), r * 0.12f, Offset(q.x - r * 0.38f, q.y - r * 0.42f))
}

/** Jugador 3D: cuerpo con cabeza, torso con el diseño de la camiseta, brazos y piernas que se mueven al correr, patear, barrerse o atajar. */
private fun DrawScope.drawPlayer3D(world: SoccerWorld, p: SPlayer, cam: SCam, pt: SPaints, t: Float, mine: Boolean) {
  val kit = world.kitOf(p.team)
  val jersey = if (p.isGK) kit.gk else kit.shirt
  val trim = if (p.isGK) Color(0xFF1B1B1B) else kit.shirt2
  val shorts = if (p.isGK) Color(0xFF212121) else kit.shorts
  val sock = if (p.isGK) Color(0xFF212121) else kit.socks
  val skin = SKINS[p.id % SKINS.size]; val hair = HAIRS[(p.id * 3 + 1) % HAIRS.size]
  val sp = hypot(p.vx, p.vy); val mv = min(1f, sp / 6f)
  val fx0 = cos(p.face); val fy0 = sin(p.face); val lx = -fy0; val ly = fx0
  val ph = p.run * 2.2f
  val slide = p.tackleT > 0.5f; val dive = p.diveT > 0f; val stun = p.stunT > 0.3f
  fun P(f: Float, l: Float, hh: Float) = floatArrayOf(p.x + fx0 * f + lx * l, hh, p.y + fy0 * f + ly * l)
  fun dep(a: FloatArray): Float = cam.depthOf(a[0], a[1], a[2])
  fun sc(a: FloatArray): Offset = cam.p(a[0], a[1], a[2])
  fun mid(a: FloatArray, b: FloatArray) = floatArrayOf((a[0] + b[0]) / 2f, (a[1] + b[1]) / 2f, (a[2] + b[2]) / 2f)

  val lean = 0.10f * mv
  var hipC = P(0f, 0f, 0.92f); var shC = P(lean, 0f, 1.46f); var head = P(lean * 1.4f, 0f, 1.70f)
  if (slide) { hipC = P(-0.45f, 0f, 0.30f); shC = P(0.35f, 0f, 0.36f); head = P(0.62f, 0f, 0.42f) }
  else if (stun) { shC = P(-0.12f, 0f, 1.43f); head = P(-0.2f, 0f, 1.62f) }
  else if (dive) { shC = P(0.1f, 0f, 1.30f); head = P(0.22f, 0f, 1.50f) }
  fun foot(side: Float): FloatArray {
    if (slide) return if (side > 0) P(1.0f, 0.08f, 0.14f) else P(0.15f, -0.14f, 0.10f)
    val phase = ph + (if (side > 0) 0f else PI.toFloat())
    var f = sin(phase) * 0.40f * mv; var hh = 0.05f + max(0f, cos(phase)) * 0.24f * mv
    if (p.kickT > 0f && side < 0) { f = 0.55f; hh = 0.30f }
    if (dive) { f = side * 0.0f - 0.1f; hh = 0.05f + (if (side > 0) 0.25f else 0f) }
    return P(f, side * 0.10f, hh)
  }
  fun hand(side: Float): FloatArray {
    if (slide) return P(0.25f, side * 0.34f, 0.36f)
    if (dive) return P(0.35f, side * 0.28f, 1.95f)
    val phase = ph + (if (side > 0) PI.toFloat() else 0f)
    return P(sin(phase) * 0.32f * mv + lean, side * 0.27f, 0.80f + max(0f, cos(phase)) * 0.12f * mv)
  }
  val dn = floatArrayOf(hipC[0] - shC[0], hipC[1] - shC[1], hipC[2] - shC[2]).let { d -> val m = sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]).coerceAtLeast(0.01f); floatArrayOf(d[0] / m, d[1] / m, d[2] / m) }
  fun off(a: FloatArray, d: FloatArray, k: Float) = floatArrayOf(a[0] + d[0] * k, a[1] + d[1] * k, a[2] + d[2] * k)
  val lat = floatArrayOf(lx, 0f, ly)

  fun cap(a: FloatArray, b: FloatArray, th: Float, c: Color) {
    val pa = sc(a); val sa = cam.scale(); val pb = sc(b); val sb = cam.scale()
    val wpx = max(2f, th * (sa + sb) / 2f)
    drawLine(shade(c, 0.55f), Offset(pa.x + wpx * 0.10f, pa.y + wpx * 0.10f), Offset(pb.x + wpx * 0.10f, pb.y + wpx * 0.10f), wpx, StrokeCap.Round)
    drawLine(c, pa, pb, wpx * 0.9f, StrokeCap.Round)
    drawLine(shade(c, 1.35f).copy(alpha = 0.45f), Offset(pa.x - wpx * 0.2f, pa.y), Offset(pb.x - wpx * 0.2f, pb.y), wpx * 0.22f, StrokeCap.Round)
  }
  fun orb(a: FloatArray, rM: Float, c: Color) {
    val o = sc(a); val r = max(2f, rM * cam.scale())
    drawCircle(Brush.radialGradient(listOf(shade(c, 1.25f), c, shade(c, 0.55f)), Offset(o.x - r * 0.35f, o.y - r * 0.4f), r * 1.5f), r, o)
  }
  fun leg(side: Float) {
    val hip = off(P(0f, side * 0.10f, 0f).also { it[1] = hipC[1] }, floatArrayOf(hipC[0] - p.x, 0f, hipC[2] - p.y), 1f)
    val ft = foot(side)
    val kn = mid(hip, ft); kn[0] += fx0 * 0.10f * max(mv, 0.2f); kn[2] += fy0 * 0.10f * max(mv, 0.2f)
    cap(hip, kn, 0.13f, skin); cap(hip, off(hip, floatArrayOf(kn[0] - hip[0], kn[1] - hip[1], kn[2] - hip[2]), 0.62f), 0.19f, shorts)
    cap(kn, ft, 0.105f, sock)
    val toe = floatArrayOf(ft[0] + fx0 * 0.20f, ft[1], ft[2] + fy0 * 0.20f)
    cap(ft, toe, 0.12f, Color(0xFF151515))
  }
  fun arm(side: Float) {
    val sh = floatArrayOf(shC[0] + lat[0] * side * 0.23f, shC[1] - 0.04f, shC[2] + lat[2] * side * 0.23f)
    val hd = hand(side)
    val el = mid(sh, hd); el[1] -= 0.05f; el[0] += lat[0] * side * 0.04f; el[2] += lat[2] * side * 0.04f
    cap(sh, el, 0.10f, jersey); cap(el, hd, 0.075f, skin)
    orb(hd, if (p.isGK) 0.075f else 0.05f, if (p.isGK) Color(0xFFFF7043) else skin)
  }
  val dL = dep(P(0f, 0.10f, 0.9f)); val dR = dep(P(0f, -0.10f, 0.9f))
  val farSide = if (dL > dR) 1f else -1f

  arm(farSide); leg(farSide); leg(-farSide)
  // pantaloneta + torso con el diseño de la camiseta
  val A = off(hipC, lat, 0.17f); val B = off(hipC, lat, -0.17f); val C = off(shC, lat, -0.22f); val D = off(shC, lat, 0.22f)
  val oa = sc(A); val ob = sc(B); val oc = sc(C); val od = sc(D)
  fun bil(u: Float, v: Float) = Offset((1 - u) * (1 - v) * oa.x + u * (1 - v) * ob.x + u * v * oc.x + (1 - u) * v * od.x, (1 - u) * (1 - v) * oa.y + u * (1 - v) * ob.y + u * v * oc.y + (1 - u) * v * od.y)
  fun quadP(pts: List<Offset>): Path = Path().apply { pts.forEachIndexed { i, o -> if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) }; close() }
  val sA = off(A, dn, 0.04f); val sB = off(B, dn, 0.04f); val sC = off(B, dn, 0.30f); val sD = off(A, dn, 0.30f)
  drawPath(quadP(listOf(sc(sA), sc(sB), sc(sC), sc(sD))), shorts)
  drawPath(quadP(listOf(oa, ob, oc, od)), jersey)
  if (!p.isGK) when (kit.pattern) {
    1 -> for (k in 0 until 5 step 2) drawPath(quadP(listOf(bil(k / 5f, 0f), bil((k + 1) / 5f, 0f), bil((k + 1) / 5f, 1f), bil(k / 5f, 1f))), trim)
    2 -> drawPath(quadP(listOf(bil(0f, 0.34f), bil(1f, 0.34f), bil(1f, 0.64f), bil(0f, 0.34f + 0.30f))), trim)
    3 -> drawPath(quadP(listOf(bil(0f, 1f), bil(0.40f, 1f), bil(1f, 0.20f), bil(1f, 0f), bil(0.70f, 0f))), trim)
    4 -> for (k in 0 until 4 step 2) drawPath(quadP(listOf(bil(0f, k / 4f), bil(1f, k / 4f), bil(1f, (k + 1) / 4f), bil(0f, (k + 1) / 4f))), trim)
    5 -> drawPath(quadP(listOf(bil(0.5f, 0f), bil(1f, 0f), bil(1f, 1f), bil(0.5f, 1f))), trim)
    6 -> { drawPath(quadP(listOf(bil(0f, 0f), bil(0.14f, 0f), bil(0.14f, 1f), bil(0f, 1f))), trim); drawPath(quadP(listOf(bil(0.86f, 0f), bil(1f, 0f), bil(1f, 1f), bil(0.86f, 1f))), trim) }
  }
  drawPath(quadP(listOf(bil(0.62f, 0f), bil(1f, 0f), bil(1f, 1f), bil(0.62f, 1f))), Color(0x26000000))
  drawPath(quadP(listOf(bil(0f, 0f), bil(0.2f, 0f), bil(0.2f, 1f), bil(0f, 1f))), Color(0x20FFFFFF))
  // número en la espalda cuando se ve de espaldas
  val camDir = if (cam.flip) 1f else -1f
  val facing = fx0 * camDir
  val s0 = cam.scale()
  if (facing < -0.2f && s0 > 26f && !slide) {
    val c = bil(0.5f, 0.55f)
    txt(pt, "${p.number}", c.x, c.y + s0 * 0.12f, s0 * 0.34f, if ((jersey.red + jersey.green + jersey.blue) / 3f > 0.6f) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
  }
  arm(-farSide)
  // cuello y cabeza
  val nk = off(shC, floatArrayOf(0f, 1f, 0f), 0.06f)
  cap(shC, nk, 0.09f, skin)
  orb(head, 0.115f, skin)
  val ho = sc(head); val hr = max(3f, 0.115f * cam.scale())
  if (facing < 0f) drawCircle(hair, hr * 1.03f, ho) else drawArc(hair, 180f, 180f, true, Offset(ho.x - hr * 1.04f, ho.y - hr * 1.08f), Size(hr * 2.08f, hr * 1.5f))
  if (facing > 0.15f) {
    for (sd in listOf(-1f, 1f)) {
      val e = floatArrayOf(head[0] + fx0 * 0.10f + lat[0] * sd * 0.042f, head[1] + 0.012f, head[2] + fy0 * 0.10f + lat[2] * sd * 0.042f)
      drawCircle(Color(0xFF1B1B1B), max(1.5f, hr * 0.11f), sc(e))
    }
  }
  if (stun) for (i in 0 until 3) { val a = t * 7f + i * 2.1f; val st = floatArrayOf(head[0] + cos(a) * 0.22f * lx + sin(a) * 0.22f * fx0, head[1] + 0.28f, head[2] + cos(a) * 0.22f * ly + sin(a) * 0.22f * fy0); drawCircle(Color(0xFFFFEB3B), max(2f, hr * 0.22f), sc(st)) }
  // indicador y nombre sobre la cabeza
  val top = cam.p(head[0], head[1] + 0.32f, head[2]); val s1 = cam.scale()
  if (mine) {
    val bob = sin(t * 6f) * s1 * 0.05f
    val tri = Path().apply { moveTo(top.x, top.y - s1 * 0.05f + bob); lineTo(top.x - s1 * 0.16f, top.y - s1 * 0.38f + bob); lineTo(top.x + s1 * 0.16f, top.y - s1 * 0.38f + bob); close() }
    drawPath(tri, Color(0xFFFFEB3B)); drawPath(tri, Color(0xFF3E2723), style = Stroke(max(1.5f, s1 * 0.03f)))
  }
  if ((p.human || mine) && p.name.isNotEmpty() && s1 > 22f) txt(pt, p.name, top.x, top.y - s1 * (if (mine) 0.5f else 0.12f), max(11f, s1 * 0.22f), if (mine) android.graphics.Color.YELLOW else android.graphics.Color.WHITE)
}

/** Radar: toda la cancha con los jugadores y el balón; la vista se gira para el equipo que ataca hacia y negativo. */
internal fun DrawScope.drawRadar(world: SoccerWorld, flip: Boolean, x0: Float, y0: Float, wid: Float, myId: Int) {
  val hgt = wid * (2f * S_HL) / (2f * S_HW)
  drawRect(Color(0xAA0B3D13), Offset(x0, y0), Size(wid, hgt))
  drawRect(Color(0x99FFFFFF), Offset(x0, y0), Size(wid, hgt), style = Stroke(1.5f))
  drawLine(Color(0x66FFFFFF), Offset(x0, y0 + hgt / 2f), Offset(x0 + wid, y0 + hgt / 2f), 1f)
  fun pos(x: Float, y: Float): Offset { val vx = if (flip) -x else x; val vy = if (flip) -y else y; return Offset(x0 + (vx + S_HW) / (2f * S_HW) * wid, y0 + hgt - (vy + S_HL) / (2f * S_HL) * hgt) }
  for (p in world.players) { val kc = world.kitOf(p.team); val rc = if (p.isGK) kc.gk else kc.shirt; drawCircle(Color.Black, (if (p.id == myId) 5f else 3.2f) + 1f, pos(p.x, p.y)); drawCircle(rc, if (p.id == myId) 5f else 3.2f, pos(p.x, p.y)) }
  drawCircle(Color.White, 2.8f, pos(world.ball.x, world.ball.y))
}
