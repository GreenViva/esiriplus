package com.esiri.esiriplus.core.database.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.esiri.esiriplus.core.database.EsiriplusDatabase
import com.esiri.esiriplus.core.database.entity.SessionEntity
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
class SessionDaoTest {

    private lateinit var database: EsiriplusDatabase
    private lateinit var sessionDao: SessionDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            EsiriplusDatabase::class.java,
        ).allowMainThreadQueries().build()
        sessionDao = database.sessionDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `insert and retrieve current session`() = runTest {
        val session = SessionEntity(
            id = 0,
            accessToken = "access-token-123",
            refreshToken = "refresh-token-456",
            expiresAt = Instant.ofEpochMilli(1735689600000L),
            userId = "user-1",
        )
        sessionDao.insertSession(session)

        sessionDao.getCurrentSession().test {
            val result = awaitItem()
            assertNotNull(result)
            assertEquals("access-token-123", result!!.accessToken)
            assertEquals("user-1", result.userId)
            cancel()
        }
    }

    @Test
    fun `getCurrentSession returns null when no session`() = runTest {
        sessionDao.getCurrentSession().test {
            assertNull(awaitItem())
            cancel()
        }
    }

    @Test
    fun `clearSession removes session`() = runTest {
        sessionDao.insertSession(
            SessionEntity(0, "token", "refresh", Instant.now(), "user-1"),
        )
        sessionDao.clearSession()

        sessionDao.getCurrentSession().test {
            assertNull(awaitItem())
            cancel()
        }
    }

    @Test
    fun `insert replaces existing session`() = runTest {
        sessionDao.insertSession(
            SessionEntity(0, "old-token", "old-refresh", Instant.now(), "user-1"),
        )
        sessionDao.insertSession(
            SessionEntity(0, "new-token", "new-refresh", Instant.now(), "user-1"),
        )

        sessionDao.getCurrentSession().test {
            val result = awaitItem()
            assertEquals("new-token", result!!.accessToken)
            cancel()
        }
    }
}
