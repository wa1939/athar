package com.athar.feature.widgets

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.athar.core.domain.repo.WidgetRefresher
import com.athar.feature.widgets.monthly.MonthlyWidget
import com.athar.feature.widgets.pending.PendingWidget
import com.athar.feature.widgets.today.TodayWidget
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GlanceWidgetRefresher @Inject constructor(
    @ApplicationContext private val context: Context,
) : WidgetRefresher {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val requests = Channel<Unit>(capacity = Channel.CONFLATED)

    init {
        scope.launch {
            while (true) {
                requests.receive()
                delay(REFRESH_DELAY_MS)
                while (requests.tryReceive().isSuccess) {
                    // Drain a burst into one widget refresh.
                }
                refreshNow()
            }
        }
    }

    override fun requestRefresh() {
        requests.trySend(Unit)
    }

    override suspend fun refreshNow() {
        updateAtharWidgets(context)
    }

    private companion object {
        const val REFRESH_DELAY_MS = 750L
    }
}

suspend fun updateAtharWidgets(context: Context) {
    MonthlyWidget().updateAll(context)
    TodayWidget().updateAll(context)
    PendingWidget().updateAll(context)
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class WidgetBindingsModule {
    @Binds @Singleton
    abstract fun bindWidgetRefresher(impl: GlanceWidgetRefresher): WidgetRefresher
}
