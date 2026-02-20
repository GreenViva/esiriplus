package com.esiri.esiriplus.core.database.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.esiri.esiriplus.core.database.EsiriplusDatabase
import com.esiri.esiriplus.core.database.entity.ConsultationEntity
import com.esiri.esiriplus.core.database.entity.MessageEntity
import com.esiri.esiriplus.core.database.entity.PatientSessionEntity
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
class MessageDaoTest {

    private lateinit var database: EsiriplusDatabase
    private lateinit var messageDao: MessageDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            EsiriplusDatabase::class.java,
        ).allowMainThreadQueries().build()
        messageDao = database.messageDao()

        // Insert parent records to satisfy FK constraints
        kotlinx.coroutines.test.runTest {
            database.patientSessionDao().insert(
                PatientSessionEntity(
                    sessionId = "session-1",
                    sessionTokenHash = "hash",
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
                    requestExpiresAt = 300_000L,
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
                    requestExpiresAt = 300_000L,
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

    private fun createMessage(
        messageId: String = "m1",
        consultationId: String = "consult-1",
        senderType: String = "PATIENT",
        senderId: String = "session-1",
        messageText: String = "Hello doctor",
        messageType: String = "TEXT",
        synced: Boolean = false,
        createdAt: Long = 1000L,
    ) = MessageEntity(
        messageId = messageId,
        consultationId = consultationId,
        senderType = senderType,
        senderId = senderId,
        messageText = messageText,
        messageType = messageType,
        synced = synced,
        createdAt = createdAt,
    )

    @Test
    fun `insert and retrieve by id`() = runTest {
        val message = createMessage()
        messageDao.insert(message)

        val result = messageDao.getById("m1")
        assertNotNull(result)
        assertEquals("m1", result!!.messageId)
        assertEquals("consult-1", result.consultationId)
        assertEquals("PATIENT", result.senderType)
        assertEquals("Hello doctor", result.messageText)
        assertEquals("TEXT", result.messageType)
        assertFalse(result.isRead)
        assertFalse(result.synced)
    }

    @Test
    fun `getById returns null when not found`() = runTest {
        assertNull(messageDao.getById("nonexistent"))
    }

    @Test
    fun `getByConsultationId returns messages ordered by createdAt ASC`() = runTest {
        messageDao.insert(createMessage("m-later", createdAt = 2000L))
        messageDao.insert(createMessage("m-earlier", createdAt = 1000L))

        messageDao.getByConsultationId("consult-1").test {
            val result = awaitItem()
            assertEquals(2, result.size)
            assertEquals("m-earlier", result[0].messageId)
            assertEquals("m-later", result[1].messageId)
            cancel()
        }
    }

    @Test
    fun `getByConsultationId only returns messages for given consultation`() = runTest {
        messageDao.insert(createMessage("m1", consultationId = "consult-1"))
        messageDao.insert(createMessage("m2", consultationId = "consult-2"))

        messageDao.getByConsultationId("consult-1").test {
            val result = awaitItem()
            assertEquals(1, result.size)
            assertEquals("m1", result[0].messageId)
            cancel()
        }
    }

    @Test
    fun `insertAll batch inserts messages`() = runTest {
        val messages = listOf(
            createMessage("m1", createdAt = 1000L),
            createMessage("m2", createdAt = 2000L),
            createMessage("m3", createdAt = 3000L),
        )
        messageDao.insertAll(messages)

        messageDao.getByConsultationId("consult-1").test {
            assertEquals(3, awaitItem().size)
            cancel()
        }
    }

    @Test
    fun `markAsRead sets isRead to true`() = runTest {
        messageDao.insert(createMessage("m1"))
        assertFalse(messageDao.getById("m1")!!.isRead)

        messageDao.markAsRead("m1")
        assertTrue(messageDao.getById("m1")!!.isRead)
    }

    @Test
    fun `markAsSynced sets synced to true`() = runTest {
        messageDao.insert(createMessage("m1"))
        assertFalse(messageDao.getById("m1")!!.synced)

        messageDao.markAsSynced("m1")
        assertTrue(messageDao.getById("m1")!!.synced)
    }

    @Test
    fun `getUnsyncedMessages returns only unsynced messages`() = runTest {
        messageDao.insert(createMessage("m1", synced = false))
        messageDao.insert(createMessage("m2", synced = true))
        messageDao.insert(createMessage("m3", synced = false))

        val unsynced = messageDao.getUnsyncedMessages()
        assertEquals(2, unsynced.size)
        assertTrue(unsynced.all { !it.synced })
    }

    @Test
    fun `getUnsyncedMessages returns empty when all synced`() = runTest {
        messageDao.insert(createMessage("m1", synced = true))
        messageDao.insert(createMessage("m2", synced = true))

        val unsynced = messageDao.getUnsyncedMessages()
        assertTrue(unsynced.isEmpty())
    }

    @Test
    fun `clearAll removes all messages`() = runTest {
        messageDao.insert(createMessage("m1"))
        messageDao.insert(createMessage("m2"))
        messageDao.clearAll()

        assertNull(messageDao.getById("m1"))
        assertNull(messageDao.getById("m2"))
    }

    @Test
    fun `flow emits updates when data changes`() = runTest {
        messageDao.getByConsultationId("consult-1").test {
            assertEquals(0, awaitItem().size)

            messageDao.insert(createMessage("m1"))
            assertEquals(1, awaitItem().size)

            messageDao.clearAll()
            assertEquals(0, awaitItem().size)

            cancel()
        }
    }
}
