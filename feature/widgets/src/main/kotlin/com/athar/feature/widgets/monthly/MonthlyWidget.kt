package com.athar.feature.widgets.monthly

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.athar.feature.widgets.R
import com.athar.feature.widgets.WidgetColors
import com.athar.feature.widgets.WidgetDataLoader
import com.athar.feature.widgets.appLaunchComponent
import com.athar.feature.widgets.widgetLocaleContext

class MonthlyWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                MonthlyContent(context)
            }
        }
    }

    @Composable
    private fun MonthlyContent(context: Context) {
        val snapshot by produceState<WidgetDataLoader.MonthlySnapshot?>(initialValue = null) {
            value = runCatching { WidgetDataLoader.loadMonthlySnapshot(context) }.getOrNull()
        }
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(WidgetColors.Parchment)
                .padding(16.dp)
                .clickable(actionStartActivity(appLaunchComponent(context))),
        ) {
            val s = snapshot
            if (s == null) {
                Text(text = "Athar", style = TextStyle(color = ColorProvider(WidgetColors.Muted)))
            } else {
                val strings = context.widgetLocaleContext(s.localeTag)
                Column(modifier = GlanceModifier.fillMaxSize()) {
                    Text(
                        text = strings.getString(R.string.widget_monthly_title, s.yearMonth),
                        style = TextStyle(
                            color = ColorProvider(WidgetColors.Muted),
                            fontSize = 11.sp,
                        ),
                    )
                    Spacer(modifier = GlanceModifier.height(4.dp))
                    Text(
                        text = "${s.netFlow.amount.toPlainString()} ${s.netFlow.currency}",
                        style = TextStyle(
                            color = ColorProvider(WidgetColors.Ink),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    Spacer(modifier = GlanceModifier.height(8.dp))
                    Row(modifier = GlanceModifier.fillMaxWidth()) {
                        Pill(strings.getString(R.string.widget_monthly_income), s.income, WidgetColors.Olive)
                        Spacer(modifier = GlanceModifier.width(8.dp))
                        Pill(strings.getString(R.string.widget_monthly_expense), s.expense, WidgetColors.Ember)
                        Spacer(modifier = GlanceModifier.width(8.dp))
                        Pill(strings.getString(R.string.widget_monthly_net_worth), s.netWorth, WidgetColors.Ink)
                    }
                }
            }
        }
    }

    @Composable
    private fun Pill(label: String, money: com.athar.core.common.money.Money, accent: androidx.compose.ui.graphics.Color) {
        Column(
            modifier = GlanceModifier
                .background(WidgetColors.Surface)
                .padding(8.dp),
        ) {
            Text(
                text = label,
                style = TextStyle(color = ColorProvider(WidgetColors.Muted), fontSize = 10.sp),
            )
            Text(
                text = "${money.amount.toPlainString()} ${money.currency}",
                style = TextStyle(color = ColorProvider(accent), fontSize = 12.sp, fontWeight = FontWeight.Medium),
            )
        }
    }
}

class MonthlyWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MonthlyWidget()
}

private val Int.sp get() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
