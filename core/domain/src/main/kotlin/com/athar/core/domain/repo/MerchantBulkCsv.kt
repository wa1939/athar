package com.athar.core.domain.repo

import java.io.InputStream
import java.io.OutputStream

/**
 * Bulk-categorize-via-AI workflow (Issue #5a).
 *
 * Flow:
 *  1. **Export** — write a CSV of every non-transfer PENDING / DISMISSED /
 *     uncategorized-CONFIRMED transaction. Columns: id, stable_key, source_ref_id,
 *     merchant, merchant_group_count, category_options, amount, currency, type,
 *     status, date, raw_body, category_id (blank for user to fill).
 *  2. **External** — user feeds the CSV to ChatGPT/Claude/Z.ai with the AI triage prompt.
 *     `category_options` is read-only row context; only `category_id` should be edited.
 *  3. **Import** — read the filled CSV. For each row with a non-blank category_id:
 *       - validate the category exists,
 *       - set categoryId on the matching transaction AND move it to CONFIRMED
 *         (matched by id first, stable_key/source/content fingerprint second),
 *       - record a learned `CategoryRule` per unique (merchant → categoryId) pair so
 *         future ingests auto-categorize the same merchant.
 *
 * The pair builds a permanent personal merchant library without needing inline AI API keys.
 */
interface MerchantBulkExportTrigger {
    suspend fun exportUncategorized(out: OutputStream): MerchantBulkExportResult
}

interface MerchantBulkImportTrigger {
    suspend fun importCategorizations(input: InputStream): MerchantBulkImportResult
}

sealed interface MerchantBulkExportResult {
    data class Done(val rows: Int) : MerchantBulkExportResult
    data class Failed(val reason: String) : MerchantBulkExportResult
}

sealed interface MerchantBulkImportResult {
    data class Done(val updated: Int, val rulesAdded: Int, val skipped: Int) : MerchantBulkImportResult
    data class Failed(val reason: String) : MerchantBulkImportResult
}
