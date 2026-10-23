package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.GameSfx
import com.example.ui.components.GlassCard
import com.example.ui.components.GradientButton
import com.example.ui.components.SoftButton
import com.example.ui.theme.GoldYellow
import kotlinx.coroutines.delay

// Orden del mazo codificado: oros, copas, espadas, bastos.
private val T_SUITS = listOf("♦", "♥", "♠", "♣")
private fun tSuit(card: Int) = card / 20
private fun tRank(card: Int) = card % 20
private fun tAssetName(card: Int): String { val suit = listOf("oros","copas","espadas","bastos")[tSuit(card).coerceIn(0,3)]; return "truco_${suit}_${tRank(card)}" }
private fun tName(card: Int): String = "${when(tRank(card)){1->"A";2->"2";3->"3";4->"4";5->"5";6->"6";7->"7";10->"S";11->"C";12->"R";else->"?"}}${T_SUITS[tSuit(card).coerceIn(0,3)]}"
internal fun tPower(card: Int): Int = when {
  // Jerarquía argentina: ancho de espadas, ancho de bastos,
  // siete de espadas, siete de oros; no confundir el índice del palo
  // con el símbolo mostrado en pantalla.
  tRank(card)==1 && tSuit(card)==2 -> 14
  tRank(card)==1 && tSuit(card)==3 -> 13
  tRank(card)==7 && tSuit(card)==2 -> 12
  tRank(card)==7 && tSuit(card)==0 -> 11
  tRank(card)==3 -> 10
  tRank(card)==2 -> 9
  tRank(card)==1 -> 8
  tRank(card)==12 -> 7
  tRank(card)==11 -> 6
  tRank(card)==10 -> 5
  tRank(card)==7 -> 4
  tRank(card)==6 -> 3
  tRank(card)==5 -> 2
  else -> 1
}
internal fun tEnvido(hand: List<Int>): Int {
  var best = hand.maxOfOrNull { if (tRank(it) in 10..12) 0 else tRank(it) } ?: 0
  for (i in hand.indices) for (j in i+1 until hand.size) if (tSuit(hand[i])==tSuit(hand[j])) {
    val a=if(tRank(hand[i]) in 10..12) 0 else tRank(hand[i]); val b=if(tRank(hand[j]) in 10..12) 0 else tRank(hand[j])
    best=maxOf(best,20+a+b)
  }
  return best
}
internal fun tDeck() = (0..3).flatMap { suit -> listOf(1,2,3,4,5,6,7,10,11,12).map { suit*20+it } }.shuffled()

/**
 * Resuelve quién gana la mano según las bazas jugadas (-1 = parda). Devuelve -2 si la mano sigue.
 * Reglas: quien gana 2 bazas gana; si la primera es parda decide la segunda (o la tercera);
 * si la primera la ganó un equipo y la segunda es parda, gana el primero; si todo es parda gana la mano.
 */
internal fun trucoHandWinner(tw: List<Int>, manoTeam: Int): Int {
  if (tw.count { it == 0 } >= 2) return 0
  if (tw.count { it == 1 } >= 2) return 1
  if (tw.size == 2) {
    val f = tw[0]; val sc = tw[1]
    if (f == -1 && sc == -1) return -2
    if (f == -1) return sc
    if (sc == -1) return f
    return -2
  }
  if (tw.size >= 3) {
    if (tw[2] != -1) return tw[2]
    if (tw[0] != -1) return tw[0]
    if (tw[1] != -1) return tw[1]
    return manoTeam
  }
  return -2
}

@Composable
internal fun TrucoGame(c: GameCtx) {
  val cardContext = LocalContext.current
  fun cardRes(card: Int): Int = cardContext.resources.getIdentifier(tAssetName(card), "drawable", cardContext.packageName)
  val flow = remember { c.vm.gameState("truco") }
  val st by flow.collectAsState(initial = null)
  fun num(k:String,d:Int=0)=(st?.get(k) as? Number)?.toInt()?:d
  fun list(k:String)=(st?.get(k) as? List<*>)?.mapNotNull { (it as? Number)?.toInt() }?: emptyList()
  val seats=(st?.get("seats") as? List<*>)?.mapNotNull { it as? String }?: emptyList()
  val started=seats.isNotEmpty() && st?.get("deck") is List<*>
  val hands=seats.indices.map { list("h$it") }
  // Cartas originales de la mano: el envido se cuenta con las 3 cartas repartidas, aunque ya se hayan jugado.
  val origHands=seats.indices.map { i -> list("o$i").ifEmpty { hands[i] } }
  val trucoTeam=num("trucoTeam",-1)
  val turn=num("turn")
  val mano=num("mano",0).coerceIn(0, (seats.size-1).coerceAtLeast(0))
  val scores= listOf(num("p0"),num("p1"))
  val played=list("played")
  val playedBy=(st?.get("playedBy") as? List<*>)?.mapNotNull { (it as? Number)?.toInt() }?: emptyList()
  // Historial persistente de bazas ya jugadas; las cartas no desaparecen al pasar de mano.
  val historyCards = list("historyCards")
  val historyBy = list("historyBy")
  val historyGroups = list("historyGroups")
  val trick=num("trick")
  val trickWinners=list("trickWinners") // -1 indica parda
  val t0=num("t0"); val t1=num("t1")
  val bet=num("bet",1)
  val pending=num("pending",-1)
  val envidoPending=num("envidoPending",-1)
  val envidoCaller=num("envidoCaller",-1)
  val envidoWager=num("envidoWager",2)
  val envidoPreviousWager=num("envidoPreviousWager")
  val envidoRaises=num("envidoRaises")
  val envidoKind=(st?.get("envidoKind") as? String) ?: "envido"
  val envidoDone=num("envidoDone")==1
  val msg=(st?.get("msg") as? String)?:""
  val mySeat=seats.indexOf(c.me)
  val isGameHost = c.me.isNotBlank() && c.me == c.hostUid
  fun seatName(index: Int): String = seats.getOrNull(index)?.let { if (it == "bot") "Bot" else c.nameOf(it) } ?: "Jugador"
  fun teamName(team: Int): String = seats.indices.filter { it % 2 == team }.map { seatName(it) }.distinct().joinToString(" / ").ifBlank { "Jugador" }
  val myTurn=started && pending<0 && envidoPending<0 && turn==mySeat && num("winner",-1)<0 && played.size < seats.size
  val soundContext = LocalContext.current
  var previousPlayedCount by remember { mutableStateOf(0) }
  LaunchedEffect(played.size, played.lastOrNull()) {
    if (played.size > previousPlayedCount) GameSfx.play(soundContext, "sfx_truco_card")
    previousPlayedCount = played.size
  }
  var botsSel by remember { mutableStateOf(c.lobbyBots.coerceIn(0, 3).let { if (it == 0 && c.players.size <= 1) 1 else it }) }

  fun publish(m: MutableMap<String,Any?>) { c.vm.publishGame("truco",m) }
  fun copyState(): MutableMap<String,Any?> {
    val m=mutableMapOf<String,Any?>()
    (st?: emptyMap()).forEach { (k,v)->m[k]=v }
    return m
  }
  // La baza completa queda visible ocho segundos para que ambos equipos puedan revisar
  // las cartas y el resultado antes de limpiar la mesa. Solo publica el anfitrión.
  LaunchedEffect(played.size, played.lastOrNull(), started, c.me, c.hostUid) {
    if (started && c.me == c.hostUid && seats.isNotEmpty() && played.size >= seats.size) {
      val snapshot = played.toList()
      delay(2_600)
      if (played == snapshot && played.size >= seats.size) {
        val m = copyState()
        val oldCards = (m["historyCards"] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() }.orEmpty()
        val oldBy = (m["historyBy"] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() }.orEmpty()
        val oldGroups = (m["historyGroups"] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() }.orEmpty()
        val groupId = (oldGroups.maxOrNull() ?: -1) + 1
        var nc = oldCards + snapshot; var nb = oldBy + playedBy; var ng = oldGroups + List(snapshot.size) { groupId }
        if (nc.size > 15) { val cut = nc.size - 15; nc = nc.drop(cut); nb = nb.drop(cut); ng = ng.drop(cut) }
        m["historyCards"] = nc; m["historyBy"] = nb; m["historyGroups"] = ng
        m["played"] = emptyList<Int>()
        m["playedBy"] = emptyList<Int>()
        publish(m)
      }
    }
  }
  fun start() {
    if (!c.players.contains(c.me)) return
    // El truco argentino se juega en 1 contra 1 o en equipos de 2 contra 2.
    // Evitamos mesas impares: un tercer jugador humano recibe un compañero bot.
    val players=c.players.filter { it.isNotBlank() && it != "bot" }.distinct().take(4).toMutableList()
    // Primero se agregan los bots pedidos; luego se completa automáticamente la pareja
    // que falte para evitar mesas impares (el Truco siempre juega por equipos).
    repeat(botsSel.coerceAtMost((4-players.size).coerceAtLeast(0))) { players.add("bot") }
    if (players.size % 2 != 0 && players.size < 4) players.add("bot")
    while(players.size<2) players.add("bot")
    val d=tDeck().toMutableList(); val m: MutableMap<String, Any?> = mutableMapOf("seats" to players,"deck" to d,"turn" to 0,"mano" to 0,"p0" to 0,"p1" to 0,"played" to emptyList<Int>(),"playedBy" to emptyList<Int>(),"historyCards" to emptyList<Int>(),"historyBy" to emptyList<Int>(),"historyGroups" to emptyList<Int>(),"trick" to 0,"trickWinners" to emptyList<Int>(),"t0" to 0,"t1" to 0,"bet" to 1,"pending" to -1,"envidoPending" to -1,"envidoCaller" to -1,"envidoWager" to 2,"envidoPreviousWager" to 0,"envidoRaises" to 0,"envidoKind" to "envido","envidoDone" to 0,"winner" to -1,"msg" to "¡Buenas! Repartimos tres cartas.")
    players.indices.forEach { i -> val h0=List(3){d.removeAt(0)}; m["h$i"]=h0; m["o$i"]=h0 }
    m["trucoTeam"]=-1
    m["deck"]=d; m["turn"]=0; m["mano"]=0; publish(m)
  }
  fun who(i: Int) = seats.getOrNull(i)?.let { if (it == "bot") "El bot" else c.nameOf(it) } ?: "Jugador"
  fun dealNewHand(m: MutableMap<String, Any?>) {
    val d = tDeck().toMutableList()
    seats.indices.forEach { i -> val h0 = List(3) { d.removeAt(0) }; m["h$i"] = h0; m["o$i"] = h0 }
    m["deck"] = d
  }
  fun archivePlayed(m: MutableMap<String, Any?>, cards: List<Int>, by: List<Int>) {
    if (cards.isEmpty()) return
    val oldCards = (m["historyCards"] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() }.orEmpty()
    val oldBy = (m["historyBy"] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() }.orEmpty()
    val oldGroups = (m["historyGroups"] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() }.orEmpty()
    val groupId = (oldGroups.maxOrNull() ?: -1) + 1
    var nc = oldCards + cards; var nb = oldBy + by; var ng = oldGroups + List(cards.size) { groupId }
    if (nc.size > 15) { val cut = nc.size - 15; nc = nc.drop(cut); nb = nb.drop(cut); ng = ng.drop(cut) }
    m["historyCards"] = nc; m["historyBy"] = nb; m["historyGroups"] = ng
  }
  // Cierra la mano: suma puntos, reinicia apuestas y reparte la siguiente (salvo que alguien llegue a 30).
  fun endHand(m: MutableMap<String, Any?>, winTeam: Int, points: Int, text: String, archiveNow: Boolean) {
    val ns = scores.toMutableList(); ns[winTeam] += points
    m["p0"] = ns[0]; m["p1"] = ns[1]
    val won = ns[winTeam] >= 30
    m["winner"] = if (won) winTeam else -1
    m["bet"] = 1; m["pending"] = -1; m["trucoTeam"] = -1; m["trick"] = 0; m["trickWinners"] = emptyList<Int>()
    m["t0"] = 0; m["t1"] = 0; m["envidoDone"] = 0; m["envidoPending"] = -1; m["envidoKind"] = "envido"; m["envidoRaises"] = 0
    m["msg"] = text
    if (archiveNow) { archivePlayed(m, played, playedBy); m["played"] = emptyList<Int>(); m["playedBy"] = emptyList<Int>() }
    if (!won) { dealNewHand(m); val nm = (mano + 1) % seats.size; m["mano"] = nm; m["turn"] = nm }
  }
  fun opponentSeat(actor: Int): Int =
    seats.indices.firstOrNull { it % 2 != actor % 2 && seats[it] != "bot" } ?: seats.indices.firstOrNull { it % 2 != actor % 2 } ?: ((actor + 1) % seats.size)
  // Solo puede subir la apuesta el equipo que NO hizo la última subida.
  fun canRaiseTruco(actor: Int): Boolean = started && bet < 4 && pending < 0 && envidoPending < 0 && trucoTeam != actor % 2 && played.size < seats.size
  fun playCard(actor:Int, idx:Int) {
    if (actor !in seats.indices || actor != turn || pending >= 0 || envidoPending >= 0 || played.size >= seats.size) return
    val handNow = hands.getOrNull(actor).orEmpty()
    if (idx !in handNow.indices) return
    val m = copyState(); val card = handNow[idx]
    m["h$actor"] = handNow.toMutableList().also { it.removeAt(idx) }
    val pc = played + card; val pb = playedBy + actor
    m["played"] = pc; m["playedBy"] = pb
    if (pc.size < seats.size) { m["turn"] = (actor + 1) % seats.size; m["msg"] = "${who(actor)} jugó ${tName(card)}"; publish(m); return }
    // Baza completa: gana la carta más fuerte; si empatan equipos distintos es parda y abre quien es mano.
    val maxPower = pc.maxOf { tPower(it) }
    val topIdx = pc.indices.filter { tPower(pc[it]) == maxPower }
    val parda = topIdx.map { pb[it] % 2 }.distinct().size > 1
    val trickSeat = if (parda) mano else pb[topIdx.first()]
    val team = if (parda) -1 else trickSeat % 2
    val tw = trickWinners + team
    val decided = trucoHandWinner(tw, mano % 2)
    m["trickWinners"] = tw; m["t0"] = tw.count { it == 0 }; m["t1"] = tw.count { it == 1 }
    if (decided == -2) {
      m["trick"] = trick + 1; m["turn"] = trickSeat
      m["msg"] = if (parda) "Baza parda. Abre ${who(mano)}." else "Baza para ${teamName(team)}."
      publish(m); return
    }
    m["turn"] = trickSeat
    endHand(m, decided, bet, "${teamName(decided)} gana la mano (+$bet).", false)
    publish(m)
  }
  fun callTruco(actor: Int = mySeat) {
    if (actor !in seats.indices || actor != turn || !canRaiseTruco(actor)) return
    val m = copyState(); val newBet = bet + 1
    m["pending"] = opponentSeat(actor); m["bet"] = newBet; m["trucoTeam"] = actor % 2
    m["msg"] = "${who(actor)}: ${when (newBet) { 2 -> "¡TRUCO!"; 3 -> "¡RETRUCO!"; else -> "¡VALE CUATRO!" }}"
    publish(m)
  }
  // «Quiero y retruco / vale cuatro»: se acepta lo anterior y se sube la apuesta.
  fun raiseTruco(actor: Int = mySeat) {
    if (pending < 0 || actor != pending || bet >= 4) return
    val m = copyState(); val newBet = bet + 1
    m["pending"] = opponentSeat(actor); m["bet"] = newBet; m["trucoTeam"] = actor % 2
    m["msg"] = "${who(actor)}: Quiero y ${if (newBet == 3) "¡RETRUCO!" else "¡VALE CUATRO!"}"
    publish(m)
  }
  fun answer(yes: Boolean) {
    if (pending < 0) return
    val m = copyState()
    if (yes) {
      // La baza sigue con quien tenía el turno; la apuesta queda aceptada.
      m["pending"] = -1; m["msg"] = "¡Quiero! Se juega por $bet."
      publish(m)
    } else {
      val winTeam = if (trucoTeam in 0..1) trucoTeam else (1 - pending % 2)
      val pts = (bet - 1).coerceAtLeast(1)
      endHand(m, winTeam, pts, "No quiero. ${teamName(winTeam)} suma $pts y termina la mano.", true)
      publish(m)
    }
  }
  fun fold(actor: Int = mySeat) {
    if (actor !in seats.indices || actor != turn || pending >= 0 || envidoPending >= 0 || played.size >= seats.size) return
    val m = copyState(); val winTeam = 1 - actor % 2
    endHand(m, winTeam, bet, "${who(actor)} se fue al mazo. ${teamName(winTeam)} suma $bet.", true)
    publish(m)
  }
  fun callEnvido(kind: String, actor: Int = mySeat) {
    // El envido se canta en la primera baza, antes de jugar la carta propia y mientras no haya truco.
    if (actor !in seats.indices || actor != turn || envidoDone || envidoPending >= 0 || pending >= 0 || trick != 0 || bet != 1 || played.size >= seats.size) return
    val wager=when(kind){"real"->3;"falta"->(30-maxOf(scores[0],scores[1])).coerceAtLeast(1);else->2}
    val opponent=opponentSeat(actor)
    val m=copyState();m["envidoPending"]=opponent;m["envidoCaller"]=actor;m["envidoWager"]=wager;m["envidoPreviousWager"]=0;m["envidoRaises"]=0;m["envidoKind"]=kind
    m["msg"]="${who(actor)}: "+when(kind){"real"->"¡Real envido!";"falta"->"¡Falta envido!";else->"¡Envido!"};publish(m)
  }
  fun raiseEnvido(kind: String) {
    if (envidoPending < 0 || mySeat < 0 || envidoPending % 2 != mySeat % 2 || envidoDone || envidoKind == "falta") return
    if (envidoKind == "real" && kind != "falta") return
    if (envidoKind == "envido" && kind !in setOf("envido", "real", "falta")) return
    if (kind == "envido" && envidoRaises >= 1) return
    val previousCaller = envidoCaller
    if(previousCaller !in seats.indices) return
    val newWager = when (kind) {
      "envido" -> if (envidoKind == "envido") 4 else 5
      "real" -> 5
      "falta" -> (30 - maxOf(scores[0], scores[1])).coerceAtLeast(1)
      else -> return
    }
    val m = copyState()
    m["envidoPending"] = previousCaller
    m["envidoCaller"] = mySeat
    m["envidoPreviousWager"] = envidoWager
    m["envidoRaises"] = envidoRaises + 1
    m["envidoWager"] = newWager
    m["envidoKind"] = kind
    m["msg"] = when (kind) { "real" -> "¡Real envido!"; "falta" -> "¡Falta envido!"; else -> "¡Envido!" }
    publish(m)
  }
  fun answerEnvido(yes:Boolean, actor:Int=mySeat) {
    if(envidoPending<0 || actor !in seats.indices || envidoPending % 2 != actor % 2) return
    val m=copyState(); val ns=scores.toMutableList(); val callerTeam=(envidoCaller.coerceAtLeast(0))%2
    if(yes) {
      // En empate entre compañeros con el mismo puntaje, cuenta primero el que es mano.
      val order=seats.indices.sortedBy { (it-mano+seats.size)%seats.size }
      val teamBest=(0..1).map { team ->
        val scoresForTeam=seats.indices.filter { it%2==team }.mapNotNull { idx ->
          origHands.getOrNull(idx)?.takeIf { it.isNotEmpty() }?.let { idx to tEnvido(it) }
        }
        val high=scoresForTeam.maxOfOrNull { it.second }
        if (high == null) null else order.firstNotNullOfOrNull { seat -> scoresForTeam.firstOrNull { it.first==seat && it.second==high } }
      }
      val a=teamBest.getOrNull(0);val b=teamBest.getOrNull(1)
      val aMano=a?.first
      val bMano=b?.first
      val winner=when { a==null -> 1; b==null -> 0; a.second>b.second -> 0; b.second>a.second -> 1; (aMano?.let { order.indexOf(it) } ?: Int.MAX_VALUE) < (bMano?.let { order.indexOf(it) } ?: Int.MAX_VALUE) -> 0; else -> 1 }
      ns[winner]+=envidoWager
      m["msg"]="Envido querido: ${a?.second ?: 0} a ${b?.second ?: 0}. ${teamName(winner)} suma $envidoWager."
    } else {
      val refusedPoints = if (envidoPreviousWager > 0) envidoPreviousWager else 1
      ns[callerTeam] += refusedPoints
      m["msg"]="Envido no querido. ${teamName(callerTeam)} suma $refusedPoints tanto(s)."
    }
    m["p0"]=ns[0];m["p1"]=ns[1];m["winner"]=when {ns[0]>=30->0;ns[1]>=30->1;else->-1};m["envidoPending"]=-1;m["envidoDone"]=1;publish(m)
  }

  // El botón compartido «Comenzar partida» debe iniciar también el reparto de Truco;
  // no se obliga al anfitrión a pulsar un segundo botón dentro del juego.
  LaunchedEffect(c.lobbyStarted, started, c.hostUid, c.lobbyBots) {
    if (c.lobbyStarted && !started && isGameHost) start()
  }

  LaunchedEffect(turn,pending,envidoPending,started,played.toString(),hands.toString(),msg,trick) {
    // Solo el anfitrión determinista ejecuta las acciones automáticas de los bots.
    if(!started || c.me!=c.hostUid || num("winner",-1)>=0 || played.size >= seats.size) return@LaunchedEffect
    if(envidoPending>=0 && seats.getOrNull(envidoPending)=="bot") {
      delay(900)
      val e = tEnvido(origHands.getOrElse(envidoPending) { emptyList() })
      answerEnvido(e >= 27 || (e >= 24 && (0..9).random() < 5) || (0..9).random() < 1, envidoPending)
      return@LaunchedEffect
    }
    if(pending>=0 && seats.getOrNull(pending)=="bot") {
      delay(1000)
      val s = hands.getOrElse(pending) { emptyList() }.map { tPower(it) }.sortedDescending().take(2).sum()
      if (bet < 4 && s >= 22 && (0..9).random() < 4) raiseTruco(pending)
      else answer(s >= 16 || (s >= 12 && (0..9).random() < 5) || (0..9).random() < 2)
      return@LaunchedEffect
    }
    if(pending<0 && envidoPending<0 && seats.getOrNull(turn)=="bot") {
      delay(1100)
      val me = turn
      val h = hands.getOrElse(me) { emptyList() }
      if (h.isEmpty()) return@LaunchedEffect
      val e = tEnvido(origHands.getOrElse(me) { h })
      if (trick == 0 && !envidoDone && bet == 1 && e >= 28 && (0..9).random() < 6) { callEnvido(if (e >= 32) "real" else "envido", me); return@LaunchedEffect }
      val strength = h.map { tPower(it) }.sortedDescending().take(2).sum()
      if (canRaiseTruco(me) && strength >= 21 && (0..9).random() < 3) { callTruco(me); return@LaunchedEffect }
      val sorted = h.indices.sortedBy { tPower(h[it]) }
      val oppBest = played.indices.filter { playedBy.getOrElse(it) { 0 } % 2 != me % 2 }.map { tPower(played[it]) }.maxOrNull()
      val mateBest = played.indices.filter { playedBy.getOrElse(it) { 0 } % 2 == me % 2 }.map { tPower(played[it]) }.maxOrNull()
      val idx = when {
        played.isEmpty() -> if (trick == 0 && strength < 21 && sorted.size == 3) sorted[1] else sorted.last()
        mateBest != null && mateBest > (oppBest ?: -1) -> sorted.first()           // el compañero va ganando: tiro la más baja
        else -> sorted.firstOrNull { tPower(h[it]) > (oppBest ?: -1) } ?: sorted.first() // la más baja que gana, o la peor
      }
      playCard(me, idx)
    }
  }

  if(!started) {
    GlassCard(modifier=Modifier.fillMaxWidth(),highlight=true) {
      Text("🃏 Truco argentino",color=Color.White,fontWeight=FontWeight.ExtraBold,fontSize=20.sp)
      Text("Mazo español de 40 cartas. Mesas 1 contra 1 o 2 contra 2. Envido, real envido y falta envido con aceptación/rechazo; tres bazas por mano y partida a 30 puntos.",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=12.sp)
    }
    if (c.players.contains(c.me)) {
      Spacer(Modifier.height(8.dp));Text("Bots",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=12.sp)
      Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){(0..3).forEach{k->if(botsSel==k)GradientButton("$k bots",onClick={botsSel=k},height=38.dp)else SoftButton("$k bots",onClick={botsSel=k})}}
      Spacer(Modifier.height(8.dp));GradientButton("Repartir cartas",modifier=Modifier.fillMaxWidth(),onClick={start()})
    } else {
      Spacer(Modifier.height(8.dp));Text("Esperando que el anfitrión configure los bots y reparta las cartas…",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=13.sp)
    }
    return
  }
  Column {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
      GlassCard(modifier=Modifier.weight(1f),highlight=true){Text(teamName(0),color=GoldYellow,fontWeight=FontWeight.Bold,maxLines=2);Text("${scores[0]} tantos",color=Color.White,fontSize=20.sp)}
      GlassCard(modifier=Modifier.weight(1f),highlight=true){Text(teamName(1),color=GoldYellow,fontWeight=FontWeight.Bold,maxLines=2);Text("${scores[1]} tantos",color=Color.White,fontSize=20.sp)}
    }
    Spacer(Modifier.height(8.dp));GlassCard(modifier=Modifier.fillMaxWidth()) { Text(msg,color=Color.White,fontWeight=FontWeight.Bold);Text("Bazas: $t0 - $t1 · Truco en juego: $bet",color=GoldYellow,fontSize=12.sp) }
    if (historyCards.isNotEmpty()) {
      Spacer(Modifier.height(8.dp))
      GlassCard(modifier=Modifier.fillMaxWidth()) {
        Text("MANOS ANTERIORES · ${historyCards.size} cartas", color=GoldYellow, fontWeight=FontWeight.Bold, fontSize=12.sp)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement=Arrangement.spacedBy(10.dp)) {
          historyCards.indices.groupBy { historyGroups.getOrElse(it) { 0 } }.values.forEach { indexes ->
            Box(Modifier.height(88.dp).width((42 + (indexes.size - 1).coerceAtLeast(0) * 13).dp)) {
              indexes.forEachIndexed { fanIndex, cardIndex ->
                val card = historyCards[cardIndex]
                val res = cardRes(card)
                Box(Modifier.padding(start = (fanIndex * 13).dp).graphicsLayer { rotationZ = (fanIndex - (indexes.size - 1) / 2f) * 5f; translationY = kotlin.math.abs(fanIndex - (indexes.size - 1) / 2f) * 2f }.size(48.dp, 72.dp).border(1.dp, GoldYellow, RoundedCornerShape(5.dp))) {
                  if (res != 0) Image(painter = painterResource(res), contentDescription = "${tName(card)} · ${seats.getOrNull(historyBy.getOrElse(cardIndex) { 0 })?.let { if (it == "bot") "Bot" else c.nameOf(it) } ?: "Jugador"}", modifier = Modifier.fillMaxWidth().height(72.dp), contentScale = ContentScale.FillBounds)
                  else Text(tName(card), color = Color.Black, modifier = Modifier.align(Alignment.Center))
                }
              }
            }
          }
        }
      }
    }
    Spacer(Modifier.height(8.dp))
    BoxWithConstraints(
      Modifier.fillMaxWidth().heightIn(min = 140.dp).clip(RoundedCornerShape(20.dp))
        .background(Brush.radialGradient(listOf(Color(0xFF16705A), Color(0xFF0B3F31), Color(0xFF062219)), radius = 700f))
        .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(20.dp)).padding(8.dp)
    ) {
      val shownCount = played.size.coerceAtLeast(1)
      val playedCardWidth = ((maxWidth - 8.dp * (shownCount - 1)) / shownCount).coerceIn(30.dp, 68.dp)
      Row(Modifier.fillMaxWidth().animateContentSize(), horizontalArrangement=Arrangement.Center, verticalAlignment=Alignment.CenterVertically) {
        played.forEachIndexed { i, card ->
          AnimatedVisibility(visible=true, enter=slideInVertically(initialOffsetY={ fullHeight -> fullHeight / 2 }, animationSpec=tween(480)) + fadeIn(animationSpec=tween(480))) {
            Column(horizontalAlignment=Alignment.CenterHorizontally, modifier=Modifier.padding(horizontal=4.dp, vertical=6.dp).animateContentSize()) {
              val res=cardRes(card)
              if(res!=0) Image(painter=painterResource(res), contentDescription=tName(card), modifier=Modifier.size(playedCardWidth, playedCardWidth * 1.47f).animateContentSize(), contentScale=ContentScale.Fit) else Text(tName(card))
              Text(seats.getOrNull(playedBy.getOrElse(i){0})?.let { if(it=="bot") "Bot" else c.nameOf(it) } ?: "Jugador", fontSize=10.sp, color=GoldYellow)
            }
          }
        }
      }
    }
    Text(if(myTurn)"Tu mano: tocá una carta para jugarla" else "Turno de ${if(seats.getOrNull(turn)=="bot")"Bot" else c.nameOf(seats.getOrElse(turn){""})}",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=12.sp)
    Spacer(Modifier.height(6.dp))
    BoxWithConstraints(Modifier.fillMaxWidth()) {
      val myHand = hands.getOrNull(mySeat).orEmpty()
      val cardGap = 4.dp
      val cardWidth = ((maxWidth - cardGap * (myHand.size - 1).coerceAtLeast(0)) / myHand.size.coerceAtLeast(1)).coerceIn(36.dp, 92.dp)
      Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.Center, verticalAlignment=Alignment.CenterVertically) {
        myHand.forEachIndexed { idx, card ->
          Box(Modifier.padding(horizontal = 2.dp).size(width = cardWidth, height = cardWidth * 1.47f).border(BorderStroke(2.dp,if(myTurn)GoldYellow else Color.Gray),RoundedCornerShape(10.dp)).clickable(enabled=myTurn){playCard(mySeat,idx)},contentAlignment=Alignment.Center){
            val res=cardRes(card)
            if(res!=0) Image(painter=painterResource(res),contentDescription=tName(card),modifier=Modifier.fillMaxWidth().height(cardWidth * 1.47f),contentScale=ContentScale.FillBounds)
            else Text(tName(card),fontSize=24.sp,fontWeight=FontWeight.ExtraBold,color=Color.Black)
          }
        }
      }
    }
    Spacer(Modifier.height(8.dp))
    if(envidoPending>=0 && mySeat>=0 && envidoPending%2==mySeat%2) Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
      Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { GradientButton("¡Quiero el envido!",modifier=Modifier.weight(1f),onClick={answerEnvido(true)});SoftButton("No quiero",onClick={answerEnvido(false)}) }
      if (envidoKind == "envido") Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        if (envidoRaises == 0) SoftButton("Otro envido",onClick={raiseEnvido("envido")})
        SoftButton("Real envido",onClick={raiseEnvido("real")})
        SoftButton("Falta envido",onClick={raiseEnvido("falta")})
      } else if (envidoKind == "real") {
        SoftButton("Falta envido",onClick={raiseEnvido("falta")})
      }
    }
    else if(pending>=0 && pending==mySeat) Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
      Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){GradientButton("¡Quiero!",modifier=Modifier.weight(1f),onClick={answer(true)});SoftButton("No quiero",onClick={answer(false)})}
      if (bet < 4) SoftButton(if (bet == 2) "Quiero y ¡retruco!" else "Quiero y ¡vale cuatro!", onClick={raiseTruco(mySeat)}, modifier=Modifier.fillMaxWidth())
    }
    else {
      val canEnvido = myTurn && !envidoDone && envidoPending<0 && trick==0 && bet==1
      Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
          GradientButton("Envido",onClick={callEnvido("envido")},enabled=canEnvido,modifier=Modifier.weight(1f))
          GradientButton("Real",onClick={callEnvido("real")},enabled=canEnvido,modifier=Modifier.weight(1f))
          GradientButton("Falta",onClick={callEnvido("falta")},enabled=canEnvido,modifier=Modifier.weight(1f))
        }
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
          GradientButton(when(bet){1->"Truco";2->"Retruco";else->"Vale cuatro"},onClick={callTruco()},enabled=myTurn&&canRaiseTruco(mySeat),modifier=Modifier.weight(1f))
          SoftButton("Al mazo",onClick={fold()},enabled=myTurn,modifier=Modifier.weight(1f))
        }
      }
    }
    if(num("winner",-1)>=0) {
      Spacer(Modifier.height(8.dp));Text("🏆 ¡Ganó ${teamName(num("winner",0))}!",color=GoldYellow,fontWeight=FontWeight.ExtraBold)
      if (c.players.contains(c.me)) GradientButton("Nueva partida",modifier=Modifier.fillMaxWidth(),onClick={publish(mutableMapOf())})
      else Text("Esperando que el anfitrión inicie la revancha…",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=13.sp)
    }
  }
}
