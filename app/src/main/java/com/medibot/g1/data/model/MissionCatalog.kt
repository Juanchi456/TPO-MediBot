package com.medibot.g1.data.model

/** Misiones preconfiguradas del hospital (fuera de la UI: son datos, no presentación). */
object MissionCatalog {

    private val labToUti = listOf(
        HospitalNode.LAB_CENTRAL,
        HospitalNode.FIRE_DOOR_PB,
        HospitalNode.ELEVATOR_BANK,
        HospitalNode.SAFE_POINT_P2,
        HospitalNode.UTI_NURSING
    )

    val bloodGas = MissionConfig(
        missionId = "MED-01-GAS",
        name = "Urgencia: Gases en Sangre → UTI",
        description = "Transporte prioritario de muestra arterial heparinizada desde el Laboratorio Central hasta el puesto de Terapia Intensiva.",
        priority = MissionPriority.URGENT,
        origin = HospitalNode.LAB_CENTRAL,
        destination = HospitalNode.UTI_NURSING,
        route = labToUti,
        requiredTempRange = 2.0f..6.0f,
        maxVibrationG = 0.30f
    )

    val plasma = MissionConfig(
        missionId = "MED-02-HEM",
        name = "Hemoterapia: 2 Unidades Plasma → Quirófano",
        description = "Traslado de 2 bolsas de plasma fresco congelado hacia quirófano/UTI en el Piso 2, manteniendo hermetismo y cadena de frío.",
        priority = MissionPriority.URGENT,
        origin = HospitalNode.LAB_CENTRAL,
        destination = HospitalNode.UTI_NURSING,
        route = labToUti,
        requiredTempRange = 2.0f..6.0f,
        maxVibrationG = 0.25f
    )

    // Medicación a temperatura ambiente: la cápsula se acondiciona a 15–25 °C al despachar.
    val pharmacy = MissionConfig(
        missionId = "MED-03-FAR",
        name = "Farmacia: Medicación Controlada → Piso 2",
        description = "Distribución programada de antibióticos y analgésicos desde Base de Carga hasta el Receso Seguro de Espera.",
        priority = MissionPriority.STANDARD,
        origin = HospitalNode.DOCK_BASE,
        destination = HospitalNode.SAFE_POINT_P2,
        route = listOf(
            HospitalNode.DOCK_BASE,
            HospitalNode.LAB_CENTRAL,
            HospitalNode.FIRE_DOOR_PB,
            HospitalNode.ELEVATOR_BANK,
            HospitalNode.SAFE_POINT_P2
        ),
        requiredTempRange = 15.0f..25.0f,
        maxVibrationG = 0.40f
    )

    val all: List<MissionConfig> = listOf(bloodGas, plasma, pharmacy)
}
