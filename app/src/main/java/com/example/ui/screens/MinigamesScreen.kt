package com.example.ui.screens

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.draw.shadow
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.draw.clipToBounds
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
private val N_GAMES = setOf("ludo", "bowl", "darts", "tejo", "virus", "d10k", "truco", "tutti", "uno", "impostor", "pool", "race", "mono")
private val SIX_PLAYER_GAMES = setOf("virus", "tutti", "uno", "impostor", "pool", "race", "mono")

private data class GameInfo(val id: String, val emoji: String, val title: String, val desc: String)

private val GAME_LIST = listOf(
  GameInfo("bowl", "🎳", "Bowling 3D", "Física real, cámara que sigue la bola, chuzas y spares para 1 a 4 jugadores con bots"),
  GameInfo("mono", "🎩", "Monopoly", "Reglamento oficial de Hasbro: propiedades, casas y hoteles, subastas, trueques, cárcel y quiebra; humanos y bots"),
  GameInfo("race", "🏎️", "Carreras 3D", "Pista con curvas y subidas, 3 vueltas, nitro, hasta 6 pilotos humanos en vivo y bots"),
  GameInfo("pool", "🎱", "Pool", "Mesa de 9 pies con física real, bola 8 y bola 9, repetición de las embocadas, hasta 6 humanos y bots"),
  GameInfo("darts", "🎯", "Dardos", "Tablero reglamentario, 301, 501 y alrededor del reloj; hasta 4 jugadores y bots"),
  GameInfo("tejo", "🪙", "Tejo", "Bocín, mechas que explotan, moñonas y mano; hasta 4 jugadores y bots"),
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
  GameInfo("ttt", "❤️", "TaTeTi", "Corazones contra besos, por turnos"),
  GameInfo("c4", "🔴", "Conecta 4", "Junta cuatro fichas antes que tu pareja"),
  GameInfo("mem", "🧠", "Memoria de corazones", "Encuentren las parejas en 5 niveles, de medio-bajo a súper alto"),
  GameInfo("rps", "✊", "Piedra, papel o tijera", "Elijan en secreto y reveladlo a la vez"),
  GameInfo("chess", "♟️", "Ajedrez", "Jaque, enroque, coronación y jaque mate en vivo"),
  GameInfo("impostor", "🕵️", "El impostor", "Descubran quién no conoce la palabra secreta · 3 a 6 jugadores"),
  GameInfo("coupons", "🎟️", "Cupones rasca", "Descubre y canjea premios de amor")
)

private val GAME_CATS = listOf(
  "Todos" to emptySet<String>(),
  "🏟️ Deportes" to setOf("bowl", "race", "pool", "darts", "tejo"),
  "🃏 Cartas" to setOf("truco", "virus", "uno", "poker", "mem"),
  "🎲 Mesa" to setOf("mono", "d10k", "ludo", "snl", "chess", "ttt", "c4", "rps"),
  "📝 Palabras" to setOf("tutti", "hang", "riddle", "draw", "impostor"),
  "💞 Extras" to setOf("shop", "coupons")
)

@Composable
fun MinigamesScreen(viewModel: CoupleViewModel) {
  var selected by remember { mutableStateOf<String?>(null) }
  var catIdx by remember { mutableIntStateOf(0) }
  var showRules by remember { mutableStateOf(false) }
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

  // Juegos que se juegan sin deslizar: todo entra en la pantalla (se ajusta solo al ancho y alto de cada teléfono).
  // Los de escribir texto o con listas largas mantienen el scroll.
  val noScrollGame = selected != null && selected !in setOf("shop", "coupons", "tutti", "riddle", "draw", "hang", "impostor", "qa", "tod")
  androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
  val availH = maxHeight - 16.dp
  androidx.compose.runtime.CompositionLocalProvider(LocalGameAvail provides availH) {
  Column(
    modifier = (if (noScrollGame) Modifier.fillMaxSize().clipToBounds().fitToHeight() else Modifier.fillMaxSize().verticalScroll(rememberScrollState()))
      .padding(horizontal = 12.dp, vertical = 8.dp)
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
      Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        GAME_CATS.forEachIndexed { i, (name, _) ->
          Text(
            name, color = if (catIdx == i) Color.Black else Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp,
            modifier = Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
              .background(if (catIdx == i) GoldYellow else Color(0x33FFFFFF)).clickable { catIdx = i }.padding(horizontal = 14.dp, vertical = 7.dp)
          )
        }
      }
      val catSet = GAME_CATS[catIdx].second
      GAME_LIST.filter { catSet.isEmpty() || it.id in catSet }.chunked(2).forEachIndexed { rowIdx, row ->
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
        if (showRules) {
          AlertDialog(
            onDismissRequest = { showRules = false },
            title = { Text("${info.emoji} ${info.title}") },
            text = { Text(info.desc) },
            confirmButton = { TextButton(onClick = { showRules = false }) { Text("Entendido") } }
          )
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
          Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            SoftButton("ℹ️ Reglas", onClick = { showRules = true })
            SoftButton("🔄 Reiniciar", onClick = { showRestartDialog = true })
          }
        Box(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
        key(resetCounter) {
        when (selected) {
          "ludo" -> LudoGame(ctx)
          "pool" -> PoolGame(ctx)
          "race" -> RaceGame(ctx)
          "mono" -> MonopolyGame(ctx)
          "bowl" -> BowlingGame(ctx)
          "darts" -> DartsGame(ctx)
          "tejo" -> TejoGame(ctx)
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
    // El walkie vive solo arriba a la derecha, junto a la foto (se quitó el micrófono de abajo).
    if (!noScrollGame) Spacer(Modifier.height(24.dp))
  }
  }
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

// ───────────── Modos de partida compartidos por TaTeTi y Conecta 4 ─────────────
private class GridMode(val name: String, val emoji: String, val target: Int, val infinite: Boolean = false)

@Composable
private fun GridModeChips(modes: List<GridMode>, selected: Int, enabled: Boolean, onPick: (Int) -> Unit) {
  Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    modes.forEachIndexed { i, m ->
      val label = "${m.emoji} ${m.name}"
      if (i == selected) GradientButton(label, onClick = { if (enabled) onPick(i) }, height = 38.dp)
      else SoftButton(label, onClick = { if (enabled) onPick(i) }, enabled = enabled)
    }
  }
}

// =====================================================================
// 1) TATETÍ
// =====================================================================
private val TTT_LINES = listOf(
  listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8), listOf(0, 3, 6), listOf(1, 4, 7), listOf(2, 5, 8), listOf(0, 4, 8), listOf(2, 4, 6)
)
private val TTT_MODES = listOf(
  GridMode("Clásico", "❤️", 0),
  GridMode("Mejor de 3", "🥉", 2),
  GridMode("Mejor de 5", "🥈", 3),
  GridMode("Infinito", "♾️", 0, true)
)

@Composable
private fun TttMark(symbol: String, fading: Boolean) {
  val sc = remember { Animatable(1f) }
  LaunchedEffect(symbol) {
    if (symbol.isNotEmpty()) { sc.snapTo(0.2f); sc.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 280f)) }
  }
  val pulse by rememberInfiniteTransition(label = "tttPulse").animateFloat(
    initialValue = 0.35f, targetValue = 0.85f,
    animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "tttPulseV"
  )
  Text(
    symbol, fontSize = 46.sp,
    modifier = Modifier.graphicsLayer { scaleX = sc.value; scaleY = sc.value; alpha = if (fading) pulse else 1f }
  )
}

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
  val modeIdx = st.i("mode").coerceIn(0, TTT_MODES.size - 1)
  val mode = TTT_MODES[modeIdx]
  val last = st.i("last", -1)
  val orders = listOf(st.intList("orderA"), st.intList("orderB"))
  val sym = listOf("❤️", "💋")
  val me = c.myIdx.coerceIn(0, 1)
  val winLine = TTT_LINES.firstOrNull { l -> board[l[0]].isNotEmpty() && board[l[0]] == board[l[1]] && board[l[1]] == board[l[2]] } ?: emptyList()
  val matchOver = mode.target > 0 && (scoreA >= mode.target || scoreB >= mode.target)
  val boardEmpty = board.all { it.isEmpty() }
  val enter = remember { Animatable(0f) }
  LaunchedEffect(Unit) { enter.animateTo(1f, tween(450, easing = FastOutSlowInEasing)) }
  val winAnim = remember(round, winLine.isNotEmpty()) { Animatable(0f) }
  LaunchedEffect(round, winLine.isNotEmpty()) {
    if (winLine.isNotEmpty()) { winAnim.snapTo(0f); winAnim.animateTo(1f, tween(550, easing = FastOutSlowInEasing)) }
  }

  fun publish(nb: List<String>, nextTurn: String, w: String, a: Int, b: Int, r: Int, no: List<List<Int>>, lastMove: Int, m: Int) {
    c.vm.publishGame(
      "ttt2", mapOf(
        "board" to nb, "turn" to nextTurn, "winner" to w, "scoreA" to a, "scoreB" to b, "round" to r,
        "orderA" to no[0], "orderB" to no[1], "last" to lastMove, "mode" to m
      )
    )
  }
  fun play(i: Int) {
    if (turn != c.me || board[i].isNotEmpty() || winner.isNotEmpty() || matchOver) return
    val nb = board.toMutableList()
    val no = orders.map { it.toMutableList() }
    nb[i] = sym[me]
    // Modo infinito: cada jugador tiene como máximo 3 piezas; al poner la cuarta desaparece la más vieja.
    if (mode.infinite) {
      no[me].add(i)
      if (no[me].size > 3) { val gone = no[me].removeAt(0); nb[gone] = "" }
    }
    var w = ""
    for (line in TTT_LINES) {
      if (nb[line[0]].isNotEmpty() && nb[line[0]] == nb[line[1]] && nb[line[1]] == nb[line[2]]) w = nb[line[0]]
    }
    if (w.isEmpty() && !mode.infinite && nb.none { it.isEmpty() }) w = "EMPATE"
    var a = scoreA
    var b = scoreB
    if (w == sym[0]) a++ else if (w == sym[1]) b++
    publish(nb, c.otherUid, w, a, b, round, no, i, modeIdx)
    c.vm.gameSfx(if (w.isNotEmpty()) 2 else 0)
  }
  fun nextRound(resetScores: Boolean, m: Int = modeIdx) {
    publish(List(9) { "" }, c.uidAt((round + 1) % 2), "", if (resetScores) 0 else scoreA, if (resetScores) 0 else scoreB, if (resetScores) 1 else round + 1, listOf(emptyList(), emptyList()), -1, m)
  }

  Column(Modifier.graphicsLayer { alpha = enter.value; translationY = (1f - enter.value) * 30f }) {
    ScoreBoard(c, scoreA, scoreB, if (winner.isEmpty()) turn else null, sym[0], sym[1])
    Spacer(Modifier.height(8.dp))
    GridModeChips(TTT_MODES, modeIdx, boardEmpty || matchOver) { nextRound(true, it) }
    Text(
      (if (mode.target > 0) "Primero en ganar ${mode.target} rondas" else if (mode.infinite) "Solo 3 piezas por jugador: la más vieja desaparece" else "Rondas sin límite") + " · Ronda $round",
      color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp
    )
    Spacer(Modifier.height(8.dp))
    TurnBanner(
      when {
        winner == "EMPATE" -> "🤝 ¡Empate!"
        winner.isNotEmpty() -> "🏆 ¡Ganó ${c.nameOfIdx(sym.indexOf(winner))}!"
        turn == c.me -> "Tu turno: coloca tu ${sym[me]}"
        else -> "Esperando a ${c.partnerName}…"
      }, turn == c.me && winner.isEmpty()
    )
    Spacer(Modifier.height(12.dp))
    val shape = RoundedCornerShape(28.dp)
    BoxWithConstraints(
      Modifier.fillMaxWidth().aspectRatio(1f).clip(shape)
        .background(Brush.linearGradient(listOf(Color(0x33B388FF), Color(0x1438BDF8))))
        .border(1.5.dp, Color(0x55FFFFFF), shape)
    ) {
      // Rejilla de neón
      Canvas(Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height; val pad = 14.dp.toPx()
        for (k in 1..2) {
          drawLine(Color(0x66B388FF), Offset(w * k / 3f, pad), Offset(w * k / 3f, h - pad), 5.dp.toPx(), StrokeCap.Round)
          drawLine(Color(0x6638BDF8), Offset(pad, h * k / 3f), Offset(w - pad, h * k / 3f), 5.dp.toPx(), StrokeCap.Round)
          drawLine(Color(0xCCFFFFFF), Offset(w * k / 3f, pad), Offset(w * k / 3f, h - pad), 1.5.dp.toPx(), StrokeCap.Round)
          drawLine(Color(0xCCFFFFFF), Offset(pad, h * k / 3f), Offset(w - pad, h * k / 3f), 1.5.dp.toPx(), StrokeCap.Round)
        }
      }
      Column(Modifier.fillMaxSize()) {
        for (r in 0..2) {
          Row(Modifier.weight(1f).fillMaxWidth()) {
            for (col in 0..2) {
              val idx = r * 3 + col
              // La pieza más vieja de cada jugador parpadea cuando va a desaparecer (modo infinito).
              val vanishing = mode.infinite && (0..1).any { p -> orders[p].size == 3 && orders[p].first() == idx }
              Box(Modifier.weight(1f).fillMaxSize().clickable { play(idx) }, contentAlignment = Alignment.Center) {
                if (idx == last && board[idx].isNotEmpty()) Box(Modifier.fillMaxSize(0.78f).clip(CircleShape).background(Color(0x22FFC857)))
                if (winLine.contains(idx)) Box(Modifier.fillMaxSize(0.82f).clip(CircleShape).background(Color(0x44FFC857)))
                TttMark(board[idx], vanishing)
              }
            }
          }
        }
      }
      // Línea ganadora que se dibuja sobre las tres casillas
      if (winLine.isNotEmpty()) {
        Canvas(Modifier.fillMaxSize()) {
          fun center(i: Int) = Offset(((i % 3) + 0.5f) * size.width / 3f, ((i / 3) + 0.5f) * size.height / 3f)
          val a = center(winLine[0]); val b = center(winLine[2])
          val end = Offset(a.x + (b.x - a.x) * winAnim.value, a.y + (b.y - a.y) * winAnim.value)
          drawLine(Color(0x55FFC857), a, end, 18.dp.toPx(), StrokeCap.Round)
          drawLine(Color(0xFFFFE08A), a, end, 6.dp.toPx(), StrokeCap.Round)
        }
      }
      if (winner.isNotEmpty() && winner != "EMPATE") RpsConfetti(Modifier.fillMaxSize())
    }
    Spacer(Modifier.height(10.dp))
    if (winner.isNotEmpty() && matchOver) {
      val champ = if (scoreA > scoreB) 0 else 1
      GlassCard(modifier = Modifier.fillMaxWidth(), corner = 20.dp, highlight = true) {
        Text("🏆 ¡${c.nameOfIdx(champ)} ganó la partida!", color = GoldYellow, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
        Text("Resultado final: $scoreA a $scoreB", color = Color.White, fontSize = 13.sp)
      }
      Spacer(Modifier.height(8.dp))
      GradientButton("🔁 Nueva partida", modifier = Modifier.fillMaxWidth(), onClick = { nextRound(true) })
    } else if (winner.isNotEmpty()) {
      GradientButton("Siguiente ronda", modifier = Modifier.fillMaxWidth(), onClick = { nextRound(false) })
    } else {
      SoftButton("Reiniciar la ronda", onClick = { nextRound(false) }, modifier = Modifier.fillMaxWidth(), enabled = !boardEmpty)
    }
  }
}

// =====================================================================
// 2) CONECTA 4
// =====================================================================
private val C4_MODES = listOf(
  GridMode("Clásico", "🔴", 0),
  GridMode("Mejor de 3", "🥉", 2),
  GridMode("Mejor de 5", "🥈", 3),
  GridMode("Mejor de 7", "🥇", 4)
)
private val C4_RED = Color(0xFFE53935)
private val C4_YELLOW = Color(0xFFFFCA28)

private fun c4Winner(b: List<Int>): Int {
  val cells = c4WinCells(b)
  if (cells.isNotEmpty()) return b[cells[0]]
  return if (b.none { it == 0 }) 3 else 0
}

/** Casillas de la línea ganadora (cuatro o más fichas seguidas), o vacío si todavía no hay. */
private fun c4WinCells(b: List<Int>): List<Int> {
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
      if (k == 4) return (0 until 4).map { (r + dr * it) * 7 + (col + dc * it) }
    }
  }
  return emptyList()
}

@Composable
private fun C4Disc(value: Int, row: Int, isLast: Boolean, win: Boolean, modifier: Modifier = Modifier) {
  val ctx = LocalContext.current
  val prog = remember { Animatable(if (value != 0) 1f else 0f) }
  LaunchedEffect(value) {
    if (value == 0) prog.snapTo(0f)
    else if (prog.value < 1f) {
      prog.snapTo(0f)
      prog.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 170f))
    }
  }
  LaunchedEffect(value, isLast) {
    if (value != 0 && isLast) { delay(360); com.example.audio.GameSfx.play(ctx, "sfx_card", 0.35f) }
  }
  val glow by rememberInfiniteTransition(label = "c4Glow").animateFloat(
    initialValue = 0.4f, targetValue = 1f, animationSpec = infiniteRepeatable(tween(600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "c4GlowV"
  )
  Box(modifier) {
    // hueco del tablero
    Box(Modifier.fillMaxSize().clip(CircleShape).background(Brush.verticalGradient(listOf(Color(0xFF050F2E), Color(0xFF12266B)))))
    if (value != 0) {
      val base = if (value == 1) C4_RED else C4_YELLOW
      Box(
        Modifier.fillMaxSize()
          .graphicsLayer { translationY = -(1f - prog.value) * size.height * (row + 1) }
          .clip(CircleShape)
          .background(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.85f), base, base.copy(red = base.red * 0.55f, green = base.green * 0.55f, blue = base.blue * 0.55f))))
          .border(2.dp, if (win) Color.White.copy(alpha = glow) else Color(0x33000000), CircleShape)
      ) {
        Box(Modifier.fillMaxSize(0.62f).align(Alignment.Center).clip(CircleShape).border(1.5.dp, Color(0x44FFFFFF), CircleShape))
      }
    }
  }
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
  val modeIdx = st.i("mode").coerceIn(0, C4_MODES.size - 1)
  val mode = C4_MODES[modeIdx]
  val last = st.i("last", -1)
  val me = c.myIdx.coerceIn(0, 1)
  val winCells = remember(board) { c4WinCells(board) }
  val matchOver = mode.target > 0 && (scoreA >= mode.target || scoreB >= mode.target)
  val boardEmpty = board.all { it == 0 }
  val enter = remember { Animatable(0f) }
  LaunchedEffect(Unit) { enter.animateTo(1f, tween(450, easing = FastOutSlowInEasing)) }
  val arrow by rememberInfiniteTransition(label = "c4Arrow").animateFloat(
    initialValue = 0f, targetValue = 1f, animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "c4ArrowV"
  )

  fun publish(nb: List<Int>, nextTurn: String, w: Int, a: Int, b: Int, r: Int, lastMove: Int, m: Int) {
    c.vm.publishGame("c4", mapOf("board" to nb, "turn" to nextTurn, "winner" to w, "scoreA" to a, "scoreB" to b, "round" to r, "last" to lastMove, "mode" to m))
  }
  fun drop(col: Int) {
    if (turn != c.me || winner != 0 || matchOver) return
    var row = -1
    for (r in 5 downTo 0) if (board[r * 7 + col] == 0) { row = r; break }
    if (row < 0) return
    val nb = board.toMutableList()
    nb[row * 7 + col] = me + 1
    val w = c4Winner(nb)
    var a = scoreA
    var b = scoreB
    if (w == 1) a++ else if (w == 2) b++
    publish(nb, c.otherUid, w, a, b, round, row * 7 + col, modeIdx)
    c.vm.gameSfx(if (w != 0) 2 else 0)
  }
  fun nextRound(resetScores: Boolean, m: Int = modeIdx) {
    publish(List(42) { 0 }, c.uidAt((round + 1) % 2), 0, if (resetScores) 0 else scoreA, if (resetScores) 0 else scoreB, if (resetScores) 1 else round + 1, -1, m)
  }

  Column(Modifier.graphicsLayer { alpha = enter.value; translationY = (1f - enter.value) * 30f }) {
    ScoreBoard(c, scoreA, scoreB, if (winner == 0) turn else null, "🔴", "🟡")
    Spacer(Modifier.height(8.dp))
    GridModeChips(C4_MODES, modeIdx, boardEmpty || matchOver) { nextRound(true, it) }
    Text(
      (if (mode.target > 0) "Primero en ganar ${mode.target} rondas" else "Rondas sin límite") + " · Ronda $round",
      color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp
    )
    Spacer(Modifier.height(8.dp))
    TurnBanner(
      when {
        winner == 3 -> "🤝 ¡Empate!"
        winner != 0 -> "🏆 ¡Ganó ${c.nameOfIdx(winner - 1)}!"
        turn == c.me -> "Tu turno: tocá una columna"
        else -> "Esperando a ${c.partnerName}…"
      }, turn == c.me && winner == 0
    )
    Spacer(Modifier.height(8.dp))
    // Flechas sobre cada columna, que invitan a soltar la ficha
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
      for (col in 0 until 7) {
        val full = board[col] != 0
        val active = turn == c.me && winner == 0 && !full && !matchOver
        Box(Modifier.weight(1f).height(26.dp).clickable(enabled = active) { drop(col) }, contentAlignment = Alignment.Center) {
          if (active) Text("▼", color = if (me == 0) C4_RED else C4_YELLOW, fontSize = 16.sp, modifier = Modifier.graphicsLayer { translationY = arrow * 5f; alpha = 0.55f + 0.45f * arrow })
        }
      }
    }
    val frame = RoundedCornerShape(24.dp)
    Box(
      Modifier.fillMaxWidth().shadow(10.dp, frame).clip(frame)
        .background(Brush.verticalGradient(listOf(Color(0xFF2563EB), Color(0xFF1B3FA0), Color(0xFF142D75))))
        .border(2.dp, Color(0x66FFFFFF), frame).padding(8.dp)
    ) {
      Column {
        for (r in 0 until 6) {
          Row(Modifier.fillMaxWidth()) {
            for (col in 0 until 7) {
              val idx = r * 7 + col
              C4Disc(board[idx], r, idx == last, winCells.contains(idx), Modifier.weight(1f).aspectRatio(1f).padding(3.dp).clickable { drop(col) })
            }
          }
        }
      }
      if (winner in 1..2) RpsConfetti(Modifier.matchParentSize())
    }
    Spacer(Modifier.height(10.dp))
    if (winner != 0 && matchOver) {
      val champ = if (scoreA > scoreB) 0 else 1
      GlassCard(modifier = Modifier.fillMaxWidth(), corner = 20.dp, highlight = true) {
        Text("🏆 ¡${c.nameOfIdx(champ)} ganó la partida!", color = GoldYellow, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
        Text("Resultado final: $scoreA a $scoreB", color = Color.White, fontSize = 13.sp)
      }
      Spacer(Modifier.height(8.dp))
      GradientButton("🔁 Nueva partida", modifier = Modifier.fillMaxWidth(), onClick = { nextRound(true) })
    } else if (winner != 0) {
      GradientButton("Siguiente ronda", modifier = Modifier.fillMaxWidth(), onClick = { nextRound(false) })
    } else {
      SoftButton("Reiniciar la ronda", onClick = { nextRound(false) }, modifier = Modifier.fillMaxWidth(), enabled = !boardEmpty)
    }
  }
}

// =====================================================================
// 3) MEMORIA
// =====================================================================
/** Niveles de dificultad: cantidad de cartas, columnas y milisegundos que las cartas falladas quedan a la vista. */
private class MemLevel(val name: String, val cards: Int, val cols: Int, val hideMs: Long)
private val MEM_LEVELS = listOf(
  MemLevel("Medio-bajo", 12, 3, 1400),
  MemLevel("Medio", 16, 4, 1200),
  MemLevel("Medio-alto", 20, 4, 1000),
  MemLevel("Alto", 24, 4, 800),
  MemLevel("Súper alto", 30, 5, 600)
)
private val MEM_SYMBOLS = listOf("💖", "🌹", "🍓", "🎁", "🌙", "⭐", "🍫", "🎵", "🌸", "🦋", "🍒", "🔥", "🐱", "🍀", "💎")

@Composable
private fun MemoryGame(c: GameCtx) {
  val flow = remember { c.vm.gameState("mem") }
  val st by flow.collectAsState(initial = null)
  val level = st.i("level").coerceIn(0, MEM_LEVELS.size - 1)
  val lv = MEM_LEVELS[level]
  val total = lv.cards
  val cards = st.strs("cards", total)
  val started = (st?.get("cards") as? List<*>)?.size == total
  val owner = st.ints("owner", total, -1)
  val flipped = st.intList("flipped")
  val turn = st.s("turn", c.firstUid)
  val scoreA = st.i("scoreA")
  val scoreB = st.i("scoreB")
  val finished = started && owner.none { it == -1 }
  // El nivel elegido se conserva al barajar de nuevo: si ya hay partida manda el guardado.
  var levelSel by remember { mutableStateOf(0) }
  LaunchedEffect(started, level) { if (started) levelSel = level }

  fun publish(newOwner: List<Int>, newFlipped: List<Int>, newTurn: String, a: Int, b: Int) {
    c.vm.publishGame("mem", mapOf(
      "cards" to cards, "owner" to newOwner, "flipped" to newFlipped, "turn" to newTurn, "scoreA" to a, "scoreB" to b, "level" to level
    ))
  }

  // Si fallé la pareja, yo (dueño del turno) las vuelvo a tapar y paso el turno.
  LaunchedEffect(flipped, turn) {
    if (turn == c.me && flipped.size == 2) {
      delay(lv.hideMs)
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
      !started -> "Elegí el nivel y tocá “Empezar” para repartir las cartas"
      finished -> if (scoreA == scoreB) "🤝 ¡Empate!" else "🏆 ¡Ganó ${c.nameOfIdx(if (scoreA > scoreB) 0 else 1)}!"
      turn == c.me -> "Tu turno: destapa dos cartas"
      else -> "Esperando a ${c.partnerName}…"
    }, started && turn == c.me && !finished
  )
  Spacer(Modifier.height(8.dp))
  if (!started || finished) {
    Text("Dificultad", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      MEM_LEVELS.forEachIndexed { i, l ->
        val label = "${l.name} · ${l.cards}"
        if (levelSel == i) GradientButton(label, onClick = { levelSel = i }, height = 38.dp) else SoftButton(label, onClick = { levelSel = i })
      }
    }
  } else {
    Text("Nivel: ${lv.name} · ${lv.cards} cartas", color = GoldYellow, fontWeight = FontWeight.Bold, fontSize = 12.sp)
  }
  Spacer(Modifier.height(8.dp))
  if (started) {
    val rows = (total + lv.cols - 1) / lv.cols
    for (r in 0 until rows) {
      Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (col in 0 until lv.cols) {
          val idx = r * lv.cols + col
          if (idx >= total) { Spacer(Modifier.weight(1f)); continue }
          val faceUp = owner[idx] != -1 || flipped.contains(idx)
          FlipCard(
            faceUp = faceUp,
            frontText = cards[idx],
            frontBrush = if (owner[idx] != -1) SolidColor(if (owner[idx] == 0) Color(0x66FF4D8D) else Color(0x6637E6FF)) else SolidColor(GlassWhite),
            backBrush = LocalDuoPalette.current.accentDiagonal,
            modifier = Modifier.weight(1f).aspectRatio(if (lv.cols == 3) 1.2f else 1f).clickable { tap(idx) }
          )
        }
      }
    }
  }
  Spacer(Modifier.height(8.dp))
  GradientButton(
    if (started) "Barajar de nuevo (${MEM_LEVELS[levelSel].name})" else "Empezar en ${MEM_LEVELS[levelSel].name}", modifier = Modifier.fillMaxWidth(),
    onClick = {
      val chosen = MEM_LEVELS[levelSel]
      val deck = MEM_SYMBOLS.shuffled().take(chosen.cards / 2).let { it + it }.shuffled()
      c.vm.publishGame("mem", mapOf(
        "cards" to deck, "owner" to List(chosen.cards) { -1 }, "flipped" to emptyList<Int>(),
        "turn" to c.firstUid, "scoreA" to 0, "scoreB" to 0, "level" to levelSel
      ))
    }
  )
}

// =====================================================================
// 4) PIEDRA, PAPEL O TIJERA
// =====================================================================
// ───────────── Piedra, papel o tijera: modos, apuestas por escrito y revelación animada ─────────────
private const val RPS_ROCK = "🪨"
private const val RPS_PAPER = "📄"
private const val RPS_SCISSORS = "✂️"
private const val RPS_LIZARD = "🦎"
private const val RPS_SPOCK_E = "🖖"
private val RPS_BASIC = listOf(RPS_ROCK, RPS_PAPER, RPS_SCISSORS)
private val RPS_SPOCK = listOf(RPS_ROCK, RPS_PAPER, RPS_SCISSORS, RPS_LIZARD, RPS_SPOCK_E)

private class RpsMode(val name: String, val emoji: String, val desc: String, val target: Int, val spock: Boolean)
private val RPS_MODES = listOf(
  RpsMode("Clásico", "✊", "Rondas sin límite, para jugar sin presión.", 0, false),
  RpsMode("Mejor de 3", "🥉", "Gana quien consiga 2 rondas.", 2, false),
  RpsMode("Mejor de 5", "🥈", "Gana quien consiga 3 rondas.", 3, false),
  RpsMode("Mejor de 7", "🥇", "Gana quien consiga 4 rondas.", 4, false),
  RpsMode("Muerte súbita", "💀", "Una sola ronda decide todo.", 1, false),
  RpsMode("Lagarto y Spock", "🖖", "Cinco jugadas: piedra, papel, tijera, lagarto y Spock. Mejor de 5.", 3, true)
)

private fun rpsBeats(a: String, b: String): Boolean = when (a) {
  RPS_ROCK -> b == RPS_SCISSORS || b == RPS_LIZARD
  RPS_PAPER -> b == RPS_ROCK || b == RPS_SPOCK_E
  RPS_SCISSORS -> b == RPS_PAPER || b == RPS_LIZARD
  RPS_LIZARD -> b == RPS_SPOCK_E || b == RPS_PAPER
  RPS_SPOCK_E -> b == RPS_SCISSORS || b == RPS_ROCK
  else -> false
}

private fun rpsName(e: String): String = when (e) {
  RPS_ROCK -> "Piedra"
  RPS_PAPER -> "Papel"
  RPS_SCISSORS -> "Tijera"
  RPS_LIZARD -> "Lagarto"
  else -> "Spock"
}

@Composable
private fun RpsConfetti(modifier: Modifier = Modifier) {
  val t by rememberInfiniteTransition(label = "rpsConfetti").animateFloat(
    initialValue = 0f, targetValue = 1f, animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing)), label = "rpsConfettiT"
  )
  val cols = listOf(Color(0xFFFF5C93), Color(0xFFFFC857), Color(0xFF38BDF8), Color(0xFF7DE3A6), Color.White)
  Canvas(modifier) {
    for (i in 0 until 46) {
      val seed = ((i * 7919) % 1000) / 1000f
      val seed2 = ((i * 104729) % 997) / 997f
      val x = size.width * seed
      val y = ((t * (0.6f + seed2) + seed2) % 1f) * (size.height + 40f) - 20f
      drawRect(cols[i % cols.size], Offset(x, y), Size(8.dp.toPx(), 12.dp.toPx()))
    }
  }
}

@Composable
private fun RpsOptionButton(emoji: String, label: String, index: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
  val appear = remember { Animatable(0f) }
  LaunchedEffect(Unit) { delay(index * 60L); appear.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 320f)) }
  Box(
    modifier = modifier.aspectRatio(1f)
      .graphicsLayer { scaleX = appear.value; scaleY = appear.value; alpha = appear.value.coerceIn(0f, 1f) }
      .clip(RoundedCornerShape(22.dp))
      .background(LocalDuoPalette.current.accentDiagonal)
      .border(1.5.dp, Color(0x66FFFFFF), RoundedCornerShape(22.dp))
      .clickable { onClick() },
    contentAlignment = Alignment.Center
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Text(emoji, fontSize = 38.sp)
      Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
  }
}

@Composable
private fun RpsGame(c: GameCtx) {
  val ctx = LocalContext.current
  val flow = remember { c.vm.gameState("rps") }
  val st by flow.collectAsState(initial = null)
  val started = st.i("started") == 1
  val modeIdx = st.i("mode").coerceIn(0, RPS_MODES.size - 1)
  val mode = RPS_MODES[modeIdx]
  val options = if (mode.spock) RPS_SPOCK else RPS_BASIC
  val picks = listOf(st.s("pickA"), st.s("pickB"))
  val scoreA = st.i("scoreA")
  val scoreB = st.i("scoreB")
  val round = st.i("round", 1)
  val result = st.s("result")
  val draws = st.i("draws")
  val bets = listOf(st.s("betA"), st.s("betB"))
  val me = c.myIdx.coerceIn(0, 1)
  val myPick = picks[me]
  val bothPicked = picks[0].isNotEmpty() && picks[1].isNotEmpty()

  var modeSel by remember { mutableStateOf(0) }
  var betText by remember { mutableStateOf("") }
  var betLoaded by remember { mutableStateOf(false) }
  LaunchedEffect(bets[me]) { if (!betLoaded && bets[me].isNotEmpty()) { betText = bets[me]; betLoaded = true } }
  var revealStep by remember { mutableStateOf(0) } // 0 nada · 1 puños sacudiéndose · 2 jugadas reveladas
  val enter = remember { Animatable(0f) }
  LaunchedEffect(started) { enter.snapTo(0f); enter.animateTo(1f, tween(450, easing = FastOutSlowInEasing)) }

  // Cuenta regresiva y revelación: los dos jugadores ven lo mismo a partir del estado compartido.
  LaunchedEffect(round, bothPicked) {
    if (bothPicked) {
      revealStep = 1
      repeat(3) { com.example.audio.GameSfx.play(ctx, "sfx_countdown_tick", 0.45f); delay(450) }
      revealStep = 2
      val iWon = (result == "A" && me == 0) || (result == "B" && me == 1)
      c.vm.gameSfx(if (iWon) 2 else if (result == "T") 1 else 0)
    } else revealStep = 0
  }
  val shake by rememberInfiniteTransition(label = "rpsShake").animateFloat(
    initialValue = -1f, targetValue = 1f, animationSpec = infiniteRepeatable(tween(170, easing = LinearEasing), RepeatMode.Reverse), label = "rpsShakeV"
  )

  fun publish(pa: String, pb: String, a: Int, b: Int, r: Int, res: String, d: Int, startedNow: Int = 1, mIdx: Int = modeIdx, betA: String = bets[0], betB: String = bets[1]) {
    c.vm.publishGame(
      "rps", mapOf(
        "started" to startedNow, "mode" to mIdx, "pickA" to pa, "pickB" to pb, "scoreA" to a, "scoreB" to b,
        "round" to r, "result" to res, "draws" to d, "betA" to betA, "betB" to betB
      )
    )
  }
  fun pick(o: String) {
    if (myPick.isNotEmpty() || !started) return
    val np = picks.toMutableList()
    np[me] = o
    var a = scoreA; var b = scoreB; var d = draws
    var res = ""
    if (np[0].isNotEmpty() && np[1].isNotEmpty()) {
      res = when {
        np[0] == np[1] -> { d++; "T" }
        rpsBeats(np[0], np[1]) -> { a++; "A" }
        else -> { b++; "B" }
      }
    }
    publish(np[0], np[1], a, b, round, res, d)
  }

  // ───────────── Sala previa: modo y apuesta por escrito ─────────────
  if (!started) {
    Column(Modifier.graphicsLayer { alpha = enter.value; translationY = (1f - enter.value) * 30f }) {
      GlassCard(modifier = Modifier.fillMaxWidth(), highlight = true) {
        Text("✊ Piedra, papel o tijera", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
        Text("Elegí el modo y escriban qué se juegan. Quien pierda la partida cumple lo que escribió.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
      }
      Spacer(Modifier.height(10.dp))
      Text("Modo de juego", color = Color.White, fontWeight = FontWeight.Bold)
      Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        RPS_MODES.forEachIndexed { i, m ->
          val label = "${m.emoji} ${m.name}"
          if (modeSel == i) GradientButton(label, onClick = { modeSel = i }, height = 40.dp) else SoftButton(label, onClick = { modeSel = i })
        }
      }
      Text(RPS_MODES[modeSel].desc, color = Color.White, fontSize = 12.sp)
      Spacer(Modifier.height(10.dp))
      GlassCard(modifier = Modifier.fillMaxWidth(), corner = 20.dp) {
        Text("🎯 Apuesta por escrito", color = GoldYellow, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
        Text("Escribí lo que te comprometés a hacer si perdés (por ejemplo: «lavo los platos», «masaje de 10 minutos»).", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
          value = betText,
          onValueChange = { v ->
            betText = v.take(80)
            c.vm.patchGame("rps", mapOf((if (me == 0) "betA" else "betB") to v.take(80)))
          },
          label = { Text("Si pierdo, yo…") }, singleLine = false, modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))
        val other = bets[1 - me]
        Text(
          if (other.isBlank()) "${c.partnerName} todavía no escribió su apuesta." else "${c.partnerName} apuesta: «$other»",
          color = if (other.isBlank()) Color(0xAAFFFFFF) else Color(0xFF7DE3A6), fontSize = 12.sp, fontWeight = FontWeight.Bold
        )
      }
      Spacer(Modifier.height(10.dp))
      GradientButton("✊ Empezar la partida", modifier = Modifier.fillMaxWidth(), onClick = {
        val myBet = betText.trim()
        publish("", "", 0, 0, 1, "", 0, 1, modeSel, if (me == 0) myBet else bets[0], if (me == 1) myBet else bets[1])
      })
    }
    return
  }

  // ───────────── Partida ─────────────
  val dispA = scoreA - (if (revealStep < 2 && bothPicked && result == "A") 1 else 0)
  val dispB = scoreB - (if (revealStep < 2 && bothPicked && result == "B") 1 else 0)
  val matchOver = mode.target > 0 && (scoreA >= mode.target || scoreB >= mode.target)
  val showFinal = matchOver && bothPicked && revealStep == 2
  val winnerIdx = if (scoreA > scoreB) 0 else 1
  Column(Modifier.graphicsLayer { alpha = enter.value; translationY = (1f - enter.value) * 30f }) {
    GlassCard(modifier = Modifier.fillMaxWidth(), corner = 18.dp) {
      Text(
        "${mode.emoji} ${mode.name}" + (if (mode.target > 0) " · primero en llegar a ${mode.target}" else " · sin límite") + " · Ronda $round",
        color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp
      )
      for (i in 0..1) {
        if (bets[i].isNotBlank()) Text("🎯 Si pierde ${c.nameOfIdx(i)}: «${bets[i]}»", color = Color(0xFFFFC857), fontSize = 11.sp)
      }
    }
    Spacer(Modifier.height(8.dp))
    ScoreBoard(c, dispA, dispB, null, "✊", "✊")
    Spacer(Modifier.height(10.dp))
    // Arena: los dos puños se sacuden tres veces y se revelan las jugadas.
    Box(
      Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
        .background(Brush.verticalGradient(listOf(Color(0xFF2B1B52), Color(0xFF120A2B))))
        .border(1.5.dp, Color(0x55B388FF), RoundedCornerShape(26.dp)).padding(vertical = 18.dp, horizontal = 8.dp)
    ) {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        for (i in 0..1) {
          val revealed = revealStep == 2 && bothPicked
          val isWinner = revealed && ((i == 0 && result == "A") || (i == 1 && result == "B"))
          val isLoser = revealed && result != "T" && !isWinner
          val shown = when {
            revealed -> picks[i]
            bothPicked -> "✊"
            i == me && picks[i].isNotEmpty() -> picks[i]
            picks[i].isNotEmpty() -> "✅"
            else -> "…"
          }
          val pop = remember(round, revealed) { Animatable(if (revealed) 0.3f else 1f) }
          LaunchedEffect(round, revealed) { if (revealed) pop.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 260f)) }
          val dir = if (i == 0) 1f else -1f
          Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
            Text(c.nameOfIdx(i), color = Color(0xCCFFFFFF), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Spacer(Modifier.height(6.dp))
            Text(
              shown, fontSize = 72.sp,
              modifier = Modifier.graphicsLayer {
                val sh = if (bothPicked && revealStep == 1) shake else 0f
                rotationZ = sh * 22f * dir
                translationY = if (bothPicked && revealStep == 1) -kotlin.math.abs(sh) * 26f else 0f
                val sc = if (isWinner) 1.25f * pop.value else if (isLoser) 0.8f else pop.value
                scaleX = sc; scaleY = sc
                alpha = if (isLoser) 0.45f else 1f
              }
            )
            Text(if (revealed) rpsName(picks[i]) else " ", color = if (isWinner) Color(0xFFFFC857) else Color(0xAAFFFFFF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
          }
          if (i == 0) Text("VS", color = Color(0xFFFFC857), fontWeight = FontWeight.Black, fontSize = 18.sp)
        }
      }
      if (showFinal) RpsConfetti(Modifier.matchParentSize())
    }
    Spacer(Modifier.height(10.dp))
    TurnBanner(
      when {
        bothPicked && revealStep < 2 -> "¡Piedra, papel o…!"
        bothPicked -> when (result) {
          "T" -> "🤝 ¡Empate! (empates: $draws)"
          "A" -> "🏆 Ganó la ronda ${c.nameOfIdx(0)}"
          else -> "🏆 Ganó la ronda ${c.nameOfIdx(1)}"
        }
        myPick.isEmpty() -> "Elegí tu jugada en secreto"
        else -> "Elegiste ${rpsName(myPick)}. Esperando a ${c.partnerName}…"
      }, !bothPicked && myPick.isEmpty()
    )
    Spacer(Modifier.height(10.dp))
    if (!bothPicked && myPick.isEmpty()) {
      options.chunked(3).forEachIndexed { row, group ->
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          group.forEachIndexed { k, o -> RpsOptionButton(o, rpsName(o), row * 3 + k, Modifier.weight(1f)) { pick(o) } }
          repeat(3 - group.size) { Spacer(Modifier.weight(1f)) }
        }
      }
    }
    if (showFinal) {
      val loser = 1 - winnerIdx
      GlassCard(modifier = Modifier.fillMaxWidth(), corner = 22.dp, highlight = true) {
        Text("🏆 ¡${c.nameOfIdx(winnerIdx)} ganó la partida!", color = Color(0xFFFFC857), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
        Text("Resultado: ${scoreA} a ${scoreB}", color = Color.White, fontSize = 13.sp)
        Spacer(Modifier.height(4.dp))
        Text(
          if (bets[loser].isBlank()) "No había apuesta escrita." else "🎯 ${c.nameOfIdx(loser)} cumple: «${bets[loser]}»",
          color = Color(0xFF7DE3A6), fontWeight = FontWeight.Bold, fontSize = 14.sp
        )
      }
      Spacer(Modifier.height(8.dp))
      GradientButton("🔁 Revancha (misma apuesta)", modifier = Modifier.fillMaxWidth(), onClick = { publish("", "", 0, 0, 1, "", 0) })
      Spacer(Modifier.height(6.dp))
      SoftButton("✍️ Nueva apuesta y modo", onClick = { publish("", "", 0, 0, 1, "", 0, 0, modeIdx, "", "") }, modifier = Modifier.fillMaxWidth())
    } else if (bothPicked && revealStep == 2) {
      GradientButton("Siguiente ronda", modifier = Modifier.fillMaxWidth(), onClick = { publish("", "", scoreA, scoreB, round + 1, "", draws) })
    }
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
