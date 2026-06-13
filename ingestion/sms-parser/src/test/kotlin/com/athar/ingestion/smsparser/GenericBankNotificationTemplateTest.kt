package com.athar.ingestion.smsparser

import com.athar.core.domain.model.IngestSource
import com.athar.core.domain.model.RawIngestEvent
import com.athar.core.domain.model.TxType
import com.athar.ingestion.smsparser.notification.GenericBankNotificationTemplate
import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class GenericBankNotificationTemplateTest {

    private val at = Instant.parse("2026-06-13T12:00:00Z")
    private val parser = TemplateBasedSmsParser(listOf(GenericBankNotificationTemplate()))

    @Test
    fun `parses English card spend notification`() {
        val result = parser.parse(
            event("notification:com.wise.android", "Wise\nYou spent SAR 42.00 at Starbucks"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("42.00"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.merchant).isEqualTo("Starbucks")
    }

    @Test
    fun `parses Arabic spend notification with Arabic digits`() {
        val result = parser.parse(
            event("notification:com.alrajhibank.alrajhimobile", "تم خصم ٣٥٫٥٠ ر.س لدى كارفور"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("35.50"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.merchant).isEqualTo("كارفور")
    }

    @Test
    fun `parses income notification`() {
        val result = parser.parse(
            event("notification:com.revolut.revolut", "You received USD 250.00 from ACME Payroll"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.INCOME)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("250.00"))
        assertThat(result.amount.currency).isEqualTo("USD")
        assertThat(result.counterparty).isEqualTo("ACME Payroll")
    }

    @Test
    fun `parses transfer notification`() {
        val result = parser.parse(
            event("notification:com.chase.sig.android", "You sent AED 100.00 to Ahmed"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.TRANSFER)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("100.00"))
        assertThat(result.amount.currency).isEqualTo("AED")
        assertThat(result.counterparty).isEqualTo("Ahmed")
    }

    @Test
    fun `parses card-used wallet notification`() {
        val result = parser.parse(
            event(
                "notification:com.google.android.apps.walletnfcrel",
                "Card ending 1234 was used for USD 19.99 at Amazon",
            ),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("19.99"))
        assertThat(result.amount.currency).isEqualTo("USD")
        assertThat(result.merchant).isEqualTo("Amazon")
    }

    @Test
    fun `parses card payment to merchant notification`() {
        val result = parser.parse(
            event("notification:com.revolut.revolut", "You made a card payment to Uber Trip USD 18.75"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("18.75"))
        assertThat(result.amount.currency).isEqualTo("USD")
        assertThat(result.merchant).isEqualTo("Uber Trip")
    }

    @Test
    fun `parses direct debit notification`() {
        val result = parser.parse(
            event("notification:com.monzo", "Direct debit of GBP 29.99 to Netflix"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("29.99"))
        assertThat(result.amount.currency).isEqualTo("GBP")
        assertThat(result.merchant).isEqualTo("Netflix")
    }

    @Test
    fun `parses payment-from income notification`() {
        val result = parser.parse(
            event("notification:com.mercury", "Payment from ACME Payroll USD 250.00"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.INCOME)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("250.00"))
        assertThat(result.amount.currency).isEqualTo("USD")
        assertThat(result.counterparty).isEqualTo("ACME Payroll")
    }

    @Test
    fun `parses paid-you income notification`() {
        val result = parser.parse(
            event("notification:com.paypal.android.p2pmobile", "ACME Payroll paid you $1,200.00"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.INCOME)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("1200.00"))
        assertThat(result.amount.currency).isEqualTo("USD")
        assertThat(result.counterparty).isEqualTo("ACME Payroll")
    }

    @Test
    fun `prefers currency marked amount over card last four`() {
        val result = parser.parse(
            event("notification:com.google.android.apps.walletnfcrel", "Card ending 1234 purchase at Amazon SAR 56.35"),
        ) as ParseResult.Success

        assertThat(result.amount.amount).isEqualTo(BigDecimal("56.35"))
        assertThat(result.merchant).isEqualTo("Amazon")
    }

    @Test
    fun `ignores security code notifications`() {
        assertThat(
            parser.parse(event("notification:com.wise.android", "Your verification code is 123456. Do not share it.")),
        ).isEqualTo(ParseResult.Ignored)
    }

    @Test
    fun `ignores marketing cashback notifications`() {
        assertThat(
            parser.parse(event("notification:com.revolut.revolut", "Earn 10 SAR cashback this weekend")),
        ).isEqualTo(ParseResult.Ignored)
    }

    @Test
    fun `ignores transfer limit notifications with amounts`() {
        assertThat(
            parser.parse(event("notification:com.chase.sig.android", "Your daily transfer limit is now AED 5,000")),
        ).isEqualTo(ParseResult.Ignored)
    }

    @Test
    fun `ignores statement and minimum-payment notifications with amounts`() {
        assertThat(
            parser.parse(
                event(
                    "notification:com.capitalone.mobile",
                    "Your statement is ready. Minimum payment due USD 25.00 by July 1.",
                ),
            ),
        ).isEqualTo(ParseResult.Ignored)
    }

    @Test
    fun `does not run for random non bank packages`() {
        assertThat(
            parser.parse(event("notification:com.random.shopping", "You spent SAR 42.00 at Starbucks")),
        ).isEqualTo(ParseResult.Ignored)
    }

    @Test
    fun `does not run for sms sender text that happens to contain a bank brand`() {
        assertThat(
            parser.parse(event("wise-alert", "You spent SAR 42.00 at Starbucks")),
        ).isEqualTo(ParseResult.Ignored)
    }

    private fun event(sender: String, body: String) = RawIngestEvent(
        id = "notification",
        source = IngestSource.NOTIFICATION,
        sender = sender,
        body = body,
        receivedAt = at,
        rawId = "notification-key",
    )
}
