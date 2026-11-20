package com.medibot.g1.data.repository

import com.medibot.g1.data.model.AlertSeverity
import com.medibot.g1.data.model.CommandExecutionState
import com.medibot.g1.data.model.ConnectionStatus
import com.medibot.g1.data.model.HospitalNode
import com.medibot.g1.data.model.MissionCatalog
import com.medibot.g1.data.model.MissionOutcome
import com.medibot.g1.data.model.MissionStatus
import com.medibot.g1.data.model.OperationalState
import com.medibot.g1.data.model.RobotCommand
import com.medibot.g1.data.model.RobotEvent
import com.medibot.g1.data.model.SmartLockState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Pruebas del robot simulado con tiempo virtual: cada escenario se ejecuta en milisegundos.
 * Tiempos por defecto: latencia 1.2 s, paso de ruta 2.5 s, entrega NFC 2.5 s, timeout 2.5 s.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SimulatedRobotRepositoryTest {

    private val step = SimulationTiming().routeStepMs
    private val handoff = SimulationTiming().handoffMs

    private suspend fun TestScope.withRobot(block: suspend TestScope.(SimulatedRobotRepository, List<RobotEvent>) -> Unit) {
        val robot = SimulatedRobotRepository(
            dispatcher = StandardTestDispatcher(testScheduler),
            clock = { testScheduler.currentTime },
            random = Random(7)
        )
        val events = mutableListOf<RobotEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            robot.events.collect { events += it }
        }
        try {
            block(robot, events)
        } finally {
            robot.shutdown()
        }
    }

    private suspend fun SimulatedRobotRepository.send(command: RobotCommand) =
        sendCommand(command, operatorId = "OP-TEST")

    private fun List<RobotEvent>.outcomes(): List<MissionOutcome> =
        filterIsInstance<RobotEvent.MissionFinished>().map { it.outcome }

    @Test
    fun `dispatched mission crosses the elevator, completes and the next one can start`() = runTest {
        withRobot { robot, events ->
            val first = robot.send(RobotCommand.StartMission(MissionCatalog.bloodGas))
            assertTrue(first is CommandExecutionState.Confirmed)
            val firstRunId = robot.telemetry.value.missionRunId

            advanceTimeBy(4 * step + handoff + 100)
            assertEquals(MissionStatus.COMPLETED, events.outcomes().single().status)

            // Tras entregar despeja el puesto y espera en el punto seguro del Piso 2.
            advanceTimeBy(SimulationTiming().relockMs + 100)
            assertEquals(OperationalState.AT_SAFE_POINT, robot.telemetry.value.operationalState)
            assertEquals(HospitalNode.SAFE_POINT_P2, robot.telemetry.value.currentNode)
            assertEquals(SmartLockState.SEALED_LOCKED, robot.telemetry.value.servicePayload.smartLockState)

            val second = robot.send(RobotCommand.StartMission(MissionCatalog.bloodGas))
            assertTrue(second is CommandExecutionState.Confirmed)
            assertNotEquals(firstRunId, robot.telemetry.value.missionRunId)
        }
    }

    @Test
    fun `pause then resume continues the route until delivery`() = runTest {
        withRobot { robot, events ->
            robot.send(RobotCommand.StartMission(MissionCatalog.bloodGas))
            advanceTimeBy(step + 100)

            assertTrue(robot.send(RobotCommand.PauseMission) is CommandExecutionState.Confirmed)
            val progressWhilePaused = robot.telemetry.value.missionProgressPct
            advanceTimeBy(10_000)
            assertEquals(OperationalState.PAUSED, robot.telemetry.value.operationalState)
            assertEquals(progressWhilePaused, robot.telemetry.value.missionProgressPct)

            assertTrue(robot.send(RobotCommand.ResumeMission) is CommandExecutionState.Confirmed)
            advanceTimeBy(3 * step + handoff + 100)
            assertEquals(MissionStatus.COMPLETED, events.outcomes().single().status)
        }
    }

    @Test
    fun `incident requires ack and a clear corridor before resuming`() = runTest {
        withRobot { robot, events ->
            robot.injectScenario(SimulationScenario.INCIDENT)
            runCurrent()
            advanceTimeBy(step + 100)
            assertEquals(OperationalState.INCIDENT_BLOCKED, robot.telemetry.value.operationalState)

            val unacked = robot.send(RobotCommand.ResumeMission)
            assertTrue(unacked is CommandExecutionState.Rejected)

            val alert = robot.telemetry.value.activeAlerts.first { it.severity == AlertSeverity.CRITICAL }
            assertTrue(robot.send(RobotCommand.AcknowledgeAlert(alert.id)) is CommandExecutionState.Confirmed)

            val obstacleStillThere = robot.send(RobotCommand.ResumeMission)
            assertTrue(obstacleStillThere is CommandExecutionState.Rejected)

            advanceTimeBy(SimulationTiming().obstacleClearMs)
            assertTrue(robot.send(RobotCommand.ResumeMission) is CommandExecutionState.Confirmed)

            advanceTimeBy(3 * step + handoff + 100)
            assertEquals(MissionStatus.COMPLETED, events.outcomes().single().status)
        }
    }

    @Test
    fun `without link commands get no response, data freezes and events arrive on recovery`() = runTest {
        withRobot { robot, events ->
            robot.send(RobotCommand.StartMission(MissionCatalog.bloodGas))
            robot.injectScenario(SimulationScenario.DISCONNECTION)
            runCurrent()
            val frozenAt = robot.telemetry.value.lastUpdatedTimestamp

            val result = robot.send(RobotCommand.PauseMission)
            assertTrue(result is CommandExecutionState.NoResponse)
            assertEquals(frozenAt, robot.telemetry.value.lastUpdatedTimestamp)

            // El robot sigue operando y termina la misión sin que la app lo sepa.
            advanceTimeBy(4 * step + handoff)
            assertTrue(events.outcomes().isEmpty())

            robot.restoreConnection()
            runCurrent()
            assertEquals(MissionStatus.COMPLETED, events.outcomes().single().status)
            assertTrue(robot.telemetry.value.lastUpdatedTimestamp > frozenAt)
        }
    }

    @Test
    fun `cancel while paused is recorded as cancelled`() = runTest {
        withRobot { robot, events ->
            robot.send(RobotCommand.StartMission(MissionCatalog.plasma))
            advanceTimeBy(step + 100)
            robot.send(RobotCommand.PauseMission)

            assertTrue(robot.send(RobotCommand.CancelMission) is CommandExecutionState.Confirmed)
            assertEquals(MissionStatus.CANCELLED, events.outcomes().single().status)
        }
    }

    @Test
    fun `ignoring low battery triggers the forced return below 15 percent`() = runTest {
        withRobot { robot, events ->
            robot.injectScenario(SimulationScenario.LOW_BATTERY)
            runCurrent()
            assertTrue(robot.telemetry.value.activeAlerts.any { it.severity == AlertSeverity.WARNING })

            advanceTimeBy(10_000)
            assertEquals(OperationalState.RETURNING_TO_SAFE_POINT, robot.telemetry.value.operationalState)
            assertTrue(robot.telemetry.value.activeAlerts.any { it.title.startsWith("Retorno forzado") })

            advanceTimeBy(SimulationTiming().returnMs * 2 + 100)
            val outcome = events.outcomes().single()
            assertEquals(MissionStatus.CANCELLED, outcome.status)
            assertTrue(outcome.notes.startsWith("Retorno forzado"))
            assertTrue(robot.telemetry.value.operationalState in setOf(OperationalState.AT_SAFE_POINT, OperationalState.IDLE_AT_DOCK))
        }
    }

    @Test
    fun `a paused robot that keeps draining ends crouched with a failed mission`() = runTest {
        withRobot { robot, events ->
            robot.injectScenario(SimulationScenario.LOW_BATTERY)
            runCurrent()
            assertTrue(robot.send(RobotCommand.PauseMission) is CommandExecutionState.Confirmed)

            advanceTimeBy(25_000)
            assertEquals(OperationalState.LOW_BATTERY_CROUCHED, robot.telemetry.value.operationalState)
            assertEquals(MissionStatus.FAILED, events.outcomes().single().status)
        }
    }

    @Test
    fun `pharmacy mission conditions the capsule to its own range without cold chain alerts`() = runTest {
        withRobot { robot, events ->
            assertTrue(robot.send(RobotCommand.StartMission(MissionCatalog.pharmacy)) is CommandExecutionState.Confirmed)
            advanceTimeBy(4 * step + handoff + 100)

            val outcome = events.outcomes().single()
            assertEquals(MissionStatus.COMPLETED, outcome.status)
            assertTrue(outcome.maxTempCelsius in 15f..25f)
            assertTrue(robot.telemetry.value.activeAlerts.none { it.title.startsWith("Cadena") })
        }
    }

    @Test
    fun `returning in time avoids the crouch`() = runTest {
        withRobot { robot, events ->
            robot.injectScenario(SimulationScenario.LOW_BATTERY)
            runCurrent()

            assertTrue(robot.send(RobotCommand.ReturnToSafePoint) is CommandExecutionState.Confirmed)
            advanceTimeBy(SimulationTiming().returnMs + 100)

            assertEquals(OperationalState.IDLE_AT_DOCK, robot.telemetry.value.operationalState)
            assertEquals(HospitalNode.DOCK_BASE, robot.telemetry.value.currentNode)
            assertEquals(MissionStatus.CANCELLED, events.outcomes().single().status)
        }
    }

    @Test
    fun `cancel sends the robot back to the base`() = runTest {
        withRobot { robot, events ->
            robot.send(RobotCommand.StartMission(MissionCatalog.bloodGas))
            advanceTimeBy(step + 100)

            assertTrue(robot.send(RobotCommand.CancelMission) is CommandExecutionState.Confirmed)
            assertEquals(MissionStatus.CANCELLED, events.outcomes().single().status)
            assertEquals(OperationalState.RETURNING_TO_SAFE_POINT, robot.telemetry.value.operationalState)

            advanceTimeBy(SimulationTiming().returnMs + 100)
            assertEquals(OperationalState.IDLE_AT_DOCK, robot.telemetry.value.operationalState)
            assertEquals(HospitalNode.DOCK_BASE, robot.telemetry.value.currentNode)
        }
    }

    @Test
    fun `return from the second floor goes to the nearest safe point`() = runTest {
        withRobot { robot, events ->
            robot.send(RobotCommand.StartMission(MissionCatalog.bloodGas))
            advanceTimeBy(3 * step + 100)
            assertEquals(HospitalNode.SAFE_POINT_P2, robot.telemetry.value.currentNode)

            assertTrue(robot.send(RobotCommand.ReturnToSafePoint) is CommandExecutionState.Confirmed)
            advanceTimeBy(SimulationTiming().returnMs + 100)

            assertEquals(OperationalState.AT_SAFE_POINT, robot.telemetry.value.operationalState)
            assertEquals(MissionStatus.CANCELLED, events.outcomes().single().status)
        }
    }

    @Test
    fun `after a stop only the physical rearm lets the mission resume`() = runTest {
        withRobot { robot, events ->
            robot.send(RobotCommand.StartMission(MissionCatalog.bloodGas))
            advanceTimeBy(step + 100)
            robot.send(RobotCommand.OperationalStop)
            assertEquals(OperationalState.EMERGENCY_STOPPED, robot.telemetry.value.operationalState)
            assertTrue(robot.send(RobotCommand.ResumeMission) is CommandExecutionState.Rejected)

            robot.physicalRearm()
            runCurrent()
            assertEquals(OperationalState.PAUSED, robot.telemetry.value.operationalState)

            assertTrue(robot.send(RobotCommand.ResumeMission) is CommandExecutionState.Confirmed)
            advanceTimeBy(3 * step + handoff + 100)
            assertEquals(MissionStatus.COMPLETED, events.outcomes().single().status)
        }
    }

    @Test
    fun `a tampered capsule is not released at destination`() = runTest {
        withRobot { robot, events ->
            robot.injectScenario(SimulationScenario.COLD_CHAIN_FAILURE)
            runCurrent()
            advanceTimeBy(4 * step + handoff + 100)

            val outcome = events.outcomes().single()
            assertEquals(MissionStatus.FAILED, outcome.status)
            assertEquals(SmartLockState.TAMPER_ALERT, robot.telemetry.value.servicePayload.smartLockState)
        }
    }

    @Test
    fun `battery swap recovers a crouched robot`() = runTest {
        withRobot { robot, _ ->
            robot.injectScenario(SimulationScenario.LOW_BATTERY)
            runCurrent()
            robot.send(RobotCommand.PauseMission)
            advanceTimeBy(25_000)
            assertEquals(OperationalState.LOW_BATTERY_CROUCHED, robot.telemetry.value.operationalState)

            // Se agachó en el laboratorio (no es punto seguro): tras el cambio queda en pausa.
            robot.physicalBatterySwap()
            runCurrent()
            assertEquals(OperationalState.PAUSED, robot.telemetry.value.operationalState)
            assertEquals(100, robot.telemetry.value.batteryPercentage)
        }
    }

    @Test
    fun `link is reported as degraded inside the elevator`() = runTest {
        withRobot { robot, _ ->
            robot.send(RobotCommand.StartMission(MissionCatalog.bloodGas))
            advanceTimeBy(2 * step + 100)
            assertEquals(HospitalNode.ELEVATOR_BANK, robot.telemetry.value.currentNode)
            assertEquals(ConnectionStatus.DEGRADED, robot.telemetry.value.connectionStatus)

            advanceTimeBy(step)
            assertEquals(ConnectionStatus.ONLINE, robot.telemetry.value.connectionStatus)
        }
    }

    @Test
    fun `forced rejection applies to the next command only`() = runTest {
        withRobot { robot, _ ->
            robot.setForceRejectNext(true)
            assertTrue(
                robot.send(RobotCommand.StartMission(MissionCatalog.bloodGas)) is CommandExecutionState.Rejected
            )
            assertTrue(
                robot.send(RobotCommand.StartMission(MissionCatalog.bloodGas)) is CommandExecutionState.Confirmed
            )
        }
    }
}
