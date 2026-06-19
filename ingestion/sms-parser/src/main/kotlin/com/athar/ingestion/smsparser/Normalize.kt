package com.athar.ingestion.smsparser

import java.math.BigDecimal

/**
 * SMS text normalization — Master Brief §4.6, Qayd PRD §7.
 *
 * Converts Arabic/Persian digits to Latin, normalizes SAR/ر.س/ريال, strips POS noise,
 * compresses whitespace. Pure functions; safe to call repeatedly.
 */
internal object Normalize {

    const val LOCALIZED_AMOUNT_PATTERN: String =
        """\d{1,3}(?:[ .]\d{3})+,\d{1,2}|\d{1,3}(?:[ ,]\d{3})+(?:\.\d{1,2})?|\d+,\d{1,2}|\d+(?:\.\d{1,2})?"""

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

    fun amount(raw: String): BigDecimal? {
        val compact = raw.trim()
            .replace('\u00A0', ' ')
            .replace(" ", "")
        if (compact.isBlank()) return null

        val commaCount = compact.count { it == ',' }
        val dotCount = compact.count { it == '.' }
        val lastComma = compact.lastIndexOf(',')
        val lastDot = compact.lastIndexOf('.')
        val normalized = when {
            commaCount > 0 && dotCount > 0 && lastComma > lastDot ->
                compact.replace(".", "").replace(",", ".")
            commaCount > 0 && dotCount > 0 ->
                compact.replace(",", "")
            commaCount == 1 && compact.length - lastComma - 1 in 1..2 ->
                compact.replace(",", ".")
            commaCount > 0 ->
                compact.replace(",", "")
            dotCount > 0 && compact.length - lastDot - 1 == 3 ->
                compact.replace(".", "")
            else -> compact
        }
        return runCatching { BigDecimal(normalized) }.getOrNull()
    }

    fun currencyCode(raw: String?): String {
        val normalized = raw?.trim()?.uppercase()?.replace(" ", "")
        return when (normalized) {
            "$" -> "USD"
            "€" -> "EUR"
            "£" -> "GBP"
            "﷼", "SR", "SAR", "ر.س", "رس" -> "SAR"
            "₹" -> "INR"
            "¥", "JPY" -> "JPY"
            "₺" -> "TRY"
            "S$" -> "SGD"
            "HK$" -> "HKD"
            "R$" -> "BRL"
            "MX$", "MEX$" -> "MXN"
            "RM" -> "MYR"
            "RP" -> "IDR"
            "₱" -> "PHP"
            "₩" -> "KRW"
            "฿" -> "THB"
            "₫" -> "VND"
            "CAD", "CA$", "C$" -> "CAD"
            "AUD", "AU$", "A$" -> "AUD"
            "CHF" -> "CHF"
            "د.إ", "دإ", "AED" -> "AED"
            "USD", "EUR", "GBP", "INR", "PKR", "TRY", "EGP", "KWD", "QAR", "BHD", "OMR", "JOD",
            "CNY", "HKD", "SGD", "SEK", "NOK", "DKK", "ZAR", "BRL", "MXN", "THB", "IDR",
            "MYR", "PHP", "VND", "KRW" -> normalized
            else -> "SAR"
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
