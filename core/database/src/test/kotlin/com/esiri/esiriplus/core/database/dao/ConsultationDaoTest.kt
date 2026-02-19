package com.esiri.esiriplus.core.database.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.esiri.esiriplus.core.database.EsiriplusDatabase
import com.esiri.esiriplus.core.database.entity.ConsultationEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

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
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `insert and retrieve consultation by id`() = runTest {
        val consultation = createConsultation("c1", "patient-1")
        consultationDao.insertConsultation(consultation)

        val result = consultationDao.getConsultationById("c1")
        assertNotNull(result)
        assertEquals("c1", result!!.id)
        assertEquals("patient-1", result.patientId)
    }

    @Test
    fun `getConsultationById returns null for non-existent`() = runTest {
        assertNull(consultationDao.getConsultationById("non-existent"))
    }

    @Test
    fun `getConsultationsForPatient returns correct consultations`() = runTest {
        consultationDao.insertConsultation(createConsultation("c1", "patient-1"))
        consultationDao.insertConsultation(createConsultation("c2", "patient-1"))
        consultationDao.insertConsultation(createConsultation("c3", "patient-2"))

        consultationDao.getConsultationsForPatient("patient-1").test {
            val result = awaitItem()
            assertEquals(2, result.size)
            cancel()
        }
    }

    @Test
    fun `getConsultationsForDoctor returns correct consultations`() = runTest {
        consultationDao.insertConsultation(createConsultation("c1", "p1", "doctor-1"))
        consultationDao.insertConsultation(createConsultation("c2", "p2", "doctor-1"))
        consultationDao.insertConsultation(createConsultation("c3", "p3", "doctor-2"))

        consultationDao.getConsultationsForDoctor("doctor-1").test {
            val result = awaitItem()
            assertEquals(2, result.size)
            cancel()
        }
    }

    @Test
    fun `getConsultationsForPatient orders by createdAt DESC`() = runTest {
        val older = createConsultation("c1", "patient-1").copy(
            createdAt = Instant.ofEpochMilli(1000),
        )
        val newer = createConsultation("c2", "patient-1").copy(
            createdAt = Instant.ofEpochMilli(2000),
        )
        consultationDao.insertConsultation(older)
        consultationDao.insertConsultation(newer)

        consultationDao.getConsultationsForPatient("patient-1").test {
            val result = awaitItem()
            assertEquals("c2", result[0].id)
            assertEquals("c1", result[1].id)
            cancel()
        }
    }

    @Test
    fun `insertConsultations batch insert`() = runTest {
        val consultations = listOf(
            createConsultation("c1", "patient-1"),
            createConsultation("c2", "patient-1"),
            createConsultation("c3", "patient-1"),
        )
        consultationDao.insertConsultations(consultations)

        consultationDao.getConsultationsForPatient("patient-1").test {
            assertEquals(3, awaitItem().size)
            cancel()
        }
    }

    private fun createConsultation(
        id: String,
        patientId: String,
        doctorId: String? = "doctor-1",
    ) = ConsultationEntity(
        id = id,
        patientId = patientId,
        doctorId = doctorId,
        serviceType = "GENERAL_CONSULTATION",
        status = "PENDING",
        notes = null,
        createdAt = Instant.now(),
        updatedAt = null,
    )
}
