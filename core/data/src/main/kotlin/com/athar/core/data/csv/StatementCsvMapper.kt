package com.athar.core.data.csv

import com.athar.core.common.money.Money
import com.athar.core.domain.model.TxType
import com.athar.core.domain.repo.CsvImportColumnMapping
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

internal data class StatementCsvColumns(
    val dateIdx: Int,
    val merchantIdx: Int,
    val amountIdx: Int?,
    val debitIdx: Int?,
    val creditIdx: Int?,
    val currencyIdx: Int?,
    val categoryIdx: Int?,
    val typeIdx: Int?,
    val notesIdx: Int?,
    val signedPositiveAmount: Boolean,
)

internal data class StatementCsvMappedRow(
    val date: LocalDate,
    val merchant: String,
    val amount: BigDecimal,
    val currency: String,
    val type: TxType,
    val category: String?,
    val notes: String?,
)

internal object StatementCsvMapper {

    fun detect(header: List<String>, mapping: CsvImportColumnMapping? = null): StatementCsvColumns? {
        val normalized = header.map(::normalizeHeader)
        val dateIdx = mapping?.date.mappedIndex(header, normalized) ?: find(normalized, DATE_HEADERS)
        val merchantIdx = mapping?.merchant.mappedIndex(header, normalized) ?: find(normalized, MERCHANT_HEADERS)
        val amountIdx = mapping?.amount.mappedIndex(header, normalized) ?: find(normalized, AMOUNT_HEADERS).takeIfFound()
        val debitIdx = mapping?.debit.mappedIndex(header, normalized) ?: find(normalized, DEBIT_HEADERS).takeIfFound()
        val creditIdx = mapping?.credit.mappedIndex(header, normalized) ?: find(normalized, CREDIT_HEADERS).takeIfFound()

        if (dateIdx < 0 || merchantIdx < 0 || (amountIdx == null && debitIdx == null && creditIdx == null)) {
            return null
        }

        val merchantHeader = normalized[merchantIdx]
        val amountHeader = amountIdx?.let { normalized[it] }
        val signedPositiveAmount =
            merchantHeader !in ATHAR_MERCHANT_HEADERS ||
                amountHeader?.let { h -> SIGNED_AMOUNT_HEADERS.any { h == it || h.startsWith("$it ") } } == true ||
                normalized.any { it in BALANCE_HEADERS }

        return StatementCsvColumns(
            dateIdx = dateIdx,
            merchantIdx = merchantIdx,
            amountIdx = amountIdx,
            debitIdx = debitIdx,
            creditIdx = creditIdx,
            currencyIdx = mapping?.currency.mappedIndex(header, normalized) ?: find(normalized, CURRENCY_HEADERS).takeIfFound(),
            categoryIdx = mapping?.category.mappedIndex(header, normalized) ?: find(normalized, CATEGORY_HEADERS).takeIfFound(),
            typeIdx = mapping?.type.mappedIndex(header, normalized) ?: find(normalized, TYPE_HEADERS).takeIfFound(),
            notesIdx = mapping?.notes.mappedIndex(header, normalized) ?: find(normalized, NOTES_HEADERS).takeIfFound(),
            signedPositiveAmount = signedPositiveAmount,
        )
    }

    fun map(
        row: List<String>,
        columns: StatementCsvColumns,
        positiveAmountFallbackType: TxType? = null,
    ): StatementCsvMappedRow? {
        val date = parseDate(cell(row, columns.dateIdx)) ?: return null
        val merchant = cell(row, columns.merchantIdx).trim().takeIf { it.isNotBlank() } ?: return null
        val amountWithType = amountWithType(row, columns, positiveAmountFallbackType) ?: return null
        val currency = parseCurrency(cell(row, columns.currencyIdx))
            ?: parseCurrency(amountWithType.raw)
            ?: Money.SAR
        val category = cell(row, columns.categoryIdx).trim().takeIf { it.isNotBlank() }
        val notes = cell(row, columns.notesIdx).trim().takeIf { it.isNotBlank() }

        return StatementCsvMappedRow(
            date = date,
            merchant = merchant,
            amount = amountWithType.amount,
            currency = currency,
            type = amountWithType.type,
            category = category,
            notes = notes,
        )
    }

    private fun amountWithType(
        row: List<String>,
        columns: StatementCsvColumns,
        positiveAmountFallbackType: TxType?,
    ): AmountWithType? {
        val explicitType = parseType(cell(row, columns.typeIdx))

        val debit = columns.debitIdx?.let { idx ->
            val raw = cell(row, idx)
            parseAmount(raw)?.takeIf { it.amount.signum() != 0 }?.let { AmountCandidate(raw, it) }
        }
        val credit = columns.creditIdx?.let { idx ->
            val raw = cell(row, idx)
            parseAmount(raw)?.takeIf { it.amount.signum() != 0 }?.let { AmountCandidate(raw, it) }
        }

        if (debit != null || credit != null) {
            val candidate = when {
                debit != null && credit == null -> debit to TxType.EXPENSE
                credit != null && debit == null -> credit to TxType.INCOME
                else -> return null
            }
            return AmountWithType(
                raw = candidate.first.raw,
                amount = candidate.first.parsed.amount.abs(),
                type = explicitType ?: parseType(candidate.first.raw) ?: candidate.second,
            )
        }

        val raw = columns.amountIdx?.let { cell(row, it) }.orEmpty()
        val parsed = parseAmount(raw) ?: return null
        val type = explicitType ?: parseType(raw) ?: when {
            parsed.amount.signum() < 0 -> TxType.EXPENSE
            parsed.amount.signum() > 0 && positiveAmountFallbackType != null -> positiveAmountFallbackType
            parsed.amount.signum() > 0 && columns.signedPositiveAmount -> TxType.INCOME
            else -> TxType.EXPENSE
        }
        return AmountWithType(
            raw = raw,
            amount = parsed.amount.abs(),
            type = type,
        )
    }

    private fun parseAmount(raw: String): ParsedAmount? {
        val normalizedDigits = normalizeDigits(raw).trim()
        if (normalizedDigits.isBlank()) return null

        val normalizedSeparators = normalizedDigits
            .replace('٫', '.')
            .replace('٬', ',')
            .replace('\u00A0', ' ')
        val negative =
            normalizedSeparators.startsWith("-") ||
                normalizedSeparators.endsWith("-") ||
                normalizedSeparators.contains("(") && normalizedSeparators.contains(")") ||
                parseType(normalizedSeparators) == TxType.EXPENSE

        val numeric = normalizedSeparators
            .replace(Regex("""[A-Za-z\u0600-\u06FF.]*ر\.س[A-Za-z\u0600-\u06FF.]*"""), "")
            .replace(Regex("""[^\d,.\-+]"""), "")
            .replace("+", "")
            .replace("-", "")
            .trim()
        if (numeric.isBlank()) return null

        val canonical = canonicalDecimal(numeric) ?: return null
        val value = runCatching { BigDecimal(canonical) }.getOrNull() ?: return null
        val signed = if (negative) value.negate() else value
        return ParsedAmount(signed)
    }

    private fun canonicalDecimal(raw: String): String? {
        val s = raw.trim()
        if (s.isBlank()) return null
        val lastComma = s.lastIndexOf(',')
        val lastDot = s.lastIndexOf('.')

        return when {
            lastComma >= 0 && lastDot >= 0 -> {
                val decimal = if (lastComma > lastDot) ',' else '.'
                s.filter { it.isDigit() || it == decimal }
                    .replace(decimal, '.')
            }
            lastComma >= 0 -> normalizeSingleSeparator(s, ',')
            lastDot >= 0 -> normalizeSingleSeparator(s, '.')
            else -> s.filter { it.isDigit() }
        }.takeIf { it.isNotBlank() }
    }

    private fun normalizeSingleSeparator(raw: String, separator: Char): String {
        val parts = raw.split(separator)
        return if (parts.size == 2 && parts[1].length in 1..2) {
            parts[0].filter(Char::isDigit) + "." + parts[1].filter(Char::isDigit)
        } else {
            raw.filter(Char::isDigit)
        }
    }

    private fun parseDate(raw: String): LocalDate? {
        val s = normalizeDigits(raw).trim()
        if (s.isBlank()) return null

        runCatching { return LocalDate.parse(s) }
        if (s.length >= 10) {
            runCatching { return LocalDate.parse(s.take(10)) }
        }
        if (s.length == 8 && s.all(Char::isDigit)) {
            runCatching { return LocalDate(s.take(4).toInt(), s.substring(4, 6).toInt(), s.takeLast(2).toInt()) }
        }
        s.toDoubleOrNull()?.let { serial ->
            if (serial in 36526.0..73050.0) {
                return runCatching {
                    val date = java.time.LocalDate.of(1899, 12, 30).plusDays(serial.toLong())
                    LocalDate(date.year, date.monthValue, date.dayOfMonth)
                }.getOrNull()
            }
        }

        val parts = s.split("/", "-", ".")
        if (parts.size == 3) {
            val a = parts[0].toIntOrNull() ?: return null
            val b = parts[1].toIntOrNull() ?: return null
            val c = parts[2].toIntOrNull() ?: return null
            return runCatching {
                when {
                    c > 31 && a in 1..31 && b in 1..12 -> LocalDate(c, b, a)
                    c > 31 && a in 1..12 && b in 1..31 -> LocalDate(c, a, b)
                    a > 31 && b in 1..12 && c in 1..31 -> LocalDate(a, b, c)
                    else -> null
                }
            }.getOrNull()
        }
        return null
    }

    private fun parseType(raw: String): TxType? {
        val normalized = normalizeHeader(raw)
        if (normalized.isBlank()) return null
        return when {
            normalized in TRANSFER_VALUES || TRANSFER_VALUES.any { normalized.containsWord(it) } -> TxType.TRANSFER
            normalized in INCOME_VALUES || INCOME_VALUES.any { normalized.containsWord(it) } -> TxType.INCOME
            normalized in EXPENSE_VALUES || EXPENSE_VALUES.any { normalized.containsWord(it) } -> TxType.EXPENSE
            else -> null
        }
    }

    private fun parseCurrency(raw: String): String? {
        val upper = normalizeDigits(raw).uppercase()
        val normalized = normalizeHeader(raw)
        if (upper.contains("ر.س") || normalized.contains("ريال") || upper.contains("SAR")) return Money.SAR

        return Regex("""(?<![A-Z])([A-Z]{3})(?![A-Z])""")
            .findAll(upper)
            .map { it.groupValues[1] }
            .firstOrNull { it in KNOWN_CURRENCY_CODES }
    }

    private fun find(header: List<String>, aliases: Set<String>): Int =
        header.indexOfFirst { h ->
            aliases.any { alias -> h == alias || h.startsWith("$alias ") || h.endsWith(" $alias") }
        }

    private fun String?.mappedIndex(header: List<String>, normalized: List<String>): Int? {
        val requested = this?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val normalizedRequested = normalizeHeader(requested)
        return header.indexOfFirst { it.trim() == requested }
            .takeIfFound()
            ?: normalized.indexOfFirst { it == normalizedRequested }.takeIfFound()
    }

    private fun cell(row: List<String>, idx: Int?): String =
        if (idx != null && idx >= 0 && idx < row.size) row[idx] else ""

    private fun Int.takeIfFound(): Int? = takeIf { it >= 0 }

    private fun normalizeHeader(raw: String): String =
        normalizeDigits(raw)
            .replace("\uFEFF", "")
            .trim()
            .lowercase()
            .replace('أ', 'ا')
            .replace('إ', 'ا')
            .replace('آ', 'ا')
            .replace('ى', 'ي')
            .replace('ة', 'ه')
            .replace(Regex("""[-_:/\\(){}\[\]|]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

    private fun normalizeDigits(raw: String): String = buildString(raw.length) {
        raw.forEach { ch ->
            append(
                when (ch) {
                    '٠', '۰' -> '0'
                    '١', '۱' -> '1'
                    '٢', '۲' -> '2'
                    '٣', '۳' -> '3'
                    '٤', '۴' -> '4'
                    '٥', '۵' -> '5'
                    '٦', '۶' -> '6'
                    '٧', '۷' -> '7'
                    '٨', '۸' -> '8'
                    '٩', '۹' -> '9'
                    else -> ch
                },
            )
        }
    }

    private fun String.containsWord(word: String): Boolean =
        Regex("""(^|\s)${Regex.escape(word)}($|\s)""").containsMatchIn(this)

    private data class ParsedAmount(val amount: BigDecimal)
    private data class AmountCandidate(val raw: String, val parsed: ParsedAmount)
    private data class AmountWithType(val raw: String, val amount: BigDecimal, val type: TxType)

    private val DATE_HEADERS = setOf(
        "date",
        "transaction date",
        "posted date",
        "posting date",
        "booking date",
        "value date",
        "operation date",
        "تاريخ",
        "تاريخ العمليه",
        "تاريخ الحركه",
        "تاريخ القيد",
    )
    private val MERCHANT_HEADERS = setOf(
        "vendor",
        "merchant",
        "description",
        "details",
        "detail",
        "narrative",
        "memo",
        "payee",
        "beneficiary",
        "transaction",
        "transaction description",
        "البيان",
        "الوصف",
        "تفاصيل",
        "تفاصيل العمليه",
        "المستفيد",
        "المرجع",
    )
    private val ATHAR_MERCHANT_HEADERS = setOf("vendor", "merchant")
    private val AMOUNT_HEADERS = setOf(
        "amount",
        "transaction amount",
        "net amount",
        "signed amount",
        "value",
        "المبلغ",
        "مبلغ العمليه",
    )
    private val SIGNED_AMOUNT_HEADERS = setOf(
        "transaction amount",
        "net amount",
        "signed amount",
        "value",
    )
    private val DEBIT_HEADERS = setOf(
        "debit",
        "debits",
        "withdrawal",
        "withdrawals",
        "paid out",
        "payment",
        "payments",
        "charge",
        "charges",
        "مدين",
        "خصم",
        "سحب",
        "مصروف",
    )
    private val CREDIT_HEADERS = setOf(
        "credit",
        "credits",
        "deposit",
        "deposits",
        "paid in",
        "income",
        "دائن",
        "ايداع",
        "وارد",
        "دخل",
    )
    private val CURRENCY_HEADERS = setOf("currency", "ccy", "curr", "currency code", "عملة", "العمله")
    private val CATEGORY_HEADERS = setOf("category", "category name", "الفئه", "التصنيف")
    private val TYPE_HEADERS = setOf(
        "type",
        "transaction type",
        "debit credit",
        "debit credit indicator",
        "credit debit indicator",
        "debit or credit",
        "debit credit marker",
        "d c",
        "dc",
        "dr cr",
        "drcr",
        "indicator",
        "transaction indicator",
        "direction",
        "entry type",
        "movement type",
        "نوع",
        "نوع العمليه",
        "مدين دائن",
        "اشاره",
    )
    private val NOTES_HEADERS = setOf("notes", "note", "remarks", "reference", "ملاحظات", "ملاحظه")
    private val BALANCE_HEADERS = setOf("balance", "running balance", "available balance", "الرصيد")

    private val INCOME_VALUES = setOf(
        "c",
        "crdt",
        "income",
        "credit",
        "cr",
        "deposit",
        "paid in",
        "refund",
        "salary",
        "inflow",
        "دائن",
        "ايداع",
        "وارد",
        "دخل",
        "راتب",
    )
    private val EXPENSE_VALUES = setOf(
        "d",
        "dbit",
        "expense",
        "debit",
        "dr",
        "withdrawal",
        "paid out",
        "purchase",
        "payment",
        "charge",
        "outflow",
        "مدين",
        "خصم",
        "سحب",
        "شراء",
        "دفع",
        "مصروف",
    )
    private val TRANSFER_VALUES = setOf("transfer", "internal transfer", "تحويل", "تحويل داخلي")
    private val KNOWN_CURRENCY_CODES = setOf(
        "SAR",
        "USD",
        "EUR",
        "GBP",
        "AED",
        "EGP",
        "INR",
        "PKR",
        "TRY",
        "KWD",
        "QAR",
        "BHD",
        "OMR",
        "JOD",
        "CAD",
        "AUD",
        "CHF",
        "JPY",
        "CNY",
        "HKD",
        "SGD",
        "SEK",
        "NOK",
        "DKK",
        "ZAR",
        "BRL",
        "MXN",
        "THB",
        "IDR",
        "MYR",
        "PHP",
        "VND",
        "KRW",
    )
}
