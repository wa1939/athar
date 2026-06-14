package com.athar.core.data.csv

import com.athar.core.domain.model.Category
import com.athar.core.domain.model.CategoryKind
import com.athar.core.domain.model.PatternType
import com.athar.core.domain.model.Transaction
import com.athar.core.domain.model.TxStatus
import com.athar.core.domain.model.TxType
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
 * (status → CONFIRMED). When one repeated-merchant row is filled and its blank
 * peers are left blank, the same category propagates to those peers inside the
 * imported CSV as long as the merchant group has no conflicting filled category.
 * Unambiguous merchant groups also record an exact learned `CategoryRule` so
 * future ingests of the same normalized merchant benefit without creating broad
 * substring matches.
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
        val byName = cats.groupBy { it.name.lowercase().trim() }
        val byAr = cats.groupBy { it.nameAr.trim() }

        var updated = 0
        var skipped = 0
        // Track unique (pattern → categoryId) so we add one rule per merchant, not per row.
        val rulesToAdd = mutableMapOf<String, String>()
        val allTransactions = transactions.observeAll().first()
        val byStableKey = uniqueBy(allTransactions, MerchantBulkStableKey::sourceAware)
        val byContentKey = uniqueBy(allTransactions, MerchantBulkStableKey::contentOnly)

        val dataRows = rows.drop(1).mapIndexed { index, row ->
            CsvDataRow(number = index + 2, cells = row)
        }
        val updatedTransactionIds = mutableSetOf<String>()
        val categoriesByPattern = linkedMapOf<String, MutableSet<String>>()
        val patternsWithIncompatibleTypes = linkedSetOf<String>()

        for (csvRow in dataRows) {
            val row = csvRow.cells
            if (!hasRequiredColumns(row, idIdx, stableKeyIdx, categoryIdx)) { skipped++; continue }
            val txId = if (idIdx >= 0 && idIdx < row.size) row[idIdx].trim() else ""
            val rawCat = row[categoryIdx].trim()
            if (rawCat.isEmpty()) continue

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
                Timber.w("CSV row %d skipped: no matching transaction for id/stable key", csvRow.number)
                skipped++
                continue
            }

            val pattern = merchantPattern(tx, row, merchantIdx, merchantNormIdx)
            val categoryCandidates = resolveCategoryCandidates(rawCat, byId, byName, byAr)
            if (categoryCandidates.isEmpty()) {
                Timber.w("CSV row %d skipped: unknown category '%s'", csvRow.number, rawCat)
                skipped++
                continue
            }
            val category = categoryCandidates.firstOrNull { it.isCompatibleWith(tx.type) }
            if (category == null) {
                Timber.w(
                    "CSV row %d skipped: category '%s' is incompatible with transaction type %s",
                    csvRow.number,
                    rawCat,
                    tx.type,
                )
                if (pattern.isNotBlank()) patternsWithIncompatibleTypes += pattern
                skipped++
                continue
            }

            if (applyCategory(tx, category.id, updatedTransactionIds)) updated++

            if (pattern.isNotBlank()) {
                categoriesByPattern.getOrPut(pattern) { linkedSetOf() }.add(category.id)
            }
        }

        val unambiguousGroups = categoriesByPattern
            .filterValues { it.size == 1 }
            .mapValues { it.value.single() }

        for (csvRow in dataRows) {
            val row = csvRow.cells
            if (!hasRequiredColumns(row, idIdx, stableKeyIdx, categoryIdx)) continue
            if (row[categoryIdx].trim().isNotEmpty()) continue

            val txId = if (idIdx >= 0 && idIdx < row.size) row[idIdx].trim() else ""
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
                Timber.w("CSV row %d skipped: no matching transaction for group propagation", csvRow.number)
                skipped++
                continue
            }

            val pattern = merchantPattern(tx, row, merchantIdx, merchantNormIdx)
            val categoryId = unambiguousGroups[pattern]
            if (categoryId == null) {
                skipped++
                continue
            }
            val category = byId[categoryId]
            if (category == null || !category.isCompatibleWith(tx.type)) {
                Timber.w(
                    "CSV row %d skipped: group category '%s' is incompatible with transaction type %s",
                    csvRow.number,
                    categoryId,
                    tx.type,
                )
                if (pattern.isNotBlank()) patternsWithIncompatibleTypes += pattern
                skipped++
                continue
            }
            if (applyCategory(tx, categoryId, updatedTransactionIds)) updated++
        }

        rulesToAdd.putAll(
            unambiguousGroups.filterKeys { it !in patternsWithIncompatibleTypes },
        )

        var rulesAdded = 0
        rulesToAdd.forEach { (pattern, cat) ->
            runCatching {
                rules.learnFromCorrection(
                    merchantNormalized = pattern,
                    categoryId = cat,
                    patternType = PatternType.EXACT,
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

    private suspend fun applyCategory(
        tx: Transaction,
        categoryId: String,
        updatedTransactionIds: MutableSet<String>,
    ): Boolean {
        if (!updatedTransactionIds.add(tx.id)) return false
        transactions.upsert(
            tx.copy(
                categoryId = categoryId,
                status = TxStatus.CONFIRMED,
                updatedAt = clock.now(),
            ),
        )
        return true
    }

    private fun merchantPattern(
        tx: Transaction,
        row: List<String>,
        merchantIdx: Int,
        merchantNormIdx: Int,
    ): String =
        tx.merchantNormalized.ifBlank {
            rowMerchantNormalized(row, merchantIdx, merchantNormIdx)
                ?: tx.merchant.lowercase().trim()
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
        byStableKey: Map<String, Transaction>,
        byContentKey: Map<String, Transaction>,
    ): Transaction? {
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
        transactions: List<Transaction>,
        keyOf: (Transaction) -> String,
    ): Map<String, Transaction> =
        transactions.groupBy(keyOf)
            .filterValues { it.size == 1 }
            .mapValues { it.value.single() }

    private fun hasRequiredColumns(row: List<String>, vararg indexes: Int): Boolean =
        row.size > requiredMaxIndex(*indexes)

    private fun requiredMaxIndex(vararg indexes: Int): Int =
        indexes.filter { it >= 0 }.maxOrNull() ?: -1

    private fun resolveCategoryCandidates(
        raw: String,
        byId: Map<String, Category>,
        byName: Map<String, List<Category>>,
        byAr: Map<String, List<Category>>,
    ): List<Category> {
        // Prefer exact id match (cat-restaurant). Fall back to English / Arabic names.
        byId[raw]?.let { return listOf(it) }
        byAr[raw]?.let { return it }
        return byName[raw.lowercase()].orEmpty()
    }

    private fun Category.isCompatibleWith(type: TxType): Boolean =
        when (type) {
            TxType.EXPENSE -> kind == CategoryKind.EXPENSE
            TxType.INCOME -> kind == CategoryKind.INCOME
            TxType.TRANSFER -> false
        }

    private data class CsvDataRow(
        val number: Int,
        val cells: List<String>,
    )

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
