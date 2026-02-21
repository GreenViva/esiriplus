package com.esiri.esiriplus.core.database.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.esiri.esiriplus.core.database.EsiriplusDatabase
import com.esiri.esiriplus.core.database.entity.TypingIndicatorEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TypingIndicatorDaoTest {

    private lateinit var database: EsiriplusDatabase
    private lateinit var dao: TypingIndicatorDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            EsiriplusDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.typingIndicatorDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun createIndicator(
        consultationId: String = "consult-1",
        userId: String = "user-1",
        isTyping: Boolean = true,
        updatedAt: Long = 1000L,
    ) = TypingIndicatorEntity(
        consultationId = consultationId,
        userId = userId,
        isTyping = isTyping,
        updatedAt = updatedAt,
    )

    @Test
    fun `upsert and retrieve by consultationId`() = runTest {
        dao.upsert(createIndicator())

        dao.getByConsultationId("consult-1").test {
            val result = awaitItem()
            assertEquals(1, result.size)
            assertEquals("user-1", result[0].userId)
            assertTrue(result[0].isTyping)
            cancel()
        }
    }

    @Test
    fun `upsert replaces existing with same composite key`() = runTest {
        dao.upsert(createIndicator(isTyping = true, updatedAt = 1000L))
        dao.upsert(createIndicator(isTyping = false, updatedAt = 2000L))

        dao.getByConsultationId("consult-1").test {
            val result = awaitItem()
            assertEquals(1, result.size)
            assertFalse(result[0].isTyping)
            assertEquals(2000L, result[0].updatedAt)
            cancel()
        }
    }

    @Test
    fun `getByConsultationId returns multiple users`() = runTest {
        dao.upsert(createIndicator(userId = "user-1"))
        dao.upsert(createIndicator(userId = "user-2"))

        dao.getByConsultationId("consult-1").test {
            val result = awaitItem()
            assertEquals(2, result.size)
            cancel()
        }
    }

    @Test
    fun `getByConsultationId filters by consultationId`() = runTest {
        dao.upsert(createIndicator(consultationId = "consult-1"))
        dao.upsert(createIndicator(consultationId = "consult-2", userId = "user-2"))

        dao.getByConsultationId("consult-1").test {
            val result = awaitItem()
            assertEquals(1, result.size)
            assertEquals("user-1", result[0].userId)
            cancel()
        }
    }

    @Test
    fun `deleteOld removes indicators before threshold`() = runTest {
        dao.upsert(createIndicator("consult-1", "user-1", updatedAt = 1000L))
        dao.upsert(createIndicator("consult-1", "user-2", updatedAt = 5000L))

        dao.deleteOld(3000L)

        dao.getByConsultationId("consult-1").test {
            val result = awaitItem()
            assertEquals(1, result.size)
            assertEquals("user-2", result[0].userId)
            cancel()
        }
    }

    @Test
    fun `clearAll removes all indicators`() = runTest {
        dao.upsert(createIndicator("consult-1", "user-1"))
        dao.upsert(createIndicator("consult-2", "user-2"))
        dao.clearAll()

        dao.getByConsultationId("consult-1").test {
            assertEquals(0, awaitItem().size)
            cancel()
        }
        dao.getByConsultationId("consult-2").test {
            assertEquals(0, awaitItem().size)
            cancel()
        }
    }
}
