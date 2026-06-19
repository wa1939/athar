package com.athar.feature.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athar.core.common.money.Money
import com.athar.core.domain.calc.RecurringSchedule
import com.athar.core.domain.model.RecurringRule
import com.athar.core.domain.model.Transaction
import com.athar.core.domain.model.TxType
import com.athar.core.domain.repo.RecurringRuleRepository
import com.athar.core.domain.repo.TransactionRepository
import com.athar.core.domain.repo.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject

@HiltViewModel
class BillsViewModel @Inject constructor(
    rules: RecurringRuleRepository,
    transactions: TransactionRepository,
    private val prefs: UserPreferencesRepository,
    private val clock: Clock,
) : ViewModel() {

    val state: StateFlow<BillsState> =
        combine(
            rules.observeAll(),
            transactions.observePending(),
            prefs.displayCurrency(),
            prefs.billRemindersEnabled(),
        ) { recurringRules, pending, currency, remindersEnabled ->
            derive(recurringRules, pending, currency, remindersEnabled)
        }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BillsState.initial())

    fun onEvent(event: BillsEvent) {
        viewModelScope.launch {
            when (event) {
                is BillsEvent.SetRemindersEnabled -> prefs.setBillRemindersEnabled(event.enabled)
            }
        }
    }

    private fun derive(
        recurringRules: List<RecurringRule>,
        pending: List<Transaction>,
        currency: String,
        remindersEnabled: Boolean,
    ): BillsState {
        val today = clock.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val horizonEnd = today.plus(DatePeriod(days = HORIZON_DAYS))
        val next30End = today.plus(DatePeriod(days = TOTAL_DAYS))

        val recurringItems = recurringRules
            .flatMap { rule ->
                RecurringSchedule.project(rule, today = today, horizonDays = HORIZON_DAYS).map { occurrence ->
                    BillItem(
                        id = "recurring:${rule.id}:${occurrence.dueDate}",
                        title = rule.displayName.ifBlank { rule.merchant },
                        detail = rule.merchant
                            .takeUnless { it.equals(rule.displayName, ignoreCase = true) }
                            .orEmpty(),
                        amount = rule.amount,
                        type = rule.type,
                        dueDate = occurrence.dueDate,
                        kind = BillKind.RECURRING,
                        cadence = rule.cadence.billLabelKey(),
                        isOverdue = occurrence.isOverdue,
                    )
                }
            }

        val pendingItems = pending
            .filter { it.categoryId == null }
            .map { tx ->
                BillItem(
                    id = "pending:${tx.id}",
                    title = tx.merchant,
                    detail = "",
                    amount = tx.amount,
                    type = tx.type,
                    dueDate = tx.date,
                    kind = BillKind.PENDING_REVIEW,
                    cadence = null,
                    isOverdue = tx.date < today,
                )
            }

        val items = (recurringItems + pendingItems)
            .sortedWith(compareBy<BillItem> { it.dueDate }.thenBy { it.kind.ordinal }.thenBy { it.title.lowercase() })

        val outgoingNext30 = Money.sumAmounts(
            items
                .filter { it.kind == BillKind.RECURRING }
                .filter { it.type == TxType.EXPENSE }
                .filter { it.isOverdue || it.dueDate in today..next30End }
                .map { it.amount },
            currency,
        )

        return BillsState(
            today = today,
            horizonEnd = horizonEnd,
            outgoingNext30Days = outgoingNext30,
            calendarDays = buildCalendarDays(today, items).toImmutableList(),
            items = items.toImmutableList(),
            remindersEnabled = remindersEnabled,
            isLoading = false,
        )
    }

    private fun buildCalendarDays(today: LocalDate, items: List<BillItem>): List<BillCalendarDay> =
        (0 until CALENDAR_DAYS).map { offset ->
            val date = today.plus(DatePeriod(days = offset))
            val dueItems = if (offset == 0) {
                items.filter { it.dueDate <= date }
            } else {
                items.filter { it.dueDate == date }
            }
            BillCalendarDay(
                date = date,
                count = dueItems.size,
                hasOverdue = dueItems.any { it.isOverdue },
            )
        }

    companion object {
        private const val HORIZON_DAYS = 60
        private const val TOTAL_DAYS = 30
        private const val CALENDAR_DAYS = 14
    }
}
