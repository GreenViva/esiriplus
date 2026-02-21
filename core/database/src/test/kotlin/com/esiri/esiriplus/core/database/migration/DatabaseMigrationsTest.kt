package com.esiri.esiriplus.core.database.migration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class DatabaseMigrationsTest {

    @Test
    fun `ALL_MIGRATIONS contains all migrations`() {
        assertEquals(8, DatabaseMigrations.ALL_MIGRATIONS.size)
        assertEquals(1, DatabaseMigrations.ALL_MIGRATIONS[0].startVersion)
        assertEquals(2, DatabaseMigrations.ALL_MIGRATIONS[0].endVersion)
        assertEquals(2, DatabaseMigrations.ALL_MIGRATIONS[1].startVersion)
        assertEquals(3, DatabaseMigrations.ALL_MIGRATIONS[1].endVersion)
        assertEquals(3, DatabaseMigrations.ALL_MIGRATIONS[2].startVersion)
        assertEquals(4, DatabaseMigrations.ALL_MIGRATIONS[2].endVersion)
        assertEquals(4, DatabaseMigrations.ALL_MIGRATIONS[3].startVersion)
        assertEquals(5, DatabaseMigrations.ALL_MIGRATIONS[3].endVersion)
        assertEquals(5, DatabaseMigrations.ALL_MIGRATIONS[4].startVersion)
        assertEquals(6, DatabaseMigrations.ALL_MIGRATIONS[4].endVersion)
        assertEquals(6, DatabaseMigrations.ALL_MIGRATIONS[5].startVersion)
        assertEquals(7, DatabaseMigrations.ALL_MIGRATIONS[5].endVersion)
        assertEquals(7, DatabaseMigrations.ALL_MIGRATIONS[6].startVersion)
        assertEquals(8, DatabaseMigrations.ALL_MIGRATIONS[6].endVersion)
        assertEquals(8, DatabaseMigrations.ALL_MIGRATIONS[7].startVersion)
        assertEquals(9, DatabaseMigrations.ALL_MIGRATIONS[7].endVersion)
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

    @Test
    fun `MIGRATION_4_5 has correct version range`() {
        val migration = DatabaseMigrations.MIGRATION_4_5
        assertNotNull(migration)
        assertEquals(4, migration.startVersion)
        assertEquals(5, migration.endVersion)
    }

    @Test
    fun `MIGRATION_5_6 has correct version range`() {
        val migration = DatabaseMigrations.MIGRATION_5_6
        assertNotNull(migration)
        assertEquals(5, migration.startVersion)
        assertEquals(6, migration.endVersion)
    }

    @Test
    fun `MIGRATION_6_7 has correct version range`() {
        val migration = DatabaseMigrations.MIGRATION_6_7
        assertNotNull(migration)
        assertEquals(6, migration.startVersion)
        assertEquals(7, migration.endVersion)
    }

    @Test
    fun `MIGRATION_7_8 has correct version range`() {
        val migration = DatabaseMigrations.MIGRATION_7_8
        assertNotNull(migration)
        assertEquals(7, migration.startVersion)
        assertEquals(8, migration.endVersion)
    }

    @Test
    fun `MIGRATION_8_9 has correct version range`() {
        val migration = DatabaseMigrations.MIGRATION_8_9
        assertNotNull(migration)
        assertEquals(8, migration.startVersion)
        assertEquals(9, migration.endVersion)
    }

    @Test
    fun `migration chain is continuous - each endVersion equals next startVersion`() {
        val migrations = DatabaseMigrations.ALL_MIGRATIONS
        for (i in 0 until migrations.size - 1) {
            assertEquals(
                "Gap between migration ${migrations[i].startVersion}->${migrations[i].endVersion} " +
                    "and ${migrations[i + 1].startVersion}->${migrations[i + 1].endVersion}",
                migrations[i].endVersion,
                migrations[i + 1].startVersion,
            )
        }
    }

    @Test
    fun `migration chain covers v1 to v9 with no gaps`() {
        val migrations = DatabaseMigrations.ALL_MIGRATIONS
        assertEquals("Chain should start at version 1", 1, migrations.first().startVersion)
        assertEquals("Chain should end at version 9", 9, migrations.last().endVersion)
        val coveredVersions = migrations.map { it.startVersion }.toSet() +
            migrations.last().endVersion
        val expectedVersions = (1..9).toSet()
        assertEquals("All versions 1-9 should be covered", expectedVersions, coveredVersions)
    }

    @Test
    fun `migrations are sorted by startVersion in ascending order`() {
        val migrations = DatabaseMigrations.ALL_MIGRATIONS
        val startVersions = migrations.map { it.startVersion }
        assertEquals(
            "startVersions should be in ascending order",
            startVersions.sorted(),
            startVersions,
        )
    }
}
