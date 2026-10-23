package com.example.ui

import android.app.Application
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.util.Log
import java.io.File
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.google.firebase.firestore.FieldValue
import com.example.data.firestore.FirestoreInstance
import com.example.data.SoundPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.BuzzerSoundEngine
import com.example.data.CoupleRepository
import com.example.model.BuzzerSoundType
import com.example.model.CoupleCalendarEvent
import com.example.model.CoupleMessage
import com.example.model.CoupleUser
import com.example.model.EventType
import com.example.model.MessageType
import com.example.model.ScratchCoupon
import com.example.model.ScreensaverNote
import com.example.service.CoupleBuzzerService
import com.example.service.CallKeepAliveService
import com.example.service.WebRtcCallEngine
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.VideoTrack
import org.webrtc.EglBase
import com.example.ui.theme.AppThemeMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

enum class CallStatus {
  IDLE,
  OUTGOING,
  INCOMING,
  CONNECTING,
  CONNECTED
}

data class TimeTogether(
  val years: Int = 0,
  val months: Int = 0,
  val days: Long = 0,
  val hours: Long = 0,
  val minutes: Long = 0,
  val seconds: Long = 0,
  val daysUntilNextAnniversary: Int = 0
)

class CoupleViewModel(application: Application) : AndroidViewModel(application) {

  val repository = CoupleRepository(application)
  private val soundEngine = BuzzerSoundEngine(application)
  val firestoreRepo = com.example.data.firestore.FirestoreCoupleRepository(application)
  val authManager = com.example.auth.AuthManager(application)

  val currentUserId = repository.currentUserId
  val user1 = repository.user1
  val user2 = repository.user2
  val coupleStartDate = repository.coupleStartDate
  val pairingCode = repository.pairingCode
  val appTheme = repository.appTheme
  val soundType = repository.soundType
  val vibrationEnabled = repository.vibrationEnabled
  val screensaverNote = repository.screensaverNote
  val messages = repository.messages
  val calendarEvents = repository.calendarEvents
  val coupons = repository.coupons
  val triviaQuestions = repository.triviaQuestions

  // Active user and partner user
  val activeUser: StateFlow<CoupleUser> = combine(currentUserId, user1, user2) { id, u1, u2 ->
    if (id == "user_1") u1 else u2
  }.stateIn(viewModelScope, SharingStarted.Eagerly, user1.value)

  val partnerUser: StateFlow<CoupleUser> = combine(currentUserId, user1, user2) { id, u1, u2 ->
    if (id == "user_1") u2 else u1
  }.stateIn(viewModelScope, SharingStarted.Eagerly, user2.value)

  // Buzz holding state (when local user holds down)
  private val _isHoldingBuzzSender = MutableStateFlow(false)
  val isHoldingBuzzSender: StateFlow<Boolean> = _isHoldingBuzzSender.asStateFlow()

  private val _buzzHoldingDurationMs = MutableStateFlow(0L)
  val buzzHoldingDurationMs: StateFlow<Long> = _buzzHoldingDurationMs.asStateFlow()
  private var buzzHoldJob: Job? = null

  // Incoming buzz state (when partner holds down or simulated)
  private val _isIncomingBuzzActive = MutableStateFlow(false)
  val isIncomingBuzzActive: StateFlow<Boolean> = _isIncomingBuzzActive.asStateFlow()

  private val _incomingBuzzDurationMs = MutableStateFlow(0L)
  val incomingBuzzDurationMs: StateFlow<Long> = _incomingBuzzDurationMs.asStateFlow()
  private var incomingBuzzJob: Job? = null

  // Real-time time elapsed counter
  private val _timeTogether = MutableStateFlow(TimeTogether())
  val timeTogether: StateFlow<TimeTogether> = _timeTogether.asStateFlow()

  // Voice note recording in chat
  private val _isRecordingVoice = MutableStateFlow(false)
  val isRecordingVoice: StateFlow<Boolean> = _isRecordingVoice.asStateFlow()
  private val _voiceRecordingDurationSec = MutableStateFlow(0)
  val voiceRecordingDurationSec: StateFlow<Int> = _voiceRecordingDurationSec.asStateFlow()
  private var voiceRecordJob: Job? = null
  private var mediaRecorder: MediaRecorder? = null
  private var voiceFile: File? = null
  private var mediaPlayer: MediaPlayer? = null

  // Audio message playing
  private val _playingAudioMessageId = MutableStateFlow<String?>(null)
  val playingAudioMessageId: StateFlow<String?> = _playingAudioMessageId.asStateFlow()

  // Call state
  private val _callStatus = MutableStateFlow(CallStatus.IDLE)
  val callStatus: StateFlow<CallStatus> = _callStatus.asStateFlow()

  private val _isVideoCall = MutableStateFlow(false)
  val isVideoCall: StateFlow<Boolean> = _isVideoCall.asStateFlow()

  private val _callDurationSec = MutableStateFlow(0)
  val callDurationSec: StateFlow<Int> = _callDurationSec.asStateFlow()

  private val _isMuted = MutableStateFlow(false)
  val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

  private val _isSpeakerOn = MutableStateFlow(true)
  val isSpeakerOn: StateFlow<Boolean> = _isSpeakerOn.asStateFlow()

  private val _floatingHearts = MutableStateFlow<List<Long>>(emptyList())
  val floatingHearts: StateFlow<List<Long>> = _floatingHearts.asStateFlow()
  private var callTimerJob: Job? = null
  private var callListenerJob: Job? = null
  private var candidateListenerJob: Job? = null
  private var activeCallId: String? = null
  private var activeCallCoupleId: String? = null
  private var isCallInitiator = false
  private val seenRemoteCandidates = mutableSetOf<String>()
  private var webRtcEngine: WebRtcCallEngine? = null
  private val _localVideoTrack = MutableStateFlow<VideoTrack?>(null)
  val localVideoTrack: StateFlow<VideoTrack?> = _localVideoTrack.asStateFlow()
  private val _remoteVideoTrack = MutableStateFlow<VideoTrack?>(null)
  val remoteVideoTrack: StateFlow<VideoTrack?> = _remoteVideoTrack.asStateFlow()
  fun videoEglContext(): EglBase.Context? = webRtcEngine?.eglContext

  // Minigame: Trivia
  private val _triviaIndex = MutableStateFlow(0)
  val triviaIndex: StateFlow<Int> = _triviaIndex.asStateFlow()
  private val _sharedWheelPrize = MutableStateFlow<String?>(null)
  val sharedWheelPrize: StateFlow<String?> = _sharedWheelPrize.asStateFlow()

  // Minigame: Tic Tac Toe
  private val _tictactoeBoard = MutableStateFlow(List(9) { "" })
  val tictactoeBoard: StateFlow<List<String>> = _tictactoeBoard.asStateFlow()

  private val _tictactoeTurn = MutableStateFlow("❤️") // "❤️" or "💋"
  val tictactoeTurn: StateFlow<String> = _tictactoeTurn.asStateFlow()

  private val _tictactoeWinner = MutableStateFlow<String?>(null)
  val tictactoeWinner: StateFlow<String?> = _tictactoeWinner.asStateFlow()

  private val _scoreHearts = MutableStateFlow(0)
  val scoreHearts: StateFlow<Int> = _scoreHearts.asStateFlow()

  private val _scoreKisses = MutableStateFlow(0)
  val scoreKisses: StateFlow<Int> = _scoreKisses.asStateFlow()

  private val _myBuzzAllowed = MutableStateFlow(CoupleBuzzerService.isBuzzAllowed(application))
  val myBuzzAllowed: StateFlow<Boolean> = _myBuzzAllowed.asStateFlow()
  private val _buzzAsked = MutableStateFlow(CoupleBuzzerService.wasAsked(application))
  val buzzAsked: StateFlow<Boolean> = _buzzAsked.asStateFlow()
  private val _partnerBuzzAllowed = MutableStateFlow(false)
  val partnerBuzzAllowed: StateFlow<Boolean> = _partnerBuzzAllowed.asStateFlow()
  private val _memberUids = MutableStateFlow<List<String>>(emptyList())
  val memberUids: StateFlow<List<String>> = _memberUids.asStateFlow()
  private val _gamesDoc = MutableStateFlow<Map<String, Any>>(emptyMap())
  private val _gameError = MutableStateFlow<String?>(null)
  private val _coupleDoc = MutableStateFlow<Map<String, Any>>(emptyMap())
  private val _syncIssue = MutableStateFlow<String?>(null)
  val syncIssue: StateFlow<String?> = _syncIssue.asStateFlow()
  private val _myCredits = MutableStateFlow(0L)
  val myCredits: StateFlow<Long> = _myCredits.asStateFlow()
  private val _partnerCredits = MutableStateFlow(0L)
  val partnerCredits: StateFlow<Long> = _partnerCredits.asStateFlow()
  private val _myDisplayName = MutableStateFlow(activeUser.value.name)
  val myDisplayName: StateFlow<String> = _myDisplayName.asStateFlow()
  private val _partnerDisplayName = MutableStateFlow(partnerUser.value.nickname.ifBlank { partnerUser.value.name })
  val partnerDisplayName: StateFlow<String> = _partnerDisplayName.asStateFlow()
  private val _myPhoto = MutableStateFlow<String?>(null)
  val myPhoto: StateFlow<String?> = _myPhoto.asStateFlow()
  private val _partnerPhoto = MutableStateFlow<String?>(null)
  val partnerPhoto: StateFlow<String?> = _partnerPhoto.asStateFlow()
  private val _giftEvent = MutableStateFlow<com.example.model.GiftEvent?>(null)
  val giftEvent: StateFlow<com.example.model.GiftEvent?> = _giftEvent.asStateFlow()
  private val _myRetos = MutableStateFlow<List<com.example.model.RetoItem>>(emptyList())
  val myRetos: StateFlow<List<com.example.model.RetoItem>> = _myRetos.asStateFlow()
  private val _walkieTalking = MutableStateFlow(false)
  val walkieTalking: StateFlow<Boolean> = _walkieTalking.asStateFlow()
  private val _walkieIncoming = MutableStateFlow(false)
  val walkieIncoming: StateFlow<Boolean> = _walkieIncoming.asStateFlow()
  private val _partnerTalking = MutableStateFlow(false)
  val partnerTalking: StateFlow<Boolean> = _partnerTalking.asStateFlow()
  private var walkieRecorder: MediaRecorder? = null
  private var walkieFile: File? = null
  private var walkieStart = 0L
  private var walletInitRequested = false
  private var lastStartDate = 0L
  private var metaSeen = false
  private var lastWalkieId = -1L
  private val lastWalkieIdsBySender = mutableMapOf<String, Long>()
  private var lastSeenMsgId: String? = null
  private val _guestRoom = MutableStateFlow(application.getSharedPreferences("doamor_prefs", android.content.Context.MODE_PRIVATE).getString("guest_room", "") ?: "")
  val guestRoomId: StateFlow<String> = _guestRoom.asStateFlow()
  private val _roomNames = MutableStateFlow<Map<String, String>>(emptyMap())
  val roomNames: StateFlow<Map<String, String>> = _roomNames.asStateFlow()
  private val _guestUids = MutableStateFlow<List<String>>(emptyList())
  val guestUids: StateFlow<List<String>> = _guestUids.asStateFlow()
  private val _inviteCode = MutableStateFlow("")
  val inviteCode: StateFlow<String> = _inviteCode.asStateFlow()
  private val _guestError = MutableStateFlow<String?>(null)
  val guestError: StateFlow<String?> = _guestError.asStateFlow()
  private var roomEnsuredKey = ""
  private var roomSeen = false
  val watch: WatchController = WatchController(
    getApplication(), viewModelScope, gameState("watch"),
    { values -> publishGame("watch", values) }, { firestoreRepo.currentUserId() }
  )
  private var lastAppliedUid: String? = null
  private val _partnerBattery = MutableStateFlow(-1)
  val partnerBattery: StateFlow<Int> = _partnerBattery.asStateFlow()
  private val _swapAvatars = MutableStateFlow(application.getSharedPreferences("doamor_prefs", android.content.Context.MODE_PRIVATE).getBoolean("swap_avatars", false))
  val swapAvatars: StateFlow<Boolean> = _swapAvatars.asStateFlow()
  val gameError: StateFlow<String?> = _gameError.asStateFlow()

  init {
    // FCM puede generar el token antes de iniciar sesión. Volvemos a solicitarlo
    // cuando Auth ya conoce al usuario para no perder el registro del dispositivo.
    val tokenAuth = runCatching { FirebaseAuth.getInstance() }.getOrNull()
    tokenAuth?.addAuthStateListener { firebaseAuth ->
      val uid = firebaseAuth.currentUser?.uid ?: return@addAuthStateListener
      runCatching { FirebaseMessaging.getInstance() }.getOrNull()?.token
        ?.addOnSuccessListener { token ->
          if (token.isNotBlank()) {
            runCatching { FirestoreInstance.get(application) }.getOrNull()
              ?.collection("users")?.document(uid)
              ?.set(mapOf(
                "fcmToken" to token,
                "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
              ), SetOptions.merge())
              ?.addOnFailureListener { error -> Log.w("D-oAmor", "No se pudo registrar el token FCM", error) }
          }
        }?.addOnFailureListener { error -> Log.w("D-oAmor", "No se pudo obtener el token FCM", error) }
    }

    // Mantiene sincronizadas las invitaciones entrantes mientras la pareja está abierta.
    viewModelScope.launch {
      combine(pairingCode, currentUserId) { code, _ -> code }
        .distinctUntilChanged().flatMapLatest { code ->
          val uid = firestoreRepo.currentUserId()
          if (code.isBlank() || uid.isNullOrBlank()) kotlinx.coroutines.flow.flowOf(emptyList())
          else firestoreRepo.observeIncomingCalls(code, uid)
        }.catch { error ->
          Log.e("D-oAmor", "No se pudieron escuchar llamadas entrantes", error); reportSync(error)
          emit(emptyList())
        }.collect { calls ->
          if (_callStatus.value == CallStatus.IDLE && calls.isNotEmpty()) {
            val now = System.currentTimeMillis()
            val incoming = calls.filter { (_, data) ->
              val created = (data["createdAt"] as? com.google.firebase.Timestamp)?.toDate()?.time ?: now
              now - created < 45_000L
            }.maxByOrNull { (_, data) -> (data["createdAt"] as? com.google.firebase.Timestamp)?.toDate()?.time ?: now }
            if (incoming != null) {
              activeCallId = incoming.first
              activeCallCoupleId = pairingCode.value
              isCallInitiator = false
              _isVideoCall.value = incoming.second["video"] as? Boolean ?: false
              _callStatus.value = CallStatus.INCOMING
              listenToActiveCall(incoming.first, pairingCode.value, false)
            }
          }
        }
    }

    // Start foreground service keep-alive
    try {
      CoupleBuzzerService.ensureServiceStarted(getApplication())
    } catch (_: Exception) {}

    watch.start()
    // Sala compartida (pareja + invitados): juegos, cine y walkie viven aquí.
    viewModelScope.launch {
      combine(pairingCode, _guestRoom) { p, g -> if (g.isNotBlank()) g else if (p.isBlank()) "" else roomKey(p.trim()) }
        .distinctUntilChanged()
        .flatMapLatest { id -> if (id.isBlank()) flowOf(null) else firestoreRepo.observeRoom(id).retryWhen { _, _ -> delay(2000); true } }
        .collect { room -> applyRoom(room) }
    }
    viewModelScope.launch {
      while (true) {
        delay(2000)
        maybeEnsureRoom()
      }
    }
    // Walkie de la pareja (mismos permisos que el chat, que ya funciona).
    viewModelScope.launch {
      combine(pairingCode, _guestRoom) { p, g -> if (g.isNotBlank()) "" else p.trim() }
        .distinctUntilChanged()
        .flatMapLatest { code -> if (code.isBlank()) flowOf(null) else firestoreRepo.observeWalkie(code).retryWhen { _, _ -> delay(2000); true } }
        .collect { w -> applyWalkie(w) }
    }
    // Publica la batería REAL de este teléfono para que tu pareja la vea.
    viewModelScope.launch {
      var last = -2
      while (true) {
        val level = runCatching {
          (getApplication<Application>().getSystemService(android.content.Context.BATTERY_SERVICE) as android.os.BatteryManager)
            .getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
        }.getOrDefault(-1)
        val me = firestoreRepo.currentUserId()
        if (me != null && level in 0..100 && level != last && pairingCode.value.isNotBlank()) {
          last = level
          docUpdate(mapOf("profiles.$me.battery" to level))
        }
        delay(60_000)
      }
    }
    // Cuando Firebase termina de iniciar sesión, se recalculan perfiles (evita fotos intercambiadas).
    viewModelScope.launch {
      while (true) {
        delay(1200)
        val uid = firestoreRepo.currentUserId()
        if (uid != null && uid != lastAppliedUid) applyMeta(_coupleDoc.value.takeIf { it.isNotEmpty() })
      }
    }
    // Estado del zumbido entrante (lo genera el servicio en segundo plano).
    viewModelScope.launch {
      CoupleBuzzerService.incomingBuzz.collect { incoming ->
        if (incoming != null) startIncomingBuzzUi(incoming.startedAt) else endIncomingBuzzUi()
      }
    }
    // Datos de la pareja: miembros y permisos del zumbador de cada uno.
    viewModelScope.launch {
      pairingCode.flatMapLatest { code ->
        if (code.isBlank()) flowOf(null) else firestoreRepo.observeCoupleMeta(code)
      }.catch { emit(null) }.collect { meta ->
        val members = (meta?.get("memberUids") as? List<*>)?.mapNotNull { it as? String }.orEmpty()
        if (members.isNotEmpty() || _guestRoom.value.isBlank()) _memberUids.value = members.sorted()
        applyMeta(meta)
        val me = firestoreRepo.currentUserId()
        @Suppress("UNCHECKED_CAST")
        val allow = meta?.get("buzzAllow") as? Map<String, Any?>
        val partnerUid = members.firstOrNull { it != me }
        _partnerBuzzAllowed.value = partnerUid != null && allow?.get(partnerUid) == true
        if (me != null && allow != null && (allow[me] == true) != _myBuzzAllowed.value && _buzzAsked.value) {
          firestoreRepo.setBuzzAllowed(pairingCode.value.trim(), _myBuzzAllowed.value)
        } else if (me != null && allow == null && _buzzAsked.value) {
          firestoreRepo.setBuzzAllowed(pairingCode.value.trim(), _myBuzzAllowed.value)
        }
        CoupleBuzzerService.savePartnerName(getApplication(), _partnerDisplayName.value.ifBlank { "Tu amor" })
      }
    }

    // Auto sign-in with Google if credentials exist
    authManager.attemptAutoSignIn(viewModelScope)

    // Listen to real-time messages from Firestore
    viewModelScope.launch {
      pairingCode.flatMapLatest { code ->
        if (code.isBlank()) kotlinx.coroutines.flow.flowOf(emptyList())
        else firestoreRepo.observeMessages(code)
      }.catch { error ->
        Log.e("D-oAmor", "No se pudieron sincronizar los mensajes", error); reportSync(error)
        emit(emptyList())
      }.collect { remoteMsgs ->
        repository.replaceRemoteMessages(remoteMsgs)
        notifyIncoming(remoteMsgs)
      }
    }
    // El tres en raya se escucha desde Firestore para que los movimientos aparezcan
    // en el otro teléfono sin reiniciar la aplicación.
    viewModelScope.launch {
      pairingCode.flatMapLatest { code ->
        if (code.isBlank()) kotlinx.coroutines.flow.flowOf(null) else firestoreRepo.observeGameState(code)
      }.catch { error ->
        Log.e("D-oAmor", "No se pudo sincronizar el tres en raya", error); reportSync(error)
        emit(null)
      }.collect { state ->
        if (state != null) {
          _tictactoeBoard.value = state.board
          _tictactoeTurn.value = state.turn
          _tictactoeWinner.value = state.winner
          _scoreHearts.value = state.scoreHearts
          _scoreKisses.value = state.scoreKisses
        }
      }
    }

    // La trivia y la ruleta también usan documentos compartidos de Firestore.
    viewModelScope.launch {
      pairingCode.flatMapLatest { code ->
        if (code.isBlank()) kotlinx.coroutines.flow.flowOf(null)
        else firestoreRepo.observeNamedGameState(code, "trivia")
      }.catch { error ->
        Log.e("D-oAmor", "No se pudo sincronizar la trivia", error); reportSync(error)
        emit(null)
      }.collect { state ->
        val remoteIndex = (state?.get("questionIndex") as? Number)?.toInt()
        if (remoteIndex != null && triviaQuestions.isNotEmpty()) _triviaIndex.value = remoteIndex.coerceIn(0, triviaQuestions.lastIndex)
      }
    }
    viewModelScope.launch {
      pairingCode.flatMapLatest { code ->
        if (code.isBlank()) kotlinx.coroutines.flow.flowOf(null)
        else firestoreRepo.observeNamedGameState(code, "wheel")
      }.catch { error ->
        Log.e("D-oAmor", "No se pudo sincronizar la ruleta", error); reportSync(error)
        emit(null)
      }.collect { state ->
        _sharedWheelPrize.value = state?.get("selectedPrize") as? String
      }
    }
    viewModelScope.launch {
      pairingCode.flatMapLatest { code ->
        if (code.isBlank()) kotlinx.coroutines.flow.flowOf(null)
        else firestoreRepo.observeScreensaver(code)
      }.catch { error ->
        Log.e("D-oAmor", "No se pudo sincronizar el salvapantallas", error); reportSync(error)
        emit(null)
      }.collect { remote ->
        if (remote != null) {
          val local = repository.screensaverNote.value
          if (local.note != remote.first || local.emoji != remote.second) {
            repository.updateScreensaverNote(remote.first, remote.second)
          }
        }
      }
    }

    // Live clock for relationship counter
    viewModelScope.launch {
      while (isActive) {
        updateTimeTogether()
        delay(1000)
      }
    }
  }

  private fun updateTimeTogether() {
    val start = coupleStartDate.value
    val now = System.currentTimeMillis()
    if (now < start) {
      _timeTogether.value = TimeTogether()
      return
    }

    val diffMs = now - start
    val totalSecs = diffMs / 1000
    val days = totalSecs / (24 * 3600)
    val hours = (totalSecs % (24 * 3600)) / 3600
    val minutes = (totalSecs % 3600) / 60
    val seconds = totalSecs % 60

    val startCal = Calendar.getInstance().apply { timeInMillis = start }
    val nowCal = Calendar.getInstance().apply { timeInMillis = now }

    var years = nowCal.get(Calendar.YEAR) - startCal.get(Calendar.YEAR)
    var months = nowCal.get(Calendar.MONTH) - startCal.get(Calendar.MONTH)
    if (months < 0) {
      years--
      months += 12
    }

    // Days until next anniversary
    val nextAnnivCal = Calendar.getInstance().apply {
      timeInMillis = start
      set(Calendar.YEAR, nowCal.get(Calendar.YEAR))
      if (before(nowCal)) {
        add(Calendar.YEAR, 1)
      }
    }
    val daysUntil = ((nextAnnivCal.timeInMillis - now) / (1000 * 3600 * 24)).toInt()

    _timeTogether.value = TimeTogether(
      years = years.coerceAtLeast(0),
      months = months.coerceAtLeast(0),
      days = days,
      hours = hours,
      minutes = minutes,
      seconds = seconds,
      daysUntilNextAnniversary = daysUntil
    )
  }

  // --- HOLD-TO-BUZZ ACTIONS ---
  fun startHoldingBuzz() {
    if (_isHoldingBuzzSender.value) return
    _isHoldingBuzzSender.value = true
    _buzzHoldingDurationMs.value = 0L

    // Solo un toque suave: el sonido/vibración fuerte suena en el teléfono de tu pareja.
    soundEngine.softHaptic()

    // Sync to Firestore in real time
    firestoreRepo.updateActiveBuzzState(pairingCode.value, isBuzzing = true)

    buzzHoldJob?.cancel()
    buzzHoldJob = viewModelScope.launch {
      val startTime = System.currentTimeMillis()
      while (isActive && _isHoldingBuzzSender.value) {
        val elapsed = System.currentTimeMillis() - startTime
        _buzzHoldingDurationMs.value = elapsed
        delay(50)
      }
    }
  }

  fun stopHoldingBuzz() {
    if (!_isHoldingBuzzSender.value) return
    _isHoldingBuzzSender.value = false

    val duration = _buzzHoldingDurationMs.value
    buzzHoldJob?.cancel()
    buzzHoldJob = null

    // Stop active buzz on Firestore
    firestoreRepo.updateActiveBuzzState(pairingCode.value, isBuzzing = false, durationMs = duration)

    if (duration > 400) {
      val secondsFormatted = String.format("%.1f", duration / 1000.0)
      val partnerName = partnerUser.value.name
      val content = "⚡ ¡Zumbido de $secondsFormatted s enviado a $partnerName! (Hasta que soltaste la pantalla)"
      repository.sendMessage(
        content = content,
        type = MessageType.BUZZ,
        buzzDurationMs = duration
      )
      firestoreRepo.sendMessage(
        coupleId = pairingCode.value,
        content = content,
        type = MessageType.BUZZ,
        buzzDurationMs = duration
      )
    }
    _buzzHoldingDurationMs.value = 0L
  }

  // El sonido lo reproduce CoupleBuzzerService (también con la app en segundo plano).
  // Aquí solo se refleja el estado en pantalla y se guarda el registro en el chat.
  private fun startIncomingBuzzUi(startedAt: Long) {
    _isIncomingBuzzActive.value = true
    incomingBuzzJob?.cancel()
    incomingBuzzJob = viewModelScope.launch {
      while (isActive && _isIncomingBuzzActive.value) {
        _incomingBuzzDurationMs.value = System.currentTimeMillis() - startedAt
        delay(100)
      }
    }
  }

  private fun endIncomingBuzzUi() {
    if (!_isIncomingBuzzActive.value) return
    val duration = _incomingBuzzDurationMs.value
    _isIncomingBuzzActive.value = false
    incomingBuzzJob?.cancel()
    incomingBuzzJob = null
    if (duration > 500) {
      val partnerName = partnerUser.value.nickname.ifBlank { partnerUser.value.name }
      val secondsFormatted = String.format("%.1f", duration / 1000.0)
      val content = "🔔 Zumbido de $partnerName recibido ($secondsFormatted s)"
      val coupleId = pairingCode.value.trim()
      if (coupleId.isNotBlank()) {
        firestoreRepo.sendMessage(coupleId = coupleId, content = content, type = MessageType.BUZZ, buzzDurationMs = duration)
      }
    }
    _incomingBuzzDurationMs.value = 0L
  }

  /** Prueba local: hace sonar este teléfono (si diste permiso) sin involucrar a tu pareja. */
  fun triggerSimulatedPartnerBuzz() {
    if (!CoupleBuzzerService.isBuzzAllowed(getApplication())) return
    CoupleBuzzerService.startBuzz(getApplication(), soundType.value, partnerUser.value.nickname.ifBlank { "Tu amor" })
  }

  /** El usuario pulsa "Silenciar" en la pantalla de zumbido entrante. */
  fun stopIncomingBuzz() {
    CoupleBuzzerService.stopBuzz(getApplication())
    _isIncomingBuzzActive.value = false
    incomingBuzzJob?.cancel()
    incomingBuzzJob = null
    _incomingBuzzDurationMs.value = 0L
  }

  // --- PERMISO DEL ZUMBADOR ---

  fun setBuzzAllowed(allowed: Boolean) {
    CoupleBuzzerService.setBuzzAllowed(getApplication(), allowed)
    _myBuzzAllowed.value = allowed
    _buzzAsked.value = true
    val code = pairingCode.value.trim()
    if (code.isNotBlank()) firestoreRepo.setBuzzAllowed(code, allowed)
    if (allowed) CoupleBuzzerService.ensureServiceStarted(getApplication())
    else CoupleBuzzerService.stopBuzz(getApplication())
  }

  fun dismissBuzzPrompt() {
    CoupleBuzzerService.setBuzzAllowed(getApplication(), false)
    _buzzAsked.value = true
    _myBuzzAllowed.value = false
  }

  // --- JUEGOS EN VIVO (estado compartido por Firestore) ---
  fun myUid(): String? = firestoreRepo.currentUserId()

  @Suppress("UNCHECKED_CAST")
  fun gameState(gameId: String): Flow<Map<String, Any>?> =
    _gamesDoc.map { it[gameId] as? Map<String, Any> }.distinctUntilChanged()

  /** Id de la sala: derivado del código de pareja con un hash, para que los invitados no puedan deducir el código de la pareja. */
  private fun roomKey(coupleId: String): String {
    val digest = java.security.MessageDigest.getInstance("SHA-256").digest((coupleId + "|sinijoss-room").toByteArray())
    return digest.joinToString("") { "%02x".format(it) }.take(24)
  }

  fun roomId(): String =
    if (_guestRoom.value.isNotBlank()) _guestRoom.value
    else pairingCode.value.trim().let { if (it.isBlank()) "" else roomKey(it) }

  private val _diag = MutableStateFlow("")
  val diagnostics: StateFlow<String> = _diag.asStateFlow()

  /** Prueba cada permiso de Firebase y muestra el resultado en pantalla. */
  fun runDiagnostics() {
    val uid = firestoreRepo.currentUserId()
    val code = pairingCode.value.trim()
    val head = "Sesión de Firebase: ${uid ?: "SIN SESIÓN (la autenticación no inició)"}\nCódigo de pareja: ${if (code.isBlank()) "—" else code}\n"
    _diag.value = head + "Probando…"
    if (uid == null || code.isBlank()) return
    val lines = mutableListOf<String>()
    fun add(line: String) { lines.add(line); _diag.value = head + lines.sorted().joinToString("\n") }
    val room = roomKey(code)
    firestoreRepo.diagCouple(code, uid) { add("1. Pareja (leer): $it") }
    firestoreRepo.diagList("couples/$code/messages") { add("2. Mensajes (leer): $it") }
    firestoreRepo.diagSet("couples/$code/calls/_diag") { add("3. Llamadas (escribir): $it") }
    firestoreRepo.diagGet("rooms/$room") { add("4. Sala de juegos (leer): $it") }
    firestoreRepo.diagSet("rooms/$room") { add("5. Sala de juegos (escribir): $it") }
  }

  private fun roomUpdate(fields: Map<String, Any?>) {
    val id = roomId()
    if (id.isBlank()) return
    updateRoomSafe(id, fields, onSuccess = { _syncIssue.value = null }, onError = { reportSync(it) })
  }

  /** Actualiza la sala; si todavía no existe (NOT_FOUND) la crea una vez y reintenta. */
  private fun updateRoomSafe(id: String, fields: Map<String, Any?>, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
    firestoreRepo.updateRoomFields(id, fields, onSuccess = onSuccess, onError = { e ->
      val notFound = (e as? com.google.firebase.firestore.FirebaseFirestoreException)?.code ==
        com.google.firebase.firestore.FirebaseFirestoreException.Code.NOT_FOUND
      val me = firestoreRepo.currentUserId()
      val members = _memberUids.value
      if (notFound && _guestRoom.value.isBlank() && me != null && members.size == 2) {
        firestoreRepo.ensureRoom(id, members, me, _myDisplayName.value, onError = { onError(it) })
        viewModelScope.launch {
          delay(700)
          firestoreRepo.updateRoomFields(id, fields, onSuccess = onSuccess, onError = onError)
        }
      } else onError(e)
    })
  }

  fun publishGame(gameId: String, values: Map<String, Any?>) {
    if (roomId().isBlank()) {
      _gameError.value = "Aún no estás vinculado con tu pareja."
      return
    }
    val uid = firestoreRepo.currentUserId() ?: return
    updateRoomSafe(
      roomId(), mapOf("games.$gameId" to (values + mapOf("updatedBy" to uid, "t" to System.currentTimeMillis()))),
      onSuccess = { _gameError.value = null },
      onError = { error ->
        Log.e("D-oAmor", "No se pudo publicar $gameId", error); reportSync(error)
        _gameError.value = error.localizedMessage ?: "Error de sincronización"
      }
    )
  }

  // ---------------- Infraestructura compartida (todo vive en el documento de la pareja) ----------------
  fun reportSync(error: Throwable) {
    val denied = (error as? com.google.firebase.firestore.FirebaseFirestoreException)?.code ==
      com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED
    _syncIssue.value = if (denied) {
      "Firebase bloqueó esta acción (PERMISSION_DENIED). Publica el archivo firestore.rules en Firebase → Firestore → Reglas."
    } else (error.localizedMessage ?: "Error de sincronización")
  }

  fun clearSyncIssue() { _syncIssue.value = null }

  // ---------- Sala, invitados ----------
  @Suppress("UNCHECKED_CAST")
  private fun applyRoom(room: Map<String, Any>?) {
    _gamesDoc.value = (room?.get("games") as? Map<String, Any>) ?: emptyMap()
    val players = room?.get("players") as? Map<String, Any?>
    _roomNames.value = players?.mapNotNull { (k, v) -> ((v as? Map<String, Any?>)?.get("name") as? String)?.let { k to it } }?.toMap() ?: emptyMap()
    _guestUids.value = (room?.get("guestUids") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
    _inviteCode.value = (room?.get("inviteCode") as? String) ?: ""
    if (_guestRoom.value.isNotBlank()) {
      val mem = (room?.get("memberUids") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
      if (mem.isNotEmpty()) _memberUids.value = mem.sorted()
    }
    val me = firestoreRepo.currentUserId() ?: return
    val games = _gamesDoc.value as Map<String, Any?>
    val walkie = games["walkie"] as? Map<String, Any?>
    // Cada emisor tiene su propio campo: dos grabaciones simultáneas no se pisan.
    walkie?.forEach { (key, value) ->
      if (key.startsWith("msg_") && value is Map<*, *>) {
        val message = value
        val by = message["by"] as? String ?: key.removePrefix("msg_")
        val id = (message["id"] as? Number)?.toLong() ?: 0L
        val data = message["data"] as? String
        if (id > 0L && id != (lastWalkieIdsBySender[by] ?: -1L)) {
          lastWalkieIdsBySender[by] = id
          if (roomSeen && by != me && data != null) playWalkie(data)
        }
      }
    }
    val wid = (walkie?.get("id") as? Number)?.toLong()
    if (wid != null && wid != lastWalkieId) {
      lastWalkieId = wid
      val by = walkie?.get("by") as? String
      val data = walkie?.get("data") as? String
      if (roomSeen && by != me && data != null) playWalkie(data)
    }
    val lock = games["walkieLock"] as? Map<String, Any?>
    val lockBy = (lock?.get("by") as? String) ?: ""
    val lockT = (lock?.get("t") as? Number)?.toLong() ?: 0L
    _partnerTalking.value = lockBy.isNotEmpty() && lockBy != me && System.currentTimeMillis() - lockT < 30_000L
    roomSeen = true
  }

  private var walkieDocSeen = false
  private fun applyWalkie(w: Map<String, Any>?) {
    val me = firestoreRepo.currentUserId() ?: return
    w?.forEach { (key, value) ->
      if (key.startsWith("msg_") && value is Map<*, *>) {
        val message = value
        val by = message["by"] as? String ?: key.removePrefix("msg_")
        val id = (message["id"] as? Number)?.toLong() ?: 0L
        val data = message["data"] as? String
        if (id > 0L && id != (lastWalkieIdsBySender[by] ?: -1L)) {
          lastWalkieIdsBySender[by] = id
          if (walkieDocSeen && by != me && data != null) playWalkie(data)
        }
      }
    }
    val wid = (w?.get("id") as? Number)?.toLong()
    if (wid != null && wid != lastWalkieId) {
      lastWalkieId = wid
      val by = w?.get("by") as? String
      val data = w?.get("data") as? String
      if (walkieDocSeen && by != me && data != null) playWalkie(data)
    }
    val lockBy = (w?.get("lockBy") as? String) ?: ""
    val lockT = (w?.get("lockT") as? Number)?.toLong() ?: 0L
    if (lockBy.isNotEmpty() || _guestRoom.value.isBlank()) {
      _partnerTalking.value = lockBy.isNotEmpty() && lockBy != me && System.currentTimeMillis() - lockT < 30_000L
    }
    walkieDocSeen = true
  }

  /** Escribe el estado del walkie: pareja → documento de la pareja (y sala); invitado → sala. */
  private fun walkieWrite(coupleFields: Map<String, Any?>, roomGame: String, roomFields: Map<String, Any?>, publishWhole: Boolean) {
    val code = pairingCode.value.trim()
    if (_guestRoom.value.isBlank() && code.isNotBlank()) {
      firestoreRepo.setWalkie(code, coupleFields, onError = { reportSync(it) })
    }
    if (publishWhole) publishGame(roomGame, roomFields) else patchGame(roomGame, roomFields)
  }

  private fun maybeEnsureRoom() {
    if (_guestRoom.value.isNotBlank()) return
    val code = pairingCode.value.trim()
    val me = firestoreRepo.currentUserId() ?: return
    val members = _memberUids.value
    if (code.isBlank() || members.size != 2) return
    val key = code + "|" + members.joinToString(",") + "|" + _myDisplayName.value
    if (key == roomEnsuredKey) return
    roomEnsuredKey = key
    firestoreRepo.ensureRoom(roomKey(code), members, me, _myDisplayName.value, onError = { error -> reportSync(error) })
  }

  fun generateGuestCode() {
    val room = roomId()
    if (room.isBlank()) {
      _syncIssue.value = "Primero deben vincularse los dos teléfonos. Después podrás generar el código de invitado."
      return
    }
    if (_guestRoom.value.isNotBlank()) {
      _syncIssue.value = "Los invitados no pueden generar códigos; debe hacerlo uno de los miembros de la pareja."
      return
    }
    val uid = firestoreRepo.currentUserId()
    if (uid.isNullOrBlank()) {
      _syncIssue.value = "Iniciando sesión con Firebase. Esperá unos segundos y volvé a intentarlo."
      return
    }
    // Código numérico de 4 dígitos.
    val code = (1000..9999).random().toString()
    val old = _inviteCode.value
    if (old.isNotBlank()) firestoreRepo.deactivateInvite(old)
    _syncIssue.value = null
    val createInvite = {
      firestoreRepo.createGuestInvite(room, code, uid,
        onDone = {
          _inviteCode.value = code
          roomUpdate(mapOf("inviteCode" to code))
        },
        onError = { reportSync(it) })
    }
    // La regla de guestInvites exige que la sala exista: se asegura antes de crear el código.
    val members = _memberUids.value
    if (members.size == 2) {
      firestoreRepo.ensureRoom(room, members, uid, _myDisplayName.value, onError = { reportSync(it) }, onSuccess = { createInvite() })
    } else createInvite()
  }

  fun revokeGuestCode() {
    val old = _inviteCode.value
    if (old.isNotBlank()) firestoreRepo.deactivateInvite(old)
    roomUpdate(mapOf("inviteCode" to ""))
  }

  fun kickGuest(uid: String) {
    roomUpdate(mapOf("guestUids" to FieldValue.arrayRemove(uid), "players.$uid" to FieldValue.delete()))
  }

  /** Inicia sesión anónima apenas se abre la pantalla de invitado (así nunca queda "iniciando sesión"). */
  fun prepareGuestSession() {
    firestoreRepo.ensureSignedIn(onDone = { _guestError.value = null }, onError = { _guestError.value = guestAuthMessage(it) })
  }

  private fun guestAuthMessage(e: Exception): String {
    val m = (e.localizedMessage ?: "").lowercase()
    return if (m.contains("operation") && m.contains("not allowed") || m.contains("admin_only") || m.contains("disabled"))
      "No se pudo iniciar sesión: en Firebase → Authentication → Método de acceso, activa «Anónimo»."
    else "No se pudo iniciar sesión (revisa tu internet): ${e.localizedMessage ?: ""}"
  }

  fun joinAsGuest(code: String, name: String) {
    val c = code.trim().filter { it.isDigit() }
    if (c.length != 4 || name.isBlank()) { _guestError.value = "Escribe el código de 4 dígitos y tu nombre."; return }
    _guestError.value = null
    firestoreRepo.ensureSignedIn(
      onDone = { uid ->
        firestoreRepo.joinRoomAsGuest(c, uid, name.trim().take(24),
          onDone = { roomId ->
            getApplication<Application>().getSharedPreferences("doamor_prefs", android.content.Context.MODE_PRIVATE).edit().putString("guest_room", roomId).apply()
            _guestError.value = null
            _guestRoom.value = roomId
          },
          onError = { e ->
            val code2 = (e as? com.google.firebase.firestore.FirebaseFirestoreException)?.code
            _guestError.value = when (code2) {
              com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED -> "No se pudo entrar: la sala ya tiene 2 invitados o el código venció."
              com.google.firebase.firestore.FirebaseFirestoreException.Code.NOT_FOUND -> "La sala todavía no está lista. Que la pareja abra la app y genere el código otra vez."
              else -> e.localizedMessage ?: "No se pudo entrar"
            }
          }
        )
      },
      onError = { _guestError.value = guestAuthMessage(it) }
    )
  }

  fun leaveGuest() {
    getApplication<Application>().getSharedPreferences("doamor_prefs", android.content.Context.MODE_PRIVATE).edit().remove("guest_room").apply()
    _guestRoom.value = ""
  }

  fun setSwapAvatars(on: Boolean) {
    getApplication<Application>().getSharedPreferences("doamor_prefs", android.content.Context.MODE_PRIVATE).edit().putBoolean("swap_avatars", on).apply()
    _swapAvatars.value = on
  }

  private fun publishCalendar() {
    docUpdate(mapOf("calendar" to repository.calendarEvents.value.map { e ->
      mapOf(
        "id" to e.id, "title" to e.title, "description" to e.description, "dateMillis" to e.dateMillis,
        "eventType" to e.eventType.name, "location" to e.location, "done" to e.done
      )
    }))
  }

  fun deleteCalendarEvent(id: String) {
    repository.replaceCalendarEvents(repository.calendarEvents.value.filter { it.id != id })
    publishCalendar()
  }

  fun toggleEventDone(id: String) {
    repository.replaceCalendarEvents(repository.calendarEvents.value.map { if (it.id == id) it.copy(done = !it.done) else it })
    publishCalendar()
  }

  fun docUpdate(fields: Map<String, Any?>) {
    val code = pairingCode.value.trim()
    if (code.isBlank()) return
    firestoreRepo.updateCoupleFields(code, fields, onSuccess = { _syncIssue.value = null }, onError = { reportSync(it) })
  }

  /** Cambia solo ciertos campos de un juego (sin pisar lo que escribe tu pareja). */
  fun patchGame(gameId: String, fields: Map<String, Any?>) {
    roomUpdate(fields.mapKeys { "games.$gameId.${it.key}" })
  }

  fun addCredits(delta: Long) {
    val me = myUid() ?: return
    docUpdate(mapOf("wallet.$me" to FieldValue.increment(delta)))
  }

  fun trySpend(cost: Long): Boolean {
    if (_myCredits.value < cost) return false
    addCredits(-cost)
    return true
  }

  fun updateMyName(name: String) {
    val me = myUid() ?: return
    val clean = name.trim().take(24)
    if (clean.isBlank()) return
    docUpdate(mapOf("profiles.$me.name" to clean, "profiles.$me.t" to System.currentTimeMillis()))
    _myDisplayName.value = clean
  }

  fun updateMyPhoto(uri: Uri) {
    val me = myUid() ?: return
    viewModelScope.launch {
      val data = withContext(Dispatchers.IO) { runCatching { compressPhotoToBase64(uri, 256, 60_000) }.getOrNull() }
      if (data != null) docUpdate(mapOf("profiles.$me.photo" to data, "profiles.$me.t" to System.currentTimeMillis()))
    }
  }

  fun sendGift(giftId: String): Boolean {
    val g = com.example.model.GIFTS.firstOrNull { it.id == giftId } ?: return false
    val me = myUid() ?: return false
    val partner = _memberUids.value.firstOrNull { it != me } ?: return false
    if (!trySpend(g.price)) return false
    docUpdate(mapOf("gift.$partner" to mapOf("id" to g.id, "from" to me, "t" to System.currentTimeMillis())))
    firestoreRepo.sendMessage(coupleId = pairingCode.value.trim(), content = "🎁 Te envió un regalo: ${g.emoji} ${g.name}", type = MessageType.STICKER)
    soundEngine.playShortSfx(BuzzerSoundEngine.ShortSfxType.SUCCESS)
    return true
  }

  fun dismissGift() { _giftEvent.value = null }

  @Suppress("UNCHECKED_CAST")
  private fun retosOf(uid: String): List<Map<String, Any?>> =
    ((_coupleDoc.value["retos"] as? Map<String, Any?>)?.get(uid) as? List<Map<String, Any?>>) ?: emptyList()

  fun sendReto(text: String) {
    val me = myUid() ?: return
    val partner = _memberUids.value.firstOrNull { it != me } ?: return
    val item: Map<String, Any?> = mapOf("id" to java.util.UUID.randomUUID().toString(), "text" to text, "from" to me)
    docUpdate(mapOf("retos.$partner" to (retosOf(partner) + item)))
    firestoreRepo.sendMessage(coupleId = pairingCode.value.trim(), content = "🎴 Te envié un reto: $text", type = MessageType.TEXT)
    soundEngine.playShortSfx(BuzzerSoundEngine.ShortSfxType.KISS)
  }

  fun completeReto(id: String) {
    val me = myUid() ?: return
    val list = retosOf(me)
    val item = list.firstOrNull { it["id"] == id } ?: return
    docUpdate(mapOf("retos.$me" to list.filter { it["id"] != id }))
    addCredits(40)
    firestoreRepo.sendMessage(coupleId = pairingCode.value.trim(), content = "✅ ¡Reto cumplido! ${item["text"] ?: ""}", type = MessageType.TEXT)
    soundEngine.playShortSfx(BuzzerSoundEngine.ShortSfxType.SUCCESS)
  }

  @Suppress("UNCHECKED_CAST")
  private fun applyMeta(meta: Map<String, Any>?) {
    _coupleDoc.value = meta ?: emptyMap()
    val me = firestoreRepo.currentUserId()
    if (me == null) return
    lastAppliedUid = me
    val meId: String = me
    val partner = _memberUids.value.firstOrNull { it != me }
    // Billetera: cada usuario empieza con 1000 fichas.
    val wallet = meta?.get("wallet") as? Map<String, Any?>
    if (me != null && meta != null) {
      if (wallet?.get(meId) == null && !walletInitRequested) {
        walletInitRequested = true
        docUpdate(mapOf("wallet.$me" to 1000L))
      }
      _myCredits.value = (wallet?.get(meId) as? Number)?.toLong() ?: 0L
      _partnerCredits.value = partner?.let { (wallet?.get(it) as? Number)?.toLong() } ?: 0L
    }
    // Perfiles (nombre y foto)
    val profiles = meta?.get("profiles") as? Map<String, Any?>
    val mine = profiles?.get(meId) as? Map<String, Any?>
    val theirs = partner?.let { profiles?.get(it) as? Map<String, Any?> }
    (mine?.get("name") as? String)?.takeIf { it.isNotBlank() }?.let { _myDisplayName.value = it }
    _myPhoto.value = mine?.get("photo") as? String
    (theirs?.get("name") as? String)?.takeIf { it.isNotBlank() }?.let { _partnerDisplayName.value = it }
    _partnerPhoto.value = theirs?.get("photo") as? String
    _partnerBattery.value = (theirs?.get("battery") as? Number)?.toInt() ?: -1
    // Calendario y fechas importantes
    val cal = meta?.get("calendar") as? List<Map<String, Any?>>
    if (cal != null) {
      val events = cal.mapNotNull { m ->
        runCatching {
          CoupleCalendarEvent(
            id = m["id"] as String, title = m["title"] as String, description = (m["description"] as? String) ?: "",
            dateMillis = (m["dateMillis"] as Number).toLong(), eventType = EventType.valueOf(m["eventType"] as String),
            location = (m["location"] as? String) ?: "", done = (m["done"] as? Boolean) ?: false
          )
        }.getOrNull()
      }
      repository.replaceCalendarEvents(events)
    }
    val sd = (meta?.get("startDate") as? Number)?.toLong()
    if (sd != null && sd != lastStartDate) {
      lastStartDate = sd
      repository.setCoupleStartDate(sd)
      updateTimeTogether()
    }
    // Regalos recibidos
    val gm = (meta?.get("gift") as? Map<String, Any?>)?.get(meId) as? Map<String, Any?>
    if (gm != null) {
      val t = (gm["t"] as? Number)?.toLong() ?: 0L
      val prefs = getApplication<Application>().getSharedPreferences("doamor_prefs", android.content.Context.MODE_PRIVATE)
      if (t > prefs.getLong("gift_seen", 0L)) {
        prefs.edit().putLong("gift_seen", t).apply()
        _giftEvent.value = com.example.model.GiftEvent(gm["id"] as? String ?: "", gm["from"] as? String ?: "", t)
        soundEngine.playShortSfx(BuzzerSoundEngine.ShortSfxType.SUCCESS)
      }
    }
    // Retos recibidos
    _myRetos.value = if (me == null) emptyList() else retosOf(me).map {
      com.example.model.RetoItem(it["id"] as? String ?: "", it["text"] as? String ?: "", it["from"] as? String ?: "")
    }
    metaSeen = true
  }

  /** Tonos tipo radio profesional (pip de inicio, "roger beep" al terminar, error si hay alguien hablando). */
  private fun tone(type: Int, ms: Int) {
    runCatching {
      val tg = android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 90)
      tg.startTone(type, ms)
      viewModelScope.launch { delay(ms + 200L); tg.release() }
    }
  }

  private fun notifyIncoming(msgs: List<CoupleMessage>) {
    val last = msgs.lastOrNull() ?: return
    val prev = lastSeenMsgId
    lastSeenMsgId = last.id
    if (prev == null || prev == last.id) return
    if (last.senderId == firestoreRepo.currentUserId()) return
    SoundPrefs.play(getApplication<Application>(), if (last.type == MessageType.AUDIO) SoundPrefs.AUDIO else SoundPrefs.MESSAGE)
  }

  @Suppress("DEPRECATION")
  fun startWalkie() {
    if (_walkieTalking.value) return
    // Full-duplex: cada dispositivo puede grabar mientras el otro habla o reproduce.
    // No bloqueamos el micrófono por el estado remoto, evitando bloqueos de pulsación.
    try {
      val app = getApplication<Application>()
      val f = File(app.cacheDir, "walkie_out.m4a")
      walkieFile = f
      val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(app) else MediaRecorder()
      r.setAudioSource(MediaRecorder.AudioSource.MIC)
      r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
      r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
      r.setAudioChannels(1)
      r.setAudioEncodingBitRate(32000)
      r.setAudioSamplingRate(16000)
      r.setMaxDuration(20000)
      r.setOutputFile(f.absolutePath)
      r.prepare()
      r.start()
      walkieRecorder = r
      walkieStart = System.currentTimeMillis()
      _walkieTalking.value = true
      tone(android.media.ToneGenerator.TONE_PROP_BEEP, 120)
    } catch (e: Exception) {
      Log.w("D-oAmor", "No se pudo iniciar el walkie-talkie", e)
      runCatching { walkieRecorder?.reset() }
      runCatching { walkieRecorder?.release() }
      walkieRecorder = null
      walkieFile?.delete()
      walkieFile = null
      _walkieTalking.value = false
    }
  }

  fun stopWalkie() {
    if (!_walkieTalking.value) return
    _walkieTalking.value = false
    val dur = System.currentTimeMillis() - walkieStart
    tone(android.media.ToneGenerator.TONE_PROP_ACK, 260)
    val ok = runCatching { walkieRecorder?.stop() }.isSuccess
    runCatching { walkieRecorder?.release() }
    walkieRecorder = null
    val f = walkieFile
    walkieFile = null
    if (f == null || !ok || dur < 400 || !f.exists()) { f?.delete(); return }
    viewModelScope.launch {
      val data = withContext(Dispatchers.IO) { runCatching { Base64.encodeToString(f.readBytes(), Base64.NO_WRAP) }.getOrNull() }
      if (data != null && data.length < 700_000) {
        addCredits(200)
        val sender = myUid() ?: return@launch
        val wid = System.currentTimeMillis()
        lastWalkieIdsBySender[sender] = wid
        val message = mapOf<String, Any?>("id" to wid, "by" to sender, "data" to data)
        // Campos independientes por usuario para que una grabación no sobrescriba la otra.
        walkieWrite(mapOf("msg_$sender" to message), "walkie", mapOf("msg_$sender" to message), false)
      }
      withContext(Dispatchers.IO) { runCatching { f.delete() } }
    }
  }

  private fun playWalkie(b64: String) {
    viewModelScope.launch {
      try {
        val f = File(getApplication<Application>().cacheDir, "walkie_in_${System.currentTimeMillis()}.m4a")
        withContext(Dispatchers.IO) { f.writeBytes(Base64.decode(b64, Base64.DEFAULT)) }
        _walkieIncoming.value = true
        // Volumen de música al máximo audible para que se escuche la voz.
        runCatching {
          val am = getApplication<Application>().getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
          val max = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
          if (am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) < max / 2) am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, max / 2, 0)
        }
        val mp = MediaPlayer()
        mp.setAudioAttributes(
          android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        )
        mp.setDataSource(f.absolutePath)
        mp.setVolume(1f, 1f)
        mp.setOnCompletionListener { player -> player.release(); _walkieIncoming.value = false; f.delete(); tone(android.media.ToneGenerator.TONE_PROP_ACK, 260) }
        mp.setOnErrorListener { player, what, extra ->
          Log.w("D-oAmor", "Error reproduciendo walkie: $what/$extra")
          player.release(); _walkieIncoming.value = false; true
        }
        mp.setOnPreparedListener { it.start() }
        mp.prepareAsync()
      } catch (e: Exception) {
        Log.w("D-oAmor", "No se pudo reproducir el walkie", e)
        _walkieIncoming.value = false
      }
    }
  }

  fun gameSfx(kind: Int) {
    val sfxName = when (kind) { 0 -> "sfx_move"; 1 -> "sfx_start"; else -> "sfx_win" }
    if (com.example.audio.GameSfx.play(getApplication(), sfxName)) return
    soundEngine.playShortSfx(
      when (kind) {
        0 -> BuzzerSoundEngine.ShortSfxType.MESSAGE_SENT
        1 -> BuzzerSoundEngine.ShortSfxType.KISS
        else -> BuzzerSoundEngine.ShortSfxType.SUCCESS
      }
    )
  }

  // --- CHAT ACTIONS ---
  fun sendTextMessage(text: String) {
    if (text.isBlank()) return
    val content = text.trim()
    firestoreRepo.sendMessage(coupleId = pairingCode.value, content = content, type = MessageType.TEXT)
    soundEngine.playShortSfx(BuzzerSoundEngine.ShortSfxType.MESSAGE_SENT)
  }

  fun sendSticker(emoji: String, title: String) {
    val content = "$emoji $title"
    firestoreRepo.sendMessage(coupleId = pairingCode.value, content = content, type = MessageType.STICKER)
    soundEngine.playShortSfx(BuzzerSoundEngine.ShortSfxType.KISS)
  }

  @Suppress("MissingPermission", "DEPRECATION")
  fun sendLocation() {
    val ctx = getApplication<Application>()
    val lm = ctx.getSystemService(android.content.Context.LOCATION_SERVICE) as? android.location.LocationManager
    val coupleId = pairingCode.value.trim()
    if (lm == null || coupleId.isBlank()) return
    fun post(loc: android.location.Location) {
      firestoreRepo.sendMessage(
        coupleId = coupleId, content = "📍 Mi ubicación actual", type = MessageType.LOCATION,
        locName = "Toca para abrir en el mapa", lat = loc.latitude, lng = loc.longitude
      )
      soundEngine.playShortSfx(BuzzerSoundEngine.ShortSfxType.MESSAGE_SENT)
    }
    try {
      val last = listOf("gps", "network", "passive").mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
        .maxByOrNull { it.time }
      if (last != null && System.currentTimeMillis() - last.time < 10 * 60_000L) { post(last); return }
      val provider = listOf("gps", "network").firstOrNull { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
      if (provider == null) { _syncIssue.value = "Activa la ubicación (GPS) del teléfono para compartirla."; return }
      lm.requestSingleUpdate(provider, object : android.location.LocationListener {
        override fun onLocationChanged(location: android.location.Location) { post(location) }
        @Deprecated("Deprecated in Java") override fun onStatusChanged(p: String?, st: Int, b: android.os.Bundle?) {}
        override fun onProviderEnabled(p: String) {}
        override fun onProviderDisabled(p: String) {}
      }, android.os.Looper.getMainLooper())
    } catch (e: SecurityException) {
      _syncIssue.value = "Falta el permiso de ubicación."
    } catch (e: Exception) {
      Log.w("D-oAmor", "No se pudo obtener la ubicación", e)
    }
  }

  fun sendPhoto(uri: Uri) {
    val coupleId = pairingCode.value.trim()
    if (coupleId.isBlank()) return
    viewModelScope.launch {
      // La foto se reduce y viaja dentro del mensaje: no depende de Firebase Storage (plan de pago).
      val data = withContext(Dispatchers.IO) { runCatching { compressPhotoToBase64(uri) }.getOrNull() }
      if (data == null) {
        Log.e("D-oAmor", "No se pudo preparar la foto")
        return@launch
      }
      firestoreRepo.sendMessage(
        coupleId = coupleId, content = "📷 Foto", type = MessageType.PHOTO, mediaData = data,
        onSuccess = { soundEngine.playShortSfx(BuzzerSoundEngine.ShortSfxType.MESSAGE_SENT) },
        onError = { error -> Log.e("D-oAmor", "No se pudo enviar la foto", error); reportSync(error) }
      )
    }
  }

  private fun rotateByExif(resolver: android.content.ContentResolver, uri: Uri, bmp: Bitmap): Bitmap {
    val o = runCatching {
      resolver.openInputStream(uri)?.use { android.media.ExifInterface(it).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1) }
    }.getOrNull() ?: 1
    val deg = when (o) { 6 -> 90f; 3 -> 180f; 8 -> 270f; else -> 0f }
    if (deg == 0f) return bmp
    val m = android.graphics.Matrix().apply { postRotate(deg) }
    return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
  }

  private fun compressPhotoToBase64(uri: Uri, startSide: Int = 1024, maxBytes: Int = 600_000): String? {
    val resolver = getApplication<Application>().contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var maxSide = startSide
    var quality = 75
    for (attempt in 0 until 5) {
      val longest = maxOf(bounds.outWidth, bounds.outHeight)
      var sample = 1
      while (longest / (sample * 2) >= maxSide) sample *= 2
      val opts = BitmapFactory.Options().apply { inSampleSize = sample }
      val bmp0 = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
      val bmp = rotateByExif(resolver, uri, bmp0)
      val scale = maxSide.toFloat() / maxOf(bmp.width, bmp.height)
      val out = if (scale < 1f) {
        Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt().coerceAtLeast(1), (bmp.height * scale).toInt().coerceAtLeast(1), true)
      } else bmp
      val stream = java.io.ByteArrayOutputStream()
      out.compress(Bitmap.CompressFormat.JPEG, quality, stream)
      val bytes = stream.toByteArray()
      if (bytes.size <= maxBytes) return Base64.encodeToString(bytes, Base64.NO_WRAP)
      maxSide = (maxSide * 0.8).toInt()
      quality = (quality - 8).coerceAtLeast(40)
    }
    return null
  }

  @Suppress("DEPRECATION")
  fun startVoiceRecording() {
    if (_isRecordingVoice.value) return
    try {
      val file = File(getApplication<Application>().cacheDir, "voice_${System.currentTimeMillis()}.m4a")
      voiceFile = file
      val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(getApplication()) else MediaRecorder()
      recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
      recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
      recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
      recorder.setAudioChannels(1)
      recorder.setAudioEncodingBitRate(32000)
      recorder.setAudioSamplingRate(16000)
      recorder.setOutputFile(file.absolutePath)
      recorder.prepare()
      recorder.start()
      mediaRecorder = recorder
      _isRecordingVoice.value = true
      _voiceRecordingDurationSec.value = 0
      voiceRecordJob?.cancel()
      voiceRecordJob = viewModelScope.launch {
        while (isActive && _isRecordingVoice.value) {
          delay(1000)
          _voiceRecordingDurationSec.value++
          if (_voiceRecordingDurationSec.value >= 90) { finishVoiceRecording(); break }
        }
      }
    } catch (error: Exception) {
      Log.e("D-oAmor", "No se pudo iniciar la grabación", error); reportSync(error)
      runCatching { mediaRecorder?.release() }
      mediaRecorder = null
      _isRecordingVoice.value = false
    }
  }

  fun finishVoiceRecording() {
    if (!_isRecordingVoice.value) return
    val rawDuration = _voiceRecordingDurationSec.value
    val duration = rawDuration.coerceAtLeast(1)
    _isRecordingVoice.value = false
    voiceRecordJob?.cancel()
    voiceRecordJob = null
    val stopped = runCatching { mediaRecorder?.stop() }.onFailure { Log.w("D-oAmor", "Grabación demasiado corta", it) }.isSuccess
    runCatching { mediaRecorder?.release() }
    mediaRecorder = null
    val file = voiceFile
    val coupleId = pairingCode.value.trim()
    _voiceRecordingDurationSec.value = 0
    if (!stopped || rawDuration < 1 || file == null || !file.exists() || file.length() <= 0 || coupleId.isBlank()) {
      file?.delete()
      return
    }
    viewModelScope.launch {
      val data = withContext(Dispatchers.IO) {
        runCatching {
          val bytes = file.readBytes()
          if (bytes.size > 700_000) null else Base64.encodeToString(bytes, Base64.NO_WRAP)
        }.getOrNull()
      }
      if (data == null) {
        Log.e("D-oAmor", "No se pudo preparar el audio")
        file.delete()
        return@launch
      }
      val content = "🎙️ Nota de voz (${String.format("%d:%02d", duration / 60, duration % 60)})"
      firestoreRepo.sendMessage(
        coupleId = coupleId, content = content, type = MessageType.AUDIO, audioDurationSec = duration, mediaData = data,
        onSuccess = { soundEngine.playShortSfx(BuzzerSoundEngine.ShortSfxType.MESSAGE_SENT); file.delete(); addCredits(200) },
        onError = { error -> Log.e("D-oAmor", "No se pudo enviar el audio", error); reportSync(error) }
      )
    }
  }

  fun cancelVoiceRecording() {
    _isRecordingVoice.value = false
    voiceRecordJob?.cancel()
    voiceRecordJob = null
    runCatching { mediaRecorder?.stop() }
    runCatching { mediaRecorder?.release() }
    mediaRecorder = null
    voiceFile?.delete()
    voiceFile = null
    _voiceRecordingDurationSec.value = 0
  }

  fun toggleAudioPlayback(messageId: String, durationSec: Int, mediaUrl: String? = null, mediaData: String? = null) {
    if (_playingAudioMessageId.value == messageId) {
      runCatching { mediaPlayer?.stop() }
      runCatching { mediaPlayer?.release() }
      mediaPlayer = null
      _playingAudioMessageId.value = null
      return
    }
    if (mediaUrl.isNullOrBlank() && mediaData.isNullOrBlank()) return
    runCatching { mediaPlayer?.release() }
    try {
      val source: String = if (!mediaData.isNullOrBlank()) {
        val tmp = File(getApplication<Application>().cacheDir, "play_${messageId.filter { it.isLetterOrDigit() }}.m4a")
        if (!tmp.exists()) tmp.writeBytes(Base64.decode(mediaData, Base64.DEFAULT))
        tmp.absolutePath
      } else mediaUrl.orEmpty()
      mediaPlayer = MediaPlayer().apply {
        setDataSource(source)
        setOnCompletionListener {
          _playingAudioMessageId.value = null
          runCatching { it.release() }
          mediaPlayer = null
        }
        setOnErrorListener { player, _, _ ->
          _playingAudioMessageId.value = null
          runCatching { player.release() }
          mediaPlayer = null
          true
        }
        setOnPreparedListener { player -> player.start(); _playingAudioMessageId.value = messageId }
        prepareAsync()
      }
    } catch (error: Exception) {
      Log.e("D-oAmor", "No se pudo reproducir el audio", error); reportSync(error)
      _playingAudioMessageId.value = null
    }
  }

  // --- LLAMADAS: invitación, aceptación y señalización WebRTC por Firestore ---
  @Suppress("DEPRECATION")
  private fun prepareWebRtc(callId: String, coupleId: String, initiator: Boolean, video: Boolean) {
    runCatching { webRtcEngine?.close() }
    seenRemoteCandidates.clear()
    webRtcEngine = try {
      WebRtcCallEngine(getApplication(), object : WebRtcCallEngine.Listener {
        override fun onLocalDescription(type: String, sdp: String) {
          val field = if (type == "offer") "offerSdp" else "answerSdp"
          firestoreRepo.setCallState(coupleId, callId, mapOf(field to sdp)) { error ->
            Log.e("D-oAmor", "No se pudo enviar señal SDP", error); reportSync(error)
          }
        }
        override fun onIceCandidate(candidate: IceCandidate) {
          val side = if (initiator) "offer" else "answer"
          firestoreRepo.addCallIceCandidate(coupleId, callId, side, mapOf(
            "sdpMid" to (candidate.sdpMid ?: ""), "sdpMLineIndex" to candidate.sdpMLineIndex,
            "candidate" to candidate.sdp
          )) { error -> Log.e("D-oAmor", "No se pudo enviar candidato ICE", error); reportSync(error) }
        }
        override fun onLocalVideoTrack(track: VideoTrack?) { _localVideoTrack.value = track }
        override fun onRemoteVideoTrack(track: VideoTrack?) { _remoteVideoTrack.value = track }
        override fun onConnectionState(state: PeerConnection.PeerConnectionState) {
          when (state) {
            PeerConnection.PeerConnectionState.CONNECTED -> {
              _callStatus.value = CallStatus.CONNECTED
              callTimerJob?.cancel()
              callTimerJob = viewModelScope.launch {
                while (isActive && _callStatus.value == CallStatus.CONNECTED) {
                  delay(1000); _callDurationSec.value += 1
                }
              }
              firestoreRepo.setCallState(coupleId, callId, mapOf("status" to "connected"))
            }
            PeerConnection.PeerConnectionState.FAILED, PeerConnection.PeerConnectionState.CLOSED -> {
              if (_callStatus.value == CallStatus.CONNECTED || _callStatus.value == CallStatus.CONNECTING) {
                _syncIssue.value = "La llamada no pudo conectarse (red o servidor TURN). Prueba con datos móviles en ambos teléfonos."
                endCall()
              }
            }
            else -> Unit
          }
        }
        override fun onError(message: String) { Log.e("D-oAmor-WebRTC", message) }
      })
    } catch (error: Exception) {
      Log.e("D-oAmor", "No se pudo iniciar WebRTC", error); null
    }
    val engine = webRtcEngine ?: return
    runCatching {
      val audioManager = getApplication<Application>().getSystemService(Application.AUDIO_SERVICE) as AudioManager
      audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
      audioManager.isSpeakerphoneOn = _isSpeakerOn.value
    }
    if (!engine.create(video)) { Log.e("D-oAmor", "No se pudo crear peer WebRTC"); return }
    listenToRemoteCandidates(callId, coupleId, if (initiator) "answer" else "offer")
  }

  private fun listenToRemoteCandidates(callId: String, coupleId: String, remoteSide: String) {
    candidateListenerJob?.cancel()
    candidateListenerJob = viewModelScope.launch {
      firestoreRepo.observeCallIceCandidates(coupleId, callId, remoteSide).collect { candidates ->
        candidates.forEach { data ->
          val sdp = data["candidate"] as? String ?: return@forEach
          val mid = data["sdpMid"] as? String ?: ""
          val line = (data["sdpMLineIndex"] as? Number)?.toInt() ?: 0
          val key = "$mid|$line|$sdp"
          if (seenRemoteCandidates.add(key)) webRtcEngine?.addRemoteIceCandidate(IceCandidate(mid, line, sdp))
        }
      }
    }
  }

  private fun listenToActiveCall(callId: String, coupleId: String, initiator: Boolean) {
    callListenerJob?.cancel()
    callListenerJob = viewModelScope.launch {
      firestoreRepo.observeCallState(coupleId, callId).collect { data ->
        if (data == null) return@collect
        val status = data["status"] as? String ?: return@collect
        if (status in listOf("rejected", "ended", "missed")) {
          if (_callStatus.value != CallStatus.IDLE) finishLocalCall()
          return@collect
        }
        val engine = webRtcEngine
        if (initiator && engine != null) {
          val answer = data["answerSdp"] as? String
          if (!answer.isNullOrBlank() && data["answerSdpApplied"] != true) {
            engine.acceptAnswer(answer)
            firestoreRepo.setCallState(coupleId, callId, mapOf("answerSdpApplied" to true))
          }
        } else if (!initiator && engine != null) {
          val offer = data["offerSdp"] as? String
          if (!offer.isNullOrBlank() && data["offerSdpApplied"] != true) {
            engine.acceptOffer(offer)
            firestoreRepo.setCallState(coupleId, callId, mapOf("offerSdpApplied" to true))
          }
        }
      }
    }
  }

  fun startCall(isVideo: Boolean) {
    val coupleId = pairingCode.value.trim()
    val uid = firestoreRepo.currentUserId()
    if (coupleId.isBlank() || uid.isNullOrBlank()) {
      Log.w("D-oAmor", "No se puede iniciar llamada: pareja o sesión Firebase ausente"); _syncIssue.value = "No se puede llamar: vincúlate con tu pareja y verifica que tu sesión de Firebase esté iniciada."; return
    }
    if (_callStatus.value != CallStatus.IDLE) return
    val callId = java.util.UUID.randomUUID().toString()
    activeCallId = callId; activeCallCoupleId = coupleId; isCallInitiator = true
    _isVideoCall.value = isVideo; _callStatus.value = CallStatus.OUTGOING; _callDurationSec.value = 0
    runCatching {
      val keepAlive = CallKeepAliveService.startIntent(getApplication(), isVideo)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) getApplication<Application>().startForegroundService(keepAlive)
      else getApplication<Application>().startService(keepAlive)
    }.onFailure { Log.w("D-oAmor", "No se pudo iniciar el servicio de llamada", it) }
    _isMuted.value = false; soundEngine.playShortSfx(BuzzerSoundEngine.ShortSfxType.CALL_RING)
    // Crear primero la invitación permite que la Cloud Function FCM detecte el documento al crearse.
    firestoreRepo.createCallInvitation(coupleId, callId, mapOf(
      "callerUid" to uid, "status" to "ringing", "video" to isVideo, "callId" to callId,
      "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
    ), onSuccess = {
      if (activeCallId == callId) {
        prepareWebRtc(callId, coupleId, true, isVideo)
        webRtcEngine?.createOffer()
        listenToActiveCall(callId, coupleId, true)
      }
    }, onError = { error ->
      Log.e("D-oAmor", "No se pudo crear invitación", error); reportSync(error)
      finishLocalCall()
    })
    callTimerJob?.cancel()
    callTimerJob = viewModelScope.launch {
      delay(30_000)
      if (_callStatus.value == CallStatus.OUTGOING) {
        firestoreRepo.setCallState(coupleId, callId, mapOf("status" to "missed"))
        finishLocalCall()
      }
    }
  }

  fun acceptIncomingCall() {
    val callId = activeCallId ?: return
    val coupleId = activeCallCoupleId ?: return
    val uid = firestoreRepo.currentUserId() ?: return
    if (_callStatus.value != CallStatus.INCOMING) return
    _callStatus.value = CallStatus.CONNECTING
    _callDurationSec.value = 0
    runCatching {
      val keepAlive = CallKeepAliveService.startIntent(getApplication(), _isVideoCall.value)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) getApplication<Application>().startForegroundService(keepAlive)
      else getApplication<Application>().startService(keepAlive)
    }.onFailure { Log.w("D-oAmor", "No se pudo iniciar el servicio de llamada", it) }
    prepareWebRtc(callId, coupleId, false, _isVideoCall.value)
    firestoreRepo.setCallState(coupleId, callId, mapOf("status" to "accepted", "acceptedBy" to uid))
    listenToActiveCall(callId, coupleId, false)
  }

  fun rejectIncomingCall() {
    val callId = activeCallId ?: return
    val coupleId = activeCallCoupleId ?: return
    firestoreRepo.setCallState(coupleId, callId, mapOf("status" to "rejected"))
    finishLocalCall()
  }

  private fun finishLocalCall() {
    // stopService no intenta arrancar un servicio desde segundo plano en Android O+;
    // así se evita que una llamada ya terminada deje una notificación persistente.
    runCatching { getApplication<Application>().stopService(CallKeepAliveService.stopIntent(getApplication())) }
      .onFailure { Log.w("D-oAmor", "No se pudo detener el servicio de llamada", it) }
    _callStatus.value = CallStatus.IDLE
    callTimerJob?.cancel(); callTimerJob = null
    callListenerJob?.cancel(); callListenerJob = null
    candidateListenerJob?.cancel(); candidateListenerJob = null
    runCatching { webRtcEngine?.close() }; webRtcEngine = null
    runCatching {
      val audioManager = getApplication<Application>().getSystemService(Application.AUDIO_SERVICE) as AudioManager
      audioManager.isSpeakerphoneOn = false
      audioManager.mode = AudioManager.MODE_NORMAL
    }
    activeCallId = null; activeCallCoupleId = null; seenRemoteCandidates.clear()
    _localVideoTrack.value = null; _remoteVideoTrack.value = null
    _callDurationSec.value = 0
  }

  fun endCall() {
    val callId = activeCallId; val coupleId = activeCallCoupleId
    val duration = _callDurationSec.value
    val wasConnected = _callStatus.value == CallStatus.CONNECTED
    if (callId != null && coupleId != null) firestoreRepo.setCallState(coupleId, callId, mapOf("status" to "ended"))
    finishLocalCall()
    if (wasConnected && duration > 0) {
      val callTypeStr = if (_isVideoCall.value) "Videollamada" else "Llamada de voz"
      val content = "📞 $callTypeStr finalizada (${String.format("%02d:%02d", duration / 60, duration % 60)})"
      val coupleIdForLog = pairingCode.value.trim()
      if (coupleIdForLog.isNotBlank()) {
        firestoreRepo.sendMessage(coupleId = coupleIdForLog, content = content, type = MessageType.CALL_LOG)
      }
    }
  }

  fun switchCamera() { webRtcEngine?.switchCamera() }

  fun toggleMute() {
    _isMuted.value = !_isMuted.value
    webRtcEngine?.setMuted(_isMuted.value)
  }

  @Suppress("DEPRECATION")
  fun toggleSpeaker() {
    _isSpeakerOn.value = !_isSpeakerOn.value
    runCatching {
      val audioManager = getApplication<Application>().getSystemService(Application.AUDIO_SERVICE) as AudioManager
      audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
      audioManager.isSpeakerphoneOn = _isSpeakerOn.value
    }
  }

  fun sendFloatingHeart() {
    soundEngine.playShortSfx(BuzzerSoundEngine.ShortSfxType.KISS)
    _floatingHearts.value = _floatingHearts.value + System.currentTimeMillis()
  }

  // --- SCREENSAVER & CALENDAR ---
  fun updateScreensaverNote(text: String, emoji: String) {
    repository.updateScreensaverNote(text, emoji)
    firestoreRepo.syncScreensaverNote(pairingCode.value, text, emoji)
    soundEngine.playShortSfx(BuzzerSoundEngine.ShortSfxType.SUCCESS)
  }

  fun setCoupleStartDate(millis: Long) {
    repository.setCoupleStartDate(millis)
    updateTimeTogether()
    lastStartDate = millis
    docUpdate(mapOf("startDate" to millis))
  }

  fun addCalendarEvent(title: String, desc: String, dateMillis: Long, type: EventType, location: String) {
    val event = CoupleCalendarEvent(
      id = java.util.UUID.randomUUID().toString(),
      title = title,
      description = desc,
      dateMillis = dateMillis,
      eventType = type,
      location = location
    )
    repository.addCalendarEvent(title, desc, dateMillis, type, location)
    docUpdate(mapOf("calendar" to repository.calendarEvents.value.map { e ->
      mapOf(
        "id" to e.id, "title" to e.title, "description" to e.description, "dateMillis" to e.dateMillis,
        "eventType" to e.eventType.name, "location" to e.location, "done" to e.done
      )
    }))
    soundEngine.playShortSfx(BuzzerSoundEngine.ShortSfxType.SUCCESS)
  }

  // --- MINIGAMES ---
  fun nextTriviaQuestion() {
    if (triviaQuestions.isEmpty()) return
    _triviaIndex.value = (_triviaIndex.value + 1) % triviaQuestions.size
    val code = pairingCode.value.trim()
    if (code.isNotBlank()) firestoreRepo.publishNamedGameState(code, "trivia", mapOf("questionIndex" to _triviaIndex.value)) { error ->
      Log.e("D-oAmor", "No se pudo sincronizar la trivia", error); reportSync(error)
    }
    soundEngine.playShortSfx(BuzzerSoundEngine.ShortSfxType.SUCCESS)
  }

  fun publishWheelPrize(prize: String) {
    _sharedWheelPrize.value = prize
    val code = pairingCode.value.trim()
    if (code.isNotBlank()) firestoreRepo.publishNamedGameState(code, "wheel", mapOf("selectedPrize" to prize, "selectedAt" to System.currentTimeMillis())) { error ->
      Log.e("D-oAmor", "No se pudo sincronizar la ruleta", error); reportSync(error)
    }
  }

  fun playTicTacToe(index: Int) {
    if (_tictactoeBoard.value[index].isNotEmpty() || _tictactoeWinner.value != null) return

    val currentSymbol = _tictactoeTurn.value
    val newBoard = _tictactoeBoard.value.toMutableList()
    newBoard[index] = currentSymbol
    _tictactoeBoard.value = newBoard

    soundEngine.playShortSfx(BuzzerSoundEngine.ShortSfxType.MESSAGE_SENT)

    // Check winner
    val winner = checkTicTacToeWinner(newBoard)
    if (winner != null) {
      _tictactoeWinner.value = winner
      soundEngine.playShortSfx(BuzzerSoundEngine.ShortSfxType.SUCCESS)
      if (winner == "❤️") _scoreHearts.value++ else if (winner == "💋") _scoreKisses.value++
    } else if (newBoard.none { it.isEmpty() }) {
      _tictactoeWinner.value = "EMPATE"
    } else {
      _tictactoeTurn.value = if (currentSymbol == "❤️") "💋" else "❤️"
    }
    publishTicTacToeState()
  }

  fun resetTicTacToe() {
    _tictactoeBoard.value = List(9) { "" }
    _tictactoeWinner.value = null
    _tictactoeTurn.value = "❤️"
    publishTicTacToeState()
  }

  private fun publishTicTacToeState() {
    val code = pairingCode.value.trim()
    if (code.isBlank()) return
    val state = com.example.data.firestore.FirestoreCoupleRepository.SharedGameState(
      board = _tictactoeBoard.value, turn = _tictactoeTurn.value, winner = _tictactoeWinner.value,
      scoreHearts = _scoreHearts.value, scoreKisses = _scoreKisses.value
    )
    firestoreRepo.publishGameState(code, state) { error ->
      Log.e("D-oAmor", "No se pudo sincronizar el juego", error); reportSync(error)
    }
  }

  private fun checkTicTacToeWinner(b: List<String>): String? {
    val lines = listOf(
      listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8), // rows
      listOf(0, 3, 6), listOf(1, 4, 7), listOf(2, 5, 8), // cols
      listOf(0, 4, 8), listOf(2, 4, 6) // diags
    )
    for (line in lines) {
      val (a, bIdx, c) = line
      if (b[a].isNotEmpty() && b[a] == b[bIdx] && b[a] == b[c]) {
        return b[a]
      }
    }
    return null
  }

  fun redeemCoupon(id: String) {
    repository.redeemCoupon(id)
    soundEngine.playShortSfx(BuzzerSoundEngine.ShortSfxType.KISS)
  }

  fun revealCoupon(id: String) {
    repository.revealCoupon(id)
    soundEngine.playShortSfx(BuzzerSoundEngine.ShortSfxType.SUCCESS)
  }

  fun switchUser(userId: String) {
    repository.switchUser(userId)
  }

  fun updateTheme(theme: AppThemeMode) {
    repository.updateTheme(theme)
  }

  fun updateSoundType(sound: BuzzerSoundType) {
    repository.updateSoundType(sound)
    CoupleBuzzerService.saveSound(getApplication(), sound)
    soundEngine.startBuzzer(sound)
    viewModelScope.launch {
      delay(800)
      soundEngine.stopBuzzer()
    }
  }

  fun toggleVibration(enabled: Boolean) {
    repository.toggleVibration(enabled)
  }

  override fun onCleared() {
    soundEngine.stopBuzzer()
    callTimerJob?.cancel()
    callListenerJob?.cancel()
    candidateListenerJob?.cancel()
    runCatching { webRtcEngine?.close() }
    voiceRecordJob?.cancel()
    buzzHoldJob?.cancel()
    incomingBuzzJob?.cancel()
    runCatching { mediaRecorder?.stop() }
    runCatching { mediaRecorder?.release() }
    runCatching { mediaPlayer?.stop() }
    runCatching { mediaPlayer?.release() }
    mediaRecorder = null
    mediaPlayer = null
    super.onCleared()
  }

}
