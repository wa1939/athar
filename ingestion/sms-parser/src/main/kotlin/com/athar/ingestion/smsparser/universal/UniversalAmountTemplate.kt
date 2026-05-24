package com.athar.ingestion.smsparser.universal

import com.athar.core.common.money.Money
import com.athar.core.domain.model.TxType
import com.athar.ingestion.smsparser.BankTemplate
import com.athar.ingestion.smsparser.Normalize
import com.athar.ingestion.smsparser.ParseResult
import com.athar.ingestion.smsparser.SenderMatcher
import kotlinx.datetime.Instant
import java.math.BigDecimal

/**
 * Currency-and-language-agnostic last-resort parser.
 *
 * Activates for ANY sender that wasn't matched by a more specific bank template.
 * Detects:
 *   - amount: decimal with optional thousands separators
 *   - currency: 3-letter ISO codes (SAR, AED, USD, EUR, GBP, INR, …) OR symbols ($, €, £, ﷼, ₹)
 *   - direction: action keywords in Arabic + English + Spanish + French + Urdu + Turkish
 *
 * Always returns Success with low confidence (≤0.55) so the user is forced to
 * confirm/correct in the pending tray. This is the bridge layer until the
 * on-device ML classifier ships (see ADR-005).
 *
 * Why a separate sender matcher? `SenderMatcher.Regex(".*")` lets ANY sender pass
 * the registry filter; the parser logic then either yields a Success (transaction
 * detected) or Failed (no monetary signal found). Templates above this one short-
 * circuit known-bank parsing so this only fires when nothing else matched.
 */
class UniversalAmountTemplate : BankTemplate {
    override val id: String = "universal-amount"
    override val senderMatcher: SenderMatcher = SenderMatcher.Regex(Regex(".+"))

    // (currency-symbol|ISO-code)?  amount  (ISO-code)?  — covers `$200`, `200 SAR`, `SAR 200`, `₹500`.
    private val amountWithCurrency = Regex(
        """(?:(?<lead>[\$€£﷼₹¥₺د\.ك]|SAR|SR|AED|USD|EUR|GBP|INR|PKR|TRY|EGP|KWD|QAR|BHD|OMR|JOD)\s*)?(?<num>\d{1,3}(?:[ ,]\d{3})*(?:\.\d{1,2})?|\d+(?:\.\d{1,2})?)(?:\s*(?<trail>[\$€£﷼₹¥₺د\.ك]|SAR|SR|AED|USD|EUR|GBP|INR|PKR|TRY|EGP|KWD|QAR|BHD|OMR|JOD|ر\.?\s*س|د\.?\s*إ))?""",
        RegexOption.IGNORE_CASE,
    )

    private val incomeWords = Regex(
        """\b(?:credit|deposit|received|incoming|refund|salary|payment\s+from|إيداع|ايداع|وارد|استلم|تم\s+استلام|recibido|reçu|gelir|گیا)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val expenseWords = Regex(
        """\b(?:purchase|paid|debit|withdrawal|spent|charge|pos|atm|شراء|سحب|خصم|دفع|cobrado|payé|harcanan|خرچ)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val transferWords = Regex(
        """\b(?:transfer|sent|outgoing|remit|تحويل|إرسال|envío|virement|havale|بھیج)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val ignoreWords = Regex(
        """\b(?:otp|verification\s+code|رمز\s+التحقق|verify\s+code|do\s+not\s+share|promo|عرض\s+ترويجي)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val merchantHint = Regex(
        """(?:at|from|to|لدى|من|إلى|الى|de|à|en|chez)\s+([A-Za-z\u0600-\u06FF][^\n\r]{1,40})""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE),
    )

    override fun tryParse(body: String, receivedAt: Instant): ParseResult {
        val normalized = Normalize.digits(body)

        if (ignoreWords.containsMatchIn(normalized) &&
            !incomeWords.containsMatchIn(normalized) &&
            !expenseWords.containsMatchIn(normalized) &&
            !transferWords.containsMatchIn(normalized)
        ) {
            return ParseResult.Ignored
        }

        val match = amountWithCurrency.find(normalized)
            ?: return ParseResult.Failed("no monetary value detected", listOf(id))
        val rawNum = match.groups["num"]?.value
            ?: return ParseResult.Failed("amount group missing", listOf(id))
        val parsed = runCatching {
            BigDecimal(rawNum.replace(" ", "").replace(",", ""))
        }.getOrNull() ?: return ParseResult.Failed("amount unparseable: $rawNum", listOf(id))

        val currency = (match.groups["lead"]?.value ?: match.groups["trail"]?.value)?.trim()

        // Heuristic: prefer the most specific verb. Income > Transfer > Expense (default).
        val type = when {
            incomeWords.containsMatchIn(normalized) -> TxType.INCOME
            transferWords.containsMatchIn(normalized) -> TxType.TRANSFER
            else -> TxType.EXPENSE
        }

        val merchant = merchantHint.find(normalized)?.groupValues?.get(1)?.trim()

        // Confidence model: bare amount = very low, +currency = +0.15, +merchant = +0.10,
        // explicit action verb = +0.10. Caps at 0.55 so user always confirms.
        var confidence = 0.20f
        if (currency != null) confidence += 0.15f
        if (merchant != null) confidence += 0.10f
        if (incomeWords.containsMatchIn(normalized) ||
            transferWords.containsMatchIn(normalized) ||
            expenseWords.containsMatchIn(normalized)
        ) confidence += 0.10f

        return ParseResult.Success(
            type = type,
            amount = Money.of(parsed),
            merchant = merchant,
            counterparty = null,
            balanceAfter = null,
            occurredAt = receivedAt,
            confidence = confidence.coerceAtMost(0.55f),
            templateId = id,
        )
    }
}
