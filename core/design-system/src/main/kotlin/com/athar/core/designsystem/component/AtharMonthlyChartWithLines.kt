package com.athar.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import com.athar.core.designsystem.R
import java.util.Locale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.athar.core.common.money.Money
import com.athar.core.designsystem.theme.AtharTheme
import java.time.YearMonth

/**
 * Monthly bars with optional horizontal overlays: a budget-target line (solid) and a
 * computed average line (dashed). Mirrors TMOAP's "Income / Expenses / Savings by Month"
 * dashboard charts where each panel shows monthly bars + budget target + average.
 *
 * Bar color is configurable so the same component renders the olive income chart, the
 * ember expense chart, and the ink savings chart with a single API.
 */
@Composable
fun AtharMonthlyChartWithLines(
    bars: List<AtharMonthlyBar>,
    modifier: Modifier = Modifier,
    barColor: Color,
    targetLine: Money? = null,
    averageLine: Money? = null,
    maxHeight: Dp = 160.dp,
) {
    val theme = AtharTheme
    if (bars.isEmpty()) return
    val barMax = bars.maxOfOrNull { it.amount.amount.toDouble() } ?: 0.0
    val targetVal = targetLine?.amount?.toDouble() ?: 0.0
    val averageVal = averageLine?.amount?.toDouble() ?: 0.0
    val max = listOf(barMax, targetVal, averageVal).max().takeIf { it > 0 } ?: 1.0

    val mutedColor = theme.colors.muted
    val inkColor = theme.colors.ink
    Column(modifier = modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().height(maxHeight)) {
            Row(
                modifier = Modifier.fillMaxSize(),
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
                            .background(barColor),
                    )
                }
            }
            // Overlay: target (solid hairline) + average (dashed hairline). Drawn over bars.
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                if (targetLine != null && targetVal > 0) {
                    val y = h - (targetVal / max * h).toFloat()
                    drawLine(
                        color = mutedColor,
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 2f,
                    )
                }
                if (averageLine != null && averageVal > 0) {
                    val y = h - (averageVal / max * h).toFloat()
                    drawLine(
                        color = inkColor,
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = theme.spacing.xs),
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
        // Legend: dashed line = average, solid hairline = budget target.
        if (averageLine != null || targetLine != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = theme.spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(theme.spacing.s),
            ) {
                if (averageLine != null) {
                    AtharText(
                        text = stringResource(R.string.chart_legend_average),
                        style = theme.typography.caption,
                        color = theme.colors.ink,
                    )
                }
                if (targetLine != null) {
                    AtharText(
                        text = stringResource(R.string.chart_legend_target),
                        style = theme.typography.caption,
                        color = theme.colors.muted,
                    )
                }
            }
        }
    }
}

/**
 * Side-by-side paired bars for period-vs-prior comparison (TMOAP Historical Comparison).
 * Six bars total: 2 income / 2 expense / 2 savings, current bar tinted + prior in muted.
 */
@Composable
fun AtharComparisonBars(
    currentIncome: Money,
    previousIncome: Money,
    currentExpense: Money,
    previousExpense: Money,
    currentSavings: Money,
    previousSavings: Money,
    modifier: Modifier = Modifier,
    height: Dp = 180.dp,
) {
    val theme = AtharTheme
    val all = listOf(
        currentIncome, previousIncome,
        currentExpense, previousExpense,
        currentSavings, previousSavings,
    ).map { it.amount.toDouble() }
    val max = all.max().takeIf { it > 0 } ?: 1.0

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(height),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(theme.spacing.m),
        ) {
            PairBar(label = stringResource(R.string.chart_pair_income), current = currentIncome, previous = previousIncome,
                currentColor = theme.colors.olive, previousColor = theme.colors.muted, max = max,
                modifier = Modifier.weight(1f))
            PairBar(label = stringResource(R.string.chart_pair_expense), current = currentExpense, previous = previousExpense,
                currentColor = theme.colors.ember, previousColor = theme.colors.muted, max = max,
                modifier = Modifier.weight(1f))
            PairBar(label = stringResource(R.string.chart_pair_savings), current = currentSavings, previous = previousSavings,
                currentColor = theme.colors.ink, previousColor = theme.colors.muted, max = max,
                modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun PairBar(
    label: String,
    current: Money,
    previous: Money,
    currentColor: Color,
    previousColor: Color,
    max: Double,
    modifier: Modifier = Modifier,
) {
    val theme = AtharTheme
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(theme.spacing.xs),
        ) {
            val currentFrac = (current.amount.toDouble() / max).coerceIn(0.0, 1.0).toFloat()
            val previousFrac = (previous.amount.toDouble() / max).coerceIn(0.0, 1.0).toFloat()
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(currentFrac.coerceAtLeast(0.02f))
                    .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                    .background(currentColor),
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(previousFrac.coerceAtLeast(0.02f))
                    .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                    .background(previousColor),
            )
        }
        AtharText(
            text = label,
            style = theme.typography.caption,
            color = theme.colors.muted,
            modifier = Modifier.padding(top = theme.spacing.xs),
        )
    }
}

/**
 * Horizontal stacked bar showing the percentage split of categories — the pie-chart
 * replacement (Master Brief §2.4 explicitly bans pies).
 *
 * Each entry contributes a colored segment proportional to its value. Hover/click on
 * a segment to drill in (callback-driven). Caller supplies the colors.
 */
data class AtharSplitSegment(
    val key: String,
    val labelAr: String,
    val labelEn: String,
    val value: Money,
    val color: Color,
)

@Composable
fun AtharProportionBar(
    segments: List<AtharSplitSegment>,
    modifier: Modifier = Modifier,
    height: Dp = 22.dp,
    onSegmentClick: (String) -> Unit = {},
) {
    val theme = AtharTheme
    val total = segments.sumOf { it.value.amount.toDouble() }.takeIf { it > 0 } ?: return
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(height / 2)),
        ) {
            segments.forEach { seg ->
                val frac = (seg.value.amount.toDouble() / total).toFloat().coerceAtLeast(0.01f)
                Box(
                    modifier = Modifier
                        .weight(frac)
                        .fillMaxHeight()
                        .background(seg.color),
                )
            }
        }
        // Legend below: each segment with a colored swatch and its % share.
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = theme.spacing.s),
            verticalArrangement = Arrangement.spacedBy(theme.spacing.xs),
        ) {
            segments.take(8).forEach { seg ->
                val pct = seg.value.amount.toDouble() / total * 100.0
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .padding(end = theme.spacing.xs)
                            .clip(RoundedCornerShape(2.dp))
                            .background(seg.color)
                            .height(10.dp),
                    ) {
                        Box(modifier = Modifier.height(10.dp).background(seg.color)) {
                            Box(modifier = Modifier.padding(horizontal = 6.dp)) {}
                        }
                    }
                    val isArabic = Locale.getDefault().language == "ar"
                    val segLabel = if (isArabic) seg.labelAr else seg.labelEn
                    val pctChar = if (isArabic) "٪" else "%"
                    AtharText(
                        text = "$segLabel · ${"%.1f".format(pct)}$pctChar",
                        style = theme.typography.caption,
                        color = theme.colors.ink,
                        modifier = Modifier.weight(1f),
                    )
                    AtharNumber(money = seg.value, color = theme.colors.muted)
                }
            }
        }
    }
}
