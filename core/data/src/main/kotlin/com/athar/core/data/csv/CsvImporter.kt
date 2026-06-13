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
import timber.log.Timber
import java.io.InputStream
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

        val columns = StatementCsvMapper.detect(parseRow(lines[0]))
        if (columns == null) {
            return CsvImportResult.Failed(
                "CSV must include date, merchant/description, and either amount or debit/credit columns.",
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
                columns = columns,
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
        columns: StatementCsvColumns,
        categoryByName: Map<String, Category>,
        categoryByAr: Map<String, Category>,
        now: kotlinx.datetime.Instant,
    ): Transaction? {
        val mapped = StatementCsvMapper.map(row, columns) ?: run {
            Timber.w("CSV row %d skipped (unparseable statement row)", rowIndex)
            return null
        }

        val categoryId = mapped.category?.let { key ->
            categoryByAr[key]?.id ?: categoryByName[key.lowercase()]?.id
        }

        return Transaction(
            id = UUID.randomUUID().toString(),
            accountId = MANUAL_ACCOUNT_ID,
            type = mapped.type,
            amount = Money.of(mapped.amount, mapped.currency),
            date = mapped.date,
            occurredAt = null,
            merchant = mapped.merchant,
            merchantNormalized = mapped.merchant.lowercase().trim(),
            categoryId = categoryId,
            notes = mapped.notes,
            source = IngestSource.IMPORT,
            sourceRefId = "csv-row-$rowIndex",
            status = TxStatus.CONFIRMED,
            confidence = 1.0f,
            createdAt = now,
            updatedAt = now,
        )
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
