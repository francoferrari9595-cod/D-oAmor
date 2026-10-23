package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioManager
import com.example.data.AppVisibility
import com.example.data.SoundPrefs
import com.google.firebase.firestore.Query
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.audio.BuzzerSoundEngine
import com.example.model.BuzzerSoundType
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.example.data.firestore.FirestoreInstance
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Servicio en primer plano que:
 *  1) escucha en Firestore cuando tu pareja mantiene presionado el zumbador, y
 *  2) hace sonar/vibrar ESTE teléfono mientras ella/él mantenga el botón,
 *  siempre que tú hayas dado permiso (ajuste "Permitir zumbidos de mi pareja").
 */
class CoupleBuzzerService : Service() {

  data class IncomingBuzz(val senderName: String, val startedAt: Long)

  companion object {
    const val CHANNEL_ID = "couple_buzzer_channel_v2"
    const val NOTIFICATION_ID = 1001
    const val CALL_CHANNEL_ID = "couple_call_channel"
    const val CALL_NOTIFICATION_ID = 1002

    const val ACTION_START_BUZZ = "com.example.service.ACTION_START_BUZZ"
    const val ACTION_STOP_BUZZ = "com.example.service.ACTION_STOP_BUZZ"
    const val ACTION_KEEP_ALIVE = "com.example.service.ACTION_KEEP_ALIVE"
    const val EXTRA_SOUND_TYPE = "extra_sound_type"
    const val EXTRA_SENDER_NAME = "extra_sender_name"

    const val PREFS = "doamor_prefs"
    const val PREF_ALLOW_BUZZ = "allow_buzz"
    const val PREF_ASKED_BUZZ = "asked_buzz"
    const val PREF_PARTNER_NAME = "partner_name"
    const val PREF_SOUND = "buzz_sound"
    private const val MAX_BUZZ_MS = 60_000L
    private const val STALE_MS = 90_000L

    /** La interfaz observa este estado para mostrar la pantalla de zumbido entrante. */
    val incomingBuzz = MutableStateFlow<IncomingBuzz?>(null)

    fun isBuzzAllowed(context: Context): Boolean =
      context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(PREF_ALLOW_BUZZ, false)

    fun wasAsked(context: Context): Boolean =
      context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(PREF_ASKED_BUZZ, false)

    fun setBuzzAllowed(context: Context, allowed: Boolean) {
      context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        .putBoolean(PREF_ALLOW_BUZZ, allowed).putBoolean(PREF_ASKED_BUZZ, true).apply()
    }

    fun saveSound(context: Context, sound: BuzzerSoundType) {
      context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(PREF_SOUND, sound.name).apply()
    }

    fun savePartnerName(context: Context, name: String) {
      context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(PREF_PARTNER_NAME, name).apply()
    }

    fun startBuzz(context: Context, soundType: BuzzerSoundType, senderName: String) {
      val intent = Intent(context, CoupleBuzzerService::class.java).apply {
        action = ACTION_START_BUZZ
        putExtra(EXTRA_SOUND_TYPE, soundType.name)
        putExtra(EXTRA_SENDER_NAME, senderName)
      }
      launch(context, intent)
    }

    fun stopBuzz(context: Context) {
      val intent = Intent(context, CoupleBuzzerService::class.java).apply { action = ACTION_STOP_BUZZ }
      runCatching { context.startService(intent) }
        .onFailure { Log.w("D-oAmor", "No se pudo detener el zumbido", it) }
    }

    fun ensureServiceStarted(context: Context) {
      val intent = Intent(context, CoupleBuzzerService::class.java).apply { action = ACTION_KEEP_ALIVE }
      launch(context, intent)
    }

    private fun launch(context: Context, intent: Intent) {
      runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
        else context.startService(intent)
      }.onFailure { Log.w("D-oAmor", "No se pudo iniciar el servicio del zumbador", it) }
    }
  }

  private var soundEngine: BuzzerSoundEngine? = null
  private var isBuzzing = false
  private var dismissed = false
  private var previousAlarmVolume = -1
  private val handler = Handler(Looper.getMainLooper())
  private val autoStop = Runnable { handleStopBuzz() }

  private var coupleListener: ListenerRegistration? = null
  private var callsListener: ListenerRegistration? = null
  private var messagesListener: ListenerRegistration? = null
  private var firstMsgSnapshot = true
  private var lastMsgId = ""
  private var listeningCoupleId = ""
  private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
    if (key == "coupleId") attachCoupleListener()
  }
  private val authListener = FirebaseAuth.AuthStateListener { attachCoupleListener() }

  override fun onCreate() {
    super.onCreate()
    soundEngine = BuzzerSoundEngine(applicationContext)
    createNotificationChannel()
    // Aviso en primer plano inmediato para que Android no cierre el servicio.
    safeStartForeground(buildDefaultNotification())
    getSharedPreferences("doamor_pairing", Context.MODE_PRIVATE).registerOnSharedPreferenceChangeListener(prefsListener)
    runCatching { FirebaseAuth.getInstance().addAuthStateListener(authListener) }
    attachCoupleListener()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_START_BUZZ -> {
        val soundTypeName = intent.getStringExtra(EXTRA_SOUND_TYPE)
        val soundType = runCatching { BuzzerSoundType.valueOf(soundTypeName ?: BuzzerSoundType.PHONE_RING.name) }
          .getOrDefault(BuzzerSoundType.PHONE_RING)
        handleStartBuzz(soundType, intent.getStringExtra(EXTRA_SENDER_NAME) ?: "Tu amor")
      }
      ACTION_STOP_BUZZ -> {
        if (isBuzzing) dismissed = true // el usuario lo silenció: no reiniciar hasta que la pareja suelte
        handleStopBuzz()
      }
      else -> safeStartForeground(if (isBuzzing) buildAlertNotification("Tu amor") else buildDefaultNotification())
    }
    return START_STICKY
  }

  private fun attachCoupleListener() {
    val coupleId = getSharedPreferences("doamor_pairing", Context.MODE_PRIVATE).getString("coupleId", "").orEmpty()
    val uid = runCatching { FirebaseAuth.getInstance().currentUser?.uid }.getOrNull()
    if (coupleId.isBlank() || uid.isNullOrBlank()) return
    if (coupleId == listeningCoupleId && coupleListener != null) return
    coupleListener?.remove()
    callsListener?.remove()
    listeningCoupleId = coupleId
    attachCallsListener(coupleId)
    attachMessagesListener(coupleId)
    coupleListener = runCatching {
      FirestoreInstance.get(applicationContext).collection("couples").document(coupleId)
        .addSnapshotListener { snap, error ->
          if (error != null || snap == null || !snap.exists()) return@addSnapshotListener
          val myUid = FirebaseAuth.getInstance().currentUser?.uid ?: return@addSnapshotListener
          val sender = snap.getString("activeBuzzSenderUid").orEmpty()
          if (sender.isBlank()) {
            dismissed = false
            if (isBuzzing) handleStopBuzz()
            return@addSnapshotListener
          }
          if (sender == myUid) return@addSnapshotListener
          val updatedAt = snap.getTimestamp("updatedAt")?.toDate()?.time ?: System.currentTimeMillis()
          if (System.currentTimeMillis() - updatedAt > STALE_MS) return@addSnapshotListener
          if (!isBuzzAllowed(applicationContext) || dismissed || isBuzzing) return@addSnapshotListener
          val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
          val sound = runCatching { BuzzerSoundType.valueOf(prefs.getString(PREF_SOUND, "PHONE_RING") ?: "PHONE_RING") }
            .getOrDefault(BuzzerSoundType.PHONE_RING)
          handleStartBuzz(sound, prefs.getString(PREF_PARTNER_NAME, "Tu amor")?.ifBlank { "Tu amor" } ?: "Tu amor")
        }
    }.onFailure { Log.w("D-oAmor", "No se pudo escuchar el zumbador", it) }.getOrNull()
  }

  /** Avisa de llamadas entrantes aunque la app esté en segundo plano (sin depender de Cloud Functions). */
  private fun attachCallsListener(coupleId: String) {
    callsListener = runCatching {
      FirestoreInstance.get(applicationContext).collection("couples").document(coupleId).collection("calls")
        .whereEqualTo("status", "ringing")
        .addSnapshotListener { snap, error ->
          if (error != null || snap == null) return@addSnapshotListener
          val myUid = FirebaseAuth.getInstance().currentUser?.uid ?: return@addSnapshotListener
          val now = System.currentTimeMillis()
          val ringing = snap.documents.firstOrNull { d ->
            val caller = d.getString("callerUid")
            val created = d.getTimestamp("createdAt")?.toDate()?.time ?: now
            caller != null && caller != myUid && now - created < 45_000L
          }
          val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
          if (ringing != null) showCallNotification(ringing.getBoolean("video") == true, nm) else nm.cancel(CALL_NOTIFICATION_ID)
        }
    }.onFailure { Log.w("D-oAmor", "No se pudo escuchar llamadas", it) }.getOrNull()
  }

  private fun showCallNotification(video: Boolean, nm: NotificationManager) {
    if (AppVisibility.foreground || !SoundPrefs.enabled(this, SoundPrefs.CALL)) return
    runCatching {
      val uri = SoundPrefs.resolve(this, SoundPrefs.CALL)
      val chId = CALL_CHANNEL_ID + "_" + (uri?.toString()?.hashCode() ?: 0)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        nm.createNotificationChannel(NotificationChannel(chId, "Llamadas de tu pareja", NotificationManager.IMPORTANCE_HIGH).apply {
          lockscreenVisibility = Notification.VISIBILITY_PUBLIC
          if (uri == null) setSound(null, null)
          else setSound(uri, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        })
      }
      val open = PendingIntent.getActivity(
        this, 7,
        Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
      )
      val builder = NotificationCompat.Builder(this, chId)
        .setSmallIcon(android.R.drawable.sym_call_incoming)
        .setContentTitle(if (video) "Videollamada de tu amor" else "Llamada de tu amor")
        .setContentText("Toca para contestar en D-oAmor")
        .setContentIntent(open)
        .setFullScreenIntent(open, true)
        .setCategory(NotificationCompat.CATEGORY_CALL)
        .setPriority(NotificationCompat.PRIORITY_MAX)
        .setAutoCancel(true)
        .setTimeoutAfter(45_000L)
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O && uri != null) builder.setSound(uri)
      val notification = builder.build()
      notification.flags = notification.flags or Notification.FLAG_INSISTENT
      nm.notify(CALL_NOTIFICATION_ID, notification)
    }
  }

  /** Avisa de mensajes y notas de voz nuevas con el sonido que elegiste (solo con la app en segundo plano). */
  private fun attachMessagesListener(coupleId: String) {
    messagesListener?.remove()
    firstMsgSnapshot = true
    messagesListener = runCatching {
      FirestoreInstance.get(applicationContext).collection("couples").document(coupleId).collection("messages")
        .orderBy("createdAt", Query.Direction.DESCENDING).limit(1)
        .addSnapshotListener { snap, error ->
          if (error != null || snap == null) return@addSnapshotListener
          val d = snap.documents.firstOrNull() ?: return@addSnapshotListener
          if (firstMsgSnapshot) { firstMsgSnapshot = false; lastMsgId = d.id; return@addSnapshotListener }
          if (d.id == lastMsgId) return@addSnapshotListener
          lastMsgId = d.id
          val myUid = FirebaseAuth.getInstance().currentUser?.uid ?: return@addSnapshotListener
          if (d.getString("senderId") == myUid || AppVisibility.foreground) return@addSnapshotListener
          showMessageNotification(d.getString("type") ?: "TEXT", d.getString("content") ?: "")
        }
    }.onFailure { Log.w("D-oAmor", "No se pudo escuchar mensajes", it) }.getOrNull()
  }

  private fun showMessageNotification(type: String, content: String) {
    if (type == "BUZZ") return
    val kind = if (type == "AUDIO") SoundPrefs.AUDIO else SoundPrefs.MESSAGE
    if (!SoundPrefs.enabled(this, kind)) return
    runCatching {
      val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      val uri = SoundPrefs.resolve(this, kind)
      val chId = "msg_" + kind + "_" + (uri?.toString()?.hashCode() ?: 0)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        nm.createNotificationChannel(NotificationChannel(chId, if (kind == SoundPrefs.AUDIO) "Notas de voz" else "Mensajes", NotificationManager.IMPORTANCE_HIGH).apply {
          enableVibration(true)
          if (uri == null) setSound(null, null)
          else setSound(uri, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        })
      }
      val open = PendingIntent.getActivity(
        this, 8,
        Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
      )
      val name = getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(PREF_PARTNER_NAME, "Tu amor")?.ifBlank { "Tu amor" } ?: "Tu amor"
      val text = when (type) {
        "AUDIO" -> "🎙️ Te envió una nota de voz"
        "PHOTO" -> "📷 Te envió una foto"
        else -> content.take(120)
      }
      val builder = NotificationCompat.Builder(this, chId)
        .setSmallIcon(android.R.drawable.ic_dialog_email)
        .setContentTitle(name)
        .setContentText(text)
        .setContentIntent(open)
        .setAutoCancel(true)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O && uri != null) builder.setSound(uri)
      nm.notify(2001, builder.build())
    }
  }

  private fun handleStartBuzz(soundType: BuzzerSoundType, senderName: String) {
    if (!isBuzzAllowed(applicationContext)) return
    if (isBuzzing) return
    isBuzzing = true
    incomingBuzz.value = IncomingBuzz(senderName, System.currentTimeMillis())
    safeStartForeground(buildAlertNotification(senderName))
    raiseAlarmVolume()
    runCatching { soundEngine?.startBuzzer(soundType) }.onFailure { Log.w("D-oAmor", "Error al sonar", it) }
    handler.removeCallbacks(autoStop)
    handler.postDelayed(autoStop, MAX_BUZZ_MS)
  }

  private fun handleStopBuzz() {
    handler.removeCallbacks(autoStop)
    if (isBuzzing) {
      isBuzzing = false
      runCatching { soundEngine?.stopBuzzer() }
      restoreAlarmVolume()
      incomingBuzz.value = null
      runCatching {
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
          .notify(NOTIFICATION_ID, buildDefaultNotification())
      }
    }
  }

  private fun raiseAlarmVolume() {
    runCatching {
      val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
      previousAlarmVolume = am.getStreamVolume(AudioManager.STREAM_ALARM)
      am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
    }
  }

  private fun restoreAlarmVolume() {
    runCatching {
      if (previousAlarmVolume >= 0) {
        (getSystemService(Context.AUDIO_SERVICE) as AudioManager).setStreamVolume(AudioManager.STREAM_ALARM, previousAlarmVolume, 0)
      }
    }
    previousAlarmVolume = -1
  }

  private fun safeStartForeground(notification: Notification) {
    runCatching { startForeground(NOTIFICATION_ID, notification) }
      .onFailure { Log.w("D-oAmor", "No se pudo pasar a primer plano", it) }
  }

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel = NotificationChannel(CHANNEL_ID, "Zumbido de Pareja", NotificationManager.IMPORTANCE_HIGH).apply {
        description = "Zumbidos y alertas prioritarias entre parejas"
        enableVibration(false)
        setSound(null, null) // el sonido lo reproduce el motor de zumbido, no la notificación
        lockscreenVisibility = Notification.VISIBILITY_PUBLIC
      }
      (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
    }
  }

  private fun buildDefaultNotification(): Notification {
    val pendingIntent = PendingIntent.getActivity(
      this, 0, Intent(this, MainActivity::class.java),
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setContentTitle("D-oAmor conectado")
      .setContentText("Listo para recibir llamadas y zumbidos de tu amor")
      .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
      .setContentIntent(pendingIntent)
      .setOngoing(true)
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .build()
  }

  private fun buildAlertNotification(senderName: String): Notification {
    val openAppIntent = PendingIntent.getActivity(
      this, 0,
      Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP },
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    val stopIntent = PendingIntent.getService(
      this, 1,
      Intent(this, CoupleBuzzerService::class.java).apply { action = ACTION_STOP_BUZZ },
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setContentTitle("¡$senderName te está haciendo sonar!")
      .setContentText("Toca para responder o silencia el zumbido.")
      .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
      .setContentIntent(openAppIntent)
      .setFullScreenIntent(openAppIntent, true)
      .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Silenciar", stopIntent)
      .setOngoing(true)
      .setPriority(NotificationCompat.PRIORITY_MAX)
      .setCategory(NotificationCompat.CATEGORY_ALARM)
      .build()
  }

  override fun onDestroy() {
    handler.removeCallbacks(autoStop)
    coupleListener?.remove()
    coupleListener = null
    callsListener?.remove()
    callsListener = null
    messagesListener?.remove()
    messagesListener = null
    runCatching { getSharedPreferences("doamor_pairing", Context.MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(prefsListener) }
    runCatching { FirebaseAuth.getInstance().removeAuthStateListener(authListener) }
    runCatching { soundEngine?.stopBuzzer() }
    restoreAlarmVolume()
    incomingBuzz.value = null
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null
}
