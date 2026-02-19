package com.esiri.esiriplus.core.database.migration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class DatabaseMigrationsTest {

    @Test
    fun `ALL_MIGRATIONS is initially empty for version 1`() {
        assertEquals(0, DatabaseMigrations.ALL_MIGRATIONS.size)
    }

    @Test
    fun `MIGRATION_1_2 scaffold exists and has correct version range`() {
        val migration = DatabaseMigrations.MIGRATION_1_2
        assertNotNull(migration)
        assertEquals(1, migration.startVersion)
        assertEquals(2, migration.endVersion)
    }
}
