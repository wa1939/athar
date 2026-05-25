package com.athar.feature.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import com.athar.core.designsystem.component.AtharTextField
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.athar.core.designsystem.component.AtharCard
import com.athar.core.designsystem.component.AtharEmptyState
import com.athar.core.designsystem.component.AtharNumber
import com.athar.core.designsystem.component.AtharSegment
import com.athar.core.designsystem.component.AtharSegmentedControl
import com.athar.core.designsystem.component.AtharText
import com.athar.core.designsystem.theme.AtharTheme
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

enum class PlanTab { BUDGET, WISHLIST, INVESTMENTS }

@Composable
fun PlanScreen(
    modifier: Modifier = Modifier,
    viewModel: PlanViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<BudgetRow?>(null) }
    var tab by remember { mutableStateOf(PlanTab.BUDGET) }
    val theme = AtharTheme

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(theme.colors.parchment),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(theme.spacing.m),
            verticalArrangement = Arrangement.spacedBy(theme.spacing.l),
        ) {
            AtharText(text = stringResource(R.string.plan_header_title), style = theme.typography.overline, color = theme.colors.muted)
            AtharSegmentedControl(
                segments = listOf(
                    AtharSegment(PlanTab.BUDGET, stringResource(R.string.plan_tab_budget)),
                    AtharSegment(PlanTab.WISHLIST, stringResource(R.string.plan_tab_wishlist)),
                    AtharSegment(PlanTab.INVESTMENTS, stringResource(R.string.plan_tab_investments)),
                ),
                selected = tab,
                onSelect = { tab = it },
            )
            when (tab) {
                PlanTab.BUDGET -> BudgetContent(
                    state = state,
                    onClickRow = { editing = it },
                    onEvent = viewModel::onEvent,
                )
                PlanTab.WISHLIST -> WishlistContent()
                PlanTab.INVESTMENTS -> InvestmentsContent()
            }
        }
    }

    editing?.let { row ->
        TargetEditor(
            row = row,
            onDismiss = { editing = null },
            onSave = { newTargetMinor ->
                viewModel.onEvent(PlanEvent.SaveTarget(row.category.id, newTargetMinor))
                editing = null
            },
        )
    }
}

@Composable
private fun BudgetContent(
    state: PlanState,
    onClickRow: (BudgetRow) -> Unit,
    onEvent: (PlanEvent) -> Unit,
) {
    val theme = AtharTheme
    var showCustomRange by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(theme.spacing.l)) {
        Header(state = state)
        AtharSegmentedControl(
            segments = listOf(
                AtharSegment(PlanPeriodKey.MONTH, stringResource(R.string.plan_period_month)),
                AtharSegment(PlanPeriodKey.MONTHS_3, stringResource(R.string.plan_period_months_3)),
                AtharSegment(PlanPeriodKey.YEAR, stringResource(R.string.plan_period_year)),
                AtharSegment(PlanPeriodKey.CUSTOM, stringResource(R.string.plan_period_custom)),
            ),
            selected = state.periodKey,
            onSelect = {
                if (it == PlanPeriodKey.CUSTOM) showCustomRange = true
                else onEvent(PlanEvent.SelectPeriod(it))
            },
        )
        if (state.periodKey == PlanPeriodKey.CUSTOM && state.customStart != null && state.customEnd != null) {
            CustomRangeBanner(
                start = state.customStart,
                end = state.customEnd,
                onEdit = { showCustomRange = true },
            )
        }
        if (state.periodKey != PlanPeriodKey.MONTH) {
            AtharText(
                text = stringResource(
                    R.string.plan_target_caption_scaled,
                    "%.1f".format(state.targetMultiplier),
                ),
                style = theme.typography.caption,
                color = theme.colors.muted,
            )
        }
        LimitWarningStrip(state = state)
        if (state.rows.isEmpty() && !state.isLoading) {
            AtharEmptyState(
                text = stringResource(R.string.plan_empty_text),
                subtle = stringResource(R.string.plan_empty_subtle),
            )
        } else {
            state.rows.forEach { row ->
                BudgetRowView(row = row, onClick = { onClickRow(row) })
            }
        }
    }

    if (showCustomRange) {
        CustomRangePickerSheet(
            initialStart = state.customStart,
            initialEnd = state.customEnd,
            onDismiss = { showCustomRange = false },
            onConfirm = { s, e ->
                onEvent(PlanEvent.SelectCustomRange(s, e))
                showCustomRange = false
            },
        )
    }
}

@Composable
private fun CustomRangeBanner(start: LocalDate, end: LocalDate, onEdit: () -> Unit) {
    val theme = AtharTheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(theme.spacing.s))
            .background(theme.colors.surface)
            .clickable(onClick = onEdit)
            .padding(theme.spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AtharText(
            text = stringResource(
                R.string.plan_custom_range_banner,
                start.toString(),
                end.toString(),
            ),
            style = theme.typography.caption,
            color = theme.colors.muted,
            modifier = Modifier.weight(1f),
        )
        AtharText(text = stringResource(R.string.plan_custom_range_banner_edit), style = theme.typography.caption, color = theme.colors.ember)
    }
}

private enum class CustomRangeError { INVALID, ORDER }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomRangePickerSheet(
    initialStart: LocalDate?,
    initialEnd: LocalDate?,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, LocalDate) -> Unit,
) {
    val theme = AtharTheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var startText by remember { mutableStateOf(initialStart?.toString().orEmpty()) }
    var endText by remember { mutableStateOf(initialEnd?.toString().orEmpty()) }
    var error by remember { mutableStateOf<CustomRangeError?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = theme.colors.parchment,
        contentColor = theme.colors.ink,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(theme.spacing.m),
            verticalArrangement = Arrangement.spacedBy(theme.spacing.m),
        ) {
            AtharText(text = stringResource(R.string.plan_custom_range_title), style = theme.typography.headline)
            AtharText(
                text = stringResource(R.string.plan_custom_range_hint),
                style = theme.typography.caption,
                color = theme.colors.muted,
            )
            AtharTextField(
                value = startText,
                onValueChange = { startText = it; error = null },
                label = stringResource(R.string.plan_custom_range_from),
                modifier = Modifier.fillMaxWidth(),
            )
            AtharTextField(
                value = endText,
                onValueChange = { endText = it; error = null },
                label = stringResource(R.string.plan_custom_range_to),
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let {
                val message = when (it) {
                    CustomRangeError.INVALID -> stringResource(R.string.plan_custom_range_error_invalid)
                    CustomRangeError.ORDER -> stringResource(R.string.plan_custom_range_error_order)
                }
                AtharText(text = message, style = theme.typography.caption, color = theme.colors.ember)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(theme.spacing.s))
                    .background(theme.colors.ember)
                    .clickable {
                        val s = runCatching { LocalDate.parse(startText.trim()) }.getOrNull()
                        val e = runCatching { LocalDate.parse(endText.trim()) }.getOrNull()
                        when {
                            s == null || e == null -> error = CustomRangeError.INVALID
                            !(s <= e) -> error = CustomRangeError.ORDER
                            else -> onConfirm(s, e)
                        }
                    }
                    .padding(theme.spacing.m),
                contentAlignment = Alignment.Center,
            ) {
                AtharText(text = stringResource(R.string.plan_custom_range_apply), style = theme.typography.headline, color = theme.colors.parchment)
            }
        }
    }
}

@Composable
private fun LimitWarningStrip(state: PlanState) {
    val theme = AtharTheme
    val tight = state.rows.filter { it.limit == LimitState.Tight }
    val over = state.rows.filter { it.limit == LimitState.Over }
    if (tight.isEmpty() && over.isEmpty()) return
    val isArabic = java.util.Locale.getDefault().language == "ar"
    val separator = if (isArabic) "، " else ", "
    fun categoryLabel(row: BudgetRow): String = if (isArabic) row.category.nameAr else row.category.name
    val (message, color) = when {
        over.isNotEmpty() -> {
            val names = over.take(3).joinToString(separator) { categoryLabel(it) }
            val extra = if (over.size > 3) " (+${over.size - 3})" else ""
            "$names$extra${stringResource(R.string.plan_limit_warning_over_suffix)}" to theme.colors.ember
        }
        else -> {
            val names = tight.take(3).joinToString(separator) { categoryLabel(it) }
            val extra = if (tight.size > 3) " (+${tight.size - 3})" else ""
            "$names$extra${stringResource(R.string.plan_limit_warning_tight_suffix)}" to theme.colors.dust
        }
    }
    AtharCard {
        AtharText(text = message, style = theme.typography.body, color = color)
    }
}

@Composable
private fun Header(state: PlanState) {
    val theme = AtharTheme
    Column(verticalArrangement = Arrangement.spacedBy(theme.spacing.s)) {
        AtharText(text = stringResource(R.string.plan_header_title), style = theme.typography.overline, color = theme.colors.muted)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(theme.spacing.s)) {
            AtharNumber(money = state.totalActual)
            AtharText(text = stringResource(R.string.plan_budget_header_of), style = theme.typography.caption, color = theme.colors.muted)
            AtharNumber(money = state.totalTarget, color = theme.colors.muted)
        }
        AtharText(
            text = periodLabel(state),
            style = theme.typography.caption,
            color = theme.colors.muted,
        )
    }
}

@Composable
private fun periodLabel(state: PlanState): String = when (state.periodKey) {
    PlanPeriodKey.MONTH -> "${state.month.year}/${state.month.monthValue}"
    PlanPeriodKey.MONTHS_3 -> stringResource(R.string.plan_period_label_last_3_months)
    PlanPeriodKey.YEAR -> "${state.month.year}"
    PlanPeriodKey.CUSTOM -> if (state.customStart != null && state.customEnd != null)
        "${state.customStart} → ${state.customEnd}"
    else stringResource(R.string.plan_period_label_custom_default)
}

@Composable
private fun BudgetRowView(row: BudgetRow, onClick: () -> Unit) {
    val theme = AtharTheme
    AtharCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                AtharText(text = row.category.nameAr, style = theme.typography.headline)
                AtharText(text = row.category.name, style = theme.typography.caption, color = theme.colors.muted)
            }
            Column(horizontalAlignment = Alignment.End) {
                AtharNumber(money = row.actual)
                if (row.target != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AtharText(
                            text = stringResource(R.string.plan_budget_target_label),
                            style = theme.typography.caption,
                            color = theme.colors.muted,
                        )
                        AtharNumber(money = row.target, color = theme.colors.muted)
                    }
                    VariancePill(row = row)
                } else {
                    AtharText(text = stringResource(R.string.plan_budget_no_target), style = theme.typography.caption, color = theme.colors.muted)
                }
            }
        }
    }
}

@Composable
private fun VariancePill(row: BudgetRow) {
    val theme = AtharTheme
    val variance = row.variance ?: return
    val (label, color) = when (row.limit) {
        LimitState.Over -> "${stringResource(R.string.plan_limit_over)} " to theme.colors.ember
        LimitState.Tight -> "${stringResource(R.string.plan_limit_tight)} · " to theme.colors.ember
        LimitState.Watch -> "${stringResource(R.string.plan_limit_watch)} · " to theme.colors.dust
        LimitState.Healthy -> "${stringResource(R.string.plan_limit_healthy)} " to theme.colors.olive
        LimitState.None -> "" to theme.colors.muted
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (label.isNotEmpty()) AtharText(text = label, style = theme.typography.caption, color = color)
        AtharNumber(money = if (variance.isNegative()) -variance else variance, color = color)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TargetEditor(
    row: BudgetRow,
    onDismiss: () -> Unit,
    onSave: (Long?) -> Unit,
) {
    val theme = AtharTheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var amount by remember(row.category.id) {
        mutableStateOf(row.target?.amount?.toPlainString() ?: "")
    }

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
                text = "${row.category.nameAr}${stringResource(R.string.plan_target_editor_title_suffix)}",
                style = theme.typography.headline,
            )
            AtharAmountField(
                value = amount,
                onValueChange = { amount = it },
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.plan_target_editor_amount_label),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(theme.spacing.s),
            ) {
                SheetButton(
                    text = stringResource(R.string.plan_action_clear_target),
                    background = theme.colors.divider,
                    textColor = theme.colors.ink,
                    onClick = { onSave(null) },
                    modifier = Modifier.weight(1f),
                )
                SheetButton(
                    text = stringResource(R.string.plan_action_save),
                    background = theme.colors.ember,
                    textColor = theme.colors.parchment,
                    onClick = {
                        val parsed = runCatching { BigDecimal(amount) }.getOrNull()
                        val minor = parsed?.let { Money.of(it).amount.movePointRight(2).toLong() }
                        onSave(minor)
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
