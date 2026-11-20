package com.medibot.g1.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.medibot.g1.MediBotApplication
import com.medibot.g1.data.model.AlertSeverity
import com.medibot.g1.data.model.CommandExecutionState
import com.medibot.g1.data.model.ConnectionStatus
import com.medibot.g1.data.model.EventCategory
import com.medibot.g1.data.model.EventRecord
import com.medibot.g1.data.model.MissionCatalog
import com.medibot.g1.data.model.MissionConfig
import com.medibot.g1.data.model.MissionRecord
import com.medibot.g1.data.model.RobotCommand
import com.medibot.g1.data.model.TelemetryState
import com.medibot.g1.data.repository.AuditRepository
import com.medibot.g1.data.repository.RobotRepository
import com.medibot.g1.data.repository.SimulationScenario
import com.medibot.g1.data.repository.SimulatorControls
import com.medibot.g1.domain.CommandAvailability
import com.medibot.g1.domain.CommandRules
import com.medibot.g1.domain.OperationalLimits
import com.medibot.g1.domain.Precondition
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Estado completo de la consola. La UI solo lo observa (UI = f(estado)). */
data class SupervisionUiState(
    val telemetry: TelemetryState = TelemetryState(),
    /** Estado de enlace que se muestra: si el dato es antiguo no se informa "ONLINE". */
    val linkStatus: ConnectionStatus = ConnectionStatus.ONLINE,
    val commandState: CommandExecutionState = CommandExecutionState.Idle,
    val isTelemetryStale: Boolean = false,
    val lastUpdateTimestamp: Long = 0L,
    val secondsSinceUpdate: Long = 0L,
    val availability: CommandAvailability = CommandAvailability.NONE,
    val startPreconditions: List<Precondition> = emptyList(),
    val forceRejectNext: Boolean = false,
    val simulatorLinkUp: Boolean = true
)

class SupervisionViewModel(
    private val robot: RobotRepository,
    private val simulator: SimulatorControls,
    private val audit: AuditRepository,
    private val clock: () -> Long = System::currentTimeMillis
) : ViewModel() {

    val missionCatalog: List<MissionConfig> = MissionCatalog.all

    /** Reloj de la UI: recalcula la antigüedad del dato aunque no llegue telemetría. */
    private val ticker = flow {
        while (true) {
            emit(clock())
            delay(TICK_MS)
        }
    }

    /** Id de la última orden cuyo resultado el operador cerró (el resultado sigue en auditoría). */
    private val dismissedCommandId = MutableStateFlow<String?>(null)

    private val simulatorFlags = combine(simulator.forceRejectNext, simulator.linkUp) { force, link ->
        force to link
    }

    val uiState: StateFlow<SupervisionUiState> = combine(
        robot.telemetry,
        robot.commandState,
        dismissedCommandId,
        ticker,
        simulatorFlags
    ) { telemetry, command, dismissedId, now, (forceReject, linkUp) ->
        val age = (now - telemetry.lastUpdatedTimestamp).coerceAtLeast(0L)
        val stale = age > OperationalLimits.STALE_THRESHOLD_MS
        val pending = command is CommandExecutionState.Pending
        val visibleCommand =
            if (command is CommandExecutionState.Tracked && command.commandId == dismissedId) {
                CommandExecutionState.Idle
            } else {
                command
            }
        SupervisionUiState(
            telemetry = telemetry,
            linkStatus = if (stale) ConnectionStatus.OFFLINE else telemetry.connectionStatus,
            commandState = visibleCommand,
            isTelemetryStale = stale,
            lastUpdateTimestamp = telemetry.lastUpdatedTimestamp,
            secondsSinceUpdate = age / 1000L,
            availability = CommandRules.availability(telemetry, stale, pending),
            startPreconditions = CommandRules.startPreconditions(telemetry, stale),
            forceRejectNext = forceReject,
            simulatorLinkUp = linkUp
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SupervisionUiState())

    val missionsHistory: StateFlow<List<MissionRecord>> = audit.missions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val eventLogs: StateFlow<List<EventRecord>> = audit.events
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var commandJob: Job? = null

    /**
     * Emite una orden. Mientras hay una pendiente, solo la Detención Operativa puede
     * emitirse (y anula la anterior, que queda registrada como no confirmada).
     */
    fun sendCommand(command: RobotCommand) {
        val inFlight = commandJob?.isActive == true
        if (inFlight && command !is RobotCommand.OperationalStop) return

        val snapshot = uiState.value
        val superseded = (snapshot.commandState as? CommandExecutionState.Pending)?.commandName
        commandJob?.cancel()
        commandJob = viewModelScope.launch {
            if (inFlight) {
                audit.logEvent(
                    EventCategory.COMMAND,
                    AlertSeverity.WARNING,
                    "Orden anulada por Detención Operativa",
                    "${superseded ?: "La orden en curso"} quedó sin confirmar al priorizar la detención."
                )
            }
            if (snapshot.isTelemetryStale) {
                audit.logEvent(
                    EventCategory.COMMAND,
                    AlertSeverity.WARNING,
                    "Orden emitida con datos antiguos",
                    "${command.commandName} enviada sin telemetría fresca (último dato hace ${snapshot.secondsSinceUpdate} s)."
                )
            }
            robot.sendCommand(command)
        }
    }

    fun startMission(mission: MissionConfig) = sendCommand(RobotCommand.StartMission(mission))

    fun acknowledgeAlert(alertId: String) = sendCommand(RobotCommand.AcknowledgeAlert(alertId))

    fun dismissCommandFeedback() {
        val current = robot.commandState.value
        if (current is CommandExecutionState.Tracked && current !is CommandExecutionState.Pending) {
            dismissedCommandId.value = current.commandId
        }
    }

    // ---------------- Panel de ingeniería (simulador) ----------------

    fun setForceRejectNext(value: Boolean) = simulator.setForceRejectNext(value)

    fun restoreConnection() {
        simulator.restoreConnection()
        logSimulatorAction("Enlace restablecido", "Conexión con el robot reanudada desde el panel de ingeniería.")
    }

    fun injectScenario(scenario: SimulationScenario) {
        simulator.injectScenario(scenario)
        logSimulatorAction("Inyección de prueba: ${scenario.title}", "Disparado manualmente desde el panel de ingeniería.")
    }

    fun resetSimulation() {
        simulator.resetSimulation()
        logSimulatorAction("Simulación reiniciada", "El robot simulado volvió al estado inicial en base.")
    }

    private fun logSimulatorAction(title: String, details: String) {
        viewModelScope.launch {
            audit.logEvent(EventCategory.SCENARIO, AlertSeverity.INFO, title, details)
        }
    }

    // ---------------- Historial ----------------

    fun insertSampleData() {
        viewModelScope.launch { audit.insertSampleData() }
    }

    fun clearHistory() {
        viewModelScope.launch {
            audit.clearAll()
            audit.logEvent(
                EventCategory.SYSTEM,
                AlertSeverity.INFO,
                "Historial depurado",
                "Tablas de historial y auditoría limpiadas por el operador."
            )
        }
    }

    companion object {
        private const val TICK_MS = 1_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MediBotApplication
                SupervisionViewModel(
                    robot = app.container.robotRepository,
                    simulator = app.container.simulatorControls,
                    audit = app.container.auditRepository
                )
            }
        }
    }
}
