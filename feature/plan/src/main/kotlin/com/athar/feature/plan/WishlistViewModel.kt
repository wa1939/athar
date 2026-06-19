package com.athar.feature.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athar.core.common.time.Period
import com.athar.core.domain.calc.WishlistCalc
import com.athar.core.domain.model.Transaction
import com.athar.core.domain.model.TxStatus
import com.athar.core.domain.model.WishlistItem
import com.athar.core.domain.repo.TransactionRepository
import com.athar.core.domain.repo.UserPreferencesRepository
import com.athar.core.domain.repo.WishlistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.time.YearMonth
import javax.inject.Inject

@HiltViewModel
class WishlistViewModel @Inject constructor(
    private val wishlist: WishlistRepository,
    private val transactions: TransactionRepository,
    private val prefs: UserPreferencesRepository,
    private val clock: Clock,
) : ViewModel() {

    val state: StateFlow<WishlistState> =
        combine(
            wishlist.observeAll(),
            transactions.observeByPeriod(last3MonthsPeriod(), status = TxStatus.CONFIRMED),
            prefs.displayCurrency(),
        ) { items, tx, currency ->
            derive(items, tx, currency)
        }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WishlistState.initial())

    fun onEvent(event: WishlistEvent) {
        viewModelScope.launch {
            when (event) {
                is WishlistEvent.Save -> wishlist.upsert(event.item)
                is WishlistEvent.Delete -> wishlist.delete(event.id)
            }
        }
    }

    private fun derive(items: List<WishlistItem>, tx: List<Transaction>, currency: String): WishlistState {
        val capacity = WishlistCalc.monthlyCapacityFromTransactions(tx, currency)
        val now = clock.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val currentMonth = YearMonth.of(now.year, now.monthNumber)
        val projected = items.map { item -> item to WishlistCalc.project(item, capacity, currentMonth) }
        val rows = projected
            .map { (item, projection) -> classify(item, projection) }
            .sortedWith(wishlistPriority())
            .toImmutableList()

        return WishlistState(
            items = rows,
            monthlyCapacity = capacity,
            summary = WishlistCalc.summarize(projected.map { it.second }, currency),
            isLoading = false,
        )
    }

    private fun classify(item: WishlistItem, projection: WishlistCalc.Projection): WishlistRow {
        return WishlistRow(
            item = item,
            status = projection.status,
            monthsNeeded = projection.monthsNeeded,
            remaining = projection.remaining,
            projectedMonth = projection.projectedMonth,
            targetMonth = projection.targetMonth,
            monthlyRequired = projection.monthlyRequired,
            targetFeasible = projection.targetFeasible,
        )
    }

    private fun wishlistPriority(): Comparator<WishlistRow> =
        compareBy<WishlistRow> { row ->
            when {
                row.monthsNeeded == 0 -> 0
                row.monthsNeeded != null -> 1
                else -> 2
            }
        }
            .thenBy { it.monthsNeeded ?: Int.MAX_VALUE }
            .thenBy { it.targetMonth ?: YearMonth.of(9999, 12) }
            .thenBy { it.item.name.lowercase() }

    private fun last3MonthsPeriod(): Period {
        val today = clock.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        return Period.Last(months = 3, endingAt = today)
    }
}
