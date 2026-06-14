package com.athar.core.data.csv

import com.athar.core.common.money.Money
import com.athar.core.domain.model.Category
import com.athar.core.domain.model.CategoryKind
import com.athar.core.domain.repo.BudgetTargetImportPreview
import com.athar.core.domain.repo.BudgetTargetImportPreviewResult
import com.athar.core.domain.repo.BudgetTargetImportPreviewRow
import com.athar.core.domain.repo.BudgetTargetImportResult
import com.athar.core.domain.repo.BudgetTargetImportSkippedRow
import com.athar.core.domain.repo.BudgetTargetImportTrigger
import com.athar.core.domain.repo.CategoryRepository
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.io.InputStream
import java.math.BigDecimal
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class BudgetTargetImporter @Inject constructor(
    private val categories: CategoryRepository,
) : BudgetTargetImportTrigger {

    override suspend fun preview(input: InputStream): BudgetTargetImportPreviewResult {
        return when (val plan = buildPlan(input)) {
            is BudgetTargetPlanResult.Done -> BudgetTargetImportPreviewResult.Done(plan.plan.toPreview())
            is BudgetTargetPlanResult.Failed -> BudgetTargetImportPreviewResult.Failed(plan.reason)
        }
    }

    override suspend fun import(input: InputStream): BudgetTargetImportResult {
        val plan = when (val result = buildPlan(input)) {
            is BudgetTargetPlanResult.Done -> result.plan
            is BudgetTargetPlanResult.Failed -> return BudgetTargetImportResult.Failed(result.reason)
        }

        plan.rows.forEach { row ->
            categories.upsert(row.category.copy(monthlyTarget = row.target))
        }

        Timber.i(
            "Budget target import: applied=%d changed=%d skipped=%d",
            plan.rows.size,
            plan.changedCount,
            plan.skippedRows.size,
        )
        return BudgetTargetImportResult.Done(
            applied = plan.rows.size,
            changed = plan.changedCount,
            skipped = plan.skippedRows.size,
        )
    }

    private suspend fun buildPlan(input: InputStream): BudgetTargetPlanResult {
        val bytes = runCatching { input.use { it.readBytes() } }
            .getOrElse { return BudgetTargetPlanResult.Failed("Couldn't read workbook: ${it.message}") }
        if (!OpenXmlWorkbookReader.looksLikeXlsx(bytes)) {
            return BudgetTargetPlanResult.Failed("Select a TMOAP .xlsx workbook to import budget targets.")
        }

        val workbook = runCatching { OpenXmlWorkbookReader.read(bytes) }
            .getOrElse { return BudgetTargetPlanResult.Failed("Couldn't read XLSX workbook: ${it.message}") }
        val sheet = workbook.sheets.firstOrNull { it.name.equals(BUDGET_TARGETS_SHEET, ignoreCase = true) }
            ?: return BudgetTargetPlanResult.Failed("This workbook does not include a Budget Targets sheet.")
        val grid = workbook.rows(sheet).orEmpty()
        if (grid.isEmpty()) {
            return BudgetTargetPlanResult.Failed("Budget Targets sheet is empty.")
        }

        val lookup = CategoryLookup.from(categories.observeAll(kind = null, includeArchived = false).first())
        val rows = mutableListOf<BudgetTargetPlanRow>()
        val skipped = mutableListOf<BudgetTargetImportSkippedRow>()
        var currentKind: CategoryKind? = null

        grid.forEachIndexed { index, rawRow ->
            val rowNumber = index + 1
            val label = rawRow.getOrNull(CATEGORY_LABEL_COLUMN)?.trim().orEmpty()
            val normalizedLabel = label.lowercase(Locale.US)
            when {
                normalizedLabel == "expense categories" -> {
                    currentKind = CategoryKind.EXPENSE
                    return@forEachIndexed
                }
                normalizedLabel == "income categories" -> {
                    currentKind = CategoryKind.INCOME
                    return@forEachIndexed
                }
                label.isBlank() || normalizedLabel.startsWith("total ") -> return@forEachIndexed
            }

            val rawTarget = rawRow.getOrNull(MONTHLY_TARGET_COLUMN)?.trim().orEmpty()
            if (rawTarget.isBlank()) return@forEachIndexed

            val kind = currentKind ?: run {
                skipped += BudgetTargetImportSkippedRow(
                    rowNumber = rowNumber,
                    label = label,
                    reason = "Target row is outside an expense or income section.",
                )
                return@forEachIndexed
            }
            val amount = parseAmount(rawTarget)
            if (amount == null || amount.signum() < 0) {
                skipped += BudgetTargetImportSkippedRow(
                    rowNumber = rowNumber,
                    label = label,
                    reason = "Monthly target is not a non-negative amount.",
                )
                return@forEachIndexed
            }
            val categoryOverride = lookup.resolve(label, kind)
            val category = categoryOverride?.id?.let { lookup.byId[it.lowercase().trim()] }
            if (category == null) {
                skipped += BudgetTargetImportSkippedRow(
                    rowNumber = rowNumber,
                    label = label,
                    reason = "No active Athar category matches this TMOAP label.",
                )
                return@forEachIndexed
            }

            val currency = category.monthlyTarget?.currency ?: Money.SAR
            val target = Money.of(amount, currency).rounded()
            rows += BudgetTargetPlanRow(
                rowNumber = rowNumber,
                category = category,
                target = target,
                changed = category.monthlyTarget?.sameAmountAndCurrency(target) != true,
            )
        }

        if (rows.isEmpty() && skipped.isEmpty()) {
            return BudgetTargetPlanResult.Failed("Budget Targets sheet does not include monthly target values.")
        }

        return BudgetTargetPlanResult.Done(
            BudgetTargetPlan(
                rows = rows,
                skippedRows = skipped,
            ),
        )
    }

    private fun BudgetTargetPlan.toPreview(): BudgetTargetImportPreview = BudgetTargetImportPreview(
        targetRows = rows.size,
        changed = changedCount,
        skipped = skippedRows.size,
        expenseTargets = rows.count { it.category.kind == CategoryKind.EXPENSE },
        incomeTargets = rows.count { it.category.kind == CategoryKind.INCOME },
        monthlyExpenseTotal = rows.totalFor(CategoryKind.EXPENSE),
        monthlyIncomeTotal = rows.totalFor(CategoryKind.INCOME),
        sampleRows = rows.take(PREVIEW_ROW_LIMIT).map { row ->
            BudgetTargetImportPreviewRow(
                rowNumber = row.rowNumber,
                categoryId = row.category.id,
                categoryName = row.category.name,
                kind = row.category.kind,
                target = row.target,
                previousTarget = row.category.monthlyTarget,
                changed = row.changed,
            )
        },
        skippedRows = skippedRows.take(PREVIEW_ROW_LIMIT),
    )

    private fun List<BudgetTargetPlanRow>.totalFor(kind: CategoryKind): Money {
        val targets = filter { it.category.kind == kind }.map { it.target }
        val currency = targets.firstOrNull()?.currency ?: Money.SAR
        return Money.sumAmounts(targets, intoCurrency = currency).rounded()
    }

    private fun parseAmount(raw: String): BigDecimal? {
        val cleaned = raw
            .replace(",", "")
            .replace("$", "")
            .replace("SAR", "", ignoreCase = true)
            .trim()
        return AmountRegex.matchEntire(cleaned)?.value?.toBigDecimalOrNull()
    }

    private fun Money.sameAmountAndCurrency(other: Money): Boolean =
        currency == other.currency && amount.compareTo(other.amount) == 0

    private sealed interface BudgetTargetPlanResult {
        data class Done(val plan: BudgetTargetPlan) : BudgetTargetPlanResult
        data class Failed(val reason: String) : BudgetTargetPlanResult
    }

    private data class BudgetTargetPlan(
        val rows: List<BudgetTargetPlanRow>,
        val skippedRows: List<BudgetTargetImportSkippedRow>,
    ) {
        val changedCount: Int = rows.count { it.changed }
    }

    private data class BudgetTargetPlanRow(
        val rowNumber: Int,
        val category: Category,
        val target: Money,
        val changed: Boolean,
    )

    private companion object {
        const val BUDGET_TARGETS_SHEET = "Budget Targets"
        const val CATEGORY_LABEL_COLUMN = 1
        const val MONTHLY_TARGET_COLUMN = 6
        const val PREVIEW_ROW_LIMIT = 6
        val AmountRegex = Regex("""[-+]?\d+(?:\.\d+)?""")
    }
}
