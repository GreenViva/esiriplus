package com.esiri.esiriplus.core.common.locale

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import java.util.Locale

/**
 * Locale utility for non-Activity components (Services, BroadcastReceivers).
 *
 * [AppCompatDelegate.setApplicationLocales] automatically applies the locale
 * to AppCompatActivity subclasses. For Services and other components that don't
 * extend AppCompatActivity, use [getLocalizedContext] to obtain a Context whose
 * resources reflect the user's chosen locale.
 */
object LocaleHelper {

    /**
     * Returns a [Context] configured with the app's per-app locale.
     * If no locale has been explicitly set, returns [base] unchanged
     * (system default applies).
     */
    fun getLocalizedContext(base: Context): Context {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) return base
        val locale = locales[0] ?: return base
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }

    /**
     * Returns the currently active locale code (e.g. "en", "sw").
     * Falls back to the system default if no per-app locale is set.
     */
    fun getCurrentLocaleCode(): String {
        val locales = AppCompatDelegate.getApplicationLocales()
        return if (locales.isEmpty) {
            Locale.getDefault().language
        } else {
            locales[0]?.language ?: Locale.getDefault().language
        }
    }

    /**
     * Whether the user has explicitly selected a language (vs. following system default).
     */
    fun hasLanguageBeenSelected(): Boolean {
        return !AppCompatDelegate.getApplicationLocales().isEmpty
    }
}
