package com.medibot.g1.data.model

/**
 * Ciclo de vida visible de una orden: enviar ≠ aceptar ≠ ejecutar.
 * Estados excluyentes modelados con sealed interface (criterio de la cátedra).
 */
sealed interface CommandExecutionState {

    data object Idle : CommandExecutionState

    /** Toda orden emitida tiene identificador y hora de emisión (ver contrato JSON). */
    sealed interface Tracked : CommandExecutionState {
        val commandId: String
        val commandName: String
        val issuedAt: Long

        /** Quién emitió la orden (operatorId del contrato JSON). */
        val operatorId: String?
    }

    data class Pending(
        override val commandId: String,
        override val commandName: String,
        override val issuedAt: Long,
        override val operatorId: String? = null
    ) : Tracked

    data class Confirmed(
        override val commandId: String,
        override val commandName: String,
        override val issuedAt: Long,
        val message: String,
        override val operatorId: String? = null
    ) : Tracked

    data class Rejected(
        override val commandId: String,
        override val commandName: String,
        override val issuedAt: Long,
        val reason: String,
        override val operatorId: String? = null
    ) : Tracked

    data class NoResponse(
        override val commandId: String,
        override val commandName: String,
        override val issuedAt: Long,
        val timeoutMs: Long,
        override val operatorId: String? = null
    ) : Tracked
}
