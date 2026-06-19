package com.athar.feature.today

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.res.stringResource
import com.athar.core.domain.model.Category
import com.athar.core.domain.model.Transaction
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toPersistentMap
import java.util.Locale

@Immutable
data class CategoryLabel(
    val name: String,
    val nameAr: String,
)

internal fun List<Category>.toCategoryLabels(): ImmutableMap<String, CategoryLabel> =
    associate { it.id to CategoryLabel(name = it.name, nameAr = it.nameAr) }.toPersistentMap()

internal fun Category.localizedName(): String {
    val isArabic = Locale.getDefault().language == "ar"
    return if (isArabic) {
        nameAr.ifBlank { name }
    } else {
        name.ifBlank { nameAr }
    }
}

@Composable
internal fun transactionCategoryLabel(
    tx: Transaction,
    labels: ImmutableMap<String, CategoryLabel>,
): String {
    val categoryId = tx.categoryId?.trim().takeUnless { it.isNullOrEmpty() }
        ?: return stringResource(R.string.today_uncategorized)
    val label = labels[categoryId] ?: return categoryId
    val isArabic = Locale.getDefault().language == "ar"
    return if (isArabic) {
        label.nameAr.ifBlank { label.name }
    } else {
        label.name.ifBlank { label.nameAr }
    }
}
