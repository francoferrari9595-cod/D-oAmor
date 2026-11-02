package com.example.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Cat
import com.example.model.FX_LIST
import com.example.model.GIFTS
import com.example.model.RoomMsg
import com.example.model.SHOP_ITEMS
import com.example.model.shopItem
import com.example.ui.screens.GiftArt
import com.example.ui.theme.GoldYellow
import kotlinx.coroutines.delay

// ═══════════════ Chat de la sala: burbujas con estilo, efectos y regalos ═══════════════

private val CHAT_EMOJIS = listOf(
  "😀", "😂", "🤣", "😍", "😎", "🥳", "😭", "😡", "🤔", "👍", "👏", "🙌", "🔥", "💯", "🎉", "❤️",
  "💀", "😈", "🤯", "😴", "🍀", "🏆", "🎱", "🏎️", "🎳", "🎯", "🃏", "🎲", "☕", "🍕"
)

private fun avatarColor(uid: String): Color {
  val pal = listOf(Color(0xFFFF4D8D), Color(0xFF37E6FF), Color(0xFF7CFF6B), Color(0xFFFFD60A), Color(0xFFB388FF), Color(0xFFFF9500), Color(0xFF00BFA5), Color(0xFFFF6D00))
  return pal[(uid.hashCode() and 0x7fffffff) % pal.size]
}

private fun timeLabel(ms: Long): String {
  if (ms <= 0L) return ""
  return java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(ms))
}

@Composable
private fun StyledName(name: String, nameColId: String, size: Int = 12) {
  val item = shopItem(nameColId)
  val style = if (item == null || item.id == "nm_white") TextStyle(color = Color(0xFFE2D8F5), fontSize = size.sp, fontWeight = FontWeight.Bold)
  else if (item.id == "nm_rainbow") TextStyle(
    brush = Brush.horizontalGradient(listOf(Color(0xFFFF5E5E), Color(0xFFFFD166), Color(0xFF7CFF6B), Color(0xFF37E6FF), Color(0xFFB388FF))),
    fontSize = size.sp, fontWeight = FontWeight.Black
  )
  else TextStyle(brush = Brush.horizontalGradient(listOf(item.c1, item.c2)), fontSize = size.sp, fontWeight = FontWeight.Black)
  Text(name, style = style, maxLines = 1)
}

@Composable
private fun Avatar(uid: String, name: String, size: Int = 34, onClick: (() -> Unit)? = null) {
  Box(
    Modifier.size(size.dp).clip(CircleShape).background(Brush.linearGradient(listOf(avatarColor(uid), avatarColor(uid + "x").copy(alpha = 0.7f))))
      .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
    contentAlignment = Alignment.Center
  ) { Text(name.trim().take(1).uppercase().ifBlank { "?" }, color = Color.White, fontWeight = FontWeight.Black, fontSize = (size * 0.45f).sp) }
}

@Composable
private fun ChatBubble(m: RoomMsg, mine: Boolean, onAvatar: (RoomMsg) -> Unit = {}, onDouble: () -> Unit) {
  val appCtx = androidx.compose.ui.platform.LocalContext.current.applicationContext
  val scale = remember { com.example.model.RoomPrefs.textScale(appCtx) }
  val fresh = System.currentTimeMillis() - m.t < 4_000L
  val a = remember(m.id) { Animatable(if (fresh) 0f else 1f) }
  LaunchedEffect(m.id) { if (fresh) a.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 320f)) }
  val bub = shopItem(m.bubble) ?: SHOP_ITEMS.first { it.cat == Cat.BUBBLE }
  val brush = Brush.linearGradient(listOf(bub.c1, bub.c2))
  val shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = if (mine) 18.dp else 4.dp, bottomEnd = if (mine) 4.dp else 18.dp)
  Row(
    Modifier.fillMaxWidth().padding(vertical = 3.dp, horizontal = 8.dp)
      .graphicsLayer { alpha = a.value.coerceIn(0f, 1f); translationY = (1f - a.value) * 40f; scaleX = 0.92f + 0.08f * a.value; scaleY = 0.92f + 0.08f * a.value },
    horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.Bottom
  ) {
    if (!mine) { Avatar(m.uid, m.name, onClick = { onAvatar(m) }); Spacer(Modifier.width(6.dp)) }
    Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start, modifier = Modifier.weight(1f, fill = false)) {
      if (!mine) StyledName(m.name, m.nameCol)
      Box(
        Modifier.clip(shape).background(brush).pointerInput(m.id) { detectTapGestures(onDoubleTap = { onDouble() }) }
          .padding(horizontal = 12.dp, vertical = 8.dp)
      
      ) { Text(m.text, color = Color.White, fontSize = (15f * scale).sp) }
      Text(timeLabel(m.t), color = Color(0x88FFFFFF), fontSize = 9.sp, modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 1.dp))
    }
    if (mine) { Spacer(Modifier.width(6.dp)); Avatar(m.uid, m.name) }
  }
}

@Composable
private fun GiftBubble(m: RoomMsg) {
  val g = GIFTS.firstOrNull { it.id == m.gift } ?: return
  val t = rememberInfiniteTransition(label = "giftGlow")
  val glow by t.animateFloat(0.35f, 0.95f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "glow")
  val dest = if (m.to.isBlank() || m.to == "all") "para toda la sala" else "para ${m.toName}"
  Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 5.dp), contentAlignment = Alignment.Center) {
    Row(
      Modifier.clip(RoundedCornerShape(20.dp)).background(Brush.horizontalGradient(listOf(g.c2.copy(alpha = 0.85f), g.c1.copy(alpha = 0.35f))))
        .border(2.dp, GoldYellow.copy(alpha = glow), RoundedCornerShape(20.dp)).padding(10.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      GiftArt(g, 64.dp)
      Spacer(Modifier.width(10.dp))
      Column {
        Text("🎁 ${m.name} envió", color = Color(0xFFE2D8F5), fontSize = 11.sp)
        Text(g.name, color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
        Text(dest, color = GoldYellow, fontSize = 12.sp, fontWeight = FontWeight.Bold)
      }
    }
  }
}

@Composable
private fun SystemPill(text: String, c1: Color, c2: Color) {
  Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
    Text(
      text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
      modifier = Modifier.clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(listOf(c1.copy(alpha = 0.75f), c2.copy(alpha = 0.75f)))).padding(horizontal = 14.dp, vertical = 5.dp)
    )
  }
}

@Composable
fun RoomChatScreen(vm: CoupleViewModel, social: RoomSocial) {
  val me = vm.myUid() ?: ""
  val coins by vm.myCredits.collectAsState()
  val names by vm.roomNames.collectAsState()
  val guests by vm.guestUids.collectAsState()
  var input by remember { mutableStateOf("") }
  var showEmoji by remember { mutableStateOf(false) }
  var showFx by remember { mutableStateOf(false) }
  var showGift by remember { mutableStateOf(false) }
  var giftTo by remember { mutableStateOf("all") }
  var giftToName by remember { mutableStateOf("") }
  val listState = rememberLazyListState()
  val msgs = social.messages

  LaunchedEffect(msgs.size) { if (msgs.isNotEmpty()) listState.animateScrollToItem(msgs.size - 1) }
  LaunchedEffect(social.toast) { if (social.toast.isNotEmpty()) { delay(2600); social.toast = "" } }

  Column(Modifier.fillMaxSize()) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text("💬 Chat de la sala", color = Color.White, fontWeight = FontWeight.Black, fontSize = 17.sp)
        Text("${guests.size.coerceAtLeast(1)} conectados · doble toque en un mensaje = ❤️ para todos", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
      }
      Text("🪙 $coins", color = GoldYellow, fontWeight = FontWeight.Black, fontSize = 15.sp)
    }
    RoomCallPanel(vm)
    Box(Modifier.weight(1f).fillMaxWidth()) {
      if (msgs.isEmpty()) {
        Text("Todavía no hay mensajes. ¡Rompé el hielo! 👋", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.Center))
      }
      LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        items(msgs, key = { it.id }) { m ->
          when (m.kind) {
            "gift" -> GiftBubble(m)
            "fx" -> {
              val fx = FX_LIST.firstOrNull { it.id == m.fx }
              if (fx != null) SystemPill("${fx.emoji} ${m.name} lanzó ${fx.name}", Color(0xFF5B2BD6), Color(0xFFFF4D8D))
            }
            "join" -> {
              val special = m.fx.isNotBlank()
              SystemPill(if (special) "✨ ${m.name} hizo su gran entrada" else "👋 ${m.name} entró a la sala", if (special) Color(0xFFB8741A) else Color(0xFF3A2F5E), if (special) Color(0xFFFF4D8D) else Color(0xFF2A1F4A))
            }
            "call" -> SystemPill("📞 ${m.text}", Color(0xFF0B5C7A), Color(0xFF1B8A4B))
            else -> ChatBubble(m, m.uid == me, onAvatar = { who -> giftTo = who.uid; giftToName = who.name; showGift = true }) { social.sendFx("hearts") }
          }
        }
      }
      if (social.toast.isNotEmpty()) {
        Text(
          social.toast, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp,
          modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp).clip(RoundedCornerShape(50)).background(Color(0xCC000000)).padding(horizontal = 14.dp, vertical = 6.dp)
        )
      }
    }
    // paneles desplegables
    if (showEmoji) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).background(Color(0x33000000)).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
      CHAT_EMOJIS.forEach { e -> Text(e, fontSize = 26.sp, modifier = Modifier.clip(CircleShape).clickable { input += e }.padding(5.dp)) }
    }
    if (showFx) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).background(Color(0x33000000)).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      FX_LIST.forEach { fx ->
        Column(
          Modifier.clip(RoundedCornerShape(14.dp)).background(Brush.verticalGradient(listOf(Color(0x44FFFFFF), Color(0x14FFFFFF))))
            .clickable { if (social.sendFx(fx.id)) showFx = false }.padding(horizontal = 12.dp, vertical = 6.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Text(fx.emoji, fontSize = 26.sp)
          Text(fx.name, color = Color.White, fontSize = 10.sp, maxLines = 1)
          Text(if (fx.price == 0L) "Gratis" else "${fx.price} 🪙", color = GoldYellow, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
      }
    }
    Row(Modifier.fillMaxWidth().imePadding().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
      ToolBtn("😊") { showEmoji = !showEmoji; showFx = false }
      ToolBtn("✨") { showFx = !showFx; showEmoji = false }
      ToolBtn("🎁") { giftTo = "all"; giftToName = ""; showGift = true }
      OutlinedTextField(
        value = input, onValueChange = { input = it.take(200) }, modifier = Modifier.weight(1f), singleLine = true,
        placeholder = { Text("Escribí un mensaje…", fontSize = 13.sp) }
      )
      Box(
        Modifier.size(46.dp).clip(CircleShape).background(com.example.ui.theme.LocalDuoPalette.current.accentDiagonal)
          .clickable { if (input.isNotBlank()) { social.sendText(input); input = "" } },
        contentAlignment = Alignment.Center
      ) { Text("➤", color = Color(0xFF04120A), fontSize = 20.sp) }
    }
  }

  if (showGift) {
    var toUid by remember { mutableStateOf(giftTo) }
    val others = (names.filter { it.key != me } + (if (giftTo != "all" && giftTo != me && giftTo !in names.keys) mapOf(giftTo to giftToName.ifBlank { "Jugador" }) else emptyMap()))
    AlertDialog(
      onDismissRequest = { showGift = false },
      title = { Text(if (toUid != "all" && others[toUid] != null) "🎁 Regalo para ${others[toUid]}" else "🎁 Enviar un regalo") },
      text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
          Text("Para quién", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
          Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val all = listOf("all" to "👥 Todos") + others.map { it.key to it.value }
            all.forEach { (uid, nm) ->
              val sel = toUid == uid
              Text(
                nm, color = if (sel) Color.Black else Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(if (sel) GoldYellow else Color(0x33FFFFFF)).clickable { toUid = uid }.padding(horizontal = 12.dp, vertical = 6.dp)
              )
            }
          }
          Spacer(Modifier.height(8.dp))
          Text("Tus fichas: 🪙 $coins", color = GoldYellow, fontWeight = FontWeight.Bold, fontSize = 12.sp)
          Spacer(Modifier.height(6.dp))
          GIFTS.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              row.forEach { g ->
                Column(
                  Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(Brush.verticalGradient(listOf(g.c2.copy(alpha = 0.7f), g.c1.copy(alpha = 0.25f))))
                    .clickable {
                      val nm = if (toUid == "all") "" else (others[toUid] ?: "")
                      if (social.sendGift(g.id, toUid, nm)) showGift = false
                    }.padding(6.dp),
                  horizontalAlignment = Alignment.CenterHorizontally
                ) {
                  Text(g.emoji, fontSize = 28.sp)
                  Text(g.name, color = Color.White, fontSize = 10.sp, maxLines = 1, textAlign = TextAlign.Center)
                  Text("${g.price} 🪙", color = GoldYellow, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
              }
              repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
          }
        }
      },
      confirmButton = { TextButton(onClick = { showGift = false }) { Text("Cerrar") } }
    )
  }
}

@Composable
private fun ToolBtn(emoji: String, onClick: () -> Unit) {
  Box(
    Modifier.size(40.dp).clip(CircleShape).background(Color(0x33FFFFFF)).clickable { onClick() },
    contentAlignment = Alignment.Center
  ) { Text(emoji, fontSize = 20.sp) }
}
