package com.medibot.g1.ui.viewmodel

import com.medibot.g1.data.model.AlertSeverity
import com.medibot.g1.data.model.CommandExecutionState
import com.medibot.g1.data.model.ConsoleRole
import com.medibot.g1.data.model.EventCategory
import com.medibot.g1.data.model.EventRecord
import com.medibot.g1.data.model.MissionCatalog
import com.medibot.g1.data.model.MissionOutcome
import com.medibot.g1.data.model.MissionRecord
import com.medibot.g1.data.repository.AuditRepository
import com.medibot.g1.data.repository.SimulatedRobotRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SupervisionViewModelTest {

    /** Auditoría en memoria: registra los eventos para poder verificarlos. */
    private class FakeAudit : AuditRepository {
        val logged = mutableListOf<Pair<String, String?>>()
        override val missions: Flow<List<MissionRecord>> = MutableStateFlow(emptyList())
        override val events: Flow<List<EventRecord>> = MutableStateFlow(emptyList())
        override suspend fun logEvent(
            category: EventCategory,
            severity: AlertSeverity,
            title: String,
            details: String,
            relatedMissionId: String?,
            operatorId: String?
        ) {
            logged += title to operatorId
        }
        override suspend fun saveMission(outcome: MissionOutcome) = Unit
        override suspend fun insertSampleData() = Unit
        override suspend fun clearAll() = Unit
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `operator ids are normalized and validated`() {
        assertEquals("OP-LAB-04", SupervisionViewModel.normalizeOperatorId("  op-lab-04 "))
        assertNull(SupervisionViewModel.normalizeOperatorId("ab"))
        assertNull(SupervisionViewModel.normalizeOperatorId("OP LAB"))
    }

    @Test
    fun `a supervisor cannot issue commands and an operator can`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val robot = SimulatedRobotRepository(dispatcher = dispatcher, clock = { testScheduler.currentTime })
        val audit = FakeAudit()
        try {
            val supervisorVm = SupervisionViewModel(robot, robot, audit, clock = { testScheduler.currentTime })
            assertFalse(supervisorVm.connect("G1-EDU-MED01", "x", ConsoleRole.SUPERVISOR))
            assertTrue(supervisorVm.connect("G1-EDU-MED01", "sup-01", ConsoleRole.SUPERVISOR))
            supervisorVm.startMission(MissionCatalog.bloodGas)
            advanceTimeBy(2_000)
            assertEquals(CommandExecutionState.Idle, robot.commandState.value)

            val operatorVm = SupervisionViewModel(robot, robot, audit, clock = { testScheduler.currentTime })
            assertFalse(operatorVm.connect("G1-EDU-MED02", "OP-01", ConsoleRole.OPERATOR)) // no disponible
            assertTrue(operatorVm.connect("G1-EDU-MED01", "OP-01", ConsoleRole.OPERATOR))
            operatorVm.startMission(MissionCatalog.bloodGas)
            advanceTimeBy(2_000)
            val result = robot.commandState.value
            assertTrue(result is CommandExecutionState.Confirmed)
            assertEquals("OP-01", (result as CommandExecutionState.Confirmed).operatorId)
            assertTrue(audit.logged.any { it.first.startsWith("Consola conectada") && it.second == "SUP-01" })
        } finally {
            robot.shutdown()
        }
    }
}
