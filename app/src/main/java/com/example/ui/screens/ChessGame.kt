package com.example.ui.screens

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

@Composable
internal fun ChessGame(c: GameCtx) {
  val flow = remember { c.vm.gameState("chess") }
  val st by flow.collectAsState(initial = null)

  val boardStr = (st?.get("board") as? String)?.takeIf { it.length == 64 } ?: CH_START
  val castle = (st?.get("castle") as? String) ?: "KQkq"
  val ep = (st?.get("ep") as? Number)?.toInt() ?: -1
  val half = (st?.get("half") as? Number)?.toInt() ?: 0
  val whiteTurn = (st?.get("wt") as? Boolean) ?: true
  val whiteUid = (st?.get("white") as? String)?.takeIf { it.isNotEmpty() } ?: c.uidAt(0)
  val blackUid = c.players.firstOrNull { it != whiteUid } ?: whiteUid
  val turnUid = if (whiteTurn) whiteUid else blackUid
  val status = (st?.get("status") as? String) ?: ""
  val winner = (st?.get("winner") as? String) ?: ""
  val scoreA = (st?.get("scoreA") as? Number)?.toInt() ?: 0
  val scoreB = (st?.get("scoreB") as? Number)?.toInt() ?: 0
  val round = (st?.get("round") as? Number)?.toInt() ?: 0
  val last = (st?.get("last") as? List<*>)?.mapNotNull { (it as? Number)?.toInt() } ?: emptyList()

  val pos = remember(boardStr, castle, ep, whiteTurn, half) { ChPos(boardStr.toCharArray(), whiteTurn, castle, ep, half) }
  val legal = remember(pos, status) { if (status.isEmpty()) chLegal(pos) else emptyList() }
  val inCheck = remember(pos) { chInCheck(pos) }

  var selected by remember { mutableStateOf(-1) }
  var promo by remember { mutableStateOf<Pair<Int, Int>?>(null) }
  LaunchedEffect(boardStr, status) {
    selected = -1
    promo = null
  }

  val myTurn = status.isEmpty() && turnUid == c.me && c.players.contains(c.me)
  val flip = c.me == blackUid && c.me != whiteUid
  val targets = if (selected >= 0) legal.filter { it.from == selected }.map { it.to }.toSet() else emptySet()
  val kingSq = if (inCheck) chKingSq(pos.b, whiteTurn) else -1

  fun scoresAfterWin(winUid: String): Pair<Int, Int> {
    val idx = c.players.indexOf(winUid)
    return when (idx) {
      0 -> Pair(scoreA + 1, scoreB)
      1 -> Pair(scoreA, scoreB + 1)
      else -> Pair(scoreA, scoreB)
    }
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
      a = s.first
      b = s.second
    }
    c.vm.publishGame(
      "chess", mapOf(
        "board" to np.b.concatToString(), "castle" to np.castle, "ep" to np.ep, "half" to np.half,
        "wt" to np.white, "white" to whiteUid, "status" to out, "winner" to win,
        "last" to listOf(m.from, m.to), "scoreA" to a, "scoreB" to b, "round" to round
      )
    )
    c.vm.gameSfx(if (out.isNotEmpty()) 2 else 0)
    if (out == "mate") c.vm.addCredits(100)
  }

  fun tap(sq: Int) {
    if (!myTurn || promo != null) return
    val pc = pos.b[sq]
    val mine = pc != '.' && chIsWhite(pc) == whiteTurn
    val sel = selected
    if (sel >= 0) {
      val cands = legal.filter { it.from == sel && it.to == sq }
      if (cands.size == 1) {
        commit(cands[0])
        return
      }
      if (cands.size > 1) {
        promo = Pair(sel, sq)
        return
      }
    }
    selected = if (mine && legal.any { it.from == sq }) (if (sq == sel) -1 else sq) else -1
  }

  val whiteIsFirst = whiteUid == c.uidAt(0)
  ScoreBoard(c, scoreA, scoreB, if (status.isEmpty()) turnUid else null, if (whiteIsFirst) "♔" else "♚", if (whiteIsFirst) "♚" else "♔")
  Spacer(Modifier.height(10.dp))
  TurnBanner(
    when {
      status == "mate" -> "🏆 ¡Jaque mate! Ganó ${c.nameOf(winner)}"
      status == "resign" -> "🏆 Ganó ${c.nameOf(winner)}: el rival se rindió"
      status == "stalemate" -> "🤝 Tablas: rey ahogado"
      status == "draw" -> "🤝 Tablas"
      myTurn && inCheck -> "⚠️ ¡Jaque! Tu turno (${if (whiteTurn) "blancas" else "negras"})"
      myTurn -> "Tu turno: mueves las ${if (whiteTurn) "blancas" else "negras"}"
      inCheck -> "Esperando a ${c.partnerName}… (está en jaque)"
      else -> "Esperando a ${c.partnerName}…"
    }, myTurn
  )
  Spacer(Modifier.height(10.dp))

  val lightSq = Color(0xFFF2D7DD)
  val darkSq = Color(0xFFB5657A)
  BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
    val cellWidth = maxWidth / 8
    val fs = (maxWidth.value / 8f * 0.74f).sp
    Column(
      modifier = Modifier.fillMaxWidth().aspectRatio(1f)
        .clip(RoundedCornerShape(12.dp))
        .border(BorderStroke(2.dp, GoldYellow), RoundedCornerShape(12.dp))
    ) {
      for (row in 0..7) {
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
          for (col in 0..7) {
            val sq = if (flip) (7 - row) * 8 + (7 - col) else row * 8 + col
            val piece = pos.b[sq]
            val isDark = ((sq / 8) + (sq % 8)) % 2 == 1
            val bg = when {
              sq == selected -> Color(0xFFFFC857)
              sq == kingSq -> Color(0xFFFF5A5A)
              last.contains(sq) -> if (isDark) Color(0xFFD98C9E) else Color(0xFFFFE8A6)
              else -> if (isDark) darkSq else lightSq
            }
            Box(
              modifier = Modifier.weight(1f).fillMaxHeight().background(bg).clickable { tap(sq) },
              contentAlignment = Alignment.Center
            ) {
              if (piece != '.') {
                val whitePiece = chIsWhite(piece)
                val movingTarget = last.size >= 2 && sq == last[1]
                val moveFrom = if (movingTarget) last[0] else sq
                val slideX = remember(boardStr, last, sq) { Animatable(if (movingTarget) ((moveFrom % 8) - (sq % 8)).toFloat() else 0f) }
                val slideY = remember(boardStr, last, sq) { Animatable(if (movingTarget) ((moveFrom / 8) - (sq / 8)).toFloat() else 0f) }
                LaunchedEffect(boardStr, last, sq) {
                  if (movingTarget && moveFrom != sq) {
                    slideX.snapTo(((moveFrom % 8) - (sq % 8)).toFloat())
                    slideY.snapTo(((moveFrom / 8) - (sq / 8)).toFloat())
                    kotlinx.coroutines.coroutineScope {
                      launch { slideX.animateTo(0f, tween(360)) }
                      launch { slideY.animateTo(0f, tween(360)) }
                    }
                  }
                }
                val pieceRes = when (piece) {
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
                Image(
                  painter = painterResource(pieceRes),
                  contentDescription = "Pieza de ajedrez ${piece}",
                  modifier = Modifier
                    .size(cellWidth * 0.88f)
                    .graphicsLayer {
                      translationX = slideX.value * cellWidth.toPx()
                      translationY = slideY.value * cellWidth.toPx()
                      shadowElevation = if (movingTarget) 9f else 2f
                    }
                )
              }
              if (targets.contains(sq)) {
                if (piece == '.') {
                  Box(modifier = Modifier.size(cellWidth * 0.32f).clip(CircleShape).background(Color(0x99222233)))
                } else {
                  Box(modifier = Modifier.fillMaxSize().border(BorderStroke(3.dp, Color(0xFFFF3B5C))))
                }
              }
            }
          }
        }
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
          modifier = Modifier.size(58.dp).clip(RoundedCornerShape(14.dp)).background(Color(0x33FFFFFF))
            .border(BorderStroke(1.dp, GoldYellow), RoundedCornerShape(14.dp))
            .clickable {
              commit(ChMove(pendingPromo.first, pendingPromo.second, ch))
              promo = null
            },
          contentAlignment = Alignment.Center
        ) {
          val promoRes = when (ch.lowercaseChar()) {
            'q' -> if (whiteTurn) R.drawable.chess_blancas_piezas_05 else R.drawable.chess_negras_piezas_05
            'r' -> if (whiteTurn) R.drawable.chess_blancas_piezas_01 else R.drawable.chess_negras_piezas_01
            'b' -> if (whiteTurn) R.drawable.chess_blancas_piezas_03 else R.drawable.chess_negras_piezas_03
            else -> if (whiteTurn) R.drawable.chess_blancas_piezas_02 else R.drawable.chess_negras_piezas_02
          }
          Image(painter = painterResource(promoRes), contentDescription = "Elegir pieza", modifier = Modifier.size(42.dp))
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
        c.vm.publishGame(
          "chess", mapOf(
            "board" to boardStr, "castle" to castle, "ep" to ep, "half" to half, "wt" to whiteTurn,
            "white" to whiteUid, "status" to "resign", "winner" to other, "last" to last,
            "scoreA" to s.first, "scoreB" to s.second, "round" to round
          )
        )
      })
    }
    GradientButton(
      "Nueva partida ♟", modifier = Modifier.weight(1f), height = 46.dp,
      onClick = {
        // Cambian los colores en cada partida nueva.
        c.vm.publishGame(
          "chess", mapOf(
            "board" to CH_START, "castle" to "KQkq", "ep" to -1, "half" to 0, "wt" to true,
            "white" to blackUid, "status" to "", "winner" to "", "last" to emptyList<Int>(),
            "scoreA" to scoreA, "scoreB" to scoreB, "round" to round + 1
          )
        )
      }
    )
  }
}
