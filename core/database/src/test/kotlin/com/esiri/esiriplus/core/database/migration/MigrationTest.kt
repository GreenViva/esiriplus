package com.esiri.esiriplus.core.database.migration

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class MigrationTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(TEST_DB)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(TEST_DB)
    }

    @Test
    fun `migrate 1 to 2 - adds createdAt to sessions with default 0`() {
        val db = createDatabaseFromSchema(1)

        // Insert a session in v1 schema
        db.execSQL(
            "INSERT INTO sessions (id, accessToken, refreshToken, expiresAt, userId) " +
                "VALUES (1, 'access1', 'refresh1', 1000, 'user1')",
        )

        // Run migration
        DatabaseMigrations.MIGRATION_1_2.migrate(db)

        // Verify data preserved with default createdAt = 0
        db.query("SELECT id, accessToken, userId, createdAt FROM sessions").use { cursor ->
            assertTrue("Session row should exist", cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
            assertEquals("access1", cursor.getString(1))
            assertEquals("user1", cursor.getString(2))
            assertEquals(0L, cursor.getLong(3))
        }
        db.close()
    }

    @Test
    fun `migrate 2 to 3 - adds nullable columns to patient_profiles`() {
        val db = createDatabaseFromSchema(2)

        // Insert user + patient_profile in v2 schema
        db.execSQL(
            "INSERT INTO users (id, fullName, phone, email, role, isVerified) " +
                "VALUES ('u1', 'Test User', '+255700000000', NULL, 'PATIENT', 1)",
        )
        db.execSQL(
            "INSERT INTO patient_profiles (id, userId, dateOfBirth, bloodGroup, allergies, " +
                "emergencyContactName, emergencyContactPhone) " +
                "VALUES ('pp1', 'u1', 946684800000, 'O+', NULL, NULL, NULL)",
        )

        // Run migration
        DatabaseMigrations.MIGRATION_2_3.migrate(db)

        // Verify old data preserved and new columns are null
        db.query("SELECT id, userId, sex, ageGroup, chronicConditions FROM patient_profiles").use { cursor ->
            assertTrue("Patient profile should exist", cursor.moveToFirst())
            assertEquals("pp1", cursor.getString(0))
            assertEquals("u1", cursor.getString(1))
            assertTrue("sex should be null", cursor.isNull(2))
            assertTrue("ageGroup should be null", cursor.isNull(3))
            assertTrue("chronicConditions should be null", cursor.isNull(4))
        }
        db.close()
    }

    @Test
    fun `migrate 3 to 4 - creates patient_sessions table`() {
        val db = createDatabaseFromSchema(3)

        // Run migration
        DatabaseMigrations.MIGRATION_3_4.migrate(db)

        // Verify patient_sessions table exists with correct schema by inserting a row
        db.execSQL(
            "INSERT INTO patient_sessions (sessionId, sessionTokenHash, createdAt, updatedAt) " +
                "VALUES ('ps1', 'hash1', 1000, 1000)",
        )
        db.query("SELECT sessionId, sessionTokenHash FROM patient_sessions").use { cursor ->
            assertTrue("Inserted row should exist", cursor.moveToFirst())
            assertEquals("ps1", cursor.getString(0))
            assertEquals("hash1", cursor.getString(1))
        }

        // Verify unique index exists
        assertTrue(
            "Unique index on sessionId should exist",
            hasIndex(db, "patient_sessions", "index_patient_sessions_sessionId"),
        )
        db.close()
    }

    @Test
    fun `migrate 4 to 5 - restructures consultations and dependent tables`() {
        val db = createDatabaseFromSchema(4)

        // Insert a user (users should survive)
        db.execSQL(
            "INSERT INTO users (id, fullName, phone, email, role, isVerified) " +
                "VALUES ('u1', 'Survivor', '+255700000000', NULL, 'PATIENT', 1)",
        )

        // Run migration
        DatabaseMigrations.MIGRATION_4_5.migrate(db)

        // Verify users survived
        db.query("SELECT id, fullName FROM users").use { cursor ->
            assertTrue("User should survive migration", cursor.moveToFirst())
            assertEquals("u1", cursor.getString(0))
            assertEquals("Survivor", cursor.getString(1))
        }

        // Verify recreated tables exist
        assertTrue("consultations should exist", tableExists(db, "consultations"))
        assertTrue("messages should exist", tableExists(db, "messages"))
        assertTrue("diagnoses should exist", tableExists(db, "diagnoses"))
        assertTrue("attachments should exist", tableExists(db, "attachments"))
        assertTrue("prescriptions should exist", tableExists(db, "prescriptions"))
        assertTrue("reviews should exist", tableExists(db, "reviews"))
        assertTrue("vital_signs should exist", tableExists(db, "vital_signs"))

        // Verify consultations index
        assertTrue(
            "consultations status+createdAt index should exist",
            hasIndex(db, "consultations", "index_consultations_status_createdAt"),
        )
        db.close()
    }

    @Test
    fun `migrate 5 to 6 - restructures doctor_profiles and adds availability and credentials`() {
        val db = createDatabaseFromSchema(5)

        // Insert a user (users should survive)
        db.execSQL(
            "INSERT INTO users (id, fullName, phone, email, role, isVerified) " +
                "VALUES ('u1', 'Survivor', '+255700000000', NULL, 'DOCTOR', 1)",
        )

        // Run migration
        DatabaseMigrations.MIGRATION_5_6.migrate(db)

        // Verify users survived
        db.query("SELECT id, fullName FROM users WHERE id = 'u1'").use { cursor ->
            assertTrue("User should survive migration", cursor.moveToFirst())
            assertEquals("Survivor", cursor.getString(1))
        }

        // Verify new tables exist
        assertTrue("doctor_profiles should exist", tableExists(db, "doctor_profiles"))
        assertTrue("doctor_availability should exist", tableExists(db, "doctor_availability"))
        assertTrue("doctor_credentials should exist", tableExists(db, "doctor_credentials"))

        // Verify doctor_profiles index
        assertTrue(
            "doctor_profiles composite index should exist",
            hasIndex(db, "doctor_profiles", "index_doctor_profiles_isVerified_isAvailable_specialty"),
        )
        db.close()
    }

    @Test
    fun `migrate 6 to 7 - restructures payments and adds subtypes`() {
        val db = createDatabaseFromSchema(6)

        // Insert a user (users should survive)
        db.execSQL(
            "INSERT INTO users (id, fullName, phone, email, role, isVerified) " +
                "VALUES ('u1', 'Survivor', '+255700000000', NULL, 'PATIENT', 1)",
        )

        // Run migration
        DatabaseMigrations.MIGRATION_6_7.migrate(db)

        // Verify users survived
        db.query("SELECT id FROM users WHERE id = 'u1'").use { cursor ->
            assertTrue("User should survive migration", cursor.moveToFirst())
        }

        // Verify new payment tables
        assertTrue("payments should exist", tableExists(db, "payments"))
        assertTrue("service_access_payments should exist", tableExists(db, "service_access_payments"))
        assertTrue("call_recharge_payments should exist", tableExists(db, "call_recharge_payments"))

        // Verify payments indexes
        assertTrue(
            "payments status index should exist",
            hasIndex(db, "payments", "index_payments_status"),
        )
        db.close()
    }

    @Test
    fun `migrate 7 to 8 - adds doctor_ratings and doctor_earnings`() {
        val db = createDatabaseFromSchema(7)

        // Run migration
        DatabaseMigrations.MIGRATION_7_8.migrate(db)

        assertTrue("doctor_ratings should exist", tableExists(db, "doctor_ratings"))
        assertTrue("doctor_earnings should exist", tableExists(db, "doctor_earnings"))

        // Verify unique index on doctor_ratings.consultationId
        assertTrue(
            "doctor_ratings unique consultationId index should exist",
            hasIndex(db, "doctor_ratings", "index_doctor_ratings_consultationId"),
        )
        db.close()
    }

    @Test
    fun `migrate 8 to 9 - adds notifications, video_calls, patient_reports, typing_indicators`() {
        val db = createDatabaseFromSchema(8)

        // Insert a user (users should survive)
        db.execSQL(
            "INSERT INTO users (id, fullName, phone, email, role, isVerified) " +
                "VALUES ('u1', 'Survivor', '+255700000000', NULL, 'PATIENT', 1)",
        )

        // Run migration
        DatabaseMigrations.MIGRATION_8_9.migrate(db)

        // Verify users survived
        db.query("SELECT id FROM users WHERE id = 'u1'").use { cursor ->
            assertTrue("User should survive migration", cursor.moveToFirst())
        }

        // Verify new/recreated tables
        assertTrue("notifications should exist", tableExists(db, "notifications"))
        assertTrue("video_calls should exist", tableExists(db, "video_calls"))
        assertTrue("patient_reports should exist", tableExists(db, "patient_reports"))
        assertTrue("typing_indicators should exist", tableExists(db, "typing_indicators"))
        db.close()
    }

    @Test
    fun `migrate full chain v1 to v9 - data survives and all 29 tables exist`() {
        val db = createDatabaseFromSchema(1)

        // Seed data in v1
        db.execSQL(
            "INSERT INTO users (id, fullName, phone, email, role, isVerified) " +
                "VALUES ('u1', 'Full Chain User', '+255700000000', 'user@test.com', 'PATIENT', 1)",
        )
        db.execSQL(
            "INSERT INTO sessions (id, accessToken, refreshToken, expiresAt, userId) " +
                "VALUES (1, 'access1', 'refresh1', 999999, 'u1')",
        )

        // Run ALL migrations
        for (migration in DatabaseMigrations.ALL_MIGRATIONS) {
            migration.migrate(db)
        }

        // Verify seeded data survived
        db.query("SELECT id, fullName, email FROM users WHERE id = 'u1'").use { cursor ->
            assertTrue("User should survive full migration chain", cursor.moveToFirst())
            assertEquals("Full Chain User", cursor.getString(1))
            assertEquals("user@test.com", cursor.getString(2))
        }
        db.query("SELECT id, userId, createdAt FROM sessions WHERE id = 1").use { cursor ->
            assertTrue("Session should survive full migration chain", cursor.moveToFirst())
            assertEquals("u1", cursor.getString(1))
            assertEquals(0L, cursor.getLong(2)) // default from v1->v2 migration
        }

        // Verify all 29 tables exist
        val expectedTables = listOf(
            "users", "sessions", "consultations", "payments",
            "service_tiers", "app_config",
            "doctor_profiles", "doctor_availability", "doctor_credentials",
            "patient_profiles", "patient_sessions",
            "messages", "attachments", "notifications",
            "prescriptions", "diagnoses", "vital_signs",
            "schedules", "reviews", "medical_records",
            "audit_logs", "providers",
            "service_access_payments", "call_recharge_payments",
            "doctor_ratings", "doctor_earnings",
            "video_calls", "patient_reports", "typing_indicators",
        )
        val actualTables = getTableNames(db)
        for (table in expectedTables) {
            assertTrue("Table '$table' should exist after full migration", table in actualTables)
        }
        assertEquals(29, expectedTables.size)
        db.close()
    }

    /**
     * Creates a SQLite database at the given schema version by reading the
     * Room-exported schema JSON and executing all CREATE TABLE / CREATE INDEX
     * statements from it.
     */
    private fun createDatabaseFromSchema(version: Int): SupportSQLiteDatabase {
        val schemaFile = File(
            SCHEMA_DIR,
            "$version.json",
        )
        require(schemaFile.exists()) {
            "Schema file not found: ${schemaFile.absolutePath}. " +
                "Ensure Room schema export is configured."
        }
        val schemaJson = JSONObject(schemaFile.readText())
        val database = schemaJson.getJSONObject("database")
        val entities = database.getJSONArray("entities")
        val setupQueries = database.getJSONArray("setupQueries")

        val factory = FrameworkSQLiteOpenHelperFactory()
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(TEST_DB)
            .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    for (i in 0 until entities.length()) {
                        val entity = entities.getJSONObject(i)
                        val tableName = entity.getString("tableName")
                        val createSql = entity.getString("createSql")
                            .replace("\${TABLE_NAME}", tableName)
                        db.execSQL(createSql)

                        if (entity.has("indices")) {
                            val indices = entity.getJSONArray("indices")
                            for (j in 0 until indices.length()) {
                                val indexSql = indices.getJSONObject(j)
                                    .getString("createSql")
                                    .replace("\${TABLE_NAME}", tableName)
                                db.execSQL(indexSql)
                            }
                        }
                    }
                    for (i in 0 until setupQueries.length()) {
                        db.execSQL(setupQueries.getString(i))
                    }
                }

                override fun onUpgrade(
                    db: SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int,
                ) {
                    // Not used in tests
                }
            })
            .build()

        return factory.create(config).writableDatabase
    }

    private fun tableExists(db: SupportSQLiteDatabase, tableName: String): Boolean {
        db.query(
            "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name=?",
            arrayOf(tableName),
        ).use { cursor ->
            return cursor.moveToFirst() && cursor.getInt(0) > 0
        }
    }

    private fun hasIndex(db: SupportSQLiteDatabase, tableName: String, indexName: String): Boolean {
        db.query(
            "SELECT COUNT(*) FROM sqlite_master WHERE type='index' AND tbl_name=? AND name=?",
            arrayOf(tableName, indexName),
        ).use { cursor ->
            return cursor.moveToFirst() && cursor.getInt(0) > 0
        }
    }

    private fun getTableNames(db: SupportSQLiteDatabase): Set<String> {
        val tables = mutableSetOf<String>()
        db.query(
            "SELECT name FROM sqlite_master WHERE type='table' " +
                "AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'room_%' AND name != 'android_metadata'",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                tables.add(cursor.getString(0))
            }
        }
        return tables
    }

    companion object {
        private const val TEST_DB = "migration-test"
        private const val SCHEMA_DIR =
            "schemas/com.esiri.esiriplus.core.database.EsiriplusDatabase"
    }
}
