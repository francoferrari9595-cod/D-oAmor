package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.GlassCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/*
 * ¡BASTA! — versión digital del juego de mesa electrónico (rueda con 26 teclas y botón central «Mano»).
 * Reglas del original: se lee una carta con la categoría, se pulsa el botón central y quien tiene el turno
 * dispone de 30 segundos (en el original eran 10; acá se alargó porque se juega a distancia) para (1) decir una palabra de la categoría, (2) pulsar la tecla de su letra inicial
 * —esa letra se apaga y ya no se puede usar en la ronda— y (3) pulsar el botón central para pasar el reloj al
 * siguiente. Quien no completa las tres cosas a tiempo queda fuera de la ronda; gana la ronda el último que
 * queda (o quien apaga la última letra) y se gana la partida con 3 rondas.
 */

private val BASTA_LETTERS = ('A'..'Z').toList()
private const val BASTA_TURN_MS = 30_000L
private const val BASTA_GRACE_MS = 1_500L
private const val BASTA_WIN_ROUNDS = 3

/** 36 cartas de categoría, como el juego original. */
private val BASTA_CARDS = listOf(
  "🐾 Animales", "🌎 Países", "🏙️ Ciudades", "🍎 Frutas", "🥕 Verduras", "🍕 Comidas", "🥤 Bebidas", "👩 Nombres de mujer",
  "👨 Nombres de hombre", "💼 Profesiones", "⚽ Deportes", "🛋️ Cosas de la casa", "🦴 Partes del cuerpo", "👕 Ropa y calzado",
  "🏷️ Marcas", "🎬 Películas", "📺 Series", "🎤 Cantantes y bandas", "🎸 Instrumentos musicales", "🚗 Medios de transporte",
  "✏️ Cosas de la escuela", "🍳 Cosas de la cocina", "🔧 Herramientas", "🧸 Juguetes", "🌸 Flores y plantas", "🐞 Insectos",
  "🦜 Aves", "🌊 Ríos, mares y lagos", "🏖️ Cosas de la playa", "🧊 Cosas frías", "⚪ Cosas redondas", "🦸 Personajes de ficción",
  "🍰 Postres y dulces", "😱 Cosas que dan miedo", "🛁 Cosas del baño", "🎁 Cosas que se regalan"
)

private val BASTA_SEAT_COLORS = listOf(
  Color(0xFFFF2D55), Color(0xFF00E5FF), Color(0xFF39FF14), Color(0xFFFFEA00), Color(0xFFFF2BD6), Color(0xFFFF9100), Color(0xFFB388FF), Color(0xFF69F0AE)
)

private data class BSt(
  val round: Int = 0, val phase: String = "idle", val cat: String = "", val used: List<String> = emptyList(), val pend: String = "",
  val turn: Int = 0, val alive: List<Int> = emptyList(), val dl: Long = 0L, val last: String = "", val lastSeat: Int = -1,
  val starter: Int = -1, val usedCats: List<String> = emptyList(), val winner: Int = -1, val ev: Int = 0, val word: String = "",
  val wins: List<Int> = emptyList(), val n: Int = 2, val words: List<String> = emptyList()
)

private fun parseB(m: Map<String, Any>?, n: Int): BSt {
  fun i(k: String, d: Int = 0) = (m?.get(k) as? Number)?.toInt() ?: d
  fun sl(k: String) = (m?.get(k) as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
  return BSt(
    round = i("round"), phase = (m?.get("phase") as? String) ?: "idle", cat = (m?.get("cat") as? String) ?: "",
    used = sl("used"), pend = (m?.get("pend") as? String) ?: "", turn = i("turn"),
    alive = ((m?.get("alive") as? List<*>)?.mapNotNull { (it as? Number)?.toInt() } ?: emptyList()).filter { it in 0 until n }.distinct(),
    dl = (m?.get("dl") as? Number)?.toLong() ?: 0L, last = (m?.get("last") as? String) ?: "", lastSeat = i("lastSeat", -1),
    starter = i("starter", -1), usedCats = sl("usedCats"), winner = i("winner", -1), ev = i("ev"), word = (m?.get("word") as? String) ?: "",
    wins = List(n) { i("w_$it") }, n = n, words = sl("words")
  )
}

private fun BSt.toMap(): Map<String, Any?> {
  val out = HashMap<String, Any?>()
  out["round"] = round; out["phase"] = phase; out["cat"] = cat; out["used"] = used; out["pend"] = pend; out["turn"] = turn
  out["alive"] = alive; out["dl"] = dl; out["last"] = last; out["lastSeat"] = lastSeat; out["starter"] = starter
  out["usedCats"] = usedCats; out["winner"] = winner; out["ev"] = ev; out["word"] = word; out["n"] = n; out["words"] = words
  wins.forEachIndexed { k, v -> out["w_$k"] = v }
  return out
}

private fun firstLetterOf(word: String): Char? {
  val t = java.text.Normalizer.normalize(word.trim().uppercase().replace("Ñ", "\u0001"), java.text.Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "").replace("\u0001", "Ñ")
  return t.firstOrNull { it.isLetter() }
}

private fun nextAlive(alive: List<Int>, from: Int, n: Int): Int {
  for (k in 1..n) { val s = (from + k) % n; if (s in alive) return s }
  return from
}

@Composable
internal fun BastaGame(c: GameCtx) {
  val flow = remember { c.vm.gameState("basta") }
  val doc by flow.collectAsState(initial = null)
  val nLive = (c.players.size + c.lobbyBots).coerceIn(2, 8)
  // Durante la partida se usa la cantidad de asientos con la que arrancó la ronda: si alguien se desconecta un
  // instante, los asientos no se corren ni se rompen los índices (antes eso cerraba la app).
  val storedN = (doc?.get("n") as? Number)?.toInt()?.takeIf { it in 2..8 }
  val phaseNow = (doc?.get("phase") as? String) ?: "idle"
  val n = if (storedN != null && (phaseNow == "play" || phaseNow == "roundEnd")) storedN else nLive
  val st = parseB(doc, n)
  val latest by rememberUpdatedState(st)
  val mySeat = c.players.indexOf(c.me)
  val isHost = c.me == c.firstUid
  var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
  var msg by remember { mutableStateOf("") }
  // Ventana para escribir la palabra: aparece al tocar una letra y tiene un único botón, «Aceptar».
  var pendingLetter by remember { mutableStateOf<Char?>(null) }
  var wordTfv by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue("")) }
  var wordWarn by remember { mutableStateOf("") }
  var warnedKey by remember { mutableStateOf("") }
  val ctx = androidx.compose.ui.platform.LocalContext.current.applicationContext
  LaunchedEffect(Unit) { withContext(Dispatchers.IO) { BastaDict.load(ctx) } }
  val haptic = LocalHapticFeedback.current

  fun seatName(i: Int) = if (i in c.players.indices) c.nameOfIdx(i) else "Bot ${i - c.players.size + 1}"
  fun isBot(i: Int) = i >= c.players.size
  fun push(b: BSt) = c.vm.publishGame("basta", b.copy(ev = b.ev + 1).toMap())

  fun startRound(b: BSt, fresh: Boolean): BSt {
    val taken = if (fresh) emptyList() else b.usedCats
    val cat = (BASTA_CARDS.filter { it !in taken }.ifEmpty { BASTA_CARDS }).random()
    val nn = nLive
    val starter = if (fresh || b.starter < 0) 0 else (b.starter + 1) % nn
    return BSt(
      round = if (fresh) 1 else b.round + 1, phase = "play", cat = cat, used = emptyList(), pend = "", turn = starter,
      alive = (0 until nn).toList(), dl = System.currentTimeMillis() + BASTA_TURN_MS,
      last = "Arranca ${seatName(starter)}", lastSeat = -1, starter = starter, usedCats = taken + cat, winner = -1,
      ev = b.ev, word = "", wins = List(nn) { if (fresh) 0 else b.wins.getOrElse(it) { 0 } }, n = nn
    )
  }
  fun endRound(b: BSt, winner: Int, why: String): BSt {
    val wn = winner.coerceIn(0, (b.wins.size - 1).coerceAtLeast(0))
    val w2 = (if (b.wins.isEmpty()) List(n) { 0 } else b.wins).toMutableList().also { it[wn] = it[wn] + 1 }
    val over = w2[wn] >= BASTA_WIN_ROUNDS
    return b.copy(phase = if (over) "matchEnd" else "roundEnd", winner = wn, wins = w2, pend = "", last = why)
  }
  fun passTurn(b: BSt, text: String): BSt =
    b.copy(turn = nextAlive(b.alive, b.turn, n), pend = "", dl = System.currentTimeMillis() + BASTA_TURN_MS, last = text)
  fun eliminate(b: BSt, seat: Int): BSt {
    val alive2 = b.alive.filter { it != seat }
    val text = "⏰ ${seatName(seat)} no llegó a tiempo y queda fuera"
    return if (alive2.size <= 1) endRound(b.copy(alive = alive2), alive2.firstOrNull() ?: seat, "$text. ¡Gana la ronda ${seatName(alive2.firstOrNull() ?: seat)}!")
    else b.copy(alive = alive2, turn = nextAlive(alive2, seat, n), pend = "", dl = System.currentTimeMillis() + BASTA_TURN_MS, last = text)
  }

  LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(100) } }

  // Sonidos para todos los dispositivos cuando cambia el estado compartido.
  var seenEv by remember { mutableStateOf(st.ev) }
  LaunchedEffect(st.ev) {
    if (st.ev != seenEv) {
      seenEv = st.ev
      when {
        st.phase == "roundEnd" || st.phase == "matchEnd" -> c.vm.gameSfx(2)
        st.phase == "play" && st.used.isEmpty() -> c.vm.gameSfx(1)
        else -> c.vm.gameSfx(0)
      }
      if (st.turn != mySeat || st.phase != "play") pendingLetter = null
    }
  }

  // El anfitrión hace de árbitro: mueve a los bots y elimina a quien se queda sin tiempo.
  LaunchedEffect(st.ev, st.phase, st.turn) {
    if (!isHost || st.phase != "play") return@LaunchedEffect
    try {
    if (isBot(st.turn)) {
      delay(Random.nextLong(2500, 12000))
      val cur = latest
      if (cur.ev != st.ev || cur.phase != "play") return@LaunchedEffect
      val pSuccess = (0.95 - 0.04 * cur.used.size).coerceIn(0.3, 0.95)
      if (System.currentTimeMillis() < cur.dl - 400 && Random.nextDouble() < pSuccess) {
        val free = BASTA_LETTERS.filter { it.toString() !in cur.used }
        val hard = "KQWXYZ"
        val pick = (if (cur.used.size < 14) free.filter { it !in hard }.ifEmpty { free } else free).random()
        val used2 = cur.used + pick.toString()
        val moved = cur.copy(used = used2, lastSeat = cur.turn)
        if (used2.size >= BASTA_LETTERS.size) push(endRound(moved, cur.turn, "¡Se acabaron las letras! Gana ${seatName(cur.turn)}"))
        else push(passTurn(moved, "🤖 ${seatName(cur.turn)} dijo algo con la $pick"))
        return@LaunchedEffect
      }
    }
    val cur = latest
    delay((cur.dl - System.currentTimeMillis()).coerceAtLeast(0L) + BASTA_GRACE_MS)
    val l = latest
    if (l.ev == st.ev && l.phase == "play") push(eliminate(l, l.turn))
    } catch (e: kotlinx.coroutines.CancellationException) {
      throw e
    } catch (e: Exception) {
      // Nunca dejar que un error del árbitro cierre la app: se registra y la partida sigue.
      android.util.Log.w("KomboArena", "Basta: error del árbitro", e)
    }
  }

  fun tapLetter(ch: Char) {
    val b = latest
    if (b.phase != "play" || mySeat < 0 || b.turn != mySeat || System.currentTimeMillis() > b.dl + BASTA_GRACE_MS) return
    if (ch.toString() in b.used) { msg = "La $ch ya está apagada"; c.vm.gameSfx(0); return }
    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    com.example.audio.GameSfx.play(ctx, "sfx_page_flip", 0.4f)
    msg = ""; wordWarn = ""; warnedKey = ""
    wordTfv = androidx.compose.ui.text.input.TextFieldValue(ch.toString(), androidx.compose.ui.text.TextRange(1))
    pendingLetter = ch
  }

  fun acceptWord() {
    val ch = pendingLetter ?: return
    val b = latest
    if (b.phase != "play" || b.turn != mySeat || System.currentTimeMillis() > b.dl + BASTA_GRACE_MS) { pendingLetter = null; msg = "⏰ Se acabó el tiempo"; return }
    val w = wordTfv.text.trim()
    if (w.length < 2) { wordWarn = "Escribí una palabra que empiece con la $ch"; return }
    if (firstLetterOf(w) != ch) { wordWarn = "«$w» tiene que empezar con la $ch"; return }
    val key = BastaDict.norm(w)
    if (key in b.words) { wordWarn = "«$w» ya se dijo en esta ronda. Probá con otra"; return }
    // Revisión ortográfica con los diccionarios: si no está, avisa una vez y deja corregir (o aceptar igual).
    if (BastaDict.check(b.cat, w) == BastaDict.UNKNOWN && warnedKey != key) {
      warnedKey = key
      wordWarn = "No encontré «$w» en los diccionarios. Revisá cómo se escribe; si estás seguro, tocá Aceptar otra vez."
      return
    }
    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    val used2 = b.used + ch.toString()
    val moved = b.copy(used = used2, pend = "", word = w, words = b.words + key, lastSeat = mySeat)
    pendingLetter = null; wordWarn = ""; msg = ""
    if (used2.size >= BASTA_LETTERS.size) push(endRound(moved, mySeat, "¡Se acabaron las letras! Gana ${seatName(mySeat)}"))
    else push(passTurn(moved, "✅ ${seatName(mySeat)}: «$w» con la $ch"))
  }

  fun tapCenter() {
    val b = latest
    if (mySeat < 0) return
    when (b.phase) {
      "idle", "matchEnd" -> push(startRound(b, true))
      "roundEnd" -> push(startRound(b, false))
      "play" -> {
        if (b.turn != mySeat || System.currentTimeMillis() > b.dl + BASTA_GRACE_MS) return
        msg = "Tocá una letra de la rueda para decir tu palabra"
      }
    }
  }

  val remaining = ((st.dl - now).coerceAtLeast(0L))
  LaunchedEffect(st.turn, st.phase) { if (st.turn != mySeat || st.phase != "play") pendingLetter = null }
  val myTurn = st.phase == "play" && st.turn == mySeat
  val turnColor = BASTA_SEAT_COLORS[st.turn.coerceIn(0, n - 1).coerceAtLeast(0) % BASTA_SEAT_COLORS.size]
  val bg = Brush.verticalGradient(listOf(Color(0xFF0A0420), Color(0xFF12082E), Color(0xFF05020F)))

  Column(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(bg).border(1.dp, Color(0x5500E5FF), RoundedCornerShape(24.dp)).padding(10.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Text(
      "¡BASTA!", fontSize = 34.sp, fontWeight = FontWeight.Black, color = Color(0xFFFFFFFF),
      style = TextStyle(shadow = Shadow(Color(0xFFFF2D55), Offset.Zero, 28f), fontSize = 34.sp, fontWeight = FontWeight.Black)
    )
    // Carta de categoría (neón)
    Box(
      Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF0E1236)).border(2.dp, Color(0xFF00E5FF), RoundedCornerShape(16.dp)).padding(vertical = 10.dp, horizontal = 12.dp),
      contentAlignment = Alignment.Center
    ) {
      Text(
        when (st.phase) { "idle" -> "Pulsá MANO para sacar la primera carta"; else -> "${st.cat}" },
        color = Color(0xFFE0FBFF), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center,
        style = TextStyle(shadow = Shadow(Color(0xFF00E5FF), Offset.Zero, 18f), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
      )
    }
    // Jugadores
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      for (i in 0 until n) {
        val col = BASTA_SEAT_COLORS[i % BASTA_SEAT_COLORS.size]
        val out = st.phase != "idle" && i !in st.alive
        val cur = st.phase == "play" && st.turn == i
        Column(
          Modifier.clip(RoundedCornerShape(12.dp)).background(if (cur) col.copy(alpha = 0.25f) else Color(0x22FFFFFF))
            .border(if (cur) 2.dp else 1.dp, if (out) Color(0x33FFFFFF) else col, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 5.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Text((if (cur) "▶ " else "") + seatName(i), color = if (out) Color(0x66FFFFFF) else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
          Text(if (out) "💀 fuera" else "⭐".repeat(st.wins.getOrElse(i) { 0 }).ifEmpty { "·" }, color = col, fontSize = 11.sp)
        }
      }
    }

    BastaBoard(st, remaining, now, turnColor, myTurn, mySeat, ::tapLetter, ::tapCenter, if (st.phase == "idle") "MANO" else if (st.phase == "roundEnd") "OTRA\nRONDA" else if (st.phase == "matchEnd") "REVAN-\nCHA" else "", seatName(st.turn))

    // Estado
    val status = when {
      st.phase == "idle" -> "Tocá MANO en el centro de la rueda para empezar."
      st.phase == "matchEnd" -> "🏆 ¡${seatName(st.winner)} gana la partida con $BASTA_WIN_ROUNDS rondas!"
      st.phase == "roundEnd" -> "🎉 Ronda para ${seatName(st.winner)}. ${st.last}"
      myTurn -> "¡Es tu turno! Tenés ${(BASTA_TURN_MS / 1000)} segundos"
      else -> "Turno de ${seatName(st.turn)}"
    }
    Text(status, color = if (st.phase == "matchEnd") Color(0xFFFFEA00) else Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    if (st.phase == "idle" || st.phase == "matchEnd") BastaHowTo()
    if (myTurn) BastaSteps(pendingLetter != null)
    if (st.phase == "play") Text("${st.last}  ·  quedan ${BASTA_LETTERS.size - st.used.size} letras", color = Color(0xFF9FB4FF), fontSize = 12.sp, textAlign = TextAlign.Center)
    if (msg.isNotEmpty()) Text(msg, color = Color(0xFFFF8A80), fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    val pl = pendingLetter
    if (pl != null && myTurn) {
      val focus = remember { androidx.compose.ui.focus.FocusRequester() }
      LaunchedEffect(pl) { delay(150); runCatching { focus.requestFocus() } }
      val secsLeft = ((remaining + 999) / 1000).toInt()
      androidx.compose.ui.window.Dialog(
        onDismissRequest = { },
        properties = androidx.compose.ui.window.DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false, usePlatformDefaultWidth = false)
      ) {
        Column(
          Modifier.fillMaxWidth(0.92f).clip(RoundedCornerShape(24.dp)).background(Brush.verticalGradient(listOf(Color(0xFF1A0F3C), Color(0xFF0A0420))))
            .border(2.dp, turnColor, RoundedCornerShape(24.dp)).padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Text(st.cat, color = Color(0xFFE0FBFF), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(58.dp).clip(CircleShape).background(Brush.radialGradient(listOf(Color(0xFFFF5252), Color(0xFF8E0010)))).border(2.dp, Color.White, CircleShape), contentAlignment = Alignment.Center) {
              Text(pl.toString(), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
            }
            Text("⏱ ${secsLeft}s", color = if (secsLeft <= 5) Color(0xFFFF5252) else Color(0xFFFFEA00), fontSize = 24.sp, fontWeight = FontWeight.Black)
          }
          androidx.compose.material3.OutlinedTextField(
            value = wordTfv, onValueChange = { wordTfv = it.copy(text = it.text.take(24)); wordWarn = "" },
            singleLine = true, modifier = Modifier.fillMaxWidth().focusRequester(focus),
            label = { Text("Tu palabra con la $pl") },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done, capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { acceptWord() })
          )
          if (wordWarn.isNotEmpty()) Text(wordWarn, color = Color(0xFFFFB74D), fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
          Box(
            Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp)).background(Brush.horizontalGradient(listOf(Color(0xFF39FF14), Color(0xFF00E5FF)))).clickable { acceptWord() },
            contentAlignment = Alignment.Center
          ) { Text("Aceptar", color = Color(0xFF04210A), fontSize = 18.sp, fontWeight = FontWeight.Black) }
        }
      }
    }
    if (st.phase == "play" && mySeat >= 0 && !myTurn && mySeat !in st.alive) Text("Quedaste fuera de esta ronda: esperá la próxima carta.", color = Color(0xFFB0B0C8), fontSize = 12.sp)
  }
}

@Composable
private fun BastaBoard(
  st: BSt, remaining: Long, now: Long, turnColor: Color, myTurn: Boolean, mySeat: Int,
  onKey: (Char) -> Unit, onCenter: () -> Unit, centerLabel: String, turnName: String
) {
  val tm = rememberTextMeasurer()
  val keyCb by rememberUpdatedState(onKey)
  val centerCb by rememberUpdatedState(onCenter)
  val pulse by rememberInfiniteTransition(label = "bastaPulse").animateFloat(
    0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse), label = "p"
  )
  val wave = remember { Animatable(26f) }
  val flash = remember { Animatable(0f) }
  val sfxCtx = androidx.compose.ui.platform.LocalContext.current.applicationContext
  LaunchedEffect(st.round, st.phase) {
    if (st.phase == "play" && st.used.isEmpty()) {
      // las luces giran por las letras: suena un cambio de página suave, como pasar hojas
      launch { for (k in 0..2) { com.example.audio.GameSfx.play(sfxCtx, "sfx_page_flip", 0.35f); delay(370) } }
      wave.snapTo(0f); wave.animateTo(26f, tween(1100, easing = LinearEasing))
    }
  }
  LaunchedEffect(st.used.size) { if (st.used.isNotEmpty()) { com.example.audio.GameSfx.play(sfxCtx, "sfx_page_flip", 0.3f); flash.snapTo(1f); flash.animateTo(0f, tween(750)) } }
  val lastLetter = st.used.lastOrNull()

  Canvas(
    Modifier.fillMaxWidth().aspectRatio(1f).pointerInput(Unit) {
      detectTapGestures { off ->
        val cx = size.width / 2f; val cy = size.height / 2f; val r = size.width / 2f
        val dx = off.x - cx; val dy = off.y - cy; val dist = hypot(dx, dy)
        if (dist < 0.34f * r) centerCb()
        else if (dist > 0.68f * r) {
          var deg = Math.toDegrees(atan2(dy, dx).toDouble()) + 90.0
          if (deg < 0) deg += 360.0
          val idx = ((deg / (360.0 / 26.0)).roundToInt()) % 26
          keyCb(BASTA_LETTERS[idx])
        }
      }
    }
  ) {
    val r = size.width / 2f
    val c0 = Offset(r, r)
    val playing = st.phase == "play"

    // Base roja con aro neón
    for (k in 5 downTo 1) drawCircle(Color(0xFFFF1744).copy(alpha = 0.05f * (6 - k)), radius = r * 0.82f + k * r * 0.018f, center = c0)
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFF5252), Color(0xFFE5101F), Color(0xFF8E0010)), c0, r * 0.82f), radius = r * 0.82f, center = c0)
    drawCircle(Color(0xFFFFB3B3).copy(alpha = 0.55f), radius = r * 0.82f, center = c0, style = Stroke(r * 0.008f))
    // Anillo azul
    for (k in 4 downTo 1) drawCircle(Color(0xFF2979FF).copy(alpha = 0.07f * (5 - k)), radius = r * 0.5f, center = c0, style = Stroke(r * 0.17f + k * r * 0.03f))
    drawCircle(Color(0xFF1E6BFF), radius = r * 0.5f, center = c0, style = Stroke(r * 0.17f))
    drawCircle(Color(0xFF9CC4FF).copy(alpha = 0.6f), radius = r * 0.585f, center = c0, style = Stroke(r * 0.006f))
    // Aro rojo interior
    drawCircle(Color(0xFFFF1F2E), radius = r * 0.355f, center = c0, style = Stroke(r * 0.05f))
    // Seis/ocho teclas decorativas de la rueda
    val studs = listOf(45f to true, 135f to true, 225f to true, 315f to true, 0f to false, 90f to false, 180f to false, 247f to false, 293f to false)
    studs.forEach { (deg, red) ->
      val a = Math.toRadians(deg.toDouble()).toFloat()
      val ctr = c0 + Offset(cos(a), sin(a)) * (r * 0.5f)
      val w = if (red) r * 0.25f else r * 0.095f
      val h = if (red) r * 0.13f else r * 0.2f
      rotate(deg + 90f + (if (red) 90f else 0f), ctr) {
        val col = if (red) Color(0xFFFF2B2B) else Color(0xFF1F7BFF)
        for (k in 3 downTo 1) drawRoundRect(col.copy(alpha = 0.10f * (4 - k)), ctr - Offset(w / 2 + k * 3f, h / 2 + k * 3f), Size(w + k * 6f, h + k * 6f), CornerRadius(r * 0.03f))
        drawRoundRect(col, ctr - Offset(w / 2, h / 2), Size(w, h), CornerRadius(r * 0.03f))
        drawRoundRect(Color.White.copy(alpha = 0.35f), ctr - Offset(w / 2, h / 2), Size(w, h * 0.35f), CornerRadius(r * 0.03f))
      }
    }

    // Teclas A–Z en círculo
    val rk = r * 0.885f
    val keyS = r * 0.172f
    BASTA_LETTERS.forEachIndexed { i, ch ->
      val ang = Math.toRadians(-90.0 + i * 360.0 / 26.0).toFloat()
      val kc = c0 + Offset(cos(ang), sin(ang)) * rk
      val used = ch.toString() in st.used
      val chase = !playing && ((now / 90L) % 26L).toInt() == i
      val lit = when {
        used -> false
        playing -> i <= wave.value
        else -> true
      }
      val isFlash = used && ch.toString() == lastLetter && flash.value > 0f
      val glow = when {
        isFlash -> Color(0xFFFF2BD6)
        chase -> Color(0xFFFFEA00)
        lit && playing -> Color(0xFF00E5FF)
        lit -> Color(0xFF00E5FF).copy(alpha = 0.55f)
        else -> Color.Transparent
      }
      rotate(i * 360f / 26f, kc) {
        val tl = kc - Offset(keyS / 2, keyS / 2)
        if (glow != Color.Transparent) {
          val ga = if (isFlash) flash.value else 1f
          for (k in 4 downTo 1) drawRoundRect(glow.copy(alpha = 0.10f * (5 - k) * ga), tl - Offset(k * 3.2f, k * 3.2f), Size(keyS + k * 6.4f, keyS + k * 6.4f), CornerRadius(keyS * 0.22f))
        }
        val body = when { isFlash -> lerp(Color(0xFF1A1B2E), Color(0xFFFFB3F0), flash.value); lit || chase -> Color(0xFFF7F9FF); else -> Color(0xFF1A1B2E) }
        drawRoundRect(body, tl, Size(keyS, keyS), CornerRadius(keyS * 0.22f))
        drawRoundRect(if (lit || chase) Color(0xFFBFD0FF) else Color(0xFF34365A), tl, Size(keyS, keyS), CornerRadius(keyS * 0.22f), style = Stroke(keyS * 0.035f))
        val t = tm.measure(ch.toString(), TextStyle(fontSize = (keyS * 0.62f).toSp(), fontWeight = FontWeight.Black, color = if (lit || chase) Color(0xFF101018) else Color(0x44FFFFFF)))
        drawText(t, topLeft = kc - Offset(t.size.width / 2f, t.size.height / 2f))
      }
    }

    // Botón central (disco blanco) con anillo de tiempo
    val rc = r * 0.295f
    if (playing) {
      val frac = (remaining / BASTA_TURN_MS.toFloat()).coerceIn(0f, 1f)
      val arcCol = lerp(Color(0xFFFF1744), turnColor, frac.coerceIn(0f, 1f).let { if (it < 0.3f) it / 0.3f else 1f })
      val boost = if (myTurn) 0.6f + 0.4f * pulse else 0.5f
      for (k in 5 downTo 1) drawCircle(arcCol.copy(alpha = 0.06f * (6 - k) * boost), radius = rc + k * r * 0.014f, center = c0)
      drawArc(arcCol, -90f, 360f * frac, false, c0 - Offset(rc + r * 0.03f, rc + r * 0.03f), Size((rc + r * 0.03f) * 2, (rc + r * 0.03f) * 2), style = Stroke(r * 0.03f, cap = StrokeCap.Round))
    } else {
      for (k in 5 downTo 1) drawCircle(Color(0xFFFFFFFF).copy(alpha = 0.05f * (6 - k) * (0.5f + 0.5f * pulse)), radius = rc + k * r * 0.014f, center = c0)
    }
    drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFFE6E8F5)), c0, rc), radius = rc, center = c0)
    drawCircle(Color(0xFFB8BDE0), radius = rc, center = c0, style = Stroke(r * 0.008f))
    if (playing) {
      val secs = ((remaining + 999) / 1000).toInt().coerceIn(0, (BASTA_TURN_MS / 1000).toInt())
      val big = tm.measure(secs.toString(), TextStyle(fontSize = (r * 0.2f).toSp(), fontWeight = FontWeight.Black, color = if (secs <= 5) Color(0xFFE5101F) else Color(0xFF14143A)))
      drawText(big, topLeft = c0 - Offset(big.size.width / 2f, big.size.height * 0.62f))
      val small = tm.measure(if (myTurn) "¡TU TURNO!" else turnName, TextStyle(fontSize = (r * 0.052f).toSp(), fontWeight = FontWeight.Bold, color = Color(0xFF3A3A60), textAlign = TextAlign.Center))
      drawText(small, topLeft = c0 + Offset(-small.size.width / 2f, big.size.height * 0.30f))
    } else {
      val lab = tm.measure(centerLabel, TextStyle(fontSize = (r * 0.085f).toSp(), fontWeight = FontWeight.Black, color = Color(0xFFE5101F), textAlign = TextAlign.Center))
      drawText(lab, topLeft = c0 - Offset(lab.size.width / 2f, lab.size.height / 2f))
    }
  }
}

/** Explicación corta y clara de cómo se juega (se ve antes de empezar y al terminar la partida). */
@Composable
private fun BastaHowTo() {
  Column(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0x22FFFFFF)).padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(5.dp)
  ) {
    Text("❓ Cómo se juega", color = Color(0xFFFFEA00), fontWeight = FontWeight.Black, fontSize = 14.sp)
    Text("1️⃣ Sale una carta con una categoría (por ejemplo «Frutas»).", color = Color.White, fontSize = 12.sp)
    Text("2️⃣ En tu turno tocá en la rueda una LETRA: aparece una ventana al frente.", color = Color.White, fontSize = 12.sp)
    Text("3️⃣ Escribí una palabra de esa categoría que empiece con esa letra y tocá Aceptar. Se revisa en los diccionarios y el turno pasa al siguiente.", color = Color.White, fontSize = 12.sp)
    Text("🚫 Cada letra se puede usar una sola vez: al tocarla se apaga.", color = Color.White, fontSize = 12.sp)
    Text("⏰ Tenés ${BASTA_TURN_MS / 1000} segundos por turno. Si no llegás, quedás fuera de la ronda.", color = Color.White, fontSize = 12.sp)
    Text("🏆 Gana la ronda el último que queda en pie. Gana la partida quien consiga $BASTA_WIN_ROUNDS rondas (⭐).", color = Color.White, fontSize = 12.sp)
  }
}

/** Los dos pasos del turno, con tilde a medida que se cumplen. */
@Composable
private fun BastaSteps(letterPicked: Boolean) {
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    @Composable
    fun chip(text: String, done: Boolean, modifier: Modifier) {
      Box(
        modifier.clip(RoundedCornerShape(10.dp)).background(if (done) Color(0x3339FF14) else Color(0x22FFFFFF))
          .border(1.dp, if (done) Color(0xFF39FF14) else Color(0x44FFFFFF), RoundedCornerShape(10.dp)).padding(horizontal = 6.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
      ) { Text((if (done) "✅ " else "") + text, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) }
    }
    chip("1 Tocá una letra de la rueda", letterPicked, Modifier.weight(1f))
    chip("2 Escribí tu palabra y Aceptar", false, Modifier.weight(1f))
  }
}
