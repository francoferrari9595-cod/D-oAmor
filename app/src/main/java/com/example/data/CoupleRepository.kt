package com.example.data

import android.content.Context
import com.example.model.BuzzerSoundType
import com.example.model.CoupleCalendarEvent
import com.example.model.CoupleMessage
import com.example.model.CoupleTriviaQuestion
import com.example.model.CoupleUser
import com.example.model.EventType
import com.example.model.MessageType
import com.example.model.ScratchCoupon
import com.example.model.ScreensaverNote
import com.example.ui.theme.AppThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar
import java.util.UUID

class CoupleRepository(private val context: Context) {

  // Current logged in user (supports switching between user1 and user2 for complete live demo)
  private val _user1 = MutableStateFlow(
    CoupleUser(
      id = "user_1",
      name = "Tú",
      nickname = "Mi Vida",
      avatar = "🧑🏻‍💻",
      isOnline = true,
      batteryLevel = 94,
      statusMessage = "Siempre pensando en ti ❤️"
    )
  )
  val user1: StateFlow<CoupleUser> = _user1.asStateFlow()

  private val _user2 = MutableStateFlow(
    CoupleUser(
      id = "user_2",
      name = "Tu pareja",
      nickname = "Amor",
      avatar = "👩🏼‍🦰",
      isOnline = true,
      batteryLevel = 87,
      statusMessage = "Contando los segundos para verte ✨"
    )
  )
  val user2: StateFlow<CoupleUser> = _user2.asStateFlow()

  // Active perspective (Tú vs Pareja)
  private val _currentUserId = MutableStateFlow("user_1")
  val currentUserId: StateFlow<String> = _currentUserId.asStateFlow()

  // Relationship start date (default: 1 year, 8 months ago from 2026)
  private val defaultStartDate = Calendar.getInstance().apply {
    set(2025, Calendar.FEBRUARY, 14, 18, 0, 0)
  }.timeInMillis

  private val _coupleStartDate = MutableStateFlow(defaultStartDate)
  val coupleStartDate: StateFlow<Long> = _coupleStartDate.asStateFlow()

  // Pairing code
  private val _pairingCode = MutableStateFlow(context.getSharedPreferences("doamor_pairing", Context.MODE_PRIVATE).getString("coupleId", "") ?: "")
  val pairingCode: StateFlow<String> = _pairingCode.asStateFlow()

  /** Updates the active cloud couple ID after Firebase pairing succeeds. */
  fun setPairingCode(coupleId: String) {
    _pairingCode.value = coupleId
    context.getSharedPreferences("doamor_pairing", Context.MODE_PRIVATE)
      .edit().putString("coupleId", coupleId).apply()
  }

  // Theme & Sounds
  private val _appTheme = MutableStateFlow(AppThemeMode.AURORA)
  val appTheme: StateFlow<AppThemeMode> = _appTheme.asStateFlow()

  private val _soundType = MutableStateFlow(BuzzerSoundType.PHONE_RING)
  val soundType: StateFlow<BuzzerSoundType> = _soundType.asStateFlow()

  private val _vibrationEnabled = MutableStateFlow(true)
  val vibrationEnabled: StateFlow<Boolean> = _vibrationEnabled.asStateFlow()

  // Screensaver note
  private val _screensaverNote = MutableStateFlow(
    ScreensaverNote(
      id = "init_note",
      note = "¡Hola mi amor! Dejé este mensaje para cuando prendas tu pantalla. Recuerda que te amo con todo mi corazón y que eres mi persona favorita en el mundo 💕",
      senderName = "D-oAmor",
      emoji = "💌"
    )
  )
  val screensaverNote: StateFlow<ScreensaverNote> = _screensaverNote.asStateFlow()

  // Messages list
  private val _messages = MutableStateFlow<List<CoupleMessage>>(emptyList())
  val messages: StateFlow<List<CoupleMessage>> = _messages.asStateFlow()

  // Shared Calendar Events
  private val _calendarEvents = MutableStateFlow<List<CoupleCalendarEvent>>(getInitialEvents())
  val calendarEvents: StateFlow<List<CoupleCalendarEvent>> = _calendarEvents.asStateFlow()

  // Scratch coupons
  private val _coupons = MutableStateFlow<List<ScratchCoupon>>(getInitialCoupons())
  val coupons: StateFlow<List<ScratchCoupon>> = _coupons.asStateFlow()

  // Couple Trivia list
  val triviaQuestions: List<CoupleTriviaQuestion> = listOf(
    CoupleTriviaQuestion(1, "¿Cuál fue el momento exacto en que te diste cuenta de que te habías enamorado de mí?", "Romance"),
    CoupleTriviaQuestion(2, "¿Qué fue lo primero que pensaste cuando me viste por primera vez?", "Recuerdos"),
    CoupleTriviaQuestion(3, "¿Cuál es nuestro mejor recuerdo juntos hasta hoy?", "Recuerdos"),
    CoupleTriviaQuestion(4, "Si tuviéramos un fin de semana sin planes ni límites, ¿adónde iríamos?", "Futuro"),
    CoupleTriviaQuestion(5, "¿Qué pequeño detalle que hago siempre te alegra el día?", "Complicidad"),
    CoupleTriviaQuestion(6, "¿Cuál es tu comida favorita que hemos compartido?", "Divertido"),
    CoupleTriviaQuestion(7, "¿Qué canción te hace pensar en nosotros inmediatamente?", "Música"),
    CoupleTriviaQuestion(8, "Si fuéramos personajes de una película romántica, ¿cuál seríamos?", "Divertido"),
    CoupleTriviaQuestion(9, "¿Qué meta o sueño juntos te emociona más cumplir este año?", "Futuro"),
    CoupleTriviaQuestion(10, "¿Cuál es el beso nuestro que nunca vas a olvidar?", "Romance"),
    CoupleTriviaQuestion(11, "¿Qué apodo secreto o cariñoso es tu preferido?", "Complicidad"),
    CoupleTriviaQuestion(12, "¿Qué es lo que más admiras de mi personalidad?", "Admiración")
  )

  fun switchUser(userId: String) {
    _currentUserId.value = userId
  }

  fun updateTheme(theme: AppThemeMode) {
    _appTheme.value = theme
  }

  fun updateSoundType(sound: BuzzerSoundType) {
    _soundType.value = sound
  }

  fun toggleVibration(enabled: Boolean) {
    _vibrationEnabled.value = enabled
  }

  fun setCoupleStartDate(millis: Long) {
    _coupleStartDate.value = millis
  }

  fun updateScreensaverNote(text: String, emoji: String) {
    val author = if (_currentUserId.value == "user_1") _user1.value.name else _user2.value.name
    val newNote = ScreensaverNote(
      id = UUID.randomUUID().toString(),
      note = text,
      senderName = author,
      emoji = emoji
    )
    _screensaverNote.value = newNote

    // Also add to chat as special message
    val msg = CoupleMessage(
      id = UUID.randomUUID().toString(),
      senderId = _currentUserId.value,
      content = "📌 Mensaje enviado al salvapantallas: \"$text\" $emoji",
      type = MessageType.SCREENSAVER_NOTE
    )
    _messages.value = _messages.value + msg
  }

  fun sendMessage(content: String, type: MessageType = MessageType.TEXT, audioDurationSec: Int = 0, locName: String? = null, lat: Double? = null, lng: Double? = null, buzzDurationMs: Long = 0) {
    val newMsg = CoupleMessage(
      id = UUID.randomUUID().toString(),
      senderId = _currentUserId.value,
      content = content,
      type = type,
      audioDurationSec = audioDurationSec,
      locationName = locName,
      latitude = lat,
      longitude = lng,
      buzzDurationMs = buzzDurationMs
    )
    _messages.value = _messages.value + newMsg
  }

  fun mergeRemoteMessages(remoteMessages: List<CoupleMessage>) {
    // Remote Firestore IDs are canonical. Replace local echoes with matching cloud messages
    // rather than appending a second copy of the same message.
    val remoteIds = remoteMessages.map { it.id }.toSet()
    val remainingLocal = _messages.value.filter { local ->
      local.id in remoteIds || remoteMessages.none { remote ->
        remote.senderId == local.senderId && remote.type == local.type && remote.content == local.content &&
          kotlin.math.abs(remote.timestamp - local.timestamp) < 10_000
      }
    }
    _messages.value = (remainingLocal.filter { it.id !in remoteIds } + remoteMessages)
      .distinctBy { it.id }.sortedBy { it.timestamp }
  }

  fun replaceRemoteMessages(remoteMessages: List<CoupleMessage>) {
    _messages.value = remoteMessages.sortedBy { it.timestamp }
  }

  fun addCalendarEvent(title: String, desc: String, dateMillis: Long, type: EventType, location: String) {
    val newEvent = CoupleCalendarEvent(
      id = UUID.randomUUID().toString(),
      title = title,
      description = desc,
      dateMillis = dateMillis,
      eventType = type,
      location = location
    )
    _calendarEvents.value = (_calendarEvents.value + newEvent).sortedBy { it.dateMillis }
  }

  fun replaceCalendarEvents(events: List<CoupleCalendarEvent>) {
    _calendarEvents.value = events.sortedBy { it.dateMillis }
  }

  fun redeemCoupon(id: String) {
    _coupons.value = _coupons.value.map {
      if (it.id == id) it.copy(isRedeemed = true, isRevealed = true) else it
    }
  }

  fun revealCoupon(id: String) {
    _coupons.value = _coupons.value.map {
      if (it.id == id) it.copy(isRevealed = true) else it
    }
  }

  fun updateUserName(name: String, nickname: String) {
    if (_currentUserId.value == "user_1") {
      _user1.value = _user1.value.copy(name = name, nickname = nickname)
    } else {
      _user2.value = _user2.value.copy(name = name, nickname = nickname)
    }
  }

  private fun getInitialMessages(): List<CoupleMessage> = emptyList()

  private fun getInitialEvents(): List<CoupleCalendarEvent> {
    val cal = Calendar.getInstance()
    val now = cal.timeInMillis

    cal.add(Calendar.DAY_OF_YEAR, 4)
    val dateNightTime = cal.timeInMillis

    cal.add(Calendar.DAY_OF_YEAR, 20)
    val tripTime = cal.timeInMillis

    return listOf(
      CoupleCalendarEvent(
        id = "e1",
        title = "Cita sorpresa y cena romántica",
        description = "Reservé en nuestro restaurante favorito. ¡Vístete elegante!",
        dateMillis = dateNightTime,
        eventType = EventType.DATE_NIGHT,
        location = "Bistró del Puerto"
      ),
      CoupleCalendarEvent(
        id = "e2",
        title = "Escapada de fin de semana",
        description = "Cabaña en las sierras, fogón y desconexión total juntos.",
        dateMillis = tripTime,
        eventType = EventType.TRIP,
        location = "Cabañas del Valle"
      ),
      CoupleCalendarEvent(
        id = "e3",
        title = "Nuestro Gran Aniversario",
        description = "Celebración de nuestro amor incondicional ❤️",
        dateMillis = defaultStartDate + (365L * 24 * 3600 * 1000 * 2), // 2 years
        eventType = EventType.ANNIVERSARY,
        location = "Donde empezó todo"
      )
    )
  }

  private fun getInitialCoupons(): List<ScratchCoupon> {
    return listOf(
      ScratchCoupon(
        id = "c1",
        title = "Masaje relajante de 20 min",
        description = "Válido por un masaje con aceites aromáticos sin apuro.",
        emoji = "💆‍♀️",
        isRevealed = true
      ),
      ScratchCoupon(
        id = "c2",
        title = "Cena a la luz de las velas",
        description = "El otro cocina su plato favorito y lava los platos.",
        emoji = "🍝",
        isRevealed = false
      ),
      ScratchCoupon(
        id = "c3",
        title = "Vale por 15 besos apasionados",
        description = "Canjeable en cualquier lugar y momento.",
        emoji = "💋",
        isRevealed = false
      ),
      ScratchCoupon(
        id = "c4",
        title = "Elección de película sin quejas",
        description = "Elige la serie o película completa con pochoclos.",
        emoji = "🎬",
        isRevealed = false
      ),
      ScratchCoupon(
        id = "c5",
        title = "Desayuno en la cama",
        description = "Café recién hecho, tostadas y mimos para empezar el día.",
        emoji = "☕",
        isRevealed = false
      ),
      ScratchCoupon(
        id = "c6",
        title = "Pase libre: Decir que SÍ a todo por 1 día",
        description = "Un día completo donde tus deseos son órdenes.",
        emoji = "👑",
        isRevealed = false
      )
    )
  }
}
