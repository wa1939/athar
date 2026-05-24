package com.athar.core.domain.repo

import java.io.InputStream

/**
 * Parses a CSV file of transactions and bulk-inserts them as confirmed manual entries.
 *
 * Expected CSV columns (header row, case-insensitive, any order):
 *   - **date** — `YYYY-MM-DD`, `DD/MM/YYYY`, or `MM/DD/YYYY`
 *   - **vendor** or **merchant** — text
 *   - **amount** — positive decimal; sign is derived from the **type** column when present
 *   - **category** — matches `name` or `nameAr` (case-insensitive); if missing, transaction lands uncategorized
 *   - **type** — `EXPENSE` (default) or `INCOME`
 *   - **notes** — optional
 *
 * The importer is conservative: a row with a parse error is skipped and counted in
 * [CsvImportResult.skipped] rather than aborting the whole batch.
 *
 * Master Brief / Backlog M-14 (CSV portion).
 */
interface CsvImportTrigger {
    suspend fun import(input: InputStream): CsvImportResult
}

sealed interface CsvImportResult {
    data class Done(val imported: Int, val skipped: Int) : CsvImportResult
    data class Failed(val reason: String) : CsvImportResult
}
