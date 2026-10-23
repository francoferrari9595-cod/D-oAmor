package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.GlassCard
import com.example.ui.components.GradientButton
import com.example.ui.theme.GoldYellow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sqrt

private const val TH = 1.8f          // alto de la mesa (el ancho es 1)
private const val BR = 0.034f        // radio de bola
private const val PR = 0.075f        // radio de tronera
private val POCKETS = listOf(Offset(0f, 0f), Offset(1f, 0f), Offset(0f, TH / 2), Offset(1f, TH / 2), Offset(0f, TH), Offset(1f, TH))
private val BALL_COLORS = listOf(
  Color(0xFFF5F5F5), Color(0xFFFDD835), Color(0xFF1E88E5), Color(0xFFE53935), Color(0xFF8E24AA),
  Color(0xFFFB8C00), Color(0xFF43A047), Color(0xFF8D2B2B), Color(0xFF212121), Color(0xFFFDD835)
)

private fun rackPositions(): Pair<List<Float>, List<Float>> {
  val xs = MutableList(10) { 0.5f }
  val ys = MutableList(10) { TH * 0.78f }          // 0 = blanca
  val order = listOf(1, 2, 3, 4, 9, 5, 6, 7, 8)    // el 9 al centro del rombo
  val rows = listOf(listOf(0), listOf(-1, 1), listOf(-2, 0, 2), listOf(-1, 1), listOf(0))
  var idx = 0
  for ((r, cols) in rows.withIndex()) {
    for (cx in cols) {
      if (idx >= order.size) break
      xs[order[idx]] = 0.5f + cx * (BR + 0.002f)
      ys[order[idx]] = TH * 0.28f - r * (BR * 1.75f)
      idx++
    }
  }
  return Pair(xs, ys)
}

@Composable
internal fun PoolGame(c: GameCtx) {
  val flow = remember { c.vm.gameState("pool") }
  val st by flow.collectAsState(initial = null)
  val docX = (st?.get("bx") as? List<*>)?.mapNotNull { (it as? Number)?.toFloat() } ?: emptyList()
  val docY = (st?.get("by") as? List<*>)?.mapNotNull { (it as? Number)?.toFloat() } ?: emptyList()
  val docAlive = (st?.get("alive") as? List<*>)?.mapNotNull { (it as? Number)?.toInt() } ?: emptyList()
  val started = docX.size == 10 && docY.size == 10 && docAlive.size == 10
  val turn = (st?.get("turn") as? Number)?.toInt() ?: 0
  val winner = (st?.get("winner") as? Number)?.toInt() ?: -1
  val seq = (st?.get("seq") as? Number)?.toLong() ?: 0L
  val msg = (st?.get("msg") as? String) ?: ""
  val myTurn = started && winner < 0 && turn == c.myIdx
  val scope = rememberCoroutineScope()
  val px = remember { mutableStateListOf<Float>().apply { addAll(List(10) { 0.5f }) } }
  val py = remember { mutableStateListOf<Float>().apply { addAll(List(10) { TH * 0.78f }) } }
  val alive = remember { mutableStateListOf<Int>().apply { addAll(List(10) { 1 }) } }
  var simulating by remember { mutableStateOf(false) }
  var dragStart by remember { mutableStateOf<Offset?>(null) }
  var dragNow by remember { mutableStateOf<Offset?>(null) }
  val prog = remember { Animatable(1f) }

  // Quien mira recibe las posiciones finales y las anima suavemente.
  LaunchedEffect(seq, started) {
    if (!started || simulating) return@LaunchedEffect
    val fx = px.toList(); val fy = py.toList()
    prog.snapTo(0f)
    prog.animateTo(1f, tween(900))
    for (i in 0 until 10) { px[i] = docX[i]; py[i] = docY[i]; alive[i] = docAlive[i] }
    @Suppress("UNUSED_VARIABLE") val unused = fx.size + fy.size
  }

  fun publish(turnNext: Int, win: Int, text: String) {
    c.vm.publishGame("pool", mapOf(
      "bx" to px.toList(), "by" to py.toList(), "alive" to alive.toList(), "turn" to turnNext, "winner" to win,
      "msg" to text, "seq" to seq + 1
    ))
  }

  fun shoot(dir: Offset, power: Float) {
    if (!myTurn || simulating) return
    simulating = true
    scope.launch {
      val vx = FloatArray(10); val vy = FloatArray(10)
      vx[0] = dir.x * power; vy[0] = dir.y * power
      var potted = 0; var scratch = false; var nineIn = false
      var steps = 0
      var moving = true
      while (moving && steps < 900) {
        steps++
        for (i in 0 until 10) if (alive[i] == 1) { px[i] += vx[i] * 0.016f; py[i] += vy[i] * 0.016f }
        for (i in 0 until 10) {
          if (alive[i] != 1) continue
          if (px[i] < BR) { px[i] = BR; vx[i] = -vx[i] * 0.9f }
          if (px[i] > 1f - BR) { px[i] = 1f - BR; vx[i] = -vx[i] * 0.9f }
          if (py[i] < BR) { py[i] = BR; vy[i] = -vy[i] * 0.9f }
          if (py[i] > TH - BR) { py[i] = TH - BR; vy[i] = -vy[i] * 0.9f }
        }
        for (i in 0 until 10) for (j in i + 1 until 10) {
          if (alive[i] != 1 || alive[j] != 1) continue
          val dx = px[j] - px[i]; val dy = py[j] - py[i]
          val d = sqrt(dx * dx + dy * dy)
          if (d < BR * 2 && d > 0.0001f) {
            val nx = dx / d; val ny = dy / d
            val rel = (vx[i] - vx[j]) * nx + (vy[i] - vy[j]) * ny
            if (rel > 0) {
              vx[i] -= rel * nx; vy[i] -= rel * ny; vx[j] += rel * nx; vy[j] += rel * ny
            }
            val overlap = BR * 2 - d
            px[i] -= nx * overlap / 2; py[i] -= ny * overlap / 2; px[j] += nx * overlap / 2; py[j] += ny * overlap / 2
          }
        }
        for (i in 0 until 10) {
          if (alive[i] != 1) continue
          for (p in POCKETS) {
            val dx = px[i] - p.x; val dy = py[i] - p.y
            if (sqrt(dx * dx + dy * dy) < PR) {
              alive[i] = 0; vx[i] = 0f; vy[i] = 0f
              if (i == 0) scratch = true else { potted++; if (i == 9) nineIn = true }
              c.vm.gameSfx(2)
            }
          }
        }
        moving = false
        for (i in 0 until 10) {
          vx[i] *= 0.985f; vy[i] *= 0.985f
          if (sqrt(vx[i] * vx[i] + vy[i] * vy[i]) < 0.02f) { vx[i] = 0f; vy[i] = 0f } else moving = true
        }
        delay(16)
      }
      if (scratch) { alive[0] = 1; px[0] = 0.5f; py[0] = TH * 0.78f }
      val other = 1 - c.myIdx
      when {
        nineIn && !scratch -> publish(c.myIdx, c.myIdx, "🏆 ¡${c.nameOfIdx(c.myIdx)} embocó la 9!")
        nineIn && scratch -> publish(other, other, "La blanca cayó con la 9: gana ${c.nameOfIdx(other)}")
        scratch -> publish(other, -1, "⚪ Blanca embocada: turno de ${c.nameOfIdx(other)}")
        potted > 0 -> publish(c.myIdx, -1, "¡Embocó $potted! Sigue tirando")
        else -> publish(other, -1, "Sin emboque: turno de ${c.nameOfIdx(other)}")
      }
      simulating = false
    }
  }

  if (!started) {
    GlassCard(modifier = Modifier.fillMaxWidth(), highlight = true) {
      Text("🎱 Pool 3D (bola 9)", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
      Text("Arrastra desde cualquier parte hacia atrás (como una honda) y suelta para golpear la blanca: cuanto más largo, más fuerza. Embocar sigue el turno; embocar la 9 gana; si cae la blanca pierdes el turno.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    }
    Spacer(Modifier.height(8.dp))
    GradientButton("Colocar las bolas", modifier = Modifier.fillMaxWidth(), onClick = {
      val (xs, ys) = rackPositions()
      for (i in 0 until 10) { px[i] = xs[i]; py[i] = ys[i]; alive[i] = 1 }
      publish(0, -1, "¡Rompe ${c.nameOfIdx(0)}!")
    })
    return
  }

  GlassCard(modifier = Modifier.fillMaxWidth(), corner = 16.dp, highlight = myTurn) {
    Text(if (winner >= 0) msg else if (myTurn) "Tu turno: arrastra hacia atrás y suelta" else "Turno de ${c.nameOfIdx(turn)}…", color = Color.White, fontWeight = FontWeight.ExtraBold)
    Text(msg, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
  }
  Spacer(Modifier.height(8.dp))
  Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
    Canvas(
      modifier = Modifier.fillMaxWidth(0.62f).aspectRatio(1f / TH)
        .pointerInput(myTurn, simulating) {
          if (myTurn && !simulating) {
            detectDragGestures(
              onDragStart = { o -> dragStart = o; dragNow = o },
              onDrag = { change, _ -> dragNow = change.position },
              onDragEnd = {
                val a = dragStart; val b = dragNow
                if (a != null && b != null) {
                  val w = size.width.toFloat()
                  val dx = (a.x - b.x) / w; val dy = (a.y - b.y) / w
                  val len = sqrt(dx * dx + dy * dy)
                  if (len > 0.03f) shoot(Offset(dx / len, dy / len), (len * 9f).coerceIn(0.6f, 4.2f))
                }
                dragStart = null; dragNow = null
              }
            )
          }
        }
    ) {
      val w = size.width
      val rim = w * 0.05f
      drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF8B5A2B), Color(0xFF3B2410))), Offset(-rim, -rim), Size(w + rim * 2, w * TH + rim * 2), androidx.compose.ui.geometry.CornerRadius(26f))
      drawRect(Brush.radialGradient(listOf(Color(0xFF1FA35C), Color(0xFF0B5C34), Color(0xFF06351F)), center = Offset(w * 0.5f, w * TH * 0.45f), radius = w * 1.2f), Offset.Zero, Size(w, w * TH))
      for (p in POCKETS) drawCircle(Color(0xFF05050A), PR * w, Offset(p.x * w, p.y * w))
      for (i in 9 downTo 0) {
        if (alive[i] != 1) continue
        val cx = px[i] * w; val cy = py[i] * w; val r = BR * w
        drawOval(Color(0x66000000), Offset(cx - r * 0.9f, cy + r * 0.55f), Size(r * 1.9f, r * 0.9f))
        drawCircle(Brush.radialGradient(listOf(Color.White, BALL_COLORS[i], Color(0xFF111111)), center = Offset(cx - r * 0.35f, cy - r * 0.35f), radius = r * 1.5f), r, Offset(cx, cy))
        if (i == 9) drawRect(Color(0xFFF5F5F5), Offset(cx - r * 0.95f, cy - r * 0.25f), Size(r * 1.9f, r * 0.5f))
        drawCircle(Color(0xAAFFFFFF), r * 0.2f, Offset(cx - r * 0.4f, cy - r * 0.45f))
      }
      val a = dragStart; val b = dragNow
      if (a != null && b != null && alive[0] == 1) {
        val dx = a.x - b.x; val dy = a.y - b.y
        val len = sqrt(dx * dx + dy * dy)
        if (len > 4f) {
          val ux = dx / len; val uy = dy / len
          val cx = px[0] * w; val cy = py[0] * w
          drawLine(Color(0xAAFFFFFF), Offset(cx, cy), Offset(cx + ux * w * 0.5f, cy + uy * w * 0.5f), strokeWidth = 3f)
          drawLine(Color(0xFFD2A06A), Offset(cx - ux * (BR * w + 6f), cy - uy * (BR * w + 6f)), Offset(cx - ux * (BR * w + 6f + len), cy - uy * (BR * w + 6f + len)), strokeWidth = 9f)
        }
      }
    }
  }
  if (winner >= 0) {
    Spacer(Modifier.height(8.dp))
    ResultBanner(msg)
    GradientButton("Revancha", modifier = Modifier.fillMaxWidth(), onClick = {
      val (xs, ys) = rackPositions()
      for (i in 0 until 10) { px[i] = xs[i]; py[i] = ys[i]; alive[i] = 1 }
      publish(0, -1, "¡Revancha!")
    })
  }
}
