package com.athar.feature.today

import com.athar.core.domain.model.Transaction
import com.athar.core.domain.model.TxStatus
import com.athar.core.domain.model.TxType
import com.athar.core.domain.model.isReconciliation
import com.athar.core.domain.model.specificMerchantKey
import java.math.BigDecimal

data class ManualEntrySuggestion(
    val merchant: String,
    val merchantNormalized: String,
    val amountInput: String?,
    val type: TxType,
    val categoryId: String?,
    val uses: Int,
) {
    val key: String = "${type.name}:$merchantNormalized"
}

internal object ManualEntrySuggestionBuilder {

    fun build(
        transactions: List<Transaction>,
        displayCurrency: String,
        max: Int = MAX_SUGGESTIONS,
    ): List<ManualEntrySuggestion> {
        return transactions
            .asSequence()
            .filter { it.status == TxStatus.CONFIRMED }
            .filter { it.type == TxType.EXPENSE || it.type == TxType.INCOME }
            .filterNot { it.isReconciliation() }
            .filter { it.merchant.isNotBlank() }
            .mapNotNull { tx ->
                val merchantKey = specificMerchantKey(
                    merchantNormalized = tx.merchantNormalized,
                    merchant = tx.merchant,
                ) ?: return@mapNotNull null
                ManualEntrySuggestionCandidate(merchantKey = merchantKey, transaction = tx)
            }
            .groupBy { "${it.transaction.type.name}:${it.merchantKey}" }
            .map { (_, rows) ->
                val latest = rows.maxWith(
                    compareBy<ManualEntrySuggestionCandidate> { it.transaction.date }
                        .thenBy { it.transaction.createdAt },
                )
                val latestTx = latest.transaction
                ManualEntrySuggestion(
                    merchant = latestTx.merchant,
                    merchantNormalized = latest.merchantKey,
                    amountInput = latestTx.amount.takeIf { it.currency == displayCurrency }?.amount?.toInputString(),
                    type = latestTx.type,
                    categoryId = latestTx.categoryId,
                    uses = rows.size,
                )
            }
            .sortedWith(
                compareByDescending<ManualEntrySuggestion> { it.uses }
                    .thenBy { it.merchant.lowercase() },
            )
            .take(max)
    }

    private fun BigDecimal.toInputString(): String =
        stripTrailingZeros().toPlainString()

    private const val MAX_SUGGESTIONS = 20
}

private data class ManualEntrySuggestionCandidate(
    val merchantKey: String,
    val transaction: Transaction,
)
