package com.medibot.g1.data.model

/** Rol de quien usa la consola. */
enum class ConsoleRole(val displayName: String, val description: String) {
    OPERATOR("Operador", "Supervisa y emite órdenes al robot."),
    SUPERVISOR("Supervisor", "Solo observa: no puede emitir órdenes.")
}

/** Sesión de consola: a qué robot está conectada, quién la usa y con qué rol. */
data class ConsoleSession(
    val robot: RobotProfile,
    val operatorId: String,
    val role: ConsoleRole
) {
    val canCommand: Boolean get() = role == ConsoleRole.OPERATOR
}
