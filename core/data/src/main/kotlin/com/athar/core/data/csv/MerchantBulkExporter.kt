package com.athar.core.data.csv

import com.athar.core.domain.model.Category
import com.athar.core.domain.model.CategoryKind
import com.athar.core.domain.model.Transaction
import com.athar.core.domain.model.TxStatus
import com.athar.core.domain.model.TxType
import com.athar.core.domain.model.specificMerchantKey
import com.athar.core.domain.repo.CategoryRepository
import com.athar.core.domain.repo.MerchantBulkExportMode
import com.athar.core.domain.repo.MerchantBulkExportResult
import com.athar.core.domain.repo.MerchantBulkExportTrigger
import com.athar.core.domain.repo.SmsAuditEntry
import com.athar.core.domain.repo.SmsAuditRepository
import com.athar.core.domain.repo.TransactionRepository
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.io.OutputStream
import java.io.OutputStreamWriter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Exports every non-transfer transaction that still needs a category decision:
 * anything PENDING, anything DISMISSED, and CONFIRMED rows that have no
 * `categoryId` yet. The CSV is meant to be fed to an external AI
 * (ChatGPT/Claude) with the AI triage prompt and imported back via
 * [MerchantBulkImporter].
 *
 * `category_id` is left blank in the export — that's the column the user (or AI) fills.
 * Private exports keep the same import-compatible columns but omit raw SMS bodies.
 */
@Singleton
internal class MerchantBulkExporter @Inject constructor(
    private val transactions: TransactionRepository,
    private val categories: CategoryRepository,
    private val smsAudit: SmsAuditRepository,
) : MerchantBulkExportTrigger {

    override suspend fun exportUncategorized(
        out: OutputStream,
        mode: MerchantBulkExportMode,
    ): MerchantBulkExportResult = runCatching {
        val all = transactions.observeAll().first()
        val candidates = all.filter { it.needsCategoryDecision() }
        val categoryOptions = categories.observeAll(kind = null, includeArchived = false)
            .first()
            .groupBy { it.kind }
        val rawBodiesByTransactionId = when (mode) {
            MerchantBulkExportMode.FULL_CONTEXT -> smsAudit.observeAll().first().rawBodiesByTransactionId()
            MerchantBulkExportMode.NO_RAW_BODY -> emptyMap()
        }
        val groupCounts = candidates.groupingBy { it.merchantGroupKey() }.eachCount()
        val groupImpacts = buildGroupImpacts(groupCounts, candidates.size)
        val orderedCandidates = candidates.sortedWith(
            compareByDescending<Transaction> { groupCounts[it.merchantGroupKey()] ?: 0 }
                .thenBy { it.merchantGroupKey() }
                .thenByDescending { it.date }
                .thenByDescending { it.createdAt },
        )
        OutputStreamWriter(out, Charsets.UTF_8).use { writer ->
            writer.write("id,stable_key,source_ref_id,merchant,merchant_normalized,merchant_group_count,merchant_group_rank,merchant_group_share_permille,merchant_group_cumulative_share_permille,category_options,amount,currency,type,status,date,raw_body,category_id\n")
            orderedCandidates.forEach { tx ->
                val groupKey = tx.merchantGroupKey()
                val impact = groupImpacts[groupKey] ?: MerchantGroupImpact()
                val raw = when (mode) {
                    MerchantBulkExportMode.FULL_CONTEXT -> rawBodiesByTransactionId[tx.id] ?: tx.notes.orEmpty()
                    MerchantBulkExportMode.NO_RAW_BODY -> ""
                }
                writer.write(
                    listOf(
                        tx.id,
                        MerchantBulkStableKey.sourceAware(tx),
                        tx.sourceRefId.orEmpty(),
                        tx.merchant,
                        tx.merchantNormalized,
                        (groupCounts[groupKey] ?: 1).toString(),
                        impact.rank.toString(),
                        impact.sharePermille.toString(),
                        impact.cumulativeSharePermille.toString(),
                        categoryOptions.forType(tx.type),
                        tx.amount.amount.toPlainString(),
                        tx.amount.currency,
                        tx.type.name,
                        tx.status.name,
                        tx.date.toString(),
                        raw,
                        "", // category_id — user fills this
                    ).joinToString(",") { csvEscape(it) },
                )
                writer.write("\n")
            }
            writer.flush()
        }
        candidates.size
    }.fold(
        onSuccess = {
            Timber.i("Merchant bulk export: %d candidates written", it)
            MerchantBulkExportResult.Done(rows = it)
        },
        onFailure = {
            Timber.e(it, "Merchant bulk export failed")
            MerchantBulkExportResult.Failed(reason = it.message ?: "unknown error")
        },
    )

    private fun buildGroupImpacts(
        groupCounts: Map<String, Int>,
        totalRows: Int,
    ): Map<String, MerchantGroupImpact> {
        var cumulative = 0
        return groupCounts.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .mapIndexed { index, entry ->
                cumulative += entry.value
                entry.key to MerchantGroupImpact(
                    rank = index + 1,
                    sharePermille = permille(entry.value, totalRows),
                    cumulativeSharePermille = permille(cumulative, totalRows),
                )
            }
            .toMap()
    }

    private fun Transaction.needsCategoryDecision(): Boolean =
        type != TxType.TRANSFER &&
            (
                status == TxStatus.PENDING ||
                    status == TxStatus.DISMISSED ||
                    (status == TxStatus.CONFIRMED && categoryId.isNullOrBlank())
                )

    private fun Transaction.merchantGroupKey(): String =
        specificMerchantKey(merchantNormalized = merchantNormalized, merchant = merchant)
            ?: "row:$id"

    private fun permille(numerator: Int, denominator: Int): Int =
        if (denominator <= 0) {
            0
        } else {
            ((numerator.toLong() * 1_000L) / denominator.toLong()).toInt()
        }

    private fun List<SmsAuditEntry>.rawBodiesByTransactionId(): Map<String, String> {
        val out = linkedMapOf<String, String>()
        for (entry in this) {
            val txId = entry.parsedTransactionId?.trim().orEmpty()
            val body = entry.body.trim()
            if (txId.isNotEmpty() && body.isNotEmpty() && txId !in out) {
                out[txId] = body
            }
        }
        return out
    }

    private fun Map<CategoryKind, List<Category>>.forType(type: TxType): String {
        val kind = when (type) {
            TxType.EXPENSE -> CategoryKind.EXPENSE
            TxType.INCOME -> CategoryKind.INCOME
            TxType.TRANSFER -> return ""
        }
        return this[kind].orEmpty()
            .sortedWith(compareBy<Category> { it.sortOrder }.thenBy { it.name })
            .joinToString(" | ") { "${it.id}=${it.name} / ${it.nameAr}" }
    }

    private fun csvEscape(s: String): String {
        if (s.isEmpty()) return ""
        val needsQuoting = s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuoting) {
            "\"" + s.replace("\"", "\"\"") + "\""
        } else {
            s
        }
    }

    private data class MerchantGroupImpact(
        val rank: Int = 0,
        val sharePermille: Int = 0,
        val cumulativeSharePermille: Int = 0,
    )
}
