package com.esiri.esiriplus.core.database.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.esiri.esiriplus.core.database.EsiriplusDatabase
import com.esiri.esiriplus.core.database.entity.DoctorCredentialsEntity
import com.esiri.esiriplus.core.database.entity.DoctorProfileEntity
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
class DoctorCredentialsDaoTest {

    private lateinit var database: EsiriplusDatabase
    private lateinit var doctorCredentialsDao: DoctorCredentialsDao
    private lateinit var doctorProfileDao: DoctorProfileDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            EsiriplusDatabase::class.java,
        ).allowMainThreadQueries().build()
        doctorCredentialsDao = database.doctorCredentialsDao()
        doctorProfileDao = database.doctorProfileDao()

        // Insert parent doctor profile to satisfy FK constraint
        kotlinx.coroutines.test.runTest {
            doctorProfileDao.insert(createParentProfile("doc-1"))
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun createParentProfile(doctorId: String) = DoctorProfileEntity(
        doctorId = doctorId,
        fullName = "Dr. Test",
        email = "test@example.com",
        phone = "+255700000000",
        specialty = "GP",
        languages = listOf("English"),
        bio = "Test doctor",
        licenseNumber = "LIC-001",
        yearsExperience = 5,
        createdAt = 1000L,
        updatedAt = 1000L,
    )

    private fun createCredential(
        credentialId: String = "cred-1",
        doctorId: String = "doc-1",
        documentUrl: String = "https://example.com/doc.pdf",
        documentType: String = "MEDICAL_LICENSE",
        verifiedAt: Long? = null,
    ) = DoctorCredentialsEntity(
        credentialId = credentialId,
        doctorId = doctorId,
        documentUrl = documentUrl,
        documentType = documentType,
        verifiedAt = verifiedAt,
    )

    @Test
    fun `insert and retrieve by doctorId`() = runTest {
        doctorCredentialsDao.insert(createCredential())

        doctorCredentialsDao.getByDoctorId("doc-1").test {
            val result = awaitItem()
            assertEquals(1, result.size)
            assertEquals("cred-1", result[0].credentialId)
            assertEquals("MEDICAL_LICENSE", result[0].documentType)
            cancel()
        }
    }

    @Test
    fun `getById returns credential`() = runTest {
        doctorCredentialsDao.insert(createCredential())

        val result = doctorCredentialsDao.getById("cred-1")
        assertNotNull(result)
        assertEquals("cred-1", result!!.credentialId)
    }

    @Test
    fun `insertAll batch inserts credentials`() = runTest {
        val credentials = listOf(
            createCredential("cred-1", documentType = "MEDICAL_LICENSE"),
            createCredential("cred-2", documentType = "BOARD_CERTIFICATION"),
            createCredential("cred-3", documentType = "DEGREE"),
        )
        doctorCredentialsDao.insertAll(credentials)

        doctorCredentialsDao.getByDoctorId("doc-1").test {
            assertEquals(3, awaitItem().size)
            cancel()
        }
    }

    @Test
    fun `delete removes credential by id`() = runTest {
        doctorCredentialsDao.insert(createCredential("cred-1"))
        doctorCredentialsDao.insert(createCredential("cred-2"))
        doctorCredentialsDao.delete("cred-1")

        assertNull(doctorCredentialsDao.getById("cred-1"))
        assertNotNull(doctorCredentialsDao.getById("cred-2"))
    }

    @Test
    fun `deleteByDoctorId removes all credentials for doctor`() = runTest {
        doctorCredentialsDao.insert(createCredential("cred-1"))
        doctorCredentialsDao.insert(createCredential("cred-2"))
        doctorCredentialsDao.deleteByDoctorId("doc-1")

        doctorCredentialsDao.getByDoctorId("doc-1").test {
            assertEquals(0, awaitItem().size)
            cancel()
        }
    }

    @Test
    fun `verifiedAt nullable round-trip`() = runTest {
        doctorCredentialsDao.insert(createCredential("cred-1", verifiedAt = null))
        doctorCredentialsDao.insert(createCredential("cred-2", verifiedAt = 5000L))

        val unverified = doctorCredentialsDao.getById("cred-1")
        assertNotNull(unverified)
        assertNull(unverified!!.verifiedAt)

        val verified = doctorCredentialsDao.getById("cred-2")
        assertNotNull(verified)
        assertEquals(5000L, verified!!.verifiedAt)
    }

    @Test
    fun `cascade delete removes credentials when parent deleted`() = runTest {
        doctorCredentialsDao.insert(createCredential("cred-1"))
        doctorCredentialsDao.insert(createCredential("cred-2"))

        // Delete parent profile — credentials should cascade
        doctorProfileDao.delete(createParentProfile("doc-1"))

        doctorCredentialsDao.getByDoctorId("doc-1").test {
            assertEquals(0, awaitItem().size)
            cancel()
        }
    }
}
