package com.example.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.LobbyInfo
import com.example.ui.components.AuroraBackground
import com.example.ui.components.GlassCard
import com.example.ui.components.GradientButton
import com.example.ui.components.SiniJossTitle
import com.example.ui.components.SoftButton

/**
 * Salas públicas: Sala 1, 2 y 3 libres, Sala 4 con contraseña y botón para agregar salas con nombre.
 * Todos los que entran a la misma sala pueden jugar juntos.
 */
@Composable
fun LobbyListScreen(vm: CoupleViewModel, onBack: (() -> Unit)? = null) {
  val fetched by vm.lobbies.collectAsState()
  // Siempre se ven Sala 1-4 aunque Firestore tarde o falle; los datos reales las reemplazan al llegar.
  val lobbies = remember(fetched) {
    val byId = fetched.associateBy { it.id }
    // Hasta que llegue la lista real se muestran Sala 1-4; después, solo las que existen (una sala eliminada desaparece).
    val base = if (fetched.isEmpty()) com.example.model.Lobbies.DEFAULTS.map { d -> LobbyInfo(d.id, d.name, d.hasPassword, 0, d.order, true) }
      else com.example.model.Lobbies.DEFAULTS.mapNotNull { d -> byId[d.id] }
    base + fetched.filter { f -> com.example.model.Lobbies.DEFAULTS.none { it.id == f.id } }
  }
  val error by vm.lobbyError.collectAsState()
  val joining by vm.joining.collectAsState()
  var name by remember { mutableStateOf(vm.myDisplayName.value) }
  var askPassword by remember { mutableStateOf<LobbyInfo?>(null) }
  var showCreate by remember { mutableStateOf(false) }
  var askDelete by remember { mutableStateOf<LobbyInfo?>(null) }
  var delMsg by remember { mutableStateOf("") }
  var showList by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
  LaunchedEffect(showList) { if (showList) vm.startLobbyList() }
  DisposableEffect(Unit) { onDispose { vm.stopLobbyList() } }

  if (!showList) {
    // Portada: primero el nombre y un botón; las salas solo aparecen al tocar "Ver salas".
    AuroraBackground {
      Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
      ) {
        SiniJossTitle(34.sp)
        Spacer(Modifier.height(8.dp))
        Text("Juegos, cine, chat y llamadas con tus amigos", color = Color(0xFFE2D8F5), textAlign = TextAlign.Center, fontSize = 14.sp)
        Spacer(Modifier.height(28.dp))
        OutlinedTextField(value = name, onValueChange = { name = it.take(24) }, label = { Text("Tu nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        GradientButton("🚪 Ver salas", onClick = { showList = true }, modifier = Modifier.fillMaxWidth(), enabled = name.isNotBlank())
        Spacer(Modifier.height(8.dp))
        SoftButton("➕ Crear mi sala", onClick = { showList = true; showCreate = true }, modifier = Modifier.fillMaxWidth(), enabled = name.isNotBlank())
        if (onBack != null) { Spacer(Modifier.height(8.dp)); SoftButton("← Volver", onClick = onBack, modifier = Modifier.fillMaxWidth()) }
      }
    }
    return
  }

  AuroraBackground {
    Column(
      modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
        .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      SiniJossTitle(28.sp)
      Spacer(Modifier.height(6.dp))
      Text("🎮 Elige tu sala\nTodos los que entren a la misma sala juegan juntos.", color = Color(0xFFE2D8F5), textAlign = TextAlign.Center, fontSize = 14.sp)
      Spacer(Modifier.height(14.dp))
      OutlinedTextField(value = name, onValueChange = { name = it.take(24) }, label = { Text("Tu nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
      if (error != null) Text("⚠️ $error", color = Color(0xFFFF9AA8), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
      Spacer(Modifier.height(12.dp))
      if (fetched.isEmpty() && error == null) Text("Conectando con el servidor…", color = Color(0xFFE2D8F5), fontSize = 12.sp)
      RoomSquaresGrid(lobbies, onLongPress = { l -> delMsg = ""; askDelete = l }) { l -> if (l.hasPassword) askPassword = l else vm.joinLobby(l, "", name) }
      Spacer(Modifier.height(10.dp))
      Spacer(Modifier.height(4.dp))
      GradientButton("➕ Agregar sala", onClick = { showCreate = true }, modifier = Modifier.fillMaxWidth())
      Spacer(Modifier.height(8.dp))
      SoftButton("← Volver", onClick = { showList = false }, modifier = Modifier.fillMaxWidth())
    }
  }

  joining?.let { room ->
    AlertDialog(
      onDismissRequest = {},
      title = { Text("Entrando a $room…") },
      text = { Column { androidx.compose.material3.CircularProgressIndicator(); Spacer(Modifier.height(8.dp)); Text("Conectando con el servidor. Si tarda, se reintenta solo.", fontSize = 12.sp) } },
      confirmButton = { TextButton(onClick = { vm.cancelJoin() }) { Text("Cancelar") } }
    )
  }

  askDelete?.let { l ->
    var code by remember(l.id) { mutableStateOf("") }
    AlertDialog(
      onDismissRequest = { askDelete = null },
      title = { Text("🗑️ Eliminar ${l.name}") },
      text = {
        Column {
          Text("Escribí el código de administración para eliminar esta sala.", fontSize = 13.sp)
          Spacer(Modifier.height(8.dp))
          OutlinedTextField(value = code, onValueChange = { code = it.filter { c -> c.isDigit() }.take(12); delMsg = "" }, singleLine = true, label = { Text("Código") }, visualTransformation = PasswordVisualTransformation())
          if (delMsg.isNotEmpty()) Text(delMsg, color = Color(0xFFFF8A80), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        }
      },
      confirmButton = { TextButton(enabled = code.isNotBlank(), onClick = { vm.deleteLobby(l, code) { ok, msg -> if (ok) askDelete = null else delMsg = msg } }) { Text("Eliminar") } },
      dismissButton = { TextButton(onClick = { askDelete = null }) { Text("Cancelar") } }
    )
  }

  askPassword?.let { l ->
    var pw by remember(l.id) { mutableStateOf("") }
    AlertDialog(
      onDismissRequest = { askPassword = null },
      title = { Text("🔒 ${l.name}") },
      text = {
        Column {
          Text("Escribe la contraseña de la sala.", fontSize = 13.sp)
          Spacer(Modifier.height(8.dp))
          OutlinedTextField(value = pw, onValueChange = { pw = it.take(32) }, singleLine = true, label = { Text("Contraseña") }, visualTransformation = PasswordVisualTransformation())
        }
      },
      confirmButton = { TextButton(onClick = { vm.joinLobby(l, pw, name); askPassword = null }, enabled = pw.isNotBlank()) { Text("Entrar") } },
      dismissButton = { TextButton(onClick = { askPassword = null }) { Text("Cancelar") } }
    )
  }

  if (showCreate) {
    var roomName by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    AlertDialog(
      onDismissRequest = { showCreate = false },
      title = { Text("➕ Nueva sala") },
      text = {
        Column {
          OutlinedTextField(value = roomName, onValueChange = { roomName = it.take(24) }, singleLine = true, label = { Text("Nombre de la sala") })
          Spacer(Modifier.height(8.dp))
          OutlinedTextField(value = pw, onValueChange = { pw = it.take(32) }, singleLine = true, label = { Text("Contraseña (opcional)") }, visualTransformation = PasswordVisualTransformation())
        }
      },
      confirmButton = { TextButton(onClick = { vm.createLobby(roomName, pw, name); showCreate = false }, enabled = roomName.isNotBlank() && name.isNotBlank()) { Text("Crear y entrar") } },
      dismissButton = { TextButton(onClick = { showCreate = false }) { Text("Cancelar") } }
    )
  }
}

/** Tarjeta de Ajustes: cerrar sesión y volver al inicio sin reinstalar. */
@Composable
fun SessionCard(vm: CoupleViewModel) {
  var confirm by remember { mutableStateOf(false) }
  GlassCard(modifier = Modifier.fillMaxWidth(), highlight = true) {
    Text("🚪 Sesión", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
    Text(
      "Cierra la sesión de este teléfono y vuelve a la pantalla principal para entrar de nuevo con un código de pareja, un código de invitado o una sala pública.",
      color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp
    )
    Spacer(Modifier.height(8.dp))
    GradientButton("Cerrar sesión y volver al inicio", onClick = { confirm = true }, modifier = Modifier.fillMaxWidth())
  }
  if (confirm) {
    AlertDialog(
      onDismissRequest = { confirm = false },
      title = { Text("¿Cerrar sesión?") },
      text = { Text("Este teléfono se desvinculará de la pareja actual (como si reinstalaras la app). Para volver a jugar juntos tendrán que vincularse otra vez con un código. Tu pareja no pierde su cuenta.") },
      confirmButton = { TextButton(onClick = { confirm = false; vm.logoutToStart() }) { Text("Cerrar sesión") } },
      dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancelar") } }
    )
  }
}
