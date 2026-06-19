package com.athar.core.domain.repo

import java.io.InputStream
import java.io.OutputStream

/**
 * Bulk-categorize-via-AI workflow (Issue #5a).
 *
 * Flow:
 *  1. **Export** — write a CSV of every non-transfer PENDING / DISMISSED /
 *     uncategorized-CONFIRMED transaction. Columns: id, stable_key, source_ref_id,
 *     merchant, merchant_group_count, merchant_group_rank, merchant_group_share_permille,
 *     merchant_group_cumulative_share_permille, category_options, amount, currency, type,
 *     status, date, raw_body, category_id (blank for user to fill). Full-context
 *     exports use the linked ingestion-audit message body when available, then
 *     fall back to notes. The private export mode keeps the same columns but
 *     leaves raw_body blank.
 *  2. **External** — user feeds the CSV to ChatGPT/Claude/Z.ai with the AI triage prompt.
 *     `category_options` is read-only row context; only `category_id` should be edited.
 *  3. **Preview** — read the filled CSV and return the exact update/rule/skip
 *     counts plus a category-impact summary without changing transactions or
 *     learned rules.
 *  4. **Import** — after confirmation, read the same filled CSV. For each row
 *     with a non-blank category_id:
 *       - validate the category exists and matches the transaction type,
 *       - set categoryId on the matching transaction AND move it to CONFIRMED
 *         (matched by id first, stable_key/source/content fingerprint second),
 *       - apply that category to blank rows in the same imported specific merchant group
 *         when the group has no conflicting filled categories and the category matches
 *         each row's type,
 *       - upsert an exact learned `CategoryRule` per unambiguous
 *         type-compatible specific (merchant → categoryId) pair so future ingests
 *         auto-categorize the same normalized merchant without broad substring
 *         matching, duplicate/stale exact local rules, or generic labels such as
 *         `payment` and `cash`,
 *       - return aggregate skip reasons so the user can repair AI-filled CSV
 *         mistakes without exposing raw row contents in the UI.
 *
 * The pair builds a permanent personal merchant library without needing inline AI API keys.
 */
interface MerchantBulkExportTrigger {
    suspend fun exportUncategorized(
        out: OutputStream,
        mode: MerchantBulkExportMode = MerchantBulkExportMode.FULL_CONTEXT,
    ): MerchantBulkExportResult
}

interface MerchantBulkImportTrigger {
    suspend fun previewCategorizations(input: InputStream): MerchantBulkImportResult
    suspend fun importCategorizations(input: InputStream): MerchantBulkImportResult
}

enum class MerchantBulkExportMode {
    FULL_CONTEXT,
    NO_RAW_BODY,
}

data class MerchantBulkImportSkipSummary(
    val malformedRows: Int = 0,
    val unknownCategories: Int = 0,
    val incompatibleCategories: Int = 0,
    val missingTransactions: Int = 0,
    val conflictingGroups: Int = 0,
    val blankRowsWithoutGroupChoice: Int = 0,
)

data class MerchantBulkImportCategoryImpact(
    val categoryId: String,
    val categoryName: String,
    val categoryNameAr: String,
    val updated: Int,
)

sealed interface MerchantBulkExportResult {
    data class Done(val rows: Int) : MerchantBulkExportResult
    data class Failed(val reason: String) : MerchantBulkExportResult
}

sealed interface MerchantBulkImportResult {
    data class Done(
        val updated: Int,
        val rulesAdded: Int,
        val skipped: Int,
        val skipSummary: MerchantBulkImportSkipSummary = MerchantBulkImportSkipSummary(),
        val categoryImpact: List<MerchantBulkImportCategoryImpact> = emptyList(),
    ) : MerchantBulkImportResult
    data class Failed(val reason: String) : MerchantBulkImportResult
}
