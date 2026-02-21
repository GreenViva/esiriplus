package com.esiri.esiriplus.core.database.init

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.File

class DatabaseVersionCheckerTest {

    private lateinit var context: Context
    private lateinit var dbFile: File

    @Before
    fun setUp() {
        context = mockk()
        dbFile = mockk()
    }

    @Test
    fun `getCurrentVersion returns null when database file does not exist`() {
        every { dbFile.exists() } returns false
        every { context.getDatabasePath("esiriplus.db") } returns dbFile

        val checker = DatabaseVersionChecker(context)
        val version = checker.getCurrentVersion()

        assertNull(version)
    }

    @Test
    fun `isDowngrade returns false when database file does not exist`() {
        every { dbFile.exists() } returns false
        every { context.getDatabasePath("esiriplus.db") } returns dbFile

        val checker = DatabaseVersionChecker(context)

        assertFalse(checker.isDowngrade())
    }

    @Test
    fun `isDowngrade returns false when getCurrentVersion returns null`() {
        // When the DB file doesn't exist, getCurrentVersion returns null
        // and isDowngrade should return false (fresh install scenario)
        every { dbFile.exists() } returns false
        every { context.getDatabasePath("esiriplus.db") } returns dbFile

        val checker = DatabaseVersionChecker(context)

        assertFalse(checker.isDowngrade())
    }
}
