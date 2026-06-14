package com.athar.ingestion.smsparser.notification

import com.athar.core.common.money.Money
import com.athar.core.domain.model.TxType
import com.athar.ingestion.smsparser.BankTemplate
import com.athar.ingestion.smsparser.Normalize
import com.athar.ingestion.smsparser.ParseResult
import com.athar.ingestion.smsparser.SenderMatcher
import kotlinx.datetime.Instant

/**
 * Generic parser for Play-Store-safe bank-app push notifications.
 *
 * It intentionally matches notification-prefixed package sender names, not SMS sender IDs. The
 * NotificationListenerService filters packages before events reach the pipeline and records them
 * as `notification:<package>`; this template gives those events a parser path without loosening
 * SMS sender allow-lists.
 */
class GenericBankNotificationTemplate : BankTemplate {
    override val id: String = "generic-bank-notification"
    override val senderMatcher: SenderMatcher = SenderMatcher.Regex(BankPackagePattern)

    private val amountWithCurrency = Regex(
        """(?:(?<lead>HK\$|S\$|R\$|MX\$|MEX\$|CA\$|C\$|AU\$|A\$|RM|RP|[\$€£﷼₹¥₺₱₩฿₫]|SAR|SR|AED|USD|EUR|GBP|CAD|AUD|CHF|INR|PKR|TRY|EGP|KWD|QAR|BHD|OMR|JOD|JPY|CNY|HKD|SGD|SEK|NOK|DKK|ZAR|BRL|MXN|THB|IDR|MYR|PHP|VND|KRW)\s*)?(?<num>${Normalize.LOCALIZED_AMOUNT_PATTERN})(?:\s*(?<trail>HK\$|S\$|R\$|MX\$|MEX\$|CA\$|C\$|AU\$|A\$|RM|RP|[\$€£﷼₹¥₺₱₩฿₫]|SAR|SR|AED|USD|EUR|GBP|CAD|AUD|CHF|INR|PKR|TRY|EGP|KWD|QAR|BHD|OMR|JOD|JPY|CNY|HKD|SGD|SEK|NOK|DKK|ZAR|BRL|MXN|THB|IDR|MYR|PHP|VND|KRW|ر\.?\s*س|د\.?\s*إ)(?!\s*\d))?""",
        RegexOption.IGNORE_CASE,
    )
    private val expenseWords = Regex(
        """(?:\b(?:spent|purchase|paid|payment|debit|debited|charged|card\s+purchase|withdrawal|withdrawn|pos)\b|خصم|شراء|دفع|سحب)""",
        RegexOption.IGNORE_CASE,
    )
    private val expensePhrases = Regex(
        """(?:\b(?:card\s+(?:ending\s+(?:in\s+)?\d{2,4}\s+)?(?:was\s+)?used|card\s+payment|card\s+transaction|card\s+charge|debit\s+card\s+transaction|direct\s+debit|payment\s+to|transaction\s+at|transaction\s+with|new\s+(?:card\s+)?transaction|purchase\s+from|charged\s+(?:your\s+card|you))\b|\b(?:payment|purchase|transaction|card\s+transaction|card\s+purchase|debit\s+card\s+transaction)\s+(?:successful|completed|approved|posted|confirmed)\s*[:\-])""",
        RegexOption.IGNORE_CASE,
    )
    private val incomeWords = Regex(
        """\b(?:received|deposit|deposited|credited|refund|refunded|reversal|reversed|chargeback|salary|incoming|top\s*up|إيداع|ايداع|وارد|استلام|استلمت|راتب)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val incomePhrases = Regex(
        """\b(?:paid\s+you|sent\s+you|got\s+paid|was\s+paid|were\s+paid|payment\s+from|direct\s+deposit|direct\s+credit|ach\s+credit|credit\s+(?:of|from)|money\s+received|money\s+added|cash\s+in)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val salaryIncomeWords = Regex(
        """(?:\b(?:salary|paycheck|wages?)\b|\bpayroll\s+(?:deposit|credited|credit|payment|received)\b|راتب|رواتب|أجر|اجر)""",
        RegexOption.IGNORE_CASE,
    )
    private val salaryLabelWords = Regex(
        """(?:\bsalary\b|راتب|رواتب)""",
        RegexOption.IGNORE_CASE,
    )
    private val transferWords = Regex(
        """\b(?:sent|transfer|transferred|outgoing|remit|تحويل|حوالة|إرسال|ارسال)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val statementWords = Regex(
        """\b(?:statement\s+(?:is\s+)?(?:ready|available)|minimum\s+payment|payment\s+due|due\s+date|bill\s+(?:is\s+)?due|invoice\s+(?:is\s+)?due)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val limitWords = Regex(
        """\b(?:transfer\s+limit|daily\s+limit|card\s+limit|spending\s+limit|credit\s+limit|cash\s+advance\s+limit|limit\s+(?:changed|updated|increased|decreased))\b""",
        RegexOption.IGNORE_CASE,
    )
    private val withdrawalLimitWords = Regex(
        """(?:\b(?:atm\s+withdrawal\s+limit|cash\s+withdrawal\s+limit|withdrawal\s+limit|atm\s+limit)\b|حد\s+السحب|سقف\s+السحب)""",
        RegexOption.IGNORE_CASE,
    )
    private val feeWords = Regex(
        """(?:\b(?:service\s+fee|monthly\s+fee|maintenance\s+fee|foreign\s+transaction\s+fee|international\s+transaction\s+fee|atm\s+fee|bank\s+fee|fee(?:s)?)\b|رسوم|رسم|عمولة)""",
        RegexOption.IGNORE_CASE,
    )
    private val feeScheduleWords = Regex(
        """(?:\b(?:fee\s+schedule|fees\s+(?:and\s+charges|schedule|changed|updated)|pricing\s+(?:update|change|changes)|tariff\s+(?:update|change|changes)|new\s+fees)\b|رسوم\s+التعرفة\s+البنكية|تحديث\s+قائمة\s+رسوم|قائمة\s+رسوم\s+التعرفة|تعرفة\s+بنكية)""",
        RegexOption.IGNORE_CASE,
    )
    private val creditCardPaymentWords = Regex(
        """(?:\b(?:credit\s+card\s+(?:payment|repayment)|(?:payment|repayment)\s+(?:to|towards|for)\s+(?:your\s+)?credit\s+card)\b|بطاقة\s+ائتمانية\s*[:：]?\s*سداد|سداد\s+(?:بطاقة|البطاقة)\s+(?:ائتمانية|الائتمانية))""",
        RegexOption.IGNORE_CASE,
    )
    private val loanInstalmentWords = Regex(
        """(?:\b(?:loan\s+(?:instalment|installment|payment|repayment)|(?:instalment|installment)\s+(?:payment\s+)?(?:for\s+)?(?:loan|finance)|emi\s+(?:payment|paid|debited)|finance\s+(?:instalment|installment|payment))\b|قسط\s+(?:القرض|التمويل)|سداد\s+(?:القرض|التمويل))""",
        RegexOption.IGNORE_CASE,
    )
    private val debtReminderWords = Regex(
        """(?:\b(?:credit\s+card|loan|instalment|installment|emi|finance)[^\n\r]{0,80}\b(?:due|scheduled|upcoming|reminder)\b|\b(?:due|scheduled|upcoming|reminder)[^\n\r]{0,80}\b(?:credit\s+card|loan|instalment|installment|emi|finance)\b|(?:قسط|تمويل|بطاقة\s+ائتمانية)[^\n\r]{0,80}(?:مستحق|استحقاق|موعد|قادم|مجدول))""",
        RegexOption.IGNORE_CASE,
    )
    private val salaryFinancingOfferWords = Regex(
        """(?:\b(?:no\s+salary\s+transfer|required\s+salary\s+transfer|salary\s+transfer\s+(?:required|not\s+required|is\s+not\s+required)|without\s+salary\s+transfer)[^\n\r]{0,80}\b(?:loan|finance|financing|offer|apply|approval|approved)\b|\b(?:loan|finance|financing|offer|apply|approval|approved)[^\n\r]{0,80}\b(?:no\s+salary\s+transfer|required\s+salary\s+transfer|salary\s+transfer\s+(?:required|not\s+required|is\s+not\s+required)|without\s+salary\s+transfer)\b|(?:تمويل|قرض)[^\n\r]{0,80}(?:تحويل\s+راتب|بدون\s+تحويل\s+راتب|لا\s+يتطلب\s+تحويل\s+راتب))""",
        RegexOption.IGNORE_CASE,
    )
    private val electricityBillWords = Regex(
        """(?:\b(?:electricity|electric|power)\s+bill\b|\bbill\s+(?:for\s+)?(?:electricity|electric|power)\b|فاتورة\s+الكهرباء|شركة\s+الكهرباء)""",
        RegexOption.IGNORE_CASE,
    )
    private val waterBillWords = Regex(
        """(?:\bwater\s+bill\b|\bbill\s+(?:for\s+)?water\b|فاتورة\s+المياه|فاتورة\s+الماء|شركة\s+المياه|شركة\s+الماء)""",
        RegexOption.IGNORE_CASE,
    )
    private val genericBillPaymentWords = Regex(
        """(?:\bbill\s+payment\b|\bpaid\s+(?:a\s+)?bill\b|دفع\s+فاتورة|تم\s+دفع\s+فاتورة|سداد\s+فاتورة)""",
        RegexOption.IGNORE_CASE,
    )
    private val utilityBillReminderWords = Regex(
        """(?:\b(?:(?:electricity|electric|power|water)\s+)?bill[^\n\r]{0,80}\b(?:due|scheduled|upcoming|reminder)\b|\b(?:due|scheduled|upcoming|reminder)[^\n\r]{0,80}\b(?:(?:electricity|electric|power|water)\s+)?bill\b|(?:فاتورة\s+(?:الكهرباء|المياه|الماء)|شركة\s+(?:الكهرباء|المياه|الماء))[^\n\r]{0,80}(?:مستحق|استحقاق|موعد|قادم|مجدول))""",
        RegexOption.IGNORE_CASE,
    )
    private val subscriptionPaymentWords = Regex(
        """(?:\b(?:subscription|membership)\s+(?:payment|charge|fee|paid|debited)\b|\brecurring\s+(?:payment|charge)\b|دفع\s+اشتراك|سداد\s+اشتراك|اشتراك\s+(?:مدفوع|مجدد))""",
        RegexOption.IGNORE_CASE,
    )
    private val insurancePremiumWords = Regex(
        """(?:\b(?:insurance|policy)\s+premium\b|\bpremium\s+(?:payment|paid|debited)\b|قسط\s+(?:التأمين|تأمين)|سداد\s+(?:قسط\s+)?(?:التأمين|تأمين))""",
        RegexOption.IGNORE_CASE,
    )
    private val rentPaymentWords = Regex(
        """(?:\brent\s+payment\b|\bejar\s+(?:rent\s+)?payment\b|(?:سداد|دفع|خصم)\s+(?:دفعة\s+)?(?:الإيجار|ايجار|إيجار)|(?:الإيجار|ايجار|إيجار)\s+(?:تم\s+)?(?:سداد|دفع))""",
        RegexOption.IGNORE_CASE,
    )
    private val recurringExpenseReminderWords = Regex(
        """(?:\b(?:subscription|membership|recurring\s+payment|insurance\s+premium|policy\s+premium|rent\s+payment|rent)\b[^\n\r]{0,80}\b(?:due|scheduled|upcoming|reminder|renews?|renewal|expires?)\b|\b(?:due|scheduled|upcoming|reminder|renews?|renewal|expires?)[^\n\r]{0,80}\b(?:subscription|membership|insurance\s+premium|policy\s+premium|rent\s+payment|rent)\b|(?:إيجار|ايجار|اشتراك|تأمين|قسط\s+التأمين)[^\n\r]{0,80}(?:مستحق|استحقاق|موعد|قادم|مجدول|تجديد))""",
        RegexOption.IGNORE_CASE,
    )
    private val telecomRechargeWords = Regex(
        """(?:\b(?:mobile|phone|airtime|prepaid)\s+(?:recharge|top[-\s]?up)\b|\b(?:recharge|top[-\s]?up)\s+(?:for\s+)?(?:mobile|phone|airtime|prepaid)\b|\bairtime\s+(?:purchase|payment)\b|(?:شحن|تعبئة|تعبئه)\s+(?:رصيد\s+)?(?:الجوال|الهاتف|الموبايل|المحمول)|(?:رصيد\s+)?(?:الجوال|الهاتف|الموبايل|المحمول)\s+(?:تم\s+)?(?:شحن|تعبئة|تعبئه))""",
        RegexOption.IGNORE_CASE,
    )
    private val telecomRechargeReminderWords = Regex(
        """(?:\b(?:next|offer|promo|bonus|discount|bundle|data)\b[^\n\r]{0,80}\b(?:mobile|phone|airtime|prepaid)\s+(?:recharge|top[-\s]?up)\b|\b(?:mobile|phone|airtime|prepaid)\s+(?:recharge|top[-\s]?up)\b[^\n\r]{0,80}\b(?:offer|promo|bonus|discount|bundle|data|scheduled|upcoming|reminder)\b|(?:عرض|عروض|خصم|بونص|مكافأة|باقة|بيانات)[^\n\r]{0,80}(?:شحن|تعبئة|تعبئه)\s+(?:رصيد\s+)?(?:الجوال|الهاتف|الموبايل|المحمول))""",
        RegexOption.IGNORE_CASE,
    )
    private val trafficFinePaymentWords = Regex(
        """(?:\b(?:traffic|parking)\s+(?:fine|violation)\s+(?:payment|paid|settled)\b|\b(?:payment|paid|settled)\s+(?:for\s+)?(?:traffic|parking)\s+(?:fine|violation)\b|\b(?:traffic|parking)\s+(?:fine|violation)\b[^\n\r]{0,40}\b(?:paid|settled|posted|completed)\b|(?:سداد|دفع|خصم)\s+(?:مخالفة|مخالفات)\s+(?:مرورية|المرور)|(?:مخالفة|مخالفات)\s+(?:مرورية|المرور)\s+(?:تم\s+)?(?:سداد|دفع|خصم))""",
        RegexOption.IGNORE_CASE,
    )
    private val governmentServicePaymentWords = Regex(
        """(?:\b(?:government|public)\s+service\s+(?:payment|paid|fee|charge)\b|\b(?:payment|paid)\s+(?:for\s+)?(?:government|public)\s+service\b|\b(?:government|public)\s+(?:fee|charge)\s+(?:payment|paid|debited|posted)\b|\bministry\s+(?:service\s+)?(?:payment|fee|charge)\b|مدفوعات\s+(?:حكومية|وزارة\s+الداخلية)|(?:سداد|دفع|خصم)\s+(?:رسوم\s+)?(?:حكومية|خدمة\s+حكومية|خدمات\s+حكومية|خدمات\s+المقيمين))""",
        RegexOption.IGNORE_CASE,
    )
    private val publicServiceReminderWords = Regex(
        """(?:\b(?:(?:traffic|parking)\s+(?:fine|violation)|(?:government|public)\s+service|government\s+(?:fee|charge))\b[^\n\r]{0,80}\b(?:due|scheduled|upcoming|reminder|deadline|expires?)\b|\b(?:due|scheduled|upcoming|reminder|deadline|expires?)\b[^\n\r]{0,80}\b(?:(?:traffic|parking)\s+(?:fine|violation)|(?:government|public)\s+service|government\s+(?:fee|charge))\b|(?:مخالفة|مخالفات|رسوم\s+حكومية|خدمة\s+حكومية|خدمات\s+حكومية|خدمات\s+المقيمين)[^\n\r]{0,80}(?:مستحق|استحقاق|موعد|قادم|مجدول|تذكير)|(?:تذكير|مستحق|استحقاق|موعد|قادم|مجدول)[^\n\r]{0,80}(?:مخالفة|مخالفات|رسوم\s+حكومية|خدمة\s+حكومية|خدمات\s+حكومية|خدمات\s+المقيمين))""",
        RegexOption.IGNORE_CASE,
    )
    private val parkingPaymentWords = Regex(
        """(?:\bparking\s+(?:payment|paid|fee|charge)\b|\b(?:payment|paid|charged)\s+(?:for\s+)?parking\b|\bpaid\s+parking\b|(?:سداد|دفع|خصم)\s+(?:رسوم\s+)?(?:مواقف|المواقف|موقف)|(?:مواقف|المواقف|موقف)\s+(?:تم\s+)?(?:سداد|دفع|خصم))""",
        RegexOption.IGNORE_CASE,
    )
    private val tollPaymentWords = Regex(
        """(?:\b(?:road\s+)?toll\s+(?:payment|paid|fee|charge)\b|\b(?:payment|paid|charged)\s+(?:for\s+)?(?:road\s+)?toll\b|(?:سداد|دفع|خصم)\s+(?:رسوم\s+)?(?:العبور|بوابة\s+العبور|تعرفة\s+الطريق|رسوم\s+الطريق)|(?:رسوم\s+)?(?:العبور|الطريق)\s+(?:تم\s+)?(?:سداد|دفع|خصم))""",
        RegexOption.IGNORE_CASE,
    )
    private val transitFareWords = Regex(
        """(?:\b(?:transit|metro|bus|train|tram)\s+(?:fare|ticket|payment|paid|charge)\b|\b(?:fare|ticket|payment|paid)\s+(?:for\s+)?(?:transit|metro|bus|train|tram)\b|(?:سداد|دفع|خصم)\s+(?:أجرة|اجرة|تذكرة|تذاكر)\s+(?:المترو|الحافلة|الحافلات|القطار|النقل)|(?:أجرة|اجرة|تذكرة|تذاكر)\s+(?:المترو|الحافلة|الحافلات|القطار|النقل)\s+(?:تم\s+)?(?:سداد|دفع|خصم))""",
        RegexOption.IGNORE_CASE,
    )
    private val mobilityPaymentReminderWords = Regex(
        """(?:\b(?:parking|toll|transit|metro|bus|train|tram)\b[^\n\r]{0,80}\b(?:due|scheduled|upcoming|reminder|deadline|expires?|expiry|unpaid)\b|\b(?:due|scheduled|upcoming|reminder|deadline|expires?|expiry|unpaid)\b[^\n\r]{0,80}\b(?:parking|toll|transit|metro|bus|train|tram)\b|(?:مواقف|المواقف|موقف|العبور|الطريق|المترو|الحافلة|الحافلات|القطار|النقل)[^\n\r]{0,80}(?:مستحق|استحقاق|موعد|قادم|مجدول|تذكير|ينتهي|انتهاء)|(?:تذكير|مستحق|استحقاق|موعد|قادم|مجدول|ينتهي|انتهاء)[^\n\r]{0,80}(?:مواقف|المواقف|موقف|العبور|الطريق|المترو|الحافلة|الحافلات|القطار|النقل))""",
        RegexOption.IGNORE_CASE,
    )
    private val pharmacyPaymentWords = Regex(
        """(?:\b(?:pharmacy|prescription)\s+(?:purchase|payment|paid|bill|fee|charge)\b|\b(?:purchase|payment|paid|charged)\s+(?:for\s+)?(?:pharmacy|prescription)\b|(?:سداد|دفع|خصم|شراء)\s+(?:من\s+)?(?:الصيدلية|صيدلية)|(?:الصيدلية|صيدلية)\s+(?:تم\s+)?(?:سداد|دفع|خصم|شراء))""",
        RegexOption.IGNORE_CASE,
    )
    private val medicalPaymentWords = Regex(
        """(?:\b(?:medical|healthcare|health|hospital|clinic|dental|doctor|laboratory|lab)\s+(?:payment|paid|bill|fee|charge|visit|consultation)\b|\b(?:payment|paid|charged)\s+(?:for\s+)?(?:medical|healthcare|health|hospital|clinic|dental|doctor|laboratory|lab)\b|(?:سداد|دفع|خصم)\s+(?:رسوم\s+)?(?:طبية|طبي|المستشفى|مستشفى|العيادة|عيادة|الطبيب|طبيب|تحاليل|مختبر)|(?:فاتورة\s+)?(?:المستشفى|مستشفى|العيادة|عيادة|الطبيب|طبيب|طبية|تحاليل|مختبر)\s+(?:تم\s+)?(?:سداد|دفع|خصم))""",
        RegexOption.IGNORE_CASE,
    )
    private val educationPaymentWords = Regex(
        """(?:\b(?:school|tuition|university|college|education)\s+(?:fee|fees|payment|paid|bill|charge)\b|\b(?:payment|paid|charged)\s+(?:for\s+)?(?:school|tuition|university|college|education)\b|(?:سداد|دفع|خصم)\s+(?:رسوم\s+)?(?:مدرسية|المدرسة|مدرسة|جامعية|الجامعة|جامعة|تعليم|تعليمية|دراسية)|(?:رسوم\s+)?(?:مدرسية|المدرسة|مدرسة|جامعية|الجامعة|جامعة|تعليم|تعليمية|دراسية)\s+(?:تم\s+)?(?:سداد|دفع|خصم))""",
        RegexOption.IGNORE_CASE,
    )
    private val zakatPaymentWords = Regex(
        """(?:\bzakat\s+(?:payment|paid|donation|transfer)\b|\b(?:paid|payment)\s+(?:for\s+)?zakat\b|(?:سداد|دفع|خصم)\s+(?:زكاة|زكاه)|(?:زكاة|زكاه)\s+(?:تم\s+)?(?:سداد|دفع|خصم))""",
        RegexOption.IGNORE_CASE,
    )
    private val charityDonationWords = Regex(
        """(?:\b(?:charity|donation|sadaqah|sadaka)\s+(?:payment|paid|donation|transfer)\b|\b(?:donation|donated|paid)\s+(?:to|for)\s+(?:charity|sadaqah|sadaka)\b|(?:سداد|دفع|خصم)\s+(?:تبرع|صدقة|صدقه)|(?:تبرع|صدقة|صدقه)\s+(?:تم\s+)?(?:سداد|دفع|خصم))""",
        RegexOption.IGNORE_CASE,
    )
    private val essentialLifeReminderWords = Regex(
        """(?:\b(?:medical|healthcare|hospital|clinic|dental|doctor|laboratory|lab|pharmacy|prescription|school|tuition|university|college|education)\b[^\n\r]{0,80}\b(?:due|scheduled|upcoming|reminder|unpaid|overdue)\b|\b(?:due|scheduled|upcoming|reminder|unpaid|overdue)\b[^\n\r]{0,80}\b(?:medical|healthcare|hospital|clinic|dental|doctor|laboratory|lab|pharmacy|prescription|school|tuition|university|college|education)\b|\bdonate\b[^\n\r]{0,80}\b(?:now|today|support|help|campaign|appeal)\b|\b(?:donation|charity|zakat|sadaqah|sadaka)\b[^\n\r]{0,80}\b(?:appeal|campaign|support|help|pledge|target|calculator|due)\b|(?:تذكير|مستحق|استحقاق|موعد|قادم|مجدول|غير\s+مدفوع)[^\n\r]{0,80}(?:رسوم\s+مدرسية|تعليم|جامعة|مدرسة|طبية|مستشفى|عيادة|صيدلية|زكاة|زكاه)|(?:تبرع|صدقة|صدقه|زكاة|زكاه)[^\n\r]{0,80}(?:حملة|ساهم|ادعم|دعم|حاسبة))""",
        RegexOption.IGNORE_CASE,
    )
    private val fuelPaymentWords = Regex(
        """(?:\b(?:fuel|petrol|gasoline|gas\s+station)\s+(?:purchase|payment|paid|charge|refill)\b|\b(?:purchase|payment|paid|charged)\s+(?:for\s+)?(?:fuel|petrol|gasoline)\b|(?:سداد|دفع|خصم|شراء)\s+(?:وقود|بنزين)|(?:وقود|بنزين)\s+(?:تم\s+)?(?:سداد|دفع|خصم|شراء))""",
        RegexOption.IGNORE_CASE,
    )
    private val groceryPaymentWords = Regex(
        """(?:\b(?:grocery|groceries|supermarket)\s+(?:purchase|payment|paid|charge|shopping)\b|\b(?:purchase|payment|paid|charged)\s+(?:for\s+)?(?:grocery|groceries|supermarket)\b|(?:سداد|دفع|خصم|شراء)\s+(?:بقالة|البقالة|سوبر\s*ماركت|تموينات)|(?:بقالة|البقالة|سوبر\s*ماركت|تموينات)\s+(?:تم\s+)?(?:سداد|دفع|خصم|شراء))""",
        RegexOption.IGNORE_CASE,
    )
    private val restaurantPaymentWords = Regex(
        """(?:\b(?:restaurant|dining)\s+(?:purchase|payment|paid|charge)\b|\b(?:purchase|payment|paid|charged)\s+(?:for\s+)?(?:restaurant|dining)\b|(?:سداد|دفع|خصم|شراء)\s+(?:مطعم|المطعم|مطاعم)|(?:مطعم|المطعم|مطاعم)\s+(?:تم\s+)?(?:سداد|دفع|خصم|شراء))""",
        RegexOption.IGNORE_CASE,
    )
    private val coffeePaymentWords = Regex(
        """(?:\b(?:coffee|cafe|coffee\s+shop)\s+(?:purchase|payment|paid|charge)\b|\b(?:purchase|payment|paid|charged)\s+(?:for\s+)?(?:coffee|cafe|coffee\s+shop)\b|(?:سداد|دفع|خصم|شراء)\s+(?:قهوة|قهوتك|مقهى|المقهى|كافيه)|(?:قهوة|مقهى|المقهى|كافيه)\s+(?:تم\s+)?(?:سداد|دفع|خصم|شراء))""",
        RegexOption.IGNORE_CASE,
    )
    private val foodDeliveryPaymentWords = Regex(
        """(?:\b(?:food\s+delivery|meal\s+delivery|delivery\s+order|restaurant\s+delivery)\s+(?:payment|paid|purchase|charge)\b|\b(?:payment|paid|charged)\s+(?:for\s+)?(?:food\s+delivery|meal\s+delivery|delivery\s+order)\b|(?:سداد|دفع|خصم)\s+(?:طلب\s+)?(?:توصيل\s+طعام|توصيل\s+مطعم|طلب\s+طعام)|(?:توصيل\s+طعام|توصيل\s+مطعم|طلب\s+طعام)\s+(?:تم\s+)?(?:سداد|دفع|خصم))""",
        RegexOption.IGNORE_CASE,
    )
    private val taxiRidePaymentWords = Regex(
        """(?:\b(?:taxi|cab|ride\s+hailing|rideshare|ride\s+share|ride)\s+(?:fare|payment|paid|charge)\b|\b(?:fare|payment|paid|charged)\s+(?:for\s+)?(?:taxi|cab|ride\s+hailing|rideshare|ride\s+share|ride)\b|(?:سداد|دفع|خصم)\s+(?:أجرة|اجرة|مشوار|رحلة|تاكسي|سيارة\s+أجرة)|(?:أجرة|اجرة|مشوار|رحلة|تاكسي|سيارة\s+أجرة)\s+(?:تم\s+)?(?:سداد|دفع|خصم))""",
        RegexOption.IGNORE_CASE,
    )
    private val everydayCommerceNonPostedWords = Regex(
        """(?:\b(?:fuel|petrol|gasoline|gas\s+station|grocery|groceries|supermarket|restaurant|dining|coffee|cafe|coffee\s+shop|food\s+delivery|meal\s+delivery|delivery\s+order|taxi|cab|ride\s+hailing|rideshare|ride\s+share|ride)\b[^\n\r]{0,80}\b(?:offer|promo|discount|coupon|deal|save|bonus|reward|cashback|points|estimate|estimated|scheduled|upcoming|reservation|reserved|preorder|pre-order|reminder)\b|\b(?:offer|promo|discount|coupon|deal|save|bonus|reward|cashback|points|estimate|estimated|scheduled|upcoming|reservation|reserved|preorder|pre-order|reminder)\b[^\n\r]{0,80}\b(?:fuel|petrol|gasoline|gas\s+station|grocery|groceries|supermarket|restaurant|dining|coffee|cafe|coffee\s+shop|food\s+delivery|meal\s+delivery|delivery\s+order|taxi|cab|ride\s+hailing|rideshare|ride\s+share|ride)\b|(?:عرض|عروض|قسيمة|كوبون|وفر|مكافأة|نقاط|استرداد|تذكير|مجدول|قادم|حجز|تقدير)[^\n\r]{0,80}(?:وقود|بنزين|بقالة|سوبر\s*ماركت|مطعم|مطاعم|قهوة|مقهى|كافيه|توصيل\s+طعام|مشوار|رحلة|تاكسي))""",
        RegexOption.IGNORE_CASE,
    )
    private val flightTicketPaymentWords = Regex(
        """(?:\b(?:flight|airline|air\s+ticket|flight\s+ticket|plane\s+ticket)\s+(?:purchase|payment|paid|charge|ticket)\b|\b(?:purchase|payment|paid|charged)\s+(?:for\s+)?(?:flight|airline|air\s+ticket|flight\s+ticket|plane\s+ticket)\b|(?:سداد|دفع|خصم|شراء)\s+(?:تذكرة|تذاكر)\s+(?:طيران|سفر)|(?:تذكرة|تذاكر)\s+(?:طيران|سفر)\s+(?:تم\s+)?(?:سداد|دفع|خصم|شراء))""",
        RegexOption.IGNORE_CASE,
    )
    private val hotelPaymentWords = Regex(
        """(?:\b(?:hotel|lodging|accommodation)\s+(?:payment|paid|charge|stay)\b|\b(?:payment|paid|charged)\s+(?:for\s+)?(?:hotel|lodging|accommodation)\b|(?:سداد|دفع|خصم)\s+(?:رسوم\s+)?(?:فندق|الفندق|إقامة|اقامة)|(?:فندق|الفندق|إقامة|اقامة)\s+(?:تم\s+)?(?:سداد|دفع|خصم))""",
        RegexOption.IGNORE_CASE,
    )
    private val travelBookingPaymentWords = Regex(
        """(?:\b(?:travel|trip|holiday|vacation)\s+(?:booking\s+)?(?:payment|paid|purchase|charge)\b|\b(?:payment|paid|charged)\s+(?:for\s+)?(?:travel|trip|holiday|vacation)\s+booking\b|(?:سداد|دفع|خصم)\s+(?:حجز\s+)?(?:سفر|رحلة\s+سفر|رحلة\s+طيران)|(?:حجز\s+)?(?:سفر|رحلة\s+سفر|رحلة\s+طيران)\s+(?:تم\s+)?(?:سداد|دفع|خصم))""",
        RegexOption.IGNORE_CASE,
    )
    private val carRentalPaymentWords = Regex(
        """(?:\b(?:car\s+rental|rental\s+car|vehicle\s+rental)\s+(?:payment|paid|charge|booking)\b|\b(?:payment|paid|charged)\s+(?:for\s+)?(?:car\s+rental|rental\s+car|vehicle\s+rental)\b|(?:سداد|دفع|خصم)\s+(?:رسوم\s+)?(?:تأجير|تاجير|استئجار)\s+(?:سيارة|السيارة)|(?:تأجير|تاجير|استئجار)\s+(?:سيارة|السيارة)\s+(?:تم\s+)?(?:سداد|دفع|خصم))""",
        RegexOption.IGNORE_CASE,
    )
    private val travelNonPostedWords = Regex(
        """(?:\b(?:flight|airline|air\s+ticket|hotel|lodging|accommodation|travel|trip|holiday|vacation|car\s+rental|rental\s+car|vehicle\s+rental)\b[^\n\r]{0,80}\b(?:offer|promo|discount|coupon|deal|save|bonus|reward|cashback|points|miles|itinerary|reservation|reserved|check[-\s]?in|boarding\s+pass|quote|estimate|estimated|scheduled|upcoming|reminder)\b|\b(?:offer|promo|discount|coupon|deal|save|bonus|reward|cashback|points|miles|itinerary|reservation|reserved|check[-\s]?in|boarding\s+pass|quote|estimate|estimated|scheduled|upcoming|reminder)\b[^\n\r]{0,80}\b(?:flight|airline|air\s+ticket|hotel|lodging|accommodation|travel|trip|holiday|vacation|car\s+rental|rental\s+car|vehicle\s+rental)\b|(?:عرض|عروض|قسيمة|كوبون|وفر|مكافأة|نقاط|أميال|اميال|استرداد|تذكير|مجدول|قادم|حجز|تقدير|مسار\s+رحلة|بطاقة\s+صعود)[^\n\r]{0,80}(?:طيران|تذكرة\s+طيران|فندق|فنادق|سفر|تأجير\s+سيارة|تاجير\s+سيارة|استئجار\s+سيارة))""",
        RegexOption.IGNORE_CASE,
    )
    private val clothingPurchaseWords = Regex(
        """(?:\b(?:clothing|clothes|apparel|fashion|footwear|shoes)\s+(?:purchase|payment|paid|charge|shopping)\b|\b(?:purchase|payment|paid|charged)\s+(?:for\s+)?(?:clothing|clothes|apparel|fashion|footwear|shoes)\b|(?:سداد|دفع|خصم|شراء)\s+(?:ملابس|ثياب|أزياء|ازياء|أحذية|احذية)|(?:ملابس|ثياب|أزياء|ازياء|أحذية|احذية)\s+(?:تم\s+)?(?:سداد|دفع|خصم|شراء))""",
        RegexOption.IGNORE_CASE,
    )
    private val electronicsPurchaseWords = Regex(
        """(?:\b(?:electronics?|electronic\s+goods|devices?|mobile\s+phone|smartphone|laptop|computer|gadget)\s+(?:purchase|payment|paid|charge)\b|\b(?:purchase|payment|paid|charged)\s+(?:for\s+)?(?:electronics?|electronic\s+goods|a\s+device|devices?|mobile\s+phone|smartphone|laptop|computer|gadget)\b|(?:سداد|دفع|خصم|شراء)\s+(?:إلكترونيات|الكترونيات|أجهزة|اجهزة|جوال|هاتف|كمبيوتر|لابتوب)|(?:إلكترونيات|الكترونيات|أجهزة|اجهزة|جوال|هاتف|كمبيوتر|لابتوب)\s+(?:تم\s+)?(?:سداد|دفع|خصم|شراء))""",
        RegexOption.IGNORE_CASE,
    )
    private val onlineShoppingPurchaseWords = Regex(
        """(?:\b(?:online\s+shopping|online\s+purchase|e[-\s]?commerce|marketplace)\s+(?:purchase|payment|paid|charge)\b|\b(?:purchase|payment|paid|charged)\s+(?:for\s+)?(?:online\s+shopping|online\s+purchase|e[-\s]?commerce|marketplace)\b|(?:سداد|دفع|خصم|شراء)\s+(?:تسوق\s+إلكتروني|تسوق\s+الكتروني|شراء\s+إلكتروني|شراء\s+الكتروني)|(?:تسوق\s+إلكتروني|تسوق\s+الكتروني|شراء\s+إلكتروني|شراء\s+الكتروني)\s+(?:تم\s+)?(?:سداد|دفع|خصم|شراء))""",
        RegexOption.IGNORE_CASE,
    )
    private val retailShoppingNonPostedWords = Regex(
        """(?:\b(?:clothing|clothes|apparel|fashion|footwear|shoes|electronics?|electronic\s+goods|devices?|mobile\s+phone|smartphone|laptop|computer|gadget|online\s+shopping|online\s+purchase|e[-\s]?commerce|marketplace)\b[^\n\r]{0,80}\b(?:offer|promo|discount|coupon|deal|save|price\s+drop|cart|wishlist|back\s+in\s+stock|preorder|pre-order|shipping|shipped|delivered|out\s+for\s+delivery|delivery\s+update|order\s+status|ready\s+for\s+pickup|reminder)\b|\b(?:offer|promo|discount|coupon|deal|save|price\s+drop|cart|wishlist|back\s+in\s+stock|preorder|pre-order|shipping|shipped|delivered|out\s+for\s+delivery|delivery\s+update|order\s+status|ready\s+for\s+pickup|reminder)\b[^\n\r]{0,80}\b(?:clothing|clothes|apparel|fashion|footwear|shoes|electronics?|electronic\s+goods|devices?|mobile\s+phone|smartphone|laptop|computer|gadget|online\s+shopping|online\s+purchase|e[-\s]?commerce|marketplace)\b|(?:عرض|عروض|خصم|قسيمة|كوبون|وفر|سلة|عربة|قائمة\s+الأماني|قائمة\s+الاماني|تم\s+شحن|تم\s+توصيل|قيد\s+التوصيل|تحديث\s+الطلب|تذكير)[^\n\r]{0,80}(?:ملابس|ثياب|أزياء|ازياء|أحذية|احذية|إلكترونيات|الكترونيات|أجهزة|اجهزة|تسوق\s+إلكتروني|تسوق\s+الكتروني|شراء\s+إلكتروني|شراء\s+الكتروني))""",
        RegexOption.IGNORE_CASE,
    )
    private val declinedWords = Regex(
        """\b(?:declined|rejected|failed|unsuccessful|مرفوض|رُفض|فشل|غير\s+ناجحة)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val requestWords = Regex(
        """\b(?:requested|requesting|requests?|payment\s+request|money\s+request)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val scheduledWords = Regex(
        """\b(?:scheduled\s+(?:payment|transfer|autopay|bill)|payment\s+(?:is\s+)?scheduled|transfer\s+(?:is\s+)?scheduled|autopay\s+(?:is\s+)?scheduled|upcoming\s+payment)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val spendingSummaryWords = Regex(
        """(?:\b(?:spending\s+(?:summary|recap|insight|report|snapshot)|weekly\s+(?:summary|recap|spending)|monthly\s+(?:summary|recap|spending)|budget\s+(?:summary|insight|update)|you\s+spent[^\n\r]{0,80}\b(?:this|last)\s+(?:week|month)|spent[^\n\r]{0,80}\b(?:this|last)\s+(?:week|month)|so\s+far\s+this\s+(?:week|month))\b|ملخص\s+(?:الإنفاق|الانفاق|الصرف|المصاريف)|تقرير\s+(?:الإنفاق|الانفاق|الصرف|المصاريف))""",
        RegexOption.IGNORE_CASE,
    )
    private val adminNoticeWords = Regex(
        """(?:\b(?:card\s+(?:ending\s+\d{2,4}\s+(?:has\s+been\s+|was\s+)?)?(?:activated|activation|blocked|unblocked|locked|unlocked|frozen|unfrozen|issued|shipped|delivered|ready|expired|renewed|replacement)|pin\s+(?:changed|updated|reset)|new\s+device\s+(?:linked|registered|added)|device\s+(?:linked|registered|added)|biometric\s+(?:enabled|disabled|login)|quick\s+login|beneficiary\s+(?:added|activated|updated)|payee\s+(?:added|activated|updated)|terms\s+(?:and\s+conditions\s+)?(?:updated|changed|available)|privacy\s+notice|document\s+(?:ready|available)|maintenance|service\s+interruption|app\s+(?:updated|migration|migrated))\b|تم\s+(?:تفعيل|إيقاف|ايقاف|حظر|فك\s+حظر|تجميد|إصدار|اصدار|تجديد)\s+(?:بطاقتك|البطاقة)|تفعيل\s+(?:البطاقة|بطاقتك)|تم\s+ربط\s+جهاز|تم\s+تسجيل\s+جهاز|تم\s+إضافة\s+مستفيد|تم\s+اضافة\s+مستفيد|الشروط\s+والأحكام|الأحكام\s+والشروط|الصيانة|تحديث\s+التطبيق)""",
        RegexOption.IGNORE_CASE,
    )
    private val securityWords = Regex(
        """\b(?:otp|one[-\s]?time|verification|security\s+code|login|password|do\s+not\s+share|رمز|تحقق|الدخول|كلمة\s+المرور)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val securityCodeAuthorizationWords = Regex(
        """(?:\b(?:otp|one[-\s]?time\s+(?:password|code)|verification\s+code|security\s+code|auth(?:entication|orization)?\s+code|passcode|do\s+not\s+share|use\s+(?:this\s+)?code|code\s+to\s+(?:authorize|confirm|verify))\b|رمز\s+(?:التحقق|التأكيد|الدخول|الأمان|الامان)|لا\s+تشارك|لا\s+تشاركه|لا\s+تفصح|استخدم\s+الرمز|كود\s+(?:التحقق|التأكيد))""",
        RegexOption.IGNORE_CASE,
    )
    private val authorizationHoldWords = Regex(
        """(?:\b(?:pre[-\s]?auth(?:ori[sz]ation)?|pre[-\s]?authori[sz]ation|pending\s+authori[sz]ation|authori[sz]ation\s+(?:of|for)|(?:auth(?:ori[sz]ation)?|authori[sz]ation)\s+hold|temporary\s+(?:card\s+)?hold|card\s+hold|hold\s+(?:was\s+)?(?:placed|created)|amount\s+held|(?:payment|purchase|transaction|amount)\s+authori[sz]ed|authori[sz]ed\s+(?:payment|purchase|transaction|amount)|authori[sz]e\s+(?:payment|purchase|transaction))\b|حجز\s+مؤقت|حجز\s+(?:مبلغ\s*)?[^\n\r]{0,48}مؤقت|مبلغ\s+محجوز|تفويض\s+مؤقت|عملية\s+تفويض|تفويض\s+(?:شراء|دفع))""",
        RegexOption.IGNORE_CASE,
    )
    private val marketingWords = Regex(
        """\b(?:offer|promo|cashback|points|reward|earn|win|discount|عرض|خصم|نقاط|مكافأة|اكسب|اربح)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val marketingOnlyWords = Regex(
        """(?:\b(?:offer|promo|cashback|points|reward|earn|win|discount|save|coupon|deal)\b|عرض|عروض|خصومات|تخفيض|تخفيضات|كوبون|قسيمة|وفر|اكسب|اربح|نقاط|مكافأة|استرداد\s+نقدي|كاش\s*باك|خصم\s*(?:حتى\s*)?\d+(?:[.,]\d+)?\s*[%٪])""",
        RegexOption.IGNORE_CASE,
    )
    private val rewardWords = Regex(
        """(?:\b(?:cashback|cash\s+back|points?|reward|rewards?|earned|earn|win|save)\b|نقاط|مكافأة|مكافآت|استرداد\s+نقدي|كاش\s*باك|اكسب|اربح|وفر)""",
        RegexOption.IGNORE_CASE,
    )
    private val rewardAmountContext = Regex(
        """(?:\b(?:cashback|cash\s+back|points?|reward|rewards?|earned|earn|win|save)\b|نقاط|مكافأة|مكافآت|استرداد\s+نقدي|كاش\s*باك|اكسب|اربح|وفر)""",
        RegexOption.IGNORE_CASE,
    )
    private val postedTransactionEvidence = Regex(
        """(?:\b(?:you\s+(?:spent|paid|received|sent)|card\s+(?:ending\s+\d{2,4}\s+)?(?:was\s+)?used|card\s+(?:purchase|payment|transaction|charge)|debit(?:ed)?|charged|credited|deposit(?:ed)?|direct\s+debit|payment\s+(?:from|to)|money\s+(?:received|added)|transfer\s+(?:sent|received)|sent\s+you|paid\s+you|got\s+paid)\b|تم\s+خصم|بعد\s+خصم|تم\s+دفع|تمت\s+عملية|عملية\s+(?:شراء|دفع|سحب)|دفع\s+فاتورة|تم\s+سحب|تم\s+إيداع|تم\s+ايداع|وارد|استلمت|تحويل|حوالة)""",
        RegexOption.IGNORE_CASE,
    )
    private val balanceWords = Regex(
        """\b(?:balance|available|رصيد|المتاح)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val balanceAmountContext = Regex(
        """(?:\b(?:balance|available|remaining\s+balance|current\s+balance)\b|رصيد|الرصيد|المتاح|الرصيد\s+المتبقي)""",
        RegexOption.IGNORE_CASE,
    )
    private val creditStateWords = Regex(
        """(?:\b(?:available\s+credit|credit\s+available|remaining\s+credit|credit\s+remaining|credit\s+limit|cash\s+advance\s+limit|available\s+cash\s+advance)\b|الحد\s+الائتماني|الحد\s+المتاح|الائتمان\s+المتاح)""",
        RegexOption.IGNORE_CASE,
    )
    private val trailingCurrencyContext = Regex(
        """^\s*(?:HK\$|S\$|R\$|MX\$|MEX\$|CA\$|C\$|AU\$|A\$|RM|RP|[\$€£﷼₹¥₺₱₩฿₫]|SAR|SR|AED|USD|EUR|GBP|CAD|AUD|CHF|INR|PKR|TRY|EGP|KWD|QAR|BHD|OMR|JOD|JPY|CNY|HKD|SGD|SEK|NOK|DKK|ZAR|BRL|MXN|THB|IDR|MYR|PHP|VND|KRW|ر\.?\s*س|د\.?\s*إ)""",
        RegexOption.IGNORE_CASE,
    )
    private val merchantLabelHint = Regex(
        """(?:\bmerchant(?:\s+name)?\b|\bstore\b|\bpayee\b|\bbiller\b|\bservice\s+provider\b|\b(?:transaction\s+)?location\b|\boutlet(?:\s+name)?\b|\bcard\s+acceptor(?:\s+name)?\b|التاجر|المتجر|المفوتر)\s*[:\-·]\s*([A-Za-z\u0600-\u06FF][^\n\r]+)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE),
    )
    private val senderLabelHint = Regex(
        """(?:\bsender\b|\bpayer\b|\bremitter\b|المرسل|الدافع|المحول)\s*[:\-·]\s*([A-Za-z\u0600-\u06FF][^\n\r]+)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE),
    )
    private val recipientLabelHint = Regex(
        """(?:\brecipient\b|\breceiver\b|\bbeneficiary\b|\bpayee\b|المستفيد|المستلم|المحول\s+له)\s*[:\-·]\s*([A-Za-z\u0600-\u06FF][^\n\r]+)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE),
    )
    private val atHint = Regex(
        """(?:\bat\b|\bwith\b|\bon\b|لدى|عند|في)\s*[:\-·]?\s+([A-Za-z\u0600-\u06FF][^\n\r]+)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE),
    )
    private val forHint = Regex(
        """(?:\bfor\b|مقابل|عن)\s*[:\-·]?\s+([A-Za-z\u0600-\u06FF][^\n\r]+)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE),
    )
    private val toHint = Regex(
        """(?:\bto\b|إلى|الى|لـ)\s*[:\-·]?\s+([A-Za-z\u0600-\u06FF][^\n\r]+)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE),
    )
    private val fromHint = Regex(
        """(?:\bfrom\b|من)\s*[:\-·]?\s+([A-Za-z\u0600-\u06FF][^\n\r]+)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE),
    )
    private val byHint = Regex(
        """(?:\bby\b)\s*[:\-·]?\s+([A-Za-z\u0600-\u06FF][^\n\r]+)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE),
    )
    private val incomingPersonHint = Regex(
        """(?m)^([A-Za-z\u0600-\u06FF][^\n\r]{1,64}?)\s+(?:paid|sent)\s+you\b""",
        RegexOption.IGNORE_CASE,
    )

    override fun tryParse(body: String, receivedAt: Instant): ParseResult {
        val normalized = Normalize.digits(body)
        val hasAction = hasAction(normalized)

        if (securityCodeAuthorizationWords.containsMatchIn(normalized)) return ParseResult.Ignored
        if (authorizationHoldWords.containsMatchIn(normalized)) return ParseResult.Ignored
        if (salaryFinancingOfferWords.containsMatchIn(normalized)) return ParseResult.Ignored
        if (isRewardOnlyNotification(normalized)) return ParseResult.Ignored
        if (isMarketingOnlyPromotion(normalized)) return ParseResult.Ignored
        if (declinedWords.containsMatchIn(normalized)) return ParseResult.Ignored
        if (statementWords.containsMatchIn(normalized)) return ParseResult.Ignored
        if (scheduledWords.containsMatchIn(normalized)) return ParseResult.Ignored
        if (withdrawalLimitWords.containsMatchIn(normalized)) return ParseResult.Ignored
        if (feeScheduleWords.containsMatchIn(normalized)) return ParseResult.Ignored
        if (debtReminderWords.containsMatchIn(normalized)) return ParseResult.Ignored
        if (utilityBillReminderWords.containsMatchIn(normalized)) return ParseResult.Ignored
        if (recurringExpenseReminderWords.containsMatchIn(normalized)) return ParseResult.Ignored
        if (telecomRechargeReminderWords.containsMatchIn(normalized)) return ParseResult.Ignored
        if (publicServiceReminderWords.containsMatchIn(normalized)) return ParseResult.Ignored
        if (mobilityPaymentReminderWords.containsMatchIn(normalized)) return ParseResult.Ignored
        if (essentialLifeReminderWords.containsMatchIn(normalized)) return ParseResult.Ignored
        if (everydayCommerceNonPostedWords.containsMatchIn(normalized)) return ParseResult.Ignored
        if (travelNonPostedWords.containsMatchIn(normalized)) return ParseResult.Ignored
        if (retailShoppingNonPostedWords.containsMatchIn(normalized)) return ParseResult.Ignored
        if (spendingSummaryWords.containsMatchIn(normalized)) return ParseResult.Ignored
        if (limitWords.containsMatchIn(normalized) && !hasExpenseAction(normalized) && !hasIncomeAction(normalized)) {
            return ParseResult.Ignored
        }
        if (isMoneyRequestNotification(normalized)) return ParseResult.Ignored
        if (adminNoticeWords.containsMatchIn(normalized) && !hasAction) return ParseResult.Ignored
        if (securityWords.containsMatchIn(normalized) && !hasAction) return ParseResult.Ignored
        if (marketingWords.containsMatchIn(normalized) && !hasAction) return ParseResult.Ignored
        if (hasBalanceNoticeText(normalized) && !hasAction) return ParseResult.Ignored
        if (creditStateWords.containsMatchIn(normalized) && !hasAction) return ParseResult.Ignored

        val amountMatch = selectTransactionAmount(normalized)
            ?: return ParseResult.Failed("notification amount not found", listOf(id))
        val amountRaw = amountMatch.groups["num"]?.value
            ?: return ParseResult.Failed("notification amount missing", listOf(id))
        val amount = Normalize.amount(amountRaw)
            ?: return ParseResult.Failed("notification amount unparseable: $amountRaw", listOf(id))
        val currency = Normalize.currencyCode(amountMatch.groups["lead"]?.value ?: amountMatch.groups["trail"]?.value)
        val balanceAfter = extractBalanceAfter(normalized, amountMatch, currency)

        val type = when {
            isTelecomRechargeNotification(normalized) -> TxType.EXPENSE
            hasIncomeAction(normalized) -> TxType.INCOME
            isRecurringExpenseNotification(normalized) -> TxType.EXPENSE
            isPublicServicePaymentNotification(normalized) -> TxType.EXPENSE
            isMobilityPaymentNotification(normalized) -> TxType.EXPENSE
            isEssentialLifeExpenseNotification(normalized) -> TxType.EXPENSE
            isEverydayCommerceExpenseNotification(normalized) -> TxType.EXPENSE
            isTravelExpenseNotification(normalized) -> TxType.EXPENSE
            isRetailShoppingExpenseNotification(normalized) -> TxType.EXPENSE
            transferWords.containsMatchIn(normalized) -> TxType.TRANSFER
            hasExpenseAction(normalized) || isBankFeeNotification(normalized) ||
                isDebtPaymentNotification(normalized) || hasMerchantHint(normalized) -> TxType.EXPENSE
            else -> return ParseResult.Failed("notification action not found", listOf(id))
        }

        val merchantCandidate = if (type == TxType.EXPENSE) normalized.cleanMerchantCandidate(amountMatch) else null
        val merchant = when (type) {
            TxType.EXPENSE -> "ATM Withdrawal".takeIf { isWithdrawalNotification(normalized) }
                ?: "Bank fees".takeIf { isBankFeeNotification(normalized) }
                ?: "Credit card payment".takeIf { isCreditCardPaymentNotification(normalized) }
                ?: "Loan instalment".takeIf { isLoanInstalmentNotification(normalized) }
                ?: "Mobile recharge".takeIf { isTelecomRechargeNotification(normalized) && merchantCandidate == null }
                ?: normalizeExpenseMerchant(
                    body = normalized,
                    merchant = merchantCandidate,
                )
            TxType.INCOME -> null
            TxType.TRANSFER -> null
        }
        val counterparty = when (type) {
            TxType.EXPENSE -> null
            TxType.INCOME -> normalizeIncomeCounterparty(
                body = normalized,
                counterparty = cleanParty(
                    senderLabelHint.find(normalized)?.groupValues?.get(1)
                        ?: fromHint.find(normalized)?.groupValues?.get(1)
                        ?: byHint.find(normalized)?.groupValues?.get(1)
                        ?: incomingPersonHint.find(normalized)?.groupValues?.get(1),
                ),
            )
            TxType.TRANSFER -> cleanParty(recipientLabelHint.find(normalized)?.groupValues?.get(1)
                ?: toHint.find(normalized)?.groupValues?.get(1)
                ?: fromHint.find(normalized)?.groupValues?.get(1))
        }

        var confidence = 0.45f
        if (amountMatch.hasCurrency()) confidence += 0.15f
        if (merchant != null || counterparty != null) confidence += 0.15f
        if (hasAction) confidence += 0.10f

        return ParseResult.Success(
            type = type,
            amount = Money.of(amount, currency),
            merchant = merchant,
            counterparty = counterparty,
            balanceAfter = balanceAfter,
            occurredAt = receivedAt,
            confidence = confidence.coerceAtMost(0.85f),
            templateId = id,
        )
    }

    private fun hasAction(body: String): Boolean =
        hasExpenseAction(body) || hasIncomeAction(body) || transferWords.containsMatchIn(body) ||
            isBankFeeNotification(body) || isDebtPaymentNotification(body) ||
            isRecurringExpenseNotification(body) || isTelecomRechargeNotification(body) ||
            isPublicServicePaymentNotification(body) || isMobilityPaymentNotification(body) ||
            isEssentialLifeExpenseNotification(body) || isEverydayCommerceExpenseNotification(body) ||
            isTravelExpenseNotification(body) || isRetailShoppingExpenseNotification(body)

    private fun hasExpenseAction(body: String): Boolean =
        expenseWords.containsMatchIn(body) || expensePhrases.containsMatchIn(body)

    private fun hasIncomeAction(body: String): Boolean =
        incomeWords.containsMatchIn(body) || incomePhrases.containsMatchIn(body) ||
            salaryIncomeWords.containsMatchIn(body)

    private fun hasMerchantHint(body: String): Boolean =
        merchantLabelHint.containsMatchIn(body) ||
            atHint.containsMatchIn(body) ||
            toHint.containsMatchIn(body) ||
            fromHint.containsMatchIn(body) ||
            forHint.containsMatchIn(body)

    private fun isWithdrawalNotification(body: String): Boolean =
        withdrawalWords.containsMatchIn(body)

    private fun isBankFeeNotification(body: String): Boolean =
        feeWords.containsMatchIn(body) &&
            !isPublicServicePaymentNotification(body) &&
            !isEssentialLifeExpenseNotification(body) &&
            !isEverydayCommerceExpenseNotification(body) &&
            !isTravelExpenseNotification(body) &&
            !isRetailShoppingExpenseNotification(body)

    private fun isDebtPaymentNotification(body: String): Boolean =
        isCreditCardPaymentNotification(body) || isLoanInstalmentNotification(body)

    private fun isCreditCardPaymentNotification(body: String): Boolean =
        creditCardPaymentWords.containsMatchIn(body)

    private fun isLoanInstalmentNotification(body: String): Boolean =
        loanInstalmentWords.containsMatchIn(body)

    private fun isRecurringExpenseNotification(body: String): Boolean =
        subscriptionPaymentWords.containsMatchIn(body) ||
            insurancePremiumWords.containsMatchIn(body) ||
            rentPaymentWords.containsMatchIn(body)

    private fun isTelecomRechargeNotification(body: String): Boolean =
        telecomRechargeWords.containsMatchIn(body)

    private fun isPublicServicePaymentNotification(body: String): Boolean =
        trafficFinePaymentWords.containsMatchIn(body) ||
            governmentServicePaymentWords.containsMatchIn(body)

    private fun isMobilityPaymentNotification(body: String): Boolean =
        parkingPaymentWords.containsMatchIn(body) ||
            tollPaymentWords.containsMatchIn(body) ||
            transitFareWords.containsMatchIn(body)

    private fun isEssentialLifeExpenseNotification(body: String): Boolean =
        pharmacyPaymentWords.containsMatchIn(body) ||
            medicalPaymentWords.containsMatchIn(body) ||
            educationPaymentWords.containsMatchIn(body) ||
            zakatPaymentWords.containsMatchIn(body) ||
            charityDonationWords.containsMatchIn(body)

    private fun isEverydayCommerceExpenseNotification(body: String): Boolean =
        fuelPaymentWords.containsMatchIn(body) ||
            groceryPaymentWords.containsMatchIn(body) ||
            restaurantPaymentWords.containsMatchIn(body) ||
            coffeePaymentWords.containsMatchIn(body) ||
            foodDeliveryPaymentWords.containsMatchIn(body) ||
            taxiRidePaymentWords.containsMatchIn(body)

    private fun isTravelExpenseNotification(body: String): Boolean =
        flightTicketPaymentWords.containsMatchIn(body) ||
            hotelPaymentWords.containsMatchIn(body) ||
            travelBookingPaymentWords.containsMatchIn(body) ||
            carRentalPaymentWords.containsMatchIn(body)

    private fun isRetailShoppingExpenseNotification(body: String): Boolean =
        clothingPurchaseWords.containsMatchIn(body) ||
            electronicsPurchaseWords.containsMatchIn(body) ||
            onlineShoppingPurchaseWords.containsMatchIn(body)

    private fun String.cleanMerchantCandidate(amountMatch: MatchResult): String? =
        cleanParty(merchantLabelHint.find(this)?.groupValues?.get(1)
            ?: recipientLabelHint.find(this)?.groupValues?.get(1)
            ?: toHint.find(this)?.groupValues?.get(1)
            ?: atHint.find(this)?.groupValues?.get(1)
            ?: byHint.find(this)?.groupValues?.get(1)
            ?: forHint.find(this)?.groupValues?.get(1)
            ?: fromHint.find(this)?.groupValues?.get(1))
            ?: partyBeforeAmount(this, amountMatch)
            ?: partyAfterAmount(this, amountMatch)

    private fun normalizeExpenseMerchant(body: String, merchant: String?): String? =
        normalizePublicServiceMerchant(body, merchant)
            ?: normalizeMobilityPaymentMerchant(body, merchant)
            ?: normalizeUtilityBillMerchant(body, merchant)
            ?: normalizeRecurringExpenseMerchant(body, merchant)
            ?: normalizeEssentialLifeMerchant(body, merchant)
            ?: normalizeEverydayCommerceMerchant(body, merchant)
            ?: normalizeTravelMerchant(body, merchant)
            ?: normalizeRetailShoppingMerchant(body, merchant)
            ?: merchant

    private fun normalizePublicServiceMerchant(body: String, merchant: String?): String? {
        val label = publicServiceLabel(body) ?: return null
        if (merchant == null || publicServiceLabel(merchant) != null) return label
        return merchant
    }

    private fun publicServiceLabel(body: String): String? = when {
        trafficFinePaymentWords.containsMatchIn(body) -> "Traffic fine payment"
        governmentServicePaymentWords.containsMatchIn(body) -> "Government service payment"
        else -> null
    }

    private fun normalizeMobilityPaymentMerchant(body: String, merchant: String?): String? {
        val label = mobilityPaymentLabel(body) ?: return null
        if (merchant == null || mobilityPaymentLabel(merchant) != null) return label
        return merchant
    }

    private fun mobilityPaymentLabel(body: String): String? = when {
        parkingPaymentWords.containsMatchIn(body) -> "Parking payment"
        tollPaymentWords.containsMatchIn(body) -> "Toll payment"
        transitFareWords.containsMatchIn(body) -> "Transit fare"
        else -> null
    }

    private fun normalizeUtilityBillMerchant(body: String, merchant: String?): String? {
        if (merchant != null) {
            return when {
                electricityBillWords.containsMatchIn(merchant) -> "Electric company"
                waterBillWords.containsMatchIn(merchant) -> "Water company"
                genericBillPaymentWords.containsMatchIn(merchant) -> "Bill payment"
                else -> null
            }
        }
        return when {
            electricityBillWords.containsMatchIn(body) -> "Electric company"
            waterBillWords.containsMatchIn(body) -> "Water company"
            genericBillPaymentWords.containsMatchIn(body) -> "Bill payment"
            else -> null
        }
    }

    private fun normalizeRecurringExpenseMerchant(body: String, merchant: String?): String? {
        val label = recurringExpenseLabel(body) ?: return null
        if (merchant == null || recurringExpenseLabel(merchant) != null) return label
        return merchant
    }

    private fun recurringExpenseLabel(body: String): String? = when {
        subscriptionPaymentWords.containsMatchIn(body) -> "Subscription payment"
        insurancePremiumWords.containsMatchIn(body) -> "Insurance premium"
        rentPaymentWords.containsMatchIn(body) -> "Rent payment"
        else -> null
    }

    private fun normalizeEssentialLifeMerchant(body: String, merchant: String?): String? {
        val label = essentialLifeLabel(body) ?: return null
        if (merchant == null || genericEssentialLifeMerchantWords.matches(merchant.trim())) return label
        return merchant
    }

    private fun essentialLifeLabel(body: String): String? = when {
        pharmacyPaymentWords.containsMatchIn(body) -> "Pharmacy payment"
        medicalPaymentWords.containsMatchIn(body) -> "Medical payment"
        educationPaymentWords.containsMatchIn(body) -> "Education payment"
        zakatPaymentWords.containsMatchIn(body) -> "Zakat payment"
        charityDonationWords.containsMatchIn(body) -> "Charity donation"
        else -> null
    }

    private fun normalizeEverydayCommerceMerchant(body: String, merchant: String?): String? {
        val label = everydayCommerceLabel(body) ?: return null
        if (merchant == null || genericEverydayCommerceMerchantWords.matches(merchant.trim())) return label
        return merchant
    }

    private fun everydayCommerceLabel(body: String): String? = when {
        fuelPaymentWords.containsMatchIn(body) -> "Fuel purchase"
        groceryPaymentWords.containsMatchIn(body) -> "Grocery purchase"
        restaurantPaymentWords.containsMatchIn(body) -> "Restaurant payment"
        coffeePaymentWords.containsMatchIn(body) -> "Coffee payment"
        foodDeliveryPaymentWords.containsMatchIn(body) -> "Food delivery payment"
        taxiRidePaymentWords.containsMatchIn(body) -> "Taxi ride payment"
        else -> null
    }

    private fun normalizeTravelMerchant(body: String, merchant: String?): String? {
        val label = travelLabel(body) ?: return null
        if (merchant == null || genericTravelMerchantWords.matches(merchant.trim())) return label
        return merchant
    }

    private fun travelLabel(body: String): String? = when {
        flightTicketPaymentWords.containsMatchIn(body) -> "Flight ticket"
        hotelPaymentWords.containsMatchIn(body) -> "Hotel payment"
        travelBookingPaymentWords.containsMatchIn(body) -> "Travel booking"
        carRentalPaymentWords.containsMatchIn(body) -> "Car rental payment"
        else -> null
    }

    private fun normalizeRetailShoppingMerchant(body: String, merchant: String?): String? {
        val label = retailShoppingLabel(body) ?: return null
        if (merchant == null || genericRetailShoppingMerchantWords.matches(merchant.trim())) return label
        return merchant
    }

    private fun retailShoppingLabel(body: String): String? = when {
        clothingPurchaseWords.containsMatchIn(body) -> "Clothing purchase"
        electronicsPurchaseWords.containsMatchIn(body) -> "Electronics purchase"
        onlineShoppingPurchaseWords.containsMatchIn(body) -> "Online shopping purchase"
        else -> null
    }

    private fun normalizeIncomeCounterparty(body: String, counterparty: String?): String? {
        if (!salaryIncomeWords.containsMatchIn(body)) return counterparty
        val label = if (ArabicSalaryTerms.any { body.contains(it) }) "راتب" else "Salary"
        val cleaned = counterparty?.takeIf { it.isNotBlank() } ?: return label
        if (salaryLabelWords.containsMatchIn(cleaned)) return cleaned
        return "$label - $cleaned".take(48).trim()
    }

    private fun isMarketingOnlyPromotion(body: String): Boolean =
        marketingOnlyWords.containsMatchIn(body) && !postedTransactionEvidence.containsMatchIn(body)

    private fun isRewardOnlyNotification(body: String): Boolean {
        if (!rewardWords.containsMatchIn(body) || hasIncomeAction(body)) return false
        val currencyAmounts = amountWithCurrency.findAll(body)
            .filter { it.hasCurrencyMarker(body) }
            .toList()
        return currencyAmounts.isNotEmpty() && currencyAmounts.all { it.isRewardAmount(body) }
    }

    private fun isMoneyRequestNotification(body: String): Boolean =
        requestWords.containsMatchIn(body) && amountWithCurrency.containsMatchIn(body)

    private fun selectTransactionAmount(body: String): MatchResult? {
        val matches = amountWithCurrency.findAll(body).toList()
        if (matches.isEmpty()) return null
        val currencyMatches = matches.filter { it.hasCurrencyMarker(body) }
        val withoutReward = currencyMatches.ifEmpty { matches }
            .let { amountCandidates -> amountCandidates.filterNot { it.isRewardAmount(body) }.ifEmpty { amountCandidates } }
        val candidates = withoutReward
            .filterNot { it.isCardOrAccountIdentifier(body) }
            .ifEmpty { withoutReward }
        val actionStart = firstActionIndex(body)
        val afterAction = actionStart?.let { index -> candidates.filter { it.range.first >= index } }.orEmpty()
        afterAction.firstOrNull { !it.isBalanceAmount(body) }?.let { return it }
        afterAction.firstOrNull()?.let { return it }
        return candidates.firstOrNull { !it.isBalanceAmount(body) } ?: candidates.firstOrNull()
    }

    private fun extractBalanceAfter(body: String, amountMatch: MatchResult, transactionCurrency: String): Money? {
        val balanceMatch = amountWithCurrency.findAll(body)
            .filterNot { it.range == amountMatch.range }
            .filterNot { it.isRewardAmount(body) }
            .filter { it.isBalanceAfterAmount(body, amountMatch) }
            .sortedWith(compareBy<MatchResult> { if (it.range.first > amountMatch.range.last) 0 else 1 }.thenBy { it.range.first })
            .firstOrNull()
            ?: return null
        val amountRaw = balanceMatch.groups["num"]?.value ?: return null
        val amount = Normalize.amount(amountRaw) ?: return null
        val currency = balanceMatch.currencyCodeOrNull() ?: transactionCurrency
        return Money.of(amount, currency)
    }

    private fun firstActionIndex(body: String): Int? = listOfNotNull(
        expenseWords.find(body)?.range?.first,
        expensePhrases.find(body)?.range?.first,
        incomeWords.find(body)?.range?.first,
        incomePhrases.find(body)?.range?.first,
        salaryIncomeWords.find(body)?.range?.first,
        telecomRechargeWords.find(body)?.range?.first,
        trafficFinePaymentWords.find(body)?.range?.first,
        governmentServicePaymentWords.find(body)?.range?.first,
        parkingPaymentWords.find(body)?.range?.first,
        tollPaymentWords.find(body)?.range?.first,
        transitFareWords.find(body)?.range?.first,
        transferWords.find(body)?.range?.first,
        creditCardPaymentWords.find(body)?.range?.first,
        loanInstalmentWords.find(body)?.range?.first,
        subscriptionPaymentWords.find(body)?.range?.first,
        insurancePremiumWords.find(body)?.range?.first,
        rentPaymentWords.find(body)?.range?.first,
        pharmacyPaymentWords.find(body)?.range?.first,
        medicalPaymentWords.find(body)?.range?.first,
        educationPaymentWords.find(body)?.range?.first,
        zakatPaymentWords.find(body)?.range?.first,
        charityDonationWords.find(body)?.range?.first,
        fuelPaymentWords.find(body)?.range?.first,
        groceryPaymentWords.find(body)?.range?.first,
        restaurantPaymentWords.find(body)?.range?.first,
        coffeePaymentWords.find(body)?.range?.first,
        foodDeliveryPaymentWords.find(body)?.range?.first,
        taxiRidePaymentWords.find(body)?.range?.first,
        flightTicketPaymentWords.find(body)?.range?.first,
        hotelPaymentWords.find(body)?.range?.first,
        travelBookingPaymentWords.find(body)?.range?.first,
        carRentalPaymentWords.find(body)?.range?.first,
        clothingPurchaseWords.find(body)?.range?.first,
        electronicsPurchaseWords.find(body)?.range?.first,
        onlineShoppingPurchaseWords.find(body)?.range?.first,
    ).minOrNull()

    private fun MatchResult.isBalanceAmount(body: String): Boolean {
        val prefixStart = (range.first - AmountPrefixWindow).coerceAtLeast(0)
        val suffixEnd = (range.last + AmountSuffixWindow + 1).coerceAtMost(body.length)
        val prefix = body.substring(prefixStart, range.first)
        val suffix = body.substring(range.last + 1, suffixEnd)
        return hasBalanceAmountContext(prefix) || hasBalanceAmountContext(suffix)
    }

    private fun MatchResult.isBalanceAfterAmount(body: String, amountMatch: MatchResult): Boolean {
        if (!isBalanceAmount(body)) return false
        if (range.first > amountMatch.range.last) return true
        if (range.last >= amountMatch.range.first) return false

        val between = body.substring(range.last + 1, amountMatch.range.first)
        return afterTransactionBalanceContext.containsMatchIn(between)
    }

    private fun MatchResult.isRewardAmount(body: String): Boolean {
        val prefixStart = (range.first - RewardPrefixWindow).coerceAtLeast(0)
        val suffixEnd = (range.last + RewardSuffixWindow + 1).coerceAtMost(body.length)
        val prefix = body.substring(prefixStart, range.first)
        val suffix = body.substring(range.last + 1, suffixEnd)
        return rewardAmountContext.containsMatchIn(prefix) || rewardAmountContext.containsMatchIn(suffix)
    }

    private fun MatchResult.isCardOrAccountIdentifier(body: String): Boolean {
        val prefixStart = (range.first - IdentifierPrefixWindow).coerceAtLeast(0)
        val prefix = body.substring(prefixStart, range.first)
        val compactPrefix = prefix.lowercase().trimEnd()
        return compactPrefix.endsWith("card ending") ||
            compactPrefix.endsWith("credit card ending") ||
            compactPrefix.endsWith("account ending") ||
            compactPrefix.endsWith("acct ending") ||
            compactPrefix.endsWith("ending") ||
            compactPrefix.endsWith("last four") ||
            compactPrefix.endsWith("last 4") ||
            cardOrAccountIdentifierPrefix.containsMatchIn(prefix)
    }

    private fun hasBalanceAmountContext(text: String): Boolean {
        return balanceAmountContext.containsMatchIn(text) ||
            ArabicBalanceTerms.any { text.contains(it) }
    }

    private fun hasBalanceNoticeText(text: String): Boolean =
        balanceWords.containsMatchIn(text) || ArabicBalanceTerms.any { text.contains(it) }

    private fun MatchResult.hasCurrency(): Boolean =
        groups["lead"]?.value?.isNotBlank() == true || groups["trail"]?.value?.isNotBlank() == true

    private fun MatchResult.currencyCodeOrNull(): String? {
        val marker = groups["lead"]?.value?.takeIf { it.isNotBlank() }
            ?: groups["trail"]?.value?.takeIf { it.isNotBlank() }
        return marker?.let(Normalize::currencyCode)
    }

    private fun MatchResult.hasCurrencyMarker(body: String): Boolean {
        if (hasCurrency()) return true
        val suffixEnd = (range.last + CurrencySuffixWindow + 1).coerceAtMost(body.length)
        val suffix = body.substring(range.last + 1, suffixEnd)
        return trailingCurrencyContext.containsMatchIn(suffix)
    }

    private fun partyBeforeAmount(body: String, amountMatch: MatchResult): String? {
        val beforeAmount = body
            .substring(0, amountMatch.range.first)
            .lineSequence()
            .lastOrNull()
            .orEmpty()
            .trim()
        val patterns = listOf(
            Regex(
                """\b(?:payment|purchase|transaction|card\s+transaction|card\s+purchase|debit\s+card\s+transaction)\s+(?:successful|completed|approved|posted|confirmed)\s*[:\-]\s*(.+)$""",
                RegexOption.IGNORE_CASE,
            ),
            Regex(
                """\b(?:your\s+)?card(?:\s+ending\s+(?:in\s+)?\d{2,4})?\s+(?:was\s+)?used\s*[:\-]\s*(.+)$""",
                RegexOption.IGNORE_CASE,
            ),
            Regex(
                """\b(?:your\s+)?(?:(?:debit|credit)\s+)?card(?:\s+ending\s+(?:in\s+)?\d{2,4})?\s+(?:was\s+)?used\s+(?:at|on)\s+(.+?)(?:\s+for)?$""",
                RegexOption.IGNORE_CASE,
            ),
            Regex("""\b(?:new\s+(?:card\s+)?transaction|transaction)\s*[:\-]\s*(.+)$""", RegexOption.IGNORE_CASE),
            Regex("""\b(?:you\s+)?paid\s+(.+)$""", RegexOption.IGNORE_CASE),
            Regex("""^(?!your\s+card\b)(.+?)\s+charged(?:\s+(?:your\s+card|you|card))?$""", RegexOption.IGNORE_CASE),
            Regex(
                """\b(?:debit\s+card\s+transaction|debit\s+card\s+purchase|card\s+transaction|card\s+charge|card\s+purchase|purchase)\s+(.+)$""",
                RegexOption.IGNORE_CASE,
            ),
            Regex("""\bpurchase\s+from\s+(.+)$""", RegexOption.IGNORE_CASE),
            Regex("""\b(?:was\s+charged\s+by|were\s+charged\s+by|charged\s+by)\s+(.+)$""", RegexOption.IGNORE_CASE),
        )
        return patterns
            .asSequence()
            .mapNotNull { it.find(beforeAmount)?.groupValues?.get(1) }
            .mapNotNull(::cleanParty)
            .firstOrNull()
    }

    private fun partyAfterAmount(body: String, amountMatch: MatchResult): String? {
        val afterAmount = body
            .substring(amountMatch.range.last + 1)
            .lineSequence()
            .firstOrNull()
            .orEmpty()
            .trim(' ', '.', ',', '-', '·', ':')
        if (!partyStartsWithLetter.matches(afterAmount)) return null

        val candidate = afterAmount
            .replace(trailingNonPartyContext, "")
            .trim(' ', '.', ',', '-', '·', ':')
        return cleanParty(candidate)
    }

    private fun cleanParty(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw
            .lineSequence()
            .firstOrNull()
            ?.replace(amountWithCurrency, "")
            ?.replace(trailingBalancePartyContext, "")
            ?.replace(Regex("""\s+\band\s+earned\b.*$""", RegexOption.IGNORE_CASE), "")
            ?.replace(
                Regex(
                    """\s+\b(?:(?:was|were|is|has\s+been|have\s+been)\s+)?(?:confirmed|successful|completed|approved|posted)\b\.?$""",
                    RegexOption.IGNORE_CASE,
                ),
                "",
            )
            ?.replace(Regex("""\b(?:for|using|with|via|card|ending|منتهية|البطاقة)\b.*$""", RegexOption.IGNORE_CASE), "")
            ?.trim(' ', '.', ',', '-', '·', ':')
            ?.take(48)
            ?.trim()
        return cleaned?.takeIf { it.isNotBlank() }
    }

    private companion object {
        private const val AmountPrefixWindow = 32
        private const val AmountSuffixWindow = 24
        private const val RewardPrefixWindow = 24
        private const val RewardSuffixWindow = 16
        private const val CurrencySuffixWindow = 12
        private const val IdentifierPrefixWindow = 32
        private val ArabicBalanceTerms = listOf("رصيد", "الرصيد", "المتاح", "الرصيد المتبقي")
        private val ArabicSalaryTerms = listOf("راتب", "رواتب", "أجر", "اجر")
        private val withdrawalWords = Regex(
            """(?:\b(?:atm\s+withdrawal|cash\s+withdrawal|withdrawal|withdrawn)\b|سحب|صراف)""",
            RegexOption.IGNORE_CASE,
        )
        private val afterTransactionBalanceContext = Regex(
            """(?:\bafter\s+(?:debit|purchase|payment|transaction|spend|withdrawal|transfer)\b|بعد\s+(?:خصم|شراء|دفع|سحب|تحويل|العملية|عملية))""",
            RegexOption.IGNORE_CASE,
        )
        private val partyStartsWithLetter = Regex("""^[A-Za-z\u0600-\u06FF].*""")
        private val trailingNonPartyContext = Regex(
            """(?:\b(?:balance|available|remaining\s+balance|current\s+balance|card|ending|account|acct|approved|confirmed|successful|completed|posted|paid|settled|charged|declined)\b|رصيد|الرصيد|المتاح|بطاقة|البطاقة|حساب|معتمد|مؤكد|ناجح|مكتمل).*""",
            RegexOption.IGNORE_CASE,
        )
        private val trailingBalancePartyContext = Regex(
            """(?:\b(?:balance|available|remaining\s+balance|current\s+balance)\b|رصيد|الرصيد|المتاح).*""",
            RegexOption.IGNORE_CASE,
        )
        private val cardOrAccountIdentifierPrefix = Regex(
            """(?:\b(?:card|account|acct)\s+(?:ending|ending\s+in|last\s+four|number)\s*$|\b(?:ending|last\s+four)\s*$|(?:بطاقة|البطاقة|حساب|الحساب)[^\n\r\d]{0,20}$)""",
            RegexOption.IGNORE_CASE,
        )
        private val genericEssentialLifeMerchantWords = Regex(
            """(?:medical\s+payment|pharmacy(?:\s+payment)?|education\s+payment|school\s+fees?|tuition(?:\s+payment)?|charity\s+donation|donation\s+payment|zakat(?:\s+payment)?|رسوم\s+مدرسية|صيدلية|زكاة|زكاه|صدقة|صدقه|تبرع)""",
            RegexOption.IGNORE_CASE,
        )
        private val genericEverydayCommerceMerchantWords = Regex(
            """(?:fuel\s+purchase|fuel\s+payment|petrol\s+payment|grocery\s+purchase|grocery\s+payment|supermarket\s+purchase|restaurant\s+payment|coffee\s+payment|cafe\s+payment|coffee\s+shop\s+payment|food\s+delivery\s+payment|delivery\s+order|taxi\s+ride\s+payment|taxi\s+fare|ride\s+fare|وقود|بنزين|بقالة|سوبر\s*ماركت|مطعم|قهوة|مقهى|كافيه|توصيل\s+طعام|مشوار|تاكسي)""",
            RegexOption.IGNORE_CASE,
        )
        private val genericTravelMerchantWords = Regex(
            """(?:flight\s+ticket|flight\s+payment|airline\s+payment|air\s+ticket|hotel\s+payment|travel\s+booking|travel\s+payment|trip\s+payment|car\s+rental\s+payment|rental\s+car\s+payment|تذكرة\s+طيران|تذاكر\s+طيران|تذكرة\s+سفر|تذاكر\s+سفر|فندق|إقامة|اقامة|حجز\s+سفر|تأجير\s+سيارة|تاجير\s+سيارة|استئجار\s+سيارة)""",
            RegexOption.IGNORE_CASE,
        )
        private val genericRetailShoppingMerchantWords = Regex(
            """(?:clothing\s+purchase|clothing\s+payment|apparel\s+payment|fashion\s+payment|footwear\s+payment|shoes\s+payment|electronics\s+purchase|electronics\s+payment|electronic\s+goods\s+payment|device\s+purchase|device\s+payment|online\s+shopping\s+purchase|online\s+shopping\s+payment|online\s+purchase|e[-\s]?commerce\s+payment|marketplace\s+payment|ملابس|ثياب|أزياء|ازياء|أحذية|احذية|إلكترونيات|الكترونيات|أجهزة|اجهزة|تسوق\s+إلكتروني|تسوق\s+الكتروني|شراء\s+إلكتروني|شراء\s+الكتروني)""",
            RegexOption.IGNORE_CASE,
        )

        val BankPackagePattern = Regex(
            """^notification:.*(alrajhi|stcpay|stcbank|d360|barq|alinma|riyad|snb|alahli|com\.anb\.mobile\.prod|com\.anb\.business|com\.onesingleview\.android\.anb|albilad|bsf|saib|jazira|emiratesnbd|adcb|mashreq|bankfab|qnb|boubyan|kfh|bankmuscat|wise|transferwise|revolut|chase|capitalone|mercury|monzo|n26|starling|hsbc|barclays|lloyds|natwest|santander|halifax|usbank|pnc|sofi|walletnfcrel|paisa|samsung\.android\.spay|paypal|venmo|squareup\.cash|americanexpress|amex|bankofamerica|bofa|wellsfargo|citimobile|usaa|discoverfinancial|truist|citizensbank|payoneer|remitly|westernunion|bunq|nubank|bbva|scotiabank|tdbank|rbc|commbank|westpac|nab\.mobile|anz\.android|dbsmbanking|ocbc|uob|maybank|cimb|hdfcbank|icici|axisbank|kotak|cibc|com\.bmo\.mobile|com\.bmoharris\.digital|com\.bmo\.business\.mobile|desjardins|tangerine|wealthsimple|eqbank|ca\.koho|com\.db\.pwcc\.dbmobile|com\.db\.pbc\.mibanco|de\.ingdiba\.bankingapp|deutschebank|starfinanz|sparkasse|com\.sabb\.mobilebanking|urpay|com\.es\.mobily).*""",
            RegexOption.IGNORE_CASE,
        )
    }
}
