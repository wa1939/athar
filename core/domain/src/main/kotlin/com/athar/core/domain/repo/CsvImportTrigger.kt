package com.athar.core.domain.repo

import java.io.InputStream

/**
 * Parses a CSV file of transactions and bulk-inserts them as confirmed manual entries.
 *
 * Supported CSV columns (header row, case-insensitive, any order):
 *   - **date** — `YYYY-MM-DD`, `DD/MM/YYYY`, or `MM/DD/YYYY`
 *   - **vendor**, **merchant**, **description**, **details**, **narrative**, or Arabic equivalents — text
 *   - **amount** — positive decimal for Athar/TMOAP exports, or signed decimal for statement-like files
 *   - **debit** / **credit** — split amount columns; debit imports as EXPENSE, credit imports as INCOME
 *   - **currency** — optional ISO-4217 code; defaults to SAR when missing
 *   - **category** — matches `name` or `nameAr` (case-insensitive); if missing, transaction lands uncategorized
 *   - **type** — `EXPENSE` (default), `INCOME`, `TRANSFER`, or debit/credit synonyms
 *   - **notes** — optional
 *
 * The importer is conservative: a row with a parse error is skipped and counted in
 * [CsvImportResult.skipped] rather than aborting the whole batch.
 *
 * Master Brief / Backlog M-14 and roadmap G-12 (CSV portion).
 */
interface CsvImportTrigger {
    suspend fun import(input: InputStream): CsvImportResult
}

sealed interface CsvImportResult {
    data class Done(val imported: Int, val skipped: Int) : CsvImportResult
    data class Failed(val reason: String) : CsvImportResult
}
