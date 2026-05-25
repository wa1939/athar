package com.athar.core.domain.model

import com.athar.core.common.money.Money

/**
 * The computed running balance for one account at a point in time.
 *
 * `current = account.openingBalance + sum(CONFIRMED transactions where accountId = account.id)`
 *
 * where INCOME adds and EXPENSE subtracts. TRANSFER rows are net-zero across both legs and
 * are not part of v1 ledger math (deferred until proper double-entry lands).
 */
data class AccountBalance(
    val account: Account,
    val current: Money,
)

/**
 * Aggregate net worth across all active accounts.
 *
 * `total` is a no-FX 1:1 projection into the user's chosen display currency — show
 * `byCurrency` alongside whenever `byCurrency.size > 1` so the headline is not read as
 * a converted figure.
 */
data class NetWorth(
    val total: Money,
    val byCurrency: Map<String, Money>,
    val accounts: List<AccountBalance>,
) {
    val isMixedCurrency: Boolean get() = byCurrency.size > 1
}
