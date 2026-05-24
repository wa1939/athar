package com.athar.feature.plan

import androidx.compose.runtime.Immutable
import com.athar.core.common.money.Money
import com.athar.core.domain.model.Category
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import java.time.YearMonth

@Immutable
data class PlanState(
    val month: YearMonth,
    val rows: ImmutableList<BudgetRow>,
    val totalTarget: Money,
    val totalActual: Money,
    val isLoading: Boolean,
) {
    companion object {
        fun empty(month: YearMonth): PlanState = PlanState(
            month = month,
            rows = persistentListOf(),
            totalTarget = Money.zero(),
            totalActual = Money.zero(),
            isLoading = true,
        )
    }
}

data class BudgetRow(
    val category: Category,
    val actual: Money,
    val target: Money?,
) {
    val variance: Money? = target?.let { actual - it }
    val isOver: Boolean = variance?.isPositive() == true
    val isUnder: Boolean = variance?.isNegative() == true
}

sealed interface PlanEvent {
    data class OpenTargetEditor(val categoryId: String) : PlanEvent
    data class SaveTarget(val categoryId: String, val targetMinor: Long?) : PlanEvent
    data object DismissTargetEditor : PlanEvent
}
