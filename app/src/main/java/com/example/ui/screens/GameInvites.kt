package com.example.ui.screens

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.example.ui.CoupleViewModel

/**
 * Solicitudes de juego. El anfitrión publica en «minigameLobby» el juego y a quién invitó
 * (uid → marca de tiempo de la invitación). Cada invitado responde en su propio documento
 * «gReply_<uid>» con {game, stamp, ok}; así nadie pisa lo que escribe otro.
 */
internal object GameInvites {
  const val NONE = "none"
  const val PENDING = "pending"
  const val ACCEPTED = "accepted"
  const val DECLINED = "declined"

  fun stamps(lobby: Map<String, Any>?): Map<String, Long> =
    (lobby?.get("invited") as? Map<*, *>)?.entries
      ?.mapNotNull { (k, v) -> (k as? String)?.let { it to ((v as? Number)?.toLong() ?: 0L) } }?.toMap() ?: emptyMap()

  @Suppress("UNCHECKED_CAST")
  fun status(lobby: Map<String, Any>?, all: Map<String, Any>, uid: String?): String {
    if (uid == null) return NONE
    val game = lobby?.get("game") as? String
    if (game.isNullOrBlank()) return NONE
    val stamp = stamps(lobby)[uid] ?: return NONE
    val reply = all["gReply_$uid"] as? Map<String, Any?> ?: return PENDING
    val sameInvite = reply["game"] == game && ((reply["stamp"] as? Number)?.toLong() ?: -1L) == stamp
    if (!sameInvite) return PENDING
    return if (reply["ok"] == true) ACCEPTED else DECLINED
  }

  /** Invitados que aceptaron, en el orden en que fueron invitados. */
  fun accepted(lobby: Map<String, Any>?, all: Map<String, Any>): List<String> =
    stamps(lobby).entries.sortedWith(compareBy({ it.value }, { it.key })).map { it.key }.filter { status(lobby, all, it) == ACCEPTED }
}

/** Ventana de «solicitud de juego»: aparece en cualquier pestaña cuando te invitan y todavía no respondiste. */
@Composable
internal fun GameInviteDialog(vm: CoupleViewModel, onAccepted: () -> Unit) {
  val lobbyFlow = remember { vm.gameState("minigameLobby") }
  val lobby by lobbyFlow.collectAsState(initial = null)
  val all by vm.allGameStates().collectAsState(initial = emptyMap())
  val names by vm.roomNames.collectAsState()
  val me = vm.myUid()
  val game = lobby?.get("game") as? String ?: return
  val host = lobby?.get("host") as? String ?: ""
  val started = lobby?.get("started") as? Boolean ?: false
  if (me == null || me == host || started) return
  if (GameInvites.status(lobby, all, me) != GameInvites.PENDING) return
  val info = GAME_LIST.firstOrNull { it.id == game } ?: return
  val stamp = GameInvites.stamps(lobby)[me] ?: return
  fun reply(ok: Boolean) = vm.publishGame("gReply_$me", mapOf("game" to game, "stamp" to stamp, "ok" to ok))
  AlertDialog(
    onDismissRequest = {},
    title = { Text("🎮 Solicitud de juego") },
    text = { Text("${names[host] ?: "Un jugador"} te invita a jugar ${info.emoji} ${info.title}.") },
    confirmButton = { TextButton(onClick = { reply(true); onAccepted() }) { Text("Aceptar") } },
    dismissButton = { TextButton(onClick = { reply(false) }) { Text("Rechazar") } }
  )
}
