package com.example

import androidx.compose.animation.togetherWith
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Surface
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.data.AppUpdater
import com.example.ui.CoupleViewModel
import com.example.ui.MainCoupleScaffold
import com.example.ui.PairingGate
import com.example.ui.theme.KomboArenaTheme

class MainActivity : ComponentActivity() {
  override fun onStart() { super.onStart(); com.example.data.AppVisibility.foreground = true; com.example.audio.BackgroundMusic.onForeground(this) }
  override fun onStop() { super.onStop(); com.example.data.AppVisibility.foreground = false; com.example.audio.BackgroundMusic.onBackground() }


  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    setContent {
      val viewModel: CoupleViewModel = viewModel()
      val appTheme by viewModel.appTheme.collectAsState()
      var requiredUpdateUrl by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf<String?>(null) }
      var optionalUpdate by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Pair<String, String>?>(null) }

      // El servidor publica la versión más reciente y la mínima obligatoria. Sin conexión,
      // la aplicación sigue funcionando con normalidad.
      LaunchedEffect(Unit) {
       this@MainActivity.repeatOnLifecycle(Lifecycle.State.RESUMED) {
        AppUpdater.onResume(this@MainActivity)
        // Se revisa al volver a la app y además cada 30 s mientras está abierta (reintento en 10 s si falla la red),
        // así un dispositivo que ya estaba abierto cuando se publicó la versión también queda obligado a actualizar.
        var optionalShown = false
        while (true) {
          if (AppUpdater.downloading || requiredUpdateUrl != null) { kotlinx.coroutines.delay(5000); continue }
          val remote = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
              val metadataUrl = URL("https://raw.githubusercontent.com/francoferrari9595-cod/D-oAmor/main/update.json?check=${System.currentTimeMillis()}")
              val conn = (metadataUrl.openConnection() as HttpURLConnection)
              conn.connectTimeout = 8000
              conn.readTimeout = 8000
              conn.useCaches = false
              conn.setRequestProperty("Cache-Control", "no-cache")
              conn.requestMethod = "GET"
              try {
                if (conn.responseCode !in 200..299) null
                else JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
              } finally { conn.disconnect() }
            }.getOrNull()
          }
          if (remote == null) { kotlinx.coroutines.delay(10_000); continue }
          val minVersion = remote.optInt("minimumSupportedVersionCode", 0)
          val latestVersion = remote.optInt("latestVersionCode", 0)
          val url = remote.optString("apkUrl", "").takeIf { it.startsWith("https://") }
          val releaseNotes = remote.optString("releaseNotes", "Hay mejoras y correcciones disponibles.")
            .takeIf { it.isNotBlank() } ?: "Hay mejoras y correcciones disponibles."
          if (url != null && minVersion > BuildConfig.VERSION_CODE) {
            // La obligatoria siempre manda, aunque ya hubiera un aviso opcional en pantalla.
            optionalUpdate = null
            requiredUpdateUrl = url
          } else if (url != null && latestVersion > BuildConfig.VERSION_CODE && !optionalShown) {
            optionalShown = true
            optionalUpdate = url to releaseNotes
          }
          kotlinx.coroutines.delay(30_000)
        }
       }
      }

      // Runtime permission launcher for notifications and audio
      val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
      ) { /* Result handled gracefully */ }

      LaunchedEffect(Unit) {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
          }
        }
        if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
          permissions.add(Manifest.permission.RECORD_AUDIO)
        }
        if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
          permissions.add(Manifest.permission.CAMERA)
        }
        if (permissions.isNotEmpty()) {
          permissionLauncher.launch(permissions.toTypedArray())
        }
      }

      KomboArenaTheme(appTheme = appTheme) {
        // Saludo de bienvenida: una sola vez por apertura de la plataforma.
        LaunchedEffect(Unit) { com.example.audio.WelcomeSound.playOnce(this@MainActivity) }
        // Presentación de 8 s al abrir la app (no se repite al girar la pantalla).
        var showSplash by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(true) }
        // La música de fondo empieza cuando termina la presentación (al ver las salas) y sigue durante los juegos.
        LaunchedEffect(showSplash, requiredUpdateUrl) { if (!showSplash && requiredUpdateUrl == null) com.example.audio.BackgroundMusic.start(this@MainActivity) }
        Surface(modifier = Modifier.fillMaxSize()) {
          val epoch by viewModel.sessionEpoch.collectAsState()
          androidx.compose.runtime.key(epoch) {
            val guestRoom by viewModel.guestRoomId.collectAsState()
            // Estación de juegos: todo funciona por salas. Sin sala → lista de salas; con sala → juegos de la sala.
            // Entrar a una sala: la pantalla nueva entra deslizándose con zoom suave; al salir, vuelve en sentido contrario.
            androidx.compose.animation.AnimatedContent(
              targetState = guestRoom.isNotBlank(),
              transitionSpec = {
                val forward = targetState
                (androidx.compose.animation.slideInHorizontally(androidx.compose.animation.core.tween(520, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { if (forward) it / 4 else -it / 4 } +
                  androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(520)) +
                  androidx.compose.animation.scaleIn(androidx.compose.animation.core.tween(520), initialScale = 0.94f))
                  .togetherWith(
                    androidx.compose.animation.slideOutHorizontally(androidx.compose.animation.core.tween(420, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { if (forward) -it / 5 else it / 5 } +
                      androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(320)) +
                      androidx.compose.animation.scaleOut(androidx.compose.animation.core.tween(420), targetScale = 1.04f)
                  )
              },
              label = "roomSwitch"
            ) { inRoom ->
              if (inRoom) com.example.ui.GuestScaffold(viewModel) else com.example.ui.LobbyListScreen(viewModel)
            }
          }
          if (showSplash && requiredUpdateUrl == null) com.example.ui.KomboSplash(onFinished = { showSplash = false })
          if (requiredUpdateUrl != null) {
            AlertDialog(
              onDismissRequest = {},
              properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
              title = { Text("Actualización obligatoria") },
              text = { Text("Hay una versión nueva de KOMBO ARENA y esta versión ya no puede seguir usándose. Descargá e instalá la actualización para continuar.") },
              confirmButton = {
                TextButton(onClick = { requiredUpdateUrl?.let { AppUpdater.start(this@MainActivity, it) } }) { Text("Actualizar ahora") }
              }
            )
          } else if (optionalUpdate != null) {
            AlertDialog(
              onDismissRequest = { optionalUpdate = null },
              title = { Text("Actualización disponible") },
              text = { Text("Hay una nueva versión de KOMBO ARENA.\n\n${optionalUpdate?.second}") },
              confirmButton = {
                TextButton(onClick = {
                  val url = optionalUpdate?.first
                  optionalUpdate = null
                  if (url != null) AppUpdater.start(this@MainActivity, url)
                }) { Text("Actualizar ahora") }
              },
              dismissButton = {
                TextButton(onClick = { optionalUpdate = null }) { Text("Más tarde") }
              }
            )
          }
          if (AppUpdater.downloading) {
            AlertDialog(
              onDismissRequest = {},
              properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
              title = { Text("Descargando actualización…") },
              text = {
                androidx.compose.foundation.layout.Column {
                  val p = AppUpdater.progress
                  if (p >= 0f) androidx.compose.material3.LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                  else androidx.compose.material3.LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                  androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
                  Text(if (p >= 0f) "${(p * 100).toInt()} %" else "Descargando…")
                  androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
                  Text("Al terminar, Android te pedirá tocar «Instalar».", fontSize = 12.sp)
                }
              },
              confirmButton = {}
            )
          }
          AppUpdater.error?.let { msg ->
            AlertDialog(
              onDismissRequest = { AppUpdater.error = null },
              title = { Text("Actualización") },
              text = { Text(msg) },
              confirmButton = {
                TextButton(onClick = { val u = AppUpdater.lastUrl; AppUpdater.error = null; if (u != null) AppUpdater.start(this@MainActivity, u) }) { Text("Reintentar") }
              },
              dismissButton = { TextButton(onClick = { AppUpdater.error = null }) { Text("Cerrar") } }
            )
          }
        }
      }
    }
  }
}
