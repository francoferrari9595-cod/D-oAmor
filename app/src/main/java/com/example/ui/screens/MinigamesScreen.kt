package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.CoupleViewModel
import com.example.ui.components.GlassCard
import com.example.ui.components.GradientButton
import com.example.ui.components.ScreenTitle
import com.example.ui.components.SoftButton
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassWhite
import com.example.ui.theme.GoldYellow
import com.example.ui.theme.LocalDuoPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ---------- Ayudas para leer el estado compartido (Firestore) ----------
private fun Map<String, Any>?.i(k: String, d: Int = 0): Int = (this?.get(k) as? Number)?.toInt() ?: d
private fun Map<String, Any>?.l(k: String, d: Long = 0L): Long = (this?.get(k) as? Number)?.toLong() ?: d
private fun Map<String, Any>?.s(k: String, d: String = ""): String = (this?.get(k) as? String) ?: d
private fun Map<String, Any>?.strs(k: String, size: Int, d: String = ""): List<String> {
  val l = (this?.get(k) as? List<*>)?.map { (it as? String) ?: d } ?: emptyList()
  return if (l.size >= size) l.take(size) else l + List(size - l.size) { d }
}
private fun Map<String, Any>?.ints(k: String, size: Int, d: Int = 0): List<Int> {
  val l = (this?.get(k) as? List<*>)?.map { (it as? Number)?.toInt() ?: d } ?: emptyList()
  return if (l.size >= size) l.take(size) else l + List(size - l.size) { d }
}
private fun Map<String, Any>?.intList(k: String): List<Int> =
  (this?.get(k) as? List<*>)?.mapNotNull { (it as? Number)?.toInt() } ?: emptyList()

/** Contexto común de una partida: quién soy, quiénes juegan, nombre de la pareja. */
internal class GameCtx(val vm: CoupleViewModel, val me: String, val players: List<String>, val partnerName: String, val names: Map<String, String> = emptyMap(), val lobbyBots: Int = 0, val hostUid: String = players.firstOrNull().orEmpty(), val lobbyStarted: Boolean = false) {
  val firstUid: String get() = players.firstOrNull() ?: ""
  val myIdx: Int get() = players.indexOf(me).coerceAtLeast(0)
  fun uidAt(i: Int): String = players.getOrElse(i) { firstUid }
  val otherUid: String get() = players.firstOrNull { it != me } ?: me
  fun nameOf(uid: String): String = if (uid == me) "Tú" else (names[uid] ?: partnerName)
  fun nameOfIdx(i: Int): String = nameOf(players.getOrElse(i) { "" })
}

/** Juegos que admiten 1 a 4 personas (pareja + invitados + bots). Los demás son de 2 y los invitados miran. */
private val N_GAMES = setOf("ludo", "bowl", "virus", "d10k", "truco", "tutti", "uno", "impostor")
private val SIX_PLAYER_GAMES = setOf("virus", "tutti", "uno", "impostor")

private data class GameInfo(val id: String, val emoji: String, val title: String, val desc: String)

private val GAME_LIST = listOf(
  GameInfo("bowl", "🎳", "Bowling 3D", "Carril en perspectiva, 1 a 4 jugadores con bots"),
  GameInfo("d10k", "🎲", "Diez Mil", "Vaso con cinco dados, hasta 4 jugadores y bots"),
  GameInfo("truco", "🃏", "Truco argentino", "Mazo español, envido, truco, retruco y vale cuatro"),
  GameInfo("virus", "🦠", "VIRUS!", "Cartas, órganos y tratamientos para 2 a 6 jugadores"),
  GameInfo("tutti", "📝", "Tutti Frutti 3D", "Letras al azar, categorías, cronómetro y respuestas en vivo para 2 a 6"),
  GameInfo("hang", "🪢", "Ahorcado 3D", "Por categorías, juntos o con tu palabra secreta"),
  GameInfo("shop", "🛍️", "Tienda de retos", "Raspadas de retos, regalos y retos recibidos"),
  GameInfo("poker", "♠️", "Póker", "Texas Hold'em 1 contra 1 con fichas"),
  GameInfo("uno", "🃏", "UNO", "1 jugador contra bots o hasta 6 humanos; cartas, colores, comodines y +2/+4"),
  GameInfo("riddle", "🧩", "Adivinanzas", "Quién las resuelve primero"),
  GameInfo("draw", "🎨", "Adivina el dibujo", "Uno dibuja, el otro adivina"),
  GameInfo("ludo", "🎲", "Ludo", "Hasta 4 jugadores, con bots, dados y capturas"),
  GameInfo("snl", "🐍", "Serpientes y escaleras", "Tres tableros ilustrados, dado animado, serpientes y escaleras para jugar con tu pareja o bots"),
  GameInfo("ttt", "❤️", "Tres en raya", "Corazones contra besos, por turnos"),
  GameInfo("c4", "🔴", "Conecta 4", "Junta cuatro fichas antes que tu pareja"),
  GameInfo("mem", "🧠", "Memoria de corazones", "Encuentren las parejas, turno a turno"),
  GameInfo("rps", "✊", "Piedra, papel o tijera", "Elijan en secreto y reveladlo a la vez"),
  GameInfo("chess", "♟️", "Ajedrez", "Jaque, enroque, coronación y jaque mate en vivo"),
  GameInfo("impostor", "🕵️", "El impostor", "Descubran quién no conoce la palabra secreta · 3 a 6 jugadores"),
  GameInfo("coupons", "🎟️", "Cupones rasca", "Descubre y canjea premios de amor")
)

@Composable
fun MinigamesScreen(viewModel: CoupleViewModel) {
  var selected by remember { mutableStateOf<String?>(null) }
  val lobbyFlow = remember { viewModel.gameState("minigameLobby") }
  val lobbyState by lobbyFlow.collectAsState(initial = null)
  val sharedGame = lobbyState?.get("game") as? String
  val lobbyHost = lobbyState?.get("host") as? String ?: ""
  val lobbyStarted = lobbyState?.get("started") as? Boolean ?: false
  val lobbyBots = (lobbyState?.get("bots") as? Number)?.toInt()?.coerceIn(0, 5) ?: 0
  var resetCounter by remember(selected) { mutableIntStateOf(0) }
  var showRestartDialog by remember { mutableStateOf(false) }
  val partner by viewModel.partnerUser.collectAsState()
  val members by viewModel.memberUids.collectAsState()
  val me = viewModel.myUid()
  val partnerName by viewModel.partnerDisplayName.collectAsState()
  val roomNames by viewModel.roomNames.collectAsState()
  val guestList by viewModel.guestUids.collectAsState()
  val everyone = (members + guestList + listOfNotNull(me)).distinct().take(6)
  val lobbyPlayers = ((lobbyState?.get("players") as? Number)?.toInt() ?: minOf(2, everyone.size).coerceAtLeast(1)).coerceIn(1, if (selected in SIX_PLAYER_GAMES) 6 else 4)
  // Anfitrión determinista: todos los dispositivos calculan el mismo UID y no pueden
  // pisarse la selección de juego al tocar dos tarjetas casi al mismo tiempo.
  val roomHostUid = (members + guestList).distinct().sorted().firstOrNull() ?: me.orEmpty()
  val isRoomHost = me != null && me == roomHostUid
  // Cualquiera de los dos de la pareja puede elegir juego, configurar e iniciar; el resto de la sala entra solo.
  val canControl = me != null && (members.isEmpty() || members.contains(me) || isRoomHost)
  val nCap = selected in N_GAMES
  // Juegos de 2: la pareja; si aún no hay pareja completa, los dos primeros presentes (yo siempre incluido).
  val twoPlayers = (if (members.size == 2 && (me == null || members.contains(me))) members
    else (listOfNotNull(me) + everyone.filter { it != me }).take(2))
  val selectedHumans = (listOf(roomHostUid).filter { it.isNotBlank() } + everyone.filter { it != roomHostUid }).distinct().take(lobbyPlayers)
  val ctx = GameCtx(viewModel, me ?: "", if (nCap) selectedHumans else twoPlayers, partnerName, roomNames, lobbyBots, roomHostUid, lobbyStarted)
  val spectator = me != null && !ctx.players.contains(me)
  val ready = me != null

  // La selección de juego se comparte por la misma sala de Firestore para que ambos
  // teléfonos entren a la misma pantalla, en vez de mantener una selección puramente local.
  LaunchedEffect(sharedGame) {
    if (!sharedGame.isNullOrBlank() && GAME_LIST.any { it.id == sharedGame }) selected = sharedGame
    else if (sharedGame.isNullOrBlank()) selected = null
  }

  Column(
    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)
  ) {
    GameErrorBanner(viewModel)
    if (spectator) Text("👀 En este juego eres espectador si no estás en la lista de participantes. VIRUS y Tutti Frutti admiten hasta 6 jugadores humanos.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    if (selected == null) {
      ScreenTitle("Juegos en vivo", "Juegan los dos al mismo tiempo: tú mueves y $partnerName lo ve al instante")
      if (ready && !canControl) {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
          Text("⏳ Esperando a la pareja", fontWeight = FontWeight.ExtraBold, color = Color.White)
          Text("La pareja elige el juego. Cuando lo seleccione, entrarás aquí automáticamente.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(10.dp))
      }
      Spacer(Modifier.height(10.dp))
      WalletBar(viewModel)
      Spacer(Modifier.height(12.dp))
      if (!ready) {
        GlassCard(modifier = Modifier.fillMaxWidth(), highlight = true) {
          Text("Esperando a tu pareja", fontWeight = FontWeight.ExtraBold, color = Color.White)
          Text(
            "Los juegos en vivo se activan cuando los dos teléfonos están vinculados con el mismo código.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        Spacer(Modifier.height(12.dp))
      }
      GAME_LIST.chunked(2).forEachIndexed { rowIdx, row ->
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          row.forEachIndexed { colIdx, g ->
            val tileIdx = rowIdx * 2 + colIdx
            com.example.ui.components.GameTile(
              emoji = g.emoji,
              title = g.title,
              desc = g.desc,
              accent = com.example.ui.components.GameAccents[tileIdx % com.example.ui.components.GameAccents.size],
              index = tileIdx,
              modifier = Modifier.weight(1f),
              onClick = {
                if (g.id == "coupons") {
                  selected = g.id
                } else if (ready && canControl) {
                  selected = g.id
                  viewModel.publishGame("minigameLobby", mapOf("game" to g.id, "host" to me.orEmpty(), "started" to false, "bots" to if (g.id == "uno" && everyone.size < 2) 1 else 0, "players" to minOf(2, everyone.size).coerceAtLeast(1)))
                }
              }
            )
          }
          if (row.size == 1) Spacer(Modifier.weight(1f))
        }
      }
    } else {
      val info = GAME_LIST.first { it.id == selected }
      Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) {
        SoftButton("← Juegos", onClick = {
          if (selected != "coupons" && canControl) viewModel.publishGame("minigameLobby", emptyMap())
          selected = null
        })
        Spacer(Modifier.width(12.dp))
        Text("${info.emoji} ${info.title}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = Color.White)
      }
      if (selected != "coupons" && !lobbyStarted) {
        val isHost = canControl
        GlassCard(modifier = Modifier.fillMaxWidth(), highlight = true) {
          Text("🎮 Sala de espera", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
          Text("Todos los dispositivos ven esta misma sala. Cualquiera de los dos de la pareja puede configurar y comenzar.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
          Text("Eligió el juego: ${if (lobbyHost == me) "tú" else (roomNames[lobbyHost] ?: partnerName)}", color = GoldYellow, fontWeight = FontWeight.Bold, fontSize = 12.sp)
          Spacer(Modifier.height(6.dp))
          if (selected in N_GAMES && isHost) {
            Spacer(Modifier.height(8.dp))
            Text("Jugadores humanos en la mesa", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              (1..everyone.size.coerceAtLeast(1).coerceAtMost(if (selected in SIX_PLAYER_GAMES) 6 else 4)).forEach { n ->
                if (lobbyPlayers == n) GradientButton("$n", onClick = { viewModel.publishGame("minigameLobby", mapOf("game" to selected.orEmpty(), "host" to lobbyHost, "started" to false, "bots" to lobbyBots, "players" to n)) }, height = 38.dp)
                else SoftButton("$n", onClick = { viewModel.publishGame("minigameLobby", mapOf("game" to selected.orEmpty(), "host" to lobbyHost, "started" to false, "bots" to lobbyBots, "players" to n)) })
              }
            }
          }
          if (selected in setOf("truco", "uno") && isHost) {
            Spacer(Modifier.height(8.dp))
            Text(if (selected == "uno") "Bots para completar la mesa de UNO" else "Bots adicionales para la mesa de Truco", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              ((if (selected == "uno" && lobbyPlayers == 1) 1 else 0)..((if (selected == "uno") 6 else 4)-lobbyPlayers).coerceAtLeast(if (selected == "uno" && lobbyPlayers == 1) 1 else 0)).forEach { n ->
                if (lobbyBots == n) GradientButton("$n bots", onClick = { viewModel.publishGame("minigameLobby", mapOf("game" to selected.orEmpty(), "host" to lobbyHost, "started" to false, "bots" to n, "players" to lobbyPlayers)) }, height = 38.dp)
                else SoftButton("$n bots", onClick = { viewModel.publishGame("minigameLobby", mapOf("game" to selected.orEmpty(), "host" to lobbyHost, "started" to false, "bots" to n, "players" to lobbyPlayers)) })
              }
            }
          }
          Spacer(Modifier.height(12.dp))
          if (isHost) {
            GradientButton("Comenzar partida", modifier = Modifier.fillMaxWidth(), onClick = {
              viewModel.publishGame("minigameLobby", mapOf("game" to selected.orEmpty(), "host" to lobbyHost, "started" to true, "bots" to lobbyBots, "players" to lobbyPlayers))
            })
          } else {
            Text("Esperando que la pareja presione «Comenzar partida»…", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
          }
        }
      } else if (!ready && selected != "coupons") {
        GlassCard(modifier = Modifier.fillMaxWidth(), highlight = true) {
          Text("Esperando a tu pareja", fontWeight = FontWeight.ExtraBold, color = Color.White)
          Text("Este juego se activa cuando los dos teléfonos están vinculados.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      } else {
        // Reinicio común y sincronizado para cualquier juego con estado compartido.
        val resetGameId = when (selected) {
          "ttt" -> "ttt2"
          "wheel" -> "wheel2"
          "snl" -> "snl"
          "coupons" -> "coupons"
          else -> selected.orEmpty()
        }
        if (showRestartDialog) {
          AlertDialog(
            onDismissRequest = { showRestartDialog = false },
            title = { Text("Opciones de partida") },
            text = { Text("¿Querés reiniciar ${info.title}? Se restablecerá el estado compartido de esta partida.") },
            confirmButton = {
              TextButton(onClick = {
                showRestartDialog = false
                if (resetGameId.isNotBlank() && resetGameId != "coupons") {
                  viewModel.publishGame(resetGameId, emptyMap())
                }
                resetCounter++
              }) { Text("Reiniciar juego") }
            },
            dismissButton = { TextButton(onClick = { showRestartDialog = false }) { Text("Cancelar") } }
          )
        }
        Column(modifier = Modifier.fillMaxWidth()) {
          Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.End) {
            SoftButton("⚙️ Opciones", onClick = { showRestartDialog = true })
          }
        Box(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
        key(resetCounter) {
        when (selected) {
          "ludo" -> LudoGame(ctx)
          "pool" -> PoolGame(ctx)
          "bowl" -> BowlingGame(ctx)
          "snl" -> SnakesLaddersGame(ctx)
          "d10k" -> DiezMilGame(ctx)
          "truco" -> TrucoGame(ctx)
          "virus" -> VirusGame(ctx)
          "tutti" -> TuttiFruttiGame(ctx)
          "roul" -> RouletteGame(ctx)
          "hang" -> HangmanGame(ctx)
          "shop" -> ShopGame(ctx)
          "slots" -> SlotsGame(ctx)
          "lottery" -> LotteryGame(ctx)
          "poker" -> PokerGame(ctx)
          "uno" -> UnoGame(ctx)
          "bs" -> BattleshipGame(ctx)
          "riddle" -> RiddleGame(ctx)
          "draw" -> DrawGuessGame(ctx)
          "ttt" -> TicTacToeGame(ctx)
          "c4" -> ConnectFourGame(ctx)
          "chess" -> ChessGame(ctx)
          "mem" -> MemoryGame(ctx)
          "rps" -> RpsGame(ctx)
          "qa" -> QuestionsGame(ctx)
          "wheel" -> WheelGame(ctx)
          "tod" -> TruthOrDareGame(ctx)
          "impostor" -> ImpostorGame(ctx)
          "coupons" -> CouponsGame(viewModel)
        }
        }
        }
        if (spectator) {
          Box(modifier = Modifier.matchParentSize().clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {})
        }
        }
        }
      }
    }
    if (selected != null) {
      Spacer(Modifier.height(14.dp))
      Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { WalkieButton(viewModel, 64.dp) }
      Text("📻 Mantén para hablar con tu pareja", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
    Spacer(Modifier.height(24.dp))
  }
}

@Composable
internal fun ScoreBoard(c: GameCtx, a: Int, b: Int, turnUid: String?, symA: String = "", symB: String = "") {
  val pulse = rememberPalettePulse()
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    for (i in 0..1) {
      val uid = c.players.getOrElse(i) { "" }
      val active = turnUid != null && turnUid == uid
      GlassCard(modifier = Modifier.weight(1f).graphicsLayer { scaleX = if (active) pulse else 1f; scaleY = if (active) pulse else 1f }, highlight = active, corner = 18.dp, contentPadding = PaddingValues(10.dp)) {
        Text("${if (i == 0) symA else symB} ${c.nameOfIdx(i)}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text("${if (i == 0) a else b}", color = Color.White, fontWeight = FontWeight.Black, fontSize = 28.sp)
        if (active) Text("● su turno", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
      }
    }
  }
}

@Composable
internal fun TurnBanner(text: String, highlight: Boolean) {
  if (text.startsWith("🏆")) {
    ResultBanner(text)
    return
  }
  GlassCard(modifier = Modifier.fillMaxWidth(), highlight = highlight, corner = 16.dp, contentPadding = PaddingValues(12.dp)) {
    Text(text, color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
  }
}

// =====================================================================
// 1) TRES EN RAYA
// =====================================================================
private val TTT_LINES = listOf(
  listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8), listOf(0, 3, 6), listOf(1, 4, 7), listOf(2, 5, 8), listOf(0, 4, 8), listOf(2, 4, 6)
)

@Composable
private fun TicTacToeGame(c: GameCtx) {
  val flow = remember { c.vm.gameState("ttt2") }
  val st by flow.collectAsState(initial = null)
  val board = st.strs("board", 9)
  val turn = st.s("turn", c.firstUid)
  val winner = st.s("winner")
  val scoreA = st.i("scoreA")
  val scoreB = st.i("scoreB")
  val round = st.i("round")
  val sym = listOf("❤️", "💋")
  val winLine = TTT_LINES.firstOrNull { l -> board[l[0]].isNotEmpty() && board[l[0]] == board[l[1]] && board[l[1]] == board[l[2]] } ?: emptyList()

  fun play(i: Int) {
    if (turn != c.me || board[i].isNotEmpty() || winner.isNotEmpty()) return
    val nb = board.toMutableList()
    nb[i] = sym[c.myIdx]
    var w = ""
    for (line in TTT_LINES) {
      if (nb[line[0]].isNotEmpty() && nb[line[0]] == nb[line[1]] && nb[line[1]] == nb[line[2]]) w = nb[line[0]]
    }
    if (w.isEmpty() && nb.none { it.isEmpty() }) w = "EMPATE"
    var a = scoreA
    var b = scoreB
    if (w == sym[0]) a++ else if (w == sym[1]) b++
    c.vm.publishGame("ttt2", mapOf("board" to nb, "turn" to c.otherUid, "winner" to w, "scoreA" to a, "scoreB" to b, "round" to round))
    c.vm.gameSfx(if (w.isNotEmpty()) 2 else 0)
  }

  ScoreBoard(c, scoreA, scoreB, if (winner.isEmpty()) turn else null, sym[0], sym[1])
  Spacer(Modifier.height(12.dp))
  TurnBanner(
    when {
      winner == "EMPATE" -> "🤝 ¡Empate!"
      winner.isNotEmpty() -> "🏆 ¡Ganó ${c.nameOfIdx(sym.indexOf(winner))}!"
      turn == c.me -> "Tu turno: coloca tu ${sym[c.myIdx]}"
      else -> "Esperando a ${c.partnerName}…"
    }, turn == c.me && winner.isEmpty()
  )
  Spacer(Modifier.height(12.dp))
  for (r in 0..2) {
    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      for (col in 0..2) {
        val idx = r * 3 + col
        val inWin = winLine.contains(idx)
        Box(
          modifier = Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(20.dp))
            .background(if (inWin) SolidColor(Color(0x55FFC857)) else SolidColor(GlassWhite))
            .border(BorderStroke(if (inWin) 2.dp else 1.dp, if (inWin) GoldYellow else GlassBorder), RoundedCornerShape(20.dp))
            .clickable { play(idx) },
          contentAlignment = Alignment.Center
        ) { PopText(board[idx], 44.sp) }
      }
    }
  }
  Spacer(Modifier.height(8.dp))
  GradientButton(
    "Nueva ronda", modifier = Modifier.fillMaxWidth(),
    onClick = {
      c.vm.publishGame("ttt2", mapOf(
        "board" to List(9) { "" }, "turn" to c.uidAt((round + 1) % 2), "winner" to "",
        "scoreA" to scoreA, "scoreB" to scoreB, "round" to round + 1
      ))
    }
  )
}

// =====================================================================
// 2) CONECTA 4
// =====================================================================
private fun c4Winner(b: List<Int>): Int {
  val dirs = listOf(Pair(0, 1), Pair(1, 0), Pair(1, 1), Pair(1, -1))
  for (r in 0 until 6) for (col in 0 until 7) {
    val v = b[r * 7 + col]
    if (v == 0) continue
    for ((dr, dc) in dirs) {
      var k = 1
      while (k < 4) {
        val rr = r + dr * k
        val cc = col + dc * k
        if (rr !in 0 until 6 || cc !in 0 until 7 || b[rr * 7 + cc] != v) break
        k++
      }
      if (k == 4) return v
    }
  }
  return if (b.none { it == 0 }) 3 else 0
}

@Composable
private fun ConnectFourGame(c: GameCtx) {
  val flow = remember { c.vm.gameState("c4") }
  val st by flow.collectAsState(initial = null)
  val board = st.ints("board", 42)
  val turn = st.s("turn", c.firstUid)
  val winner = st.i("winner")
  val scoreA = st.i("scoreA")
  val scoreB = st.i("scoreB")
  val round = st.i("round")
  val palette = LocalDuoPalette.current
  val colors = listOf(Color(0x14FFFFFF), palette.accentA, GoldYellow)

  fun drop(col: Int) {
    if (turn != c.me || winner != 0) return
    var row = -1
    for (r in 5 downTo 0) if (board[r * 7 + col] == 0) { row = r; break }
    if (row < 0) return
    val nb = board.toMutableList()
    nb[row * 7 + col] = c.myIdx + 1
    val w = c4Winner(nb)
    var a = scoreA
    var b = scoreB
    if (w == 1) a++ else if (w == 2) b++
    c.vm.publishGame("c4", mapOf("board" to nb, "turn" to c.otherUid, "winner" to w, "scoreA" to a, "scoreB" to b, "round" to round))
    c.vm.gameSfx(if (w != 0) 2 else 0)
  }

  ScoreBoard(c, scoreA, scoreB, if (winner == 0) turn else null, "🔴", "🟡")
  Spacer(Modifier.height(12.dp))
  TurnBanner(
    when {
      winner == 3 -> "🤝 ¡Empate!"
      winner != 0 -> "🏆 ¡Ganó ${c.nameOfIdx(winner - 1)}!"
      turn == c.me -> "Tu turno: toca una columna"
      else -> "Esperando a ${c.partnerName}…"
    }, turn == c.me && winner == 0
  )
  Spacer(Modifier.height(12.dp))
  GlassCard(modifier = Modifier.fillMaxWidth(), corner = 22.dp, contentPadding = PaddingValues(8.dp)) {
    for (r in 0 until 6) {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        for (col in 0 until 7) {
          C4Cell(board[r * 7 + col], r, colors, Modifier.weight(1f).aspectRatio(1f).padding(3.dp).clickable { drop(col) })
        }
      }
    }
  }
  Spacer(Modifier.height(10.dp))
  GradientButton(
    "Nueva ronda", modifier = Modifier.fillMaxWidth(),
    onClick = {
      c.vm.publishGame("c4", mapOf(
        "board" to List(42) { 0 }, "turn" to c.uidAt((round + 1) % 2), "winner" to 0,
        "scoreA" to scoreA, "scoreB" to scoreB, "round" to round + 1
      ))
    }
  )
}

// =====================================================================
// 3) MEMORIA
// =====================================================================
@Composable
private fun MemoryGame(c: GameCtx) {
  val flow = remember { c.vm.gameState("mem") }
  val st by flow.collectAsState(initial = null)
  val cards = st.strs("cards", 12)
  val started = (st?.get("cards") as? List<*>)?.size == 12
  val owner = st.ints("owner", 12, -1)
  val flipped = st.intList("flipped")
  val turn = st.s("turn", c.firstUid)
  val scoreA = st.i("scoreA")
  val scoreB = st.i("scoreB")
  val finished = started && owner.none { it == -1 }

  fun publish(newOwner: List<Int>, newFlipped: List<Int>, newTurn: String, a: Int, b: Int) {
    c.vm.publishGame("mem", mapOf(
      "cards" to cards, "owner" to newOwner, "flipped" to newFlipped, "turn" to newTurn, "scoreA" to a, "scoreB" to b
    ))
  }

  // Si fallé la pareja, yo (dueño del turno) las vuelvo a tapar y paso el turno.
  LaunchedEffect(flipped, turn) {
    if (turn == c.me && flipped.size == 2) {
      delay(1400)
      publish(owner, emptyList(), c.otherUid, scoreA, scoreB)
    }
  }

  fun tap(i: Int) {
    if (!started || turn != c.me || owner[i] != -1 || flipped.contains(i) || flipped.size >= 2) return
    val nf = flipped + i
    if (nf.size == 2 && cards[nf[0]] == cards[nf[1]]) {
      val no = owner.toMutableList()
      no[nf[0]] = c.myIdx
      no[nf[1]] = c.myIdx
      publish(no, emptyList(), c.me, if (c.myIdx == 0) scoreA + 1 else scoreA, if (c.myIdx == 1) scoreB + 1 else scoreB)
      c.vm.gameSfx(1)
    } else {
      publish(owner, nf, c.me, scoreA, scoreB)
      c.vm.gameSfx(0)
    }
  }

  ScoreBoard(c, scoreA, scoreB, if (started && !finished) turn else null, "💖", "💙")
  Spacer(Modifier.height(12.dp))
  TurnBanner(
    when {
      !started -> "Toca “Empezar” para repartir las cartas"
      finished -> if (scoreA == scoreB) "🤝 ¡Empate!" else "🏆 ¡Ganó ${c.nameOfIdx(if (scoreA > scoreB) 0 else 1)}!"
      turn == c.me -> "Tu turno: destapa dos cartas"
      else -> "Esperando a ${c.partnerName}…"
    }, started && turn == c.me && !finished
  )
  Spacer(Modifier.height(12.dp))
  if (started) {
    for (r in 0 until 4) {
      Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (col in 0 until 3) {
          val idx = r * 3 + col
          val faceUp = owner[idx] != -1 || flipped.contains(idx)
          FlipCard(
            faceUp = faceUp,
            frontText = cards[idx],
            frontBrush = if (owner[idx] != -1) SolidColor(if (owner[idx] == 0) Color(0x66FF4D8D) else Color(0x6637E6FF)) else SolidColor(GlassWhite),
            backBrush = LocalDuoPalette.current.accentDiagonal,
            modifier = Modifier.weight(1f).aspectRatio(1.2f).clickable { tap(idx) }
          )
        }
      }
    }
  }
  Spacer(Modifier.height(8.dp))
  GradientButton(
    if (started) "Barajar de nuevo" else "Empezar", modifier = Modifier.fillMaxWidth(),
    onClick = {
      val deck = listOf("💖", "🌹", "🍓", "🎁", "🌙", "⭐").let { it + it }.shuffled()
      c.vm.publishGame("mem", mapOf(
        "cards" to deck, "owner" to List(12) { -1 }, "flipped" to emptyList<Int>(),
        "turn" to c.firstUid, "scoreA" to 0, "scoreB" to 0
      ))
    }
  )
}

// =====================================================================
// 4) PIEDRA, PAPEL O TIJERA
// =====================================================================
@Composable
private fun RpsGame(c: GameCtx) {
  val flow = remember { c.vm.gameState("rps") }
  val st by flow.collectAsState(initial = null)
  val picks = listOf(st.s("pickA"), st.s("pickB"))
  val scoreA = st.i("scoreA")
  val scoreB = st.i("scoreB")
  val round = st.i("round")
  val result = st.s("result")
  val myPick = picks[c.myIdx]
  val bothPicked = picks[0].isNotEmpty() && picks[1].isNotEmpty()
  val options = listOf("🪨", "📄", "✂️")

  fun beats(a: String, b: String) = (a == "🪨" && b == "✂️") || (a == "📄" && b == "🪨") || (a == "✂️" && b == "📄")

  fun pick(o: String) {
    if (myPick.isNotEmpty()) return
    val np = picks.toMutableList()
    np[c.myIdx] = o
    var a = scoreA
    var b = scoreB
    var res = ""
    if (np[0].isNotEmpty() && np[1].isNotEmpty()) {
      res = when {
        np[0] == np[1] -> "T"
        beats(np[0], np[1]) -> { a++; "A" }
        else -> { b++; "B" }
      }
    }
    c.vm.publishGame("rps", mapOf("pickA" to np[0], "pickB" to np[1], "scoreA" to a, "scoreB" to b, "round" to round, "result" to res))
    c.vm.gameSfx(if (res.isNotEmpty()) 2 else 0)
  }

  ScoreBoard(c, scoreA, scoreB, null, "✊", "✊")
  Spacer(Modifier.height(12.dp))
  TurnBanner(
    when {
      bothPicked -> when (result) {
        "T" -> "🤝 ¡Empate!"
        "A" -> "🏆 Ganó ${c.nameOfIdx(0)}"
        else -> "🏆 Ganó ${c.nameOfIdx(1)}"
      }
      myPick.isEmpty() -> "Elige tu jugada en secreto"
      else -> "Elegiste ${myPick}. Esperando a ${c.partnerName}…"
    }, !bothPicked && myPick.isEmpty()
  )
  Spacer(Modifier.height(14.dp))
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
    for (i in 0..1) {
      GlassCard(modifier = Modifier.weight(1f).padding(horizontal = 4.dp), corner = 22.dp) {
        Text(c.nameOfIdx(i), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        val shown = when {
          bothPicked -> picks[i]
          i == c.myIdx && picks[i].isNotEmpty() -> picks[i]
          picks[i].isNotEmpty() -> "✅"
          else -> "…"
        }
        PopText(shown, 54.sp, Modifier.align(Alignment.CenterHorizontally))
      }
    }
  }
  Spacer(Modifier.height(14.dp))
  if (!bothPicked && myPick.isEmpty()) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      options.forEach { o ->
        Box(
          modifier = Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(22.dp))
            .background(LocalDuoPalette.current.accentDiagonal).clickable { pick(o) },
          contentAlignment = Alignment.Center
        ) { Text(o, fontSize = 42.sp) }
      }
    }
  }
  if (bothPicked) {
    GradientButton(
      "Siguiente ronda", modifier = Modifier.fillMaxWidth(),
      onClick = {
        c.vm.publishGame("rps", mapOf("pickA" to "", "pickB" to "", "scoreA" to scoreA, "scoreB" to scoreB, "round" to round + 1, "result" to ""))
      }
    )
  }
}

// =====================================================================
// 5) PREGUNTAS DE PAREJA
// =====================================================================
private val QUESTIONS = listOf(
  "¿Cuál fue el momento exacto en que te enamoraste de mí?",
  "¿Qué fue lo primero que pensaste cuando me viste?",
  "¿Cuál es nuestro mejor recuerdo juntos?",
  "Si tuviéramos un fin de semana sin límites, ¿adónde iríamos?",
  "¿Qué pequeño detalle mío te alegra el día?",
  "¿Cuál es tu comida favorita que hemos compartido?",
  "¿Qué canción te hace pensar en nosotros?",
  "Si fuéramos personajes de una película romántica, ¿cuál seríamos?",
  "¿Qué sueño juntos te emociona más cumplir este año?",
  "¿Cuál es el beso que nunca vas a olvidar?",
  "¿Qué apodo cariñoso es tu preferido?",
  "¿Qué es lo que más admiras de mi personalidad?",
  "¿Qué es lo más loco que harías por mí?",
  "¿Qué te gustaría que hiciéramos más seguido?",
  "¿Cuál fue nuestra primera cita ideal?",
  "¿Qué cosa te da más ternura de mí?",
  "¿Cómo te imaginas nuestra casa dentro de cinco años?",
  "¿Qué aprendiste de mí que no sabías antes?",
  "¿Cuál es tu mayor miedo en el amor?",
  "¿Qué te haría sentir aún más querido/a?"
)

@Composable
private fun QuestionsGame(c: GameCtx) {
  val flow = remember { c.vm.gameState("qa") }
  val st by flow.collectAsState(initial = null)
  val q = st.i("q").coerceIn(0, QUESTIONS.lastIndex)
  val answers = listOf(st.s("ansA"), st.s("ansB"))
  val mine = answers[c.myIdx]
  val theirs = answers[1 - c.myIdx]
  val both = mine.isNotEmpty() && theirs.isNotEmpty()
  var draft by remember(q) { mutableStateOf("") }

  GlassCard(modifier = Modifier.fillMaxWidth(), highlight = true) {
    Text("Pregunta ${q + 1} de ${QUESTIONS.size}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    Spacer(Modifier.height(6.dp))
    Text(QUESTIONS[q], color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, lineHeight = 26.sp)
  }
  Spacer(Modifier.height(12.dp))
  if (mine.isEmpty()) {
    OutlinedTextField(
      value = draft, onValueChange = { draft = it.take(300) }, modifier = Modifier.fillMaxWidth(),
      label = { Text("Tu respuesta") }, minLines = 2
    )
    Spacer(Modifier.height(10.dp))
    GradientButton(
      "Enviar mi respuesta", enabled = draft.isNotBlank(), modifier = Modifier.fillMaxWidth(),
      onClick = {
        val na = answers.toMutableList()
        na[c.myIdx] = draft.trim()
        c.vm.publishGame("qa", mapOf("q" to q, "ansA" to na[0], "ansB" to na[1]))
        c.vm.gameSfx(0)
      }
    )
  } else {
    TurnBanner(if (both) "💞 ¡Las dos respuestas!" else "✅ Respondiste. Esperando a ${c.partnerName}…", both)
    Spacer(Modifier.height(10.dp))
    if (both) {
      for (i in 0..1) {
        GlassCard(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), corner = 18.dp) {
          Text(c.nameOfIdx(i), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
          Text(answers[i], color = Color.White, fontSize = 16.sp)
        }
      }
      GradientButton(
        "Siguiente pregunta", modifier = Modifier.fillMaxWidth(),
        onClick = { c.vm.publishGame("qa", mapOf("q" to (q + 1) % QUESTIONS.size, "ansA" to "", "ansB" to "")); c.vm.gameSfx(2) }
      )
    }
  }
}

// =====================================================================
// 6) RULETA
// =====================================================================
private val WHEEL_ITEMS = listOf(
  "💋 Un beso largo", "💆 Masaje de 5 min", "🍫 Eliges el postre", "📞 Llamada sorpresa", "🎬 Yo elijo la peli",
  "💌 Carta de amor", "🎤 Cántame algo", "🤗 Abrazo de 1 min", "📸 Foto sorpresa", "🍝 Cena en casa"
)

@Composable
private fun WheelGame(c: GameCtx) {
  val flow = remember { c.vm.gameState("wheel2") }
  val st by flow.collectAsState(initial = null)
  val spinId = st.l("spinId")
  val resultIdx = st.i("resultIdx", -1)
  val turn = st.s("turn", c.firstUid)
  val seg = 360f / WHEEL_ITEMS.size
  val rotation = remember { Animatable(0f) }
  var seen by remember { mutableStateOf<Long?>(null) }
  var showResult by remember { mutableStateOf(false) }
  val palette = LocalDuoPalette.current
  val sliceColors = listOf(palette.accentA, palette.accentB, Color(0xFF9D6BFF), Color(0xFF37E6FF), Color(0xFFFFC857))

  LaunchedEffect(spinId, resultIdx) {
    if (resultIdx < 0) return@LaunchedEffect
    val finalMod = (360f - (resultIdx * seg + seg / 2f)) % 360f
    if (seen == null) {
      rotation.snapTo(finalMod)
      showResult = true
    } else if (seen != spinId) {
      showResult = false
      val cur = rotation.value % 360f
      rotation.snapTo(cur)
      rotation.animateTo(cur + 360f * 5 + ((finalMod - cur + 360f) % 360f), tween(3600, easing = FastOutSlowInEasing))
      showResult = true
      c.vm.gameSfx(2)
    }
    seen = spinId
  }

  TurnBanner(
    when {
      !showResult && resultIdx >= 0 && seen != null && rotation.isRunning -> "🎡 Girando…"
      turn == c.me -> "Te toca girar la ruleta"
      else -> "${c.partnerName} va a girar la ruleta"
    }, turn == c.me
  )
  Spacer(Modifier.height(14.dp))
  Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
    Box(modifier = Modifier.size(280.dp).padding(top = 14.dp)) {
      Canvas(modifier = Modifier.fillMaxSize().rotate(rotation.value)) {
        for (i in WHEEL_ITEMS.indices) {
          drawArc(
            color = sliceColors[i % sliceColors.size].copy(alpha = if (i % 2 == 0) 0.95f else 0.7f),
            startAngle = -90f + i * seg, sweepAngle = seg, useCenter = true,
            topLeft = Offset.Zero, size = Size(size.width, size.height)
          )
        }
        drawCircle(color = Color(0xFF1A0B2E), radius = size.minDimension * 0.12f)
      }
    }
    Text("▼", color = Color.White, fontSize = 34.sp, modifier = Modifier.align(Alignment.TopCenter))
  }
  Spacer(Modifier.height(10.dp))
  if (showResult && resultIdx >= 0) {
    GlassCard(modifier = Modifier.fillMaxWidth(), highlight = true) {
      Text("🎯 Resultado", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
      Text(WHEEL_ITEMS[resultIdx], color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
    }
    Spacer(Modifier.height(10.dp))
  }
  GradientButton(
    "🎡 Girar", enabled = turn == c.me && !rotation.isRunning, modifier = Modifier.fillMaxWidth(),
    onClick = {
      c.vm.publishGame("wheel2", mapOf(
        "spinId" to System.currentTimeMillis(), "resultIdx" to (0 until WHEEL_ITEMS.size).random(), "turn" to c.otherUid
      ))
    }
  )
  Spacer(Modifier.height(10.dp))
  Text(
    WHEEL_ITEMS.joinToString("  ·  "), color = MaterialTheme.colorScheme.onSurfaceVariant,
    fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
  )
}

// =====================================================================
// 7) VERDAD O RETO
// =====================================================================
private val TRUTHS = listOf(
  "¿Cuál fue tu primera impresión de mí?", "¿Qué es lo que más te gusta de mi sonrisa?", "¿Cuál es tu fantasía de cita perfecta?",
  "¿Qué es lo más romántico que has pensado hacer por mí?", "¿Qué te da vergüenza decirme?", "¿Cuándo sentiste más celos?"
)
private val DARES = listOf(
  "Mándame un audio diciéndome 3 cosas que te encantan de mí.", "Cántame un pedacito de nuestra canción.",
  "Envíame una foto haciendo la cara más graciosa.", "Escribe un poema de 4 líneas para mí ahora mismo.",
  "Haz un baile de 10 segundos en videollamada.", "Dime un piropo diferente cada 10 segundos durante un minuto."
)

@Composable
private fun TruthOrDareGame(c: GameCtx) {
  val flow = remember { c.vm.gameState("tod") }
  val st by flow.collectAsState(initial = null)
  val turn = st.s("turn", c.firstUid)
  val status = st.s("status", "idle")
  val kind = st.s("kind")
  val prompt = st.s("prompt")
  val asked = status == "asked"

  TurnBanner(
    when {
      asked && turn != c.me -> "🔥 ${c.partnerName} te lanzó un ${if (kind == "verdad") "VERDAD" else "RETO"}"
      asked -> "Esperando a que ${c.partnerName} lo cumpla…"
      turn == c.me -> "Tu turno: elige Verdad o Reto para ${c.partnerName}"
      else -> "${c.partnerName} está eligiendo…"
    }, (asked && turn != c.me) || (!asked && turn == c.me)
  )
  Spacer(Modifier.height(12.dp))
  if (asked) {
    GlassCard(modifier = Modifier.fillMaxWidth(), highlight = true) {
      Text(if (kind == "verdad") "💬 VERDAD" else "⚡ RETO", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
      Spacer(Modifier.height(4.dp))
      Text(prompt, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, lineHeight = 26.sp)
    }
    Spacer(Modifier.height(12.dp))
    if (turn != c.me) {
      GradientButton(
        "¡Cumplido! ✅", modifier = Modifier.fillMaxWidth(),
        onClick = { c.vm.publishGame("tod", mapOf("turn" to c.me, "status" to "idle", "kind" to "", "prompt" to "")); c.vm.gameSfx(2) }
      )
    }
  } else if (turn == c.me) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      GradientButton("💬 Verdad", modifier = Modifier.weight(1f), onClick = {
        c.vm.publishGame("tod", mapOf("turn" to c.me, "status" to "asked", "kind" to "verdad", "prompt" to TRUTHS.random()))
      })
      GradientButton("⚡ Reto", modifier = Modifier.weight(1f), onClick = {
        c.vm.publishGame("tod", mapOf("turn" to c.me, "status" to "asked", "kind" to "reto", "prompt" to DARES.random()))
      })
    }
  }
}

// =====================================================================
// EL IMPOSTOR — juego social sincronizado con estado compartido
// =====================================================================
// Palabras cotidianas de dificultad normal, agrupadas por categoría.
// La palabra elegida solo se guarda en el estado privado de la partida; nunca se
// incorpora a mensajes grupales/chat. Cada jugador no impostor la ve únicamente
// al tocar "Ver mi rol" en su propio dispositivo.
private val IMPOSTOR_WORDS_BY_CATEGORY = ImpostorWordBank.categories
private val IMPOSTOR_WORDS: List<String> = ImpostorWordBank.allWords

@Composable
private fun ImpostorGame(c: GameCtx) {
  val flow = remember { c.vm.gameState("impostor") }
  val st by flow.collectAsState(initial = null)
  val word = st.s("word")
  val impostor = st.s("impostorUid")
  val round = st.l("round")
  val status = st.s("status", "waiting")
  var reveal by remember(round, c.me) { mutableStateOf(false) }
  var accusation by remember(round, c.me) { mutableStateOf("") }
  val canStart = c.players.contains(c.me) && c.players.distinct().size >= 3

  if (status == "waiting" || word.isBlank() || impostor.isBlank()) {
    GlassCard(modifier = Modifier.fillMaxWidth(), highlight = true) {
      Text("🕵️ EL IMPOSTOR", color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp)
      Text("Dificultad normal. Se sortea una palabra al azar de un banco de más de 2.000 palabras en 12 categorías: nombres, animales, países, ciudades, comidas, frutas y verduras, objetos, profesiones, marcas, famosos, películas y series, canciones y artistas. Todos menos el impostor la ven en privado. Hagan preguntas y den pistas sin revelar la palabra; después voten quién creen que es.", color = MaterialTheme.colorScheme.onSurfaceVariant)
      Spacer(Modifier.height(8.dp))
      Text("Jugadores: ${c.players.distinct().size}. Se necesitan al menos 3 personas.", color = GoldYellow, fontWeight = FontWeight.Bold)
    }
    Spacer(Modifier.height(10.dp))
    GradientButton("🎲 Repartir roles y comenzar", modifier = Modifier.fillMaxWidth(), enabled = canStart, onClick = {
      val players = c.players.distinct()
      // Ambos sorteos son independientes y aleatorios: una palabra cotidiana y
      // exactamente un impostor. No enviar la palabra al chat ni a mensajes grupales.
      val chosen = players.random()
      val secretWord = IMPOSTOR_WORDS.random()
      c.vm.publishGame("impostor", mapOf(
        "word" to secretWord, "impostorUid" to chosen,
        "status" to "playing", "round" to System.currentTimeMillis(),
        "players" to players, "votes" to emptyMap<String, String>()
      ))
    })
    if (!canStart) Text("El anfitrión debe reunir al menos 3 jugadores para comenzar.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    return
  }

  TurnBanner(if (status == "playing") "Ronda en curso · den pistas y descubran al impostor" else "Votación finalizada", status == "playing")
  Spacer(Modifier.height(10.dp))
  GlassCard(modifier = Modifier.fillMaxWidth(), highlight = true) {
    Text("TU ROL", color = GoldYellow, fontWeight = FontWeight.Black)
    if (!reveal) {
      Text("No muestres tu pantalla. Toca para ver tu rol en privado.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
      Spacer(Modifier.height(8.dp))
      GradientButton("👁️ Ver mi rol", modifier = Modifier.fillMaxWidth(), onClick = { reveal = true })
    } else {
      if (c.me == impostor) {
        Text("🕵️ SOS EL IMPOSTOR", color = Color(0xFFFF6B6B), fontWeight = FontWeight.Black, fontSize = 22.sp)
        Text("No conocés la palabra. Escuchá las pistas e intentá pasar desapercibido.", color = Color.White)
      } else {
        Text("✅ NO SOS EL IMPOSTOR", color = Color(0xFF8BE6A2), fontWeight = FontWeight.Black, fontSize = 20.sp)
        Text("Palabra secreta: $word", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
      }
      Spacer(Modifier.height(8.dp))
      SoftButton("Ocultar mi rol", onClick = { reveal = false })
    }
  }
  Spacer(Modifier.height(10.dp))
  Text("¿A quién acusás?", color = Color.White, fontWeight = FontWeight.Bold)
  c.players.filter { it != c.me }.forEach { uid ->
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
      Text(c.nameOf(uid), color = Color.White, modifier = Modifier.weight(1f))
      SoftButton(if (accusation == uid) "Elegido ✓" else "Votar", onClick = { accusation = uid })
    }
  }
  Spacer(Modifier.height(8.dp))
  GradientButton("🗳️ Confirmar voto", modifier = Modifier.fillMaxWidth(), enabled = accusation.isNotBlank() && status == "playing", onClick = {
    c.vm.patchGame("impostor", mapOf("votes.${c.me}" to accusation))
  })
  val votes = (st?.get("votes") as? Map<*, *>) ?: emptyMap<Any, Any>()
  Text("Votos recibidos: ${votes.size}/${c.players.distinct().size}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
  if (c.players.contains(c.me)) {
    Spacer(Modifier.height(8.dp))
    GradientButton("Revelar impostor y nueva ronda", modifier = Modifier.fillMaxWidth(), onClick = {
      c.vm.publishGame("impostor", mapOf("status" to "waiting", "word" to "", "impostorUid" to "", "round" to System.currentTimeMillis(), "votes" to emptyMap<String, String>()))
    })
  }
}

// =====================================================================
// 8) CUPONES (local, como antes)
// =====================================================================
@Composable
private fun CouponsGame(vm: CoupleViewModel) {
  val coupons by vm.coupons.collectAsState()
  Text("Toca un cupón para descubrirlo y luego canjéalo.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
  Spacer(Modifier.height(10.dp))
  coupons.forEach { cp ->
    GlassCard(
      modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp), highlight = cp.isRevealed && !cp.isRedeemed,
      onClick = { if (!cp.isRevealed) vm.revealCoupon(cp.id) }
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(if (cp.isRevealed) cp.emoji else "🎟️", fontSize = 32.sp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(if (cp.isRevealed) cp.title else "Cupón sorpresa", color = Color.White, fontWeight = FontWeight.ExtraBold)
          Text(if (cp.isRevealed) cp.description else "Toca para descubrir", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        if (cp.isRevealed && !cp.isRedeemed) SoftButton("Canjear", onClick = { vm.redeemCoupon(cp.id) })
        if (cp.isRedeemed) Text("Canjeado ✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
      }
    }
  }
}
