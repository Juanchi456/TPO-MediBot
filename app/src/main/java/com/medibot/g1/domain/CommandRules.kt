package com.medibot.g1.domain

import com.medibot.g1.data.model.AlertSeverity
import com.medibot.g1.data.model.ConnectionStatus
import com.medibot.g1.data.model.OperationalState
import com.medibot.g1.data.model.SmartLockState
import com.medibot.g1.data.model.TelemetryState
import java.util.Locale

/**
 * Umbrales operativos tomados del informe técnico. Concentrados en un solo lugar
 * para que el simulador, el checklist y la UI usen los mismos valores.
 */
object OperationalLimits {
    const val MIN_BATTERY_TO_START_PCT = 20
    const val CRITICAL_BATTERY_CROUCH_PCT = 8
    const val MAX_START_TEMP_C = 6.0f
    const val COLD_CHAIN_BREAK_TEMP_C = 6.5f
    const val VIBRATION_WARNING_G = 0.30f
    const val VIBRATION_CRITICAL_G = 0.60f
    const val OBSTACLE_MIN_CLEARANCE_M = 0.40f
    const val STALE_THRESHOLD_MS = 4_000L
    const val COMMAND_TIMEOUT_MS = 2_500L
    const val FULL_BATTERY_AUTONOMY_MIN = 120
    const val MAX_SPEED_MPS = 0.8f
}

data class Precondition(
    val label: String,
    val currentValue: String,
    val passed: Boolean
)

/** Qué órdenes puede emitir el operador en este momento (condiciones de habilitación). */
data class CommandAvailability(
    val start: Boolean,
    val pause: Boolean,
    val resume: Boolean,
    val cancel: Boolean,
    val returnToSafePoint: Boolean,
    val acknowledge: Boolean,
    /** La detención operativa está siempre disponible (y puede anular una orden pendiente). */
    val operationalStop: Boolean = true
) {
    companion object {
        val NONE = CommandAvailability(
            start = false,
            pause = false,
            resume = false,
            cancel = false,
            returnToSafePoint = false,
            acknowledge = false
        )
    }
}

/**
 * Condiciones de habilitación de cada comando, según la tabla 2.3 del informe.
 * Son reglas de la HMI: el robot (simulado) vuelve a validar y puede rechazar.
 */
object CommandRules {

    private val PAUSABLE_STATES = setOf(
        OperationalState.EXECUTING_MISSION,
        OperationalState.RETURNING_TO_SAFE_POINT
    )

    val CANCELLABLE_STATES = setOf(
        OperationalState.EXECUTING_MISSION,
        OperationalState.PAUSED,
        OperationalState.WAITING_ELEVATOR,
        OperationalState.INCIDENT_BLOCKED
    )

    private val NO_RETURN_STATES = setOf(
        OperationalState.IDLE_AT_DOCK,
        OperationalState.LOW_BATTERY_CROUCHED,
        OperationalState.RETURNING_TO_SAFE_POINT
    )

    fun hasUnacknowledgedCritical(telemetry: TelemetryState): Boolean =
        telemetry.activeAlerts.any { it.severity == AlertSeverity.CRITICAL && !it.isAcknowledged }

    fun startPreconditions(telemetry: TelemetryState, isStale: Boolean): List<Precondition> {
        val battery = telemetry.batteryPercentage
        val lock = telemetry.servicePayload.smartLockState
        val temp = telemetry.servicePayload.compartmentTempCelsius
        val linkOk = !isStale && telemetry.connectionStatus == ConnectionStatus.ONLINE
        return listOf(
            Precondition(
                label = "Robot en reposo en base",
                currentValue = telemetry.operationalState.displayName,
                passed = telemetry.operationalState == OperationalState.IDLE_AT_DOCK
            ),
            Precondition(
                label = "Batería ≥ ${OperationalLimits.MIN_BATTERY_TO_START_PCT}%",
                currentValue = "$battery%",
                passed = battery >= OperationalLimits.MIN_BATTERY_TO_START_PCT
            ),
            Precondition(
                label = "Cápsula sellada",
                currentValue = lock.displayName,
                passed = lock == SmartLockState.SEALED_LOCKED
            ),
            Precondition(
                label = "Temperatura cápsula ≤ ${OperationalLimits.MAX_START_TEMP_C} °C",
                currentValue = String.format(Locale.US, "%.1f °C", temp),
                passed = temp <= OperationalLimits.MAX_START_TEMP_C
            ),
            Precondition(
                label = "Enlace en línea con datos frescos",
                currentValue = if (isStale) "Datos antiguos" else telemetry.connectionStatus.name,
                passed = linkOk
            )
        )
    }

    fun availability(
        telemetry: TelemetryState,
        isStale: Boolean,
        isCommandPending: Boolean
    ): CommandAvailability {
        val free = !isCommandPending
        val state = telemetry.operationalState
        return CommandAvailability(
            start = free && startPreconditions(telemetry, isStale).all { it.passed },
            pause = free && state in PAUSABLE_STATES,
            resume = free && (
                state == OperationalState.PAUSED ||
                    (state == OperationalState.INCIDENT_BLOCKED && !hasUnacknowledgedCritical(telemetry))
                ),
            cancel = free && telemetry.currentMission != null && state in CANCELLABLE_STATES,
            returnToSafePoint = free && state !in NO_RETURN_STATES,
            acknowledge = free
        )
    }
}
