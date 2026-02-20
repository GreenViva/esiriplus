package com.esiri.esiriplus.core.database.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.esiri.esiriplus.core.database.EsiriplusDatabase
import com.esiri.esiriplus.core.database.entity.DoctorProfileEntity
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
class DoctorProfileDaoTest {

    private lateinit var database: EsiriplusDatabase
    private lateinit var doctorProfileDao: DoctorProfileDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            EsiriplusDatabase::class.java,
        ).allowMainThreadQueries().build()
        doctorProfileDao = database.doctorProfileDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun createProfile(
        doctorId: String = "doc-1",
        fullName: String = "Dr. John Doe",
        email: String = "john@example.com",
        phone: String = "+255700000000",
        specialty: String = "GP",
        languages: List<String> = listOf("English", "Swahili"),
        bio: String = "Experienced doctor",
        licenseNumber: String = "LIC-001",
        yearsExperience: Int = 10,
        profilePhotoUrl: String? = null,
        averageRating: Double = 0.0,
        totalRatings: Int = 0,
        isVerified: Boolean = false,
        isAvailable: Boolean = false,
        createdAt: Long = 1000L,
        updatedAt: Long = 1000L,
    ) = DoctorProfileEntity(
        doctorId = doctorId,
        fullName = fullName,
        email = email,
        phone = phone,
        specialty = specialty,
        languages = languages,
        bio = bio,
        licenseNumber = licenseNumber,
        yearsExperience = yearsExperience,
        profilePhotoUrl = profilePhotoUrl,
        averageRating = averageRating,
        totalRatings = totalRatings,
        isVerified = isVerified,
        isAvailable = isAvailable,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    @Test
    fun `insert and retrieve by id`() = runTest {
        val profile = createProfile()
        doctorProfileDao.insert(profile)

        val result = doctorProfileDao.getById("doc-1")
        assertNotNull(result)
        assertEquals("doc-1", result!!.doctorId)
        assertEquals("Dr. John Doe", result.fullName)
        assertEquals("john@example.com", result.email)
        assertEquals("+255700000000", result.phone)
        assertEquals("GP", result.specialty)
        assertEquals("LIC-001", result.licenseNumber)
        assertEquals(10, result.yearsExperience)
    }

    @Test
    fun `getById returns null when not found`() = runTest {
        assertNull(doctorProfileDao.getById("nonexistent"))
    }

    @Test
    fun `getAll returns all profiles`() = runTest {
        doctorProfileDao.insert(createProfile("doc-1"))
        doctorProfileDao.insert(createProfile("doc-2", fullName = "Dr. Jane"))
        doctorProfileDao.insert(createProfile("doc-3", fullName = "Dr. Bob"))

        doctorProfileDao.getAll().test {
            assertEquals(3, awaitItem().size)
            cancel()
        }
    }

    @Test
    fun `getBySpecialty returns matching profiles`() = runTest {
        doctorProfileDao.insert(createProfile("doc-1", specialty = "GP"))
        doctorProfileDao.insert(createProfile("doc-2", specialty = "DERMATOLOGY"))
        doctorProfileDao.insert(createProfile("doc-3", specialty = "GP"))

        doctorProfileDao.getBySpecialty("GP").test {
            val result = awaitItem()
            assertEquals(2, result.size)
            assertTrue(result.all { it.specialty == "GP" })
            cancel()
        }
    }

    @Test
    fun `getAvailableDoctors returns only verified and available`() = runTest {
        doctorProfileDao.insert(createProfile("doc-1", isVerified = true, isAvailable = true))
        doctorProfileDao.insert(createProfile("doc-2", isVerified = true, isAvailable = false))
        doctorProfileDao.insert(createProfile("doc-3", isVerified = false, isAvailable = true))
        doctorProfileDao.insert(createProfile("doc-4", isVerified = false, isAvailable = false))

        doctorProfileDao.getAvailableDoctors().test {
            val result = awaitItem()
            assertEquals(1, result.size)
            assertEquals("doc-1", result[0].doctorId)
            cancel()
        }
    }

    @Test
    fun `getByRatingRange returns profiles at or above minRating`() = runTest {
        doctorProfileDao.insert(createProfile("doc-1", averageRating = 4.5))
        doctorProfileDao.insert(createProfile("doc-2", averageRating = 3.0))
        doctorProfileDao.insert(createProfile("doc-3", averageRating = 4.0))
        doctorProfileDao.insert(createProfile("doc-4", averageRating = 2.5))

        doctorProfileDao.getByRatingRange(4.0).test {
            val result = awaitItem()
            assertEquals(2, result.size)
            assertTrue(result.all { it.averageRating >= 4.0 })
            cancel()
        }
    }

    @Test
    fun `updateAvailability changes availability`() = runTest {
        doctorProfileDao.insert(createProfile("doc-1", isAvailable = false))
        doctorProfileDao.updateAvailability("doc-1", isAvailable = true, updatedAt = 2000L)

        val result = doctorProfileDao.getById("doc-1")
        assertNotNull(result)
        assertTrue(result!!.isAvailable)
        assertEquals(2000L, result.updatedAt)
    }

    @Test
    fun `updateRating changes rating and totalRatings`() = runTest {
        doctorProfileDao.insert(createProfile("doc-1"))
        doctorProfileDao.updateRating("doc-1", averageRating = 4.5, totalRatings = 10, updatedAt = 2000L)

        val result = doctorProfileDao.getById("doc-1")
        assertNotNull(result)
        assertEquals(4.5, result!!.averageRating, 0.001)
        assertEquals(10, result.totalRatings)
        assertEquals(2000L, result.updatedAt)
    }

    @Test
    fun `insertAll batch inserts profiles`() = runTest {
        val profiles = listOf(
            createProfile("doc-1"),
            createProfile("doc-2"),
            createProfile("doc-3"),
        )
        doctorProfileDao.insertAll(profiles)

        doctorProfileDao.getAll().test {
            assertEquals(3, awaitItem().size)
            cancel()
        }
    }

    @Test
    fun `insert with same id replaces existing`() = runTest {
        doctorProfileDao.insert(createProfile("doc-1", fullName = "Original"))
        doctorProfileDao.insert(createProfile("doc-1", fullName = "Updated"))

        val result = doctorProfileDao.getById("doc-1")
        assertNotNull(result)
        assertEquals("Updated", result!!.fullName)
    }

    @Test
    fun `delete removes profile`() = runTest {
        val profile = createProfile("doc-1")
        doctorProfileDao.insert(profile)
        doctorProfileDao.delete(profile)

        assertNull(doctorProfileDao.getById("doc-1"))
    }

    @Test
    fun `clearAll removes all profiles`() = runTest {
        doctorProfileDao.insert(createProfile("doc-1"))
        doctorProfileDao.insert(createProfile("doc-2"))
        doctorProfileDao.clearAll()

        doctorProfileDao.getAll().test {
            assertEquals(0, awaitItem().size)
            cancel()
        }
    }

    @Test
    fun `languages list round-trip`() = runTest {
        val languages = listOf("English", "Swahili", "French")
        doctorProfileDao.insert(createProfile("doc-1", languages = languages))

        val result = doctorProfileDao.getById("doc-1")
        assertNotNull(result)
        assertEquals(languages, result!!.languages)
    }

    @Test
    fun `default values are applied correctly`() = runTest {
        doctorProfileDao.insert(createProfile("doc-1"))

        val result = doctorProfileDao.getById("doc-1")
        assertNotNull(result)
        assertEquals(0.0, result!!.averageRating, 0.001)
        assertEquals(0, result.totalRatings)
        assertFalse(result.isVerified)
        assertFalse(result.isAvailable)
        assertNull(result.profilePhotoUrl)
    }
}
