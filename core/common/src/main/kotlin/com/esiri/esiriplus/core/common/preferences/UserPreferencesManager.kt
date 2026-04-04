package com.esiri.esiriplus.core.common.preferences

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class FontScale { SMALL, NORMAL, LARGE }

@Singleton
class UserPreferencesManager @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("user_preferences", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(
        ThemeMode.entries.getOrElse(prefs.getInt(KEY_THEME, 1)) { ThemeMode.LIGHT },
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _fontScale = MutableStateFlow(
        FontScale.entries.getOrElse(prefs.getInt(KEY_FONT_SCALE, 1)) { FontScale.NORMAL },
    )
    val fontScale: StateFlow<FontScale> = _fontScale.asStateFlow()

    private val _highContrast = MutableStateFlow(prefs.getBoolean(KEY_HIGH_CONTRAST, false))
    val highContrast: StateFlow<Boolean> = _highContrast.asStateFlow()

    private val _reduceMotion = MutableStateFlow(prefs.getBoolean(KEY_REDUCE_MOTION, false))
    val reduceMotion: StateFlow<Boolean> = _reduceMotion.asStateFlow()

    private val _callRingtoneUri = MutableStateFlow(
        prefs.getString(KEY_CALL_RINGTONE, null)?.let { Uri.parse(it) },
    )
    /** Custom ringtone URI for incoming calls. Null means system default. */
    val callRingtoneUri: StateFlow<Uri?> = _callRingtoneUri.asStateFlow()

    private val _requestRingtoneUri = MutableStateFlow(
        prefs.getString(KEY_REQUEST_RINGTONE, null)?.let { Uri.parse(it) },
    )
    /** Custom ringtone URI for consultation requests. Null means system default. */
    val requestRingtoneUri: StateFlow<Uri?> = _requestRingtoneUri.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putInt(KEY_THEME, mode.ordinal).apply()
        _themeMode.value = mode
    }

    fun setFontScale(scale: FontScale) {
        prefs.edit().putInt(KEY_FONT_SCALE, scale.ordinal).apply()
        _fontScale.value = scale
    }

    fun setHighContrast(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HIGH_CONTRAST, enabled).apply()
        _highContrast.value = enabled
    }

    fun setReduceMotion(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_REDUCE_MOTION, enabled).apply()
        _reduceMotion.value = enabled
    }

    fun setCallRingtoneUri(uri: Uri?) {
        prefs.edit().putString(KEY_CALL_RINGTONE, uri?.toString()).apply()
        _callRingtoneUri.value = uri
    }

    fun setRequestRingtoneUri(uri: Uri?) {
        prefs.edit().putString(KEY_REQUEST_RINGTONE, uri?.toString()).apply()
        _requestRingtoneUri.value = uri
    }

    companion object {
        private const val KEY_THEME = "theme_mode"
        private const val KEY_FONT_SCALE = "font_scale"
        private const val KEY_HIGH_CONTRAST = "high_contrast"
        private const val KEY_REDUCE_MOTION = "reduce_motion"
        private const val KEY_CALL_RINGTONE = "call_ringtone_uri"
        private const val KEY_REQUEST_RINGTONE = "request_ringtone_uri"
    }
}
