package com.example.model

enum class MessageType {
  TEXT,
  AUDIO,
  PHOTO,
  LOCATION,
  BUZZ,
  STICKER,
  SCREENSAVER_NOTE,
  CALL_LOG
}

enum class BuzzerSoundType(val title: String, val description: String) {
  PHONE_RING("Tono de llamada", "El mismo tono de una llamada entrante de tu teléfono"),
  CLASSIC_BUZZ("Zumbido Fuerte", "Zumbido eléctrico vibrante de máxima atención"),
  LOVE_SIREN("Sirena Romántica", "Sirena modulada ascendente de emergencia de amor"),
  HEARTBEAT("Latido Intenso", "Pulso sub-grave que imita un corazón latiendo fuerte"),
  CHIMES("Campanadas de Alarma", "Acordes armónicos brillantes y continuos"),
  COSMIC("Pulso Cósmico", "Frecuencia pulsante futurista y envolvente")
}

data class CoupleUser(
  val id: String,
  val name: String,
  val nickname: String,
  val avatar: String, // emoji or image
  val isOnline: Boolean = true,
  val batteryLevel: Int = 92,
  val statusMessage: String = "Pensando en ti ❤️"
)

data class CoupleMessage(
  val id: String,
  val senderId: String,
  val content: String,
  val timestamp: Long = System.currentTimeMillis(),
  val type: MessageType = MessageType.TEXT,
  val isRead: Boolean = true,
  val audioDurationSec: Int = 0,
  val locationName: String? = null,
  val latitude: Double? = null,
  val longitude: Double? = null,
  val photoPlaceholder: String? = null,
  val mediaUrl: String? = null,
  val buzzDurationMs: Long = 0,
  val mediaData: String? = null // foto/audio en base64 (sin necesitar Firebase Storage)
)

data class ScreensaverNote(
  val id: String,
  val note: String,
  val senderName: String,
  val timestamp: Long = System.currentTimeMillis(),
  val emoji: String = "💌",
  val isPinned: Boolean = true
)

enum class EventType(val label: String, val emoji: String) {
  ANNIVERSARY("Aniversario", "💍"),
  DATE_NIGHT("Cita Romántica", "🍷"),
  BIRTHDAY("Cumpleaños", "🎂"),
  TRIP("Viaje Juntos", "✈️"),
  SPECIAL("Momento Especial", "✨")
}

data class CoupleCalendarEvent(
  val id: String,
  val title: String,
  val description: String,
  val dateMillis: Long,
  val eventType: EventType,
  val location: String = "",
  val done: Boolean = false
)

/**
 * Nota compartida de la pareja. Si [dayKey] ("yyyy-MM-dd") no está vacío queda anotada en ese día del calendario;
 * si está vacío es una nota suelta para el buzón. [readBy] guarda quién ya la leyó.
 */
data class CoupleNote(
  val id: String,
  val from: String,
  val text: String,
  val dayKey: String = "",
  val createdAt: Long = 0L,
  val readBy: List<String> = emptyList()
)

data class CoupleTriviaQuestion(
  val id: Int,
  val question: String,
  val category: String, // "Romance", "Divertido", "Futuro", "Secretos"
  val hint: String = ""
)

data class ScratchCoupon(
  val id: String,
  val title: String,
  val description: String,
  val emoji: String,
  var isRedeemed: Boolean = false,
  var isRevealed: Boolean = false
)
