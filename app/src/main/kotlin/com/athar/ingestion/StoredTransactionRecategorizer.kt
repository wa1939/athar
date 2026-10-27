package com.athar.ingestion

import android.content.Context
import com.athar.core.domain.model.CategorySource
import com.athar.core.domain.model.TxType
import com.athar.core.domain.repo.MerchantCatalogRepository
import com.athar.core.domain.repo.TransactionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.datetime.Clock
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WM084: one-time backfill of categories for stored transactions that never
 * received one (`categoryId == null`).
 *
 * Runs after rule seeding at startup whenever the rule bundle or the merchant
 * catalog version changed since the last pass — so shipping new
 * merchant→category rules (or a catalog update) also categorizes everything
 * the user already has, not only future messages.
 *
 * Contract:
 * - Never overwrites an existing category — user intent is final.
 * - Never touches TRANSFER rows (unclassifiable by design).
 * - Never changes confirmation status — this fills in a missing category only.
 */
@Singleton
class StoredTransactionRecategorizer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val transactions: TransactionRepository,
    private val resolver: MerchantCategoryResolver,
    private val catalog: MerchantCatalogRepository,
    private val clock: Clock,
) {

    data class RecategorizeResult(val candidates: Int, val categorized: Int) {
        val changed: Boolean get() = categorized > 0
    }

    suspend fun recategorizeIfRulesChanged(): RecategorizeResult {
        val seedPrefs = context.getSharedPreferences(SEED_PREFS, Context.MODE_PRIVATE)
        val rulesSha = seedPrefs.getString(RULES_SHA_KEY, null) ?: return RecategorizeResult(0, 0)
        val catalogVersion = catalog.snapshot()?.version?.toString() ?: "none"
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastRulesSha = prefs.getString(LAST_RULES_SHA, null)
        val lastCatalog = prefs.getString(LAST_CATALOG, null)
        if (lastRulesSha == rulesSha && lastCatalog == catalogVersion) {
            return RecategorizeResult(0, 0)
        }

        val result = runPass()
        prefs.edit()
            .putString(LAST_RULES_SHA, rulesSha)
            .putString(LAST_CATALOG, catalogVersion)
            .apply()
        if (result.changed) {
            Timber.i("Recategorized ${result.categorized}/${result.candidates} stored transactions with new rules.")
        }
        return result
    }

    internal suspend fun runPass(): RecategorizeResult {
        val now = clock.now()
        var candidates = 0
        var categorized = 0
        transactions.observeAll().first().forEach { tx ->
            if (tx.categoryId != null || tx.type == TxType.TRANSFER) return@forEach
            val merchantKey = tx.merchantNormalized.ifBlank { tx.merchant }.trim()
            if (merchantKey.isBlank()) return@forEach
            candidates += 1
            val suggestion = resolver.resolve(merchantKey, tx.type).suggestion
            val categoryId = suggestion.categoryId ?: return@forEach
            transactions.upsert(
                tx.copy(
                    categoryId = categoryId,
                    categorySource = suggestion.source.takeIf { it != CategorySource.UNKNOWN },
                    updatedAt = now,
                ),
            )
            categorized += 1
        }
        return RecategorizeResult(candidates, categorized)
    }

    private companion object {
        const val SEED_PREFS = "athar_seed_markers"
        const val RULES_SHA_KEY = "rule_seed_payload_sha256"
        const val PREFS = "athar_recategorizer"
        const val LAST_RULES_SHA = "last_rules_sha"
        const val LAST_CATALOG = "last_catalog_version"
    }
}
