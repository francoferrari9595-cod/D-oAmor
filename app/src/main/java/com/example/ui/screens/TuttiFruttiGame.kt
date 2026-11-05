package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.GlassCard
import com.example.ui.components.GradientButton
import com.example.ui.components.OptionChip
import com.example.ui.components.SoftButton
import com.example.ui.theme.GoldYellow
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.relocation.bringIntoViewRequester
import java.text.Normalizer

private val TF_CATEGORIES = listOf(
  "Nombre", "Animal", "País o provincia", "Ciudad", "Comida", "Fruta o verdura",
  "Objeto", "Profesión", "Marca", "Famoso/a", "Película o serie", "Canción o artista", "Cosa de internet"
)
private val TF_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".toList()
// En modo Diccionario se evitan las letras con muy pocas palabras en el vocabulario integrado.
private const val TF_HARD = "KQUWXYZ"

private class TfMode(val name: String, val emoji: String, val desc: String, val seconds: Int, val cats: Int, val strict: Boolean, val uniqueOnly: Boolean)
private val TF_MODES = listOf(
  TfMode("Clásico", "📝", "90 s · 8 categorías · tú escribes, el grupo confía.", 90, 8, false, false),
  TfMode("Relámpago", "⚡", "40 s · 5 categorías · ¡a toda velocidad!", 40, 5, false, false),
  TfMode("Diccionario", "📖", "120 s · solo cuentan palabras del diccionario integrado.", 120, 8, true, false),
  TfMode("Experto", "🧠", "90 s · 10 categorías · las respuestas repetidas valen 0.", 90, 10, false, true),
  TfMode("Sin tiempo", "♾️", "Sin cronómetro: termina cuando alguien completa todo y grita BASTA.", 0, 8, false, false)
)
private val TF_POINT_GOALS = listOf(100, 200, 300, 500, 750, 1000)
private val TF_ROUND_GOALS = listOf(3, 5, 7, 10, 15, 20)

private fun tfNorm(s: String): String {
  val protected = s.trim().lowercase().replace("ñ", "__enye__")
  return Normalizer.normalize(protected, Normalizer.Form.NFD)
    .replace("\\p{Mn}+".toRegex(), "").replace("__enye__", "ñ")
    .replace("[^a-zñ ]".toRegex(), "").replace("\\s+".toRegex(), " ").trim()
}

internal class TfCell(val answer: String, val valid: Boolean, val points: Int)

/**
 * Puntúa una ronda. Respuesta válida única: 10; única y nadie más escribió algo válido: 20;
 * repetida: 5 (0 en modo experto); inválida o vacía: 0. Devuelve las celdas por jugador y categoría.
 */
internal fun tfScoreRound(
  players: List<String>, cats: List<String>, letter: String, answers: (String, Int) -> String,
  strict: Boolean, uniqueOnly: Boolean,
  // Veredicto de los demás jugadores para (autor, categoría): 1 = aprobada, -1 = rechazada, null = sin decisión.
  verdict: (String, Int) -> Int? = { _, _ -> null }
): Map<String, List<TfCell>> {
  val normalized = players.associateWith { uid -> cats.indices.map { idx -> tfNorm(answers(uid, idx)) } }
  /** Condiciones mínimas: letra correcta, dos letras o más, con vocales y no repetida. Ningún voto las salta. */
  fun basicOk(w: String): Boolean {
    if (w.length < 2 || w.firstOrNull()?.uppercaseChar()?.toString() != letter) return false
    if (w.all { it == w[0] }) return false
    return w.any { it in "aeiou" }
  }
  fun okWord(uid: String, idx: Int, w: String): Boolean {
    if (!basicOk(w)) return false
    return when (verdict(uid, idx)) {
      1 -> true
      -1 -> false
      // Sin decisión: en modos estrictos hace falta que esté en el diccionario; en los demás, se confía.
      else -> if (strict) TuttiFruttiLexicon.isValid(cats[idx], w) else true
    }
  }
  val valid = players.associateWith { uid -> cats.indices.map { idx -> okWord(uid, idx, normalized[uid]!![idx]) } }
  val out = players.associateWith { mutableListOf<TfCell>() }
  for (idx in cats.indices) {
    val owners = players.filter { valid[it]!![idx] }.groupBy { normalized[it]!![idx] }
    val validCount = players.count { valid[it]!![idx] }
    for (uid in players) {
      val w = normalized[uid]!![idx]
      val ok = valid[uid]!![idx]
      val pts = when {
        !ok -> 0
        (owners[w]?.size ?: 0) > 1 -> if (uniqueOnly) 0 else 5
        validCount == 1 && players.size > 1 -> 20
        else -> 10
      }
      out[uid]!!.add(TfCell(w, ok, pts))
    }
  }
  return out
}

/** Aparece deslizándose y con fundido, con un retraso escalonado según su posición. */
@Composable
private fun TfAppear(index: Int, content: @Composable () -> Unit) {
  val a = remember { Animatable(0f) }
  LaunchedEffect(Unit) { delay(index * 70L); a.animateTo(1f, tween(380, easing = FastOutSlowInEasing)) }
  Box(Modifier.graphicsLayer { alpha = a.value; translationY = (1f - a.value) * 36f }) { content() }
}

/** Tema visual de cada categoría: emoji y par de colores (como el color de cada propiedad del Monopoly). */
private class TfTheme(val emoji: String, val c1: Color, val c2: Color)
private fun tfTheme(category: String): TfTheme = when (tfNorm(category)) {
  "nombre" -> TfTheme("👤", Color(0xFF3B82F6), Color(0xFF1D4ED8))
  "animal" -> TfTheme("🐾", Color(0xFF22C55E), Color(0xFF15803D))
  "pais o provincia" -> TfTheme("🌎", Color(0xFF14B8A6), Color(0xFF0F766E))
  "ciudad" -> TfTheme("🏙️", Color(0xFF8B5CF6), Color(0xFF6D28D9))
  "comida" -> TfTheme("🍕", Color(0xFFF59E0B), Color(0xFFD97706))
  "fruta o verdura" -> TfTheme("🍎", Color(0xFFEF4444), Color(0xFFB91C1C))
  "objeto" -> TfTheme("🧩", Color(0xFF64748B), Color(0xFF334155))
  "profesion" -> TfTheme("💼", Color(0xFFA16207), Color(0xFF78350F))
  "marca" -> TfTheme("🏷️", Color(0xFFEC4899), Color(0xFFBE185D))
  "famoso a", "famoso/a" -> TfTheme("⭐", Color(0xFFEAB308), Color(0xFFA16207))
  "pelicula o serie" -> TfTheme("🎬", Color(0xFFD946EF), Color(0xFFA21CAF))
  "cancion o artista" -> TfTheme("🎤", Color(0xFF6366F1), Color(0xFF4338CA))
  "cosa de internet" -> TfTheme("🌐", Color(0xFF06B6D4), Color(0xFF0E7490))
  else -> TfTheme("📝", Color(0xFF94A3B8), Color(0xFF475569))
}

/** Tarjeta tipo escritura de propiedad: franja de color con emoji y nombre arriba, cuerpo claro abajo. */
@Composable
private fun TfDeedCard(index: Int, total: Int, category: String, highlight: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
  val th = tfTheme(category)
  val shape = RoundedCornerShape(16.dp)
  Column(
    Modifier.fillMaxWidth().shadow(if (highlight) 12.dp else 4.dp, shape).clip(shape).background(Color(0xFFFFF8E6))
      .border(if (highlight) 2.5.dp else 1.dp, if (highlight) th.c1 else Color(0x66000000), shape)
  ) {
    Row(
      Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(th.c1, th.c2))).padding(horizontal = 12.dp, vertical = 7.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(th.emoji, fontSize = 20.sp)
      Spacer(Modifier.width(8.dp))
      Text(category.uppercase(), color = Color.White, fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 1.sp, modifier = Modifier.weight(1f), maxLines = 1)
      Text("${index + 1}/$total", color = Color(0xCCFFFFFF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
    Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), content = content)
  }
}

/** Campo de respuesta grande y claro dentro de la tarjeta, con estado en vivo (✏️ / ⚠️ / ✅ / 📖). */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun TfAnswerRow(
  index: Int, total: Int, category: String, letter: String, value: String, enabled: Boolean, isLast: Boolean, onChange: (String) -> Unit
) {
  val th = tfTheme(category)
  val t = tfNorm(value)
  val startsOk = t.isNotEmpty() && t.first().uppercaseChar().toString() == letter
  var focused by remember { mutableStateOf(false) }
  val bring = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
  val scope = rememberCoroutineScope()
  val status = when {
    t.isEmpty() -> ""
    !startsOk -> "⚠️ debe empezar con $letter"
    t.length < 2 -> "✏️ seguí escribiendo…"
    TuttiFruttiLexicon.isValid(category, t) -> "📖 ¡en el diccionario!"
    else -> "✅ lista"
  }
  val statusColor = when {
    !startsOk && t.isNotEmpty() -> Color(0xFFC62828)
    status.startsWith("📖") -> Color(0xFF1B7F46)
    status.startsWith("✅") -> Color(0xFF2E7D32)
    else -> Color(0xFF8A7A6A)
  }
  TfDeedCard(index, total, category, highlight = focused) {
    androidx.compose.foundation.text.BasicTextField(
      value = value, onValueChange = onChange, enabled = enabled, singleLine = true,
      textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFF2B1C00), fontSize = 22.sp, fontWeight = FontWeight.Bold),
      cursorBrush = androidx.compose.ui.graphics.SolidColor(th.c1),
      keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
        capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Words,
        imeAction = if (isLast) androidx.compose.ui.text.input.ImeAction.Done else androidx.compose.ui.text.input.ImeAction.Next
      ),
      modifier = Modifier.fillMaxWidth()
        .bringIntoViewRequester(bring)
        .onFocusChanged { focused = it.isFocused; if (it.isFocused) scope.launch { delay(280); runCatching { bring.bringIntoView() } } },
      decorationBox = { inner ->
        Box(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
          if (value.isEmpty()) Text("Empieza con $letter…", color = Color(0x668A7A6A), fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
          inner()
        }
      }
    )
    Box(Modifier.fillMaxWidth().height(2.dp).background(if (focused) th.c1 else Color(0x33000000)))
    Text(if (status.isEmpty()) " " else status, color = statusColor, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
  }
}

/** Reloj circular con los segundos que quedan. */
@Composable
private fun TfTimerRing(frac: Float, label: String, danger: Boolean) {
  Box(Modifier.size(74.dp), contentAlignment = Alignment.Center) {
    Canvas(Modifier.fillMaxSize()) {
      val sw = 7.dp.toPx()
      drawArc(Color(0x33FFFFFF), 0f, 360f, false, topLeft = Offset(sw / 2, sw / 2), size = Size(size.width - sw, size.height - sw), style = Stroke(sw))
      drawArc(if (danger) Color(0xFFFF5252) else Color(0xFFFFC857), -90f, 360f * frac.coerceIn(0f, 1f), false, topLeft = Offset(sw / 2, sw / 2), size = Size(size.width - sw, size.height - sw), style = Stroke(sw, cap = StrokeCap.Round))
    }
    Text(label, color = if (danger) Color(0xFFFF8A80) else Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
  }
}

/** Botón redondo de voto (👍 / 👎) con rebote al tocarlo. */
@Composable
private fun TfVoteButton(emoji: String, selected: Boolean, tint: Color, enabled: Boolean, onClick: () -> Unit) {
  val sc by animateFloatAsState(if (selected) 1.18f else 1f, spring(dampingRatio = 0.4f, stiffness = 400f), label = "tfVote")
  Box(
    Modifier.size(38.dp).graphicsLayer { scaleX = sc; scaleY = sc }.clip(CircleShape)
      .background(if (selected) tint.copy(alpha = 0.9f) else Color(0x14000000))
      .border(1.5.dp, if (selected) Color.White else Color(0x33000000), CircleShape)
      .clickable(enabled = enabled) { onClick() },
    contentAlignment = Alignment.Center
  ) { Text(emoji, fontSize = 17.sp) }
}

@Composable
private fun TfBigLetter(letter: String, spinning: Boolean) {
  val pop = remember { Animatable(1f) }
  LaunchedEffect(letter, spinning) {
    if (!spinning) { pop.snapTo(0.4f); pop.animateTo(1f, spring(0.4f, 300f)) }
  }
  val t = rememberInfiniteTransition(label = "tfGlow")
  val glow by t.animateFloat(0.55f, 1f, infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "tfGlowV")
  Box(
    Modifier.size(132.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value }
      .clip(CircleShape)
      .background(Brush.radialGradient(listOf(GoldYellow.copy(alpha = 0.55f * glow), Color(0x331C1030))))
      .border(BorderStroke(3.dp, Brush.linearGradient(listOf(GoldYellow, Color(0x55FFFFFF), GoldYellow))), CircleShape),
    contentAlignment = Alignment.Center
  ) { Text(letter, color = Color.White, fontSize = 72.sp, fontWeight = FontWeight.Black) }
}

@Composable
internal fun TuttiFruttiGame(c: GameCtx) {
  val flow = remember { c.vm.gameState("tuttiFrutti") }
  val appCtx = LocalContext.current
  remember { TuttiFruttiLexicon.loadExtra(appCtx) }
  val st by flow.collectAsState(initial = null)
  val avatarMap by c.vm.avatars.collectAsState()
  val myPhoto by c.vm.myAvatar.collectAsState()
  fun photoOf(uid: String): String? = if (uid == c.me) (myPhoto ?: avatarMap[uid]) else avatarMap[uid]
  val phase = st?.get("phase") as? String ?: "lobby"
  val players = (st?.get("players") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
  val round = (st?.get("round") as? Number)?.toInt() ?: 0
  val letter = st?.get("letter") as? String ?: "?"
  val endAt = (st?.get("endAt") as? Number)?.toLong() ?: 0L
  val spinStart = (st?.get("spinStart") as? Number)?.toLong() ?: 0L
  val modeIdxState = (st?.get("mode") as? Number)?.toInt() ?: 0
  val mode = TF_MODES[modeIdxState.coerceIn(0, TF_MODES.size - 1)]
  val goalType = (st?.get("goalType") as? Number)?.toInt() ?: 0   // 0 = puntos, 1 = rondas
  val goal = (st?.get("goal") as? Number)?.toInt() ?: 100
  val durationSec = (st?.get("duration") as? Number)?.toInt() ?: mode.seconds
  val hostUid = st?.get("host") as? String ?: c.hostUid
  val isHost = c.me == hostUid
  val myIdx = players.indexOf(c.me)
  val cats = (st?.get("categories") as? List<*>)?.mapNotNull { it as? String } ?: TF_CATEGORIES.take(8)
  val scores = players.associateWith { (st?.get("score_${it}") as? Number)?.toInt() ?: 0 }
  val goalReached = (st?.get("goalReached") as? Number)?.toInt() == 1

  var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
  LaunchedEffect(phase) {
    while (phase == "spin" || phase == "playing") { nowMs = System.currentTimeMillis(); delay(if (phase == "spin") 45L else 250L) }
  }
  val seconds = if (endAt > 0L) ((endAt - nowMs).coerceAtLeast(0L) / 1000L).toInt() else 0
  val spinIdx = if (spinStart > 0L) (((nowMs - spinStart).coerceAtLeast(0L) / 85L) % 26L).toInt() else 0
  var duration by remember { mutableStateOf(90) }
  var categoriesCount by remember { mutableStateOf(8) }
  var modeSel by remember { mutableStateOf(0) }
  var goalTypeSel by remember { mutableStateOf(0) }
  var goalIdx by remember { mutableStateOf(0) }
  var localAnswers by remember(round, letter) { mutableStateOf(List(13) { "" }) }

  // Fin del tiempo: solo el anfitrión pasa a la revisión.
  LaunchedEffect(phase, endAt) {
    if (phase == "playing" && endAt > 0L) {
      while (System.currentTimeMillis() < endAt) delay(250)
      if (c.me == hostUid) c.vm.patchGame("tuttiFrutti", mapOf("phase" to "review", "stoppedBy" to "timer"))
    }
  }
  // ---- Revisión: cada jugador aprueba o rechaza las respuestas de los demás ----
  val scoredRound = (st?.get("scoredRound") as? Number)?.toInt() ?: 0
  fun voteOf(judge: String, author: String, idx: Int): Int = (st?.get("vote_${round}_${judge}_${author}_$idx") as? Number)?.toInt() ?: 0
  /** Mayoría de los demás jugadores: 1 aprobada, -1 rechazada, null si no hay decisión (empate o sin votos). */
  fun verdictOf(author: String, idx: Int): Int? {
    var up = 0; var down = 0
    players.forEach { judge -> if (judge != author) when (voteOf(judge, author, idx)) { 1 -> up++; -1 -> down++; else -> {} } }
    return if (up > down) 1 else if (down > up) -1 else null
  }
  fun isReady(uid: String): Boolean = ((st?.get("ready_${round}_$uid") as? Number)?.toInt() ?: 0) == 1
  val allReady = players.isNotEmpty() && players.all { isReady(it) }

  /** Cuenta la ronda con el diccionario y los votos. La ejecuta solo el anfitrión, una vez por ronda. */
  fun scoreRound() {
    if (scoredRound == round) return
    val result = tfScoreRound(players, cats, letter, { uid, idx -> st?.get("ans_${uid}_$idx") as? String ?: "" }, mode.strict, mode.uniqueOnly, ::verdictOf)
    val stopper = st?.get("stoppedBy") as? String ?: ""
    val updates = mutableMapOf<String, Any?>("scoredRound" to round)
    var best = 0
    players.forEach { uid ->
      val cells = result[uid].orEmpty()
      var gain = cells.sumOf { it.points }
      // Bonus por gritar BASTA con todo completo y válido.
      if (uid == stopper && cells.isNotEmpty() && cells.all { it.valid }) gain += 10
      val total = (scores[uid] ?: 0) + gain
      updates["score_$uid"] = total
      updates["gain_$uid"] = gain
      best = maxOf(best, total)
    }
    updates["goalReached"] = if ((goalType == 0 && best >= goal) || (goalType == 1 && round >= goal)) 1 else 0
    c.vm.patchGame("tuttiFrutti", updates)
  }
  // Cuando todos confirmaron su revisión, el anfitrión cuenta los puntos.
  LaunchedEffect(round, phase, allReady, scoredRound) {
    if (phase == "review" && isHost && scoredRound != round && allReady) scoreRound()
  }

  fun configKeys() = listOf("host", "players", "mode", "goalType", "goal", "duration", "categories")
  fun startSpin(roundNo: Int, keep: Map<String, Any?>) {
    val chosen = TF_CATEGORIES.shuffled().take(((st?.get("categories") as? List<*>)?.size ?: categoriesCount).coerceIn(5, 13))
    val fields = mutableMapOf<String, Any?>(
      "phase" to "spin", "round" to roundNo, "letter" to "?", "categories" to chosen, "spinStart" to System.currentTimeMillis(),
      "endAt" to 0L, "stoppedBy" to "", "scoredRound" to 0, "goalReached" to 0
    )
    c.vm.publishGame("tuttiFrutti", keep + fields)
  }
  fun stopSpin() {
    var l = TF_ALPHABET[spinIdx]
    if (mode.strict) { var guard = 0; while (TF_HARD.contains(l) && guard < 26) { l = TF_ALPHABET[(TF_ALPHABET.indexOf(l) + 1) % 26]; guard++ } }
    c.vm.patchGame("tuttiFrutti", mapOf(
      "phase" to "playing", "letter" to l.toString(), "spinBy" to c.me,
      "endAt" to (if (durationSec > 0) System.currentTimeMillis() + durationSec * 1000L else 0L)
    ))
  }

  // ---------------- Sala ----------------
  if (phase == "lobby" || players.isEmpty()) {
    GlassCard(modifier = Modifier.fillMaxWidth(), highlight = true) {
      Text("📝 TUTTI FRUTTI", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
      Text("La ruleta recorre la A a la Z sin parar: alguien pulsa STOP y esa letra manda la ronda. Llena las categorías antes de que termine el tiempo o grita BASTA.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
      Spacer(Modifier.height(4.dp))
      Text("Puntos: única 10 · única y nadie más acertó 20 · repetida 5 · inválida 0 · BASTA con todo válido +10. En la revisión cada uno aprueba 👍 o rechaza 👎 las respuestas de los demás (manda la mayoría) y el diccionario 📖 ayuda a decidir.", color = GoldYellow, fontSize = 11.sp)
    }
    Spacer(Modifier.height(10.dp))
    Text("Modo de juego", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      TF_MODES.forEachIndexed { i, m ->
        val label = "${m.emoji} ${m.name}"
        val pick = { modeSel = i; duration = m.seconds; categoriesCount = m.cats }
        OptionChip(label, selected = (modeSel == i), onClick = pick)
      }
    }
    Text(TF_MODES[modeSel].desc, color = Color.White, fontSize = 12.sp)
    Spacer(Modifier.height(8.dp))
    Text("Meta para ganar", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      OptionChip("🎯 Por puntos", selected = (goalTypeSel == 0), onClick = { goalTypeSel = 0; goalIdx = 0 })
      OptionChip("🔁 Por rondas", selected = (goalTypeSel == 1), onClick = { goalTypeSel = 1; goalIdx = 0 })
    }
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      (if (goalTypeSel == 0) TF_POINT_GOALS else TF_ROUND_GOALS).forEachIndexed { i, v ->
        val txt = if (goalTypeSel == 0) "$v pts" else "$v rondas"
        OptionChip(txt, selected = (goalIdx == i), onClick = { goalIdx = i })
      }
    }
    if (TF_MODES[modeSel].seconds > 0) {
      Spacer(Modifier.height(8.dp))
      Text("Tiempo por ronda", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(30, 45, 60, 90, 120, 180).forEach { v -> OptionChip("${v}s", selected = (duration == v), onClick = { duration = v }) } }
    }
    Spacer(Modifier.height(8.dp))
    Text("Categorías por ronda", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(5, 6, 8, 10, 12, 13).forEach { v -> OptionChip("$v", selected = (categoriesCount == v), onClick = { categoriesCount = v }) } }
    Spacer(Modifier.height(10.dp))
    GradientButton("🎰 Crear sala y girar la ruleta", modifier = Modifier.fillMaxWidth(), onClick = {
      val humanSeats = c.players.filter { it.isNotBlank() }.distinct().take(6)
      if (humanSeats.size >= 2) {
        val m = TF_MODES[modeSel]
        val keep = mutableMapOf<String, Any?>(
          "host" to c.me, "players" to humanSeats, "mode" to modeSel, "goalType" to goalTypeSel,
          "goal" to (if (goalTypeSel == 0) TF_POINT_GOALS[goalIdx] else TF_ROUND_GOALS[goalIdx]),
          "duration" to (if (m.seconds == 0) 0 else duration)
        )
        humanSeats.forEach { keep["score_$it"] = 0 }
        val chosen = TF_CATEGORIES.shuffled().take(categoriesCount)
        c.vm.publishGame("tuttiFrutti", keep + mapOf(
          "phase" to "spin", "round" to 1, "letter" to "?", "categories" to chosen, "spinStart" to System.currentTimeMillis(),
          "endAt" to 0L, "stoppedBy" to "", "scoredRound" to 0, "goalReached" to 0
        ))
      } else c.vm.gameSfx(0)
    })
    if (c.players.distinct().size < 2) Text("Se necesitan al menos dos jugadores humanos conectados.", color = GoldYellow, fontSize = 12.sp)
    return
  }

  val goalText = if (goalType == 0) "Meta: $goal pts" else "Ronda $round de $goal"
  val phaseEnter = remember { Animatable(0f) }
  LaunchedEffect(phase) { phaseEnter.snapTo(0f); phaseEnter.animateTo(1f, tween(420, easing = FastOutSlowInEasing)) }
  Column(
    Modifier.graphicsLayer { alpha = phaseEnter.value; translationY = (1f - phaseEnter.value) * 28f }
      .clip(RoundedCornerShape(22.dp)).background(Brush.verticalGradient(listOf(Color(0xFF1A0F3C), Color(0xFF0B0720)))).padding(8.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    // Marcador siempre visible
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      players.forEach { uid ->
        val target = scores[uid] ?: 0
        val shown by animateFloatAsState(target.toFloat(), tween(700, easing = FastOutSlowInEasing), label = "tfScore")
        Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(Brush.verticalGradient(listOf(Color(0xFF2B3A55), Color(0xFF141D2D)))).border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(14.dp)).padding(8.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            com.example.ui.AvatarCircle(photoOf(uid), c.nameOf(uid), 24.dp)
            Text(c.nameOf(uid), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
          }
          Text("${shown.toInt()}", color = GoldYellow, fontSize = 20.sp, fontWeight = FontWeight.Black)
          if (goalType == 0 && goal > 0) {
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)).background(Color(0x33FFFFFF))) {
              Box(Modifier.fillMaxWidth((shown / goal).coerceIn(0.02f, 1f)).height(4.dp).clip(RoundedCornerShape(50)).background(GoldYellow))
            }
          }
        }
      }
    }
    Text("${TF_MODES[modeIdxState.coerceIn(0, TF_MODES.size - 1)].emoji} ${mode.name} · $goalText · Ronda $round", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)

    // ---------- Ruleta ----------
    if (phase == "spin") {
      GlassCard(modifier = Modifier.fillMaxWidth(), highlight = true) {
        Text("🎰 ¡LA RULETA ESTÁ GIRANDO!", color = GoldYellow, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
        Text("Pulsa STOP cuando quieras: la letra en pantalla será la de la ronda.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TfBigLetter(TF_ALPHABET[spinIdx].toString(), spinning = true) }
        Spacer(Modifier.height(8.dp))
        TF_ALPHABET.chunked(9).forEach { rowLetters ->
          Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            rowLetters.forEach { ch ->
              val cur = ch == TF_ALPHABET[spinIdx]
              Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(if (cur) GoldYellow else Color(0x22FFFFFF)), contentAlignment = Alignment.Center) {
                Text(ch.toString(), color = if (cur) Color.Black else Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
              }
            }
          }
        }
      }
      GradientButton("🛑 ¡STOP!", modifier = Modifier.fillMaxWidth(), onClick = { stopSpin() }, height = 64.dp)
      return@Column
    }

    // ---------- Podio final ----------
    if (phase == "finished") {
      val ranking = players.sortedByDescending { scores[it] ?: 0 }
      GlassCard(modifier = Modifier.fillMaxWidth(), highlight = true) {
        Text("🏆 ¡FIN DE LA PARTIDA!", color = GoldYellow, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
        Spacer(Modifier.height(6.dp))
        ranking.forEachIndexed { pos, uid ->
          Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(listOf("🥇", "🥈", "🥉").getOrElse(pos) { "🎖️" }, fontSize = 24.sp)
            Text("  ${c.nameOf(uid)}", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("${scores[uid] ?: 0} pts", color = GoldYellow, fontWeight = FontWeight.Black, fontSize = 18.sp)
          }
        }
      }
      if (isHost) GradientButton("Nueva partida", modifier = Modifier.fillMaxWidth(), onClick = { c.vm.publishGame("tuttiFrutti", emptyMap()) })
      else Text("Esperando al anfitrión…", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
      return@Column
    }

    // ---------- Letra, cronómetro y respuestas ----------
    GlassCard(modifier = Modifier.fillMaxWidth(), highlight = true) {
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        TfBigLetter(letter, spinning = false)
        Column(horizontalAlignment = Alignment.End) {
          if (phase == "playing") {
            if (endAt > 0L && durationSec > 0) TfTimerRing(seconds / durationSec.toFloat(), "${seconds}s", seconds < 10)
            else Text("♾️ Sin tiempo", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
          } else Text("REVISIÓN", color = GoldYellow, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
          Text(goalText, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
      }
      if (phase == "playing") Text("Las respuestas de los demás se revelan al terminar la ronda.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
    }

    if (phase == "playing") {
      val filledCount = cats.indices.count { localAnswers.getOrElse(it) { "" }.trim().length >= 2 }
      Text("✍️ Completadas: $filledCount de ${cats.size}", color = Color(0xFFFFC857), fontWeight = FontWeight.Bold, fontSize = 12.sp)
      cats.forEachIndexed { idx, category ->
        val ownAnswer = st?.get("ans_${c.me}_$idx") as? String ?: ""
        val canType = myIdx >= 0 && (endAt == 0L || nowMs < endAt)
        TfAnswerRow(
          index = idx, total = cats.size, category = category, letter = letter,
          value = localAnswers.getOrElse(idx) { ownAnswer }, enabled = canType, isLast = idx == cats.lastIndex,
          onChange = { value ->
            if (canType) {
              val copy = localAnswers.toMutableList(); while (copy.size <= idx) copy.add(""); copy[idx] = value; localAnswers = copy
              c.vm.patchGame("tuttiFrutti", mapOf("ans_${c.me}_$idx" to value.take(48)))
            }
          }
        )
      }
      val allFilled = cats.indices.all { localAnswers.getOrElse(it) { "" }.trim().length >= 2 }
      GradientButton("🛑 ¡BASTA!${if (!allFilled) " (completa todo)" else " +10"}", modifier = Modifier.fillMaxWidth(), enabled = allFilled && myIdx >= 0,
        onClick = { c.vm.patchGame("tuttiFrutti", mapOf("phase" to "review", "stoppedBy" to c.me)) }, height = 52.dp)
    } else {
      // Revisión: cada jugador aprueba o rechaza las respuestas de los demás; el diccionario orienta pero decide la mayoría.
      val scored = scoredRound == round
      val result = tfScoreRound(players, cats, letter, { uid, idx -> st?.get("ans_${uid}_$idx") as? String ?: "" }, mode.strict, mode.uniqueOnly, ::verdictOf)
      val stopper = st?.get("stoppedBy") as? String ?: ""
      Text(if (stopper.isNotBlank() && stopper != "timer") "🛑 ${c.nameOf(stopper)} gritó BASTA" else "⏱ Se acabó el tiempo", color = GoldYellow, fontWeight = FontWeight.Bold, fontSize = 14.sp)
      if (!scored) {
        Text(
          "Mirá las respuestas de los demás: 👍 si te parece válida, 👎 si no. 📖 = está en el diccionario. Después confirmá tu revisión.",
          color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp
        )
      }
      cats.forEachIndexed { idx, category ->
        TfAppear(idx) {
          TfDeedCard(idx, cats.size, category) {
            players.forEach { uid ->
              val cell = result[uid]?.getOrNull(idx)
              val raw = (st?.get("ans_${uid}_$idx") as? String ?: "").trim()
              val norm = tfNorm(raw)
              val letterOk = norm.length >= 2 && norm.first().uppercaseChar().toString() == letter
              val inDict = letterOk && TuttiFruttiLexicon.isValid(category, norm)
              val mine = uid == c.me
              val myVote = voteOf(c.me, uid, idx)
              var up = 0; var down = 0
              players.forEach { j -> if (j != uid) when (voteOf(j, uid, idx)) { 1 -> up++; -1 -> down++; else -> {} } }
              Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                com.example.ui.AvatarCircle(photoOf(uid), c.nameOf(uid), 30.dp)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                  Text(if (mine) "${c.nameOf(uid)} (vos)" else c.nameOf(uid), color = Color(0xFF6B5A4A), fontSize = 11.sp, maxLines = 1)
                  Text(
                    if (raw.isEmpty()) "—" else raw,
                    color = if (cell?.valid == true) Color(0xFF1B2A1B) else Color(0xFFC62828), fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 2
                  )
                  Text(
                    when {
                      raw.isEmpty() -> "sin respuesta"
                      !letterOk -> "no empieza con $letter"
                      inDict -> "📖 en el diccionario"
                      else -> "❔ sin verificar"
                    } + if (up + down > 0) "  ·  👍$up 👎$down" else "",
                    color = if (inDict) Color(0xFF1B7F46) else Color(0xFF8A7A6A), fontSize = 10.sp
                  )
                }
                if (scored) {
                  Text(if (cell?.valid == true) "+${cell.points}" else "✗", color = if (cell?.valid == true) Color(0xFF1B8F4B) else Color(0xFFD32F2F), fontWeight = FontWeight.Black, fontSize = 20.sp)
                } else if (!mine && raw.isNotEmpty() && letterOk) {
                  val canVote = myIdx >= 0 && !isReady(c.me)
                  Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TfVoteButton("👍", myVote == 1, Color(0xFF2E9E5B), canVote) {
                      c.vm.patchGame("tuttiFrutti", mapOf("vote_${round}_${c.me}_${uid}_$idx" to (if (myVote == 1) 0 else 1)))
                    }
                    TfVoteButton("👎", myVote == -1, Color(0xFFD64545), canVote) {
                      c.vm.patchGame("tuttiFrutti", mapOf("vote_${round}_${c.me}_${uid}_$idx" to (if (myVote == -1) 0 else -1)))
                    }
                  }
                }
              }
            }
          }
        }
      }
      if (!scored) {
        val readyCount = players.count { isReady(it) }
        val meReady = isReady(c.me)
        GradientButton(
          if (meReady) "✅ Revisión confirmada ($readyCount/${players.size})" else "✅ Confirmar mi revisión ($readyCount/${players.size})",
          modifier = Modifier.fillMaxWidth(), enabled = myIdx >= 0 && !meReady, height = 52.dp,
          onClick = { c.vm.patchGame("tuttiFrutti", mapOf("ready_${round}_${c.me}" to 1)) }
        )
        if (isHost) SoftButton("Cerrar la revisión ahora y contar puntos", onClick = { scoreRound() })
        else if (meReady) Text("Esperando a que los demás confirmen…", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
      } else {
        players.forEach { uid ->
          val g = (st?.get("gain_$uid") as? Number)?.toInt()
          if (g != null) {
            val pop = remember(round, uid) { Animatable(0.5f) }
            LaunchedEffect(round, uid) { pop.animateTo(1f, spring(0.4f, 300f)) }
            Text("${c.nameOf(uid)}: +$g esta ronda", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value })
          }
        }
        if (isHost) {
          if (goalReached) GradientButton("🏆 Ver podio final", modifier = Modifier.fillMaxWidth(), onClick = { c.vm.patchGame("tuttiFrutti", mapOf("phase" to "finished")) }, height = 50.dp)
          else GradientButton("🎰 Siguiente ronda: girar la ruleta", modifier = Modifier.fillMaxWidth(), height = 50.dp, onClick = {
            val keep = (st ?: emptyMap()).filterKeys { it.startsWith("score_") || it in configKeys() }
            startSpin(round + 1, keep)
          })
        } else Text(if (goalReached) "Esperando el podio final…" else "Esperando al anfitrión para la próxima ronda…", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
      }
    }
  }
}
