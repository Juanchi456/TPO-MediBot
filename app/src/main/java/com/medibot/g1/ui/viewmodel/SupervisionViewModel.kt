package com.medibot.g1.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.medibot.g1.MediBotApplication
import com.medibot.g1.data.model.AlertSeverity
import com.medibot.g1.data.model.CommandExecutionState
import com.medibot.g1.data.model.ConnectionStatus
import com.medibot.g1.data.model.ConsoleRole
import com.medibot.g1.data.model.ConsoleSession
import com.medibot.g1.data.model.EventCategory
import com.medibot.g1.data.model.EventRecord
import com.medibot.g1.data.model.FleetCatalog
import com.medibot.g1.data.model.MissionCatalog
import com.medibot.g1.data.model.MissionConfig
import com.medibot.g1.data.model.MissionRecord
import com.medibot.g1.data.model.RobotCommand
import com.medibot.g1.data.model.RobotProfile
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
    val simulatorLinkUp: Boolean = true,
    /** Sesión de consola (robot, operador y rol); null = consola desconectada. */
    val session: ConsoleSession? = null
) {
    val connectedRobot: RobotProfile? get() = session?.robot
    val isConsoleConnected: Boolean get() = session != null

    /** Solo un operador conectado puede emitir órdenes; el supervisor observa. */
    val canCommand: Boolean get() = session?.canCommand == true
}

private data class SessionFlags(
    val forceRejectNext: Boolean,
    val simulatorLinkUp: Boolean,
    val session: ConsoleSession?
)

class SupervisionViewModel(
    private val robot: RobotRepository,
    private val simulator: SimulatorControls,
    private val audit: AuditRepository,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    private val clock: () -> Long = System::currentTimeMillis
) : ViewModel() {

    val missionCatalog: List<MissionConfig> = MissionCatalog.all
    val fleet: List<RobotProfile> = FleetCatalog.robots

    /** Sesión de la consola: sobrevive a la recreación de la Activity y a la muerte del proceso. */
    private val session = combine(
        savedStateHandle.getStateFlow<String?>(KEY_CONNECTED_ROBOT, null),
        savedStateHandle.getStateFlow<String?>(KEY_OPERATOR_ID, null),
        savedStateHandle.getStateFlow<String?>(KEY_ROLE, null)
    ) { robotId, operatorId, role -> buildSession(robotId, operatorId, role) }

    private fun currentSession(): ConsoleSession? = buildSession(
        savedStateHandle[KEY_CONNECTED_ROBOT],
        savedStateHandle[KEY_OPERATOR_ID],
        savedStateHandle[KEY_ROLE]
    )

    private fun buildSession(robotId: String?, operatorId: String?, role: String?): ConsoleSession? {
        val profile = FleetCatalog.find(robotId) ?: return null
        val consoleRole = ConsoleRole.entries.firstOrNull { it.name == role } ?: return null
        return ConsoleSession(profile, operatorId ?: return null, consoleRole)
    }

    /** Para elegir la pantalla inicial antes de que el uiState emita. */
    val isInitiallyConnected: Boolean
        get() = savedStateHandle.get<String>(KEY_CONNECTED_ROBOT) != null

    /** Reloj de la UI: recalcula la antigüedad del dato aunque no llegue telemetría. */
    private val ticker = flow {
        while (true) {
            emit(clock())
            delay(TICK_MS)
        }
    }

    /** Id de la última orden cuyo resultado el operador cerró (el resultado sigue en auditoría). */
    private val dismissedCommandId = MutableStateFlow<String?>(null)

    private val sessionFlags = combine(
        simulator.forceRejectNext,
        simulator.linkUp,
        session
    ) { force, link, current -> SessionFlags(force, link, current) }

    val uiState: StateFlow<SupervisionUiState> = combine(
        robot.telemetry,
        robot.commandState,
        dismissedCommandId,
        ticker,
        sessionFlags
    ) { telemetry, command, dismissedId, now, session ->
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
            availability = if (session.session?.canCommand == true) {
                CommandRules.availability(telemetry, stale, pending)
            } else {
                CommandAvailability.DISCONNECTED
            },
            startPreconditions = CommandRules.startPreconditions(telemetry, stale),
            forceRejectNext = session.forceRejectNext,
            simulatorLinkUp = session.simulatorLinkUp,
            session = session.session
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
        val session = currentSession() ?: return
        if (!session.canCommand) return
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
                    "${superseded ?: "La orden en curso"} quedó sin confirmar al priorizar la detención.",
                    operatorId = session.operatorId
                )
            }
            if (snapshot.isTelemetryStale) {
                audit.logEvent(
                    EventCategory.COMMAND,
                    AlertSeverity.WARNING,
                    "Orden emitida con datos antiguos",
                    "${command.commandName} enviada sin telemetría fresca (último dato hace ${snapshot.secondsSinceUpdate} s).",
                    operatorId = session.operatorId
                )
            }
            robot.sendCommand(command, session.operatorId)
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

    // ---------------- Sesión de consola ----------------

    /**
     * Conecta la consola a un robot disponible. [operatorId] es el legajo o ID de quien la usa
     * (sin contraseña en el MVP) y [role] define si puede emitir órdenes.
     * Devuelve false si los datos no son válidos.
     */
    fun connect(robotId: String, operatorId: String, role: ConsoleRole): Boolean {
        val profile = FleetCatalog.find(robotId) ?: return false
        val id = normalizeOperatorId(operatorId) ?: return false
        if (!profile.isAvailable || currentSession() != null) return false
        savedStateHandle[KEY_OPERATOR_ID] = id
        savedStateHandle[KEY_ROLE] = role.name
        savedStateHandle[KEY_CONNECTED_ROBOT] = robotId
        viewModelScope.launch {
            audit.logEvent(
                EventCategory.SYSTEM,
                AlertSeverity.INFO,
                "Consola conectada (${role.displayName})",
                "$id inició la supervisión de ${profile.displayName} (${profile.robotId}) con rol ${role.displayName.lowercase()}.",
                operatorId = id
            )
        }
        return true
    }

    fun disconnect() {
        val session = currentSession() ?: return
        val activeMission = robot.telemetry.value.currentMission
        commandJob?.cancel()
        savedStateHandle[KEY_CONNECTED_ROBOT] = null
        savedStateHandle[KEY_OPERATOR_ID] = null
        savedStateHandle[KEY_ROLE] = null
        viewModelScope.launch {
            audit.logEvent(
                EventCategory.SYSTEM,
                if (activeMission != null) AlertSeverity.WARNING else AlertSeverity.INFO,
                "Consola desconectada",
                if (activeMission != null) {
                    "${session.operatorId} desconectó la consola de ${session.robot.robotId} con la misión '${activeMission.name}' en curso: el robot continúa sin supervisión remota."
                } else {
                    "${session.operatorId} desconectó la consola de ${session.robot.robotId}."
                },
                operatorId = session.operatorId
            )
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

    fun physicalRearm() {
        simulator.physicalRearm()
        logSimulatorAction("Rearme presencial", "Un técnico rearmó el robot en el lugar (simulado).")
    }

    fun physicalBatterySwap() {
        simulator.physicalBatterySwap()
        logSimulatorAction("Cambio de batería presencial", "Un técnico reemplazó la batería del robot (simulado).")
    }

    fun resetSimulation() {
        simulator.resetSimulation()
        logSimulatorAction("Simulación reiniciada", "El robot simulado volvió al estado inicial en base.")
    }

    private fun logSimulatorAction(title: String, details: String) {
        viewModelScope.launch {
            audit.logEvent(
                EventCategory.SCENARIO,
                AlertSeverity.INFO,
                title,
                details,
                operatorId = currentSession()?.operatorId
            )
        }
    }

    // ---------------- Historial ----------------

    // Herramientas de prueba: se usan desde el panel de ingeniería, no desde la vista de Historial.

    fun insertSampleData() {
        viewModelScope.launch { audit.insertSampleData() }
        logSimulatorAction("Datos de ejemplo cargados", "Se agregaron misiones y eventos de ejemplo al historial.")
    }

    fun clearHistory() {
        val operatorId = currentSession()?.operatorId
        viewModelScope.launch {
            audit.clearAll()
            // El borrado queda registrado como primer evento de la nueva auditoría.
            audit.logEvent(
                EventCategory.SYSTEM,
                AlertSeverity.WARNING,
                "Historial borrado",
                "Se borraron el historial de misiones y la auditoría desde el panel de ingeniería${operatorId?.let { " (sesión de $it)" } ?: ""}.",
                operatorId = operatorId
            )
        }
    }

    companion object {
        private const val TICK_MS = 1_000L
        private const val KEY_CONNECTED_ROBOT = "connected_robot_id"
        private const val KEY_OPERATOR_ID = "operator_id"
        private const val KEY_ROLE = "console_role"
        private val OPERATOR_ID_PATTERN = Regex("^[A-Z0-9-]{3,20}$")

        /** ID de operador en mayúsculas, 3 a 20 caracteres (letras, números o guiones). */
        fun normalizeOperatorId(raw: String): String? =
            raw.trim().uppercase().takeIf { OPERATOR_ID_PATTERN.matches(it) }

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MediBotApplication
                SupervisionViewModel(
                    robot = app.container.robotRepository,
                    simulator = app.container.simulatorControls,
                    audit = app.container.auditRepository,
                    savedStateHandle = createSavedStateHandle()
                )
            }
        }
    }
}
