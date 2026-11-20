package com.medibot.g1.data.repository

import com.medibot.g1.data.local.EventLogDao
import com.medibot.g1.data.local.EventLogEntity
import com.medibot.g1.data.local.MissionHistoryDao
import com.medibot.g1.data.local.MissionHistoryEntity
import com.medibot.g1.data.model.AlertSeverity
import com.medibot.g1.data.model.EventCategory
import com.medibot.g1.data.model.EventRecord
import com.medibot.g1.data.model.HospitalNode
import com.medibot.g1.data.model.MissionOutcome
import com.medibot.g1.data.model.MissionPriority
import com.medibot.g1.data.model.MissionRecord
import com.medibot.g1.data.model.MissionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Historial de misiones y registro de auditoría. La app no conoce Room, solo esta interfaz. */
interface AuditRepository {
    val missions: Flow<List<MissionRecord>>
    val events: Flow<List<EventRecord>>

    suspend fun logEvent(
        category: EventCategory,
        severity: AlertSeverity,
        title: String,
        details: String,
        relatedMissionId: String? = null,
        operatorId: String? = null
    )

    suspend fun saveMission(outcome: MissionOutcome)

    suspend fun insertSampleData()

    suspend fun clearAll()
}

class RoomAuditRepository(
    private val missionDao: MissionHistoryDao,
    private val eventDao: EventLogDao,
    private val clock: () -> Long = System::currentTimeMillis
) : AuditRepository {

    override val missions: Flow<List<MissionRecord>> =
        missionDao.getAllMissions().map { list -> list.map { it.toRecord() } }

    override val events: Flow<List<EventRecord>> =
        eventDao.getAllEvents().map { list -> list.map { it.toRecord() } }

    override suspend fun logEvent(
        category: EventCategory,
        severity: AlertSeverity,
        title: String,
        details: String,
        relatedMissionId: String?,
        operatorId: String?
    ) {
        eventDao.insertEvent(
            EventLogEntity(
                timestamp = clock(),
                category = category.name,
                severity = severity.name,
                title = title,
                details = details,
                relatedMissionId = relatedMissionId,
                operatorId = operatorId
            )
        )
    }

    override suspend fun saveMission(outcome: MissionOutcome) {
        missionDao.insertMission(
            MissionHistoryEntity(
                missionId = outcome.runId,
                missionName = outcome.mission.name,
                description = outcome.mission.description,
                priority = outcome.mission.priority.name,
                originNode = outcome.mission.origin.displayName,
                destinationNode = outcome.mission.destination.displayName,
                status = outcome.status.name,
                startTime = outcome.startedAt,
                endTime = outcome.endedAt,
                finalBatteryPct = outcome.finalBatteryPct,
                maxTempCelsius = outcome.maxTempCelsius,
                maxVibrationG = outcome.maxVibrationG,
                operatorNotes = outcome.notes
            )
        )
    }

    override suspend fun insertSampleData() {
        val now = clock()
        listOf(
            MissionHistoryEntity(
                missionId = "SEED01",
                missionName = "Farmacia: Antibióticos → Piso 2",
                description = "Traslado urgente de vancomicina y meropenem.",
                priority = MissionPriority.URGENT.name,
                originNode = HospitalNode.DOCK_BASE.displayName,
                destinationNode = HospitalNode.SAFE_POINT_P2.displayName,
                status = MissionStatus.COMPLETED.name,
                startTime = now - 3_600_000,
                endTime = now - 3_000_000,
                finalBatteryPct = 91,
                maxTempCelsius = 4.2f,
                maxVibrationG = 0.18f,
                operatorNotes = "Completado sin anomalías de transporte."
            ),
            MissionHistoryEntity(
                missionId = "SEED02",
                missionName = "Hemoterapia: Plasma Fresco",
                description = "2 Unidades de PFC a Terapia Intensiva.",
                priority = MissionPriority.URGENT.name,
                originNode = HospitalNode.LAB_CENTRAL.displayName,
                destinationNode = HospitalNode.UTI_NURSING.displayName,
                status = MissionStatus.CANCELLED.name,
                startTime = now - 7_200_000,
                endTime = now - 6_800_000,
                finalBatteryPct = 78,
                maxTempCelsius = 5.8f,
                maxVibrationG = 0.22f,
                operatorNotes = "Cancelada por cambio de prioridad médica en UTI."
            )
        ).forEach { missionDao.insertMission(it) }

        listOf(
            EventLogEntity(
                timestamp = now - 3_600_000,
                category = EventCategory.MISSION.name,
                severity = AlertSeverity.INFO.name,
                title = "Misión SEED01 iniciada",
                details = "Traslado de farmacia hacia Piso 2 en ejecución."
            ),
            EventLogEntity(
                timestamp = now - 3_000_000,
                category = EventCategory.MISSION.name,
                severity = AlertSeverity.INFO.name,
                title = "Misión SEED01 completada",
                details = "Custodia validada por enfermería."
            ),
            EventLogEntity(
                timestamp = now - 1_800_000,
                category = EventCategory.ALERT.name,
                severity = AlertSeverity.WARNING.name,
                title = "Tránsito peatonal denso",
                details = "Velocidad reducida temporalmente a 0.3 m/s en pasillo central."
            )
        ).forEach { eventDao.insertEvent(it) }
    }

    override suspend fun clearAll() {
        missionDao.clearAll()
        eventDao.clearAll()
    }
}

private inline fun <reified T : Enum<T>> enumOrDefault(name: String, default: T): T =
    runCatching { enumValueOf<T>(name) }.getOrDefault(default)

private fun MissionHistoryEntity.toRecord() = MissionRecord(
    runId = missionId,
    missionName = missionName,
    description = description,
    priority = enumOrDefault(priority, MissionPriority.STANDARD),
    originName = originNode,
    destinationName = destinationNode,
    status = enumOrDefault(status, MissionStatus.FAILED),
    startTime = startTime,
    endTime = endTime,
    finalBatteryPct = finalBatteryPct,
    maxTempCelsius = maxTempCelsius,
    maxVibrationG = maxVibrationG,
    notes = operatorNotes
)

private fun EventLogEntity.toRecord() = EventRecord(
    eventId = eventId,
    timestamp = timestamp,
    category = enumOrDefault(category, EventCategory.SYSTEM),
    severity = enumOrDefault(severity, AlertSeverity.INFO),
    title = title,
    details = details,
    relatedMissionId = relatedMissionId,
    operatorId = operatorId
)
