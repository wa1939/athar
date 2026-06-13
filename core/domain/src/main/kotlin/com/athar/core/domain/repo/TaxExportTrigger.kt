package com.athar.core.domain.repo

import java.io.OutputStream

/**
 * Writes an annual accountant/tax PDF report for confirmed operating transactions.
 *
 * Reconciliation adjustments are excluded from category totals because they are balance
 * corrections, not real income or spending.
 */
interface TaxExportTrigger {
    suspend fun exportAnnual(output: OutputStream, year: Int, localeTag: String): TaxExportResult
}

sealed interface TaxExportResult {
    data class Done(
        val year: Int,
        val transactions: Int,
        val categoryTotals: Int,
        val excludedReconciliations: Int,
    ) : TaxExportResult

    data class Failed(val reason: String) : TaxExportResult
}
