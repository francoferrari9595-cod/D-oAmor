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
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.GlassCard
import com.example.ui.components.GradientButton
import com.example.ui.components.SoftButton
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ═════════════════════════════════════════════════════════════════════════════
// TEJO — tablero de arcilla con bocín y cuatro mechas (medidas en centímetros).
//  · Embocinada (el tejo cae dentro del bocín): 6 puntos.
//  · Mecha que explota: 3 puntos cada una. Embocinada + mecha = ¡moñona!: 9 o más.
//  · Mano: al terminar la ronda, el tejo más cercano al bocín suma 1 punto.
// Mantené apretado para aquietar la mira, arrastrá para apuntar y soltá para lanzar.
// ═════════════════════════════════════════════════════════════════════════════

private const val TEJO_VIEW = 26.0           // centímetros visibles desde el centro hasta el borde del lienzo
private const val TEJO_BOCIN_R = 4.2
private const val TEJO_EMBO_R = 2.1
private const val TEJO_MECHA_HIT = 2.4
private const val TEJO_R = 3.6
private val TEJO_MECHAS = listOf(Pair(0.0, 4.2), Pair(4.2, 0.0), Pair(0.0, -4.2), Pair(-4.2, 0.0))
private val TEJO_SEAT = listOf(Color(0xFFE53935), Color(0xFF3DA9FF), Color(0xFFFFC83D), Color(0xFF3DDC84))
private val TEJO_GOALS = listOf(15, 27, 36)

private class TejoShot(val x: Double, val y: Double, val owner: Int)

private fun DrawScope.drawTejoDisc(c: Offset, r: Float, ring: Color, lift: Float) {
  drawOval(Color(0x66000000), Offset(c.x - r * 1.05f + lift * 0.25f, c.y + r * 0.45f + lift), Size(r * 2.1f, r * 0.9f))
  drawCircle(
    Brush.radialGradient(listOf(Color(0xFFFFE9A8), Color(0xFFD9A441), Color(0xFF8A5E1C)), center = Offset(c.x - r * 0.3f, c.y - r * 0.3f), radius = r * 1.4f),
    r, c
  )
  drawCircle(ring, r * 0.8f, c, style = Stroke(width = r * 0.24f))
  drawCircle(Color(0x66FFFFFF), r * 0.28f, Offset(c.x - r * 0.3f, c.y - r * 0.32f))
  drawCircle(Color(0x99000000), r, c, style = Stroke(width = 1.5f))
}

@Composable
internal fun TejoGame(c: GameCtx) {
  val flow = remember { c.vm.gameState("tejo") }
  val st by flow.collectAsState(initial = null)
  fun n(k: String, d: Int = 0) = (st?.get(k) as? Number)?.toInt() ?: d
  fun dbl(k: String, d: Double = 0.0) = (st?.get(k) as? Number)?.toDouble() ?: d
  fun dList(k: String) = (st?.get(k) as? List<*>)?.mapNotNull { (it as? Number)?.toDouble() } ?: emptyList()
  val seats = (st?.get("seats") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
  val started = seats.isNotEmpty()
  val goal = n("goal", 27)
  val pts = (0 until 4).map { n("pts$it") }
  val round = n("round", 1)
  val k = n("k")
  val startSeat = n("start")
  val tid = n("tid")
  val done = (st?.get("done") as? Boolean) ?: false
  val msg = (st?.get("msg") as? String) ?: ""
  val turn = if (seats.isEmpty()) 0 else (startSeat + k) % seats.size

  var botsSel by remember { mutableStateOf(1) }
  var goalSel by remember { mutableStateOf(27) }
  var doneTid by remember { mutableStateOf(-1) }
  var primed by remember { mutableStateOf(false) }
  var shownPts by remember { mutableStateOf<List<Int>>(emptyList()) }
  var shownTejos by remember { mutableStateOf<List<TejoShot>>(emptyList()) }
  var burnt by remember { mutableStateOf<Set<Int>>(emptySet()) }
  var flying by remember { mutableStateOf<TejoShot?>(null) }
  var fxMechas by remember { mutableStateOf<List<Int>>(emptyList()) }
  var fxAt by remember { mutableStateOf(Offset.Zero) }
  var fxKind by remember { mutableStateOf(0) } // 0 polvo · 1 explosión
  val flight = remember { Animatable(0f) }
  val fx = remember { Animatable(1f) }
  var popId by remember { mutableStateOf(0) }
  var popText by remember { mutableStateOf<String?>(null) }
  var popColor by remember { mutableStateOf(Color(0xFF2E7D32)) }
  var aim by remember { mutableStateOf(Offset.Zero) }
  var holding by remember { mutableStateOf(false) }
  val holdClock = rememberHoldClock(holding)
  val enter = remember { Animatable(0f) }
  val animating = started && doneTid != tid
  fun nameAt(i: Int): String = if (seats.getOrNull(i) == "bot") "Bot ${seats.take(i + 1).count { it == "bot" }}" else c.nameOf(seats.getOrElse(i) { "" })

  LaunchedEffect(started) { enter.snapTo(0f); enter.animateTo(1f, tween(450, easing = FastOutSlowInEasing)) }

  /** Un tejo lanzado: (lx, ly) es donde cae, en centímetros respecto del bocín. */
  fun doThrow(seat: Int, lx: Double, ly: Double) {
    if (seat !in seats.indices || done || animating) return
    val seed = (System.nanoTime() and 0x7FFFFFFFL).toInt()
    val rnd = java.util.Random(seed.toLong())
    val embo = hypot(lx, ly) <= TEJO_EMBO_R
    val exploded = TEJO_MECHAS.indices.filter { hypot(lx - TEJO_MECHAS[it].first, ly - TEJO_MECHAS[it].second) <= TEJO_MECHA_HIT && rnd.nextDouble() < 0.6 }
    val gained = (if (embo) 6 else 0) + 3 * exploded.size
    val np = pts.toMutableList()
    np[seat] += gained
    val tx = dList("tx") + lx
    val ty = dList("ty") + ly
    val owners = (st?.get("to") as? List<*>)?.mapNotNull { (it as? Number)?.toInt() }.orEmpty() + seat
    val newK = k + 1
    val roundEnds = newK >= 2 * seats.size
    var mano = -1
    var winner = -1
    var text = when {
      embo && exploded.isNotEmpty() -> "${nameAt(seat)}: ¡MOÑONA! +$gained"
      embo -> "${nameAt(seat)}: ¡EMBOCINADA! +6"
      exploded.isNotEmpty() -> "${nameAt(seat)}: ¡mecha! +$gained"
      else -> "${nameAt(seat)}: sin puntos"
    }
    if (roundEnds) {
      // Mano: el tejo más cercano al bocín suma 1 punto.
      var best = Double.MAX_VALUE
      for (i in tx.indices) { val d = hypot(tx[i], ty[i]); if (d < best) { best = d; mano = owners[i] } }
      if (mano >= 0) { np[mano] += 1; text += " · mano para ${nameAt(mano)} (+1)" }
      val top = np.take(seats.size).maxOrNull() ?: 0
      if (top >= goal && np.take(seats.size).count { it == top } == 1) {
        winner = np.take(seats.size).indexOf(top)
        text = "🏆 ¡${nameAt(winner)} ganó con $top puntos!"
      }
    }
    val nextRound = if (roundEnds && winner < 0) round + 1 else round
    val data = mutableMapOf<String, Any?>(
      "seats" to seats, "goal" to goal, "round" to nextRound, "k" to (if (roundEnds) 0 else newK),
      "start" to (if (roundEnds && winner < 0) (startSeat + 1) % seats.size else startSeat),
      "tid" to (tid + 1), "tseat" to seat, "lx" to lx, "ly" to ly, "lgain" to gained, "lembo" to embo,
      "lmechas" to exploded, "roundEnd" to roundEnds, "mano" to mano,
      "tx" to (if (roundEnds) emptyList<Double>() else tx), "ty" to (if (roundEnds) emptyList<Double>() else ty),
      "to" to (if (roundEnds) emptyList<Int>() else owners), "done" to (winner >= 0), "msg" to text
    )
    for (i in 0 until 4) data["pts$i"] = np[i]
    c.vm.publishGame("tejo", data)
  }

  // Cada lanzamiento se anima igual en todos los dispositivos a partir del estado compartido.
  LaunchedEffect(tid, started) {
    if (!started) { primed = false; return@LaunchedEffect }
    val first = !primed
    primed = true
    val seat = n("tseat", -1)
    if (first || tid <= 0 || tid == doneTid || seat < 0) {
      val tx = dList("tx"); val ty = dList("ty")
      val owners = (st?.get("to") as? List<*>)?.mapNotNull { (it as? Number)?.toInt() }.orEmpty()
      shownPts = pts
      shownTejos = tx.indices.map { TejoShot(tx[it], ty.getOrElse(it) { 0.0 }, owners.getOrElse(it) { 0 }) }
      burnt = emptySet()
      doneTid = tid
      return@LaunchedEffect
    }
    val lx = dbl("lx"); val ly = dbl("ly")
    val gained = n("lgain")
    val embo = (st?.get("lembo") as? Boolean) ?: false
    val mechas = (st?.get("lmechas") as? List<*>)?.mapNotNull { (it as? Number)?.toInt() } ?: emptyList()
    val roundEnd = (st?.get("roundEnd") as? Boolean) ?: false
    val mano = n("mano", -1)
    val isDone = (st?.get("done") as? Boolean) ?: false
    flying = TejoShot(lx, ly, seat)
    launch { SynthSfx.play(SynthSfx.whoosh, 0.55f) }
    flight.snapTo(0f)
    flight.animateTo(1f, tween(620, easing = FastOutSlowInEasing))
    shownTejos = shownTejos + TejoShot(lx, ly, seat)
    flying = null
    fxAt = Offset(lx.toFloat(), ly.toFloat())
    fxKind = if (mechas.isNotEmpty()) 1 else 0
    fxMechas = mechas
    burnt = burnt + mechas
    launch { SynthSfx.play(SynthSfx.thud, 0.9f) }
    if (embo) launch { SynthSfx.play(SynthSfx.clang, 0.7f) }
    if (mechas.isNotEmpty()) launch { delay(60); SynthSfx.play(SynthSfx.boom, 0.9f) }
    launch { fx.snapTo(0f); fx.animateTo(1f, tween(if (mechas.isNotEmpty()) 1100 else 600)) }
    popId += 1
    popText = when {
      embo && mechas.isNotEmpty() -> "💥 ¡MOÑONA! +$gained"
      embo -> "🪙 ¡EMBOCINADA! +6"
      mechas.isNotEmpty() -> "💥 ¡MECHA! +$gained"
      else -> "Sin puntos"
    }
    popColor = if (mechas.isNotEmpty() || embo) Color(0xFFE65100) else Color(0xFF546E7A)
    delay(1100)
    if (roundEnd && mano >= 0) {
      popId += 1; popText = "🤝 Mano para ${nameAtSafe(seats, c, mano)} (+1)"; popColor = Color(0xFF1E88E5)
      delay(1200)
    }
    shownPts = pts
    if (isDone) c.vm.gameSfx(2)
    if (roundEnd) { shownTejos = emptyList(); burnt = emptySet() }
    doneTid = tid
  }

  // Los bots tiran desde el primer dispositivo apuntando al bocín, con algo de error.
  LaunchedEffect(tid, turn, animating, done, started) {
    if (!started || done || animating || seats.getOrNull(turn) != "bot" || c.me != c.firstUid) return@LaunchedEffect
    delay(1100)
    val rnd = java.util.Random()
    doThrow(turn, rnd.nextGaussian() * 3.4, rnd.nextGaussian() * 3.4)
  }

  if (!started) {
    Column(Modifier.graphicsLayer { alpha = enter.value; translationY = (1f - enter.value) * 30f }) {
      GlassCard(modifier = Modifier.fillMaxWidth(), highlight = true) {
        Text("🪙 Tejo", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
        Text(
          "Lanzá el tejo al bocín. Embocinada (adentro del aro): 6. Cada mecha que explota: 3. Embocinada + mecha = ¡moñona!, 9 o más. " +
            "Cada ronda tiran dos tejos por jugador y el más cercano al bocín suma 1 de mano. Mantené apretado para aquietar la mira, arrastrá para apuntar y soltá.",
          color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp
        )
      }
      Spacer(Modifier.height(10.dp))
      Text("Puntos para ganar", color = Color.White, fontWeight = FontWeight.Bold)
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TEJO_GOALS.forEach { g -> if (goalSel == g) GradientButton("$g", onClick = { goalSel = g }, height = 38.dp) else SoftButton("$g", onClick = { goalSel = g }) }
      }
      Spacer(Modifier.height(8.dp))
      Text("Bots", color = Color.White, fontWeight = FontWeight.Bold)
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (0..3).forEach { b -> if (botsSel == b) GradientButton("$b bots", onClick = { botsSel = b }, height = 38.dp) else SoftButton("$b bots", onClick = { botsSel = b }) }
      }
      Spacer(Modifier.height(10.dp))
      GradientButton("🪙 Empezar", modifier = Modifier.fillMaxWidth(), onClick = {
        val list = c.players.take(4).toMutableList()
        repeat(botsSel.coerceAtMost(4 - list.size)) { list.add("bot") }
        if (list.isEmpty()) list.add("bot")
        c.vm.publishGame("tejo", tejoFreshState(list, goalSel, tid + 1, "¡A tirar!"))
      })
    }
    return
  }

  val canThrow = !done && !animating && seats.getOrNull(turn) == c.me
  val phase = remember(tid) { (tid * 1.37f) % 6.28f }
  val shownP = if (shownPts.isEmpty()) pts else shownPts
  val activeSeat = if (animating || done) -1 else turn

  Column(Modifier.graphicsLayer { alpha = enter.value; translationY = (1f - enter.value) * 30f }) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      seats.indices.forEach { i ->
        val active = i == activeSeat
        val border by animateColorAsState(if (active) TEJO_SEAT[i % 4] else Color(0x33FFFFFF), tween(250), label = "tejoChip")
        Column(
          Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(if (active) Color(0x26FFFFFF) else Color(0x12FFFFFF))
            .border(2.dp, border, RoundedCornerShape(14.dp)).padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
          Text(nameAt(i), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1)
          Text("${shownP[i]}", color = TEJO_SEAT[i % 4], fontWeight = FontWeight.Black, fontSize = 22.sp)
          Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)).background(Color(0x33FFFFFF))) {
            Box(Modifier.fillMaxWidth((shownP[i].toFloat() / goal).coerceIn(0.02f, 1f)).height(4.dp).clip(RoundedCornerShape(50)).background(TEJO_SEAT[i % 4]))
          }
        }
      }
    }
    Text("Ronda $round · meta $goal puntos · tejos que quedan en esta ronda: ${(2 * seats.size - k).coerceAtLeast(0)}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, modifier = Modifier.padding(vertical = 4.dp))
    val frame = RoundedCornerShape(24.dp)
    Box(
      Modifier.fillMaxWidth().aspectRatio(1f).shadow(10.dp, frame).clip(frame).border(2.dp, Color(0x66FFB74D), frame)
    ) {
      Canvas(
        Modifier.fillMaxSize().pointerInput(canThrow, tid) {
          if (!canThrow) return@pointerInput
          awaitEachGesture {
            val down = awaitFirstDown()
            holding = true
            fun place(p: Offset) {
              val s = minOf(size.width, size.height).toFloat() / 2f / TEJO_VIEW.toFloat()
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
            val t = holdClock.value
            val off = aimWobbleOffset(t, aimWobbleAmp(t, 14f, 1f), phase)
            val rnd = java.util.Random()
            holding = false
            doThrow(turn, aim.x + off.x + rnd.nextGaussian() * 0.8, aim.y + off.y + rnd.nextGaussian() * 0.8)
          }
        }
      ) {
        val s = minOf(size.width, size.height).toFloat() / 2f / TEJO_VIEW.toFloat()
        val cx = size.width / 2f
        val cy = size.height / 2f
        fun px(x: Double, y: Double) = Offset(cx + x.toFloat() * s, cy - y.toFloat() * s)
        val f = fx.value
        val shakeAmp = if (fxKind == 1 && f < 0.6f) (1f - f / 0.6f) * 7f else 0f
        translate(sin(f * 90f) * shakeAmp, cos(f * 83f) * shakeAmp * 0.6f) {
          // arcilla con grano
          drawRect(Brush.radialGradient(listOf(Color(0xFFA86B44), Color(0xFF7A4B2D), Color(0xFF4F301C)), center = Offset(cx, cy), radius = size.maxDimension * 0.75f))
          for (i in 0 until 140) {
            val sx = ((i * 7919) % 1000) / 1000f
            val sy = ((i * 104729) % 997) / 997f
            drawCircle(Color(if (i % 2 == 0) 0x18FFFFFF else 0x22000000), (1.2f + (i % 4)) * s * 0.35f, Offset(size.width * sx, size.height * sy))
          }
          // mechas (con marca de quemado si ya explotaron)
          TEJO_MECHAS.forEachIndexed { i, m ->
            val p = px(m.first, m.second)
            if (burnt.contains(i)) {
              drawCircle(Brush.radialGradient(listOf(Color(0xCC000000), Color.Transparent), center = p, radius = 5f * s), 5f * s, p)
            } else {
              val tri = Path().apply {
                moveTo(p.x, p.y - 1.6f * s); lineTo(p.x - 1.2f * s, p.y + 1.1f * s); lineTo(p.x + 1.2f * s, p.y + 1.1f * s); close()
              }
              drawPath(tri, Color(0xFFF5F0E1))
              drawPath(tri, Color(0xFFD32F2F), style = Stroke(width = 1.5f))
            }
          }
          // bocín: aro de metal
          val bc = px(0.0, 0.0)
          drawCircle(Color(0xFF2B1A0E), (TEJO_BOCIN_R - 0.7f).toFloat() * s, bc)
          drawCircle(
            Brush.sweepGradient(listOf(Color(0xFFE3E7EE), Color(0xFF8A929E), Color(0xFFE3E7EE), Color(0xFF6C7480), Color(0xFFE3E7EE)), center = bc),
            TEJO_BOCIN_R.toFloat() * s, bc, style = Stroke(width = 1.4f * s)
          )
          // tejos ya caídos
          shownTejos.forEach { t -> drawTejoDisc(px(t.x, t.y), TEJO_R.toFloat() * s, TEJO_SEAT[t.owner % 4], 0f) }
          // tejo en vuelo: sale desde abajo, se achica y baja con arco
          flying?.let { fl ->
            val p = flight.value
            val target = px(fl.x, fl.y)
            val startY = size.height + 80f
            val x = cx + (target.x - cx) * p
            val y = startY + (target.y - startY) * p - sin(p * PI.toFloat()) * 140f
            drawTejoDisc(Offset(x, y), TEJO_R.toFloat() * s * (1.9f - 0.9f * p), TEJO_SEAT[fl.owner % 4], 40f * (1f - p) * s * 0.2f)
          }
          // efectos de impacto
          if (f < 1f) {
            val at = px(fxAt.x.toDouble(), fxAt.y.toDouble())
            if (fxKind == 0) {
              for (i in 0 until 12) {
                val a = i * (2f * PI.toFloat() / 12f)
                val d = f * 12f * s * (0.5f + (i % 3) * 0.25f)
                drawCircle(Color(0xFFD9B38C).copy(alpha = (1f - f) * 0.7f), (1.6f - f) * s, Offset(at.x + cos(a) * d, at.y + sin(a) * d))
              }
            } else {
              fxMechas.forEach { mi ->
                val m = TEJO_MECHAS[mi.coerceIn(0, 3)]
                val mp = px(m.first, m.second)
                val r = (3f + 8f * f) * s
                drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 1f - f), Color(0xFFFFC107).copy(alpha = (1f - f) * 0.9f), Color(0xFFFF5722).copy(alpha = (1f - f) * 0.5f), Color.Transparent), center = mp, radius = r), r, mp)
                for (i in 0 until 14) {
                  val a = i * (2f * PI.toFloat() / 14f) + mi
                  val d1 = (2f + 5f * f) * s
                  val d2 = (4f + 16f * f) * s
                  drawLine(Color(0xFFFFB300).copy(alpha = 1f - f), Offset(mp.x + cos(a) * d1, mp.y + sin(a) * d1), Offset(mp.x + cos(a) * d2, mp.y + sin(a) * d2), 2.5f, StrokeCap.Round)
                }
                for (i in 0 until 5) {
                  drawCircle(Color(0xFF9E9E9E).copy(alpha = 0.38f * (1f - f)), (2f + 5f * f) * s, Offset(mp.x + sin(i * 2.1f) * 4f * s * f, mp.y - f * 9f * s - i * 1.2f * s * f))
                }
              }
              drawRect(Color.White.copy(alpha = (0.35f * (1f - f * 3f)).coerceAtLeast(0f)))
            }
          }
          // mira
          if (holding) {
            val t = holdClock.value
            val amp = aimWobbleAmp(t, 14f, 1f)
            val off = aimWobbleOffset(t, amp, phase)
            val center = Offset(cx + (aim.x + off.x) * s, cy - (aim.y + off.y) * s)
            drawCircle(Color(0x33FFFFFF), amp * s, Offset(cx + aim.x * s, cy - aim.y * s))
            drawCircle(Color(0xFFFFE08A), TEJO_R.toFloat() * s, center, style = Stroke(width = 2f))
            drawLine(Color(0xFFFFE08A), Offset(center.x - 1.6f * s, center.y), Offset(center.x + 1.6f * s, center.y), 2f)
            drawLine(Color(0xFFFFE08A), Offset(center.x, center.y - 1.6f * s), Offset(center.x, center.y + 1.6f * s), 2f)
          }
        }
      }
      val pt = popText
      if (pt != null) key(popId) {
        TejoPop(popId, pt, popColor, { popText = null }, Modifier.align(Alignment.TopCenter).padding(top = 12.dp))
      }
      if (done && !animating) GameConfetti(Modifier.fillMaxSize())
      Text(
        if (animating) "🪙 ¡Va el tejo!" else if (done) "Fin de la partida" else if (canThrow) "Tu turno · mantené, apuntá y soltá" else "Turno de ${nameAt(turn)}",
        color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.Center,
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp).clip(RoundedCornerShape(50)).background(Color(0x99000000)).padding(horizontal = 12.dp, vertical = 4.dp)
      )
    }
    Spacer(Modifier.height(8.dp))
    Text(if (animating) "…" else msg, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    if (done && !animating) {
      Spacer(Modifier.height(8.dp))
      GradientButton("🔁 Revancha", modifier = Modifier.fillMaxWidth(), onClick = { c.vm.publishGame("tejo", tejoFreshState(seats, goal, tid + 1, "¡Revancha!")) })
    }
  }
}

private fun nameAtSafe(seats: List<String>, c: GameCtx, i: Int): String =
  if (seats.getOrNull(i) == "bot") "Bot ${seats.take(i + 1).count { it == "bot" }}" else c.nameOf(seats.getOrElse(i) { "" })

/** Estado inicial de una partida de tejo (también sirve para la revancha). */
private fun tejoFreshState(seats: List<String>, goal: Int, tid: Int, msg: String): Map<String, Any?> {
  val data = mutableMapOf<String, Any?>(
    "seats" to seats, "goal" to goal, "round" to 1, "k" to 0, "start" to 0, "tid" to tid, "tseat" to -1,
    "lx" to 0.0, "ly" to 0.0, "lgain" to 0, "lembo" to false, "lmechas" to emptyList<Int>(), "roundEnd" to false, "mano" to -1,
    "tx" to emptyList<Double>(), "ty" to emptyList<Double>(), "to" to emptyList<Int>(), "done" to false, "msg" to msg
  )
  for (i in 0 until 4) data["pts$i"] = 0
  return data
}

@Composable
private fun TejoPop(id: Int, text: String, color: Color, onDone: () -> Unit, modifier: Modifier = Modifier) {
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
  ) { Text(text, color = Color.White, fontWeight = FontWeight.Black, fontSize = 17.sp) }
}
