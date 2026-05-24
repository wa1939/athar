package com.athar.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.athar.core.common.money.Money
import com.athar.core.designsystem.theme.AtharTheme
import java.time.YearMonth

/**
 * Vertical bars over a fixed 12-month window. Each column = one month; height ∝ amount.
 *
 * Used by the Trends drilldown sheet — Master Brief §3 (Trends → category drill-into-time-trend).
 * No axes, no grid lines — just calm bars on the parchment.
 */
data class AtharMonthlyBar(
    val month: YearMonth,
    val amount: Money,
)

@Composable
fun AtharMonthlyChart(
    bars: List<AtharMonthlyBar>,
    modifier: Modifier = Modifier,
    maxHeight: androidx.compose.ui.unit.Dp = 160.dp,
) {
    val theme = AtharTheme
    if (bars.isEmpty()) return
    val max = bars.maxOfOrNull { it.amount.amount.toDouble() }?.takeIf { it > 0 } ?: 1.0

    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(maxHeight),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(theme.spacing.xs),
        ) {
            bars.forEach { bar ->
                val fraction = (bar.amount.amount.toDouble() / max).coerceIn(0.0, 1.0).toFloat()
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(fraction.coerceAtLeast(0.02f))
                        .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                        .background(theme.colors.ember),
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = theme.spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(theme.spacing.xs),
        ) {
            bars.forEach { bar ->
                AtharText(
                    text = bar.month.monthValue.toString().padStart(2, '0'),
                    style = theme.typography.caption,
                    color = theme.colors.muted,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Preview
@Composable
private fun PreviewMonthly() {
    AtharTheme {
        AtharMonthlyChart(
            bars = (1..12).map { m ->
                AtharMonthlyBar(
                    month = YearMonth.of(2026, m),
                    amount = Money.of((100 + (m * 23) % 700).toString()),
                )
            },
        )
    }
}
