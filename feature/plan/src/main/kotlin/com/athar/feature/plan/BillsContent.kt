package com.athar.feature.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.athar.core.designsystem.component.AtharCard
import com.athar.core.designsystem.component.AtharEmptyState
import com.athar.core.designsystem.component.AtharNumber
import com.athar.core.designsystem.component.AtharText
import com.athar.core.designsystem.theme.AtharTheme
import com.athar.core.designsystem.theme.MinTouchTarget
import com.athar.core.domain.model.TxType
import kotlinx.datetime.LocalDate
import java.time.temporal.ChronoUnit

@Composable
fun BillsContent(viewModel: BillsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val theme = AtharTheme

    Column(verticalArrangement = Arrangement.spacedBy(theme.spacing.l)) {
        BillsSummaryCard(state = state)
        BillsCalendarStrip(days = state.calendarDays)
        if (state.items.isEmpty() && !state.isLoading) {
            AtharEmptyState(
                text = stringResource(R.string.plan_bills_empty_text),
                subtle = stringResource(R.string.plan_bills_empty_subtle),
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(theme.spacing.xs)) {
                state.items.forEach { item ->
                    BillRow(item = item, today = state.today)
                }
            }
        }
    }
}

@Composable
private fun BillsSummaryCard(state: BillsState) {
    val theme = AtharTheme
    AtharCard {
        Column(verticalArrangement = Arrangement.spacedBy(theme.spacing.s)) {
            AtharText(
                text = stringResource(R.string.plan_bills_summary_title),
                style = theme.typography.caption,
                color = theme.colors.muted,
            )
            AtharNumber(money = state.outgoingNext30Days, color = theme.colors.ember)
            AtharText(
                text = stringResource(
                    R.string.plan_bills_summary_subtitle,
                    state.items.size,
                    state.horizonEnd?.toString().orEmpty(),
                ),
                style = theme.typography.caption,
                color = theme.colors.muted,
            )
        }
    }
}

@Composable
private fun BillsCalendarStrip(days: List<BillCalendarDay>) {
    if (days.isEmpty()) return
    val theme = AtharTheme
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(theme.spacing.s),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(
            items = days,
            key = { it.date.toString() },
        ) { day ->
            CalendarDayChip(day = day)
        }
    }
}

@Composable
private fun CalendarDayChip(day: BillCalendarDay) {
    val theme = AtharTheme
    val hasBills = day.count > 0
    Column(
        modifier = Modifier
            .width(52.dp)
            .heightIn(min = MinTouchTarget)
            .clip(RoundedCornerShape(theme.spacing.s))
            .background(if (hasBills) theme.colors.surface else theme.colors.divider)
            .padding(vertical = theme.spacing.s),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        AtharText(
            text = day.date.dayOfMonth.toString(),
            style = theme.typography.body,
            color = if (hasBills) theme.colors.ink else theme.colors.muted,
        )
        AtharText(
            text = day.date.monthNumber.toString(),
            style = theme.typography.caption,
            color = theme.colors.muted,
        )
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(
                    when {
                        day.hasOverdue -> theme.colors.crimson
                        hasBills -> theme.colors.ember
                        else -> theme.colors.divider
                    },
                ),
        )
    }
}

@Composable
private fun BillRow(item: BillItem, today: LocalDate?) {
    val theme = AtharTheme
    AtharCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(theme.spacing.m),
        ) {
            DateBadge(item = item, today = today)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                AtharText(
                    text = item.title.ifBlank { stringResource(R.string.plan_bills_unknown_title) },
                    style = theme.typography.headline,
                    color = theme.colors.ink,
                )
                AtharText(
                    text = itemSubtitle(item),
                    style = theme.typography.caption,
                    color = if (item.isOverdue) theme.colors.crimson else theme.colors.muted,
                )
            }
            AtharNumber(
                money = item.amount,
                color = if (item.type == TxType.INCOME) theme.colors.olive else theme.colors.ember,
            )
        }
    }
}

@Composable
private fun DateBadge(item: BillItem, today: LocalDate?) {
    val theme = AtharTheme
    Column(
        modifier = Modifier
            .width(58.dp)
            .height(MinTouchTarget)
            .clip(RoundedCornerShape(theme.spacing.s))
            .background(if (item.isOverdue) theme.colors.crimson else theme.colors.divider),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AtharText(
            text = item.dueDate.dayOfMonth.toString(),
            style = theme.typography.body,
            color = if (item.isOverdue) theme.colors.parchment else theme.colors.ink,
        )
        AtharText(
            text = dateStatusLabel(item.dueDate, today, item.isOverdue),
            style = theme.typography.caption,
            color = if (item.isOverdue) theme.colors.parchment else theme.colors.muted,
            maxLines = 1,
        )
    }
}

@Composable
private fun itemSubtitle(item: BillItem): String {
    val source = when (item.kind) {
        BillKind.RECURRING -> item.cadence?.let { cadenceLabel(it) }.orEmpty()
        BillKind.PENDING_REVIEW -> stringResource(R.string.plan_bills_pending_review)
    }
    return listOfNotNull(
        source.takeIf { it.isNotBlank() },
        item.detail.takeIf { it.isNotBlank() },
    ).joinToString(" · ")
}

@Composable
private fun cadenceLabel(cadence: BillCadenceLabel): String = when (cadence) {
    BillCadenceLabel.MONTHLY -> stringResource(R.string.plan_bills_cadence_monthly)
    BillCadenceLabel.WEEKLY -> stringResource(R.string.plan_bills_cadence_weekly)
    BillCadenceLabel.YEARLY -> stringResource(R.string.plan_bills_cadence_yearly)
}

@Composable
private fun dateStatusLabel(date: LocalDate, today: LocalDate?, isOverdue: Boolean): String {
    if (isOverdue) return stringResource(R.string.plan_bills_overdue)
    if (today == null) return date.monthNumber.toString()
    val days = ChronoUnit.DAYS.between(today.toJava(), date.toJava()).toInt()
    return when (days) {
        0 -> stringResource(R.string.plan_bills_today)
        in 1..6 -> stringResource(R.string.plan_bills_in_days_short, days)
        else -> date.monthNumber.toString()
    }
}

private fun LocalDate.toJava(): java.time.LocalDate =
    java.time.LocalDate.of(year, monthNumber, dayOfMonth)
