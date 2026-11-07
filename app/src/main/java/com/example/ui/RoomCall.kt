package com.example.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Franja de llamada de voz grupal de la sala (hasta 6 personas). */
@Composable
fun RoomCallPanel(vm: CoupleViewModel) {
  val gc = vm.groupCall
  val members by gc.members.collectAsState()
  val inCall by gc.inCall.collectAsState()
  val muted by gc.muted.collectAsState()
  val speaker by gc.speaker.collectAsState()
  val status by gc.status.collectAsState()
  val peers by gc.peerStates.collectAsState()
  val me = vm.myUid() ?: ""
  val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
    if (ok) gc.join() else gc.join() // join() muestra el aviso si falta el permiso
  }
  val others = members.filter { it.uid != me }
  if (!inCall && others.isEmpty() && status.isBlank()) {
    Row(
      Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp).clip(RoundedCornerShape(50)).background(Color(0x22FFFFFF))
        .clickable { ask.launch(Manifest.permission.RECORD_AUDIO) }.padding(horizontal = 14.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically
    ) { Text("📞 Iniciar llamada de voz de la sala", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
    return
  }
  Column(
    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp).clip(RoundedCornerShape(18.dp))
      .background(Brush.horizontalGradient(listOf(Color(0xCC0B5C7A), Color(0xCC1B8A4B)))).padding(10.dp)
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        if (inCall) "📞 En llamada · ${members.size}/6" else "📞 Llamada de sala · ${others.size} conectado(s)",
        color = Color.White, fontWeight = FontWeight.Black, fontSize = 13.sp, modifier = Modifier.weight(1f)
      )
      if (!inCall) Pill("Unirme", Color(0xFFFFD166), Color.Black) { ask.launch(Manifest.permission.RECORD_AUDIO) }
    }
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      members.forEach { m ->
        val st = peers[m.uid].orEmpty()
        Text(
          (if (m.muted) "🔇 " else "🎙️ ") + (if (m.uid == me) "Vos" else m.name) + (if (st.isNotBlank() && m.uid != me) " · $st" else ""),
          color = Color.White, fontSize = 11.sp,
          modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0x33000000)).padding(horizontal = 10.dp, vertical = 4.dp)
        )
      }
    }
    if (status.isNotBlank()) Text(status, color = Color(0xFFFFE9A8), fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
    if (inCall) Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Pill(if (muted) "🔇 Activar mic" else "🎙️ Silenciar", Color(0x55FFFFFF), Color.White) { gc.setMuted(!muted) }
      Pill(if (speaker) "🔊 Altavoz" else "👂 Auricular", Color(0x55FFFFFF), Color.White) { gc.setSpeaker(!speaker) }
      Pill("📴 Salir", Color(0xFFE53950), Color.White) { gc.leave() }
    }
  }
}

@Composable
private fun Pill(text: String, bg: Color, fg: Color, onClick: () -> Unit) {
  Box(Modifier.clip(RoundedCornerShape(50)).background(bg).clickable { onClick() }.padding(horizontal = 12.dp, vertical = 6.dp)) {
    Text(text, color = fg, fontWeight = FontWeight.Bold, fontSize = 12.sp)
  }
}

/** Aviso visible en cualquier pestaña cuando alguien llama a la sala. */
@Composable
fun RoomRingBanner(vm: CoupleViewModel) {
  val gc = vm.groupCall
  val who by gc.ringing.collectAsState()
  val inCall by gc.inCall.collectAsState()
  val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { gc.join() }
  if (who.isBlank() || inCall) return
  Row(
    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp).clip(RoundedCornerShape(18.dp))
      .background(Brush.horizontalGradient(listOf(Color(0xEE0B5C7A), Color(0xEE1B8A4B)))).padding(horizontal = 12.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text("📞 $who está llamando…", color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp, modifier = Modifier.weight(1f))
    Pill("Unirme", Color(0xFFFFD166), Color.Black) { ask.launch(Manifest.permission.RECORD_AUDIO) }
    Spacer(Modifier.width(6.dp))
    Pill("Ignorar", Color(0x55FFFFFF), Color.White) { gc.dismissRing() }
  }
}
