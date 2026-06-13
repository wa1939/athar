package com.athar.feature.plan

import com.athar.core.common.money.Money
import com.athar.core.domain.model.Cadence
import com.athar.core.domain.model.TxType
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.LocalDate

data class BillsState(
    val today: LocalDate?,
    val horizonEnd: LocalDate?,
    val outgoingNext30Days: Money,
    val calendarDays: ImmutableList<BillCalendarDay>,
    val items: ImmutableList<BillItem>,
    val isLoading: Boolean,
) {
    companion object {
        fun initial(): BillsState = BillsState(
            today = null,
            horizonEnd = null,
            outgoingNext30Days = Money.zero(),
            calendarDays = persistentListOf(),
            items = persistentListOf(),
            isLoading = true,
        )
    }
}

data class BillCalendarDay(
    val date: LocalDate,
    val count: Int,
    val hasOverdue: Boolean,
)

data class BillItem(
    val id: String,
    val title: String,
    val detail: String,
    val amount: Money,
    val type: TxType,
    val dueDate: LocalDate,
    val kind: BillKind,
    val cadence: BillCadenceLabel?,
    val isOverdue: Boolean,
)

enum class BillKind {
    RECURRING,
    PENDING_REVIEW,
}

fun Cadence.billLabelKey(): BillCadenceLabel = when (this) {
    Cadence.MONTHLY -> BillCadenceLabel.MONTHLY
    Cadence.WEEKLY -> BillCadenceLabel.WEEKLY
    Cadence.YEARLY -> BillCadenceLabel.YEARLY
}

enum class BillCadenceLabel {
    MONTHLY,
    WEEKLY,
    YEARLY,
}
