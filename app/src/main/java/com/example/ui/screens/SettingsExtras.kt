package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.example.data.SoundPrefs
import com.example.ui.CoupleViewModel
import com.example.ui.components.AvatarView
import com.example.ui.GuestManagement
import com.example.ui.components.GlassCard
import com.example.ui.components.GradientButton
import com.example.ui.components.SoftButton

/** Perfil (nombre y foto, visibles para tu pareja) y sonidos de avisos. */
@Composable
fun ProfileAndSoundSettings(vm: CoupleViewModel) {
  val ctx = LocalContext.current
  val myName by vm.myDisplayName.collectAsState()
  val myPhoto by vm.myPhoto.collectAsState()
  val activeUser by vm.activeUser.collectAsState()
  var nameDraft by remember(myName) { mutableStateOf(myName) }
  var tick by remember { mutableStateOf(0) }
  var pendingKind by remember { mutableStateOf(SoundPrefs.MESSAGE) }

  val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
    if (uri != null) vm.updateMyPhoto(uri)
  }
  @Suppress("DEPRECATION")
  val ringtonePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
    if (res.resultCode == Activity.RESULT_OK) {
      val picked: Uri? = res.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
      SoundPrefs.setRaw(ctx, pendingKind, picked?.toString() ?: "silent")
      tick++
    }
  }

  fun pickSound(kind: String) {
    pendingKind = kind
    val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
      putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, if (kind == SoundPrefs.CALL) RingtoneManager.TYPE_RINGTONE else RingtoneManager.TYPE_NOTIFICATION)
      putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
      putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
      putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Elige el sonido")
      val cur = SoundPrefs.raw(ctx, kind)
      if (cur.isNotEmpty() && cur != "silent") putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(cur) as android.os.Parcelable)
    }
    ringtonePicker.launch(intent)
  }

  GuestManagement(vm)
  Spacer(Modifier.height(14.dp))
  val diag by vm.diagnostics.collectAsState()
  GlassCard(modifier = Modifier.fillMaxWidth()) {
    Text("🩺 Diagnóstico de conexión", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
    Text("Si algo dice “permiso denegado”, pulsa aquí y dime qué línea sale en ERROR.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    Spacer(Modifier.height(8.dp))
    SoftButton("Probar conexión", onClick = { vm.runDiagnostics() }, modifier = Modifier.fillMaxWidth())
    if (diag.isNotBlank()) {
      Spacer(Modifier.height(6.dp))
      Text(diag, color = Color.White, fontSize = 12.sp)
    }
  }
  Spacer(Modifier.height(14.dp))
  GlassCard(modifier = Modifier.fillMaxWidth(), highlight = true) {
    Text("👤 Mi perfil (lo ve tu pareja)", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
      AvatarView(myPhoto, activeUser.avatar, 72.dp)
      Spacer(Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        OutlinedTextField(value = nameDraft, onValueChange = { nameDraft = it.take(24) }, label = { Text("Mi nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
      }
    }
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
      SoftButton("📷 Cambiar foto", onClick = { photoPicker.launch("image/*") }, modifier = Modifier.weight(1f))
      GradientButton("Guardar nombre", onClick = { vm.updateMyName(nameDraft) }, enabled = nameDraft.isNotBlank() && nameDraft.trim() != myName, modifier = Modifier.weight(1f))
    }
  }
  val swap by vm.swapAvatars.collectAsState()
  Spacer(Modifier.height(8.dp))
  Row(verticalAlignment = Alignment.CenterVertically) {
    Text("Si ves las fotos intercambiadas (la tuya y la de tu pareja), activa esto", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.weight(1f))
    Switch(checked = swap, onCheckedChange = { vm.setSwapAvatars(it) })
  }
  Spacer(Modifier.height(14.dp))
  GlassCard(modifier = Modifier.fillMaxWidth()) {
    Text("🔔 Sonidos y avisos", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
    Text("Elige cómo te avisa D-oAmor cuando llega algo de tu pareja.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    listOf(
      Triple(SoundPrefs.MESSAGE, "💬 Mensajes", "Cuando te escribe"),
      Triple(SoundPrefs.AUDIO, "🎙️ Notas de voz", "Cuando te manda un audio"),
      Triple(SoundPrefs.CALL, "📞 Llamadas", "Tono de llamada entrante")
    ).forEach { (kind, title, sub) ->
      val label = remember(tick) { SoundPrefs.label(ctx, kind) }
      var on by remember(tick) { mutableStateOf(SoundPrefs.enabled(ctx, kind)) }
      Spacer(Modifier.height(12.dp))
      Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
          Text(title, color = Color.White, fontWeight = FontWeight.Bold)
          Text("$sub · $label", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        Switch(checked = on, onCheckedChange = { on = it; SoundPrefs.setEnabled(ctx, kind, it) })
      }
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
        SoftButton("Elegir sonido", onClick = { pickSound(kind) }, modifier = Modifier.weight(1f))
        SoftButton("▶ Probar", onClick = { SoundPrefs.play(ctx, kind) }, modifier = Modifier.weight(1f))
      }
    }
  }
}
