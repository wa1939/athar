package com.athar.feature.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athar.core.common.money.Money
import com.athar.core.common.time.Period
import com.athar.core.domain.model.Transaction
import com.athar.core.domain.model.TxStatus
import com.athar.core.domain.model.TxType
import com.athar.core.domain.model.WishlistItem
import com.athar.core.domain.model.WishlistStatus
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
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.YearMonth
import javax.inject.Inject
import kotlin.math.ceil

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
        val income = Money.sumAmounts(tx.filter { it.type == TxType.INCOME }.map { it.amount }, currency)
        val expense = Money.sumAmounts(tx.filter { it.type == TxType.EXPENSE }.map { it.amount }, currency)
        val net = income - expense
        // Monthly capacity = (3-month net flow) / 3, floored at zero.
        val capacity: Money = if (net.amount.signum() <= 0) Money.zero(currency) else
            Money.of(net.amount.divide(BigDecimal(3), 2, RoundingMode.HALF_EVEN), currency)

        val rows = items.map { item -> classify(item, capacity) }.toImmutableList()

        return WishlistState(
            items = rows,
            monthlyCapacity = capacity,
            isLoading = false,
        )
    }

    private fun classify(item: WishlistItem, capacity: Money): WishlistRow {
        val remaining = item.cost - item.currentSaved
        return when {
            remaining.amount.signum() <= 0 ->
                WishlistRow(item = item, status = WishlistStatus.Now, monthsNeeded = 0)
            capacity.amount.signum() <= 0 ->
                WishlistRow(item = item, status = WishlistStatus.Infeasible, monthsNeeded = null)
            else -> {
                val ratio = remaining.amount.toDouble() / capacity.amount.toDouble()
                val months = ceil(ratio).toInt()
                if (months > MAX_REASONABLE_MONTHS) {
                    WishlistRow(item = item, status = WishlistStatus.Infeasible, monthsNeeded = null)
                } else {
                    val now = clock.now().toLocalDateTime(TimeZone.currentSystemDefault())
                    val ym = YearMonth.of(now.year, now.monthNumber).plusMonths(months.toLong())
                    WishlistRow(
                        item = item,
                        status = WishlistStatus.WaitUntil(ym),
                        monthsNeeded = months,
                    )
                }
            }
        }
    }

    private fun last3MonthsPeriod(): Period {
        val today = clock.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        return Period.Last(months = 3, endingAt = today)
    }

    companion object {
        private const val MAX_REASONABLE_MONTHS = 36
    }
}
