package com.esiri.esiriplus.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EsiriplusDatabaseTest {

    private lateinit var database: EsiriplusDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            EsiriplusDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `database creates successfully`() {
        assertNotNull(database)
    }

    @Test
    fun `all 20 DAOs are accessible`() {
        assertNotNull(database.userDao())
        assertNotNull(database.sessionDao())
        assertNotNull(database.consultationDao())
        assertNotNull(database.paymentDao())
        assertNotNull(database.serviceTierDao())
        assertNotNull(database.appConfigDao())
        assertNotNull(database.doctorProfileDao())
        assertNotNull(database.patientProfileDao())
        assertNotNull(database.patientSessionDao())
        assertNotNull(database.messageDao())
        assertNotNull(database.attachmentDao())
        assertNotNull(database.notificationDao())
        assertNotNull(database.prescriptionDao())
        assertNotNull(database.diagnosisDao())
        assertNotNull(database.vitalSignDao())
        assertNotNull(database.scheduleDao())
        assertNotNull(database.reviewDao())
        assertNotNull(database.medicalRecordDao())
        assertNotNull(database.auditLogDao())
        assertNotNull(database.providerDao())
    }

    @Test
    fun `database has expected tables`() {
        val cursor = database.openHelper.readableDatabase.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'room_%' AND name != 'android_metadata'",
        )
        val tables = mutableListOf<String>()
        while (cursor.moveToNext()) {
            tables.add(cursor.getString(0))
        }
        cursor.close()
        assertEquals(EXPECTED_TABLE_COUNT, tables.size)
        assertNotNull(tables.find { it == "patient_sessions" })
    }

    companion object {
        private const val EXPECTED_TABLE_COUNT = 20
    }
}
