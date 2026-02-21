package com.esiri.esiriplus.core.database.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.esiri.esiriplus.core.database.EsiriplusDatabase
import com.esiri.esiriplus.core.database.entity.ConsultationEntity
import com.esiri.esiriplus.core.database.entity.DoctorEarningsEntity
import com.esiri.esiriplus.core.database.entity.DoctorProfileEntity
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
class DoctorEarningsDaoTest {

    private lateinit var database: EsiriplusDatabase
    private lateinit var dao: DoctorEarningsDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            EsiriplusDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.doctorEarningsDao()

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
                    createdAt = 2000L,
                    updatedAt = 2000L,
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
                    createdAt = 3000L,
                    updatedAt = 3000L,
                ),
            )
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun createEarning(
        earningId: String = "earn-1",
        doctorId: String = "doc-1",
        consultationId: String = "consult-1",
        amount: Int = 2500,
        status: String = "PENDING",
        paidAt: Long? = null,
        createdAt: Long = 1000L,
    ) = DoctorEarningsEntity(
        earningId = earningId,
        doctorId = doctorId,
        consultationId = consultationId,
        amount = amount,
        status = status,
        paidAt = paidAt,
        createdAt = createdAt,
    )

    @Test
    fun `insert and retrieve by id`() = runTest {
        dao.insert(createEarning())

        val result = dao.getById("earn-1")
        assertNotNull(result)
        assertEquals("earn-1", result!!.earningId)
        assertEquals("doc-1", result.doctorId)
        assertEquals("consult-1", result.consultationId)
        assertEquals(2500, result.amount)
        assertEquals("PENDING", result.status)
    }

    @Test
    fun `getById returns null for non-existent`() = runTest {
        assertNull(dao.getById("non-existent"))
    }

    @Test
    fun `getEarningsForDoctor returns all earnings ordered by createdAt DESC`() = runTest {
        dao.insert(createEarning("earn-1", consultationId = "consult-1", createdAt = 1000L))
        dao.insert(createEarning("earn-2", consultationId = "consult-2", createdAt = 2000L))
        dao.insert(createEarning("earn-3", consultationId = "consult-3", createdAt = 3000L))

        dao.getEarningsForDoctor("doc-1").test {
            val result = awaitItem()
            assertEquals(3, result.size)
            assertEquals("earn-3", result[0].earningId)
            assertEquals("earn-1", result[2].earningId)
            cancel()
        }
    }

    @Test
    fun `getEarningsByStatus filters by status`() = runTest {
        dao.insert(createEarning("earn-1", consultationId = "consult-1", status = "PENDING"))
        dao.insert(createEarning("earn-2", consultationId = "consult-2", status = "PAID"))
        dao.insert(createEarning("earn-3", consultationId = "consult-3", status = "PENDING"))

        dao.getEarningsByStatus("doc-1", "PENDING").test {
            assertEquals(2, awaitItem().size)
            cancel()
        }

        dao.getEarningsByStatus("doc-1", "PAID").test {
            assertEquals(1, awaitItem().size)
            cancel()
        }
    }

    @Test
    fun `getTotalEarnings sums amounts within date range`() = runTest {
        dao.insert(createEarning("earn-1", consultationId = "consult-1", amount = 2500, createdAt = 1000L))
        dao.insert(createEarning("earn-2", consultationId = "consult-2", amount = 3000, createdAt = 2000L))
        dao.insert(createEarning("earn-3", consultationId = "consult-3", amount = 1500, createdAt = 5000L))

        // Range includes first two
        dao.getTotalEarnings("doc-1", startDate = 0L, endDate = 3000L).test {
            assertEquals(5500, awaitItem())
            cancel()
        }

        // Range includes all
        dao.getTotalEarnings("doc-1", startDate = 0L, endDate = 10000L).test {
            assertEquals(7000, awaitItem())
            cancel()
        }
    }

    @Test
    fun `getTotalEarnings returns 0 when no earnings in range`() = runTest {
        dao.insert(createEarning("earn-1", createdAt = 5000L))

        dao.getTotalEarnings("doc-1", startDate = 0L, endDate = 1000L).test {
            assertEquals(0, awaitItem())
            cancel()
        }
    }

    @Test
    fun `paidAt nullable round-trip`() = runTest {
        dao.insert(createEarning("earn-1", consultationId = "consult-1", paidAt = null))
        dao.insert(createEarning("earn-2", consultationId = "consult-2", paidAt = 5000L))

        assertNull(dao.getById("earn-1")!!.paidAt)
        assertEquals(5000L, dao.getById("earn-2")!!.paidAt)
    }

    @Test
    fun `insertAll batch inserts earnings`() = runTest {
        val earnings = listOf(
            createEarning("earn-1", consultationId = "consult-1"),
            createEarning("earn-2", consultationId = "consult-2"),
        )
        dao.insertAll(earnings)

        dao.getEarningsForDoctor("doc-1").test {
            assertEquals(2, awaitItem().size)
            cancel()
        }
    }

    @Test
    fun `delete removes specific earning`() = runTest {
        val earning = createEarning()
        dao.insert(earning)
        dao.delete(earning)

        assertNull(dao.getById("earn-1"))
    }

    @Test
    fun `clearAll removes all earnings`() = runTest {
        dao.insert(createEarning("earn-1", consultationId = "consult-1"))
        dao.insert(createEarning("earn-2", consultationId = "consult-2"))
        dao.clearAll()

        assertNull(dao.getById("earn-1"))
        assertNull(dao.getById("earn-2"))
    }

    @Test
    fun `cascade delete removes earnings when doctor deleted`() = runTest {
        dao.insert(createEarning("earn-1"))

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

        assertNull(dao.getById("earn-1"))
    }
}
