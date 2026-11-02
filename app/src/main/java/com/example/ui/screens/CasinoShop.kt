package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.GIFTS
import com.example.model.GiftDef
import com.example.model.RETO_CATEGORIAS
import com.example.ui.CoupleViewModel
import com.example.ui.components.GlassCard
import com.example.ui.components.GradientButton
import com.example.ui.components.SoftButton
import com.example.ui.theme.GoldYellow
import com.example.ui.theme.LocalDuoPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

// =====================================================================
// Billetera
// =====================================================================
@Composable
fun WalletBar(vm: CoupleViewModel) {
  val mine by vm.myCredits.collectAsState()
  GlassCard(modifier = Modifier.fillMaxWidth(), highlight = true, corner = 16.dp, contentPadding = PaddingValues(10.dp)) {
    Text("🪙 Mis coins", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
    PopText("$mine", 22.sp, Modifier.align(Alignment.Start))
  }
}

// =====================================================================
// Tragamonedas
// =====================================================================
private val SLOT_SYMBOLS = listOf("🍒" to 28, "🍋" to 24, "🔔" to 18, "⭐" to 12, "❤️" to 8, "💎" to 6, "7️⃣" to 4)
private val SLOT_PAY = mapOf("🍒" to 5, "🍋" to 8, "🔔" to 12, "⭐" to 20, "❤️" to 30, "💎" to 50, "7️⃣" to 100)

private fun randomSymbol(): String {
  var r = (0 until 100).random()
  for ((sym, w) in SLOT_SYMBOLS) {
    if (r < w) return sym
    r -= w
  }
  return SLOT_SYMBOLS[0].first
}

@Composable
internal fun SlotsGame(c: GameCtx) {
  val credits by c.vm.myCredits.collectAsState()
  val scope = rememberCoroutineScope()
  var reels by remember { mutableStateOf(listOf("🍒", "🔔", "⭐")) }
  var spinning by remember { mutableStateOf(false) }
  var bet by remember { mutableStateOf(10) }
  var message by remember { mutableStateOf("Elige el nivel y gira") }
  var bigWin by remember { mutableStateOf(false) }

  fun spin() {
    if (spinning) return
    if (!c.vm.trySpend(bet.toLong())) { message = "No tienes fichas suficientes 😢"; return }
    spinning = true
    bigWin = false
    message = "¡Girando…!"
    c.vm.gameSfx(0)
    val finals = listOf(randomSymbol(), randomSymbol(), randomSymbol())
    scope.launch {
      val current = reels.toMutableList()
      for (i in 0..2) {
        val steps = 10 + i * 6
        for (k in 0 until steps) {
          current[i] = SLOT_SYMBOLS[(0 until SLOT_SYMBOLS.size).random()].first
          reels = current.toList()
          delay(60)
        }
        current[i] = finals[i]
        reels = current.toList()
        c.vm.gameSfx(0)
      }
      val win: Long = when {
        finals[0] == finals[1] && finals[1] == finals[2] -> bet.toLong() * (SLOT_PAY[finals[0]] ?: 1)
        finals[0] == finals[1] || finals[1] == finals[2] || finals[0] == finals[2] -> bet.toLong() * 12 / 10
        else -> 0L
      }
      if (win > 0) {
        c.vm.addCredits(win)
        c.vm.gameSfx(2)
        message = "🎉 ¡Ganaste! x${win / bet.coerceAtLeast(1)}"
        bigWin = win >= bet * 5
      } else {
        message = "Sin premio… ¡otra vez! 🍀"
      }
      spinning = false
    }
  }

  WalletBar(c.vm)
  Spacer(Modifier.height(12.dp))
  GlassCard(modifier = Modifier.fillMaxWidth(), highlight = true, corner = 26.dp) {
    Row(modifier = Modifier.fillMaxWidth().graphicsLayer { rotationX = 16f; cameraDistance = 14f * density }, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      reels.forEach { sym ->
        Box(
          modifier = Modifier.weight(1f).aspectRatio(0.85f).graphicsLayer { rotationX = if (spinning) 28f else 0f; cameraDistance = 12f * density }.clip(RoundedCornerShape(18.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF0B0616), Color(0xFF2A1646), Color(0xFF0B0616))))
            .border(BorderStroke(2.dp, GoldYellow), RoundedCornerShape(18.dp)),
          contentAlignment = Alignment.Center
        ) { PopText(sym, 46.sp) }
      }
    }
    Spacer(Modifier.height(10.dp))
    Text(message, color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
  }
  if (bigWin) {
    Spacer(Modifier.height(8.dp))
    ResultBanner("🏆 ¡PREMIAZO!")
  }
  Spacer(Modifier.height(12.dp))
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    listOf(10, 50, 100).forEach { b ->
      if (bet == b) GradientButton("$b", onClick = { bet = b }, modifier = Modifier.weight(1f))
      else SoftButton("$b", onClick = { if (!spinning) bet = b }, modifier = Modifier.weight(1f))
    }
  }
  Spacer(Modifier.height(10.dp))
  GradientButton("🎰 GIRAR ($bet 🪙)", onClick = { spin() }, enabled = !spinning && credits >= bet, modifier = Modifier.fillMaxWidth(), height = 60.dp)
  Spacer(Modifier.height(10.dp))
  Text(
    "Tres iguales pagan: 🍒×5 🍋×8 🔔×12 ⭐×20 ❤️×30 💎×50 7️⃣×100. Dos iguales: ×1.2",
    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
  )
}

// =====================================================================
// Lotería
// =====================================================================
private fun lotteryPrize(matches: Int): Long = when (matches) {
  2 -> 100L
  3 -> 300L
  4 -> 1500L
  5 -> 10000L
  else -> 0L
}

@Suppress("UNCHECKED_CAST")
@Composable
internal fun LotteryGame(c: GameCtx) {
  val flow = remember { c.vm.gameState("lottery") }
  val st by flow.collectAsState(initial = null)
  val round = (st?.get("round") as? Number)?.toLong() ?: 0L
  val tickets = (st?.get("tickets") as? Map<String, Any?>) ?: emptyMap()
  val myTicket = (tickets[c.me] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() } ?: emptyList()
  val theirTicket = (tickets[c.otherUid] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() } ?: emptyList()
  val drawn = (st?.get("drawn") as? List<*>)?.mapNotNull { (it as? Number)?.toInt() } ?: emptyList()
  val claimed = (st?.get("claimed") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
  val picks = remember(round) { mutableStateListOf<Int>() }
  var reveal by remember(round) { mutableStateOf(0) }
  var paidRound by remember { mutableStateOf(-1L) }

  LaunchedEffect(drawn.size, round) {
    if (drawn.size == 6) {
      reveal = 0
      for (k in 1..6) { delay(650); reveal = k; c.vm.gameSfx(0) }
      val matches = myTicket.count { drawn.contains(it) }
      val prize = lotteryPrize(matches)
      if (!claimed.contains(c.me) && paidRound != round) {
        paidRound = round
        if (prize > 0) { c.vm.addCredits(prize); c.vm.gameSfx(2) }
        c.vm.patchGame("lottery", mapOf("claimed" to com.google.firebase.firestore.FieldValue.arrayUnion(c.me)))
      }
    }
  }

  WalletBar(c.vm)
  Spacer(Modifier.height(10.dp))
  GlassCard(modifier = Modifier.fillMaxWidth(), highlight = true) {
    Text("🎟️ Lotería de pareja", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
    Text(
      "Elige 5 números del 1 al 30. Boleto 50 🪙. Premios: 2 aciertos 100, 3 → 300, 4 → 1.500, 5 → 10.000 🪙.",
      color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp
    )
  }
  Spacer(Modifier.height(10.dp))

  if (drawn.size < 6) {
    if (myTicket.isEmpty()) {
      for (r in 0 until 5) {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          for (col in 0 until 6) {
            val n = r * 6 + col + 1
            val on = picks.contains(n)
            Box(
              modifier = Modifier.weight(1f).aspectRatio(1f).clip(CircleShape)
                .background(if (on) LocalDuoPalette.current.accentDiagonal else Brush.linearGradient(listOf(Color(0x22FFFFFF), Color(0x11FFFFFF))))
                .border(BorderStroke(1.dp, Color(0x44FFFFFF)), CircleShape)
                .clickable { if (on) picks.remove(n) else if (picks.size < 5) picks.add(n) },
              contentAlignment = Alignment.Center
            ) { Text("$n", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
          }
        }
      }
      GradientButton(
        "Comprar boleto (50 🪙) · ${picks.size}/5", modifier = Modifier.fillMaxWidth(), enabled = picks.size == 5,
        onClick = {
          if (c.vm.trySpend(50)) {
            c.vm.patchGame("lottery", mapOf("tickets.${c.me}" to picks.sorted(), "round" to round))
            c.vm.gameSfx(1)
          }
        }
      )
    } else {
      GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text("Tu boleto: ${myTicket.joinToString(" · ")}", color = Color.White, fontWeight = FontWeight.Bold)
        Text(
          if (theirTicket.isEmpty()) "${c.partnerName} aún no compró su boleto." else "${c.partnerName} ya tiene boleto 🎫",
          color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp
        )
      }
      Spacer(Modifier.height(10.dp))
      GradientButton("🎰 Hacer el sorteo", modifier = Modifier.fillMaxWidth(), onClick = {
        val nums = (1..30).shuffled().take(6)
        c.vm.patchGame("lottery", mapOf("drawn" to nums, "round" to round, "claimed" to emptyList<String>()))
        c.vm.gameSfx(2)
      })
    }
  } else {
    GlassCard(modifier = Modifier.fillMaxWidth(), highlight = true) {
      Text("Números sorteados", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
      Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (k in 0 until 6) {
          Box(
            modifier = Modifier.weight(1f).aspectRatio(1f).clip(CircleShape)
              .background(if (k < reveal) Brush.linearGradient(listOf(GoldYellow, Color(0xFFFF8A5B))) else Brush.linearGradient(listOf(Color(0x33FFFFFF), Color(0x11FFFFFF)))),
            contentAlignment = Alignment.Center
          ) { if (k < reveal) PopText("${drawn[k]}", 18.sp) else Text("?", color = Color.White) }
        }
      }
      val my = myTicket.count { drawn.contains(it) }
      val their = theirTicket.count { drawn.contains(it) }
      if (reveal == 6) {
        Text("Tu boleto: ${myTicket.joinToString(" · ")} → $my aciertos · premio ${lotteryPrize(my)} pts", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        if (theirTicket.isNotEmpty()) Text("${c.partnerName}: $their aciertos · premio ${lotteryPrize(their)}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
      }
    }
    if (reveal == 6) {
      if (myTicket.count { drawn.contains(it) } >= 3) ResultBanner("🏆 ¡Premio gordo!")
      Spacer(Modifier.height(10.dp))
      GradientButton("Nueva ronda", modifier = Modifier.fillMaxWidth(), onClick = {
        c.vm.patchGame("lottery", mapOf("round" to round + 1, "tickets" to emptyMap<String, Any>(), "drawn" to emptyList<Int>(), "claimed" to emptyList<String>()))
      })
    }
  }
}

// =====================================================================
// Arte de regalos y aviso animado
// =====================================================================
@Composable
fun GiftArt(def: GiftDef, size: Dp) {
  val t = rememberInfiniteTransition(label = "gift")
  val ang by t.animateFloat(0f, 360f, infiniteRepeatable(tween(7000, easing = LinearEasing), RepeatMode.Restart), label = "orbit")
  val bob by t.animateFloat(-6f, 6f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "bob")
  Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val r = this.size.minDimension / 2f
      drawCircle(Brush.radialGradient(listOf(def.c1, def.c2)), radius = r * 0.78f)
      drawCircle(Color(0x33FFFFFF), radius = r * 0.78f, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
      for (i in 0 until 7) {
        val a = Math.toRadians((ang + i * 360.0 / 7))
        val rr = r * (0.88f + 0.08f * ((i % 3) - 1))
        drawCircle(Color.White.copy(alpha = 0.85f), radius = r * 0.045f, center = Offset(center.x + (cos(a) * rr).toFloat(), center.y + (sin(a) * rr).toFloat()))
      }
    }
    Text(def.emoji, fontSize = (size.value * 0.46f).sp, modifier = Modifier.graphicsLayer { translationY = bob })
  }
}

@Composable
fun GiftOverlay(vm: CoupleViewModel) {
  val event by vm.giftEvent.collectAsState()
  val partner by vm.partnerDisplayName.collectAsState()
  val ev = event ?: return
  val def = GIFTS.firstOrNull { it.id == ev.giftId } ?: return
  val scale = remember { Animatable(0.1f) }
  LaunchedEffect(ev.t) { scale.snapTo(0.1f); scale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow)) }
  Box(
    modifier = Modifier.fillMaxSize().background(Color(0xE6070311)).clickable { vm.dismissGift() },
    contentAlignment = Alignment.Center
  ) {
    ConfettiBurst(Modifier.fillMaxSize())
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
      Text("🎁 ¡$partner te envió un regalo!", color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp, textAlign = TextAlign.Center)
      Spacer(Modifier.height(18.dp))
      Box(modifier = Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value }) { GiftArt(def, 220.dp) }
      Spacer(Modifier.height(16.dp))
      Text(def.name, color = GoldYellow, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
      Text(def.note, color = Color(0xFFE2D8F5), fontSize = 14.sp, textAlign = TextAlign.Center)
      Spacer(Modifier.height(20.dp))
      GradientButton("💖 Guardar en mi corazón", onClick = { vm.dismissGift() })
    }
  }
}

// =====================================================================
// Tienda: raspadas de retos, regalos y retos recibidos
// =====================================================================
private class ScratchCard(val tier: String, val category: String, val text: String, val bonus: Long)

private fun buildCard(tier: String): ScratchCard {
  val cats = RETO_CATEGORIAS.keys.toList()
  val pool: List<String> = when (tier) {
    "Básica" -> cats.filter { it.contains("Divertidos") || it.contains("Cocina") || it.contains("Música") || it.contains("Bienestar") }
    "Premium" -> cats.filter { !it.contains("Atrevidos") }
    else -> cats
  }
  val cat = pool.random()
  val text = RETO_CATEGORIAS[cat]!!.random()
  val bonus = if (tier == "Épica") listOf(0L, 100L, 300L).random() else 0L
  return ScratchCard(tier, cat, text, bonus)
}

@Composable
private fun ScratchView(card: ScratchCard, onRevealed: () -> Unit) {
  val points = remember(card) { mutableStateListOf<Offset>() }
  val revealed = points.size > 80
  LaunchedEffect(revealed) { if (revealed) onRevealed() }
  Box(
    modifier = Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(20.dp))
      .background(Brush.linearGradient(listOf(Color(0xFF2A1646), Color(0xFF3A1F5E))))
      .border(BorderStroke(2.dp, GoldYellow), RoundedCornerShape(20.dp)),
    contentAlignment = Alignment.Center
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
      Text(card.category, color = GoldYellow, fontWeight = FontWeight.Bold, fontSize = 12.sp)
      Spacer(Modifier.height(6.dp))
      Text(card.text, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, textAlign = TextAlign.Center)
      if (card.bonus > 0) Text("¡Reto especial!", color = GoldYellow, fontSize = 12.sp)
    }
    if (!revealed) {
      Canvas(
        modifier = Modifier.fillMaxSize()
          .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
          .pointerInput(card) {
            detectDragGestures(onDrag = { change, _ -> points.add(change.position) })
          }
      ) {
        drawRect(Brush.linearGradient(listOf(Color(0xFFD9D9E3), Color(0xFF9A9AAE), Color(0xFFF2F2FA))))
        for (p in points) drawCircle(Color.Black, radius = 34f, center = p, blendMode = BlendMode.Clear)
      }
      Text("✨ Raspa aquí ✨", color = Color(0xFF55556A), fontWeight = FontWeight.Bold, modifier = Modifier.alpha(if (points.isEmpty()) 1f else 0f))
    }
  }
}

private fun Modifier.alpha(a: Float): Modifier = this.graphicsLayer { alpha = a }

@Composable
internal fun ShopGame(c: GameCtx) {
  var tab by remember { mutableStateOf(0) }
  var card by remember { mutableStateOf<ScratchCard?>(null) }
  var cardRevealed by remember { mutableStateOf(false) }
  var info by remember { mutableStateOf("") }
  val shopCtx = androidx.compose.ui.platform.LocalContext.current
  var ballTick by remember { mutableStateOf(0) }
  val retos by c.vm.myRetos.collectAsState()

  WalletBar(c.vm)
  Spacer(Modifier.height(10.dp))
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    listOf("🎴 Retos", "🎁 Regalos", "🎳 Juegos", "📬 Míos (${retos.size})").forEachIndexed { i, label ->
      if (tab == i) GradientButton(label, onClick = { tab = i }, modifier = Modifier.weight(1f), height = 44.dp)
      else SoftButton(label, onClick = { tab = i }, modifier = Modifier.weight(1f))
    }
  }
  Spacer(Modifier.height(10.dp))
  if (info.isNotEmpty()) {
    Text(info, color = GoldYellow, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    Spacer(Modifier.height(6.dp))
  }
  when (tab) {
    0 -> {
      val cur = card
      if (cur != null) {
        ScratchView(cur) {
          if (!cardRevealed) {
            cardRevealed = true
            if (cur.bonus > 0) c.vm.addCredits(cur.bonus)
            c.vm.gameSfx(2)
          }
        }
        Spacer(Modifier.height(8.dp))
        if (cardRevealed) {
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            GradientButton("💌 Enviar a ${c.partnerName}", onClick = { c.vm.sendReto(cur.text); info = "Reto enviado 💌"; card = null }, modifier = Modifier.weight(1f))
            SoftButton("Listo", onClick = { card = null }, modifier = Modifier.weight(0.6f))
          }
        }
        Spacer(Modifier.height(10.dp))
      }
      listOf(Triple("Básica", 30L, "Retos divertidos, de cocina, música y bienestar"),
        Triple("Premium", 80L, "Románticos, a distancia, creativos y foto/video"),
        Triple("Épica", 200L, "¡Todos los retos, incluidos los atrevidos!")).forEach { (tier, price, desc) ->
        GlassCard(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), corner = 18.dp, highlight = tier == "Épica") {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
              Text("🎟️ Raspada $tier", color = Color.White, fontWeight = FontWeight.ExtraBold)
              Text(desc, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            Spacer(Modifier.width(8.dp))
            GradientButton("$price 🪙", onClick = {
              if (c.vm.trySpend(price)) { card = buildCard(tier); cardRevealed = false; info = ""; c.vm.gameSfx(1) }
              else info = "No te alcanzan las fichas. ¡Juega para ganar más! 🎰"
            }, height = 44.dp)
          }
        }
      }
    }
    1 -> {
      GIFTS.chunked(2).forEach { row ->
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          row.forEach { g ->
            GlassCard(modifier = Modifier.weight(1f), corner = 18.dp, contentPadding = PaddingValues(10.dp)) {
              Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { GiftArt(g, 84.dp) }
              Text(g.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
              Spacer(Modifier.height(6.dp))
              GradientButton("${g.price} 🪙 Enviar", onClick = {
                info = if (c.vm.sendGift(g.id)) "¡${g.name} enviado a ${c.partnerName}! 🎁" else "No te alcanzan las fichas 😢"
              }, modifier = Modifier.fillMaxWidth(), height = 40.dp)
            }
          }
          if (row.size == 1) Spacer(Modifier.weight(1f))
        }
      }
    }
    2 -> {
      val owned = remember(ballTick) { com.example.model.Cosmetics.ownedBalls(shopCtx) }
      val sel = remember(ballTick) { com.example.model.Cosmetics.selectedBall(shopCtx) }
      Text("🎳 Bolas de boliche (se guardan en este teléfono)", color = Color.White, fontWeight = FontWeight.ExtraBold)
      Spacer(Modifier.height(6.dp))
      com.example.model.BOWL_BALLS.forEach { b ->
        GlassCard(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), corner = 18.dp, highlight = b.id == sel) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(androidx.compose.foundation.shape.CircleShape)
              .background(androidx.compose.ui.graphics.Brush.radialGradient(listOf(b.hi, b.mid, b.lo))))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
              Text(b.name, color = Color.White, fontWeight = FontWeight.Bold)
              Text(if (b.price == 0L) "Gratis" else "${b.price} 🪙", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            if (b.id in owned) {
              if (b.id == sel) Text("✔ En uso", color = GoldYellow, fontWeight = FontWeight.Bold)
              else SoftButton("Usar", onClick = { com.example.model.Cosmetics.selectBall(shopCtx, b.id); ballTick++ })
            } else GradientButton("Comprar", onClick = {
              if (c.vm.trySpend(b.price)) { com.example.model.Cosmetics.markBallOwned(shopCtx, b.id); com.example.model.Cosmetics.selectBall(shopCtx, b.id); ballTick++; info = "¡${b.name} es tuya! 🎳"; c.vm.gameSfx(1) }
              else info = "No te alcanzan las fichas 😢"
            }, height = 40.dp)
          }
        }
      }
    }
    else -> {
      if (retos.isEmpty()) {
        Text("No tienes retos pendientes. ¡Tu pareja puede enviarte uno desde su tienda!", color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      retos.forEach { r ->
        GlassCard(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), highlight = true) {
          Text("🎴 Reto de ${c.partnerName}", color = GoldYellow, fontWeight = FontWeight.Bold, fontSize = 12.sp)
          Text(r.text, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
          Spacer(Modifier.height(8.dp))
          GradientButton("¡Cumplido! ✅ (+40 🪙)", onClick = { c.vm.completeReto(r.id) }, modifier = Modifier.fillMaxWidth(), height = 44.dp)
        }
      }
    }
  }
}
