package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.GlassCard
import com.example.ui.components.GradientButton
import com.example.ui.components.SoftButton
import com.example.ui.theme.GoldYellow
import kotlinx.coroutines.delay

/** (cuadro 0..9, tiro dentro del cuadro, cuadro terminado) para una lista de tiros. */
private fun frameOf(r: List<Int>): Triple<Int, Int, Boolean> {
  var i = 0
  var f = 0
  while (f < 9) {
    if (i >= r.size) return Triple(f, 0, false)
    if (r[i] == 10) { i += 1; f++ } else {
      if (i + 1 >= r.size) return Triple(f, 1, false)
      i += 2; f++
    }
  }
  val rest = r.drop(i)
  return when (rest.size) {
    0 -> Triple(9, 0, false)
    1 -> Triple(9, 1, false)
    2 -> if (rest[0] == 10 || rest[0] + rest[1] == 10) Triple(9, 2, false) else Triple(9, 2, true)
    else -> Triple(9, 2, true)
  }
}

private fun bowlScore(r: List<Int>): Int {
  var total = 0
  var i = 0
  for (f in 0 until 10) {
    if (i >= r.size) break
    if (r[i] == 10) { total += 10 + r.getOrElse(i + 1) { 0 } + r.getOrElse(i + 2) { 0 }; i += 1 }
    else if (i + 1 < r.size && r[i] + r[i + 1] == 10) { total += 10 + r.getOrElse(i + 2) { 0 }; i += 2 }
    else { total += r[i] + r.getOrElse(i + 1) { 0 }; i += 2 }
  }
  return total
}

// Posiciones de los 10 bolos (triángulo) en coordenadas de carril 0..1 (x) y fila 0 = cabeza.
private val PIN_POS = listOf(
  Pair(0.5f, 0f),
  Pair(0.42f, 1f), Pair(0.58f, 1f),
  Pair(0.34f, 2f), Pair(0.5f, 2f), Pair(0.66f, 2f),
  Pair(0.26f, 3f), Pair(0.42f, 3f), Pair(0.58f, 3f), Pair(0.74f, 3f)
)

@Composable
internal fun BowlingGame(c: GameCtx) {
  val flow = remember { c.vm.gameState("bowl") }
  val st by flow.collectAsState(initial = null)
  val seats = (st?.get("seats") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
  val started = seats.isNotEmpty()
  val n = seats.size
  fun rollsOf(i: Int) = (st?.get("r$i") as? List<*>)?.mapNotNull { (it as? Number)?.toInt() } ?: emptyList()
  val rolls = (0 until n).map { rollsOf(it) }
  val stand = ((st?.get("stand") as? List<*>)?.mapNotNull { (it as? Number)?.toInt() } ?: emptyList()).let { if (it.size == 10) it else List(10) { 1 } }
  val turn = (st?.get("turn") as? Number)?.toInt() ?: 0
  val over = (st?.get("over") as? Boolean) ?: false
  val msg = (st?.get("msg") as? String) ?: ""
  val myIdx = seats.indexOf(c.me)
  val myTurn = started && !over && turn == myIdx
  var aim by remember { mutableStateOf(0f) }
  var botsSel by remember { mutableStateOf(0) }
  var soloSel by remember { mutableStateOf(false) }
  val ball = remember { Animatable(1f) }
  var shownStand by remember { mutableStateOf(stand) }
  val totalRolls = rolls.sumOf { it.size }
  val meterT = rememberInfiniteTransition(label = "meter")
  val meter by meterT.animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse), label = "power")

  LaunchedEffect(totalRolls) {
    if (totalRolls > 0) {
      ball.snapTo(0f)
      ball.animateTo(1f, tween(1300, easing = FastOutSlowInEasing))
    }
    shownStand = stand
    if (totalRolls > 0 && stand.count { it == 0 } >= 10) c.vm.gameSfx(2)
  }

  fun who(i: Int) = if (seats[i] == "bot") "Bot ${i}" else c.nameOf(seats[i])

  fun doThrow(i: Int, aimV: Float, power: Float) {
    val r = rolls[i]
    val acc = (1f - kotlin.math.abs(aimV) * 0.9f).coerceIn(0f, 1f)
    val pq = (1f - kotlin.math.abs(power - 0.8f) * 1.6f).coerceIn(0.1f, 1f)
    val q = acc * pq
    val standingIdx = stand.indices.filter { stand[it] == 1 }
    val fallen = if (q > 0.82f && (0..99).random() < 55) standingIdx else standingIdx.filter { (0..99).random() < (25 + 70 * q).toInt() }
    val knocked = fallen.size
    val newRolls = r + knocked
    var newStand = stand.toMutableList()
    fallen.forEach { newStand[it] = 0 }
    val (frame, rollIn, done) = frameOf(newRolls)
    // Los bolos se reponen al empezar cuadro o al limpiar la mesa en el décimo.
    if (rollIn == 0 || newStand.all { it == 0 }) newStand = MutableList(10) { 1 }
    val seatDone = frame == 9 && done
    val maps = mutableMapOf<String, Any?>("seats" to seats)
    for (k in 0 until n) maps["r$k"] = if (k == i) newRolls else rolls[k]
    val finishedAll = (0 until n).all { k -> val rr = if (k == i) newRolls else rolls[k]; frameOf(rr).let { it.first == 9 && it.third } }
    // ¿Termina el turno de este jugador? (cuadro completo)
    val turnEnds = rollIn == 0 || seatDone
    var next = i
    if (turnEnds && !finishedAll) {
      var guard = 0
      do { next = (next + 1) % n; guard++ } while (guard <= n && (frameOf(if (next == i) newRolls else rolls[next]).let { it.first == 9 && it.third }))
    }
    if (turnEnds && next != i) newStand = MutableList(10) { 1 }
    maps["stand"] = newStand
    maps["turn"] = next
    maps["over"] = finishedAll
    maps["msg"] = when {
      knocked == 10 && r.isNotEmpty() && frameOf(r).second == 1 -> "🎯 ¡Semipleno de ${who(i)}!"
      knocked == 10 -> "💥 ¡PONCHE de ${who(i)}!"
      else -> "${who(i)} derribó $knocked"
    }
    c.vm.publishGame("bowl", maps)
    c.vm.gameSfx(if (knocked == 10) 2 else 0)
  }

  LaunchedEffect(turn, totalRolls, over, started) {
    if (started && !over && seats.getOrNull(turn) == "bot" && c.me == c.firstUid) {
      delay(1900)
      doThrow(turn, (-30..30).random() / 100f, 0.65f + (0..30).random() / 100f)
    }
  }

  if (!started) {
    GlassCard(modifier = Modifier.fillMaxWidth(), highlight = true) {
      Text("🎳 Bowling 3D", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
      Text("10 cuadros, dos tiros por cuadro. Apunta con el deslizador y toca LANZAR cuando la barra de fuerza esté cerca del verde. Ponche = 10 + los 2 siguientes tiros; semipleno = 10 + el siguiente.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      if (soloSel) GradientButton("Solo yo", onClick = { soloSel = true }, height = 38.dp) else SoftButton("Solo yo", onClick = { soloSel = true })
      if (!soloSel) GradientButton("Los dos", onClick = { soloSel = false }, height = 38.dp) else SoftButton("Los dos", onClick = { soloSel = false })
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      listOf(0, 1, 2).forEach { k -> if (botsSel == k) GradientButton("$k bots", onClick = { botsSel = k }, height = 38.dp) else SoftButton("$k bots", onClick = { botsSel = k }) }
    }
    Spacer(Modifier.height(8.dp))
    GradientButton("Empezar partida", modifier = Modifier.fillMaxWidth(), onClick = {
      val sl = if (soloSel) mutableListOf(c.me) else c.players.take(4).toMutableList()
      repeat(botsSel.coerceAtMost(4 - sl.size)) { sl.add("bot") }
      val m = mutableMapOf<String, Any?>("seats" to sl, "stand" to List(10) { 1 }, "turn" to 0, "over" to false, "msg" to "¡A rodar esas bolas!")
      for (k in sl.indices) m["r$k"] = emptyList<Int>()
      c.vm.publishGame("bowl", m)
    })
    return
  }

  // Marcador
  GlassCard(modifier = Modifier.fillMaxWidth(), corner = 16.dp) {
    for (i in 0 until n) {
      val (fr, _, dn) = frameOf(rolls[i])
      Text(
        "${if (i == turn && !over) "▶ " else ""}${who(i)}: ${bowlScore(rolls[i])} pts · cuadro ${if (dn) 10 else fr + 1}",
        color = if (i == turn) GoldYellow else Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp
      )
      Text(rolls[i].joinToString(" ") { if (it == 10) "X" else "$it" }, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
    }
    Text(msg, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
  }
  Spacer(Modifier.height(8.dp))

  // Carril 3D
  Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(0.95f)) {
    val w = size.width
    val h = size.height
    val topW = w * 0.34f
    val lane = Path()
    lane.moveTo(w * 0.5f - topW / 2, h * 0.04f); lane.lineTo(w * 0.5f + topW / 2, h * 0.04f)
    lane.lineTo(w * 0.94f, h); lane.lineTo(w * 0.06f, h); lane.close()
    drawPath(lane, Brush.verticalGradient(listOf(Color(0xFFC98E4B), Color(0xFFE8B873), Color(0xFFA9692C))))
    val gl = Path()
    gl.moveTo(w * 0.5f - topW / 2 - w * 0.03f, h * 0.04f); gl.lineTo(w * 0.5f - topW / 2, h * 0.04f); gl.lineTo(w * 0.06f, h); gl.lineTo(w * 0.0f, h); gl.close()
    drawPath(gl, Color(0xFF1A1020))
    val gr = Path()
    gr.moveTo(w * 0.5f + topW / 2, h * 0.04f); gr.lineTo(w * 0.5f + topW / 2 + w * 0.03f, h * 0.04f); gr.lineTo(w, h); gr.lineTo(w * 0.94f, h); gr.close()
    drawPath(gr, Color(0xFF1A1020))
    // bolos con perspectiva
    for (k in PIN_POS.indices) {
      val (px, row) = PIN_POS[k]
      val y = h * (0.09f + row * 0.055f)
      val sc = 0.5f + row * 0.06f
      val x = w * 0.5f + (px - 0.5f) * topW * 1.25f
      if (shownStand[k] == 1) {
        drawOval(Color(0x55000000), Offset(x - 9f * sc, y + 14f * sc), Size(18f * sc, 6f * sc))
        drawRoundRect(Brush.horizontalGradient(listOf(Color(0xFFDDDDDD), Color.White, Color(0xFFBBBBBB))), Offset(x - 7f * sc, y - 12f * sc), Size(14f * sc, 28f * sc), androidx.compose.ui.geometry.CornerRadius(7f * sc))
        drawRect(Color(0xFFE53935), Offset(x - 6.5f * sc, y - 2f * sc), Size(13f * sc, 3f * sc))
      } else {
        drawOval(Color(0x66FFFFFF), Offset(x - 12f * sc, y + 6f * sc), Size(24f * sc, 7f * sc))
      }
    }
    // bola
    val t = ball.value
    val bx = w * 0.5f + aim * topW * 0.5f * (0.3f + t * 0.7f)
    val by = h * (0.95f - 0.80f * t)
    val br = (w * 0.075f) * (1f - 0.62f * t)
    if (t < 1f || totalRolls == 0) {
      drawCircle(Color(0x66000000), br, Offset(bx + 6f, by + 8f))
      drawCircle(Brush.radialGradient(listOf(Color(0xFF6A6AD8), Color(0xFF1B1B5A), Color(0xFF05051A)), center = Offset(bx - br * 0.3f, by - br * 0.3f), radius = br * 1.4f), br, Offset(bx, by))
      drawCircle(Color(0xAAFFFFFF), br * 0.18f, Offset(bx - br * 0.35f, by - br * 0.4f))
    }
  }
  if (over) {
    val best = (0 until n).maxByOrNull { bowlScore(rolls[it]) } ?: 0
    ResultBanner("🏆 ¡Ganó ${who(best)} con ${bowlScore(rolls[best])}!")
    Spacer(Modifier.height(6.dp))
    GradientButton("Otra partida", modifier = Modifier.fillMaxWidth(), onClick = {
      val m = mutableMapOf<String, Any?>("seats" to seats, "stand" to List(10) { 1 }, "turn" to 0, "over" to false, "msg" to "¡Nueva partida!")
      for (k in 0 until n) m["r$k"] = emptyList<Int>()
      c.vm.publishGame("bowl", m)
    })
  } else {
    Text("Dirección", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
    Slider(value = aim, onValueChange = { aim = it }, valueRange = -1f..1f, enabled = myTurn)
    Text("Fuerza (suelta cerca del verde)", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
    Canvas(modifier = Modifier.fillMaxWidth().height(14.dp)) {
      drawRect(Color(0x33FFFFFF), Offset.Zero, size)
      drawRect(Color(0x883DDC84), Offset(size.width * 0.65f, 0f), Size(size.width * 0.3f, size.height))
      drawRect(GoldYellow, Offset(size.width * meter - 3f, 0f), Size(6f, size.height))
    }
    Spacer(Modifier.height(6.dp))
    GradientButton(if (myTurn) "🎳 LANZAR" else "Turno de ${who(turn)}…", modifier = Modifier.fillMaxWidth(), enabled = myTurn, height = 54.dp, onClick = { doThrow(myIdx, aim, meter) })
  }
}
