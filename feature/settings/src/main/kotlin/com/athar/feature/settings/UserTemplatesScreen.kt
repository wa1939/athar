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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.athar.core.designsystem.component.AtharCard
import com.athar.core.designsystem.component.AtharText
import com.athar.core.designsystem.component.AtharTextField
import com.athar.core.designsystem.theme.AtharTheme
import com.athar.core.domain.model.TxType
import com.athar.core.domain.model.UserTemplate

@Composable
fun UserTemplatesScreen(
    onBack: () -> Unit,
    viewModel: UserTemplatesViewModel = hiltViewModel(),
) {
    val theme = AtharTheme
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    var form by remember { mutableStateOf(TemplateForm()) }

    Box(modifier = Modifier.fillMaxSize().background(theme.colors.parchment)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(theme.spacing.m),
            verticalArrangement = Arrangement.spacedBy(theme.spacing.l),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                AtharText(text = "قوالب البنوك", style = theme.typography.title)
                AtharText(
                    text = "إغلاق",
                    style = theme.typography.body,
                    color = theme.colors.muted,
                    modifier = Modifier.clickable(onClick = onBack).padding(theme.spacing.s),
                )
            }
            AtharCard {
                Column(verticalArrangement = Arrangement.spacedBy(theme.spacing.xs)) {
                    AtharText(
                        text = "إذا فشل تحليل رسائل بنك معيّن، علِّم أثر تنسيقها هنا. الصق نص الرسالة، ثم اكتب الكلمة التي تسبق المبلغ والكلمة التي تليه — يستخدم أثر هذه الإشارات لاستخراج المبلغ من أي رسالة بنفس التنسيق.",
                        style = theme.typography.body,
                        color = theme.colors.muted,
                    )
                }
            }

            if (templates.isNotEmpty()) {
                AtharText(text = "قوالبك", style = theme.typography.overline, color = theme.colors.muted)
                templates.forEach { template ->
                    SavedTemplateRow(template = template, onDelete = { viewModel.delete(template.id) })
                }
            }

            AtharText(text = "إضافة قالب جديد", style = theme.typography.overline, color = theme.colors.muted)
            TemplateFormCard(
                form = form,
                onChange = { form = it },
                onSave = {
                    viewModel.save(form)
                    form = TemplateForm()
                },
            )
        }
    }
}

@Composable
private fun SavedTemplateRow(template: UserTemplate, onDelete: () -> Unit) {
    val theme = AtharTheme
    AtharCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(theme.spacing.xs),
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    AtharText(text = template.displayName, style = theme.typography.headline)
                    AtharText(
                        text = "المرسل: ${template.sender} · ${typeLabel(template.txType)}",
                        style = theme.typography.caption,
                        color = theme.colors.muted,
                    )
                }
                AtharText(
                    text = "حذف",
                    style = theme.typography.caption,
                    color = theme.colors.crimson,
                    modifier = Modifier.clickable(onClick = onDelete).padding(theme.spacing.s),
                )
            }
            AtharText(
                text = "قبل المبلغ: \"${template.amountAnchorBefore}\"" +
                    (template.amountAnchorAfter?.let { " · بعد: \"$it\"" } ?: ""),
                style = theme.typography.caption,
                color = theme.colors.muted,
            )
        }
    }
}

@Composable
private fun TemplateFormCard(
    form: TemplateForm,
    onChange: (TemplateForm) -> Unit,
    onSave: () -> Unit,
) {
    val theme = AtharTheme
    AtharCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(theme.spacing.m),
        ) {
            AtharTextField(
                value = form.displayName,
                onValueChange = { onChange(form.copy(displayName = it)) },
                label = "اسم البنك (مثلاً: مصرف الإنماء)",
                modifier = Modifier.fillMaxWidth(),
            )
            AtharTextField(
                value = form.sender,
                onValueChange = { onChange(form.copy(sender = it)) },
                label = "مرسل الرسائل كما يظهر في الجهاز (مثلاً: AlinmaBank)",
                modifier = Modifier.fillMaxWidth(),
            )
            AtharTextField(
                value = form.sampleBody,
                onValueChange = { onChange(form.copy(sampleBody = it)) },
                label = "الصق نص رسالة عيّنة هنا",
                singleLine = false,
                modifier = Modifier.fillMaxWidth(),
            )
            TypeSelector(
                selected = form.txType,
                onSelect = { onChange(form.copy(txType = it)) },
            )

            AnchorPair(
                label = "المبلغ",
                hint = "اكتب الكلمة قبل المبلغ مباشرة (مثلاً: Amount: أو المبلغ)",
                before = form.amountAnchorBefore,
                after = form.amountAnchorAfter,
                onBeforeChange = { onChange(form.copy(amountAnchorBefore = it)) },
                onAfterChange = { onChange(form.copy(amountAnchorAfter = it)) },
            )
            AnchorPair(
                label = "المتجر (اختياري)",
                hint = "للمشتريات: الكلمة قبل اسم المتجر (مثلاً: At: أو لدى)",
                before = form.merchantAnchorBefore,
                after = form.merchantAnchorAfter,
                onBeforeChange = { onChange(form.copy(merchantAnchorBefore = it)) },
                onAfterChange = { onChange(form.copy(merchantAnchorAfter = it)) },
            )
            AnchorPair(
                label = "المُحوَّل إليه (اختياري)",
                hint = "للتحويلات: الكلمة قبل اسم المستلم (مثلاً: To: أو إلى)",
                before = form.counterpartyAnchorBefore,
                after = form.counterpartyAnchorAfter,
                onBeforeChange = { onChange(form.copy(counterpartyAnchorBefore = it)) },
                onAfterChange = { onChange(form.copy(counterpartyAnchorAfter = it)) },
            )

            SaveButton(enabled = form.isValid, onClick = onSave)
        }
    }
}

@Composable
private fun AnchorPair(
    label: String,
    hint: String,
    before: String,
    after: String,
    onBeforeChange: (String) -> Unit,
    onAfterChange: (String) -> Unit,
) {
    val theme = AtharTheme
    Column(verticalArrangement = Arrangement.spacedBy(theme.spacing.xs)) {
        AtharText(text = label, style = theme.typography.headline)
        AtharText(text = hint, style = theme.typography.caption, color = theme.colors.muted)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(theme.spacing.s),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                AtharTextField(
                    value = before,
                    onValueChange = onBeforeChange,
                    label = "قبل",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                AtharTextField(
                    value = after,
                    onValueChange = onAfterChange,
                    label = "بعد (اختياري)",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun TypeSelector(selected: TxType, onSelect: (TxType) -> Unit) {
    val theme = AtharTheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(theme.spacing.s),
    ) {
        listOf(TxType.EXPENSE, TxType.TRANSFER, TxType.INCOME).forEach { type ->
            val isSelected = type == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(theme.spacing.s))
                    .background(if (isSelected) theme.colors.ember else theme.colors.divider)
                    .clickable { onSelect(type) }
                    .padding(theme.spacing.m),
                contentAlignment = Alignment.Center,
            ) {
                AtharText(
                    text = typeLabel(type),
                    style = theme.typography.headline,
                    color = if (isSelected) theme.colors.parchment else theme.colors.ink,
                )
            }
        }
    }
}

@Composable
private fun SaveButton(enabled: Boolean, onClick: () -> Unit) {
    val theme = AtharTheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(theme.spacing.s))
            .background(if (enabled) theme.colors.ember else theme.colors.divider)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(theme.spacing.m),
        contentAlignment = Alignment.Center,
    ) {
        AtharText(
            text = "حفظ القالب",
            style = theme.typography.headline,
            color = if (enabled) theme.colors.parchment else theme.colors.muted,
        )
    }
}

private fun typeLabel(type: TxType): String = when (type) {
    TxType.EXPENSE -> "شراء"
    TxType.TRANSFER -> "تحويل"
    TxType.INCOME -> "إيداع"
}
