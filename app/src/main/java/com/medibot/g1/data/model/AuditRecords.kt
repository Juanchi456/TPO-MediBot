package com.medibot.g1.data.model

/** Modelos de dominio del historial: la UI no conoce las entidades de Room. */
data class MissionRecord(
    val runId: String,
    val missionName: String,
    val description: String,
    val priority: MissionPriority,
    val originName: String,
    val destinationName: String,
    val status: MissionStatus,
    val startTime: Long,
    val endTime: Long,
    val finalBatteryPct: Int,
    val maxTempCelsius: Float,
    val maxVibrationG: Float,
    val notes: String
)

data class EventRecord(
    val eventId: String,
    val timestamp: Long,
    val category: EventCategory,
    val severity: AlertSeverity,
    val title: String,
    val details: String,
    val relatedMissionId: String?
)
