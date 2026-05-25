package com.athar.feature.plan

import androidx.compose.runtime.Immutable
import com.athar.core.common.money.Money
import com.athar.core.domain.model.InvestmentPool
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Immutable
data class InvestmentsState(
    val pools: ImmutableList<PoolRow>,
    val isLoading: Boolean,
) {
    companion object {
        fun initial(): InvestmentsState = InvestmentsState(
            pools = persistentListOf(),
            isLoading = true,
        )
    }
}

data class PoolRow(
    val pool: InvestmentPool,
    val totalCorpus: Money,
    val contributors: ImmutableList<ContributorRow>,
)

data class ContributorRow(
    val id: String,
    val name: String,
    val amount: Money,
    val sharePercent: Double,
    val shareReturn: Money,
    val netTotal: Money,
)

sealed interface InvestmentsEvent {
    data class SavePool(val pool: InvestmentPool) : InvestmentsEvent
    data class SaveContribution(val poolId: String, val owner: String, val amount: Money) : InvestmentsEvent
    data class DeletePool(val id: String) : InvestmentsEvent
    data class DeleteContribution(val id: String) : InvestmentsEvent
    /**
     * Updates a pool's total return given as a percentage of the corpus, instead of an
     * absolute SAR amount. The ViewModel computes `corpus × pct/100` and writes the
     * resulting absolute return back to the pool.
     */
    data class UpdatePoolReturnPercent(val poolId: String, val percent: Double) : InvestmentsEvent
}
