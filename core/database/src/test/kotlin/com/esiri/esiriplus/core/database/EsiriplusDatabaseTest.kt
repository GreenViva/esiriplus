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
    fun `all 19 DAOs are accessible`() {
        assertNotNull(database.userDao())
        assertNotNull(database.sessionDao())
        assertNotNull(database.consultationDao())
        assertNotNull(database.paymentDao())
        assertNotNull(database.serviceTierDao())
        assertNotNull(database.appConfigDao())
        assertNotNull(database.doctorProfileDao())
        assertNotNull(database.patientProfileDao())
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
    fun `database version is 1`() {
        val dbAnnotation = EsiriplusDatabase::class.java.getAnnotation(
            androidx.room.Database::class.java,
        )
        assertNotNull(dbAnnotation)
        assertEquals(1, dbAnnotation!!.version)
    }

    @Test
    fun `database exports schema`() {
        val dbAnnotation = EsiriplusDatabase::class.java.getAnnotation(
            androidx.room.Database::class.java,
        )
        assertNotNull(dbAnnotation)
        assertEquals(true, dbAnnotation!!.exportSchema)
    }

    @Test
    fun `database has 19 entities`() {
        val dbAnnotation = EsiriplusDatabase::class.java.getAnnotation(
            androidx.room.Database::class.java,
        )
        assertNotNull(dbAnnotation)
        assertEquals(EXPECTED_ENTITY_COUNT, dbAnnotation!!.entities.size)
    }

    companion object {
        private const val EXPECTED_ENTITY_COUNT = 19
    }
}
