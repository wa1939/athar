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
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.athar.core.designsystem.component.AtharCard
import com.athar.core.designsystem.component.AtharText
import com.athar.core.designsystem.component.AtharTextField
import com.athar.core.designsystem.theme.AtharTheme
import com.athar.core.domain.model.TxType
import com.athar.core.domain.model.UserTemplate
import com.athar.core.domain.model.UserTemplateAnchorMatch

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
                AtharText(text = stringResource(R.string.settings_templates_title), style = theme.typography.title)
                AtharText(
                    text = stringResource(R.string.settings_action_close),
                    style = theme.typography.body,
                    color = theme.colors.muted,
                    modifier = Modifier.clickable(onClick = onBack).padding(theme.spacing.s),
                )
            }
            AtharCard {
                Column(verticalArrangement = Arrangement.spacedBy(theme.spacing.xs)) {
                    AtharText(
                        text = stringResource(R.string.settings_templates_intro),
                        style = theme.typography.body,
                        color = theme.colors.muted,
                    )
                }
            }

            if (templates.isNotEmpty()) {
                AtharText(text = stringResource(R.string.settings_templates_saved_header), style = theme.typography.overline, color = theme.colors.muted)
                templates.forEach { template ->
                    SavedTemplateRow(template = template, onDelete = { viewModel.delete(template.id) })
                }
            }

            AtharText(text = stringResource(R.string.settings_templates_new_header), style = theme.typography.overline, color = theme.colors.muted)
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
                        text = stringResource(
                            R.string.settings_templates_row_sender,
                            template.sender,
                            typeLabel(template.txType),
                        ),
                        style = theme.typography.caption,
                        color = theme.colors.muted,
                    )
                }
                AtharText(
                    text = stringResource(R.string.settings_action_delete),
                    style = theme.typography.caption,
                    color = theme.colors.crimson,
                    modifier = Modifier.clickable(onClick = onDelete).padding(theme.spacing.s),
                )
            }
            val anchorBeforeText = stringResource(R.string.settings_templates_row_anchor_before, template.amountAnchorBefore)
            val anchorAfterText = template.amountAnchorAfter?.let {
                stringResource(R.string.settings_templates_row_anchor_after, it)
            } ?: ""
            AtharText(
                text = anchorBeforeText + anchorAfterText,
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
                label = stringResource(R.string.settings_templates_field_display_name),
                modifier = Modifier.fillMaxWidth(),
            )
            AtharTextField(
                value = form.sender,
                onValueChange = { onChange(form.copy(sender = it)) },
                label = stringResource(R.string.settings_templates_field_sender),
                modifier = Modifier.fillMaxWidth(),
            )
            AtharTextField(
                value = form.sampleBody,
                onValueChange = { onChange(form.copy(sampleBody = it)) },
                label = stringResource(R.string.settings_templates_field_sample_body),
                singleLine = false,
                modifier = Modifier.fillMaxWidth(),
            )
            TypeSelector(
                selected = form.txType,
                onSelect = { onChange(form.copy(txType = it)) },
            )

            AnchorPair(
                label = stringResource(R.string.settings_templates_anchor_amount_label),
                hint = stringResource(R.string.settings_templates_anchor_amount_hint),
                before = form.amountAnchorBefore,
                after = form.amountAnchorAfter,
                onBeforeChange = { onChange(form.copy(amountAnchorBefore = it)) },
                onAfterChange = { onChange(form.copy(amountAnchorAfter = it)) },
            )
            AnchorPair(
                label = stringResource(R.string.settings_templates_anchor_merchant_label),
                hint = stringResource(R.string.settings_templates_anchor_merchant_hint),
                before = form.merchantAnchorBefore,
                after = form.merchantAnchorAfter,
                onBeforeChange = { onChange(form.copy(merchantAnchorBefore = it)) },
                onAfterChange = { onChange(form.copy(merchantAnchorAfter = it)) },
            )
            AnchorPair(
                label = stringResource(R.string.settings_templates_anchor_counterparty_label),
                hint = stringResource(R.string.settings_templates_anchor_counterparty_hint),
                before = form.counterpartyAnchorBefore,
                after = form.counterpartyAnchorAfter,
                onBeforeChange = { onChange(form.copy(counterpartyAnchorBefore = it)) },
                onAfterChange = { onChange(form.copy(counterpartyAnchorAfter = it)) },
            )

            TemplatePreview(match = form.preview)
            SaveButton(enabled = form.isValid, onClick = onSave)
        }
    }
}

@Composable
private fun TemplatePreview(match: UserTemplateAnchorMatch?) {
    val theme = AtharTheme
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(theme.spacing.xs),
    ) {
        AtharText(
            text = stringResource(R.string.settings_templates_preview_title),
            style = theme.typography.headline,
        )
        if (match == null) {
            AtharText(
                text = stringResource(R.string.settings_templates_preview_waiting),
                style = theme.typography.caption,
                color = theme.colors.muted,
            )
            return
        }

        if (match.canParseAmount) {
            AtharText(
                text = stringResource(
                    R.string.settings_templates_preview_amount,
                    match.amount?.toPlainString().orEmpty(),
                ),
                style = theme.typography.caption,
                color = theme.colors.olive,
            )
            match.merchant?.let {
                AtharText(
                    text = stringResource(R.string.settings_templates_preview_merchant, it),
                    style = theme.typography.caption,
                    color = theme.colors.muted,
                )
            }
            match.counterparty?.let {
                AtharText(
                    text = stringResource(R.string.settings_templates_preview_counterparty, it),
                    style = theme.typography.caption,
                    color = theme.colors.muted,
                )
            }
            OptionalAnchorWarning(
                anchorFound = match.merchantAnchorFound,
                text = stringResource(R.string.settings_templates_preview_merchant_missing),
            )
            OptionalAnchorWarning(
                anchorFound = match.counterpartyAnchorFound,
                text = stringResource(R.string.settings_templates_preview_counterparty_missing),
            )
        } else {
            AtharText(
                text = if (match.amountAnchorFound) {
                    stringResource(R.string.settings_templates_preview_amount_invalid)
                } else {
                    stringResource(R.string.settings_templates_preview_amount_missing)
                },
                style = theme.typography.caption,
                color = theme.colors.crimson,
            )
        }
    }
}

@Composable
private fun OptionalAnchorWarning(anchorFound: Boolean?, text: String) {
    if (anchorFound == false) {
        AtharText(
            text = text,
            style = AtharTheme.typography.caption,
            color = AtharTheme.colors.dust,
        )
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
                    label = stringResource(R.string.settings_templates_field_before),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                AtharTextField(
                    value = after,
                    onValueChange = onAfterChange,
                    label = stringResource(R.string.settings_templates_field_after),
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
            text = stringResource(R.string.settings_templates_save),
            style = theme.typography.headline,
            color = if (enabled) theme.colors.parchment else theme.colors.muted,
        )
    }
}

@Composable
private fun typeLabel(type: TxType): String = when (type) {
    TxType.EXPENSE -> stringResource(R.string.settings_templates_type_expense)
    TxType.TRANSFER -> stringResource(R.string.settings_templates_type_transfer)
    TxType.INCOME -> stringResource(R.string.settings_templates_type_income)
}
