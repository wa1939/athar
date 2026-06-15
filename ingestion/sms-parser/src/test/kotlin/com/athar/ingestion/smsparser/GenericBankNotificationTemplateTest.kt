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
    fun `parses salary credited notification with shared salary label`() {
        val result = parser.parse(
            event("notification:com.chase.sig.android", "Salary credited SAR 9,000.00"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.INCOME)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("9000.00"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.counterparty).isEqualTo("Salary")
    }

    @Test
    fun `preserves payroll source while keeping shared salary label`() {
        val result = parser.parse(
            event("notification:com.mercury", "Salary credited USD 9,000.00 from ACME Payroll"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.INCOME)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("9000.00"))
        assertThat(result.amount.currency).isEqualTo("USD")
        assertThat(result.counterparty).isEqualTo("Salary - ACME Payroll")
    }

    @Test
    fun `parses Arabic salary deposit notification with shared salary label`() {
        val result = parser.parse(
            event("notification:com.alrajhibank.alrajhimobile", "تم إيداع راتب ٩٠٠٠ ر.س"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.INCOME)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("9000"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.counterparty).isEqualTo("راتب")
    }

    @Test
    fun `ignores salary transfer financing offers with amounts`() {
        assertThat(
            parser.parse(
                event(
                    "notification:com.alrajhibank.alrajhimobile",
                    "No salary transfer required. Get SAR 100,000 financing approved instantly.",
                ),
            ),
        ).isEqualTo(ParseResult.Ignored)
    }

    @Test
    fun `parses refunded notification as income`() {
        val result = parser.parse(
            event("notification:com.capitalone.mobile", "You were refunded GBP 12.50 from Amazon"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.INCOME)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("12.50"))
        assertThat(result.amount.currency).isEqualTo("GBP")
        assertThat(result.counterparty).isEqualTo("Expense reimbursement - Amazon")
    }

    @Test
    fun `parses card purchase reversal notification as income`() {
        val result = parser.parse(
            event("notification:com.emiratesnbd.android", "Purchase reversal AED 18.75 from Uber Trip"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.INCOME)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("18.75"))
        assertThat(result.amount.currency).isEqualTo("AED")
        assertThat(result.counterparty).isEqualTo("Expense reimbursement - Uber Trip")
    }

    @Test
    fun `parses chargeback notification as income`() {
        val result = parser.parse(
            event("notification:com.chase.sig.android", "Chargeback of USD 15.00 from Hotel Desk"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.INCOME)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("15.00"))
        assertThat(result.amount.currency).isEqualTo("USD")
        assertThat(result.counterparty).isEqualTo("Expense reimbursement - Hotel Desk")
    }

    @Test
    fun `parses tax refund notification with shared income label`() {
        val result = parser.parse(
            event("notification:com.chase.sig.android", "Tax refund credited USD 600.00 from IRS"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.INCOME)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("600.00"))
        assertThat(result.amount.currency).isEqualTo("USD")
        assertThat(result.counterparty).isEqualTo("Tax refund - IRS")
    }

    @Test
    fun `parses Arabic tax refund notification with shared income label`() {
        val result = parser.parse(
            event("notification:com.alrajhibank.alrajhimobile", "تم إيداع استرداد ضريبي ٦٠٠ ر.س"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.INCOME)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("600"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.counterparty).isEqualTo("استرداد ضريبي")
    }

    @Test
    fun `parses expense reimbursement notification with shared income label`() {
        val result = parser.parse(
            event("notification:com.mercury", "Expense reimbursement credited SAR 120.00 from Employer"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.INCOME)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("120.00"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.counterparty).isEqualTo("Expense reimbursement - Employer")
    }

    @Test
    fun `parses paid reimbursement claim notification with shared income label`() {
        val result = parser.parse(
            event("notification:com.mercury", "Reimbursement claim paid SAR 120.00 by Employer"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.INCOME)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("120.00"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.counterparty).isEqualTo("Expense reimbursement - Employer")
    }

    @Test
    fun `parses bonus notification with shared income label`() {
        val result = parser.parse(
            event("notification:com.mercury", "Annual bonus paid SAR 1,000.00 by ACME"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.INCOME)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("1000.00"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.counterparty).isEqualTo("Bonus income - ACME")
    }

    @Test
    fun `parses Arabic bonus notification with shared income label`() {
        val result = parser.parse(
            event("notification:com.alrajhibank.alrajhimobile", "تم إيداع مكافأة ٥٠٠ ر.س"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.INCOME)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("500"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.counterparty).isEqualTo("دخل مكافأة")
    }

    @Test
    fun `parses freelance income notification with shared income label`() {
        val result = parser.parse(
            event("notification:com.mercury", "Freelance payment received USD 450.00 from Upwork"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.INCOME)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("450.00"))
        assertThat(result.amount.currency).isEqualTo("USD")
        assertThat(result.counterparty).isEqualTo("Freelance income - Upwork")
    }

    @Test
    fun `parses rental income notification with shared income label`() {
        val result = parser.parse(
            event("notification:com.chase.sig.android", "Rental income credited SAR 2,500.00 from Tenant"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.INCOME)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("2500.00"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.counterparty).isEqualTo("Rental income - Tenant")
    }

    @Test
    fun `parses dividend income notification with shared income label`() {
        val result = parser.parse(
            event("notification:com.capitalone.mobile", "Dividend credited USD 32.10 from Vanguard"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.INCOME)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("32.10"))
        assertThat(result.amount.currency).isEqualTo("USD")
        assertThat(result.counterparty).isEqualTo("Dividend income - Vanguard")
    }

    @Test
    fun `parses interest income notification with shared income label`() {
        val result = parser.parse(
            event("notification:com.emiratesnbd.android", "Interest income credited AED 14.25"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.INCOME)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("14.25"))
        assertThat(result.amount.currency).isEqualTo("AED")
        assertThat(result.counterparty).isEqualTo("Interest income")
    }

    @Test
    fun `parses Arabic freelance income notification with shared income label`() {
        val result = parser.parse(
            event("notification:com.alrajhibank.alrajhimobile", "تم إيداع دخل عمل حر ٥٠٠ ر.س من مستقل"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.INCOME)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("500"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.counterparty).isEqualTo("دخل عمل حر - مستقل")
    }

    @Test
    fun `parses Arabic interest income notification with shared income label`() {
        val result = parser.parse(
            event("notification:com.alrajhibank.alrajhimobile", "تم إيداع دخل فوائد ٢٠ ر.س"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.INCOME)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("20"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.counterparty).isEqualTo("دخل فوائد")
    }

    @Test
    fun `ignores tax refund estimate notifications with amounts`() {
        assertThat(
            parser.parse(
                event("notification:com.chase.sig.android", "Your tax refund estimate is USD 600.00"),
            ),
        ).isEqualTo(ParseResult.Ignored)
    }

    @Test
    fun `ignores expense reimbursement claim notifications with amounts`() {
        assertThat(
            parser.parse(
                event("notification:com.mercury", "Expense reimbursement claim approved for SAR 120.00"),
            ),
        ).isEqualTo(ParseResult.Ignored)
    }

    @Test
    fun `ignores bonus promotion notifications with amounts`() {
        assertThat(
            parser.parse(
                event("notification:com.capitalone.mobile", "Get SAR 50.00 bonus when you top up"),
            ),
        ).isEqualTo(ParseResult.Ignored)
    }

    @Test
    fun `ignores side income invoice notifications with amounts`() {
        assertThat(
            parser.parse(
                event("notification:com.mercury", "Freelance invoice approved for USD 450.00"),
            ),
        ).isEqualTo(ParseResult.Ignored)
    }

    @Test
    fun `ignores side income estimate notifications with amounts`() {
        assertThat(
            parser.parse(
                event("notification:com.capitalone.mobile", "Dividend estimate USD 32.10 available"),
            ),
        ).isEqualTo(ParseResult.Ignored)
    }

    @Test
    fun `parses work expense notification with shared merchant label`() {
        val result = parser.parse(
            event("notification:com.mercury", "Business expense paid USD 42.00 at Office Depot"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("42.00"))
        assertThat(result.amount.currency).isEqualTo("USD")
        assertThat(result.merchant).isEqualTo("Work expense - Office Depot")
    }

    @Test
    fun `parses office supplies notification with shared merchant label`() {
        val result = parser.parse(
            event("notification:com.capitalone.mobile", "Office supplies purchase SAR 75.00 at Jarir"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("75.00"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.merchant).isEqualTo("Office supplies purchase - Jarir")
    }

    @Test
    fun `parses Arabic work expense notification with shared merchant label`() {
        val result = parser.parse(
            event("notification:com.alrajhibank.alrajhimobile", "تم دفع مصروفات العمل ٧٥ ر.س لدى مكتبة جرير"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("75"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.merchant).isEqualTo("مصروفات العمل - مكتبة جرير")
    }

    @Test
    fun `parses Arabic office supplies notification with shared merchant label`() {
        val result = parser.parse(
            event("notification:com.alrajhibank.alrajhimobile", "تم شراء مستلزمات مكتبية ٨٠ ر.س لدى مكتبة"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("80"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.merchant).isEqualTo("مشتريات مكتبية - مكتبة")
    }

    @Test
    fun `ignores work expense claim notifications with amounts`() {
        assertThat(
            parser.parse(
                event("notification:com.mercury", "Business expense report approved for USD 42.00"),
            ),
        ).isEqualTo(ParseResult.Ignored)
    }

    @Test
    fun `ignores office supplies order status notifications with amounts`() {
        assertThat(
            parser.parse(
                event("notification:com.capitalone.mobile", "Office supplies order status: delivery for USD 75.00"),
            ),
        ).isEqualTo(ParseResult.Ignored)
    }

    @Test
    fun `parses atm withdrawal notification with shared merchant label`() {
        val result = parser.parse(
            event("notification:com.chase.sig.android", "ATM withdrawal of USD 100.00 from Main Street ATM"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("100.00"))
        assertThat(result.amount.currency).isEqualTo("USD")
        assertThat(result.merchant).isEqualTo("ATM Withdrawal")
    }

    @Test
    fun `parses withdrawn notification with shared merchant label`() {
        val result = parser.parse(
            event("notification:com.emiratesnbd.android", "Cash withdrawn AED 250.00 from ATM 123"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("250.00"))
        assertThat(result.amount.currency).isEqualTo("AED")
        assertThat(result.merchant).isEqualTo("ATM Withdrawal")
    }

    @Test
    fun `parses Arabic cash withdrawal notification with shared merchant label`() {
        val result = parser.parse(
            event("notification:com.alrajhibank.alrajhimobile", "تم سحب ٥٠٠ ر.س من صراف آلي"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("500"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.merchant).isEqualTo("ATM Withdrawal")
    }

    @Test
    fun `ignores atm withdrawal limit notifications with amounts`() {
        assertThat(
            parser.parse(
                event("notification:com.chase.sig.android", "Your ATM withdrawal limit is now USD 1,000.00"),
            ),
        ).isEqualTo(ParseResult.Ignored)
    }

    @Test
    fun `parses service fee notification with shared merchant label`() {
        val result = parser.parse(
            event("notification:com.chase.sig.android", "Monthly service fee SAR 15.00 charged"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("15.00"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.merchant).isEqualTo("Bank fees")
    }

    @Test
    fun `parses foreign transaction fee notification with shared merchant label`() {
        val result = parser.parse(
            event("notification:com.capitalone.mobile", "Foreign transaction fee of USD 1.20"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("1.20"))
        assertThat(result.amount.currency).isEqualTo("USD")
        assertThat(result.merchant).isEqualTo("Bank fees")
    }

    @Test
    fun `parses Arabic bank fee notification with shared merchant label`() {
        val result = parser.parse(
            event("notification:com.alrajhibank.alrajhimobile", "تم خصم رسوم ١٥ ر.س من حسابك"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("15"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.merchant).isEqualTo("Bank fees")
    }

    @Test
    fun `ignores fee schedule notifications with amounts`() {
        assertThat(
            parser.parse(
                event("notification:com.chase.sig.android", "New fee schedule: ATM fee USD 3.00 from July 1"),
            ),
        ).isEqualTo(ParseResult.Ignored)
    }

    @Test
    fun `parses credit card payment notification with shared debt label`() {
        val result = parser.parse(
            event("notification:com.chase.sig.android", "Credit card payment SAR 500.00 posted"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("500.00"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.merchant).isEqualTo("Credit card payment")
    }

    @Test
    fun `parses payment to credit card notification with shared debt label`() {
        val result = parser.parse(
            event("notification:com.capitalone.mobile", "Payment to your credit card ending 2106 USD 250.00 successful"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("250.00"))
        assertThat(result.amount.currency).isEqualTo("USD")
        assertThat(result.merchant).isEqualTo("Credit card payment")
    }

    @Test
    fun `parses loan installment notification with shared debt label`() {
        val result = parser.parse(
            event("notification:com.emiratesnbd.android", "Loan installment of AED 1,000.00 debited"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("1000.00"))
        assertThat(result.amount.currency).isEqualTo("AED")
        assertThat(result.merchant).isEqualTo("Loan instalment")
    }

    @Test
    fun `parses mortgage payment notification with shared mortgage label`() {
        val result = parser.parse(
            event("notification:com.chase.sig.android", "Mortgage payment USD 2,100.00 posted"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("2100.00"))
        assertThat(result.amount.currency).isEqualTo("USD")
        assertThat(result.merchant).isEqualTo("Mortgage payment")
    }

    @Test
    fun `parses home loan repayment notification with shared mortgage label`() {
        val result = parser.parse(
            event("notification:com.alrajhibank.alrajhimobile", "Home loan repayment SAR 3,500.00 debited"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("3500.00"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.merchant).isEqualTo("Mortgage payment")
    }

    @Test
    fun `parses Arabic mortgage installment notification with shared mortgage label`() {
        val result = parser.parse(
            event("notification:com.alrajhibank.alrajhimobile", "تم سداد قسط عقاري بمبلغ ٤٠٠٠ ر.س"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("4000"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.merchant).isEqualTo("Mortgage payment")
    }

    @Test
    fun `ignores mortgage offers rates and reminders with amounts`() {
        val nonPostedMessages = listOf(
            "Your mortgage payment of USD 2,100.00 is due tomorrow",
            "Home loan refinance offer SAR 500,000.00 available",
            "Housing finance rate estimate AED 250,000.00 today",
            "عرض تمويل عقاري بمبلغ ٥٠٠٠٠٠ ر.س متاح الآن",
        )

        nonPostedMessages.forEach { body ->
            assertThat(parser.parse(event("notification:com.chase.sig.android", body)))
                .isEqualTo(ParseResult.Ignored)
        }
    }

    @Test
    fun `parses auto loan payment notification with shared car payment label`() {
        val result = parser.parse(
            event("notification:com.chase.sig.android", "Auto loan payment USD 420.00 posted"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("420.00"))
        assertThat(result.amount.currency).isEqualTo("USD")
        assertThat(result.merchant).isEqualTo("Car payment")
    }

    @Test
    fun `parses exact car payment notification with shared car payment label`() {
        val result = parser.parse(
            event("notification:com.capitalone.mobile", "Car payment SAR 1,200.00 posted"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("1200.00"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.merchant).isEqualTo("Car payment")
    }

    @Test
    fun `parses vehicle finance installment notification with shared car payment label`() {
        val result = parser.parse(
            event("notification:com.emiratesnbd.android", "Vehicle finance installment AED 1,250.00 debited"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("1250.00"))
        assertThat(result.amount.currency).isEqualTo("AED")
        assertThat(result.merchant).isEqualTo("Car payment")
    }

    @Test
    fun `parses Arabic car installment notification with shared car payment label`() {
        val result = parser.parse(
            event("notification:com.alrajhibank.alrajhimobile", "تم سداد قسط سيارة بمبلغ ١٢٠٠ ر.س"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("1200"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.merchant).isEqualTo("Car payment")
    }

    @Test
    fun `ignores car payment offers and reminders with amounts`() {
        val nonPostedMessages = listOf(
            "Your auto loan payment of USD 420.00 is due tomorrow",
            "Car payment SAR 1,200.00 scheduled for tomorrow",
            "Pre-approved car finance offer SAR 75,000.00 available",
            "Vehicle lease reminder AED 1,250.00 upcoming",
            "عرض تمويل سيارة بمبلغ ٨٠٠٠٠ ر.س متاح الآن",
        )

        nonPostedMessages.forEach { body ->
            assertThat(parser.parse(event("notification:com.chase.sig.android", body)))
                .isEqualTo(ParseResult.Ignored)
        }
    }

    @Test
    fun `parses Arabic credit card repayment notification with shared debt label`() {
        val result = parser.parse(
            event("notification:com.alrajhibank.alrajhimobile", "تم سداد بطاقة ائتمانية بمبلغ ٥٠٠ ر.س"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("500"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.merchant).isEqualTo("Credit card payment")
    }

    @Test
    fun `ignores loan installment due reminder notifications with amounts`() {
        assertThat(
            parser.parse(
                event("notification:com.chase.sig.android", "Your loan installment of USD 300.00 is due tomorrow"),
            ),
        ).isEqualTo(ParseResult.Ignored)
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
    fun `parses digit-starting paid-you income counterparty notification`() {
        val result = parser.parse(
            event("notification:com.squareup.cash", "3M Payroll paid you USD 250.00"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.INCOME)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("250.00"))
        assertThat(result.amount.currency).isEqualTo("USD")
        assertThat(result.counterparty).isEqualTo("3M Payroll")
    }

    @Test
    fun `does not use numeric-only paid-you income counterparty notification`() {
        val result = parser.parse(
            event("notification:com.squareup.cash", "123456789 paid you USD 25.00"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.INCOME)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("25.00"))
        assertThat(result.amount.currency).isEqualTo("USD")
        assertThat(result.counterparty).isNull()
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
    fun `parses transaction of amount at merchant notification`() {
        val result = parser.parse(
            event("notification:com.chase.sig.android", "Transaction of USD 23.10 at Trader Joe's was approved"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("23.10"))
        assertThat(result.amount.currency).isEqualTo("USD")
        assertThat(result.merchant).isEqualTo("Trader Joe's")
    }

    @Test
    fun `parses title merchant before card purchase amount notification`() {
        val result = parser.parse(
            event("notification:com.alrajhibank.alrajhimobile", "Noon\nCard purchase SAR 99.00 approved"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("99.00"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.merchant).isEqualTo("Noon")
    }

    @Test
    fun `parses title merchant before amount first card transaction notification`() {
        val result = parser.parse(
            event("notification:com.emiratesnbd.android", "Carrefour\nAED 42.00 card transaction settled"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("42.00"))
        assertThat(result.amount.currency).isEqualTo("AED")
        assertThat(result.merchant).isEqualTo("Carrefour")
    }

    @Test
    fun `parses title merchant before status line and amount notification`() {
        val result = parser.parse(
            event("notification:com.dbsmbanking", "Toast Box\nPurchase processed\nSGD 6.40"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("6.40"))
        assertThat(result.amount.currency).isEqualTo("SGD")
        assertThat(result.merchant).isEqualTo("Toast Box")
    }

    @Test
    fun `does not use bank app title as merchant when notification has no merchant hint`() {
        val result = parser.parse(
            event("notification:com.wise.android", "Wise\nCard purchase SAR 42.00 approved"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("42.00"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.merchant).isNull()
    }

    @Test
    fun `parses merchant line after card purchase amount notification`() {
        val result = parser.parse(
            event("notification:com.alrajhibank.alrajhimobile", "Card purchase SAR 99.00\nNoon"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("99.00"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.merchant).isEqualTo("Noon")
    }

    @Test
    fun `parses merchant line after status suffix notification`() {
        val result = parser.parse(
            event("notification:com.emiratesnbd.android", "AED 42.00 card transaction settled\nCarrefour"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("42.00"))
        assertThat(result.amount.currency).isEqualTo("AED")
        assertThat(result.merchant).isEqualTo("Carrefour")
    }

    @Test
    fun `does not use bank app body line as merchant after amount notification`() {
        val result = parser.parse(
            event("notification:com.wise.android", "Card purchase SAR 42.00\nWise"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("42.00"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.merchant).isNull()
    }

    @Test
    fun `skips merchant label line and parses following merchant after amount notification`() {
        val result = parser.parse(
            event("notification:com.alrajhibank.alrajhimobile", "Card purchase SAR 99.00\nMerchant\nNoon"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("99.00"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.merchant).isEqualTo("Noon")
    }

    @Test
    fun `skips location label line and parses following merchant after amount notification`() {
        val result = parser.parse(
            event("notification:com.emiratesnbd.android", "AED 42.00 card transaction settled\nLocation\nCarrefour"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("42.00"))
        assertThat(result.amount.currency).isEqualTo("AED")
        assertThat(result.merchant).isEqualTo("Carrefour")
    }

    @Test
    fun `does not use merchant label only body line after amount notification`() {
        val result = parser.parse(
            event("notification:com.wise.android", "Card purchase SAR 42.00\nMerchant"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("42.00"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.merchant).isNull()
    }

    @Test
    fun `skips date metadata line and parses following merchant after amount notification`() {
        val result = parser.parse(
            event("notification:com.alrajhibank.alrajhimobile", "Card purchase SAR 99.00\nDate 15 Jun 2026 14:03\nNoon"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("99.00"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.merchant).isEqualTo("Noon")
    }

    @Test
    fun `skips reference metadata line and parses following merchant after amount notification`() {
        val result = parser.parse(
            event("notification:com.emiratesnbd.android", "Card purchase AED 42.00\nReference 123456789\nCarrefour"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("42.00"))
        assertThat(result.amount.currency).isEqualTo("AED")
        assertThat(result.merchant).isEqualTo("Carrefour")
    }

    @Test
    fun `does not use reference metadata only body line after amount notification`() {
        val result = parser.parse(
            event("notification:com.chase.sig.android", "Card purchase USD 12.99\nReference ABC123"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("12.99"))
        assertThat(result.amount.currency).isEqualTo("USD")
        assertThat(result.merchant).isNull()
    }

    @Test
    fun `parses digit-starting title merchant before amount notification`() {
        val result = parser.parse(
            event("notification:com.wise.android", "7-Eleven\nCard purchase USD 8.50 approved"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("8.50"))
        assertThat(result.amount.currency).isEqualTo("USD")
        assertThat(result.merchant).isEqualTo("7-Eleven")
    }

    @Test
    fun `parses digit-starting body line merchant after amount notification`() {
        val result = parser.parse(
            event("notification:com.emiratesnbd.android", "Card purchase AED 42.00\n6th Street"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("42.00"))
        assertThat(result.amount.currency).isEqualTo("AED")
        assertThat(result.merchant).isEqualTo("6th Street")
    }

    @Test
    fun `parses digit-starting trailing merchant after amount notification`() {
        val result = parser.parse(
            event("notification:com.alrajhibank.alrajhimobile", "POS purchase SAR 18.00 7-Eleven"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("18.00"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.merchant).isEqualTo("7-Eleven")
    }

    @Test
    fun `does not use numeric identifier body line after amount notification`() {
        val result = parser.parse(
            event("notification:com.chase.sig.android", "Card purchase USD 12.99\n123456789"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("12.99"))
        assertThat(result.amount.currency).isEqualTo("USD")
        assertThat(result.merchant).isNull()
    }

    @Test
    fun `parses digit-starting at hint merchant after amount notification`() {
        val result = parser.parse(
            event("notification:com.wise.android", "You spent USD 8.50 at 7-Eleven"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("8.50"))
        assertThat(result.amount.currency).isEqualTo("USD")
        assertThat(result.merchant).isEqualTo("7-Eleven")
    }

    @Test
    fun `parses digit-starting structured merchant field notification`() {
        val result = parser.parse(
            event("notification:com.alrajhibank.alrajhimobile", "Card purchase SAR 18.00 approved\nMerchant: 7-Eleven"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("18.00"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.merchant).isEqualTo("7-Eleven")
    }

    @Test
    fun `parses digit-starting payment to merchant notification`() {
        val result = parser.parse(
            event("notification:com.chase.sig.android", "Payment to 3M USD 12.99 completed"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("12.99"))
        assertThat(result.amount.currency).isEqualTo("USD")
        assertThat(result.merchant).isEqualTo("3M")
    }

    @Test
    fun `does not use numeric-only structured merchant field notification`() {
        val result = parser.parse(
            event("notification:com.emiratesnbd.android", "Card purchase AED 42.00\nMerchant: 123456789"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("42.00"))
        assertThat(result.amount.currency).isEqualTo("AED")
        assertThat(result.merchant).isNull()
    }

    @Test
    fun `parses pos transaction at merchant amount notification`() {
        val result = parser.parse(
            event("notification:com.emiratesnbd.android", "POS transaction at Carrefour AED 42.00"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("42.00"))
        assertThat(result.amount.currency).isEqualTo("AED")
        assertThat(result.merchant).isEqualTo("Carrefour")
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
        assertThat(result.merchant).isEqualTo("Electric company")
    }

    @Test
    fun `parses generic water bill notification with shared utility label`() {
        val result = parser.parse(
            event("notification:com.chase.sig.android", "Water bill paid USD 80.00"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("80.00"))
        assertThat(result.amount.currency).isEqualTo("USD")
        assertThat(result.merchant).isEqualTo("Water company")
    }

    @Test
    fun `parses generic bill payment notification with shared utility label`() {
        val result = parser.parse(
            event("notification:com.emiratesnbd.android", "Bill payment AED 225.00 completed"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("225.00"))
        assertThat(result.amount.currency).isEqualTo("AED")
        assertThat(result.merchant).isEqualTo("Bill payment")
    }

    @Test
    fun `ignores utility bill due reminder notifications with amounts`() {
        assertThat(
            parser.parse(
                event("notification:com.chase.sig.android", "Your electricity bill of SAR 300.00 is due tomorrow"),
            ),
        ).isEqualTo(ParseResult.Ignored)
    }

    @Test
    fun `parses generic subscription payment notification with shared label`() {
        val result = parser.parse(
            event("notification:com.chase.sig.android", "Subscription payment USD 39.00 completed"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("39.00"))
        assertThat(result.amount.currency).isEqualTo("USD")
        assertThat(result.merchant).isEqualTo("Subscription payment")
    }

    @Test
    fun `parses posted subscription renewal and preserves merchant before renewal phrase`() {
        val result = parser.parse(
            event("notification:com.chase.sig.android", "Your Netflix subscription renewed for USD 9.99"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("9.99"))
        assertThat(result.amount.currency).isEqualTo("USD")
        assertThat(result.merchant).isEqualTo("Netflix")
    }

    @Test
    fun `parses posted subscription renewal and preserves merchant after for hint`() {
        val result = parser.parse(
            event("notification:com.emiratesnbd.android", "Subscription renewal for Spotify AED 19.99 completed"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("19.99"))
        assertThat(result.amount.currency).isEqualTo("AED")
        assertThat(result.merchant).isEqualTo("Spotify")
    }

    @Test
    fun `parses Arabic posted subscription renewal and preserves merchant`() {
        val result = parser.parse(
            event("notification:com.alrajhibank.alrajhimobile", "تم تجديد اشتراك نتفلكس بمبلغ ٣٩ ر.س"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("39"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.merchant).isEqualTo("نتفلكس")
    }

    @Test
    fun `ignores subscription membership and rent offer notifications with amounts`() {
        val nonPosted = listOf(
            "Membership fee offer USD 49.00 today",
            "Recurring payment promo SAR 29.00",
            "Rent payment discount SAR 2,500.00",
            "عرض سداد إيجار ٢٥٠٠ ر.س",
        )

        nonPosted.forEach { body ->
            assertThat(parser.parse(event("notification:com.chase.sig.android", body))).isEqualTo(ParseResult.Ignored)
        }
    }

    @Test
    fun `ignores merchant-specific subscription renewal reminders with amounts`() {
        assertThat(
            parser.parse(
                event("notification:com.chase.sig.android", "Your Netflix subscription renews tomorrow for USD 9.99"),
            ),
        ).isEqualTo(ParseResult.Ignored)
    }

    @Test
    fun `parses generic insurance premium notification with shared label`() {
        val result = parser.parse(
            event("notification:com.emiratesnbd.android", "Insurance premium AED 500.00 completed"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("500.00"))
        assertThat(result.amount.currency).isEqualTo("AED")
        assertThat(result.merchant).isEqualTo("Insurance premium")
    }

    @Test
    fun `preserves specific insurance merchant on premium notification`() {
        val result = parser.parse(
            event("notification:com.emiratesnbd.android", "Insurance premium paid to Tawuniya AED 500.00"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("500.00"))
        assertThat(result.amount.currency).isEqualTo("AED")
        assertThat(result.merchant).isEqualTo("Tawuniya")
    }

    @Test
    fun `ignores insurance premium quote offer and estimate notifications with amounts`() {
        val nonPosted = listOf(
            "Policy premium quote AED 500.00 is ready",
            "Insurance offer SAR 650.00 for your vehicle policy",
            "Your insurance estimate is USD 900.00",
            "عرض تأمين بمبلغ ٩٠٠ ر.س",
        )

        nonPosted.forEach { body ->
            assertThat(parser.parse(event("notification:com.emiratesnbd.android", body))).isEqualTo(ParseResult.Ignored)
        }
    }

    @Test
    fun `parses generic rent payment notification with shared label`() {
        val result = parser.parse(
            event("notification:com.alrajhibank.alrajhimobile", "Rent payment SAR 2,500.00 completed"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("2500.00"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.merchant).isEqualTo("Rent payment")
    }

    @Test
    fun `parses Arabic rent payment notification with shared label`() {
        val result = parser.parse(
            event("notification:com.alrajhibank.alrajhimobile", "تم سداد إيجار بمبلغ ٢٥٠٠ ر.س"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("2500"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.merchant).isEqualTo("Rent payment")
    }

    @Test
    fun `parses generic condo fee notification with shared label`() {
        val result = parser.parse(
            event("notification:com.chase.sig.android", "HOA fee payment USD 275.00 posted"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("275.00"))
        assertThat(result.amount.currency).isEqualTo("USD")
        assertThat(result.merchant).isEqualTo("Condo fees")
    }

    @Test
    fun `parses building service charge notification with shared condo fee label`() {
        val result = parser.parse(
            event("notification:com.emiratesnbd.android", "Building service charge AED 900.00 paid"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("900.00"))
        assertThat(result.amount.currency).isEqualTo("AED")
        assertThat(result.merchant).isEqualTo("Condo fees")
    }

    @Test
    fun `parses Arabic condo fee notification with shared label`() {
        val result = parser.parse(
            event("notification:com.alrajhibank.alrajhimobile", "تم سداد رسوم السكن بمبلغ ٤٥٠ ر.س"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("450"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.merchant).isEqualTo("Condo fees")
    }

    @Test
    fun `preserves specific condo fee merchant when present`() {
        val result = parser.parse(
            event("notification:com.chase.sig.android", "Condo fee paid to Marina Heights HOA USD 275.00"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("275.00"))
        assertThat(result.amount.currency).isEqualTo("USD")
        assertThat(result.merchant).isEqualTo("Marina Heights HOA")
    }

    @Test
    fun `ignores condo fee reminder notifications with amounts`() {
        val reminders = listOf(
            "Your HOA fee of USD 275.00 is due tomorrow",
            "Condo fee reminder AED 900.00",
            "Building service charge assessment notice USD 1200.00",
            "رسوم السكن مستحقة غداً ٤٥٠ ر.س",
        )

        reminders.forEach { body ->
            assertThat(parser.parse(event("notification:com.chase.sig.android", body))).isEqualTo(ParseResult.Ignored)
        }
    }

    @Test
    fun `ignores recurring expense reminder notifications with amounts`() {
        val reminders = listOf(
            "Your rent payment of SAR 2,500.00 is due tomorrow",
            "Your insurance premium of AED 500.00 is due tomorrow",
            "Your subscription renews tomorrow for USD 9.99",
            "تذكير: إيجار مستحق ٢٥٠٠ ر.س",
        )

        reminders.forEach { body ->
            assertThat(parser.parse(event("notification:com.chase.sig.android", body))).isEqualTo(ParseResult.Ignored)
        }
    }

    @Test
    fun `parses generic mobile top up notification as telecom expense`() {
        val result = parser.parse(
            event("notification:com.alrajhibank.alrajhimobile", "Mobile top up SAR 50.00 completed"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("50.00"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.merchant).isEqualTo("Mobile recharge")
    }

    @Test
    fun `parses airtime purchase notification as telecom expense`() {
        val result = parser.parse(
            event("notification:com.emiratesnbd.android", "Airtime purchase AED 25.00 completed"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("25.00"))
        assertThat(result.amount.currency).isEqualTo("AED")
        assertThat(result.merchant).isEqualTo("Mobile recharge")
    }

    @Test
    fun `preserves mobile carrier on recharge notification`() {
        val result = parser.parse(
            event("notification:com.alrajhibank.alrajhimobile", "Mobile recharge paid to Mobily SAR 50.00"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("50.00"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.merchant).isEqualTo("Mobily")
    }

    @Test
    fun `parses Arabic mobile recharge notification as telecom expense`() {
        val result = parser.parse(
            event("notification:com.alrajhibank.alrajhimobile", "تم شحن رصيد الجوال بمبلغ ٥٠ ر.س"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("50"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.merchant).isEqualTo("Mobile recharge")
    }

    @Test
    fun `keeps non telecom wallet top up notification as income`() {
        val result = parser.parse(
            event("notification:com.revolut.revolut", "Wallet top up SAR 100.00 from Bank Account"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.INCOME)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("100.00"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.counterparty).isEqualTo("Bank Account")
    }

    @Test
    fun `ignores mobile recharge promotional notifications with amounts`() {
        assertThat(
            parser.parse(
                event("notification:com.alrajhibank.alrajhimobile", "Get 20% bonus on your next mobile top-up SAR 50.00"),
            ),
        ).isEqualTo(ParseResult.Ignored)
    }

    @Test
    fun `parses traffic fine payment notification with shared public service label`() {
        val result = parser.parse(
            event("notification:com.alrajhibank.alrajhimobile", "Traffic fine payment SAR 300.00 completed"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("300.00"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.merchant).isEqualTo("Traffic fine payment")
    }

    @Test
    fun `parses government service payment notification with shared public service label`() {
        val result = parser.parse(
            event("notification:com.emiratesnbd.android", "Government service payment AED 150.00 completed"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("150.00"))
        assertThat(result.amount.currency).isEqualTo("AED")
        assertThat(result.merchant).isEqualTo("Government service payment")
    }

    @Test
    fun `preserves specific agency on government service payment notification`() {
        val result = parser.parse(
            event(
                "notification:com.alrajhibank.alrajhimobile",
                "Government service payment SAR 100.00 to Ministry of Interior",
            ),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("100.00"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.merchant).isEqualTo("Ministry of Interior")
    }

    @Test
    fun `parses Arabic traffic fine payment notification with shared public service label`() {
        val result = parser.parse(
            event("notification:com.alrajhibank.alrajhimobile", "تم سداد مخالفة مرورية بمبلغ ٣٠٠ ر.س"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("300"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.merchant).isEqualTo("Traffic fine payment")
    }

    @Test
    fun `ignores public service due reminder notifications with amounts`() {
        val reminders = listOf(
            "Your traffic fine of SAR 300.00 is due tomorrow",
            "Government service fee of AED 150.00 is due tomorrow",
            "تذكير: مخالفة مرورية مستحقة ٣٠٠ ر.س",
        )

        reminders.forEach { body ->
            assertThat(parser.parse(event("notification:com.alrajhibank.alrajhimobile", body))).isEqualTo(ParseResult.Ignored)
        }
    }

    @Test
    fun `ignores public service and mobility quotes estimates and discounts with amounts`() {
        val nonPosted = listOf(
            "Government service fee estimate AED 150.00",
            "Traffic fine payment discount SAR 300.00",
            "Parking fee estimate SAR 12.00",
            "Transit fare estimate USD 2.75",
            "عرض سداد مخالفة مرورية ٣٠٠ ر.س",
        )

        nonPosted.forEach { body ->
            assertThat(parser.parse(event("notification:com.alrajhibank.alrajhimobile", body))).isEqualTo(ParseResult.Ignored)
        }
    }

    @Test
    fun `does not treat fine dining notification as traffic fine payment`() {
        val result = parser.parse(
            event("notification:com.chase.sig.android", "Fine Dining charged your card SAR 80.00"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("80.00"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.merchant).isEqualTo("Fine Dining")
    }

    @Test
    fun `parses parking payment notification with shared mobility label`() {
        val result = parser.parse(
            event("notification:com.alrajhibank.alrajhimobile", "Parking payment SAR 12.00 completed"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("12.00"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.merchant).isEqualTo("Parking payment")
    }

    @Test
    fun `preserves specific parking operator on parking payment notification`() {
        val result = parser.parse(
            event("notification:com.alrajhibank.alrajhimobile", "Parking payment SAR 12.00 at Riyadh Parking"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("12.00"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.merchant).isEqualTo("Riyadh Parking")
    }

    @Test
    fun `parses toll payment notification with shared mobility label`() {
        val result = parser.parse(
            event("notification:com.emiratesnbd.android", "Road toll payment AED 8.00 posted"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("8.00"))
        assertThat(result.amount.currency).isEqualTo("AED")
        assertThat(result.merchant).isEqualTo("Toll payment")
    }

    @Test
    fun `parses transit fare notification with shared mobility label`() {
        val result = parser.parse(
            event("notification:com.chase.sig.android", "Transit fare USD 2.75 charged"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("2.75"))
        assertThat(result.amount.currency).isEqualTo("USD")
        assertThat(result.merchant).isEqualTo("Transit fare")
    }

    @Test
    fun `parses Arabic transit fare notification with shared mobility label`() {
        val result = parser.parse(
            event("notification:com.alrajhibank.alrajhimobile", "تم دفع تذكرة المترو بمبلغ ٤ ر.س"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("4"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.merchant).isEqualTo("Transit fare")
    }

    @Test
    fun `ignores mobility due reminder notifications with amounts`() {
        val reminders = listOf(
            "Your parking session expires with SAR 12.00 unpaid",
            "Unpaid toll AED 8.00 is due tomorrow",
            "تذكير: مواقف مستحقة ١٢ ر.س",
        )

        reminders.forEach { body ->
            assertThat(parser.parse(event("notification:com.alrajhibank.alrajhimobile", body))).isEqualTo(ParseResult.Ignored)
        }
    }

    @Test
    fun `keeps parking fine payment on public service label`() {
        val result = parser.parse(
            event("notification:com.alrajhibank.alrajhimobile", "Parking fine payment SAR 120.00 completed"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("120.00"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.merchant).isEqualTo("Traffic fine payment")
    }

    @Test
    fun `parses generic medical payment notification with shared label`() {
        val result = parser.parse(
            event("notification:com.alrajhibank.alrajhimobile", "Medical payment SAR 220.00 completed"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("220.00"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.merchant).isEqualTo("Medical payment")
    }

    @Test
    fun `parses pharmacy purchase notification with shared label`() {
        val result = parser.parse(
            event("notification:com.emiratesnbd.android", "Pharmacy purchase AED 75.00 posted"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("75.00"))
        assertThat(result.amount.currency).isEqualTo("AED")
        assertThat(result.merchant).isEqualTo("Pharmacy payment")
    }

    @Test
    fun `preserves specific clinic merchant on medical notification`() {
        val result = parser.parse(
            event("notification:com.alrajhibank.alrajhimobile", "Medical payment SAR 120.00 at Nahdi Care Clinic"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("120.00"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.merchant).isEqualTo("Nahdi Care Clinic")
    }

    @Test
    fun `parses education payment notification with shared label`() {
        val result = parser.parse(
            event("notification:com.chase.sig.android", "Tuition payment USD 500.00 completed"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("500.00"))
        assertThat(result.amount.currency).isEqualTo("USD")
        assertThat(result.merchant).isEqualTo("Education payment")
    }

    @Test
    fun `parses Arabic school fee notification with shared label`() {
        val result = parser.parse(
            event("notification:com.alrajhibank.alrajhimobile", "تم سداد رسوم مدرسية بمبلغ ١٥٠٠ ر.س"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("1500"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.merchant).isEqualTo("Education payment")
    }

    @Test
    fun `parses zakat payment notification with shared label`() {
        val result = parser.parse(
            event("notification:com.alrajhibank.alrajhimobile", "Zakat payment SAR 250.00 completed"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("250.00"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.merchant).isEqualTo("Zakat payment")
    }

    @Test
    fun `preserves specific charity merchant on donation notification`() {
        val result = parser.parse(
            event("notification:com.emiratesnbd.android", "Donation payment AED 100.00 to Red Crescent"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("100.00"))
        assertThat(result.amount.currency).isEqualTo("AED")
        assertThat(result.merchant).isEqualTo("Red Crescent")
    }

    @Test
    fun `ignores essential life quotes estimates and status copy with amounts`() {
        val nonPosted = listOf(
            "Clinic visit estimate SAR 220.00",
            "Dental consultation quote AED 350.00",
            "Tuition fee estimate USD 500.00",
            "تقدير رسوم مدرسية ١٥٠٠ ر.س",
        )

        nonPosted.forEach { body ->
            assertThat(parser.parse(event("notification:com.alrajhibank.alrajhimobile", body)))
                .isEqualTo(ParseResult.Ignored)
        }
    }

    @Test
    fun `ignores essential life reminders and charity appeals with amounts`() {
        val reminders = listOf(
            "Your school fees of SAR 1,500.00 are due tomorrow",
            "Medical bill reminder: SAR 220.00 is due tomorrow",
            "Donate SAR 50.00 today to support the campaign",
            "تذكير: رسوم مدرسية مستحقة ١٥٠٠ ر.س",
        )

        reminders.forEach { body ->
            assertThat(parser.parse(event("notification:com.alrajhibank.alrajhimobile", body)))
                .isEqualTo(ParseResult.Ignored)
        }
    }

    @Test
    fun `parses gift card purchase notification with shared label`() {
        val result = parser.parse(
            event("notification:com.chase.sig.android", "Gift card purchase USD 50.00 completed"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("50.00"))
        assertThat(result.amount.currency).isEqualTo("USD")
        assertThat(result.merchant).isEqualTo("Gift card purchase")
    }

    @Test
    fun `parses gift purchase notification with shared label`() {
        val result = parser.parse(
            event("notification:com.emiratesnbd.android", "Gift payment AED 220.00 posted"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("220.00"))
        assertThat(result.amount.currency).isEqualTo("AED")
        assertThat(result.merchant).isEqualTo("Gift purchase")
    }

    @Test
    fun `preserves flower merchant on delivery notification`() {
        val result = parser.parse(
            event("notification:com.alrajhibank.alrajhimobile", "Flower delivery payment SAR 180.00 to Floward"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("180.00"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.merchant).isEqualTo("Floward")
    }

    @Test
    fun `parses Arabic gift card purchase notification with shared label`() {
        val result = parser.parse(
            event("notification:com.alrajhibank.alrajhimobile", "تم شراء بطاقة هدية بمبلغ ١٠٠ ر.س"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("100"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.merchant).isEqualTo("Gift card purchase")
    }

    @Test
    fun `ignores gift promos balances and reminders with amounts`() {
        val nonPostedMessages = listOf(
            "Free gift card offer USD 25.00 today",
            "Gift card balance USD 50.00 available",
            "Flower delivery reminder SAR 180.00 due tomorrow",
            "عرض بطاقة هدية بقيمة ١٠٠ ر.س اليوم",
        )

        nonPostedMessages.forEach { body ->
            assertThat(parser.parse(event("notification:com.chase.sig.android", body)))
                .isEqualTo(ParseResult.Ignored)
        }
    }

    @Test
    fun `parses furniture purchase notification with shared label`() {
        val result = parser.parse(
            event("notification:com.chase.sig.android", "Furniture purchase SAR 900.00 completed"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("900.00"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.merchant).isEqualTo("Furniture purchase")
    }

    @Test
    fun `parses home goods purchase notification with shared label`() {
        val result = parser.parse(
            event("notification:com.revolut.revolut", "Home goods payment USD 75.00 posted"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("75.00"))
        assertThat(result.amount.currency).isEqualTo("USD")
        assertThat(result.merchant).isEqualTo("Home goods purchase")
    }

    @Test
    fun `preserves home goods store on appliance purchase notification`() {
        val result = parser.parse(
            event("notification:com.emiratesnbd.android", "Appliance purchase AED 1,200.00 at IKEA"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("1200.00"))
        assertThat(result.amount.currency).isEqualTo("AED")
        assertThat(result.merchant).isEqualTo("IKEA")
    }

    @Test
    fun `parses Arabic furniture purchase notification with shared label`() {
        val result = parser.parse(
            event("notification:com.alrajhibank.alrajhimobile", "تم شراء أثاث بمبلغ ٨٠٠ ر.س"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("800"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.merchant).isEqualTo("Furniture purchase")
    }

    @Test
    fun `ignores home goods quotes carts delivery statuses and offers with amounts`() {
        val nonPostedMessages = listOf(
            "Furniture quote SAR 900.00 is ready",
            "Appliance delivery update AED 1,200.00 scheduled tomorrow",
            "Home decor cart reminder USD 75.00",
            "عرض خصم ٢٠٠ ر.س على أثاث المنزل",
        )

        nonPostedMessages.forEach { body ->
            assertThat(parser.parse(event("notification:com.chase.sig.android", body)))
                .isEqualTo(ParseResult.Ignored)
        }
    }

    @Test
    fun `parses home service payment notification with shared label`() {
        val result = parser.parse(
            event("notification:com.alrajhibank.alrajhimobile", "Home repair payment SAR 350.00 completed"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("350.00"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.merchant).isEqualTo("Home service payment")
    }

    @Test
    fun `preserves home service merchant on maintenance notification`() {
        val result = parser.parse(
            event("notification:com.chase.sig.android", "Cleaning service payment USD 120.00 to Handy"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("120.00"))
        assertThat(result.amount.currency).isEqualTo("USD")
        assertThat(result.merchant).isEqualTo("Handy")
    }

    @Test
    fun `parses gym membership notification with shared label`() {
        val result = parser.parse(
            event("notification:com.revolut.revolut", "Gym membership payment GBP 45.00 completed"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("45.00"))
        assertThat(result.amount.currency).isEqualTo("GBP")
        assertThat(result.merchant).isEqualTo("Gym membership")
    }

    @Test
    fun `parses childcare fee notification with shared label`() {
        val result = parser.parse(
            event("notification:com.emiratesnbd.android", "Daycare fee payment AED 800.00 posted"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("800.00"))
        assertThat(result.amount.currency).isEqualTo("AED")
        assertThat(result.merchant).isEqualTo("Childcare payment")
    }

    @Test
    fun `parses Arabic home service notification with shared label`() {
        val result = parser.parse(
            event("notification:com.alrajhibank.alrajhimobile", "تم سداد صيانة منزلية بمبلغ ٣٠٠ ر.س"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("300"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.merchant).isEqualTo("Home service payment")
    }

    @Test
    fun `ignores life admin offers quotes and reminders with amounts`() {
        val nonPostedMessages = listOf(
            "Home repair quote SAR 350.00 is ready",
            "Gym membership offer GBP 45.00 this month",
            "Daycare fee reminder AED 800.00 due tomorrow",
            "عرض خصم ٥٠ ر.س على خدمات التنظيف",
        )

        nonPostedMessages.forEach { body ->
            assertThat(parser.parse(event("notification:com.alrajhibank.alrajhimobile", body)))
                .isEqualTo(ParseResult.Ignored)
        }
    }

    @Test
    fun `parses car service payment notification with shared label`() {
        val result = parser.parse(
            event("notification:com.alrajhibank.alrajhimobile", "Vehicle service payment SAR 450.00 completed"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("450.00"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.merchant).isEqualTo("Car service payment")
    }

    @Test
    fun `preserves automotive provider on oil change notification`() {
        val result = parser.parse(
            event("notification:com.chase.sig.android", "Oil change payment USD 90.00 at Petromin"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("90.00"))
        assertThat(result.amount.currency).isEqualTo("USD")
        assertThat(result.merchant).isEqualTo("Petromin")
    }

    @Test
    fun `parses car wash notification with shared label`() {
        val result = parser.parse(
            event("notification:com.emiratesnbd.android", "Car wash payment AED 30.00 posted"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("30.00"))
        assertThat(result.amount.currency).isEqualTo("AED")
        assertThat(result.merchant).isEqualTo("Car wash payment")
    }

    @Test
    fun `parses tire service notification with shared label`() {
        val result = parser.parse(
            event("notification:com.revolut.revolut", "Tyre replacement fee GBP 120.00 paid"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("120.00"))
        assertThat(result.amount.currency).isEqualTo("GBP")
        assertThat(result.merchant).isEqualTo("Tire service payment")
    }

    @Test
    fun `parses Arabic oil change notification with shared label`() {
        val result = parser.parse(
            event("notification:com.alrajhibank.alrajhimobile", "تم سداد تغيير زيت بمبلغ ٩٠ ر.س"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("90"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.merchant).isEqualTo("Oil change payment")
    }

    @Test
    fun `ignores automotive maintenance quotes offers and reminders with amounts`() {
        val nonPostedMessages = listOf(
            "Car repair quote SAR 450.00 is ready",
            "Oil change reminder USD 90.00 due tomorrow",
            "Car wash offer AED 30.00 this week",
            "عرض خصم ٥٠ ر.س على صيانة سيارة",
        )

        nonPostedMessages.forEach { body ->
            assertThat(parser.parse(event("notification:com.alrajhibank.alrajhimobile", body)))
                .isEqualTo(ParseResult.Ignored)
        }
    }

    @Test
    fun `parses fuel payment notification with shared gas label`() {
        val result = parser.parse(
            event("notification:com.alrajhibank.alrajhimobile", "Fuel payment SAR 80.00 completed"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("80.00"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.merchant).isEqualTo("Fuel purchase")
    }

    @Test
    fun `preserves fuel station merchant on fuel notification`() {
        val result = parser.parse(
            event("notification:com.alrajhibank.alrajhimobile", "Fuel payment SAR 80.00 at Aldrees"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("80.00"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.merchant).isEqualTo("Aldrees")
    }

    @Test
    fun `parses grocery purchase notification with shared grocery label`() {
        val result = parser.parse(
            event("notification:com.cibc.android.mobi", "Grocery purchase CAD 45.67 posted"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("45.67"))
        assertThat(result.amount.currency).isEqualTo("CAD")
        assertThat(result.merchant).isEqualTo("Grocery purchase")
    }

    @Test
    fun `parses supermarket purchase notification with shared grocery label`() {
        val result = parser.parse(
            event("notification:de.ingdiba.bankingapp", "Supermarket purchase EUR 22,50 completed"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("22.50"))
        assertThat(result.amount.currency).isEqualTo("EUR")
        assertThat(result.merchant).isEqualTo("Grocery purchase")
    }

    @Test
    fun `parses restaurant payment notification with shared restaurant label`() {
        val result = parser.parse(
            event("notification:com.chase.sig.android", "Restaurant payment USD 32.10 completed"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("32.10"))
        assertThat(result.amount.currency).isEqualTo("USD")
        assertThat(result.merchant).isEqualTo("Restaurant payment")
    }

    @Test
    fun `parses coffee payment notification with shared coffee label`() {
        val result = parser.parse(
            event("notification:com.revolut.revolut", "Coffee shop payment GBP 4.20 completed"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("4.20"))
        assertThat(result.amount.currency).isEqualTo("GBP")
        assertThat(result.merchant).isEqualTo("Coffee payment")
    }

    @Test
    fun `parses food delivery payment notification with shared restaurant label`() {
        val result = parser.parse(
            event("notification:com.emiratesnbd.android", "Food delivery payment AED 64.00 completed"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("64.00"))
        assertThat(result.amount.currency).isEqualTo("AED")
        assertThat(result.merchant).isEqualTo("Food delivery payment")
    }

    @Test
    fun `parses taxi ride payment notification with shared transport label`() {
        val result = parser.parse(
            event("notification:com.alrajhibank.alrajhimobile", "Taxi ride payment SAR 18.50 completed"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("18.50"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.merchant).isEqualTo("Taxi ride payment")
    }

    @Test
    fun `preserves ride merchant on ride payment notification`() {
        val result = parser.parse(
            event("notification:com.chase.sig.android", "Ride fare USD 19.00 at Uber"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("19.00"))
        assertThat(result.amount.currency).isEqualTo("USD")
        assertThat(result.merchant).isEqualTo("Uber")
    }

    @Test
    fun `parses Arabic fuel notification with shared gas label`() {
        val result = parser.parse(
            event("notification:com.alrajhibank.alrajhimobile", "تم سداد وقود بمبلغ ٨٠ ر.س"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("80"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.merchant).isEqualTo("Fuel purchase")
    }

    @Test
    fun `ignores everyday commerce promos estimates and reservations with amounts`() {
        val nonPostedMessages = listOf(
            "Save SAR 10.00 on your next grocery purchase",
            "Taxi ride estimate SAR 35.00",
            "Restaurant reservation reminder SAR 50.00",
            "Coffee shop discount AED 12.00",
            "عرض خصم ١٠ ر.س على المطاعم",
        )

        nonPostedMessages.forEach { body ->
            assertThat(parser.parse(event("notification:com.alrajhibank.alrajhimobile", body)))
                .isEqualTo(ParseResult.Ignored)
        }
    }

    @Test
    fun `parses flight ticket payment notification with shared travel label`() {
        val result = parser.parse(
            event("notification:com.revolut.revolut", "Flight ticket payment USD 420.00 completed"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("420.00"))
        assertThat(result.amount.currency).isEqualTo("USD")
        assertThat(result.merchant).isEqualTo("Flight ticket")
    }

    @Test
    fun `preserves airline merchant on flight payment notification`() {
        val result = parser.parse(
            event("notification:com.emiratesnbd.android", "Flight ticket payment AED 750.00 at Qatar Airways"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("750.00"))
        assertThat(result.amount.currency).isEqualTo("AED")
        assertThat(result.merchant).isEqualTo("Qatar Airways")
    }

    @Test
    fun `parses hotel payment notification with shared travel label`() {
        val result = parser.parse(
            event("notification:com.chase.sig.android", "Hotel payment USD 180.25 completed"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("180.25"))
        assertThat(result.amount.currency).isEqualTo("USD")
        assertThat(result.merchant).isEqualTo("Hotel payment")
    }

    @Test
    fun `parses travel booking payment notification with shared travel label`() {
        val result = parser.parse(
            event("notification:com.cibc.android.mobi", "Travel booking payment CAD 300.00 posted"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("300.00"))
        assertThat(result.amount.currency).isEqualTo("CAD")
        assertThat(result.merchant).isEqualTo("Travel booking")
    }

    @Test
    fun `parses car rental payment notification with shared travel label`() {
        val result = parser.parse(
            event("notification:com.capitalone.mobile", "Car rental payment USD 89.90 completed"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("89.90"))
        assertThat(result.amount.currency).isEqualTo("USD")
        assertThat(result.merchant).isEqualTo("Car rental payment")
    }

    @Test
    fun `parses Arabic flight ticket notification with shared travel label`() {
        val result = parser.parse(
            event("notification:com.alrajhibank.alrajhimobile", "تم سداد تذكرة طيران بمبلغ ٩٠٠ ر.س"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("900"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.merchant).isEqualTo("Flight ticket")
    }

    @Test
    fun `ignores travel offers itineraries reservations and quotes with amounts`() {
        val nonPostedMessages = listOf(
            "Save SAR 200.00 on your next flight",
            "Hotel reservation reminder SAR 500.00",
            "Flight itinerary: fare SAR 1,200.00",
            "Car rental quote USD 45.00",
            "عرض خصم ١٠٠ ر.س على الفنادق",
        )

        nonPostedMessages.forEach { body ->
            assertThat(parser.parse(event("notification:com.alrajhibank.alrajhimobile", body)))
                .isEqualTo(ParseResult.Ignored)
        }
    }

    @Test
    fun `parses clothing purchase notification with shared clothing label`() {
        val result = parser.parse(
            event("notification:com.revolut.revolut", "Clothing purchase GBP 89.99 completed"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("89.99"))
        assertThat(result.amount.currency).isEqualTo("GBP")
        assertThat(result.merchant).isEqualTo("Clothing purchase")
    }

    @Test
    fun `preserves clothing merchant on clothing notification`() {
        val result = parser.parse(
            event("notification:com.alrajhibank.alrajhimobile", "Clothing purchase SAR 220.00 at Zara"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("220.00"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.merchant).isEqualTo("Zara")
    }

    @Test
    fun `parses electronics purchase notification with shared electronics label`() {
        val result = parser.parse(
            event("notification:com.chase.sig.android", "Electronics purchase USD 399.00 completed"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("399.00"))
        assertThat(result.amount.currency).isEqualTo("USD")
        assertThat(result.merchant).isEqualTo("Electronics purchase")
    }

    @Test
    fun `preserves electronics merchant on electronics notification`() {
        val result = parser.parse(
            event("notification:com.cibc.android.mobi", "Electronics purchase CAD 129.95 at Best Buy"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("129.95"))
        assertThat(result.amount.currency).isEqualTo("CAD")
        assertThat(result.merchant).isEqualTo("Best Buy")
    }

    @Test
    fun `parses online shopping payment notification with shared shopping label`() {
        val result = parser.parse(
            event("notification:com.emiratesnbd.android", "Online shopping payment AED 79.00 completed"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("79.00"))
        assertThat(result.amount.currency).isEqualTo("AED")
        assertThat(result.merchant).isEqualTo("Online shopping purchase")
    }

    @Test
    fun `parses Arabic clothing purchase notification with shared clothing label`() {
        val result = parser.parse(
            event("notification:com.alrajhibank.alrajhimobile", "تم شراء ملابس بمبلغ ٢٠٠ ر.س"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("200"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.merchant).isEqualTo("Clothing purchase")
    }

    @Test
    fun `ignores retail shopping offers carts and shipping status with amounts`() {
        val nonPostedMessages = listOf(
            "Save SAR 50.00 on your next clothing purchase",
            "Electronics order shipped for USD 399.00",
            "Online shopping cart reminder AED 120.00",
            "Price drop CAD 20.00 on shoes",
            "عرض خصم ٢٠ ر.س على الإلكترونيات",
        )

        nonPostedMessages.forEach { body ->
            assertThat(parser.parse(event("notification:com.alrajhibank.alrajhimobile", body)))
                .isEqualTo(ParseResult.Ignored)
        }
    }

    @Test
    fun `parses cinema ticket notification with shared entertainment label`() {
        val result = parser.parse(
            event("notification:com.revolut.revolut", "Cinema ticket payment GBP 18.50 completed"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("18.50"))
        assertThat(result.amount.currency).isEqualTo("GBP")
        assertThat(result.merchant).isEqualTo("Cinema ticket")
    }

    @Test
    fun `preserves cinema merchant on cinema ticket notification`() {
        val result = parser.parse(
            event("notification:com.alrajhibank.alrajhimobile", "Movie ticket payment SAR 65.00 at VOX Cinemas"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("65.00"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.merchant).isEqualTo("VOX Cinemas")
    }

    @Test
    fun `parses event ticket notification with shared going out label`() {
        val result = parser.parse(
            event("notification:com.emiratesnbd.android", "Event ticket payment AED 150.00 completed"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("150.00"))
        assertThat(result.amount.currency).isEqualTo("AED")
        assertThat(result.merchant).isEqualTo("Event ticket")
    }

    @Test
    fun `preserves event merchant on event ticket notification`() {
        val result = parser.parse(
            event("notification:com.cibc.android.mobi", "Concert ticket payment CAD 85.00 at Riyadh Season"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("85.00"))
        assertThat(result.amount.currency).isEqualTo("CAD")
        assertThat(result.merchant).isEqualTo("Riyadh Season")
    }

    @Test
    fun `parses game purchase notification with shared entertainment label`() {
        val result = parser.parse(
            event("notification:com.chase.sig.android", "Game purchase USD 59.99 completed"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("59.99"))
        assertThat(result.amount.currency).isEqualTo("USD")
        assertThat(result.merchant).isEqualTo("Game purchase")
    }

    @Test
    fun `parses Arabic cinema ticket notification with shared entertainment label`() {
        val result = parser.parse(
            event("notification:com.alrajhibank.alrajhimobile", "تم شراء تذكرة سينما بمبلغ ٥٠ ر.س"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("50"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.merchant).isEqualTo("Cinema ticket")
    }

    @Test
    fun `ignores entertainment promos showtimes reservations and game offers with amounts`() {
        val nonPostedMessages = listOf(
            "Movie ticket discount SAR 20.00 this weekend",
            "Cinema showtime reminder SAR 50.00",
            "Event ticket presale starts at AED 150.00",
            "Game offer USD 5.00 bonus credits",
            "عرض خصم ٢٠ ر.س على تذاكر السينما",
        )

        nonPostedMessages.forEach { body ->
            assertThat(parser.parse(event("notification:com.alrajhibank.alrajhimobile", body)))
                .isEqualTo(ParseResult.Ignored)
        }
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
    fun `parses payment successful merchant before amount notification`() {
        val result = parser.parse(
            event("notification:com.alrajhibank.alrajhimobile", "Payment successful: Noon SAR 99.00"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("99.00"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.merchant).isEqualTo("Noon")
    }

    @Test
    fun `parses transaction completed merchant before amount notification`() {
        val result = parser.parse(
            event("notification:com.emiratesnbd.android", "Transaction completed - Carrefour AED 42.00"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("42.00"))
        assertThat(result.amount.currency).isEqualTo("AED")
        assertThat(result.merchant).isEqualTo("Carrefour")
    }

    @Test
    fun `parses purchase approved merchant before amount notification`() {
        val result = parser.parse(
            event("notification:com.dbsmbanking", "Purchase approved: Toast Box SGD 6.40"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("6.40"))
        assertThat(result.amount.currency).isEqualTo("SGD")
        assertThat(result.merchant).isEqualTo("Toast Box")
    }

    @Test
    fun `parses payment processed merchant before amount notification`() {
        val result = parser.parse(
            event("notification:com.alrajhibank.alrajhimobile", "Payment processed: Noon SAR 99.00"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("99.00"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.merchant).isEqualTo("Noon")
    }

    @Test
    fun `parses transaction settled merchant before amount notification`() {
        val result = parser.parse(
            event("notification:com.emiratesnbd.android", "Transaction settled - Carrefour AED 42.00"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("42.00"))
        assertThat(result.amount.currency).isEqualTo("AED")
        assertThat(result.merchant).isEqualTo("Carrefour")
    }

    @Test
    fun `parses purchase processed merchant before amount notification`() {
        val result = parser.parse(
            event("notification:com.dbsmbanking", "Purchase processed: Toast Box SGD 6.40"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("6.40"))
        assertThat(result.amount.currency).isEqualTo("SGD")
        assertThat(result.merchant).isEqualTo("Toast Box")
    }

    @Test
    fun `parses card used merchant before amount notification`() {
        val result = parser.parse(
            event("notification:com.alrajhibank.alrajhimobile", "Card used: Lulu SAR 55.00"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("55.00"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.merchant).isEqualTo("Lulu")
    }

    @Test
    fun `parses card ending in used merchant before amount notification`() {
        val result = parser.parse(
            event("notification:com.chase.sig.android", "Card ending in 1234 was used - Amazon USD 12.99"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("12.99"))
        assertThat(result.amount.currency).isEqualTo("USD")
        assertThat(result.merchant).isEqualTo("Amazon")
    }

    @Test
    fun `parses card used at merchant before amount notification`() {
        val result = parser.parse(
            event("notification:com.alrajhibank.alrajhimobile", "Your card was used at Lulu for SAR 55.00"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("55.00"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.merchant).isEqualTo("Lulu")
    }

    @Test
    fun `parses debit card used at merchant before amount notification`() {
        val result = parser.parse(
            event("notification:com.emiratesnbd.android", "Debit card used at Carrefour AED 42.00"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("42.00"))
        assertThat(result.amount.currency).isEqualTo("AED")
        assertThat(result.merchant).isEqualTo("Carrefour")
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
    fun `parses labeled location notification fields`() {
        val result = parser.parse(
            event(
                "notification:com.emiratesnbd.android",
                """
                Card purchase
                Amount: AED 42.00
                Location: Carrefour
                """.trimIndent(),
            ),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("42.00"))
        assertThat(result.amount.currency).isEqualTo("AED")
        assertThat(result.merchant).isEqualTo("Carrefour")
    }

    @Test
    fun `parses labeled merchant name notification fields`() {
        val result = parser.parse(
            event(
                "notification:com.dbsmbanking",
                """
                Card transaction
                Amount SGD 19.90
                Merchant Name: Toast Box
                """.trimIndent(),
            ),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("19.90"))
        assertThat(result.amount.currency).isEqualTo("SGD")
        assertThat(result.merchant).isEqualTo("Toast Box")
    }

    @Test
    fun `parses labeled card acceptor notification fields`() {
        val result = parser.parse(
            event(
                "notification:com.barclays.android",
                """
                Debit card purchase
                GBP 42.00
                Card acceptor: IKEA
                """.trimIndent(),
            ),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("42.00"))
        assertThat(result.amount.currency).isEqualTo("GBP")
        assertThat(result.merchant).isEqualTo("IKEA")
    }

    @Test
    fun `parses recipient label as merchant on posted payment notification`() {
        val result = parser.parse(
            event(
                "notification:com.chase.sig.android",
                """
                Payment completed
                Amount: SAR 75.00
                Recipient: Amazon
                """.trimIndent(),
            ),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("75.00"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.merchant).isEqualTo("Amazon")
    }

    @Test
    fun `parses beneficiary label as merchant on card purchase notification`() {
        val result = parser.parse(
            event(
                "notification:com.barclays.android",
                """
                Card purchase
                Amount: GBP 9.99
                Beneficiary: Spotify
                """.trimIndent(),
            ),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("9.99"))
        assertThat(result.amount.currency).isEqualTo("GBP")
        assertThat(result.merchant).isEqualTo("Spotify")
    }

    @Test
    fun `parses receiver label as merchant on paid notification`() {
        val result = parser.parse(
            event(
                "notification:com.squareup.cash",
                """
                Paid AED 24.50
                Receiver: Careem
                """.trimIndent(),
            ),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("24.50"))
        assertThat(result.amount.currency).isEqualTo("AED")
        assertThat(result.merchant).isEqualTo("Careem")
    }

    @Test
    fun `parses Arabic beneficiary label as merchant on payment notification`() {
        val result = parser.parse(
            event(
                "notification:com.alrajhibank.alrajhimobile",
                """
                تم دفع ٤٥ ر.س
                المستفيد: هنقرستيشن
                """.trimIndent(),
            ),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.EXPENSE)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("45"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.merchant).isEqualTo("هنقرستيشن")
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
    fun `parses digit-starting labeled sender income notification field`() {
        val result = parser.parse(
            event(
                "notification:com.mercury",
                """
                Incoming transfer
                Amount: USD 250.00
                Sender: 3M Payroll
                """.trimIndent(),
            ),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.INCOME)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("250.00"))
        assertThat(result.amount.currency).isEqualTo("USD")
        assertThat(result.counterparty).isEqualTo("3M Payroll")
    }

    @Test
    fun `parses digit-starting labeled recipient transfer notification field`() {
        val result = parser.parse(
            event(
                "notification:com.chase.sig.android",
                """
                Transfer sent
                Amount: USD 100.00
                Recipient: 401K Savings
                """.trimIndent(),
            ),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.TRANSFER)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("100.00"))
        assertThat(result.amount.currency).isEqualTo("USD")
        assertThat(result.counterparty).isEqualTo("401K Savings")
    }

    @Test
    fun `does not use numeric-only labeled transfer counterparty field`() {
        val result = parser.parse(
            event(
                "notification:com.chase.sig.android",
                """
                Transfer sent
                Amount: USD 100.00
                Recipient: 123456789
                """.trimIndent(),
            ),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.TRANSFER)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("100.00"))
        assertThat(result.amount.currency).isEqualTo("USD")
        assertThat(result.counterparty).isNull()
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
        assertThat(result.merchant).isEqualTo("Electric company")
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
    fun `parses Canadian bank package notifications`() {
        val cibc = parser.parse(
            event("notification:com.cibc.android.mobi", "You spent CAD 12.34 at Tim Hortons"),
        ) as ParseResult.Success
        val bmo = parser.parse(
            event("notification:com.bmo.mobile", "Card transaction CAD 45.67 at Metro"),
        ) as ParseResult.Success

        assertThat(cibc.type).isEqualTo(TxType.EXPENSE)
        assertThat(cibc.amount.amount).isEqualTo(BigDecimal("12.34"))
        assertThat(cibc.amount.currency).isEqualTo("CAD")
        assertThat(cibc.merchant).isEqualTo("Tim Hortons")
        assertThat(bmo.type).isEqualTo(TxType.EXPENSE)
        assertThat(bmo.amount.amount).isEqualTo(BigDecimal("45.67"))
        assertThat(bmo.amount.currency).isEqualTo("CAD")
        assertThat(bmo.merchant).isEqualTo("Metro")
    }

    @Test
    fun `parses German bank package notifications`() {
        val deutsche = parser.parse(
            event("notification:com.db.pwcc.dbmobile", "Card transaction EUR 8.90 at REWE"),
        ) as ParseResult.Success
        val sparkasse = parser.parse(
            event("notification:com.starfinanz.smob.android.sfinanzstatus", "Card purchase EUR 21.10 at Lidl"),
        ) as ParseResult.Success

        assertThat(deutsche.type).isEqualTo(TxType.EXPENSE)
        assertThat(deutsche.amount.amount).isEqualTo(BigDecimal("8.90"))
        assertThat(deutsche.amount.currency).isEqualTo("EUR")
        assertThat(deutsche.merchant).isEqualTo("REWE")
        assertThat(sparkasse.type).isEqualTo(TxType.EXPENSE)
        assertThat(sparkasse.amount.amount).isEqualTo(BigDecimal("21.10"))
        assertThat(sparkasse.amount.currency).isEqualTo("EUR")
        assertThat(sparkasse.merchant).isEqualTo("Lidl")
    }

    @Test
    fun `parses Saudi wallet package notifications`() {
        val anb = parser.parse(
            event("notification:com.anb.mobile.prod", "تم خصم ١٢ ر.س لدى متجر القهوة"),
        ) as ParseResult.Success
        val urpay = parser.parse(
            event("notification:com.urpay.consumer", "تم خصم ١٥ ر.س لدى كارفور"),
        ) as ParseResult.Success
        val mobilyPay = parser.parse(
            event("notification:com.es.mobily", "تم خصم ٢٥ ر.س لدى هنقرستيشن"),
        ) as ParseResult.Success

        assertThat(anb.type).isEqualTo(TxType.EXPENSE)
        assertThat(anb.amount.amount).isEqualTo(BigDecimal("12"))
        assertThat(anb.amount.currency).isEqualTo("SAR")
        assertThat(anb.merchant).isEqualTo("متجر القهوة")
        assertThat(urpay.type).isEqualTo(TxType.EXPENSE)
        assertThat(urpay.amount.amount).isEqualTo(BigDecimal("15"))
        assertThat(urpay.amount.currency).isEqualTo("SAR")
        assertThat(urpay.merchant).isEqualTo("كارفور")
        assertThat(mobilyPay.type).isEqualTo(TxType.EXPENSE)
        assertThat(mobilyPay.amount.amount).isEqualTo(BigDecimal("25"))
        assertThat(mobilyPay.amount.currency).isEqualTo("SAR")
        assertThat(mobilyPay.merchant).isEqualTo("هنقرستيشن")
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
        assertThat(result.counterparty).isEqualTo("Cashback income - Rewards")
    }

    @Test
    fun `parses Arabic cashback credited as labeled income`() {
        val result = parser.parse(
            event("notification:com.alrajhibank.alrajhimobile", "تم إيداع كاش باك ١٢ ر.س"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.INCOME)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("12"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.counterparty).isEqualTo("دخل كاش باك")
    }

    @Test
    fun `ignores cashback income offer notifications`() {
        assertThat(
            parser.parse(
                event("notification:com.revolut.revolut", "Cashback income offer: earn SAR 50 when you spend"),
            ),
        ).isEqualTo(ParseResult.Ignored)
    }

    @Test
    fun `parses cash deposit notification with other income label`() {
        val result = parser.parse(
            event("notification:com.chase.sig.android", "Cash deposit SAR 500.00 posted"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.INCOME)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("500.00"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.counterparty).isEqualTo("Cash deposit")
    }

    @Test
    fun `parses posted cash deposit notifications with available balance`() {
        val result = parser.parse(
            event(
                "notification:com.chase.sig.android",
                "Cash deposit SAR 500.00 posted. Available balance SAR 1,000.00",
            ),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.INCOME)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("500.00"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.counterparty).isEqualTo("Cash deposit")
        assertThat(result.balanceAfter!!.amount).isEqualTo(BigDecimal("1000.00"))
        assertThat(result.balanceAfter!!.currency).isEqualTo("SAR")
    }

    @Test
    fun `parses check deposit notification with source label`() {
        val result = parser.parse(
            event("notification:com.wellsfargo.mobile", "Check deposit credited USD 250.00 from Mobile Deposit"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.INCOME)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("250.00"))
        assertThat(result.amount.currency).isEqualTo("USD")
        assertThat(result.counterparty).isEqualTo("Check deposit - Mobile Deposit")
    }

    @Test
    fun `parses Arabic cash deposit notification with other income label`() {
        val result = parser.parse(
            event("notification:com.alrajhibank.alrajhimobile", "تم إيداع نقدي ٥٠٠ ر.س"),
        ) as ParseResult.Success

        assertThat(result.type).isEqualTo(TxType.INCOME)
        assertThat(result.amount.amount).isEqualTo(BigDecimal("500"))
        assertThat(result.amount.currency).isEqualTo("SAR")
        assertThat(result.counterparty).isEqualTo("إيداع نقدي")
    }

    @Test
    fun `ignores cash deposit limit notifications with amounts`() {
        assertThat(
            parser.parse(event("notification:com.chase.sig.android", "Your cash deposit limit is SAR 5,000")),
        ).isEqualTo(ParseResult.Ignored)
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
    fun `ignores payment request notifications with payment action words`() {
        assertThat(
            parser.parse(event("notification:com.venmo", "Payment request from John for USD 25.00")),
        ).isEqualTo(ParseResult.Ignored)
    }

    @Test
    fun `ignores requesting payment notifications with amounts`() {
        assertThat(
            parser.parse(event("notification:com.squareup.cash", "John is requesting SAR 25.00 payment from you")),
        ).isEqualTo(ParseResult.Ignored)
    }

    @Test
    fun `ignores money request notifications with amounts`() {
        assertThat(
            parser.parse(
                event(
                    "notification:com.paypal.android.p2pmobile",
                    "You have a money request for AED 75.00 from Ahmed",
                ),
            ),
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
    fun `does not run for ambiguous non finance package names`() {
        assertThat(
            parser.parse(
                event("notification:com.alaeat.customer.android.kohokoreanbbqhouse", "You spent CAD 42.00 at KOHO"),
            ),
        ).isEqualTo(ParseResult.Ignored)
        assertThat(
            parser.parse(event("notification:com.cryart.sabbathschool", "You spent SAR 42.00 at Bookstore")),
        ).isEqualTo(ParseResult.Ignored)
        assertThat(
            parser.parse(event("notification:com.mobily.activity", "تم خصم ٢٥ ر.س لدى متجر")),
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
