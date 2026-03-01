package com.esiri.esiriplus.core.database.init

import android.content.Context
import android.util.Log
import com.esiri.esiriplus.core.database.encryption.DatabaseEncryption
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pre-Room downgrade detection by reading the on-disk database version directly via SQLCipher.
 * This runs before Room opens the database, so Room's IllegalStateException for downgrades
 * is caught early and surfaced as a user-friendly error.
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
     * Returns true if the on-disk database version is higher than the expected version,
     * indicating a downgrade scenario (e.g., user installed an older app version).
     */
    fun isDowngrade(): Boolean {
        val currentVersion = getCurrentVersion() ?: return false
        return currentVersion > EXPECTED_VERSION
    }

    companion object {
        private const val TAG = "DatabaseVersionChecker"
        private const val DATABASE_NAME = "esiriplus.db"
        const val EXPECTED_VERSION = 19
    }
}
