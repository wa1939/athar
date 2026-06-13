package com.athar.ingestion.smsparser.alrajhi

import com.athar.core.common.money.Money
import com.athar.core.domain.model.TxType
import com.athar.ingestion.smsparser.BankTemplate
import com.athar.ingestion.smsparser.Normalize
import com.athar.ingestion.smsparser.ParseResult
import com.athar.ingestion.smsparser.SenderMatcher
import kotlinx.datetime.Instant
import java.math.BigDecimal

/**
 * Al Rajhi sender variants. Add new ones here as we observe them.
 */
private val AL_RAJHI_SENDERS = SenderMatcher.AnyOf(
    setOf("AlRajhiBank", "ALRAJHIBANK", "AlRajhi", "ALRAJHI"),
)

/**
 * Purchase: card POS / online (Arabic + English fallback in one template).
 *
 * Sample (Master Brief §4.6):
 *   شراء بمبلغ 200.00 ر.س
 *   البطاقة 1234
 *   من STARBUCKS 1234
 *   الرصيد 4,521.30 ر.س
 *
 *   Purchase SAR 200.00
 *   Card 1234
 *   At STARBUCKS 1234
 *   Balance SAR 4,521.30
 */
class AlRajhiPurchaseTemplate : BankTemplate {
    override val id: String = "al-rajhi-purchase"
    override val senderMatcher: SenderMatcher = AL_RAJHI_SENDERS

    private val amountAr = Regex("""شراء\s+بمبلغ\s+([\d.,]+)\s*ر\.?\s*س""")
    private val amountEn = Regex("""(?:Purchase|purchase)\s+SAR\s+([\d.,]+)""", RegexOption.IGNORE_CASE)
    private val merchantAr = Regex("""(?:من|لدى)\s+([A-Za-z0-9][^\n\r]*?)(?:\s+\d{3,})?\s*$""", RegexOption.MULTILINE)
    private val merchantEn = Regex("""(?:At|From)\s+([A-Za-z0-9][^\n\r]*?)(?:\s+\d{3,})?\s*$""", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
    private val balance = Regex("""(?:الرصيد|Balance)\s+(?:SAR\s+)?([\d.,]+)\s*(?:ر\.?\s*س)?""")
    private val occurredAt = Regex("""(\d{4})/(\d{1,2})/(\d{1,2})\s+(\d{1,2}):(\d{2})""")

    override fun tryParse(body: String, receivedAt: Instant): ParseResult {
        val normalized = Normalize.digits(body)
        val amount = amountAr.find(normalized)?.groupValues?.get(1)
            ?: amountEn.find(normalized)?.groupValues?.get(1)
            ?: return ParseResult.Failed("amount not found", listOf(id))
        val parsedAmount = runCatching { BigDecimal(amount.replace(",", "")) }.getOrNull()
            ?: return ParseResult.Failed("amount unparseable: $amount", listOf(id))

        val merchant = merchantAr.find(normalized)?.groupValues?.get(1)?.trim()
            ?: merchantEn.find(normalized)?.groupValues?.get(1)?.trim()

        val balanceAmount = balance.find(normalized)?.groupValues?.get(1)
            ?.replace(",", "")
            ?.let { runCatching { BigDecimal(it) }.getOrNull() }

        return ParseResult.Success(
            type = TxType.EXPENSE,
            amount = Money.of(parsedAmount),
            merchant = merchant,
            counterparty = null,
            balanceAfter = balanceAmount?.let { Money.of(it) },
            occurredAt = receivedAt,
            confidence = if (merchant != null) 0.92f else 0.6f,
            templateId = id,
        )
    }
}

/**
 * Transfer-out (Arabic + English).
 *   تحويل بمبلغ 1,000.00 ر.س الى احمد محمد ع
 *   Transfer SAR 1,000.00 to AHMED ...
 */
class AlRajhiTransferOutTemplate : BankTemplate {
    override val id: String = "al-rajhi-transfer-out"
    override val senderMatcher: SenderMatcher = AL_RAJHI_SENDERS

    private val amountAr = Regex("""تحويل\s+بمبلغ\s+([\d.,]+)\s*ر\.?\s*س""")
    private val amountEn = Regex("""Transfer\s+SAR\s+([\d.,]+)""", RegexOption.IGNORE_CASE)
    private val counterpartyAr = Regex("""(?:الى|إلى)\s+([^\n\r]+?)(?:\s*$|\n)""", RegexOption.MULTILINE)
    private val counterpartyEn = Regex("""\bto\s+([^\n\r]+?)(?:\s*$|\n)""", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))

    override fun tryParse(body: String, receivedAt: Instant): ParseResult {
        val normalized = Normalize.digits(body)
        val amount = amountAr.find(normalized)?.groupValues?.get(1)
            ?: amountEn.find(normalized)?.groupValues?.get(1)
            ?: return ParseResult.Failed("amount not found", listOf(id))
        val parsedAmount = runCatching { BigDecimal(amount.replace(",", "")) }.getOrNull()
            ?: return ParseResult.Failed("amount unparseable", listOf(id))

        val to = counterpartyAr.find(normalized)?.groupValues?.get(1)?.trim()
            ?: counterpartyEn.find(normalized)?.groupValues?.get(1)?.trim()

        return ParseResult.Success(
            type = TxType.TRANSFER,
            amount = Money.of(parsedAmount),
            merchant = null,
            counterparty = to,
            balanceAfter = null,
            occurredAt = receivedAt,
            confidence = 0.85f,
            templateId = id,
        )
    }
}

/**
 * Deposit / inbound credit.
 *   ايداع 27,700.00 ر.س من ELM CO
 *   Deposit SAR 27,700.00 from ELM CO
 */
class AlRajhiDepositTemplate : BankTemplate {
    override val id: String = "al-rajhi-deposit"
    override val senderMatcher: SenderMatcher = AL_RAJHI_SENDERS

    private val amountAr = Regex("""(?:ايداع|إيداع)\s+([\d.,]+)\s*ر\.?\s*س""")
    private val amountEn = Regex("""Deposit\s+SAR\s+([\d.,]+)""", RegexOption.IGNORE_CASE)
    private val sourceAr = Regex("""من\s+([^\n\r]+?)(?:\s*$|\n)""", RegexOption.MULTILINE)
    private val sourceEn = Regex("""from\s+([^\n\r]+?)(?:\s*$|\n)""", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))

    override fun tryParse(body: String, receivedAt: Instant): ParseResult {
        val normalized = Normalize.digits(body)
        val amount = amountAr.find(normalized)?.groupValues?.get(1)
            ?: amountEn.find(normalized)?.groupValues?.get(1)
            ?: return ParseResult.Failed("amount not found", listOf(id))
        val parsedAmount = runCatching { BigDecimal(amount.replace(",", "")) }.getOrNull()
            ?: return ParseResult.Failed("amount unparseable", listOf(id))

        val source = sourceAr.find(normalized)?.groupValues?.get(1)?.trim()
            ?: sourceEn.find(normalized)?.groupValues?.get(1)?.trim()

        return ParseResult.Success(
            type = TxType.INCOME,
            amount = Money.of(parsedAmount),
            merchant = source,
            counterparty = source,
            balanceAfter = null,
            occurredAt = receivedAt,
            confidence = 0.9f,
            templateId = id,
        )
    }
}

/**
 * Real-world Al Rajhi POS purchase format (observed in user audit log 2026-05-25).
 *
 * Format (English, single-block with embedded fields):
 *   AlRajhiBank
 *   PoS purchase
 *   Card:5916 ;Visa-Samsung Pay
 *   At: ALDREES 8
 *   Amount:201 SAR
 *
 * Also covers the Arabic variant where labels appear as: "نقاط بيع" / "البطاقة" / "لدى" / "مبلغ".
 * This is more specific than [AlRajhiPurchaseTemplate] because it keys on the "Amount:" label
 * rather than the older "Purchase SAR …" header.
 */
class AlRajhiPosPurchaseTemplate : BankTemplate {
    override val id: String = "al-rajhi-pos-purchase"
    override val senderMatcher: SenderMatcher = AL_RAJHI_SENDERS

    private val markerEn = Regex("""(?:PoS|POS)\s+(?:purchase|Purchase)""")
    private val markerAr = Regex(
        """(?:^|\n)\s*(?:شراء|شراء\s+عبر\s+نقاط\s+البيع|شراء\s+دولي|شراء\s+إنترنت|شراء\s+انترنت|نقاط\s+بيع|شراء\s+نقطة\s+بيع)\s*(?:\n|$)""",
        RegexOption.IGNORE_CASE,
    )
    private val amount = Regex("""(?:Amount|المبلغ|مبلغ)\s*[:\s]\s*(?:SAR\s+)?([\d.,]+)(?:\s*SAR|\s*ر\.?\s*س)?""", RegexOption.IGNORE_CASE)
    private val merchant = Regex("""(?:At|لدى|من)\s*[:\s]\s*([^\n\r]+?)(?:\n|$)""", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
    private val card = Regex("""(?:Card|البطاقة)\s*[:\s]\s*(\d{3,4})""", RegexOption.IGNORE_CASE)

    override fun tryParse(body: String, receivedAt: Instant): ParseResult {
        val normalized = Normalize.digits(body)
        if (!markerEn.containsMatchIn(normalized) && !markerAr.containsMatchIn(normalized)) {
            return ParseResult.Failed("not a PoS purchase", listOf(id))
        }
        val amountRaw = amount.find(normalized)?.groupValues?.get(1)
            ?: return ParseResult.Failed("amount not found", listOf(id))
        val parsedAmount = runCatching { BigDecimal(amountRaw.replace(",", "")) }.getOrNull()
            ?: return ParseResult.Failed("amount unparseable: $amountRaw", listOf(id))

        val merchantName = merchant.find(normalized)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
        val cardLast4 = card.find(normalized)?.groupValues?.get(1)

        return ParseResult.Success(
            type = TxType.EXPENSE,
            amount = Money.of(parsedAmount),
            merchant = merchantName,
            counterparty = cardLast4?.let { "Card $it" },
            balanceAfter = null,
            occurredAt = receivedAt,
            confidence = if (merchantName != null) 0.93f else 0.7f,
            templateId = id,
        )
    }
}

/**
 * Real-world Al Rajhi Internal Transfer format (observed 2026-05-25).
 *
 * Format:
 *   AlRajhiBank  Debit Internal Transfer
 *   From:0930
 *   Amount:SR 1350
 *   To:RAGHAD ALGHAMDI
 *
 * Also handles Credit Internal Transfer (incoming) and Outgoing Wire / Domestic Transfer.
 */
class AlRajhiInternalTransferTemplate : BankTemplate {
    override val id: String = "al-rajhi-internal-transfer"
    override val senderMatcher: SenderMatcher = AL_RAJHI_SENDERS

    private val debitMarker = Regex(
        """(?:Debit|Outgoing).{0,40}(?:Transfer|Wire)|تحويل\s+صادر|حوالة\s+(?:صادرة|داخلية\s+صادرة|محلية\s+صادرة)|حوالة\s+(?:داخلية|محلية)(?!\s+واردة)""",
        RegexOption.IGNORE_CASE,
    )
    private val creditMarker = Regex(
        """(?:Credit|Incoming).{0,40}(?:Transfer|Wire)|تحويل\s+وارد|حوالة\s+(?:واردة|داخلية\s+واردة|محلية\s+واردة)""",
        RegexOption.IGNORE_CASE,
    )
    private val amount = Regex("""(?:Amount|المبلغ|مبلغ)\s*[:\s]\s*(?:SR\s+|SAR\s+)?([\d.,]+)(?:\s*SAR|\s*ر\.?\s*س)?""", RegexOption.IGNORE_CASE)
    private val toField = Regex("""(?:To|الى|إلى|لـ)\s*[:\s]\s*([^\n\r]+?)(?:\n|$)""", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
    private val fromField = Regex("""(?:From|من)\s*[:\s]\s*([^\n\r]+?)(?:\n|$)""", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))

    override fun tryParse(body: String, receivedAt: Instant): ParseResult {
        val normalized = Normalize.digits(body)
        val isDebit = debitMarker.containsMatchIn(normalized)
        val isCredit = creditMarker.containsMatchIn(normalized)
        if (!isDebit && !isCredit) {
            return ParseResult.Failed("not an internal transfer", listOf(id))
        }
        val amountRaw = amount.find(normalized)?.groupValues?.get(1)
            ?: return ParseResult.Failed("amount not found", listOf(id))
        val parsedAmount = runCatching { BigDecimal(amountRaw.replace(",", "")) }.getOrNull()
            ?: return ParseResult.Failed("amount unparseable: $amountRaw", listOf(id))

        val to = toField.find(normalized)?.groupValues?.get(1)?.trim()
        val from = fromField.find(normalized)?.groupValues?.get(1)?.trim()
        val counterparty = if (isDebit) to else from

        return ParseResult.Success(
            type = if (isDebit) TxType.TRANSFER else TxType.INCOME,
            amount = Money.of(parsedAmount),
            merchant = null,
            counterparty = counterparty,
            balanceAfter = null,
            occurredAt = receivedAt,
            confidence = if (counterparty != null) 0.9f else 0.7f,
            templateId = id,
        )
    }
}

/**
 * Generic Al Rajhi fallback: any message that has a recognizable Amount + (SAR|ر.س)
 * AND comes from the bank sender. Used as a last-resort capture so legitimate but
 * unrecognized SMS at least show up in the pending tray with a low confidence score,
 * rather than getting filed as "failed" and lost.
 */
class AlRajhiGenericAmountTemplate : BankTemplate {
    override val id: String = "al-rajhi-generic-amount"
    override val senderMatcher: SenderMatcher = AL_RAJHI_SENDERS

    private val amount = Regex("""(?:Amount|المبلغ|مبلغ)\s*[:\s]\s*(?:SAR\s+|SR\s+)?([\d.,]+)(?:\s*SAR|\s*SR|\s*ر\.?\s*س)?""", RegexOption.IGNORE_CASE)
    private val isIncome = Regex("""(?:Credit|Deposit|ايداع|إيداع|وارد)""", RegexOption.IGNORE_CASE)
    private val isTransfer = Regex("""(?:Transfer|Wire|تحويل|حوالة)""", RegexOption.IGNORE_CASE)
    private val party = Regex(
        """(?:At|Merchant|Biller|Service|لدى|الجهة|الخدمة|مكان\s+السحب|مفوتر)\s*[:\s]\s*([^\n\r]+?)(?:\n|$)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE),
    )
    private val toField = Regex("""(?:To|الى|إلى|لـ)\s*[:\s]\s*([^\n\r]+?)(?:\n|$)""", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
    private val fromField = Regex("""(?:From|من)\s*[:\s]\s*([^\n\r]+?)(?:\n|$)""", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))

    override fun tryParse(body: String, receivedAt: Instant): ParseResult {
        val normalized = Normalize.digits(body)
        val amountRaw = amount.find(normalized)?.groupValues?.get(1)
            ?: return ParseResult.Failed("no Amount: field", listOf(id))
        val parsedAmount = runCatching { BigDecimal(amountRaw.replace(",", "")) }.getOrNull()
            ?: return ParseResult.Failed("amount unparseable", listOf(id))

        val type = when {
            isIncome.containsMatchIn(normalized) -> TxType.INCOME
            isTransfer.containsMatchIn(normalized) -> TxType.TRANSFER
            else -> TxType.EXPENSE
        }
        val merchant = party.find(normalized)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
        val counterparty = when (type) {
            TxType.INCOME -> fromField.find(normalized)?.groupValues?.get(1)?.trim()
            TxType.TRANSFER -> toField.find(normalized)?.groupValues?.get(1)?.trim()
            TxType.EXPENSE -> merchant
        }
        return ParseResult.Success(
            type = type,
            amount = Money.of(parsedAmount),
            merchant = merchant.takeIf { type == TxType.EXPENSE },
            counterparty = counterparty.takeUnless { type == TxType.EXPENSE },
            balanceAfter = null,
            occurredAt = receivedAt,
            confidence = if (merchant != null || counterparty != null) 0.6f else 0.5f,  // low — user must confirm
            templateId = id,
        )
    }
}

/**
 * Balance alert / non-financial messages. Always [ParseResult.Ignored].
 */
class AlRajhiBalanceAlertTemplate : BankTemplate {
    override val id: String = "al-rajhi-balance-alert"
    override val senderMatcher: SenderMatcher = AL_RAJHI_SENDERS

    private val patterns = listOf(
        Regex("""رصيدك|الرصيد المتاح""", RegexOption.IGNORE_CASE),
        Regex("""your balance|available balance|otp|verification code""", RegexOption.IGNORE_CASE),
    )

    override fun tryParse(body: String, receivedAt: Instant): ParseResult =
        if (patterns.any { it.containsMatchIn(body) } && !looksLikeTransaction(body))
            ParseResult.Ignored
        else
            ParseResult.Failed("not a balance alert", listOf(id))

    private fun looksLikeTransaction(body: String): Boolean =
        Regex("""شراء|تحويل|ايداع|purchase|transfer|deposit""", RegexOption.IGNORE_CASE)
            .containsMatchIn(body)
}
