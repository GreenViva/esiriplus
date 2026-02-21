package com.esiri.esiriplus.core.database.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.esiri.esiriplus.core.database.EsiriplusDatabase
import com.esiri.esiriplus.core.database.entity.CallRechargePaymentEntity
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
class CallRechargePaymentDaoTest {

    private lateinit var database: EsiriplusDatabase
    private lateinit var dao: CallRechargePaymentDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            EsiriplusDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.callRechargePaymentDao()

        // Insert parent entities for FK chain: PatientSession → Consultation → CallRechargePayment
        runTest {
            database.patientSessionDao().insert(
                PatientSessionEntity(
                    sessionId = "session-1",
                    sessionTokenHash = "hash1",
                    createdAt = 1000L,
                    updatedAt = 1000L,
                ),
            )
            database.consultationDao().insert(
                ConsultationEntity(
                    consultationId = "consult-1",
                    patientSessionId = "session-1",
                    doctorId = "doctor-1",
                    status = "ACTIVE",
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
                    doctorId = "doctor-1",
                    status = "ACTIVE",
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

    private fun createPayment(
        paymentId: String = "crp-1",
        consultationId: String = "consult-1",
        status: String = "PENDING",
        createdAt: Long = 1000L,
    ) = CallRechargePaymentEntity(
        paymentId = paymentId,
        consultationId = consultationId,
        status = status,
        createdAt = createdAt,
    )

    @Test
    fun `insert and retrieve by id`() = runTest {
        dao.insert(createPayment())

        val result = dao.getById("crp-1")
        assertNotNull(result)
        assertEquals("crp-1", result!!.paymentId)
        assertEquals("consult-1", result.consultationId)
    }

    @Test
    fun `default values for amount and additionalMinutes`() = runTest {
        dao.insert(createPayment())

        val result = dao.getById("crp-1")!!
        assertEquals(2500, result.amount)
        assertEquals(3, result.additionalMinutes)
    }

    @Test
    fun `getByConsultationId returns matching payments`() = runTest {
        dao.insert(createPayment("crp-1", "consult-1"))
        dao.insert(createPayment("crp-2", "consult-1"))
        dao.insert(createPayment("crp-3", "consult-2"))

        dao.getByConsultationId("consult-1").test {
            assertEquals(2, awaitItem().size)
            cancel()
        }
    }

    @Test
    fun `getByStatus returns matching payments`() = runTest {
        dao.insert(createPayment("crp-1", status = "PENDING"))
        dao.insert(createPayment("crp-2", status = "COMPLETED"))
        dao.insert(createPayment("crp-3", status = "PENDING"))

        dao.getByStatus("PENDING").test {
            assertEquals(2, awaitItem().size)
            cancel()
        }
    }

    @Test
    fun `insertAll batch inserts payments`() = runTest {
        val payments = listOf(
            createPayment("crp-1"),
            createPayment("crp-2"),
        )
        dao.insertAll(payments)

        dao.getByConsultationId("consult-1").test {
            assertEquals(2, awaitItem().size)
            cancel()
        }
    }

    @Test
    fun `delete removes specific payment`() = runTest {
        val payment = createPayment()
        dao.insert(payment)
        dao.delete(payment)

        assertNull(dao.getById("crp-1"))
    }

    @Test
    fun `clearAll removes all payments`() = runTest {
        dao.insert(createPayment("crp-1"))
        dao.insert(createPayment("crp-2"))
        dao.clearAll()

        assertNull(dao.getById("crp-1"))
        assertNull(dao.getById("crp-2"))
    }
}
