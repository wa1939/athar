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
        """(?:(?<lead>CA\$|C\$|AU\$|A\$|[\$€£﷼₹¥₺]|SAR|SR|AED|USD|EUR|GBP|CAD|AUD|CHF|INR|PKR|TRY|EGP|KWD|QAR|BHD|OMR|JOD|JPY|CNY|HKD|SGD|SEK|NOK|DKK|ZAR|BRL|MXN|THB|IDR|MYR|PHP|VND|KRW)\s*)?(?<num>${Normalize.LOCALIZED_AMOUNT_PATTERN})(?:\s*(?<trail>CA\$|C\$|AU\$|A\$|[\$€£﷼₹¥₺]|SAR|SR|AED|USD|EUR|GBP|CAD|AUD|CHF|INR|PKR|TRY|EGP|KWD|QAR|BHD|OMR|JOD|JPY|CNY|HKD|SGD|SEK|NOK|DKK|ZAR|BRL|MXN|THB|IDR|MYR|PHP|VND|KRW|ر\.?\s*س|د\.?\s*إ))?""",
        RegexOption.IGNORE_CASE,
    )
    private val expenseWords = Regex(
        """(?:\b(?:spent|purchase|paid|payment|debit|debited|charged|card\s+purchase|withdrawal|pos)\b|خصم|شراء|دفع|سحب)""",
        RegexOption.IGNORE_CASE,
    )
    private val expensePhrases = Regex(
        """\b(?:card\s+(?:ending\s+\d{2,4}\s+)?(?:was\s+)?used|card\s+payment|card\s+transaction|card\s+charge|debit\s+card\s+transaction|direct\s+debit|payment\s+to|transaction\s+at|transaction\s+with|new\s+(?:card\s+)?transaction|purchase\s+from|charged\s+(?:your\s+card|you))\b""",
        RegexOption.IGNORE_CASE,
    )
    private val incomeWords = Regex(
        """\b(?:received|deposit|deposited|credited|refund|salary|incoming|top\s*up|إيداع|ايداع|وارد|استلام|استلمت|راتب)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val incomePhrases = Regex(
        """\b(?:paid\s+you|sent\s+you|got\s+paid|was\s+paid|were\s+paid|payment\s+from|direct\s+deposit|direct\s+credit|ach\s+credit|credit\s+(?:of|from)|money\s+received|money\s+added|cash\s+in)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val transferWords = Regex(
        """\b(?:sent|transfer|transferred|outgoing|remit|تحويل|حوالة|إرسال|ارسال)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val statementWords = Regex(
        """\b(?:statement\s+(?:is\s+)?(?:ready|available)|minimum\s+payment|payment\s+due|due\s+date|bill\s+(?:is\s+)?due|invoice\s+(?:is\s+)?due)\b""",
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
    private val scheduledWords = Regex(
        """\b(?:scheduled\s+(?:payment|transfer|autopay|bill)|payment\s+(?:is\s+)?scheduled|transfer\s+(?:is\s+)?scheduled|autopay\s+(?:is\s+)?scheduled|upcoming\s+payment)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val securityWords = Regex(
        """\b(?:otp|one[-\s]?time|verification|security\s+code|login|password|do\s+not\s+share|رمز|تحقق|الدخول|كلمة\s+المرور)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val securityCodeAuthorizationWords = Regex(
        """(?:\b(?:otp|one[-\s]?time\s+(?:password|code)|verification\s+code|security\s+code|auth(?:entication|orization)?\s+code|passcode|do\s+not\s+share|use\s+(?:this\s+)?code|code\s+to\s+(?:authorize|confirm|verify))\b|رمز\s+(?:التحقق|التأكيد|الدخول|الأمان|الامان)|لا\s+تشارك|لا\s+تشاركه|لا\s+تفصح|استخدم\s+الرمز|كود\s+(?:التحقق|التأكيد))""",
        RegexOption.IGNORE_CASE,
    )
    private val marketingWords = Regex(
        """\b(?:offer|promo|cashback|points|reward|earn|win|discount|عرض|خصم|نقاط|مكافأة|اكسب|اربح)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val marketingOnlyWords = Regex(
        """(?:\b(?:offer|promo|cashback|points|reward|earn|win|discount|save|coupon|deal)\b|عرض|عروض|خصومات|تخفيض|تخفيضات|كوبون|قسيمة|وفر|اكسب|اربح|نقاط|مكافأة|استرداد\s+نقدي|كاش\s*باك|خصم\s*(?:حتى\s*)?\d+(?:[.,]\d+)?\s*[%٪])""",
        RegexOption.IGNORE_CASE,
    )
    private val rewardWords = Regex(
        """(?:\b(?:cashback|cash\s+back|points?|reward|rewards?|earned|earn|win|save)\b|نقاط|مكافأة|مكافآت|استرداد\s+نقدي|كاش\s*باك|اكسب|اربح|وفر)""",
        RegexOption.IGNORE_CASE,
    )
    private val rewardAmountContext = Regex(
        """(?:\b(?:cashback|cash\s+back|points?|reward|rewards?|earned|earn|win|save)\b|نقاط|مكافأة|مكافآت|استرداد\s+نقدي|كاش\s*باك|اكسب|اربح|وفر)""",
        RegexOption.IGNORE_CASE,
    )
    private val postedTransactionEvidence = Regex(
        """(?:\b(?:you\s+(?:spent|paid|received|sent)|card\s+(?:ending\s+\d{2,4}\s+)?(?:was\s+)?used|card\s+(?:purchase|payment|transaction|charge)|debit(?:ed)?|charged|credited|deposit(?:ed)?|direct\s+debit|payment\s+(?:from|to)|money\s+(?:received|added)|transfer\s+(?:sent|received)|sent\s+you|paid\s+you|got\s+paid)\b|تم\s+خصم|بعد\s+خصم|تم\s+دفع|تمت\s+عملية|عملية\s+(?:شراء|دفع|سحب)|دفع\s+فاتورة|تم\s+سحب|تم\s+إيداع|تم\s+ايداع|وارد|استلمت|تحويل|حوالة)""",
        RegexOption.IGNORE_CASE,
    )
    private val balanceWords = Regex(
        """\b(?:balance|available|رصيد|المتاح)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val balanceAmountContext = Regex(
        """(?:\b(?:balance|available|remaining\s+balance|current\s+balance)\b|رصيد|الرصيد|المتاح|الرصيد\s+المتبقي)""",
        RegexOption.IGNORE_CASE,
    )
    private val trailingCurrencyContext = Regex(
        """^\s*(?:CA\$|C\$|AU\$|A\$|[\$€£﷼₹¥₺]|SAR|SR|AED|USD|EUR|GBP|CAD|AUD|CHF|INR|PKR|TRY|EGP|KWD|QAR|BHD|OMR|JOD|JPY|CNY|HKD|SGD|SEK|NOK|DKK|ZAR|BRL|MXN|THB|IDR|MYR|PHP|VND|KRW|ر\.?\s*س|د\.?\s*إ)""",
        RegexOption.IGNORE_CASE,
    )
    private val merchantLabelHint = Regex(
        """(?:\bmerchant\b|\bstore\b|\bpayee\b|\bbiller\b|\bservice\s+provider\b|التاجر|المتجر|المفوتر)\s*[:\-·]\s*([A-Za-z\u0600-\u06FF][^\n\r]+)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE),
    )
    private val senderLabelHint = Regex(
        """(?:\bsender\b|\bpayer\b|\bremitter\b|المرسل|الدافع|المحول)\s*[:\-·]\s*([A-Za-z\u0600-\u06FF][^\n\r]+)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE),
    )
    private val recipientLabelHint = Regex(
        """(?:\brecipient\b|\breceiver\b|\bbeneficiary\b|\bpayee\b|المستفيد|المستلم|المحول\s+له)\s*[:\-·]\s*([A-Za-z\u0600-\u06FF][^\n\r]+)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE),
    )
    private val atHint = Regex(
        """(?:\bat\b|\bwith\b|\bon\b|لدى|عند|في)\s*[:\-·]?\s+([A-Za-z\u0600-\u06FF][^\n\r]+)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE),
    )
    private val forHint = Regex(
        """(?:\bfor\b|مقابل|عن)\s*[:\-·]?\s+([A-Za-z\u0600-\u06FF][^\n\r]+)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE),
    )
    private val toHint = Regex(
        """(?:\bto\b|إلى|الى|لـ)\s*[:\-·]?\s+([A-Za-z\u0600-\u06FF][^\n\r]+)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE),
    )
    private val fromHint = Regex(
        """(?:\bfrom\b|من)\s*[:\-·]?\s+([A-Za-z\u0600-\u06FF][^\n\r]+)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE),
    )
    private val byHint = Regex(
        """(?:\bby\b)\s*[:\-·]?\s+([A-Za-z\u0600-\u06FF][^\n\r]+)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE),
    )
    private val incomingPersonHint = Regex(
        """(?m)^([A-Za-z\u0600-\u06FF][^\n\r]{1,64}?)\s+(?:paid|sent)\s+you\b""",
        RegexOption.IGNORE_CASE,
    )

    override fun tryParse(body: String, receivedAt: Instant): ParseResult {
        val normalized = Normalize.digits(body)
        val hasAction = hasAction(normalized)

        if (securityCodeAuthorizationWords.containsMatchIn(normalized)) return ParseResult.Ignored
        if (isRewardOnlyNotification(normalized)) return ParseResult.Ignored
        if (isMarketingOnlyPromotion(normalized)) return ParseResult.Ignored
        if (declinedWords.containsMatchIn(normalized)) return ParseResult.Ignored
        if (statementWords.containsMatchIn(normalized)) return ParseResult.Ignored
        if (scheduledWords.containsMatchIn(normalized)) return ParseResult.Ignored
        if (limitWords.containsMatchIn(normalized) && !hasExpenseAction(normalized) && !hasIncomeAction(normalized)) {
            return ParseResult.Ignored
        }
        if (requestWords.containsMatchIn(normalized) && !hasAction) return ParseResult.Ignored
        if (securityWords.containsMatchIn(normalized) && !hasAction) return ParseResult.Ignored
        if (marketingWords.containsMatchIn(normalized) && !hasAction) return ParseResult.Ignored
        if (balanceWords.containsMatchIn(normalized) && !hasAction && !hasMerchantHint(normalized)) {
            return ParseResult.Ignored
        }

        val amountMatch = selectTransactionAmount(normalized)
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
            TxType.EXPENSE -> cleanParty(merchantLabelHint.find(normalized)?.groupValues?.get(1)
                ?: toHint.find(normalized)?.groupValues?.get(1)
                ?: atHint.find(normalized)?.groupValues?.get(1)
                ?: byHint.find(normalized)?.groupValues?.get(1)
                ?: forHint.find(normalized)?.groupValues?.get(1)
                ?: fromHint.find(normalized)?.groupValues?.get(1))
                ?: partyBeforeAmount(normalized, amountMatch)
            TxType.INCOME -> null
            TxType.TRANSFER -> null
        }
        val counterparty = when (type) {
            TxType.EXPENSE -> null
            TxType.INCOME -> cleanParty(
                senderLabelHint.find(normalized)?.groupValues?.get(1)
                    ?: fromHint.find(normalized)?.groupValues?.get(1)
                    ?: byHint.find(normalized)?.groupValues?.get(1)
                    ?: incomingPersonHint.find(normalized)?.groupValues?.get(1),
            )
            TxType.TRANSFER -> cleanParty(recipientLabelHint.find(normalized)?.groupValues?.get(1)
                ?: toHint.find(normalized)?.groupValues?.get(1)
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
        merchantLabelHint.containsMatchIn(body) ||
            atHint.containsMatchIn(body) ||
            toHint.containsMatchIn(body) ||
            fromHint.containsMatchIn(body) ||
            forHint.containsMatchIn(body)

    private fun isMarketingOnlyPromotion(body: String): Boolean =
        marketingOnlyWords.containsMatchIn(body) && !postedTransactionEvidence.containsMatchIn(body)

    private fun isRewardOnlyNotification(body: String): Boolean {
        if (!rewardWords.containsMatchIn(body) || hasIncomeAction(body)) return false
        val currencyAmounts = amountWithCurrency.findAll(body)
            .filter { it.hasCurrencyMarker(body) }
            .toList()
        return currencyAmounts.isNotEmpty() && currencyAmounts.all { it.isRewardAmount(body) }
    }

    private fun selectTransactionAmount(body: String): MatchResult? {
        val matches = amountWithCurrency.findAll(body).toList()
        if (matches.isEmpty()) return null
        val currencyMatches = matches.filter { it.hasCurrencyMarker(body) }
        val candidates = currencyMatches.ifEmpty { matches }
            .let { amountCandidates -> amountCandidates.filterNot { it.isRewardAmount(body) }.ifEmpty { amountCandidates } }
        val actionStart = firstActionIndex(body)
        val afterAction = actionStart?.let { index -> candidates.filter { it.range.first >= index } }.orEmpty()
        afterAction.firstOrNull { !it.isBalanceAmount(body) }?.let { return it }
        afterAction.firstOrNull()?.let { return it }
        return candidates.firstOrNull { !it.isBalanceAmount(body) } ?: candidates.firstOrNull()
    }

    private fun firstActionIndex(body: String): Int? = listOfNotNull(
        expenseWords.find(body)?.range?.first,
        expensePhrases.find(body)?.range?.first,
        incomeWords.find(body)?.range?.first,
        incomePhrases.find(body)?.range?.first,
        transferWords.find(body)?.range?.first,
    ).minOrNull()

    private fun MatchResult.isBalanceAmount(body: String): Boolean {
        val prefixStart = (range.first - AmountPrefixWindow).coerceAtLeast(0)
        val suffixEnd = (range.last + AmountSuffixWindow + 1).coerceAtMost(body.length)
        val prefix = body.substring(prefixStart, range.first)
        val suffix = body.substring(range.last + 1, suffixEnd)
        return hasBalanceAmountContext(prefix) || hasBalanceAmountContext(suffix)
    }

    private fun MatchResult.isRewardAmount(body: String): Boolean {
        val prefixStart = (range.first - RewardPrefixWindow).coerceAtLeast(0)
        val suffixEnd = (range.last + RewardSuffixWindow + 1).coerceAtMost(body.length)
        val prefix = body.substring(prefixStart, range.first)
        val suffix = body.substring(range.last + 1, suffixEnd)
        return rewardAmountContext.containsMatchIn(prefix) || rewardAmountContext.containsMatchIn(suffix)
    }

    private fun hasBalanceAmountContext(text: String): Boolean {
        return balanceAmountContext.containsMatchIn(text) ||
            ArabicBalanceTerms.any { text.contains(it) }
    }

    private fun MatchResult.hasCurrency(): Boolean =
        groups["lead"]?.value?.isNotBlank() == true || groups["trail"]?.value?.isNotBlank() == true

    private fun MatchResult.hasCurrencyMarker(body: String): Boolean {
        if (hasCurrency()) return true
        val suffixEnd = (range.last + CurrencySuffixWindow + 1).coerceAtMost(body.length)
        val suffix = body.substring(range.last + 1, suffixEnd)
        return trailingCurrencyContext.containsMatchIn(suffix)
    }

    private fun partyBeforeAmount(body: String, amountMatch: MatchResult): String? {
        val beforeAmount = body
            .substring(0, amountMatch.range.first)
            .lineSequence()
            .lastOrNull()
            .orEmpty()
            .trim()
        val patterns = listOf(
            Regex("""\b(?:new\s+(?:card\s+)?transaction|transaction)\s*[:\-]\s*(.+)$""", RegexOption.IGNORE_CASE),
            Regex("""\b(?:you\s+)?paid\s+(.+)$""", RegexOption.IGNORE_CASE),
            Regex("""^(?!your\s+card\b)(.+?)\s+charged(?:\s+(?:your\s+card|you|card))?$""", RegexOption.IGNORE_CASE),
            Regex(
                """\b(?:debit\s+card\s+transaction|debit\s+card\s+purchase|card\s+transaction|card\s+charge|card\s+purchase|purchase)\s+(.+)$""",
                RegexOption.IGNORE_CASE,
            ),
            Regex("""\bpurchase\s+from\s+(.+)$""", RegexOption.IGNORE_CASE),
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
            ?.replace(Regex("""\s+\band\s+earned\b.*$""", RegexOption.IGNORE_CASE), "")
            ?.replace(Regex("""\s+\b(?:confirmed|successful|completed|approved|posted)\b\.?$""", RegexOption.IGNORE_CASE), "")
            ?.replace(Regex("""\b(?:for|using|with|via|card|ending|منتهية|البطاقة)\b.*$""", RegexOption.IGNORE_CASE), "")
            ?.trim(' ', '.', ',', '-', '·', ':')
            ?.take(48)
            ?.trim()
        return cleaned?.takeIf { it.isNotBlank() }
    }

    private companion object {
        private const val AmountPrefixWindow = 32
        private const val AmountSuffixWindow = 24
        private const val RewardPrefixWindow = 24
        private const val RewardSuffixWindow = 16
        private const val CurrencySuffixWindow = 12
        private val ArabicBalanceTerms = listOf("رصيد", "الرصيد", "المتاح", "الرصيد المتبقي")

        val BankPackagePattern = Regex(
            """^notification:.*(alrajhi|stcpay|stcbank|d360|barq|alinma|riyad|snb|alahli|anb|albilad|bsf|saib|jazira|emiratesnbd|adcb|mashreq|bankfab|qnb|boubyan|kfh|bankmuscat|wise|transferwise|revolut|chase|capitalone|mercury|monzo|n26|starling|hsbc|barclays|lloyds|natwest|santander|halifax|usbank|pnc|sofi|walletnfcrel|paisa|samsung\.android\.spay|paypal|venmo|squareup\.cash|americanexpress|amex|bankofamerica|bofa|wellsfargo|citimobile|usaa|discoverfinancial|truist|citizensbank|payoneer|remitly|westernunion|bunq|nubank|bbva|scotiabank|tdbank|rbc|commbank|westpac|nab\.mobile|anz\.android|dbsmbanking|ocbc|uob|maybank|cimb|hdfcbank|icici|axisbank|kotak).*""",
            RegexOption.IGNORE_CASE,
        )
    }
}
