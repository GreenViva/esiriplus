package com.esiri.esiriplus.core.network

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.tokenDataStore: DataStore<Preferences> by preferencesDataStore(name = "auth_tokens")

@Singleton
class TokenManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val accessTokenKey = stringPreferencesKey("access_token")
    private val refreshTokenKey = stringPreferencesKey("refresh_token")

    val accessToken: Flow<String?> = context.tokenDataStore.data.map { prefs ->
        prefs[accessTokenKey]
    }

    val refreshToken: Flow<String?> = context.tokenDataStore.data.map { prefs ->
        prefs[refreshTokenKey]
    }

    suspend fun saveTokens(accessToken: String, refreshToken: String) {
        context.tokenDataStore.edit { prefs ->
            prefs[accessTokenKey] = accessToken
            prefs[refreshTokenKey] = refreshToken
        }
    }

    suspend fun clearTokens() {
        context.tokenDataStore.edit { it.clear() }
    }
}
