package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import com.example.ui.components.AvatarView
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.GlassCard
import com.example.ui.components.GradientButton
import com.example.ui.components.SoftButton
import com.google.firebase.firestore.FieldValue

// ---------- Evaluador de manos (mejor mano de 5 entre 7 cartas) ----------
private val SUITS = listOf("♠", "♥", "♦", "♣")
private val RANKS = listOf("2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K", "A")
private val HAND_NAMES = listOf("Carta alta", "Pareja", "Doble pareja", "Trío", "Escalera", "Color", "Full", "Póker", "Escalera de color")

private fun cardLabel(c: Int): String = RANKS[c % 13] + SUITS[c / 13]
private fun cardRed(c: Int): Boolean = (c / 13) == 1 || (c / 13) == 2

private fun rank5(cs: List<Int>): Long {
  val ranks = cs.map { it % 13 }
  val flush = cs.map { it / 13 }.distinct().size == 1
  val counts = ranks.groupingBy { it }.eachCount()
  val ordered = counts.entries.sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenByDescending { it.key }).map { it.key }
  val distinct = ranks.distinct().sortedDescending()
  var straightTop = -1
  if (distinct.size == 5) {
    if (distinct[0] - distinct[4] == 4) straightTop = distinct[0]
    else if (distinct == listOf(12, 3, 2, 1, 0)) straightTop = 3
  }
  val shape = counts.values.sortedDescending()
  val cat = when {
    straightTop >= 0 && flush -> 8
    shape == listOf(4, 1) -> 7
    shape == listOf(3, 2) -> 6
    flush -> 5
    straightTop >= 0 -> 4
    shape == listOf(3, 1, 1) -> 3
    shape == listOf(2, 2, 1) -> 2
    shape == listOf(2, 1, 1, 1) -> 1
    else -> 0
  }
  val tie = if (straightTop >= 0) listOf(straightTop) else ordered
  var score = cat.toLong()
  for (i in 0 until 5) score = score * 13 + (tie.getOrElse(i) { 0 }).toLong()
  return score
}

private fun bestOf7(cs: List<Int>): Long {
  var best = -1L
  for (i in 0 until 7) for (j in i + 1 until 7) {
    val five = cs.filterIndexed { idx, _ -> idx != i && idx != j }
    val r = rank5(five)
    if (r > best) best = r
  }
  return best
}

private fun handName(score: Long): String {
  var s = score
  repeat(5) { s /= 13 }
  return HAND_NAMES[s.toInt().coerceIn(0, 8)]
}

private const val START_STACK = 500
private const val SB = 10
private const val BB = 20
private const val RAISE = 40

@Composable
internal fun PokerGame(c: GameCtx) {
  val flow = remember { c.vm.gameState("poker") }
  val st by flow.collectAsState(initial = null)
  val started = (st?.get("deck") as? List<*>)?.isNotEmpty() == true
  fun ints(k: String): List<Int> = (st?.get(k) as? List<*>)?.mapNotNull { (it as? Number)?.toInt() } ?: emptyList()
  fun n(k: String, d: Int = 0): Int = (st?.get(k) as? Number)?.toInt() ?: d
  val deck = ints("deck")
  val hole = listOf(ints("h0"), ints("h1"))
  val board = ints("board")
  val stacks = listOf(n("st0", START_STACK), n("st1", START_STACK))
  val bets = listOf(n("b0"), n("b1"))
  val pot = n("pot")
  val street = n("street")
  val turn = n("turn")
  val dealer = n("dealer")
  val acted = listOf((st?.get("a0") as? Boolean) ?: false, (st?.get("a1") as? Boolean) ?: false)
  val over = (st?.get("over") as? Boolean) ?: false
  val closed = (st?.get("closed") as? Boolean) ?: false
  val winner = n("winner", -1)
  val msg = (st?.get("msg") as? String) ?: ""
  val handNo = n("hand")
  val settled = (st?.get("settled") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
  val myIdx = c.myIdx
  val oppIdx = 1 - myIdx
  val myPhoto by c.vm.myPhoto.collectAsState()
  val partnerPhoto by c.vm.partnerPhoto.collectAsState()
  val myTurn = started && !over && turn == myIdx
  val matchOver = started && over && (stacks[0] <= 0 || stacks[1] <= 0 || closed)
  val toCall = (bets[oppIdx] - bets[myIdx]).coerceAtLeast(0).coerceAtMost(stacks[myIdx])

  fun save(m: Map<String, Any?>) = c.vm.publishGame("poker", m)

  fun base(): MutableMap<String, Any?> = mutableMapOf(
    "deck" to deck, "h0" to hole[0], "h1" to hole[1], "board" to board, "st0" to stacks[0], "st1" to stacks[1],
    "b0" to bets[0], "b1" to bets[1], "pot" to pot, "street" to street, "turn" to turn, "dealer" to dealer,
    "a0" to acted[0], "a1" to acted[1], "over" to over, "winner" to winner, "msg" to msg, "hand" to handNo, "closed" to closed
  )

  fun deal(newDealer: Int, s0: Int, s1: Int, hand: Int) {
    val d = (0 until 52).shuffled()
    val sbIdx = newDealer
    val bbIdx = 1 - newDealer
    val st2 = mutableListOf(s0, s1)
    val bt = mutableListOf(0, 0)
    val sbAmt = minOf(SB, st2[sbIdx]); st2[sbIdx] -= sbAmt; bt[sbIdx] = sbAmt
    val bbAmt = minOf(BB, st2[bbIdx]); st2[bbIdx] -= bbAmt; bt[bbIdx] = bbAmt
    save(mapOf(
      "deck" to d, "h0" to listOf(d[0], d[2]), "h1" to listOf(d[1], d[3]), "board" to emptyList<Int>(),
      "st0" to st2[0], "st1" to st2[1], "b0" to bt[0], "b1" to bt[1], "pot" to 0, "street" to 0,
      "turn" to sbIdx, "dealer" to newDealer, "a0" to false, "a1" to false, "over" to false, "winner" to -1,
      "msg" to "Mano ${hand + 1}: reparto hecho", "hand" to hand + 1, "closed" to false, "settled" to emptyList<String>()
    ))
    c.vm.gameSfx(1)
  }

  fun showdownOrAdvance(m: MutableMap<String, Any?>, stk: MutableList<Int>, bt: MutableList<Int>, ac: MutableList<Boolean>, nextTurnIfContinue: Int) {
    val allIn = stk[0] == 0 || stk[1] == 0
    val equalBets = bt[0] == bt[1]
    if (!(equalBets && ac[0] && ac[1]) && !(allIn && equalBets)) {
      m["turn"] = nextTurnIfContinue
      m["st0"] = stk[0]; m["st1"] = stk[1]; m["b0"] = bt[0]; m["b1"] = bt[1]; m["a0"] = ac[0]; m["a1"] = ac[1]
      save(m)
      return
    }
    var newPot = pot + bt[0] + bt[1]
    var newStreet = street + 1
    if (allIn) newStreet = 4
    val shownBoard = when (newStreet) { 1 -> 3; 2 -> 4; else -> 5 }
    val boardCards = listOf(deck[4], deck[5], deck[6], deck[7], deck[8]).take(shownBoard)
    m["pot"] = newPot; m["b0"] = 0; m["b1"] = 0; m["a0"] = false; m["a1"] = false
    m["st0"] = stk[0]; m["st1"] = stk[1]; m["board"] = boardCards; m["street"] = newStreet
    if (newStreet >= 4) {
      val s0 = bestOf7(hole[0] + boardCards)
      val s1 = bestOf7(hole[1] + boardCards)
      val w = if (s0 > s1) 0 else if (s1 > s0) 1 else 2
      if (w == 0) m["st0"] = stk[0] + newPot else if (w == 1) m["st1"] = stk[1] + newPot
      else { m["st0"] = stk[0] + newPot / 2; m["st1"] = stk[1] + newPot - newPot / 2 }
      m["over"] = true; m["winner"] = w; m["pot"] = 0
      m["msg"] = if (w == 2) "Empate (${handName(s0)})" else "${c.nameOfIdx(w)} gana con ${handName(if (w == 0) s0 else s1)}"
      m["turn"] = -1
      c.vm.gameSfx(2)
    } else {
      m["turn"] = 1 - dealer
      m["msg"] = listOf("Pre-flop", "Flop", "Turn", "River")[newStreet.coerceIn(0, 3)]
    }
    save(m)
  }

  fun fold() {
    if (!myTurn) return
    val m = base()
    val total = pot + bets[0] + bets[1]
    m["st$oppIdx"] = stacks[oppIdx] + total
    m["pot"] = 0; m["b0"] = 0; m["b1"] = 0; m["over"] = true; m["winner"] = oppIdx; m["turn"] = -1
    m["msg"] = "${c.nameOfIdx(myIdx)} se retiró"
    save(m)
  }

  fun checkCall() {
    if (!myTurn) return
    val m = base()
    val stk = stacks.toMutableList(); val bt = bets.toMutableList(); val ac = acted.toMutableList()
    stk[myIdx] -= toCall; bt[myIdx] += toCall; ac[myIdx] = true
    m["msg"] = if (toCall == 0) "${c.nameOfIdx(myIdx)} pasa" else "${c.nameOfIdx(myIdx)} iguala $toCall"
    showdownOrAdvance(m, stk, bt, ac, oppIdx)
  }

  fun raise() {
    if (!myTurn) return
    val m = base()
    val stk = stacks.toMutableList(); val bt = bets.toMutableList(); val ac = acted.toMutableList()
    val amount = minOf(toCall + RAISE, stk[myIdx])
    stk[myIdx] -= amount; bt[myIdx] += amount; ac[myIdx] = true; ac[oppIdx] = false
    m["msg"] = "${c.nameOfIdx(myIdx)} sube a ${bt[myIdx]}"
    showdownOrAdvance(m, stk, bt, ac, oppIdx)
  }

  // Al terminar el match, cada quien cobra o paga su diferencia contra el stack inicial.
  LaunchedEffect(matchOver, handNo) {
    if (matchOver && !settled.contains(c.me)) {
      c.vm.addCredits((stacks[myIdx] - START_STACK).toLong())
      c.vm.patchGame("poker", mapOf("settled" to FieldValue.arrayUnion(c.me)))
    }
  }

  if (!started) {
    GlassCard(modifier = Modifier.fillMaxWidth(), highlight = true) {
      Text("♠️ Póker Texas Hold'em (1 contra 1)", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
      Text("Cada uno empieza con $START_STACK puntos de mesa. Gana quien se quede con todos. Ciegas $SB/$BB, subidas de $RAISE.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    }
    Spacer(Modifier.height(10.dp))
    GradientButton("Sentarse y repartir", modifier = Modifier.fillMaxWidth(), onClick = { deal(0, START_STACK, START_STACK, 0) })
    return
  }

  // Mesa 3D: fieltro inclinado con jugadores sentados
  Box(modifier = Modifier.fillMaxWidth().height(400.dp)) {
    Box(
      modifier = Modifier.fillMaxSize().graphicsLayer {
        rotationX = 22f
        cameraDistance = 16f * density
        transformOrigin = TransformOrigin(0.5f, 0.9f)
      }
    ) {
      Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        drawOval(Color(0x66000000), Offset(w * 0.02f, h * 0.10f), Size(w * 0.96f, h * 0.86f))
        drawOval(Brush.verticalGradient(listOf(Color(0xFF8B5A2B), Color(0xFF3B2410))), Offset(0f, h * 0.06f), Size(w, h * 0.86f))
        drawOval(Brush.radialGradient(listOf(Color(0xFF1FA35C), Color(0xFF0B5C34), Color(0xFF06351F)), center = Offset(w * 0.5f, h * 0.45f), radius = w * 0.62f), Offset(w * 0.05f, h * 0.11f), Size(w * 0.90f, h * 0.76f))
        drawOval(Color(0x33FFFFFF), Offset(w * 0.12f, h * 0.17f), Size(w * 0.76f, h * 0.64f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
      }
      // Rival arriba
      Column(modifier = Modifier.align(Alignment.TopCenter).padding(top = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          AvatarView(partnerPhoto, "🙂", 36.dp)
          Text("${c.nameOfIdx(oppIdx)} · ${stacks[oppIdx]}${if (dealer == oppIdx) " 🔘" else ""}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.padding(top = 4.dp)) {
          val reveal = over && winner != -1 && board.size == 5
          hole[oppIdx].forEach { cd -> PlayingCardView(cardLabel(cd), cardRed(cd), hidden = !reveal) }
        }
        if (bets[oppIdx] > 0) Text("apuesta ${bets[oppIdx]}", color = Color(0xFFFFE9A8), fontSize = 11.sp)
      }
      // Centro: bote y cartas comunitarias
      Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Bote ${pot + bets[0] + bets[1]} · ${listOf("Pre-flop", "Flop", "Turn", "River", "Showdown")[street.coerceIn(0, 4)]}", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 6.dp)) {
          for (i in 0 until 5) {
            if (i < board.size) PlayingCardView(cardLabel(board[i]), cardRed(board[i]))
            else PlayingCardView("", false, hidden = true)
          }
        }
        Text(msg, color = Color(0xFFD9F5E3), fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
      }
      // Yo abajo
      Column(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (bets[myIdx] > 0) Text("apuesta ${bets[myIdx]}", color = Color(0xFFFFE9A8), fontSize = 11.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 4.dp)) {
          hole[myIdx].forEach { cd -> PlayingCardView(cardLabel(cd), cardRed(cd)) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          AvatarView(myPhoto, "🙂", 36.dp)
          Text("Tú · ${stacks[myIdx]}${if (dealer == myIdx) " 🔘" else ""}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
      }
    }
  }
  Spacer(Modifier.height(8.dp))
  if (over) {
    if (matchOver) {
      ResultBanner(if (closed) "🏆 Mesa cerrada: ${stacks[0]} vs ${stacks[1]}" else "🏆 ¡${c.nameOfIdx(if (stacks[0] > 0) 0 else 1)} se llevó la mesa!")
      Spacer(Modifier.height(6.dp))
      GradientButton("Nueva mesa", modifier = Modifier.fillMaxWidth(), onClick = { deal(0, START_STACK, START_STACK, 0) })
    } else {
      TurnBanner(if (winner == 2) msg else "🏆 $msg", false)
      Spacer(Modifier.height(6.dp))
      GradientButton("Siguiente mano", modifier = Modifier.fillMaxWidth(), onClick = { deal(1 - dealer, stacks[0], stacks[1], handNo) })
      Spacer(Modifier.height(6.dp))
      SoftButton("Cerrar mesa y cobrar", modifier = Modifier.fillMaxWidth(), onClick = {
        val m = base()
        m["closed"] = true
        save(m)
      })
    }
  } else {
    TurnBanner(if (myTurn) "Tu turno" else "Turno de ${c.nameOfIdx(turn)}…", myTurn)
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
      SoftButton("Retirarme", onClick = { fold() }, enabled = myTurn, modifier = Modifier.weight(1f))
      GradientButton(if (toCall == 0) "Pasar" else "Igualar $toCall", onClick = { checkCall() }, enabled = myTurn, modifier = Modifier.weight(1.2f))
      GradientButton("Subir +$RAISE", onClick = { raise() }, enabled = myTurn && stacks[myIdx] > toCall, modifier = Modifier.weight(1.2f))
    }
  }
}
