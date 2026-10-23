package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.BuzzerSoundType
import com.example.ui.CoupleViewModel
import com.example.ui.components.GlassCard
import com.example.ui.components.ScreenTitle
import com.example.ui.components.SoftButton
import com.example.ui.components.StatusPill
import com.example.ui.components.HoldToBuzzButton
import com.example.ui.theme.BuzzAmber
import com.example.ui.theme.HeartRed
import com.example.ui.theme.WhatsAppGreen

@Composable
fun BuzzerScreen(viewModel: CoupleViewModel) {
  val partner by viewModel.partnerUser.collectAsState()
  val isHolding by viewModel.isHoldingBuzzSender.collectAsState()
  val soundType by viewModel.soundType.collectAsState()
  val myAllowed by viewModel.myBuzzAllowed.collectAsState()
  val partnerAllowed by viewModel.partnerBuzzAllowed.collectAsState()
  val partnerName = partner.nickname.ifEmpty { partner.name }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 16.dp, vertical = 8.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    ScreenTitle(
      title = "Zumbador",
      subtitle = "Mantén presionado y su teléfono suena y vibra mientras no sueltes",
      modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(10.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      if (partnerAllowed) StatusPill("$partnerName te permite zumbarle", WhatsAppGreen)
      else StatusPill("$partnerName aún no activó el zumbador", BuzzAmber)
    }

    Spacer(Modifier.height(14.dp))

    HoldToBuzzButton(
      isBuzzing = isHolding,
      partnerName = partnerName,
      onBuzzStart = { viewModel.startHoldingBuzz() },
      onBuzzRelease = { _ -> viewModel.stopHoldingBuzz() }
    )

    Text(
      text = if (isHolding) "¡Sonando en el teléfono de $partnerName! 🔊" else "Mantén presionado para hacer sonar a $partnerName",
      style = MaterialTheme.typography.bodyMedium,
      textAlign = TextAlign.Center,
      color = if (isHolding) HeartRed else MaterialTheme.colorScheme.onSurfaceVariant,
      fontWeight = if (isHolding) FontWeight.Bold else FontWeight.Normal,
      modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
    )
    if (!partnerAllowed) {
      Text(
        "Para que suene, $partnerName debe abrir D-oAmor y aceptar el permiso del zumbador.",
        fontSize = 12.sp, color = BuzzAmber, textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 24.dp)
      )
    }

    Spacer(Modifier.height(16.dp))

    // Mi permiso
    GlassCard(modifier = Modifier.fillMaxWidth(), highlight = myAllowed) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
          Text("Permitir zumbidos de mi pareja", fontWeight = FontWeight.Bold, color = Color.White)
          Text(
            if (myAllowed) "Tu teléfono sonará y vibrará cuando $partnerName mantenga el botón."
            else "Desactivado: tu teléfono nunca sonará por un zumbido.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        Spacer(Modifier.width(10.dp))
        Switch(
          checked = myAllowed,
          onCheckedChange = { viewModel.setBuzzAllowed(it) },
          colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
        )
      }
    }

    Spacer(Modifier.height(18.dp))
    Text(
      "Sonido que escucharás",
      style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White,
      modifier = Modifier.align(Alignment.Start)
    )
    Spacer(Modifier.height(8.dp))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
      BuzzerSoundType.values().forEach { sound ->
        val selected = soundType == sound
        GlassCard(
          modifier = Modifier.fillMaxWidth(),
          corner = 18.dp,
          highlight = selected,
          onClick = { viewModel.updateSoundType(sound) },
          contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
              Text(sound.title, fontWeight = FontWeight.Bold, color = Color.White)
              Text(sound.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selected) Text("Activo ✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
          }
        }
      }
    }

    Spacer(Modifier.height(16.dp))
    SoftButton(
      text = "🧪 Probar cómo suena en mi teléfono",
      onClick = { viewModel.triggerSimulatedPartnerBuzz() },
      enabled = myAllowed,
      modifier = Modifier.fillMaxWidth().testTag("simulate_incoming_buzz_btn")
    )
    Spacer(Modifier.height(24.dp))
  }
}
