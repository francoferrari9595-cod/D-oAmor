package com.example.model

/**
 * Reglas compartidas para apuestas con créditos virtuales SIN retiro de dinero.
 * Este archivo contiene el modelo puro de dominio; la reserva/cobro definitivo debe
 * ejecutarse transaccionalmente en el repositorio remoto antes de conectarlo a cada juego.
 */
enum class BetMode { FIXED, HOST_LIMITS }
enum class BetStatus { DRAFT, RESERVED, SETTLED, REFUNDED }
enum class BetOutcome { PLAYER_ONE, PLAYER_TWO, DRAW, CANCELLED }

data class BetPolicy(
    val mode: BetMode = BetMode.HOST_LIMITS,
    val fixedAmount: Long = 100L,
    val minimum: Long = 10L,
    val maximum: Long = 1_000L,
    val step: Long = 10L,
    val hostId: String,
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun validate(): String? {
        if (hostId.isBlank()) return "La mesa no tiene anfitrión válido."
        if (minimum <= 0 || maximum < minimum || step <= 0) return "Los límites de apuesta no son válidos."
        if (mode == BetMode.FIXED && (fixedAmount < minimum || fixedAmount > maximum)) return "La apuesta fija está fuera de los límites."
        return null
    }

    fun validateAmount(amount: Long): String? {
        validate()?.let { return it }
        val selected = if (mode == BetMode.FIXED) fixedAmount else amount
        if (selected < minimum || selected > maximum) return "Elegí una apuesta entre $minimum y $maximum créditos."
        if ((selected - minimum) % step != 0L) return "La apuesta debe avanzar en intervalos de $step créditos."
        return null
    }

    fun effectiveAmount(selectedAmount: Long): Long = if (mode == BetMode.FIXED) fixedAmount else selectedAmount
}

data class BetParticipant(val uid: String, val stake: Long) {
    init { require(uid.isNotBlank()); require(stake > 0) }
}

data class BetRound(
    val id: String,
    val gameId: String,
    val roomId: String,
    val participants: List<BetParticipant>,
    val status: BetStatus = BetStatus.DRAFT,
    val winnerUid: String? = null,
    val prizeByUid: Map<String, Long> = emptyMap(),
    val createdAt: Long = System.currentTimeMillis(),
    val settledAt: Long? = null
) {
    val pot: Long get() = participants.sumOf { it.stake }
}

data class BetSettlement(val round: BetRound, val creditsDeltaByUid: Map<String, Long>)

/** Calcula el resultado sin escribir saldos. Debe persistirse con idempotencia en una transacción remota. */
object BettingRules {
    fun createRound(id: String, gameId: String, roomId: String, stakes: Map<String, Long>): BetRound {
        require(id.isNotBlank() && gameId.isNotBlank() && roomId.isNotBlank())
        require(stakes.size >= 2) { "Se necesitan al menos dos participantes." }
        require(stakes.values.all { it > 0 }) { "Todas las apuestas deben ser mayores que cero." }
        require(stakes.keys.none { it.isBlank() })
        return BetRound(id, gameId, roomId, stakes.map { BetParticipant(it.key, it.value) })
    }

    fun reserve(round: BetRound, balances: Map<String, Long>): BetSettlement {
        require(round.status == BetStatus.DRAFT) { "La apuesta ya fue procesada." }
        round.participants.forEach { p ->
            require((balances[p.uid] ?: 0L) >= p.stake) { "Saldo insuficiente para ${p.uid}." }
        }
        return BetSettlement(round.copy(status = BetStatus.RESERVED), round.participants.associate { it.uid to -it.stake })
    }

    fun settle(round: BetRound, outcome: BetOutcome, winnerUid: String? = null, now: Long = System.currentTimeMillis()): BetSettlement {
        require(round.status == BetStatus.RESERVED) { "Solo se puede cerrar una apuesta reservada." }
        val participants = round.participants.associateBy { it.uid }
        val deltas = participants.keys.associateWith { 0L }.toMutableMap()
        when (outcome) {
            BetOutcome.CANCELLED, BetOutcome.DRAW -> participants.values.forEach { deltas[it.uid] = it.stake }
            BetOutcome.PLAYER_ONE, BetOutcome.PLAYER_TWO -> {
                require(winnerUid != null && winnerUid in participants) { "El ganador debe pertenecer a la partida." }
                val pot = round.pot
                deltas[winnerUid] = (deltas[winnerUid] ?: 0L) + pot
            }
        }
        val finalStatus = if (outcome == BetOutcome.CANCELLED || outcome == BetOutcome.DRAW) BetStatus.REFUNDED else BetStatus.SETTLED
        val prizes = deltas.filterValues { it > 0L }
        return BetSettlement(round.copy(status = finalStatus, winnerUid = winnerUid, prizeByUid = prizes, settledAt = now), deltas)
    }
}
