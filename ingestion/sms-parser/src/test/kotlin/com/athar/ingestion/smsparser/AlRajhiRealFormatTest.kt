package com.athar.ingestion.smsparser

import com.athar.core.domain.model.IngestSource
import com.athar.core.domain.model.RawIngestEvent
import com.athar.core.domain.model.TxType
import com.athar.ingestion.smsparser.alrajhi.AlRajhiBalanceAlertTemplate
import com.athar.ingestion.smsparser.alrajhi.AlRajhiDepositTemplate
import com.athar.ingestion.smsparser.alrajhi.AlRajhiGenericAmountTemplate
import com.athar.ingestion.smsparser.alrajhi.AlRajhiInternalTransferTemplate
import com.athar.ingestion.smsparser.alrajhi.AlRajhiPosPurchaseTemplate
import com.athar.ingestion.smsparser.alrajhi.AlRajhiPurchaseTemplate
import com.athar.ingestion.smsparser.alrajhi.AlRajhiTransferOutTemplate
import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/**
 * Verifies the parser against the EXACT format observed in the user's
 * SMS audit log on 2026-05-25 — the format that was failing in v0.1.0-beta.1.
 *
 * The audit showed 552 failed parses, all with "no template matched (4 attempts)".
 * These tests pin the new templates (PoS purchase, Internal Transfer) so the
 * regression doesn't reappear.
 */
class AlRajhiRealFormatTest {

    private val parser = TemplateBasedSmsParser(
        listOf(
            AlRajhiBalanceAlertTemplate(),
            AlRajhiPosPurchaseTemplate(),
            AlRajhiInternalTransferTemplate(),
            AlRajhiPurchaseTemplate(),
            AlRajhiTransferOutTemplate(),
            AlRajhiDepositTemplate(),
            AlRajhiGenericAmountTemplate(),
        ),
    )

    private val sampleTime = Instant.parse("2026-05-25T12:00:00Z")

    private fun event(body: String) = RawIngestEvent(
        id = "id-1",
        source = IngestSource.SMS,
        sender = "AlRajhiBank",
        body = body,
        receivedAt = sampleTime,
        rawId = "raw-1",
    )

    @Test
    fun `parses real PoS purchase format`() {
        val body = """
            PoS purchase
            Card:5916 ;Visa-Samsung Pay
            At: ALDREES 8
            Amount:201 SAR
        """.trimIndent()

        val result = parser.parse(event(body))

        assertThat(result).isInstanceOf(ParseResult.Success::class.java)
        val success = result as ParseResult.Success
        assertThat(success.type).isEqualTo(TxType.EXPENSE)
        assertThat(success.amount.amount).isEqualTo(BigDecimal("201"))
        assertThat(success.merchant).isEqualTo("ALDREES 8")
        assertThat(success.templateId).isEqualTo("al-rajhi-pos-purchase")
    }

    @Test
    fun `parses real Debit Internal Transfer format`() {
        val body = """
            Debit Internal Transfer
            From:0930
            Amount:SR 1350
            To:RAGHAD ALGHAMDI
        """.trimIndent()

        val result = parser.parse(event(body))

        assertThat(result).isInstanceOf(ParseResult.Success::class.java)
        val success = result as ParseResult.Success
        assertThat(success.type).isEqualTo(TxType.TRANSFER)
        assertThat(success.amount.amount).isEqualTo(BigDecimal("1350"))
        assertThat(success.counterparty).isEqualTo("RAGHAD ALGHAMDI")
        assertThat(success.templateId).isEqualTo("al-rajhi-internal-transfer")
    }

    @Test
    fun `parses Credit Internal Transfer as income`() {
        val body = """
            Credit Internal Transfer
            From:RAGHAD ALGHAMDI
            Amount:SR 500
            To:0930
        """.trimIndent()

        val result = parser.parse(event(body)) as ParseResult.Success
        assertThat(result.type).isEqualTo(TxType.INCOME)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("500"))
        assertThat(result.counterparty).isEqualTo("RAGHAD ALGHAMDI")
    }

    @Test
    fun `parses Arabic labeled internal transfer as transfer`() {
        val body = """
            حوالة داخلية
            من:0930
            مبلغ:1350 SAR
            الى:RAGHAD ALGHAMDI
            في:27/12/25 23:04
        """.trimIndent()

        val result = parser.parse(event(body)) as ParseResult.Success
        assertThat(result.type).isEqualTo(TxType.TRANSFER)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("1350"))
        assertThat(result.counterparty).isEqualTo("RAGHAD ALGHAMDI")
        assertThat(result.templateId).isEqualTo("al-rajhi-internal-transfer")
    }

    @Test
    fun `parses Arabic labeled incoming transfer as income`() {
        val body = """
            حوالة داخلية واردة
            مبلغ:500 SAR
            من:RAGHAD ALGHAMDI
            الى:0930
            في:27/12/25 23:04
        """.trimIndent()

        val result = parser.parse(event(body)) as ParseResult.Success
        assertThat(result.type).isEqualTo(TxType.INCOME)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("500"))
        assertThat(result.counterparty).isEqualTo("RAGHAD ALGHAMDI")
        assertThat(result.templateId).isEqualTo("al-rajhi-internal-transfer")
    }

    @Test
    fun `generic-amount fallback catches unrecognized formats with Amount label`() {
        val body = """
            ATM Withdrawal
            Card:5916
            Amount:500 SAR
            Location: Riyadh
        """.trimIndent()

        val result = parser.parse(event(body)) as ParseResult.Success
        assertThat(result.amount.amount).isEqualTo(BigDecimal("500"))
        assertThat(result.templateId).isEqualTo("al-rajhi-generic-amount")
        assertThat(result.confidence).isLessThan(0.7f)  // low — user must confirm
    }

    @Test
    fun `generic-amount fallback preserves Arabic biller label`() {
        val body = """
            مدفوعات وزارة الداخلية
            من:0930
            مبلغ:500 SAR
            الجهة: المخالفات المرورية
            الخدمة: الاستعلام عن المخالفات
            في:27/12/25 23:04
        """.trimIndent()

        val result = parser.parse(event(body)) as ParseResult.Success
        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("500"))
        assertThat(result.merchant).isEqualTo("المخالفات المرورية")
        assertThat(result.templateId).isEqualTo("al-rajhi-generic-amount")
        assertThat(result.confidence).isLessThan(0.7f)
    }

    @Test
    fun `ignores OTP without an Amount field`() {
        val body = "Your OTP is 123456. Do not share it."
        val result = parser.parse(event(body))
        assertThat(result).isInstanceOf(ParseResult.Ignored::class.java)
    }
}
