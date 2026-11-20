package com.medibot.g1.data.model

/**
 * Plantilla de misión (catálogo). Cada ejecución recibe un identificador propio
 * (runId) al iniciarse, de modo que el historial no se pisa entre ejecuciones.
 */
data class MissionConfig(
    val missionId: String,
    val name: String,
    val description: String,
    val priority: MissionPriority = MissionPriority.STANDARD,
    val origin: HospitalNode,
    val destination: HospitalNode,
    val route: List<HospitalNode>,
    val requiredTempRange: ClosedFloatingPointRange<Float> = 2.0f..6.0f,
    val maxVibrationG: Float = 0.30f,
    val requiresNfcHandoff: Boolean = true
)

enum class MissionPriority(val displayName: String) {
    URGENT("Urgente"),
    STANDARD("Estándar"),
    ROUTINE("Rutinaria")
}
