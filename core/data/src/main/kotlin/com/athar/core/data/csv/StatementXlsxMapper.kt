package com.athar.core.data.csv

import com.athar.core.domain.model.TxType
import com.athar.core.domain.repo.CsvImportColumnMapping
import com.athar.core.domain.repo.CsvImportDetectedColumns
import kotlinx.datetime.LocalDate
import java.math.BigDecimal
import java.util.Locale

internal data class StatementXlsxMappedRow(
    val rowNumber: Int,
    val date: LocalDate,
    val merchant: String,
    val amount: BigDecimal,
    val currency: String,
    val type: TxType,
    val category: String?,
    val notes: String?,
)

internal sealed interface StatementXlsxParseResult {
    data class Done(
        val columns: CsvImportDetectedColumns,
        val availableColumns: List<String>,
        val rows: List<StatementXlsxMappedRow>,
        val skippedRowNumbers: List<Int>,
    ) : StatementXlsxParseResult

    data class MappingRequired(val columns: List<String>, val reason: String) : StatementXlsxParseResult
    data class Failed(val reason: String) : StatementXlsxParseResult
}

internal object StatementXlsxMapper {

    fun looksLikeXlsx(bytes: ByteArray): Boolean =
        OpenXmlWorkbookReader.looksLikeXlsx(bytes)

    fun parse(
        bytes: ByteArray,
        mapping: CsvImportColumnMapping? = null,
    ): StatementXlsxParseResult {
        val workbook = runCatching { OpenXmlWorkbookReader.read(bytes) }
            .getOrElse { return StatementXlsxParseResult.Failed("Couldn't read XLSX workbook: ${it.message}") }

        var nextRowNumber = 1
        var firstDetectedColumns: CsvImportDetectedColumns? = null
        var firstAvailableColumns: List<String> = emptyList()
        var bestMappingHeader: List<String> = emptyList()
        val rows = mutableListOf<StatementXlsxMappedRow>()
        val skipped = mutableListOf<Int>()

        workbook.sheets.forEach { sheet ->
            val grid = workbook.rows(sheet) ?: return@forEach
            val candidateHeader = grid
                .map { it.trimTrailingBlankCells() }
                .filter { it.count(String::isNotBlank) >= 2 }
                .maxByOrNull { it.count(String::isNotBlank) }
            if (bestMappingHeader.isEmpty() && candidateHeader != null) {
                bestMappingHeader = candidateHeader
            }

            val detected = detectHeader(grid, mapping) ?: return@forEach
            val header = detected.header
            val columns = detected.columns
            if (firstDetectedColumns == null) {
                firstDetectedColumns = detectedColumns(header, columns, prefix = "XLSX")
                firstAvailableColumns = header
            }

            val fallbackType = sheet.positiveAmountFallbackType()
            grid.drop(detected.rowIndex + 1).forEach { rawRow ->
                val row = rawRow.trimTrailingBlankCells()
                if (row.all(String::isBlank)) return@forEach

                val rowNumber = nextRowNumber++
                val mapped = StatementCsvMapper.map(row, columns, positiveAmountFallbackType = fallbackType)
                if (mapped == null) {
                    skipped += rowNumber
                    return@forEach
                }
                rows += StatementXlsxMappedRow(
                    rowNumber = rowNumber,
                    date = mapped.date,
                    merchant = mapped.merchant,
                    amount = mapped.amount,
                    currency = mapped.currency,
                    type = mapped.type,
                    category = mapped.category,
                    notes = mapped.notes,
                )
            }
        }

        if (firstDetectedColumns == null) {
            return StatementXlsxParseResult.MappingRequired(
                columns = bestMappingHeader,
                reason = "Map date, merchant, and amount/debit/credit columns before previewing this workbook.",
            )
        }
        if (rows.isEmpty() && skipped.isEmpty()) {
            return StatementXlsxParseResult.Failed("XLSX workbook did not include statement transactions.")
        }

        return StatementXlsxParseResult.Done(
            columns = firstDetectedColumns,
            availableColumns = firstAvailableColumns,
            rows = rows,
            skippedRowNumbers = skipped,
        )
    }

    private fun detectHeader(
        grid: List<List<String>>,
        mapping: CsvImportColumnMapping?,
    ): DetectedHeader? =
        grid.asSequence()
            .mapIndexed { index, row ->
                val header = row.trimTrailingBlankCells()
                val columns = StatementCsvMapper.detect(header, mapping)
                if (columns == null) null else DetectedHeader(index, header, columns)
            }
            .filterNotNull()
            .firstOrNull()

    private fun OpenXmlSheet.positiveAmountFallbackType(): TxType? {
        val normalized = name.lowercase(Locale.US)
        return when {
            "income" in normalized || "دخل" in normalized -> TxType.INCOME
            "expenses" in normalized || "expense" in normalized || "مصروف" in normalized -> TxType.EXPENSE
            else -> null
        }
    }

    private fun detectedColumns(
        header: List<String>,
        columns: StatementCsvColumns,
        prefix: String,
    ): CsvImportDetectedColumns =
        CsvImportDetectedColumns(
            date = "$prefix ${headerName(header, columns.dateIdx)}",
            merchant = "$prefix ${headerName(header, columns.merchantIdx)}",
            amount = columns.amountIdx?.let { "$prefix ${headerName(header, it)}" },
            debit = columns.debitIdx?.let { "$prefix ${headerName(header, it)}" },
            credit = columns.creditIdx?.let { "$prefix ${headerName(header, it)}" },
            currency = columns.currencyIdx?.let { "$prefix ${headerName(header, it)}" },
            category = columns.categoryIdx?.let { "$prefix ${headerName(header, it)}" },
            type = columns.typeIdx?.let { "$prefix ${headerName(header, it)}" },
            notes = columns.notesIdx?.let { "$prefix ${headerName(header, it)}" },
        )

    private fun headerName(header: List<String>, index: Int): String =
        header.getOrNull(index)?.takeIf { it.isNotBlank() } ?: "Column ${index + 1}"

    private fun List<String>.trimTrailingBlankCells(): List<String> {
        val lastNonBlank = indexOfLast { it.isNotBlank() }
        return if (lastNonBlank < 0) emptyList() else take(lastNonBlank + 1)
    }

    private data class DetectedHeader(
        val rowIndex: Int,
        val header: List<String>,
        val columns: StatementCsvColumns,
    )
}
