package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.GlassCard
import com.example.ui.components.GradientButton
import com.example.ui.components.SoftButton
import com.example.ui.theme.GoldYellow
import com.google.firebase.firestore.FieldValue

// Orden real de la ruleta europea.
private val WHEEL_ORDER = listOf(0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23, 10, 5, 24, 16, 33, 1, 20, 14, 31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26)
private val REDS = setOf(1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36)

private fun numColor(n: Int): Color = when {
  n == 0 -> Color(0xFF1B8F4B)
  REDS.contains(n) -> Color(0xFFD32F2F)
  else -> Color(0xFF16161D)
}

private fun betWins(bet: String, r: Int): Int = when {
  bet == "R" -> if (r != 0 && REDS.contains(r)) 1 else 0
  bet == "N" -> if (r != 0 && !REDS.contains(r)) 1 else 0
  bet == "P" -> if (r != 0 && r % 2 == 0) 1 else 0
  bet == "I" -> if (r % 2 == 1) 1 else 0
  bet.startsWith("n:") -> if (bet.substring(2).toIntOrNull() == r) 5 else 0
  else -> 0
}

private fun betLabel(b: String): String = when {
  b == "R" -> "🔴 Rojo"
  b == "N" -> "⚫ Negro"
  b == "P" -> "Par"
  b == "I" -> "Impar"
  b.startsWith("n:") -> "Número ${b.substring(2)}"
  else -> "—"
}

@Suppress("UNCHECKED_CAST")
@Composable
internal fun RouletteGame(c: GameCtx) {
  val flow = remember { c.vm.gameState("roul") }
  val st by flow.collectAsState(initial = null)
  val spinId = (st?.get("spinId") as? Number)?.toLong() ?: 0L
  val result = (st?.get("res") as? Number)?.toInt() ?: -1
  val bots = (st?.get("bots") as? Number)?.toInt() ?: 0
  val pts = (st?.get("pts") as? Map<String, Any?>) ?: emptyMap()
  val bets = (st?.get("bets") as? Map<String, Any?>) ?: emptyMap()
  val rot = remember { Animatable(0f) }
  var seen by remember { mutableStateOf<Long?>(null) }
  var showRes by remember { mutableStateOf(false) }
  var myBet by remember { mutableStateOf("R") }
  var number by remember { mutableStateOf(17) }
  val seg = 360f / 37f

  LaunchedEffect(spinId) {
    if (result < 0) return@LaunchedEffect
    val idx = WHEEL_ORDER.indexOf(result)
    val finalMod = (360f - (idx * seg + seg / 2f)) % 360f
    if (seen == null) {
      rot.snapTo(finalMod); showRes = true
    } else if (seen != spinId) {
      showRes = false
      val cur = rot.value % 360f
      rot.snapTo(cur)
      rot.animateTo(cur + 360f * 6 + ((finalMod - cur + 360f) % 360f), tween(4200, easing = FastOutSlowInEasing))
      showRes = true
      c.vm.gameSfx(2)
    }
    seen = spinId
  }

  fun spin() {
    val r = (0..36).random()
    val allBets = mutableMapOf<String, String>()
    allBets[c.me] = if (myBet == "n") "n:$number" else myBet
    val fields = mutableMapOf<String, Any?>("spinId" to System.currentTimeMillis(), "res" to r, "bots" to bots)
    // La apuesta de tu pareja se toma de lo último que eligió; si no eligió, es al rojo.
    val partnerBet = (bets[c.otherUid] as? String) ?: "R"
    allBets[c.otherUid] = partnerBet
    for (b in 1..bots) allBets["bot$b"] = listOf("R", "N", "P", "I", "n:${(0..36).random()}").random()
    for ((who, bet) in allBets) {
      val w = betWins(bet, r)
      if (w > 0) fields["pts.$who"] = FieldValue.increment(w.toLong())
    }
    fields["bets"] = allBets
    c.vm.patchGame("roul", fields)
    c.vm.gameSfx(0)
  }

  GlassCard(modifier = Modifier.fillMaxWidth(), highlight = true) {
    Text("🎡 Ruleta europea 3D", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
    Text("Elige tu apuesta: color, par/impar o un número. Acertar color o paridad suma 1 punto; acertar el número suma 5. Sin dinero: solo puntos.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
  }
  Spacer(Modifier.height(8.dp))
  Box(modifier = Modifier.fillMaxWidth().aspectRatio(1.15f).graphicsLayer {
    rotationX = 52f
    cameraDistance = 16f * density
    transformOrigin = TransformOrigin(0.5f, 0.85f)
  }) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val cx = size.width / 2f
      val cy = size.height / 2f
      val r = size.minDimension * 0.46f
      drawCircle(Color(0x66000000), r * 1.06f, Offset(cx, cy + r * 0.10f))
      drawCircle(Brush.radialGradient(listOf(Color(0xFF8B5A2B), Color(0xFF3B2410)), center = Offset(cx, cy), radius = r * 1.1f), r * 1.08f, Offset(cx, cy))
      val rotRad = rot.value
      for (i in WHEEL_ORDER.indices) {
        val start = -90f + rotRad + i * seg
        drawArc(numColor(WHEEL_ORDER[i]), start, seg, true, Offset(cx - r, cy - r), Size(r * 2, r * 2))
      }
      drawCircle(Color(0x33FFFFFF), r, Offset(cx, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f))
      drawCircle(Brush.radialGradient(listOf(Color(0xFFF5E6A8), Color(0xFFB8860B), Color(0xFF5A4300)), center = Offset(cx - r * 0.1f, cy - r * 0.1f), radius = r * 0.5f), r * 0.45f, Offset(cx, cy))
      drawCircle(Color(0xFFFFF4CC), r * 0.08f, Offset(cx, cy))
      // bola
      drawCircle(Color.White, r * 0.055f, Offset(cx, cy - r * 0.86f))
      drawCircle(Color(0x44000000), r * 0.055f, Offset(cx + 3f, cy - r * 0.86f + 5f))
    }
  }
  if (showRes && result >= 0) {
    ResultBanner("${result} ${if (result == 0) "🟢" else if (REDS.contains(result)) "🔴" else "⚫"}")
  }
  Spacer(Modifier.height(6.dp))
  Text("Tu apuesta: ${if (myBet == "n") "Número $number" else betLabel(myBet)}", color = Color.White, fontWeight = FontWeight.Bold)
  Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    listOf("R" to "🔴 Rojo", "N" to "⚫ Negro", "P" to "Par", "I" to "Impar", "n" to "Número").forEach { (k, label) ->
      if (myBet == k) GradientButton(label, onClick = { myBet = k }, height = 40.dp) else SoftButton(label, onClick = { myBet = k })
    }
  }
  if (myBet == "n") {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
      SoftButton("−", onClick = { number = (number + 36) % 37 })
      Text("$number", color = GoldYellow, fontWeight = FontWeight.Black, fontSize = 24.sp, textAlign = TextAlign.Center)
      SoftButton("+", onClick = { number = (number + 1) % 37 })
    }
  }
  // La apuesta propia queda registrada para que tu pareja la vea.
  LaunchedEffect(myBet, number) {
    c.vm.patchGame("roul", mapOf("bets.${c.me}" to (if (myBet == "n") "n:$number" else myBet)))
  }
  Text("Bots en la mesa", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
  Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    listOf(0, 1, 2).forEach { n ->
      if (bots == n) GradientButton("$n", onClick = { c.vm.patchGame("roul", mapOf("bots" to n)) }, height = 38.dp) else SoftButton("$n bot", onClick = { c.vm.patchGame("roul", mapOf("bots" to n)) })
    }
  }
  Spacer(Modifier.height(8.dp))
  GradientButton("🎡 GIRAR RULETA", onClick = { spin() }, enabled = !rot.isRunning, modifier = Modifier.fillMaxWidth(), height = 56.dp)
  Spacer(Modifier.height(8.dp))
  GlassCard(modifier = Modifier.fillMaxWidth(), corner = 16.dp) {
    Text("Marcador", color = GoldYellow, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    Text("${c.nameOf(c.me)}: ${(pts[c.me] as? Number)?.toInt() ?: 0} pts · apuesta ${betLabel((bets[c.me] as? String) ?: "-")}", color = Color.White, fontSize = 13.sp)
    Text("${c.partnerName}: ${(pts[c.otherUid] as? Number)?.toInt() ?: 0} pts · apuesta ${betLabel((bets[c.otherUid] as? String) ?: "-")}", color = Color.White, fontSize = 13.sp)
    for (b in 1..bots) Text("🤖 Bot $b: ${(pts["bot$b"] as? Number)?.toInt() ?: 0} pts · apuesta ${betLabel((bets["bot$b"] as? String) ?: "-")}", color = Color.White, fontSize = 13.sp)
  }
}
