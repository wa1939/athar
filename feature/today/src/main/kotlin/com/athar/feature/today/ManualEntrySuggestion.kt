package com.athar.feature.today

import com.athar.core.domain.model.Transaction
import com.athar.core.domain.model.TxStatus
import com.athar.core.domain.model.TxType
import com.athar.core.domain.model.isReconciliation
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
            .filter { it.merchantNormalized.isNotBlank() && it.merchant.isNotBlank() }
            .groupBy { "${it.type.name}:${it.merchantNormalized}" }
            .map { (_, rows) ->
                val latest = rows.maxWith(compareBy<Transaction> { it.date }.thenBy { it.createdAt })
                ManualEntrySuggestion(
                    merchant = latest.merchant,
                    merchantNormalized = latest.merchantNormalized,
                    amountInput = latest.amount.takeIf { it.currency == displayCurrency }?.amount?.toInputString(),
                    type = latest.type,
                    categoryId = latest.categoryId,
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
