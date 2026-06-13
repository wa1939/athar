package com.athar.feature.widgets

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.athar.feature.widgets.monthly.MonthlyWidget
import com.athar.feature.widgets.pending.PendingWidget
import com.athar.feature.widgets.today.TodayWidget

object AtharWidgets {
    suspend fun updateAll(context: Context) {
        PendingWidget().updateAll(context)
        TodayWidget().updateAll(context)
        MonthlyWidget().updateAll(context)
    }
}
