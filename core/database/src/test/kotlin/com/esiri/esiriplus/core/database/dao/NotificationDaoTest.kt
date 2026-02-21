package com.esiri.esiriplus.core.database.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.esiri.esiriplus.core.database.EsiriplusDatabase
import com.esiri.esiriplus.core.database.entity.NotificationEntity
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
class NotificationDaoTest {

    private lateinit var database: EsiriplusDatabase
    private lateinit var dao: NotificationDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            EsiriplusDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.notificationDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun createNotification(
        notificationId: String = "notif-1",
        userId: String = "user-1",
        title: String = "Test Title",
        body: String = "Test Body",
        type: String = "MESSAGE_RECEIVED",
        data: String = "{}",
        readAt: Long? = null,
        createdAt: Long = 1000L,
    ) = NotificationEntity(
        notificationId = notificationId,
        userId = userId,
        title = title,
        body = body,
        type = type,
        data = data,
        readAt = readAt,
        createdAt = createdAt,
    )

    @Test
    fun `insert and retrieve by id`() = runTest {
        dao.insert(createNotification())

        val result = dao.getById("notif-1")
        assertNotNull(result)
        assertEquals("notif-1", result!!.notificationId)
        assertEquals("user-1", result.userId)
        assertEquals("Test Title", result.title)
        assertEquals("MESSAGE_RECEIVED", result.type)
    }

    @Test
    fun `getById returns null for non-existent`() = runTest {
        assertNull(dao.getById("non-existent"))
    }

    @Test
    fun `getForUser returns notifications ordered by createdAt DESC`() = runTest {
        dao.insert(createNotification("notif-1", createdAt = 1000L))
        dao.insert(createNotification("notif-2", createdAt = 3000L))
        dao.insert(createNotification("notif-3", createdAt = 2000L))

        dao.getForUser("user-1").test {
            val result = awaitItem()
            assertEquals(3, result.size)
            assertEquals("notif-2", result[0].notificationId)
            assertEquals("notif-3", result[1].notificationId)
            assertEquals("notif-1", result[2].notificationId)
            cancel()
        }
    }

    @Test
    fun `getUnreadNotifications returns only unread`() = runTest {
        dao.insert(createNotification("notif-1", readAt = null))
        dao.insert(createNotification("notif-2", readAt = 5000L))
        dao.insert(createNotification("notif-3", readAt = null))

        dao.getUnreadNotifications("user-1").test {
            val result = awaitItem()
            assertEquals(2, result.size)
            cancel()
        }
    }

    @Test
    fun `getUnreadCount returns correct count`() = runTest {
        dao.insert(createNotification("notif-1", readAt = null))
        dao.insert(createNotification("notif-2", readAt = 5000L))
        dao.insert(createNotification("notif-3", readAt = null))

        dao.getUnreadCount("user-1").test {
            assertEquals(2, awaitItem())
            cancel()
        }
    }

    @Test
    fun `markAsRead sets readAt timestamp`() = runTest {
        dao.insert(createNotification("notif-1"))
        assertNull(dao.getById("notif-1")!!.readAt)

        dao.markAsRead("notif-1", 9999L)
        assertEquals(9999L, dao.getById("notif-1")!!.readAt)
    }

    @Test
    fun `markAllAsRead marks all unread for user`() = runTest {
        dao.insert(createNotification("notif-1", readAt = null))
        dao.insert(createNotification("notif-2", readAt = null))
        dao.insert(createNotification("notif-3", userId = "user-2", readAt = null))

        dao.markAllAsRead("user-1", 9999L)

        assertEquals(9999L, dao.getById("notif-1")!!.readAt)
        assertEquals(9999L, dao.getById("notif-2")!!.readAt)
        assertNull(dao.getById("notif-3")!!.readAt)
    }

    @Test
    fun `deleteOld removes notifications before threshold`() = runTest {
        dao.insert(createNotification("notif-1", createdAt = 1000L))
        dao.insert(createNotification("notif-2", createdAt = 5000L))

        dao.deleteOld(3000L)

        assertNull(dao.getById("notif-1"))
        assertNotNull(dao.getById("notif-2"))
    }

    @Test
    fun `insertAll inserts multiple`() = runTest {
        dao.insertAll(
            listOf(
                createNotification("notif-1"),
                createNotification("notif-2"),
            ),
        )

        assertNotNull(dao.getById("notif-1"))
        assertNotNull(dao.getById("notif-2"))
    }

    @Test
    fun `insert with REPLACE updates existing`() = runTest {
        dao.insert(createNotification("notif-1", title = "Original"))
        dao.insert(createNotification("notif-1", title = "Updated"))

        assertEquals("Updated", dao.getById("notif-1")!!.title)
    }

    @Test
    fun `delete removes specific notification`() = runTest {
        val notification = createNotification()
        dao.insert(notification)
        dao.delete(notification)

        assertNull(dao.getById("notif-1"))
    }

    @Test
    fun `clearAll removes all notifications`() = runTest {
        dao.insert(createNotification("notif-1"))
        dao.insert(createNotification("notif-2"))
        dao.clearAll()

        assertNull(dao.getById("notif-1"))
        assertNull(dao.getById("notif-2"))
    }

    @Test
    fun `data field stores JSON string`() = runTest {
        val jsonData = """{"consultationId":"c-1","messageId":"m-1"}"""
        dao.insert(createNotification("notif-1", data = jsonData))

        assertEquals(jsonData, dao.getById("notif-1")!!.data)
    }
}
