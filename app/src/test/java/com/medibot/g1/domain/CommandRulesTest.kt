package com.medibot.g1.domain

import com.medibot.g1.data.model.AlertSeverity
import com.medibot.g1.data.model.MissionCatalog
import com.medibot.g1.data.model.OperationalState
import com.medibot.g1.data.model.RobotAlert
import com.medibot.g1.data.model.TelemetryState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandRulesTest {

    private val idle = TelemetryState(lastUpdatedTimestamp = 0L)

    @Test
    fun `start is enabled only with every precondition met`() {
        assertTrue(CommandRules.availability(idle, isStale = false, isCommandPending = false).start)
        assertFalse(CommandRules.availability(idle, isStale = true, isCommandPending = false).start)
        assertFalse(CommandRules.availability(idle.copy(batteryPercentage = 19), false, false).start)
        assertFalse(
            CommandRules.availability(idle.copy(operationalState = OperationalState.PAUSED), false, false).start
        )
    }

    @Test
    fun `missions can also be dispatched from a safe point`() {
        val atSafePoint = idle.copy(operationalState = OperationalState.AT_SAFE_POINT)
        assertTrue(CommandRules.availability(atSafePoint, isStale = false, isCommandPending = false).start)
        assertFalse(CommandRules.availability(atSafePoint, isStale = false, isCommandPending = false).returnToSafePoint)
    }

    @Test
    fun `a disconnected console cannot issue any command, not even the stop`() {
        assertFalse(CommandAvailability.DISCONNECTED.operationalStop)
        assertFalse(CommandAvailability.DISCONNECTED.start)
    }

    @Test
    fun `a pending command blocks everything except the operational stop`() {
        val executing = idle.copy(
            operationalState = OperationalState.EXECUTING_MISSION,
            currentMission = MissionCatalog.bloodGas
        )
        val availability = CommandRules.availability(executing, isStale = false, isCommandPending = true)
        assertFalse(availability.pause)
        assertFalse(availability.cancel)
        assertFalse(availability.acknowledge)
        assertTrue(availability.operationalStop)
    }

    @Test
    fun `resume after an incident requires acknowledging critical alerts`() {
        val critical = RobotAlert("A1", AlertSeverity.CRITICAL, "Obstáculo", "", timestamp = 0L)
        val blocked = idle.copy(
            operationalState = OperationalState.INCIDENT_BLOCKED,
            currentMission = MissionCatalog.plasma,
            activeAlerts = listOf(critical)
        )
        assertFalse(CommandRules.availability(blocked, false, false).resume)

        val acknowledged = blocked.copy(activeAlerts = listOf(critical.copy(isAcknowledged = true)))
        assertTrue(CommandRules.availability(acknowledged, false, false).resume)
    }

    @Test
    fun `pause is available while returning and cancel while waiting the elevator`() {
        val returning = idle.copy(operationalState = OperationalState.RETURNING_TO_SAFE_POINT)
        assertTrue(CommandRules.availability(returning, false, false).pause)

        val elevator = idle.copy(
            operationalState = OperationalState.WAITING_ELEVATOR,
            currentMission = MissionCatalog.bloodGas
        )
        assertTrue(CommandRules.availability(elevator, false, false).cancel)
        assertFalse(CommandRules.availability(elevator, false, false).pause)
    }
}
