package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.BOWL_BALLS
import com.example.model.Cat
import com.example.model.Cosmetics
import com.example.model.GIFTS
import com.example.model.LocalWallet
import com.example.model.ShopItem
import com.example.model.entryFx
import com.example.model.shopItemsOf
import com.example.ui.components.GlassCard
import com.example.ui.components.GradientButton
import com.example.ui.components.SoftButton
import com.example.ui.screens.GiftArt
import com.example.ui.theme.GoldYellow
import kotlinx.coroutines.delay

private val SHOP_TABS = listOf("🎁 Regalos", "🎱 Pool", "🏎️ Carreras", "🎳 Bowling", "💬 Chat", "🚪 Entradas")

/** Tienda de la sala: ítems para los juegos, el chat y las entradas. Se paga con fichas locales. */
@Composable
fun RoomShopScreen(vm: CoupleViewModel, social: RoomSocial) {
  val ctx = LocalContext.current
  val coins by vm.myCredits.collectAsState()
  var tab by remember { mutableStateOf(0) }
  var tick by remember { mutableStateOf(0) }
  var info by remember { mutableStateOf("") }
  var daily by remember(tick) { mutableStateOf(LocalWallet.dailyAvailable(ctx)) }
  LaunchedEffect(info) { if (info.isNotEmpty()) { delay(2400); info = "" } }

  Column(Modifier.fillMaxSize()) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text("🛒 Tienda KOMBO", color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
        Text("Tus compras se guardan en este teléfono", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
      }
      Text("🪙 $coins", color = GoldYellow, fontWeight = FontWeight.Black, fontSize = 18.sp)
    }
    if (daily) {
      GradientButton("🎁 Reclamar bono diario +${LocalWallet.DAILY_BONUS} 🪙", onClick = {
        if (LocalWallet.claimDaily(ctx) != null) { vm.refreshWallet(); info = "¡Bono diario reclamado! 🎉"; tick++ }
      }, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), height = 44.dp)
      Spacer(Modifier.height(6.dp))
    }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      SHOP_TABS.forEachIndexed { i, t ->
        Text(
          t, color = if (tab == i) Color.Black else Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp,
          modifier = Modifier.clip(RoundedCornerShape(50)).background(if (tab == i) GoldYellow else Color(0x33FFFFFF)).clickable { tab = i }.padding(horizontal = 14.dp, vertical = 7.dp)
        )
      }
    }
    if (info.isNotEmpty()) Text(info, color = GoldYellow, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(10.dp)) {
      when (tab) {
        0 -> {
          Text("Los regalos se envían desde el chat (botón 🎁) y todos en la sala los ven con efecto. Quien recibe uno gana fichas.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
          Spacer(Modifier.height(8.dp))
          GIFTS.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              row.forEach { g ->
                GlassCard(modifier = Modifier.weight(1f), corner = 18.dp) {
                  Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { GiftArt(g, 72.dp) }
                  Text(g.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                  Text("${g.price} 🪙", color = GoldYellow, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
              }
              if (row.size == 1) Spacer(Modifier.weight(1f))
            }
          }
        }
        1 -> {
          SectionTitle("Tacos"); ItemList(vm, social, Cat.CUE, tick, { tick++ }) { info = it }
          SectionTitle("Paños de mesa"); ItemList(vm, social, Cat.FELT, tick, { tick++ }) { info = it }
        }
        2 -> { SectionTitle("Autos (los ven todos en la carrera)"); ItemList(vm, social, Cat.CAR, tick, { tick++ }) { info = it } }
        3 -> {
          SectionTitle("Bolas de bowling")
          val owned = remember(tick) { Cosmetics.ownedBalls(ctx) }
          val sel = remember(tick) { Cosmetics.selectedBall(ctx) }
          BOWL_BALLS.forEach { b ->
            GlassCard(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), corner = 18.dp, highlight = b.id == sel) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(Brush.radialGradient(listOf(b.hi, b.mid, b.lo))))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                  Text(b.name, color = Color.White, fontWeight = FontWeight.Bold)
                  Text(if (b.price == 0L) "Gratis" else "${b.price} 🪙", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
                if (b.id in owned) {
                  if (b.id == sel) Text("✔ En uso", color = GoldYellow, fontWeight = FontWeight.Bold)
                  else SoftButton("Usar", onClick = { Cosmetics.selectBall(ctx, b.id); tick++ })
                } else GradientButton("Comprar", onClick = {
                  if (vm.trySpend(b.price)) { Cosmetics.markBallOwned(ctx, b.id); Cosmetics.selectBall(ctx, b.id); tick++; info = "¡${b.name} es tuya! 🎳" }
                  else info = "No te alcanzan las fichas 😢"
                }, height = 40.dp)
              }
            }
          }
        }
        4 -> {
          SectionTitle("Burbujas del chat"); ItemList(vm, social, Cat.BUBBLE, tick, { tick++ }) { info = it }
          SectionTitle("Color del nombre"); ItemList(vm, social, Cat.NAME, tick, { tick++ }) { info = it }
        }
        else -> {
          SectionTitle("Efecto al entrar a la sala (probalos con 👁️)")
          ItemList(vm, social, Cat.ENTRY, tick, { tick++ }, preview = true) { info = it }
        }
      }
    }
  }
}

@Composable
private fun SectionTitle(t: String) {
  Text(t, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp, bottom = 6.dp))
}

@Composable
private fun ItemList(vm: CoupleViewModel, social: RoomSocial, cat: String, tick: Int, bump: () -> Unit, preview: Boolean = false, say: (String) -> Unit) {
  val ctx = LocalContext.current
  val owned = remember(tick) { LocalWallet.owned(ctx) }
  val eq = remember(tick) { LocalWallet.equipped(ctx, cat) }
  shopItemsOf(cat).forEach { it0 ->
    val it: ShopItem = it0
    GlassCard(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), corner = 18.dp, highlight = it.id == eq) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          Modifier.size(46.dp).clip(CircleShape).background(Brush.linearGradient(listOf(it.c1, it.c2))),
          contentAlignment = Alignment.Center
        ) { Text(it.emoji, fontSize = 22.sp) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
          Text(it.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
          Text(it.desc, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
          if (it.id !in owned) Text("${it.price} 🪙", color = GoldYellow, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        if (preview && entryFx(it.id).isNotBlank()) {
          Text("👁️", fontSize = 20.sp, modifier = Modifier.clickable { social.preview(entryFx(it.id), "${vm.roomDisplayName()} entró a la sala") }.padding(6.dp))
        }
        if (it.id in owned) {
          if (it.id == eq) Text("✔ En uso", color = GoldYellow, fontWeight = FontWeight.Bold, fontSize = 12.sp)
          else SoftButton("Usar", onClick = { LocalWallet.equip(ctx, cat, it.id); bump() })
        } else GradientButton("Comprar", onClick = {
          if (vm.trySpend(it.price)) { LocalWallet.own(ctx, it.id); LocalWallet.equip(ctx, cat, it.id); bump(); say("¡${it.name} es tuyo! ✨") }
          else say("No te alcanzan las fichas 😢")
        }, height = 40.dp)
      }
    }
  }
}
