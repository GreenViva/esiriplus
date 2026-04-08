package com.esiri.esiriplus.core.database.init

import android.content.Context
import android.util.Log
import com.esiri.esiriplus.core.database.EsiriplusDatabase
import com.esiri.esiriplus.core.database.encryption.DatabaseEncryption
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pre-Room downgrade detection by reading the on-disk database version directly via SQLCipher.
 * This runs before Room opens the database. When a downgrade is detected the database files
 * are deleted automatically so Room can recreate them — the user's data lives on Supabase,
 * so no real data is lost.
 */
@Singleton
class DatabaseVersionChecker @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * Reads the current on-disk database version using PRAGMA user_version.
     * Returns null if the database file doesn't exist (fresh install).
     */
    fun getCurrentVersion(): Int? {
        val dbFile = context.getDatabasePath(DATABASE_NAME)
        if (!dbFile.exists()) return null

        val passphrase = DatabaseEncryption.getPassphrase(context) ?: return null

        return try {
            val db = net.zetetic.database.sqlcipher.SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                passphrase.toByteArray(),
                null, // CursorFactory
                net.zetetic.database.sqlcipher.SQLiteDatabase.OPEN_READONLY,
                null, // SQLiteDatabaseHook
            )
            try {
                val cursor = db.rawQuery("PRAGMA user_version", null)
                cursor.use {
                    if (it.moveToFirst()) it.getInt(0) else null
                }
            } finally {
                db.close()
            }
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Log.w(TAG, "Failed to read database version", e)
            null
        }
    }

    /**
     * Checks for a version downgrade and automatically resolves it by deleting the
     * old database files. Returns true if the database was reset (caller should
     * proceed normally — Room will recreate it).
     *
     * Also deletes the database if the version cannot be read (e.g. passphrase
     * mismatch after reinstall) — better to start fresh than block the user.
     */
    fun checkAndResolveDowngrade(): Boolean {
        val dbFile = context.getDatabasePath(DATABASE_NAME)
        if (!dbFile.exists()) return false

        val currentVersion = getCurrentVersion()
        if (currentVersion == null) {
            // Can't read version — this often happens when EncryptedSharedPreferences
            // fails transiently after process kill (especially on Samsung/Xiaomi/OPPO).
            // Do NOT delete the database. Let Room attempt to open it — Room's own
            // fallback-to-destructive-migration will handle genuine corruption, while
            // transient passphrase read failures won't nuke the user's session.
            Log.w(TAG, "Cannot read database version — skipping pre-check, letting Room handle it.")
            return false
        }
        if (currentVersion <= EsiriplusDatabase.VERSION) return false

        Log.w(TAG, "Database downgrade detected: on-disk v$currentVersion > app v${EsiriplusDatabase.VERSION}. Deleting old database.")
        deleteDatabase()
        return true
    }

    private fun deleteDatabase() {
        val dbFile = context.getDatabasePath(DATABASE_NAME)
        val walFile = context.getDatabasePath("$DATABASE_NAME-wal")
        val shmFile = context.getDatabasePath("$DATABASE_NAME-shm")
        val journalFile = context.getDatabasePath("$DATABASE_NAME-journal")

        listOf(dbFile, walFile, shmFile, journalFile).forEach { file ->
            if (file.exists()) {
                val deleted = file.delete()
                Log.d(TAG, "Deleted ${file.name}: $deleted")
            }
        }
    }

    companion object {
        private const val TAG = "DatabaseVersionChecker"
        private const val DATABASE_NAME = "esiriplus.db"
    }
}
