package com.esiri.esiriplus.core.database.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.esiri.esiriplus.core.database.EsiriplusDatabase
import com.esiri.esiriplus.core.database.entity.UserEntity
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
class UserDaoTest {

    private lateinit var database: EsiriplusDatabase
    private lateinit var userDao: UserDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            EsiriplusDatabase::class.java,
        ).allowMainThreadQueries().build()
        userDao = database.userDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `insert and retrieve user by id`() = runTest {
        val user = createTestUser("user-1")
        userDao.insertUser(user)

        userDao.getUserById("user-1").test {
            val result = awaitItem()
            assertNotNull(result)
            assertEquals("user-1", result!!.id)
            assertEquals("John Doe", result.fullName)
            cancel()
        }
    }

    @Test
    fun `getUserById returns null for non-existent user`() = runTest {
        userDao.getUserById("non-existent").test {
            assertNull(awaitItem())
            cancel()
        }
    }

    @Test
    fun `insert with conflict replaces existing user`() = runTest {
        userDao.insertUser(createTestUser("user-1"))
        userDao.insertUser(createTestUser("user-1").copy(fullName = "Jane Doe"))

        userDao.getUserById("user-1").test {
            val result = awaitItem()
            assertEquals("Jane Doe", result!!.fullName)
            cancel()
        }
    }

    @Test
    fun `deleteAll removes all users`() = runTest {
        userDao.insertUser(createTestUser("user-1"))
        userDao.insertUser(createTestUser("user-2"))
        userDao.deleteAll()

        userDao.getUserById("user-1").test {
            assertNull(awaitItem())
            cancel()
        }
    }

    private fun createTestUser(id: String) = UserEntity(
        id = id,
        fullName = "John Doe",
        phone = "+255700000000",
        email = "john@example.com",
        role = "PATIENT",
        isVerified = true,
    )
}
