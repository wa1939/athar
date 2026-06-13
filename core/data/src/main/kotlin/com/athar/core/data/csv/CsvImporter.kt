package com.athar.core.data.csv

import com.athar.core.common.money.Money
import com.athar.core.domain.model.Category
import com.athar.core.domain.model.IngestSource
import com.athar.core.domain.model.MANUAL_ACCOUNT_ID
import com.athar.core.domain.model.Transaction
import com.athar.core.domain.model.TxStatus
import com.athar.core.domain.model.TxType
import com.athar.core.domain.repo.CategoryRepository
import com.athar.core.domain.repo.CsvImportDetectedColumns
import com.athar.core.domain.repo.CsvImportPreview
import com.athar.core.domain.repo.CsvImportPreviewResult
import com.athar.core.domain.repo.CsvImportPreviewRow
import com.athar.core.domain.repo.CsvImportResult
import com.athar.core.domain.repo.CsvImportSkippedRow
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

    override suspend fun preview(input: InputStream): CsvImportPreviewResult {
        return when (val plan = buildPlan(input)) {
            is CsvPlanResult.Done -> CsvImportPreviewResult.Done(plan.plan.toPreview())
            is CsvPlanResult.Failed -> CsvImportPreviewResult.Failed(plan.reason)
        }
    }

    override suspend fun import(input: InputStream): CsvImportResult {
        val plan = when (val result = buildPlan(input)) {
            is CsvPlanResult.Done -> result.plan
            is CsvPlanResult.Failed -> return CsvImportResult.Failed(result.reason)
        }

        plan.transactions.forEach { transactions.upsert(it.transaction) }
        Timber.i("CSV import: imported=%d skipped=%d", plan.transactions.size, plan.skippedRows.size)
        return CsvImportResult.Done(imported = plan.transactions.size, skipped = plan.skippedRows.size)
    }

    private suspend fun buildPlan(input: InputStream): CsvPlanResult {
        val text = runCatching { input.bufferedReader().use { it.readText() } }
            .getOrElse { return CsvPlanResult.Failed("Couldn't read CSV file: ${it.message}") }
        val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.isEmpty()) return CsvPlanResult.Failed("Empty CSV file.")

        val header = parseRow(lines[0])
        val columns = StatementCsvMapper.detect(header)
        if (columns == null) {
            return CsvPlanResult.Failed(
                "CSV must include date, merchant/description, and either amount or debit/credit columns.",
            )
        }

        val cats = categories.observeAll(kind = null, includeArchived = false).first()
        val categoryByName = cats.associateBy { it.name.lowercase().trim() }
        val categoryByAr = cats.associateBy { it.nameAr.trim() }
        val now = clock.now()

        val transactions = mutableListOf<CsvPlanTransaction>()
        val skippedRows = mutableListOf<CsvImportSkippedRow>()
        for ((rowIndex, line) in lines.drop(1).withIndex()) {
            val rowNumber = rowIndex + 2 // +1 for header, +1 to make 1-based
            val row = parseRow(line)
            val mapped = mapRow(
                rowIndex = rowNumber,
                row = row,
                columns = columns,
                categoryByName = categoryByName,
                categoryByAr = categoryByAr,
                now = now,
            )
            if (mapped == null) {
                skippedRows += CsvImportSkippedRow(
                    rowNumber = rowNumber,
                    reason = "Unparseable row",
                )
                continue
            }
            transactions += CsvPlanTransaction(
                rowNumber = rowNumber,
                transaction = mapped.transaction,
                categoryPreview = mapped.categoryPreview,
            )
        }

        return CsvPlanResult.Done(
            CsvImportPlan(
                header = header,
                columns = columns,
                transactions = transactions,
                skippedRows = skippedRows,
            ),
        )
    }

    private fun mapRow(
        rowIndex: Int,
        row: List<String>,
        columns: StatementCsvColumns,
        categoryByName: Map<String, Category>,
        categoryByAr: Map<String, Category>,
        now: kotlinx.datetime.Instant,
    ): CsvMappedTransaction? {
        val mapped = StatementCsvMapper.map(row, columns) ?: run {
            Timber.w("CSV row %d skipped (unparseable statement row)", rowIndex)
            return null
        }

        val categoryId = mapped.category?.let { key ->
            categoryByAr[key]?.id ?: categoryByName[key.lowercase()]?.id
        }

        return CsvMappedTransaction(
            transaction = Transaction(
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
            ),
            categoryPreview = mapped.category,
        )
    }

    private fun CsvImportPlan.toPreview(): CsvImportPreview = CsvImportPreview(
        importable = transactions.size,
        skipped = skippedRows.size,
        columns = CsvImportDetectedColumns(
            date = headerName(header, columns.dateIdx),
            merchant = headerName(header, columns.merchantIdx),
            amount = columns.amountIdx?.let { headerName(header, it) },
            debit = columns.debitIdx?.let { headerName(header, it) },
            credit = columns.creditIdx?.let { headerName(header, it) },
            currency = columns.currencyIdx?.let { headerName(header, it) },
            category = columns.categoryIdx?.let { headerName(header, it) },
            type = columns.typeIdx?.let { headerName(header, it) },
            notes = columns.notesIdx?.let { headerName(header, it) },
        ),
        sampleRows = transactions.take(PREVIEW_ROW_LIMIT).map { row ->
            CsvImportPreviewRow(
                rowNumber = row.rowNumber,
                date = row.transaction.date.toString(),
                merchant = row.transaction.merchant,
                amount = row.transaction.amount.amount.toPlainString(),
                currency = row.transaction.amount.currency,
                type = row.transaction.type,
                category = row.categoryPreview,
            )
        },
        skippedRows = skippedRows.take(PREVIEW_ROW_LIMIT),
    )

    private fun headerName(header: List<String>, index: Int): String =
        header.getOrNull(index)?.takeIf { it.isNotBlank() } ?: "Column ${index + 1}"

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

    private sealed interface CsvPlanResult {
        data class Done(val plan: CsvImportPlan) : CsvPlanResult
        data class Failed(val reason: String) : CsvPlanResult
    }

    private data class CsvImportPlan(
        val header: List<String>,
        val columns: StatementCsvColumns,
        val transactions: List<CsvPlanTransaction>,
        val skippedRows: List<CsvImportSkippedRow>,
    )

    private data class CsvPlanTransaction(
        val rowNumber: Int,
        val transaction: Transaction,
        val categoryPreview: String?,
    )

    private data class CsvMappedTransaction(
        val transaction: Transaction,
        val categoryPreview: String?,
    )

    private companion object {
        const val PREVIEW_ROW_LIMIT = 5
    }
}
