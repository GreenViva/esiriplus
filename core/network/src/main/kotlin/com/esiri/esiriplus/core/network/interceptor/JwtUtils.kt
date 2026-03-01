package com.esiri.esiriplus.core.network.interceptor

import android.util.Base64
import org.json.JSONObject

/**
 * Shared JWT utility to avoid duplicating isPatientToken logic across
 * AuthInterceptor, TokenRefreshAuthenticator, and ProactiveTokenRefreshInterceptor.
 */
object JwtUtils {

    /**
     * Returns true if the given JWT is a patient custom token.
     * Checks both legacy format (`role = "patient"`) and new format
     * (`role = "authenticated"` + `app_role = "patient"`).
     * Patient custom JWTs are NOT valid Supabase Auth tokens and must not be
     * sent to PostgREST/Realtime/Storage endpoints.
     */
    fun isPatientToken(token: String): Boolean {
        return try {
            val parts = token.split(".")
            if (parts.size < 2) false
            else {
                val payload = String(Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_PADDING))
                val json = JSONObject(payload)
                val role = json.optString("role", "")
                val appRole = json.optString("app_role", "")
                role == "patient" || appRole == "patient"
            }
        } catch (_: Exception) {
            false
        }
    }
}
