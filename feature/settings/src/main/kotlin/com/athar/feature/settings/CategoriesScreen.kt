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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.athar.core.designsystem.component.AtharCard
import com.athar.core.designsystem.component.AtharSegment
import com.athar.core.designsystem.component.AtharSegmentedControl
import com.athar.core.designsystem.component.AtharText
import com.athar.core.designsystem.component.AtharTextField
import com.athar.core.designsystem.theme.AtharTheme
import com.athar.core.designsystem.theme.MinTouchTarget
import com.athar.core.domain.model.Category
import com.athar.core.domain.model.CategoryKind
import java.util.UUID

@Composable
fun CategoriesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CategoriesViewModel = hiltViewModel(),
) {
    val theme = AtharTheme
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Category?>(null) }
    var creating by remember { mutableStateOf(false) }
    var filterKind by remember { mutableStateOf<CategoryKind?>(CategoryKind.EXPENSE) }

    val filtered = when (filterKind) {
        null -> state.items
        else -> state.items.filter { it.kind == filterKind }
    }

    Box(modifier = modifier
        .fillMaxSize()
        .background(theme.colors.parchment)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = theme.spacing.m, vertical = theme.spacing.s),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                BackButton(onClick = onBack)
                AtharText(text = "التصنيفات", style = theme.typography.headline)
                Box(modifier = Modifier.size(MinTouchTarget))  // spacer to balance back button
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = theme.spacing.m),
                verticalArrangement = Arrangement.spacedBy(theme.spacing.s),
            ) {
                AtharSegmentedControl(
                    segments = listOf(
                        AtharSegment(CategoryKind.EXPENSE, "مصاريف"),
                        AtharSegment(CategoryKind.INCOME, "دخل"),
                    ),
                    selected = filterKind ?: CategoryKind.EXPENSE,
                    onSelect = { filterKind = it },
                )

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(theme.spacing.xs),
                ) {
                    items(filtered, key = { it.id }) { cat ->
                        CategoryRowView(
                            category = cat,
                            onEdit = { editing = cat },
                            onMoveUp = { viewModel.moveUp(cat) },
                            onMoveDown = { viewModel.moveDown(cat) },
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(theme.spacing.s))
                        .background(theme.colors.ember)
                        .clickable { creating = true }
                        .padding(theme.spacing.m),
                    contentAlignment = Alignment.Center,
                ) {
                    AtharText(text = "إضافة تصنيف", style = theme.typography.headline, color = theme.colors.parchment)
                }
            }
        }
    }

    if (creating) {
        CategoryEditor(
            initial = null,
            defaultKind = filterKind ?: CategoryKind.EXPENSE,
            onSave = {
                viewModel.save(it)
                creating = false
            },
            onArchive = null,
            onDismiss = { creating = false },
        )
    }

    editing?.let { cat ->
        CategoryEditor(
            initial = cat,
            defaultKind = cat.kind,
            onSave = {
                viewModel.save(it)
                editing = null
            },
            onArchive = {
                viewModel.archive(cat.id)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun BackButton(onClick: () -> Unit) {
    val theme = AtharTheme
    Box(
        modifier = Modifier
            .size(MinTouchTarget)
            .clip(RoundedCornerShape(theme.spacing.s))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AtharText(text = "‹", style = theme.typography.title, color = theme.colors.ink)
    }
}

@Composable
private fun CategoryRowView(
    category: Category,
    onEdit: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    val theme = AtharTheme
    AtharCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onEdit),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                AtharText(
                    text = if (category.archived) "${category.nameAr} (مؤرشف)" else category.nameAr,
                    style = theme.typography.headline,
                    color = if (category.archived) theme.colors.muted else theme.colors.ink,
                )
                AtharText(text = category.name, style = theme.typography.caption, color = theme.colors.muted)
            }
            ArrowButton(text = "▲", onClick = onMoveUp)
            ArrowButton(text = "▼", onClick = onMoveDown)
        }
    }
}

@Composable
private fun ArrowButton(text: String, onClick: () -> Unit) {
    val theme = AtharTheme
    Box(
        modifier = Modifier
            .size(MinTouchTarget)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AtharText(text = text, style = theme.typography.body, color = theme.colors.muted)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryEditor(
    initial: Category?,
    defaultKind: CategoryKind,
    onSave: (Category) -> Unit,
    onArchive: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val theme = AtharTheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var nameAr by remember(initial?.id) { mutableStateOf(initial?.nameAr ?: "") }
    var nameEn by remember(initial?.id) { mutableStateOf(initial?.name ?: "") }
    var kind by remember(initial?.id) { mutableStateOf(initial?.kind ?: defaultKind) }

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
            AtharText(
                text = if (initial == null) "تصنيف جديد" else "تعديل تصنيف",
                style = theme.typography.headline,
            )
            AtharSegmentedControl(
                segments = listOf(
                    AtharSegment(CategoryKind.EXPENSE, "مصروف"),
                    AtharSegment(CategoryKind.INCOME, "دخل"),
                ),
                selected = kind,
                onSelect = { kind = it },
            )
            AtharTextField(
                value = nameAr,
                onValueChange = { nameAr = it },
                label = "الاسم بالعربية",
                modifier = Modifier.fillMaxWidth(),
            )
            AtharTextField(
                value = nameEn,
                onValueChange = { nameEn = it },
                label = "الاسم بالإنجليزية",
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(theme.spacing.s),
            ) {
                if (onArchive != null) {
                    SheetButton(
                        text = if (initial?.archived == true) "(مؤرشف)" else "أرشفة",
                        background = theme.colors.divider,
                        textColor = theme.colors.ink,
                        onClick = onArchive,
                        modifier = Modifier.weight(1f),
                    )
                }
                SheetButton(
                    text = "حفظ",
                    background = theme.colors.ember,
                    textColor = theme.colors.parchment,
                    onClick = {
                        if (nameAr.isNotBlank() && nameEn.isNotBlank()) {
                            val base = initial ?: Category(
                                id = "cat-${UUID.randomUUID()}",
                                name = nameEn,
                                nameAr = nameAr,
                                kind = kind,
                                icon = null,
                                monthlyTarget = null,
                                archived = false,
                                sortOrder = Int.MAX_VALUE,
                            )
                            onSave(base.copy(name = nameEn.trim(), nameAr = nameAr.trim(), kind = kind))
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
