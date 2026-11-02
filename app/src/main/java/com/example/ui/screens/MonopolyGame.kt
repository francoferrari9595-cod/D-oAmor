package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.GlassCard
import com.example.ui.components.GradientButton
import com.example.ui.components.SoftButton
import com.example.ui.theme.GoldYellow
import kotlinx.coroutines.delay
import kotlin.random.Random

// ═══════════════════════════════════════════════════════════════════════════════
//  MONOPOLY · pantalla del juego
//  Usa EXACTAMENTE el tablero (monopoly_tablero.jpg) y los billetes (monopoly_billete_N.png) que subiste.
//  Fichas: monopoly_ficha_1 … monopoly_ficha_8 (tus imágenes). Si falta alguna, se dibuja un disco con su símbolo.
// ═══════════════════════════════════════════════════════════════════════════════

private val GROUP_COLORS = listOf(
  Color(0xFF955436), Color(0xFFAAE0FA), Color(0xFFD93A96), Color(0xFFF7941D), Color(0xFFED1B24),
  Color(0xFFFEF200), Color(0xFF1FB25A), Color(0xFF0072BB), Color(0xFF222222), Color(0xFFBBBBBB)
)
private val PLAYER_COLORS = listOf(
  Color(0xFFE53935), Color(0xFF1E88E5), Color(0xFF43A047), Color(0xFFFDD835), Color(0xFF8E24AA), Color(0xFFFB8C00), Color(0xFF00ACC1), Color(0xFFEC407A)
)
private val TOKEN_EMOJI = listOf("🎩", "🏎️", "🐕", "🧵", "🐈", "🚢", "🐧", "🦆")
private val TOKEN_NAME = listOf("Sombrero de copa", "Auto de carreras", "Perro Scottie", "Dedal", "Gato", "Barco", "Pingüino", "Patito de goma")
private val BILLS = listOf(500, 100, 50, 20, 10, 5, 1)

private const val BOARD_PX = 1500f
private const val CORNER = 197f
private const val CELL = 122.9f

/** Centro del casillero `i` en fracciones (0..1) de la imagen del tablero. */
private fun spaceCenter(i: Int): Pair<Float, Float> {
  val x: Float; val y: Float
  when {
    i == 0 -> { x = 1401.5f; y = 1401.5f }
    i in 1..9 -> { x = 1303f - (i - 0.5f) * CELL; y = 1402f }
    i == 10 -> { x = 98.5f; y = 1401.5f }
    i in 11..19 -> { x = 98.5f; y = 1303f - (i - 10 - 0.5f) * CELL }
    i == 20 -> { x = 98.5f; y = 98.5f }
    i in 21..29 -> { x = CORNER + (i - 20 - 0.5f) * CELL; y = 98.5f }
    i == 30 -> { x = 1401.5f; y = 98.5f }
    else -> { x = 1401.5f; y = CORNER + (i - 30 - 0.5f) * CELL }
  }
  return Pair(x / BOARD_PX, y / BOARD_PX)
}

/** Casillero tocado en (fx, fy) fracciones de la imagen, o -1 si es el centro. */
private fun spaceAt(fx: Float, fy: Float): Int {
  val px = fx * BOARD_PX; val py = fy * BOARD_PX
  return when {
    py >= 1303f -> if (px >= 1303f) 0 else if (px < CORNER) 10 else (1 + ((1303f - px) / CELL).toInt()).coerceIn(1, 9)
    py < CORNER -> if (px < CORNER) 20 else if (px >= 1303f) 30 else (21 + ((px - CORNER) / CELL).toInt()).coerceIn(21, 29)
    px < CORNER -> (11 + ((1303f - py) / CELL).toInt()).coerceIn(11, 19)
    px >= 1303f -> (31 + ((py - CORNER) / CELL).toInt()).coerceIn(31, 39)
    else -> -1
  }
}

/** Posición (en fracciones) de la franja de color de un casillero, donde se apoyan las casas. */
private fun stripCenter(i: Int): Pair<Float, Float> {
  val (cx, cy) = spaceCenter(i)
  return when {
    i in 1..9 -> Pair(cx, 1326f / BOARD_PX)
    i in 11..19 -> Pair(175f / BOARD_PX, cy)
    i in 21..29 -> Pair(cx, 175f / BOARD_PX)
    else -> Pair(1326f / BOARD_PX, cy)
  }
}

private fun ownerDot(i: Int): Pair<Float, Float> {
  val (cx, cy) = spaceCenter(i)
  return when {
    i in 1..9 -> Pair(cx, 1436f / BOARD_PX)
    i in 11..19 -> Pair(62f / BOARD_PX, cy)
    i in 21..29 -> Pair(cx, 62f / BOARD_PX)
    else -> Pair(1440f / BOARD_PX, cy)
  }
}

private fun money(n: Int): String = "\$" + "%,d".format(n).replace(',', '.')

@Composable
private fun resId(name: String): Int {
  val ctx = LocalContext.current
  return remember(name) { ctx.resources.getIdentifier(name, "drawable", ctx.packageName) }
}

@Composable
private fun Token(idx: Int, size: Dp, modifier: Modifier = Modifier) {
  val rid = resId("monopoly_ficha_${idx + 1}")
  if (rid != 0) {
    Image(painterResource(rid), TOKEN_NAME[idx], modifier.size(size), contentScale = ContentScale.Fit)
  } else {
    Box(
      modifier.size(size).shadow(3.dp, CircleShape).clip(CircleShape)
        .background(Brush.radialGradient(listOf(PLAYER_COLORS[idx].copy(alpha = 1f), Color(0xFF111111)), radius = size.value * 2.2f))
        .border(1.5.dp, Color.White, CircleShape),
      contentAlignment = Alignment.Center
    ) { Text(TOKEN_EMOJI[idx], fontSize = (size.value * 0.58f).sp) }
  }
}

@Composable
private fun DiceFace(v: Int, size: Dp) {
  Canvas(Modifier.size(size).shadow(5.dp, RoundedCornerShape(size / 5)).clip(RoundedCornerShape(size / 5)).background(Color.White)) {
    val w = this.size.width
    val pips = when (v) {
      1 -> listOf(4); 2 -> listOf(0, 8); 3 -> listOf(0, 4, 8); 4 -> listOf(0, 2, 6, 8); 5 -> listOf(0, 2, 4, 6, 8); else -> listOf(0, 2, 3, 5, 6, 8)
    }
    for (p in pips) drawCircle(Color(0xFF1B1B1B), w * 0.085f, Offset(w * (0.25f + 0.25f * (p % 3)), w * (0.25f + 0.25f * (p / 3))))
  }
}

@Composable
internal fun MonopolyGame(c: GameCtx) {
  val flow = remember { c.vm.gameState("mono") }
  val st by flow.collectAsState(initial = null)
  val liveFlow = remember { c.vm.liveStates() }
  val liveStates by liveFlow.collectAsState(initial = emptyMap())
  var state by remember { mutableStateOf<MState?>(null) }
  var tick by remember { mutableStateOf(0) }
  var ver by remember { mutableStateOf(0L) }
  val shown = remember { mutableStateListOf<Int>() }
  var rolling by remember { mutableStateOf(false) }
  var rv by remember { mutableStateOf(Pair(1, 6)) }
  var deed by remember { mutableStateOf(-1) }
  var showManage by remember { mutableStateOf(false) }
  var showTrade by remember { mutableStateOf(false) }
  var showMoney by remember { mutableStateOf(false) }
  var showLog by remember { mutableStateOf(false) }
  var showCard by remember { mutableStateOf(false) }
  var botsSel by remember { mutableStateOf(maxOf(c.lobbyBots, if (c.players.size <= 1) 1 else 0).coerceAtMost(8 - c.players.size)) }
  val context = LocalContext.current
  var selectedToken by remember { mutableStateOf(context.getSharedPreferences("doamor_prefs", android.content.Context.MODE_PRIVATE).getInt("monopoly_token", 0).coerceIn(0, 7)) }
  var potRule by remember { mutableStateOf(false) }
  var botProposedAt by remember { mutableStateOf(-1) }
  val isHost = c.me == c.hostUid
  val isPlayer = c.players.contains(c.me)
  @Suppress("UNUSED_VARIABLE") val subscribe = tick

  // ── sincronización: se acepta el estado remoto solo si es más nuevo que el local ──
  LaunchedEffect(st) {
    val js = st?.get("s") as? String
    val v = (st?.get("v") as? Number)?.toLong() ?: 0L
    if (js == null) {
      if (state != null) { state = null; ver = 0L; shown.clear(); tick++ }
    } else if (v > ver) {
      MState.fromJson(js)?.let { state = it; ver = v; tick++ }
    }
  }

  fun commit(f: (MState) -> Unit) {
    val s = state ?: return
    f(s)
    ver += 1; tick++
    c.vm.publishGame("mono", mapOf("s" to s.toJson(), "v" to ver))
  }

  fun myName(): String = c.vm.roomDisplayName().ifBlank { "Jugador" }
  LaunchedEffect(selectedToken, c.me) {
    // Cada jugador publica su preferencia para que el anfitrión pueda respetarla al crear la partida.
    delay(300)
    c.vm.publishLive(mapOf("monoToken" to selectedToken, "monoTokenAt" to System.currentTimeMillis(), "on" to true))
  }

  fun startGame() {
    val names = ArrayList<String>(); val uids = ArrayList<String>()
    for (u in c.players) { names.add(if (u == c.me) myName() else (c.names[u] ?: c.partnerName)); uids.add(u) }
    for (b in 1..botsSel) { names.add("Bot $b"); uids.add("bot") }
    if (names.size < 2) { names.add("Bot ${botsSel + 1}"); uids.add("bot") }
    val tokenPreferences = liveStates.mapNotNull { (uid, values) ->
      val token = (values["monoToken"] as? Number)?.toInt()
      val selectedAt = (values["monoTokenAt"] as? Number)?.toLong() ?: 0L
      if (uid in uids && token != null && token in 0..7 && System.currentTimeMillis() - selectedAt < 120_000L) uid to token else null
    }.toMap()
    val s = MState.newGame(names.take(8), uids.take(8), potRule, Random.Default, selectedToken, c.me, tokenPreferences)
    s.turn = Random.nextInt(s.players.size)
    s.addLog("Empieza ${s.players[s.turn].name}")
    ver = System.currentTimeMillis() / 1000L
    state = s; tick++
    c.vm.publishGame("mono", mapOf("s" to s.toJson(), "v" to ver))
  }

  // ── dados que ruedan + sonido ──
  val rollSeq = state?.rollSeq ?: 0
  LaunchedEffect(rollSeq) {
    if (rollSeq > 0) { rolling = true; c.vm.gameSfx(0); delay(1750); rolling = false }
  }
  LaunchedEffect(rolling) { while (rolling) { rv = Pair(Random.nextInt(1, 7), Random.nextInt(1, 7)); delay(72) } }

  // ── las fichas caminan casillero por casillero ──
  LaunchedEffect(tick) {
    val ss = state ?: return@LaunchedEffect
    while (shown.size < ss.players.size) shown.add(ss.players[shown.size].pos)
    for (i in ss.players.indices) {
      var guard = 0
      while (shown[i] != ss.players[i].pos && guard++ < 45) {
        val target = ss.players[i].pos
        val fwd = (target - shown[i] + 40) % 40
        if (fwd > 14) { shown[i] = target; break }
        shown[i] = (shown[i] + 1) % 40
        delay(205)
      }
      shown[i] = ss.players[i].pos
    }
  }

  // ── los bots juegan (solo el anfitrión los mueve) ──
  LaunchedEffect(tick) {
    val s = state ?: return@LaunchedEffect
    if (!isHost || s.phase == P_OVER) return@LaunchedEffect
    val rnd = Random.Default
    val tr = s.trade
    if (tr != null) {
      if (s.players[tr.to].uid == "bot") {
        delay(1300)
        commit { s2 ->
          val t = s2.trade
          if (t != null) {
            if (s2.botAcceptsTrade(t) && s2.applyTrade(t)) { /* trato cerrado */ } else s2.addLog("${s2.players[t.to].name} rechaza el trato")
            s2.trade = null
          }
        }
      }
      return@LaunchedEffect
    }
    if (s.phase == P_AUC) {
      val a = s.aucActor()
      if (a >= 0 && s.players[a].uid == "bot") { delay(950); commit { it.botAuctionStep() } }
      return@LaunchedEffect
    }
    if (s.cur().uid != "bot") return@LaunchedEffect
    delay(if (s.phase == P_ROLL) 1100L else 850L)
    when (s.phase) {
      P_ROLL -> commit {
        val p = it.cur()
        if (p.jail >= 0) { if (p.goojf > 0) it.useJailCard() else if (p.money > 450) it.payJail() }
        it.botManage(); it.roll(rnd)
      }
      P_BUY -> commit { if (it.botBuyDecision()) it.buy() else it.declineBuy() }
      P_DEBT -> commit { it.botRaiseCash(rnd) }
      P_END -> {
        val prop = if (botProposedAt != s.rollNo) s.botProposeTrade(rnd) else null
        if (prop != null) {
          botProposedAt = s.rollNo
          commit { it.trade = prop; it.addLog("${it.players[prop.from].name} propone un trato a ${it.players[prop.to].name}") }
        } else commit { it.botManage(); it.endTurn() }
      }
    }
  }

  // ═══════════════ Pantalla de preparación ═══════════════
  val s = state
  if (s == null) {
    GlassCard(modifier = Modifier.fillMaxWidth(), highlight = true) {
      Text("🎩 MONOPOLY", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
      Text(
        "Reglamento oficial de Hasbro: 2 a 8 jugadores, \$1.500 cada uno, dados dobles, cárcel, propiedades, estaciones y servicios, casas y hoteles, hipotecas, subastas, intercambios, cartas de Suerte y Arca comunal, impuestos y quiebra. Gana el último que queda con dinero.",
        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp
      )
      Spacer(Modifier.height(8.dp))
      Text("Humanos en la mesa: " + c.players.joinToString(", ") { if (it == c.me) "Tú" else (c.names[it] ?: c.partnerName) }, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
      if (isPlayer) {
        Text("Elegí tu ficha", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
          TOKEN_NAME.indices.forEach { idx ->
            Column(
              Modifier.clip(RoundedCornerShape(12.dp)).background(if (selectedToken == idx) Color(0x5548C774) else Color(0x22000000))
                .border(if (selectedToken == idx) 2.dp else 1.dp, if (selectedToken == idx) GoldYellow else Color(0x55FFFFFF), RoundedCornerShape(12.dp))
                .clickable { selectedToken = idx; context.getSharedPreferences("doamor_prefs", android.content.Context.MODE_PRIVATE).edit().putInt("monopoly_token", idx).apply() }
                .padding(horizontal = 8.dp, vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Token(idx, 34.dp)
              Text(TOKEN_NAME[idx], color = Color.White, fontSize = 9.sp, maxLines = 1)
            }
          }
        }
        val maxBots = (8 - c.players.size).coerceAtLeast(0)
        val minBots = if (c.players.size <= 1) 1 else 0
        Spacer(Modifier.height(8.dp))
        Text("Bots", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          (minBots..maxBots).forEach { n -> if (botsSel == n) GradientButton("$n", onClick = {}, height = 38.dp) else SoftButton("$n", onClick = { botsSel = n }) }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { potRule = !potRule }) {
          Text(if (potRule) "☑" else "☐", color = GoldYellow, fontSize = 22.sp)
          Spacer(Modifier.width(8.dp))
          Column {
            Text("Regla de la casa: Parada libre acumula el dinero", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text("Apagada = reglamento oficial (Parada libre no da nada).", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
          }
        }
        Spacer(Modifier.height(10.dp))
        GradientButton("🎲 Empezar la partida", modifier = Modifier.fillMaxWidth(), onClick = { startGame() })
      } else Text("Esperando que empiecen la partida…", color = GoldYellow, fontSize = 12.sp)
    }
    return
  }

  // ═══════════════ Partida (todo en una sola pantalla, sin deslizar) ═══════════════
  val myIdx = s.players.indexOfFirst { it.uid == c.me }
  val cur = s.cur()
  val myTurn = myIdx >= 0 && s.turn == myIdx && s.phase != P_OVER
  val aucActor = if (s.phase == P_AUC) s.aucActor() else -1
  val tr = s.trade
  val avail = (gameAvail() - 60.dp).coerceAtLeast(380.dp)

  LaunchedEffect(s.cardSeq) { if (s.cardSeq > 0) { showCard = true; delay(4200); showCard = false } }

  BoxWithConstraints(Modifier.fillMaxWidth().height(avail)) {
    val boardSize = minOf(maxWidth, maxHeight - 214.dp).coerceAtLeast(180.dp)
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      // jugadores
      Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        s.players.forEachIndexed { i, p ->
          val active = i == s.turn && !p.bankrupt
          Row(
            Modifier.clip(RoundedCornerShape(12.dp))
              .background(if (active) Brush.horizontalGradient(listOf(PLAYER_COLORS[p.token].copy(alpha = 0.55f), Color(0x33000000))) else Brush.horizontalGradient(listOf(Color(0x33FFFFFF), Color(0x14FFFFFF))))
              .border(if (active) 2.dp else 1.dp, if (active) GoldYellow else Color(0x44FFFFFF), RoundedCornerShape(12.dp)).padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)
          ) {
            Token(p.token, 24.dp)
            Column {
              Text(p.name + if (i == myIdx) " (tú)" else "", color = if (p.bankrupt) Color(0x88FFFFFF) else Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
              Text(if (p.bankrupt) "💥 quebrado" else money(p.money) + (if (p.jail >= 0) " 🚔" else "") + (if (p.goojf > 0) " 🎟️" else ""), color = if (p.bankrupt) Color(0x88FFFFFF) else GoldYellow, fontSize = 11.sp, fontWeight = FontWeight.Black, maxLines = 1)
            }
          }
        }
      }

      // tablero
      Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        BoxWithConstraints(Modifier.size(boardSize).clip(RoundedCornerShape(8.dp)).border(2.dp, Color(0xFF2E7D32), RoundedCornerShape(8.dp))) {
          val bw = maxWidth
          val boardId = resId("monopoly_tablero")
          if (boardId != 0) Image(painterResource(boardId), "Tablero de Monopoly", Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
          else Box(Modifier.fillMaxSize().background(Color(0xFFCDE6D0)))
          Canvas(Modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures { off -> val i = spaceAt(off.x / size.width, off.y / size.height); if (i >= 0) deed = i }
          }) {
            val w = size.width
            val k = w / BOARD_PX
            val curC = spaceCenter(s.players.getOrNull(s.turn)?.pos ?: 0)
            drawRoundRect(Color(0x66FFD60A), Offset((curC.first * BOARD_PX - 60f) * k, (curC.second * BOARD_PX - 60f) * k), Size(120f * k, 120f * k), CornerRadius(14f * k))
            for (i in 0 until 40) {
              val o = s.owner[i]; if (o < 0) continue
              val d = ownerDot(i); val dc = Offset(d.first * w, d.second * w)
              val col = PLAYER_COLORS[s.players[o].token]
              drawCircle(Color.White, 17f * k, dc); drawCircle(col, 13f * k, dc)
              if (s.mort[i]) { drawLine(Color.Black, Offset(dc.x - 13f * k, dc.y - 13f * k), Offset(dc.x + 13f * k, dc.y + 13f * k), 4f * k); drawCircle(Color(0xFFE53935), 17f * k, dc, style = androidx.compose.ui.graphics.drawscope.Stroke(3f * k)) }
              val h = s.houses[i]
              if (h > 0) {
                val sc = stripCenter(i); val horiz = i in 1..9 || i in 21..29
                if (h == 5) {
                  val bwid = 54f * k; val bh = 24f * k
                  if (horiz) drawRoundRect(Color(0xFFD32F2F), Offset(sc.first * w - bwid / 2f, sc.second * w - bh / 2f), Size(bwid, bh), CornerRadius(4f * k))
                  else drawRoundRect(Color(0xFFD32F2F), Offset(sc.first * w - bh / 2f, sc.second * w - bwid / 2f), Size(bh, bwid), CornerRadius(4f * k))
                } else for (q in 0 until h) {
                  val off2 = (q - (h - 1) / 2f) * 28f * k
                  val cx = if (horiz) sc.first * w + off2 else sc.first * w
                  val cy = if (horiz) sc.second * w else sc.second * w + off2
                  drawRoundRect(Color(0xFF2E7D32), Offset(cx - 11f * k, cy - 11f * k), Size(22f * k, 22f * k), CornerRadius(3f * k))
                  drawRoundRect(Color.White, Offset(cx - 11f * k, cy - 11f * k), Size(22f * k, 22f * k), CornerRadius(3f * k), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f * k))
                }
              }
            }
          }
          val tokSize = bw * 0.078f
          s.players.forEachIndexed { i, p ->
            if (p.bankrupt) return@forEachIndexed
            val pos = shown.getOrNull(i) ?: p.pos
            var (fx, fy) = spaceCenter(pos)
            if (pos == 10) { if (p.jail >= 0) { fx = 128f / BOARD_PX; fy = 1372f / BOARD_PX } else { fx = 34f / BOARD_PX; fy = 1450f / BOARD_PX } }
            val ox = ((i % 4) - 1.5f) * 0.017f; val oy = ((i / 4) - 0.5f) * 0.026f
            Token(p.token, tokSize, Modifier.offset(x = bw * (fx + ox) - tokSize / 2, y = bw * (fy + oy) - tokSize / 2 - tokSize * 0.15f))
          }
          // centro: dados + turno
          Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              DiceFace(if (rolling) rv.first else s.d1.coerceAtLeast(1), bw * 0.11f)
              DiceFace(if (rolling) rv.second else s.d2.coerceAtLeast(1), bw * 0.11f)
            }
            Text(
              if (s.phase == P_OVER) "🏆 Fin" else "Turno de ${cur.name}", color = Color.White, fontWeight = FontWeight.Black, fontSize = 11.sp, maxLines = 1,
              modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(50)).background(Color(0xAA000000)).padding(horizontal = 10.dp, vertical = 2.dp)
            )
            if (s.rulePot && s.pot > 0) Text("Pozo: ${money(s.pot)}", color = GoldYellow, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp).clip(RoundedCornerShape(50)).background(Color(0xAA000000)).padding(horizontal = 8.dp, vertical = 1.dp))
          }
          // carta de Suerte / Arca comunal que acaba de salir
          if (showCard && s.cardText.isNotBlank()) {
            Column(
              Modifier.align(Alignment.Center).fillMaxWidth(0.72f).shadow(8.dp, RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp))
                .background(if (s.cardTitle == "Suerte") Color(0xFFFB8C00) else Color(0xFF1E88E5)).clickable { showCard = false }.padding(10.dp),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Text(if (s.cardTitle == "Suerte") "❓ SUERTE" else "📦 ARCA COMUNAL", color = Color.White, fontWeight = FontWeight.Black, fontSize = 11.sp)
              Text(s.cardText, color = Color.White, fontSize = 11.sp, textAlign = TextAlign.Center)
            }
          }
        }
      }

      // panel de acciones (compacto)
      MPanel(myTurn || aucActor == myIdx) {
        when {
          s.phase == P_OVER -> {
            val wn = s.players.getOrNull(s.winner)
            Text("🏆 ¡Ganó ${wn?.name ?: "nadie"}!", color = GoldYellow, fontWeight = FontWeight.Black, fontSize = 17.sp)
            Text(s.players.indices.sortedByDescending { s.assets(it) }.joinToString(" · ") { "${s.players[it].name} ${money(s.assets(it))}" }, color = Color.White, fontSize = 10.sp, maxLines = 2)
            if (isPlayer) GradientButton("Nueva partida", modifier = Modifier.fillMaxWidth(), height = 38.dp, onClick = { c.vm.publishGame("mono", emptyMap()) })
          }
          tr != null -> {
            val f = s.players[tr.from]; val t = s.players[tr.to]
            Text("🤝 ${f.name} → ${t.name}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text(tradeText(s, tr), color = Color(0xFFE2D8F5), fontSize = 10.sp, maxLines = 3)
            if (tr.from == myIdx) {
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Esperando respuesta…", color = GoldYellow, fontSize = 11.sp, modifier = Modifier.weight(1f))
                SoftButton("Cancelar", onClick = { commit { it.trade = null } })
              }
            } else if (tr.to == myIdx) {
              val err = s.tradeValid(tr)
              if (err != null) Text(err, color = Color(0xFFFF8A80), fontSize = 10.sp)
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GradientButton("Aceptar", modifier = Modifier.weight(1f), height = 38.dp, enabled = err == null, onClick = { commit { it.applyTrade(tr); it.trade = null } })
                SoftButton("Rechazar", onClick = { commit { it.addLog("${t.name} rechaza el trato"); it.trade = null } }, modifier = Modifier.weight(1f))
              }
            }
          }
          s.phase == P_AUC -> {
            val a = s.auc!!; val sp = MONO_SPACES[a.space]
            Text("🔨 ${sp.name} (${money(sp.price)})", color = Color.White, fontWeight = FontWeight.Black, fontSize = 13.sp, maxLines = 1)
            Text(if (a.leader >= 0) "Oferta: ${money(a.bid)} de ${s.players[a.leader].name}" else "Todavía nadie ofertó", color = GoldYellow, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            if (aucActor == myIdx) {
              val me = s.players[myIdx]
              Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (inc in listOf(10, 50, 100, 200)) {
                  val amt = a.bid + inc
                  GradientButton("+$inc", enabled = amt <= me.money, onClick = { commit { it.aucBid(amt) } }, height = 38.dp)
                }
                SoftButton("Retirarme", onClick = { commit { it.aucPass() } })
              }
            } else Text("Ofertando: ${s.players.getOrNull(aucActor)?.name ?: "-"}", color = Color(0xFFE2D8F5), fontSize = 11.sp)
          }
          !myTurn -> {
            Text("Turno de ${cur.name}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(phaseHint(s), color = Color(0xFFE2D8F5), fontSize = 11.sp)
            Text("• " + (s.log.lastOrNull() ?: ""), color = Color(0xFFC9BEDD), fontSize = 10.sp, maxLines = 2)
          }
          s.phase == P_ROLL -> {
            if (cur.jail >= 0) {
              Text("🚔 En la cárcel (intento ${cur.jail + 1}/3)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
              Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SoftButton("Pagar \$50", enabled = cur.money >= 50, onClick = { commit { it.payJail() } })
                if (cur.goojf > 0) SoftButton("Carta 🎟️", onClick = { commit { it.useJailCard() } })
                GradientButton("🎲 Tirar (dobles)", onClick = { commit { it.roll(Random.Default) } }, height = 38.dp)
              }
            } else {
              Text(if (s.dblCount > 0) "¡Dobles! Tirá de nuevo" else "Es tu turno", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
              GradientButton("🎲 Tirar los dados", modifier = Modifier.fillMaxWidth(), height = 40.dp, onClick = { commit { it.roll(Random.Default) } })
            }
          }
          s.phase == P_BUY -> {
            val sp = MONO_SPACES[cur.pos]
            Text("${sp.name} · ${money(sp.price)} (tenés ${money(cur.money)})", color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp, maxLines = 1)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              GradientButton("Comprar", modifier = Modifier.weight(1f), height = 40.dp, enabled = cur.money >= sp.price, onClick = { commit { it.buy() } })
              SoftButton("Subastar", onClick = { commit { it.declineBuy() } }, modifier = Modifier.weight(1f))
            }
          }
          s.phase == P_DEBT -> {
            val cr = if (s.debtTo >= 0) s.players[s.debtTo].name else "el banco"
            Text("💸 Debés ${money(s.debtAmt)} a $cr · tenés ${money(cur.money)}", color = Color(0xFFFF8A80), fontWeight = FontWeight.Black, fontSize = 12.sp, maxLines = 2)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              GradientButton("Pagar", enabled = cur.money >= s.debtAmt, onClick = { commit { it.payDebt(Random.Default) } }, height = 38.dp)
              SoftButton("Vender / hipotecar", onClick = { showManage = true })
              SoftButton("💥 Quiebra", onClick = { commit { it.declareBankrupt() } })
            }
          }
          else -> {
            Text("Tu turno terminó", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            GradientButton("✅ Terminar turno", modifier = Modifier.fillMaxWidth(), height = 40.dp, onClick = { commit { it.endTurn() } })
          }
        }
      }

      // barra inferior
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        SoftButton("🏠 Props", onClick = { showManage = true }, modifier = Modifier.weight(1f), enabled = myIdx >= 0)
        SoftButton("🤝 Trato", onClick = { showTrade = true }, modifier = Modifier.weight(1f), enabled = myTurn && tr == null && (s.phase == P_ROLL || s.phase == P_END) && s.alive().size > 1)
        SoftButton("💵 Plata", onClick = { showMoney = true }, modifier = Modifier.weight(1f), enabled = myIdx >= 0)
        SoftButton("📜 Log", onClick = { showLog = true }, modifier = Modifier.weight(1f))
      }
    }
  }

  // ═══════════════ Diálogos ═══════════════
  if (deed >= 0) DeedDialog(s, deed) { deed = -1 }
  if (showMoney && myIdx >= 0) {
    AlertDialog(
      onDismissRequest = { showMoney = false },
      confirmButton = { TextButton(onClick = { showMoney = false }) { Text("Cerrar") } },
      title = { Text("💵 Tu dinero: ${money(s.players[myIdx].money)}") },
      text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          var rest = s.players[myIdx].money
          for (d in BILLS) {
            val n = rest / d; rest -= n * d
            if (n <= 0) continue
            val rid = resId("monopoly_billete_$d")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              if (rid != 0) Image(painterResource(rid), "Billete de $d", Modifier.width(96.dp).height(50.dp), contentScale = ContentScale.Fit)
              else Box(Modifier.width(96.dp).height(50.dp).background(Color(0xFFE8D9A8)), contentAlignment = Alignment.Center) { Text("$d") }
              Text("× $n", fontWeight = FontWeight.Black, fontSize = 18.sp)
            }
          }
        }
      }
    )
  }
  if (showLog) {
    AlertDialog(
      onDismissRequest = { showLog = false },
      confirmButton = { TextButton(onClick = { showLog = false }) { Text("Cerrar") } },
      title = { Text("📜 Últimas jugadas") },
      text = { Column(Modifier.verticalScroll(rememberScrollState())) { s.log.takeLast(30).reversed().forEach { Text("• $it", fontSize = 12.sp) } } }
    )
  }
  if (showManage && myIdx >= 0) {
    ManageDialog(s, myIdx, myTurn && (s.phase == P_ROLL || s.phase == P_END || s.phase == P_DEBT), { f -> commit(f) }) { showManage = false }
  }
  if (showTrade && myTurn && s.trade == null) {
    TradeDialog(s, myIdx, { t -> commit { it.trade = t; it.addLog("${it.players[t.from].name} propone un trato a ${it.players[t.to].name}") } }) { showTrade = false }
  }
}

@Composable
private fun MPanel(highlight: Boolean, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
  Column(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Brush.verticalGradient(listOf(Color(0x33FFFFFF), Color(0x14FFFFFF))))
      .border(if (highlight) 2.dp else 1.dp, if (highlight) GoldYellow else Color(0x44FFFFFF), RoundedCornerShape(14.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp), content = content
  )
}

private fun phaseHint(s: MState): String = when (s.phase) {
  P_ROLL -> if (s.cur().jail >= 0) "Está en la cárcel" else "Va a tirar los dados"
  P_BUY -> "Decide si compra ${MONO_SPACES[s.cur().pos].name}"
  P_DEBT -> "Debe dinero y está juntando efectivo"
  P_END -> "Está terminando su turno"
  else -> ""
}

private fun tradeText(s: MState, t: MTrade): String {
  fun lst(ps: Set<Int>, cash: Int, jail: Int): String {
    val parts = ArrayList<String>()
    ps.sorted().forEach { parts.add(MONO_SPACES[it].name) }
    if (cash > 0) parts.add(money(cash))
    if (jail > 0) parts.add("carta de cárcel")
    return if (parts.isEmpty()) "nada" else parts.joinToString(", ")
  }
  return "${s.players[t.from].name} da: ${lst(t.giveProps, t.giveCash, t.giveJail)}\n${s.players[t.to].name} da: ${lst(t.getProps, t.getCash, t.getJail)}"
}

@Composable
private fun ManageRow(s: MState, onManage: () -> Unit, onTrade: () -> Unit) {
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    SoftButton("🏠 Propiedades", onClick = onManage, modifier = Modifier.weight(1f))
    SoftButton("🤝 Comerciar", onClick = onTrade, modifier = Modifier.weight(1f), enabled = s.alive().size > 1)
  }
}

@Composable
private fun DeedDialog(s: MState, i: Int, onClose: () -> Unit) {
  val sp = MONO_SPACES[i]
  AlertDialog(
    onDismissRequest = onClose,
    confirmButton = { TextButton(onClick = onClose) { Text("Cerrar") } },
    title = null,
    text = {
      Column(Modifier.verticalScroll(rememberScrollState())) {
        val gc = if (sp.group >= 0) GROUP_COLORS[sp.group] else Color(0xFF555555)
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(gc).padding(10.dp)) {
          Text(sp.name.uppercase(), color = if (sp.group == 1 || sp.group == 5 || sp.group == 9) Color.Black else Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(8.dp))
        when (sp.type) {
          MT_PROP -> {
            Text("Alquiler ${money(sp.rents[0])}", fontWeight = FontWeight.Bold)
            Text("Con grupo completo (sin casas): ${money(sp.rents[0] * 2)}", fontSize = 12.sp)
            for (h in 1..4) Text("Con $h casa${if (h > 1) "s" else ""}: ${money(sp.rents[h])}", fontSize = 13.sp)
            Text("Con hotel: ${money(sp.rents[5])}", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("Cada casa cuesta ${money(sp.houseCost)} · el hotel ${money(sp.houseCost)} + 4 casas", fontSize = 12.sp)
          }
          MT_RR -> { Text("Alquiler: \$25 con 1 estación, \$50 con 2, \$100 con 3 y \$200 con las 4.", fontSize = 13.sp) }
          MT_UTIL -> { Text("Con 1 servicio: 4 veces lo que salga en los dados. Con los 2: 10 veces.", fontSize = 13.sp) }
          MT_TAX -> Text("Pagás ${money(sp.tax)} al banco.", fontSize = 13.sp)
          MT_GO -> Text("Cada vez que pasás o caés en GO cobrás \$200.", fontSize = 13.sp)
          MT_JAIL -> Text("Cárcel / solo de visita. Para salir: pagá \$50, usá una carta o sacá dobles (máximo 3 intentos). Igual cobrás alquileres estando preso.", fontSize = 13.sp)
          MT_FREE -> Text(if (s.rulePot) "Cobrás el pozo acumulado: ${money(s.pot)}." else "Descanso: no pasa nada.", fontSize = 13.sp)
          MT_GOJAIL -> Text("Andá directo a la cárcel.", fontSize = 13.sp)
          else -> Text(if (sp.type == MT_CH) "Sacá una carta de Suerte." else "Sacá una carta de Arca comunal.", fontSize = 13.sp)
        }
        if (sp.price > 0) {
          Spacer(Modifier.height(6.dp))
          Text("Precio ${money(sp.price)} · hipoteca ${money(sp.price / 2)}", fontSize = 12.sp)
          val o = s.owner[i]
          Text(if (o >= 0) "Dueño: ${s.players[o].name}${if (s.mort[i]) " (hipotecada)" else ""}${if (s.houses[i] in 1..4) " · ${s.houses[i]} casa(s)" else if (s.houses[i] == 5) " · hotel" else ""}" else "Sin dueño", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
      }
    }
  )
}

@Composable
private fun ManageDialog(s: MState, me: Int, canAct: Boolean, commit: ((MState) -> Unit) -> Unit, onClose: () -> Unit) {
  AlertDialog(
    onDismissRequest = onClose,
    confirmButton = { TextButton(onClick = onClose) { Text("Cerrar") } },
    title = { Text("🏠 Mis propiedades · ${money(s.players[me].money)}") },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val mine = s.ownedBy(me)
        if (mine.isEmpty()) Text("Todavía no tenés propiedades.")
        if (!canAct && mine.isNotEmpty()) Text("Solo podés construir e hipotecar en tu turno.", fontSize = 12.sp, color = Color(0xFFFF8A80))
        mine.forEach { i ->
          val sp = MONO_SPACES[i]
          val gc = GROUP_COLORS[sp.group]
          Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0x22000000)).padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(Modifier.size(14.dp, 26.dp).clip(RoundedCornerShape(3.dp)).background(gc))
              Spacer(Modifier.width(8.dp))
              Column(Modifier.weight(1f)) {
                Text(sp.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(
                  (if (s.mort[i]) "Hipotecada" else if (s.houses[i] == 5) "Hotel" else if (s.houses[i] > 0) "${s.houses[i]} casa(s)" else if (sp.type == MT_PROP && s.ownsGroup(me, sp.group)) "Grupo completo ✔" else "Alquiler ${money(s.rentOf(i, 7))}"),
                  fontSize = 11.sp, color = Color(0xFF9E9E9E)
                )
              }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              if (sp.type == MT_PROP) {
                SoftButton("＋ Casa ${money(sp.houseCost)}", enabled = canAct && s.canBuild(i), onClick = { commit { it.build(i) } })
                SoftButton("－ Vender", enabled = canAct && s.canSellBuilding(i), onClick = { commit { it.sellBuilding(i) } })
              }
              if (s.mort[i]) SoftButton("Levantar ${money(s.unmortgageCost(i))}", enabled = canAct && s.players[me].money >= s.unmortgageCost(i), onClick = { commit { it.unmortgage(i) } })
              else SoftButton("Hipotecar +${money(sp.price / 2)}", enabled = canAct && s.canMortgage(i), onClick = { commit { it.mortgage(i) } })
            }
          }
        }
        Text("Reglas: construís parejo dentro del grupo, con el grupo completo y sin hipotecas. Quedan ${32 - s.housesUsed()} casas y ${12 - s.hotelsUsed()} hoteles en el banco.", fontSize = 11.sp, color = Color(0xFF9E9E9E))
      }
    }
  )
}

@Composable
private fun TradeDialog(s: MState, me: Int, onPropose: (MTrade) -> Unit, onClose: () -> Unit) {
  val others = s.alive().filter { it != me }
  var partner by remember { mutableStateOf(others.firstOrNull() ?: -1) }
  val give = remember { mutableStateListOf<Int>() }
  val get = remember { mutableStateListOf<Int>() }
  var giveCash by remember { mutableStateOf(0) }
  var getCash by remember { mutableStateOf(0) }
  var err by remember { mutableStateOf("") }
  if (partner < 0) { onClose(); return }
  AlertDialog(
    onDismissRequest = onClose,
    title = { Text("🤝 Proponer un trato") },
    confirmButton = {
      TextButton(onClick = {
        val t = MTrade(me, partner)
        t.giveProps.addAll(give); t.getProps.addAll(get); t.giveCash = giveCash; t.getCash = getCash
        val e = s.tradeValid(t)
        if (e != null) err = e else { onPropose(t); onClose() }
      }) { Text("Proponer") }
    },
    dismissButton = { TextButton(onClick = onClose) { Text("Cancelar") } },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Con quién", fontSize = 12.sp, color = Color(0xFF9E9E9E))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          others.forEach { o ->
            val sel = o == partner
            Text(
              s.players[o].name, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (sel) Color.Black else Color.White,
              modifier = Modifier.clip(RoundedCornerShape(50)).background(if (sel) GoldYellow else Color(0x55808080)).clickable { partner = o; get.clear() }.padding(horizontal = 12.dp, vertical = 6.dp)
            )
          }
        }
        Text("Yo doy", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        PropPicker(s, s.ownedBy(me), give)
        CashStepper("Efectivo", giveCash, s.players[me].money) { giveCash = it }
        Text("Yo pido", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        PropPicker(s, s.ownedBy(partner), get)
        CashStepper("Efectivo", getCash, s.players[partner].money) { getCash = it }
        if (err.isNotEmpty()) Text(err, color = Color(0xFFFF8A80), fontSize = 12.sp)
      }
    }
  )
}

@Composable
private fun PropPicker(s: MState, list: List<Int>, sel: MutableList<Int>) {
  if (list.isEmpty()) { Text("(sin propiedades)", fontSize = 11.sp, color = Color(0xFF9E9E9E)); return }
  Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    list.forEach { i ->
      val on = i in sel
      Row(
        Modifier.clip(RoundedCornerShape(10.dp)).background(if (on) GoldYellow else Color(0x33808080)).clickable { if (on) sel.remove(i) else sel.add(i) }.padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Box(Modifier.size(10.dp, 18.dp).background(GROUP_COLORS[MONO_SPACES[i].group]))
        Spacer(Modifier.width(5.dp))
        Text(MONO_SPACES[i].name + if (s.mort[i]) " (H)" else "", fontSize = 11.sp, color = if (on) Color.Black else Color.White, maxLines = 1)
      }
    }
  }
}

@Composable
private fun CashStepper(label: String, value: Int, max: Int, onChange: (Int) -> Unit) {
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Text("$label: ${money(value)}", fontSize = 12.sp, modifier = Modifier.width(120.dp))
    SoftButton("−50", onClick = { onChange((value - 50).coerceAtLeast(0)) })
    SoftButton("+50", onClick = { onChange((value + 50).coerceAtMost(max)) })
    SoftButton("+200", onClick = { onChange((value + 200).coerceAtMost(max)) })
  }
}
