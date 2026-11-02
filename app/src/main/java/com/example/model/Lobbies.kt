package com.example.model

/** Datos de las salas públicas (Sala 1-4 y las que crean los usuarios). */
data class LobbyInfo(
  val id: String,
  val name: String,
  val hasPassword: Boolean,
  val online: Int,
  val order: Long,
  val fixed: Boolean
)

object Lobbies {
  const val PREFIX = "lobby_"
  /** Un jugador cuenta como conectado si latió en los últimos 2 minutos. */
  const val PRESENCE_MS = 120_000L
  /** Contraseña inicial de la Sala 4. Cámbiala aquí antes de compilar si quieres otra. */
  const val DEFAULT_SALA4_PASSWORD = "sini4"

  data class Default(val id: String, val name: String, val hasPassword: Boolean, val order: Long)
  val DEFAULTS = listOf(
    Default(PREFIX + "sala1", "Sala 1", false, 1),
    Default(PREFIX + "sala2", "Sala 2", false, 2),
    Default(PREFIX + "sala3", "Sala 3", false, 3),
    Default(PREFIX + "sala4", "Sala 4", true, 4)
  )

  fun hash(password: String): String {
    val d = java.security.MessageDigest.getInstance("SHA-256").digest(("sinijoss-lobby|" + password.trim()).toByteArray())
    return d.joinToString("") { "%02x".format(it) }
  }
}
