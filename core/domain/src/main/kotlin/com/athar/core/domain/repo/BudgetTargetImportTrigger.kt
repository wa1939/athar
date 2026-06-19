package com.athar.core.domain.repo

import com.athar.core.common.money.Money
import com.athar.core.domain.model.CategoryKind
import java.io.InputStream

/**
 * Imports monthly category targets from the TMOAP Budget Targets worksheet.
 *
 * This deliberately stays separate from statement import: selecting a workbook
 * for transactions should never silently mutate category budgets.
 */
interface BudgetTargetImportTrigger {
    suspend fun preview(input: InputStream): BudgetTargetImportPreviewResult
    suspend fun import(input: InputStream): BudgetTargetImportResult
}

sealed interface BudgetTargetImportPreviewResult {
    data class Done(val preview: BudgetTargetImportPreview) : BudgetTargetImportPreviewResult
    data class Failed(val reason: String) : BudgetTargetImportPreviewResult
}

data class BudgetTargetImportPreview(
    val targetRows: Int,
    val changed: Int,
    val skipped: Int,
    val expenseTargets: Int,
    val incomeTargets: Int,
    val monthlyExpenseTotal: Money,
    val monthlyIncomeTotal: Money,
    val sampleRows: List<BudgetTargetImportPreviewRow>,
    val skippedRows: List<BudgetTargetImportSkippedRow>,
)

data class BudgetTargetImportPreviewRow(
    val rowNumber: Int,
    val categoryId: String,
    val categoryName: String,
    val kind: CategoryKind,
    val target: Money,
    val previousTarget: Money?,
    val changed: Boolean,
)

data class BudgetTargetImportSkippedRow(
    val rowNumber: Int,
    val label: String,
    val reason: String,
)

sealed interface BudgetTargetImportResult {
    data class Done(val applied: Int, val changed: Int, val skipped: Int) : BudgetTargetImportResult
    data class Failed(val reason: String) : BudgetTargetImportResult
}
