package com.esiri.esiriplus.core.database.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.esiri.esiriplus.core.database.EsiriplusDatabase
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
class PatientSessionDaoTest {

    private lateinit var database: EsiriplusDatabase
    private lateinit var dao: PatientSessionDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            EsiriplusDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.patientSessionDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun createSession(
        sessionId: String = "session-1",
        tokenHash: String = "hash-abc",
        createdAt: Long = 1000L,
        updatedAt: Long = 1000L,
    ) = PatientSessionEntity(
        sessionId = sessionId,
        sessionTokenHash = tokenHash,
        ageGroup = "18-25",
        sex = "Male",
        region = null,
        bloodType = "O+",
        allergies = listOf("Penicillin"),
        chronicConditions = listOf("Asthma"),
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    @Test
    fun `insert and retrieve by id`() = runTest {
        val session = createSession()
        dao.insert(session)

        val result = dao.getById("session-1")
        assertNotNull(result)
        assertEquals("session-1", result!!.sessionId)
        assertEquals("hash-abc", result.sessionTokenHash)
        assertEquals("18-25", result.ageGroup)
        assertEquals("Male", result.sex)
        assertEquals("O+", result.bloodType)
        assertEquals(listOf("Penicillin"), result.allergies)
        assertEquals(listOf("Asthma"), result.chronicConditions)
    }

    @Test
    fun `getById returns null when not found`() = runTest {
        assertNull(dao.getById("nonexistent"))
    }

    @Test
    fun `clearAll removes all sessions`() = runTest {
        dao.insert(createSession(sessionId = "s1"))
        dao.insert(createSession(sessionId = "s2"))
        dao.clearAll()

        assertNull(dao.getById("s1"))
        assertNull(dao.getById("s2"))
    }

    @Test
    fun `update modifies existing session`() = runTest {
        dao.insert(createSession())

        val updated = createSession().copy(
            ageGroup = "26-35",
            allergies = listOf("Penicillin", "Dust"),
            updatedAt = 2000L,
        )
        dao.update(updated)

        val result = dao.getById("session-1")
        assertNotNull(result)
        assertEquals("26-35", result!!.ageGroup)
        assertEquals(listOf("Penicillin", "Dust"), result.allergies)
        assertEquals(2000L, result.updatedAt)
    }

    @Test
    fun `getSession flow emits most recent session`() = runTest {
        dao.insert(createSession(sessionId = "s-old", createdAt = 1000L))
        dao.insert(createSession(sessionId = "s-new", createdAt = 2000L))

        dao.getSession().test {
            val result = awaitItem()
            assertNotNull(result)
            assertEquals("s-new", result!!.sessionId)
            cancel()
        }
    }

    @Test
    fun `getSession flow emits null when empty`() = runTest {
        dao.getSession().test {
            assertNull(awaitItem())
            cancel()
        }
    }

    @Test
    fun `getSession flow emits updates when data changes`() = runTest {
        dao.getSession().test {
            // Initially empty
            assertNull(awaitItem())

            // Insert a session
            dao.insert(createSession())
            val result = awaitItem()
            assertNotNull(result)
            assertEquals("session-1", result!!.sessionId)

            // Clear all
            dao.clearAll()
            assertNull(awaitItem())

            cancel()
        }
    }
}
