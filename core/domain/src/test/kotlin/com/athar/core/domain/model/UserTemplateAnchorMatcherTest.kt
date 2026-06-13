package com.athar.core.domain.model

import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class UserTemplateAnchorMatcherTest {

    private val template = UserTemplate(
        id = "template-1",
        displayName = "Sample bank",
        sender = "SampleBank",
        txType = TxType.EXPENSE,
        amountAnchorBefore = "Amount:",
        amountAnchorAfter = "SAR",
        merchantAnchorBefore = "At:",
        merchantAnchorAfter = null,
        counterpartyAnchorBefore = null,
        counterpartyAnchorAfter = null,
        sampleBody = "",
        createdAt = Instant.parse("2026-06-13T00:00:00Z"),
    )

    @Test
    fun `matches amount and merchant from a valid sample`() {
        val match = UserTemplateAnchorMatcher.match(
            template = template,
            body = """
                Purchase approved
                At: ALDREES 8
                Amount: 1,350.50 SAR
            """.trimIndent(),
        )

        assertThat(match.canParseAmount).isTrue()
        assertThat(match.amount).isEqualTo(BigDecimal("1350.50"))
        assertThat(match.merchant).isEqualTo("ALDREES 8")
        assertThat(match.amountAnchorFound).isTrue()
        assertThat(match.merchantAnchorFound).isTrue()
        assertThat(match.counterpartyAnchorFound).isNull()
    }

    @Test
    fun `normalizes arabic digits before matching`() {
        val match = UserTemplateAnchorMatcher.match(
            body = "خصم\nالمبلغ: ١٬٣٥٠٫٥٠ ر.س\nلدى ستاربكس",
            amountAnchorBefore = "المبلغ:",
            amountAnchorAfter = "ر.س",
            merchantAnchorBefore = "لدى",
            merchantAnchorAfter = null,
            counterpartyAnchorBefore = null,
            counterpartyAnchorAfter = null,
        )

        assertThat(match.amount).isEqualTo(BigDecimal("1350.50"))
        assertThat(match.merchant).isEqualTo("ستاربكس")
    }

    @Test
    fun `reports missing amount anchor without parsing a nearby number`() {
        val match = UserTemplateAnchorMatcher.match(
            template = template.copy(amountAnchorBefore = "Total:"),
            body = "Card 1234\nAmount: 500 SAR",
        )

        assertThat(match.canParseAmount).isFalse()
        assertThat(match.amount).isNull()
        assertThat(match.amountAnchorFound).isFalse()
    }

    @Test
    fun `after anchor must appear near the parsed amount`() {
        val match = UserTemplateAnchorMatcher.match(
            template = template.copy(amountAnchorAfter = "SAR"),
            body = "Amount: 500 USD",
        )

        assertThat(match.canParseAmount).isFalse()
        assertThat(match.amountAnchorFound).isTrue()
    }
}
