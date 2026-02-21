package com.esiri.esiriplus.core.database.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.esiri.esiriplus.core.database.EsiriplusDatabase
import com.esiri.esiriplus.core.database.entity.ConsultationEntity
import com.esiri.esiriplus.core.database.entity.PatientSessionEntity
import com.esiri.esiriplus.core.database.entity.VideoCallEntity
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
class VideoCallDaoTest {

    private lateinit var database: EsiriplusDatabase
    private lateinit var dao: VideoCallDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            EsiriplusDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.videoCallDao()

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
            database.consultationDao().insert(
                ConsultationEntity(
                    consultationId = "consult-1",
                    patientSessionId = "session-1",
                    doctorId = "doc-1",
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
                    doctorId = "doc-1",
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

    private fun createVideoCall(
        callId: String = "call-1",
        consultationId: String = "consult-1",
        startedAt: Long = 1000L,
        endedAt: Long? = null,
        durationSeconds: Int = 300,
        callQuality: String = "GOOD",
        createdAt: Long = 1000L,
    ) = VideoCallEntity(
        callId = callId,
        consultationId = consultationId,
        startedAt = startedAt,
        endedAt = endedAt,
        durationSeconds = durationSeconds,
        callQuality = callQuality,
        createdAt = createdAt,
    )

    @Test
    fun `insert and retrieve by id`() = runTest {
        dao.insert(createVideoCall())

        val result = dao.getById("call-1")
        assertNotNull(result)
        assertEquals("call-1", result!!.callId)
        assertEquals("consult-1", result.consultationId)
        assertEquals(300, result.durationSeconds)
        assertEquals("GOOD", result.callQuality)
    }

    @Test
    fun `getById returns null for non-existent`() = runTest {
        assertNull(dao.getById("non-existent"))
    }

    @Test
    fun `getByConsultationId returns calls ordered by createdAt DESC`() = runTest {
        dao.insert(createVideoCall("call-1", createdAt = 1000L))
        dao.insert(createVideoCall("call-2", createdAt = 3000L))

        dao.getByConsultationId("consult-1").test {
            val result = awaitItem()
            assertEquals(2, result.size)
            assertEquals("call-2", result[0].callId)
            assertEquals("call-1", result[1].callId)
            cancel()
        }
    }

    @Test
    fun `endedAt nullable round-trip`() = runTest {
        dao.insert(createVideoCall("call-1", endedAt = null))
        dao.insert(createVideoCall("call-2", endedAt = 5000L))

        assertNull(dao.getById("call-1")!!.endedAt)
        assertEquals(5000L, dao.getById("call-2")!!.endedAt)
    }

    @Test
    fun `insertAll inserts multiple`() = runTest {
        dao.insertAll(
            listOf(
                createVideoCall("call-1"),
                createVideoCall("call-2"),
            ),
        )

        assertNotNull(dao.getById("call-1"))
        assertNotNull(dao.getById("call-2"))
    }

    @Test
    fun `delete removes specific video call`() = runTest {
        val call = createVideoCall()
        dao.insert(call)
        dao.delete(call)

        assertNull(dao.getById("call-1"))
    }

    @Test
    fun `clearAll removes all video calls`() = runTest {
        dao.insert(createVideoCall("call-1"))
        dao.insert(createVideoCall("call-2"))
        dao.clearAll()

        assertNull(dao.getById("call-1"))
        assertNull(dao.getById("call-2"))
    }

    @Test
    fun `cascade delete removes calls when consultation cleared`() = runTest {
        dao.insert(createVideoCall("call-1", consultationId = "consult-1"))
        assertNotNull(dao.getById("call-1"))

        // Deleting the parent session cascades to consultations, then to video_calls
        database.patientSessionDao().clearAll()

        assertNull(dao.getById("call-1"))
    }
}
