package com.athar.recurring

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.athar.core.domain.repo.RecurringRuleRepository
import com.athar.feature.widgets.AtharWidgets
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import timber.log.Timber

@HiltWorker
class RecurringMaterializeWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val recurringRules: RecurringRuleRepository,
    private val clock: Clock,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result =
        runCatching {
            val today = clock.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            val created = recurringRules.materializeDue(today)
            if (created > 0) {
                AtharWidgets.updateAll(applicationContext)
            }
            Timber.i("[recurring] materialized %d due rule(s) for %s", created, today)
            Result.success()
        }.getOrElse { error ->
            Timber.e(error, "[recurring] materialization failed")
            Result.retry()
        }
}
