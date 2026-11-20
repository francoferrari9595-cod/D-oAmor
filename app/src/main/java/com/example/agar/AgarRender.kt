package com.example.agar

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Paleta de las células y de la comida (colores vivos como en el juego original). */
internal val A_COLORS = listOf(
  Color(0xFFFF3B30), Color(0xFFFF9500), Color(0xFFFFCC00), Color(0xFF34C759), Color(0xFF00C7BE), Color(0xFF32ADE6),
  Color(0xFF007AFF), Color(0xFF5856D6), Color(0xFFAF52DE), Color(0xFFFF2D92), Color(0xFFA2845E), Color(0xFF8E8E93)
)

internal fun lighten(c: Color, t: Float) = Color(c.red + (1f - c.red) * t, c.green + (1f - c.green) * t, c.blue + (1f - c.blue) * t, 1f)
internal fun darken(c: Color, k: Float) = Color(c.red * k, c.green * k, c.blue * k, 1f)

internal class AgarCam { var x = 3500f; var y = 3500f; var scale = 1f; var animT = 0f; var shx = 0f; var shy = 0f }

internal class AgarPaints {
  val fill = Paint().apply { isAntiAlias = true; textAlign = Paint.Align.CENTER; color = android.graphics.Color.WHITE; typeface = Typeface.DEFAULT_BOLD }
  val stroke = Paint().apply { isAntiAlias = true; textAlign = Paint.Align.CENTER; style = Paint.Style.STROKE; color = android.graphics.Color.BLACK; typeface = Typeface.DEFAULT_BOLD }
  val body = Path()
  val vpath = Path()
  val list = ArrayList<Pair<PCell, AgarPlayer>>()
}

private fun wobblePath(path: Path, cx: Float, cy: Float, rr: Float, id: Int, t: Float, amp: Float) {
  path.rewind()
  val n = if (rr > 140f) 60 else 40
  for (i in 0..n) {
    val a = i * 2f * PI.toFloat() / n
    val wob = 1f + amp * (sin(a * 5f + t * 2.6f + id) * 0.6f + sin(a * 9f - t * 3.4f + id * 0.7f) * 0.4f)
    val x = cx + cos(a) * rr * wob; val y = cy + sin(a) * rr * wob
    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
  }
  path.close()
}

private fun DrawScope.text(pt: AgarPaints, s: String, x: Float, y: Float, size: Float) {
  drawIntoCanvas { cv ->
    pt.fill.textSize = size; pt.stroke.textSize = size; pt.stroke.strokeWidth = max(2f, size * 0.16f)
    cv.nativeCanvas.drawText(s, x, y, pt.stroke); cv.nativeCanvas.drawText(s, x, y, pt.fill)
  }
}

/** Dibuja toda la arena: fondo con profundidad, comida brillante, células gelatinosas con relieve, virus, partículas y viñeta. */
internal fun DrawScope.drawArena(world: AgarWorld, cam: AgarCam, fx: FxState, dark: Boolean, grid: Boolean, showMass: Boolean, effects: Boolean, pt: AgarPaints, myId: Int) {
  val w = size.width; val h = size.height; val s = cam.scale; val t = cam.animT
  val outside = if (dark) Color(0xFF020204) else Color(0xFFCFD8DC)
  drawRect(outside)
  translate(cam.shx, cam.shy) {
    fun sx(x: Float) = (x - cam.x) * s + w / 2f
    fun sy(y: Float) = (y - cam.y) * s + h / 2f
    val left = cam.x - w / 2f / s; val right = cam.x + w / 2f / s; val top = cam.y - h / 2f / s; val bottom = cam.y + h / 2f / s
    val ax0 = sx(0f); val ay0 = sy(0f); val ax1 = sx(world.size); val ay1 = sy(world.size)
    val vx0 = max(ax0, -40f); val vy0 = max(ay0, -40f); val vx1 = min(ax1, w + 40f); val vy1 = min(ay1, h + 40f)
    val bgA = if (dark) Color(0xFF191B2E) else Color(0xFFFAFEFF); val bgB = if (dark) Color(0xFF07070E) else Color(0xFFE6F3F8)
    if (vx1 > vx0 && vy1 > vy0) drawRect(Brush.radialGradient(listOf(bgA, bgB), Offset(w / 2f, h / 2f), max(w, h) * 0.8f), Offset(vx0, vy0), Size(vx1 - vx0, vy1 - vy0))
    // esporas flotantes con parallax (profundidad)
    if (effects) for (i in 0 until 34) {
      val par = 0.12f + (i % 4) * 0.07f
      val bx = ((i * 7919) % 1000) / 1000f; val by = ((i * 104729) % 997) / 997f
      val x = (((bx * w - cam.x * s * par + t * (4f + i % 5 * 2f)) % w) + w) % w
      val y = (((by * h - cam.y * s * par - t * (3f + i % 3 * 2f)) % h) + h) % h
      val r = (3f + (i % 5) * 3.2f) * (0.7f + 0.3f * sin(t * 0.8f + i))
      drawCircle((if (dark) Color(0x1A9FB8FF) else Color(0x2290CAF9)), r, Offset(x, y))
    }
    if (grid && vx1 > vx0) {
      val gs = if (50f * s < 14f) 200f else 50f
      val gc = if (dark) Color(0xFF22243A) else Color(0xFFD5E4EA)
      var gx = floor(max(left, 0f) / gs) * gs
      while (gx <= min(right, world.size)) { val px = sx(gx); drawLine(gc, Offset(px, max(ay0, 0f)), Offset(px, min(ay1, h)), 1f); gx += gs }
      var gy = floor(max(top, 0f) / gs) * gs
      while (gy <= min(bottom, world.size)) { val py = sy(gy); drawLine(gc, Offset(max(ax0, 0f), py), Offset(min(ax1, w), py), 1f); gy += gs }
    }
    // borde de la arena con resplandor de neón
    if (effects) {
      val pulse = 0.75f + 0.25f * sin(t * 2.2f)
      drawRect(Color(0xFFFF2D92).copy(alpha = 0.10f * pulse), Offset(ax0, ay0), Size(ax1 - ax0, ay1 - ay0), style = Stroke(26f * s + 6f))
      drawRect(Color(0xFFFF2D92).copy(alpha = 0.22f * pulse), Offset(ax0, ay0), Size(ax1 - ax0, ay1 - ay0), style = Stroke(12f * s + 3f))
    }
    drawRect(if (dark) Color(0xFFFF4DA6) else Color(0xFFE91E63), Offset(ax0, ay0), Size(ax1 - ax0, ay1 - ay0), style = Stroke(max(3f, 4f * s)))

    // comida: orbes brillantes que laten
    val pr = A_PELLET_SIZE * s
    world.forPellets(left - 20f, top - 20f, right + 20f, bottom + 20f) { sl ->
      val col = A_COLORS[world.pcol[sl] % A_COLORS.size]
      val c = Offset(sx(world.px[sl]), sy(world.py[sl]))
      val pp = pr * (1f + 0.09f * sin(t * 3f + sl))
      if (effects) drawCircle(col.copy(alpha = if (dark) 0.28f else 0.16f), pp * 2.1f, c)
      drawCircle(col, pp, c)
      if (effects) drawCircle(Color.White.copy(alpha = 0.55f), pp * 0.32f, Offset(c.x - pp * 0.3f, c.y - pp * 0.3f))
    }
    // masa expulsada: gotas gelatinosas
    for (e in world.ejects) {
      if (e.x < left || e.x > right || e.y < top || e.y > bottom) continue
      val col = A_COLORS[e.color % A_COLORS.size]; val c = Offset(sx(e.x), sy(e.y)); val rr = 18f * s
      drawCircle(Brush.radialGradient(0f to lighten(col, 0.6f), 0.5f to col, 1f to darken(col, 0.7f), center = Offset(c.x - rr * 0.3f, c.y - rr * 0.3f), radius = rr * 1.4f), rr, c)
      drawCircle(darken(col, 0.7f), rr, c, style = Stroke(max(1.5f, rr * 0.1f)))
      drawCircle(Color.White.copy(alpha = 0.6f), rr * 0.18f, Offset(c.x - rr * 0.35f, c.y - rr * 0.4f))
    }

    // células ordenadas de pequeña a grande (las grandes tapan a las pequeñas)
    val list = pt.list
    list.clear()
    for (p in world.players) if (p.alive) for (c in p.cells) {
      val r = c.size
      if (c.x + r < left || c.x - r > right || c.y + r < top || c.y - r > bottom) continue
      list.add(c to p)
    }
    list.sortBy { it.first.mass }
    val leaderId = world.leaderboard(1).firstOrNull()?.id ?: -1
    var vDrawn = false
    fun drawViruses() {
      for (v in world.viruses) {
        val r = v.size
        if (v.x + r < left || v.x - r > right || v.y + r < top || v.y - r > bottom) continue
        val ctr = Offset(sx(v.x), sy(v.y)); val rr = r * s
        pt.vpath.rewind()
        val n = 46; val rot = t * 0.35f + v.id
        for (i in 0 until n * 2) {
          val a = i * PI.toFloat() / n + rot
          val q = if (i % 2 == 0) rr else rr * 0.9f
          val x = ctr.x + cos(a) * q; val y = ctr.y + sin(a) * q
          if (i == 0) pt.vpath.moveTo(x, y) else pt.vpath.lineTo(x, y)
        }
        pt.vpath.close()
        if (effects) drawCircle(Color(0xFF33FF33).copy(alpha = 0.18f), rr * 1.35f, ctr)
        drawPath(pt.vpath, Brush.radialGradient(0f to Color(0xFFB9FF9E), 0.55f to Color(0xFF33FF33), 1f to Color(0xFF16A31B), center = Offset(ctr.x - rr * 0.3f, ctr.y - rr * 0.3f), radius = rr * 1.4f))
        drawPath(pt.vpath, Color(0xFF138F17), style = Stroke(max(2f, rr * 0.05f)))
        drawCircle(Color.White.copy(alpha = 0.22f), rr * 0.62f, ctr, style = Stroke(max(1.5f, rr * 0.03f)))
        drawCircle(Color.White.copy(alpha = 0.35f), rr * 0.12f, Offset(ctr.x - rr * 0.32f, ctr.y - rr * 0.36f))
      }
    }
    for ((c, p) in list) {
      if (!vDrawn && c.mass > A_VIRUS_MASS) { drawViruses(); vDrawn = true }
      val col = A_COLORS[p.color % A_COLORS.size]
      val ctr = Offset(sx(c.x), sy(c.y)); val rr = c.size * s
      // estela de las células lanzadas (división / explosión)
      if (effects && c.boost > 80f) for (k in 1..3) {
        val gc = Offset(ctr.x - c.bx * rr * 0.5f * k, ctr.y - c.by * rr * 0.5f * k)
        drawCircle(col.copy(alpha = 0.2f - k * 0.045f), rr * (1f - 0.07f * k), gc)
      }
      // aura de tu propia célula
      if (effects && p.id == myId) drawCircle(Brush.radialGradient(listOf(col.copy(alpha = 0.38f), Color.Transparent), ctr, rr * 1.55f), rr * 1.55f, ctr)
      // sombra
      drawCircle(Color.Black.copy(alpha = if (dark) 0.40f else 0.15f), rr * 1.03f, Offset(ctr.x + rr * 0.05f, ctr.y + rr * 0.09f))
      val organic = effects && rr > 22f
      val brush = Brush.radialGradient(0f to lighten(col, 0.55f), 0.45f to col, 1f to darken(col, 0.62f), center = Offset(ctr.x - rr * 0.32f, ctr.y - rr * 0.36f), radius = rr * 1.45f)
      if (organic) {
        wobblePath(pt.body, ctr.x, ctr.y, rr, c.id, t, if (c.boost > 80f) 0.04f else 0.016f)
        drawPath(pt.body, brush)
        drawPath(pt.body, darken(col, 0.68f), style = Stroke(max(2f, rr * 0.055f)))
      } else {
        drawCircle(brush, rr, ctr)
        drawCircle(darken(col, 0.68f), rr, ctr, style = Stroke(max(2f, rr * 0.055f)))
      }
      if (effects && rr > 12f) {
        drawCircle(Color.White.copy(alpha = 0.16f), rr * 0.9f, ctr, style = Stroke(rr * 0.035f))
        if (rr > 38f) {
          val nc = Offset(ctr.x + cos(t * 0.5f + c.id) * rr * 0.13f, ctr.y + sin(t * 0.4f + c.id * 1.3f) * rr * 0.13f)
          drawCircle(Brush.radialGradient(listOf(darken(col, 0.5f).copy(alpha = 0.55f), darken(col, 0.5f).copy(alpha = 0f)), nc, rr * 0.3f), rr * 0.3f, nc)
          for (i in 0 until 6) {
            val a = c.id * 1.7f + i * 1.05f + t * 0.15f; val d = rr * (0.35f + 0.3f * ((i * 37) % 10) / 10f)
            drawCircle(lighten(col, 0.6f).copy(alpha = 0.35f), rr * 0.045f, Offset(ctr.x + cos(a) * d, ctr.y + sin(a) * d))
          }
        }
        drawOval(Color.White.copy(alpha = 0.30f), Offset(ctr.x - rr * 0.62f, ctr.y - rr * 0.72f), Size(rr * 0.64f, rr * 0.34f))
        drawCircle(Color.White.copy(alpha = 0.55f), rr * 0.05f, Offset(ctr.x - rr * 0.4f, ctr.y - rr * 0.52f))
      }
      if (rr > 14f) {
        val ts = max(12f, rr * 0.42f)
        val ny = ctr.y + ts * 0.3f - (if (showMass) ts * 0.2f else 0f)
        text(pt, p.name, ctr.x, ny, ts)
        if (showMass && rr > 26f) text(pt, c.mass.roundToInt().toString(), ctr.x, ctr.y + ts * 0.85f, ts * 0.7f)
        if (effects && p.id == leaderId && c === p.cells.maxByOrNull { it.mass }) text(pt, "👑", ctr.x, ctr.y - rr - 4f, max(16f, rr * 0.55f))
      }
    }
    if (!vDrawn) drawViruses()

    // efectos: ondas, partículas y textos flotantes
    if (effects) {
      for (r in fx.rings) {
        val k = 1f - r.life / r.max
        drawCircle(r.color.copy(alpha = (1f - k) * 0.9f), r.maxR * k * s, Offset(sx(r.x), sy(r.y)), style = Stroke(r.width * (1f - k) * s + 1.5f))
      }
      for (q in fx.parts) {
        val a = (q.life / q.max).coerceIn(0f, 1f)
        val c = Offset(sx(q.x), sy(q.y))
        drawCircle(q.color.copy(alpha = a * 0.35f), q.size * s * 2.4f * a + 1f, c)
        drawCircle(q.color.copy(alpha = a), q.size * s * a + 1f, c)
      }
      for (tx in fx.texts) {
        val a = (tx.life / 1.1f).coerceIn(0f, 1f)
        drawIntoCanvas { cv ->
          pt.fill.alpha = (255 * a).toInt(); pt.stroke.alpha = (255 * a).toInt()
          pt.fill.textSize = 22f; pt.stroke.textSize = 22f; pt.stroke.strokeWidth = 4f
          cv.nativeCanvas.drawText(tx.text, sx(tx.x), sy(tx.y), pt.stroke); cv.nativeCanvas.drawText(tx.text, sx(tx.x), sy(tx.y), pt.fill)
          pt.fill.alpha = 255; pt.stroke.alpha = 255
        }
      }
    }
  }
  // viñeta, peligro cercano y destello (por encima de todo, sin sacudida)
  if (effects) {
    drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = if (dark) 0.5f else 0.2f)), Offset(w / 2f, h / 2f), max(w, h) * 0.78f))
    if (fx.danger > 0.02f) drawRect(Brush.radialGradient(listOf(Color.Transparent, Color(0xFFFF1744).copy(alpha = 0.40f * fx.danger)), Offset(w / 2f, h / 2f), max(w, h) * 0.72f))
    if (fx.flash > 0.01f) drawRect(fx.flashColor.copy(alpha = 0.35f * fx.flash))
  }
}
