package com.medibot.g1.data.repository

import com.medibot.g1.data.model.AlertSeverity
import com.medibot.g1.data.model.CommandExecutionState
import com.medibot.g1.data.model.ConnectionStatus
import com.medibot.g1.data.model.HospitalNode
import com.medibot.g1.data.model.MissionCatalog
import com.medibot.g1.data.model.MissionConfig
import com.medibot.g1.data.model.MissionOutcome
import com.medibot.g1.data.model.MissionStatus
import com.medibot.g1.data.model.OperationalState
import com.medibot.g1.data.model.RobotAlert
import com.medibot.g1.data.model.RobotCommand
import com.medibot.g1.data.model.RobotEvent
import com.medibot.g1.data.model.RobotPosture
import com.medibot.g1.data.model.ServicePayloadTelemetry
import com.medibot.g1.data.model.SmartLockState
import com.medibot.g1.data.model.TelemetryState
import com.medibot.g1.domain.CommandRules
import com.medibot.g1.domain.OperationalLimits
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import kotlin.math.max
import kotlin.random.Random

/** Tiempos del simulador (ms). Parametrizados para poder probarlo con tiempo virtual. */
data class SimulationTiming(
    val telemetryPeriodMs: Long = 1_000L,
    val commandLatencyMs: Long = 1_200L,
    val commandTimeoutMs: Long = OperationalLimits.COMMAND_TIMEOUT_MS,
    val routeStepMs: Long = 2_500L,
    val handoffMs: Long = 2_500L,
    val relockMs: Long = 4_000L,
    val returnMs: Long = 3_000L,
    val obstacleClearMs: Long = 10_000L
)

/**
 * Robot simulado. Mantiene el estado "real" del robot ([robot]) separado de lo que
 * recibe la app ([telemetry]): si el enlace cae, el robot sigue operando pero la app
 * deja de recibir paquetes y los eventos quedan en cola hasta que vuelva el enlace.
 *
 * Todo el estado mutable se toca desde un único dispatcher secuencial, así que no hay
 * condiciones de carrera entre el bucle de telemetría, las misiones y los comandos.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SimulatedRobotRepository(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1),
    private val clock: () -> Long = System::currentTimeMillis,
    private val random: Random = Random.Default,
    private val timing: SimulationTiming = SimulationTiming()
) : RobotRepository, SimulatorControls {

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    // ---------- Estado interno del robot (solo se accede desde [dispatcher]) ----------
    private var robot: TelemetryState = initialRobotState()
    private var batteryLevel = INITIAL_BATTERY
    private var coolingTargetC = NOMINAL_CAPSULE_TEMP_C
    private var activeRun: MissionRun? = null
    private var resumeTarget: OperationalState? = null
    private var returnDestination: HospitalNode? = null
    private var stoppedFrom: OperationalState? = null
    private var obstaclePresent = false
    private var fastBatteryDrain = false
    private val activeConditions = mutableSetOf<AlertCondition>()
    private val queuedEvents = ArrayDeque<RobotEvent>()

    private var motionJob: Job? = null
    private var obstacleJob: Job? = null
    private var relockJob: Job? = null

    // ---------- Lo que ve la app ----------
    private val _telemetry = MutableStateFlow(robot.copy(lastUpdatedTimestamp = clock()))
    override val telemetry: StateFlow<TelemetryState> = _telemetry.asStateFlow()

    private val _commandState = MutableStateFlow<CommandExecutionState>(CommandExecutionState.Idle)
    override val commandState: StateFlow<CommandExecutionState> = _commandState.asStateFlow()

    private val _events = MutableSharedFlow<RobotEvent>(extraBufferCapacity = 64)
    override val events: Flow<RobotEvent> = _events.asSharedFlow()

    // ---------- Controles del simulador ----------
    private val _forceRejectNext = MutableStateFlow(false)
    override val forceRejectNext: StateFlow<Boolean> = _forceRejectNext.asStateFlow()

    private val _linkUp = MutableStateFlow(true)
    override val linkUp: StateFlow<Boolean> = _linkUp.asStateFlow()

    init {
        scope.launch {
            while (isActive) {
                delay(timing.telemetryPeriodMs)
                tick()
            }
        }
    }

    /** Detiene el simulador (usado por los tests). */
    fun shutdown() {
        scope.cancel()
    }

    // =====================================================================
    // Comandos
    // =====================================================================

    override suspend fun sendCommand(command: RobotCommand): CommandExecutionState = withContext(dispatcher) {
        val commandId = "CMD-${shortId()}"
        val issuedAt = clock()
        _commandState.value = CommandExecutionState.Pending(commandId, command.commandName, issuedAt)

        // La orden solo llega si hay enlace dentro del timeout: enviada ≠ recibida.
        val delivered = withTimeoutOrNull(timing.commandTimeoutMs) {
            delay(timing.commandLatencyMs)
            _linkUp.first { it }
        }

        val result: CommandExecutionState = when {
            delivered == null -> CommandExecutionState.NoResponse(
                commandId, command.commandName, issuedAt, timing.commandTimeoutMs
            )
            _forceRejectNext.value -> {
                _forceRejectNext.value = false
                CommandExecutionState.Rejected(
                    commandId, command.commandName, issuedAt,
                    "Rechazado por política de seguridad (forzado desde el panel de ingeniería)."
                )
            }
            else -> when (val verdict = execute(command)) {
                is Verdict.Accepted -> CommandExecutionState.Confirmed(
                    commandId, command.commandName, issuedAt, verdict.message
                )
                is Verdict.Refused -> CommandExecutionState.Rejected(
                    commandId, command.commandName, issuedAt, verdict.reason
                )
            }
        }
        _commandState.value = result
        publish()
        result
    }

    private fun execute(command: RobotCommand): Verdict = when (command) {
        is RobotCommand.StartMission -> handleStart(command.mission)
        RobotCommand.PauseMission -> handlePause()
        RobotCommand.ResumeMission -> handleResume()
        RobotCommand.CancelMission -> handleCancel()
        RobotCommand.ReturnToSafePoint -> handleReturnToSafePoint()
        RobotCommand.OperationalStop -> handleOperationalStop()
        is RobotCommand.AcknowledgeAlert -> handleAcknowledge(command.alertId)
    }

    private fun handleStart(mission: MissionConfig): Verdict {
        val payload = robot.servicePayload
        return when {
            robot.operationalState !in CommandRules.RESTING_STATES -> Verdict.Refused(
                "Robot en estado incompatible (${robot.operationalState.displayName}). Solo se inicia desde la base o un punto seguro."
            )
            robot.batteryPercentage < OperationalLimits.MIN_BATTERY_TO_START_PCT -> Verdict.Refused(
                "Batería insuficiente (${robot.batteryPercentage}%). Se requiere mínimo ${OperationalLimits.MIN_BATTERY_TO_START_PCT}%."
            )
            payload.smartLockState != SmartLockState.SEALED_LOCKED -> Verdict.Refused(
                "Compartimento no sellado (${payload.smartLockState.displayName}). Cierre la cápsula antes de iniciar."
            )
            payload.compartmentTempCelsius > OperationalLimits.MAX_START_TEMP_C -> Verdict.Refused(
                "Temperatura de cápsula elevada (${payload.compartmentTempCelsius} °C). Máximo ${OperationalLimits.MAX_START_TEMP_C} °C."
            )
            else -> {
                startMissionRun(mission)
                Verdict.Accepted("Misión '${mission.name}' iniciada hacia ${mission.destination.displayName}.")
            }
        }
    }

    private fun handlePause(): Verdict = when (robot.operationalState) {
        OperationalState.WAITING_ELEVATOR -> Verdict.Refused(
            "Protocolo de seguridad: no se puede pausar mientras cruza el umbral o espera en el ascensor."
        )
        OperationalState.EXECUTING_MISSION, OperationalState.RETURNING_TO_SAFE_POINT -> {
            motionJob?.cancel()
            resumeTarget = robot.operationalState
            robot = robot.copy(
                operationalState = OperationalState.PAUSED,
                speedMps = 0f,
                posture = RobotPosture.BIPED_STANDING,
                hriLedColor = LED_AMBER,
                hriVoiceStatus = "Unidad en pausa por indicación del operador."
            )
            Verdict.Accepted("Marcha pausada de forma segura en ${robot.currentNode.displayName}.")
        }
        else -> Verdict.Refused(
            "No hay marcha activa para pausar (estado: ${robot.operationalState.displayName})."
        )
    }

    private fun handleResume(): Verdict = when (robot.operationalState) {
        OperationalState.PAUSED -> {
            val target = resumeTarget ?: OperationalState.EXECUTING_MISSION
            resumeTarget = null
            if (target == OperationalState.RETURNING_TO_SAFE_POINT || activeRun == null) {
                val destination = returnDestination ?: nearestSafePoint(robot.currentNode)
                startReturn(destination, "Reanudando regreso hacia ${destination.displayName}.")
                Verdict.Accepted("Regreso hacia ${destination.displayName} reanudado.")
            } else {
                setWalking("Reanudando trayectoria.")
                launchRouteRunner()
                Verdict.Accepted("Misión reanudada.")
            }
        }
        OperationalState.INCIDENT_BLOCKED -> when {
            CommandRules.hasUnacknowledgedCritical(robot) -> Verdict.Refused(
                "Reconozca las alertas críticas antes de reanudar."
            )
            obstaclePresent -> Verdict.Refused(
                "El LiDAR sigue detectando un obstáculo frontal a 0.35 m (mínimo ${OperationalLimits.OBSTACLE_MIN_CLEARANCE_M} m)."
            )
            else -> {
                setWalking("Pasillo despejado. Reanudando trayectoria.")
                launchRouteRunner()
                Verdict.Accepted("Pasillo despejado. Misión reanudada.")
            }
        }
        else -> Verdict.Refused(
            "No hay misión pausada ni bloqueada para reanudar (estado: ${robot.operationalState.displayName})."
        )
    }

    private fun handleCancel(): Verdict {
        if (activeRun == null) return Verdict.Refused("No hay ninguna misión activa para cancelar.")
        if (robot.operationalState !in CommandRules.CANCELLABLE_STATES) {
            return Verdict.Refused("No se puede cancelar en estado ${robot.operationalState.displayName}.")
        }
        motionJob?.cancel()
        raiseAlert(
            AlertSeverity.WARNING,
            "Misión cancelada",
            "La misión fue abortada por orden del operador en ${robot.currentNode.displayName}."
        )
        finishMission(MissionStatus.CANCELLED, "Cancelada por el operador en ${robot.currentNode.displayName}.")
        startReturn(HospitalNode.DOCK_BASE, "Misión cancelada. Regreso a la base de carga.")
        return Verdict.Accepted("Misión cancelada. El robot regresa a la base de carga.")
    }

    private fun handleReturnToSafePoint(): Verdict = when {
        robot.batteryPercentage < OperationalLimits.CRITICAL_BATTERY_CROUCH_PCT -> Verdict.Refused(
            "Batería crítica (${robot.batteryPercentage}%): no es seguro caminar. El robot debe agacharse en el lugar."
        )
        robot.operationalState in CommandRules.RESTING_STATES ||
            robot.operationalState == OperationalState.LOW_BATTERY_CROUCHED -> Verdict.Refused(
            "Orden no aplicable en estado ${robot.operationalState.displayName}."
        )
        robot.operationalState == OperationalState.RETURNING_TO_SAFE_POINT -> Verdict.Refused(
            "El robot ya está regresando al punto seguro."
        )
        else -> {
            resumeTarget = null
            val destination = nearestSafePoint(robot.currentNode)
            startReturn(destination, "Desviando hacia ${destination.displayName}.")
            Verdict.Accepted("Regreso al punto seguro más cercano confirmado: ${destination.displayName}.")
        }
    }

    private fun handleOperationalStop(): Verdict {
        if (robot.operationalState == OperationalState.LOW_BATTERY_CROUCHED) {
            return Verdict.Accepted("El robot ya está inmóvil en postura agachada.")
        }
        if (robot.operationalState != OperationalState.EMERGENCY_STOPPED) {
            stoppedFrom = robot.operationalState
        }
        motionJob?.cancel()
        resumeTarget = null
        robot = robot.copy(
            operationalState = OperationalState.EMERGENCY_STOPPED,
            speedMps = 0f,
            posture = RobotPosture.BIPED_STANDING,
            hriLedColor = LED_RED,
            hriVoiceStatus = "Detención operativa activada."
        )
        raiseAlert(
            AlertSeverity.CRITICAL,
            "Detención operativa",
            "Detención por software ejecutada. No sustituye la parada física de emergencia. El rearme se hace en forma presencial."
        )
        return Verdict.Accepted("Detención operativa completada: velocidad 0.0 m/s.")
    }

    private fun handleAcknowledge(alertId: String): Verdict {
        val alert = robot.activeAlerts.find { it.id == alertId }
            ?: return Verdict.Refused("Alerta '$alertId' no encontrada.")
        if (alert.isAcknowledged) return Verdict.Refused("La alerta '${alert.title}' ya estaba reconocida.")
        val now = clock()
        robot = robot.copy(
            activeAlerts = robot.activeAlerts.map {
                if (it.id == alertId) it.copy(isAcknowledged = true, acknowledgedAt = now) else it
            }
        )
        return Verdict.Accepted("Alerta '${alert.title}' reconocida.")
    }

    // =====================================================================
    // Ejecución de misiones
    // =====================================================================

    private fun startMissionRun(mission: MissionConfig, incidentAt: HospitalNode? = null) {
        motionJob?.cancel()
        relockJob?.cancel()
        val payload = robot.servicePayload
        val firstStep = if (mission.route.firstOrNull() == mission.origin) 1 else 0
        val run = MissionRun(
            config = mission,
            runId = "${mission.missionId}-${shortId().take(4)}",
            startedAt = clock(),
            routeIndex = firstStep,
            incidentAtNode = incidentAt,
            maxTemp = payload.compartmentTempCelsius,
            maxVib = payload.bioSampleStabilityIndexG
        )
        activeRun = run
        resumeTarget = null
        robot = robot.copy(
            operationalState = OperationalState.EXECUTING_MISSION,
            currentMission = mission,
            missionRunId = run.runId,
            missionProgressPct = 0,
            currentNode = mission.origin,
            speedMps = CRUISE_SPEED,
            posture = RobotPosture.BIPED_WALKING,
            hriLedColor = LED_CYAN,
            hriVoiceStatus = "Unidad médica en tránsito. Conserve su derecha."
        )
        launchRouteRunner()
    }

    /** Avanza por la ruta desde el último nodo alcanzado; se puede relanzar tras una pausa. */
    private fun launchRouteRunner() {
        motionJob?.cancel()
        motionJob = scope.launch {
            val run = activeRun ?: return@launch
            val route = run.config.route
            val lastIndex = (route.size - 1).coerceAtLeast(1)
            while (run.routeIndex < route.size) {
                delay(timing.routeStepMs)
                val node = route[run.routeIndex]
                val atElevator = node == HospitalNode.ELEVATOR_BANK
                robot = robot.copy(
                    currentNode = node,
                    missionProgressPct = (run.routeIndex * 100 / lastIndex).coerceAtMost(99),
                    operationalState = if (atElevator) OperationalState.WAITING_ELEVATOR else OperationalState.EXECUTING_MISSION,
                    posture = when (node) {
                        HospitalNode.FIRE_DOOR_PB -> RobotPosture.OPERATING_DOOR_ARM
                        HospitalNode.ELEVATOR_BANK -> RobotPosture.BIPED_STANDING
                        else -> RobotPosture.BIPED_WALKING
                    },
                    speedMps = if (atElevator) 0f else CRUISE_SPEED,
                    hriLedColor = LED_CYAN,
                    hriVoiceStatus = if (atElevator) "Esperando ascensor hacia Piso 2." else "Transitando por: ${node.displayName}"
                )
                run.routeIndex++
                if (run.incidentAtNode == node) {
                    run.incidentAtNode = null
                    blockByObstacle(node)
                    publish()
                    return@launch
                }
                publish()
            }
            arriveAndHandOff(run)
        }
    }

    private suspend fun arriveAndHandOff(run: MissionRun) {
        robot = robot.copy(
            operationalState = OperationalState.AWAITING_NFC_HANDOFF,
            missionProgressPct = 100,
            speedMps = 0f,
            posture = RobotPosture.BIPED_STANDING,
            hriLedColor = LED_VIOLET,
            hriVoiceStatus = "Destino alcanzado. Acerque su credencial para retirar la carga."
        )
        publish()
        delay(timing.handoffMs)

        robot = robot.copy(
            servicePayload = robot.servicePayload.copy(smartLockState = SmartLockState.UNLOCKED_NFC_AUTHORIZED),
            hriLedColor = LED_GREEN,
            hriVoiceStatus = "Credencial validada. Custodia transferida."
        )
        raiseAlert(
            AlertSeverity.INFO,
            "Entrega validada",
            "Misión ${run.config.name} entregada en ${run.config.destination.displayName} con validación NFC."
        )
        finishMission(MissionStatus.COMPLETED, "Entrega validada por NFC en destino.")
        scheduleRelock()
        // Tras la entrega despeja el puesto de enfermería y espera en el punto seguro más cercano.
        startReturn(
            nearestSafePoint(robot.currentNode),
            "Entrega completada. Despejo el puesto y espero en el punto seguro."
        )
        publish()
    }

    /** Tras retirar la carga, el compartimento se vuelve a sellar para el próximo despacho. */
    private fun scheduleRelock() {
        relockJob?.cancel()
        relockJob = scope.launch {
            delay(timing.relockMs)
            if (robot.servicePayload.smartLockState == SmartLockState.UNLOCKED_NFC_AUTHORIZED) {
                robot = robot.copy(
                    servicePayload = robot.servicePayload.copy(smartLockState = SmartLockState.SEALED_LOCKED),
                    hriVoiceStatus = "Compartimento cerrado y sellado. Listo para nueva carga."
                )
                publish()
            }
        }
    }

    /**
     * Desplazamiento hacia un punto seguro (la base en PB o la bahía de espera en P2).
     * Si había una misión activa, queda registrada como cancelada: la carga no se entregó.
     */
    private fun startReturn(destination: HospitalNode, voice: String) {
        motionJob?.cancel()
        // Regresar es la acción que resuelve la batería baja: se camina despacio y con consumo normal.
        fastBatteryDrain = false
        returnDestination = destination
        val sameFloor = robot.currentNode.floor == destination.floor
        robot = robot.copy(
            operationalState = OperationalState.RETURNING_TO_SAFE_POINT,
            speedMps = RETURN_SPEED,
            posture = RobotPosture.BIPED_WALKING,
            hriLedColor = LED_AMBER,
            hriVoiceStatus = voice
        )
        motionJob = scope.launch {
            delay(if (sameFloor) timing.returnMs else timing.returnMs * 2)
            if (activeRun != null) {
                finishMission(
                    MissionStatus.CANCELLED,
                    "Desviada a ${destination.displayName} por orden del operador; carga no entregada."
                )
            }
            returnDestination = null
            val atBase = destination == HospitalNode.DOCK_BASE
            robot = robot.copy(
                operationalState = if (atBase) OperationalState.IDLE_AT_DOCK else OperationalState.AT_SAFE_POINT,
                currentNode = destination,
                speedMps = 0f,
                posture = RobotPosture.BIPED_STANDING,
                hriLedColor = LED_CYAN,
                hriVoiceStatus = if (atBase) "En base de carga." else "En punto seguro de espera, fuera del flujo de camillas."
            )
            publish()
        }
    }

    /** Punto seguro más cercano: la base en Planta Baja; la bahía de espera desde el ascensor o el Piso 2. */
    private fun nearestSafePoint(node: HospitalNode): HospitalNode =
        if (node.floor == 0) HospitalNode.DOCK_BASE else HospitalNode.SAFE_POINT_P2

    private fun blockByObstacle(node: HospitalNode) {
        obstaclePresent = true
        robot = robot.copy(
            operationalState = OperationalState.INCIDENT_BLOCKED,
            speedMps = 0f,
            posture = RobotPosture.BIPED_STANDING,
            hriLedColor = LED_RED,
            hriVoiceStatus = "Atención: pasillo bloqueado. Unidad médica detenida."
        )
        raiseAlert(
            AlertSeverity.CRITICAL,
            "Obstáculo en pasillo",
            "Camilla bloqueando el paso frontal a 0.35 m en ${node.displayName}. Decisión requerida: esperar y reanudar, o desviar a punto seguro."
        )
        obstacleJob?.cancel()
        obstacleJob = scope.launch {
            delay(timing.obstacleClearMs)
            obstaclePresent = false
            raiseAlert(
                AlertSeverity.INFO,
                "Pasillo despejado",
                "El LiDAR ya no detecta obstáculos a menos de ${OperationalLimits.OBSTACLE_MIN_CLEARANCE_M} m. Puede reanudar."
            )
            publish()
        }
    }

    private fun crouchForLowBattery() {
        motionJob?.cancel()
        resumeTarget = null
        fastBatteryDrain = false
        robot = robot.copy(
            operationalState = OperationalState.LOW_BATTERY_CROUCHED,
            speedMps = 0f,
            posture = RobotPosture.CROUCHED_SAFE_LOCK,
            hriLedColor = LED_RED,
            hriVoiceStatus = "Batería crítica. Postura agachada de seguridad activa."
        )
        raiseAlert(
            AlertSeverity.CRITICAL,
            "Batería crítica (${robot.batteryPercentage}%) — robot agachado",
            "El robot adoptó la postura agachada (CROUCHED_SAFE_LOCK) para evitar un desplome. Requiere recarga en el lugar."
        )
        finishMission(MissionStatus.FAILED, "Interrumpida por batería crítica: el robot quedó agachado en ${robot.currentNode.displayName}.")
    }

    private fun finishMission(status: MissionStatus, notes: String) {
        val run = activeRun ?: return
        emitEvent(
            RobotEvent.MissionFinished(
                MissionOutcome(
                    runId = run.runId,
                    mission = run.config,
                    status = status,
                    startedAt = run.startedAt,
                    endedAt = clock(),
                    finalBatteryPct = robot.batteryPercentage,
                    maxTempCelsius = run.maxTemp,
                    maxVibrationG = run.maxVib,
                    notes = notes
                )
            )
        )
        activeRun = null
        resumeTarget = null
        robot = robot.copy(currentMission = null, missionRunId = null, missionProgressPct = 0)
    }

    private fun setWalking(voice: String) {
        robot = robot.copy(
            operationalState = OperationalState.EXECUTING_MISSION,
            speedMps = CRUISE_SPEED,
            posture = RobotPosture.BIPED_WALKING,
            hriLedColor = LED_CYAN,
            hriVoiceStatus = voice
        )
    }

    // =====================================================================
    // Física simplificada y alertas reactivas
    // =====================================================================

    private fun tick() {
        if (robot.operationalState in ACTIVE_STATES) {
            batteryLevel -= if (fastBatteryDrain) FAST_DRAIN_PER_TICK else NORMAL_DRAIN_PER_TICK
        }
        batteryLevel = batteryLevel.coerceIn(0.0, 100.0)

        val payload = robot.servicePayload
        val temp = payload.compartmentTempCelsius
        val newTemp = round2(temp + (coolingTargetC - temp) * 0.3f + random.nextInt(-1, 2) * 0.05f)
        val newVib = if (robot.speedMps > 0.05f) round2(0.12f + random.nextInt(0, 6) * 0.01f) else 0.05f

        // Dentro del ascensor (cabina metálica) el enlace Wi-Fi se degrada.
        val inElevator = robot.currentNode == HospitalNode.ELEVATOR_BANK
        val latencyRange = if (inElevator) 180..420 else 28..85
        val signalRange = if (inElevator) -84..-74 else -75..-20
        robot = robot.copy(
            latencyMs = (robot.latencyMs + random.nextInt(-3, 5) * (if (inElevator) 15 else 1))
                .coerceIn(latencyRange.first, latencyRange.last),
            signalDbm = (robot.signalDbm + random.nextInt(-2, 3))
                .coerceIn(signalRange.first, signalRange.last),
            batteryTempCelsius = round1(robot.batteryTempCelsius + random.nextInt(-1, 2) * 0.1f).coerceIn(26f, 42f),
            servicePayload = payload.copy(compartmentTempCelsius = newTemp, bioSampleStabilityIndexG = newVib)
        )
        syncBattery()
        activeRun?.track(newTemp, newVib)
        evaluateThresholds()
        publish()
    }

    private fun evaluateThresholds() {
        val battery = robot.batteryPercentage
        val payload = robot.servicePayload

        condition(AlertCondition.LOW_BATTERY, battery < OperationalLimits.MIN_BATTERY_TO_START_PCT) {
            raiseAlert(
                AlertSeverity.WARNING,
                "Batería baja ($battery%)",
                "Por debajo del ${OperationalLimits.MIN_BATTERY_TO_START_PCT}% no se pueden iniciar misiones. Evalúe regresar a punto seguro."
            )
        }
        if (battery < OperationalLimits.CRITICAL_BATTERY_CROUCH_PCT &&
            robot.operationalState !in CommandRules.RESTING_STATES &&
            robot.operationalState != OperationalState.LOW_BATTERY_CROUCHED
        ) {
            crouchForLowBattery()
        }
        condition(AlertCondition.COLD_CHAIN, payload.compartmentTempCelsius > OperationalLimits.COLD_CHAIN_BREAK_TEMP_C) {
            raiseAlert(
                AlertSeverity.CRITICAL,
                "Cadena de frío comprometida (${payload.compartmentTempCelsius} °C)",
                "La cápsula superó ${OperationalLimits.COLD_CHAIN_BREAK_TEMP_C} °C. Riesgo para la muestra: reconozca y cancele o desvíe a punto seguro."
            )
        }
        condition(AlertCondition.VIBRATION_CRITICAL, payload.bioSampleStabilityIndexG > OperationalLimits.VIBRATION_CRITICAL_G) {
            raiseAlert(
                AlertSeverity.CRITICAL,
                "Vibración crítica (${payload.bioSampleStabilityIndexG} G)",
                "Riesgo de hemólisis en las muestras transportadas."
            )
        }
        condition(
            AlertCondition.VIBRATION_WARNING,
            payload.bioSampleStabilityIndexG > OperationalLimits.VIBRATION_WARNING_G &&
                payload.bioSampleStabilityIndexG <= OperationalLimits.VIBRATION_CRITICAL_G
        ) {
            raiseAlert(
                AlertSeverity.WARNING,
                "Vibración elevada (${payload.bioSampleStabilityIndexG} G)",
                "La vibración supera ${OperationalLimits.VIBRATION_WARNING_G} G. Considere reducir la velocidad."
            )
        }
        condition(AlertCondition.TAMPER, payload.smartLockState == SmartLockState.TAMPER_ALERT) {
            raiseAlert(
                AlertSeverity.CRITICAL,
                "Alerta de manipulación (tamper)",
                "Se detectó un intento de apertura no autorizada del compartimento biológico."
            )
        }
    }

    /** Emite la alerta una vez al entrar en la condición; se rearma cuando la condición desaparece. */
    private inline fun condition(key: AlertCondition, active: Boolean, onEnter: () -> Unit) {
        if (active) {
            if (activeConditions.add(key)) onEnter()
        } else {
            activeConditions.remove(key)
        }
    }

    private fun raiseAlert(severity: AlertSeverity, title: String, message: String) {
        val alert = RobotAlert(
            id = shortId(),
            severity = severity,
            title = title,
            message = message,
            timestamp = clock(),
            relatedMissionId = activeRun?.runId
        )
        val all = robot.activeAlerts + alert
        // Se conservan todas las alertas sin reconocer y solo las últimas reconocidas.
        val pending = all.filter { !it.isAcknowledged }
        val acknowledged = all.filter { it.isAcknowledged }.takeLast(MAX_ACKNOWLEDGED_ALERTS)
        robot = robot.copy(activeAlerts = (pending + acknowledged).sortedBy { it.timestamp })
        emitEvent(RobotEvent.AlertRaised(alert))
    }

    // =====================================================================
    // Enlace
    // =====================================================================

    /** Publica el estado del robot hacia la app solo si hay enlace. */
    private fun publish() {
        if (_linkUp.value) {
            _telemetry.value = robot.copy(
                connectionStatus = if (robot.currentNode == HospitalNode.ELEVATOR_BANK) {
                    ConnectionStatus.DEGRADED
                } else {
                    ConnectionStatus.ONLINE
                },
                lastUpdatedTimestamp = clock()
            )
        }
    }

    private fun emitEvent(event: RobotEvent) {
        if (_linkUp.value) _events.tryEmit(event) else queuedEvents.addLast(event)
    }

    // =====================================================================
    // Controles del simulador
    // =====================================================================

    override fun setForceRejectNext(value: Boolean) {
        _forceRejectNext.value = value
    }

    override fun restoreConnection() {
        scope.launch {
            _linkUp.value = true
            while (queuedEvents.isNotEmpty()) _events.tryEmit(queuedEvents.removeFirst())
            publish()
        }
    }

    override fun physicalRearm() {
        scope.launch {
            if (robot.operationalState != OperationalState.EMERGENCY_STOPPED) return@launch
            val previous = stoppedFrom
            stoppedFrom = null
            if (previous == OperationalState.INCIDENT_BLOCKED && obstaclePresent) {
                robot = robot.copy(
                    operationalState = OperationalState.INCIDENT_BLOCKED,
                    hriLedColor = LED_RED,
                    hriVoiceStatus = "Rearmado. El obstáculo sigue presente."
                )
            } else {
                resumeTarget = when {
                    previous == OperationalState.RETURNING_TO_SAFE_POINT -> OperationalState.RETURNING_TO_SAFE_POINT
                    activeRun != null -> OperationalState.EXECUTING_MISSION
                    else -> null
                }
                robot = robot.copy(
                    operationalState = stateAfterPhysicalRecovery(),
                    posture = RobotPosture.BIPED_STANDING,
                    hriLedColor = LED_AMBER,
                    hriVoiceStatus = "Rearme presencial realizado. En espera de instrucciones."
                )
            }
            raiseAlert(
                AlertSeverity.INFO,
                "Rearme presencial",
                "Un técnico rearmó el robot en el lugar. Desde la consola puede reanudar, cancelar o regresar a punto seguro."
            )
            publish()
        }
    }

    override fun physicalBatterySwap() {
        scope.launch {
            if (robot.operationalState != OperationalState.LOW_BATTERY_CROUCHED) return@launch
            batteryLevel = 100.0
            fastBatteryDrain = false
            syncBattery()
            resumeTarget = null
            robot = robot.copy(
                operationalState = stateAfterPhysicalRecovery(),
                posture = RobotPosture.BIPED_STANDING,
                hriLedColor = LED_AMBER,
                hriVoiceStatus = "Batería reemplazada. En espera de instrucciones."
            )
            raiseAlert(
                AlertSeverity.INFO,
                "Batería reemplazada",
                "Recarga presencial (hot-swap) completada al 100%. Ordene regresar a punto seguro."
            )
            evaluateThresholds()
            publish()
        }
    }

    /**
     * Tras una intervención presencial el robot queda en pausa a la espera de órdenes, salvo que
     * no tenga misión y ya esté en la base o en un punto seguro: ahí queda directamente en reposo.
     */
    private fun stateAfterPhysicalRecovery(): OperationalState = when {
        activeRun != null || resumeTarget != null -> OperationalState.PAUSED
        robot.currentNode == HospitalNode.DOCK_BASE -> OperationalState.IDLE_AT_DOCK
        robot.currentNode == HospitalNode.SAFE_POINT_P2 -> OperationalState.AT_SAFE_POINT
        else -> OperationalState.PAUSED
    }

    override fun resetSimulation() {
        scope.launch {
            resetState()
            _commandState.value = CommandExecutionState.Idle
            publish()
        }
    }

    override fun injectScenario(scenario: SimulationScenario) {
        scope.launch {
            when (scenario) {
                SimulationScenario.NORMAL_MISSION -> {
                    resetState()
                    batteryLevel = 85.0
                    syncBattery()
                    robot = robot.copy(currentNode = HospitalNode.LAB_CENTRAL)
                    startMissionRun(MissionCatalog.bloodGas)
                }
                SimulationScenario.LOW_BATTERY -> {
                    // Se demuestra durante una misión para que la acción "regresar" esté disponible.
                    if (activeRun == null) {
                        resetState()
                        startMissionRun(MissionCatalog.bloodGas)
                    }
                    batteryLevel = 14.0
                    fastBatteryDrain = true
                    syncBattery()
                    robot = robot.copy(hriVoiceStatus = "Batería baja: se recomienda regresar a punto seguro.")
                    evaluateThresholds()
                }
                SimulationScenario.DISCONNECTION -> {
                    _linkUp.value = false
                }
                SimulationScenario.INCIDENT -> {
                    resetState()
                    batteryLevel = 76.0
                    syncBattery()
                    startMissionRun(MissionCatalog.plasma, incidentAt = HospitalNode.FIRE_DOOR_PB)
                }
                SimulationScenario.COLD_CHAIN_FAILURE -> {
                    if (activeRun == null) {
                        resetState()
                        startMissionRun(MissionCatalog.plasma)
                    }
                    coolingTargetC = FAILED_CAPSULE_TEMP_C
                    robot = robot.copy(
                        hriLedColor = LED_RED,
                        hriVoiceStatus = "Alerta biológica: cadena de frío rota y manipulación detectada.",
                        servicePayload = robot.servicePayload.copy(
                            compartmentTempCelsius = FAILED_CAPSULE_TEMP_C,
                            smartLockState = SmartLockState.TAMPER_ALERT
                        )
                    )
                    activeRun?.track(FAILED_CAPSULE_TEMP_C, robot.servicePayload.bioSampleStabilityIndexG)
                    evaluateThresholds()
                }
            }
            publish()
        }
    }

    private fun resetState() {
        motionJob?.cancel()
        obstacleJob?.cancel()
        relockJob?.cancel()
        activeRun = null
        resumeTarget = null
        returnDestination = null
        stoppedFrom = null
        obstaclePresent = false
        fastBatteryDrain = false
        batteryLevel = INITIAL_BATTERY
        coolingTargetC = NOMINAL_CAPSULE_TEMP_C
        activeConditions.clear()
        queuedEvents.clear()
        robot = initialRobotState()
        _linkUp.value = true
    }

    private fun syncBattery() {
        robot = robot.copy(
            batteryPercentage = batteryLevel.toInt(),
            estimatedMinutesLeft = (batteryLevel * OperationalLimits.FULL_BATTERY_AUTONOMY_MIN / 100).toInt()
        )
    }

    private fun initialRobotState() = TelemetryState(
        robotId = "G1-EDU-MED01",
        firmwareVersion = "v2.4.1-DEX3",
        connectionStatus = ConnectionStatus.ONLINE,
        latencyMs = 42,
        signalDbm = -35,
        lastUpdatedTimestamp = clock(),
        batteryPercentage = INITIAL_BATTERY.toInt(),
        batteryTempCelsius = 28.5f,
        estimatedMinutesLeft = (INITIAL_BATTERY * OperationalLimits.FULL_BATTERY_AUTONOMY_MIN / 100).toInt(),
        operationalState = OperationalState.IDLE_AT_DOCK,
        currentNode = HospitalNode.DOCK_BASE,
        speedMps = 0f,
        posture = RobotPosture.BIPED_STANDING,
        hriLedColor = LED_CYAN,
        hriVoiceStatus = "Sistema listo en base de carga.",
        servicePayload = ServicePayloadTelemetry(
            compartmentTempCelsius = NOMINAL_CAPSULE_TEMP_C,
            bioSampleStabilityIndexG = 0.05f,
            smartLockState = SmartLockState.SEALED_LOCKED
        ),
        activeAlerts = emptyList()
    )

    private fun shortId() = UUID.randomUUID().toString().take(8).uppercase()

    private fun round1(v: Float) = Math.round(v * 10f) / 10f
    private fun round2(v: Float) = Math.round(v * 100f) / 100f

    private sealed interface Verdict {
        data class Accepted(val message: String) : Verdict
        data class Refused(val reason: String) : Verdict
    }

    private enum class AlertCondition { LOW_BATTERY, COLD_CHAIN, VIBRATION_WARNING, VIBRATION_CRITICAL, TAMPER }

    private class MissionRun(
        val config: MissionConfig,
        val runId: String,
        val startedAt: Long,
        var routeIndex: Int,
        var incidentAtNode: HospitalNode?,
        var maxTemp: Float,
        var maxVib: Float
    ) {
        fun track(temp: Float, vib: Float) {
            maxTemp = max(maxTemp, temp)
            maxVib = max(maxVib, vib)
        }
    }

    private companion object {
        const val INITIAL_BATTERY = 95.0
        const val NORMAL_DRAIN_PER_TICK = 0.1
        const val FAST_DRAIN_PER_TICK = 0.75
        const val NOMINAL_CAPSULE_TEMP_C = 4.0f
        const val FAILED_CAPSULE_TEMP_C = 8.4f
        const val CRUISE_SPEED = 0.6f
        const val RETURN_SPEED = 0.4f
        const val MAX_ACKNOWLEDGED_ALERTS = 5

        const val LED_CYAN = "#00F5D4"
        const val LED_AMBER = "#FFAB00"
        const val LED_RED = "#FF1744"
        const val LED_GREEN = "#00E676"
        const val LED_VIOLET = "#7C4DFF"

        val ACTIVE_STATES = setOf(
            OperationalState.EXECUTING_MISSION,
            OperationalState.WAITING_ELEVATOR,
            OperationalState.RETURNING_TO_SAFE_POINT,
            OperationalState.AWAITING_NFC_HANDOFF,
            OperationalState.INCIDENT_BLOCKED,
            OperationalState.PAUSED
        )
    }
}
