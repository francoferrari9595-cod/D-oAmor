package com.example.ui.screens

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// ═══════════════════════════════════════════════════════════════════════════════
//  MOTOR 3D DE CARRERAS (Canvas)
//  · Los autos son mallas 3D reales (carrocería, cabina, cristales, ruedas octogonales, alerón, luces)
//    proyectadas con perspectiva, iluminadas por cara (difusa + brillo especular) y ordenadas por profundidad.
//  · El mundo se proyecta con cámara de persecución: calzada, banquinas, guardarraíles con postes,
//    edificios con ventanas, pinos, palmeras, cactus, farolas, carteles, pórticos y mar.
//  · 4 zonas con su propio cielo y ambiente: bosque, ciudad nocturna, desierto y costa.
// ═══════════════════════════════════════════════════════════════════════════════

private const val K_BODY = 0
private const val K_GLASS = 1
private const val K_TIRE = 2
private const val K_RIM = 3
private const val K_LIGHT = 4
private const val K_DARK = 5
private const val K_STRIPE = 6
private const val K_PLATE = 7

private fun r3Mix(a: Color, b: Color, t: Float): Color {
  val k = t.coerceIn(0f, 1f)
  return Color(a.red + (b.red - a.red) * k, a.green + (b.green - a.green) * k, a.blue + (b.blue - a.blue) * k, 1f)
}
private fun r3Shade(c: Color, k: Float): Color = Color((c.red * k).coerceIn(0f, 1f), (c.green * k).coerceIn(0f, 1f), (c.blue * k).coerceIn(0f, 1f), 1f)
private fun r3Hash(i: Int, k: Int): Float {
  var h = i * 374761393 + k * 668265263
  h = (h xor (h ushr 13)) * 1274126177.toInt()
  h = h xor (h ushr 16)
  return (h and 0xFFFF) / 65535f
}

// ───────────────────────── Malla del auto ─────────────────────────
internal class R3Poly(val p: FloatArray, val nx: Float, val ny: Float, val nz: Float, val kind: Int, val decal: Boolean)

private fun r3Poly(out: MutableList<R3Poly>, kind: Int, decal: Boolean, cx: Float, cy: Float, cz: Float, vararg p: Float) {
  val n = p.size / 3
  var nx = 0f; var ny = 0f; var nz = 0f
  var mx = 0f; var my = 0f; var mz = 0f
  for (i in 0 until n) {
    val j = (i + 1) % n
    val x0 = p[i * 3]; val y0 = p[i * 3 + 1]; val z0 = p[i * 3 + 2]
    val x1 = p[j * 3]; val y1 = p[j * 3 + 1]; val z1 = p[j * 3 + 2]
    nx += (y0 - y1) * (z0 + z1); ny += (z0 - z1) * (x0 + x1); nz += (x0 - x1) * (y0 + y1)
    mx += x0; my += y0; mz += z0
  }
  val l = sqrt(nx * nx + ny * ny + nz * nz)
  if (l < 1e-6f) return
  nx /= l; ny /= l; nz /= l
  mx = mx / n - cx; my = my / n - cy; mz = mz / n - cz
  if (nx * mx + ny * my + nz * mz < 0f) { nx = -nx; ny = -ny; nz = -nz }
  out.add(R3Poly(p, nx, ny, nz, kind, decal))
}

private fun r3Box(out: MutableList<R3Poly>, kind: Int, x0: Float, x1: Float, y0: Float, y1: Float, z0: Float, z1: Float) {
  val cx = (x0 + x1) / 2f; val cy = (y0 + y1) / 2f; val cz = (z0 + z1) / 2f
  r3Poly(out, kind, false, cx, cy, cz, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1)
  r3Poly(out, kind, false, cx, cy, cz, x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1)
  r3Poly(out, kind, false, cx, cy, cz, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1)
  r3Poly(out, kind, false, cx, cy, cz, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1)
  r3Poly(out, kind, false, cx, cy, cz, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0)
  r3Poly(out, kind, false, cx, cy, cz, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1)
}

private fun r3Hw(y: Float): Float = if (y <= 0.92f) 0.93f else 0.93f - (y - 0.92f) / (1.32f - 0.92f) * 0.20f

private fun r3BuildCar(seg: Int): List<R3Poly> {
  val out = ArrayList<R3Poly>()
  val pz = floatArrayOf(-2.20f, -2.22f, -2.10f, -1.30f, -0.80f, -0.35f, 0.45f, 0.95f, 1.85f, 2.25f, 2.25f)
  val py = floatArrayOf(0.30f, 0.62f, 0.86f, 0.92f, 1.00f, 1.32f, 1.32f, 0.98f, 0.80f, 0.55f, 0.30f)
  val ek = intArrayOf(K_BODY, K_BODY, K_BODY, K_BODY, K_GLASS, K_BODY, K_GLASS, K_BODY, K_BODY, K_DARK)
  val cy = 0.8f
  for (e in 0 until 10) {
    val i = e; val j = e + 1
    r3Poly(out, ek[e], false, 0f, cy, 0f, -r3Hw(py[i]), py[i], pz[i], -r3Hw(py[j]), py[j], pz[j], r3Hw(py[j]), py[j], pz[j], r3Hw(py[i]), py[i], pz[i])
  }
  for (side in listOf(-1f, 1f)) {
    val arr = FloatArray(33)
    for (i in 0 until 11) { arr[i * 3] = side * r3Hw(py[i]); arr[i * 3 + 1] = py[i]; arr[i * 3 + 2] = pz[i] }
    r3Poly(out, K_BODY, false, 0f, cy, 0f, *arr)
    // ventanillas laterales
    val wz = floatArrayOf(-0.70f, -0.33f, 0.42f, 0.82f); val wy = floatArrayOf(1.02f, 1.28f, 1.28f, 1.01f)
    val wa = FloatArray(12)
    for (i in 0 until 4) { wa[i * 3] = side * (r3Hw(wy[i]) + 0.004f); wa[i * 3 + 1] = wy[i]; wa[i * 3 + 2] = wz[i] }
    r3Poly(out, K_GLASS, true, 0f, cy, 0f, *wa)
  }
  // franjas de carrera sobre baúl, techo y capó
  for (e in intArrayOf(2, 3, 5, 7)) {
    r3Poly(out, K_STRIPE, true, 0f, cy, 0f, -0.11f, py[e] + 0.006f, pz[e], -0.11f, py[e + 1] + 0.006f, pz[e + 1], 0.11f, py[e + 1] + 0.006f, pz[e + 1], 0.11f, py[e] + 0.006f, pz[e])
  }
  // luces traseras (emisivas) y patente
  for (side in listOf(-1f, 1f)) {
    val xa = side * 0.30f; val xb = side * 0.86f
    r3Poly(out, K_LIGHT, true, 0f, cy, 0f, xa, 0.66f, -2.215f, xb, 0.66f, -2.215f, xb, 0.78f, -2.155f, xa, 0.78f, -2.155f)
  }
  r3Poly(out, K_LIGHT, true, 0f, cy, 0f, -0.30f, 0.70f, -2.20f, 0.30f, 0.70f, -2.20f, 0.30f, 0.73f, -2.185f, -0.30f, 0.73f, -2.185f)
  r3Poly(out, K_PLATE, true, 0f, cy, 0f, -0.28f, 0.36f, -2.205f, 0.28f, 0.36f, -2.205f, 0.28f, 0.52f, -2.215f, -0.28f, 0.52f, -2.215f)
  // alerón
  r3Box(out, K_DARK, -0.88f, 0.88f, 1.02f, 1.07f, -2.26f, -1.86f)
  r3Box(out, K_DARK, -0.50f, -0.44f, 0.86f, 1.02f, -2.06f, -1.98f)
  r3Box(out, K_DARK, 0.44f, 0.50f, 0.86f, 1.02f, -2.06f, -1.98f)
  // espejos
  r3Box(out, K_BODY, 0.93f, 1.10f, 1.00f, 1.09f, 0.50f, 0.74f)
  r3Box(out, K_BODY, -1.10f, -0.93f, 1.00f, 1.09f, 0.50f, 0.74f)
  // ruedas
  val r = 0.34f
  for (sx in listOf(-1f, 1f)) for (zc in listOf(-1.38f, 1.38f)) {
    val xi = sx * 0.67f; val xo = sx * 0.97f
    val yc = r
    val wcx = sx * 0.80f
    val ys = FloatArray(seg); val zs = FloatArray(seg)
    for (i in 0 until seg) { val a = i * 2f * PI.toFloat() / seg; ys[i] = yc + r * cos(a); zs[i] = zc + r * sin(a) }
    for (i in 0 until seg) {
      val j = (i + 1) % seg
      r3Poly(out, K_TIRE, false, wcx, yc, zc, xi, ys[i], zs[i], xi, ys[j], zs[j], xo, ys[j], zs[j], xo, ys[i], zs[i])
    }
    val cap = FloatArray(seg * 3)
    for (i in 0 until seg) { cap[i * 3] = xo; cap[i * 3 + 1] = ys[i]; cap[i * 3 + 2] = zs[i] }
    r3Poly(out, K_TIRE, false, wcx, yc, zc, *cap)
    val rim = FloatArray(seg * 3)
    for (i in 0 until seg) { val a = i * 2f * PI.toFloat() / seg; rim[i * 3] = xo + sx * 0.005f; rim[i * 3 + 1] = yc + r * 0.62f * cos(a); rim[i * 3 + 2] = zc + r * 0.62f * sin(a) }
    r3Poly(out, K_RIM, true, wcx, yc, zc, *rim)
  }
  return out
}

private val R3_CAR_HI: List<R3Poly> by lazy { r3BuildCar(8) }
private val R3_CAR_LO: List<R3Poly> by lazy { r3BuildCar(6) }

private class R3Draw(val key: Float, val path: Path, val color: Color, val outline: Boolean)
private val r3Pool = ArrayList<Path>()
private var r3PoolN = 0
private fun r3Path(): Path {
  if (r3PoolN >= r3Pool.size) r3Pool.add(Path())
  val p = r3Pool[r3PoolN++]
  p.rewind()
  return p
}

private const val LX = 0.42f
private const val LY = 0.84f
private const val LZ = -0.34f

/**
 * Dibuja un auto 3D con el punto de apoyo central en (gx, gy). `ppm` = píxeles por metro en ese punto.
 * `yaw` gira el auto respecto de la cámara (curvas, cambio de carril).
 */
private fun DrawScope.r3Car(
  hi: Boolean, gx: Float, gy: Float, ppm: Float, yaw: Float, body: Color, camDist: Float, camH: Float, pitch: Float,
  brake: Boolean, flame: Float, skyCol: Color, fog: Float, fogCol: Color
) {
  r3PoolN = 0
  val mesh = if (hi) R3_CAR_HI else R3_CAR_LO
  val cy = cos(yaw); val sy = sin(yaw); val cp = cos(pitch); val sp = sin(pitch)
  val zc0 = camH * sp + camDist * cp
  val yc0 = -camH * cp + camDist * sp
  val v0 = -yc0 / zc0
  val ks = ppm * zc0
  fun px(x: Float, y: Float, z: Float): Offset {
    val xw = x * cy + z * sy; val zw = -x * sy + z * cy
    val ry = y - camH; val rz = zw + camDist
    val yc = ry * cp + rz * sp; val zc = -ry * sp + rz * cp
    return Offset(gx + (xw / zc) * ks, gy + (-yc / zc - v0) * ks)
  }
  // sombra de contacto
  drawOval(Color(0x77000000), Offset(gx - ppm * 1.25f, gy - ppm * 0.22f), Size(ppm * 2.5f, ppm * 0.60f))
  // posición de la cámara en el espacio del auto ya girado
  val camX = 0f; val camY = camH; val camZ = -camDist
  val list = ArrayList<R3Draw>()
  for (pl in mesh) {
    val n = pl.p.size / 3
    // normal girada
    val nx = pl.nx * cy + pl.nz * sy; val nz = -pl.nx * sy + pl.nz * cy; val ny = pl.ny
    val x0 = pl.p[0] * cy + pl.p[2] * sy; val z0 = -pl.p[0] * sy + pl.p[2] * cy; val y0 = pl.p[1]
    val vx = camX - x0; val vy = camY - y0; val vz = camZ - z0
    val vl = sqrt(vx * vx + vy * vy + vz * vz)
    val facing = (nx * vx + ny * vy + nz * vz) / vl
    if (facing <= 0.001f) continue
    val dif = max(0f, nx * LX + ny * LY + nz * LZ)
    val hx = LX + vx / vl; val hy = LY + vy / vl; val hz = LZ + vz / vl
    val hl = sqrt(hx * hx + hy * hy + hz * hz)
    val spec = max(0f, (nx * hx + ny * hy + nz * hz) / hl).pow(26f)
    val k = 0.40f + 0.72f * dif + 0.10f * ny
    var col = when (pl.kind) {
      K_BODY -> r3Mix(r3Shade(body, k), Color.White, spec * 0.55f)
      K_STRIPE -> r3Shade(Color(0xFFF2F2F2), k)
      K_GLASS -> r3Mix(Color(0xFF0A1422), skyCol, 0.16f + 0.45f * max(0f, ny) + 0.5f * spec)
      K_TIRE -> r3Shade(Color(0xFF18181B), 0.65f + 0.5f * dif)
      K_RIM -> r3Mix(r3Shade(Color(0xFFC9CED8), k), Color.White, spec * 0.5f)
      K_LIGHT -> if (brake) Color(0xFFFF5A5A) else Color(0xFFD7192B)
      K_PLATE -> r3Shade(Color.White, k)
      else -> r3Shade(Color(0xFF15151A), 0.7f + 0.5f * dif)
    }
    if (fog > 0f) col = r3Mix(col, fogCol, fog)
    val path = r3Path()
    var depth = 0f
    for (i in 0 until n) {
      val o = px(pl.p[i * 3], pl.p[i * 3 + 1], pl.p[i * 3 + 2])
      if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
      val zz = -pl.p[i * 3] * sy + pl.p[i * 3 + 2] * cy
      depth += zz
    }
    path.close()
    list.add(R3Draw(depth / n - (if (pl.decal) 0.06f else 0f), path, col, pl.kind != K_LIGHT && pl.kind != K_STRIPE))
  }
  list.sortByDescending { it.key }
  val edge = Color(0x44000000)
  for (d in list) {
    drawPath(d.path, d.color)
    if (d.outline && ppm > 11f) drawPath(d.path, edge, style = Stroke(0.7f))
  }
  // brillo de luces traseras
  val gl = if (brake) 0.9f else 0.45f
  for (sx in listOf(-0.58f, 0.58f)) {
    val c = px(sx, 0.72f, -2.2f)
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFF2D3F).copy(alpha = gl), Color.Transparent), c, ppm * (if (brake) 0.85f else 0.5f)), ppm * (if (brake) 0.85f else 0.5f), c)
  }
  // llamas del nitro
  if (flame > 0f) {
    for (sx in listOf(-0.55f, 0.55f)) {
      val a = px(sx, 0.42f, -2.25f); val b = px(sx * 0.8f, 0.40f, -2.25f - (1.2f + 2.4f * flame))
      val wdt = ppm * 0.16f
      val f = Path(); f.moveTo(a.x - wdt, a.y); f.lineTo(b.x, b.y); f.lineTo(a.x + wdt, a.y); f.close()
      drawPath(f, Brush.linearGradient(listOf(Color(0xFFFFF59D), Color(0xFFFF6D00), Color(0x00FF1744)), a, b))
      drawCircle(Color(0xFF9BE7FF).copy(alpha = 0.9f), wdt * 0.55f, a)
    }
  }
}

// ───────────────────────── Zonas ─────────────────────────
private class R3Zone(val skyTop: Color, val skyMid: Color, val skyHor: Color, val sun: Color, val gA: Color, val gB: Color, val fog: Color, val hill: Color)

private val R3_ZONES = listOf(
  R3Zone(Color(0xFF1B1446), Color(0xFFD9507A), Color(0xFFFFB066), Color(0xFFFFE9A8), Color(0xFF1F6B33), Color(0xFF185A2A), Color(0xFFFFA876), Color(0xFF3A2A5E)),
  R3Zone(Color(0xFF060A24), Color(0xFF3A1B6B), Color(0xFFFF6B9D), Color(0xFFFFD1E8), Color(0xFF2A2E3A), Color(0xFF22252F), Color(0xFF7A4A8F), Color(0xFF1B1038)),
  R3Zone(Color(0xFF2C3E8F), Color(0xFFF2994A), Color(0xFFFFE0A0), Color(0xFFFFF6D0), Color(0xFFC79A55), Color(0xFFB88848), Color(0xFFFFD9A0), Color(0xFF9A6A3A)),
  R3Zone(Color(0xFF0F2A6B), Color(0xFFFF7B54), Color(0xFFFFCF8A), Color(0xFFFFF0C0), Color(0xFF2E8B4F), Color(0xFF267A44), Color(0xFFFFC08A), Color(0xFF2B4A7A))
)
private const val ZONE_LEN = 300

private fun r3LerpZone(a: R3Zone, b: R3Zone, t: Float) = R3Zone(
  r3Mix(a.skyTop, b.skyTop, t), r3Mix(a.skyMid, b.skyMid, t), r3Mix(a.skyHor, b.skyHor, t), r3Mix(a.sun, b.sun, t),
  r3Mix(a.gA, b.gA, t), r3Mix(a.gB, b.gB, t), r3Mix(a.fog, b.fog, t), r3Mix(a.hill, b.hill, t)
)

private fun zoneOf(abs: Int): Int = (abs / ZONE_LEN) % R3_ZONES.size

// ───────────────────────── Helpers de dibujo ─────────────────────────
private fun DrawScope.latQuad(x1: Float, y1: Float, w1: Float, x2: Float, y2: Float, w2: Float, a: Float, b: Float, color: Color) {
  val p = r3Path()
  p.moveTo(x1 + w1 * a, y1); p.lineTo(x1 + w1 * b, y1); p.lineTo(x2 + w2 * b, y2); p.lineTo(x2 + w2 * a, y2); p.close()
  drawPath(p, color)
}

private fun DrawScope.r3Poly4(color: Color, ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float, dx: Float, dy: Float) {
  val p = r3Path()
  p.moveTo(ax, ay); p.lineTo(bx, by); p.lineTo(cx, cy); p.lineTo(dx, dy); p.close()
  drawPath(p, color)
}

private fun DrawScope.r3Pine(x: Float, y: Float, s: Float, hmul: Float, fogCol: Color, fog: Float) {
  val hgt = s * 9f * hmul
  drawOval(Color(0x33000000), Offset(x - s * 2.2f, y - s * 0.3f), Size(s * 4.4f, s * 0.7f))
  drawRect(r3Mix(Color(0xFF4E342E), fogCol, fog), Offset(x - s * 0.25f, y - hgt * 0.25f), Size(s * 0.5f, hgt * 0.25f))
  for (l in 0 until 3) {
    val by = y - hgt * (0.18f + l * 0.26f)
    val tp = by - hgt * 0.42f
    val bw = s * (2.6f - l * 0.55f)
    val pl = Path(); pl.moveTo(x, tp); pl.lineTo(x - bw, by); pl.lineTo(x + bw, by); pl.close()
    drawPath(pl, r3Mix(r3Mix(Color(0xFF2E7D32), Color(0xFF0B3D14), l * 0.25f), fogCol, fog))
    val pr = Path(); pr.moveTo(x, tp); pr.lineTo(x + bw, by); pr.lineTo(x, by); pr.close()
    drawPath(pr, r3Mix(Color(0xFF0B3D14), fogCol, fog).copy(alpha = 0.55f))
  }
}

private fun DrawScope.r3Palm(x: Float, y: Float, s: Float, lean: Float, fogCol: Color, fog: Float) {
  val top = Offset(x + lean * s * 2f, y - s * 8f)
  val trunk = Path(); trunk.moveTo(x - s * 0.22f, y); trunk.quadraticBezierTo(x + lean * s, y - s * 4f, top.x - s * 0.12f, top.y); trunk.lineTo(top.x + s * 0.12f, top.y)
  trunk.quadraticBezierTo(x + lean * s + s * 0.2f, y - s * 4f, x + s * 0.22f, y); trunk.close()
  drawPath(trunk, r3Mix(Color(0xFF8D6E4A), fogCol, fog))
  for (i in 0 until 7) {
    val a = (i / 7f) * 2f * PI.toFloat()
    val ex = top.x + cos(a) * s * 3.2f; val ey = top.y + sin(a) * s * 1.2f + s * 1.1f
    val fr = Path(); fr.moveTo(top.x, top.y); fr.quadraticBezierTo((top.x + ex) / 2f, top.y - s * 1.3f, ex, ey)
    drawPath(fr, r3Mix(Color(0xFF2E9B4F), fogCol, fog), style = Stroke(s * 0.45f, cap = StrokeCap.Round))
  }
}

private fun DrawScope.r3Cactus(x: Float, y: Float, s: Float, fogCol: Color, fog: Float) {
  val c = r3Mix(Color(0xFF2E7D4F), fogCol, fog)
  drawRect(c, Offset(x - s * 0.45f, y - s * 4.5f), Size(s * 0.9f, s * 4.5f))
  drawRect(c, Offset(x - s * 1.6f, y - s * 3.2f), Size(s * 0.6f, s * 1.5f)); drawRect(c, Offset(x - s * 1.6f, y - s * 2.0f), Size(s * 1.2f, s * 0.55f))
  drawRect(c, Offset(x + s * 1.0f, y - s * 3.6f), Size(s * 0.6f, s * 1.5f)); drawRect(c, Offset(x + s * 0.4f, y - s * 2.5f), Size(s * 1.2f, s * 0.55f))
  drawRect(Color(0x33000000), Offset(x + s * 0.2f, y - s * 4.5f), Size(s * 0.25f, s * 4.5f))
}

private fun DrawScope.r3Rock(x: Float, y: Float, s: Float, fogCol: Color, fog: Float) {
  val p = Path(); p.moveTo(x - s * 1.6f, y); p.lineTo(x - s * 1.1f, y - s * 1.4f); p.lineTo(x - s * 0.2f, y - s * 2.1f); p.lineTo(x + s * 0.9f, y - s * 1.3f); p.lineTo(x + s * 1.7f, y); p.close()
  drawPath(p, r3Mix(Color(0xFF8D6E63), fogCol, fog))
  val q = Path(); q.moveTo(x - s * 0.2f, y - s * 2.1f); q.lineTo(x + s * 0.9f, y - s * 1.3f); q.lineTo(x + s * 1.7f, y); q.lineTo(x + s * 0.3f, y); q.close()
  drawPath(q, Color(0x44000000))
}

private fun DrawScope.r3Lamp(x: Float, y: Float, s: Float, dir: Float, fogCol: Color, fog: Float, night: Boolean) {
  val pc = r3Mix(Color(0xFF37474F), fogCol, fog)
  drawRect(pc, Offset(x - s * 0.12f, y - s * 8.5f), Size(s * 0.24f, s * 8.5f))
  val hx = x + dir * s * 2.4f
  drawLine(pc, Offset(x, y - s * 8.4f), Offset(hx, y - s * 8.7f), s * 0.2f)
  drawRect(Color(0xFFFFF3C4), Offset(hx - s * 0.5f, y - s * 8.75f), Size(s * 1.0f, s * 0.25f))
  val a = if (night) 0.65f else 0.35f
  drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE9A8).copy(alpha = a * (1f - fog)), Color.Transparent), Offset(hx, y - s * 8.6f), s * 4.2f), s * 4.2f, Offset(hx, y - s * 8.6f))
  drawOval(Color(0xFFFFE9A8).copy(alpha = 0.10f * (1f - fog)), Offset(hx - s * 3f, y - s * 0.4f), Size(s * 6f, s * 0.8f))
}

private fun DrawScope.r3Billboard(x: Float, y: Float, s: Float, hue: Int, fogCol: Color, fog: Float) {
  val pc = r3Mix(Color(0xFF455A64), fogCol, fog)
  drawRect(pc, Offset(x - s * 3.5f, y - s * 8f), Size(s * 0.3f, s * 8f)); drawRect(pc, Offset(x + s * 3.2f, y - s * 8f), Size(s * 0.3f, s * 8f))
  val c1 = when (hue % 3) { 0 -> Color(0xFFFF2E93); 1 -> Color(0xFF00B8D4); else -> Color(0xFFFFB300) }
  val c2 = when (hue % 3) { 0 -> Color(0xFF5B2BD6); 1 -> Color(0xFF1A237E); else -> Color(0xFFD50000) }
  drawRect(Brush.linearGradient(listOf(r3Mix(c1, fogCol, fog), r3Mix(c2, fogCol, fog)), Offset(x - s * 4f, y - s * 12f), Offset(x + s * 4f, y - s * 7f)), Offset(x - s * 4f, y - s * 12f), Size(s * 8f, s * 4.4f))
  drawRect(Color(0x66FFFFFF), Offset(x - s * 3.6f, y - s * 11.5f), Size(s * 4f, s * 0.5f))
  drawRect(Color(0x44FFFFFF), Offset(x - s * 3.6f, y - s * 10.4f), Size(s * 2.6f, s * 0.35f))
  drawRect(Color(0xFF111111), Offset(x - s * 4.1f, y - s * 12.1f), Size(s * 8.2f, s * 0.2f))
}

// ───────────────────────── Escena completa ─────────────────────────
internal fun DrawScope.drawRace(sim: RaceSim, paint: android.graphics.Paint) {
  val w = size.width; val h = size.height
  val base = (sim.total / SEG_LEN).toInt().coerceAtLeast(0)
  val zi = zoneOf(base)
  val zt = (((base % ZONE_LEN) - (ZONE_LEN - 40)) / 40f).coerceIn(0f, 1f)
  val zs = zt * zt * (3f - 2f * zt)
  val zc = r3LerpZone(R3_ZONES[zi], R3_ZONES[(zi + 1) % R3_ZONES.size], zs)
  val shakeY = if (sim.shake > 0f) sin(sim.clock * 80f) * 3f * sim.shake else 0f
  withTransform({
    translate(0f, shakeY)
    rotate(-sim.steer * 1.4f, Offset(w / 2f, h * 0.85f))
    scale(1.05f, 1.05f, Offset(w / 2f, h * 0.6f))
  }) { r3World(sim, paint, zc, zi, zs, base) }
  // efectos de pantalla
  val pct = (sim.speed / MAX_SPEED).coerceIn(0f, 1.4f)
  if (sim.boostK > 0.05f || pct > 0.8f) {
    val a = (sim.boostK * 0.6f + max(0f, pct - 0.8f)).coerceIn(0f, 0.8f)
    for (i in 0 until 22) {
      val ang = (i * 0.29f + floor(sim.clock * 14f) * 1.7f) % (2f * PI.toFloat())
      val r1 = w * (0.30f + 0.12f * ((i * 7) % 5) / 5f); val r2 = w * (0.62f + 0.12f * ((i * 3) % 4) / 4f)
      drawLine(Color.White.copy(alpha = 0.22f * a), Offset(w / 2f + cos(ang) * r1, h * 0.5f + sin(ang) * r1 * 0.7f), Offset(w / 2f + cos(ang) * r2, h * 0.5f + sin(ang) * r2 * 0.7f), 2.5f)
    }
  }
  for (q in sim.parts) {
    val t = (q.life / q.maxLife).coerceIn(0f, 1f)
    val c = Offset(q.x * w, q.y * h)
    when (q.kind) {
      0 -> drawCircle(Color(0xFFEDEDED).copy(alpha = 0.40f * t), q.size * w * (1.6f - t), c)
      1 -> drawLine(Color(0xFFFFD166).copy(alpha = t), c, Offset(c.x - q.vx * 14f, c.y - q.vy * 14f), 3f, StrokeCap.Round)
      2 -> drawCircle(Color(0xFFC9A66B).copy(alpha = 0.45f * t), q.size * w * (1.6f - t), c)
      else -> drawCircle(Brush.radialGradient(listOf(Color(0xFFFFD166).copy(alpha = t), Color.Transparent), c, q.size * w * 2f), q.size * w * 2f, c)
    }
  }
  drawRect(Brush.radialGradient(listOf(Color.Transparent, Color(0x77000000)), Offset(w / 2f, h / 2f), w * 0.95f))
}

private fun DrawScope.r3World(sim: RaceSim, paint: android.graphics.Paint, zc: R3Zone, zi: Int, zs: Float, base: Int) {
  val w = size.width; val h = size.height
  r3PoolN = 0
  val hz = h * 0.42f
  val n = sim.track.size
  val night = zi == 1 || (zi == 0 && zs > 0.5f)
  // ── cielo ──
  drawRect(Brush.verticalGradient(listOf(zc.skyTop, zc.skyMid, zc.skyHor), 0f, hz), Offset.Zero, Size(w, hz + 2f))
  val sunX = (((w * 0.5f - sim.bg * 0.2f) % w) + w) % w
  val sunC = Offset(sunX, hz - w * 0.05f)
  drawCircle(Brush.radialGradient(listOf(zc.sun.copy(alpha = 0.65f), Color.Transparent), sunC, w * 0.5f), w * 0.5f, sunC)
  drawCircle(Brush.verticalGradient(listOf(zc.sun, r3Mix(zc.sun, zc.skyMid, 0.7f)), sunC.y - w * 0.14f, sunC.y + w * 0.14f), w * 0.12f, sunC)
  if (night) for (i in 0 until 40) {
    val sx = (((r3Hash(i, 1) * w - sim.bg * 0.05f) % w) + w) % w
    drawCircle(Color.White.copy(alpha = 0.35f + 0.4f * abs(sin(sim.clock * 2f + i))), 1.1f, Offset(sx, r3Hash(i, 2) * hz * 0.7f))
  }
  for (i in 0 until 7) {
    val sp = 0.4f + (i % 3) * 0.3f
    val cx = (((r3Hash(i, 3) * w * 1.4f - sim.bg * 0.12f * sp + sim.clock * 4f * sp) % (w * 1.4f)) + w * 1.4f) % (w * 1.4f) - w * 0.2f
    val cy = hz * (0.10f + 0.55f * r3Hash(i, 4)); val cw = w * (0.12f + 0.12f * r3Hash(i, 5))
    drawOval(r3Mix(zc.skyMid, zc.sun, 0.45f).copy(alpha = 0.55f), Offset(cx, cy), Size(cw, cw * 0.18f))
    drawOval(r3Mix(zc.skyHor, Color.White, 0.3f).copy(alpha = 0.35f), Offset(cx + cw * 0.15f, cy - cw * 0.05f), Size(cw * 0.6f, cw * 0.14f))
  }
  for (layer in 0..1) {
    val sc = if (layer == 0) 0.35f else 0.8f
    val col = r3Mix(zc.hill, zc.fog, if (layer == 0) 0.45f else 0.15f)
    val p = Path(); p.moveTo(0f, hz)
    var xx = 0f
    while (xx <= w + 20f) {
      val u = xx + sim.bg * sc * 3f
      p.lineTo(xx, hz - (abs(sin(u * 0.012f + layer * 2f)) * 0.8f + 0.25f * sin(u * 0.05f)) * (if (layer == 0) h * 0.13f else h * 0.08f))
      xx += 14f
    }
    p.lineTo(w, hz); p.close(); drawPath(p, col)
  }
  val cityA = if (zi == 1) 1f - zs else if (zoneOf(base + ZONE_LEN) == 1) zs else 0f
  if (cityA > 0.02f) {
    var xx = 0f
    while (xx < w) {
      val idx = floor((xx + sim.bg * 1.4f) / 26f).toInt()
      val hsh = r3Hash(idx, 9)
      val bh = h * (0.04f + 0.09f * hsh)
      drawRect(Color(0xFF1A0E3A).copy(alpha = cityA), Offset(xx, hz - bh), Size(26f, bh))
      if (hsh > 0.35f) drawRect(Color(0xFFFFD54F).copy(alpha = 0.6f * cityA), Offset(xx + 6f, hz - bh + 8f), Size(4f, 6f))
      if (hsh > 0.7f) drawRect(Color(0xFF00E5FF).copy(alpha = 0.6f * cityA), Offset(xx + 15f, hz - bh + 16f), Size(4f, 6f))
      xx += 26f
    }
  }
  drawRect(zc.gA, Offset(0f, hz), Size(w, h - hz))

  // ── proyección de segmentos ──
  val off = sim.total.coerceAtLeast(0f) % SEG_LEN
  val pctSeg = off / SEG_LEN
  val s0 = sim.track[base % n]; val s1 = sim.track[(base + 1) % n]
  val camY = CAM_H + s0.y + (s1.y - s0.y) * pctSeg
  val camDepth = CAM_DEPTH * (1f - 0.14f * sim.boostK)
  val px = sim.x * ROAD_W
  val ex = FloatArray(DRAW_N + 2); val ey = FloatArray(DRAW_N + 2); val ew = FloatArray(DRAW_N + 2); val edx = FloatArray(DRAW_N + 2)
  var xAcc = 0f; var dxAcc = -s0.curve * pctSeg
  for (k in 0..DRAW_N + 1) {
    val s = sim.track[(base + k) % n]
    val z = k * SEG_LEN - off + CAM_BACK
    val sc = camDepth / z
    ex[k] = w / 2f + sc * (xAcc - px) * w / 2f
    ey[k] = hz - sc * (s.y - camY) * h / 2f
    ew[k] = sc * ROAD_W * w / 2f
    edx[k] = dxAcc
    xAcc += dxAcc; dxAcc += s.curve
  }
  if (ey[0] < h && ey[0] - hz > 1f) {
    val f = (h - hz) / (ey[0] - hz)
    val xh = w / 2f + (ex[0] - w / 2f) * f; val wh = ew[0] * f
    latQuad(ex[0], ey[0], ew[0], xh, h, wh, -1.12f, 1.12f, Color(0xFFE53935))
    latQuad(ex[0], ey[0], ew[0], xh, h, wh, -1f, 1f, Color(0xFF4B4B53))
  }
  fun m(k: Int, meters: Float) = ew[k] * meters / 6f
  val fogBase = zc.fog

  // ── segmentos de lejos a cerca ──
  for (k in DRAW_N - 1 downTo 0) {
    val a = base + k
    val zone = zoneOf(a)
    val z = R3_ZONES[zone]
    val alt = (a / 3) % 2 == 0
    val fog = (k.toFloat() / DRAW_N).let { it * it } * 0.85f
    val y1 = ey[k]; val y2 = ey[k + 1]
    if (y1 <= y2) continue
    val x1 = ex[k]; val x2 = ex[k + 1]; val w1 = ew[k]; val w2 = ew[k + 1]
    drawRect(r3Mix(if (alt) z.gA else z.gB, fogBase, fog), Offset(0f, y2), Size(w, y1 - y2 + 1f))
    if (zone == 2) {
      latQuad(x1, y1, w1, x2, y2, w2, -1.9f, -1.12f, r3Mix(Color(0xFFD9B070), fogBase, fog))
      latQuad(x1, y1, w1, x2, y2, w2, 1.12f, 1.9f, r3Mix(Color(0xFFD9B070), fogBase, fog))
    }
    if (zone == 3) {
      val sx = max(0f, x1 + w1 * 1.5f)
      val sea = r3Mix(r3Mix(Color(0xFF1D6FA5), Color(0xFF0B3A6B), min(1f, k / 60f)), fogBase, fog)
      if (sx < w) drawRect(sea, Offset(sx, y2), Size(w - sx, y1 - y2 + 1f))
      if (sx < w && (a * 7) % 5 == 0) drawRect(Color(0xFFFFF0C0).copy(alpha = 0.35f * (1f - fog)), Offset(w * 0.5f + (r3Hash(a, 3) - 0.5f) * w * 0.5f, y2), Size(w * 0.12f, max(1f, (y1 - y2) * 0.5f)))
    }
    latQuad(x1, y1, w1, x2, y2, w2, -1.12f, -1f, r3Mix(if ((a / 2) % 2 == 0) Color(0xFFE53935) else Color.White, fogBase, fog))
    latQuad(x1, y1, w1, x2, y2, w2, 1f, 1.12f, r3Mix(if ((a / 2) % 2 == 0) Color(0xFFE53935) else Color.White, fogBase, fog))
    latQuad(x1, y1, w1, x2, y2, w2, -1f, 1f, r3Mix(if (alt) Color(0xFF4B4B53) else Color(0xFF52525A), fogBase, fog))
    val track = r3Mix(Color(0xFF3C3C43), fogBase, fog)
    latQuad(x1, y1, w1, x2, y2, w2, -0.62f, -0.40f, track); latQuad(x1, y1, w1, x2, y2, w2, 0.40f, 0.62f, track)
    val line = r3Mix(Color.White, fogBase, fog)
    latQuad(x1, y1, w1, x2, y2, w2, -0.95f, -0.92f, line); latQuad(x1, y1, w1, x2, y2, w2, 0.92f, 0.95f, line)
    if (alt) { latQuad(x1, y1, w1, x2, y2, w2, -0.34f, -0.31f, line); latQuad(x1, y1, w1, x2, y2, w2, 0.31f, 0.34f, line) }
    if (a % n == 0) {
      latQuad(x1, y1, w1, x2, y2, w2, -1f, 1f, r3Mix(Color.White, fogBase, fog))
      for (q in -4..3 step 2) latQuad(x1, y1, w1, x2, y2, w2, (q) / 4.5f, (q + 1) / 4.5f, r3Mix(Color(0xFF111111), fogBase, fog))
    }
    // guardarraíles con postes
    for (side in listOf(-1f, 1f)) {
      if (zone == 3 && side > 0f) continue
      val rx1 = x1 + side * 1.34f * w1; val rx2 = x2 + side * 1.34f * w2
      r3Poly4(r3Mix(if (zone == 1) Color(0xFF8A8A94) else Color(0xFFB7BCC4), fogBase, fog), rx1, y1 - m(k, 0.95f), rx2, y2 - m(k + 1, 0.95f), rx2, y2 - m(k + 1, 0.55f), rx1, y1 - m(k, 0.55f))
      r3Poly4(r3Mix(Color(0xFF3A3A42), fogBase, fog), rx1, y1 - m(k, 0.55f), rx2, y2 - m(k + 1, 0.55f), rx2, y2, rx1, y1)
      if (a % 2 == 0) drawRect(r3Mix(Color(0xFF55555E), fogBase, fog), Offset(rx1 - m(k, 0.08f), y1 - m(k, 0.95f)), Size(m(k, 0.16f) + 1f, m(k, 0.95f)))
    }
    // pórtico sobre la pista
    if (k > 2 && (a % 150 == 75 || a % n == 0)) {
      val hb = m(k, 7.6f); val bw = max(2f, m(k, 0.5f))
      val pc = r3Mix(Color(0xFF546E7A), fogBase, fog)
      drawRect(pc, Offset(x1 - w1 * 1.38f - bw / 2f, y1 - hb), Size(bw, hb)); drawRect(pc, Offset(x1 + w1 * 1.38f - bw / 2f, y1 - hb), Size(bw, hb))
      drawRect(pc, Offset(x1 - w1 * 1.4f, y1 - hb - m(k, 1.2f)), Size(w1 * 2.8f, m(k, 1.2f)))
      if (a % n == 0) {
        val cw = w1 * 2.8f / 14f
        for (c in 0 until 14) drawRect(if (c % 2 == 0) Color.White else Color(0xFF111111), Offset(x1 - w1 * 1.4f + c * cw, y1 - hb - m(k, 1.2f)), Size(cw, m(k, 1.2f)))
      } else {
        drawRect(Color(0xFF00695C), Offset(x1 - w1 * 0.7f, y1 - hb + m(k, 0.2f)), Size(w1 * 1.4f, m(k, 1.8f)))
        drawRect(Color(0xCCFFFFFF), Offset(x1 - w1 * 0.55f, y1 - hb + m(k, 0.6f)), Size(w1 * 1.1f, m(k, 0.4f)))
        drawCircle(Color(0xFFFFF3C4).copy(alpha = 0.5f * (1f - fog)), m(k, 1.4f), Offset(x1, y1 - hb - m(k, 1.4f)))
      }
    }
    // ambiente lateral según la zona
    if (k > 1) for (side in listOf(-1f, 1f)) {
      val hs = r3Hash(a, if (side > 0f) 11 else 12)
      val lat = side * (1.75f + r3Hash(a, if (side > 0f) 13 else 14) * 2.6f)
      val ox = x1 + lat * w1; val sc6 = w1 / 6f
      when (zone) {
        0 -> {
          if (a % 2 == 0 && hs > 0.2f) r3Pine(ox, y1, sc6, 0.8f + hs * 0.5f, fogBase, fog)
          else if (hs < 0.06f) r3Rock(ox, y1, sc6, fogBase, fog)
          if (a % 41 == 7 && side > 0f) r3Billboard(x1 + 2.1f * w1, y1, sc6, a, fogBase, fog)
        }
        1 -> {
          if ((a + (if (side > 0f) 2 else 0)) % 5 == 0 && k in 6 until DRAW_N - 5) {
            val d = 4
            val bwm = 12f + 8f * hs; val hm = 22f + 38f * r3Hash(a, 15)
            val latN = side * (2.0f + 0.6f * hs)
            val inner = latN - side * (bwm / 12f); val outer = latN + side * (bwm / 12f)
            val fx = x1 + inner * w1; val bx = ex[k + d] + inner * ew[k + d]
            val fy = y1; val by2 = ey[k + d]
            val hF = m(k, hm); val hB = m(k + d, hm)
            val sideCol = r3Mix(Color(0xFF241A45), fogBase, fog)
            val frontCol = r3Mix(Color(0xFF362866), fogBase, fog)
            r3Poly4(sideCol, fx, fy, bx, by2, bx, by2 - hB, fx, fy - hF)
            val ox2 = x1 + outer * w1
            r3Poly4(frontCol, fx, fy, ox2, fy, ox2, fy - hF, fx, fy - hF)
            val rows = min(8, (hm / 5f).toInt())
            for (r in 0 until rows) for (c in 0 until 3) {
              if (r3Hash(a * 13 + r, c + (if (side > 0f) 5 else 9)) < 0.45f) continue
              val col = if (r3Hash(a + r, c) > 0.8f) Color(0xFF37E6FF) else Color(0xFFFFD166)
              val fr0 = (r + 0.2f) / rows; val fr1 = (r + 0.6f) / rows
              val c0 = c / 3f + 0.05f; val c1 = c / 3f + 0.25f
              val ax = fx + (bx - fx) * c0; val bxx = fx + (bx - fx) * c1
              val ay = fy + (by2 - fy) * c0; val byy = fy + (by2 - fy) * c1
              val hA = hF + (hB - hF) * c0; val hBn = hF + (hB - hF) * c1
              r3Poly4(col.copy(alpha = 0.7f * (1f - fog)), ax, ay - hA * fr1, bxx, byy - hBn * fr1, bxx, byy - hBn * fr0, ax, ay - hA * fr0)
            }
          }
        }
        2 -> {
          if (a % 4 == 0 && hs > 0.35f) { if (hs > 0.7f) r3Cactus(ox, y1, sc6, fogBase, fog) else r3Rock(ox, y1, sc6, fogBase, fog) }
          if (a % 6 == 3) { val pc2 = r3Mix(Color(0xFF5D4037), fogBase, fog); drawRect(pc2, Offset(x1 + side * 1.5f * w1 - sc6 * 0.12f, y1 - sc6 * 9f), Size(sc6 * 0.24f, sc6 * 9f)); drawRect(pc2, Offset(x1 + side * 1.5f * w1 - sc6 * 1.2f, y1 - sc6 * 8.6f), Size(sc6 * 2.4f, sc6 * 0.22f)) }
        }
        else -> {
          if (side < 0f && a % 3 == 0) r3Palm(ox, y1, sc6, 0.5f - hs, fogBase, fog)
        }
      }
    }
    if (k > 1 && (zone == 1 && a % 3 == 0 || zone == 3 && a % 4 == 0 || zone == 0 && a % 9 == 0)) {
      r3Lamp(x1 - 1.5f * w1, y1, w1 / 6f, 1f, fogBase, fog, true)
      if (zone != 3) r3Lamp(x1 + 1.5f * w1, y1, w1 / 6f, -1f, fogBase, fog, true)
    }
    // obstáculos y pads de turbo
    ROBS_BY_SEG[a % n]?.forEachIndexed { j, o ->
      if (k < 1 || sim.hits.contains(a.toLong() * 8 + j)) return@forEachIndexed
      val ox = x1 + o.lane * w1; val sz = w1 * 0.14f
      when (o.type) {
        0 -> {
          drawOval(Color(0x55000000), Offset(ox - sz * 0.8f, y1 - sz * 0.14f), Size(sz * 1.6f, sz * 0.28f))
          val cp = Path(); cp.moveTo(ox - sz * 0.5f, y1); cp.lineTo(ox + sz * 0.5f, y1); cp.lineTo(ox + sz * 0.12f, y1 - sz * 1.5f); cp.lineTo(ox - sz * 0.12f, y1 - sz * 1.5f); cp.close()
          drawPath(cp, r3Mix(Color(0xFFFF6D00), fogBase, fog * 0.7f))
          val cr = Path(); cr.moveTo(ox + sz * 0.1f, y1); cr.lineTo(ox + sz * 0.5f, y1); cr.lineTo(ox + sz * 0.12f, y1 - sz * 1.5f); cr.lineTo(ox + sz * 0.05f, y1 - sz * 1.5f); cr.close()
          drawPath(cr, Color(0x33000000))
          r3Poly4(r3Mix(Color.White, fogBase, fog * 0.7f), ox - sz * 0.36f, y1 - sz * 0.55f, ox + sz * 0.36f, y1 - sz * 0.55f, ox + sz * 0.27f, y1 - sz * 0.8f, ox - sz * 0.27f, y1 - sz * 0.8f)
          drawRect(r3Mix(Color(0xFF263238), fogBase, fog * 0.7f), Offset(ox - sz * 0.62f, y1 - sz * 0.1f), Size(sz * 1.24f, sz * 0.12f))
        }
        1 -> {
          val bw = sz * 2.3f
          drawOval(Color(0x55000000), Offset(ox - bw, y1 - sz * 0.15f), Size(bw * 2f, sz * 0.3f))
          drawRect(Color(0xFF263238), Offset(ox - bw * 0.85f, y1 - sz * 0.9f), Size(sz * 0.18f, sz * 0.9f))
          drawRect(Color(0xFF263238), Offset(ox + bw * 0.85f - sz * 0.18f, y1 - sz * 0.9f), Size(sz * 0.18f, sz * 0.9f))
          drawRect(r3Mix(Color.White, fogBase, fog * 0.7f), Offset(ox - bw, y1 - sz * 1.45f), Size(bw * 2f, sz * 0.62f))
          for (q in 0 until 6 step 2) drawRect(r3Mix(Color(0xFFE53935), fogBase, fog * 0.7f), Offset(ox - bw + q * bw / 3f, y1 - sz * 1.45f), Size(bw / 3f, sz * 0.62f))
          drawCircle(Color(0xFFFFC107).copy(alpha = 0.6f + 0.4f * sin(sim.clock * 10f)), sz * 0.14f, Offset(ox, y1 - sz * 1.6f))
        }
        else -> {
          val pw = w1 * 0.2f
          latQuad(ox, y1, pw, ox, y2, w2 * 0.2f, -1f, 1f, r3Mix(Color(0xFF00E5FF), fogBase, fog * 0.6f))
          latQuad(ox, y1, pw, ox, y2, w2 * 0.2f, -0.55f, 0.55f, r3Mix(Color(0xFFB2FF59), fogBase, fog * 0.6f))
          drawCircle(Color(0xFF00E5FF).copy(alpha = 0.25f * (1f - fog)), pw * 1.3f, Offset(ox, y1 - pw * 0.2f))
        }
      }
    }
  }

  // ── rivales (bots y humanos), de lejos a cerca ──
  val tl = sim.trackLen
  val rivals = sim.cars.map { b ->
    var dz = (b.pos() % tl) - (sim.total % tl); if (dz < 0f) dz += tl
    Pair(b, dz)
  }.filter { it.second < DRAW_N * SEG_LEN - SEG_LEN * 2 }.sortedByDescending { it.second }
  for ((b, dz) in rivals) {
    val kf = (dz + off) / SEG_LEN
    val k = floor(kf).toInt().coerceIn(0, DRAW_N - 1); val f = kf - k
    val sx = ex[k] + (ex[k + 1] - ex[k]) * f; val sy = ey[k] + (ey[k + 1] - ey[k]) * f
    val sw = ew[k] + (ew[k + 1] - ew[k]) * f
    if (sw < 3f) continue
    val cx = sx + b.lane() * sw
    val roadYaw = atan(edx[k] / SEG_LEN).coerceIn(-0.9f, 0.9f)
    val laneYaw = if (b.isBot) atan(0.35f * 0.45f * cos(0.45f * sim.time + b.ph2) * 6f / max(1f, b.speed / 333f)) else 0f
    val fogK = (k.toFloat() / DRAW_N).let { it * it } * 0.85f
    r3Car(sw > 60f, cx, sy, sw / 6f, roadYaw + laneYaw, b.color, 11f, 2.4f, 0.20f, false, 0f, zc.skyMid, fogK * 0.8f, zc.fog)
    if (!b.isBot && sw > 24f) {
      paint.textSize = (sw * 0.13f).coerceIn(14f, 34f)
      drawIntoCanvas { it.nativeCanvas.drawText(b.name, cx, sy - sw * 0.33f - 8f, paint) }
    }
  }
  // ── mi auto ──
  val boosting = sim.boosting()
  val flame = if (boosting) 0.6f + 0.4f * sin(sim.clock * 50f) else 0f
  val ppm = w * 0.158f
  r3Car(true, w / 2f, h * 0.905f, ppm, sim.steer * 0.26f + (if (sim.shake > 0f) sin(sim.clock * 60f) * 0.03f * sim.shake else 0f), sim.myColor, 5.0f, 1.5f, 0.18f, sim.brake, flame, zc.skyMid, 0f, zc.fog)
}
