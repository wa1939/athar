package com.athar.core.domain.model

import java.math.BigDecimal

data class UserTemplateAnchorMatch(
    val amount: BigDecimal?,
    val merchant: String?,
    val counterparty: String?,
    val amountAnchorFound: Boolean,
    val merchantAnchorFound: Boolean?,
    val counterpartyAnchorFound: Boolean?,
) {
    val canParseAmount: Boolean get() = amount != null
}

object UserTemplateAnchorMatcher {

    fun match(template: UserTemplate, body: String = template.sampleBody): UserTemplateAnchorMatch =
        match(
            body = body,
            amountAnchorBefore = template.amountAnchorBefore,
            amountAnchorAfter = template.amountAnchorAfter,
            merchantAnchorBefore = template.merchantAnchorBefore,
            merchantAnchorAfter = template.merchantAnchorAfter,
            counterpartyAnchorBefore = template.counterpartyAnchorBefore,
            counterpartyAnchorAfter = template.counterpartyAnchorAfter,
        )

    fun match(
        body: String,
        amountAnchorBefore: String,
        amountAnchorAfter: String?,
        merchantAnchorBefore: String?,
        merchantAnchorAfter: String?,
        counterpartyAnchorBefore: String?,
        counterpartyAnchorAfter: String?,
    ): UserTemplateAnchorMatch {
        val normalized = normalizeDigits(body)
        val amount = extractAmount(normalized, amountAnchorBefore, amountAnchorAfter)
        val merchant = extractOptionalText(normalized, merchantAnchorBefore, merchantAnchorAfter)
        val counterparty = extractOptionalText(
            body = normalized,
            before = counterpartyAnchorBefore,
            after = counterpartyAnchorAfter,
        )
        return UserTemplateAnchorMatch(
            amount = amount.value,
            merchant = merchant.value,
            counterparty = counterparty.value,
            amountAnchorFound = amount.anchorFound == true,
            merchantAnchorFound = merchant.anchorFound,
            counterpartyAnchorFound = counterparty.anchorFound,
        )
    }

    private fun extractAmount(body: String, before: String, after: String?): Extracted<BigDecimal> {
        val anchor = before.trim()
        if (anchor.isBlank()) return Extracted(value = null, anchorFound = false)
        val idx = body.indexOf(anchor, ignoreCase = true)
        if (idx < 0) return Extracted(value = null, anchorFound = false)

        val tail = body.substring(idx + anchor.length)
        val cleaned = tail.dropWhile { it.isWhitespace() || it in ":·" }
        val stripped = cleaned.replace(CurrencyPrefixRegex, "")
        val numberMatch = NumberRegex.find(stripped)
            ?: return Extracted(value = null, anchorFound = true)
        val rawNumber = numberMatch.value.replace(" ", "").replace(",", "")
        val parsed = runCatching { BigDecimal(rawNumber) }.getOrNull()
            ?: return Extracted(value = null, anchorFound = true)

        if (after != null) {
            val afterAnchor = after.trim()
            if (afterAnchor.isNotBlank()) {
                val windowEnd = (numberMatch.range.last + 50).coerceAtMost(stripped.length)
                val window = stripped.substring(numberMatch.range.last + 1, windowEnd)
                if (!window.contains(afterAnchor, ignoreCase = true)) {
                    return Extracted(value = null, anchorFound = true)
                }
            }
        }
        return Extracted(value = parsed, anchorFound = true)
    }

    private fun extractOptionalText(
        body: String,
        before: String?,
        after: String?,
    ): Extracted<String> {
        val anchor = before?.trim().orEmpty()
        if (anchor.isBlank()) return Extracted(value = null, anchorFound = null)
        val idx = body.indexOf(anchor, ignoreCase = true)
        if (idx < 0) return Extracted(value = null, anchorFound = false)

        val tail = body.substring(idx + anchor.length).trimStart(' ', '\t', ':', '·')
        val end = after?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { tail.indexOf(it, ignoreCase = true).takeIf { index -> index > 0 } }
            ?: tail.indexOf('\n').takeIf { it > 0 }
            ?: tail.length
        return Extracted(
            value = tail.substring(0, end).trim().takeIf { it.isNotEmpty() },
            anchorFound = true,
        )
    }

    private fun normalizeDigits(input: String): String = buildString(input.length) {
        for (ch in input) {
            append(
                when (ch) {
                    ArabicDecimal -> '.'
                    ArabicThousands -> ','
                    else -> ArabicDigits[ch] ?: ch
                },
            )
        }
    }

    private data class Extracted<T>(
        val value: T?,
        val anchorFound: Boolean?,
    )

    private val ArabicDigits = mapOf(
        '٠' to '0',
        '١' to '1',
        '٢' to '2',
        '٣' to '3',
        '٤' to '4',
        '٥' to '5',
        '٦' to '6',
        '٧' to '7',
        '٨' to '8',
        '٩' to '9',
        '۰' to '0',
        '۱' to '1',
        '۲' to '2',
        '۳' to '3',
        '۴' to '4',
        '۵' to '5',
        '۶' to '6',
        '۷' to '7',
        '۸' to '8',
        '۹' to '9',
    )
    private const val ArabicDecimal = '٫'
    private const val ArabicThousands = '٬'

    private val CurrencyPrefixRegex = Regex(
        pattern = "^(?:SAR|SR|AED|USD|EUR|GBP|INR|PKR|TRY|EGP|﷼|\\$|€|£|₹|ر\\.?\\s*س)\\s*",
        option = RegexOption.IGNORE_CASE,
    )
    private val NumberRegex = Regex(
        pattern = """\d{1,3}(?:[ ,]\d{3})+(?:\.\d{1,2})?|\d+(?:\.\d{1,2})?""",
    )
}
