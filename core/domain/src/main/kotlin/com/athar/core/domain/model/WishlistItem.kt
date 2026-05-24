package com.athar.core.domain.model

import com.athar.core.common.money.Money
import java.time.YearMonth

data class WishlistItem(
    val id: String,
    val name: String,
    val cost: Money,
    val currentSaved: Money,
    val desiredMonths: Int?,
    val startMonth: YearMonth,
    val notes: String?,
)

/** Computed status. Master Brief §10.4. */
sealed interface WishlistStatus {
    /** Affordable now from current cash + accrued capacity. */
    data object Now : WishlistStatus
    /** Affordable later — projected purchase month included. */
    data class WaitUntil(val month: YearMonth) : WishlistStatus
    /** Required monthly saving exceeds capacity for any reasonable horizon. */
    data object Infeasible : WishlistStatus
}
