package com.athar.ingestion.smsparser.genericbank

import com.athar.core.common.money.Money
import com.athar.core.domain.model.TxType
import com.athar.ingestion.smsparser.BankTemplate
import com.athar.ingestion.smsparser.Normalize
import com.athar.ingestion.smsparser.ParseResult
import com.athar.ingestion.smsparser.SenderMatcher
import kotlinx.datetime.Instant

/**
 * Shared base template for Saudi banks/wallets that use a structured
 * label-based SMS format similar to Al Rajhi's. Covers most of:
 *
 *   - Alinma Bank (`Alinma`, `AlInmaBank`)
 *   - D360 (`D360`, `D360Bank`)
 *   - Barq (`Barq`, `BarqWallet`)
 *   - Riyad Bank, NCB/SNB, ANB (added by sender matcher subclass)
 *
 * The shape we recognize:
 *   <Bank>
 *   <Action: purchase | transfer | withdrawal | deposit | credit>
 *   Amount: <value> [SAR|SR|ر.س]
 *   At|From|To: <merchant or counterparty>
 *   Card|Acc: <last4>
 *
 * Each subclass below overrides only the senderMatcher.
 *
 * REAL-WORLD SAMPLES NEEDED: please paste a real SMS body from each of these
 * banks into `corpus/sms/<bank>.txt` so we can tighten the patterns against
 * the actual wire format.
 */
abstract class StructuredBankTemplate(
    final override val id: String,
    final override val senderMatcher: SenderMatcher,
) : BankTemplate {

    private val amount = Regex(
        """(?:Amount|المبلغ|مبلغ(?:\s+العملية)?|بمبلغ|قيمة\s+العملية)\s*[:\s]\s*(?:SAR\s+|SR\s+)?([\d., ]+)(?:\s*SAR|\s*SR|\s*ر\.?\s*س)?""",
        RegexOption.IGNORE_CASE,
    )
    private val purchaseMarker = Regex(
        """(?:purchase|POS|PoS|نقاط\s+بيع|شراء|خصم|سحب|مدين)""",
        RegexOption.IGNORE_CASE,
    )
    private val transferOutMarker = Regex(
        """(?:Debit|Outgoing|outward|حوالة\s+صادرة|حوالة\s+مالية\s+صادرة|حوالة\s+(?:داخلية|محلية)(?!\s+واردة)|تحويل\s+صادر|تحويل\s+خارج|إرسال|صادرة\s+(?:داخلية|محلية))""",
        RegexOption.IGNORE_CASE,
    )
    private val incomeMarker = Regex(
        """(?:Credit|Deposit|incoming|حوالة\s+واردة|ايداع|إيداع|وارد|استلام)""",
        RegexOption.IGNORE_CASE,
    )
    private val merchantAt = Regex(
        """(?:At|لدى|من\s+متجر)\s*[:\s]\s*([^\n\r]+?)(?:\n|$)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE),
    )
    private val toField = Regex(
        """(?:To|الى|إلى|لـ)\s*[:\s]\s*([^\n\r]+?)(?:\n|$)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE),
    )
    private val fromField = Regex(
        """(?:From|من|اسم\s+المرسل)\s*[:\s]\s*([^\n\r]+?)(?:\n|$)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE),
    )
    private val card = Regex(
        """(?:Card|البطاقة|بطاقة)\s*[:\s]\s*(\d{3,4})""",
        RegexOption.IGNORE_CASE,
    )
    private val ignore = Regex(
        """(?:OTP|verification|رمز\s+التحقق|رصيدك|available\s+balance|الرصيد\s+المتاح|offer|عرض)""",
        RegexOption.IGNORE_CASE,
    )

    override fun tryParse(body: String, receivedAt: Instant): ParseResult {
        val normalized = Normalize.digits(body)

        // Short-circuit OTP/marketing/balance-only messages.
        if (ignore.containsMatchIn(normalized) &&
            !purchaseMarker.containsMatchIn(normalized) &&
            !transferOutMarker.containsMatchIn(normalized) &&
            !incomeMarker.containsMatchIn(normalized)
        ) {
            return ParseResult.Ignored
        }

        val amountRaw = amount.find(normalized)?.groupValues?.get(1)
            ?: return ParseResult.Failed("amount label not found", listOf(id))
        val parsedAmount = Normalize.amount(amountRaw)
            ?: return ParseResult.Failed("amount unparseable: $amountRaw", listOf(id))

        val isPurchase = purchaseMarker.containsMatchIn(normalized)
        val type = when {
            incomeMarker.containsMatchIn(normalized) -> TxType.INCOME
            transferOutMarker.containsMatchIn(normalized) -> TxType.TRANSFER
            isPurchase -> TxType.EXPENSE
            else -> TxType.EXPENSE  // default — user can correct on confirm
        }

        val to = toField.find(normalized)?.groupValues?.get(1)?.trim()
        val from = fromField.find(normalized)?.groupValues?.get(1)?.trim()
        val merchant = merchantAt.find(normalized)?.groupValues?.get(1)?.trim()
            ?: from.takeIf { type == TxType.EXPENSE && isPurchase && !it.isNullOrBlank() }
        val counterparty = when (type) {
            TxType.INCOME -> from
            TxType.TRANSFER -> to
            else -> merchant ?: to
        }
        val cardLast4 = card.find(normalized)?.groupValues?.get(1)

        return ParseResult.Success(
            type = type,
            amount = Money.of(parsedAmount),
            merchant = merchant,
            counterparty = counterparty ?: cardLast4?.let { "Card $it" },
            balanceAfter = null,
            occurredAt = receivedAt,
            confidence = when {
                merchant != null -> 0.85f
                counterparty != null -> 0.8f
                else -> 0.55f
            },
            templateId = id,
        )
    }
}

class AlinmaTemplate : StructuredBankTemplate(
    id = "alinma-structured",
    senderMatcher = SenderMatcher.AnyOf(
        setOf("Alinma", "AlinmaBank", "ALINMA", "ALINMABANK", "AlInma", "Alinma Bank"),
    ),
)

class D360Template : StructuredBankTemplate(
    id = "d360-structured",
    senderMatcher = SenderMatcher.AnyOf(
        setOf("D360", "D360Bank", "D-360", "Dahab", "D360BANK"),
    ),
)

class BarqTemplate : StructuredBankTemplate(
    id = "barq-structured",
    senderMatcher = SenderMatcher.AnyOf(
        setOf("Barq", "BarqWallet", "BARQ", "Barq.sa", "برق"),
    ),
)

class RiyadBankTemplate : StructuredBankTemplate(
    id = "riyad-bank-structured",
    senderMatcher = SenderMatcher.AnyOf(
        setOf("RiyadBank", "RIYADBANK", "Riyad Bank", "بنك الرياض"),
    ),
)

class SnbTemplate : StructuredBankTemplate(
    id = "snb-structured",
    senderMatcher = SenderMatcher.AnyOf(
        setOf("SNB", "SNB-AlAhli", "SNB Bank", "AlAhli", "AlAhliBank", "AlahliBank", "البنك الأهلي السعودي"),
    ),
)

class AnbTemplate : StructuredBankTemplate(
    id = "anb-structured",
    senderMatcher = SenderMatcher.AnyOf(
        setOf("ANB", "ANBBank", "Arab National Bank", "البنك العربي الوطني"),
    ),
)

class AlJaziraTemplate : StructuredBankTemplate(
    id = "aljazira-structured",
    senderMatcher = SenderMatcher.AnyOf(
        setOf("AlJazira", "AlJaziraSMS", "Jazira Bank", "JaziraBank", "BankAlJazira", "Bank AlJazira", "BAJ"),
    ),
)

class UrpayTemplate : StructuredBankTemplate(
    id = "urpay-structured",
    senderMatcher = SenderMatcher.AnyOf(
        setOf("urpay", "Urpay", "URPAY"),
    ),
)
