package com.athar.feature.widgets

import android.content.Context
import com.athar.core.common.money.Money
import com.athar.core.common.time.Period
import com.athar.core.domain.model.NetWorth
import com.athar.core.domain.model.Transaction
import com.athar.core.domain.model.TxStatus
import com.athar.core.domain.model.TxType
import com.athar.core.domain.model.isReconciliation
import com.athar.core.domain.repo.AccountRepository
import com.athar.core.domain.repo.TransactionRepository
import com.athar.core.domain.repo.UserPreferencesRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.time.YearMonth

/**
 * Hilt EntryPoint that exposes the data-layer repositories to Glance widgets,
 * which can't use `@Inject` because they have no lifecycle owner.
 *
 * Usage:
 *   val snap = WidgetDataLoader.loadTodaySnapshot(context)
 *
 * Each snapshot suspend fun does one Flow.first() per source, so the call is
 * cheap and bounded. Widgets re-invoke on each Glance composition (which the
 * system retriggers on its own update cadence + when the app calls updateAll).
 */
object WidgetDataLoader {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetDataEntryPoint {
        fun transactions(): TransactionRepository
        fun accounts(): AccountRepository
        fun prefs(): UserPreferencesRepository
        fun clock(): Clock
    }

    private fun entry(context: Context): WidgetDataEntryPoint =
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetDataEntryPoint::class.java,
        )

    // ---------- Public snapshot loaders ----------

    suspend fun loadMonthlySnapshot(context: Context): MonthlySnapshot {
        val e = entry(context)
        val currency = e.prefs().displayCurrency().first()
        val now = e.clock().now().toLocalDateTime(TimeZone.currentSystemDefault())
        val month = YearMonth.of(now.year, now.monthNumber)
        val period = Period.Month(month)
        val confirmed = e.transactions()
            .observeByPeriod(period, status = TxStatus.CONFIRMED).first()
        // Reconciliations only move net worth; never count them as income/expense.
        val operating = confirmed.filterNot { it.isReconciliation() }
        val (income, expense) = operating.partition { it.type == TxType.INCOME }
        val incomeSum = Money.sumAmounts(income.map { it.amount }, currency)
        val expenseSum = Money.sumAmounts(expense.map { it.amount }, currency)
        val netWorth: NetWorth = e.accounts().observeNetWorth(currency).first()
        return MonthlySnapshot(
            yearMonth = "${month.year}/${month.monthValue}",
            netFlow = incomeSum - expenseSum,
            income = incomeSum,
            expense = expenseSum,
            netWorth = netWorth.total,
        )
    }

    suspend fun loadTodaySnapshot(context: Context): TodaySnapshot {
        val e = entry(context)
        val currency = e.prefs().displayCurrency().first()
        val today = e.clock().now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val month = YearMonth.of(today.year, today.monthNumber)
        val period = Period.Month(month)
        val confirmed = e.transactions()
            .observeByPeriod(period, status = TxStatus.CONFIRMED).first()
        val todayTxns = confirmed
            .filter { it.date == today }
            .filterNot { it.isReconciliation() }
        val (todayIncome, todayExpense) = todayTxns.partition { it.type == TxType.INCOME }
        val todayNet = Money.sumAmounts(todayIncome.map { it.amount }, currency) -
            Money.sumAmounts(todayExpense.map { it.amount }, currency)
        val pending = e.transactions().observePending().first()
        return TodaySnapshot(
            todayNet = todayNet,
            pendingCount = pending.size,
        )
    }

    suspend fun loadPendingHead(context: Context, max: Int = 3): PendingSnapshot {
        val e = entry(context)
        val pending: List<Transaction> = e.transactions().observePending().first()
        return PendingSnapshot(
            totalCount = pending.size,
            head = pending.take(max),
        )
    }

    // ---------- Snapshot DTOs ----------

    data class MonthlySnapshot(
        val yearMonth: String,
        val netFlow: Money,
        val income: Money,
        val expense: Money,
        val netWorth: Money,
    )

    data class TodaySnapshot(
        val todayNet: Money,
        val pendingCount: Int,
    )

    data class PendingSnapshot(
        val totalCount: Int,
        val head: List<Transaction>,
    )
}
