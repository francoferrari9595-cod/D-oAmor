package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay
import com.example.R

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.GradientButton
import com.example.ui.components.SoftButton
import com.example.ui.theme.GoldYellow
import kotlinx.coroutines.launch

// =====================================================================
// AJEDREZ (2 jugadores, sincronizado en vivo por la sala)
// Tablero: 64 caracteres, índice 0 = a8 (arriba-izquierda). Mayúscula = blancas, minúscula = negras.
// =====================================================================

private val CH_START = "rnbqkbnr" + "pppppppp" + ".".repeat(32) + "PPPPPPPP" + "RNBQKBNR"

private class ChMove(val from: Int, val to: Int, val promo: Char = ' ')

private class ChPos(val b: CharArray, val white: Boolean, val castle: String, val ep: Int, val half: Int = 0)

private val CH_ORTHO = listOf(Pair(-1, 0), Pair(1, 0), Pair(0, -1), Pair(0, 1))
private val CH_DIAG = listOf(Pair(-1, -1), Pair(-1, 1), Pair(1, -1), Pair(1, 1))
private val CH_KNIGHT = listOf(
  Pair(-2, -1), Pair(-2, 1), Pair(-1, -2), Pair(-1, 2), Pair(1, -2), Pair(1, 2), Pair(2, -1), Pair(2, 1)
)

private fun chIsWhite(c: Char): Boolean = c in 'A'..'Z'

/** 0 = fuera/propia, 1 = vacía, 2 = captura. */
private fun chTarget(b: CharArray, w: Boolean, nr: Int, nf: Int): Int {
  if (nr !in 0..7 || nf !in 0..7) return 0
  val t = b[nr * 8 + nf]
  if (t == '.') return 1
  return if (chIsWhite(t) != w) 2 else 0
}

private fun chAttacked(b: CharArray, sq: Int, byWhite: Boolean): Boolean {
  val r = sq / 8
  val f = sq % 8
  val pawn = if (byWhite) 'P' else 'p'
  val pr = if (byWhite) r + 1 else r - 1
  if (pr in 0..7) {
    if (f - 1 >= 0 && b[pr * 8 + f - 1] == pawn) return true
    if (f + 1 <= 7 && b[pr * 8 + f + 1] == pawn) return true
  }
  val knight = if (byWhite) 'N' else 'n'
  for ((dr, df) in CH_KNIGHT) {
    val nr = r + dr
    val nf = f + df
    if (nr in 0..7 && nf in 0..7 && b[nr * 8 + nf] == knight) return true
  }
  val king = if (byWhite) 'K' else 'k'
  for (dr in -1..1) {
    for (df in -1..1) {
      if (dr == 0 && df == 0) continue
      val nr = r + dr
      val nf = f + df
      if (nr in 0..7 && nf in 0..7 && b[nr * 8 + nf] == king) return true
    }
  }
  val rook = if (byWhite) 'R' else 'r'
  val bishop = if (byWhite) 'B' else 'b'
  val queen = if (byWhite) 'Q' else 'q'
  for ((dr, df) in CH_ORTHO) {
    var nr = r + dr
    var nf = f + df
    while (nr in 0..7 && nf in 0..7) {
      val p = b[nr * 8 + nf]
      if (p != '.') {
        if (p == rook || p == queen) return true
        break
      }
      nr += dr
      nf += df
    }
  }
  for ((dr, df) in CH_DIAG) {
    var nr = r + dr
    var nf = f + df
    while (nr in 0..7 && nf in 0..7) {
      val p = b[nr * 8 + nf]
      if (p != '.') {
        if (p == bishop || p == queen) return true
        break
      }
      nr += dr
      nf += df
    }
  }
  return false
}

private fun chAddPawn(out: MutableList<ChMove>, from: Int, to: Int, lastRow: Int) {
  if (to / 8 == lastRow) {
    for (pr in "QRBN") out.add(ChMove(from, to, pr))
  } else out.add(ChMove(from, to))
}

private fun chPseudo(p: ChPos): List<ChMove> {
  val out = ArrayList<ChMove>()
  val b = p.b
  val w = p.white
  for (sq in 0 until 64) {
    val pc = b[sq]
    if (pc == '.' || chIsWhite(pc) != w) continue
    val r = sq / 8
    val f = sq % 8
    when (pc.lowercaseChar()) {
      'p' -> {
        val dir = if (w) -1 else 1
        val startRow = if (w) 6 else 1
        val lastRow = if (w) 0 else 7
        val nr = r + dir
        if (nr in 0..7) {
          if (b[nr * 8 + f] == '.') {
            chAddPawn(out, sq, nr * 8 + f, lastRow)
            if (r == startRow && b[(r + 2 * dir) * 8 + f] == '.') out.add(ChMove(sq, (r + 2 * dir) * 8 + f))
          }
          for (df in intArrayOf(-1, 1)) {
            val nf = f + df
            if (nf !in 0..7) continue
            val t = nr * 8 + nf
            val tp = b[t]
            if ((tp != '.' && chIsWhite(tp) != w) || (tp == '.' && t == p.ep)) chAddPawn(out, sq, t, lastRow)
          }
        }
      }
      'n' -> {
        for ((dr, df) in CH_KNIGHT) {
          if (chTarget(b, w, r + dr, f + df) != 0) out.add(ChMove(sq, (r + dr) * 8 + f + df))
        }
      }
      'b', 'r', 'q' -> {
        val lc = pc.lowercaseChar()
        val dirs = when (lc) {
          'b' -> CH_DIAG
          'r' -> CH_ORTHO
          else -> CH_DIAG + CH_ORTHO
        }
        for ((dr, df) in dirs) {
          var nr = r + dr
          var nf = f + df
          while (true) {
            val k = chTarget(b, w, nr, nf)
            if (k == 0) break
            out.add(ChMove(sq, nr * 8 + nf))
            if (k == 2) break
            nr += dr
            nf += df
          }
        }
      }
      'k' -> {
        for (dr in -1..1) {
          for (df in -1..1) {
            if (dr == 0 && df == 0) continue
            if (chTarget(b, w, r + dr, f + df) != 0) out.add(ChMove(sq, (r + dr) * 8 + f + df))
          }
        }
        // Enroque
        if (w && sq == 60 && !chAttacked(b, 60, false)) {
          if (p.castle.contains('K') && b[61] == '.' && b[62] == '.' && b[63] == 'R' &&
            !chAttacked(b, 61, false) && !chAttacked(b, 62, false)
          ) out.add(ChMove(60, 62))
          if (p.castle.contains('Q') && b[59] == '.' && b[58] == '.' && b[57] == '.' && b[56] == 'R' &&
            !chAttacked(b, 59, false) && !chAttacked(b, 58, false)
          ) out.add(ChMove(60, 58))
        }
        if (!w && sq == 4 && !chAttacked(b, 4, true)) {
          if (p.castle.contains('k') && b[5] == '.' && b[6] == '.' && b[7] == 'r' &&
            !chAttacked(b, 5, true) && !chAttacked(b, 6, true)
          ) out.add(ChMove(4, 6))
          if (p.castle.contains('q') && b[3] == '.' && b[2] == '.' && b[1] == '.' && b[0] == 'r' &&
            !chAttacked(b, 3, true) && !chAttacked(b, 2, true)
          ) out.add(ChMove(4, 2))
        }
      }
    }
  }
  return out
}

private fun chApply(p: ChPos, m: ChMove): ChPos {
  val b = p.b.copyOf()
  val pc = b[m.from]
  val w = p.white
  val captured = b[m.to]
  var capture = captured != '.'
  var castle = p.castle
  var ep = -1
  b[m.from] = '.'
  if (pc.lowercaseChar() == 'p') {
    if (m.to == p.ep && captured == '.' && m.from % 8 != m.to % 8) {
      b[(m.from / 8) * 8 + m.to % 8] = '.'
      capture = true
    }
    if (kotlin.math.abs(m.to / 8 - m.from / 8) == 2) ep = (m.from + m.to) / 2
  }
  b[m.to] = if (m.promo != ' ') (if (w) m.promo.uppercaseChar() else m.promo.lowercaseChar()) else pc
  if (pc.lowercaseChar() == 'k' && kotlin.math.abs(m.to % 8 - m.from % 8) == 2) {
    if (m.to % 8 == 6) {
      b[m.to - 1] = b[m.to + 1]
      b[m.to + 1] = '.'
    } else {
      b[m.to + 1] = b[m.to - 2]
      b[m.to - 2] = '.'
    }
  }
  if (pc == 'K') castle = castle.replace("K", "").replace("Q", "")
  if (pc == 'k') castle = castle.replace("k", "").replace("q", "")
  for (s in intArrayOf(m.from, m.to)) {
    when (s) {
      63 -> castle = castle.replace("K", "")
      56 -> castle = castle.replace("Q", "")
      7 -> castle = castle.replace("k", "")
      0 -> castle = castle.replace("q", "")
    }
  }
  val half = if (pc.lowercaseChar() == 'p' || capture) 0 else p.half + 1
  return ChPos(b, !w, castle, ep, half)
}

private fun chKingSq(b: CharArray, w: Boolean): Int = b.indexOf(if (w) 'K' else 'k')

private fun chInCheck(p: ChPos): Boolean {
  val k = chKingSq(p.b, p.white)
  return k >= 0 && chAttacked(p.b, k, !p.white)
}

private fun chLegal(p: ChPos): List<ChMove> = chPseudo(p).filter { m ->
  val np = chApply(p, m)
  val k = chKingSq(np.b, p.white)
  k >= 0 && !chAttacked(np.b, k, !p.white)
}

private fun chInsufficient(b: CharArray): Boolean {
  val rest = b.filter { it != '.' && it.lowercaseChar() != 'k' }
  if (rest.isEmpty()) return true
  return rest.size == 1 && (rest[0].lowercaseChar() == 'b' || rest[0].lowercaseChar() == 'n')
}

/** "" = sigue, "mate", "stalemate" o "draw". */
private fun chOutcome(np: ChPos): String {
  if (chLegal(np).isEmpty()) return if (chInCheck(np)) "mate" else "stalemate"
  if (np.half >= 100 || chInsufficient(np.b)) return "draw"
  return ""
}

private fun chGlyph(p: Char): String {
  val g = when (p.lowercaseChar()) {
    'k' -> "♚"
    'q' -> "♛"
    'r' -> "♜"
    'b' -> "♝"
    'n' -> "♞"
    'p' -> "♟"
    else -> ""
  }
  return if (g.isEmpty()) "" else g + "\uFE0E"
}

// ═════════════════════════════════════════════════════════════════════════════
// Interfaz 3D del ajedrez: tablero en perspectiva real (plano inclinado), piezas como sprites que escalan con la
// profundidad y proyectan sombra, saltos del caballo, enroque, capturas con onda expansiva y chispas, y reloj opcional.
// El motor de reglas (arriba) no cambia.
// ═════════════════════════════════════════════════════════════════════════════

/** Cámara: el tablero es un plano; la casilla (u, v) con altura z se proyecta con una perspectiva simple. */
private class ChCam(val w: Float, val h: Float) {
  val f = 0.735f * w
  val d0 = 6.0f
  val dd = 0.38f
  val camH = 18.0f
  val cx = w / 2f
  val yh = 0.88f * h - f * camH / d0
  fun depth(v: Float): Float = d0 + (8f - v) * dd
  fun p(u: Float, v: Float, z: Float = 0f): Offset = Offset(cx + f * (u - 4f) / depth(v), yh + f * (camH - z) / depth(v))
  /** Píxeles por casilla a la profundidad de la fila v. */
  fun cell(v: Float): Float = f / depth(v)
  /** Inversa sobre el plano del suelo: pantalla → coordenadas (u, v) del tablero. */
  fun inv(x: Float, y: Float): Offset {
    val d = f * camH / (y - yh)
    return Offset(4f + (x - cx) * d / f, 8f - (d - d0) / dd)
  }
}

private class ChAnim(
  val from: Int, val to: Int, val mover: Char, val cap: Char, val capSq: Int,
  val rookFrom: Int, val rookTo: Int, val promoted: Char
)

private class ChPiece(val ch: Char, val u: Float, val v: Float, val z: Float, val alpha: Float, val scale: Float, val tilt: Float)

private class ChClockPreset(val label: String, val ms: Long, val incMs: Long)
private val CH_CLOCKS = listOf(
  ChClockPreset("Sin reloj", 0L, 0L), ChClockPreset("Bala 1+0", 60_000L, 0L), ChClockPreset("Blitz 3+0", 180_000L, 0L),
  ChClockPreset("Blitz 3+2", 180_000L, 2_000L), ChClockPreset("Rápida 10+0", 600_000L, 0L), ChClockPreset("Clásica 15+10", 900_000L, 10_000L)
)
private val CH_VALUES = mapOf('p' to 1, 'n' to 3, 'b' to 3, 'r' to 5, 'q' to 9)

private fun chSan(p: ChPos, m: ChMove, np: ChPos, out: String): String {
  val pc = p.b[m.from]
  val up = pc.uppercaseChar()
  val file = 'a' + m.to % 8
  val rank = 8 - m.to / 8
  val cap = p.b[m.to] != '.' || (up == 'P' && m.from % 8 != m.to % 8)
  var s = if (up == 'K' && kotlin.math.abs(m.to % 8 - m.from % 8) == 2) {
    if (m.to % 8 == 6) "O-O" else "O-O-O"
  } else if (up == 'P') {
    (if (cap) "${'a' + m.from % 8}x" else "") + "$file$rank" + (if (m.promo != ' ') "=${m.promo.uppercaseChar()}" else "")
  } else {
    "$up" + (if (cap) "x" else "") + "$file$rank"
  }
  s += if (out == "mate") "#" else if (chInCheck(np)) "+" else ""
  return s
}

/** Piezas que se ven "capturadas" de cada color a partir del tablero actual (las coronaciones no cuentan como peón capturado). */
private fun chCapturedCounts(b: CharArray, whitePieces: Boolean): Map<Char, Int> {
  val init = mapOf('p' to 8, 'n' to 2, 'b' to 2, 'r' to 2, 'q' to 1)
  val cur = HashMap<Char, Int>()
  for (ch in b) if (ch != '.' && chIsWhite(ch) == whitePieces) cur[ch.lowercaseChar()] = (cur[ch.lowercaseChar()] ?: 0) + 1
  var extras = 0
  for (k in listOf('n', 'b', 'r', 'q')) extras += maxOf(0, (cur[k] ?: 0) - (init[k] ?: 0))
  val out = HashMap<Char, Int>()
  for ((k, v) in init) {
    var missing = maxOf(0, v - (cur[k] ?: 0))
    if (k == 'p') missing = maxOf(0, missing - extras)
    if (missing > 0) out[k] = missing
  }
  return out
}

private fun chPieceRes(piece: Char): Int = when (piece) {
  'K' -> R.drawable.chess_blancas_piezas_04
  'Q' -> R.drawable.chess_blancas_piezas_05
  'R' -> R.drawable.chess_blancas_piezas_01
  'B' -> R.drawable.chess_blancas_piezas_03
  'N' -> R.drawable.chess_blancas_piezas_02
  'P' -> R.drawable.chess_blancas_peones_01
  'k' -> R.drawable.chess_negras_piezas_04
  'q' -> R.drawable.chess_negras_piezas_05
  'r' -> R.drawable.chess_negras_piezas_01
  'b' -> R.drawable.chess_negras_piezas_03
  'n' -> R.drawable.chess_negras_piezas_02
  'p' -> R.drawable.chess_negras_peones_01
  else -> 0
}

private fun chFmtClock(ms: Long): String {
  val t = maxOf(0L, ms)
  val m = t / 60000
  val s = (t / 1000) % 60
  return if (t < 10_000L) String.format(java.util.Locale.US, "%d:%02d.%d", m, s, (t / 100) % 10) else String.format(java.util.Locale.US, "%d:%02d", m, s)
}

/** Polígono de un círculo de radio `rad` (en casillas) apoyado en el tablero. */
private fun chCirclePath(cam: ChCam, uc: Float, vc: Float, rad: Float, z: Float = 0f): Path {
  val path = Path()
  for (i in 0..24) {
    val a = i * (2f * PI.toFloat() / 24f)
    val pt = cam.p(uc + cos(a) * rad, vc + sin(a) * rad, z)
    if (i == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
  }
  path.close()
  return path
}

/** Rectángulo en pantalla que ocupa el sprite de una pieza colocada en (u, v): sirve para dibujar y para tocar la pieza. */
private fun chSpriteBox(cam: ChCam, bmp: ImageBitmap, u: Float, v: Float, z: Float, scale: Float): androidx.compose.ui.geometry.Rect {
  val base = cam.p(u, v, z)
  val k = cam.cell(v) * 1.5f / 458f * scale
  val w = bmp.width * k
  val h = bmp.height * k
  val by = base.y + cam.cell(v) * 0.12f
  return androidx.compose.ui.geometry.Rect(base.x - w / 2f, by - h, base.x + w / 2f, by)
}

private fun DrawScope.drawChessScene(
  cam: ChCam, board: CharArray, flip: Boolean, bmps: Map<Char, ImageBitmap>, selected: Int, targets: Set<Int>, last: List<Int>,
  kingSq: Int, pulse: Float, anim: ChAnim?, animP: Float, fxP: Float, fallenKing: Int
) {
  fun col(sq: Int) = if (flip) 7 - sq % 8 else sq % 8
  fun row(sq: Int) = if (flip) 7 - sq / 8 else sq / 8
  drawRect(Brush.verticalGradient(listOf(Color(0xFF120A22), Color(0xFF241338), Color(0xFF0C0716))))
  drawCircle(Brush.radialGradient(listOf(Color(0x33FFFFFF), Color.Transparent), center = Offset(cam.cx, cam.h * 0.45f), radius = cam.w * 0.8f), cam.w * 0.8f, Offset(cam.cx, cam.h * 0.45f))

  // temblor de pantalla al capturar
  val shake = if (anim != null && anim.cap != '.' && animP >= 1f && fxP < 0.35f) (1f - fxP / 0.35f) * 6f else 0f
  translate(sin(fxP * 120f) * shake, cos(fxP * 97f) * shake * 0.6f) {
    // sombra y marco de madera con espesor
    val shadow = Path().apply {
      val a = cam.p(-0.5f, -0.5f, -0.6f); val b = cam.p(8.5f, -0.5f, -0.6f); val c = cam.p(8.5f, 8.5f, -0.6f); val d = cam.p(-0.5f, 8.5f, -0.6f)
      moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); lineTo(d.x, d.y); close()
    }
    drawPath(shadow, Color(0x66000000))
    val fa = cam.p(-0.4f, -0.4f); val fb = cam.p(8.4f, -0.4f); val fc = cam.p(8.4f, 8.4f); val fd = cam.p(-0.4f, 8.4f)
    val frontA = cam.p(-0.4f, 8.4f, -0.55f); val frontB = cam.p(8.4f, 8.4f, -0.55f)
    drawPath(Path().apply { moveTo(fd.x, fd.y); lineTo(fc.x, fc.y); lineTo(frontB.x, frontB.y); lineTo(frontA.x, frontA.y); close() }, Brush.verticalGradient(listOf(Color(0xFF5A3320), Color(0xFF2C170B)), startY = fd.y, endY = frontA.y))
    drawPath(Path().apply { moveTo(fa.x, fa.y); lineTo(fb.x, fb.y); lineTo(fc.x, fc.y); lineTo(fd.x, fd.y); close() }, Brush.verticalGradient(listOf(Color(0xFF8A5533), Color(0xFF4A2A15)), startY = fa.y, endY = fd.y))
    // casillas
    for (r in 0..7) for (cc in 0..7) {
      val a = cam.p(cc.toFloat(), r.toFloat()); val b = cam.p(cc + 1f, r.toFloat()); val c = cam.p(cc + 1f, r + 1f); val d = cam.p(cc.toFloat(), r + 1f)
      val path = Path().apply { moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); lineTo(d.x, d.y); close() }
      val dark = (r + cc) % 2 == 1
      drawPath(path, Brush.verticalGradient(if (dark) listOf(Color(0xFFB5657A), Color(0xFF8E4A5E)) else listOf(Color(0xFFF7E3E8), Color(0xFFE2C3CB)), startY = a.y, endY = d.y))
    }
    // coordenadas
    // resaltados: última jugada, selección y rey en jaque
    fun tint(sq: Int, color: Color) {
      val cc = col(sq); val r = row(sq)
      val a = cam.p(cc.toFloat(), r.toFloat()); val b = cam.p(cc + 1f, r.toFloat()); val c = cam.p(cc + 1f, r + 1f); val d = cam.p(cc.toFloat(), r + 1f)
      drawPath(Path().apply { moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); lineTo(d.x, d.y); close() }, color)
    }
    last.forEach { tint(it, Color(0x55FFD54F)) }
    if (selected >= 0) tint(selected, Color(0x99FFC857))
    if (kingSq >= 0) {
      val kp = chCirclePath(cam, col(kingSq) + 0.5f, row(kingSq) + 0.5f, 0.62f)
      drawPath(kp, Color(0xFFFF2D2D).copy(alpha = 0.25f + 0.35f * pulse))
      drawPath(chCirclePath(cam, col(kingSq) + 0.5f, row(kingSq) + 0.5f, 0.46f), Color(0xFFFF5252).copy(alpha = 0.6f + 0.4f * pulse), style = Stroke(width = 3f))
    }
    // destinos legales
    for (t in targets) {
      val uc = col(t) + 0.5f; val vc = row(t) + 0.5f
      if (board[t] == '.') drawPath(chCirclePath(cam, uc, vc, 0.17f), Color(0xCC1F2433))
      else drawPath(chCirclePath(cam, uc, vc, 0.45f), Color(0xFFFF3B5C), style = Stroke(width = 4f))
    }

    // piezas ordenadas de lejos a cerca
    val items = ArrayList<ChPiece>()
    for (sq in 0 until 64) {
      val ch = board[sq]
      if (ch == '.') continue
      if (anim != null && (sq == anim.to || sq == anim.rookTo)) continue
      val tilt = if (sq == fallenKing) 72f else 0f
      items.add(ChPiece(ch, col(sq) + 0.5f, row(sq) + 0.5f, 0f, 1f, 1f, tilt))
    }
    if (anim != null) {
      val e = animP.coerceIn(0f, 1f)
      fun lerp1(a: Float, b: Float) = a + (b - a) * e
      val isKnight = anim.mover.lowercaseChar() == 'n'
      val hop = if (isKnight) sin(e * PI.toFloat()) * 1.25f else sin(e * PI.toFloat()) * 0.18f
      val shown = if (e >= 1f && anim.promoted != ' ') anim.promoted else anim.mover
      items.add(ChPiece(shown, lerp1(col(anim.from) + 0.5f, col(anim.to) + 0.5f), lerp1(row(anim.from) + 0.5f, row(anim.to) + 0.5f), hop, 1f, if (isKnight) 1f + 0.12f * sin(e * PI.toFloat()) else 1f, 0f))
      if (anim.rookFrom >= 0) {
        val rook = if (chIsWhite(anim.mover)) 'R' else 'r'
        items.add(ChPiece(rook, lerp1(col(anim.rookFrom) + 0.5f, col(anim.rookTo) + 0.5f), lerp1(row(anim.rookFrom) + 0.5f, row(anim.rookTo) + 0.5f), sin(e * PI.toFloat()) * 0.35f, 1f, 1f, 0f))
      }
      // pieza capturada: se hunde y se desvanece tras el impacto
      if (anim.cap != '.') {
        val fall = if (animP < 1f) 0f else (fxP / 0.55f).coerceIn(0f, 1f)
        if (fall < 1f) items.add(ChPiece(anim.cap, col(anim.capSq) + 0.5f, row(anim.capSq) + 0.5f, -fall * 0.5f, 1f - fall, 1f - 0.35f * fall, fall * 40f * (if (anim.capSq % 2 == 0) 1f else -1f)))
      }
    }
    items.sortBy { it.v }
    for (pc in items) {
      val bmp = bmps[pc.ch] ?: continue
      val shadowP = cam.p(pc.u, pc.v, 0f)
      val cellPx = cam.cell(pc.v)
      drawOval(Color(0x55000000).copy(alpha = 0.4f * pc.alpha * (1f - (pc.z * 0.25f).coerceIn(0f, 0.6f))), Offset(shadowP.x - cellPx * 0.34f, shadowP.y - cellPx * 0.07f), Size(cellPx * 0.68f, cellPx * 0.2f))
      val box = chSpriteBox(cam, bmp, pc.u, pc.v, pc.z, pc.scale)
      val draw = {
        drawImage(
          bmp, IntOffset.Zero, IntSize(bmp.width, bmp.height), IntOffset(box.left.toInt(), box.top.toInt()),
          IntSize(box.width.toInt().coerceAtLeast(1), box.height.toInt().coerceAtLeast(1)), pc.alpha
        )
      }
      if (pc.tilt != 0f) rotate(pc.tilt, Offset((box.left + box.right) / 2f, box.bottom)) { draw() } else draw()
    }

    // efectos del impacto: onda expansiva en el tablero y chispas
    if (anim != null && anim.cap != '.' && animP >= 1f && fxP < 1f) {
      val uc = col(anim.to) + 0.5f; val vc = row(anim.to) + 0.5f
      drawPath(chCirclePath(cam, uc, vc, 0.2f + fxP * 1.9f), Color(0xFFFFE08A).copy(alpha = (1f - fxP) * 0.9f), style = Stroke(width = 5f * (1f - fxP) + 1f))
      drawPath(chCirclePath(cam, uc, vc, 0.1f + fxP * 1.2f), Color.White.copy(alpha = (1f - fxP) * 0.6f), style = Stroke(width = 2.5f))
      val c0 = cam.p(uc, vc, 0.35f)
      val cellPx = cam.cell(vc)
      for (s in 0 until 22) {
        val a = s * 0.62f + anim.to * 0.37f
        val dist = fxP * cellPx * (0.8f + (s % 4) * 0.45f)
        val colr = when (s % 3) { 0 -> Color(0xFFFFD166); 1 -> Color.White; else -> Color(0xFFFF5C93) }
        drawCircle(colr.copy(alpha = 1f - fxP), (3.2f * (1f - fxP) + 0.8f), Offset(c0.x + cos(a) * dist, c0.y + sin(a) * dist * 0.7f - fxP * cellPx * 0.7f + fxP * fxP * cellPx * 0.9f))
      }
    }
  }
  drawRect(Brush.radialGradient(listOf(Color.Transparent, Color(0x77000000)), center = Offset(cam.cx, cam.h / 2f), radius = maxOf(cam.w, cam.h) * 0.8f))
}

@Composable
private fun ChCapturedRow(counts: Map<Char, Int>, capturedAreWhite: Boolean, advantage: Int) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    val order = listOf('q', 'r', 'b', 'n', 'p')
    Row(horizontalArrangement = Arrangement.spacedBy((-7).dp)) {
      for (k in order) {
        val n = counts[k] ?: 0
        repeat(n) {
          val ch = if (capturedAreWhite) k.uppercaseChar() else k
          Image(painterResource(chPieceRes(ch)), contentDescription = "Pieza capturada", modifier = Modifier.size(if (k == 'p') 22.dp else 30.dp))
        }
      }
    }
    if (advantage > 0) Text(" +$advantage", color = Color(0xFF7DE3A6), fontWeight = FontWeight.Black, fontSize = 12.sp)
  }
}

@Composable
private fun ChClockPill(ms: Long, active: Boolean, low: Boolean) {
  val shape = RoundedCornerShape(12.dp)
  Box(
    Modifier.clip(shape).background(if (active) (if (low) Color(0xFFC62828) else Color(0xFF2E7D32)) else Color(0x33FFFFFF))
      .border(1.5.dp, if (active) Color.White else Color(0x33FFFFFF), shape).padding(horizontal = 12.dp, vertical = 5.dp)
  ) { Text(chFmtClock(ms), color = Color.White, fontWeight = FontWeight.Black, fontSize = 17.sp) }
}


private fun chCol(sq: Int, flip: Boolean) = if (flip) 7 - sq % 8 else sq % 8
private fun chRow(sq: Int, flip: Boolean) = if (flip) 7 - sq / 8 else sq / 8

@Composable
internal fun ChessGame(c: GameCtx) {
  val flow = remember { c.vm.gameState("chess") }
  val st by flow.collectAsState(initial = null)
  fun num(k: String, d: Int = 0) = (st?.get(k) as? Number)?.toInt() ?: d
  fun lng(k: String, d: Long = 0L) = (st?.get(k) as? Number)?.toLong() ?: d

  val boardStr = (st?.get("board") as? String)?.takeIf { it.length == 64 } ?: CH_START
  val castle = (st?.get("castle") as? String) ?: "KQkq"
  val ep = num("ep", -1)
  val half = num("half")
  val whiteTurn = (st?.get("wt") as? Boolean) ?: true
  val whiteUid = (st?.get("white") as? String)?.takeIf { it.isNotEmpty() } ?: c.uidAt(0)
  val blackUid = c.players.firstOrNull { it != whiteUid } ?: whiteUid
  val turnUid = if (whiteTurn) whiteUid else blackUid
  val status = (st?.get("status") as? String) ?: ""
  val winner = (st?.get("winner") as? String) ?: ""
  val scoreA = num("scoreA")
  val scoreB = num("scoreB")
  val round = num("round")
  val last = (st?.get("last") as? List<*>)?.mapNotNull { (it as? Number)?.toInt() } ?: emptyList()
  val pboard = (st?.get("pboard") as? String) ?: ""
  val hist = (st?.get("hist") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
  val mseq = num("mseq")
  val clIdx = num("cl").coerceIn(0, CH_CLOCKS.size - 1)
  val clockOn = CH_CLOCKS[clIdx].ms > 0L
  val tw = lng("tw"); val tb = lng("tb"); val inc = lng("inc"); val lm = lng("lm")

  val pos = remember(boardStr, castle, ep, whiteTurn, half) { ChPos(boardStr.toCharArray(), whiteTurn, castle, ep, half) }
  val legal = remember(pos, status) { if (status.isEmpty()) chLegal(pos) else emptyList() }
  val inCheck = remember(pos) { chInCheck(pos) }

  var selected by remember { mutableStateOf(-1) }
  var promo by remember { mutableStateOf<Pair<Int, Int>?>(null) }
  var primed by remember { mutableStateOf(false) }
  var anim by remember { mutableStateOf<ChAnim?>(null) }
  val animP = remember { Animatable(1f) }
  val fxP = remember { Animatable(1f) }
  var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
  val enter = remember { Animatable(0f) }
  val pulse by rememberInfiniteTransition(label = "chPulse").animateFloat(
    initialValue = 0f, targetValue = 1f, animationSpec = infiniteRepeatable(tween(650, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "chPulseV"
  )
  LaunchedEffect(Unit) { enter.animateTo(1f, tween(450, easing = FastOutSlowInEasing)) }
  LaunchedEffect(boardStr, status) { selected = -1; promo = null }

  val myTurn = status.isEmpty() && turnUid == c.me && c.players.contains(c.me)
  val flip = c.me == blackUid && c.me != whiteUid
  val targets = if (selected >= 0) legal.filter { it.from == selected }.map { it.to }.toSet() else emptySet()
  val kingSq = if (inCheck && status.isEmpty()) chKingSq(pos.b, whiteTurn) else -1
  val fallenKing = if (status == "mate") chKingSq(pos.b, whiteTurn) else -1

  val bmps = mapOf(
    'K' to ImageBitmap.imageResource(R.drawable.chess_blancas_piezas_04), 'Q' to ImageBitmap.imageResource(R.drawable.chess_blancas_piezas_05),
    'R' to ImageBitmap.imageResource(R.drawable.chess_blancas_piezas_01), 'B' to ImageBitmap.imageResource(R.drawable.chess_blancas_piezas_03),
    'N' to ImageBitmap.imageResource(R.drawable.chess_blancas_piezas_02), 'P' to ImageBitmap.imageResource(R.drawable.chess_blancas_peones_01),
    'k' to ImageBitmap.imageResource(R.drawable.chess_negras_piezas_04), 'q' to ImageBitmap.imageResource(R.drawable.chess_negras_piezas_05),
    'r' to ImageBitmap.imageResource(R.drawable.chess_negras_piezas_01), 'b' to ImageBitmap.imageResource(R.drawable.chess_negras_piezas_03),
    'n' to ImageBitmap.imageResource(R.drawable.chess_negras_piezas_02), 'p' to ImageBitmap.imageResource(R.drawable.chess_negras_peones_01)
  )

  fun scoresAfterWin(winUid: String): Pair<Int, Int> {
    val idx = c.players.indexOf(winUid)
    return when (idx) {
      0 -> Pair(scoreA + 1, scoreB)
      1 -> Pair(scoreA, scoreB + 1)
      else -> Pair(scoreA, scoreB)
    }
  }
  /** Todo el estado actual de la partida; cada publicación parte de acá para no perder campos. */
  fun baseMap(): MutableMap<String, Any?> = mutableMapOf(
    "board" to boardStr, "castle" to castle, "ep" to ep, "half" to half, "wt" to whiteTurn, "white" to whiteUid, "status" to status,
    "winner" to winner, "last" to last, "scoreA" to scoreA, "scoreB" to scoreB, "round" to round, "pboard" to pboard, "hist" to hist,
    "mseq" to mseq, "cl" to clIdx, "tw" to tw, "tb" to tb, "inc" to inc, "lm" to lm
  )
  fun remainingFor(white: Boolean): Long {
    val base = if (white) tw else tb
    return if (clockOn && status.isEmpty() && lm > 0L && whiteTurn == white) base - (nowMs - lm) else base
  }
  fun publishTimeout() {
    val loser = turnUid
    val winUid = c.players.firstOrNull { it != loser } ?: ""
    val s = if (winUid.isNotEmpty()) scoresAfterWin(winUid) else Pair(scoreA, scoreB)
    val m = baseMap()
    m["status"] = "timeout"; m["winner"] = winUid; m["scoreA"] = s.first; m["scoreB"] = s.second
    if (loser == whiteUid) m["tw"] = 0L else m["tb"] = 0L
    c.vm.publishGame("chess", m)
    if (winUid == c.me) c.vm.addCredits(100)
  }

  fun commit(m: ChMove) {
    val np = chApply(pos, m)
    val out = chOutcome(np)
    var a = scoreA
    var b = scoreB
    var win = ""
    if (out == "mate") {
      win = c.me
      val s = scoresAfterWin(c.me)
      a = s.first; b = s.second
    }
    val now = System.currentTimeMillis()
    var twN = tw; var tbN = tb; var lmN = lm
    if (clockOn) {
      if (lm > 0L) { val el = now - lm; if (whiteTurn) twN = twN - el + inc else tbN = tbN - el + inc }
      lmN = now
    }
    val data = baseMap()
    data["board"] = np.b.concatToString(); data["castle"] = np.castle; data["ep"] = np.ep; data["half"] = np.half
    data["wt"] = np.white; data["status"] = out; data["winner"] = win; data["last"] = listOf(m.from, m.to)
    data["scoreA"] = a; data["scoreB"] = b; data["pboard"] = boardStr; data["hist"] = (hist + chSan(pos, m, np, out)).takeLast(300)
    data["mseq"] = mseq + 1; data["tw"] = twN; data["tb"] = tbN; data["lm"] = lmN
    c.vm.publishGame("chess", data)
    if (out == "mate") { c.vm.gameSfx(2); c.vm.addCredits(100) } else if (out.isNotEmpty()) c.vm.gameSfx(1)
  }

  fun tapSquare(sq: Int) {
    if (!myTurn || promo != null || sq !in 0..63) return
    val pc = pos.b[sq]
    val mine = pc != '.' && chIsWhite(pc) == whiteTurn
    val sel = selected
    if (sel >= 0) {
      val cands = legal.filter { it.from == sel && it.to == sq }
      if (cands.size == 1) { commit(cands[0]); return }
      if (cands.size > 1) { promo = Pair(sel, sq); return }
    }
    selected = if (mine && legal.any { it.from == sq }) (if (sq == sel) -1 else sq) else -1
  }

  // Animación de cada jugada, igual en los dos dispositivos: salto del caballo, deslizamiento, enroque y captura con efectos.
  LaunchedEffect(mseq, st != null) {
    if (st == null) return@LaunchedEffect
    if (!primed) { primed = true; return@LaunchedEffect }
    if (pboard.length != 64 || last.size < 2) return@LaunchedEffect
    val from = last[0]; val to = last[1]
    val pb = pboard.toCharArray()
    val mover = pb[from]
    if (mover == '.') return@LaunchedEffect
    var cap = pb[to]; var capSq = to
    if (mover.lowercaseChar() == 'p' && cap == '.' && from % 8 != to % 8) { capSq = (from / 8) * 8 + to % 8; cap = pb[capSq] }
    var rf = -1; var rt = -1
    if (mover.lowercaseChar() == 'k' && kotlin.math.abs(to % 8 - from % 8) == 2) {
      if (to % 8 == 6) { rf = (from / 8) * 8 + 7; rt = to - 1 } else { rf = (from / 8) * 8; rt = to + 1 }
    }
    val newPiece = boardStr[to]
    val promoted = if (mover.lowercaseChar() == 'p' && newPiece != mover) newPiece else ' '
    anim = ChAnim(from, to, mover, cap, capSq, rf, rt, promoted)
    animP.snapTo(0f); fxP.snapTo(0f)
    launch { SynthSfx.play(SynthSfx.whoosh, 0.22f) }
    animP.animateTo(1f, tween(if (mover.lowercaseChar() == 'n') 720 else 540, easing = FastOutSlowInEasing))
    launch { SynthSfx.play(SynthSfx.thud, 0.7f) }
    if (rf >= 0) launch { SynthSfx.play(SynthSfx.thud, 0.5f) }
    if (cap != '.') {
      launch { SynthSfx.play(SynthSfx.clang, 0.4f) }
      launch { SynthSfx.play(SynthSfx.click, 0.6f) }
      fxP.animateTo(1f, tween(850))
    } else delay(120)
    if (inCheck && status.isEmpty()) launch { SynthSfx.play(SynthSfx.clang, 0.55f) }
    anim = null
  }

  // Reloj: se actualiza varias veces por segundo; quien se queda sin tiempo pierde (el rival lo reclama tras 4 s si no responde).
  LaunchedEffect(clockOn, status, mseq) {
    while (clockOn && status.isEmpty()) {
      nowMs = System.currentTimeMillis()
      if (lm > 0L && c.players.contains(c.me)) {
        val rem = remainingFor(whiteTurn)
        if (rem <= 0L && (turnUid == c.me || rem <= -4000L)) { publishTimeout(); break }
      }
      delay(200)
    }
  }

  val whiteIsFirst = whiteUid == c.uidAt(0)
  val topWhite = flip
  val bottomWhite = !flip
  val topUid = if (topWhite) whiteUid else blackUid
  val bottomUid = if (bottomWhite) whiteUid else blackUid
  fun material(counts: Map<Char, Int>): Int = counts.entries.sumOf { (CH_VALUES[it.key] ?: 0) * it.value }
  val missingWhite = chCapturedCounts(pos.b, true)   // piezas blancas que ya no están
  val missingBlack = chCapturedCounts(pos.b, false)
  val capturedByBottom = if (bottomWhite) missingBlack else missingWhite  // lo que capturó quien juega abajo
  val capturedByTop = if (bottomWhite) missingWhite else missingBlack
  val bottomAdv = material(capturedByBottom) - material(capturedByTop)

  Column(Modifier.graphicsLayer { alpha = enter.value; translationY = (1f - enter.value) * 30f }) {
    ScoreBoard(c, scoreA, scoreB, if (status.isEmpty()) turnUid else null, if (whiteIsFirst) "♔" else "♚", if (whiteIsFirst) "♚" else "♔")
    Spacer(Modifier.height(8.dp))
    TurnBanner(
      when {
        status == "mate" -> "🏆 ¡Jaque mate! Ganó ${c.nameOf(winner)}"
        status == "resign" -> "🏆 Ganó ${c.nameOf(winner)}: el rival se rindió"
        status == "timeout" -> "⏱ Se acabó el tiempo: ganó ${c.nameOf(winner)}"
        status == "stalemate" -> "🤝 Tablas: rey ahogado"
        status == "draw" -> "🤝 Tablas"
        myTurn && inCheck -> "⚠️ ¡Jaque! Tu turno (${if (whiteTurn) "blancas" else "negras"})"
        myTurn -> "Tu turno: mueves las ${if (whiteTurn) "blancas" else "negras"}"
        inCheck -> "Esperando a ${c.partnerName}… (está en jaque)"
        else -> "Esperando a ${c.partnerName}…"
      }, myTurn
    )
    Spacer(Modifier.height(8.dp))
    // jugador de arriba: nombre, lo que capturó y su reloj
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text("${if (topWhite) "♔" else "♚"} ${c.nameOf(topUid)}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
        ChCapturedRow(capturedByTop, !topWhite, -bottomAdv)
      }
      if (clockOn) ChClockPill(remainingFor(topWhite), status.isEmpty() && whiteTurn == topWhite && lm > 0L, remainingFor(topWhite) < 10_000L)
    }
    Spacer(Modifier.height(4.dp))
    Box(Modifier.fillMaxWidth()) {
      Canvas(
        Modifier.fillMaxWidth().aspectRatio(0.93f).pointerInput(boardStr, selected, myTurn, flip, promo, status, mseq) {
          detectTapGestures { tap ->
            if (!myTurn || promo != null) return@detectTapGestures
            val cam = ChCam(size.width.toFloat(), size.height.toFloat())
            var hitSq = -1
            var bestV = -1f
            for (sq in 0 until 64) {
              val ch = pos.b[sq]
              if (ch == '.') continue
              val bmp = bmps[ch] ?: continue
              val u = chCol(sq, flip) + 0.5f; val v = chRow(sq, flip) + 0.5f
              val box = chSpriteBox(cam, bmp, u, v, 0f, 1f)
              if (tap.x >= box.left + box.width * 0.2f && tap.x <= box.right - box.width * 0.2f && tap.y >= box.top && tap.y <= box.bottom && v > bestV) { bestV = v; hitSq = sq }
            }
            val uv = cam.inv(tap.x, tap.y)
            var floorSq = -1
            if (uv.x in 0f..8f && uv.y in 0f..8f) {
              val sc = uv.x.toInt().coerceIn(0, 7); val sr = uv.y.toInt().coerceIn(0, 7)
              floorSq = if (flip) (7 - sr) * 8 + (7 - sc) else sr * 8 + sc
            }
            // prioridad: destino legal tocado por la pieza o por el suelo; si no, la pieza; si no, el suelo
            val sq = when {
              hitSq >= 0 && targets.contains(hitSq) -> hitSq
              floorSq >= 0 && targets.contains(floorSq) -> floorSq
              hitSq >= 0 -> hitSq
              else -> floorSq
            }
            tapSquare(sq)
          }
        }
      ) {
        val cam = ChCam(size.width, size.height)
        drawChessScene(cam, pos.b, flip, bmps, selected, targets, last, kingSq, pulse, anim, animP.value, fxP.value, fallenKing)
      }
      if (status == "mate" || status == "timeout" || status == "resign") GameConfetti(Modifier.matchParentSize())
    }
    Spacer(Modifier.height(4.dp))
    // jugador de abajo
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text("${if (bottomWhite) "♔" else "♚"} ${c.nameOf(bottomUid)}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
        ChCapturedRow(capturedByBottom, !bottomWhite, bottomAdv)
      }
      if (clockOn) ChClockPill(remainingFor(bottomWhite), status.isEmpty() && whiteTurn == bottomWhite && lm > 0L, remainingFor(bottomWhite) < 10_000L)
    }
    // historial de jugadas
    if (hist.isNotEmpty()) {
      val histScroll = rememberScrollState()
      LaunchedEffect(hist.size) { histScroll.animateScrollTo(histScroll.maxValue) }
      Spacer(Modifier.height(6.dp))
      Row(Modifier.fillMaxWidth().horizontalScroll(histScroll), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        hist.chunked(2).forEachIndexed { i, pair ->
          Text("${i + 1}. ${pair.joinToString(" ")}", color = if (i == (hist.size - 1) / 2) GoldYellow else Color(0xCCFFFFFF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
      }
    }
    // elección de reloj antes de la primera jugada
    val fresh = boardStr == CH_START && last.isEmpty() && status.isEmpty()
    if (fresh && c.players.contains(c.me)) {
      Spacer(Modifier.height(8.dp))
      Text("⏱ Reloj (opcional)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
      Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        CH_CLOCKS.forEachIndexed { i, p ->
          val pick = {
            val m = baseMap()
            m["cl"] = i; m["tw"] = p.ms; m["tb"] = p.ms; m["inc"] = p.incMs; m["lm"] = 0L
            c.vm.publishGame("chess", m)
          }
          if (clIdx == i) GradientButton(p.label, onClick = pick, height = 36.dp) else SoftButton(p.label, onClick = pick)
        }
      }
    }

    val pendingPromo = promo
    if (pendingPromo != null) {
      Spacer(Modifier.height(10.dp))
      Text("Elige la pieza para coronar:", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
      Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        for (ch in "QRBN") {
          Box(
            modifier = Modifier.size(62.dp).clip(RoundedCornerShape(14.dp)).background(Color(0x33FFFFFF))
              .border(BorderStroke(1.dp, GoldYellow), RoundedCornerShape(14.dp))
              .clickable { commit(ChMove(pendingPromo.first, pendingPromo.second, ch)); promo = null },
            contentAlignment = Alignment.Center
          ) {
            val pieceChar = if (whiteTurn) ch.uppercaseChar() else ch.lowercaseChar()
            Image(painter = painterResource(chPieceRes(pieceChar)), contentDescription = "Elegir pieza", modifier = Modifier.size(50.dp))
          }
        }
      }
    }

    Spacer(Modifier.height(12.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      if (status.isEmpty() && c.players.contains(c.me)) {
        SoftButton("🏳️ Rendirse", modifier = Modifier.weight(1f), onClick = {
          val other = c.players.firstOrNull { it != c.me } ?: ""
          val s = if (other.isNotEmpty()) scoresAfterWin(other) else Pair(scoreA, scoreB)
          val m = baseMap()
          m["status"] = "resign"; m["winner"] = other; m["scoreA"] = s.first; m["scoreB"] = s.second
          c.vm.publishGame("chess", m)
        })
      }
      GradientButton(
        "Nueva partida ♟", modifier = Modifier.weight(1f), height = 46.dp,
        onClick = {
          // Cambian los colores en cada partida nueva; el reloj elegido se conserva.
          val m = baseMap()
          m["board"] = CH_START; m["castle"] = "KQkq"; m["ep"] = -1; m["half"] = 0; m["wt"] = true; m["white"] = blackUid
          m["status"] = ""; m["winner"] = ""; m["last"] = emptyList<Int>(); m["round"] = round + 1
          m["pboard"] = ""; m["hist"] = emptyList<String>(); m["mseq"] = mseq + 1
          m["tw"] = CH_CLOCKS[clIdx].ms; m["tb"] = CH_CLOCKS[clIdx].ms; m["lm"] = 0L
          c.vm.publishGame("chess", m)
        }
      )
    }
  }
}
