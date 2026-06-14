package com.athar.core.domain.repo

import com.athar.core.common.money.Money
import java.io.InputStream

/**
 * Imports the TMOAP Family Investments worksheet into the Plan investments model.
 *
 * This is separate from statement import so choosing a transaction workbook never
 * silently changes investment planning data.
 */
interface InvestmentImportTrigger {
    suspend fun preview(input: InputStream): InvestmentImportPreviewResult
    suspend fun import(input: InputStream): InvestmentImportResult
}

sealed interface InvestmentImportPreviewResult {
    data class Done(val preview: InvestmentImportPreview) : InvestmentImportPreviewResult
    data class Failed(val reason: String) : InvestmentImportPreviewResult
}

data class InvestmentImportPreview(
    val poolName: String,
    val period: String,
    val existingPool: Boolean,
    val contributionRows: Int,
    val replacedContributions: Int,
    val skipped: Int,
    val totalCorpus: Money,
    val totalReturn: Money,
    val sampleRows: List<InvestmentImportPreviewRow>,
    val skippedRows: List<InvestmentImportSkippedRow>,
)

data class InvestmentImportPreviewRow(
    val rowNumber: Int,
    val ownerName: String,
    val amount: Money,
)

data class InvestmentImportSkippedRow(
    val rowNumber: Int,
    val label: String,
    val reason: String,
)

sealed interface InvestmentImportResult {
    data class Done(
        val importedContributions: Int,
        val replacedContributions: Int,
        val skipped: Int,
        val existingPool: Boolean,
    ) : InvestmentImportResult

    data class Failed(val reason: String) : InvestmentImportResult
}
