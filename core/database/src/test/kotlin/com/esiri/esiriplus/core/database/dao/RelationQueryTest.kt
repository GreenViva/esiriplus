package com.esiri.esiriplus.core.database.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.esiri.esiriplus.core.database.EsiriplusDatabase
import com.esiri.esiriplus.core.database.entity.ConsultationEntity
import com.esiri.esiriplus.core.database.entity.DoctorCredentialsEntity
import com.esiri.esiriplus.core.database.entity.DoctorProfileEntity
import com.esiri.esiriplus.core.database.entity.MessageEntity
import com.esiri.esiriplus.core.database.entity.PatientSessionEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RelationQueryTest {

    private lateinit var database: EsiriplusDatabase
    private lateinit var consultationDao: ConsultationDao
    private lateinit var messageDao: MessageDao
    private lateinit var doctorProfileDao: DoctorProfileDao
    private lateinit var doctorCredentialsDao: DoctorCredentialsDao
    private lateinit var patientSessionDao: PatientSessionDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            EsiriplusDatabase::class.java,
        ).allowMainThreadQueries().build()
        consultationDao = database.consultationDao()
        messageDao = database.messageDao()
        doctorProfileDao = database.doctorProfileDao()
        doctorCredentialsDao = database.doctorCredentialsDao()
        patientSessionDao = database.patientSessionDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    // — Factory helpers —

    private fun createPatientSession(
        sessionId: String = "session-1",
        createdAt: Long = 1000L,
    ) = PatientSessionEntity(
        sessionId = sessionId,
        sessionTokenHash = "hash-$sessionId",
        createdAt = createdAt,
        updatedAt = createdAt,
    )

    private fun createDoctorProfile(
        doctorId: String = "doctor-1",
        fullName: String = "Dr. Smith",
        specialty: String = "General",
        averageRating: Double = 4.5,
    ) = DoctorProfileEntity(
        doctorId = doctorId,
        fullName = fullName,
        email = "$doctorId@test.com",
        phone = "+1234567890",
        specialty = specialty,
        languages = listOf("English"),
        bio = "Test bio",
        licenseNumber = "LIC-$doctorId",
        yearsExperience = 10,
        averageRating = averageRating,
        createdAt = 1000L,
        updatedAt = 1000L,
    )

    private fun createConsultation(
        consultationId: String = "c1",
        patientSessionId: String = "session-1",
        doctorId: String = "doctor-1",
        status: String = "PENDING",
        createdAt: Long = 1000L,
    ) = ConsultationEntity(
        consultationId = consultationId,
        patientSessionId = patientSessionId,
        doctorId = doctorId,
        status = status,
        serviceType = "GP",
        consultationFee = 5000,
        requestExpiresAt = createdAt + 300_000L,
        createdAt = createdAt,
        updatedAt = createdAt,
    )

    private fun createMessage(
        messageId: String = "m1",
        consultationId: String = "c1",
        text: String = "Hello",
        createdAt: Long = 1000L,
    ) = MessageEntity(
        messageId = messageId,
        consultationId = consultationId,
        senderType = "PATIENT",
        senderId = "sender-1",
        messageText = text,
        messageType = "TEXT",
        createdAt = createdAt,
    )

    private fun createCredential(
        credentialId: String = "cred-1",
        doctorId: String = "doctor-1",
    ) = DoctorCredentialsEntity(
        credentialId = credentialId,
        doctorId = doctorId,
        documentUrl = "https://example.com/$credentialId",
        documentType = "LICENSE",
    )

    // — ConsultationWithMessages tests —

    @Test
    fun `getConsultationWithMessages returns consultation with its messages`() = runTest {
        patientSessionDao.insert(createPatientSession())
        consultationDao.insert(createConsultation())
        messageDao.insert(createMessage("m1", "c1", "First"))
        messageDao.insert(createMessage("m2", "c1", "Second"))

        consultationDao.getConsultationWithMessages("c1").test {
            val result = awaitItem()
            assertNotNull(result)
            assertEquals("c1", result!!.consultation.consultationId)
            assertEquals(2, result.messages.size)
            cancel()
        }
    }

    @Test
    fun `getConsultationWithMessages returns empty messages list when none exist`() = runTest {
        patientSessionDao.insert(createPatientSession())
        consultationDao.insert(createConsultation())

        consultationDao.getConsultationWithMessages("c1").test {
            val result = awaitItem()
            assertNotNull(result)
            assertEquals(0, result!!.messages.size)
            cancel()
        }
    }

    @Test
    fun `getConsultationWithMessages returns null for nonexistent consultation`() = runTest {
        consultationDao.getConsultationWithMessages("nonexistent").test {
            assertNull(awaitItem())
            cancel()
        }
    }

    @Test
    fun `getConsultationWithMessages flow updates when message is added`() = runTest {
        patientSessionDao.insert(createPatientSession())
        consultationDao.insert(createConsultation())

        consultationDao.getConsultationWithMessages("c1").test {
            val initial = awaitItem()
            assertNotNull(initial)
            assertEquals(0, initial!!.messages.size)

            messageDao.insert(createMessage("m1", "c1", "New message"))

            val updated = awaitItem()
            assertNotNull(updated)
            assertEquals(1, updated!!.messages.size)
            cancel()
        }
    }

    // — ConsultationWithDoctor tests (via getPatientConsultations) —

    @Test
    fun `getPatientConsultations returns consultations with doctor`() = runTest {
        patientSessionDao.insert(createPatientSession())
        doctorProfileDao.insert(createDoctorProfile())
        consultationDao.insert(createConsultation())

        consultationDao.getPatientConsultations("session-1").test {
            val result = awaitItem()
            assertEquals(1, result.size)
            assertEquals("c1", result[0].consultation.consultationId)
            assertNotNull(result[0].doctor)
            assertEquals("Dr. Smith", result[0].doctor!!.fullName)
            cancel()
        }
    }

    @Test
    fun `getPatientConsultations returns null doctor when doctor profile missing`() = runTest {
        patientSessionDao.insert(createPatientSession())
        consultationDao.insert(createConsultation(doctorId = "nonexistent-doctor"))

        consultationDao.getPatientConsultations("session-1").test {
            val result = awaitItem()
            assertEquals(1, result.size)
            assertNull(result[0].doctor)
            cancel()
        }
    }

    @Test
    fun `getPatientConsultations returns empty list for session with no consultations`() = runTest {
        patientSessionDao.insert(createPatientSession())

        consultationDao.getPatientConsultations("session-1").test {
            val result = awaitItem()
            assertTrue(result.isEmpty())
            cancel()
        }
    }

    // — DoctorWithCredentials tests —

    @Test
    fun `getDoctorWithCredentials returns doctor with credentials`() = runTest {
        doctorProfileDao.insert(createDoctorProfile())
        doctorCredentialsDao.insert(createCredential("cred-1", "doctor-1"))
        doctorCredentialsDao.insert(createCredential("cred-2", "doctor-1"))

        doctorProfileDao.getDoctorWithCredentials("doctor-1").test {
            val result = awaitItem()
            assertNotNull(result)
            assertEquals("doctor-1", result!!.doctor.doctorId)
            assertEquals(2, result.credentials.size)
            cancel()
        }
    }

    @Test
    fun `getDoctorWithCredentials returns empty credentials when none exist`() = runTest {
        doctorProfileDao.insert(createDoctorProfile())

        doctorProfileDao.getDoctorWithCredentials("doctor-1").test {
            val result = awaitItem()
            assertNotNull(result)
            assertEquals(0, result!!.credentials.size)
            cancel()
        }
    }

    @Test
    fun `getDoctorWithCredentials returns null for nonexistent doctor`() = runTest {
        doctorProfileDao.getDoctorWithCredentials("nonexistent").test {
            assertNull(awaitItem())
            cancel()
        }
    }

    // — PatientWithConsultations tests —

    @Test
    fun `getPatientWithConsultations returns session with consultations`() = runTest {
        patientSessionDao.insert(createPatientSession())
        consultationDao.insert(createConsultation("c1", "session-1"))
        consultationDao.insert(createConsultation("c2", "session-1"))

        patientSessionDao.getPatientWithConsultations("session-1").test {
            val result = awaitItem()
            assertNotNull(result)
            assertEquals("session-1", result!!.patientSession.sessionId)
            assertEquals(2, result.consultations.size)
            cancel()
        }
    }

    @Test
    fun `getPatientWithConsultations returns empty consultations when none exist`() = runTest {
        patientSessionDao.insert(createPatientSession())

        patientSessionDao.getPatientWithConsultations("session-1").test {
            val result = awaitItem()
            assertNotNull(result)
            assertEquals(0, result!!.consultations.size)
            cancel()
        }
    }

    // — ConsultationWithDoctorInfo JOIN tests —

    @Test
    fun `getConsultationsWithDoctorInfo returns joined data`() = runTest {
        patientSessionDao.insert(createPatientSession())
        doctorProfileDao.insert(createDoctorProfile("doctor-1", "Dr. Smith", "Cardiology", 4.8))
        consultationDao.insert(createConsultation("c1", "session-1", "doctor-1"))

        consultationDao.getConsultationsWithDoctorInfo("session-1").test {
            val result = awaitItem()
            assertEquals(1, result.size)
            val info = result[0]
            assertEquals("c1", info.consultationId)
            assertEquals("doctor-1", info.doctorId)
            assertEquals("Dr. Smith", info.fullName)
            assertEquals("Cardiology", info.specialty)
            assertEquals(4.8, info.averageRating, 0.01)
            cancel()
        }
    }

    @Test
    fun `getConsultationsWithDoctorInfo excludes consultations without matching doctor`() = runTest {
        patientSessionDao.insert(createPatientSession())
        doctorProfileDao.insert(createDoctorProfile("doctor-1"))
        consultationDao.insert(createConsultation("c1", "session-1", "doctor-1"))
        consultationDao.insert(createConsultation("c2", "session-1", "nonexistent-doctor"))

        consultationDao.getConsultationsWithDoctorInfo("session-1").test {
            val result = awaitItem()
            assertEquals(1, result.size)
            assertEquals("c1", result[0].consultationId)
            cancel()
        }
    }

    @Test
    fun `getConsultationsWithDoctorInfo orders by createdAt DESC`() = runTest {
        patientSessionDao.insert(createPatientSession())
        doctorProfileDao.insert(createDoctorProfile("doctor-1"))
        consultationDao.insert(createConsultation("c-old", "session-1", "doctor-1", createdAt = 1000L))
        consultationDao.insert(createConsultation("c-new", "session-1", "doctor-1", createdAt = 2000L))

        consultationDao.getConsultationsWithDoctorInfo("session-1").test {
            val result = awaitItem()
            assertEquals(2, result.size)
            assertEquals("c-new", result[0].consultationId)
            assertEquals("c-old", result[1].consultationId)
            cancel()
        }
    }

    // — FK Cascade Chain test —

    @Test
    fun `deleting patient session cascades to consultations and messages`() = runTest {
        val session = createPatientSession()
        patientSessionDao.insert(session)
        consultationDao.insert(createConsultation("c1", "session-1"))
        messageDao.insert(createMessage("m1", "c1"))
        messageDao.insert(createMessage("m2", "c1"))

        // Verify data exists
        assertNotNull(consultationDao.getById("c1"))
        assertNotNull(messageDao.getById("m1"))

        // Delete parent session — should cascade
        patientSessionDao.delete(session)

        // Consultation and messages should be gone
        assertNull(consultationDao.getById("c1"))
        assertNull(messageDao.getById("m1"))
        assertNull(messageDao.getById("m2"))
    }
}
