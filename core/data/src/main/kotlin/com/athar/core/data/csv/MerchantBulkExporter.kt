package com.athar.core.data.csv

import com.athar.core.domain.model.Transaction
import com.athar.core.domain.model.TxStatus
import com.athar.core.domain.model.TxType
import com.athar.core.domain.repo.MerchantBulkExportResult
import com.athar.core.domain.repo.MerchantBulkExportTrigger
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
 */
@Singleton
internal class MerchantBulkExporter @Inject constructor(
    private val transactions: TransactionRepository,
) : MerchantBulkExportTrigger {

    override suspend fun exportUncategorized(out: OutputStream): MerchantBulkExportResult = runCatching {
        val all = transactions.observeAll().first()
        val candidates = all.filter { it.needsCategoryDecision() }
        val groupCounts = candidates.groupingBy { it.merchantGroupKey() }.eachCount()
        val orderedCandidates = candidates.sortedWith(
            compareByDescending<Transaction> { groupCounts[it.merchantGroupKey()] ?: 0 }
                .thenBy { it.merchantGroupKey() }
                .thenByDescending { it.date }
                .thenByDescending { it.createdAt },
        )
        OutputStreamWriter(out, Charsets.UTF_8).use { writer ->
            writer.write("id,stable_key,source_ref_id,merchant,merchant_normalized,merchant_group_count,amount,currency,type,status,date,raw_body,category_id\n")
            orderedCandidates.forEach { tx ->
                val raw = tx.notes.orEmpty() // raw SMS body lives in notes when ingested
                writer.write(
                    listOf(
                        tx.id,
                        MerchantBulkStableKey.sourceAware(tx),
                        tx.sourceRefId.orEmpty(),
                        tx.merchant,
                        tx.merchantNormalized,
                        (groupCounts[tx.merchantGroupKey()] ?: 1).toString(),
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

    private fun Transaction.needsCategoryDecision(): Boolean =
        type != TxType.TRANSFER &&
            (
                status == TxStatus.PENDING ||
                    status == TxStatus.DISMISSED ||
                    (status == TxStatus.CONFIRMED && categoryId.isNullOrBlank())
                )

    private fun Transaction.merchantGroupKey(): String =
        merchantNormalized.ifBlank { merchant.lowercase().trim() }.ifBlank { "blank" }

    private fun csvEscape(s: String): String {
        if (s.isEmpty()) return ""
        val needsQuoting = s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuoting) {
            "\"" + s.replace("\"", "\"\"") + "\""
        } else {
            s
        }
    }
}
