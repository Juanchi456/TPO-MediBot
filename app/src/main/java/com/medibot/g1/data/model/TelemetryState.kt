package com.medibot.g1.data.model

data class TelemetryState(
    val robotId: String = "G1-EDU-MED01",
    val firmwareVersion: String = "v2.4.1-DEX3",
    val connectionStatus: ConnectionStatus = ConnectionStatus.ONLINE,
    val latencyMs: Int = 42,
    val signalDbm: Int = -35,
    val lastUpdatedTimestamp: Long = System.currentTimeMillis(),
    val batteryPercentage: Int = 95,
    val batteryTempCelsius: Float = 28.5f,
    val estimatedMinutesLeft: Int = 114,
    val operationalState: OperationalState = OperationalState.IDLE_AT_DOCK,
    val currentMission: MissionConfig? = null,
    /** Identificador de la ejecución en curso (distinto en cada despacho). */
    val missionRunId: String? = null,
    val missionProgressPct: Int = 0,
    val currentNode: HospitalNode = HospitalNode.DOCK_BASE,
    val speedMps: Float = 0.0f,
    val posture: RobotPosture = RobotPosture.BIPED_STANDING,
    val hriLedColor: String = "#00F5D4",
    val hriVoiceStatus: String = "En espera",
    val servicePayload: ServicePayloadTelemetry = ServicePayloadTelemetry(),
    val activeAlerts: List<RobotAlert> = emptyList()
)
