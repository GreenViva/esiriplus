package com.esiri.esiriplus.viewmodel

import com.esiri.esiriplus.core.database.init.DatabaseInitError

/**
 * Combined app-level initialization state exposed by [MainViewModel].
 */
sealed interface AppInitState {
    data object Loading : AppInitState
    data object Ready : AppInitState
    data class DatabaseError(val error: DatabaseInitError) : AppInitState
}
