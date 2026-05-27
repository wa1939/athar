package com.athar.feature.widgets.pending

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
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.athar.feature.widgets.WidgetColors
import com.athar.feature.widgets.WidgetDataLoader
import com.athar.feature.widgets.appLaunchComponent

class PendingWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                PendingContent(context)
            }
        }
    }

    @Composable
    private fun PendingContent(context: Context) {
        val snapshot by produceState<WidgetDataLoader.PendingSnapshot?>(initialValue = null) {
            value = runCatching { WidgetDataLoader.loadPendingHead(context, max = 3) }.getOrNull()
        }
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(WidgetColors.Parchment)
                .padding(12.dp)
                .clickable(actionStartActivity(appLaunchComponent(context))),
        ) {
            val s = snapshot
            if (s == null) {
                Text(text = "Athar", style = TextStyle(color = ColorProvider(WidgetColors.Muted)))
            } else {
                Column(modifier = GlanceModifier.fillMaxSize()) {
                    Text(
                        text = "${s.totalCount} pending · بانتظار",
                        style = TextStyle(
                            color = if (s.totalCount > 0) ColorProvider(WidgetColors.Ember) else ColorProvider(WidgetColors.Muted),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                    Spacer(modifier = GlanceModifier.height(8.dp))
                    if (s.head.isEmpty()) {
                        Text(
                            text = "All caught up — tap to open",
                            style = TextStyle(
                                color = ColorProvider(WidgetColors.Muted),
                                fontSize = 11.sp,
                            ),
                        )
                    } else {
                        s.head.forEach { tx ->
                            Row(modifier = GlanceModifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                Text(
                                    text = tx.merchant.ifBlank { "?" },
                                    style = TextStyle(
                                        color = ColorProvider(WidgetColors.Ink),
                                        fontSize = 13.sp,
                                    ),
                                    modifier = GlanceModifier.defaultWeight(),
                                )
                                Text(
                                    text = "${tx.amount.amount.toPlainString()} ${tx.amount.currency}",
                                    style = TextStyle(
                                        color = ColorProvider(WidgetColors.Ember),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                    ),
                                )
                            }
                        }
                        if (s.totalCount > s.head.size) {
                            Spacer(modifier = GlanceModifier.height(4.dp))
                            Text(
                                text = "+${s.totalCount - s.head.size} more",
                                style = TextStyle(
                                    color = ColorProvider(WidgetColors.Muted),
                                    fontSize = 11.sp,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

class PendingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PendingWidget()
}

private val Int.sp get() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
