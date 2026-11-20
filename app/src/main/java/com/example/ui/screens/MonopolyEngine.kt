package com.example.ui.screens

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.min
import kotlin.random.Random

// ═══════════════════════════════════════════════════════════════════════════════
//  MONOPOLY · motor de reglas (reglamento oficial de Hasbro)
//  · 2 a 8 jugadores, $1.500 cada uno, 2 dados, dobles repiten turno, 3 dobles = cárcel.
//  · Propiedades, estaciones y servicios; alquiler doble con grupo completo; casas y hoteles (parejo, 32 casas / 12 hoteles).
//  · Hipotecas (10 % de interés al levantar), cárcel (pagar $50, carta, o sacar dobles; 3er intento paga), subastas,
//    intercambios, cartas de Suerte y Arca comunal, impuestos, quiebra ante el banco o ante otro jugador.
//  · Opción de casa: Parada libre acumula los impuestos y multas (apagada por defecto = reglamento oficial).
// ═══════════════════════════════════════════════════════════════════════════════

internal const val MT_GO = 0
internal const val MT_PROP = 1
internal const val MT_RR = 2
internal const val MT_UTIL = 3
internal const val MT_CC = 4
internal const val MT_CH = 5
internal const val MT_TAX = 6
internal const val MT_JAIL = 7
internal const val MT_FREE = 8
internal const val MT_GOJAIL = 9

internal const val P_ROLL = 0
internal const val P_BUY = 1
internal const val P_END = 2
internal const val P_AUC = 3
internal const val P_DEBT = 4
internal const val P_OVER = 5

internal class MSpace(val idx: Int, val name: String, val type: Int, val price: Int = 0, val group: Int = -1, val rents: IntArray = IntArray(0), val houseCost: Int = 0, val tax: Int = 0)

/** Grupos de color: 0 marrón, 1 celeste, 2 rosa, 3 naranja, 4 rojo, 5 amarillo, 6 verde, 7 azul; 8 estaciones, 9 servicios. */
internal val MONO_GROUP_NAMES = listOf("Marrón", "Celeste", "Rosa", "Naranja", "Rojo", "Amarillo", "Verde", "Azul", "Estaciones", "Servicios")

private fun prop(i: Int, n: String, price: Int, g: Int, hc: Int, vararg r: Int) = MSpace(i, n, MT_PROP, price, g, r, hc)

internal val MONO_SPACES: List<MSpace> = listOf(
  MSpace(0, "GO", MT_GO),
  prop(1, "Mediterranean Avenue", 60, 0, 50, 2, 10, 30, 90, 160, 250),
  MSpace(2, "Community Chest", MT_CC),
  prop(3, "Baltic Avenue", 60, 0, 50, 4, 20, 60, 180, 320, 450),
  MSpace(4, "Income Tax", MT_TAX, tax = 200),
  MSpace(5, "Reading Railroad", MT_RR, 200, 8),
  prop(6, "Oriental Avenue", 100, 1, 50, 6, 30, 90, 270, 400, 550),
  MSpace(7, "Chance", MT_CH),
  prop(8, "Vermont Avenue", 100, 1, 50, 6, 30, 90, 270, 400, 550),
  prop(9, "Connecticut Avenue", 120, 1, 50, 8, 40, 100, 300, 450, 600),
  MSpace(10, "Jail", MT_JAIL),
  prop(11, "St. Charles Place", 140, 2, 100, 10, 50, 150, 450, 625, 750),
  MSpace(12, "Electric Company", MT_UTIL, 150, 9),
  prop(13, "States Avenue", 140, 2, 100, 10, 50, 150, 450, 625, 750),
  prop(14, "Virginia Avenue", 160, 2, 100, 12, 60, 180, 500, 700, 900),
  MSpace(15, "Pennsylvania Railroad", MT_RR, 200, 8),
  prop(16, "St. James Place", 180, 3, 100, 14, 70, 200, 550, 750, 950),
  MSpace(17, "Community Chest", MT_CC),
  prop(18, "Tennessee Avenue", 180, 3, 100, 14, 70, 200, 550, 750, 950),
  prop(19, "New York Avenue", 200, 3, 100, 16, 80, 220, 600, 800, 1000),
  MSpace(20, "Free Parking", MT_FREE),
  prop(21, "Kentucky Avenue", 220, 4, 150, 18, 90, 250, 700, 875, 1050),
  MSpace(22, "Chance", MT_CH),
  prop(23, "Indiana Avenue", 220, 4, 150, 18, 90, 250, 700, 875, 1050),
  prop(24, "Illinois Avenue", 240, 4, 150, 20, 100, 300, 750, 925, 1100),
  MSpace(25, "B. & O. Railroad", MT_RR, 200, 8),
  prop(26, "Atlantic Avenue", 260, 5, 150, 22, 110, 330, 800, 975, 1150),
  prop(27, "Ventnor Avenue", 260, 5, 150, 22, 110, 330, 800, 975, 1150),
  MSpace(28, "Water Works", MT_UTIL, 150, 9),
  prop(29, "Marvin Gardens", 280, 5, 150, 24, 120, 360, 850, 1025, 1200),
  MSpace(30, "Go To Jail", MT_GOJAIL),
  prop(31, "Pacific Avenue", 300, 6, 200, 26, 130, 390, 900, 1100, 1275),
  prop(32, "North Carolina Avenue", 300, 6, 200, 26, 130, 390, 900, 1100, 1275),
  MSpace(33, "Community Chest", MT_CC),
  prop(34, "Pennsylvania Avenue", 320, 6, 200, 28, 150, 450, 1000, 1200, 1400),
  MSpace(35, "Short Line", MT_RR, 200, 8),
  MSpace(36, "Chance", MT_CH),
  prop(37, "Park Place", 350, 7, 200, 35, 175, 500, 1100, 1300, 1500),
  MSpace(38, "Luxury Tax", MT_TAX, tax = 100),
  prop(39, "Boardwalk", 400, 7, 200, 50, 200, 600, 1400, 1700, 2000)
)

internal val MONO_GROUPS: Map<Int, List<Int>> = MONO_SPACES.filter { it.group >= 0 }.groupBy({ it.group }, { it.idx })

// ───────────────────────── Cartas ─────────────────────────
internal const val CK_MOVE = 0
internal const val CK_BACK = 1
internal const val CK_COLLECT = 2
internal const val CK_PAY = 3
internal const val CK_JAIL = 4
internal const val CK_GOOJF = 5
internal const val CK_NEARRR = 6
internal const val CK_NEARUT = 7
internal const val CK_REPAIR = 8
internal const val CK_PAYEACH = 9
internal const val CK_COLEACH = 10

internal class MCard(val text: String, val kind: Int, val a: Int = 0, val b: Int = 0)

internal val CH_CARDS = listOf(
  MCard("Avanzá hasta la salida (GO). Cobrá $200.", CK_MOVE, 0),
  MCard("Avanzá hasta Illinois Avenue. Si pasás por GO, cobrá $200.", CK_MOVE, 24),
  MCard("Avanzá hasta St. Charles Place. Si pasás por GO, cobrá $200.", CK_MOVE, 11),
  MCard("Avanzá hasta el servicio más cercano. Si no tiene dueño podés comprarlo; si lo tiene, tirá los dados y pagá 10 veces lo que salga.", CK_NEARUT),
  MCard("Avanzá hasta la estación más cercana. Si tiene dueño, pagá el doble del alquiler.", CK_NEARRR),
  MCard("Avanzá hasta la estación más cercana. Si tiene dueño, pagá el doble del alquiler.", CK_NEARRR),
  MCard("El banco te paga un dividendo de $50.", CK_COLLECT, 50),
  MCard("Salí gratis de la cárcel. Conservá esta carta hasta usarla o venderla.", CK_GOOJF),
  MCard("Retrocedé 3 casilleros.", CK_BACK, 3),
  MCard("Andá directo a la cárcel. No pasás por GO ni cobrás $200.", CK_JAIL),
  MCard("Reparaciones generales: pagá $25 por casa y $100 por hotel.", CK_REPAIR, 25, 100),
  MCard("Multa por exceso de velocidad: pagá $15.", CK_PAY, 15),
  MCard("Hacé un viaje a Reading Railroad. Si pasás por GO, cobrá $200.", CK_MOVE, 5),
  MCard("Avanzá hasta Boardwalk.", CK_MOVE, 39),
  MCard("Te eligieron presidente de la mesa: pagale $50 a cada jugador.", CK_PAYEACH, 50),
  MCard("Vence tu préstamo de construcción: cobrá $150.", CK_COLLECT, 150)
)

internal val CC_CARDS = listOf(
  MCard("Avanzá hasta la salida (GO). Cobrá $200.", CK_MOVE, 0),
  MCard("Error bancario a tu favor: cobrá $200.", CK_COLLECT, 200),
  MCard("Honorarios del médico: pagá $50.", CK_PAY, 50),
  MCard("Vendiste acciones: cobrá $50.", CK_COLLECT, 50),
  MCard("Salí gratis de la cárcel. Conservá esta carta hasta usarla o venderla.", CK_GOOJF),
  MCard("Andá directo a la cárcel. No pasás por GO ni cobrás $200.", CK_JAIL),
  MCard("Vence el fondo de vacaciones: cobrá $100.", CK_COLLECT, 100),
  MCard("Devolución de impuestos: cobrá $20.", CK_COLLECT, 20),
  MCard("Es tu cumpleaños: cada jugador te paga $10.", CK_COLEACH, 10),
  MCard("Vence tu seguro de vida: cobrá $100.", CK_COLLECT, 100),
  MCard("Gastos de hospital: pagá $100.", CK_PAY, 100),
  MCard("Gastos escolares: pagá $50.", CK_PAY, 50),
  MCard("Cobrás $25 por honorarios de consultoría.", CK_COLLECT, 25),
  MCard("Te cobran arreglo de calles: $40 por casa y $115 por hotel.", CK_REPAIR, 40, 115),
  MCard("Ganaste el segundo premio en un concurso de belleza: cobrá $10.", CK_COLLECT, 10),
  MCard("Recibís una herencia de $100.", CK_COLLECT, 100)
)

// ───────────────────────── Estado ─────────────────────────
internal class MPlayer(var name: String, var uid: String, var token: Int) {
  var money = 1500
  var pos = 0
  var jail = -1
  var goojf = 0
  var bankrupt = false
}

internal class MAuction(var space: Int, var bid: Int, var leader: Int, val active: MutableList<Int>, var ptr: Int)

internal class MTrade(var from: Int, var to: Int) {
  val giveProps = HashSet<Int>()
  val getProps = HashSet<Int>()
  var giveCash = 0
  var getCash = 0
  var giveJail = 0
  var getJail = 0
}

internal class MState {
  val players = ArrayList<MPlayer>()
  val owner = IntArray(40) { -1 }
  val houses = IntArray(40)
  val mort = BooleanArray(40)
  var turn = 0
  var phase = P_ROLL
  var d1 = 0
  var d2 = 0
  var dblCount = 0
  var extra = false
  var debtAmt = 0
  var debtTo = -1
  var pendingMove = 0
  var auc: MAuction? = null
  val aucQueue = ArrayList<Int>()
  var trade: MTrade? = null
  val log = ArrayList<String>()
  var winner = -1
  var ccOrder = IntArray(16) { it }
  var chOrder = IntArray(16) { it }
  var ccIdx = 0
  var chIdx = 0
  var ccHeld = false
  var chHeld = false
  var pot = 0
  var rulePot = false
  var cardText = ""
  var cardTitle = ""
  var ev = 0
  var rollNo = 0
  var rollSeq = 0
  var cardSeq = 0

  fun cur(): MPlayer = players[turn]
  fun alive(): List<Int> = players.indices.filter { !players[it].bankrupt }
  fun addLog(t: String) { log.add(t); while (log.size > 40) log.removeAt(0); ev++ }

  // ── consultas ──
  fun ownsGroup(p: Int, g: Int): Boolean = MONO_GROUPS[g]?.all { owner[it] == p } == true
  fun housesUsed(): Int = (0 until 40).sumOf { if (houses[it] in 1..4) houses[it] else 0 }
  fun hotelsUsed(): Int = (0 until 40).count { houses[it] == 5 }
  fun countOwned(p: Int, g: Int): Int = MONO_GROUPS[g]?.count { owner[it] == p } ?: 0
  fun ownedBy(p: Int): List<Int> = (0 until 40).filter { owner[it] == p }

  fun rentOf(i: Int, dice: Int, rrDouble: Boolean = false): Int {
    val sp = MONO_SPACES[i]; val o = owner[i]
    if (o < 0) return 0
    return when (sp.type) {
      MT_PROP -> if (houses[i] > 0) sp.rents[houses[i]] else sp.rents[0] * (if (ownsGroup(o, sp.group)) 2 else 1)
      MT_RR -> (25 shl (countOwned(o, 8) - 1)) * (if (rrDouble) 2 else 1)
      MT_UTIL -> dice * (if (countOwned(o, 9) >= 2) 10 else 4)
      else -> 0
    }
  }

  fun assets(p: Int): Int {
    var t = players[p].money
    for (i in ownedBy(p)) {
      val sp = MONO_SPACES[i]
      t += if (mort[i]) sp.price / 2 else sp.price
      t += houses[i] * sp.houseCost
    }
    return t
  }

  /** Dinero que puede juntar vendiendo edificios e hipotecando todo lo que tenga. */
  fun liquidation(p: Int): Int {
    var t = players[p].money
    for (i in ownedBy(p)) {
      val sp = MONO_SPACES[i]
      t += houses[i] * (sp.houseCost / 2)
      if (!mort[i]) t += sp.price / 2
    }
    return t
  }

  // ── turnos ──
  fun nextTurn() {
    dblCount = 0; extra = false; pendingMove = 0
    val al = alive()
    if (al.size <= 1) { winner = al.firstOrNull() ?: -1; phase = P_OVER; return }
    var n = turn
    do { n = (n + 1) % players.size } while (players[n].bankrupt)
    turn = n; phase = P_ROLL; rollNo++
  }

  private fun startQueuedAuction(): Boolean {
    if (aucQueue.isEmpty() || alive().size <= 1) { aucQueue.clear(); return false }
    startAuction(aucQueue.removeAt(0))
    return true
  }

  fun finishAction() {
    if (alive().size <= 1) { winner = alive().firstOrNull() ?: -1; phase = P_OVER; return }
    if (startQueuedAuction()) return
    val p = cur()
    if (p.bankrupt) { nextTurn(); return }
    phase = if (extra && p.jail < 0) P_ROLL else P_END
  }

  fun endTurn() { if (phase == P_END) nextTurn() }

  fun goToJail(i: Int) {
    players[i].pos = 10; players[i].jail = 0; extra = false
    addLog("${players[i].name} va a la cárcel 🚔")
  }

  // ── tirada ──
  fun roll(rnd: Random) = doRoll(rnd.nextInt(1, 7), rnd.nextInt(1, 7), rnd)

  fun doRoll(a: Int, b: Int, rnd: Random) {
    if (phase != P_ROLL) return
    d1 = a; d2 = b; rollSeq++
    val p = cur(); val dbl = a == b
    addLog("${p.name} sacó $a + $b = ${a + b}${if (dbl) " (dobles)" else ""}")
    if (p.jail >= 0) {
      if (dbl) {
        p.jail = -1; extra = false; addLog("${p.name} sacó dobles y sale de la cárcel")
        moveBy(a + b, rnd)
      } else {
        p.jail++
        if (p.jail >= 3) {
          addLog("${p.name} no sacó dobles en 3 intentos: paga \$50 y sale")
          p.jail = -1; pendingMove = a + b
          charge(50, -2, rnd)
        } else { addLog("${p.name} sigue en la cárcel"); phase = P_END }
      }
      return
    }
    if (dbl) {
      dblCount++
      if (dblCount >= 3) { addLog("${p.name} sacó 3 dobles seguidos"); goToJail(turn); phase = P_END; return }
      extra = true
    } else extra = false
    moveBy(a + b, rnd)
  }

  fun moveBy(steps: Int, rnd: Random) {
    val p = cur(); val np = p.pos + steps
    if (np >= 40) { p.money += 200; addLog("${p.name} pasa por GO y cobra \$200") }
    p.pos = np % 40
    land(rnd)
  }

  private fun advanceTo(idx: Int, collect: Boolean) {
    val p = cur()
    if (collect && idx < p.pos) { p.money += 200; addLog("${p.name} pasa por GO y cobra \$200") }
    p.pos = idx
  }

  fun land(rnd: Random, rrDouble: Boolean = false, utilDice: Int = 0) {
    val p = cur(); val sp = MONO_SPACES[p.pos]
    when (sp.type) {
      MT_PROP, MT_RR, MT_UTIL -> {
        val o = owner[sp.idx]
        if (o == -1) { phase = P_BUY; addLog("${p.name} cae en ${sp.name} (libre, \$${sp.price})") }
        else if (o == turn || mort[sp.idx]) { addLog("${p.name} cae en ${sp.name}${if (o == turn) " (suya)" else " (hipotecada)"}"); finishAction() }
        else {
          val amt = if (sp.type == MT_UTIL && utilDice > 0) utilDice * 10 else rentOf(sp.idx, d1 + d2, rrDouble)
          addLog("${p.name} paga \$$amt de alquiler a ${players[o].name} por ${sp.name}")
          charge(amt, o, rnd)
        }
      }
      MT_CC -> drawCard(true, rnd)
      MT_CH -> drawCard(false, rnd)
      MT_TAX -> { addLog("${p.name} paga ${sp.name}: \$${sp.tax}"); charge(sp.tax, -2, rnd) }
      MT_GOJAIL -> { goToJail(turn); finishAction() }
      MT_FREE -> {
        if (rulePot && pot > 0) { p.money += pot; addLog("${p.name} cobra el pozo de Parada libre: \$$pot"); pot = 0 }
        finishAction()
      }
      else -> finishAction()
    }
  }

  /** Cobra `amt` al jugador de turno. to: jugador, -1 banco, -2 banco/pozo (impuestos), -3 a cada jugador. */
  fun charge(amt: Int, to: Int, @Suppress("UNUSED_PARAMETER") rnd: Random) {
    val p = cur()
    if (amt <= 0) { settleNext(); return }
    if (p.money >= amt) { settle(amt, to); settleNext() }
    else { debtAmt = amt; debtTo = to; phase = P_DEBT; addLog("${p.name} debe \$$amt y no le alcanza el efectivo") }
  }

  private fun settle(amt: Int, to: Int) {
    val p = cur()
    p.money -= amt
    when {
      to >= 0 -> players[to].money += amt
      to == -2 && rulePot -> pot += amt
      to == -3 -> {
        val others = alive().filter { it != turn }
        val each = if (others.isEmpty()) 0 else amt / others.size
        for (o in others) players[o].money += each
      }
    }
  }

  private fun settleNext() {
    if (pendingMove > 0) { val m = pendingMove; pendingMove = 0; moveBy(m, Random.Default) } else finishAction()
  }

  /** Paga la deuda pendiente (cuando ya juntó el dinero). */
  fun payDebt(rnd: Random): Boolean {
    if (phase != P_DEBT) return false
    val p = cur()
    if (p.money < debtAmt) return false
    val amt = debtAmt; val to = debtTo
    debtAmt = 0; debtTo = -1
    settle(amt, to)
    addLog("${p.name} saldó su deuda de \$$amt")
    settleNext()
    return true
  }

  // ── cartas ──
  private fun drawCard(cc: Boolean, rnd: Random) {
    val deck = if (cc) CC_CARDS else CH_CARDS
    val order = if (cc) ccOrder else chOrder
    var idx = if (cc) ccIdx else chIdx
    var card = deck[order[idx % 16]]
    var tries = 0
    while (card.kind == CK_GOOJF && (if (cc) ccHeld else chHeld) && tries < 16) { idx++; card = deck[order[idx % 16]]; tries++ }
    idx++
    if (cc) ccIdx = idx % 16 else chIdx = idx % 16
    cardTitle = if (cc) "Arca comunal" else "Suerte"
    cardText = card.text; cardSeq++
    val p = cur()
    addLog("${p.name} saca una carta de $cardTitle")
    when (card.kind) {
      CK_MOVE -> { advanceTo(card.a, true); land(rnd) }
      CK_BACK -> { p.pos = (p.pos - card.a + 40) % 40; land(rnd) }
      CK_COLLECT -> { p.money += card.a; finishAction() }
      CK_PAY -> charge(card.a, -2, rnd)
      CK_JAIL -> { goToJail(turn); finishAction() }
      CK_GOOJF -> { p.goojf++; if (cc) ccHeld = true else chHeld = true; finishAction() }
      CK_NEARRR -> {
        val t = listOf(5, 15, 25, 35).firstOrNull { it > p.pos } ?: 5
        advanceTo(t, true); land(rnd, rrDouble = true)
      }
      CK_NEARUT -> {
        val t = listOf(12, 28).firstOrNull { it > p.pos } ?: 12
        advanceTo(t, true)
        land(rnd, utilDice = rnd.nextInt(1, 7) + rnd.nextInt(1, 7))
      }
      CK_REPAIR -> {
        var h = 0; var ho = 0
        for (i in ownedBy(turn)) if (houses[i] == 5) ho++ else h += houses[i]
        val cost = h * card.a + ho * card.b
        addLog("Reparaciones: $h casas y $ho hoteles = \$$cost")
        charge(cost, -2, rnd)
      }
      CK_PAYEACH -> charge(card.a * (alive().size - 1), -3, rnd)
      CK_COLEACH -> {
        for (o in alive()) if (o != turn) { val pay = min(card.a, players[o].money); players[o].money -= pay; p.money += pay }
        finishAction()
      }
    }
  }

  // ── compra y subasta ──
  fun buy(): Boolean {
    if (phase != P_BUY) return false
    val p = cur(); val sp = MONO_SPACES[p.pos]
    if (owner[sp.idx] != -1 || p.money < sp.price) return false
    p.money -= sp.price; owner[sp.idx] = turn
    addLog("${p.name} compra ${sp.name} por \$${sp.price}")
    finishAction()
    return true
  }

  fun startAuction(space: Int) {
    val act = ArrayList<Int>()
    var i = turn
    repeat(players.size) { i = (i + 1) % players.size; if (!players[i].bankrupt) act.add(i) }
    if (act.isEmpty()) { finishAction(); return }
    auc = MAuction(space, 0, -1, act, 0)
    phase = P_AUC
    addLog("Subasta de ${MONO_SPACES[space].name}")
  }

  fun declineBuy() { if (phase == P_BUY) startAuction(cur().pos) }

  fun aucActor(): Int = auc?.let { it.active.getOrNull(it.ptr) } ?: -1

  fun aucBid(amount: Int): Boolean {
    val a = auc ?: return false
    val who = aucActor(); if (who < 0) return false
    if (amount <= a.bid || amount > players[who].money) return false
    a.bid = amount; a.leader = who
    addLog("${players[who].name} ofrece \$$amount")
    a.ptr = (a.ptr + 1) % a.active.size
    aucAfter(a)
    return true
  }

  fun aucPass() {
    val a = auc ?: return
    val who = aucActor(); if (who < 0) return
    a.active.removeAt(a.ptr)
    addLog("${players[who].name} se retira de la subasta")
    if (a.ptr >= a.active.size) a.ptr = 0
    aucAfter(a)
  }

  private fun aucAfter(a: MAuction) {
    if (a.leader >= 0 && a.active.all { it == a.leader }) {
      owner[a.space] = a.leader; players[a.leader].money -= a.bid
      addLog("${players[a.leader].name} gana ${MONO_SPACES[a.space].name} por \$${a.bid}")
      auc = null
      finishAction(); return
    }
    if (a.active.isEmpty()) { addLog("Nadie ofertó: ${MONO_SPACES[a.space].name} queda libre"); auc = null; finishAction(); return }
    if (a.leader >= 0 && a.active.getOrNull(a.ptr) == a.leader) a.ptr = (a.ptr + 1) % a.active.size
  }

  // ── construcción, hipotecas ──
  fun canBuild(i: Int): Boolean {
    val sp = MONO_SPACES[i]
    if (sp.type != MT_PROP || owner[i] != turn) return false
    val g = MONO_GROUPS[sp.group] ?: return false
    if (!ownsGroup(turn, sp.group) || g.any { mort[it] }) return false
    val h = houses[i]
    if (h >= 5 || cur().money < sp.houseCost) return false
    if (h != g.minOf { houses[it] }) return false
    if (h < 4 && housesUsed() >= 32) return false
    if (h == 4 && hotelsUsed() >= 12) return false
    return true
  }

  fun build(i: Int): Boolean {
    if (!canBuild(i)) return false
    cur().money -= MONO_SPACES[i].houseCost; houses[i]++
    addLog("${cur().name} construye ${if (houses[i] == 5) "un hotel" else "una casa"} en ${MONO_SPACES[i].name}")
    return true
  }

  fun canSellBuilding(i: Int, p: Int = turn): Boolean {
    val sp = MONO_SPACES[i]
    if (sp.type != MT_PROP || owner[i] != p || houses[i] <= 0) return false
    val g = MONO_GROUPS[sp.group] ?: return false
    if (houses[i] != g.maxOf { houses[it] }) return false
    if (houses[i] == 5 && housesUsed() > 28) return false
    return true
  }

  fun sellBuilding(i: Int): Boolean {
    if (!canSellBuilding(i)) return false
    cur().money += MONO_SPACES[i].houseCost / 2; houses[i]--
    addLog("${cur().name} vende ${if (houses[i] == 4) "un hotel" else "una casa"} de ${MONO_SPACES[i].name}")
    return true
  }

  fun canMortgage(i: Int, p: Int = turn): Boolean {
    val sp = MONO_SPACES[i]
    if (owner[i] != p || mort[i] || sp.group < 0) return false
    return MONO_GROUPS[sp.group]?.all { houses[it] == 0 } == true
  }

  fun mortgage(i: Int): Boolean {
    if (!canMortgage(i)) return false
    mort[i] = true; cur().money += MONO_SPACES[i].price / 2
    addLog("${cur().name} hipoteca ${MONO_SPACES[i].name} (+\$${MONO_SPACES[i].price / 2})")
    return true
  }

  fun unmortgageCost(i: Int): Int { val m = MONO_SPACES[i].price / 2; return m + (m * 0.1f + 0.999f).toInt() }

  fun unmortgage(i: Int): Boolean {
    if (owner[i] != turn || !mort[i] || cur().money < unmortgageCost(i)) return false
    cur().money -= unmortgageCost(i); mort[i] = false
    addLog("${cur().name} levanta la hipoteca de ${MONO_SPACES[i].name}")
    return true
  }

  // ── cárcel ──
  fun payJail(): Boolean {
    val p = cur()
    if (phase != P_ROLL || p.jail < 0 || p.money < 50) return false
    p.money -= 50; p.jail = -1; if (rulePot) pot += 50
    addLog("${p.name} paga \$50 y sale de la cárcel")
    return true
  }

  fun useJailCard(): Boolean {
    val p = cur()
    if (phase != P_ROLL || p.jail < 0 || p.goojf <= 0) return false
    p.goojf--; p.jail = -1
    ccHeld = false; chHeld = false
    addLog("${p.name} usa su carta de salida de la cárcel")
    return true
  }

  // ── intercambios ──
  fun tradeValid(t: MTrade): String? {
    val a = players[t.from]; val b = players[t.to]
    if (a.bankrupt || b.bankrupt) return "Jugador fuera de juego"
    if (t.giveProps.any { owner[it] != t.from } || t.getProps.any { owner[it] != t.to }) return "Una propiedad ya no pertenece a quien la ofrece"
    for (i in t.giveProps + t.getProps) { val g = MONO_SPACES[i].group; if (MONO_GROUPS[g]?.any { houses[it] > 0 } == true) return "Hay edificios en el grupo de ${MONO_SPACES[i].name}: vendelos antes" }
    if (t.giveCash > a.money || t.getCash > b.money) return "No alcanza el efectivo"
    if (t.giveJail > a.goojf || t.getJail > b.goojf) return "Falta la carta de salida de la cárcel"
    if (t.giveProps.isEmpty() && t.getProps.isEmpty() && t.giveCash == 0 && t.getCash == 0 && t.giveJail == 0 && t.getJail == 0) return "El trato está vacío"
    return null
  }

  fun applyTrade(t: MTrade): Boolean {
    if (tradeValid(t) != null) return false
    val a = players[t.from]; val b = players[t.to]
    a.money += t.getCash - t.giveCash; b.money += t.giveCash - t.getCash
    a.goojf += t.getJail - t.giveJail; b.goojf += t.giveJail - t.getJail
    for (i in t.giveProps) { owner[i] = t.to; if (mort[i]) b.money -= min(b.money, (MONO_SPACES[i].price / 20)) }
    for (i in t.getProps) { owner[i] = t.from; if (mort[i]) a.money -= min(a.money, (MONO_SPACES[i].price / 20)) }
    addLog("Trato cerrado entre ${a.name} y ${b.name} 🤝")
    return true
  }

  // ── quiebra ──
  fun declareBankrupt() {
    if (phase != P_DEBT) return
    val p = cur(); val cred = debtTo
    for (i in ownedBy(turn)) if (houses[i] > 0) { p.money += houses[i] * (MONO_SPACES[i].houseCost / 2); houses[i] = 0 }
    if (cred >= 0) {
      players[cred].money += p.money
      for (i in ownedBy(turn)) {
        owner[i] = cred
        if (mort[i]) players[cred].money -= min(players[cred].money, MONO_SPACES[i].price / 20)
      }
      players[cred].goojf += p.goojf
    } else {
      for (i in ownedBy(turn)) { owner[i] = -1; mort[i] = false; aucQueue.add(i) }
      if (p.goojf > 0) { ccHeld = false; chHeld = false }
    }
    p.money = 0; p.goojf = 0; p.bankrupt = true
    debtAmt = 0; debtTo = -1; pendingMove = 0
    addLog("💥 ${p.name} quebró${if (cred >= 0) " ante ${players[cred].name}" else " ante el banco"}")
    finishAction()
  }

  // ═════════════════ Inteligencia de los bots ═════════════════
  private fun groupBonus(p: Int, i: Int): Int {
    val sp = MONO_SPACES[i]
    val g = MONO_GROUPS[sp.group] ?: return 0
    val mine = g.count { owner[it] == p }
    return if (mine == g.size - 1) 1 else 0
  }

  private fun wantsGroupPiece(p: Int, i: Int): Boolean = groupBonus(p, i) == 1

  fun botManage() {
    val p = cur()
    // levantar hipotecas si hay efectivo de sobra
    for (i in ownedBy(turn)) if (mort[i] && p.money > unmortgageCost(i) + 500) unmortgage(i)
    // construir parejo en los grupos más caros, dejando un colchón de efectivo
    var guard = 0
    while (guard++ < 12) {
      val cand = ownedBy(turn).filter { canBuild(it) && p.money - MONO_SPACES[it].houseCost >= 220 }.maxByOrNull { MONO_SPACES[it].price } ?: break
      build(cand)
    }
  }

  /** Vende y/o hipoteca hasta cubrir la deuda; si no alcanza, quiebra. */
  fun botRaiseCash(rnd: Random) {
    val p = cur()
    if (liquidation(turn) < debtAmt) { declareBankrupt(); return }
    var guard = 0
    while (p.money < debtAmt && guard++ < 80) {
      val sell = ownedBy(turn).filter { canSellBuilding(it) }.minByOrNull { MONO_SPACES[it].price }
      if (sell != null) { sellBuilding(sell); continue }
      val m = ownedBy(turn).filter { canMortgage(it) }.minByOrNull { MONO_SPACES[it].price } ?: break
      mortgage(m)
    }
    if (p.money >= debtAmt) payDebt(rnd) else declareBankrupt()
  }

  fun botBuyDecision(): Boolean {
    val p = cur(); val sp = MONO_SPACES[p.pos]
    if (p.money < sp.price) return false
    if (wantsGroupPiece(turn, sp.idx)) return true
    val reserve = if (alive().size > 3) 150 else 100
    return p.money - sp.price >= reserve
  }

  fun botAuctionStep() {
    val a = auc ?: return
    val who = aucActor(); if (who < 0) return
    val sp = MONO_SPACES[a.space]
    var maxBid = (sp.price * (if (wantsGroupPiece(who, sp.idx)) 1.6f else 1.05f)).toInt()
    maxBid = min(maxBid, players[who].money - 60)
    val next = a.bid + (if (sp.price >= 200) 20 else 10)
    if (next <= maxBid && next <= players[who].money) aucBid(next) else aucPass()
  }

  private fun propValue(i: Int, forPlayer: Int): Int {
    val sp = MONO_SPACES[i]
    return sp.price + (if (wantsGroupPiece(forPlayer, i)) sp.price else 0) + (if (sp.type == MT_RR) 40 else 0)
  }

  /** ¿Acepta el bot el trato? Quiere recibir al menos un 10 % más de valor del que entrega. */
  fun botAcceptsTrade(t: MTrade): Boolean {
    val me = t.to
    var gain = t.giveCash + t.giveJail * 50
    for (i in t.giveProps) gain += propValue(i, me)
    var loss = t.getCash + t.getJail * 50
    for (i in t.getProps) {
      val g = MONO_SPACES[i].group
      val grp = MONO_GROUPS[g] ?: emptyList()
      // si con esta pieza el rival completa un grupo, pide una compensación enorme
      val completes = grp.isNotEmpty() && grp.count { owner[it] == t.from || it in t.giveProps } == grp.size - 1
      loss += MONO_SPACES[i].price + (if (completes) 500 else 0)
    }
    return gain >= loss * 1.1f
  }

  /** Un bot intenta completar un grupo ofreciendo efectivo por la pieza que le falta. */
  fun botProposeTrade(rnd: Random): MTrade? {
    if (trade != null || rnd.nextInt(100) > 35) return null
    val p = cur()
    for ((g, members) in MONO_GROUPS) {
      if (g >= 8) continue
      val mine = members.count { owner[it] == turn }
      if (mine != members.size - 1) continue
      val miss = members.firstOrNull { owner[it] != turn && owner[it] >= 0 } ?: continue
      if (houses[miss] > 0) continue
      val offer = (MONO_SPACES[miss].price * 2f).toInt()
      if (p.money < offer + 100) continue
      val t = MTrade(turn, owner[miss])
      t.getProps.add(miss); t.giveCash = offer
      if (tradeValid(t) == null) return t
    }
    return null
  }

  // ═════════════════ Serialización (JSON) ═════════════════
  fun toJson(): String {
    val o = JSONObject()
    val pl = JSONArray()
    for (p in players) pl.put(JSONObject().put("n", p.name).put("u", p.uid).put("t", p.token).put("m", p.money).put("p", p.pos).put("j", p.jail).put("g", p.goojf).put("b", p.bankrupt))
    o.put("pl", pl)
    o.put("ow", JSONArray(owner.toList())); o.put("ho", JSONArray(houses.toList())); o.put("mo", JSONArray(mort.map { if (it) 1 else 0 }))
    o.put("tu", turn).put("ph", phase).put("d1", d1).put("d2", d2).put("dc", dblCount).put("ex", extra)
    o.put("da", debtAmt).put("dt", debtTo).put("pm", pendingMove)
    auc?.let { a -> o.put("au", JSONObject().put("s", a.space).put("b", a.bid).put("l", a.leader).put("a", JSONArray(a.active)).put("p", a.ptr)) }
    o.put("aq", JSONArray(aucQueue))
    trade?.let { t ->
      o.put("tr", JSONObject().put("f", t.from).put("t", t.to).put("gp", JSONArray(t.giveProps.toList())).put("rp", JSONArray(t.getProps.toList()))
        .put("gc", t.giveCash).put("rc", t.getCash).put("gj", t.giveJail).put("rj", t.getJail))
    }
    o.put("lg", JSONArray(log)); o.put("wn", winner)
    o.put("cco", JSONArray(ccOrder.toList())); o.put("cho", JSONArray(chOrder.toList()))
    o.put("cci", ccIdx).put("chi", chIdx).put("cch", ccHeld).put("chh", chHeld)
    o.put("pot", pot).put("rp", rulePot).put("ct", cardText).put("cn", cardTitle).put("ev", ev).put("rn", rollNo).put("rs", rollSeq).put("cs", cardSeq)
    return o.toString()
  }

  companion object {
    fun newGame(
      names: List<String>, uids: List<String>, rulePot: Boolean, rnd: Random,
      preferredToken: Int = -1, preferredUid: String = "", preferredTokens: Map<String, Int> = emptyMap()
    ): MState {
      val s = MState()
      val available = (0 until 8).shuffled(rnd).toMutableList()
      val assigned = mutableMapOf<String, Int>()
      // Respeta la ficha elegida por cada jugador remoto, sin duplicar fichas.
      val preferences = preferredTokens.toMutableMap()
      if (preferredUid.isNotBlank() && preferredToken in 0..7) preferences[preferredUid] = preferredToken
      for (uid in uids) {
        val wanted = preferences[uid]
        if (uid != "bot" && wanted != null && wanted in available) {
          assigned[uid] = wanted
          available.remove(wanted)
        }
      }
      for (i in names.indices) {
        val uid = uids.getOrElse(i) { "bot" }
        val token = assigned[uid] ?: available.removeAt(0).also { if (uid != "bot") assigned[uid] = it }
        s.players.add(MPlayer(names[i], uid, token))
      }
      s.rulePot = rulePot
      s.ccOrder = (0 until 16).shuffled(rnd).toIntArray(); s.chOrder = (0 until 16).shuffled(rnd).toIntArray()
      s.turn = 0; s.phase = P_ROLL
      s.addLog("¡Empieza el Monopoly! Cada jugador recibe \$1.500")
      return s
    }

    fun fromJson(js: String): MState? = runCatching {
      val o = JSONObject(js)
      val s = MState()
      val pl = o.getJSONArray("pl")
      for (i in 0 until pl.length()) {
        val j = pl.getJSONObject(i)
        val p = MPlayer(j.getString("n"), j.getString("u"), j.getInt("t"))
        p.money = j.getInt("m"); p.pos = j.getInt("p"); p.jail = j.getInt("j"); p.goojf = j.getInt("g"); p.bankrupt = j.getBoolean("b")
        s.players.add(p)
      }
      val ow = o.getJSONArray("ow"); val ho = o.getJSONArray("ho"); val mo = o.getJSONArray("mo")
      for (i in 0 until 40) { s.owner[i] = ow.getInt(i); s.houses[i] = ho.getInt(i); s.mort[i] = mo.getInt(i) == 1 }
      s.turn = o.getInt("tu"); s.phase = o.getInt("ph"); s.d1 = o.getInt("d1"); s.d2 = o.getInt("d2"); s.dblCount = o.getInt("dc"); s.extra = o.getBoolean("ex")
      s.debtAmt = o.getInt("da"); s.debtTo = o.getInt("dt"); s.pendingMove = o.getInt("pm")
      o.optJSONObject("au")?.let { a ->
        val act = ArrayList<Int>(); val aa = a.getJSONArray("a"); for (i in 0 until aa.length()) act.add(aa.getInt(i))
        s.auc = MAuction(a.getInt("s"), a.getInt("b"), a.getInt("l"), act, a.getInt("p"))
      }
      val aq = o.getJSONArray("aq"); for (i in 0 until aq.length()) s.aucQueue.add(aq.getInt(i))
      o.optJSONObject("tr")?.let { t ->
        val tr = MTrade(t.getInt("f"), t.getInt("t"))
        val gp = t.getJSONArray("gp"); for (i in 0 until gp.length()) tr.giveProps.add(gp.getInt(i))
        val rp = t.getJSONArray("rp"); for (i in 0 until rp.length()) tr.getProps.add(rp.getInt(i))
        tr.giveCash = t.getInt("gc"); tr.getCash = t.getInt("rc"); tr.giveJail = t.getInt("gj"); tr.getJail = t.getInt("rj")
        s.trade = tr
      }
      val lg = o.getJSONArray("lg"); for (i in 0 until lg.length()) s.log.add(lg.getString(i))
      s.winner = o.getInt("wn")
      val cco = o.getJSONArray("cco"); val cho = o.getJSONArray("cho")
      s.ccOrder = IntArray(16) { cco.getInt(it) }; s.chOrder = IntArray(16) { cho.getInt(it) }
      s.ccIdx = o.getInt("cci"); s.chIdx = o.getInt("chi"); s.ccHeld = o.getBoolean("cch"); s.chHeld = o.getBoolean("chh")
      s.pot = o.getInt("pot"); s.rulePot = o.getBoolean("rp"); s.cardText = o.getString("ct"); s.cardTitle = o.getString("cn"); s.ev = o.getInt("ev"); s.rollNo = o.getInt("rn"); s.rollSeq = o.optInt("rs", 0); s.cardSeq = o.optInt("cs", 0)
      s
    }.getOrNull()
  }
}

