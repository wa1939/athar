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
