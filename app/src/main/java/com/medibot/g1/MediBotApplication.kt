package com.medibot.g1

import android.app.Application
import android.content.Context
import com.medibot.g1.data.local.AppDatabase
import com.medibot.g1.data.repository.AuditRepository
import com.medibot.g1.data.repository.RobotEventRecorder
import com.medibot.g1.data.repository.RobotRepository
import com.medibot.g1.data.repository.RoomAuditRepository
import com.medibot.g1.data.repository.SimulatedRobotRepository
import com.medibot.g1.data.repository.SimulatorControls
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Contenedor de dependencias manual (inyección por constructor sin Hilt).
 * Para conectar un robot real alcanza con reemplazar la implementación de [robotRepository].
 */
class AppContainer(context: Context) {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val database = AppDatabase.getInstance(context)
    private val simulator = SimulatedRobotRepository()

    val robotRepository: RobotRepository = simulator
    val simulatorControls: SimulatorControls = simulator
    val auditRepository: AuditRepository =
        RoomAuditRepository(database.missionHistoryDao(), database.eventLogDao())

    init {
        RobotEventRecorder(robotRepository, auditRepository, applicationScope).start()
    }
}

class MediBotApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
