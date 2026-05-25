package com.athar.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
import com.athar.core.designsystem.R
import com.athar.core.designsystem.theme.AtharTheme
import com.athar.core.designsystem.theme.MinTouchTarget

/**
 * Generic category-picker primitive. The caller passes the list and a select callback.
 *
 * Search filters by both English and Arabic labels, case-insensitively. Selected item
 * gets an ember left rail; everything else is calm parchment-on-surface.
 */
data class AtharPickerItem(
    val key: String,
    val labelEn: String,
    val labelAr: String,
)

@Composable
fun AtharCategoryPicker(
    items: List<AtharPickerItem>,
    selectedKey: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = AtharTheme
    var query by remember { mutableStateOf("") }
    val filtered = remember(items, query) {
        if (query.isBlank()) items
        else items.filter {
            it.labelAr.contains(query, ignoreCase = true) ||
                it.labelEn.contains(query, ignoreCase = true)
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(theme.spacing.s),
    ) {
        AtharTextField(
            value = query,
            onValueChange = { query = it },
            label = stringResource(R.string.category_picker_search_label),
            placeholder = stringResource(R.string.category_picker_search_placeholder),
            modifier = Modifier.fillMaxWidth(),
            keyboardType = KeyboardType.Text,
        )
        LazyColumn {
            items(filtered, key = { it.key }) { item ->
                CategoryRow(
                    item = item,
                    selected = item.key == selectedKey,
                    onClick = { onSelect(item.key) },
                )
            }
        }
    }
}

@Composable
private fun CategoryRow(
    item: AtharPickerItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val theme = AtharTheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(MinTouchTarget)
            .clip(RoundedCornerShape(theme.spacing.s))
            .clickable(onClick = onClick)
            .padding(horizontal = theme.spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .fillMaxHeight()
                .padding(vertical = theme.spacing.s)
                .background(if (selected) theme.colors.ember else theme.colors.parchment),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = theme.spacing.m),
        ) {
            AtharText(text = item.labelAr, style = theme.typography.body)
            AtharText(text = item.labelEn, style = theme.typography.caption, color = theme.colors.muted)
        }
    }
}
