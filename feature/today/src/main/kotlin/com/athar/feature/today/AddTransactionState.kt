package com.athar.feature.today

import com.athar.core.domain.model.Category
import com.athar.core.domain.model.TxType
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.LocalDate

data class AddTransactionState(
    val amount: String,
    val merchant: String,
    val notes: String,
    val type: TxType,
    val date: LocalDate,
    val selectedCategoryId: String?,
    val expenseCategories: ImmutableList<Category>,
    val incomeCategories: ImmutableList<Category>,
    val isSaving: Boolean,
    val validationError: ValidationError?,
) {
    val categoriesForType: ImmutableList<Category>
        get() = if (type == TxType.INCOME) incomeCategories else expenseCategories

    companion object {
        fun initial(today: LocalDate): AddTransactionState = AddTransactionState(
            amount = "",
            merchant = "",
            notes = "",
            type = TxType.EXPENSE,
            date = today,
            selectedCategoryId = null,
            expenseCategories = persistentListOf(),
            incomeCategories = persistentListOf(),
            isSaving = false,
            validationError = null,
        )
    }
}

enum class ValidationError {
    AMOUNT_REQUIRED,
    AMOUNT_INVALID,
    MERCHANT_REQUIRED,
    CATEGORY_REQUIRED,
}

sealed interface AddTransactionEvent {
    data class SetAmount(val value: String) : AddTransactionEvent
    data class SetMerchant(val value: String) : AddTransactionEvent
    data class SetNotes(val value: String) : AddTransactionEvent
    data class SetType(val type: TxType) : AddTransactionEvent
    data class SetDate(val date: LocalDate) : AddTransactionEvent
    data class SelectCategory(val categoryId: String) : AddTransactionEvent
    data object Save : AddTransactionEvent
}
