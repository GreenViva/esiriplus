package com.esiri.esiriplus.core.database.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.esiri.esiriplus.core.database.EsiriplusDatabase
import com.esiri.esiriplus.core.database.entity.ServiceTierEntity
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
class ServiceTierDaoTest {

    private lateinit var database: EsiriplusDatabase
    private lateinit var serviceTierDao: ServiceTierDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            EsiriplusDatabase::class.java,
        ).allowMainThreadQueries().build()
        serviceTierDao = database.serviceTierDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `insert and retrieve service tiers`() = runTest {
        val tiers = createTestTiers()
        serviceTierDao.insertServiceTiers(tiers)

        serviceTierDao.getActiveServiceTiers().test {
            val result = awaitItem()
            assertEquals(3, result.size)
            cancel()
        }
    }

    @Test
    fun `getActiveServiceTiers returns only active tiers`() = runTest {
        val tiers = listOf(
            createTier("t1", "NURSE", 3000, isActive = true, sortOrder = 1),
            createTier("t2", "GP", 10000, isActive = false, sortOrder = 2),
            createTier("t3", "SPECIALIST", 15000, isActive = true, sortOrder = 3),
        )
        serviceTierDao.insertServiceTiers(tiers)

        serviceTierDao.getActiveServiceTiers().test {
            val result = awaitItem()
            assertEquals(2, result.size)
            assertEquals("NURSE", result[0].category)
            assertEquals("SPECIALIST", result[1].category)
            cancel()
        }
    }

    @Test
    fun `getActiveServiceTiers orders by sortOrder`() = runTest {
        val tiers = listOf(
            createTier("t3", "SPECIALIST", 15000, sortOrder = 3),
            createTier("t1", "NURSE", 3000, sortOrder = 1),
            createTier("t2", "GP", 10000, sortOrder = 2),
        )
        serviceTierDao.insertServiceTiers(tiers)

        serviceTierDao.getActiveServiceTiers().test {
            val result = awaitItem()
            assertEquals("NURSE", result[0].category)
            assertEquals("GP", result[1].category)
            assertEquals("SPECIALIST", result[2].category)
            cancel()
        }
    }

    @Test
    fun `getServiceTierById returns correct tier`() = runTest {
        serviceTierDao.insertServiceTiers(createTestTiers())
        val result = serviceTierDao.getServiceTierById("tier_nurse")
        assertNotNull(result)
        assertEquals("NURSE", result!!.category)
        assertEquals(3000, result.priceAmount)
    }

    @Test
    fun `getServiceTierById returns null for non-existent`() = runTest {
        assertNull(serviceTierDao.getServiceTierById("non-existent"))
    }

    @Test
    fun `getServiceTierByCategory returns correct tier`() = runTest {
        serviceTierDao.insertServiceTiers(createTestTiers())
        val result = serviceTierDao.getServiceTierByCategory("GP")
        assertNotNull(result)
        assertEquals(10000, result!!.priceAmount)
    }

    @Test
    fun `count returns correct number`() = runTest {
        assertEquals(0, serviceTierDao.count())
        serviceTierDao.insertServiceTiers(createTestTiers())
        assertEquals(3, serviceTierDao.count())
    }

    private fun createTestTiers() = listOf(
        createTier("tier_nurse", "NURSE", 3000, sortOrder = 1),
        createTier("tier_gp", "GP", 10000, sortOrder = 2),
        createTier("tier_specialist", "SPECIALIST", 15000, sortOrder = 3),
    )

    private fun createTier(
        id: String,
        category: String,
        price: Int,
        isActive: Boolean = true,
        sortOrder: Int = 0,
    ) = ServiceTierEntity(
        id = id,
        category = category,
        displayName = category,
        description = "Consultation with $category",
        priceAmount = price,
        currency = "TZS",
        isActive = isActive,
        sortOrder = sortOrder,
    )
}
