package com.medibot.g1.data.model

/** Eventos discretos que informa el robot (además de la telemetría continua). */
sealed interface RobotEvent {
    data class AlertRaised(val alert: RobotAlert) : RobotEvent
    data class MissionFinished(val outcome: MissionOutcome) : RobotEvent
}

data class MissionOutcome(
    val runId: String,
    val mission: MissionConfig,
    val status: MissionStatus,
    val startedAt: Long,
    val endedAt: Long,
    val finalBatteryPct: Int,
    val maxTempCelsius: Float,
    val maxVibrationG: Float,
    val notes: String
)
