package com.athar.core.data.csv

import com.athar.core.common.money.Money
import com.athar.core.domain.model.TxType
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

internal data class StatementMt940MappedRow(
    val rowNumber: Int,
    val date: LocalDate,
    val merchant: String,
    val amount: BigDecimal,
    val currency: String,
    val type: TxType,
    val notes: String?,
    val sourceRefId: String?,
)

internal sealed interface StatementMt940ParseResult {
    data class Done(
        val currency: String,
        val rows: List<StatementMt940MappedRow>,
        val skippedRowNumbers: List<Int>,
    ) : StatementMt940ParseResult

    data class Failed(val reason: String) : StatementMt940ParseResult
}

internal object StatementMt940Mapper {

    fun looksLikeMt940(text: String): Boolean {
        val normalized = normalizeLines(text)
        return TransactionRecordPattern.containsMatchIn(normalized) &&
            (OpeningBalancePattern.containsMatchIn(normalized) || StatementHeaderPattern.containsMatchIn(normalized))
    }

    fun parse(text: String): StatementMt940ParseResult {
        val records = records(text)
        val transactionRecords = records.withIndex().filter { it.value.tag == "61" }
        if (transactionRecords.isEmpty()) {
            return StatementMt940ParseResult.Failed("MT940 file did not include statement transactions.")
        }

        val statementCurrency = statementCurrency(records) ?: Money.SAR
        val rows = mutableListOf<StatementMt940MappedRow>()
        val skipped = mutableListOf<Int>()
        for ((index, indexedRecord) in transactionRecords.withIndex()) {
            val rowNumber = index + 1
            val recordIndex = indexedRecord.index
            val record = indexedRecord.value
            val details = records.getOrNull(recordIndex + 1)?.takeIf { it.tag == "86" }?.value
            val mapped = mapTransaction(rowNumber, record.value, details, statementCurrency)
            if (mapped == null) skipped += rowNumber else rows += mapped
        }

        return StatementMt940ParseResult.Done(
            currency = statementCurrency,
            rows = rows,
            skippedRowNumbers = skipped,
        )
    }

    private fun mapTransaction(
        rowNumber: Int,
        value: String,
        details: String?,
        statementCurrency: String,
    ): StatementMt940MappedRow? {
        val match = TransactionValuePattern.matchEntire(value.trim()) ?: return null
        val date = parseDate(match.groupValues[1]) ?: return null
        val debitCredit = match.groupValues[3].uppercase()
        val amount = parseAmount(match.groupValues[5]) ?: return null
        val tail = match.groupValues.getOrNull(7).orEmpty()
        val fallbackDetails = cleanParty(tail.substringBefore("//"))
        val merchant = cleanParty(details) ?: fallbackDetails ?: return null
        val notes = fallbackDetails?.takeIf { !it.equals(merchant, ignoreCase = true) }

        return StatementMt940MappedRow(
            rowNumber = rowNumber,
            date = date,
            merchant = merchant,
            amount = amount.abs(),
            currency = statementCurrency,
            type = typeFromDebitCredit(debitCredit),
            notes = notes,
            sourceRefId = referenceFrom(tail)?.let { "mt940-$it" },
        )
    }

    private fun typeFromDebitCredit(debitCredit: String): TxType =
        when (debitCredit) {
            "D", "RC" -> TxType.EXPENSE
            else -> TxType.INCOME
        }

    private fun statementCurrency(records: List<Mt940Record>): String? =
        records.asSequence()
            .filter { it.tag == "60F" || it.tag == "60M" || it.tag == "62F" || it.tag == "62M" }
            .mapNotNull { BalanceCurrencyPattern.find(it.value)?.groupValues?.getOrNull(3) }
            .map(::normalizeCurrency)
            .firstOrNull()

    private fun records(text: String): List<Mt940Record> {
        val out = mutableListOf<Mt940Record>()
        var currentTag: String? = null
        var currentValue = StringBuilder()
        fun flush() {
            val tag = currentTag ?: return
            out += Mt940Record(tag = tag, value = currentValue.toString().trim())
        }

        normalizeLines(text).lineSequence().forEach { line ->
            val match = RecordTagPattern.matchEntire(line.trimEnd())
            if (match != null) {
                flush()
                currentTag = match.groupValues[1]
                currentValue = StringBuilder(match.groupValues[2].trim())
            } else if (currentTag != null) {
                val continuation = line.trim()
                if (continuation.isNotBlank() && continuation != "-}") {
                    if (currentValue.isNotEmpty()) currentValue.append('\n')
                    currentValue.append(continuation)
                }
            }
        }
        flush()
        return out
    }

    private fun parseDate(raw: String): LocalDate? {
        if (raw.length != 6) return null
        return runCatching {
            val yy = raw.substring(0, 2).toInt()
            val year = if (yy >= 70) 1900 + yy else 2000 + yy
            LocalDate(
                year = year,
                monthNumber = raw.substring(2, 4).toInt(),
                dayOfMonth = raw.substring(4, 6).toInt(),
            )
        }.getOrNull()
    }

    private fun parseAmount(raw: String): BigDecimal? {
        val cleaned = raw.trim().replace(" ", "")
        val normalized = when {
            "," in cleaned && "." in cleaned && cleaned.lastIndexOf(',') > cleaned.lastIndexOf('.') ->
                cleaned.replace(".", "").replace(",", ".")
            "," in cleaned && "." in cleaned ->
                cleaned.replace(",", "")
            "," in cleaned ->
                cleaned.replace(".", "").replace(",", ".")
            else ->
                cleaned
        }
        return runCatching { BigDecimal(normalized) }.getOrNull()
    }

    private fun cleanParty(raw: String?): String? {
        val cleaned = raw
            ?.replace(StructuredFieldPattern, " ")
            ?.replace(Mt940TechnicalTokenPattern, " ")
            ?.replace(Regex("""[/|]+"""), " ")
            ?.replace(Regex("""\s+"""), " ")
            ?.trim(' ', '.', ',', '-', ':')
            ?.take(96)
            ?.trim()
        return cleaned?.takeIf { it.isNotBlank() && !it.equals("NONREF", ignoreCase = true) }
    }

    private fun referenceFrom(tail: String): String? {
        val ref = tail.substringAfter("//", missingDelimiterValue = "")
            .lineSequence()
            .firstOrNull()
            ?.substringBefore("/")
            ?.trim(' ', '/', ':')
        return ref
            ?.takeIf { it.isNotBlank() }
            ?.takeIf { !it.equals("NONREF", ignoreCase = true) }
            ?.take(64)
    }

    private fun normalizeCurrency(raw: String): String =
        raw.trim().uppercase().takeIf { it in KnownCurrencies } ?: Money.SAR

    private fun normalizeLines(text: String): String =
        text.replace("\r\n", "\n").replace('\r', '\n')

    private data class Mt940Record(
        val tag: String,
        val value: String,
    )

    private val RecordTagPattern = Regex("""^:([0-9]{2}[A-Z]?):(.*)$""")
    private val TransactionRecordPattern = Regex("""(?m)^:61:\d{6}""")
    private val StatementHeaderPattern = Regex("""(?m)^:20:""")
    private val OpeningBalancePattern = Regex("""(?m)^:6[02][FM]:""")
    private val TransactionValuePattern = Regex(
        """^(\d{6})(\d{4})?([R]?[DC])([A-Z])?([0-9][0-9.,]*)([A-Z]{1,4})?(.*)$""",
        RegexOption.DOT_MATCHES_ALL,
    )
    private val BalanceCurrencyPattern = Regex("""^([CD])(\d{6})([A-Z]{3})([0-9][0-9.,]*)""")
    private val StructuredFieldPattern = Regex("""\?\d{2}|[A-Z]{3,5}\+""")
    private val Mt940TechnicalTokenPattern = Regex(
        """\b(NONREF|NOTPROVIDED|EREF|MREF|CRED|IBAN|BIC|SVWZ|SEPA|NMSC|NTRF|NCHG)\b""",
        RegexOption.IGNORE_CASE,
    )
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
