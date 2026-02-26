package com.esiri.esiriplus.core.network.fcm

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.esiri.esiriplus.core.domain.repository.FcmTokenRepository
import com.esiri.esiriplus.core.network.SupabaseClientProvider
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FCM token management for both doctors and patients.
 *
 * Stores token locally in encrypted prefs, and pushes to the Supabase
 * `fcm_tokens` table keyed by user_id. This table is user-role-agnostic
 * so both doctors and patients can receive push notifications.
 */
@Singleton
class FcmTokenRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context,
    private val supabaseClientProvider: SupabaseClientProvider,
) : FcmTokenRepository {

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "esiri_fcm_prefs",
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    override suspend fun registerToken(token: String, userId: String) {
        prefs.edit().putString(KEY_FCM_TOKEN, token).apply()

        try {
            supabaseClientProvider.client.from("fcm_tokens")
                .upsert(
                    mapOf(
                        "user_id" to userId,
                        "token" to token,
                        "updated_at" to java.time.Instant.now().toString(),
                    ),
                )
            Log.d(TAG, "FCM token pushed to Supabase for $userId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to push FCM token to Supabase", e)
        }
    }

    override suspend fun getStoredToken(): String? {
        return prefs.getString(KEY_FCM_TOKEN, null)
    }

    override suspend fun clearToken() {
        prefs.edit().remove(KEY_FCM_TOKEN).apply()
    }

    override suspend fun fetchAndRegisterToken(userId: String) {
        try {
            val storedToken = getStoredToken()
            if (storedToken != null) {
                registerToken(storedToken, userId)
                return
            }
            val token = FirebaseMessaging.getInstance().token.await()
            registerToken(token, userId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch and register FCM token", e)
        }
    }

    companion object {
        private const val TAG = "FcmTokenRepo"
        private const val KEY_FCM_TOKEN = "fcm_token"
    }
}
