package com.athar.recurring

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.athar.core.domain.calc.BillReminderPlanner
import com.athar.core.domain.repo.RecurringRuleRepository
import com.athar.core.domain.repo.TransactionRepository
import com.athar.core.domain.repo.UserPreferencesRepository
import com.athar.feature.widgets.AtharWidgets
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import timber.log.Timber

@HiltWorker
class RecurringMaterializeWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val recurringRules: RecurringRuleRepository,
    private val transactions: TransactionRepository,
    private val prefs: UserPreferencesRepository,
    private val billReminderNotifier: BillReminderNotifier,
    private val clock: Clock,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result =
        runCatching {
            val today = clock.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            val remindersSent = sendBillReminders(today)
            val created = recurringRules.materializeDue(today)
            if (created > 0) {
                AtharWidgets.updateAll(applicationContext)
            }
            Timber.i(
                "[recurring] sent %d bill reminder(s), materialized %d due rule(s) for %s",
                remindersSent,
                created,
                today,
            )
            Result.success()
        }.getOrElse { error ->
            Timber.e(error, "[recurring] materialization failed")
            Result.retry()
        }

    private suspend fun sendBillReminders(today: kotlinx.datetime.LocalDate): Int {
        if (!prefs.billRemindersEnabled().first()) return 0

        val planned = BillReminderPlanner.plan(
            rules = recurringRules.observeAll().first(),
            pending = transactions.observePending().first(),
            today = today,
        )
        val plannedKeys = planned.mapTo(mutableSetOf()) { it.key }
        val sentKeys = prefs.billReminderSentKeys().first()
        val delivered = planned
            .asSequence()
            .filterNot { it.key in sentKeys }
            .filter { billReminderNotifier.notify(it) }
            .mapTo(mutableSetOf()) { it.key }

        val retained = sentKeys.intersect(plannedKeys) + delivered
        if (retained != sentKeys) {
            prefs.setBillReminderSentKeys(retained)
        }
        return delivered.size
    }
}
