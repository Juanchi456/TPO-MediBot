package com.medibot.g1.data.model

data class RobotAlert(
    val id: String,
    val severity: AlertSeverity,
    val title: String,
    val message: String,
    val timestamp: Long,
    val isAcknowledged: Boolean = false,
    val acknowledgedAt: Long? = null,
    val relatedMissionId: String? = null
)
