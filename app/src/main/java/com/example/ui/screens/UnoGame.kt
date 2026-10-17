package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.GlassCard
import com.example.ui.components.GradientButton
import com.example.ui.components.SoftButton

// Carta = color*20 + tipo. Colores 0..3 (rojo, amarillo, verde, azul), 4 = comodín.
// Tipos: 0-9 número, 10 salto, 11 reversa, 12 +2, 13 comodín, 14 comodín +4.
private val UNO_COLORS = listOf(Color(0xFFE53935), Color(0xFFFDD835), Color(0xFF43A047), Color(0xFF1E88E5))
private val UNO_COLOR_NAMES = listOf("Rojo", "Amarillo", "Verde", "Azul")

private fun unoColor(card: Int) = card / 20
private fun unoType(card: Int) = card % 20

private fun unoDeck(): List<Int> {
  val d = mutableListOf<Int>()
  for (c in 0..3) {
    d.add(c * 20)
    for (t in 1..12) { d.add(c * 20 + t); d.add(c * 20 + t) }
  }
  repeat(4) { d.add(4 * 20 + 13); d.add(4 * 20 + 14) }
  return d.shuffled()
}

private fun unoSymbol(card: Int): String = when (val t = unoType(card)) {
  in 0..9 -> "$t"
  10 -> "⊘"
  11 -> "⇄"
  12 -> "+2"
  13 -> "🌈"
  else -> "+4"
}

private fun unoPoints(card: Int): Int = when (val t = unoType(card)) {
  in 0..9 -> t
  in 10..12 -> 20
  else -> 50
}

@Composable
private fun UnoCardView(card: Int, playable: Boolean, onClick: () -> Unit) {
  val wild = unoColor(card) == 4
  val bg = if (wild) Brush.linearGradient(listOf(Color(0xFFE53935), Color(0xFFFDD835), Color(0xFF43A047), Color(0xFF1E88E5)))
  else Brush.linearGradient(listOf(UNO_COLORS[unoColor(card)], UNO_COLORS[unoColor(card)].copy(alpha = 0.75f)))
  Box(
    modifier = Modifier
      .offset(y = if (playable) (-8).dp else 0.dp)
      .size(width = 56.dp, height = 82.dp)
      .clip(RoundedCornerShape(10.dp))
      .background(bg)
      .border(BorderStroke(3.dp, if (playable) Color.White else Color(0x66FFFFFF)), RoundedCornerShape(10.dp))
      .clickable(enabled = playable) { onClick() },
    contentAlignment = Alignment.Center
  ) {
    Box(modifier = Modifier.size(38.dp).clip(CircleShape).background(Color(0x33FFFFFF)), contentAlignment = Alignment.Center) {
      Text(unoSymbol(card), color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
    }
  }
}

@Composable
internal fun UnoGame(c: GameCtx) {
  val flow = remember { c.vm.gameState("uno") }
  val st by flow.collectAsState(initial = null)
  fun ints(k: String): List<Int> = (st?.get(k) as? List<*>)?.mapNotNull { (it as? Number)?.toInt() } ?: emptyList()
  fun n(k: String, d: Int = 0): Int = (st?.get(k) as? Number)?.toInt() ?: d
  val started = (st?.get("disc") as? List<*>)?.isNotEmpty() == true
  val deck = ints("deck")
  val disc = ints("disc")
  val hands = listOf(ints("h0"), ints("h1"))
  val turn = n("turn")
  val curColor = n("color")
  val winner = n("winner", -1)
  val drew = (st?.get("drew") as? Boolean) ?: false
  val unoFlag = listOf((st?.get("u0") as? Boolean) ?: false, (st?.get("u1") as? Boolean) ?: false)
  val scores = listOf(n("sc0"), n("sc1"))
  val msg = (st?.get("msg") as? String) ?: ""
  val me = c.myIdx
  val opp = 1 - me
  val myTurn = started && winner < 0 && turn == me
  var pickWild by remember { mutableStateOf<Int?>(null) }

  fun playable(card: Int): Boolean {
    if (disc.isEmpty()) return false
    val top = disc.last()
    return unoColor(card) == 4 || unoColor(card) == curColor || (unoType(card) == unoType(top) && unoColor(top) != 4)
  }

  fun save(h0: List<Int>, h1: List<Int>, d: List<Int>, dc: List<Int>, t: Int, col: Int, w: Int, dr: Boolean, u0: Boolean, u1: Boolean, m: String, s0: Int = scores[0], s1: Int = scores[1]) {
    c.vm.publishGame("uno", mapOf(
      "deck" to d, "disc" to dc, "h0" to h0, "h1" to h1, "turn" to t, "color" to col, "winner" to w, "drew" to dr,
      "u0" to u0, "u1" to u1, "msg" to m, "sc0" to s0, "sc1" to s1
    ))
  }

  fun deal(keepScores: Boolean) {
    val d = unoDeck().toMutableList()
    val h0 = List(7) { d.removeAt(0) }
    val h1 = List(7) { d.removeAt(0) }
    var first = d.removeAt(0)
    while (unoColor(first) == 4 || unoType(first) > 9) { d.add(first); first = d.removeAt(0) }
    save(h0, h1, d, listOf(first), 0, unoColor(first), -1, false, false, false, "¡Empieza la partida!",
      if (keepScores) scores[0] else 0, if (keepScores) scores[1] else 0)
    c.vm.gameSfx(1)
  }

  fun drawFrom(dk: MutableList<Int>, dc: MutableList<Int>, count: Int): List<Int> {
    val out = mutableListOf<Int>()
    repeat(count) {
      if (dk.isEmpty() && dc.size > 1) {
        val top = dc.removeAt(dc.lastIndex)
        dk.addAll(dc.shuffled()); dc.clear(); dc.add(top)
      }
      if (dk.isNotEmpty()) out.add(dk.removeAt(0))
    }
    return out
  }

  fun play(card: Int, chosen: Int) {
    if (!myTurn || !playable(card)) return
    val dk = deck.toMutableList(); val dc = disc.toMutableList()
    val mine = hands[me].toMutableList(); val theirs = hands[opp].toMutableList()
    mine.remove(card); dc.add(card)
    val t = unoType(card)
    val newColor = if (unoColor(card) == 4) chosen else unoColor(card)
    var nextTurn = opp
    var text = "${c.nameOfIdx(me)} jugó ${unoSymbol(card)}"
    when (t) {
      10, 11 -> { nextTurn = me; text += " · ${c.nameOfIdx(opp)} pierde el turno" }
      12 -> { theirs.addAll(drawFrom(dk, dc, 2)); nextTurn = me; text += " · ${c.nameOfIdx(opp)} roba 2" }
      14 -> { theirs.addAll(drawFrom(dk, dc, 4)); nextTurn = me; text += " · ${c.nameOfIdx(opp)} roba 4" }
    }
    var myUno = unoFlag[me]
    if (mine.size == 1 && !myUno) { mine.addAll(drawFrom(dk, dc, 2)); text += " · ¡no dijo UNO! roba 2" }
    if (mine.size != 1) myUno = false
    val h0 = if (me == 0) mine else theirs
    val h1 = if (me == 1) mine else theirs
    if (mine.isEmpty()) {
      val pts = theirs.sumOf { unoPoints(it) }
      val s0 = scores[0] + if (me == 0) pts else 0
      val s1 = scores[1] + if (me == 1) pts else 0
      save(h0, h1, dk, dc, nextTurn, newColor, me, false, false, false, "🏆 ${c.nameOfIdx(me)} se quedó sin cartas (+$pts)", s0, s1)
      c.vm.gameSfx(2)
    } else {
      save(h0, h1, dk, dc, nextTurn, newColor, -1, false, if (me == 0) myUno else unoFlag[0], if (me == 1) myUno else unoFlag[1], text)
      c.vm.gameSfx(0)
    }
    pickWild = null
  }

  fun drawOne() {
    if (!myTurn || drew) return
    val dk = deck.toMutableList(); val dc = disc.toMutableList()
    val mine = hands[me].toMutableList()
    val got = drawFrom(dk, dc, 1)
    mine.addAll(got)
    val canPlay = got.isNotEmpty() && playable(got[0])
    val h0 = if (me == 0) mine else hands[0]
    val h1 = if (me == 1) mine else hands[1]
    save(h0, h1, dk, dc, if (canPlay) me else opp, curColor, -1, canPlay, unoFlag[0], unoFlag[1], "${c.nameOfIdx(me)} robó una carta")
    c.vm.gameSfx(0)
  }

  fun pass() {
    if (!myTurn || !drew) return
    save(hands[0], hands[1], deck, disc, opp, curColor, -1, false, unoFlag[0], unoFlag[1], "${c.nameOfIdx(me)} pasó")
  }

  fun sayUno() {
    if (!myTurn || hands[me].size > 2) return
    save(hands[0], hands[1], deck, disc, turn, curColor, -1, drew,
      if (me == 0) true else unoFlag[0], if (me == 1) true else unoFlag[1], "🔔 ¡${c.nameOfIdx(me)} dijo UNO!")
    c.vm.gameSfx(1)
  }

  if (!started) {
    GlassCard(modifier = Modifier.fillMaxWidth(), highlight = true) {
      Text("🃏 UNO", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
      Text(
        "Gana quien se quede sin cartas. Juega por color, número o símbolo. Salto y reversa hacen perder el turno; +2 y +4 hacen robar; el comodín cambia el color. Con 2 cartas pulsa ¡UNO! antes de jugar. Gana la partida quien llegue a 500 puntos.",
        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp
      )
    }
    Spacer(Modifier.height(10.dp))
    GradientButton("Repartir 7 cartas", modifier = Modifier.fillMaxWidth(), onClick = { deal(false) })
    return
  }

  ScoreBoard(c, scores[0], scores[1], if (winner < 0) c.uidAt(turn) else null, "🃏", "🃏")
  Spacer(Modifier.height(8.dp))
  GlassCard(modifier = Modifier.fillMaxWidth(), corner = 18.dp) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
      Box(contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.size(width = 56.dp, height = 82.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF111111)).border(BorderStroke(3.dp, Color.White), RoundedCornerShape(10.dp)).clickable(enabled = myTurn && !drew) { drawOne() }, contentAlignment = Alignment.Center) {
          Text("UNO", color = Color(0xFFE53935), fontWeight = FontWeight.Black, fontSize = 16.sp)
        }
      }
      if (disc.isNotEmpty()) UnoCardView(disc.last(), false) {}
      Column {
        Text("Color actual", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(modifier = Modifier.size(18.dp).clip(CircleShape).background(UNO_COLORS[curColor.coerceIn(0, 3)]))
          Text("  ${UNO_COLOR_NAMES[curColor.coerceIn(0, 3)]}", color = Color.White, fontWeight = FontWeight.Bold)
        }
        Text("Mazo: ${deck.size}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
      }
    }
    Text(msg, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
  }
  Spacer(Modifier.height(8.dp))
  Text("${c.nameOfIdx(opp)}: ${hands[opp].size} cartas${if (unoFlag[opp] && hands[opp].size == 1) " · ¡UNO!" else ""}", color = Color.White, fontWeight = FontWeight.Bold)
  Row(horizontalArrangement = Arrangement.spacedBy((-30).dp), modifier = Modifier.padding(vertical = 6.dp)) {
    repeat(hands[opp].size.coerceAtMost(12)) {
      Box(modifier = Modifier.size(width = 40.dp, height = 58.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF111111)).border(BorderStroke(2.dp, Color.White), RoundedCornerShape(8.dp)))
    }
  }
  if (winner >= 0) {
    ResultBanner("🏆 ¡Ganó ${c.nameOfIdx(winner)}! ${if (scores[winner] >= 500) "(¡llegó a 500 puntos!)" else ""}")
    Spacer(Modifier.height(8.dp))
    GradientButton("Siguiente ronda", modifier = Modifier.fillMaxWidth(), onClick = { deal(scores[winner] < 500) })
  } else {
    TurnBanner(if (myTurn) (if (drew) "Juega la carta robada o pasa" else "Tu turno: juega una carta o roba") else "Turno de ${c.nameOfIdx(turn)}…", myTurn)
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
      GradientButton("¡UNO!", onClick = { sayUno() }, enabled = myTurn && hands[me].size <= 2 && !unoFlag[me], modifier = Modifier.weight(1f))
      SoftButton("Robar", onClick = { drawOne() }, enabled = myTurn && !drew, modifier = Modifier.weight(1f))
      SoftButton("Pasar", onClick = { pass() }, enabled = myTurn && drew, modifier = Modifier.weight(1f))
    }
  }
  Spacer(Modifier.height(10.dp))
  val chosen = pickWild
  if (chosen != null) {
    Text("Elige el nuevo color:", color = Color.White, fontWeight = FontWeight.Bold)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 6.dp)) {
      for (col in 0..3) {
        Box(modifier = Modifier.size(54.dp).clip(CircleShape).background(UNO_COLORS[col]).border(BorderStroke(3.dp, Color.White), CircleShape).clickable { play(chosen, col) })
      }
    }
  }
  Text("Tu mano (${hands[me].size})", color = Color.White, fontWeight = FontWeight.Bold)
  Row(
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 14.dp, bottom = 6.dp)
  ) {
    hands[me].forEach { card ->
      val ok = myTurn && playable(card) && (!drew || card == hands[me].last())
      UnoCardView(card, ok) { if (unoColor(card) == 4) pickWild = card else play(card, unoColor(card)) }
    }
  }
}
