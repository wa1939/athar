package com.athar.ingestion.smsparser

import com.athar.core.domain.model.IngestSource
import com.athar.core.domain.model.RawIngestEvent
import com.athar.core.domain.model.TxType
import com.athar.core.domain.model.UserTemplate
import com.athar.ingestion.smsparser.user.UserTemplateBankTemplate
import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class UserTemplateMatcherTest {

    private val sampleTime = Instant.parse("2026-05-25T12:00:00Z")

    private fun template(
        sender: String = "AlinmaBank",
        type: TxType = TxType.EXPENSE,
        amountBefore: String = "Amount:",
        amountAfter: String? = "SAR",
        merchantBefore: String? = "At:",
    ) = UserTemplate(
        id = "test-1",
        displayName = "Test bank",
        sender = sender,
        txType = type,
        amountAnchorBefore = amountBefore,
        amountAnchorAfter = amountAfter,
        merchantAnchorBefore = merchantBefore,
        merchantAnchorAfter = null,
        counterpartyAnchorBefore = null,
        counterpartyAnchorAfter = null,
        sampleBody = "",
        createdAt = sampleTime,
    )

    private fun event(body: String, sender: String) = RawIngestEvent(
        id = "id-1",
        source = IngestSource.SMS,
        sender = sender,
        body = body,
        receivedAt = sampleTime,
        rawId = "raw-1",
    )

    @Test
    fun `parses an Al-Rajhi-style body with anchors`() {
        val parser = UserTemplateBankTemplate(template())
        val body = """
            PoS purchase
            Card:5916
            At: ALDREES 8
            Amount: 201 SAR
        """.trimIndent()
        val result = parser.tryParse(body, sampleTime) as ParseResult.Success
        assertThat(result.amount.amount).isEqualTo(BigDecimal("201"))
        assertThat(result.merchant).isEqualTo("ALDREES 8")
        assertThat(result.type).isEqualTo(TxType.EXPENSE)
    }

    @Test
    fun `sender mismatch is filtered by registry`() {
        val parser = TemplateBasedSmsParser(listOf(UserTemplateBankTemplate(template())))
        val result = parser.parse(event("Amount: 100 SAR", sender = "DifferentBank"))
        assertThat(result).isInstanceOf(ParseResult.Ignored::class.java)
    }

    @Test
    fun `anchor not found returns Failed not Success`() {
        val parser = UserTemplateBankTemplate(template(amountBefore = "Total:"))
        val body = "Amount: 200 SAR"
        val result = parser.tryParse(body, sampleTime)
        assertThat(result).isInstanceOf(ParseResult.Failed::class.java)
    }

    @Test
    fun `optional after-anchor bounds the number`() {
        val parser = UserTemplateBankTemplate(template(amountAfter = "SAR"))
        // The "0930" digits before "Amount:" should not be confused for the amount.
        val body = "From:0930\nAmount: 1350 SAR\nTo:Someone"
        val result = parser.tryParse(body, sampleTime) as ParseResult.Success
        assertThat(result.amount.amount).isEqualTo(BigDecimal("1350"))
    }

    @Test
    fun `arabic anchors work`() {
        val parser = UserTemplateBankTemplate(template(
            amountBefore = "المبلغ",
            amountAfter = "ر.س",
            merchantBefore = "لدى",
        ))
        val body = "خصم: المبلغ 500 ر.س\nلدى ستاربكس\nالبطاقة 1234"
        val result = parser.tryParse(body, sampleTime) as ParseResult.Success
        assertThat(result.amount.amount).isEqualTo(BigDecimal("500"))
        assertThat(result.merchant).isEqualTo("ستاربكس")
    }
}
