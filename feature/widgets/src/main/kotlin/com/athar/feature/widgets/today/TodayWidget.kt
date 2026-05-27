package com.athar.feature.widgets.today

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
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.athar.feature.widgets.WidgetColors
import com.athar.feature.widgets.WidgetDataLoader
import com.athar.feature.widgets.appLaunchComponent

class TodayWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                TodayContent(context)
            }
        }
    }

    @Composable
    private fun TodayContent(context: Context) {
        val snapshot by produceState<WidgetDataLoader.TodaySnapshot?>(initialValue = null) {
            value = runCatching { WidgetDataLoader.loadTodaySnapshot(context) }.getOrNull()
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
                Column(modifier = GlanceModifier.fillMaxSize()) {
                    Text(
                        text = "اليوم · Today",
                        style = TextStyle(
                            color = ColorProvider(WidgetColors.Muted),
                            fontSize = 11.sp,
                        ),
                    )
                    Spacer(modifier = GlanceModifier.height(4.dp))
                    Text(
                        text = "${s.todayNet.amount.toPlainString()} ${s.todayNet.currency}",
                        style = TextStyle(
                            color = ColorProvider(WidgetColors.Ink),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    if (s.pendingCount > 0) {
                        Spacer(modifier = GlanceModifier.height(6.dp))
                        Text(
                            text = "${s.pendingCount} pending",
                            style = TextStyle(
                                color = ColorProvider(WidgetColors.Ember),
                                fontSize = 12.sp,
                            ),
                        )
                    }
                }
            }
        }
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

private val Int.sp get() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
