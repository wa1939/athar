package com.athar.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.athar.core.common.money.Money
import com.athar.core.designsystem.theme.AtharTheme

/**
 * Horizontal bar chart. No axes, no legend — just a sorted list of rows with proportional bars.
 *
 * Single accent: every bar is the ember color. Master Brief §2.4 — one accent per screen.
 * Sorted desc by value; consumer is expected to pass already-sorted data with [items].
 */
data class AtharBarItem(
    val key: String,
    val labelAr: String,
    val labelEn: String? = null,
    val value: Money,
)

@Composable
fun AtharBarChart(
    items: List<AtharBarItem>,
    modifier: Modifier = Modifier,
    barColor: Color? = null,
    onItemClick: ((key: String) -> Unit)? = null,
) {
    val theme = AtharTheme
    if (items.isEmpty()) return
    val max = items.maxOfOrNull { it.value.amount } ?: java.math.BigDecimal.ONE

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(theme.spacing.s),
    ) {
        items.forEach { item ->
            val fraction = (item.value.amount.toDouble() / max.toDouble())
                .coerceIn(0.0, 1.0)
                .toFloat()
            val rowModifier = if (onItemClick != null) {
                Modifier
                    .fillMaxWidth()
                    .clickable { onItemClick(item.key) }
                    .padding(vertical = theme.spacing.xs)
            } else {
                Modifier.fillMaxWidth()
            }
            Column(
                modifier = rowModifier,
                verticalArrangement = Arrangement.spacedBy(theme.spacing.xs),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AtharText(text = item.labelAr, style = theme.typography.body)
                    AtharNumber(money = item.value)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(theme.colors.divider),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(barColor ?: theme.colors.ember),
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun PreviewBarChart() {
    AtharTheme {
        AtharBarChart(
            items = listOf(
                AtharBarItem("groceries", "بقالة", "Groceries", Money.of("2400")),
                AtharBarItem("restaurants", "مطاعم", "Restaurants", Money.of("1800")),
                AtharBarItem("coffee", "قهوة", "Coffee", Money.of("1000")),
                AtharBarItem("travel", "سفر", "Travel", Money.of("400")),
            ),
            modifier = Modifier.padding(16.dp),
        )
    }
}
