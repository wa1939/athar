package com.athar.core.domain.repo

import java.io.OutputStream

/**
 * Writes all confirmed transactions as a CSV file the user can re-open in Excel/Numbers.
 *
 * Header row: `date,vendor,amount,currency,category,type,notes`. UTF-8, RFC-4180 quoting
 * for fields that contain commas, quotes, or newlines. Master Brief §16 #10 acceptance
 * criterion: "It can export the data to CSV/backup so the user is never locked in."
 */
interface CsvExportTrigger {
    suspend fun exportAll(output: OutputStream): CsvExportResult
}

sealed interface CsvExportResult {
    data class Done(val exported: Int) : CsvExportResult
    data class Failed(val reason: String) : CsvExportResult
}
