package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.components.pressScale
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.GlassCard
import com.example.ui.components.GradientButton
import com.example.ui.components.OptionChip
import com.example.ui.components.SoftButton
import com.example.ui.theme.GoldYellow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
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

/**
 * Los dos dados: ruedan ~3 s dando vueltas y volteretas, rebotan sobre el tapete (con sombra) y van frenando
 * hasta quedar quietos con una leve inclinación. [animate] = false muestra los dados quietos (lo usa la lupa).
 */
@Composable
private fun DicePair(v1: Int, v2: Int, rollSeq: Int, dieSize: Dp, animate: Boolean, onStart: () -> Unit, onSettled: () -> Unit) {
  val end1 by rememberUpdatedState(v1.coerceAtLeast(1))
  val end2 by rememberUpdatedState(v2.coerceAtLeast(1))
  var f1 by remember { mutableStateOf(v1.coerceAtLeast(1)) }
  var f2 by remember { mutableStateOf(v2.coerceAtLeast(1)) }
  val zA = remember { Animatable(0f) }
  val xA = remember { Animatable(0f) }
  val zB = remember { Animatable(0f) }
  val xB = remember { Animatable(0f) }
  val lift = remember { Animatable(0f) }
  LaunchedEffect(rollSeq, animate) {
    if (!animate) return@LaunchedEffect
    if (rollSeq <= 0) { f1 = end1; f2 = end2; return@LaunchedEffect }
    onStart()
    val rnd = Random.Default
    val tiltA = rnd.nextInt(-14, 15).toFloat()
    val tiltB = rnd.nextInt(-14, 15).toFloat()
    coroutineScope {
      launch { zA.snapTo(0f); zA.animateTo(720f + tiltA, tween(3200, easing = FastOutSlowInEasing)) }
      launch { zB.snapTo(0f); zB.animateTo(-720f + tiltB, tween(3200, easing = FastOutSlowInEasing)) }
      launch { xA.snapTo(0f); xA.animateTo(720f, tween(3000, easing = FastOutSlowInEasing)) }
      launch { xB.snapTo(0f); xB.animateTo(-720f, tween(3000, easing = FastOutSlowInEasing)) }
      launch {
        lift.snapTo(0f)
        for ((h, d) in listOf(1f to 520, 0.55f to 360, 0.28f to 260, 0.12f to 200)) {
          lift.animateTo(h, tween(d, easing = LinearOutSlowInEasing))
          lift.animateTo(0f, tween(d, easing = FastOutLinearInEasing))
        }
      }
      launch {
        // las caras cambian cada vez más despacio, como un dado que se va frenando
        var t = 0L; var gap = 55L
        while (t < 2700L) {
          f1 = rnd.nextInt(1, 7); f2 = rnd.nextInt(1, 7)
          delay(gap); t += gap; gap = minOf((gap * 1.16f).toLong() + 4L, 330L)
        }
        f1 = end1; f2 = end2
      }
    }
    delay(150)
    onSettled()
  }
  Row(horizontalArrangement = Arrangement.spacedBy(dieSize * 0.22f), verticalAlignment = Alignment.CenterVertically) {
    for (k in 0..1) {
      val face = if (animate) (if (k == 0) f1 else f2) else (if (k == 0) end1 else end2)
      val zr = if (k == 0) zA else zB
      val xr = if (k == 0) xA else xB
      Box(Modifier.size(dieSize)) {
        Canvas(Modifier.fillMaxSize()) {
          val l = lift.value
          drawOval(
            Color(0x55000000),
            Offset(size.width * (0.12f + 0.06f * l), size.height * 0.90f),
            Size(size.width * (0.76f - 0.12f * l), size.height * (0.16f - 0.05f * l))
          )
        }
        Box(Modifier.graphicsLayer {
          rotationZ = if (animate) zr.value else 0f
          rotationX = if (animate) xr.value else 0f
          translationY = -lift.value * size.height * 0.9f
          cameraDistance = 14f * density
        }) { DiceFace(face, dieSize) }
      }
    }
  }
}

/** Ficha sobre el tablero: se desliza hasta su casillero dando un saltito (con sombra), como una ficha de verdad. */
@Composable
private fun BoardToken(token: Int, tokSize: Dp, x: Dp, y: Dp, pos: Int, ms: Int) {
  val ax by animateDpAsState(x, tween(ms, easing = FastOutSlowInEasing), label = "tokx")
  val ay by animateDpAsState(y, tween(ms, easing = FastOutSlowInEasing), label = "toky")
  val hop = remember { Animatable(0f) }
  val firstRun = remember { booleanArrayOf(true) }
  LaunchedEffect(pos) {
    if (firstRun[0]) { firstRun[0] = false; return@LaunchedEffect }
    hop.snapTo(0f)
    hop.animateTo(1f, tween(ms, easing = LinearEasing))
  }
  val h = kotlin.math.sin(Math.PI * hop.value).toFloat()
  Box(
    Modifier.offset(x = ax - tokSize * 0.4f, y = ay + tokSize * 0.16f).size(tokSize * 0.8f, tokSize * 0.22f)
      .graphicsLayer { alpha = 0.55f * (1f - 0.55f * h); scaleX = 1f - 0.3f * h }
      .clip(CircleShape).background(Color(0x99000000))
  )
  Token(
    token, tokSize,
    Modifier.offset(x = ax - tokSize / 2, y = ay - tokSize / 2 - tokSize * 0.15f)
      .graphicsLayer { translationY = -h * tokSize.toPx() * 0.8f; scaleX = 1f + 0.14f * h; scaleY = 1f + 0.14f * h }
  )
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
  var settledSeq by remember { mutableStateOf(0) }
  var stepping by remember { mutableStateOf(false) }
  val moveMs = remember { mutableStateListOf<Int>() }
  var zoom by remember { mutableStateOf(1f) }
  var panX by remember { mutableStateOf(0f) }
  var panY by remember { mutableStateOf(0f) }
  var loupe by remember { mutableStateOf<Offset?>(null) }
  var hintOn by remember { mutableStateOf(true) }
  LaunchedEffect(Unit) { delay(9000); hintOn = false }
  var deed by remember { mutableStateOf(-1) }
  var showManage by remember { mutableStateOf(false) }
  var showTrade by remember { mutableStateOf(false) }
  var showMoney by remember { mutableStateOf(false) }
  var showLog by remember { mutableStateOf(false) }
  var showCard by remember { mutableStateOf(false) }
  val cardP = remember { Animatable(0f) }
  val sfxCtx = LocalContext.current.applicationContext
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

  // ── los dados ruedan (DicePair) y las fichas esperan a que se asienten ──
  val rollSeq = state?.rollSeq ?: 0

  // ── las fichas caminan casillero por casillero, deslizándose con un saltito ──
  LaunchedEffect(tick) {
    val ss = state ?: return@LaunchedEffect
    while (shown.size < ss.players.size) shown.add(ss.players[shown.size].pos)
    while (moveMs.size < shown.size) moveMs.add(330)
    var waited = false; var guardW = 0
    while (settledSeq < ss.rollSeq && guardW++ < 70) { waited = true; delay(60) }
    if (waited) delay(350)
    stepping = true
    try {
      for (i in ss.players.indices) {
        var guard = 0
        while (shown[i] != ss.players[i].pos && guard++ < 45) {
          val target = ss.players[i].pos
          val fwd = (target - shown[i] + 40) % 40
          if (fwd > 14) { moveMs[i] = 950; shown[i] = target; if (ss.players[i].uid == c.me) com.example.audio.GameSfx.play(sfxCtx, "sfx_mono_step", 0.32f); delay(1050); break }
          moveMs[i] = 340
          shown[i] = (shown[i] + 1) % 40
          if (ss.players[i].uid == c.me) com.example.audio.GameSfx.play(sfxCtx, "sfx_mono_step", 0.32f)   // solo lo oye quien mueve la ficha
          delay(380)
        }
        shown[i] = ss.players[i].pos
      }
    } finally { stepping = false }
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
        var spin = 0; while ((rolling || stepping) && spin++ < 80) delay(100)
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
      if (a >= 0 && s.players[a].uid == "bot") { delay(950); var spin = 0; while ((rolling || stepping) && spin++ < 80) delay(100); commit { it.botAuctionStep() } }
      return@LaunchedEffect
    }
    if (s.cur().uid != "bot") return@LaunchedEffect
    delay(if (s.phase == P_ROLL) 1100L else 850L)
    var spin = 0; while ((rolling || stepping) && spin++ < 80) delay(100)
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
          (minBots..maxBots).forEach { n -> OptionChip("$n", selected = (botsSel == n), onClick = { botsSel = n }) }
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
  // Se aprovecha todo el alto: la barra de arriba (Juegos/Reglas/Reiniciar) mide ~44 dp, ya no se deja una franja vacía abajo.
  val avail = (gameAvail() - 46.dp).coerceAtLeast(380.dp)

  // La carta sale del mazo levantada por una mano, se da vuelta y muestra su texto sobre el tablero.
  LaunchedEffect(s.cardSeq) {
    if (s.cardSeq > 0) {
      showCard = true
      cardP.snapTo(0f)
      com.example.audio.GameSfx.play(sfxCtx, "sfx_card_lift", 0.55f)
      launch { delay(1450); com.example.audio.GameSfx.play(sfxCtx, "sfx_page_flip", 0.4f) }
      cardP.animateTo(1f, tween(5200, easing = LinearEasing))
      showCard = false
    }
  }

  BoxWithConstraints(Modifier.fillMaxWidth().height(avail)) {
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

      // tablero: pellizcá o doble toque = zoom · mantené apretado = lupa · tocá un casillero = ver su escritura
      // El tablero toma todo el espacio que sobra (cuadrado, el más grande posible) y el panel y los botones quedan abajo.
      BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
        val boardSize = minOf(maxWidth, maxHeight).coerceAtLeast(120.dp)
        BoxWithConstraints(
          Modifier.size(boardSize).clip(RoundedCornerShape(8.dp)).border(2.dp, Color(0xFF2E7D32), RoundedCornerShape(8.dp))
            .pointerInput(Unit) {
              awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                do {
                  val ev = awaitPointerEvent()
                  val fingers = ev.changes.count { it.pressed }
                  if (ev.changes.none { it.isConsumed } && (fingers >= 2 || zoom > 1.02f)) {
                    val z = ev.calculateZoom(); val pn = ev.calculatePan()
                    val ct = ev.calculateCentroid(useCurrent = true)
                    if ((z != 1f || pn != Offset.Zero) && ct != Offset.Unspecified) {
                      val sizePx = size.width.toFloat()
                      val nz = (zoom * z).coerceIn(1f, 4f)
                      val cx = ct.x - sizePx / 2f; val cy = ct.y - sizePx / 2f
                      val k = nz / zoom
                      val maxP = (nz - 1f) * sizePx / 2f
                      panX = (cx - k * (cx - panX) + pn.x).coerceIn(-maxP, maxP)
                      panY = (cy - k * (cy - panY) + pn.y).coerceIn(-maxP, maxP)
                      zoom = nz
                      if (nz <= 1.001f) { zoom = 1f; panX = 0f; panY = 0f }
                      ev.changes.forEach { if (it.positionChanged()) it.consume() }
                    }
                  }
                } while (ev.changes.any { it.pressed })
              }
            }
        ) {
          val bw = maxWidth
          val sizePx = with(LocalDensity.current) { bw.toPx() }
          fun focusOn(fx: Float, fy: Float, z: Float) {
            val maxP = (z - 1f) * sizePx / 2f
            zoom = z
            panX = (-(fx * sizePx - sizePx / 2f) * z).coerceIn(-maxP, maxP)
            panY = (-(fy * sizePx - sizePx / 2f) * z).coerceIn(-maxP, maxP)
          }
          // Todo lo que se ve sobre el tablero. Se dibuja dos veces: el tablero normal y el interior de la lupa.
          val layers: @Composable (Boolean) -> Unit = { interactive ->
            Box(Modifier.size(bw)) {
              val boardId = resId("monopoly_tablero")
              if (boardId != 0) Image(painterResource(boardId), "Tablero de Monopoly", Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
              else Box(Modifier.fillMaxSize().background(Color(0xFFCDE6D0)))
              Canvas(
                Modifier.fillMaxSize().then(
                  if (interactive) Modifier
                    .pointerInput(Unit) {
                      detectTapGestures(
                        onDoubleTap = { off ->
                          if (zoom > 1.05f) { zoom = 1f; panX = 0f; panY = 0f } else focusOn(off.x / size.width, off.y / size.height, 2.6f)
                        },
                        onTap = { off -> val i = spaceAt(off.x / size.width, off.y / size.height); if (i >= 0) deed = i }
                      )
                    }
                    .pointerInput(Unit) {
                      detectDragGesturesAfterLongPress(
                        onDragStart = { off -> if (zoom < 1.3f) loupe = off },
                        onDrag = { change, _ -> change.consume(); if (loupe != null) loupe = change.position },
                        onDragEnd = { loupe = null },
                        onDragCancel = { loupe = null }
                      )
                    }
                  else Modifier
                )
              ) {
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
                BoardToken(p.token, tokSize, bw * (fx + ox), bw * (fy + oy), pos, moveMs.getOrNull(i) ?: 340)
              }
              // centro: dados + turno
              Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                DicePair(
                  s.d1, s.d2, rollSeq, bw * 0.12f, interactive,
                  onStart = { rolling = true; c.vm.gameSfx(0) },
                  onSettled = { rolling = false; settledSeq = rollSeq }
                )
                Text(
                  if (s.phase == P_OVER) "🏆 Fin" else "Turno de ${cur.name}", color = Color.White, fontWeight = FontWeight.Black, fontSize = 11.sp, maxLines = 1,
                  modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(50)).background(Color(0xAA000000)).padding(horizontal = 10.dp, vertical = 2.dp)
                )
                if (s.rulePot && s.pot > 0) Text("Pozo: ${money(s.pot)}", color = GoldYellow, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp).clip(RoundedCornerShape(50)).background(Color(0xAA000000)).padding(horizontal = 8.dp, vertical = 1.dp))
              }
              // carta de Suerte / Arca comunal que acaba de salir: una mano la levanta del mazo y la muestra
              if (showCard && s.cardText.isNotBlank()) {
                MonoCardLift(bw, s.cardTitle == "Suerte", s.cardTitle, s.cardText, cardP.value, Modifier.align(Alignment.TopStart).clickable { showCard = false })
              }
            }
          }

          // tablero con zoom / desplazamiento
          Box(Modifier.fillMaxSize().graphicsLayer { scaleX = zoom; scaleY = zoom; translationX = panX; translationY = panY }) { layers(true) }

          // lupa (mantené apretado sobre el tablero y arrastrá)
          val lp = loupe
          if (lp != null) {
            val lensDp = 150.dp
            val r = with(LocalDensity.current) { lensDp.toPx() } / 2f
            val mag = 3f
            val fx = sizePx / 2f + zoom * (lp.x - sizePx / 2f) + panX
            val fy = sizePx / 2f + zoom * (lp.y - sizePx / 2f) + panY
            var cy = fy - r * 1.35f
            if (cy - r < 0f) cy = fy + r * 1.35f
            val cx = fx.coerceIn(r, sizePx - r)
            cy = cy.coerceIn(r, sizePx - r)
            Box(
              Modifier.offset { IntOffset((cx - r).roundToInt(), (cy - r).roundToInt()) }.size(lensDp)
                .shadow(10.dp, CircleShape).clip(CircleShape).background(Color(0xFF0B1B10)).border(3.dp, GoldYellow, CircleShape)
            ) {
              Box(
                Modifier.graphicsLayer {
                  transformOrigin = TransformOrigin(0f, 0f)
                  scaleX = mag; scaleY = mag
                  translationX = r - mag * lp.x; translationY = r - mag * lp.y
                }.wrapContentSize(Alignment.TopStart, unbounded = true)
              ) { layers(false) }
              Canvas(Modifier.fillMaxSize()) {
                drawCircle(Color(0xCCFFFFFF), 5f, center, style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
              }
            }
          }

          // botón de zoom + ayuda
          Box(
            Modifier.align(Alignment.TopEnd).padding(6.dp).clip(CircleShape).background(Color(0xAA000000))
              .clickable {
                if (zoom > 1.05f) { zoom = 1f; panX = 0f; panY = 0f }
                else { val cp = spaceCenter(s.players.getOrNull(s.turn)?.pos ?: 0); focusOn(cp.first, cp.second, 2.6f) }
              }.padding(horizontal = 9.dp, vertical = 6.dp)
          ) { Text(if (zoom > 1.05f) "↩" else "🔍", fontSize = 15.sp) }
          if (hintOn) Text(
            "Mantené apretado = lupa · pellizcá o doble toque = zoom", color = Color.White, fontSize = 9.sp, textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp).clip(RoundedCornerShape(50)).background(Color(0xAA000000)).padding(horizontal = 10.dp, vertical = 3.dp)
          )
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
            Text("• " + (s.log.lastOrNull() ?: ""), color = Color(0xFFC9BEDD), fontSize = 10.sp, maxLines = 2, modifier = Modifier.clickable { showLog = true })
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

      // barra inferior (el Log ya no está aquí: se abre tocando la última jugada del panel)
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val canTrade = myTurn && tr == null && (s.phase == P_ROLL || s.phase == P_END) && s.alive().size > 1
        MAction("🏠 Propiedades", if (myIdx >= 0) "${s.ownedBy(myIdx).size} tuyas" else "-", listOf(Color(0xFF7B5CFF), Color(0xFF3D2A9E)), myIdx >= 0, Modifier.weight(1f)) { showManage = true }
        MAction("🤝 Trato", if (canTrade) "proponer" else "no disponible", listOf(Color(0xFFFF8A3D), Color(0xFFC0392B)), canTrade, Modifier.weight(1f)) { showTrade = true }
        MAction("💵 Plata", if (myIdx >= 0) money(s.players[myIdx].money) else "-", listOf(Color(0xFF2ECC71), Color(0xFF117A4D)), myIdx >= 0, Modifier.weight(1f)) { showMoney = true }
      }
    }
  }

  // ═══════════════ Diálogos ═══════════════
  if (deed >= 0) DeedDialog(s, deed) { deed = -1 }
  if (showMoney && myIdx >= 0) {
    MDialog("💵 Tu dinero", listOf(Color(0xFF2ECC71), Color(0xFF0E8A57)), { showMoney = false }) {
      val total = s.players[myIdx].money
      val shown by animateIntAsState(total, tween(900, easing = FastOutSlowInEasing), label = "cashCount")
      Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
          .background(Brush.horizontalGradient(listOf(Color(0xFF0F3D2B), Color(0xFF1E6B47), Color(0xFF0F3D2B))))
          .border(1.dp, Color(0x66A8FFD0), RoundedCornerShape(16.dp)).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Text("SALDO", color = Color(0xFFA8FFD0), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
        Text(money(shown), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black)
      }
      var rest = total
      for (d in BILLS) {
        val n = rest / d; rest -= n * d
        if (n <= 0) continue
        val rid = resId("monopoly_billete_$d")
        val pile = minOf(n, 5)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          Box(Modifier.width(138.dp + (7 * (pile - 1)).dp + 6.dp).height(64.dp + (5 * (pile - 1)).dp + 6.dp)) {
            for (q in 0 until pile) {
              val m = Modifier.offset(x = (7 * q).dp, y = (5 * q).dp).width(138.dp).height(64.dp)
                .graphicsLayer { rotationZ = if (q % 2 == 0) -1.6f else 1.6f }
                .shadow(4.dp, RoundedCornerShape(6.dp))
              if (rid != 0) Image(painterResource(rid), "Billete de $d", m, contentScale = ContentScale.FillBounds)
              else Box(m.background(Color(0xFFE8D9A8)), contentAlignment = Alignment.Center) { Text("$d", color = Color.Black) }
            }
          }
          Text("× $n", fontWeight = FontWeight.Black, fontSize = 22.sp, color = Color(0xFFFFE08A))
        }
      }
      Text("FORTUNAS DE LA MESA", color = Color(0xFFC9BEDD), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
      val top = s.players.indices.maxOfOrNull { s.assets(it) }?.coerceAtLeast(1) ?: 1
      s.players.indices.sortedByDescending { s.assets(it) }.forEach { i ->
        val frac = (s.assets(i).toFloat() / top).coerceIn(0.04f, 1f)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Row { Text(s.players[i].name + if (i == myIdx) " (vos)" else "", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Text(money(s.assets(i)), color = Color(0xFFFFE08A), fontSize = 12.sp, fontWeight = FontWeight.Black) }
          Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(Color(0x22FFFFFF))) {
            Box(Modifier.fillMaxWidth(frac).height(8.dp).clip(CircleShape).background(Brush.horizontalGradient(if (i == myIdx) listOf(Color(0xFF2ECC71), Color(0xFFB8FF6A)) else listOf(Color(0xFFFFB347), Color(0xFFFFE08A)))))
          }
        }
      }
    }
  }
  if (showLog) {
    MDialog("📜 Últimas jugadas", listOf(Color(0xFF7B5CFF), Color(0xFF3D2A9E)), { showLog = false }) {
      s.log.takeLast(30).reversed().forEachIndexed { k, line ->
        Text("•  $line", fontSize = 12.sp, color = if (k == 0) Color.White else Color(0xFFC9BEDD), fontWeight = if (k == 0) FontWeight.Bold else FontWeight.Normal)
      }
    }
  }
  if (showManage && myIdx >= 0) {
    ManageDialog(s, tick, myIdx, myTurn && (s.phase == P_ROLL || s.phase == P_BUY || s.phase == P_END || s.phase == P_DEBT), { f -> commit(f) }) { showManage = false }
  }
  if (showTrade && myTurn && s.trade == null) {
    TradeDialog(s, myIdx, { t -> commit { it.trade = t; it.addLog("${it.players[t.from].name} propone un trato a ${it.players[t.to].name}") } }) { showTrade = false }
  }
}

@Composable
private fun MDialog(
  title: String, accent: List<Color>, onClose: () -> Unit,
  footer: (@Composable androidx.compose.foundation.layout.RowScope.() -> Unit)? = null,
  content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
  Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val sc by androidx.compose.animation.core.animateFloatAsState(if (shown) 1f else 0.82f, androidx.compose.animation.core.spring(dampingRatio = 0.6f, stiffness = 380f), label = "mdSc")
    val al by androidx.compose.animation.core.animateFloatAsState(if (shown) 1f else 0f, tween(220), label = "mdAl")
    val inf = androidx.compose.animation.core.rememberInfiniteTransition(label = "mdGlow")
    val glow by inf.animateFloat(0.35f, 1f, androidx.compose.animation.core.infiniteRepeatable(tween(1700), androidx.compose.animation.core.RepeatMode.Reverse), label = "mdG")
    val shape = RoundedCornerShape(22.dp)
    Column(
      Modifier.padding(horizontal = 14.dp).fillMaxWidth().androidx_widthIn(460.dp).androidx_heightIn(620.dp)
        .graphicsLayer { scaleX = sc; scaleY = sc; alpha = al }
        .shadow(20.dp, shape, ambientColor = accent[0], spotColor = accent[0])
        .clip(shape).background(Brush.verticalGradient(listOf(Color(0xFF2B1C52), Color(0xFF130C2A))))
        .border(2.dp, Brush.linearGradient(accent.map { it.copy(alpha = glow) }), shape)
    ) {
      Row(Modifier.fillMaxWidth().background(Brush.horizontalGradient(accent)).padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
        Box(Modifier.size(30.dp).clip(CircleShape).background(Color(0x44000000)).clickable(onClick = onClose), contentAlignment = Alignment.Center) { Text("✕", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
      }
      Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
      if (footer != null) Row(Modifier.fillMaxWidth().background(Color(0x22000000)).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = footer)
    }
  }
}

private fun Modifier.androidx_widthIn(max: Dp): Modifier = this.then(Modifier.widthIn(max = max))
private fun Modifier.androidx_heightIn(max: Dp): Modifier = this.then(Modifier.heightIn(max = max))

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
private fun ManageDialog(s: MState, tick: Int, me: Int, canAct: Boolean, commit: ((MState) -> Unit) -> Unit, onClose: () -> Unit) {
  // `tick` obliga a redibujar el panel después de cada acción (el estado se modifica en el mismo objeto).
  @Suppress("UNUSED_VARIABLE") val subscribe = tick
  val haptic = LocalHapticFeedback.current
  val mine = s.ownedBy(me)
  val cash = s.players[me].money
  val shownCash by animateIntAsState(cash, tween(550), label = "manageCash")
  var notice by remember { mutableStateOf("") }
  var flash by remember { mutableStateOf(-1) }
  LaunchedEffect(notice) { if (notice.isNotEmpty()) { delay(2800); notice = "" } }
  LaunchedEffect(flash) { if (flash >= 0) { delay(650); flash = -1 } }

  fun act(i: Int, f: (MState) -> Boolean) {
    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    var ok = false
    commit { st -> ok = f(st) }
    if (ok) { flash = i; notice = s.log.lastOrNull() ?: "" }
  }

  fun buildReason(i: Int): String {
    val sp = MONO_SPACES[i]
    if (sp.type != MT_PROP) return ""
    val g = MONO_GROUPS[sp.group] ?: return ""
    return when {
      !canAct -> "Solo podés construir en tu turno"
      !s.ownsGroup(me, sp.group) -> "Necesitás todo el grupo de color"
      g.any { s.mort[it] } -> "Hay una propiedad hipotecada en el grupo"
      s.houses[i] >= 5 -> "Ya tiene hotel"
      cash < sp.houseCost -> "No te alcanza el dinero"
      s.houses[i] != g.minOf { s.houses[it] } -> "Construí parejo: primero en las otras del grupo"
      s.houses[i] < 4 && s.housesUsed() >= 32 -> "No quedan casas en el banco"
      s.houses[i] == 4 && s.hotelsUsed() >= 12 -> "No quedan hoteles en el banco"
      else -> ""
    }
  }

  fun mortgageReason(i: Int): String {
    val sp = MONO_SPACES[i]
    return when {
      !canAct -> "Solo podés hipotecar en tu turno"
      sp.group >= 0 && MONO_GROUPS[sp.group]?.any { s.houses[it] > 0 } == true -> "Vendé primero las casas del grupo"
      else -> ""
    }
  }

  Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
    val panel = RoundedCornerShape(26.dp)
    Column(
      Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.9f).shadow(18.dp, panel).clip(panel)
        .background(Brush.verticalGradient(listOf(Color(0xFF241F3D), Color(0xFF14121F))))
        .border(1.dp, Brush.linearGradient(listOf(Color(0x77FFD54F), Color(0x22FFFFFF), Color(0x5500E5FF))), panel)
    ) {
      // ── cabecera ──
      Row(
        Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(Color(0xFF6A1B9A), Color(0xFF283593)))).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(Modifier.weight(1f)) {
          Text("🏠 Mis propiedades", fontWeight = FontWeight.Black, fontSize = 19.sp, color = Color.White)
          Text("${mine.size} propiedad${if (mine.size == 1) "" else "es"} · quedan ${32 - s.housesUsed()} casas y ${12 - s.hotelsUsed()} hoteles", fontSize = 11.sp, color = Color(0xCCFFFFFF))
        }
        Text(
          money(shownCash), fontWeight = FontWeight.Black, fontSize = 17.sp, color = Color(0xFF1B5E20),
          modifier = Modifier.clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(listOf(Color(0xFFB9F6CA), Color(0xFF69F0AE)))).padding(horizontal = 14.dp, vertical = 7.dp)
        )
      }
      AnimatedVisibility(notice.isNotEmpty()) {
        Text(
          "✅ $notice", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB9F6CA),
          modifier = Modifier.fillMaxWidth().background(Color(0x3300C853)).padding(horizontal = 14.dp, vertical = 8.dp)
        )
      }
      if (!canAct && mine.isNotEmpty()) {
        Text(
          "⏳ Podés construir, vender e hipotecar solo en tu turno.", fontSize = 12.sp, color = Color(0xFFFFCC80),
          modifier = Modifier.fillMaxWidth().background(Color(0x33FF9800)).padding(horizontal = 14.dp, vertical = 8.dp)
        )
      }
      // ── lista ──
      Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (mine.isEmpty()) {
          Text("Todavía no tenés propiedades. Comprá en el tablero y volvé acá para construir o hipotecar.", color = Color(0xFFB0B0C0), fontSize = 13.sp, modifier = Modifier.padding(8.dp))
        }
        mine.groupBy { MONO_SPACES[it].group }.forEach { (g, list) ->
          val gc = GROUP_COLORS.getOrElse(g) { Color(0xFF777777) }
          val total = MONO_GROUPS[g]?.size ?: list.size
          val complete = s.ownsGroup(me, g)
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 2.dp)) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(gc))
            Text(MONO_GROUP_NAMES.getOrElse(g) { "" }.uppercase(), fontWeight = FontWeight.Black, fontSize = 12.sp, color = Color(0xFFE0E0F0))
            Text("${list.size}/$total", fontSize = 11.sp, color = Color(0xFF9E9EB8))
            if (complete) Text(
              "COMPLETO ✔", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color(0xFF1B1B1B),
              modifier = Modifier.clip(RoundedCornerShape(50)).background(GoldYellow).padding(horizontal = 8.dp, vertical = 2.dp)
            )
          }
          list.forEach { i ->
            val sp = MONO_SPACES[i]
            val isProp = sp.type == MT_PROP
            val h = s.houses[i]
            val mort = s.mort[i]
            val card = RoundedCornerShape(16.dp)
            val bg by animateColorAsState(if (flash == i) Color(0x5500E676) else Color(0x22FFFFFF), tween(350), label = "cardFlash")
            Column(Modifier.fillMaxWidth().clip(card).background(bg).border(1.dp, gc.copy(alpha = 0.55f), card).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp, 40.dp).clip(RoundedCornerShape(4.dp)).background(Brush.verticalGradient(listOf(gc, gc.copy(alpha = 0.55f)))))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                  Text(sp.name, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = Color.White, maxLines = 1)
                  Text(if (mort) "Hipotecada · sin cobrar alquiler" else "Alquiler ${money(s.rentOf(i, 7))}", fontSize = 11.sp, color = Color(0xFFB0B0C8))
                }
                if (isProp) Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                  if (h == 5) Box(Modifier.size(26.dp, 18.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFFE53935)), contentAlignment = Alignment.Center) { Text("H", color = Color.White, fontWeight = FontWeight.Black, fontSize = 11.sp) }
                  else for (q in 1..4) Box(Modifier.size(15.dp).clip(RoundedCornerShape(3.dp)).background(if (q <= h) Color(0xFF43A047) else Color(0x33FFFFFF)))
                }
                if (mort) Text("🔒", fontSize = 18.sp, modifier = Modifier.padding(start = 6.dp))
              }
              Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isProp) {
                  val canB = canAct && s.canBuild(i)
                  val canS = canAct && s.canSellBuilding(i)
                  MAction(if (h == 4) "🏨 Hotel" else "🏠 Casa", money(sp.houseCost), listOf(Color(0xFF66BB6A), Color(0xFF2E7D32)), canB, Modifier.weight(1f)) { act(i) { it.build(i) } }
                  MAction("💰 Vender", "+${money(sp.houseCost / 2)}", listOf(Color(0xFFFFB74D), Color(0xFFE65100)), canS, Modifier.weight(1f)) { act(i) { it.sellBuilding(i) } }
                }
                if (mort) {
                  val cost = s.unmortgageCost(i)
                  MAction("🔓 Levantar", "-${money(cost)}", listOf(Color(0xFF4FC3F7), Color(0xFF0277BD)), canAct && cash >= cost, Modifier.weight(1f)) { act(i) { it.unmortgage(i) } }
                } else {
                  MAction("🔒 Hipotecar", "+${money(sp.price / 2)}", listOf(Color(0xFFEF5350), Color(0xFFB71C1C)), canAct && s.canMortgage(i), Modifier.weight(1f)) { act(i) { it.mortgage(i) } }
                }
              }
              val hint = when {
                mort && cash < s.unmortgageCost(i) -> "No te alcanza para levantar la hipoteca"
                !canAct -> ""
                isProp && !mort && buildReason(i).isNotEmpty() -> buildReason(i)
                !mort && mortgageReason(i).isNotEmpty() -> mortgageReason(i)
                else -> ""
              }
              if (hint.isNotEmpty()) Text("ℹ️ $hint", fontSize = 10.sp, color = Color(0xFF9E9EB8))
            }
          }
        }
        Text(
          "Reglas: se construye parejo dentro del grupo, con el grupo completo y sin hipotecas. Vender una construcción devuelve la mitad de lo que costó.",
          fontSize = 10.sp, color = Color(0xFF8A8AA3), modifier = Modifier.padding(top = 4.dp)
        )
      }
      // ── pie ──
      Box(
        Modifier.fillMaxWidth().padding(12.dp).height(50.dp).clip(RoundedCornerShape(16.dp))
          .background(Brush.horizontalGradient(listOf(Color(0xFF7C4DFF), Color(0xFF448AFF))))
          .clickable(onClick = onClose),
        contentAlignment = Alignment.Center
      ) { Text("Cerrar", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp) }
    }
  }
}

@Composable
private fun MAction(label: String, sub: String, colors: List<Color>, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
  val source = remember { MutableInteractionSource() }
  val shape = RoundedCornerShape(14.dp)
  Column(
    modifier.height(54.dp).pressScale(source, 0.93f).clip(shape)
      .background(if (enabled) Brush.verticalGradient(colors) else Brush.verticalGradient(listOf(Color(0xFF3A3A48), Color(0xFF2C2C38))))
      .border(1.dp, if (enabled) Color(0x66FFFFFF) else Color(0x22FFFFFF), shape)
      .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    Text(label, fontWeight = FontWeight.Black, fontSize = 13.sp, color = if (enabled) Color.White else Color(0x77FFFFFF), maxLines = 1)
    Text(sub, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = if (enabled) Color(0xE6FFFFFF) else Color(0x55FFFFFF), maxLines = 1)
  }
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
  MDialog("🤝 Proponer un trato", listOf(Color(0xFFFF8A3D), Color(0xFFC0392B)), onClose, footer = {
    SoftButton("Cancelar", onClick = onClose, modifier = Modifier.weight(1f))
    GradientButton("Proponer", modifier = Modifier.weight(1.4f), height = 42.dp, onClick = {
      val t = MTrade(me, partner)
      t.giveProps.addAll(give); t.getProps.addAll(get); t.giveCash = giveCash; t.getCash = getCash
      val e = s.tradeValid(t)
      if (e != null) err = e else { onPropose(t); onClose() }
    })
  }) {
    Text("CON QUIÉN", fontSize = 10.sp, color = Color(0xFFC9BEDD), fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      others.forEach { o ->
        val sel = o == partner
        Text(
          s.players[o].name, fontWeight = FontWeight.Black, fontSize = 13.sp, color = if (sel) Color(0xFF2B1C00) else Color.White,
          modifier = Modifier.clip(RoundedCornerShape(50))
            .background(if (sel) Brush.horizontalGradient(listOf(Color(0xFFFFE08A), Color(0xFFFFB347))) else Brush.horizontalGradient(listOf(Color(0x33FFFFFF), Color(0x22FFFFFF))))
            .border(1.dp, if (sel) Color(0xFFFFE08A) else Color(0x44FFFFFF), RoundedCornerShape(50))
            .clickable { partner = o; get.clear() }.padding(horizontal = 14.dp, vertical = 7.dp)
        )
      }
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0x1A2ECC71)).border(1.dp, Color(0x552ECC71), RoundedCornerShape(14.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text("⬆️ VOS DAS", fontWeight = FontWeight.Black, fontSize = 12.sp, color = Color(0xFF7DFFB0))
      PropPicker(s, s.ownedBy(me), give)
      CashStepper("Efectivo", giveCash, s.players[me].money) { giveCash = it }
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0x1AFFB347)).border(1.dp, Color(0x55FFB347), RoundedCornerShape(14.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text("⬇️ VOS PEDÍS", fontWeight = FontWeight.Black, fontSize = 12.sp, color = Color(0xFFFFD08A))
      PropPicker(s, s.ownedBy(partner), get)
      CashStepper("Efectivo", getCash, s.players[partner].money) { getCash = it }
    }
    if (err.isNotEmpty()) Text("⚠️ $err", color = Color(0xFFFF8A80), fontSize = 12.sp, fontWeight = FontWeight.Bold)
  }
}

@Composable
private fun PropPicker(s: MState, list: List<Int>, sel: MutableList<Int>) {
  if (list.isEmpty()) { Text("(sin propiedades)", fontSize = 11.sp, color = Color(0xFF9E9E9E)); return }
  Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    list.forEach { i ->
      val on = i in sel
      Row(
        Modifier.clip(RoundedCornerShape(10.dp)).background(if (on) GoldYellow else Color(0x33FFFFFF)).border(1.dp, if (on) Color.White else Color(0x33FFFFFF), RoundedCornerShape(10.dp)).clickable { if (on) sel.remove(i) else sel.add(i) }.padding(horizontal = 8.dp, vertical = 5.dp),
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
    Text("💵 ${money(value)}", fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color.White, modifier = Modifier.width(96.dp))
    SoftButton("−50", onClick = { onChange((value - 50).coerceAtLeast(0)) })
    SoftButton("+50", onClick = { onChange((value + 50).coerceAtMost(max)) })
    SoftButton("+200", onClick = { onChange((value + 200).coerceAtMost(max)) })
  }
}

// ───────────────────────── Carta levantada por una mano ─────────────────────────

/** Mano estilizada que pellizca el borde de la carta (la muñeca sale hacia abajo a la derecha). */
@Composable
private fun CardHand(modifier: Modifier) {
  Canvas(modifier) {
    val k = size.width / 100f
    fun o(x: Float, y: Float) = Offset(x * k, y * k)
    val skin = Color(0xFFF1C7A0); val skinD = Color(0xFFD59F76)
    // antebrazo y puño de la camisa
    drawLine(skin, o(58f, 66f), o(98f, 112f), 24f * k, StrokeCap.Round)
    drawLine(Color(0xFFF4F4F8), o(88f, 100f), o(100f, 114f), 30f * k, StrokeCap.Butt)
    // palma y dedos recogidos
    drawCircle(skin, 23f * k, o(56f, 62f))
    for ((cx, cy) in listOf(66f to 40f, 75f to 47f, 79f to 58f)) {
      drawCircle(skinD, 9.5f * k, o(cx, cy)); drawCircle(skin, 8.5f * k, o(cx - 1f, cy - 1f))
    }
    // pulgar (abajo) e índice (arriba) pellizcando el borde de la carta
    drawLine(skinD, o(44f, 78f), o(20f, 72f), 13f * k, StrokeCap.Round)
    drawLine(skin, o(44f, 76f), o(21f, 71f), 11f * k, StrokeCap.Round)
    drawLine(skinD, o(48f, 50f), o(21f, 64f), 12f * k, StrokeCap.Round)
    drawLine(skin, o(48f, 49f), o(22f, 63f), 10f * k, StrokeCap.Round)
    drawCircle(Color(0x55FFFFFF), 2.2f * k, o(24f, 61f))
  }
}

@Composable
private fun MonoCardLift(bw: Dp, chance: Boolean, title: String, text: String, p: Float, modifier: Modifier) {
  fun ease(x: Float) = (1f - (1f - x.coerceIn(0f, 1f)).let { it * it * it })
  val lift = ease(p / 0.24f)                                    // sale del mazo
  val flip = ease((p - 0.26f) / 0.12f)                          // se da vuelta
  val out = ((p - 0.90f) / 0.10f).coerceIn(0f, 1f)             // se va
  val deckX = if (chance) 0.728f else 0.274f
  val deckY = if (chance) 0.72f else 0.255f
  val cx = deckX + (0.5f - deckX) * lift
  val cy = deckY + (0.47f - deckY) * lift - 0.012f * kotlin.math.sin(p * 9f) * lift
  val w = bw * 0.54f; val h = bw * 0.34f
  val sc = (0.34f + 0.66f * lift) * (1f - 0.12f * out)
  val handA = (1f - ((p - 0.24f) / 0.10f).coerceIn(0f, 1f)) * (1f - out)
  val main = if (chance) Color(0xFFFB8C00) else Color(0xFF1E88E5)
  val dark = if (chance) Color(0xFFC25E00) else Color(0xFF0D5DB0)
  val shape = RoundedCornerShape(12.dp)
  Box(modifier.fillMaxSize()) {
    Box(
      Modifier.offset(bw * cx - w / 2, bw * cy - h / 2).size(w, h)
        .graphicsLayer {
          scaleX = sc; scaleY = sc; alpha = 1f - out
          rotationZ = (-16f * (1f - lift)) + 2f * kotlin.math.sin(p * 7f) * flip
          rotationX = 38f * (1f - lift)
          rotationY = 180f * flip
          cameraDistance = 14f * density
        }
        .shadow((2f + 16f * lift).dp, shape).clip(shape)
        .background(Brush.linearGradient(listOf(main, dark)))
        .border(2.dp, Color(0xFFFFF3D6), shape),
      contentAlignment = Alignment.Center
    ) {
      if (flip < 0.5f) {
        // dorso del mazo
        Box(Modifier.fillMaxSize().padding(8.dp).border(1.5.dp, Color(0x88FFFFFF), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
          Text(if (chance) "?" else "📦", color = Color.White, fontWeight = FontWeight.Black, fontSize = 34.sp)
        }
      } else {
        // frente (se compensa el giro para que el texto no quede espejado)
        Column(Modifier.fillMaxSize().graphicsLayer { rotationY = 180f }.background(Color(0xFFFFF8E6)), horizontalAlignment = Alignment.CenterHorizontally) {
          Text(
            if (chance) "❓ SUERTE" else "📦 ARCA COMUNAL", color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().background(main).padding(vertical = 4.dp)
          )
          Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), contentAlignment = Alignment.Center) {
            Text(text, color = Color(0xFF2B1C00), fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
          }
        }
      }
    }
    if (handA > 0.02f) {
      val hs = bw * 0.30f
      Box(
        Modifier.offset(bw * cx - w * sc / 2 - hs * 0.12f, bw * cy - h * sc / 2 - hs * 0.60f).size(hs, hs * 1.2f)
          .graphicsLayer { alpha = handA; rotationZ = -8f * (1f - lift) }
      ) { CardHand(Modifier.fillMaxSize()) }
    }
  }
}
