package com.athar.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
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
    val currency = LocalDisplayCurrency.current
    var showAdd by remember { mutableStateOf(false) }

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
                AtharText(text = "الحركات المتكررة", style = theme.typography.overline, color = theme.colors.muted)
                AtharText(
                    text = "رجوع",
                    style = theme.typography.body,
                    color = theme.colors.muted,
                    modifier = Modifier.clickable(onClick = onBack),
                )
            }

            AtharCard {
                Column(verticalArrangement = Arrangement.spacedBy(theme.spacing.s)) {
                    AtharText(text = "كيف تعمل", style = theme.typography.headline)
                    AtharText(
                        text = "أنشئ قاعدة تُكرَّر شهريًا أو أسبوعيًا أو سنويًا (الإيجار، الراتب، الاشتراكات). عند تاريخ الاستحقاق تظهر الحركة في قائمة الانتظار في «اليوم»، فتؤكدها بنقرة.",
                        style = theme.typography.body,
                        color = theme.colors.muted,
                    )
                    if (materialized > 0) {
                        AtharText(
                            text = "أنشئت $materialized حركة جديدة في قائمة الانتظار.",
                            style = theme.typography.caption,
                            color = theme.colors.olive,
                        )
                    }
                    PrimaryActionButton(
                        text = "تشغيل الآن",
                        onClick = viewModel::materializeNow,
                    )
                }
            }

            if (suggestions.isNotEmpty()) {
                AtharCard {
                    Column(verticalArrangement = Arrangement.spacedBy(theme.spacing.s)) {
                        AtharText(
                            text = "اقتراحات تلقائية · ${suggestions.size}",
                            style = theme.typography.headline,
                            color = theme.colors.ember,
                        )
                        AtharText(
                            text = "اكتشفنا هذه الأنماط من حركاتك السابقة. تأكد منها لإنشاء قاعدة تكرار.",
                            style = theme.typography.body,
                            color = theme.colors.muted,
                        )
                    }
                }
                suggestions.forEach { s ->
                    SuggestionRow(
                        suggestion = s,
                        onAccept = { viewModel.acceptSuggestion(s) },
                    )
                }
            }

            AtharCard(modifier = Modifier
                .fillMaxWidth()
                .clickable { showAdd = !showAdd }
            ) {
                AtharText(
                    text = if (showAdd) "إخفاء النموذج" else "+ إضافة قاعدة",
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
                        text = "لا قواعد بعد. ابدأ بإيجارك أو راتبك أو اشتراك Netflix.",
                        style = theme.typography.body,
                        color = theme.colors.muted,
                    )
                }
            } else {
                rules.forEach { rule ->
                    RuleRow(
                        rule = rule,
                        onToggle = { viewModel.toggle(rule.id, !rule.isActive) },
                        onDelete = { viewModel.delete(rule.id) },
                    )
                }
            }
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
            AtharText(text = "إضافة قاعدة جديدة", style = theme.typography.headline)
            AtharTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = "الاسم (مثلاً: راتب)",
                modifier = Modifier.fillMaxWidth(),
            )
            AtharTextField(
                value = merchant,
                onValueChange = { merchant = it },
                label = "الجهة (مثلاً: شركة ، Netflix)",
                modifier = Modifier.fillMaxWidth(),
            )
            AtharTextField(
                value = amount,
                onValueChange = { amount = it },
                label = "المبلغ ($currency)",
                modifier = Modifier.fillMaxWidth(),
                keyboardType = KeyboardType.Number,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(theme.spacing.s)) {
                Chip(label = "مصروف", selected = type == TxType.EXPENSE, accent = theme.colors.ember,
                    onClick = { type = TxType.EXPENSE })
                Chip(label = "دخل", selected = type == TxType.INCOME, accent = theme.colors.olive,
                    onClick = { type = TxType.INCOME })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(theme.spacing.s)) {
                Chip(label = "شهري", selected = cadence == Cadence.MONTHLY, accent = theme.colors.ember,
                    onClick = { cadence = Cadence.MONTHLY })
                Chip(label = "أسبوعي", selected = cadence == Cadence.WEEKLY, accent = theme.colors.ember,
                    onClick = { cadence = Cadence.WEEKLY })
                Chip(label = "سنوي", selected = cadence == Cadence.YEARLY, accent = theme.colors.ember,
                    onClick = { cadence = Cadence.YEARLY })
            }
            if (cadence == Cadence.MONTHLY) {
                AtharTextField(
                    value = dayOfMonth,
                    onValueChange = { dayOfMonth = it.filter { c -> c.isDigit() }.take(2) },
                    label = "يوم الشهر (1-28)",
                    modifier = Modifier.fillMaxWidth(),
                    keyboardType = KeyboardType.Number,
                )
            }
            PrimaryActionButton(
                text = "حفظ القاعدة",
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
                        text = "التالي: ${rule.nextRunDate}",
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
                        text = if (rule.isActive) "مفعّل" else "موقَف",
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
                    AtharText(text = "حذف", style = theme.typography.caption, color = theme.colors.crimson)
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
                        text = "${suggestion.occurrenceCount} تكرارات · يوم ${suggestion.typicalDayOfMonth}",
                        style = theme.typography.caption,
                        color = theme.colors.muted,
                    )
                    AtharText(
                        text = "آخر مرة: ${suggestion.lastSeen}",
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
                text = "إنشاء قاعدة",
                onClick = onAccept,
            )
        }
    }
}

private fun cadenceLabel(c: Cadence): String = when (c) {
    Cadence.MONTHLY -> "شهري"
    Cadence.WEEKLY -> "أسبوعي"
    Cadence.YEARLY -> "سنوي"
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
