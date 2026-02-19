package com.esiri.esiriplus.core.database.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.esiri.esiriplus.core.database.EsiriplusDatabase
import com.esiri.esiriplus.core.database.entity.PaymentEntity
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
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `insert and retrieve payment by id`() = runTest {
        val payment = createPayment("pay-1", "consult-1")
        paymentDao.insertPayment(payment)

        val result = paymentDao.getPaymentById("pay-1")
        assertNotNull(result)
        assertEquals("pay-1", result!!.id)
        assertEquals(3000, result.amount)
        assertEquals("TZS", result.currency)
    }

    @Test
    fun `getPaymentById returns null for non-existent`() = runTest {
        assertNull(paymentDao.getPaymentById("non-existent"))
    }

    @Test
    fun `getPaymentsForConsultation returns correct payments`() = runTest {
        paymentDao.insertPayment(createPayment("pay-1", "consult-1"))
        paymentDao.insertPayment(createPayment("pay-2", "consult-1"))
        paymentDao.insertPayment(createPayment("pay-3", "consult-2"))

        paymentDao.getPaymentsForConsultation("consult-1").test {
            val result = awaitItem()
            assertEquals(2, result.size)
            cancel()
        }
    }

    @Test
    fun `insert with conflict replaces existing payment`() = runTest {
        paymentDao.insertPayment(createPayment("pay-1", "consult-1"))
        paymentDao.insertPayment(createPayment("pay-1", "consult-1").copy(status = "COMPLETED"))

        val result = paymentDao.getPaymentById("pay-1")
        assertEquals("COMPLETED", result!!.status)
    }

    private fun createPayment(id: String, consultationId: String) = PaymentEntity(
        id = id,
        consultationId = consultationId,
        amount = 3000,
        currency = "TZS",
        status = "PENDING",
        mpesaReceiptNumber = null,
        createdAt = Instant.now(),
    )
}
