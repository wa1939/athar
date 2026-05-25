package com.athar.feature.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athar.core.common.money.Money
import com.athar.core.common.time.Period
import com.athar.core.domain.model.Category
import com.athar.core.domain.model.CategoryKind
import com.athar.core.domain.model.Transaction
import com.athar.core.domain.model.TxStatus
import com.athar.core.domain.model.TxType
import com.athar.core.domain.repo.CategoryRepository
import com.athar.core.domain.repo.TransactionRepository
import com.athar.core.domain.repo.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.YearMonth
import javax.inject.Inject

@HiltViewModel
class PlanViewModel @Inject constructor(
    private val transactions: TransactionRepository,
    private val categories: CategoryRepository,
    private val prefs: UserPreferencesRepository,
    private val clock: Clock,
) : ViewModel() {

    private val selectedKey = MutableStateFlow(PlanPeriodKey.MONTH)
    private val customRange = MutableStateFlow<Pair<LocalDate, LocalDate>?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<PlanState> =
        selectedKey
            .flatMapLatest { key ->
                customRange.flatMapLatest { range ->
                    val period = period(key, range)
                    combine(
                        categories.observeAll(kind = CategoryKind.EXPENSE),
                        transactions.observeByPeriod(period, status = TxStatus.CONFIRMED),
                        prefs.displayCurrency(),
                    ) { cats, txs, currency ->
                        derive(key, period, range, cats, txs, currency)
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanState.empty(currentMonth()))

    fun onEvent(event: PlanEvent) {
        when (event) {
            is PlanEvent.SaveTarget -> saveTarget(event.categoryId, event.targetMinor)
            is PlanEvent.OpenTargetEditor, PlanEvent.DismissTargetEditor -> Unit // UI-owned
            is PlanEvent.SelectPeriod -> selectedKey.value = event.key
            is PlanEvent.SelectCustomRange -> {
                customRange.value = event.start to event.end
                selectedKey.value = PlanPeriodKey.CUSTOM
            }
        }
    }

    private fun saveTarget(categoryId: String, targetMinor: Long?) {
        viewModelScope.launch {
            val current = categories.get(categoryId) ?: return@launch
            val currency = prefs.displayCurrency().first()
            val newTarget = targetMinor?.let { Money.ofMinor(it, currency = currency) }
            categories.upsert(current.copy(monthlyTarget = newTarget))
        }
    }

    private fun period(key: PlanPeriodKey, custom: Pair<LocalDate, LocalDate>?): Period {
        val now = clock.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val ym = YearMonth.of(now.year, now.monthNumber)
        return when (key) {
            PlanPeriodKey.MONTH -> Period.Month(ym)
            PlanPeriodKey.MONTHS_3 -> Period.Last(months = 3, endingAt = now.date)
            PlanPeriodKey.YEAR -> Period.Year(now.year)
            PlanPeriodKey.CUSTOM -> custom?.let { (s, e) -> Period.Custom(s, e.plus(DatePeriod(days = 1))) }
                ?: Period.Month(ym)
        }
    }

    private fun derive(
        key: PlanPeriodKey,
        period: Period,
        custom: Pair<LocalDate, LocalDate>?,
        cats: List<Category>,
        txs: List<Transaction>,
        currency: String,
    ): PlanState {
        val byCategory = txs
            .filter { it.type == TxType.EXPENSE }
            .groupBy { it.categoryId }
            .mapValues { (_, list) -> Money.sumAmounts(list.map { it.amount }, currency) }

        val multiplier = monthsInPeriod(period)
        val multiplierBd = BigDecimal.valueOf(multiplier).setScale(4, RoundingMode.HALF_EVEN)

        val rows = cats.map { cat ->
            val scaled = cat.monthlyTarget?.let { mt ->
                Money.of(mt.amount.multiply(multiplierBd).setScale(2, RoundingMode.HALF_EVEN), mt.currency)
            }
            BudgetRow(
                category = cat,
                actual = byCategory[cat.id] ?: Money.zero(currency),
                target = scaled,
            )
        }.toImmutableList()

        val totalTarget = Money.sumAmounts(rows.mapNotNull { it.target }, currency)
        val totalActual = Money.sumAmounts(byCategory.values, currency)

        return PlanState(
            month = currentMonth(),
            periodKey = key,
            period = period,
            customStart = custom?.first,
            customEnd = custom?.second,
            targetMultiplier = multiplier,
            rows = rows,
            totalTarget = totalTarget,
            totalActual = totalActual,
            isLoading = false,
        )
    }

    /**
     * Average number of months covered by [period], using the Gregorian mean month length
     * (30.4375 days). For Period.Month this is exactly 1.0 thanks to rounding rules below
     * (28..31 days / 30.4375 stays close to 1.0; we clamp Month to 1.0 explicitly so a
     * 28-day February doesn't shrink the target).
     */
    private fun monthsInPeriod(period: Period): Double {
        if (period is Period.Month) return 1.0
        val startEpoch = period.start.toEpochDays().toLong()
        val endEpoch = period.endExclusive.toEpochDays().toLong()
        val days = (endEpoch - startEpoch).coerceAtLeast(1L)
        return days / 30.4375
    }

    private fun currentMonth(): YearMonth {
        val now = clock.now().toLocalDateTime(TimeZone.currentSystemDefault())
        return YearMonth.of(now.year, now.monthNumber)
    }
}
