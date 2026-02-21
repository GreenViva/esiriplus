package com.esiri.esiriplus.core.database.dao

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.esiri.esiriplus.core.database.EsiriplusDatabase
import com.esiri.esiriplus.core.database.entity.ConsultationEntity
import com.esiri.esiriplus.core.database.entity.DoctorProfileEntity
import com.esiri.esiriplus.core.database.entity.DoctorRatingEntity
import com.esiri.esiriplus.core.database.entity.PatientSessionEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DoctorRatingDaoTest {

    private lateinit var database: EsiriplusDatabase
    private lateinit var dao: DoctorRatingDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            EsiriplusDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.doctorRatingDao()

        // Insert parent entities for FK chain
        runTest {
            database.patientSessionDao().insert(
                PatientSessionEntity(
                    sessionId = "session-1",
                    sessionTokenHash = "hash1",
                    createdAt = 1000L,
                    updatedAt = 1000L,
                ),
            )
            database.doctorProfileDao().insert(
                DoctorProfileEntity(
                    doctorId = "doc-1",
                    fullName = "Dr. Test",
                    email = "test@example.com",
                    phone = "+255700000000",
                    specialty = "GP",
                    languages = listOf("English"),
                    bio = "Test",
                    licenseNumber = "LIC-001",
                    yearsExperience = 5,
                    createdAt = 1000L,
                    updatedAt = 1000L,
                ),
            )
            database.consultationDao().insert(
                ConsultationEntity(
                    consultationId = "consult-1",
                    patientSessionId = "session-1",
                    doctorId = "doc-1",
                    status = "COMPLETED",
                    serviceType = "GP",
                    consultationFee = 5000,
                    requestExpiresAt = 2000L,
                    createdAt = 1000L,
                    updatedAt = 1000L,
                ),
            )
            database.consultationDao().insert(
                ConsultationEntity(
                    consultationId = "consult-2",
                    patientSessionId = "session-1",
                    doctorId = "doc-1",
                    status = "COMPLETED",
                    serviceType = "GP",
                    consultationFee = 5000,
                    requestExpiresAt = 2000L,
                    createdAt = 1000L,
                    updatedAt = 1000L,
                ),
            )
            database.consultationDao().insert(
                ConsultationEntity(
                    consultationId = "consult-3",
                    patientSessionId = "session-1",
                    doctorId = "doc-1",
                    status = "COMPLETED",
                    serviceType = "GP",
                    consultationFee = 5000,
                    requestExpiresAt = 2000L,
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

    private fun createRating(
        ratingId: String = "rating-1",
        doctorId: String = "doc-1",
        consultationId: String = "consult-1",
        patientSessionId: String = "session-1",
        rating: Int = 5,
        comment: String? = null,
        createdAt: Long = 1000L,
    ) = DoctorRatingEntity(
        ratingId = ratingId,
        doctorId = doctorId,
        consultationId = consultationId,
        patientSessionId = patientSessionId,
        rating = rating,
        comment = comment,
        createdAt = createdAt,
    )

    @Test
    fun `insert and retrieve by id`() = runTest {
        dao.insert(createRating())

        val result = dao.getById("rating-1")
        assertNotNull(result)
        assertEquals("rating-1", result!!.ratingId)
        assertEquals("doc-1", result.doctorId)
        assertEquals("consult-1", result.consultationId)
        assertEquals("session-1", result.patientSessionId)
        assertEquals(5, result.rating)
    }

    @Test
    fun `getById returns null for non-existent`() = runTest {
        assertNull(dao.getById("non-existent"))
    }

    @Test
    fun `getByDoctorId returns ratings ordered by createdAt DESC`() = runTest {
        dao.insert(createRating("rating-1", consultationId = "consult-1", createdAt = 1000L))
        dao.insert(createRating("rating-2", consultationId = "consult-2", createdAt = 2000L))

        dao.getByDoctorId("doc-1").test {
            val result = awaitItem()
            assertEquals(2, result.size)
            assertEquals("rating-2", result[0].ratingId)
            assertEquals("rating-1", result[1].ratingId)
            cancel()
        }
    }

    @Test
    fun `getAverageRating calculates correct average`() = runTest {
        dao.insert(createRating("rating-1", consultationId = "consult-1", rating = 4))
        dao.insert(createRating("rating-2", consultationId = "consult-2", rating = 5))
        dao.insert(createRating("rating-3", consultationId = "consult-3", rating = 3))

        dao.getAverageRating("doc-1").test {
            assertEquals(4.0, awaitItem(), 0.001)
            cancel()
        }
    }

    @Test
    fun `getAverageRating returns 0 when no ratings`() = runTest {
        dao.getAverageRating("doc-1").test {
            assertEquals(0.0, awaitItem(), 0.001)
            cancel()
        }
    }

    @Test
    fun `unique constraint on consultationId prevents duplicate ratings`() = runTest {
        dao.insert(createRating("rating-1", consultationId = "consult-1"))

        // REPLACE strategy: inserting with same consultationId but different ratingId replaces
        dao.insert(createRating("rating-2", consultationId = "consult-1", rating = 3))

        val result = dao.getById("rating-2")
        assertNotNull(result)
        assertEquals(3, result!!.rating)
        // Old rating should be gone due to UNIQUE index REPLACE
        assertNull(dao.getById("rating-1"))
    }

    @Test
    fun `comment nullable round-trip`() = runTest {
        dao.insert(createRating("rating-1", consultationId = "consult-1", comment = null))
        dao.insert(createRating("rating-2", consultationId = "consult-2", comment = "Great doctor!"))

        assertNull(dao.getById("rating-1")!!.comment)
        assertEquals("Great doctor!", dao.getById("rating-2")!!.comment)
    }

    @Test
    fun `getUnsyncedRatings returns only unsynced`() = runTest {
        dao.insert(createRating("rating-1", consultationId = "consult-1"))
        dao.insert(createRating("rating-2", consultationId = "consult-2"))
        dao.insert(createRating("rating-3", consultationId = "consult-3").copy(synced = true))

        val result = dao.getUnsyncedRatings()
        assertEquals(2, result.size)
    }

    @Test
    fun `markSynced sets synced to true`() = runTest {
        dao.insert(createRating("rating-1"))
        assertFalse(dao.getById("rating-1")!!.synced)

        dao.markSynced("rating-1")
        assertTrue(dao.getById("rating-1")!!.synced)
    }

    @Test
    fun `delete removes specific rating`() = runTest {
        val rating = createRating()
        dao.insert(rating)
        dao.delete(rating)

        assertNull(dao.getById("rating-1"))
    }

    @Test
    fun `clearAll removes all ratings`() = runTest {
        dao.insert(createRating("rating-1", consultationId = "consult-1"))
        dao.insert(createRating("rating-2", consultationId = "consult-2"))
        dao.clearAll()

        assertNull(dao.getById("rating-1"))
        assertNull(dao.getById("rating-2"))
    }

    @Test
    fun `cascade delete removes ratings when doctor deleted`() = runTest {
        dao.insert(createRating("rating-1"))

        database.doctorProfileDao().delete(
            DoctorProfileEntity(
                doctorId = "doc-1",
                fullName = "Dr. Test",
                email = "test@example.com",
                phone = "+255700000000",
                specialty = "GP",
                languages = listOf("English"),
                bio = "Test",
                licenseNumber = "LIC-001",
                yearsExperience = 5,
                createdAt = 1000L,
                updatedAt = 1000L,
            ),
        )

        assertNull(dao.getById("rating-1"))
    }
}
