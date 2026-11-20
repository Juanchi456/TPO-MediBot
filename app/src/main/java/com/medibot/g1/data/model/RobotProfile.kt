package com.medibot.g1.data.model

/** Robot de la flota que la consola puede supervisar. */
data class RobotProfile(
    val robotId: String,
    val displayName: String,
    val model: String,
    val homeLocation: String,
    val isAvailable: Boolean,
    val statusNote: String
)

/**
 * Flota del hospital. Solo el MED01 tiene un gateway (simulado) disponible; los otros
 * se listan para que la vista de selección muestre estados reales de una flota.
 */
object FleetCatalog {
    val robots: List<RobotProfile> = listOf(
        RobotProfile(
            robotId = "G1-EDU-MED01",
            displayName = "MediBot 1",
            model = "Unitree G1 EDU",
            homeLocation = "Base de carga · Laboratorio Central (PB)",
            isAvailable = true,
            statusNote = "Disponible (robot simulado)"
        ),
        RobotProfile(
            robotId = "G1-EDU-MED02",
            displayName = "MediBot 2",
            model = "Unitree G1 EDU",
            homeLocation = "Taller técnico (PB)",
            isAvailable = false,
            statusNote = "En mantenimiento: recambio de manos Dex3-1"
        ),
        RobotProfile(
            robotId = "G1-EDU-MED03",
            displayName = "MediBot 3",
            model = "Unitree G1 EDU",
            homeLocation = "Bahía de espera (Piso 2)",
            isAvailable = false,
            statusNote = "Fuera de servicio: recalibración del LiDAR"
        )
    )

    fun find(robotId: String?): RobotProfile? = robots.firstOrNull { it.robotId == robotId }
}
