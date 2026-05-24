package com.athar.feature.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.athar.core.common.time.HijriDate
import com.athar.core.designsystem.display.LocalHijriEnabled
import com.athar.core.domain.model.Transaction
import com.athar.core.designsystem.component.AtharCard
import com.athar.core.designsystem.component.AtharEmptyState
import com.athar.core.designsystem.component.AtharFab
import com.athar.core.designsystem.component.AtharListRow
import com.athar.core.designsystem.component.AtharNumber
import com.athar.core.designsystem.component.AtharSwipeRow
import com.athar.core.designsystem.component.AtharText
import com.athar.core.designsystem.theme.AtharTheme

@Composable
fun TodayScreen(
    modifier: Modifier = Modifier,
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showAddSheet by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Transaction?>(null) }

    TodayContent(
        state = state,
        onEvent = { event ->
            when (event) {
                is TodayEvent.AddManual -> showAddSheet = true
                is TodayEvent.OpenTransaction -> {
                    editing = state.recent.firstOrNull { it.id == event.id }
                        ?: state.pending.firstOrNull { it.id == event.id }
                }
                else -> viewModel.onEvent(event)
            }
        },
        modifier = modifier,
    )

    if (showAddSheet) {
        AddTransactionSheet(
            onDismiss = { showAddSheet = false },
            onSaved = { showAddSheet = false },
        )
    }

    editing?.let { tx ->
        EditTransactionSheet(
            transaction = tx,
            onDismiss = { editing = null },
            onSave = { updated, learnRule ->
                viewModel.updateTransaction(updated, learnRule)
                editing = null
            },
            onDelete = { id ->
                viewModel.deleteTransaction(id)
                editing = null
            },
        )
    }
}

@Composable
internal fun TodayContent(
    state: TodayState,
    onEvent: (TodayEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = AtharTheme
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(theme.colors.parchment),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(theme.spacing.m),
            verticalArrangement = Arrangement.spacedBy(theme.spacing.l),
        ) {
            Header(state = state)
            if (state.pending.isNotEmpty()) {
                PendingTray(state = state, onEvent = onEvent)
            }
            RecentList(state = state, onEvent = onEvent)
        }

        AtharFab(
            onClick = { onEvent(TodayEvent.AddManual) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(theme.spacing.l),
        )
    }
}

@Composable
private fun Header(state: TodayState) {
    val theme = AtharTheme
    Column(
        verticalArrangement = Arrangement.spacedBy(theme.spacing.s),
        horizontalAlignment = Alignment.Start,
    ) {
        AtharText(
            text = "اليوم",
            style = theme.typography.overline,
            color = theme.colors.muted,
        )
        AtharNumber(money = state.netFlow, landmark = true)
        val hijriOn = LocalHijriEnabled.current
        val gregorian = "${state.month.year}/${state.month.monthValue}"
        val tail = if (hijriOn) " · ${HijriDate.formatYearMonth(state.month.year, state.month.monthValue)} هـ" else ""
        AtharText(
            text = "صافي الشهر · $gregorian$tail",
            style = theme.typography.caption,
            color = theme.colors.muted,
        )
    }
}

@Composable
private fun PendingTray(
    state: TodayState,
    onEvent: (TodayEvent) -> Unit,
) {
    val theme = AtharTheme
    AtharCard {
        AtharText(
            text = "بانتظار التأكيد · ${state.pending.size}",
            style = theme.typography.headline,
        )
        AtharText(
            text = "اسحب للتأكيد أو التجاهل",
            style = theme.typography.caption,
            color = theme.colors.muted,
        )
        state.pending.forEach { tx ->
            AtharSwipeRow(
                onConfirm = { onEvent(TodayEvent.ConfirmPending(tx.id)) },
                onDismiss = { onEvent(TodayEvent.DismissPending(tx.id)) },
            ) {
                AtharListRow(
                    title = tx.merchant,
                    subtitle = tx.categoryId ?: "غير مصنف",
                    trailing = tx.amount,
                    onClick = { onEvent(TodayEvent.OpenTransaction(tx.id)) },
                )
            }
        }
    }
}

@Composable
private fun RecentList(
    state: TodayState,
    onEvent: (TodayEvent) -> Unit,
) {
    val theme = AtharTheme
    if (state.recent.isEmpty()) {
        AtharEmptyState(
            text = "لا حركات هذا الشهر.",
            subtle = "أضف حركة بالـ + أو فعّل قراءة الرسائل من الإعدادات.",
        )
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(theme.spacing.xs)) {
            items(state.recent, key = { it.id }) { tx ->
                AtharListRow(
                    modifier = Modifier.animateItem(),
                    title = tx.merchant,
                    subtitle = tx.categoryId ?: "غير مصنف",
                    trailing = tx.amount,
                    onClick = { onEvent(TodayEvent.OpenTransaction(tx.id)) },
                )
            }
        }
    }
}
