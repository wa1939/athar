package com.athar.core.data.csv

import com.athar.core.domain.model.PatternType
import com.athar.core.domain.model.TxStatus
import com.athar.core.domain.repo.CategoryRepository
import com.athar.core.domain.repo.CategoryRuleRepository
import com.athar.core.domain.repo.MerchantBulkImportResult
import com.athar.core.domain.repo.MerchantBulkImportTrigger
import com.athar.core.domain.repo.TransactionRepository
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import timber.log.Timber
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads a CSV emitted by [MerchantBulkExporter] (or any CSV with `id`/`stable_key` +
 * `category_id` columns), applies the chosen category to each matching transaction
 * (status → CONFIRMED), and records a learned `CategoryRule` per unique
 * (merchant_normalized → category_id) pair so future ingests benefit too.
 *
 * The category column may be either a category **id** (`cat-restaurant`) or the English /
 * Arabic display name — for AI-edited files the id is preferred since it's unambiguous.
 */
@Singleton
internal class MerchantBulkImporter @Inject constructor(
    private val transactions: TransactionRepository,
    private val rules: CategoryRuleRepository,
    private val categories: CategoryRepository,
    private val clock: Clock,
) : MerchantBulkImportTrigger {

    override suspend fun importCategorizations(input: InputStream): MerchantBulkImportResult {
        val text = runCatching { input.bufferedReader().use { it.readText() } }
            .getOrElse { return MerchantBulkImportResult.Failed("Couldn't read CSV: ${it.message}") }
        val rows = parseRows(text).filter { row -> row.any { it.isNotBlank() } }
        if (rows.isEmpty()) return MerchantBulkImportResult.Failed("Empty CSV file.")

        val header = rows[0].map { it.lowercase().trim() }
        val idIdx = header.indexOf("id")
        val stableKeyIdx = header.indexOf("stable_key")
        val sourceRefIdx = header.indexOf("source_ref_id")
        val merchantIdx = header.indexOfFirst { it == "merchant" || it == "vendor" }
        val merchantNormIdx = header.indexOf("merchant_normalized")
        val amountIdx = header.indexOf("amount")
        val currencyIdx = header.indexOf("currency")
        val typeIdx = header.indexOf("type")
        val dateIdx = header.indexOf("date")
        val categoryIdx = header.indexOfFirst {
            it == "category_id" || it == "categoryid" || it == "category"
        }
        if (categoryIdx < 0 || (idIdx < 0 && stableKeyIdx < 0)) {
            return MerchantBulkImportResult.Failed(
                "CSV needs a `category_id` (or `category`) column plus either `id` or `stable_key`.",
            )
        }

        val cats = categories.observeAll(kind = null, includeArchived = false).first()
        val byId = cats.associateBy { it.id }
        val byName = cats.associateBy { it.name.lowercase().trim() }
        val byAr = cats.associateBy { it.nameAr.trim() }

        var updated = 0
        var skipped = 0
        // Track unique (pattern → categoryId) so we add one rule per merchant, not per row.
        val rulesToAdd = mutableMapOf<String, String>()
        val allTransactions = transactions.observeAll().first()
        val byStableKey = uniqueBy(allTransactions, MerchantBulkStableKey::sourceAware)
        val byContentKey = uniqueBy(allTransactions, MerchantBulkStableKey::contentOnly)

        for ((rowIndex, row) in rows.drop(1).withIndex()) {
            if (row.size <= requiredMaxIndex(idIdx, stableKeyIdx, categoryIdx)) { skipped++; continue }
            val txId = if (idIdx >= 0 && idIdx < row.size) row[idIdx].trim() else ""
            val rawCat = row[categoryIdx].trim()
            if (rawCat.isEmpty()) { skipped++; continue }

            val categoryId = resolveCategoryId(rawCat, byId, byName, byAr)
            if (categoryId == null) {
                Timber.w("CSV row %d skipped: unknown category '%s'", rowIndex + 2, rawCat)
                skipped++
                continue
            }

            val tx = resolveTransaction(
                txId = txId,
                row = row,
                stableKeyIdx = stableKeyIdx,
                sourceRefIdx = sourceRefIdx,
                merchantIdx = merchantIdx,
                merchantNormIdx = merchantNormIdx,
                amountIdx = amountIdx,
                currencyIdx = currencyIdx,
                typeIdx = typeIdx,
                dateIdx = dateIdx,
                byStableKey = byStableKey,
                byContentKey = byContentKey,
            )
            if (tx == null) {
                Timber.w("CSV row %d skipped: no matching transaction for id/stable key", rowIndex + 2)
                skipped++
                continue
            }
            transactions.upsert(
                tx.copy(
                    categoryId = categoryId,
                    status = TxStatus.CONFIRMED,
                    updatedAt = clock.now(),
                ),
            )
            updated++

            val pattern = tx.merchantNormalized.ifBlank {
                val provided = if (merchantNormIdx >= 0 && merchantNormIdx < row.size) row[merchantNormIdx] else ""
                provided.ifBlank {
                    (if (merchantIdx >= 0 && merchantIdx < row.size) row[merchantIdx] else tx.merchant)
                        .lowercase().trim()
                }
            }
            if (pattern.isNotBlank()) rulesToAdd[pattern] = categoryId
        }

        var rulesAdded = 0
        rulesToAdd.forEach { (pattern, cat) ->
            runCatching {
                rules.learnFromCorrection(
                    merchantNormalized = pattern,
                    categoryId = cat,
                    patternType = PatternType.SUBSTRING,
                )
                rulesAdded++
            }.onFailure { Timber.w(it, "Failed to add rule for '$pattern'") }
        }

        Timber.i("Merchant bulk import: updated=%d rules=%d skipped=%d", updated, rulesAdded, skipped)
        return MerchantBulkImportResult.Done(
            updated = updated,
            rulesAdded = rulesAdded,
            skipped = skipped,
        )
    }

    private suspend fun resolveTransaction(
        txId: String,
        row: List<String>,
        stableKeyIdx: Int,
        sourceRefIdx: Int,
        merchantIdx: Int,
        merchantNormIdx: Int,
        amountIdx: Int,
        currencyIdx: Int,
        typeIdx: Int,
        dateIdx: Int,
        byStableKey: Map<String, com.athar.core.domain.model.Transaction>,
        byContentKey: Map<String, com.athar.core.domain.model.Transaction>,
    ): com.athar.core.domain.model.Transaction? {
        if (txId.isNotEmpty()) {
            transactions.get(txId)?.let { return it }
        }

        val exportedStableKey = row.getOrNull(stableKeyIdx)?.trim().orEmpty()
        if (exportedStableKey.isNotEmpty()) {
            byStableKey[exportedStableKey]?.let { return it }
        }

        val rowSourceAware = rowSourceAwareKey(
            row,
            sourceRefIdx,
            merchantIdx,
            merchantNormIdx,
            amountIdx,
            currencyIdx,
            typeIdx,
            dateIdx,
        )
        rowSourceAware?.let { byStableKey[it] }?.let { return it }

        val rowContentKey = rowContentKey(row, merchantIdx, merchantNormIdx, amountIdx, currencyIdx, typeIdx, dateIdx)
        return rowContentKey?.let { byContentKey[it] }
    }

    private fun rowSourceAwareKey(
        row: List<String>,
        sourceRefIdx: Int,
        merchantIdx: Int,
        merchantNormIdx: Int,
        amountIdx: Int,
        currencyIdx: Int,
        typeIdx: Int,
        dateIdx: Int,
    ): String? {
        val sourceRef = row.getOrNull(sourceRefIdx)?.trim().orEmpty()
        if (sourceRef.isEmpty()) return null
        return MerchantBulkStableKey.sourceAware(
            sourceRefId = sourceRef,
            merchantNormalized = rowMerchantNormalized(row, merchantIdx, merchantNormIdx) ?: return null,
            amount = row.getOrNull(amountIdx)?.trim()?.takeIf { it.isNotEmpty() } ?: return null,
            currency = row.getOrNull(currencyIdx)?.trim()?.takeIf { it.isNotEmpty() } ?: return null,
            type = row.getOrNull(typeIdx)?.trim()?.takeIf { it.isNotEmpty() } ?: return null,
            date = row.getOrNull(dateIdx)?.trim()?.takeIf { it.isNotEmpty() } ?: return null,
        )
    }

    private fun rowContentKey(
        row: List<String>,
        merchantIdx: Int,
        merchantNormIdx: Int,
        amountIdx: Int,
        currencyIdx: Int,
        typeIdx: Int,
        dateIdx: Int,
    ): String? {
        return MerchantBulkStableKey.contentOnly(
            merchantNormalized = rowMerchantNormalized(row, merchantIdx, merchantNormIdx) ?: return null,
            amount = row.getOrNull(amountIdx)?.trim()?.takeIf { it.isNotEmpty() } ?: return null,
            currency = row.getOrNull(currencyIdx)?.trim()?.takeIf { it.isNotEmpty() } ?: return null,
            type = row.getOrNull(typeIdx)?.trim()?.takeIf { it.isNotEmpty() } ?: return null,
            date = row.getOrNull(dateIdx)?.trim()?.takeIf { it.isNotEmpty() } ?: return null,
        )
    }

    private fun rowMerchantNormalized(row: List<String>, merchantIdx: Int, merchantNormIdx: Int): String? {
        val normalized = row.getOrNull(merchantNormIdx)?.lowercase()?.trim().orEmpty()
        if (normalized.isNotEmpty()) return normalized
        return row.getOrNull(merchantIdx)?.lowercase()?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun uniqueBy(
        transactions: List<com.athar.core.domain.model.Transaction>,
        keyOf: (com.athar.core.domain.model.Transaction) -> String,
    ): Map<String, com.athar.core.domain.model.Transaction> =
        transactions.groupBy(keyOf)
            .filterValues { it.size == 1 }
            .mapValues { it.value.single() }

    private fun requiredMaxIndex(vararg indexes: Int): Int =
        indexes.filter { it >= 0 }.maxOrNull() ?: -1

    private fun resolveCategoryId(
        raw: String,
        byId: Map<String, com.athar.core.domain.model.Category>,
        byName: Map<String, com.athar.core.domain.model.Category>,
        byAr: Map<String, com.athar.core.domain.model.Category>,
    ): String? {
        // Prefer exact id match (cat-restaurant). Fall back to English / Arabic names.
        byId[raw]?.let { return it.id }
        byAr[raw]?.let { return it.id }
        return byName[raw.lowercase()]?.id
    }

    private fun parseRows(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                inQuotes && c == '"' && i + 1 < text.length && text[i + 1] == '"' -> {
                    cur.append('"'); i++
                }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> { out.add(cur.toString()); cur.clear() }
                (c == '\n' || c == '\r') && !inQuotes -> {
                    out.add(cur.toString())
                    rows.add(out.toList())
                    out.clear()
                    cur.clear()
                    if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                }
                else -> cur.append(c)
            }
            i += 1
        }
        out.add(cur.toString())
        if (out.size > 1 || out.firstOrNull()?.isNotEmpty() == true) rows.add(out.toList())
        return rows
    }
}
