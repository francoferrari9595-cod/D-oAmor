package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.GlassCard
import com.example.ui.components.GradientButton
import com.example.ui.components.SoftButton
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ═════════════════════════════════════════════════════════════════════════════
// DARDOS — tablero reglamentario (medidas en milímetros), 301 / 501 / alrededor del reloj.
// Mantené apretado para aquietar la mira, arrastrá para apuntar y soltá para lanzar.
// ═════════════════════════════════════════════════════════════════════════════

private val DART_ORDER = intArrayOf(20, 1, 18, 4, 13, 6, 10, 15, 2, 17, 3, 19, 7, 16, 8, 11, 14, 9, 12, 5)
private const val DART_VIEW = 205.0          // milímetros visibles desde el centro hasta el borde del lienzo
private val DART_SEAT = listOf(Color(0xFFE53935), Color(0xFF3DA9FF), Color(0xFFFFC83D), Color(0xFF3DDC84))

private class DartHit(val points: Int, val label: String, val number: Int, val ring: Int) // ring: 0 fuera, 1 simple, 2 doble, 3 triple, 4 diana, 5 diana doble
private class DartMode(val name: String, val start: Int, val clock: Boolean)
private val DART_MODES = listOf(DartMode("301", 301, false), DartMode("501", 501, false), DartMode("Alrededor del reloj", 1, true))

/** Qué se acierta en el punto (x, y) en milímetros, con el eje y hacia arriba y el 20 en la parte superior. */
private fun dartHit(x: Double, y: Double): DartHit {
  val r = hypot(x, y)
  if (r > 170.0) return DartHit(0, "Fuera", 0, 0)
  if (r <= 6.35) return DartHit(50, "Diana doble", 25, 5)
  if (r <= 15.9) return DartHit(25, "Diana", 25, 4)
  var ang = Math.toDegrees(atan2(x, y))
  if (ang < 0) ang += 360.0
  val n = DART_ORDER[((ang + 9.0) / 18.0).toInt() % 20]
  return when {
    r in 99.0..107.0 -> DartHit(3 * n, "Triple $n", n, 3)
    r in 162.0..170.0 -> DartHit(2 * n, "Doble $n", n, 2)
    else -> DartHit(n, "$n", n, 1)
  }
}

/** Punto del tablero (mm) para apuntar al número y anillo pedidos (1 simple, 2 doble, 3 triple). */
private fun dartPoint(number: Int, ring: Int): Pair<Double, Double> {
  val idx = DART_ORDER.indexOf(number).coerceAtLeast(0)
  val a = Math.toRadians(idx * 18.0)
  val r = when (ring) { 2 -> 166.0; 3 -> 103.0; else -> 133.0 }
  return Pair(r * sin(a), r * cos(a))
}

private fun DrawScope.drawDartboard(cx: Float, cy: Float, s: Float, numberPaint: android.graphics.Paint) {
  // pared de fondo con luz de foco
  drawCircle(Brush.radialGradient(listOf(Color(0x55FFFFFF), Color.Transparent), center = Offset(cx, cy), radius = 250f * s), 250f * s, Offset(cx, cy))
  drawCircle(Color(0xFF14110F), 190f * s, Offset(cx, cy))
  drawCircle(Color(0xFF2A2420), 190f * s, Offset(cx, cy), style = Stroke(width = 2f))
  for (i in 0 until 20) {
    val start = -90f + i * 18f - 9f
    val dark = i % 2 == 0
    val single = if (dark) Color(0xFF16161A) else Color(0xFFF0E4C8)
    val ring = if (dark) Color(0xFFC62828) else Color(0xFF2E7D32)
    val tl = Offset(cx - 170f * s, cy - 170f * s)
    drawArc(single, start, 18f, true, tl, Size(340f * s, 340f * s))
    // triple y doble como anillos de color
    val rt = 103f * s
    drawArc(ring, start, 18f, false, Offset(cx - rt, cy - rt), Size(rt * 2, rt * 2), style = Stroke(width = 8f * s))
    val rd = 166f * s
    drawArc(ring, start, 18f, false, Offset(cx - rd, cy - rd), Size(rd * 2, rd * 2), style = Stroke(width = 8f * s))
    // alambre entre sectores
    val a = Math.toRadians((start).toDouble())
    drawLine(Color(0xFFB8BEC8), Offset(cx + (15.9f * s) * cos(a).toFloat(), cy + (15.9f * s) * sin(a).toFloat()), Offset(cx + (170f * s) * cos(a).toFloat(), cy + (170f * s) * sin(a).toFloat()), 1f)
  }
  for (r in listOf(99f, 107f, 162f, 170f)) drawCircle(Color(0xFFB8BEC8), r * s, Offset(cx, cy), style = Stroke(width = 1f))
  drawCircle(Color(0xFF2E7D32), 15.9f * s, Offset(cx, cy))
  drawCircle(Color(0xFFC62828), 6.35f * s, Offset(cx, cy))
  drawCircle(Color(0xFFB8BEC8), 15.9f * s, Offset(cx, cy), style = Stroke(width = 1f))
  drawCircle(Color(0xFFB8BEC8), 6.35f * s, Offset(cx, cy), style = Stroke(width = 1f))
  // números alrededor
  numberPaint.textSize = 17f * s * 1.2f
  drawIntoCanvas { canvas ->
    for (i in 0 until 20) {
      val a = Math.toRadians(-90.0 + i * 18.0)
      val rx = cx + (183f * s) * cos(a).toFloat()
      val ry = cy + (183f * s) * sin(a).toFloat()
      canvas.nativeCanvas.drawText("${DART_ORDER[i]}", rx, ry + numberPaint.textSize * 0.35f, numberPaint)
    }
  }
}

private fun DrawScope.drawDart(tip: Offset, color: Color, k: Float) {
  val dx = 0.55f
  val dy = 0.83f
  val len = 74.dp.toPx() * k
  fun at(f: Float) = Offset(tip.x + dx * len * f, tip.y + dy * len * f)
  drawLine(Color(0x55000000), Offset(tip.x + 4f, tip.y + 5f), Offset(at(1f).x + 4f, at(1f).y + 5f), 5.dp.toPx() * k, StrokeCap.Round)
  drawLine(Color(0xFFC9CFDA), tip, at(0.4f), 2.5.dp.toPx() * k, StrokeCap.Round)
  drawLine(Color(0xFF2C313B), at(0.4f), at(0.78f), 6.dp.toPx() * k, StrokeCap.Round)
  val fp = at(0.78f)
  val back = at(1f)
  val px = dy
  val py = -dx
  val wing = 11.dp.toPx() * k
  val flight = Path().apply {
    moveTo(fp.x, fp.y)
    lineTo(back.x + px * wing, back.y + py * wing)
    lineTo(back.x - px * wing, back.y - py * wing)
    close()
  }
  drawPath(flight, color)
  drawPath(flight, Color(0x55FFFFFF), style = Stroke(width = 1f))
}

/** Nivel de dificultad: temblor máximo/mínimo de la mira (mm) y dispersión de los bots. */
private data class DartDiff(val name: String, val amp: Float, val min: Float, val botSigma: Double, val hint: String)
private val DART_DIFFS = listOf(
  DartDiff("😊 Fácil", 28f, 2f, 38.0, "Mira estable y bots torpes"),
  DartDiff("🙂 Normal", 45f, 3f, 22.0, "Como siempre"),
  DartDiff("😤 Difícil", 62f, 5f, 13.0, "Mira que tiembla más y bots precisos"),
  DartDiff("🔥 Experto", 80f, 7f, 7.0, "Temblor fuerte y bots casi perfectos")
)

@Composable
internal fun DartsGame(c: GameCtx) {
  val flow = remember { c.vm.gameState("darts") }
  val st by flow.collectAsState(initial = null)
  fun n(k: String, d: Int = 0) = (st?.get(k) as? Number)?.toInt() ?: d
  fun dbl(k: String, d: Double = 0.0) = (st?.get(k) as? Number)?.toDouble() ?: d
  fun dList(k: String) = (st?.get(k) as? List<*>)?.mapNotNull { (it as? Number)?.toDouble() } ?: emptyList()
  val seats = (st?.get("seats") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
  val started = seats.isNotEmpty()
  val dartDiff = DART_DIFFS[n("diff", 1).coerceIn(0, DART_DIFFS.size - 1)]
  val modeIdx = n("mode").coerceIn(0, DART_MODES.size - 1)
  val mode = DART_MODES[modeIdx]
  val rems = (0 until 4).map { n("rem$it") }
  val turn = n("turn")
  val cnt = n("cnt")
  val startRem = n("startRem")
  val tid = n("tid")
  val done = (st?.get("done") as? Boolean) ?: false
  val msg = (st?.get("msg") as? String) ?: ""
  val myIdx = seats.indexOf(c.me)

  var botsSel by remember { mutableStateOf(1) }
  var modeSel by remember { mutableStateOf(0) }
  var diffSel by remember { mutableStateOf(1) }
  var doneTid by remember { mutableStateOf(-1) }
  var primed by remember { mutableStateOf(false) }
  var shownRems by remember { mutableStateOf<List<Int>>(emptyList()) }
  var shownDarts by remember { mutableStateOf<List<Triple<Double, Double, Int>>>(emptyList()) }
  var flying by remember { mutableStateOf<Triple<Double, Double, Int>?>(null) }
  val flight = remember { Animatable(0f) }
  var popId by remember { mutableStateOf(0) }
  var popText by remember { mutableStateOf<String?>(null) }
  var popColor by remember { mutableStateOf(Color(0xFF2E7D32)) }
  var aim by remember { mutableStateOf(Offset.Zero) }
  var holding by remember { mutableStateOf(false) }
  val holdClock = rememberHoldClock(holding)
  val enter = remember { Animatable(0f) }
  val animating = started && doneTid != tid
  val numberPaint = remember {
    android.graphics.Paint().apply {
      isAntiAlias = true; textAlign = android.graphics.Paint.Align.CENTER
      color = android.graphics.Color.WHITE; typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
  }
  fun nameAt(i: Int): String = if (seats.getOrNull(i) == "bot") "Bot ${seats.take(i + 1).count { it == "bot" }}" else c.nameOf(seats.getOrElse(i) { "" })

  LaunchedEffect(started) { enter.snapTo(0f); enter.animateTo(1f, tween(450, easing = FastOutSlowInEasing)) }

  /** Un lanzamiento: (lx, ly) es el punto exacto donde cae el dardo, en milímetros. */
  fun doThrow(seat: Int, lx: Double, ly: Double) {
    if (seat !in seats.indices || done || animating) return
    val hit = dartHit(lx, ly)
    val r = rems.toMutableList()
    var bust = false
    var win = false
    if (mode.clock) {
      val target = r[seat]
      val ok = (target in 1..20 && hit.number == target && hit.ring in 1..3) || (target == 21 && hit.ring >= 4)
      if (ok) r[seat] = target + 1
      win = r[seat] > 21
    } else {
      val nr = r[seat] - hit.points
      if (nr < 0) { bust = true; r[seat] = startRem } else r[seat] = nr
      win = !bust && nr == 0
    }
    val newCnt = cnt + 1
    val turnEnds = bust || win || newCnt >= 3
    val nextSeat = if (turnEnds && !win) (seat + 1) % seats.size else seat
    val newStart = if (turnEnds && !win) r[nextSeat] else startRem
    val curX: List<Double> = if (turnEnds) emptyList() else dList("curX") + lx
    val curY: List<Double> = if (turnEnds) emptyList() else dList("curY") + ly
    val label = if (mode.clock) (if (r[seat] != rems[seat]) "✔ ${hit.label}" else hit.label) else "${hit.label} · ${hit.points}"
    val text = when {
      win -> "🏆 ¡${nameAt(seat)} ganó la partida!"
      bust -> "💥 ${nameAt(seat)}: ${hit.label} (${hit.points}) ¡se pasó! Vuelve a ${startRem}"
      else -> "${nameAt(seat)}: $label"
    }
    val data = mutableMapOf<String, Any?>(
      "seats" to seats, "mode" to modeIdx, "turn" to nextSeat, "cnt" to (if (turnEnds) 0 else newCnt), "startRem" to newStart,
      "tid" to (tid + 1), "tseat" to seat, "lx" to lx, "ly" to ly, "lpoints" to hit.points, "llabel" to hit.label, "bust" to bust,
      "clear" to turnEnds, "curX" to curX, "curY" to curY, "done" to win, "msg" to text
    )
    for (i in 0 until 4) data["rem$i"] = r[i]
    c.vm.publishGame("darts", data)
  }

  // Cada lanzamiento se anima igual en todos los dispositivos a partir del estado compartido.
  LaunchedEffect(tid, started) {
    if (!started) { primed = false; return@LaunchedEffect }
    val first = !primed
    primed = true
    val seat = n("tseat", -1)
    if (first || tid <= 0 || tid == doneTid || seat < 0) {
      val cx = dList("curX"); val cy = dList("curY")
      shownRems = rems
      shownDarts = cx.indices.map { Triple(cx[it], cy.getOrElse(it) { 0.0 }, turn) }
      doneTid = tid
      return@LaunchedEffect
    }
    val lx = dbl("lx"); val ly = dbl("ly")
    val hitLabel = (st?.get("llabel") as? String) ?: ""
    val pts = n("lpoints")
    val bust = (st?.get("bust") as? Boolean) ?: false
    val clear = (st?.get("clear") as? Boolean) ?: false
    flying = Triple(lx, ly, seat)
    launch { SynthSfx.play(SynthSfx.whoosh, 0.5f) }
    flight.snapTo(0f)
    flight.animateTo(1f, tween(480, easing = FastOutSlowInEasing))
    shownDarts = shownDarts + Triple(lx, ly, seat)
    flying = null
    launch { SynthSfx.play(SynthSfx.thud, 0.9f) }
    popId += 1
    popText = if (bust) "¡BUST!" else if (pts > 0 && !mode.clock) "$hitLabel · $pts" else hitLabel
    popColor = if (bust) Color(0xFFC62828) else if (pts >= 40) Color(0xFFFF8F00) else Color(0xFF2E7D32)
    if (bust) c.vm.gameSfx(0)
    delay(900)
    shownRems = rems
    if ((st?.get("done") as? Boolean) == true) c.vm.gameSfx(2)
    if (clear) { delay(1000); shownDarts = emptyList() }
    doneTid = tid
  }

  // Los bots tiran desde el primer dispositivo, con un pulso que no es perfecto.
  LaunchedEffect(tid, turn, animating, done, started) {
    if (!started || done || animating || seats.getOrNull(turn) != "bot" || c.me != c.firstUid) return@LaunchedEffect
    delay(1100)
    val rem = rems[turn]
    val target: Pair<Double, Double> = when {
      mode.clock -> if (rem >= 21) Pair(0.0, 0.0) else dartPoint(rem.coerceIn(1, 20), 1)
      rem > 60 -> dartPoint(20, 3)
      rem == 50 -> Pair(0.0, 0.0)
      rem <= 20 -> dartPoint(rem.coerceAtLeast(1), 1)
      rem <= 40 && rem % 2 == 0 -> dartPoint(rem / 2, 2)
      rem <= 40 -> dartPoint(1, 1)
      else -> dartPoint(20, 1)
    }
    val rnd = java.util.Random()
    doThrow(turn, target.first + rnd.nextGaussian() * dartDiff.botSigma, target.second + rnd.nextGaussian() * dartDiff.botSigma)
  }

  if (!started) {
    Column(Modifier.graphicsLayer { alpha = enter.value; translationY = (1f - enter.value) * 30f }) {
      GlassCard(modifier = Modifier.fillMaxWidth(), highlight = true) {
        Text("🎯 Dardos", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
        Text(
          "Tres dardos por turno. En 301 y 501 restá tus puntos hasta llegar justo a cero (si te pasás, el turno no cuenta). " +
            "En «Alrededor del reloj» acertá del 1 al 20 en orden y cerrá con la diana. Mantené apretado para aquietar la mira, arrastrá para apuntar y soltá para tirar.",
          color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp
        )
      }
      Spacer(Modifier.height(10.dp))
      Text("Modo", color = Color.White, fontWeight = FontWeight.Bold)
      Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DART_MODES.forEachIndexed { i, m -> if (modeSel == i) GradientButton(m.name, onClick = { modeSel = i }, height = 38.dp) else SoftButton(m.name, onClick = { modeSel = i }) }
      }
      Spacer(Modifier.height(8.dp))
      Text("Dificultad", color = Color.White, fontWeight = FontWeight.Bold)
      Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DART_DIFFS.forEachIndexed { i, d -> if (diffSel == i) GradientButton(d.name, onClick = { diffSel = i }, height = 38.dp) else SoftButton(d.name, onClick = { diffSel = i }) }
      }
      Text(DART_DIFFS[diffSel].hint, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
      Spacer(Modifier.height(8.dp))
      Text("Bots", color = Color.White, fontWeight = FontWeight.Bold)
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (0..3).forEach { k -> if (botsSel == k) GradientButton("$k bots", onClick = { botsSel = k }, height = 38.dp) else SoftButton("$k bots", onClick = { botsSel = k }) }
      }
      Spacer(Modifier.height(10.dp))
      GradientButton("🎯 Empezar", modifier = Modifier.fillMaxWidth(), onClick = {
        val list = c.players.take(4).toMutableList()
        repeat(botsSel.coerceAtMost(4 - list.size)) { list.add("bot") }
        if (list.isEmpty()) list.add("bot")
        val m = DART_MODES[modeSel]
        val data = mutableMapOf<String, Any?>(
          "seats" to list, "mode" to modeSel, "diff" to diffSel, "turn" to 0, "cnt" to 0, "startRem" to m.start, "tid" to (tid + 1), "tseat" to -1,
          "lx" to 0.0, "ly" to 0.0, "lpoints" to 0, "llabel" to "", "bust" to false, "clear" to false,
          "curX" to emptyList<Double>(), "curY" to emptyList<Double>(), "done" to false, "msg" to "¡A tirar!"
        )
        for (i in 0 until 4) data["rem$i"] = m.start
        c.vm.publishGame("darts", data)
      })
    }
    return
  }

  val canThrow = !done && !animating && seats.getOrNull(turn) == c.me
  val phase = remember(tid) { (tid * 1.37f) % 6.28f }
  // La mira del que lanza se ve en vivo en todos los teléfonos mientras apunta.
  val liveAll by remember { c.vm.liveStates() }.collectAsState(initial = emptyMap())
  val throwerUid = seats.getOrNull(turn) ?: ""
  val remote = liveAll[throwerUid]?.takeIf { throwerUid != c.me && throwerUid != "bot" && (it["g"] as? String) == "darts" && (it["tid"] as? Number)?.toInt() == tid }
  val remoteOn = !done && !animating && !canThrow && remote != null && (remote["on"] as? Boolean) == true
  val rx = (remote?.get("x") as? Number)?.toFloat() ?: 0f
  val ry = (remote?.get("y") as? Number)?.toFloat() ?: 0f
  val rAmp = (remote?.get("amp") as? Number)?.toFloat() ?: 0f
  val rCx by androidx.compose.animation.core.animateFloatAsState(rx, androidx.compose.animation.core.tween(120), label = "dx")
  val rCy by androidx.compose.animation.core.animateFloatAsState(ry, androidx.compose.animation.core.tween(120), label = "dy")
  LaunchedEffect(holding, tid) {
    if (holding) {
      while (holding) {
        val t = holdClock.value
        val amp = aimWobbleAmp(t, dartDiff.amp, dartDiff.min)
        val off = aimWobbleOffset(t, amp, phase)
        c.vm.publishLive(mapOf("g" to "darts", "tid" to tid, "on" to true, "x" to (aim.x + off.x).toDouble(), "y" to (aim.y + off.y).toDouble(), "amp" to amp.toDouble()))
        delay(120)
      }
      c.vm.publishLive(mapOf("g" to "darts", "tid" to tid, "on" to false))
    }
  }
  val shownR = if (shownRems.isEmpty()) rems else shownRems
  val activeSeat = if (animating || done) -1 else turn

  Column(Modifier.graphicsLayer { alpha = enter.value; translationY = (1f - enter.value) * 30f }) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      seats.indices.forEach { i ->
        val active = i == activeSeat
        val border by animateColorAsState(if (active) DART_SEAT[i % 4] else Color(0x33FFFFFF), tween(250), label = "dartChip")
        Column(
          Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(if (active) Color(0x26FFFFFF) else Color(0x12FFFFFF))
            .border(2.dp, border, RoundedCornerShape(14.dp)).padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
          Text(nameAt(i), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1)
          Text(
            if (mode.clock) (if (shownR[i] > 21) "🏆" else if (shownR[i] == 21) "Diana" else "${shownR[i]}") else "${shownR[i]}",
            color = DART_SEAT[i % 4], fontWeight = FontWeight.Black, fontSize = 22.sp
          )
          if (active) Text("●".repeat((3 - cnt).coerceIn(0, 3)) + "○".repeat(cnt.coerceIn(0, 3)), color = Color(0xFFFFC857), fontSize = 11.sp)
        }
      }
    }
    Spacer(Modifier.height(8.dp))
    val frame = RoundedCornerShape(24.dp)
    Box(
      Modifier.fillMaxWidth().aspectRatio(1f).shadow(10.dp, frame).clip(frame)
        .background(Brush.verticalGradient(listOf(Color(0xFF241B35), Color(0xFF0C0814))))
        .border(2.dp, Color(0x66B388FF), frame)
    ) {
      Canvas(
        Modifier.fillMaxSize().pointerInput(canThrow, tid) {
          if (!canThrow) return@pointerInput
          awaitEachGesture {
            val down = awaitFirstDown()
            holding = true
            fun place(p: Offset) {
              val s = minOf(size.width, size.height).toFloat() / 2f / DART_VIEW.toFloat()
              aim = Offset((p.x - size.width / 2f) / s, -(p.y - 56.dp.toPx() - size.height / 2f) / s)
            }
            place(down.position)
            while (true) {
              val ev = awaitPointerEvent()
              val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
              if (!ch.pressed) break
              place(ch.position)
              ch.consume()
            }
            // Al soltar: el dardo cae donde apunta la mira, más el temblor que quede en ese instante.
            val t = holdClock.value
            val off = aimWobbleOffset(t, aimWobbleAmp(t, dartDiff.amp, dartDiff.min), phase)
            val rnd = java.util.Random()
            holding = false
            doThrow(turn, aim.x + off.x + rnd.nextGaussian() * 2.5, aim.y + off.y + rnd.nextGaussian() * 2.5)
          }
        }
      ) {
        val s = minOf(size.width, size.height).toFloat() / 2f / DART_VIEW.toFloat()
        val cx = size.width / 2f
        val cy = size.height / 2f
        drawDartboard(cx, cy, s, numberPaint)
        // dardos clavados
        shownDarts.forEach { d -> drawDart(Offset(cx + d.first.toFloat() * s, cy - d.second.toFloat() * s), DART_SEAT[d.third % 4], 1f) }
        // dardo en vuelo: sale desde abajo, se achica y gira con un poco de arco
        flying?.let { f ->
          val p = flight.value
          val tx = cx + f.first.toFloat() * s
          val ty = cy - f.second.toFloat() * s
          val sx = cx + (tx - cx) * 0.35f * p
          val x = sx + (tx - sx) * p
          val y = size.height + 60f + (ty - (size.height + 60f)) * p - sin(p * PI.toFloat()) * 80f
          drawDart(Offset(x, y), DART_SEAT[f.third % 4], 1.6f - 0.6f * p)
        }
        // mira del otro jugador (en vivo)
        if (remoteOn) {
          val rc = Offset(cx + rCx * s, cy - rCy * s)
          drawCircle(Color(0x33FF6B8B), rAmp * s, rc)
          drawCircle(Color(0xFFFF8AA0), 9f * s, rc, style = Stroke(width = 2f))
          drawLine(Color(0xFFFF8AA0), Offset(rc.x - 16f * s, rc.y), Offset(rc.x + 16f * s, rc.y), 2f)
          drawLine(Color(0xFFFF8AA0), Offset(rc.x, rc.y - 16f * s), Offset(rc.x, rc.y + 16f * s), 2f)
        }
        // mira: círculo de incertidumbre + cruz
        if (holding) {
          val t = holdClock.value
          val amp = aimWobbleAmp(t, dartDiff.amp, dartDiff.min)
          val off = aimWobbleOffset(t, amp, phase)
          val center = Offset(cx + (aim.x + off.x) * s, cy - (aim.y + off.y) * s)
          drawCircle(Color(0x33FFFFFF), amp * s, Offset(cx + aim.x * s, cy - aim.y * s))
          drawCircle(Color(0xFFFFE08A), 9f * s, center, style = Stroke(width = 2f))
          drawLine(Color(0xFFFFE08A), Offset(center.x - 16f * s, center.y), Offset(center.x + 16f * s, center.y), 2f)
          drawLine(Color(0xFFFFE08A), Offset(center.x, center.y - 16f * s), Offset(center.x, center.y + 16f * s), 2f)
        }
      }
      val pt = popText
      if (pt != null) key(popId) {
        DartPop(popId, pt, popColor, { popText = null }, Modifier.align(Alignment.TopCenter).padding(top = 12.dp))
      }
      if (done && !animating) GameConfetti(Modifier.fillMaxSize())
      Text(
        if (animating) "🎯 ¡Va el dardo!" else if (done) "Fin de la partida" else if (canThrow) "Tu turno · mantené, apuntá y soltá" else if (remoteOn) "👀 ${nameAt(turn)} está apuntando…" else "Turno de ${nameAt(turn)}",
        color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.Center,
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp).clip(RoundedCornerShape(50)).background(Color(0x99000000)).padding(horizontal = 12.dp, vertical = 4.dp)
      )
    }
    Spacer(Modifier.height(8.dp))
    Text(if (animating) "…" else msg, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    if (done && !animating) {
      Spacer(Modifier.height(8.dp))
      GradientButton("🔁 Revancha", modifier = Modifier.fillMaxWidth(), onClick = {
        val m = DART_MODES[modeIdx]
        val data = mutableMapOf<String, Any?>(
          "seats" to seats, "mode" to modeIdx, "turn" to 0, "cnt" to 0, "startRem" to m.start, "tid" to (tid + 1), "tseat" to -1,
          "lx" to 0.0, "ly" to 0.0, "lpoints" to 0, "llabel" to "", "bust" to false, "clear" to false,
          "curX" to emptyList<Double>(), "curY" to emptyList<Double>(), "done" to false, "msg" to "¡Revancha!"
        )
        for (i in 0 until 4) data["rem$i"] = m.start
        c.vm.publishGame("darts", data)
      })
    }
  }
}

@Composable
private fun DartPop(id: Int, text: String, color: Color, onDone: () -> Unit, modifier: Modifier = Modifier) {
  val s = remember { Animatable(0.3f) }
  val a = remember { Animatable(0f) }
  LaunchedEffect(id) {
    launch { a.animateTo(1f, tween(120)) }
    s.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 380f))
    delay(1100)
    a.animateTo(0f, tween(300))
    onDone()
  }
  val shape = RoundedCornerShape(50)
  Box(
    modifier.graphicsLayer { scaleX = s.value; scaleY = s.value; alpha = a.value }
      .clip(shape).background(color).border(2.dp, Color.White, shape).padding(horizontal = 18.dp, vertical = 8.dp)
  ) { Text(text, color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp) }
}
