package com.esiri.esiriplus.core.database.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.esiri.esiriplus.core.database.EsiriplusDatabase
import com.esiri.esiriplus.core.database.entity.ConsultationEntity
import com.esiri.esiriplus.core.database.entity.PatientSessionEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ConsultationDaoTest {

    private lateinit var database: EsiriplusDatabase
    private lateinit var consultationDao: ConsultationDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            EsiriplusDatabase::class.java,
        ).allowMainThreadQueries().build()
        consultationDao = database.consultationDao()

        // Insert parent patient session to satisfy FK constraint
        kotlinx.coroutines.test.runTest {
            database.patientSessionDao().insert(
                PatientSessionEntity(
                    sessionId = "session-1",
                    sessionTokenHash = "hash",
                    createdAt = 1000L,
                    updatedAt = 1000L,
                ),
            )
            database.patientSessionDao().insert(
                PatientSessionEntity(
                    sessionId = "session-2",
                    sessionTokenHash = "hash2",
                    createdAt = 1000L,
                    updatedAt = 1000L,
                ),
            )
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun createConsultation(
        consultationId: String = "c1",
        patientSessionId: String = "session-1",
        doctorId: String = "doctor-1",
        status: String = "PENDING",
        serviceType: String = "GP",
        createdAt: Long = 1000L,
    ) = ConsultationEntity(
        consultationId = consultationId,
        patientSessionId = patientSessionId,
        doctorId = doctorId,
        status = status,
        serviceType = serviceType,
        consultationFee = 5000,
        requestExpiresAt = createdAt + 300_000L,
        createdAt = createdAt,
        updatedAt = createdAt,
    )

    @Test
    fun `insert and retrieve by id`() = runTest {
        val consultation = createConsultation()
        consultationDao.insert(consultation)

        val result = consultationDao.getById("c1")
        assertNotNull(result)
        assertEquals("c1", result!!.consultationId)
        assertEquals("session-1", result.patientSessionId)
        assertEquals("doctor-1", result.doctorId)
        assertEquals("PENDING", result.status)
        assertEquals("GP", result.serviceType)
        assertEquals(5000, result.consultationFee)
        assertEquals(15, result.sessionDurationMinutes)
    }

    @Test
    fun `getById returns null when not found`() = runTest {
        assertNull(consultationDao.getById("nonexistent"))
    }

    @Test
    fun `getByPatientSessionId returns matching consultations`() = runTest {
        consultationDao.insert(createConsultation("c1", "session-1"))
        consultationDao.insert(createConsultation("c2", "session-1"))
        consultationDao.insert(createConsultation("c3", "session-2"))

        consultationDao.getByPatientSessionId("session-1").test {
            val result = awaitItem()
            assertEquals(2, result.size)
            cancel()
        }
    }

    @Test
    fun `getByPatientSessionId orders by createdAt DESC`() = runTest {
        consultationDao.insert(createConsultation("c-old", createdAt = 1000L))
        consultationDao.insert(createConsultation("c-new", createdAt = 2000L))

        consultationDao.getByPatientSessionId("session-1").test {
            val result = awaitItem()
            assertEquals("c-new", result[0].consultationId)
            assertEquals("c-old", result[1].consultationId)
            cancel()
        }
    }

    @Test
    fun `getByStatus returns matching consultations`() = runTest {
        consultationDao.insert(createConsultation("c1", status = "PENDING"))
        consultationDao.insert(createConsultation("c2", status = "ACTIVE"))
        consultationDao.insert(createConsultation("c3", status = "PENDING"))

        consultationDao.getByStatus("PENDING").test {
            val result = awaitItem()
            assertEquals(2, result.size)
            cancel()
        }
    }

    @Test
    fun `getActiveConsultation returns active consultation`() = runTest {
        consultationDao.insert(createConsultation("c1", status = "PENDING"))
        consultationDao.insert(createConsultation("c2", status = "ACTIVE"))

        consultationDao.getActiveConsultation().test {
            val result = awaitItem()
            assertNotNull(result)
            assertEquals("c2", result!!.consultationId)
            cancel()
        }
    }

    @Test
    fun `getActiveConsultation returns null when none active`() = runTest {
        consultationDao.insert(createConsultation("c1", status = "PENDING"))

        consultationDao.getActiveConsultation().test {
            assertNull(awaitItem())
            cancel()
        }
    }

    @Test
    fun `updateStatus changes consultation status`() = runTest {
        consultationDao.insert(createConsultation("c1", status = "PENDING"))
        consultationDao.updateStatus("c1", "ACTIVE", 2000L)

        val result = consultationDao.getById("c1")
        assertNotNull(result)
        assertEquals("ACTIVE", result!!.status)
        assertEquals(2000L, result.updatedAt)
    }

    @Test
    fun `insertAll batch inserts consultations`() = runTest {
        val consultations = listOf(
            createConsultation("c1"),
            createConsultation("c2"),
            createConsultation("c3"),
        )
        consultationDao.insertAll(consultations)

        consultationDao.getByPatientSessionId("session-1").test {
            assertEquals(3, awaitItem().size)
            cancel()
        }
    }

    @Test
    fun `clearAll removes all consultations`() = runTest {
        consultationDao.insert(createConsultation("c1"))
        consultationDao.insert(createConsultation("c2"))
        consultationDao.clearAll()

        assertNull(consultationDao.getById("c1"))
        assertNull(consultationDao.getById("c2"))
    }
}
