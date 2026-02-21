package com.esiri.esiriplus.core.database.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.esiri.esiriplus.core.database.EsiriplusDatabase
import com.esiri.esiriplus.core.database.entity.ServiceAccessPaymentEntity
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
class ServiceAccessPaymentDaoTest {

    private lateinit var database: EsiriplusDatabase
    private lateinit var dao: ServiceAccessPaymentDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            EsiriplusDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.serviceAccessPaymentDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun createPayment(
        paymentId: String = "sap-1",
        serviceType: String = "CONSULTATION",
        status: String = "PENDING",
        createdAt: Long = 1000L,
    ) = ServiceAccessPaymentEntity(
        paymentId = paymentId,
        serviceType = serviceType,
        amount = 5000,
        status = status,
        createdAt = createdAt,
    )

    @Test
    fun `insert and retrieve by id`() = runTest {
        dao.insert(createPayment())

        val result = dao.getById("sap-1")
        assertNotNull(result)
        assertEquals("sap-1", result!!.paymentId)
        assertEquals("CONSULTATION", result.serviceType)
        assertEquals(5000, result.amount)
    }

    @Test
    fun `getAll returns all payments`() = runTest {
        dao.insert(createPayment("sap-1"))
        dao.insert(createPayment("sap-2"))
        dao.insert(createPayment("sap-3"))

        dao.getAll().test {
            assertEquals(3, awaitItem().size)
            cancel()
        }
    }

    @Test
    fun `getByServiceType returns matching payments`() = runTest {
        dao.insert(createPayment("sap-1", serviceType = "CONSULTATION"))
        dao.insert(createPayment("sap-2", serviceType = "LAB_TEST"))
        dao.insert(createPayment("sap-3", serviceType = "CONSULTATION"))

        dao.getByServiceType("CONSULTATION").test {
            assertEquals(2, awaitItem().size)
            cancel()
        }
    }

    @Test
    fun `getByStatus returns matching payments`() = runTest {
        dao.insert(createPayment("sap-1", status = "PENDING"))
        dao.insert(createPayment("sap-2", status = "COMPLETED"))
        dao.insert(createPayment("sap-3", status = "PENDING"))

        dao.getByStatus("PENDING").test {
            assertEquals(2, awaitItem().size)
            cancel()
        }
    }

    @Test
    fun `insertAll batch inserts payments`() = runTest {
        val payments = listOf(
            createPayment("sap-1"),
            createPayment("sap-2"),
        )
        dao.insertAll(payments)

        dao.getAll().test {
            assertEquals(2, awaitItem().size)
            cancel()
        }
    }

    @Test
    fun `delete removes specific payment`() = runTest {
        val payment = createPayment()
        dao.insert(payment)
        dao.delete(payment)

        assertNull(dao.getById("sap-1"))
    }

    @Test
    fun `clearAll removes all payments`() = runTest {
        dao.insert(createPayment("sap-1"))
        dao.insert(createPayment("sap-2"))
        dao.clearAll()

        assertNull(dao.getById("sap-1"))
        assertNull(dao.getById("sap-2"))
    }
}
