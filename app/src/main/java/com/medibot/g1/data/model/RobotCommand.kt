package com.medibot.g1.data.model

sealed interface RobotCommand {
    val commandName: String

    data class StartMission(val mission: MissionConfig) : RobotCommand {
        override val commandName = "START_MISSION"
    }

    data object PauseMission : RobotCommand {
        override val commandName = "PAUSE_MISSION"
    }

    data object ResumeMission : RobotCommand {
        override val commandName = "RESUME_MISSION"
    }

    data object CancelMission : RobotCommand {
        override val commandName = "CANCEL_MISSION"
    }

    data object ReturnToSafePoint : RobotCommand {
        override val commandName = "RETURN_TO_SAFE_POINT"
    }

    data object OperationalStop : RobotCommand {
        override val commandName = "OPERATIONAL_STOP"
    }

    data class AcknowledgeAlert(val alertId: String) : RobotCommand {
        override val commandName = "ACKNOWLEDGE_ALERT"
    }
}
