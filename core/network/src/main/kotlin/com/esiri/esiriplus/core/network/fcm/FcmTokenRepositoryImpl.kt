package com.esiri.esiriplus.core.network.fcm

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.esiri.esiriplus.core.domain.repository.FcmTokenRepository
import com.esiri.esiriplus.core.network.api.SupabaseApi
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FCM token management for both doctors and patients.
 *
 * Stores token locally in encrypted prefs, and pushes to the Supabase
 * `fcm_tokens` table via the authenticated Retrofit client.
 */
@Singleton
class FcmTokenRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context,
    private val supabaseApi: SupabaseApi,
) : FcmTokenRepository {

    private val prefs: SharedPreferences = try {
        EncryptedSharedPreferences.create(
            context,
            "esiri_fcm_prefs",
            MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (e: Exception) {
        Log.w(TAG, "Encrypted prefs corrupted, clearing and recreating", e)
        context.getSharedPreferences("esiri_fcm_prefs", Context.MODE_PRIVATE).edit().clear().apply()
        context.deleteSharedPreferences("esiri_fcm_prefs")
        EncryptedSharedPreferences.create(
            context,
            "esiri_fcm_prefs",
            MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override suspend fun registerToken(token: String, userId: String): Boolean {
        prefs.edit().putString(KEY_FCM_TOKEN, token).apply()

        return try {
            val response = supabaseApi.upsertFcmToken(
                mapOf(
                    "user_id" to userId,
                    "token" to token,
                    "updated_at" to java.time.Instant.now().toString(),
                ),
            )
            if (response.isSuccessful) {
                Log.d(TAG, "FCM token pushed to fcm_tokens for $userId")
                true
            } else {
                Log.e(TAG, "FCM token upsert failed: ${response.code()} ${response.errorBody()?.string()}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to push FCM token to fcm_tokens", e)
            false
        }
    }

    override suspend fun getStoredToken(): String? {
        return prefs.getString(KEY_FCM_TOKEN, null)
    }

    override suspend fun clearToken() {
        prefs.edit().remove(KEY_FCM_TOKEN).apply()
    }

    override suspend fun fetchAndRegisterToken(userId: String, maxAttempts: Int): Boolean {
        repeat(maxAttempts) { attempt ->
            val token = try {
                FirebaseMessaging.getInstance().token.await()
            } catch (e: Exception) {
                Log.w(TAG, "FCM fetch failed on attempt ${attempt + 1}", e)
                getStoredToken()  // Fallback to cached token
            }

            if (token.isNullOrBlank()) {
                Log.w(TAG, "No FCM token available on attempt ${attempt + 1}")
            } else if (registerToken(token, userId)) {
                Log.d(TAG, "FCM token registered for $userId on attempt ${attempt + 1}")
                return true
            }

            if (attempt < maxAttempts - 1) {
                kotlinx.coroutines.delay(1_000L shl attempt) // 1s, 2s, 4s
            }
        }
        Log.e(TAG, "FCM token registration failed after $maxAttempts attempts for $userId — pushes will not arrive")
        return false
    }

    companion object {
        private const val TAG = "FcmTokenRepo"
        private const val KEY_FCM_TOKEN = "fcm_token"
    }
}
