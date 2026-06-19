package com.athar.core.data.csv

import com.athar.core.domain.model.Transaction
import java.math.BigDecimal
import java.security.MessageDigest

internal object MerchantBulkStableKey {

    fun sourceAware(tx: Transaction): String = hash(
        sourceRefId = tx.sourceRefId,
        merchantNormalized = tx.merchantNormalized,
        amount = tx.amount.amount.toPlainString(),
        currency = tx.amount.currency,
        type = tx.type.name,
        date = tx.date.toString(),
    )

    fun contentOnly(tx: Transaction): String = hash(
        sourceRefId = null,
        merchantNormalized = tx.merchantNormalized,
        amount = tx.amount.amount.toPlainString(),
        currency = tx.amount.currency,
        type = tx.type.name,
        date = tx.date.toString(),
    )

    fun sourceAware(
        sourceRefId: String?,
        merchantNormalized: String,
        amount: String,
        currency: String,
        type: String,
        date: String,
    ): String = hash(sourceRefId, merchantNormalized, amount, currency, type, date)

    fun contentOnly(
        merchantNormalized: String,
        amount: String,
        currency: String,
        type: String,
        date: String,
    ): String = hash(null, merchantNormalized, amount, currency, type, date)

    private fun hash(
        sourceRefId: String?,
        merchantNormalized: String,
        amount: String,
        currency: String,
        type: String,
        date: String,
    ): String {
        val sourcePart = sourceRefId?.trim()?.takeIf { it.isNotEmpty() }?.let { "source=$it" }
        val material = sourcePart ?: listOf(
            "merchant=${merchantNormalized.lowercase().trim()}",
            "amount=${normalizeAmount(amount)}",
            "currency=${currency.uppercase().trim()}",
            "type=${type.uppercase().trim()}",
            "date=${date.trim()}",
        ).joinToString("|")
        return sha256(material)
    }

    private fun normalizeAmount(raw: String): String =
        runCatching {
            BigDecimal(raw.trim().replace(",", ""))
                .stripTrailingZeros()
                .toPlainString()
        }.getOrDefault(raw.trim())

    private fun sha256(raw: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return bytes.joinToString(separator = "") { "%02x".format(it) }
    }
}
