package com.athar.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.athar.core.common.money.Money
import com.athar.core.designsystem.component.AtharCard
import com.athar.core.designsystem.component.AtharNumber
import com.athar.core.designsystem.component.AtharText
import com.athar.core.designsystem.component.AtharTextField
import com.athar.core.designsystem.display.LocalDisplayCurrency
import com.athar.core.designsystem.theme.AtharTheme
import com.athar.core.domain.model.Cadence
import com.athar.core.domain.model.RecurringRule
import com.athar.core.domain.model.RecurringSuggestion
import com.athar.core.domain.model.TxType
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun RecurringRulesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecurringRulesViewModel = hiltViewModel(),
) {
    val theme = AtharTheme
    val rules by viewModel.state.collectAsStateWithLifecycle()
    val materialized by viewModel.lastMaterializeCount.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val categoriesAll by viewModel.categories.collectAsStateWithLifecycle()
    val lastAccepted by viewModel.lastAccepted.collectAsStateWithLifecycle()
    LaunchedEffect(lastAccepted) {
        if (lastAccepted != null) {
            kotlinx.coroutines.delay(3000)
            viewModel.clearLastAccepted()
        }
    }
    val currency = LocalDisplayCurrency.current
    var showAdd by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf<RecurringSuggestion?>(null) }

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AtharText(text = stringResource(R.string.settings_recurring_overline), style = theme.typography.overline, color = theme.colors.muted)
                AtharText(
                    text = stringResource(R.string.settings_action_back),
                    style = theme.typography.body,
                    color = theme.colors.muted,
                    modifier = Modifier.clickable(onClick = onBack),
                )
            }

            AtharCard {
                Column(verticalArrangement = Arrangement.spacedBy(theme.spacing.s)) {
                    AtharText(text = stringResource(R.string.settings_recurring_how_title), style = theme.typography.headline)
                    AtharText(
                        text = stringResource(R.string.settings_recurring_how_body),
                        style = theme.typography.body,
                        color = theme.colors.muted,
                    )
                    if (materialized > 0) {
                        AtharText(
                            text = stringResource(R.string.settings_recurring_materialized, materialized),
                            style = theme.typography.caption,
                            color = theme.colors.olive,
                        )
                    }
                    PrimaryActionButton(
                        text = stringResource(R.string.settings_recurring_run_now),
                        onClick = viewModel::materializeNow,
                    )
                }
            }

            lastAccepted?.let { merchant ->
                AtharCard(modifier = Modifier
                    .fillMaxWidth()
                    .background(theme.colors.olive)
                ) {
                    AtharText(
                        text = stringResource(R.string.settings_recurring_accepted_toast, merchant),
                        style = theme.typography.body,
                        color = theme.colors.parchment,
                    )
                }
            }

            if (suggestions.isNotEmpty()) {
                AtharCard {
                    Column(verticalArrangement = Arrangement.spacedBy(theme.spacing.s)) {
                        AtharText(
                            text = stringResource(R.string.settings_recurring_suggestions_title, suggestions.size),
                            style = theme.typography.headline,
                            color = theme.colors.ember,
                        )
                        AtharText(
                            text = stringResource(R.string.settings_recurring_suggestions_body),
                            style = theme.typography.body,
                            color = theme.colors.muted,
                        )
                    }
                }
                suggestions.forEach { s ->
                    SuggestionRow(
                        suggestion = s,
                        onAccept = { confirming = s },
                    )
                }
            }

            AtharCard(modifier = Modifier
                .fillMaxWidth()
                .clickable { showAdd = !showAdd }
            ) {
                AtharText(
                    text = if (showAdd) {
                        stringResource(R.string.settings_recurring_hide_form)
                    } else {
                        stringResource(R.string.settings_recurring_add)
                    },
                    style = theme.typography.headline,
                    color = theme.colors.ember,
                )
            }
            if (showAdd) {
                AddRuleForm(
                    currency = currency,
                    onSave = { displayName, merchant, amount, type, cadence, dayOfMonth, startDate ->
                        viewModel.add(
                            displayName = displayName,
                            merchant = merchant,
                            amountText = amount,
                            currency = currency,
                            type = type,
                            categoryId = null,
                            cadence = cadence,
                            dayOfMonth = dayOfMonth,
                            startDate = startDate,
                        )
                        showAdd = false
                    },
                )
            }

            if (rules.isEmpty()) {
                AtharCard {
                    AtharText(
                        text = stringResource(R.string.settings_recurring_empty),
                        style = theme.typography.body,
                        color = theme.colors.muted,
                    )
                }
            } else {
                val (active, paused) = rules.partition { it.isActive }
                if (active.isNotEmpty()) {
                    val monthlyTotal = active
                        .filter { it.type == TxType.EXPENSE && it.cadence == Cadence.MONTHLY }
                        .fold(Money.zero(currency)) { acc, r ->
                            val sameCurrency = r.amount.currency == currency
                            if (sameCurrency) acc + r.amount else acc
                        }
                    SubscriptionsHeader(activeCount = active.size, monthlyTotal = monthlyTotal)
                    AtharText(
                        text = stringResource(R.string.settings_recurring_active_section),
                        style = theme.typography.overline,
                        color = theme.colors.muted,
                    )
                    active.forEach { rule ->
                        RuleRow(
                            rule = rule,
                            onToggle = { viewModel.toggle(rule.id, !rule.isActive) },
                            onDelete = { viewModel.delete(rule.id) },
                        )
                    }
                }
                if (paused.isNotEmpty()) {
                    AtharText(
                        text = stringResource(R.string.settings_recurring_paused_section, paused.size),
                        style = theme.typography.overline,
                        color = theme.colors.muted,
                    )
                    paused.forEach { rule ->
                        RuleRow(
                            rule = rule,
                            onToggle = { viewModel.toggle(rule.id, !rule.isActive) },
                            onDelete = { viewModel.delete(rule.id) },
                        )
                    }
                }
            }
        }

        confirming?.let { suggestion ->
            val notesText = stringResource(R.string.settings_recurring_auto_detected_notes, suggestion.occurrenceCount)
            ConfirmSuggestionSheet(
                suggestion = suggestion,
                categories = categoriesAll,
                onDismiss = { confirming = null },
                onConfirm = { cadence, dayOfMonth, categoryId ->
                    viewModel.acceptSuggestion(suggestion, notesText, cadence, dayOfMonth, categoryId)
                    confirming = null
                },
            )
        }
    }
}

@Composable
private fun AddRuleForm(
    currency: String,
    onSave: (displayName: String, merchant: String, amount: String, type: TxType, cadence: Cadence, dayOfMonth: Int?, startDate: kotlinx.datetime.LocalDate) -> Unit,
) {
    val theme = AtharTheme
    var displayName by remember { mutableStateOf("") }
    var merchant by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(TxType.EXPENSE) }
    var cadence by remember { mutableStateOf(Cadence.MONTHLY) }
    var dayOfMonth by remember { mutableStateOf("") }
    val today = remember { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date }

    AtharCard {
        Column(verticalArrangement = Arrangement.spacedBy(theme.spacing.s)) {
            AtharText(text = stringResource(R.string.settings_recurring_form_title), style = theme.typography.headline)
            AtharTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = stringResource(R.string.settings_recurring_field_name),
                modifier = Modifier.fillMaxWidth(),
            )
            AtharTextField(
                value = merchant,
                onValueChange = { merchant = it },
                label = stringResource(R.string.settings_recurring_field_merchant),
                modifier = Modifier.fillMaxWidth(),
            )
            AtharTextField(
                value = amount,
                onValueChange = { amount = it },
                label = stringResource(R.string.settings_recurring_field_amount, currency),
                modifier = Modifier.fillMaxWidth(),
                keyboardType = KeyboardType.Number,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(theme.spacing.s)) {
                Chip(label = stringResource(R.string.settings_recurring_type_expense), selected = type == TxType.EXPENSE, accent = theme.colors.ember,
                    onClick = { type = TxType.EXPENSE })
                Chip(label = stringResource(R.string.settings_recurring_type_income), selected = type == TxType.INCOME, accent = theme.colors.olive,
                    onClick = { type = TxType.INCOME })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(theme.spacing.s)) {
                Chip(label = stringResource(R.string.settings_recurring_cadence_monthly), selected = cadence == Cadence.MONTHLY, accent = theme.colors.ember,
                    onClick = { cadence = Cadence.MONTHLY })
                Chip(label = stringResource(R.string.settings_recurring_cadence_weekly), selected = cadence == Cadence.WEEKLY, accent = theme.colors.ember,
                    onClick = { cadence = Cadence.WEEKLY })
                Chip(label = stringResource(R.string.settings_recurring_cadence_yearly), selected = cadence == Cadence.YEARLY, accent = theme.colors.ember,
                    onClick = { cadence = Cadence.YEARLY })
            }
            if (cadence == Cadence.MONTHLY) {
                AtharTextField(
                    value = dayOfMonth,
                    onValueChange = { dayOfMonth = it.filter { c -> c.isDigit() }.take(2) },
                    label = stringResource(R.string.settings_recurring_field_day_of_month),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardType = KeyboardType.Number,
                )
            }
            PrimaryActionButton(
                text = stringResource(R.string.settings_recurring_save),
                onClick = {
                    val dom = dayOfMonth.toIntOrNull()
                    val startDate = if (cadence == Cadence.MONTHLY && dom != null) {
                        val jt = java.time.LocalDate.of(today.year, today.monthNumber, 1)
                            .let { firstOfMonth ->
                                val safeDom = dom.coerceIn(1, firstOfMonth.lengthOfMonth())
                                firstOfMonth.withDayOfMonth(safeDom)
                            }
                        kotlinx.datetime.LocalDate(jt.year, jt.monthValue, jt.dayOfMonth)
                    } else {
                        today
                    }
                    onSave(displayName, merchant, amount, type, cadence, dom, startDate)
                },
            )
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, accent: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    val theme = AtharTheme
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(theme.spacing.s))
            .background(if (selected) accent else theme.colors.divider)
            .clickable(onClick = onClick)
            .padding(horizontal = theme.spacing.m, vertical = theme.spacing.s),
    ) {
        AtharText(
            text = label,
            style = theme.typography.caption,
            color = if (selected) theme.colors.parchment else theme.colors.ink,
        )
    }
}

@Composable
private fun RuleRow(
    rule: RecurringRule,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    val theme = AtharTheme
    AtharCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(theme.spacing.xs)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.padding(end = theme.spacing.s)) {
                    AtharText(text = rule.displayName, style = theme.typography.headline)
                    AtharText(
                        text = "${cadenceLabel(rule.cadence)} · ${rule.merchant}",
                        style = theme.typography.caption,
                        color = theme.colors.muted,
                    )
                    AtharText(
                        text = stringResource(R.string.settings_recurring_row_next, rule.nextRunDate.toString()),
                        style = theme.typography.caption,
                        color = theme.colors.muted,
                    )
                }
                AtharNumber(
                    money = rule.amount,
                    color = if (rule.type == TxType.INCOME) theme.colors.olive else theme.colors.ember,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(theme.spacing.s)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(theme.spacing.s))
                        .background(if (rule.isActive) theme.colors.olive else theme.colors.divider)
                        .clickable(onClick = onToggle)
                        .padding(horizontal = theme.spacing.m, vertical = theme.spacing.s),
                ) {
                    AtharText(
                        text = if (rule.isActive) {
                            stringResource(R.string.settings_recurring_row_active)
                        } else {
                            stringResource(R.string.settings_recurring_row_inactive)
                        },
                        style = theme.typography.caption,
                        color = if (rule.isActive) theme.colors.parchment else theme.colors.ink,
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(theme.spacing.s))
                        .background(theme.colors.divider)
                        .clickable(onClick = onDelete)
                        .padding(horizontal = theme.spacing.m, vertical = theme.spacing.s),
                ) {
                    AtharText(text = stringResource(R.string.settings_action_delete), style = theme.typography.caption, color = theme.colors.crimson)
                }
            }
        }
    }
}

@Composable
private fun SuggestionRow(
    suggestion: RecurringSuggestion,
    onAccept: () -> Unit,
) {
    val theme = AtharTheme
    AtharCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(theme.spacing.xs)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.padding(end = theme.spacing.s)) {
                    AtharText(text = suggestion.merchant, style = theme.typography.headline)
                    AtharText(
                        text = stringResource(
                            R.string.settings_recurring_suggestion_counts,
                            suggestion.occurrenceCount,
                            suggestion.typicalDayOfMonth.toString(),
                        ),
                        style = theme.typography.caption,
                        color = theme.colors.muted,
                    )
                    AtharText(
                        text = stringResource(R.string.settings_recurring_suggestion_last, suggestion.lastSeen.toString()),
                        style = theme.typography.caption,
                        color = theme.colors.muted,
                    )
                }
                AtharNumber(
                    money = suggestion.amount,
                    color = if (suggestion.type == TxType.INCOME) theme.colors.olive else theme.colors.ember,
                )
            }
            PrimaryActionButton(
                text = stringResource(R.string.settings_recurring_create_rule),
                onClick = onAccept,
            )
        }
    }
}

@Composable
private fun cadenceLabel(c: Cadence): String = when (c) {
    Cadence.MONTHLY -> stringResource(R.string.settings_recurring_cadence_monthly)
    Cadence.WEEKLY -> stringResource(R.string.settings_recurring_cadence_weekly)
    Cadence.YEARLY -> stringResource(R.string.settings_recurring_cadence_yearly)
}

@Composable
private fun PrimaryActionButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val theme = AtharTheme
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(theme.spacing.s))
            .background(theme.colors.ember)
            .clickable(onClick = onClick)
            .padding(theme.spacing.m),
        contentAlignment = Alignment.Center,
    ) {
        AtharText(text = text, style = theme.typography.headline, color = theme.colors.parchment)
    }
}

@Composable
private fun SubscriptionsHeader(activeCount: Int, monthlyTotal: Money) {
    val theme = AtharTheme
    AtharCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(theme.spacing.xs)) {
            AtharText(
                text = stringResource(R.string.settings_recurring_active_count, activeCount),
                style = theme.typography.caption,
                color = theme.colors.muted,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(theme.spacing.s)) {
                AtharText(
                    text = stringResource(R.string.settings_recurring_monthly_total_label),
                    style = theme.typography.body,
                    color = theme.colors.muted,
                )
                AtharNumber(money = monthlyTotal, color = theme.colors.ember)
            }
        }
    }
}

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
private fun ConfirmSuggestionSheet(
    suggestion: RecurringSuggestion,
    categories: List<com.athar.core.domain.model.Category>,
    onDismiss: () -> Unit,
    onConfirm: (Cadence, Int?, String?) -> Unit,
) {
    val theme = AtharTheme
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var cadence by remember { mutableStateOf(Cadence.MONTHLY) }
    var dayOfMonth by remember { mutableStateOf(suggestion.typicalDayOfMonth.toString()) }
    var selectedCategoryId by remember { mutableStateOf<String?>(null) }
    val expectedKind = if (suggestion.type == TxType.INCOME) {
        com.athar.core.domain.model.CategoryKind.INCOME
    } else {
        com.athar.core.domain.model.CategoryKind.EXPENSE
    }
    val pickable = categories.filter { it.kind == expectedKind && !it.archived }
    androidx.compose.material3.ModalBottomSheet(
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
                text = stringResource(R.string.settings_recurring_confirm_title),
                style = theme.typography.headline,
            )
            Column(verticalArrangement = Arrangement.spacedBy(theme.spacing.xs)) {
                AtharText(text = suggestion.merchant, style = theme.typography.title)
                AtharText(
                    text = stringResource(
                        R.string.settings_recurring_suggestion_counts,
                        suggestion.occurrenceCount,
                        suggestion.typicalDayOfMonth.toString(),
                    ),
                    style = theme.typography.caption,
                    color = theme.colors.muted,
                )
                AtharNumber(
                    money = suggestion.amount,
                    color = if (suggestion.type == TxType.INCOME) theme.colors.olive else theme.colors.ember,
                )
            }

            AtharText(
                text = stringResource(R.string.settings_recurring_confirm_cadence),
                style = theme.typography.caption,
                color = theme.colors.muted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(theme.spacing.s)) {
                Cadence.entries.forEach { c ->
                    CadenceChip(
                        text = cadenceLabel(c),
                        selected = c == cadence,
                        onClick = { cadence = c },
                    )
                }
            }

            if (cadence == Cadence.MONTHLY) {
                AtharTextField(
                    value = dayOfMonth,
                    onValueChange = { dayOfMonth = it.filter { ch -> ch.isDigit() }.take(2) },
                    label = stringResource(R.string.settings_recurring_field_day_of_month),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardType = KeyboardType.Number,
                )
            }

            AtharText(
                text = stringResource(R.string.settings_recurring_confirm_category),
                style = theme.typography.caption,
                color = theme.colors.muted,
            )
            com.athar.core.designsystem.component.AtharCategoryPicker(
                items = pickable.map {
                    com.athar.core.designsystem.component.AtharPickerItem(key = it.id, labelEn = it.name, labelAr = it.nameAr)
                },
                selectedKey = selectedCategoryId,
                onSelect = { selectedCategoryId = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp, max = 240.dp),
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(theme.spacing.s)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(theme.spacing.s))
                        .background(theme.colors.divider)
                        .clickable(onClick = onDismiss)
                        .padding(theme.spacing.m),
                    contentAlignment = Alignment.Center,
                ) {
                    AtharText(
                        text = stringResource(R.string.settings_action_cancel),
                        style = theme.typography.headline,
                        color = theme.colors.ink,
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(2f)
                        .clip(RoundedCornerShape(theme.spacing.s))
                        .background(theme.colors.ember)
                        .clickable {
                            val dom = dayOfMonth.toIntOrNull()?.coerceIn(1, 31)
                            onConfirm(cadence, dom, selectedCategoryId)
                        }
                        .padding(theme.spacing.m),
                    contentAlignment = Alignment.Center,
                ) {
                    AtharText(
                        text = stringResource(R.string.settings_recurring_confirm_create),
                        style = theme.typography.headline,
                        color = theme.colors.parchment,
                    )
                }
            }
        }
    }
}

@Composable
private fun CadenceChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val theme = AtharTheme
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(theme.spacing.s))
            .background(if (selected) theme.colors.ember else theme.colors.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = theme.spacing.m, vertical = theme.spacing.s),
    ) {
        AtharText(
            text = text,
            style = theme.typography.body,
            color = if (selected) theme.colors.parchment else theme.colors.ink,
        )
    }
}
