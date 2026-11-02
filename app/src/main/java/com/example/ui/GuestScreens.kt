package com.example.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.AuroraBackground
import com.example.ui.components.GlassCard
import com.example.ui.components.GradientButton
import com.example.ui.components.SiniJossTitle
import com.example.ui.components.SoftButton
import com.example.ui.screens.MinigamesScreen
import com.example.ui.screens.WalkieButton
import com.example.ui.screens.WatchPartyScreen
import com.example.ui.theme.GoldYellow

/** Pantalla para entrar con un código de invitado. */
@Composable
fun GuestJoinScreen(vm: CoupleViewModel, onBack: () -> Unit) {
  var code by remember { mutableStateOf("") }
  var name by remember { mutableStateOf("") }
  val error by vm.guestError.collectAsState()
  androidx.compose.runtime.LaunchedEffect(Unit) { vm.prepareGuestSession() }
  AuroraBackground {
    Column(
      modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),
      verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
    ) {
      SiniJossTitle(30.sp)
      Spacer(Modifier.height(10.dp))
      Text(
        "🎟️ Entrar como invitado\nPide a la pareja un código de invitado. Podrás jugar, ver cine y mandar audios con ellos.",
        color = Color(0xFFE2D8F5), textAlign = TextAlign.Center, fontSize = 14.sp
      )
      Spacer(Modifier.height(18.dp))
      OutlinedTextField(value = code, onValueChange = { code = it.filter { ch -> ch.isDigit() }.take(4) }, label = { Text("Código de invitado (4 dígitos)") }, singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number), modifier = Modifier.fillMaxWidth())
      Spacer(Modifier.height(8.dp))
      OutlinedTextField(value = name, onValueChange = { name = it.take(24) }, label = { Text("Tu nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
      if (error != null) Text("⚠️ $error", color = Color(0xFFFF9AA8), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
      Spacer(Modifier.height(14.dp))
      GradientButton("Unirme a la sala", onClick = { vm.joinAsGuest(code, name) }, modifier = Modifier.fillMaxWidth(), enabled = code.length == 4 && name.isNotBlank())
      Spacer(Modifier.height(8.dp))
      SoftButton("← Volver", onClick = onBack)
    }
  }
}

/** Vista de la sala: Cine, Juegos, Chat y Tienda, con llamada de voz grupal y efectos para todos. */
/** Estado REAL de la conexión: solo dice «Conectado» cuando el servidor confirmó mi último latido. */
@Composable
fun ConnectionChip(vm: CoupleViewModel) {
  val st by vm.connState.collectAsState()
  val others by vm.presentUids.collectAsState()
  var showDiag by remember { mutableStateOf(false) }
  val (color, label) = when (st) {
    com.example.model.ConnState.ONLINE -> Color(0xFF39FF88) to "Conectado · ${others.size + 1} en la sala"
    com.example.model.ConnState.CONNECTING -> Color(0xFFFFC107) to "Conectando… (toca)"
    com.example.model.ConnState.OFFLINE -> Color(0xFFFF5252) to "Sin conexión (toca)"
    com.example.model.ConnState.DENIED -> Color(0xFFFF5252) to "Reglas sin publicar"
  }
  Row(
    Modifier.clip(RoundedCornerShape(50)).background(Color(0x66000000)).clickable { vm.runConnDiag(); showDiag = true }.padding(horizontal = 8.dp, vertical = 2.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box(Modifier.size(7.dp).clip(CircleShape).background(color))
    Spacer(Modifier.width(5.dp))
    Text(label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
  }
  if (showDiag) {
    val report by vm.connDiag.collectAsState()
    androidx.compose.material3.AlertDialog(
      onDismissRequest = { showDiag = false },
      title = { Text("Diagnóstico de conexión") },
      text = { Text(report, fontSize = 12.sp, modifier = Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) },
      confirmButton = { androidx.compose.material3.TextButton(onClick = { vm.runConnDiag() }) { Text("Repetir") } },
      dismissButton = { androidx.compose.material3.TextButton(onClick = { showDiag = false }) { Text("Cerrar") } }
    )
  }
}

@Composable
fun GuestScaffold(vm: CoupleViewModel) {
  var tab by remember { mutableStateOf(0) }
  val syncIssue by vm.syncIssue.collectAsState()
  val social = remember { RoomSocial(vm) }

  androidx.compose.runtime.LaunchedEffect(Unit) {
    vm.refreshWallet()
    vm.groupCall.startWatching()
  }
  androidx.compose.runtime.DisposableEffect(Unit) { onDispose { runCatching { vm.groupCall.stopWatching() } } }
  androidx.compose.runtime.LaunchedEffect(Unit) {
    vm.roomChat().collect { social.onSnapshot(it, vm.myUid()) }
  }
  androidx.compose.runtime.LaunchedEffect(Unit) {
    kotlinx.coroutines.delay(1800)
    social.announceJoin()
  }
  androidx.compose.runtime.LaunchedEffect(tab) {
    social.chatOpen = (tab == 2)
    if (tab == 2) social.unread = 0
  }

  Box(Modifier.fillMaxSize()) {
    AuroraBackground {
      Scaffold(
        containerColor = Color.Transparent,
        topBar = {
          Column {
            Row(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
              Column(modifier = Modifier.weight(1f)) {
                SiniJossTitle(18.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Text(if (vm.isInLobby) "🚪 ${vm.lobbyRoomName().ifBlank { "Sala" }}" else "Invitado 🎟️", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                  Spacer(Modifier.width(8.dp))
                  ConnectionChip(vm)
                }
              }
              WalkieButton(vm, 40.dp)
              Spacer(Modifier.width(8.dp))
              SoftButton("Cambiar de sala", onClick = { vm.leaveGuest() })
            }
            RoomRingBanner(vm)
          }
        },
        bottomBar = {
          Row(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val tabs = listOf("🎬" to "Cine", "🎲" to "Juegos", "💬" to "Chat", "🛒" to "Tienda", "⚙️" to "Ajustes")
            tabs.forEachIndexed { i, (e, n) ->
              val sel = tab == i
              Box(
                Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                  .background(if (sel) com.example.ui.theme.LocalDuoPalette.current.accentBrush else Brush.horizontalGradient(listOf(Color(0x33FFFFFF), Color(0x22FFFFFF))))
                  .clickable { tab = i }.padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
              ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                  Text(e, fontSize = 18.sp)
                  Text(n, color = if (sel) Color(0xFF04120A) else Color.White, fontSize = 11.sp, fontWeight = if (sel) FontWeight.Black else FontWeight.Medium)
                }
                if (i == 2 && social.unread > 0 && !sel) {
                  Text(
                    if (social.unread > 9) "9+" else social.unread.toString(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black,
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 10.dp).clip(CircleShape).background(Color(0xFFE53950)).padding(horizontal = 5.dp, vertical = 1.dp)
                  )
                }
              }
            }
          }
        }
      ) { pad ->
        Column(modifier = Modifier.fillMaxSize().padding(pad)) {
          if (syncIssue != null) {
            GlassCard(modifier = Modifier.fillMaxWidth().padding(8.dp), corner = 12.dp, onClick = { vm.clearSyncIssue() }) {
              Text("⚠️ $syncIssue", color = Color(0xFFFFD0D8), fontSize = 12.sp)
            }
          }
          androidx.compose.animation.Crossfade(
            targetState = tab,
            animationSpec = androidx.compose.animation.core.tween(380),
            label = "roomTabs"
          ) { t ->
            when (t) {
              0 -> WatchPartyScreen(vm)
              1 -> MinigamesScreen(vm)
              2 -> RoomChatScreen(vm, social)
              3 -> RoomShopScreen(vm, social)
              else -> RoomSettingsScreen(vm, social)
            }
          }
        }
      }
    }
    RoomFxLayer(social, vm)
  }
}

/** Para la pareja: generar el código de invitado y administrar a quienes entraron. */
@Composable
fun GuestManagement(vm: CoupleViewModel) {
  val code by vm.inviteCode.collectAsState()
  val guests by vm.guestUids.collectAsState()
  val names by vm.roomNames.collectAsState()
  val ctx = LocalContext.current
  val clipboard = LocalClipboardManager.current
  GlassCard(modifier = Modifier.fillMaxWidth(), highlight = true) {
    Text("👥 Invitados (hasta 2 más)", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
    Text(
      "Cualquiera de los dos puede generar un código. Los invitados solo ven Juegos y Cine y pueden hablar por walkie; no ven tu chat, tu calendario ni tus datos.",
      color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp
    )
    Spacer(Modifier.height(8.dp))
    if (code.isNotBlank()) {
      Text(code, color = GoldYellow, fontWeight = FontWeight.Black, fontSize = 30.sp, letterSpacing = 6.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        SoftButton("Copiar", onClick = { clipboard.setText(AnnotatedString(code)) }, modifier = Modifier.weight(1f))
        SoftButton("Compartir", onClick = {
          val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Únete a nuestra sala en SINI&JOSS con este código de invitado: $code")
          runCatching { ctx.startActivity(Intent.createChooser(send, "Compartir código").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }, modifier = Modifier.weight(1f))
        SoftButton("Desactivar", onClick = { vm.revokeGuestCode() }, modifier = Modifier.weight(1f))
      }
      Spacer(Modifier.height(6.dp))
    }
    GradientButton(if (code.isBlank()) "Generar código de invitado" else "Generar un código nuevo", onClick = { vm.generateGuestCode() }, modifier = Modifier.fillMaxWidth(), enabled = guests.size < 2)
    if (guests.size >= 2) Text("La sala ya tiene 2 invitados. Expulsa a uno para invitar a otra persona.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
    guests.forEach { uid ->
      Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        Text("🎟️ ${names[uid] ?: "Invitado"}", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        SoftButton("Expulsar", onClick = { vm.kickGuest(uid) })
      }
    }
  }
}
