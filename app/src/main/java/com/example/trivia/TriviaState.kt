package com.example.trivia

internal fun Any?.tLong(): Long = when (this) { is Number -> toLong(); is com.google.firebase.Timestamp -> toDate().time; else -> 0L }

/** Lectura tipada del estado compartido de la partida (games/trivia). Los campos ausentes toman valores neutros. */
internal class TS(private val m: Map<String, Any?>) {
  val raw: Map<String, Any?> get() = m
  val exists: Boolean get() = m.isNotEmpty() && phase.isNotEmpty()
  private fun str(k: String) = (m[k] as? String).orEmpty()
  private fun list(k: String) = (m[k] as? List<*>)?.map { it.toString() } ?: emptyList()
  val phase get() = str("phase")           // ready, question, answering, judging, review, reveal, final
  val ev get() = m["ev"].tLong()
  val gid get() = str("gid")
  val rid get() = str("rid")
  val seats get() = list("seats")
  val sc: List<Long> get() = (m["sc"] as? List<*>)?.map { it.tLong() } ?: emptyList()
  val round get() = m["r"].tLong().toInt()
  val total get() = m["total"].tLong().toInt()
  val qs: List<Map<String, Any?>> get() = (m["qs"] as? List<*>)?.filterIsInstance<Map<*, *>>()?.map { mm -> mm.entries.associate { e -> e.key.toString() to e.value } } ?: emptyList()
  val curQ: Map<String, Any?>? get() = qs.getOrNull(round)
  // configuración
  val qSec get() = m["cQ"].tLong().toInt().coerceAtLeast(5)
  val ansSec get() = m["cA"].tLong().toInt().coerceAtLeast(3)
  val ptsOk get() = m["cOk"].tLong().toInt()
  val pen get() = m["cPen"].tLong().toInt()
  val speed get() = (m["cSp"] as? Boolean) != false
  val reb get() = m["cReb"].tLong().toInt()
  // reloj (todas las horas son del SERVIDOR, en ms)
  val baseUsed get() = m["baseUsed"].tLong()
  val resumeAt get() = m["resumeAt"].tLong()
  val readyAt get() = m["readyAt"].tLong()
  val buzzAt get() = m["buzzAt"].tLong()
  val revealAt get() = m["revealAt"].tLong()
  val buzzer get() = str("buzzer")
  val locked get() = list("locked")
  val attempts get() = m["attempts"].tLong().toInt()
  val answer get() = str("answer")
  // veredicto
  val vOk get() = str("vOk")               // "1" correcta, "0" incorrecta, "?" ambigua
  val vHow get() = str("vHow")             // local, ia, web, manual
  val vWeb get() = (m["vWeb"] as? Boolean) == true
  val vMsg get() = str("vMsg")
  val vSrc get() = list("vSrc")
  val revealAns get() = str("revealAns")
  val lastGain get() = m["lastGain"].tLong().toInt()
  val lastWho get() = str("lastWho")
  val wrongUid get() = str("wrongUid")
  val wrongAns get() = str("wrongAns")
  val wrongEv get() = m["wrongEv"].tLong()
  val note get() = str("note")
  // voto de revisión manual: lo emite un jugador que NO es quien respondió ("1" acepta, "0" rechaza)
  val rv get() = str("rv")
  val rvBy get() = str("rvBy")

  /** Milisegundos de la pregunta ya consumidos (el reloj se detiene mientras alguien responde). */
  fun usedMs(srvNow: Long): Long = baseUsed + when (phase) {
    "question" -> (srvNow - resumeAt).coerceAtLeast(0L)
    "answering", "judging", "review", "reveal" -> if (buzzAt > 0L && resumeAt > 0L) (buzzAt - resumeAt).coerceAtLeast(0L) else 0L
    else -> 0L
  }
  fun remainingMs(srvNow: Long): Long = (qSec * 1000L - usedMs(srvNow)).coerceAtLeast(0L)
}
