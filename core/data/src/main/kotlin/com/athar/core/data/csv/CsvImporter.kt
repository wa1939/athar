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

    override suspend fun import(input: InputStream, accountId: String): CsvImportResult {
        val plan = when (val result = buildPlan(input, accountId)) {
            is CsvPlanResult.Done -> result.plan
            is CsvPlanResult.Failed -> return CsvImportResult.Failed(result.reason)
        }

        plan.transactions.forEach { transactions.upsert(it.transaction) }
        Timber.i("Statement import: imported=%d skipped=%d", plan.transactions.size, plan.skippedRows.size)
        return CsvImportResult.Done(imported = plan.transactions.size, skipped = plan.skippedRows.size)
    }

    private suspend fun buildPlan(
        input: InputStream,
        accountId: String = MANUAL_ACCOUNT_ID,
    ): CsvPlanResult {
        val text = runCatching { input.bufferedReader().use { it.readText() } }
            .getOrElse { return CsvPlanResult.Failed("Couldn't read import file: ${it.message}") }
        val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.isEmpty()) return CsvPlanResult.Failed("Empty import file.")

        if (StatementOfxMapper.looksLikeOfx(text)) {
            return buildOfxPlan(text, accountId)
        }

        if (StatementMt940Mapper.looksLikeMt940(text)) {
            return buildMt940Plan(text, accountId)
        }

        val format = detectCsvFormat(lines[0])
        if (format == null) {
            return CsvPlanResult.Failed(
                "Delimited statement file must include date, merchant/description, and either amount or debit/credit columns.",
            )
        }
        val header = format.header
        val columns = format.columns

        val cats = categories.observeAll(kind = null, includeArchived = false).first()
        val categoryByName = cats.associateBy { it.name.lowercase().trim() }
        val categoryByAr = cats.associateBy { it.nameAr.trim() }
        val now = clock.now()

        val transactions = mutableListOf<CsvPlanTransaction>()
        val skippedRows = mutableListOf<CsvImportSkippedRow>()
        for ((rowIndex, line) in lines.drop(1).withIndex()) {
            val rowNumber = rowIndex + 2 // +1 for header, +1 to make 1-based
            val row = parseRow(line, format.delimiter)
            val mapped = mapRow(
                rowIndex = rowNumber,
                row = row,
                columns = columns,
                categoryByName = categoryByName,
                categoryByAr = categoryByAr,
                now = now,
                accountId = accountId,
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
                columns = detectedColumns(header, columns),
                transactions = transactions,
                skippedRows = skippedRows,
            ),
        )
    }

    private suspend fun buildOfxPlan(text: String, accountId: String): CsvPlanResult {
        val parsed = when (val result = StatementOfxMapper.parse(text)) {
            is StatementOfxParseResult.Done -> result
            is StatementOfxParseResult.Failed -> return CsvPlanResult.Failed(result.reason)
        }

        val now = clock.now()
        val transactions = parsed.rows.map { row ->
            CsvPlanTransaction(
                rowNumber = row.rowNumber,
                transaction = Transaction(
                    id = UUID.randomUUID().toString(),
                    accountId = accountId,
                    type = row.type,
                    amount = Money.of(row.amount, row.currency),
                    date = row.date,
                    occurredAt = null,
                    merchant = row.merchant,
                    merchantNormalized = row.merchant.lowercase().trim(),
                    categoryId = null,
                    notes = row.notes,
                    source = IngestSource.IMPORT,
                    sourceRefId = row.sourceRefId ?: "ofx-row-${row.rowNumber}",
                    status = TxStatus.CONFIRMED,
                    confidence = 1.0f,
                    createdAt = now,
                    updatedAt = now,
                ),
                categoryPreview = null,
            )
        }
        val skippedRows = parsed.skippedRowNumbers.map { skippedRowNumber ->
            CsvImportSkippedRow(
                rowNumber = skippedRowNumber,
                reason = "Unparseable OFX/QFX transaction",
            )
        }

        return CsvPlanResult.Done(
            CsvImportPlan(
                columns = CsvImportDetectedColumns(
                    date = "OFX DTPOSTED",
                    merchant = "OFX NAME/MEMO",
                    amount = "OFX TRNAMT",
                    debit = null,
                    credit = null,
                    currency = "OFX CURDEF",
                    category = null,
                    type = "OFX TRNTYPE",
                    notes = "OFX MEMO/FITID",
                ),
                transactions = transactions,
                skippedRows = skippedRows,
            ),
        )
    }

    private suspend fun buildMt940Plan(text: String, accountId: String): CsvPlanResult {
        val parsed = when (val result = StatementMt940Mapper.parse(text)) {
            is StatementMt940ParseResult.Done -> result
            is StatementMt940ParseResult.Failed -> return CsvPlanResult.Failed(result.reason)
        }

        val now = clock.now()
        val transactions = parsed.rows.map { row ->
            CsvPlanTransaction(
                rowNumber = row.rowNumber,
                transaction = Transaction(
                    id = UUID.randomUUID().toString(),
                    accountId = accountId,
                    type = row.type,
                    amount = Money.of(row.amount, row.currency),
                    date = row.date,
                    occurredAt = null,
                    merchant = row.merchant,
                    merchantNormalized = row.merchant.lowercase().trim(),
                    categoryId = null,
                    notes = row.notes,
                    source = IngestSource.IMPORT,
                    sourceRefId = row.sourceRefId ?: "mt940-row-${row.rowNumber}",
                    status = TxStatus.CONFIRMED,
                    confidence = 1.0f,
                    createdAt = now,
                    updatedAt = now,
                ),
                categoryPreview = null,
            )
        }
        val skippedRows = parsed.skippedRowNumbers.map { skippedRowNumber ->
            CsvImportSkippedRow(
                rowNumber = skippedRowNumber,
                reason = "Unparseable MT940 transaction",
            )
        }

        return CsvPlanResult.Done(
            CsvImportPlan(
                columns = CsvImportDetectedColumns(
                    date = "MT940 :61: date",
                    merchant = "MT940 :86:",
                    amount = "MT940 :61: amount",
                    debit = null,
                    credit = null,
                    currency = "MT940 :60F:/:62F:",
                    category = null,
                    type = "MT940 debit/credit mark",
                    notes = "MT940 :61:/:86:",
                ),
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
        accountId: String,
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
                accountId = accountId,
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
        columns = columns,
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

    private fun detectedColumns(header: List<String>, columns: StatementCsvColumns): CsvImportDetectedColumns =
        CsvImportDetectedColumns(
            date = headerName(header, columns.dateIdx),
            merchant = headerName(header, columns.merchantIdx),
            amount = columns.amountIdx?.let { headerName(header, it) },
            debit = columns.debitIdx?.let { headerName(header, it) },
            credit = columns.creditIdx?.let { headerName(header, it) },
            currency = columns.currencyIdx?.let { headerName(header, it) },
            category = columns.categoryIdx?.let { headerName(header, it) },
            type = columns.typeIdx?.let { headerName(header, it) },
            notes = columns.notesIdx?.let { headerName(header, it) },
        )

    private fun headerName(header: List<String>, index: Int): String =
        header.getOrNull(index)?.takeIf { it.isNotBlank() } ?: "Column ${index + 1}"

    private fun detectCsvFormat(headerLine: String): CsvFormat? =
        CsvDelimiters
            .mapNotNull { delimiter ->
                val header = parseRow(headerLine, delimiter)
                val columns = StatementCsvMapper.detect(header) ?: return@mapNotNull null
                CsvFormat(
                    delimiter = delimiter,
                    header = header,
                    columns = columns,
                )
            }
            .maxByOrNull { it.header.size }

    /** RFC 4180-ish delimited row parser. Handles quoted delimiters, `""` escapes, trailing empties. */
    private fun parseRow(line: String, delimiter: Char = ','): List<String> {
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
                c == delimiter && !inQuotes -> { out.add(cur.toString()); cur.clear() }
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
        val columns: CsvImportDetectedColumns,
        val transactions: List<CsvPlanTransaction>,
        val skippedRows: List<CsvImportSkippedRow>,
    )

    private data class CsvFormat(
        val delimiter: Char,
        val header: List<String>,
        val columns: StatementCsvColumns,
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
        val CsvDelimiters = listOf(',', ';', '\t')
    }
}
