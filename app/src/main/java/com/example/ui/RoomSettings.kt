package com.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.LocalWallet
import com.example.model.RoomPrefs
import com.example.ui.components.GlassCard
import com.example.ui.components.GradientButton
import com.example.ui.components.SoftButton

/** Ajustes de la sala: nombre, efectos, chat, llamada y salida. */
@Composable
fun RoomSettingsScreen(vm: CoupleViewModel, social: RoomSocial) {
  val ctx = LocalContext.current.applicationContext
  var fx by remember { mutableStateOf(RoomPrefs.fxEnabled(ctx)) }
  var gifts by remember { mutableStateOf(RoomPrefs.giftBanners(ctx)) }
  var joinFx by remember { mutableStateOf(RoomPrefs.joinFx(ctx)) }
  var announce by remember { mutableStateOf(RoomPrefs.announceJoin(ctx)) }
  var scale by remember { mutableStateOf(RoomPrefs.textScale(ctx)) }
  var name by remember { mutableStateOf(vm.roomDisplayName()) }
  var saved by remember { mutableStateOf("") }

  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Text("⚙️ Ajustes de la sala", color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)

    GlassCard(modifier = Modifier.fillMaxWidth()) {
      Text("👤 Tu perfil", color = Color.White, fontWeight = FontWeight.ExtraBold)
      OutlinedTextField(value = name, onValueChange = { name = it.take(24) }, singleLine = true, label = { Text("Nombre en la sala") }, modifier = Modifier.fillMaxWidth())
      Spacer(Modifier.height(6.dp))
      GradientButton("Guardar nombre", onClick = {
        if (name.isNotBlank()) {
          ctx.getSharedPreferences("doamor_prefs", android.content.Context.MODE_PRIVATE).edit().putString("lobby_name", name.trim()).apply()
          saved = "Nombre guardado ✔ (se aplica en tus próximos mensajes)"
        }
      }, modifier = Modifier.fillMaxWidth(), height = 44.dp, enabled = name.isNotBlank())
      if (saved.isNotEmpty()) Text(saved, color = Color(0xFF9CFF7A), fontSize = 12.sp)
    }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
      Text("✨ Efectos", color = Color.White, fontWeight = FontWeight.ExtraBold)
      SwitchRow("Efectos de pantalla", "Confeti, fuego, galaxia…", fx) { fx = it; RoomPrefs.setFx(ctx, it) }
      SwitchRow("Avisos de regalos", "Tarjeta animada al recibir un regalo", gifts) { gifts = it; RoomPrefs.setGiftBanners(ctx, it) }
      SwitchRow("Entradas de los demás", "Ver el efecto de entrada de otros", joinFx) { joinFx = it; RoomPrefs.setJoinFx(ctx, it) }
    }

    com.example.ui.components.SceneThemePicker()

    GlassCard(modifier = Modifier.fillMaxWidth()) {
      Text("💬 Chat", color = Color.White, fontWeight = FontWeight.ExtraBold)
      SwitchRow("Anunciar mi entrada", "Avisar a la sala cuando entro", announce) { announce = it; RoomPrefs.setAnnounceJoin(ctx, it) }
      Text("Tamaño del texto: ${(scale * 100).toInt()}%", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
      Slider(value = scale, onValueChange = { scale = (it * 20).toInt() / 20f }, onValueChangeFinished = { RoomPrefs.setTextScale(ctx, scale) }, valueRange = 0.8f..1.5f)
    }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
      Text("🪙 Fichas", color = Color.White, fontWeight = FontWeight.ExtraBold)
      Text("Tenés ${LocalWallet.coins(ctx)} fichas en este teléfono. Se ganan con el bono diario, jugando y recibiendo regalos.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
      Text("🚪 Sala", color = Color.White, fontWeight = FontWeight.ExtraBold)
      Text(if (vm.isInLobby) "Estás en ${vm.lobbyRoomName().ifBlank { "la sala" }}" else "Sala de invitado", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
      Spacer(Modifier.height(6.dp))
      SoftButton("Salir y ver otras salas", onClick = { vm.leaveGuest() }, modifier = Modifier.fillMaxWidth())
    }
    Spacer(Modifier.height(30.dp))
  }
}

@Composable
private fun SwitchRow(title: String, sub: String, value: Boolean, onChange: (Boolean) -> Unit) {
  Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
    Column(Modifier.weight(1f)) {
      Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
      Text(sub, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
    }
    Switch(checked = value, onCheckedChange = onChange)
  }
}
