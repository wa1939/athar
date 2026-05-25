package com.athar.core.domain.model

import com.athar.core.common.money.Money
import kotlinx.datetime.Instant

/**
 * A user-owned account: checking, savings, credit card, cash wallet, investment.
 * Replaces the v1 single-account model. Net worth (G-4) is sum of all accounts'
 * computed current balance = openingBalance + sum(CONFIRMED transactions on this account).
 *
 * `openingBalance` is the user-stated balance at the moment the account is added to Athar
 * (or the date Athar starts tracking). It can be negative (credit-card debt).
 *
 * `archived` is the soft-delete flag. Archived accounts stay in the DB so historical
 * transactions still resolve, but they don't show in the active picker.
 */
data class Account(
    val id: String,
    val name: String,
    val type: AccountType,
    val currency: String,
    val openingBalance: Money,
    val smsSenders: List<String>,
    val notes: String?,
    val sortOrder: Int,
    val archived: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
)
