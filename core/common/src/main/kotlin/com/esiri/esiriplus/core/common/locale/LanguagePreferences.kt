package com.esiri.esiriplus.core.common.locale

import android.content.Context

object LanguagePreferences {
    private const val PREFS_NAME = "esiriplus_language_prefs"
    private const val KEY_LANGUAGE_CODE = "language_code"
    private const val KEY_LANGUAGE_SELECTED = "language_selected"
    private const val DEFAULT_LANGUAGE = "en"

    fun getLanguageCode(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE_CODE, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE
    }

    fun setLanguageCode(context: Context, code: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE_CODE, code)
            .putBoolean(KEY_LANGUAGE_SELECTED, true)
            .apply()
    }

    fun hasLanguageBeenSelected(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_LANGUAGE_SELECTED, false)
    }
}
