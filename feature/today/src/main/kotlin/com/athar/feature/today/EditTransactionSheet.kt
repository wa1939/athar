package com.athar.feature.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.athar.core.domain.model.Category
import com.athar.core.domain.model.CategoryKind
import com.athar.core.domain.model.Transaction
import com.athar.core.domain.model.TxType
import com.athar.core.domain.repo.CategoryRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import javax.inject.Inject
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.launch

/**
 * State for the edit-mode sheet. The original transaction is captured so we can detect
 * category changes for the "always categorize X as Y?" learning prompt (Master Brief §4.7).
 */
data class EditTransactionState(
    val original: Transaction,
    val amount: String,
    val merchant: String,
    val notes: String,
    val type: TxType,
    val selectedCategoryId: String?,
    val expenseCategories: ImmutableList<Category>,
    val incomeCategories: ImmutableList<Category>,
) {
    val categoriesForType: ImmutableList<Category>
        get() = if (type == TxType.INCOME) incomeCategories else expenseCategories

    val categoryChanged: Boolean
        get() = original.categoryId != selectedCategoryId
}

@HiltViewModel
class EditTransactionViewModel @Inject constructor(
    private val categories: CategoryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<EditTransactionState?>(null)
    val state: StateFlow<EditTransactionState?> = _state.asStateFlow()

    fun load(tx: Transaction) {
        viewModelScope.launch {
            categories.observeAll().collect { all ->
                val expense = all.filter { it.kind == CategoryKind.EXPENSE }.toImmutableList()
                val income = all.filter { it.kind == CategoryKind.INCOME }.toImmutableList()
                _state.update { existing ->
                    existing?.copy(expenseCategories = expense, incomeCategories = income)
                        ?: EditTransactionState(
                            original = tx,
                            amount = tx.amount.amount.toPlainString(),
                            merchant = tx.merchant,
                            notes = tx.notes.orEmpty(),
                            type = tx.type,
                            selectedCategoryId = tx.categoryId,
                            expenseCategories = expense,
                            incomeCategories = income,
                        )
                }
            }
        }
    }

    fun setAmount(v: String) = _state.update { it?.copy(amount = v) }
    fun setMerchant(v: String) = _state.update { it?.copy(merchant = v) }
    fun setNotes(v: String) = _state.update { it?.copy(notes = v) }
    fun setType(t: TxType) = _state.update { it?.copy(type = t, selectedCategoryId = null) }
    fun selectCategory(id: String) = _state.update { it?.copy(selectedCategoryId = id) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTransactionSheet(
    transaction: Transaction,
    onDismiss: () -> Unit,
    onSave: (Transaction, learnRule: Boolean) -> Unit,
    onDelete: (String) -> Unit,
    viewModel: EditTransactionViewModel = hiltViewModel(),
) {
    val theme = AtharTheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var pendingLearn by remember { mutableStateOf<EditTransactionState?>(null) }

    LaunchedEffect(transaction.id) { viewModel.load(transaction) }

    val s = viewModel.state.collectAsStateWithLifecycle().value ?: return

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
            AtharText(text = "تعديل حركة", style = theme.typography.headline)

            AtharSegmentedControl(
                segments = listOf(
                    AtharSegment(TxType.EXPENSE, "مصروف"),
                    AtharSegment(TxType.INCOME, "دخل"),
                ),
                selected = s.type,
                onSelect = { viewModel.setType(it) },
            )

            AtharAmountField(
                value = s.amount,
                onValueChange = { viewModel.setAmount(it) },
                modifier = Modifier.fillMaxWidth(),
            )

            AtharTextField(
                value = s.merchant,
                onValueChange = { viewModel.setMerchant(it) },
                label = "التاجر",
                modifier = Modifier.fillMaxWidth(),
            )

            AtharText(text = "التصنيف", style = theme.typography.caption, color = theme.colors.muted)
            AtharCategoryPicker(
                items = s.categoriesForType.map {
                    AtharPickerItem(key = it.id, labelEn = it.name, labelAr = it.nameAr)
                },
                selectedKey = s.selectedCategoryId,
                onSelect = { viewModel.selectCategory(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp, max = 280.dp),
            )

            AtharTextField(
                value = s.notes,
                onValueChange = { viewModel.setNotes(it) },
                label = "ملاحظات",
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(theme.spacing.s),
            ) {
                SheetButton(
                    text = "حذف",
                    background = theme.colors.crimson,
                    textColor = theme.colors.parchment,
                    onClick = { onDelete(s.original.id) },
                    modifier = Modifier.weight(1f),
                )
                SheetButton(
                    text = "حفظ",
                    background = theme.colors.ember,
                    textColor = theme.colors.parchment,
                    onClick = {
                        if (s.categoryChanged && s.selectedCategoryId != null) {
                            pendingLearn = s
                        } else {
                            commit(s, learnRule = false, onSave)
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    pendingLearn?.let { stateForLearn ->
        val newCatLabel = stateForLearn.categoriesForType
            .firstOrNull { it.id == stateForLearn.selectedCategoryId }
            ?.nameAr
            .orEmpty()
        AlertDialog(
            onDismissRequest = { pendingLearn = null },
            title = { AtharText(text = "تعلّم من التصنيف؟", style = theme.typography.headline) },
            text = {
                AtharText(
                    text = "هل تريد أن يصنّف أثر دائمًا «${stateForLearn.merchant}» كـ«$newCatLabel»؟",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    commit(stateForLearn, learnRule = true, onSave)
                    pendingLearn = null
                }) { AtharText(text = "دائمًا", color = theme.colors.ember) }
            },
            dismissButton = {
                TextButton(onClick = {
                    commit(stateForLearn, learnRule = false, onSave)
                    pendingLearn = null
                }) { AtharText(text = "هذه المرة فقط", color = theme.colors.muted) }
            },
            containerColor = theme.colors.parchment,
        )
    }
}

private fun commit(
    s: EditTransactionState,
    learnRule: Boolean,
    onSave: (Transaction, Boolean) -> Unit,
) {
    val amount = runCatching { BigDecimal(s.amount) }.getOrNull() ?: s.original.amount.amount
    val updated = s.original.copy(
        amount = com.athar.core.common.money.Money.of(amount),
        merchant = s.merchant.trim(),
        merchantNormalized = s.merchant.lowercase().trim(),
        notes = s.notes.takeIf { it.isNotBlank() },
        type = s.type,
        categoryId = s.selectedCategoryId,
    )
    onSave(updated, learnRule)
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
            .heightIn(min = MinTouchTarget)
            .clip(RoundedCornerShape(theme.spacing.s))
            .background(background)
            .clickable(onClick = onClick)
            .padding(theme.spacing.m),
        contentAlignment = Alignment.Center,
    ) {
        AtharText(text = text, style = theme.typography.headline, color = textColor)
    }
}

