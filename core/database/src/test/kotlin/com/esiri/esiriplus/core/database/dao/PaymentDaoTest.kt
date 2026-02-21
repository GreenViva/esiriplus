package com.esiri.esiriplus.core.database.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.esiri.esiriplus.core.database.EsiriplusDatabase
import com.esiri.esiriplus.core.database.entity.PatientSessionEntity
import com.esiri.esiriplus.core.database.entity.PaymentEntity
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
class PaymentDaoTest {

    private lateinit var database: EsiriplusDatabase
    private lateinit var paymentDao: PaymentDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            EsiriplusDatabase::class.java,
        ).allowMainThreadQueries().build()
        paymentDao = database.paymentDao()

        // Insert parent patient sessions to satisfy FK constraint
        runTest {
            database.patientSessionDao().insert(
                PatientSessionEntity(
                    sessionId = "session-1",
                    sessionTokenHash = "hash1",
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

    private fun createPayment(
        paymentId: String = "pay-1",
        patientSessionId: String = "session-1",
        status: String = "PENDING",
        createdAt: Long = 1000L,
    ) = PaymentEntity(
        paymentId = paymentId,
        patientSessionId = patientSessionId,
        amount = 3000,
        paymentMethod = "MPESA",
        transactionId = null,
        phoneNumber = "+255700000000",
        status = status,
        failureReason = null,
        createdAt = createdAt,
        updatedAt = createdAt,
    )

    @Test
    fun `insert and retrieve payment by id`() = runTest {
        val payment = createPayment()
        paymentDao.insert(payment)

        val result = paymentDao.getById("pay-1")
        assertNotNull(result)
        assertEquals("pay-1", result!!.paymentId)
        assertEquals("session-1", result.patientSessionId)
        assertEquals(3000, result.amount)
        assertEquals("MPESA", result.paymentMethod)
        assertEquals("+255700000000", result.phoneNumber)
    }

    @Test
    fun `getById returns null for non-existent`() = runTest {
        assertNull(paymentDao.getById("non-existent"))
    }

    @Test
    fun `getByPatientSessionId returns correct payments`() = runTest {
        paymentDao.insert(createPayment("pay-1", "session-1"))
        paymentDao.insert(createPayment("pay-2", "session-1"))
        paymentDao.insert(createPayment("pay-3", "session-2"))

        paymentDao.getByPatientSessionId("session-1").test {
            val result = awaitItem()
            assertEquals(2, result.size)
            cancel()
        }
    }

    @Test
    fun `getByPatientSessionId orders by createdAt DESC`() = runTest {
        paymentDao.insert(createPayment("pay-old", createdAt = 1000L))
        paymentDao.insert(createPayment("pay-new", createdAt = 2000L))

        paymentDao.getByPatientSessionId("session-1").test {
            val result = awaitItem()
            assertEquals("pay-new", result[0].paymentId)
            assertEquals("pay-old", result[1].paymentId)
            cancel()
        }
    }

    @Test
    fun `getByStatus returns matching payments`() = runTest {
        paymentDao.insert(createPayment("pay-1", status = "PENDING"))
        paymentDao.insert(createPayment("pay-2", status = "COMPLETED"))
        paymentDao.insert(createPayment("pay-3", status = "PENDING"))

        paymentDao.getByStatus("PENDING").test {
            val result = awaitItem()
            assertEquals(2, result.size)
            cancel()
        }
    }

    @Test
    fun `getTransactionHistory with pagination`() = runTest {
        paymentDao.insert(createPayment("pay-1", createdAt = 1000L))
        paymentDao.insert(createPayment("pay-2", createdAt = 2000L))
        paymentDao.insert(createPayment("pay-3", createdAt = 3000L))

        paymentDao.getTransactionHistory(limit = 2, offset = 0).test {
            val result = awaitItem()
            assertEquals(2, result.size)
            assertEquals("pay-3", result[0].paymentId)
            assertEquals("pay-2", result[1].paymentId)
            cancel()
        }

        paymentDao.getTransactionHistory(limit = 2, offset = 2).test {
            val result = awaitItem()
            assertEquals(1, result.size)
            assertEquals("pay-1", result[0].paymentId)
            cancel()
        }
    }

    @Test
    fun `updateStatus changes status and transactionId`() = runTest {
        paymentDao.insert(createPayment("pay-1", status = "PENDING"))
        paymentDao.updateStatus("pay-1", "COMPLETED", "txn-123", 2000L)

        val result = paymentDao.getById("pay-1")
        assertNotNull(result)
        assertEquals("COMPLETED", result!!.status)
        assertEquals("txn-123", result.transactionId)
        assertEquals(2000L, result.updatedAt)
    }

    @Test
    fun `getUnsyncedPayments returns only unsynced`() = runTest {
        paymentDao.insert(createPayment("pay-1"))
        paymentDao.insert(createPayment("pay-2"))
        paymentDao.insert(createPayment("pay-3").copy(synced = true))

        val result = paymentDao.getUnsyncedPayments()
        assertEquals(2, result.size)
    }

    @Test
    fun `markSynced sets synced to true`() = runTest {
        paymentDao.insert(createPayment("pay-1"))
        assertFalse(paymentDao.getById("pay-1")!!.synced)

        paymentDao.markSynced("pay-1")
        assertTrue(paymentDao.getById("pay-1")!!.synced)
    }

    @Test
    fun `insert with conflict replaces existing payment`() = runTest {
        paymentDao.insert(createPayment("pay-1", status = "PENDING"))
        paymentDao.insert(createPayment("pay-1", status = "COMPLETED"))

        val result = paymentDao.getById("pay-1")
        assertEquals("COMPLETED", result!!.status)
    }

    @Test
    fun `insertAll batch inserts payments`() = runTest {
        val payments = listOf(
            createPayment("pay-1"),
            createPayment("pay-2"),
            createPayment("pay-3"),
        )
        paymentDao.insertAll(payments)

        paymentDao.getByPatientSessionId("session-1").test {
            assertEquals(3, awaitItem().size)
            cancel()
        }
    }

    @Test
    fun `delete removes specific payment`() = runTest {
        val payment = createPayment("pay-1")
        paymentDao.insert(payment)
        paymentDao.delete(payment)

        assertNull(paymentDao.getById("pay-1"))
    }

    @Test
    fun `clearAll removes all payments`() = runTest {
        paymentDao.insert(createPayment("pay-1"))
        paymentDao.insert(createPayment("pay-2"))
        paymentDao.clearAll()

        assertNull(paymentDao.getById("pay-1"))
        assertNull(paymentDao.getById("pay-2"))
    }
}
