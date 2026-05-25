package com.athar.feature.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athar.core.common.money.Money
import com.athar.core.domain.model.InvestmentContribution
import com.athar.core.domain.model.InvestmentPool
import com.athar.core.domain.repo.InvestmentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class InvestmentsViewModel @Inject constructor(
    private val investments: InvestmentRepository,
) : ViewModel() {

    val state: StateFlow<InvestmentsState> =
        investments.observePoolsWithContributions()
            .map { derive(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InvestmentsState.initial())

    fun onEvent(event: InvestmentsEvent) {
        viewModelScope.launch {
            when (event) {
                is InvestmentsEvent.SavePool -> investments.upsertPool(event.pool)
                is InvestmentsEvent.SaveContribution -> investments.upsertContribution(
                    InvestmentContribution(
                        id = UUID.randomUUID().toString(),
                        poolId = event.poolId,
                        ownerName = event.owner,
                        amount = event.amount,
                    ),
                )
                is InvestmentsEvent.DeletePool -> investments.deletePool(event.id)
                is InvestmentsEvent.DeleteContribution -> investments.deleteContribution(event.id)
                is InvestmentsEvent.UpdatePoolReturnPercent -> {
                    val row = state.value.pools.firstOrNull { it.pool.id == event.poolId } ?: return@launch
                    val pct = BigDecimal.valueOf(event.percent / 100.0)
                    val newReturn = row.totalCorpus.amount.multiply(pct).setScale(2, RoundingMode.HALF_EVEN)
                    investments.upsertPool(row.pool.copy(totalReturn = Money.of(newReturn, row.pool.totalReturn.currency)))
                }
            }
        }
    }

    private fun derive(data: Map<InvestmentPool, List<InvestmentContribution>>): InvestmentsState {
        val rows = data.entries.map { (pool, contributions) ->
            val totalCorpus = contributions.fold(Money.zero(pool.totalReturn.currency)) { acc, c -> acc + c.amount }
            val contributorRows = contributions.map { contribution ->
                val pct = if (totalCorpus.amount.signum() == 0) 0.0
                    else (contribution.amount.amount.toDouble() / totalCorpus.amount.toDouble())
                val shareReturnAmount = pool.totalReturn.amount
                    .multiply(BigDecimal(pct))
                    .setScale(2, RoundingMode.HALF_EVEN)
                val shareReturn = Money.of(shareReturnAmount, pool.totalReturn.currency)
                ContributorRow(
                    id = contribution.id,
                    name = contribution.ownerName,
                    amount = contribution.amount,
                    sharePercent = pct * 100.0,
                    shareReturn = shareReturn,
                    netTotal = contribution.amount + shareReturn,
                )
            }.toImmutableList()
            PoolRow(pool = pool, totalCorpus = totalCorpus, contributors = contributorRows)
        }.toImmutableList()
        return InvestmentsState(pools = rows, isLoading = false)
    }
}
