package com.esiri.esiriplus.core.database.migration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class DatabaseMigrationsTest {

    @Test
    fun `ALL_MIGRATIONS contains all migrations`() {
        assertEquals(3, DatabaseMigrations.ALL_MIGRATIONS.size)
        assertEquals(1, DatabaseMigrations.ALL_MIGRATIONS[0].startVersion)
        assertEquals(2, DatabaseMigrations.ALL_MIGRATIONS[0].endVersion)
        assertEquals(2, DatabaseMigrations.ALL_MIGRATIONS[1].startVersion)
        assertEquals(3, DatabaseMigrations.ALL_MIGRATIONS[1].endVersion)
        assertEquals(3, DatabaseMigrations.ALL_MIGRATIONS[2].startVersion)
        assertEquals(4, DatabaseMigrations.ALL_MIGRATIONS[2].endVersion)
    }

    @Test
    fun `MIGRATION_1_2 has correct version range`() {
        val migration = DatabaseMigrations.MIGRATION_1_2
        assertNotNull(migration)
        assertEquals(1, migration.startVersion)
        assertEquals(2, migration.endVersion)
    }

    @Test
    fun `MIGRATION_2_3 has correct version range`() {
        val migration = DatabaseMigrations.MIGRATION_2_3
        assertNotNull(migration)
        assertEquals(2, migration.startVersion)
        assertEquals(3, migration.endVersion)
    }

    @Test
    fun `MIGRATION_3_4 has correct version range`() {
        val migration = DatabaseMigrations.MIGRATION_3_4
        assertNotNull(migration)
        assertEquals(3, migration.startVersion)
        assertEquals(4, migration.endVersion)
    }
}
