package com.esiri.esiriplus.core.database.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.esiri.esiriplus.core.database.EsiriplusDatabase
import com.esiri.esiriplus.core.database.entity.DoctorAvailabilityEntity
import com.esiri.esiriplus.core.database.entity.DoctorProfileEntity
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
class DoctorAvailabilityDaoTest {

    private lateinit var database: EsiriplusDatabase
    private lateinit var doctorAvailabilityDao: DoctorAvailabilityDao
    private lateinit var doctorProfileDao: DoctorProfileDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            EsiriplusDatabase::class.java,
        ).allowMainThreadQueries().build()
        doctorAvailabilityDao = database.doctorAvailabilityDao()
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

    private fun createAvailability(
        availabilityId: String = "avail-1",
        doctorId: String = "doc-1",
        isAvailable: Boolean = true,
        availabilitySchedule: String = """{"monday":"09:00-17:00"}""",
        lastUpdated: Long = 1000L,
    ) = DoctorAvailabilityEntity(
        availabilityId = availabilityId,
        doctorId = doctorId,
        isAvailable = isAvailable,
        availabilitySchedule = availabilitySchedule,
        lastUpdated = lastUpdated,
    )

    @Test
    fun `insert and retrieve by doctorId`() = runTest {
        doctorAvailabilityDao.insert(createAvailability())

        doctorAvailabilityDao.getByDoctorId("doc-1").test {
            val result = awaitItem()
            assertNotNull(result)
            assertEquals("avail-1", result!!.availabilityId)
            assertEquals("doc-1", result.doctorId)
            assertTrue(result.isAvailable)
            assertEquals("""{"monday":"09:00-17:00"}""", result.availabilitySchedule)
            cancel()
        }
    }

    @Test
    fun `getById returns availability`() = runTest {
        doctorAvailabilityDao.insert(createAvailability())

        val result = doctorAvailabilityDao.getById("avail-1")
        assertNotNull(result)
        assertEquals("avail-1", result!!.availabilityId)
    }

    @Test
    fun `updateAvailability changes availability status`() = runTest {
        doctorAvailabilityDao.insert(createAvailability(isAvailable = true))
        doctorAvailabilityDao.updateAvailability("doc-1", isAvailable = false, lastUpdated = 2000L)

        val result = doctorAvailabilityDao.getById("avail-1")
        assertNotNull(result)
        assertEquals(false, result!!.isAvailable)
        assertEquals(2000L, result.lastUpdated)
    }

    @Test
    fun `deleteByDoctorId removes availability`() = runTest {
        doctorAvailabilityDao.insert(createAvailability())
        doctorAvailabilityDao.deleteByDoctorId("doc-1")

        doctorAvailabilityDao.getByDoctorId("doc-1").test {
            assertNull(awaitItem())
            cancel()
        }
    }

    @Test
    fun `cascade delete removes availability when parent deleted`() = runTest {
        doctorAvailabilityDao.insert(createAvailability())

        // Delete parent profile — availability should cascade
        doctorProfileDao.delete(createParentProfile("doc-1"))

        doctorAvailabilityDao.getByDoctorId("doc-1").test {
            assertNull(awaitItem())
            cancel()
        }
    }
}
