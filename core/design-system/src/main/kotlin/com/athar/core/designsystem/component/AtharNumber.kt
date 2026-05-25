package com.athar.core.designsystem.component

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import com.athar.core.common.money.Money
import com.athar.core.designsystem.display.CurrencyCatalog
import com.athar.core.designsystem.display.LocalDisplayCurrency
import com.athar.core.designsystem.theme.AtharTheme
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

/**
 * Money / numeric display with tabular figures.
 *
 * Always use this for amounts. Master Brief §2.4 requires tabular numerals so money
 * columns align. §2.5 specifies "numbers animate by counting up/down, not crossfading" —
 * landmark figures count up over [CountUpDurationMs]; inline numbers render snappy.
 */
@Composable
fun AtharNumber(
    money: Money,
    modifier: Modifier = Modifier,
    landmark: Boolean = false,
    locale: Locale = Locale.getDefault(),
    color: Color? = null,
    animate: Boolean = landmark,
) {
    val style: TextStyle = if (landmark) AtharTheme.typography.moneyLandmark else AtharTheme.typography.money
    val resolvedColor: Color = color ?: AtharTheme.colors.ink
    val displayCurrency = LocalDisplayCurrency.current
    val targetValue = money.amount.toFloat()
    val displayedAmount = if (animate) {
        val animated by animateFloatAsState(
            targetValue = targetValue,
            animationSpec = CountUpSpec,
            label = "AtharNumber.countUp",
        )
        BigDecimal.valueOf(animated.toDouble())
    } else {
        money.amount
    }
    Text(
        text = format(displayedAmount, money.currency, displayCurrency, locale),
        modifier = modifier,
        color = resolvedColor,
        style = style,
    )
}

private const val CountUpDurationMs = 600
private val CountUpSpec: AnimationSpec<Float> = tween(durationMillis = CountUpDurationMs, easing = EaseOut)

private fun format(
    amount: BigDecimal,
    moneyCurrency: String,
    displayCurrency: String,
    locale: Locale,
): String {
    val formatter = NumberFormat.getNumberInstance(locale).apply {
        maximumFractionDigits = 2
        minimumFractionDigits = 0
        isGroupingUsed = true
    }
    val sign = if (amount.signum() < 0) "-" else ""
    val abs = amount.abs()
    val number = formatter.format(abs)
    // The Money value keeps its own currency code (e.g., a SAR-stored SMS transaction
    // stays SAR even when the user picked USD as display). Render with the Money's
    // currency symbol — that's the truth. If the user wants foreign amounts converted
    // they'll switch the underlying transactions, not the formatter.
    val symbol = CurrencyCatalog.symbolOf(moneyCurrency)
    return "$sign$number $symbol"
}

@Preview
@Composable
private fun PreviewLandmark() {
    AtharTheme {
        AtharNumber(money = Money.of("12847.00"), landmark = true)
    }
}

@Preview
@Composable
private fun PreviewInline() {
    AtharTheme {
        AtharNumber(money = Money.of("200.00"))
    }
}
