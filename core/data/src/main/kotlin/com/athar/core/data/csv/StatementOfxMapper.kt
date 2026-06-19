package com.athar.core.data.csv

import com.athar.core.common.money.Money
import com.athar.core.domain.model.TxType
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

internal data class StatementOfxMappedRow(
    val rowNumber: Int,
    val date: LocalDate,
    val merchant: String,
    val amount: BigDecimal,
    val currency: String,
    val type: TxType,
    val notes: String?,
    val sourceRefId: String?,
)

internal sealed interface StatementOfxParseResult {
    data class Done(
        val currency: String,
        val rows: List<StatementOfxMappedRow>,
        val skippedRowNumbers: List<Int>,
    ) : StatementOfxParseResult

    data class Failed(val reason: String) : StatementOfxParseResult
}

internal object StatementOfxMapper {

    fun looksLikeOfx(text: String): Boolean {
        val trimmed = text.trimStart()
        return trimmed.startsWith("OFXHEADER:", ignoreCase = true) ||
            trimmed.contains("<OFX", ignoreCase = true) ||
            trimmed.contains("<STMTTRN", ignoreCase = true)
    }

    fun parse(text: String): StatementOfxParseResult {
        val statementCurrency = tag(text, "CURDEF")?.let(::normalizeCurrency) ?: Money.SAR
        val blocks = StatementTransactionPattern.findAll(text).toList()
        if (blocks.isEmpty()) {
            return StatementOfxParseResult.Failed("OFX/QFX file did not include statement transactions.")
        }

        val rows = mutableListOf<StatementOfxMappedRow>()
        val skipped = mutableListOf<Int>()
        for ((index, match) in blocks.withIndex()) {
            val rowNumber = index + 1
            val body = match.groupValues[1]
            val mapped = mapTransaction(rowNumber, body, statementCurrency)
            if (mapped == null) skipped += rowNumber else rows += mapped
        }

        return StatementOfxParseResult.Done(
            currency = statementCurrency,
            rows = rows,
            skippedRowNumbers = skipped,
        )
    }

    private fun mapTransaction(rowNumber: Int, body: String, statementCurrency: String): StatementOfxMappedRow? {
        val date = parseOfxDate(tag(body, "DTPOSTED") ?: tag(body, "DTUSER")) ?: return null
        val amount = parseAmount(tag(body, "TRNAMT")) ?: return null
        val merchant = cleanParty(tag(body, "NAME") ?: tag(body, "PAYEE") ?: tag(body, "MEMO")) ?: return null
        val memo = cleanParty(tag(body, "MEMO"))?.takeIf { !it.equals(merchant, ignoreCase = true) }
        val fitId = tag(body, "FITID")?.takeIf { it.isNotBlank() }
        val currency = transactionCurrency(body) ?: statementCurrency
        val type = transactionType(tag(body, "TRNTYPE"), amount)

        return StatementOfxMappedRow(
            rowNumber = rowNumber,
            date = date,
            merchant = merchant,
            amount = amount.abs(),
            currency = currency,
            type = type,
            notes = memo,
            sourceRefId = fitId?.let { "ofx-$it" },
        )
    }

    private fun transactionType(rawType: String?, amount: BigDecimal): TxType {
        val normalized = rawType.orEmpty().trim().uppercase()
        return when {
            normalized in TransferTypes -> TxType.TRANSFER
            normalized in IncomeTypes -> TxType.INCOME
            normalized in ExpenseTypes -> TxType.EXPENSE
            amount.signum() < 0 -> TxType.EXPENSE
            else -> TxType.INCOME
        }
    }

    private fun transactionCurrency(body: String): String? =
        tag(body, "CURSYM")?.let(::normalizeCurrency)
            ?: CurrencyBlockPattern.find(body)
                ?.groupValues
                ?.getOrNull(1)
                ?.let(::normalizeCurrency)

    private fun parseOfxDate(raw: String?): LocalDate? {
        val digits = raw.orEmpty().filter(Char::isDigit)
        if (digits.length < 8) return null
        return runCatching {
            LocalDate(
                year = digits.substring(0, 4).toInt(),
                monthNumber = digits.substring(4, 6).toInt(),
                dayOfMonth = digits.substring(6, 8).toInt(),
            )
        }.getOrNull()
    }

    private fun parseAmount(raw: String?): BigDecimal? {
        val cleaned = raw.orEmpty()
            .trim()
            .replace(",", "")
            .replace("+", "")
        if (cleaned.isBlank()) return null
        return runCatching { BigDecimal(cleaned) }.getOrNull()
    }

    private fun cleanParty(raw: String?): String? {
        val cleaned = raw
            ?.decodeEntities()
            ?.replace(Regex("""\s+"""), " ")
            ?.trim(' ', '.', ',', '-', '·', ':')
            ?.take(96)
            ?.trim()
        return cleaned?.takeIf { it.isNotBlank() }
    }

    private fun normalizeCurrency(raw: String): String =
        raw.trim().uppercase().takeIf { it in KnownCurrencies } ?: Money.SAR

    private fun tag(body: String, tag: String): String? =
        Regex("""<\s*${Regex.escape(tag)}\b[^>]*>\s*([^<\r\n]*)""", RegexOption.IGNORE_CASE)
            .find(body)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.decodeEntities()
            ?.takeIf { it.isNotBlank() }

    private fun String.decodeEntities(): String =
        replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")

    private val StatementTransactionPattern = Regex(
        """<\s*STMTTRN\b[^>]*>(.*?)(?:</\s*STMTTRN\s*>|(?=<\s*STMTTRN\b)|</\s*BANKTRANLIST\s*>|</\s*OFX\s*>|$)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val CurrencyBlockPattern = Regex(
        """<\s*CURRENCY\b[^>]*>.*?<\s*CURSYM\b[^>]*>\s*([^<\r\n]*)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    private val ExpenseTypes = setOf(
        "DEBIT",
        "POS",
        "ATM",
        "CHECK",
        "PAYMENT",
        "FEE",
        "SRVCHG",
        "REPEATPMT",
        "CASH",
    )
    private val IncomeTypes = setOf(
        "CREDIT",
        "DEP",
        "DIRECTDEP",
        "INT",
        "DIV",
    )
    private val TransferTypes = setOf("XFER", "TRANSFER")
    private val KnownCurrencies = setOf(
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
