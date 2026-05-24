package com.athar.core.domain.model

import kotlinx.datetime.Instant

/**
 * A user-defined bank-SMS parsing template. Created from the Settings screen when
 * the user wants to teach Athar how to parse messages from a bank we don't have a
 * built-in template for.
 *
 * The matching strategy is **anchor-based**, not regex:
 *   - `amountAnchorBefore` — the literal substring immediately preceding the amount
 *     (e.g. "Amount:", "المبلغ:", "تم خصم"). At runtime we find this substring,
 *     skip whitespace + currency symbols, and read the number.
 *   - `amountAnchorAfter` — optional, used to bound the number when the SMS has
 *     other digits after it (e.g. "SAR", "ر.س").
 *   - `merchantAnchorBefore/After` — same idea for the merchant or destination.
 *   - `counterpartyAnchorBefore/After` — for transfers, the recipient/sender name.
 *
 * Why anchors over regex: regular users cannot author regex. Asking them for a
 * literal substring they can see in their own SMS is a much friendlier ask, and
 * we generate a robust runtime extractor under the hood.
 */
data class UserTemplate(
    val id: String,
    val displayName: String,
    val sender: String,
    val txType: TxType,
    val amountAnchorBefore: String,
    val amountAnchorAfter: String? = null,
    val merchantAnchorBefore: String? = null,
    val merchantAnchorAfter: String? = null,
    val counterpartyAnchorBefore: String? = null,
    val counterpartyAnchorAfter: String? = null,
    val sampleBody: String,
    val createdAt: Instant,
)
