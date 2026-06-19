package com.athar.recurring

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object RecurringWorkScheduler {
    private const val STARTUP_WORK_NAME = "recurring-rule-materializer-startup"
    private const val PERIODIC_WORK_NAME = "recurring-rule-materializer-daily"
    private const val TAG = "recurring-rule-materializer"

    fun schedule(context: Context) {
        val appContext = context.applicationContext
        val workManager = WorkManager.getInstance(appContext)

        val startup = OneTimeWorkRequestBuilder<RecurringMaterializeWorker>()
            .addTag(TAG)
            .build()
        workManager.enqueueUniqueWork(STARTUP_WORK_NAME, ExistingWorkPolicy.KEEP, startup)

        val daily = PeriodicWorkRequestBuilder<RecurringMaterializeWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(6, TimeUnit.HOURS)
            .addTag(TAG)
            .build()
        workManager.enqueueUniquePeriodicWork(PERIODIC_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, daily)
    }
}
