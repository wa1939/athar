package com.athar.ingestion.smsparser.notification

import com.athar.core.common.money.Money
import com.athar.core.domain.model.TxType
import com.athar.ingestion.smsparser.BankTemplate
import com.athar.ingestion.smsparser.Normalize
import com.athar.ingestion.smsparser.ParseResult
import com.athar.ingestion.smsparser.SenderMatcher
import kotlinx.datetime.Instant

/**
 * Generic parser for Play-Store-safe bank-app push notifications.
 *
 * It intentionally matches notification-prefixed package sender names, not SMS sender IDs. The
 * NotificationListenerService filters packages before events reach the pipeline and records them
 * as `notification:<package>`; this template gives those events a parser path without loosening
 * SMS sender allow-lists.
 */
class GenericBankNotificationTemplate : BankTemplate {
    override val id: String = "generic-bank-notification"
    override val senderMatcher: SenderMatcher = SenderMatcher.Regex(BankPackagePattern)

    private val amountWithCurrency = Regex(
        """(?:(?<lead>CA\$|C\$|AU\$|A\$|[\$€£﷼₹¥₺]|SAR|SR|AED|USD|EUR|GBP|CAD|AUD|CHF|INR|PKR|TRY|EGP|KWD|QAR|BHD|OMR|JOD|JPY)\s*)?(?<num>${Normalize.LOCALIZED_AMOUNT_PATTERN})(?:\s*(?<trail>CA\$|C\$|AU\$|A\$|[\$€£﷼₹¥₺]|SAR|SR|AED|USD|EUR|GBP|CAD|AUD|CHF|INR|PKR|TRY|EGP|KWD|QAR|BHD|OMR|JOD|JPY|ر\.?\s*س|د\.?\s*إ))?""",
        RegexOption.IGNORE_CASE,
    )
    private val expenseWords = Regex(
        """\b(?:spent|purchase|paid|payment|debit|charged|card\s+purchase|withdrawal|pos|خصم|شراء|دفع|سحب)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val expensePhrases = Regex(
        """\b(?:card\s+(?:ending\s+\d{2,4}\s+)?(?:was\s+)?used|card\s+payment|debit\s+card\s+transaction|direct\s+debit|payment\s+to|transaction\s+at|transaction\s+with)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val incomeWords = Regex(
        """\b(?:received|deposit|deposited|credited|refund|salary|incoming|top\s*up|إيداع|ايداع|وارد|استلام|استلمت|راتب)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val incomePhrases = Regex(
        """\b(?:paid\s+you|sent\s+you|got\s+paid|was\s+paid|were\s+paid|payment\s+from|direct\s+deposit|direct\s+credit|ach\s+credit|credit\s+from|money\s+received)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val transferWords = Regex(
        """\b(?:sent|transfer|transferred|outgoing|remit|تحويل|حوالة|إرسال|ارسال)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val statementWords = Regex(
        """\b(?:statement\s+(?:is\s+)?(?:ready|available)|minimum\s+payment|payment\s+due|due\s+date)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val limitWords = Regex(
        """\b(?:transfer\s+limit|daily\s+limit|card\s+limit|spending\s+limit|limit\s+(?:changed|updated|increased|decreased))\b""",
        RegexOption.IGNORE_CASE,
    )
    private val declinedWords = Regex(
        """\b(?:declined|rejected|failed|unsuccessful|مرفوض|رُفض|فشل|غير\s+ناجحة)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val requestWords = Regex(
        """\b(?:requested|requesting|requests?|payment\s+request|money\s+request)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val securityWords = Regex(
        """\b(?:otp|one[-\s]?time|verification|security\s+code|login|password|do\s+not\s+share|رمز|تحقق|الدخول|كلمة\s+المرور)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val marketingWords = Regex(
        """\b(?:offer|promo|cashback|points|reward|earn|win|discount|عرض|خصم|نقاط|مكافأة|اكسب|اربح)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val balanceWords = Regex(
        """\b(?:balance|available|رصيد|المتاح)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val atHint = Regex(
        """(?:\bat\b|\bwith\b|لدى|عند)\s+([A-Za-z\u0600-\u06FF][^\n\r]+)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE),
    )
    private val toHint = Regex(
        """(?:\bto\b|إلى|الى|لـ)\s+([A-Za-z\u0600-\u06FF][^\n\r]+)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE),
    )
    private val fromHint = Regex(
        """(?:\bfrom\b|من)\s+([A-Za-z\u0600-\u06FF][^\n\r]+)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE),
    )
    private val byHint = Regex(
        """(?:\bby\b)\s+([A-Za-z\u0600-\u06FF][^\n\r]+)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE),
    )
    private val incomingPersonHint = Regex(
        """(?m)^([A-Za-z\u0600-\u06FF][^\n\r]{1,64}?)\s+(?:paid|sent)\s+you\b""",
        RegexOption.IGNORE_CASE,
    )

    override fun tryParse(body: String, receivedAt: Instant): ParseResult {
        val normalized = Normalize.digits(body)
        val hasAction = hasAction(normalized)

        if (declinedWords.containsMatchIn(normalized)) return ParseResult.Ignored
        if (statementWords.containsMatchIn(normalized)) return ParseResult.Ignored
        if (limitWords.containsMatchIn(normalized) && !hasExpenseAction(normalized) && !hasIncomeAction(normalized)) {
            return ParseResult.Ignored
        }
        if (requestWords.containsMatchIn(normalized) && !hasAction) return ParseResult.Ignored
        if (securityWords.containsMatchIn(normalized) && !hasAction) return ParseResult.Ignored
        if (marketingWords.containsMatchIn(normalized) && !hasAction) return ParseResult.Ignored
        if (balanceWords.containsMatchIn(normalized) && !hasAction && !hasMerchantHint(normalized)) {
            return ParseResult.Ignored
        }

        val amountMatch = amountWithCurrency.findAll(normalized).firstOrNull { it.hasCurrency() }
            ?: amountWithCurrency.find(normalized)
            ?: return ParseResult.Failed("notification amount not found", listOf(id))
        val amountRaw = amountMatch.groups["num"]?.value
            ?: return ParseResult.Failed("notification amount missing", listOf(id))
        val amount = Normalize.amount(amountRaw)
            ?: return ParseResult.Failed("notification amount unparseable: $amountRaw", listOf(id))
        val currency = Normalize.currencyCode(amountMatch.groups["lead"]?.value ?: amountMatch.groups["trail"]?.value)

        val type = when {
            hasIncomeAction(normalized) -> TxType.INCOME
            transferWords.containsMatchIn(normalized) -> TxType.TRANSFER
            hasExpenseAction(normalized) || hasMerchantHint(normalized) -> TxType.EXPENSE
            else -> return ParseResult.Failed("notification action not found", listOf(id))
        }

        val merchant = when (type) {
            TxType.EXPENSE -> cleanParty(toHint.find(normalized)?.groupValues?.get(1)
                ?: atHint.find(normalized)?.groupValues?.get(1)
                ?: byHint.find(normalized)?.groupValues?.get(1)
                ?: fromHint.find(normalized)?.groupValues?.get(1))
                ?: partyBeforeAmount(normalized, amountMatch)
            TxType.INCOME -> null
            TxType.TRANSFER -> null
        }
        val counterparty = when (type) {
            TxType.EXPENSE -> null
            TxType.INCOME -> cleanParty(
                fromHint.find(normalized)?.groupValues?.get(1)
                    ?: byHint.find(normalized)?.groupValues?.get(1)
                    ?: incomingPersonHint.find(normalized)?.groupValues?.get(1),
            )
            TxType.TRANSFER -> cleanParty(toHint.find(normalized)?.groupValues?.get(1)
                ?: fromHint.find(normalized)?.groupValues?.get(1))
        }

        var confidence = 0.45f
        if (amountMatch.hasCurrency()) confidence += 0.15f
        if (merchant != null || counterparty != null) confidence += 0.15f
        if (hasAction) confidence += 0.10f

        return ParseResult.Success(
            type = type,
            amount = Money.of(amount, currency),
            merchant = merchant,
            counterparty = counterparty,
            balanceAfter = null,
            occurredAt = receivedAt,
            confidence = confidence.coerceAtMost(0.85f),
            templateId = id,
        )
    }

    private fun hasAction(body: String): Boolean =
        hasExpenseAction(body) || hasIncomeAction(body) || transferWords.containsMatchIn(body)

    private fun hasExpenseAction(body: String): Boolean =
        expenseWords.containsMatchIn(body) || expensePhrases.containsMatchIn(body)

    private fun hasIncomeAction(body: String): Boolean =
        incomeWords.containsMatchIn(body) || incomePhrases.containsMatchIn(body)

    private fun hasMerchantHint(body: String): Boolean =
        atHint.containsMatchIn(body) || toHint.containsMatchIn(body) || fromHint.containsMatchIn(body)

    private fun MatchResult.hasCurrency(): Boolean =
        groups["lead"]?.value?.isNotBlank() == true || groups["trail"]?.value?.isNotBlank() == true

    private fun partyBeforeAmount(body: String, amountMatch: MatchResult): String? {
        val beforeAmount = body
            .substring(0, amountMatch.range.first)
            .lineSequence()
            .lastOrNull()
            .orEmpty()
            .trim()
        val patterns = listOf(
            Regex("""\b(?:you\s+)?paid\s+(.+)$""", RegexOption.IGNORE_CASE),
            Regex(
                """\b(?:debit\s+card\s+transaction|debit\s+card\s+purchase|card\s+purchase|purchase)\s+(.+)$""",
                RegexOption.IGNORE_CASE,
            ),
            Regex("""\b(?:was\s+charged\s+by|were\s+charged\s+by|charged\s+by)\s+(.+)$""", RegexOption.IGNORE_CASE),
        )
        return patterns
            .asSequence()
            .mapNotNull { it.find(beforeAmount)?.groupValues?.get(1) }
            .mapNotNull(::cleanParty)
            .firstOrNull()
    }

    private fun cleanParty(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw
            .lineSequence()
            .firstOrNull()
            ?.replace(amountWithCurrency, "")
            ?.replace(Regex("""\b(?:for|using|with|via|card|ending|منتهية|البطاقة)\b.*$""", RegexOption.IGNORE_CASE), "")
            ?.trim(' ', '.', ',', '-', '·', ':')
            ?.take(48)
            ?.trim()
        return cleaned?.takeIf { it.isNotBlank() }
    }

    private companion object {
        val BankPackagePattern = Regex(
            """^notification:.*(alrajhi|stcpay|stcbank|d360|barq|alinma|riyad|snb|alahli|anb|albilad|bsf|saib|jazira|wise|revolut|chase|capitalone|mercury|monzo|n26|starling|hsbc|barclays|lloyds|natwest|santander|halifax|usbank|pnc|sofi|walletnfcrel|paisa|samsung\.android\.spay|paypal|venmo|squareup\.cash|americanexpress|amex|bankofamerica|bofa|wellsfargo|citimobile|usaa).*""",
            RegexOption.IGNORE_CASE,
        )
    }
}
