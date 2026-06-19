package com.athar.core.domain.repo

import com.athar.core.common.money.Money
import java.io.InputStream
import java.time.YearMonth

/**
 * Imports wishlist rows from the TMOAP Wishlist worksheet.
 *
 * This stays separate from statement import and budget-target import so selecting
 * a transactions workbook never silently changes the planning module.
 */
interface WishlistImportTrigger {
    suspend fun preview(input: InputStream): WishlistImportPreviewResult
    suspend fun import(input: InputStream): WishlistImportResult
}

sealed interface WishlistImportPreviewResult {
    data class Done(val preview: WishlistImportPreview) : WishlistImportPreviewResult
    data class Failed(val reason: String) : WishlistImportPreviewResult
}

data class WishlistImportPreview(
    val itemRows: Int,
    val newItems: Int,
    val updatedItems: Int,
    val skipped: Int,
    val totalCost: Money,
    val totalSaved: Money,
    val sampleRows: List<WishlistImportPreviewRow>,
    val skippedRows: List<WishlistImportSkippedRow>,
)

data class WishlistImportPreviewRow(
    val rowNumber: Int,
    val name: String,
    val cost: Money,
    val currentSaved: Money,
    val desiredMonths: Int?,
    val startMonth: YearMonth,
    val existing: Boolean,
)

data class WishlistImportSkippedRow(
    val rowNumber: Int,
    val label: String,
    val reason: String,
)

sealed interface WishlistImportResult {
    data class Done(
        val imported: Int,
        val newItems: Int,
        val updatedItems: Int,
        val skipped: Int,
    ) : WishlistImportResult

    data class Failed(val reason: String) : WishlistImportResult
}
