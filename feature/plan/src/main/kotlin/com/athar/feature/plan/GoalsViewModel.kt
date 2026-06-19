package com.athar.feature.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athar.core.common.money.Money
import com.athar.core.common.time.Period
import com.athar.core.domain.calc.GoalCalc
import com.athar.core.domain.model.AccountBalance
import com.athar.core.domain.model.AccountType
import com.athar.core.domain.model.NetWorth
import com.athar.core.domain.model.Transaction
import com.athar.core.domain.model.TxStatus
import com.athar.core.domain.model.TxType
import com.athar.core.domain.model.isReconciliation
import com.athar.core.domain.repo.AccountRepository
import com.athar.core.domain.repo.TransactionRepository
import com.athar.core.domain.repo.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.math.BigDecimal
import javax.inject.Inject

@HiltViewModel
class GoalsViewModel @Inject constructor(
    private val transactions: TransactionRepository,
    private val accounts: AccountRepository,
    private val prefs: UserPreferencesRepository,
    private val clock: Clock,
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<GoalsState> =
        prefs.displayCurrency()
            .flatMapLatest { currency ->
                combine(
                    transactions.observeByPeriod(last3MonthsPeriod(), status = TxStatus.CONFIRMED),
                    accounts.observeNetWorth(currency),
                    prefs.savingsRateTargetPercent(),
                    prefs.emergencyFundTargetMonths(),
                ) { txs, netWorth, savingsTarget, emergencyMonths ->
                    derive(
                        txs = txs,
                        netWorth = netWorth,
                        currency = currency,
                        savingsTarget = savingsTarget,
                        emergencyMonths = emergencyMonths,
                    )
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GoalsState.initial())

    fun onEvent(event: GoalsEvent) {
        viewModelScope.launch {
            when (event) {
                is GoalsEvent.SetSavingsRateTarget -> prefs.setSavingsRateTargetPercent(event.percent)
                is GoalsEvent.SetEmergencyFundTarget -> prefs.setEmergencyFundTargetMonths(event.months)
            }
        }
    }

    private fun derive(
        txs: List<Transaction>,
        netWorth: NetWorth,
        currency: String,
        savingsTarget: Int,
        emergencyMonths: Int,
    ): GoalsState {
        val operating = txs.filterNot { it.isReconciliation() }
        val totalIncome = Money.sumAmounts(
            operating.filter { it.type == TxType.INCOME }.map { it.amount },
            currency,
        )
        val totalExpense = Money.sumAmounts(
            operating.filter { it.type == TxType.EXPENSE }.map { it.amount },
            currency,
        )

        val monthlyIncome = GoalCalc.averageMonthly(totalIncome, LOOKBACK_MONTHS)
        val monthlyExpense = GoalCalc.averageMonthly(totalExpense, LOOKBACK_MONTHS)
        val monthlySavings = monthlyIncome - monthlyExpense
        val savingsRate = GoalCalc.savingsRatePercent(monthlyIncome, monthlyExpense)
        val liquidBalance = liquidBalance(netWorth.accounts, currency)
        val emergencyTargetAmount = GoalCalc.emergencyTargetAmount(monthlyExpense, emergencyMonths)
        val emergencyCovered = GoalCalc.emergencyMonthsCovered(liquidBalance, monthlyExpense)

        return GoalsState(
            monthlyIncome = monthlyIncome,
            monthlyExpense = monthlyExpense,
            monthlySavings = monthlySavings,
            savingsRatePercent = savingsRate,
            savingsRateTargetPercent = savingsTarget,
            savingsRateProgress = GoalCalc.savingsRateProgress(savingsRate, savingsTarget).toProgressFloat(),
            liquidBalance = liquidBalance,
            emergencyTargetAmount = emergencyTargetAmount,
            emergencyMonthsCovered = emergencyCovered,
            emergencyFundTargetMonths = emergencyMonths,
            emergencyFundProgress = GoalCalc.emergencyProgress(emergencyCovered, emergencyMonths).toProgressFloat(),
            isLoading = false,
        )
    }

    private fun liquidBalance(balances: List<AccountBalance>, currency: String): Money {
        val total = Money.sumAmounts(
            balances
                .filter { it.account.type in LIQUID_ACCOUNT_TYPES }
                .map { it.current },
            currency,
        )
        return if (total.amount.signum() < 0) Money.zero(currency) else total
    }

    private fun last3MonthsPeriod(): Period {
        val today = clock.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        return Period.Last(months = LOOKBACK_MONTHS, endingAt = today)
    }

    private fun BigDecimal.toProgressFloat(): Float =
        toFloat().coerceIn(0f, 1f)

    companion object {
        private const val LOOKBACK_MONTHS = 3
        private val LIQUID_ACCOUNT_TYPES = setOf(
            AccountType.CHECKING,
            AccountType.SAVINGS,
            AccountType.CASH,
        )
    }
}
