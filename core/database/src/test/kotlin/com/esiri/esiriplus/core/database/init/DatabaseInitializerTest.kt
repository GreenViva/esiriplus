package com.esiri.esiriplus.core.database.init

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.esiri.esiriplus.core.database.EsiriplusDatabase
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DatabaseInitializerTest {

    private lateinit var database: EsiriplusDatabase
    private lateinit var versionChecker: DatabaseVersionChecker
    private lateinit var databaseLazy: dagger.Lazy<EsiriplusDatabase>
    private lateinit var openHelper: SupportSQLiteOpenHelper

    @Before
    fun setUp() {
        database = mockk()
        versionChecker = mockk()
        databaseLazy = dagger.Lazy { database }
        openHelper = mockk()
    }

    private fun stubSuccessfulOpen() {
        every { versionChecker.isDowngrade() } returns false
        val sqliteDb = mockk<SupportSQLiteDatabase>()
        every { database.openHelper } returns openHelper
        every { openHelper.writableDatabase } returns sqliteDb
    }

    @Test
    fun `initialize succeeds and emits Ready`() = runTest {
        stubSuccessfulOpen()

        val initializer = DatabaseInitializer(databaseLazy, versionChecker)
        val result = initializer.initialize()

        assertEquals(DatabaseInitState.Ready, result)
        assertEquals(DatabaseInitState.Ready, initializer.state.value)
    }

    @Test
    fun `initialize detects downgrade and emits Failed VersionDowngrade`() = runTest {
        every { versionChecker.isDowngrade() } returns true

        val initializer = DatabaseInitializer(databaseLazy, versionChecker)
        val result = initializer.initialize()

        assertTrue(result is DatabaseInitState.Failed)
        val failed = result as DatabaseInitState.Failed
        assertTrue(failed.error is DatabaseInitError.VersionDowngrade)
    }

    @Test
    fun `initialize emits MigrationFailed when Room throws IllegalStateException`() = runTest {
        every { versionChecker.isDowngrade() } returns false
        every { database.openHelper } returns openHelper
        every { openHelper.writableDatabase } throws IllegalStateException("Migration failed")

        val initializer = DatabaseInitializer(databaseLazy, versionChecker)
        val result = initializer.initialize()

        assertTrue(result is DatabaseInitState.Failed)
        val failed = result as DatabaseInitState.Failed
        assertTrue(failed.error is DatabaseInitError.MigrationFailed)
    }

    @Test
    fun `initialize emits EncryptionFailed when encryption exception is thrown`() = runTest {
        every { versionChecker.isDowngrade() } returns false
        every { database.openHelper } returns openHelper
        every { openHelper.writableDatabase } throws DatabaseEncryptionException("Encryption failed")

        val initializer = DatabaseInitializer(databaseLazy, versionChecker)
        val result = initializer.initialize()

        assertTrue(result is DatabaseInitState.Failed)
        val failed = result as DatabaseInitState.Failed
        assertTrue(failed.error is DatabaseInitError.EncryptionFailed)
    }

    @Test
    fun `initialize is idempotent after success`() = runTest {
        stubSuccessfulOpen()

        val initializer = DatabaseInitializer(databaseLazy, versionChecker)
        initializer.initialize()
        val secondResult = initializer.initialize()

        assertEquals(DatabaseInitState.Ready, secondResult)
    }
}
