package com.athar.feature.today

import com.athar.core.domain.model.TxType
import java.math.BigDecimal

data class ManualEntryPhrase(
    val amountInput: String,
    val merchant: String,
    val merchantNormalized: String,
    val type: TxType,
)

internal object ManualEntryPhraseParser {

    fun parse(raw: String): ManualEntryPhrase? {
        val body = normalizeDigits(raw).replace('\u00A0', ' ').trim()
        if (body.isBlank()) return null

        val amountMatch = AmountPattern.find(body) ?: return null
        val amount = parseAmount(amountMatch.value)?.takeIf { it.signum() > 0 } ?: return null
        val type = if (IncomeWords.containsMatchIn(body)) TxType.INCOME else TxType.EXPENSE
        val merchant = extractMerchant(body, amountMatch, type) ?: return null

        return ManualEntryPhrase(
            amountInput = amount.stripTrailingZeros().toPlainString(),
            merchant = merchant,
            merchantNormalized = merchant.lowercase().trim(),
            type = type,
        )
    }

    private fun extractMerchant(body: String, amountMatch: MatchResult, type: TxType): String? {
        val afterAmount = body.substring(amountMatch.range.last + 1)
        hintAfterAmount(afterAmount, type)?.let { return it }

        val beforeAmount = body.substring(0, amountMatch.range.first)
        if (type == TxType.EXPENSE) {
            merchantBeforeAmount(beforeAmount)?.let { return it }
        }
        return cleanParty(afterAmount.ifBlank { beforeAmount })
    }

    private fun hintAfterAmount(raw: String, type: TxType): String? {
        val regex = if (type == TxType.INCOME) IncomeHint else ExpenseHint
        return regex.find(raw)?.groupValues?.get(1)?.let(::cleanParty)
    }

    private fun merchantBeforeAmount(raw: String): String? =
        ExpenseBeforeAmount
            .asSequence()
            .mapNotNull { it.find(raw)?.groupValues?.get(1) }
            .mapNotNull(::cleanParty)
            .firstOrNull()

    private fun cleanParty(raw: String): String? {
        val cleaned = raw
            .lineSequence()
            .firstOrNull()
            .orEmpty()
            .replace(AmountPattern, "")
            .replace(CurrencyWords, "")
            .replace(LeadingNoise, "")
            .trim(' ', '.', ',', '-', '·', ':')
            .replace(Regex("""\s+"""), " ")
            .take(56)
            .trim()
        return cleaned.takeIf { it.length >= 2 && !AmountPattern.containsMatchIn(it) }
    }

    private fun parseAmount(raw: String): BigDecimal? {
        val compact = raw.trim().replace(" ", "")
        val lastComma = compact.lastIndexOf(',')
        val lastDot = compact.lastIndexOf('.')
        val canonical = when {
            lastComma >= 0 && lastDot >= 0 && lastComma > lastDot ->
                compact.replace(".", "").replace(",", ".")
            lastComma >= 0 && lastDot >= 0 ->
                compact.replace(",", "")
            lastComma >= 0 && compact.length - lastComma - 1 in 1..2 ->
                compact.replace(",", ".")
            lastComma >= 0 ->
                compact.replace(",", "")
            lastDot >= 0 && compact.length - lastDot - 1 == 3 ->
                compact.replace(".", "")
            else -> compact
        }
        return runCatching { BigDecimal(canonical) }.getOrNull()
    }

    private fun normalizeDigits(raw: String): String = buildString(raw.length) {
        raw.forEach { ch ->
            append(
                when (ch) {
                    '٠', '۰' -> '0'
                    '١', '۱' -> '1'
                    '٢', '۲' -> '2'
                    '٣', '۳' -> '3'
                    '٤', '۴' -> '4'
                    '٥', '۵' -> '5'
                    '٦', '۶' -> '6'
                    '٧', '۷' -> '7'
                    '٨', '۸' -> '8'
                    '٩', '۹' -> '9'
                    '٫' -> '.'
                    '٬' -> ','
                    else -> ch
                },
            )
        }
    }

    private val AmountPattern = Regex("""(?<!\d)\d{1,3}(?:[ ,.]\d{3})+(?:[.,]\d{1,2})?|\d+[.,]\d{1,2}|\d+""")
    private val IncomeWords = Regex(
        """\b(?:income|received|receive|salary|deposit|paid\s+me)\b|استلمت|استلام|راتب|دخل|ايداع|إيداع|وارد""",
        RegexOption.IGNORE_CASE,
    )
    private val ExpenseHint = Regex(
        """(?:\bat\b|\bto\b|\bfor\b|\bin\b|لدى|عند|في|الى|إلى|لـ)\s+(.+)$""",
        RegexOption.IGNORE_CASE,
    )
    private val IncomeHint = Regex("""(?:\bfrom\b|\bby\b|من)\s+(.+)$""", RegexOption.IGNORE_CASE)
    private val ExpenseBeforeAmount = listOf(
        Regex("""\b(?:spent|paid|bought|purchase|expense)\s+(.+)$""", RegexOption.IGNORE_CASE),
        Regex("""(?:دفعت|دفع|اشتريت|شراء|صرفت|مصروف)\s+(.+)$"""),
    )
    private val CurrencyWords = Regex(
        """\b(?:sar|sr|usd|eur|gbp|aed|inr|riyal|riyals)\b|ر\.?\s*س|ريال|دولار|درهم""",
        RegexOption.IGNORE_CASE,
    )
    private val LeadingNoise = Regex(
        """^(?:at|to|for|from|by|in|merchant|vendor|income|salary|spent|paid|expense|لدى|عند|في|من|الى|إلى|لـ|دفعت|دفع|استلمت|راتب)\s+""",
        RegexOption.IGNORE_CASE,
    )
}

internal object ManualEntryPhraseApplier {

    fun apply(state: AddTransactionState, phrase: String): AddTransactionState {
        val parsed = ManualEntryPhraseParser.parse(phrase)
            ?: return state.copy(quickEntryError = QuickEntryError.PARSE_FAILED)
        val suggestion = state.merchantSuggestions.firstOrNull {
            it.type == parsed.type && it.merchantNormalized == parsed.merchantNormalized
        }

        return state.copy(
            quickEntry = phrase,
            quickEntryError = null,
            amount = parsed.amountInput,
            merchant = suggestion?.merchant ?: parsed.merchant,
            type = parsed.type,
            selectedCategoryId = suggestion?.categoryId,
            validationError = null,
        )
    }
}
