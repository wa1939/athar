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
 * Reads a CSV emitted by [MerchantBulkExporter] (or any CSV with `id` + `category_id`
 * columns), applies the chosen category to each matching transaction (status → CONFIRMED),
 * and records a learned `CategoryRule` per unique (merchant_normalized → category_id) pair
 * so future ingests benefit too.
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
        val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.isEmpty()) return MerchantBulkImportResult.Failed("Empty CSV file.")

        val header = parseRow(lines[0]).map { it.lowercase().trim() }
        val idIdx = header.indexOf("id")
        val merchantIdx = header.indexOfFirst { it == "merchant" || it == "vendor" }
        val merchantNormIdx = header.indexOf("merchant_normalized")
        val categoryIdx = header.indexOfFirst {
            it == "category_id" || it == "categoryid" || it == "category"
        }
        if (idIdx < 0 || categoryIdx < 0) {
            return MerchantBulkImportResult.Failed(
                "CSV needs an `id` column and a `category_id` (or `category`) column.",
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

        for ((rowIndex, line) in lines.drop(1).withIndex()) {
            val row = parseRow(line)
            if (row.size <= maxOf(idIdx, categoryIdx)) { skipped++; continue }
            val txId = row[idIdx].trim()
            val rawCat = row[categoryIdx].trim()
            if (txId.isEmpty() || rawCat.isEmpty()) { skipped++; continue }

            val categoryId = resolveCategoryId(rawCat, byId, byName, byAr)
            if (categoryId == null) {
                Timber.w("CSV row %d skipped: unknown category '%s'", rowIndex + 2, rawCat)
                skipped++
                continue
            }

            val tx = transactions.get(txId)
            if (tx == null) {
                Timber.w("CSV row %d skipped: no transaction with id %s", rowIndex + 2, txId)
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

    private fun parseRow(line: String): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                inQuotes && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> {
                    cur.append('"'); i++
                }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> { out.add(cur.toString()); cur.clear() }
                else -> cur.append(c)
            }
            i += 1
        }
        out.add(cur.toString())
        return out
    }
}
