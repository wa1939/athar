package com.athar.core.data.csv

import com.athar.core.common.money.Money
import com.athar.core.domain.model.Category
import com.athar.core.domain.model.IngestSource
import com.athar.core.domain.model.MANUAL_ACCOUNT_ID
import com.athar.core.domain.model.Transaction
import com.athar.core.domain.model.TxStatus
import com.athar.core.domain.model.TxType
import com.athar.core.domain.repo.CategoryRepository
import com.athar.core.domain.repo.CsvImportResult
import com.athar.core.domain.repo.CsvImportTrigger
import com.athar.core.domain.repo.TransactionRepository
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import timber.log.Timber
import java.io.InputStream
import java.math.BigDecimal
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class CsvImporter @Inject constructor(
    private val transactions: TransactionRepository,
    private val categories: CategoryRepository,
    private val clock: Clock,
) : CsvImportTrigger {

    override suspend fun import(input: InputStream): CsvImportResult {
        val text = runCatching { input.bufferedReader().use { it.readText() } }
            .getOrElse { return CsvImportResult.Failed("Couldn't read CSV file: ${it.message}") }
        val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.isEmpty()) return CsvImportResult.Failed("Empty CSV file.")

        val header = parseRow(lines[0]).map { it.lowercase().trim() }
        val dateIdx = header.indexOf("date")
        val merchantIdx = header.indexOfFirst { it == "vendor" || it == "merchant" }
        val amountIdx = header.indexOf("amount")
        val categoryIdx = header.indexOf("category")
        val typeIdx = header.indexOf("type")
        val notesIdx = header.indexOf("notes")

        if (dateIdx < 0 || merchantIdx < 0 || amountIdx < 0) {
            return CsvImportResult.Failed(
                "CSV must have these columns at minimum: date, vendor (or merchant), amount.",
            )
        }

        val cats = categories.observeAll(kind = null, includeArchived = false).first()
        val categoryByName = cats.associateBy { it.name.lowercase().trim() }
        val categoryByAr = cats.associateBy { it.nameAr.trim() }
        val now = clock.now()

        var imported = 0
        var skipped = 0
        for ((rowIndex, line) in lines.drop(1).withIndex()) {
            val row = parseRow(line)
            val tx = mapRow(
                rowIndex = rowIndex + 2, // +1 for header, +1 to make 1-based
                row = row,
                dateIdx = dateIdx,
                merchantIdx = merchantIdx,
                amountIdx = amountIdx,
                categoryIdx = categoryIdx,
                typeIdx = typeIdx,
                notesIdx = notesIdx,
                categoryByName = categoryByName,
                categoryByAr = categoryByAr,
                now = now,
            )
            if (tx == null) {
                skipped += 1
                continue
            }
            transactions.upsert(tx)
            imported += 1
        }
        Timber.i("CSV import: imported=%d skipped=%d", imported, skipped)
        return CsvImportResult.Done(imported = imported, skipped = skipped)
    }

    private fun mapRow(
        rowIndex: Int,
        row: List<String>,
        dateIdx: Int,
        merchantIdx: Int,
        amountIdx: Int,
        categoryIdx: Int,
        typeIdx: Int,
        notesIdx: Int,
        categoryByName: Map<String, Category>,
        categoryByAr: Map<String, Category>,
        now: kotlinx.datetime.Instant,
    ): Transaction? {
        if (row.size <= maxOf(dateIdx, merchantIdx, amountIdx)) {
            Timber.w("CSV row %d skipped (too few columns)", rowIndex)
            return null
        }
        val date = parseDate(row[dateIdx]) ?: run {
            Timber.w("CSV row %d skipped (unparseable date %s)", rowIndex, row[dateIdx])
            return null
        }
        val merchant = row[merchantIdx].trim()
        if (merchant.isBlank()) {
            Timber.w("CSV row %d skipped (blank merchant)", rowIndex)
            return null
        }
        val amount = parseAmount(row[amountIdx]) ?: run {
            Timber.w("CSV row %d skipped (unparseable amount %s)", rowIndex, row[amountIdx])
            return null
        }
        val type = if (typeIdx >= 0 && typeIdx < row.size) {
            when (row[typeIdx].uppercase().trim()) {
                "INCOME" -> TxType.INCOME
                "TRANSFER" -> TxType.TRANSFER
                else -> TxType.EXPENSE
            }
        } else TxType.EXPENSE

        val categoryId = if (categoryIdx >= 0 && categoryIdx < row.size) {
            val key = row[categoryIdx].trim()
            categoryByAr[key]?.id ?: categoryByName[key.lowercase()]?.id
        } else null

        val notes = if (notesIdx >= 0 && notesIdx < row.size) {
            row[notesIdx].trim().takeIf { it.isNotBlank() }
        } else null

        return Transaction(
            id = UUID.randomUUID().toString(),
            accountId = MANUAL_ACCOUNT_ID,
            type = type,
            amount = Money.of(amount),
            date = date,
            occurredAt = null,
            merchant = merchant,
            merchantNormalized = merchant.lowercase().trim(),
            categoryId = categoryId,
            notes = notes,
            source = IngestSource.IMPORT,
            sourceRefId = "csv-row-$rowIndex",
            status = TxStatus.CONFIRMED,
            confidence = 1.0f,
            createdAt = now,
            updatedAt = now,
        )
    }

    private fun parseAmount(raw: String): BigDecimal? {
        val cleaned = raw.replace(",", "").replace("ر.س", "").replace("SAR", "", ignoreCase = true).trim()
        return runCatching { BigDecimal(cleaned).abs() }.getOrNull()
    }

    private fun parseDate(raw: String): LocalDate? {
        val s = raw.trim()
        // Try ISO first
        runCatching { return LocalDate.parse(s) }
        // DD/MM/YYYY or MM/DD/YYYY (assume DD first for non-US users; could be made config later)
        val slashed = s.split("/", "-")
        if (slashed.size == 3) {
            val a = slashed[0].toIntOrNull() ?: return null
            val b = slashed[1].toIntOrNull() ?: return null
            val c = slashed[2].toIntOrNull() ?: return null
            return runCatching {
                when {
                    c > 31 && a in 1..31 && b in 1..12 -> LocalDate(c, b, a) // DD/MM/YYYY
                    c > 31 && a in 1..12 -> LocalDate(c, a, b)               // MM/DD/YYYY
                    a > 31 -> LocalDate(a, b, c)                              // YYYY/MM/DD
                    else -> null
                }
            }.getOrNull()
        }
        return null
    }

    /** RFC 4180-ish CSV row parser. Handles `"a,b"`, `""` escapes, trailing empties. */
    private fun parseRow(line: String): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                inQuotes && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> {
                    cur.append('"'); i++
                }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> { out.add(cur.toString()); cur.clear() }
                else -> cur.append(c)
            }
            i += 1
        }
        out.add(cur.toString())
        return out
    }
}
