package com.example.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Descarga el APK nuevo dentro de la app y abre el instalador del sistema.
 * El usuario solo toca «Actualizar ahora» y luego «Instalar» en la pantalla de Android
 * (Android no permite instalar en silencio sin permisos especiales de dispositivo).
 */
object AppUpdater {
  var downloading by mutableStateOf(false); private set
  /** 0..1; negativo = tamaño desconocido. */
  var progress by mutableStateOf(-1f); private set
  var error by mutableStateOf<String?>(null)
  var lastUrl: String? = null; private set

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private var pendingInstall = false

  private fun apkFile(app: Context) = File(File(app.cacheDir, "updates"), "KOMBO-ARENA.apk")

  fun start(context: Context, url: String) {
    if (downloading) return
    val app = context.applicationContext
    lastUrl = url
    error = null
    downloading = true
    progress = -1f
    scope.launch {
      val result = withContext(Dispatchers.IO) { runCatching { download(app, url) } }
      downloading = false
      result.onSuccess { install(app) }
        .onFailure { error = "No se pudo descargar la actualización. Revisá tu conexión e intentá de nuevo." }
    }
  }

  private fun download(app: Context, url: String) {
    val dir = File(app.cacheDir, "updates").apply { mkdirs() }
    val tmp = File(dir, "KOMBO-ARENA.part")
    val out = apkFile(app)
    tmp.delete(); out.delete()
    val conn = URL(url).openConnection() as HttpURLConnection
    try {
      conn.connectTimeout = 15_000
      conn.readTimeout = 30_000
      conn.instanceFollowRedirects = true
      conn.useCaches = false
      conn.setRequestProperty("Cache-Control", "no-cache")
      if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode}")
      val total = conn.contentLengthLong
      var read = 0L
      var lastUi = 0L
      conn.inputStream.use { input ->
        tmp.outputStream().use { o ->
          val buf = ByteArray(32 * 1024)
          while (true) {
            val n = input.read(buf)
            if (n < 0) break
            o.write(buf, 0, n)
            read += n
            val now = System.currentTimeMillis()
            if (now - lastUi > 150) { lastUi = now; progress = if (total > 0) (read.toFloat() / total).coerceIn(0f, 1f) else -1f }
          }
        }
      }
      if (total > 0 && read != total) throw IOException("Descarga incompleta")
      // Debe ser un APK válido de esta misma aplicación.
      val info = app.packageManager.getPackageArchiveInfo(tmp.path, 0) ?: throw IOException("APK inválido")
      if (info.packageName != app.packageName) throw IOException("El archivo no es de esta aplicación")
      if (!tmp.renameTo(out)) throw IOException("No se pudo guardar el APK")
    } finally {
      conn.disconnect()
    }
  }

  /** Abre el instalador de Android. Si falta el permiso «instalar apps desconocidas», lo pide y continúa al volver. */
  fun install(context: Context) {
    val app = context.applicationContext
    val f = apkFile(app)
    if (!f.exists()) return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !app.packageManager.canRequestPackageInstalls()) {
      pendingInstall = true
      val i = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      runCatching { app.startActivity(i) }.onFailure { error = "Activá «Permitir instalar apps» para KOMBO ARENA en Ajustes." }
      return
    }
    pendingInstall = false
    val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", f)
    val i = Intent(Intent.ACTION_VIEW)
      .setDataAndType(uri, "application/vnd.android.package-archive")
      .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { app.startActivity(i) }.onFailure { error = "No se pudo abrir el instalador de Android." }
  }

  /** Al volver a la app (por ejemplo desde Ajustes tras dar el permiso) continúa la instalación pendiente. */
  fun onResume(context: Context) {
    if (pendingInstall && !downloading) install(context)
  }
}
