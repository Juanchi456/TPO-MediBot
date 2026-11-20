package com.medibot.g1.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "event_log")
data class EventLogEntity(
    @PrimaryKey val eventId: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val category: String,
    val severity: String,
    val title: String,
    val details: String,
    val relatedMissionId: String? = null,
    /** Operador de la consola que originó el evento (null si lo originó el robot). */
    val operatorId: String? = null
)
