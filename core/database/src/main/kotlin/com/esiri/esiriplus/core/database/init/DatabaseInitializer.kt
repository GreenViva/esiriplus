package com.esiri.esiriplus.core.database.init

import android.util.Log
import com.esiri.esiriplus.core.database.EsiriplusDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Safe database initialization wrapper. Forces Room to open the database on a background
 * thread and translates failures into typed [DatabaseInitState] values for the UI layer.
 */
@Singleton
class DatabaseInitializer @Inject constructor(
    private val database: dagger.Lazy<EsiriplusDatabase>,
    private val versionChecker: DatabaseVersionChecker,
) {

    private val _state = MutableStateFlow<DatabaseInitState>(DatabaseInitState.Idle)
    val state: StateFlow<DatabaseInitState> = _state.asStateFlow()

    /**
     * Initializes the database. Idempotent: returns [DatabaseInitState.Ready] immediately
     * on subsequent calls after a successful initialization.
     */
    suspend fun initialize(): DatabaseInitState {
        // Already initialized — return immediately
        if (_state.value is DatabaseInitState.Ready) return _state.value

        _state.value = DatabaseInitState.Initializing

        // Step 1: Pre-Room downgrade check
        if (versionChecker.isDowngrade()) {
            val failed = DatabaseInitState.Failed(DatabaseInitError.VersionDowngrade)
            _state.value = failed
            return failed
        }

        // Step 2: Force Room to open the database (runs migrations + callback.onOpen)
        return try {
            withContext(Dispatchers.IO) {
                database.get().openHelper.writableDatabase
            }
            _state.value = DatabaseInitState.Ready
            DatabaseInitState.Ready
        } catch (e: DatabaseEncryptionException) {
            Log.e(TAG, "Database encryption failed", e)
            val failed = DatabaseInitState.Failed(DatabaseInitError.EncryptionFailed(e))
            _state.value = failed
            failed
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Database migration failed", e)
            val failed = DatabaseInitState.Failed(DatabaseInitError.MigrationFailed(e))
            _state.value = failed
            failed
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Log.e(TAG, "Database initialization failed", e)
            val failed = DatabaseInitState.Failed(DatabaseInitError.Unknown(e))
            _state.value = failed
            failed
        }
    }

    companion object {
        private const val TAG = "DatabaseInitializer"
    }
}
