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
    fun `prefers transaction amount when balance appears before English spend amount`() {
        val result = parser.parse(
            event(
                "notification:com.wise.android",
                "Available balance: SAR 1,234.56. You spent SAR 42.00 at Starbucks",
            ),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("42.00"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.merchant).isEqualTo("Starbucks")
        assertThat(result.balanceAfter).isNull()
    }

    @Test
    fun `prefers transaction amount when Arabic balance appears before debit amount`() {
        val result = parser.parse(
            event("notification:com.alrajhibank.alrajhimobile", "رصيدك ١٬٠٠٠٫٠٠ ر.س بعد خصم ٣٥٫٥٠ ر.س لدى كارفور"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("35.50"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.merchant).isEqualTo("كارفور")
        assertThat(result.balanceAfter?.amount).isEqualTo(BigDecimal("1000.00"))
        assertThat(result.balanceAfter?.currency).isEqualTo("SAR")
    }

    @Test
    fun `extracts English balance after spend amount`() {
        val result = parser.parse(
            event(
                "notification:com.wise.android",
                "You spent SAR 42.00 at Starbucks. Balance SAR 958.00",
            ),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("42.00"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.merchant).isEqualTo("Starbucks")
        assertThat(result.balanceAfter?.amount).isEqualTo(BigDecimal("958.00"))
        assertThat(result.balanceAfter?.currency).isEqualTo("SAR")
    }

    @Test
    fun `ignores English balance-only notifications with account hints`() {
        assertThat(
            parser.parse(
                event(
                    "notification:com.chase.sig.android",
                    "Available balance SAR 1,234.56 at your checking account",
                ),
            ),
        ).isEqualTo(ParseResult.Ignored)
    }

    @Test
    fun `ignores Arabic balance-only notifications with account hints`() {
        assertThat(
            parser.parse(
                event("notification:com.alrajhibank.alrajhimobile", "رصيدك ٥٠٠ ر.س لدى حسابك الجاري"),
            ),
        ).isEqualTo(ParseResult.Ignored)
    }

    @Test
    fun `ignores available credit notifications with card hints`() {
        assertThat(
            parser.parse(
                event(
                    "notification:com.capitalone.mobile",
                    "Available credit USD 5,000.00 on your card ending 1234",
                ),
            ),
        ).isEqualTo(ParseResult.Ignored)
    }

    @Test
    fun `ignores credit limit update notifications with amounts`() {
        assertThat(
            parser.parse(
                event("notification:com.chase.sig.android", "Your credit limit was updated to AED 20,000.00"),
            ),
        ).isEqualTo(ParseResult.Ignored)
    }

    @Test
    fun `ignores Arabic credit availability notifications with amounts`() {
        assertThat(
            parser.parse(
                event("notification:com.alrajhibank.alrajhimobile", "الحد الائتماني المتاح ٥٠٠٠ ر.س على بطاقتك"),
            ),
        ).isEqualTo(ParseResult.Ignored)
    }

    @Test
    fun `ignores card activation admin notifications`() {
        assertThat(
            parser.parse(
                event("notification:com.chase.sig.android", "Your card ending 1234 has been activated."),
            ),
        ).isEqualTo(ParseResult.Ignored)
    }

    @Test
    fun `ignores card terms admin notifications`() {
        assertThat(
            parser.parse(
                event("notification:com.capitalone.mobile", "Terms and conditions updated for your card ending 1234."),
            ),
        ).isEqualTo(ParseResult.Ignored)
    }

    @Test
    fun `ignores device link admin notifications`() {
        assertThat(
            parser.parse(
                event("notification:com.wise.android", "New device linked to your account. Review if this was not you."),
            ),
        ).isEqualTo(ParseResult.Ignored)
    }

    @Test
    fun `ignores Arabic card activation admin notifications`() {
        assertThat(
            parser.parse(
                event("notification:com.alrajhibank.alrajhimobile", "تم تفعيل بطاقتك الرقمية بنجاح"),
            ),
        ).isEqualTo(ParseResult.Ignored)
    }

    @Test
    fun `ignores weekly spending recap notifications with amounts`() {
        assertThat(
            parser.parse(
                event("notification:com.wise.android", "Weekly recap: You spent USD 500.00 this week"),
            ),
        ).isEqualTo(ParseResult.Ignored)
    }

    @Test
    fun `ignores monthly spending summary notifications with amounts`() {
        assertThat(
            parser.parse(
                event("notification:com.revolut.revolut", "You spent SAR 1,200.00 this month"),
            ),
        ).isEqualTo(ParseResult.Ignored)
    }

    @Test
    fun `ignores Arabic spending summary notifications with amounts`() {
        assertThat(
            parser.parse(
                event("notification:com.alrajhibank.alrajhimobile", "ملخص الإنفاق: دفع ٥٠٠ ر.س هذا الشهر"),
            ),
        ).isEqualTo(ParseResult.Ignored)
    }

    @Test
    fun `parses Arabic debit without offer wording`() {
        val result = parser.parse(
            event("notification:com.alrajhibank.alrajhimobile", "خصم ٣٥٫٥٠ ر.س لدى كارفور"),
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
    fun `parses sent-you peer payment as income`() {
        val result = parser.parse(
            event("notification:com.squareup.cash", "John Appleseed sent you $25.00"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.INCOME)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("25.00"))
        assertThat(result.amount.currency).isEqualTo("USD")
        assertThat(result.counterparty).isEqualTo("John Appleseed")
    }

    @Test
    fun `parses got-paid by counterparty notification`() {
        val result = parser.parse(
            event("notification:com.mercury", "You got paid USD 250.00 by ACME Payroll"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.INCOME)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("250.00"))
        assertThat(result.amount.currency).isEqualTo("USD")
        assertThat(result.counterparty).isEqualTo("ACME Payroll")
    }

    @Test
    fun `parses wider global currency codes`() {
        val result = parser.parse(
            event("notification:com.wise.android", "You spent CAD 12.34 at Tim Hortons"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("12.34"))
        assertThat(result.amount.currency).isEqualTo("CAD")
        assertThat(result.merchant).isEqualTo("Tim Hortons")
    }

    @Test
    fun `parses comma decimal notification amount`() {
        val result = parser.parse(
            event("notification:com.revolut.revolut", "You spent €18,50 at Carrefour"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("18.50"))
        assertThat(result.amount.currency).isEqualTo("EUR")
        assertThat(result.merchant).isEqualTo("Carrefour")
    }

    @Test
    fun `parses European grouped notification amount`() {
        val result = parser.parse(
            event("notification:com.revolut.revolut", "You spent EUR 1.234,56 at Ikea"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("1234.56"))
        assertThat(result.amount.currency).isEqualTo("EUR")
        assertThat(result.merchant).isEqualTo("Ikea")
    }

    @Test
    fun `parses Google Pay India payment package`() {
        val result = parser.parse(
            event("notification:com.google.android.apps.nbu.paisa.user", "Paid INR 450.00 to Swiggy"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("450.00"))
        assertThat(result.amount.currency).isEqualTo("INR")
        assertThat(result.merchant).isEqualTo("Swiggy")
    }

    @Test
    fun `parses card charged by merchant notification`() {
        val result = parser.parse(
            event("notification:com.capitalone.mobile", "Your card was charged $8.99 by Netflix"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("8.99"))
        assertThat(result.amount.currency).isEqualTo("USD")
        assertThat(result.merchant).isEqualTo("Netflix")
    }

    @Test
    fun `parses merchant charged your card notification`() {
        val result = parser.parse(
            event("notification:com.capitalone.mobile", "Netflix charged your card USD 8.99"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("8.99"))
        assertThat(result.amount.currency).isEqualTo("USD")
        assertThat(result.merchant).isEqualTo("Netflix")
    }

    @Test
    fun `parses card transaction merchant before amount notification`() {
        val result = parser.parse(
            event("notification:com.transferwise.android", "Card transaction Starbucks SGD 6.40"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("6.40"))
        assertThat(result.amount.currency).isEqualTo("SGD")
        assertThat(result.merchant).isEqualTo("Starbucks")
    }

    @Test
    fun `parses compact trailing merchant after amount notification`() {
        val result = parser.parse(
            event("notification:com.emiratesnbd.android", "POS purchase SAR 42.00 Carrefour"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("42.00"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.merchant).isEqualTo("Carrefour")
        assertThat(result.balanceAfter).isNull()
    }

    @Test
    fun `parses trailing merchant before balance suffix notification`() {
        val result = parser.parse(
            event("notification:com.alrajhibank.alrajhimobile", "POS purchase SAR 42.00 Carrefour الرصيد ١٠٠ ر.س"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("42.00"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.merchant).isEqualTo("Carrefour")
        assertThat(result.balanceAfter?.amount).isEqualTo(BigDecimal("100"))
        assertThat(result.balanceAfter?.currency).isEqualTo("SAR")
    }

    @Test
    fun `parses Arabic trailing biller after amount notification`() {
        val result = parser.parse(
            event("notification:com.alrajhibank.alrajhimobile", "تم دفع ١٢٥ ر.س فاتورة الكهرباء"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("125"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.merchant).isEqualTo("فاتورة الكهرباء")
    }

    @Test
    fun `does not treat trailing status as merchant`() {
        val result = parser.parse(
            event("notification:com.chase.sig.android", "Card purchase SAR 42.00 approved"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("42.00"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.merchant).isNull()
    }

    @Test
    fun `parses broader global currency code notification`() {
        val result = parser.parse(
            event("notification:com.revolut.revolut", "You spent SEK 129,00 at IKEA"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("129.00"))
        assertThat(result.amount.currency).isEqualTo("SEK")
        assertThat(result.merchant).isEqualTo("IKEA")
    }

    @Test
    fun `parses regional currency symbol notifications`() {
        val cases = listOf(
            RegionalCurrencyCase(
                sender = "notification:com.dbsmbanking.mobile",
                body = "You spent S$ 6.40 at Toast Box",
                amount = "6.40",
                currency = "SGD",
                merchant = "Toast Box",
            ),
            RegionalCurrencyCase(
                sender = "notification:com.nu.production.nubank",
                body = "You spent R$ 19,90 at iFood",
                amount = "19.90",
                currency = "BRL",
                merchant = "iFood",
            ),
            RegionalCurrencyCase(
                sender = "notification:com.maybank2u.life",
                body = "You spent RM12.30 at Grab",
                amount = "12.30",
                currency = "MYR",
                merchant = "Grab",
            ),
            RegionalCurrencyCase(
                sender = "notification:com.transferwise.android",
                body = "You spent Rp 75.000,00 at Alfamart",
                amount = "75000.00",
                currency = "IDR",
                merchant = "Alfamart",
            ),
            RegionalCurrencyCase(
                sender = "notification:com.cimb.mobile",
                body = "Paid ₱250.00 to Jollibee",
                amount = "250.00",
                currency = "PHP",
                merchant = "Jollibee",
            ),
            RegionalCurrencyCase(
                sender = "notification:com.transferwise.android",
                body = "You spent ₩1,000 at CU",
                amount = "1000",
                currency = "KRW",
                merchant = "CU",
            ),
            RegionalCurrencyCase(
                sender = "notification:com.transferwise.android",
                body = "You spent ฿120.00 at BTS",
                amount = "120.00",
                currency = "THB",
                merchant = "BTS",
            ),
            RegionalCurrencyCase(
                sender = "notification:com.transferwise.android",
                body = "You spent ₫120,000 at Highlands Coffee",
                amount = "120000",
                currency = "VND",
                merchant = "Highlands Coffee",
            ),
        )

        cases.forEach { case ->
            val result = parser.parse(event(case.sender, case.body)) as ParseResult.Success

            assertThat(result.type).isEqualTo(TxType.EXPENSE)
            assertThat(result.amount.amount).isEqualTo(BigDecimal(case.amount))
            assertThat(result.amount.currency).isEqualTo(case.currency)
            assertThat(result.merchant).isEqualTo(case.merchant)
        }
    }

    @Test
    fun `parses debit card transaction from merchant notification`() {
        val result = parser.parse(
            event("notification:com.chase.sig.android", "Debit card transaction from Trader Joe's for $23.10"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("23.10"))
        assertThat(result.amount.currency).isEqualTo("USD")
        assertThat(result.merchant).isEqualTo("Trader Joe's")
    }

    @Test
    fun `parses paid merchant before amount notification`() {
        val result = parser.parse(
            event("notification:com.revolut.revolut", "You paid Apple Services $9.99"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("9.99"))
        assertThat(result.amount.currency).isEqualTo("USD")
        assertThat(result.merchant).isEqualTo("Apple Services")
    }

    @Test
    fun `parses paid-for merchant after amount notification`() {
        val result = parser.parse(
            event("notification:com.monzo", "You paid $9.99 for Netflix"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("9.99"))
        assertThat(result.amount.currency).isEqualTo("USD")
        assertThat(result.merchant).isEqualTo("Netflix")
    }

    @Test
    fun `parses on merchant hint notification`() {
        val result = parser.parse(
            event("notification:com.chase.sig.android", "Debit of USD 23.10 on Trader Joe's"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("23.10"))
        assertThat(result.amount.currency).isEqualTo("USD")
        assertThat(result.merchant).isEqualTo("Trader Joe's")
    }

    @Test
    fun `parses new transaction merchant before amount notification`() {
        val result = parser.parse(
            event("notification:com.transferwise.android", "New transaction: Starbucks SGD 6.40"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("6.40"))
        assertThat(result.amount.currency).isEqualTo("SGD")
        assertThat(result.merchant).isEqualTo("Starbucks")
    }

    @Test
    fun `parses Arabic fi merchant hint notification`() {
        val result = parser.parse(
            event("notification:com.alrajhibank.alrajhimobile", "تم خصم ٣٥٫٥٠ ر.س في كارفور"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("35.50"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.merchant).isEqualTo("كارفور")
    }

    @Test
    fun `parses credit-of income notification`() {
        val result = parser.parse(
            event("notification:com.mercury", "Credit of USD 250.00 from ACME Payroll"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.INCOME)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("250.00"))
        assertThat(result.amount.currency).isEqualTo("USD")
        assertThat(result.counterparty).isEqualTo("ACME Payroll")
    }

    @Test
    fun `parses labeled merchant notification fields`() {
        val result = parser.parse(
            event(
                "notification:com.emiratesnbd.android",
                """
                Card purchase
                Merchant: Carrefour
                Amount: AED 42.00
                """.trimIndent(),
            ),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("42.00"))
        assertThat(result.amount.currency).isEqualTo("AED")
        assertThat(result.merchant).isEqualTo("Carrefour")
    }

    @Test
    fun `parses labeled sender income notification fields`() {
        val result = parser.parse(
            event(
                "notification:com.mercury",
                """
                Incoming transfer
                Amount: USD 250.00
                Sender: ACME Payroll
                """.trimIndent(),
            ),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.INCOME)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("250.00"))
        assertThat(result.amount.currency).isEqualTo("USD")
        assertThat(result.counterparty).isEqualTo("ACME Payroll")
    }

    @Test
    fun `parses labeled recipient transfer notification fields`() {
        val result = parser.parse(
            event(
                "notification:com.chase.sig.android",
                """
                Transfer sent
                Amount: AED 100.00
                Recipient: Ahmed
                """.trimIndent(),
            ),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.TRANSFER)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("100.00"))
        assertThat(result.amount.currency).isEqualTo("AED")
        assertThat(result.counterparty).isEqualTo("Ahmed")
    }

    @Test
    fun `parses colon separated from counterparty notification`() {
        val result = parser.parse(
            event(
                "notification:com.mercury",
                """
                Money received
                Amount: USD 80.00
                From: Consulting Client
                """.trimIndent(),
            ),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.INCOME)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("80.00"))
        assertThat(result.amount.currency).isEqualTo("USD")
        assertThat(result.counterparty).isEqualTo("Consulting Client")
    }

    @Test
    fun `parses labeled biller expense notification fields`() {
        val result = parser.parse(
            event(
                "notification:com.alrajhibank.alrajhimobile",
                """
                Bill payment
                Biller: Saudi Electricity
                Amount: SAR 225.00
                """.trimIndent(),
            ),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("225.00"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.merchant).isEqualTo("Saudi Electricity")
    }

    @Test
    fun `parses Arabic labeled biller expense notification fields`() {
        val result = parser.parse(
            event(
                "notification:com.alrajhibank.alrajhimobile",
                """
                دفع فاتورة
                المفوتر: شركة الكهرباء
                المبلغ: ١٢٥ ر.س
                """.trimIndent(),
            ),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("125"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.merchant).isEqualTo("شركة الكهرباء")
    }

    @Test
    fun `parses payer and remitter income notification fields`() {
        val payer = parser.parse(
            event(
                "notification:com.mercury",
                """
                Incoming transfer
                Amount: USD 250.00
                Payer: ACME Payroll
                """.trimIndent(),
            ),
        ) as ParseResult.Success
        val remitter = parser.parse(
            event(
                "notification:com.emiratesnbd.android",
                """
                Money received
                Amount: AED 80.00
                Remitter: Consulting Client
                """.trimIndent(),
            ),
        ) as ParseResult.Success

        assertThat(payer.type).isEqualTo(TxType.INCOME)
        assertThat(payer.counterparty).isEqualTo("ACME Payroll")
        assertThat(remitter.type).isEqualTo(TxType.INCOME)
        assertThat(remitter.counterparty).isEqualTo("Consulting Client")
    }

    @Test
    fun `parses receiver and payee transfer notification fields`() {
        val receiver = parser.parse(
            event(
                "notification:com.chase.sig.android",
                """
                Transfer sent
                Amount: USD 100.00
                Receiver: Ahmed
                """.trimIndent(),
            ),
        ) as ParseResult.Success
        val payee = parser.parse(
            event(
                "notification:com.revolut.revolut",
                """
                Transfer sent
                Amount: EUR 900.00
                Payee: Rent Account
                """.trimIndent(),
            ),
        ) as ParseResult.Success

        assertThat(receiver.type).isEqualTo(TxType.TRANSFER)
        assertThat(receiver.counterparty).isEqualTo("Ahmed")
        assertThat(payee.type).isEqualTo(TxType.TRANSFER)
        assertThat(payee.counterparty).isEqualTo("Rent Account")
    }

    @Test
    fun `strips wallet suffix from paid-to merchant notification`() {
        val result = parser.parse(
            event("notification:com.usbank.mobilebanking", "You paid $9.99 to Apple Services with Apple Pay"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("9.99"))
        assertThat(result.amount.currency).isEqualTo("USD")
        assertThat(result.merchant).isEqualTo("Apple Services")
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
    fun `parses MENA bank package notifications`() {
        val result = parser.parse(
            event("notification:com.emiratesnbd.android", "You spent AED 42.00 at Carrefour"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("42.00"))
        assertThat(result.amount.currency).isEqualTo("AED")
        assertThat(result.merchant).isEqualTo("Carrefour")
    }

    @Test
    fun `parses Australian bank package notifications`() {
        val result = parser.parse(
            event("notification:com.commbank.netbank", "Card transaction Woolworths AUD 12.34"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("12.34"))
        assertThat(result.amount.currency).isEqualTo("AUD")
        assertThat(result.merchant).isEqualTo("Woolworths")
    }

    @Test
    fun `parses Indian bank package notifications`() {
        val result = parser.parse(
            event("notification:com.hdfcbank.mobilebanking", "Paid INR 450.00 to Swiggy"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("450.00"))
        assertThat(result.amount.currency).isEqualTo("INR")
        assertThat(result.merchant).isEqualTo("Swiggy")
    }

    @Test
    fun `parses US card package notifications`() {
        val result = parser.parse(
            event("notification:com.discoverfinancial.mobile", "Your card was charged $8.99 by Netflix"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("8.99"))
        assertThat(result.amount.currency).isEqualTo("USD")
        assertThat(result.merchant).isEqualTo("Netflix")
    }

    @Test
    fun `ignores security code notifications`() {
        assertThat(
            parser.parse(event("notification:com.wise.android", "Your verification code is 123456. Do not share it.")),
        ).isEqualTo(ParseResult.Ignored)
    }

    @Test
    fun `ignores security code authorization notifications with payment amounts`() {
        assertThat(
            parser.parse(
                event(
                    "notification:com.chase.sig.android",
                    "Use OTP 123456 to authorize payment of SAR 500.00. Do not share it.",
                ),
            ),
        ).isEqualTo(ParseResult.Ignored)
    }

    @Test
    fun `ignores Arabic verification code purchase authorization notifications`() {
        assertThat(
            parser.parse(
                event(
                    "notification:com.alrajhibank.alrajhimobile",
                    "رمز التحقق ١٢٣٤٥٦ لتأكيد عملية شراء بمبلغ ٥٠٠ ر.س. لا تشاركه",
                ),
            ),
        ).isEqualTo(ParseResult.Ignored)
    }

    @Test
    fun `parses real posted payment notifications without security code wording`() {
        val result = parser.parse(
            event("notification:com.chase.sig.android", "Card payment SAR 500.00 to Amazon confirmed"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("500.00"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.merchant).isEqualTo("Amazon")
    }

    @Test
    fun `ignores temporary authorization hold notifications with merchant hints`() {
        assertThat(
            parser.parse(
                event(
                    "notification:com.capitalone.mobile",
                    "Temporary authorization hold of USD 50.00 at Grand Hotel",
                ),
            ),
        ).isEqualTo(ParseResult.Ignored)
    }

    @Test
    fun `ignores pre authorization notifications with amounts`() {
        assertThat(
            parser.parse(
                event("notification:com.wise.android", "Pre-authorization of SAR 1.00 at Apple Services"),
            ),
        ).isEqualTo(ParseResult.Ignored)
    }

    @Test
    fun `ignores pending authorization notifications with amounts`() {
        assertThat(
            parser.parse(
                event("notification:com.emiratesnbd.android", "Pending authorization AED 200.00 with Booking.com"),
            ),
        ).isEqualTo(ParseResult.Ignored)
    }

    @Test
    fun `ignores Arabic temporary hold notifications with merchant hints`() {
        assertThat(
            parser.parse(
                event(
                    "notification:com.alrajhibank.alrajhimobile",
                    "تم حجز مبلغ ٥٠٠ ر.س مؤقتاً لدى فندق الرياض",
                ),
            ),
        ).isEqualTo(ParseResult.Ignored)
    }

    @Test
    fun `parses posted card transaction notifications despite authorization hold guards`() {
        val result = parser.parse(
            event("notification:com.capitalone.mobile", "Card transaction SAR 50.00 at Grand Hotel posted"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("50.00"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.merchant).isEqualTo("Grand Hotel")
    }

    @Test
    fun `ignores marketing cashback notifications`() {
        assertThat(
            parser.parse(event("notification:com.revolut.revolut", "Earn 10 SAR cashback this weekend")),
        ).isEqualTo(ParseResult.Ignored)
    }

    @Test
    fun `ignores Arabic discount offer notifications with money amounts`() {
        assertThat(
            parser.parse(
                event("notification:com.alrajhibank.alrajhimobile", "عرض خصم 20 ر.س عند استخدام بطاقتك اليوم"),
            ),
        ).isEqualTo(ParseResult.Ignored)
    }

    @Test
    fun `ignores Arabic percentage discount offer notifications`() {
        assertThat(
            parser.parse(
                event("notification:com.alrajhibank.alrajhimobile", "خصم 20% لدى شركائنا عند الدفع ببطاقتك"),
            ),
        ).isEqualTo(ParseResult.Ignored)
    }

    @Test
    fun `ignores reward-only cashback notifications with card purchase wording`() {
        assertThat(
            parser.parse(
                event("notification:com.revolut.revolut", "Earn 10 SAR cashback on your next card purchase"),
            ),
        ).isEqualTo(ParseResult.Ignored)
    }

    @Test
    fun `ignores card purchase cashback notifications without posted spend amount`() {
        assertThat(
            parser.parse(
                event("notification:com.capitalone.mobile", "Your card purchase at Carrefour earned SAR 5 cashback"),
            ),
        ).isEqualTo(ParseResult.Ignored)
    }

    @Test
    fun `prefers posted spend amount over cashback reward amount`() {
        val result = parser.parse(
            event(
                "notification:com.revolut.revolut",
                "You spent SAR 42.00 at Starbucks and earned SAR 5.00 cashback",
            ),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("42.00"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.merchant).isEqualTo("Starbucks")
    }

    @Test
    fun `parses cashback credited as income`() {
        val result = parser.parse(
            event("notification:com.revolut.revolut", "Cashback credited SAR 10.00 from Rewards"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.INCOME)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("10.00"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.counterparty).isEqualTo("Rewards")
    }

    @Test
    fun `ignores transfer limit notifications with amounts`() {
        assertThat(
            parser.parse(event("notification:com.chase.sig.android", "Your daily transfer limit is now AED 5,000")),
        ).isEqualTo(ParseResult.Ignored)
    }

    @Test
    fun `ignores peer payment requests with amounts`() {
        assertThat(
            parser.parse(event("notification:com.venmo", "John Appleseed requested $25.00")),
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
    fun `ignores scheduled payment notifications with amounts`() {
        assertThat(
            parser.parse(
                event(
                    "notification:com.chase.sig.android",
                    "Your scheduled payment of USD 25.00 to Netflix is tomorrow.",
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

    private data class RegionalCurrencyCase(
        val sender: String,
        val body: String,
        val amount: String,
        val currency: String,
        val merchant: String,
    )
}
