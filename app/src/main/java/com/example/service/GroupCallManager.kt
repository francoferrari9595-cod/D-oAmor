package com.example.service

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import androidx.core.content.ContextCompat
import com.example.data.firestore.FirestoreCoupleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.webrtc.PeerConnection

/**
 * Llamada de voz grupal de la sala (hasta 6 personas, malla WebRTC).
 *  · Presencia: un documento por persona en "gcall" (se renueva cada 10 s).
 *  · Señalización: documentos en "gsig" dirigidos a un destinatario; se borran al procesarlos.
 *  · Para cada par, el UID menor envía la oferta (así nunca se pisan dos ofertas).
 */
class GroupCallManager(
  private val app: Application,
  private val repo: FirestoreCoupleRepository,
  private val scope: CoroutineScope,
  private val roomId: () -> String,
  private val myUid: () -> String?,
  private val myName: () -> String
) {
  data class Member(val uid: String, val name: String, val muted: Boolean, val sid: String, val ageMs: Long)

  companion object { const val MAX_MEMBERS = 6; private const val STALE_MS = 35_000L }

  private val _members = MutableStateFlow<List<Member>>(emptyList())
  val members: StateFlow<List<Member>> = _members.asStateFlow()
  private val _inCall = MutableStateFlow(false)
  val inCall: StateFlow<Boolean> = _inCall.asStateFlow()
  private val _muted = MutableStateFlow(false)
  val muted: StateFlow<Boolean> = _muted.asStateFlow()
  private val _speaker = MutableStateFlow(true)
  val speaker: StateFlow<Boolean> = _speaker.asStateFlow()
  private val _status = MutableStateFlow("")
  val status: StateFlow<String> = _status.asStateFlow()
  private val _peerStates = MutableStateFlow<Map<String, String>>(emptyMap())
  val peerStates: StateFlow<Map<String, String>> = _peerStates.asStateFlow()

  private var engine: GroupCallEngine? = null
  private var watchJob: Job? = null
  private var sigJob: Job? = null
  private var hbJob: Job? = null
  private var reconcileJob: Job? = null
  private var watchedRoom = ""
  private var mySid = ""
  private val peerSid = HashMap<String, String>()
  private val audio get() = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager

  /** Empieza a mirar quién está en la llamada de esta sala (sirve para mostrar el aviso "llamada en curso"). */
  fun startWatching() {
    val room = roomId()
    if (room.isBlank() || (watchJob != null && watchedRoom == room)) return
    watchJob?.cancel()
    watchedRoom = room
    watchJob = scope.launch {
      repo.observeCallMembers(room).collect { docs ->
        _members.value = docs.mapNotNull { d ->
          val uid = d["uid"] as? String ?: return@mapNotNull null
          val age = (d["ageMs"] as? Number)?.toLong() ?: 0L
          if (age > STALE_MS) return@mapNotNull null
          Member(uid, d["name"] as? String ?: "Invitado", d["muted"] as? Boolean ?: false, d["sid"] as? String ?: "", age)
        }.sortedBy { it.uid }
        checkRing()
      }
    }
  }

  // ───────── Timbre: avisa (suave) cuando alguien empieza una llamada y yo no estoy en ella ─────────
  private val _ringing = MutableStateFlow("")
  /** Nombre de quien está llamando ("" = nadie). */
  val ringing: StateFlow<String> = _ringing.asStateFlow()
  private var ringJob: Job? = null
  private var lastOthers = 0
  private var ringDismissedUntil = 0L
  private val ringChannel = "kombo_group_call"

  private fun checkRing() {
    val me = myUid()
    val others = _members.value.filter { it.uid != me }
    val n = others.size
    if (_inCall.value) { stopRing(); lastOthers = n; return }
    if (n == 0) { stopRing(); lastOthers = 0; return }
    // llamada nueva: antes no había nadie y ahora sí
    if (lastOthers == 0 && _ringing.value.isBlank() && System.currentTimeMillis() > ringDismissedUntil) startRing(others.first().name)
    lastOthers = n
  }

  private fun startRing(who: String) {
    _ringing.value = who
    postRingNotification(who)
    ringJob?.cancel()
    ringJob = scope.launch {
      val uri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
      val tone = runCatching { android.media.RingtoneManager.getRingtone(app, uri) }.getOrNull()
      runCatching {
        if (android.os.Build.VERSION.SDK_INT >= 28) tone?.volume = 0.6f
      }
      repeat(6) {
        if (_ringing.value.isBlank()) return@launch
        runCatching { tone?.stop(); tone?.play() }
        runCatching {
          val v = app.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
          if (android.os.Build.VERSION.SDK_INT >= 26) v?.vibrate(android.os.VibrationEffect.createOneShot(180, 90)) else @Suppress("DEPRECATION") v?.vibrate(180)
        }
        delay(3500)
      }
      runCatching { tone?.stop() }
      _ringing.value = ""
      cancelRingNotification()
    }
  }

  fun stopRing() {
    if (_ringing.value.isNotBlank()) { _ringing.value = ""; ringJob?.cancel(); ringJob = null; cancelRingNotification() }
  }

  /** "Ignorar": silencia y no vuelve a sonar durante esta llamada. */
  fun dismissRing() { ringDismissedUntil = System.currentTimeMillis() + 60_000L; stopRing() }

  private fun postRingNotification(who: String) {
    runCatching {
      val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
      if (android.os.Build.VERSION.SDK_INT >= 26) {
        nm.createNotificationChannel(android.app.NotificationChannel(ringChannel, "Llamadas de la sala", android.app.NotificationManager.IMPORTANCE_DEFAULT).apply {
          description = "Aviso suave cuando alguien inicia una llamada de voz en tu sala"
          setSound(null, null)
        })
      }
      val open = app.packageManager.getLaunchIntentForPackage(app.packageName)?.apply { addFlags(android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP) }
      val pi = android.app.PendingIntent.getActivity(app, 7701, open, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
      val n = androidx.core.app.NotificationCompat.Builder(app, ringChannel)
        .setSmallIcon(android.R.drawable.sym_call_incoming)
        .setContentTitle("📞 $who está llamando")
        .setContentText("Llamada de voz de la sala · tocá para unirte")
        .setCategory(androidx.core.app.NotificationCompat.CATEGORY_CALL)
        .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)
        .setAutoCancel(true).setOnlyAlertOnce(true)
        .setTimeoutAfter(45_000L)
        .setContentIntent(pi)
        .build()
      nm.notify(7701, n)
    }
  }

  private fun cancelRingNotification() {
    runCatching { (app.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager).cancel(7701) }
  }

  fun stopWatching() {
    stopRing(); lastOthers = 0
    watchJob?.cancel(); watchJob = null; watchedRoom = ""
    _members.value = emptyList()
  }

  private fun publishPresence() {
    val uid = myUid() ?: return
    repo.setCallMember(roomId(), uid, mapOf("uid" to uid, "name" to myName(), "muted" to _muted.value, "sid" to mySid))
  }

  fun join() {
    if (_inCall.value) return
    stopRing()
    val uid = myUid() ?: return
    val room = roomId()
    if (room.isBlank()) return
    if (ContextCompat.checkSelfPermission(app, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
      _status.value = "Falta el permiso del micrófono. Activalo en los ajustes del teléfono."
      return
    }
    val others = _members.value.count { it.uid != uid }
    if (others >= MAX_MEMBERS - 1) { _status.value = "La llamada ya tiene $MAX_MEMBERS personas."; return }
    startWatching()
    mySid = java.util.UUID.randomUUID().toString().take(8)
    peerSid.clear()
    _peerStates.value = emptyMap()
    _status.value = "Conectando…"
    val eng = GroupCallEngine(app, object : GroupCallEngine.Listener {
      override fun onSignal(to: String, type: String, data: String) {
        scope.launch {
          val toSid = _members.value.firstOrNull { it.uid == to }?.sid ?: ""
          repo.sendCallSignal(roomId(), mapOf("from" to uid, "to" to to, "type" to type, "data" to data, "fromSid" to mySid, "toSid" to toSid))
        }
      }
      override fun onPeerState(uid: String, state: PeerConnection.PeerConnectionState) {
        scope.launch {
          _peerStates.value = _peerStates.value + (uid to state.name)
          if (state == PeerConnection.PeerConnectionState.FAILED) _status.value = "Se cortó la conexión con un participante; reintentando…"
        }
      }
      override fun onError(message: String) { scope.launch { _status.value = message } }
    })
    engine = eng
    eng.startLocalAudio()
    eng.setMuted(_muted.value)
    runCatching {
      audio.mode = AudioManager.MODE_IN_COMMUNICATION
      audio.isSpeakerphoneOn = _speaker.value
    }
    runCatching { ContextCompat.startForegroundService(app, CallKeepAliveService.startIntent(app, false)) }
    _inCall.value = true
    publishPresence()
    hbJob = scope.launch { while (true) { delay(10_000); publishPresence() } }
    sigJob = scope.launch {
      repo.observeCallSignals(room, uid).collect { list -> list.forEach { handleSignal(it) } }
    }
    reconcileJob = scope.launch {
      _members.collect { reconcile(uid, it) }
    }
  }

  private fun reconcile(uid: String, list: List<Member>) {
    val eng = engine ?: return
    val others = list.filter { it.uid != uid }.take(MAX_MEMBERS - 1)
    val ids = others.map { it.uid }.toSet()
    // quitar a los que se fueron o volvieron a entrar con otra sesión
    for (m in others) {
      val old = peerSid[m.uid]
      if (old != null && old != m.sid && eng.hasPeer(m.uid)) { eng.removePeer(m.uid); peerSid.remove(m.uid) }
    }
    for (u in peerSid.keys.toList()) if (u !in ids) { eng.removePeer(u); peerSid.remove(u); _peerStates.value = _peerStates.value - u }
    // el UID menor ofrece
    for (m in others) {
      if (peerSid[m.uid] == m.sid && eng.hasPeer(m.uid)) continue
      if (m.sid.isBlank()) continue
      peerSid[m.uid] = m.sid
      if (uid < m.uid) eng.offerTo(m.uid)
    }
    if (others.isEmpty()) _status.value = "Esperando que alguien se una…"
    else if (_status.value.startsWith("Conectando") || _status.value.startsWith("Esperando")) _status.value = ""
  }

  private fun handleSignal(sig: Map<String, Any?>) {
    val id = sig["id"] as? String ?: return
    val from = sig["from"] as? String ?: ""
    val toSid = sig["toSid"] as? String ?: ""
    repo.deleteCallSignal(roomId(), id)
    val eng = engine ?: return
    if (!_inCall.value || from.isBlank()) return
    if (toSid.isNotBlank() && toSid != mySid) return // era para una sesión anterior mía
    val type = sig["type"] as? String ?: return
    val data = sig["data"] as? String ?: return
    val fromSid = sig["fromSid"] as? String ?: ""
    if (type == "offer") {
      val old = peerSid[from]
      if (old != null && old != fromSid && eng.hasPeer(from)) eng.removePeer(from)
      peerSid[from] = fromSid
      eng.handleOffer(from, data)
    } else if (type == "answer") eng.handleAnswer(from, data)
    else if (type == "ice") eng.handleIce(from, data)
  }

  fun setMuted(m: Boolean) {
    _muted.value = m
    engine?.setMuted(m)
    if (_inCall.value) publishPresence()
  }

  fun setSpeaker(on: Boolean) {
    _speaker.value = on
    runCatching { audio.isSpeakerphoneOn = on }
  }

  fun leave() {
    if (!_inCall.value && engine == null) return
    _inCall.value = false
    hbJob?.cancel(); hbJob = null
    sigJob?.cancel(); sigJob = null
    reconcileJob?.cancel(); reconcileJob = null
    val uid = myUid()
    val room = roomId()
    if (uid != null && room.isNotBlank()) repo.removeCallMember(room, uid)
    engine?.close(); engine = null
    peerSid.clear()
    _peerStates.value = emptyMap()
    _status.value = ""
    runCatching { audio.mode = AudioManager.MODE_NORMAL; audio.isSpeakerphoneOn = false }
    runCatching { app.startService(CallKeepAliveService.stopIntent(app)) }
  }
}
