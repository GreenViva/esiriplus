package com.esiri.esiriplus.core.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {

    val ALL_MIGRATIONS: Array<Migration> = arrayOf(
        // Add migrations here as the schema evolves:
        // MIGRATION_1_2,
    )

    /**
     * Example migration from version 1 to 2.
     * Ready to be filled in when the schema changes.
     *
     * Production apps MUST use incremental migrations.
     * Destructive migration is only acceptable in debug builds.
     */
    @Suppress("unused")
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Example: Add a new column
            // db.execSQL("ALTER TABLE users ADD COLUMN avatarUrl TEXT")
        }
    }
}
