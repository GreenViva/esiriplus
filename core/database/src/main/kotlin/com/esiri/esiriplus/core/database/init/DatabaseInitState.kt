package com.esiri.esiriplus.core.database.init

/**
 * Represents the current state of database initialization.
 */
sealed interface DatabaseInitState {
    data object Idle : DatabaseInitState
    data object Initializing : DatabaseInitState
    data object Ready : DatabaseInitState
    data class Failed(val error: DatabaseInitError) : DatabaseInitState
}

/**
 * Specific database initialization errors with associated exceptions.
 */
sealed interface DatabaseInitError {
    data class MigrationFailed(val exception: Exception) : DatabaseInitError
    data class EncryptionFailed(val exception: Exception) : DatabaseInitError
    data object VersionDowngrade : DatabaseInitError
    data class Unknown(val exception: Exception) : DatabaseInitError
}

/**
 * Thrown when database encryption verification fails during onOpen.
 */
class DatabaseEncryptionException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
