package com.athar.ui

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import androidx.core.content.edit
import java.util.Locale

/**
 * Per-app language helper used at MainActivity.attachBaseContext.
 *
 * The chosen tag is mirrored from DataStore into a synchronous SharedPreferences file because
 * attachBaseContext runs before any coroutine scope is available; reading DataStore there
 * would block and is not supported. Source-of-truth stays in DataStore — the UserPreferences
 * repo writes both stores on [com.athar.core.domain.repo.UserPreferencesRepository.setAppLocale].
 */
internal object LocaleHelper {
    private const val PREFS_NAME = "locale_prefs"
    private const val KEY_LANGUAGE_TAG = "app_locale_tag"

    /**
     * Wraps [base] with a Configuration whose first locale is the persisted user choice,
     * or returns [base] unchanged if no override is set (let the system locale apply).
     */
    fun wrap(base: Context): Context {
        val tag = prefs(base).getString(KEY_LANGUAGE_TAG, "").orEmpty()
        if (tag.isBlank()) return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration).apply {
            setLocale(locale)
            // setLocale doesn't propagate the layout direction in API 28+; do it explicitly so
            // English flips to LTR and Arabic stays RTL on activity recreate.
            setLayoutDirection(locale)
        }
        return base.createConfigurationContext(config)
    }

    /** Mirrors the user's choice into the synchronous prefs so attachBaseContext can read it. */
    fun persist(context: Context, tag: String) {
        prefs(context).edit { putString(KEY_LANGUAGE_TAG, tag) }
    }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
