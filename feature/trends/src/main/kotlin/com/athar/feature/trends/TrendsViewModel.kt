package com.athar.feature.trends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athar.core.common.money.Money
import com.athar.core.common.time.Period
import com.athar.core.common.time.previous
import com.athar.core.designsystem.component.AtharBarItem
import com.athar.core.designsystem.component.AtharMonthlyBar
import com.athar.core.domain.model.Category
import com.athar.core.domain.model.CategoryKind
import com.athar.core.domain.model.Transaction
import com.athar.core.domain.model.TxStatus
import com.athar.core.domain.model.TxType
import com.athar.core.domain.repo.CategoryRepository
import com.athar.core.domain.repo.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.time.YearMonth
import javax.inject.Inject

data class DrilldownState(
    val categoryId: String,
    val categoryLabelAr: String,
    val bars: ImmutableList<AtharMonthlyBar>,
    val total: Money,
)

@HiltViewModel
class TrendsViewModel @Inject constructor(
    private val transactions: TransactionRepository,
    private val categories: CategoryRepository,
    private val clock: Clock,
) : ViewModel() {

    private val selectedKey = MutableStateFlow(PeriodKey.MONTH)
    private val selectedCategoryId = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<TrendsState> =
        selectedKey
            .flatMapLatest { key ->
                val period = period(key)
                val previous = period.previous()
                combine(
                    categories.observeAll(),
                    transactions.observeByPeriod(period, status = TxStatus.CONFIRMED),
                    transactions.observeByPeriod(previous, status = TxStatus.CONFIRMED),
                ) { cats, current, prior ->
                    derive(key, period, cats, current, prior)
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrendsState.initial())

    @OptIn(ExperimentalCoroutinesApi::class)
    val drilldown: StateFlow<DrilldownState?> =
        selectedCategoryId
            .flatMapLatest { catId ->
                if (catId == null) flowOf(null)
                else combine(
                    categories.observeAll(includeArchived = true),
                    transactions.observeByPeriod(last12Months(), status = TxStatus.CONFIRMED),
                ) { cats, txs -> buildDrilldown(catId, cats, txs) }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun onEvent(event: TrendsEvent) {
        when (event) {
            is TrendsEvent.SelectPeriod -> selectedKey.value = event.key
        }
    }

    fun openDrilldown(categoryId: String) {
        selectedCategoryId.value = categoryId
    }

    fun closeDrilldown() {
        selectedCategoryId.value = null
    }

    private fun period(key: PeriodKey): Period {
        val now = clock.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val ym = YearMonth.of(now.year, now.monthNumber)
        return when (key) {
            PeriodKey.MONTH -> Period.Month(ym)
            PeriodKey.MONTHS_3 -> Period.Last(months = 3, endingAt = now.date)
            PeriodKey.YEAR -> Period.Year(now.year)
            PeriodKey.MONTH_VS_PREVIOUS -> Period.Month(ym)
        }
    }

    private fun last12Months(): Period {
        val today: LocalDate = clock.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        return Period.Last(months = 12, endingAt = today)
    }

    private fun derive(
        key: PeriodKey,
        period: Period,
        cats: List<Category>,
        current: List<Transaction>,
        prior: List<Transaction>,
    ): TrendsState {
        val expense = current.filter { it.type == TxType.EXPENSE }
        val income = current.filter { it.type == TxType.INCOME }
        val expenseSum = expense.fold(Money.zero()) { acc, tx -> acc + tx.amount }
        val incomeSum = income.fold(Money.zero()) { acc, tx -> acc + tx.amount }
        val priorExpense = prior.filter { it.type == TxType.EXPENSE }
        val priorIncome = prior.filter { it.type == TxType.INCOME }
        val priorExpenseSum = priorExpense.fold(Money.zero()) { acc, tx -> acc + tx.amount }
        val priorIncomeSum = priorIncome.fold(Money.zero()) { acc, tx -> acc + tx.amount }

        val byCategoryId = expense.groupBy { it.categoryId }
            .mapValues { (_, list) -> list.fold(Money.zero()) { acc, tx -> acc + tx.amount } }
        val priorByCategoryId = priorExpense.groupBy { it.categoryId }
            .mapValues { (_, list) -> list.fold(Money.zero()) { acc, tx -> acc + tx.amount } }

        val categoryById = cats.associateBy { it.id }
        val bars = byCategoryId
            .mapNotNull { (catId, total) ->
                val cat = catId?.let { categoryById[it] } ?: return@mapNotNull null
                AtharBarItem(
                    key = cat.id,
                    labelAr = cat.nameAr,
                    labelEn = cat.name,
                    value = total,
                )
            }
            .sortedByDescending { it.value.amount }
            .take(TOP_N)
            .toImmutableList()

        // Category-delta table — every category that had non-zero spend in EITHER period.
        val allCatIds = (byCategoryId.keys + priorByCategoryId.keys).filterNotNull().toSet()
        val deltas = allCatIds.mapNotNull { catId ->
            val cat = categoryById[catId] ?: return@mapNotNull null
            CategoryDeltaRow(
                categoryId = catId,
                labelAr = cat.nameAr,
                labelEn = cat.name,
                currentTotal = byCategoryId[catId] ?: Money.zero(),
                previousTotal = priorByCategoryId[catId] ?: Money.zero(),
            )
        }.sortedByDescending { it.currentTotal.amount.max(it.previousTotal.amount) }
            .toImmutableList()

        return TrendsState(
            periodKey = key,
            period = period,
            totalExpense = expenseSum,
            totalIncome = incomeSum,
            previousExpense = priorExpenseSum,
            previousIncome = priorIncomeSum,
            savings = incomeSum - expenseSum,
            previousSavings = priorIncomeSum - priorExpenseSum,
            categories = bars,
            categoryDeltas = deltas,
            isLoading = false,
        )
    }

    private fun buildDrilldown(
        categoryId: String,
        cats: List<Category>,
        txs: List<Transaction>,
    ): DrilldownState {
        val now = clock.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val nowYm = YearMonth.of(now.year, now.monthNumber)
        // 12-month timeline ending at the current month (inclusive).
        val months: List<YearMonth> = (11 downTo 0).map { offset -> nowYm.minusMonths(offset.toLong()) }
        val bucketed = mutableMapOf<YearMonth, Money>().apply {
            months.forEach { put(it, Money.zero()) }
        }
        txs.filter { it.categoryId == categoryId }
            .forEach { tx ->
                val ym = YearMonth.of(tx.date.year, tx.date.monthNumber)
                if (ym in bucketed) {
                    bucketed[ym] = bucketed.getValue(ym) + tx.amount
                }
            }
        val bars = months.map { ym -> AtharMonthlyBar(month = ym, amount = bucketed.getValue(ym)) }
            .toImmutableList()
        val total = bars.fold(Money.zero()) { acc, b -> acc + b.amount }
        val cat = cats.firstOrNull { it.id == categoryId }
        return DrilldownState(
            categoryId = categoryId,
            categoryLabelAr = cat?.nameAr.orEmpty(),
            bars = bars,
            total = total,
        )
    }

    companion object {
        private const val TOP_N = 8
    }
}
