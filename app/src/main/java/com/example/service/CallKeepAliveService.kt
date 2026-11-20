package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R

/** Mantiene priorizado el proceso mientras una llamada WebRTC está activa en segundo plano. */
class CallKeepAliveService : Service() {
  companion object {
    const val ACTION_START = "com.example.service.CALL_KEEP_ALIVE_START"
    const val ACTION_STOP = "com.example.service.CALL_KEEP_ALIVE_STOP"
    const val EXTRA_VIDEO = "call_is_video"
    private const val CHANNEL = "active_call_media"
    private const val NOTIFICATION_ID = 1207

    fun startIntent(context: android.content.Context, video: Boolean): Intent =
      Intent(context, CallKeepAliveService::class.java).apply {
        action = ACTION_START
        putExtra(EXTRA_VIDEO, video)
      }
    fun stopIntent(context: android.content.Context): Intent =
      Intent(context, CallKeepAliveService::class.java).apply { action = ACTION_STOP }
  }

  override fun onCreate() {
    super.onCreate()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val manager = getSystemService(NotificationManager::class.java)
      manager.createNotificationChannel(NotificationChannel(CHANNEL, "Llamada activa", NotificationManager.IMPORTANCE_LOW).apply {
        description = "Mantiene la llamada de voz o vídeo mientras usas otra aplicación"
        setShowBadge(false)
      })
    }
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.action == ACTION_STOP) {
      stopForeground(STOP_FOREGROUND_REMOVE)
      stopSelf()
      return START_NOT_STICKY
    }
    val video = intent?.getBooleanExtra(EXTRA_VIDEO, false) ?: false
    val openApp = PendingIntent.getActivity(
      this, 1207, Intent(this, MainActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
      }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val notification: Notification = NotificationCompat.Builder(this, CHANNEL)
      .setSmallIcon(R.mipmap.ic_launcher)
      .setContentTitle(if (video) "Videollamada en curso" else "Llamada en curso")
      .setContentText("La llamada seguirá activa mientras usas otra aplicación")
      .setCategory(NotificationCompat.CATEGORY_CALL)
      .setOngoing(true)
      .setContentIntent(openApp)
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .build()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
      if (video) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
      startForeground(NOTIFICATION_ID, notification, type)
    } else {
      startForeground(NOTIFICATION_ID, notification)
    }
    // El motor WebRTC vive en el proceso de la app y no se restaura por sí solo.
    // No resucitar una notificación de llamada falsa si Android mata el proceso.
    return START_NOT_STICKY
  }

  override fun onBind(intent: Intent?): IBinder? = null
}
