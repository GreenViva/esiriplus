package com.esiri.esiriplus.core.database.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.esiri.esiriplus.core.database.EsiriplusDatabase
import com.esiri.esiriplus.core.database.entity.AppConfigEntity
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
class AppConfigDaoTest {

    private lateinit var database: EsiriplusDatabase
    private lateinit var appConfigDao: AppConfigDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            EsiriplusDatabase::class.java,
        ).allowMainThreadQueries().build()
        appConfigDao = database.appConfigDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `insert and retrieve config by key`() = runTest {
        val config = AppConfigEntity("session_timeout", "30", "Timeout in minutes")
        appConfigDao.insertConfig(config)

        val result = appConfigDao.getConfig("session_timeout")
        assertNotNull(result)
        assertEquals("30", result!!.value)
    }

    @Test
    fun `getConfig returns null for non-existent key`() = runTest {
        assertNull(appConfigDao.getConfig("non_existent"))
    }

    @Test
    fun `getConfigValue returns value string`() = runTest {
        appConfigDao.insertConfig(AppConfigEntity("max_file_size", "10", null))
        assertEquals("10", appConfigDao.getConfigValue("max_file_size"))
    }

    @Test
    fun `getConfigValue returns null for non-existent key`() = runTest {
        assertNull(appConfigDao.getConfigValue("non_existent"))
    }

    @Test
    fun `getAllConfig returns all entries`() = runTest {
        appConfigDao.insertConfigs(
            listOf(
                AppConfigEntity("key1", "val1", null),
                AppConfigEntity("key2", "val2", null),
            ),
        )

        appConfigDao.getAllConfig().test {
            val result = awaitItem()
            assertEquals(2, result.size)
            cancel()
        }
    }

    @Test
    fun `insert with same key replaces value`() = runTest {
        appConfigDao.insertConfig(AppConfigEntity("timeout", "30", null))
        appConfigDao.insertConfig(AppConfigEntity("timeout", "60", null))

        assertEquals("60", appConfigDao.getConfigValue("timeout"))
    }

    @Test
    fun `count returns correct number`() = runTest {
        assertEquals(0, appConfigDao.count())
        appConfigDao.insertConfigs(
            listOf(
                AppConfigEntity("k1", "v1", null),
                AppConfigEntity("k2", "v2", null),
                AppConfigEntity("k3", "v3", null),
            ),
        )
        assertEquals(3, appConfigDao.count())
    }
}
