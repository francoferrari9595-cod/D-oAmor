package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.example.data.firestore.FirestoreInstance
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/** FCM receiver. Production sends must come from a trusted server/Cloud Function using Admin SDK. */
class DuoAmorMessagingService : FirebaseMessagingService() {
  companion object {
    const val CHANNEL_ID = "duoamor_remote_alerts"
    const val EXTRA_ACTION = "duoamor_action"
    const val EXTRA_CALL_ID = "duoamor_call_id"
    private const val CALL_NOTIFICATION_ID = 2201
  }

  override fun onNewToken(token: String) {
    super.onNewToken(token)
    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
    // Rules must restrict this write to the authenticated user document.
    FirestoreInstance.get(applicationContext).collection("users").document(uid)
      .set(mapOf("fcmToken" to token, "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()),
        com.google.firebase.firestore.SetOptions.merge())
      .addOnFailureListener { android.util.Log.w("DuoAmorFCM", "No se pudo guardar token FCM", it) }
  }

  override fun onMessageReceived(message: RemoteMessage) {
    super.onMessageReceived(message)
    when (message.data["action"]) {
      "buzz_stop" -> {
        // El emisor soltó el botón; detener también el sonido del teléfono remoto.
        try {
          startService(Intent(this, CoupleBuzzerService::class.java).apply {
            action = CoupleBuzzerService.ACTION_STOP_BUZZ
          })
        } catch (error: Exception) {
          android.util.Log.w("DuoAmorFCM", "No se pudo detener el zumbido remoto", error)
        }
      }
      "buzz" -> {
        val sender = message.data["senderName"]?.take(60)?.ifBlank { "Tu amor" } ?: "Tu amor"
        val sound = message.data["soundType"] ?: "PHONE_RING"
        val intent = Intent(this, CoupleBuzzerService::class.java).apply {
          action = CoupleBuzzerService.ACTION_START_BUZZ
          putExtra(CoupleBuzzerService.EXTRA_SOUND_TYPE, sound)
          putExtra(CoupleBuzzerService.EXTRA_SENDER_NAME, sender)
        }
        try {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        } catch (_: Exception) {
          showNotification("Tu amor te está haciendo sonar", "Abre D-oAmor para responder al zumbido.", 2200, false, "")
        }
      }
      "incoming_call" -> {
        val callId = message.data["callId"].orEmpty()
        val kind = if (message.data["video"] == "true") "Videollamada" else "Llamada de voz"
        showNotification("Llamada entrante de tu amor", "$kind · toca para abrir D-oAmor", CALL_NOTIFICATION_ID, true, callId)
      }
    }
  }

  private fun showNotification(title: String, body: String, id: Int, fullScreen: Boolean, callId: String) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Alertas de D-oAmor", NotificationManager.IMPORTANCE_HIGH).apply {
        description = "Llamadas entrantes y avisos importantes de tu pareja"
        lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        enableVibration(true)
      })
    }
    val intent = Intent(this, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
      if (fullScreen) {
        putExtra(EXTRA_ACTION, "incoming_call")
        putExtra(EXTRA_CALL_ID, callId)
      }
    }
    val pending = PendingIntent.getActivity(this, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val builder = NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(android.R.drawable.sym_call_incoming).setContentTitle(title).setContentText(body)
      .setContentIntent(pending).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_MAX)
      .setCategory(if (fullScreen) Notification.CATEGORY_CALL else Notification.CATEGORY_ALARM)
    if (fullScreen) builder.setFullScreenIntent(pending, true)
    (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(id, builder.build())
  }
}
