package com.athar.core.data.report

import com.athar.core.domain.model.Category
import com.athar.core.domain.model.Transaction
import com.athar.core.domain.model.TxStatus
import com.athar.core.domain.model.TxType
import com.athar.core.domain.model.isReconciliation
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

internal enum class TaxReportLocale { AR, EN }

internal data class TaxReport(
    val year: Int,
    val locale: TaxReportLocale,
    val transactions: List<TaxReportTransaction>,
    val categoryTotals: List<TaxCategoryTotal>,
    val excludedReconciliationCount: Int,
)

internal data class TaxReportTransaction(
    val date: LocalDate,
    val type: TxType,
    val categoryName: String,
    val merchant: String,
    val amount: BigDecimal,
    val currency: String,
    val notes: String?,
)

internal data class TaxCategoryTotal(
    val type: TxType,
    val categoryName: String,
    val currency: String,
    val amount: BigDecimal,
    val count: Int,
)

internal object TaxReportBuilder {

    fun build(
        year: Int,
        transactions: List<Transaction>,
        categories: List<Category>,
        localeTag: String,
    ): TaxReport {
        val locale = if (localeTag.lowercase().startsWith("ar")) TaxReportLocale.AR else TaxReportLocale.EN
        val categoryById = categories.associateBy { it.id }
        val matchingYear = transactions.filter { it.date.year == year && it.status == TxStatus.CONFIRMED }
        val excludedReconciliationCount = matchingYear.count { it.isReconciliation() }
        val reportRows = matchingYear
            .filterNot { it.isReconciliation() }
            .sortedWith(compareBy<Transaction> { it.date }.thenBy { it.merchant.lowercase() })
            .map { tx ->
                val category = tx.categoryId?.let(categoryById::get)
                TaxReportTransaction(
                    date = tx.date,
                    type = tx.type,
                    categoryName = category?.localizedName(locale) ?: uncategorizedLabel(locale),
                    merchant = tx.merchant,
                    amount = tx.amount.amount.abs(),
                    currency = tx.amount.currency,
                    notes = tx.notes?.takeIf { it.isNotBlank() },
                )
            }

        val totals = reportRows
            .groupBy { TotalKey(it.type, it.categoryName, it.currency) }
            .map { (key, rows) ->
                TaxCategoryTotal(
                    type = key.type,
                    categoryName = key.categoryName,
                    currency = key.currency,
                    amount = rows.fold(BigDecimal.ZERO) { acc, row -> acc + row.amount },
                    count = rows.size,
                )
            }
            .sortedWith(
                compareBy<TaxCategoryTotal> { it.type.sortOrder() }
                    .thenBy { it.categoryName.lowercase() }
                    .thenBy { it.currency },
            )

        return TaxReport(
            year = year,
            locale = locale,
            transactions = reportRows,
            categoryTotals = totals,
            excludedReconciliationCount = excludedReconciliationCount,
        )
    }

    private data class TotalKey(
        val type: TxType,
        val categoryName: String,
        val currency: String,
    )

    private fun Category.localizedName(locale: TaxReportLocale): String =
        when (locale) {
            TaxReportLocale.AR -> nameAr.takeIf { it.isNotBlank() } ?: name
            TaxReportLocale.EN -> name
        }

    private fun uncategorizedLabel(locale: TaxReportLocale): String =
        when (locale) {
            TaxReportLocale.AR -> "غير مصنف"
            TaxReportLocale.EN -> "Uncategorized"
        }

    private fun TxType.sortOrder(): Int =
        when (this) {
            TxType.INCOME -> 0
            TxType.EXPENSE -> 1
            TxType.TRANSFER -> 2
        }
}
