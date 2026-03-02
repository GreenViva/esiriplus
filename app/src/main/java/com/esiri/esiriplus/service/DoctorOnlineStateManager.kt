package com.esiri.esiriplus.service

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the doctor's online state so the foreground service can recover
 * after process death (START_STICKY restart with null intent).
 */
@Singleton
class DoctorOnlineStateManager @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("doctor_online_state", Context.MODE_PRIVATE)

    var isOnline: Boolean
        get() = prefs.getBoolean(KEY_IS_ONLINE, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_ONLINE, value).apply()

    var doctorId: String?
        get() = prefs.getString(KEY_DOCTOR_ID, null)
        set(value) = prefs.edit().putString(KEY_DOCTOR_ID, value).apply()

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_IS_ONLINE = "is_online"
        const val KEY_DOCTOR_ID = "doctor_id"
    }
}
