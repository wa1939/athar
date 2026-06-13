package com.athar.feature.plan

import androidx.compose.runtime.Immutable
import com.athar.core.common.money.Money
import com.athar.core.domain.model.WishlistItem
import com.athar.core.domain.model.WishlistStatus
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Immutable
data class WishlistState(
    val items: ImmutableList<WishlistRow>,
    val monthlyCapacity: Money,
    val isLoading: Boolean,
) {
    companion object {
        fun initial(): WishlistState = WishlistState(
            items = persistentListOf(),
            monthlyCapacity = Money.zero(),
            isLoading = true,
        )
    }
}

data class WishlistRow(
    val item: WishlistItem,
    val status: WishlistStatus,
    val monthsNeeded: Int?,
    val remaining: Money,
    val projectedMonth: java.time.YearMonth?,
    val targetMonth: java.time.YearMonth?,
    val monthlyRequired: Money?,
    val targetFeasible: Boolean?,
)

sealed interface WishlistEvent {
    data class Save(val item: WishlistItem) : WishlistEvent
    data class Delete(val id: String) : WishlistEvent
}
