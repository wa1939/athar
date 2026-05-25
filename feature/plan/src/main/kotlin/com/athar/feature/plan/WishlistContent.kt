package com.athar.feature.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.athar.core.common.money.Money
import com.athar.core.designsystem.component.AtharAmountField
import com.athar.core.designsystem.component.AtharCard
import com.athar.core.designsystem.component.AtharEmptyState
import com.athar.core.designsystem.component.AtharNumber
import com.athar.core.designsystem.component.AtharText
import com.athar.core.designsystem.component.AtharTextField
import com.athar.core.designsystem.theme.AtharTheme
import com.athar.core.designsystem.theme.MinTouchTarget
import com.athar.core.domain.model.WishlistItem
import com.athar.core.domain.model.WishlistStatus
import java.math.BigDecimal
import java.time.YearMonth
import java.util.UUID

@Composable
fun WishlistContent(viewModel: WishlistViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val theme = AtharTheme
    var editing by remember { mutableStateOf<WishlistItem?>(null) }
    var creating by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(theme.spacing.l)) {
        CapacityCard(state = state, onAdd = { creating = true })
        if (state.items.isEmpty() && !state.isLoading) {
            AtharEmptyState(
                text = stringResource(R.string.plan_wishlist_empty_text),
                subtle = stringResource(R.string.plan_wishlist_empty_subtle),
            )
        } else {
            // Parent PlanScreen already provides a Column(verticalScroll); a LazyColumn here
            // would receive infinite-height constraints and crash with IllegalStateException
            // ("Vertically scrollable component was measured with an infinity maximum height").
            // Wishlists are short by nature (typically <20 items) — non-virtualized rendering
            // is cheaper than restructuring the screen into a single LazyColumn.
            Column(verticalArrangement = Arrangement.spacedBy(theme.spacing.xs)) {
                state.items.forEach { row ->
                    WishlistRowView(row = row, onClick = { editing = row.item })
                }
            }
        }
    }

    if (creating) {
        WishlistEditor(
            initial = null,
            onSave = {
                viewModel.onEvent(WishlistEvent.Save(it))
                creating = false
            },
            onDismiss = { creating = false },
            onDelete = null,
        )
    }
    editing?.let { item ->
        WishlistEditor(
            initial = item,
            onSave = {
                viewModel.onEvent(WishlistEvent.Save(it))
                editing = null
            },
            onDelete = {
                viewModel.onEvent(WishlistEvent.Delete(item.id))
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun CapacityCard(state: WishlistState, onAdd: () -> Unit) {
    val theme = AtharTheme
    AtharCard {
        Column(verticalArrangement = Arrangement.spacedBy(theme.spacing.s)) {
            AtharText(
                text = stringResource(R.string.plan_wishlist_capacity_title),
                style = theme.typography.caption,
                color = theme.colors.muted,
            )
            AtharNumber(money = state.monthlyCapacity)
            AtharText(
                text = stringResource(R.string.plan_wishlist_capacity_subtitle),
                style = theme.typography.caption,
                color = theme.colors.muted,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(theme.spacing.s))
                    .background(theme.colors.ember)
                    .clickable(onClick = onAdd)
                    .padding(theme.spacing.m),
                contentAlignment = Alignment.Center,
            ) {
                AtharText(text = stringResource(R.string.plan_wishlist_add), style = theme.typography.headline, color = theme.colors.parchment)
            }
        }
    }
}

@Composable
private fun WishlistRowView(row: WishlistRow, onClick: () -> Unit) {
    val theme = AtharTheme
    AtharCard(modifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                AtharText(text = row.item.name, style = theme.typography.headline)
                AtharText(
                    text = statusLabel(row),
                    style = theme.typography.caption,
                    color = statusColor(row),
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                AtharNumber(money = row.item.cost)
                if (!row.item.currentSaved.isZero()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AtharText(text = stringResource(R.string.plan_wishlist_saved_label), style = theme.typography.caption, color = theme.colors.muted)
                        AtharNumber(money = row.item.currentSaved, color = theme.colors.olive)
                    }
                }
            }
        }
    }
}

@Composable
private fun statusColor(row: WishlistRow): androidx.compose.ui.graphics.Color {
    val t = AtharTheme
    return when (row.status) {
        WishlistStatus.Now -> t.colors.olive
        is WishlistStatus.WaitUntil -> t.colors.dust
        WishlistStatus.Infeasible -> t.colors.muted
    }
}

@Composable
private fun statusLabel(row: WishlistRow): String = when (val s = row.status) {
    WishlistStatus.Now -> stringResource(R.string.plan_wishlist_status_now)
    is WishlistStatus.WaitUntil -> stringResource(
        R.string.plan_wishlist_status_wait_until,
        row.monthsNeeded ?: 0,
        "${s.month.year}/${s.month.monthValue}",
    )
    WishlistStatus.Infeasible -> stringResource(R.string.plan_wishlist_status_infeasible)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WishlistEditor(
    initial: WishlistItem?,
    onSave: (WishlistItem) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val theme = AtharTheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember(initial?.id) { mutableStateOf(initial?.name ?: "") }
    var cost by remember(initial?.id) { mutableStateOf(initial?.cost?.amount?.toPlainString() ?: "") }
    var saved by remember(initial?.id) { mutableStateOf(initial?.currentSaved?.amount?.toPlainString() ?: "0") }
    var notes by remember(initial?.id) { mutableStateOf(initial?.notes.orEmpty()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = theme.colors.parchment,
        contentColor = theme.colors.ink,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(theme.spacing.m),
            verticalArrangement = Arrangement.spacedBy(theme.spacing.m),
        ) {
            AtharText(
                text = if (initial == null) stringResource(R.string.plan_wishlist_editor_title_new) else stringResource(R.string.plan_wishlist_editor_title_edit),
                style = theme.typography.headline,
            )
            AtharTextField(
                value = name,
                onValueChange = { name = it },
                label = stringResource(R.string.plan_wishlist_label_name),
                modifier = Modifier.fillMaxWidth(),
            )
            AtharAmountField(
                value = cost,
                onValueChange = { cost = it },
                label = stringResource(R.string.plan_wishlist_label_cost),
                modifier = Modifier.fillMaxWidth(),
            )
            AtharAmountField(
                value = saved,
                onValueChange = { saved = it },
                label = stringResource(R.string.plan_wishlist_label_saved),
                modifier = Modifier.fillMaxWidth(),
            )
            AtharTextField(
                value = notes,
                onValueChange = { notes = it },
                label = stringResource(R.string.plan_wishlist_label_notes),
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(theme.spacing.s),
            ) {
                if (onDelete != null) {
                    SheetButton(
                        text = stringResource(R.string.plan_wishlist_action_delete),
                        background = theme.colors.crimson,
                        textColor = theme.colors.parchment,
                        onClick = onDelete,
                        modifier = Modifier.weight(1f),
                    )
                }
                SheetButton(
                    text = stringResource(R.string.plan_wishlist_action_save),
                    background = theme.colors.ember,
                    textColor = theme.colors.parchment,
                    onClick = {
                        val parsedCost = runCatching { BigDecimal(cost) }.getOrNull()
                        val parsedSaved = runCatching { BigDecimal(saved) }.getOrNull() ?: BigDecimal.ZERO
                        if (parsedCost != null && name.isNotBlank() && parsedCost.signum() > 0) {
                            val base = initial ?: WishlistItem(
                                id = UUID.randomUUID().toString(),
                                name = name,
                                cost = Money.of(parsedCost),
                                currentSaved = Money.of(parsedSaved),
                                desiredMonths = null,
                                startMonth = YearMonth.now(),
                                notes = null,
                            )
                            onSave(
                                base.copy(
                                    name = name.trim(),
                                    cost = Money.of(parsedCost),
                                    currentSaved = Money.of(parsedSaved),
                                    notes = notes.takeIf { it.isNotBlank() },
                                ),
                            )
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SheetButton(
    text: String,
    background: androidx.compose.ui.graphics.Color,
    textColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = AtharTheme
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(theme.spacing.s))
            .background(background)
            .clickable(onClick = onClick)
            .padding(theme.spacing.m),
        contentAlignment = Alignment.Center,
    ) {
        AtharText(text = text, style = theme.typography.headline, color = textColor)
    }
}
