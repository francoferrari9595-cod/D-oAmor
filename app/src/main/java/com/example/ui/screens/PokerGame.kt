package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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

internal fun rank5(cs: List<Int>): Long {
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

internal fun bestOf7(cs: List<Int>): Long {
  var best = -1L
  for (i in 0 until 7) for (j in i + 1 until 7) {
    val five = cs.filterIndexed { idx, _ -> idx != i && idx != j }
    val r = rank5(five)
    if (r > best) best = r
  }
  return best
}

internal fun handName(score: Long): String {
  var s = score
  repeat(5) { s /= 13 }
  return HAND_NAMES[s.toInt().coerceIn(0, 8)]
}

private const val START_STACK = 500
private const val SB = 10
private const val BB = 20
private const val RAISE = 40

@Composable
private fun PokerCardImage(card: Int, hidden: Boolean = false) {
  val context = LocalContext.current
  val imageId = if (!hidden && card in 0..51) context.resources.getIdentifier(
    "poker_card_%02d".format(card), "drawable", context.packageName
  ) else 0
  Box(
    modifier = Modifier.width(44.dp).aspectRatio(0.70f)
      .clip(RoundedCornerShape(6.dp))
      .background(if (hidden || imageId == 0) Color(0xFF24345E) else Color.White)
      .border(1.dp, Color(0x66000000), RoundedCornerShape(6.dp)),
    contentAlignment = Alignment.Center
  ) {
    if (hidden || imageId == 0) {
      Text("🂠", fontSize = 22.sp, color = Color.White)
    } else {
      Image(
        painter = painterResource(imageId),
        contentDescription = "Carta de póker",
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.FillBounds
      )
    }
  }
}

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
  var showRaiseSlider by remember { mutableStateOf(false) }
  var customRaise by remember { mutableStateOf(RAISE.toFloat()) }
  val handNo = n("hand")
  val settled = (st?.get("settled") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
  val myIdx = c.myIdx
  val oppIdx = 1 - myIdx
  // Las acciones que crean una mano deben salir de un solo dispositivo para evitar
  // que dos escrituras simultáneas repartan mazos distintos en Firestore.
  val isTableHost = c.me.isNotBlank() && c.me == c.hostUid
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
    var allIn = stk[0] == 0 || stk[1] == 0
    // Si alguien queda all-in con una apuesta menor, la parte no igualada se devuelve:
    // el rival no puede ganar fichas que el jugador all-in no tenía para cubrir.
    if (allIn && bt[0] != bt[1]) {
      val matched = minOf(bt[0], bt[1])
      for (i in 0..1) {
        val unmatched = bt[i] - matched
        if (unmatched > 0) stk[i] += unmatched
        bt[i] = matched
      }
      m["st0"] = stk[0]; m["st1"] = stk[1]; m["b0"] = bt[0]; m["b1"] = bt[1]
      // La devolución puede dejar fichas detrás del jugador; en ese caso no se debe
      // forzar el showdown y hay que permitir que el rival decida si iguala.
      allIn = stk[0] == 0 || stk[1] == 0
    }
    val equalBets = bt[0] == bt[1]
    if (!(equalBets && ac[0] && ac[1]) && !(allIn && equalBets)) {
      m["turn"] = nextTurnIfContinue
      m["st0"] = stk[0]; m["st1"] = stk[1]; m["b0"] = bt[0]; m["b1"] = bt[1]; m["a0"] = ac[0]; m["a1"] = ac[1]
      save(m)
      return
    }
    val newPot = pot + bt[0] + bt[1]
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
      // En heads-up el botón/SB actúa primero pre-flop y también después del flop.
      m["turn"] = dealer
      m["msg"] = listOf("Pre-flop", "Flop", "Turn", "River")[newStreet.coerceIn(0, 3)]
    }
    save(m)
  }

  // El inicio compartido de la sala también reparte la primera mano de póker;
  // el anfitrión no necesita confirmar dos veces dentro de la pantalla del juego.
  LaunchedEffect(c.lobbyStarted, started, isTableHost) {
    if (c.lobbyStarted && !started && isTableHost) deal(0, START_STACK, START_STACK, 0)
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

  fun raise(extraRaise: Int = RAISE) {
    if (!myTurn) return
    val m = base()
    val stk = stacks.toMutableList(); val bt = bets.toMutableList(); val ac = acted.toMutableList()
    val amount = minOf(toCall + extraRaise.coerceAtLeast(0), stk[myIdx])
    stk[myIdx] -= amount; bt[myIdx] += amount; ac[myIdx] = true; ac[oppIdx] = false
    m["msg"] = "${c.nameOfIdx(myIdx)} sube a ${bt[myIdx]}"
    showdownOrAdvance(m, stk, bt, ac, oppIdx)
  }

  // Al terminar el match, cada quien cobra o paga su diferencia contra el stack inicial.
  // Evita que recomposiciones repetidas acrediten dos veces el mismo resultado.
  // El marcador remoto sigue siendo la fuente persistente; esta guarda cubre la ventana
  // entre la primera escritura y la confirmación de Firestore en el dispositivo actual.
  var settlementRequested by remember { mutableStateOf(false) }
  LaunchedEffect(matchOver, handNo, c.me, settled) {
    if (!matchOver) settlementRequested = false
    if (matchOver && c.me.isNotBlank() && !settled.contains(c.me) && !settlementRequested) {
      settlementRequested = true
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
    if (c.players.contains(c.me)) {
      GradientButton("Sentarse y repartir", modifier = Modifier.fillMaxWidth(), onClick = { deal(0, START_STACK, START_STACK, 0) })
    } else {
      Text("Esperando que el anfitrión reparta la primera mano…", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
    }
    return
  }

  // Mesa 3D: fieltro inclinado con jugadores sentados
  val tableT by rememberTableClock()
  Box(modifier = Modifier.fillMaxWidth().height((gameAvail() * 0.40f).coerceIn(220.dp, 420.dp))) {
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
        drawLuxuryOval(tableT)
        drawChipStacks(w * 0.5f, h * 0.72f, pot + bets[0] + bets[1])
      }
      // Rival arriba
      Column(modifier = Modifier.align(Alignment.TopCenter).padding(top = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          AvatarView(partnerPhoto, "🙂", 36.dp)
          Text("${c.nameOfIdx(oppIdx)} · ${stacks[oppIdx]}${if (dealer == oppIdx) " 🔘" else ""}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.padding(top = 4.dp)) {
          val reveal = over && winner != -1 && board.size == 5
          hole[oppIdx].forEach { cd -> PokerCardImage(cd, hidden = !reveal) }
        }
        if (bets[oppIdx] > 0) Text("apuesta ${bets[oppIdx]}", color = Color(0xFFFFE9A8), fontSize = 11.sp)
      }
      // Centro: bote y cartas comunitarias
      Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Bote ${pot + bets[0] + bets[1]} · ${listOf("Pre-flop", "Flop", "Turn", "River", "Showdown")[street.coerceIn(0, 4)]}", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 6.dp)) {
          for (i in 0 until 5) {
            if (i < board.size) PokerCardImage(board[i])
            else PokerCardImage(-1, hidden = true)
          }
        }
        Text(msg, color = Color(0xFFD9F5E3), fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
      }
      // Yo abajo
      Column(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (bets[myIdx] > 0) Text("apuesta ${bets[myIdx]}", color = Color(0xFFFFE9A8), fontSize = 11.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 4.dp)) {
          hole[myIdx].forEach { cd -> PokerCardImage(cd) }
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
      if (c.players.contains(c.me)) {
        GradientButton("Nueva mesa", modifier = Modifier.fillMaxWidth(), onClick = { deal(0, START_STACK, START_STACK, 0) })
      } else {
        Text("Esperando que el anfitrión abra una nueva mesa…", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
      }
    } else {
      TurnBanner(if (winner == 2) msg else "🏆 $msg", false)
      Spacer(Modifier.height(6.dp))
      if (c.players.contains(c.me)) {
        GradientButton("Siguiente mano", modifier = Modifier.fillMaxWidth(), onClick = { deal(1 - dealer, stacks[0], stacks[1], handNo) })
        Spacer(Modifier.height(6.dp))
        SoftButton("Cerrar mesa y cobrar", modifier = Modifier.fillMaxWidth(), onClick = {
          val m = base()
          m["closed"] = true
          save(m)
        })
      } else {
        Text("Esperando al anfitrión para repartir la próxima mano o cerrar la mesa…", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
      }
    }
  } else {
    TurnBanner(if (myTurn) "Tu turno" else "Turno de ${c.nameOfIdx(turn)}…", myTurn)
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
      SoftButton("Retirarme", onClick = { fold() }, enabled = myTurn, modifier = Modifier.weight(1f))
      GradientButton(if (toCall == 0) "Pasar" else "Igualar $toCall", onClick = { checkCall() }, enabled = myTurn, modifier = Modifier.weight(1.2f))
      Box(
        modifier = Modifier.weight(1.2f).clip(RoundedCornerShape(14.dp))
          .background(Brush.horizontalGradient(listOf(Color(0xFF5B2CFF), Color(0xFF00D9FF))))
          .border(1.dp, Color(0xFF70F7FF), RoundedCornerShape(14.dp))
          .pointerInput(myTurn, toCall, stacks, bets) {
            detectTapGestures(
              onTap = {
                if (myTurn && stacks[myIdx] > toCall) {
                  val quickExtra = maxOf(RAISE, bets[oppIdx] * 2 - bets[myIdx] - toCall)
                  raise(quickExtra)
                }
              },
              onLongPress = {
                if (myTurn && stacks[myIdx] > toCall) {
                  customRaise = maxOf(RAISE, stacks[myIdx] - toCall).toFloat()
                  showRaiseSlider = true
                }
              }
            )
          }.padding(horizontal = 8.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
      ) {
        Text("Subir · mantener", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, textAlign = TextAlign.Center)
      }
    }
    if (showRaiseSlider && myTurn) {
      GlassCard(modifier = Modifier.fillMaxWidth(), highlight = true) {
        Text("✨ Subida personalizada: ${minOf(toCall + customRaise.toInt(), stacks[myIdx])} fichas", color = Color.White, fontWeight = FontWeight.Bold)
        // Control de subida con halo cónico/neón; el máximo nunca supera el stack disponible.
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .background(
              Brush.sweepGradient(
                listOf(Color(0xFF00E5FF), Color(0xFF8A2BE2), Color(0xFFFF2BD6), Color(0xFF00E5FF))
              ),
              RoundedCornerShape(18.dp)
            )
            .padding(2.dp)
            .background(Color(0xFF07121F), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
          Slider(
            value = customRaise.coerceIn(
              RAISE.toFloat(), maxOf(RAISE, stacks[myIdx] - toCall).toFloat()
            ),
            onValueChange = { customRaise = it.coerceIn(RAISE.toFloat(), maxOf(RAISE, stacks[myIdx] - toCall).toFloat()) },
            valueRange = RAISE.toFloat()..maxOf(RAISE, stacks[myIdx] - toCall).toFloat()
          )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
          SoftButton("Cancelar", onClick = { showRaiseSlider = false }, modifier = Modifier.weight(1f))
          GradientButton("Apostar ${minOf(toCall + customRaise.toInt(), stacks[myIdx])}", onClick = {
            raise(customRaise.toInt()); showRaiseSlider = false
          }, modifier = Modifier.weight(1.4f), enabled = myTurn && stacks[myIdx] > toCall)
        }
      }
    }
  }
}
