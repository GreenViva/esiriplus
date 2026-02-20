package com.esiri.esiriplus.core.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE sessions ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE patient_profiles ADD COLUMN sex TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE patient_profiles ADD COLUMN ageGroup TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE patient_profiles ADD COLUMN chronicConditions TEXT DEFAULT NULL")
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `patient_sessions` (
                    `sessionId` TEXT NOT NULL,
                    `sessionTokenHash` TEXT NOT NULL,
                    `ageGroup` TEXT DEFAULT NULL,
                    `sex` TEXT DEFAULT NULL,
                    `region` TEXT DEFAULT NULL,
                    `bloodType` TEXT DEFAULT NULL,
                    `allergies` TEXT NOT NULL DEFAULT '[]',
                    `chronicConditions` TEXT NOT NULL DEFAULT '[]',
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    `lastSynced` INTEGER DEFAULT NULL,
                    PRIMARY KEY(`sessionId`)
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_patient_sessions_sessionId` ON `patient_sessions` (`sessionId`)",
            )
        }
    }

    val ALL_MIGRATIONS: Array<Migration> = arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
    )
}
