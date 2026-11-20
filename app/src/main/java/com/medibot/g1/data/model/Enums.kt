package com.medibot.g1.data.model

enum class ConnectionStatus { ONLINE, DEGRADED, OFFLINE }

enum class OperationalState(val displayName: String) {
    IDLE_AT_DOCK("En Base (Reposo)"),
    EXECUTING_MISSION("Ejecutando Misión"),
    WAITING_ELEVATOR("Esperando Ascensor"),
    AWAITING_NFC_HANDOFF("Esperando Validación NFC"),
    PAUSED("Pausado"),
    RETURNING_TO_SAFE_POINT("Retornando a Punto Seguro"),
    EMERGENCY_STOPPED("Parada de Emergencia"),
    INCIDENT_BLOCKED("Bloqueado por Incidente"),
    LOW_BATTERY_CROUCHED("Batería Baja - Agachado")
}

enum class RobotPosture(val displayName: String) {
    BIPED_STANDING("Bípedo - De Pie"),
    BIPED_WALKING("Bípedo - Caminando"),
    OPERATING_DOOR_ARM("Operando Puerta (Brazo)"),
    CROUCHED_SAFE_LOCK("Agachado - Bloqueo Seguro")
}

enum class SmartLockState(val displayName: String) {
    SEALED_LOCKED("Sellado y Bloqueado"),
    UNLOCKED_NFC_AUTHORIZED("Desbloqueado (NFC Autorizado)"),
    TAMPER_ALERT("Alerta de Manipulación")
}

enum class AlertSeverity { INFO, WARNING, CRITICAL }

enum class MissionStatus(val label: String) {
    IN_PROGRESS("En curso"),
    COMPLETED("Completada"),
    CANCELLED("Cancelada"),
    FAILED("Fallida")
}

enum class EventCategory { SYSTEM, MISSION, COMMAND, ALERT, SCENARIO }

enum class TelemetryCardState { NORMAL, WARNING, ERROR, STALE }
