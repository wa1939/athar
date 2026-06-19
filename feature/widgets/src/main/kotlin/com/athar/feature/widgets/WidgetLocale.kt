package com.athar.feature.widgets

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

internal fun Context.widgetLocaleContext(languageTag: String): Context {
    if (languageTag.isBlank()) return this
    val locale = Locale.forLanguageTag(languageTag)
    val config = Configuration(resources.configuration).apply {
        setLocale(locale)
        setLayoutDirection(locale)
    }
    return createConfigurationContext(config)
}
