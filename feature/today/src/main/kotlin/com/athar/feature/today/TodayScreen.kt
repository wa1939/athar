package com.athar.feature.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
import kotlinx.collections.immutable.ImmutableMap
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

@Composable
fun TodayScreen(
    onOpenHistory: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val backfill by viewModel.lastBackfill.collectAsStateWithLifecycle()
    val pendingSuggestionApply by viewModel.lastPendingSuggestionApply.collectAsStateWithLifecycle()
    var showAddSheet by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Transaction?>(null) }

    LaunchedEffect(backfill) {
        if (backfill != null) {
            kotlinx.coroutines.delay(4_000)
            viewModel.clearBackfill()
        }
    }

    LaunchedEffect(pendingSuggestionApply) {
        if (pendingSuggestionApply != null) {
            kotlinx.coroutines.delay(4_000)
            viewModel.clearPendingSuggestionApply()
        }
    }

    TodayContent(
        state = state,
        backfill = backfill,
        pendingSuggestionApply = pendingSuggestionApply,
        onClearBackfill = viewModel::clearBackfill,
        onClearPendingSuggestionApply = viewModel::clearPendingSuggestionApply,
        onEvent = { event ->
            when (event) {
                is TodayEvent.AddManual -> showAddSheet = true
                is TodayEvent.OpenHistory -> onOpenHistory()
                is TodayEvent.OpenTransaction -> {
                    editing = state.today.firstOrNull { it.id == event.id }
                        ?: state.recent.firstOrNull { it.id == event.id }
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
        key(tx.id) {
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
}

@Composable
internal fun TodayContent(
    state: TodayState,
    onEvent: (TodayEvent) -> Unit,
    modifier: Modifier = Modifier,
    backfill: TodayViewModel.BackfillEvent? = null,
    pendingSuggestionApply: TodayViewModel.PendingSuggestionApplyEvent? = null,
    onClearBackfill: () -> Unit = {},
    onClearPendingSuggestionApply: () -> Unit = {},
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
            backfill?.let {
                CategoryBackfillToast(
                    pattern = it.pattern,
                    count = it.count,
                    onDismiss = onClearBackfill,
                )
            }
            pendingSuggestionApply?.let {
                PendingSuggestionApplyToast(
                    count = it.count,
                    onDismiss = onClearPendingSuggestionApply,
                )
            }
            if (state.pending.isNotEmpty()) {
                PendingAttentionBanner(
                    count = state.pending.size,
                    onClick = { onEvent(TodayEvent.OpenHistory) },
                )
            }
            if (state.dismissedToday.isNotEmpty()) {
                DismissedAttentionBanner(
                    count = state.dismissedToday.size,
                    onClick = { onEvent(TodayEvent.OpenHistory) },
                )
            }
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
private fun PendingAttentionBanner(count: Int, onClick: () -> Unit) {
    val theme = AtharTheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(theme.spacing.s))
            .background(theme.colors.ember)
            .clickable(onClick = onClick)
            .padding(horizontal = theme.spacing.m, vertical = theme.spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(theme.spacing.s),
    ) {
        AtharText(
            text = stringResource(R.string.today_pending_banner, count),
            style = theme.typography.headline,
            color = theme.colors.parchment,
            modifier = Modifier.weight(1f),
        )
        AtharText(
            text = stringResource(R.string.today_pending_banner_cta),
            style = theme.typography.caption,
            color = theme.colors.parchment,
        )
    }
}

@Composable
private fun DismissedAttentionBanner(count: Int, onClick: () -> Unit) {
    val theme = AtharTheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(theme.spacing.s))
            .background(theme.colors.dust)
            .clickable(onClick = onClick)
            .padding(horizontal = theme.spacing.m, vertical = theme.spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(theme.spacing.s),
    ) {
        AtharText(
            text = stringResource(R.string.today_dismissed_banner, count),
            style = theme.typography.body,
            color = theme.colors.parchment,
            modifier = Modifier.weight(1f),
        )
        AtharText(
            text = stringResource(R.string.today_dismissed_banner_cta),
            style = theme.typography.caption,
            color = theme.colors.parchment,
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
            text = stringResource(R.string.today_header_title),
            style = theme.typography.overline,
            color = theme.colors.muted,
        )
        // Landmark is *today's* net so the "اليوم" header isn't misleading. The month total
        // moves to a sub-row below alongside Gregorian/Hijri month label.
        AtharNumber(money = state.todayNet, landmark = true)
        val hijriOn = LocalHijriEnabled.current
        val gregorian = "${state.month.year}/${state.month.monthValue}"
        val landmarkCaption = if (hijriOn) {
            stringResource(
                R.string.today_caption_landmark_today_hijri,
                HijriDate.formatYearMonth(state.month.year, state.month.monthValue),
            )
        } else {
            stringResource(R.string.today_caption_landmark_today)
        }
        AtharText(
            text = landmarkCaption,
            style = theme.typography.caption,
            color = theme.colors.muted,
        )
        // Secondary row: month-net summary (what the landmark used to be).
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(theme.spacing.s),
        ) {
            AtharText(
                text = stringResource(R.string.today_caption_net_flow_month, gregorian),
                style = theme.typography.caption,
                color = theme.colors.muted,
            )
            AtharNumber(money = state.netFlow, color = theme.colors.muted)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = theme.spacing.s),
            horizontalArrangement = Arrangement.spacedBy(theme.spacing.s),
        ) {
            FlowPill(label = stringResource(R.string.today_pill_income), money = state.totalIncome, accent = theme.colors.olive, modifier = Modifier.weight(1f))
            FlowPill(label = stringResource(R.string.today_pill_expense), money = state.totalExpense, accent = theme.colors.ember, modifier = Modifier.weight(1f))
            FlowPill(label = stringResource(R.string.today_pill_net_worth), money = state.netWorth, accent = theme.colors.ink, modifier = Modifier.weight(1f))
        }
        val savingsRate = state.savingsRate
        if (savingsRate != null) {
            AtharText(
                text = stringResource(R.string.today_caption_savings_rate, "%.0f".format(savingsRate)),
                style = theme.typography.caption,
                color = if (savingsRate >= 0) theme.colors.olive else theme.colors.ember,
            )
        }
        if (state.netWorthIsMixed) {
            AtharText(
                text = stringResource(R.string.today_caption_net_worth_mixed),
                style = theme.typography.caption,
                color = theme.colors.muted,
            )
        }
        state.goalNudge?.let { GoalsNudge(it) }
    }
}

@Composable
private fun GoalsNudge(nudge: TodayGoalNudge) {
    val theme = AtharTheme
    AtharCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(theme.spacing.xs)) {
            AtharText(
                text = stringResource(R.string.today_goals_nudge_title),
                style = theme.typography.overline,
                color = theme.colors.muted,
            )
            GoalNudgeLine(
                text = savingsNudgeText(nudge),
                color = when {
                    nudge.savingsRatePercent == null -> theme.colors.muted
                    nudge.savingsRateProgress >= 1f -> theme.colors.olive
                    else -> theme.colors.ember
                },
            )
            GoalNudgeLine(
                text = emergencyNudgeText(nudge),
                color = when {
                    nudge.emergencyMonthsCovered == null -> theme.colors.muted
                    nudge.emergencyFundProgress >= 1f -> theme.colors.olive
                    else -> theme.colors.ember
                },
            )
        }
    }
}

@Composable
private fun GoalNudgeLine(text: String, color: Color) {
    val theme = AtharTheme
    AtharText(text = text, style = theme.typography.caption, color = color)
}

@Composable
private fun savingsNudgeText(nudge: TodayGoalNudge): String {
    val actual = nudge.savingsRatePercent ?: return stringResource(R.string.today_goals_savings_waiting)
    val actualLabel = actual.format(scale = 0)
    return if (nudge.savingsRateProgress >= 1f) {
        stringResource(R.string.today_goals_savings_on_track, actualLabel, nudge.savingsRateTargetPercent)
    } else {
        stringResource(R.string.today_goals_savings_below, actualLabel, nudge.savingsRateTargetPercent)
    }
}

@Composable
private fun emergencyNudgeText(nudge: TodayGoalNudge): String {
    val actual = nudge.emergencyMonthsCovered ?: return stringResource(R.string.today_goals_emergency_waiting)
    val actualLabel = actual.format(scale = 1)
    return if (nudge.emergencyFundProgress >= 1f) {
        stringResource(R.string.today_goals_emergency_on_track, actualLabel, nudge.emergencyFundTargetMonths)
    } else {
        stringResource(R.string.today_goals_emergency_below, actualLabel, nudge.emergencyFundTargetMonths)
    }
}

private fun BigDecimal.format(scale: Int): String =
    setScale(scale, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

@Composable
private fun FlowPill(
    label: String,
    money: com.athar.core.common.money.Money,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val theme = AtharTheme
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(theme.spacing.s))
            .background(theme.colors.surface)
            .padding(theme.spacing.s),
        verticalArrangement = Arrangement.spacedBy(theme.spacing.xs),
    ) {
        AtharText(text = label, style = theme.typography.caption, color = theme.colors.muted)
        AtharNumber(money = money, color = accent)
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
            text = stringResource(R.string.today_pending_title, state.pending.size),
            style = theme.typography.headline,
        )
        AtharText(
            text = stringResource(R.string.today_pending_subtle),
            style = theme.typography.caption,
            color = theme.colors.muted,
        )

        if (state.pendingCategorySuggestions.isNotEmpty()) {
            PendingSuggestionsBulkAction(
                count = state.pendingCategorySuggestions.size,
                onClick = { onEvent(TodayEvent.BulkApplyPendingCategorySuggestions) },
            )
        }

        if (state.pending.size >= 5) {
            BulkActionsBar(onEvent = onEvent)
        }

        state.pending.take(MAX_PENDING_VISIBLE).forEach { tx ->
            AtharSwipeRow(
                onConfirm = { onEvent(TodayEvent.ConfirmPending(tx.id)) },
                onDismiss = { onEvent(TodayEvent.DismissPending(tx.id)) },
            ) {
                PendingTransactionRow(
                    tx = tx,
                    suggestion = state.pendingCategorySuggestions[tx.id],
                    categoryLabels = state.categoryLabels,
                    onOpen = { onEvent(TodayEvent.OpenTransaction(tx.id)) },
                    onApplySuggestion = { onEvent(TodayEvent.ApplyPendingCategorySuggestion(tx.id)) },
                )
            }
        }
        if (state.pending.size > MAX_PENDING_VISIBLE) {
            AtharText(
                text = stringResource(R.string.today_pending_more, state.pending.size - MAX_PENDING_VISIBLE),
                style = theme.typography.caption,
                color = theme.colors.muted,
            )
        }
    }
}

@Composable
private fun PendingSuggestionApplyToast(
    count: Int,
    onDismiss: () -> Unit,
) {
    val theme = AtharTheme
    AtharCard(modifier = Modifier.clickable(onClick = onDismiss)) {
        AtharText(
            text = stringResource(R.string.today_pending_suggestions_applied, count),
            style = theme.typography.body,
            color = theme.colors.olive,
        )
    }
}

@Composable
private fun PendingSuggestionsBulkAction(
    count: Int,
    onClick: () -> Unit,
) {
    val theme = AtharTheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = theme.spacing.xs)
            .clip(RoundedCornerShape(theme.spacing.s))
            .background(theme.colors.olive)
            .clickable(onClick = onClick)
            .padding(horizontal = theme.spacing.m, vertical = theme.spacing.s),
        contentAlignment = Alignment.Center,
    ) {
        AtharText(
            text = stringResource(R.string.today_pending_apply_safe_suggestions, count),
            style = theme.typography.caption,
            color = theme.colors.parchment,
        )
    }
}

@Composable
private fun PendingTransactionRow(
    tx: Transaction,
    suggestion: PendingCategorySuggestion?,
    categoryLabels: ImmutableMap<String, CategoryLabel>,
    onOpen: () -> Unit,
    onApplySuggestion: () -> Unit,
) {
    val theme = AtharTheme
    Column {
        AtharListRow(
            title = tx.merchant,
            subtitle = transactionCategoryLabel(tx, categoryLabels),
            trailing = tx.amount,
            onClick = onOpen,
        )
        if (suggestion != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = theme.spacing.m,
                        end = theme.spacing.m,
                        bottom = theme.spacing.s,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(theme.spacing.s),
            ) {
                AtharText(
                    text = stringResource(
                        R.string.today_pending_category_suggestion,
                        categoryLabelText(suggestion.categoryId, categoryLabels),
                        suggestion.useCount,
                    ),
                    style = theme.typography.caption,
                    color = theme.colors.muted,
                    modifier = Modifier.weight(1f),
                )
                SuggestionButton(onClick = onApplySuggestion)
            }
        }
    }
}

@Composable
private fun SuggestionButton(onClick: () -> Unit) {
    val theme = AtharTheme
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(theme.spacing.s))
            .background(theme.colors.olive)
            .clickable(onClick = onClick)
            .padding(horizontal = theme.spacing.m, vertical = theme.spacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        AtharText(
            text = stringResource(R.string.today_pending_apply_suggestion),
            style = theme.typography.caption,
            color = theme.colors.parchment,
        )
    }
}

private fun categoryLabelText(
    categoryId: String,
    labels: ImmutableMap<String, CategoryLabel>,
): String {
    val label = labels[categoryId] ?: return categoryId
    val isArabic = Locale.getDefault().language == "ar"
    return if (isArabic) {
        label.nameAr.ifBlank { label.name }
    } else {
        label.name.ifBlank { label.nameAr }
    }
}

@Composable
private fun BulkActionsBar(onEvent: (TodayEvent) -> Unit) {
    val theme = AtharTheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = theme.spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(theme.spacing.s),
    ) {
        BulkButton(
            text = stringResource(R.string.today_bulk_confirm_confident),
            background = theme.colors.olive,
            onClick = { onEvent(TodayEvent.BulkConfirmConfident) },
            modifier = Modifier.weight(1f),
        )
        BulkButton(
            text = stringResource(R.string.today_bulk_dismiss_low),
            background = theme.colors.dust,
            onClick = { onEvent(TodayEvent.BulkDismissLowConfidence) },
            modifier = Modifier.weight(1f),
        )
        BulkButton(
            text = stringResource(R.string.today_bulk_dismiss_all),
            background = theme.colors.crimson,
            onClick = { onEvent(TodayEvent.BulkDismissAll) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.BulkButton(
    text: String,
    background: Color,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val theme = AtharTheme
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(theme.spacing.s))
            .background(background)
            .clickable(onClick = onClick)
            .padding(theme.spacing.s),
        contentAlignment = Alignment.Center,
    ) {
        AtharText(text = text, style = theme.typography.caption, color = theme.colors.parchment)
    }
}

private const val MAX_PENDING_VISIBLE = 12

@Composable
private fun RecentList(
    state: TodayState,
    onEvent: (TodayEvent) -> Unit,
) {
    val theme = AtharTheme
    if (state.today.isEmpty() && state.recent.isEmpty()) {
        AtharEmptyState(
            text = stringResource(R.string.today_empty_text),
            subtle = stringResource(R.string.today_empty_subtle),
        )
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(theme.spacing.xs)) {
        item(key = "today-header") {
            Column(verticalArrangement = Arrangement.spacedBy(theme.spacing.xs)) {
                AtharText(
                    text = stringResource(R.string.today_section_today),
                    style = theme.typography.overline,
                    color = theme.colors.muted,
                )
                if (state.today.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(theme.spacing.xs)) {
                        AtharText(
                            text = stringResource(R.string.today_section_today_net_prefix),
                            style = theme.typography.caption,
                            color = theme.colors.muted,
                        )
                        AtharNumber(
                            money = state.todayNet,
                            color = if (state.todayNet.amount.signum() >= 0) theme.colors.olive else theme.colors.ember,
                        )
                    }
                }
            }
        }
        if (state.today.isEmpty()) {
            item(key = "today-empty") {
                AtharText(
                    text = stringResource(R.string.today_no_today_transactions),
                    style = theme.typography.body,
                    color = theme.colors.muted,
                    modifier = Modifier.padding(vertical = theme.spacing.s),
                )
            }
        } else {
            items(state.today, key = { "today-${it.id}" }) { tx ->
                AtharListRow(
                    modifier = Modifier.animateItem(),
                    title = tx.merchant,
                    subtitle = transactionCategoryLabel(tx, state.categoryLabels),
                    trailing = tx.amount,
                    onClick = { onEvent(TodayEvent.OpenTransaction(tx.id)) },
                )
            }
        }
        if (state.recent.isNotEmpty()) {
            item(key = "recent-header") {
                AtharText(
                    text = stringResource(R.string.today_section_recent),
                    style = theme.typography.overline,
                    color = theme.colors.muted,
                    modifier = Modifier.padding(top = theme.spacing.m),
                )
            }
            items(state.recent, key = { "recent-${it.id}" }) { tx ->
                AtharListRow(
                    modifier = Modifier.animateItem(),
                    title = tx.merchant,
                    subtitle = transactionCategoryLabel(tx, state.categoryLabels),
                    trailing = tx.amount,
                    onClick = { onEvent(TodayEvent.OpenTransaction(tx.id)) },
                )
            }
        }
    }
}
