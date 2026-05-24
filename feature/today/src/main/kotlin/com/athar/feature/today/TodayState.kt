package com.athar.feature.today

import androidx.compose.runtime.Immutable
import com.athar.core.common.money.Money
import com.athar.core.domain.model.Transaction
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import java.time.YearMonth

@Immutable
data class TodayState(
    val month: YearMonth,
    val netFlow: Money,
    val totalExpense: Money,
    val totalIncome: Money,
    val recent: ImmutableList<Transaction>,
    val pending: ImmutableList<Transaction>,
    val isLoading: Boolean,
) {
    companion object {
        fun empty(month: YearMonth): TodayState = TodayState(
            month = month,
            netFlow = Money.zero(),
            totalExpense = Money.zero(),
            totalIncome = Money.zero(),
            recent = persistentListOf(),
            pending = persistentListOf(),
            isLoading = true,
        )
    }
}

sealed interface TodayEvent {
    data class ConfirmPending(val id: String) : TodayEvent
    data class DismissPending(val id: String) : TodayEvent
    data class OpenTransaction(val id: String) : TodayEvent
    data object AddManual : TodayEvent
}
