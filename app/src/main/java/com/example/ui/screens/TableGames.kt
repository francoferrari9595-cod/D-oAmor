package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.width
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.GlassCard
import com.example.ui.components.GradientButton
import com.example.ui.components.SoftButton
import com.example.ui.theme.GoldYellow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.key
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.roundToInt

private val DIE_FACES = listOf("", "⚀", "⚁", "⚂", "⚃", "⚄", "⚅")

// =====================================================================
// =====================================================================
// DIEZ MIL: cinco dados, vaso animado, reglas solicitadas y hasta cuatro asientos.
// =====================================================================
private fun scoreDice(d: List<Int>): Int {
  if (d.isEmpty()) return 0
  val cnt = IntArray(7)
  d.filter { it in 1..6 }.forEach { cnt[it]++ }
  var total = 0
  for (f in 1..6) {
    val n = cnt[f]
    if (n >= 3) {
      total += if (f == 1) 1000 else f * 100
      if (n > 3) total += (n - 3) * if (f == 1) 100 else if (f == 5) 50 else 0
    } else {
      if (f == 1) total += 100 * n
      if (f == 5) total += 50 * n
    }
  }
  return total
}

private fun validKeep(sel: List<Int>): Boolean {
  if (sel.isEmpty() || scoreDice(sel) <= 0) return false
  return true
}

// ---------- Vaso y dados repartidos ----------
/** Vaso de cuero dibujado: se sacude al tirar y se mece despacio en reposo. Tocarlo tira los dados. */
@Composable
private fun DiceCup(rolling: Boolean, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
  val t = rememberInfiniteTransition(label = "cup")
  val shake by t.animateFloat(-1f, 1f, infiniteRepeatable(tween(95, easing = LinearEasing), RepeatMode.Reverse), label = "cupShake")
  val sway by t.animateFloat(-1f, 1f, infiniteRepeatable(tween(3400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "cupSway")
  Canvas(
    modifier = modifier
      .graphicsLayer {
        transformOrigin = TransformOrigin(0.5f, 0.85f)
        rotationZ = if (rolling) shake * 16f else sway * 3f
        translationY = if (rolling) -kotlin.math.abs(shake) * 10f * density else 0f
      }
      .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
  ) {
    val w = size.width; val h = size.height
    // sombra
    drawOval(Color(0x55000000), Offset(w * 0.12f, h * 0.90f), Size(w * 0.76f, h * 0.09f))
    // dados asomando por la boca
    if (!rolling) {
      for ((k, dx) in listOf(-0.16f, 0.02f, 0.18f).withIndex()) {
        val x = w * (0.40f + dx); val y = h * (0.02f + (k % 2) * 0.04f)
        drawRoundRect(Color.White, Offset(x, y), Size(w * 0.16f, w * 0.16f), CornerRadius(w * 0.03f))
        drawCircle(Color(0xFF222222), w * 0.015f, Offset(x + w * 0.08f, y + w * 0.08f))
      }
    }
    val body = Path().apply {
      moveTo(w * 0.14f, h * 0.14f); lineTo(w * 0.86f, h * 0.14f)
      lineTo(w * 0.74f, h * 0.92f); lineTo(w * 0.26f, h * 0.92f); close()
    }
    drawPath(body, Brush.horizontalGradient(listOf(Color(0xFF3B1F0E), Color(0xFFB5733A), Color(0xFF7A4520), Color(0xFF2E1709)), startX = w * 0.14f, endX = w * 0.86f))
    // bandas doradas
    drawLine(Color(0xFFFFD166), Offset(w * 0.19f, h * 0.36f), Offset(w * 0.81f, h * 0.36f), strokeWidth = h * 0.04f)
    drawLine(Color(0xFFFFD166), Offset(w * 0.23f, h * 0.76f), Offset(w * 0.77f, h * 0.76f), strokeWidth = h * 0.04f)
    // brillo
    drawLine(Color(0x44FFFFFF), Offset(w * 0.32f, h * 0.20f), Offset(w * 0.40f, h * 0.88f), strokeWidth = w * 0.035f, cap = StrokeCap.Round)
    // borde y boca
    drawOval(Color(0xFFE6B86A), Offset(w * 0.12f, h * 0.08f), Size(w * 0.76f, h * 0.14f))
    drawOval(Color(0xFF1A0B04), Offset(w * 0.17f, h * 0.10f), Size(w * 0.66f, h * 0.10f))
  }
}

/** Dado repartido desde el vaso: cae al casillero con rebote; los guardados no se vuelven a animar. */
@Composable
private fun DealDie(
  value: Int, held: Boolean, picked: Boolean, index: Int, rollId: Long, boxDp: Dp, enabled: Boolean,
  modifier: Modifier = Modifier, onClick: () -> Unit
) {
  val a = remember { Animatable(1f) }
  LaunchedEffect(value, rollId) {
    when {
      value <= 0 -> a.snapTo(0f)
      held -> a.snapTo(1f)
      else -> { a.snapTo(0f); delay(index * 110L); a.animateTo(1f, spring(0.5f, 170f)) }
    }
  }
  val lift by animateFloatAsState(if (picked) -8f else 0f, spring(0.6f, 420f), label = "dieLift")
  val shape = RoundedCornerShape(14.dp)
  Box(
    modifier = modifier
      .aspectRatio(1f)
      .clip(shape)
      .background(if (held) Color(0x669FE8B5) else Color(0x1AFFFFFF))
      .border(BorderStroke(if (picked) 3.dp else 1.dp, if (picked) GoldYellow else if (held) Color(0xFF9FE8B5) else Color(0x33FFFFFF)), shape)
      .clickable(enabled = enabled && !held && value > 0) { onClick() },
    contentAlignment = Alignment.Center
  ) {
    if (value > 0) {
      Box(
        Modifier.graphicsLayer {
          translationY = -(1f - a.value) * 170f * density + lift * density
          val sc = 0.25f + 0.75f * a.value
          scaleX = sc; scaleY = sc
          rotationZ = (1f - a.value) * 200f
          alpha = (a.value * 2.5f).coerceIn(0f, 1f)
        },
        contentAlignment = Alignment.Center
      ) { Dice3D(value = value, rollId = if (held) 0L else rollId, sizeDp = boxDp * 0.62f) }
    }
  }
}

@Composable
internal fun DiezMilGame(c: GameCtx) {
  val flow = remember { c.vm.gameState("d10k") }
  val st by flow.collectAsState(initial = null)
  fun n(k: String, d: Int = 0) = (st?.get(k) as? Number)?.toInt() ?: d
  fun ints(k: String) = (st?.get(k) as? List<*>)?.mapNotNull { (it as? Number)?.toInt() } ?: emptyList()
  val seats = (st?.get("seats") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
  val started = (st?.get("dice") as? List<*>) != null && seats.isNotEmpty()
  val dice = ints("dice").let { if (it.size == 5) it else List(5) { 0 } }
  val held = ints("held").let { if (it.size == 5) it else List(5) { 0 } }
  val scores = seats.indices.map { n("s$it") }
  val turn = n("turn").coerceAtLeast(0)
  val turnPts = n("pts")
  val winner = n("winner", -1)
  val msg = (st?.get("msg") as? String) ?: ""
  val myIdx = seats.indexOf(c.me)
  val myTurn = started && winner < 0 && turn == myIdx
  val sel = remember { mutableStateListOf<Int>() }
  var botsSel by remember { mutableStateOf(1) }
  var shake by remember { mutableStateOf(false) }
  var diceRollId by remember { mutableStateOf(0L) }
  var rolling by remember { mutableStateOf(false) }
  var visualDice by remember { mutableStateOf(List(5) { 0 }) }
  val rollScope = rememberCoroutineScope()

  fun save(d: List<Int>, h: List<Int>, ss: List<Int>, t: Int, pts: Int, w: Int, m: String, seatList: List<String> = seats) {
    val map = mutableMapOf<String, Any?>("seats" to seatList, "dice" to d, "held" to h, "turn" to t, "pts" to pts, "winner" to w, "msg" to m)
    seatList.indices.forEach { map["s$it"] = ss.getOrElse(it) { 0 } }
    c.vm.publishGame("d10k", map)
  }
  suspend fun rollFree(h: List<Int>, pts: Int, m: String) {
    if (rolling) return
    rolling = true
    val nd = dice.mapIndexed { i, v -> if (h[i] == 0) (1..6).random() else v }
    val free = nd.filterIndexed { i, _ -> h[i] == 0 }
    sel.clear(); shake = !shake; diceRollId += 1L; c.vm.gameSfx(0)
    visualDice = dice.mapIndexed { i, v -> if (h[i] == 1) v else 0 }
    // Revela cada dado por separado para dar suspenso; el resultado se publica al final.
    for (i in nd.indices) {
      if (h[i] == 0) {
        delay(240)
        visualDice = visualDice.toMutableList().also { it[i] = nd[i] }
      }
    }
    delay(180)
    rolling = false
    if (free.isEmpty()) save(List(5) { (1..6).random() }, List(5) { 0 }, scores, turn, pts, -1, "🔥 ¡Dados calientes! Volvés a tirar los cinco")
    else if (scoreDice(free) == 0) save(List(5) { 0 }, List(5) { 0 }, scores, (turn + 1) % seats.size, 0, -1, "💥 ¡Sin puntaje! ${seats[turn].let { if (it == "bot") "El bot" else c.nameOf(it) } } pierde el turno")
    else save(nd, h, scores, turn, pts, -1, m)
  }
  suspend fun keepAndRoll() {
    if (!myTurn) return
    val chosen = sel.map { dice[it] }
    if (!validKeep(chosen)) return
    val h = held.toMutableList(); sel.forEach { h[it] = 1 }
    val pts = turnPts + scoreDice(chosen); sel.clear()
    if (h.all { it == 1 }) rollFree(List(5) { 0 }, pts, "🔥 ¡Dados calientes! Tirá otra vez")
    else rollFree(h, pts, "Llevás $pts puntos en este turno")
  }
  fun bank() {
    if (!myTurn) return
    val chosen = sel.map { dice[it] }
    var pts = turnPts; val h = held.toMutableList()
    if (chosen.isNotEmpty()) { if (!validKeep(chosen)) return; pts += scoreDice(chosen); sel.forEach { h[it] = 1 } }
    if (scores[turn] == 0 && pts < 750) { c.vm.gameSfx(0); return }
    val ns = scores.toMutableList(); ns[turn] += pts
    val w = if (ns[turn] >= 10000) turn else -1
    val next = if (w >= 0) turn else (turn + 1) % seats.size
    save(List(5) { 0 }, List(5) { 0 }, ns, next, 0, w, if (w >= 0) "🏆 ¡${if (seats[turn] == "bot") "El bot" else c.nameOf(seats[turn])} llegó a 10.000!" else "${if (seats[turn] == "bot") "El bot" else c.nameOf(seats[turn])} suma $pts puntos")
    c.vm.gameSfx(2)
  }
  if (!started) {
    GlassCard(modifier = Modifier.fillMaxWidth(), highlight = true) {
      Text("🎲 Diez Mil", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
      Text("Cinco dados en un vaso. El 1 vale 100, el 5 vale 50; tres 1 valen 1.000 y tres iguales del 2 al 6 valen su cara × 100. Para plantarte por primera vez necesitás 750 puntos en un turno. Gana quien llega a 10.000.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    }
    Spacer(Modifier.height(8.dp))
    Text("Bots en la mesa", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { (0..3).forEach { k -> if (botsSel == k) GradientButton("$k bots", onClick = { botsSel = k }, height = 38.dp) else SoftButton("$k bots", onClick = { botsSel = k }) } }
    Spacer(Modifier.height(8.dp))
    GradientButton("🥤 Repartir y empezar", modifier = Modifier.fillMaxWidth(), onClick = {
      val list = c.players.take(4).toMutableList()
      repeat(botsSel.coerceAtMost(4 - list.size)) { list.add("bot") }
      while (list.size < 2) list.add("bot")
      val dice0 = List(5) { 0 }
      val map = mutableMapOf<String, Any?>("seats" to list, "dice" to dice0, "held" to List(5) { 0 }, "turn" to 0, "pts" to 0, "winner" to -1, "msg" to "¡Agitá el vaso y tirá los dados!")
      list.indices.forEach { map["s$it"] = 0 }
      c.vm.publishGame("d10k", map)
    })
    return
  }
  // Bots play on the first device only, avoiding duplicate moves from two clients.
  LaunchedEffect(turn, winner, started, msg, dice.toString()) {
    if (!started || winner >= 0 || seats.getOrNull(turn) != "bot" || c.me != c.firstUid) return@LaunchedEffect
    delay(950)
    if (dice.all { it == 0 }) { val firstRoll = List(5) { (1..6).random() }; if (scoreDice(firstRoll) == 0) save(List(5) { 0 }, List(5) { 0 }, scores, (turn + 1) % seats.size, 0, -1, "El bot no puntuó y pierde el turno") else save(firstRoll, held, scores, turn, turnPts, -1, "El bot sacudió el vaso"); return@LaunchedEffect }
    val freeIdx = dice.indices.filter { held[it] == 0 && dice[it] > 0 }
    val pick = mutableListOf<Int>()
    val faces = freeIdx.map { dice[it] }
    for (face in listOf(1, 5, 2, 3, 4, 6)) {
      val count = faces.count { it == face }
      val need = if (face == 1 || face == 5) 1 else 3
      if (count >= need) repeat(if (need == 1) count else need) { pick.add(face) }
    }
    if (pick.isNotEmpty()) {
      val selectedIndices = mutableListOf<Int>(); val remaining = pick.toMutableList()
      freeIdx.forEach { idx -> if (remaining.contains(dice[idx])) { selectedIndices.add(idx); remaining.remove(dice[idx]) } }
      val h = held.toMutableList(); selectedIndices.forEach { h[it] = 1 }
      val pts = turnPts + scoreDice(selectedIndices.map { dice[it] })
      if (pts >= 750 && (scores[turn] == 0 || pts >= 1200 || (1..6).random() < 4)) {
        val ns = scores.toMutableList(); ns[turn] += pts; val w = if (ns[turn] >= 10000) turn else -1
        save(List(5) { 0 }, List(5) { 0 }, ns, if (w >= 0) turn else (turn + 1) % seats.size, 0, w, "El bot se planta con $pts puntos")
      } else if (h.all { it == 1 }) save(List(5) { (1..6).random() }, List(5) { 0 }, scores, turn, pts, -1, "El bot sacudió el vaso: ¡dados calientes!")
      else {
        val nd = dice.mapIndexed { i, v -> if (h[i] == 0) (1..6).random() else v }
        if (scoreDice(nd.filterIndexed { i, _ -> h[i] == 0 }) == 0) save(List(5) { 0 }, List(5) { 0 }, scores, (turn + 1) % seats.size, 0, -1, "El bot no puntuó y pierde el turno")
        else save(nd, h, scores, turn, pts, -1, "El bot sigue arriesgando")
      }
    } else save(List(5) { 0 }, List(5) { 0 }, scores, (turn + 1) % seats.size, 0, -1, "El bot no encontró dados puntuables")
  }
  Column {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      seats.forEachIndexed { i, uid -> GlassCard(modifier = Modifier.weight(1f), highlight = i == turn) {
        Text(if (uid == "bot") "🤖 Bot ${i + 1}" else c.nameOf(uid), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        Text("${scores.getOrElse(i) { 0 }}", color = GoldYellow, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
      } }
    }
    Spacer(Modifier.height(8.dp))
    GlassCard(modifier = Modifier.fillMaxWidth(), highlight = true) {
      Text("🥤 VASO DE DADOS", color = GoldYellow, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
      Text(msg, color = Color.White, fontSize = 12.sp)
      Spacer(Modifier.height(6.dp))
      BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = 6.dp
        val dieBox = (maxWidth - gap * 4) / 5
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
          // El vaso reparte los cinco dados: tocarlo también tira.
          DiceCup(
            rolling = rolling,
            onClick = if (myTurn && dice.all { it == 0 } && !rolling) ({ rollScope.launch { rollFree(held, turnPts, "¡Dados lanzados!") }; Unit }) else null,
            modifier = Modifier.size(110.dp, 118.dp)
          )
          Spacer(Modifier.height(6.dp))
          Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            (if (rolling) visualDice else dice).forEachIndexed { i, d ->
              DealDie(
                value = d, held = held[i] == 1, picked = sel.contains(i), index = i, rollId = diceRollId, boxDp = dieBox,
                enabled = myTurn && !rolling, modifier = Modifier.weight(1f)
              ) { if (sel.contains(i)) sel.remove(i) else sel.add(i) }
            }
          }
        }
      }
      Spacer(Modifier.height(4.dp))
      Text("Puntaje del turno: $turnPts · Primera plantada: mínimo 750", color = GoldYellow, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
    if (winner >= 0) {
      ResultBanner(msg)
      GradientButton("Revancha", modifier = Modifier.fillMaxWidth(), onClick = { c.vm.publishGame("d10k", emptyMap()) })
    } else if (myTurn) {
      Spacer(Modifier.height(8.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        GradientButton(if (rolling) "🎲 Rodando…" else "🎲 Sacudir / tirar", onClick = { rollScope.launch { rollFree(held, turnPts, "¡Dados lanzados!") } }, enabled = dice.all { it == 0 } && !rolling, modifier = Modifier.weight(1f))
        GradientButton("Guardar y tirar", onClick = { rollScope.launch { keepAndRoll() } }, enabled = sel.isNotEmpty() && !rolling, modifier = Modifier.weight(1f))
      }
      Spacer(Modifier.height(6.dp))
      SoftButton("Plantarme", onClick = { bank() }, enabled = !rolling && (turnPts > 0 || sel.isNotEmpty()))
    } else Text("Turno de ${if (seats.getOrNull(turn) == "bot") "un bot" else c.nameOf(seats.getOrElse(turn) { "" })}…", color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

// =====================================================================
// SERPIENTES Y ESCALERAS (hasta 4 jugadores, con bots opcionales)
// Tres tableros que usan el material gráfico original sin modificarlo:
// las fichas se colocan sobre la imagen según la cuadrícula de cada una.
// =====================================================================
private class SnlBoard(
  val name: String,
  val description: String,
  val imageRes: Int,
  val ladders: Map<Int, Int>,
  val snakes: Map<Int, Int>,
  // Márgenes de la cuadrícula dentro de la imagen, como fracción del ancho/alto.
  val left: Float,
  val top: Float,
  val right: Float,
  val bottom: Float
)

private val SNL_BOARDS = listOf(
  SnlBoard(
    "Tablero original", "Cuadrícula clásica con serpientes de colores", com.example.R.drawable.snakes_ladders_original,
    mapOf(9 to 27, 18 to 37, 25 to 54, 28 to 51, 68 to 88, 56 to 64, 76 to 97, 79 to 100),
    mapOf(99 to 77, 95 to 75, 93 to 69, 87 to 24, 63 to 19, 67 to 30, 59 to 17, 16 to 7),
    0.021f, 0.021f, 0.979f, 0.979f
  ),
  SnlBoard(
    "Tablero 2", "Casillas de colores con serpientes y escaleras", com.example.R.drawable.snl_board2,
    mapOf(3 to 22, 10 to 29, 25 to 46, 50 to 69, 59 to 98, 64 to 85),
    mapOf(94 to 68, 92 to 72, 66 to 58, 48 to 15, 42 to 16, 28 to 9),
    0.022f, 0.022f, 0.978f, 0.978f
  ),
  SnlBoard(
    "Tablero 3", "Serpientes gigantes y muchas escaleras", com.example.R.drawable.snl_board3,
    mapOf(3 to 20, 6 to 14, 11 to 28, 15 to 34, 17 to 74, 22 to 37, 38 to 59, 49 to 67, 57 to 76, 61 to 78, 73 to 86, 88 to 91, 81 to 98),
    mapOf(99 to 63, 97 to 87, 92 to 25, 83 to 45, 85 to 59, 90 to 48, 75 to 28, 56 to 1, 60 to 23, 51 to 6, 54 to 36, 39 to 5, 26 to 10, 8 to 4),
    0.0f, 0.0f, 1.0f, 1.0f
  )
)
private val SNAKE_COLORS = listOf(Color(0xFFE53935), Color(0xFF3DA9FF), Color(0xFFFFC83D), Color(0xFF3DDC84))

/** Centro de la casilla `pos` (1..100) en píxeles, dentro de una imagen de w x h. Fila inferior = 1..10, zigzag hacia arriba. */
private fun snlCenter(pos: Int, b: SnlBoard, w: Float, h: Float): Offset {
  val p = pos.coerceIn(1, 100) - 1
  val row = p / 10
  val col = if (row % 2 == 0) p % 10 else 9 - (p % 10)
  val cw = (b.right - b.left) * w / 10f
  val ch = (b.bottom - b.top) * h / 10f
  return Offset(b.left * w + (col + 0.5f) * cw, b.top * h + (9 - row + 0.5f) * ch)
}

/** Casillas que recorre la ficha casilla por casilla; si se pasa del 100 rebota hacia atrás. */
private fun snlPath(from: Int, d: Int): List<Int> = (1..d).map { val s = from + it; if (s > 100) 200 - s else s }

@Composable
private fun SnlToken(idx: Int, pos: Int, board: SnlBoard, sidePx: Float, slot: Int, slots: Int, ms: Int, active: Boolean) {
  val cell = sidePx * (board.right - board.left) / 10f
  val c0 = snlCenter(if (pos <= 0) 1 else pos, board, sidePx, sidePx)
  val spread = if (slots > 1) cell * 0.17f else 0f
  val ox = (if (slot % 2 == 0) -1f else 1f) * spread
  val oy = (if (slot % 4 < 2) -1f else 1f) * spread
  val x by animateFloatAsState(c0.x + ox, tween(ms, easing = FastOutSlowInEasing), label = "snlx")
  val y by animateFloatAsState(c0.y + oy, tween(ms, easing = FastOutSlowInEasing), label = "snly")
  val hop = remember { Animatable(0f) }
  LaunchedEffect(pos) {
    if (ms < 500) { hop.snapTo(0f); hop.animateTo(1f, tween(ms)) }
  }
  val pulse by rememberInfiniteTransition(label = "snlPulse").animateFloat(
    initialValue = 0f, targetValue = 1f,
    animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "snlPulseV"
  )
  val tokenPx = cell * 0.58f
  val tokenDp = with(LocalDensity.current) { tokenPx.toDp() }
  val ring = if (active) Color(0xFFFFD166).copy(alpha = 0.55f + 0.45f * pulse) else Color.White
  Box(
    Modifier.offset { IntOffset((x - tokenPx / 2f).roundToInt(), (y - tokenPx / 2f).roundToInt()) }
      .graphicsLayer {
        val h = sin(hop.value * PI).toFloat()
        translationY = -h * cell * 0.4f
        scaleX = 1f + 0.12f * h
        scaleY = 1f + 0.12f * h
      }
      .size(tokenDp)
      .shadow(5.dp, CircleShape)
      .clip(CircleShape)
      .background(Brush.radialGradient(listOf(Color.White, SNAKE_COLORS[idx % SNAKE_COLORS.size], Color(0xFF111111))))
      .border(BorderStroke(if (active) 3.dp else 2.dp, ring), CircleShape)
  )
}

/** Dado dibujado con puntos; `rotProvider` se lee solo al dibujar para no recomponer en cada cuadro. */
@Composable
private fun SnlDie(face: Int, rotProvider: () -> Float, dieSize: Dp) {
  Canvas(
    Modifier.size(dieSize).graphicsLayer {
      val r = rotProvider()
      val bounce = abs(sin(r * PI / 90.0)).toFloat()
      rotationZ = r
      translationY = -bounce * 16f
      scaleX = 1f + bounce * 0.10f
      scaleY = 1f + bounce * 0.10f
    }
  ) {
    val s = size.minDimension
    val b = s * 0.94f
    val corner = CornerRadius(s * 0.2f, s * 0.2f)
    drawRoundRect(Color(0x55000000), Offset(s * 0.03f, s * 0.07f), Size(b, b), corner)
    drawRoundRect(Brush.linearGradient(listOf(Color.White, Color(0xFFE6E6EE))), Offset.Zero, Size(b, b), corner)
    drawRoundRect(Color(0x33000000), Offset.Zero, Size(b, b), corner, style = Stroke(width = 2f))
    val f = face.coerceIn(1, 6)
    val pc = if (f == 1) Color(0xFFD32F2F) else Color(0xFF1B1B22)
    val pr = b * (if (f == 1) 0.12f else 0.085f)
    val a = 0.27f
    val m = 0.5f
    val z = 0.73f
    val pts = when (f) {
      1 -> listOf(m to m)
      2 -> listOf(a to a, z to z)
      3 -> listOf(a to a, m to m, z to z)
      4 -> listOf(a to a, z to a, a to z, z to z)
      5 -> listOf(a to a, z to a, m to m, a to z, z to z)
      else -> listOf(a to a, z to a, a to m, z to m, a to z, z to z)
    }
    for (pt in pts) drawCircle(pc, pr, Offset(pt.first * b, pt.second * b))
  }
}

@Composable
private fun SnlBanner(id: Int, text: String, color: Color, onDone: () -> Unit, modifier: Modifier = Modifier) {
  val s = remember { Animatable(0.3f) }
  val a = remember { Animatable(0f) }
  LaunchedEffect(id) {
    launch { a.animateTo(1f, tween(160)) }
    s.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 380f))
    delay(1000)
    a.animateTo(0f, tween(300))
    onDone()
  }
  val shape = RoundedCornerShape(50)
  Box(
    modifier.graphicsLayer { scaleX = s.value; scaleY = s.value; alpha = a.value }
      .shadow(8.dp, shape).clip(shape).background(color).border(2.dp, Color.White, shape)
      .padding(horizontal = 18.dp, vertical = 8.dp)
  ) {
    Text(text, color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp)
  }
}

@Composable
private fun SnlConfetti(modifier: Modifier = Modifier) {
  val t by rememberInfiniteTransition(label = "snlConfetti").animateFloat(
    initialValue = 0f, targetValue = 1f,
    animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing)), label = "snlConfettiT"
  )
  val cols = SNAKE_COLORS + Color.White
  Canvas(modifier) {
    for (i in 0 until 50) {
      val seed = ((i * 7919) % 1000) / 1000f
      val seed2 = ((i * 104729) % 997) / 997f
      val x = size.width * seed
      val y = ((t * (0.6f + seed2) + seed2) % 1f) * (size.height + 40f) - 20f
      drawRect(cols[i % cols.size], Offset(x, y), Size(8.dp.toPx(), 12.dp.toPx()))
    }
  }
}

@Composable
private fun SnlBoardCard(board: SnlBoard, selected: Boolean, onClick: () -> Unit) {
  val border by animateColorAsState(if (selected) Color(0xFFFFD166) else Color(0x33FFFFFF), tween(250), label = "snlCardBorder")
  val sc by animateFloatAsState(if (selected) 1.02f else 1f, spring(dampingRatio = 0.6f), label = "snlCardScale")
  val shape = RoundedCornerShape(16.dp)
  Row(
    Modifier.fillMaxWidth().graphicsLayer { scaleX = sc; scaleY = sc }
      .clip(shape).background(if (selected) Color(0x26FFD166) else Color(0x14FFFFFF))
      .border(2.dp, border, shape).clickable { onClick() }.padding(8.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Image(
      painter = painterResource(id = board.imageRes), contentDescription = board.name,
      modifier = Modifier.size(76.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop
    )
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f)) {
      Text(board.name, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
      Text(board.description, color = Color(0xCCFFFFFF), fontSize = 11.sp)
      Text("🪜 ${board.ladders.size} escaleras · 🐍 ${board.snakes.size} serpientes", color = Color(0xFFFFD166), fontWeight = FontWeight.Bold, fontSize = 11.sp)
    }
    if (selected) Text("✓", color = Color(0xFFFFD166), fontWeight = FontWeight.Black, fontSize = 22.sp)
  }
}

@Composable
internal fun SnakesLaddersGame(c: GameCtx) {
  val ctx = LocalContext.current
  val flow = remember { c.vm.gameState("snl") }
  val st by flow.collectAsState(initial = null)
  fun n(k: String, d: Int = 0) = (st?.get(k) as? Number)?.toInt() ?: d
  val seats = (st?.get("seats") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
  val started = seats.isNotEmpty()
  val pos = (st?.get("pos") as? List<*>)?.mapNotNull { (it as? Number)?.toInt() } ?: emptyList()
  val turn = n("turn")
  val winner = n("winner", -1)
  val msg = (st?.get("msg") as? String) ?: ""
  val seq = n("seq")
  val moveWho = n("who", -1)
  val moveFrom = n("from", 1)
  val moveDice = n("dice")
  val myIdx = seats.indexOf(c.me)
  val board = SNL_BOARDS.getOrElse(n("boardType")) { SNL_BOARDS[0] }
  var botsSel by remember { mutableStateOf(1) }
  var boardSel by remember { mutableStateOf(0) }

  // Estado visual local: posiciones mostradas (pueden ir detrás de las reales mientras se anima la jugada).
  val shown = remember { mutableStateListOf<Int>() }
  var doneSeq by remember { mutableStateOf(-1) }
  var primed by remember { mutableStateOf(false) }
  var dieFace by remember { mutableStateOf(1) }
  val dieRot = remember { Animatable(0f) }
  var tokenMs by remember { mutableStateOf(260) }
  var bannerId by remember { mutableStateOf(0) }
  var bannerText by remember { mutableStateOf<String?>(null) }
  var bannerColor by remember { mutableStateOf(Color(0xFF2E7D32)) }
  var pending by remember { mutableStateOf(-1) }
  val animating = started && doneSeq != seq

  fun nameAt(i: Int): String =
    if (seats.getOrNull(i) == "bot") "Bot ${seats.take(i + 1).count { it == "bot" }}" else c.nameOf(seats.getOrElse(i) { "" })

  fun doRoll(who: Int) {
    if (who !in pos.indices || who !in seats.indices || winner >= 0) return
    val d = (1..6).random()
    val from = pos[who]
    val landing = snlPath(from, d).last()
    var p = landing
    var text = "${nameAt(who)} sacó $d"
    if (from + d > 100) text += " (rebota)"
    val ladderTo = board.ladders[p]
    val snakeTo = board.snakes[p]
    if (ladderTo != null) { text += " 🪜 ¡escalera a $ladderTo!"; p = ladderTo }
    else if (snakeTo != null) { text += " 🐍 ¡serpiente a $snakeTo!"; p = snakeTo }
    val np = pos.toMutableList()
    np[who] = p
    val w = if (p == 100) who else -1
    val next = if (w >= 0) who else if (d == 6) who else (who + 1) % seats.size
    c.vm.publishGame(
      "snl",
      mapOf(
        "seats" to seats, "pos" to np, "turn" to next, "dice" to d, "who" to who, "from" to from, "winner" to w,
        "boardType" to n("boardType"), "seq" to (seq + 1),
        "msg" to if (w >= 0) "🏆 ¡${nameAt(who)} llegó al 100!" else text
      )
    )
  }

  // Animación de cada jugada, dirigida por el estado compartido: la ven y oyen todos los jugadores.
  LaunchedEffect(seq, started) {
    if (!started) { primed = false; return@LaunchedEffect }
    val first = !primed
    primed = true
    val animate = !first && moveWho in seats.indices && moveWho in pos.indices && moveDice in 1..6 && seq != doneSeq
    if (!animate) {
      shown.clear(); shown.addAll(pos)
      dieFace = if (moveDice in 1..6) moveDice else 1
      doneSeq = seq
      return@LaunchedEffect
    }
    shown.clear(); shown.addAll(pos); shown[moveWho] = moveFrom
    tokenMs = 260
    // 1) Dado: gira despacio y va frenando hasta mostrar el resultado.
    launch {
      dieRot.snapTo(0f)
      dieRot.animateTo(720f, tween(1900, easing = FastOutSlowInEasing))
      dieRot.snapTo(0f)
    }
    var last = dieFace
    for (i in 0 until 12) {
      var f = (1..6).random()
      if (f == last) f = f % 6 + 1
      last = f
      dieFace = f
      com.example.audio.GameSfx.play(ctx, "sfx_countdown_tick", 0.25f)
      delay(70L + i * 14L)
    }
    dieFace = moveDice
    delay(500)
    // 2) La ficha avanza casilla por casilla.
    var alt = false
    val path = snlPath(moveFrom, moveDice)
    for (sq in path) {
      shown[moveWho] = sq
      com.example.audio.GameSfx.play(ctx, if (alt) "sfx_ludo_step2" else "sfx_ludo_step", 0.4f)
      alt = !alt
      delay(280)
    }
    val landing = path.lastOrNull() ?: moveFrom
    val end = pos.getOrElse(moveWho) { landing }
    // 3) Escalera o serpiente: deslizamiento lento hasta la casilla final.
    if (end != landing) {
      delay(350)
      val ladder = end > landing
      bannerId += 1
      bannerColor = if (ladder) Color(0xFF2E7D32) else Color(0xFFC62828)
      bannerText = if (ladder) "🪜 ¡Escalera! $landing → $end" else "🐍 ¡Serpiente! $landing → $end"
      if (ladder) c.vm.gameSfx(1) else com.example.audio.GameSfx.play(ctx, "sfx_ludo_squash_laugh", 0.5f)
      tokenMs = 950
      shown[moveWho] = end
      delay(1200)
      tokenMs = 260
    }
    if (winner >= 0) { c.vm.gameSfx(2); delay(400) }
    shown.clear(); shown.addAll(pos)
    doneSeq = seq
  }

  // Un solo cliente (el primer jugador) tira por los bots, y solo cuando terminó la animación anterior.
  LaunchedEffect(turn, winner, seq, animating) {
    if (started && winner < 0 && !animating && seats.getOrNull(turn) == "bot" && c.me == c.firstUid) {
      delay(1000)
      doRoll(turn)
    }
  }

  Crossfade(targetState = started, animationSpec = tween(450), label = "snlPhase") { isStarted ->
    if (!isStarted) {
      Column(Modifier.fillMaxWidth()) {
        GlassCard(modifier = Modifier.fillMaxWidth(), highlight = true) {
          Text("🐍 Serpientes y Escaleras", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
          Text(
            "Tirá el dado y avanzá casilla por casilla. Las escaleras te suben y las serpientes te bajan. Un 6 repite turno. Si te pasás del 100, rebotás hacia atrás.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp
          )
        }
        Spacer(Modifier.height(10.dp))
        Text("Elegí el tablero", color = Color.White, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          SNL_BOARDS.forEachIndexed { index, b -> SnlBoardCard(b, boardSel == index) { boardSel = index } }
        }
        Spacer(Modifier.height(10.dp))
        Text("Bots", color = Color.White, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          listOf(0, 1, 2).forEach { k ->
            if (botsSel == k) GradientButton("$k bots", onClick = { botsSel = k }, height = 38.dp) else SoftButton("$k bots", onClick = { botsSel = k })
          }
        }
        Spacer(Modifier.height(10.dp))
        GradientButton("Empezar en ${SNL_BOARDS[boardSel].name}", modifier = Modifier.fillMaxWidth(), onClick = {
          val sl = c.players.take(4).toMutableList()
          repeat(maxOf(botsSel, 2 - sl.size).coerceAtMost(4 - sl.size)) { sl.add("bot") }
          c.vm.publishGame(
            "snl",
            mapOf(
              "seats" to sl, "pos" to List(sl.size) { 1 }, "turn" to 0, "dice" to 0, "who" to -1, "from" to 1, "winner" to -1,
              "boardType" to boardSel, "seq" to (seq + 1), "msg" to "¡A jugar en ${SNL_BOARDS[boardSel].name}!"
            )
          )
        })
      }
    } else {
      val myTurn = winner < 0 && !animating && turn == myIdx
      val activeTurn = if (animating || winner >= 0) -1 else turn
      Column(Modifier.fillMaxWidth()) {
        // Jugadores
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
          seats.indices.forEach { i ->
            val active = i == activeTurn
            val chipBorder by animateColorAsState(if (active) Color(0xFFFFD166) else Color(0x33FFFFFF), tween(250), label = "snlChip")
            val chipShape = RoundedCornerShape(14.dp)
            Column(
              Modifier.weight(1f).clip(chipShape).background(if (active) Color(0x33FFD166) else Color(0x14FFFFFF))
                .border(2.dp, chipBorder, chipShape).padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(SNAKE_COLORS[i % SNAKE_COLORS.size]).border(1.dp, Color.White, CircleShape))
                Spacer(Modifier.width(5.dp))
                Text(nameAt(i), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
              }
              Text("Casilla ${shown.getOrElse(i) { pos.getOrElse(i) { 1 } }}", color = Color(0xCCFFFFFF), fontSize = 11.sp)
            }
          }
        }
        Spacer(Modifier.height(10.dp))
        // Tablero con marco de madera
        val frame = RoundedCornerShape(22.dp)
        Box(
          Modifier.fillMaxWidth().shadow(12.dp, frame).clip(frame)
            .background(Brush.verticalGradient(listOf(Color(0xFF8A5A2B), Color(0xFF4A2D12), Color(0xFF6B4220))))
            .border(2.dp, Color(0x99FFD166), frame).padding(10.dp)
        ) {
          BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp))) {
            val sidePx = with(LocalDensity.current) { maxWidth.toPx() }
            Image(
              painter = painterResource(id = board.imageRes), contentDescription = board.name,
              modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds
            )
            // Fichas: las que comparten casilla se reparten en esquinas; la que se mueve se dibuja encima.
            val at = seats.indices.map { shown.getOrElse(it) { pos.getOrElse(it) { 1 } } }
            val order = seats.indices.sortedBy { if (it == moveWho && animating) 1 else 0 }
            order.forEach { i ->
              key(i) {
                val same = seats.indices.filter { at[it] == at[i] }
                SnlToken(i, at[i], board, sidePx, same.indexOf(i), same.size, tokenMs, i == activeTurn)
              }
            }
            val bt = bannerText
            if (bt != null) key(bannerId) {
              SnlBanner(bannerId, bt, bannerColor, { bannerText = null }, Modifier.align(Alignment.TopCenter).padding(top = 14.dp))
            }
            if (winner >= 0 && !animating) SnlConfetti(Modifier.fillMaxSize())
          }
        }
        Spacer(Modifier.height(10.dp))
        // Panel del dado
        GlassCard(modifier = Modifier.fillMaxWidth(), corner = 18.dp, highlight = myTurn) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            SnlDie(dieFace, { dieRot.value }, 78.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
              Text(
                if (winner >= 0 && !animating) "¡Fin de la partida!" else if (animating) "Tirando el dado…" else if (myTurn) "¡Es tu turno!" else "Turno de ${nameAt(turn)}",
                color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp
              )
              Text(if (animating) "…" else msg, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
          }
          Spacer(Modifier.height(8.dp))
          if (winner >= 0 && !animating) {
            ResultBanner(msg)
            GradientButton("Revancha", modifier = Modifier.fillMaxWidth(), onClick = {
              c.vm.publishGame(
                "snl",
                mapOf(
                  "seats" to seats, "pos" to List(seats.size) { 1 }, "turn" to 0, "dice" to 0, "who" to -1, "from" to 1, "winner" to -1,
                  "boardType" to n("boardType"), "seq" to (seq + 1), "msg" to "¡Revancha!"
                )
              )
            })
          } else {
            GradientButton(
              "🎲 Tirar dado", modifier = Modifier.fillMaxWidth(), enabled = myTurn && pending != seq, height = 54.dp,
              onClick = { pending = seq; doRoll(myIdx) }
            )
          }
        }
      }
    }
  }
}
