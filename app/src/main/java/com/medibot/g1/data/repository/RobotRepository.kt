package com.medibot.g1.data.repository

import com.medibot.g1.data.model.CommandExecutionState
import com.medibot.g1.data.model.RobotCommand
import com.medibot.g1.data.model.RobotEvent
import com.medibot.g1.data.model.TelemetryState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Lo que la app necesita de un robot, real o simulado: telemetría, comandos y eventos.
 * Un gateway real (p. ej. sobre el SDK de Unitree) implementaría solo esta interfaz.
 */
interface RobotRepository {
    /** Último paquete de telemetría recibido. Si el enlace cae, deja de actualizarse. */
    val telemetry: StateFlow<TelemetryState>

    /** Estado de la última orden emitida. */
    val commandState: StateFlow<CommandExecutionState>

    /** Eventos discretos (alertas emitidas, misiones finalizadas). */
    val events: Flow<RobotEvent>

    /** Envía una orden y suspende hasta que se confirma, se rechaza o vence el timeout. */
    suspend fun sendCommand(command: RobotCommand): CommandExecutionState
}

enum class SimulationScenario(val title: String) {
    NORMAL_MISSION("Escenario 1: Misión normal"),
    LOW_BATTERY("Escenario 2: Batería baja"),
    DISCONNECTION("Escenario 3: Desconexión y datos antiguos"),
    INCIDENT("Escenario 4: Incidente en pasillo"),
    COLD_CHAIN_FAILURE("Escenario 5: Falla de cadena de frío / tamper")
}

/**
 * Controles exclusivos del simulador (modo ingeniero). Separados de [RobotRepository]
 * para que el contrato con el robot no incluya operaciones que un robot real no tiene.
 */
interface SimulatorControls {
    val forceRejectNext: StateFlow<Boolean>

    /** Estado del enlace simulado (no es lo que ve el operador: el operador infiere por la antigüedad del dato). */
    val linkUp: StateFlow<Boolean>

    fun setForceRejectNext(value: Boolean)
    fun injectScenario(scenario: SimulationScenario)
    fun restoreConnection()
    fun resetSimulation()
}
