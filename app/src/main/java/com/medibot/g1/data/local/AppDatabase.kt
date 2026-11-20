package com.medibot.g1.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [MissionHistoryEntity::class, EventLogEntity::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun missionHistoryDao(): MissionHistoryDao
    abstract fun eventLogDao(): EventLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "medibot_database"
                )
                    // Sin fallbackToDestructiveMigration: un cambio de esquema exige una Migration
                    // explícita para no borrar en silencio la auditoría (ver app/schemas/).
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
