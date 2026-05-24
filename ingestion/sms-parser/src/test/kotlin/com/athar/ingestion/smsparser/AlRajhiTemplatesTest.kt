package com.athar.ingestion.smsparser

import com.athar.core.domain.model.IngestSource
import com.athar.core.domain.model.RawIngestEvent
import com.athar.core.domain.model.TxType
import com.athar.ingestion.smsparser.alrajhi.AlRajhiBalanceAlertTemplate
import com.athar.ingestion.smsparser.alrajhi.AlRajhiDepositTemplate
import com.athar.ingestion.smsparser.alrajhi.AlRajhiPurchaseTemplate
import com.athar.ingestion.smsparser.alrajhi.AlRajhiTransferOutTemplate
import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class AlRajhiTemplatesTest {

    private val parser = TemplateBasedSmsParser(
        listOf(
            AlRajhiBalanceAlertTemplate(),
            AlRajhiPurchaseTemplate(),
            AlRajhiTransferOutTemplate(),
            AlRajhiDepositTemplate(),
        ),
    )

    private val sampleTime = Instant.parse("2026-02-14T14:32:00Z")

    private fun event(body: String, sender: String = "AlRajhiBank") = RawIngestEvent(
        id = "id-1",
        source = IngestSource.SMS,
        sender = sender,
        body = body,
        receivedAt = sampleTime,
        rawId = "raw-1",
    )

    @Test
    fun `parses Arabic purchase`() {
        val body = """
            شراء بمبلغ 200.00 ر.س
            البطاقة 1234
            من STARBUCKS 1234
            الرصيد 4,521.30 ر.س
            2026/02/14 14:32
        """.trimIndent()
        val r = parser.parse(event(body)) as ParseResult.Success
        assertThat(r.type).isEqualTo(TxType.EXPENSE)
        assertThat(r.amount.amount).isEqualTo(BigDecimal("200.00"))
        assertThat(r.merchant).isEqualTo("STARBUCKS")
        assertThat(r.templateId).isEqualTo("al-rajhi-purchase")
    }

    @Test
    fun `parses English purchase`() {
        val body = """
            Purchase SAR 200.00
            Card 1234
            At STARBUCKS 1234
            Balance SAR 4,521.30
            2026/02/14 14:32
        """.trimIndent()
        val r = parser.parse(event(body)) as ParseResult.Success
        assertThat(r.type).isEqualTo(TxType.EXPENSE)
        assertThat(r.amount.amount).isEqualTo(BigDecimal("200.00"))
        assertThat(r.merchant).isEqualTo("STARBUCKS")
    }

    @Test
    fun `parses Arabic-Indic digits via normalization`() {
        val body = "شراء بمبلغ ٢٠٠٫٠٠ ر.س\nمن ستاربكس"
        val r = parser.parse(event(body))
        // We expect at least amount + type to come through; merchant may stay null if regex doesn't pick Arabic letters.
        assertThat(r).isInstanceOf(ParseResult.Success::class.java)
        val s = r as ParseResult.Success
        assertThat(s.amount.amount).isEqualTo(BigDecimal("200.00"))
        assertThat(s.type).isEqualTo(TxType.EXPENSE)
    }

    @Test
    fun `parses Arabic transfer-out`() {
        val body = """
            تحويل بمبلغ 1,000.00 ر.س
            الى احمد محمد ع
            الرصيد 3,521.30 ر.س
        """.trimIndent()
        val r = parser.parse(event(body)) as ParseResult.Success
        assertThat(r.type).isEqualTo(TxType.TRANSFER)
        assertThat(r.counterparty).contains("احمد")
    }

    @Test
    fun `parses Arabic deposit`() {
        val body = """
            ايداع 27,700.00 ر.س
            من ELM CO
            الرصيد 31,221.30 ر.س
        """.trimIndent()
        val r = parser.parse(event(body)) as ParseResult.Success
        assertThat(r.type).isEqualTo(TxType.INCOME)
        assertThat(r.amount.amount).isEqualTo(BigDecimal("27700.00"))
        assertThat(r.merchant).isEqualTo("ELM CO")
    }

    @Test
    fun `ignores balance alert without purchase keyword`() {
        val body = "رصيدك المتاح 4,521.30 ر.س"
        assertThat(parser.parse(event(body))).isEqualTo(ParseResult.Ignored)
    }

    @Test
    fun `unknown sender returns Ignored`() {
        assertThat(parser.parse(event("Purchase SAR 100", sender = "RandomSender")))
            .isEqualTo(ParseResult.Ignored)
    }
}
