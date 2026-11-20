package com.medibot.g1.data.repository

import com.medibot.g1.data.model.AlertSeverity
import com.medibot.g1.data.model.CommandExecutionState
import com.medibot.g1.data.model.EventCategory
import com.medibot.g1.data.model.MissionStatus
import com.medibot.g1.data.model.RobotEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Persiste en la auditoría todo lo que informa el robot (alertas, fin de misiones y
 * resultado de cada orden). Vive a nivel aplicación: el registro no depende de que
 * una pantalla esté abierta.
 */
class RobotEventRecorder(
    private val robot: RobotRepository,
    private val audit: AuditRepository,
    private val scope: CoroutineScope
) {
    fun start() {
        scope.launch {
            audit.logEvent(
                EventCategory.SYSTEM,
                AlertSeverity.INFO,
                "Consola HMI iniciada",
                "Supervisión conectada al robot ${robot.telemetry.value.robotId} (simulado)."
            )
        }

        scope.launch {
            robot.events.collect { event ->
                when (event) {
                    is RobotEvent.AlertRaised -> audit.logEvent(
                        EventCategory.ALERT,
                        event.alert.severity,
                        event.alert.title,
                        event.alert.message,
                        event.alert.relatedMissionId
                    )
                    is RobotEvent.MissionFinished -> {
                        val outcome = event.outcome
                        audit.saveMission(outcome)
                        audit.logEvent(
                            EventCategory.MISSION,
                            when (outcome.status) {
                                MissionStatus.COMPLETED -> AlertSeverity.INFO
                                MissionStatus.FAILED -> AlertSeverity.CRITICAL
                                else -> AlertSeverity.WARNING
                            },
                            "Misión ${outcome.status.label.lowercase()}: ${outcome.mission.name}",
                            "${outcome.notes} Batería final: ${outcome.finalBatteryPct}%.",
                            outcome.runId
                        )
                    }
                }
            }
        }

        scope.launch {
            robot.commandState.collect { state ->
                when (state) {
                    is CommandExecutionState.Confirmed -> audit.logEvent(
                        EventCategory.COMMAND,
                        AlertSeverity.INFO,
                        "Orden confirmada: ${state.commandName}",
                        "[${state.commandId}] ${state.message}"
                    )
                    is CommandExecutionState.Rejected -> audit.logEvent(
                        EventCategory.COMMAND,
                        AlertSeverity.WARNING,
                        "Orden rechazada: ${state.commandName}",
                        "[${state.commandId}] Motivo: ${state.reason}"
                    )
                    is CommandExecutionState.NoResponse -> audit.logEvent(
                        EventCategory.COMMAND,
                        AlertSeverity.CRITICAL,
                        "Orden sin respuesta: ${state.commandName}",
                        "[${state.commandId}] Sin confirmación del robot tras ${state.timeoutMs} ms. No se sabe si se ejecutó."
                    )
                    is CommandExecutionState.Pending, CommandExecutionState.Idle -> Unit
                }
            }
        }
    }
}
