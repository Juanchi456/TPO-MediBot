package com.medibot.g1.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [MissionHistoryEntity::class, EventLogEntity::class],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun missionHistoryDao(): MissionHistoryDao
    abstract fun eventLogDao(): EventLogDao

    companion object {
        /** v2: el registro de eventos guarda qué operador originó cada evento. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE event_log ADD COLUMN operatorId TEXT")
            }
        }

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
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
