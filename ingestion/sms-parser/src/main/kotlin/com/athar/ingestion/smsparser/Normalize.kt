package com.athar.ingestion.smsparser

/**
 * SMS text normalization — Master Brief §4.6, Qayd PRD §7.
 *
 * Converts Arabic/Persian digits to Latin, normalizes SAR/ر.س/ريال, strips POS noise,
 * compresses whitespace. Pure functions; safe to call repeatedly.
 */
internal object Normalize {

    private val ArabicDigits = mapOf(
        '٠' to '0', '١' to '1', '٢' to '2', '٣' to '3', '٤' to '4',
        '٥' to '5', '٦' to '6', '٧' to '7', '٨' to '8', '٩' to '9',
        '۰' to '0', '۱' to '1', '۲' to '2', '۳' to '3', '۴' to '4',
        '۵' to '5', '۶' to '6', '۷' to '7', '۸' to '8', '۹' to '9',
    )

    private val ArabicDecimal = '٫'  // Arabic decimal separator
    private val ArabicThousands = '٬' // Arabic thousands separator

    fun digits(input: String): String = buildString(input.length) {
        for (ch in input) {
            when (ch) {
                // SMS bodies from some Arabic bank gateways contain bidi controls
                // around SAR/amount tokens. They are invisible, but break regexes.
                '\u200E', '\u200F',
                '\u202A', '\u202B', '\u202C', '\u202D', '\u202E',
                '\u2066', '\u2067', '\u2068', '\u2069' -> Unit
                '\u00A0' -> append(' ')
                ArabicDecimal -> append('.')
                ArabicThousands -> append(',')
                else -> append(ArabicDigits[ch] ?: ch)
            }
        }
    }

    fun merchant(raw: String): String =
        raw.trim()
            .lowercase()
            .replace(Regex("\\s+"), " ")
            .replace(Regex("\\b(pos|purchase|domestic|debit|card)\\b"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
}
