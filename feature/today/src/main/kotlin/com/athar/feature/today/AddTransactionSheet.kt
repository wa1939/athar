package com.athar.feature.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.athar.core.designsystem.component.AtharAmountField
import com.athar.core.designsystem.component.AtharCategoryPicker
import com.athar.core.designsystem.component.AtharPickerItem
import com.athar.core.designsystem.component.AtharSegment
import com.athar.core.designsystem.component.AtharSegmentedControl
import com.athar.core.designsystem.component.AtharText
import com.athar.core.designsystem.component.AtharTextField
import com.athar.core.designsystem.theme.AtharTheme
import com.athar.core.designsystem.theme.MinTouchTarget
import com.athar.core.domain.model.TxType
import kotlinx.coroutines.flow.filterIsInstance

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionSheet(
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AddTransactionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(viewModel) {
        viewModel.events
            .filterIsInstance<AddTransactionResult.Saved>()
            .collect { onSaved() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AtharTheme.colors.parchment,
        contentColor = AtharTheme.colors.ink,
        modifier = modifier,
    ) {
        AddTransactionForm(
            state = state,
            onEvent = viewModel::onEvent,
            contentPadding = PaddingValues(
                start = AtharTheme.spacing.m,
                end = AtharTheme.spacing.m,
                bottom = AtharTheme.spacing.l,
            ),
        )
    }
}

@Composable
private fun AddTransactionForm(
    state: AddTransactionState,
    onEvent: (AddTransactionEvent) -> Unit,
    contentPadding: PaddingValues,
) {
    val theme = AtharTheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 400.dp)
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(theme.spacing.m),
    ) {
        AtharText(text = stringResource(R.string.add_tx_title), style = theme.typography.headline)

        AtharSegmentedControl(
            segments = listOf(
                AtharSegment(TxType.EXPENSE, stringResource(R.string.add_tx_segment_expense)),
                AtharSegment(TxType.INCOME, stringResource(R.string.add_tx_segment_income)),
            ),
            selected = state.type,
            onSelect = { onEvent(AddTransactionEvent.SetType(it)) },
        )

        AtharAmountField(
            value = state.amount,
            onValueChange = { onEvent(AddTransactionEvent.SetAmount(it)) },
            modifier = Modifier.fillMaxWidth(),
            isError = state.validationError == ValidationError.AMOUNT_REQUIRED ||
                state.validationError == ValidationError.AMOUNT_INVALID,
            supportingText = when (state.validationError) {
                ValidationError.AMOUNT_REQUIRED -> stringResource(R.string.add_tx_error_amount_required)
                ValidationError.AMOUNT_INVALID -> stringResource(R.string.add_tx_error_amount_invalid)
                else -> null
            },
        )

        AtharTextField(
            value = state.merchant,
            onValueChange = { onEvent(AddTransactionEvent.SetMerchant(it)) },
            label = stringResource(R.string.add_tx_label_merchant),
            placeholder = stringResource(R.string.add_tx_placeholder_merchant),
            modifier = Modifier.fillMaxWidth(),
            isError = state.validationError == ValidationError.MERCHANT_REQUIRED,
            supportingText = if (state.validationError == ValidationError.MERCHANT_REQUIRED) stringResource(R.string.add_tx_error_merchant_required) else null,
        )

        SuggestionStrip(
            suggestions = state.visibleMerchantSuggestions,
            onSelect = { onEvent(AddTransactionEvent.ApplySuggestion(it)) },
        )

        AtharText(
            text = if (state.validationError == ValidationError.CATEGORY_REQUIRED) stringResource(R.string.add_tx_error_category_required) else stringResource(R.string.add_tx_label_category),
            style = theme.typography.caption,
            color = if (state.validationError == ValidationError.CATEGORY_REQUIRED) theme.colors.crimson else theme.colors.muted,
        )

        AtharCategoryPicker(
            items = state.categoriesForType.map {
                AtharPickerItem(key = it.id, labelEn = it.name, labelAr = it.nameAr)
            },
            selectedKey = state.selectedCategoryId,
            onSelect = { onEvent(AddTransactionEvent.SelectCategory(it)) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 200.dp, max = 320.dp),
        )

        AtharTextField(
            value = state.notes,
            onValueChange = { onEvent(AddTransactionEvent.SetNotes(it)) },
            label = stringResource(R.string.add_tx_label_notes),
            modifier = Modifier.fillMaxWidth(),
            singleLine = false,
        )

        SaveButton(
            isSaving = state.isSaving,
            onClick = { onEvent(AddTransactionEvent.Save) },
        )
    }
}

@Composable
private fun SuggestionStrip(
    suggestions: List<ManualEntrySuggestion>,
    onSelect: (ManualEntrySuggestion) -> Unit,
) {
    if (suggestions.isEmpty()) return
    val theme = AtharTheme
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(theme.spacing.s),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(
            items = suggestions,
            key = { it.key },
        ) { suggestion ->
            SuggestionChip(
                suggestion = suggestion,
                onClick = { onSelect(suggestion) },
            )
        }
    }
}

@Composable
private fun SuggestionChip(
    suggestion: ManualEntrySuggestion,
    onClick: () -> Unit,
) {
    val theme = AtharTheme
    Box(
        modifier = Modifier
            .heightIn(min = MinTouchTarget)
            .clip(RoundedCornerShape(theme.spacing.s))
            .background(theme.colors.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = theme.spacing.m, vertical = theme.spacing.s),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            AtharText(
                text = suggestion.merchant,
                style = theme.typography.body,
                color = theme.colors.ink,
            )
            suggestion.amountInput?.let { amount ->
                AtharText(
                    text = amount,
                    style = theme.typography.caption,
                    color = if (suggestion.type == TxType.INCOME) theme.colors.olive else theme.colors.ember,
                )
            }
        }
    }
}

@Composable
private fun SaveButton(
    isSaving: Boolean,
    onClick: () -> Unit,
) {
    val theme = AtharTheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget)
            .clip(RoundedCornerShape(theme.spacing.m))
            .background(theme.colors.ember)
            .clickable(enabled = !isSaving, onClick = onClick)
            .padding(theme.spacing.m),
        contentAlignment = Alignment.Center,
    ) {
        AtharText(
            text = if (isSaving) stringResource(R.string.add_tx_saving) else stringResource(R.string.add_tx_save),
            style = theme.typography.headline,
            color = theme.colors.parchment,
        )
    }
}
