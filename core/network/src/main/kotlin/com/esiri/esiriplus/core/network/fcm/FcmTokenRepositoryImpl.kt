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

    override suspend fun registerToken(token: String, userId: String) {
        prefs.edit().putString(KEY_FCM_TOKEN, token).apply()

        try {
            val response = supabaseApi.upsertFcmToken(
                mapOf(
                    "user_id" to userId,
                    "token" to token,
                    "updated_at" to java.time.Instant.now().toString(),
                ),
            )
            if (response.isSuccessful) {
                Log.d(TAG, "FCM token pushed to fcm_tokens for $userId")
            } else {
                Log.e(TAG, "FCM token upsert failed: ${response.code()} ${response.errorBody()?.string()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to push FCM token to fcm_tokens", e)
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
            // Always fetch a fresh token from Firebase to ensure it's current
            val token = FirebaseMessaging.getInstance().token.await()
            Log.d(TAG, "Firebase token obtained for $userId: ${token.take(10)}...")
            registerToken(token, userId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch Firebase token", e)
            // Fall back to stored token if Firebase fetch fails
            val stored = getStoredToken()
            if (stored != null) {
                Log.d(TAG, "Using stored FCM token for $userId")
                registerToken(stored, userId)
            } else {
                Log.e(TAG, "No stored FCM token available either")
            }
        }
    }

    companion object {
        private const val TAG = "FcmTokenRepo"
        private const val KEY_FCM_TOKEN = "fcm_token"
    }
}
