package com.athar.core.data.csv

internal fun specificMerchantBulkKey(
    merchantNormalized: String,
    merchant: String,
): String? =
    merchantNormalized
        .ifBlank { merchant }
        .trim()
        .lowercase()
        .takeIf { it.isSpecificMerchantBulkKey() }

internal fun String.isSpecificMerchantBulkKey(): Boolean {
    if (length < 3) return false
    if (all { it.isDigit() || it.isWhitespace() || it == '-' || it == '+' }) return false
    if (this in GenericMerchantBulkKeys) return false
    if (GenericMerchantBulkKeys.any { this == it || startsWith("$it ") }) return false
    return true
}

private val GenericMerchantBulkKeys = setOf(
    "unknown",
    "merchant",
    "bank",
    "cash",
    "purchase",
    "online purchase",
    "transfer",
    "payment",
    "manual adjustment",
    "غير معروف",
    "تاجر",
    "بنك",
    "كاش",
    "شراء",
    "تحويل",
    "دفع",
    "تسوية",
    "تسوية يدوية",
)
