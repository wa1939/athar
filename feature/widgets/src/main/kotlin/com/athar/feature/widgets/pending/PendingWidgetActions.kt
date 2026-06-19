package com.athar.feature.widgets.pending

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.athar.core.domain.model.TxStatus
import com.athar.core.domain.repo.TransactionRepository
import com.athar.core.domain.repo.WidgetRefresher
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

internal object PendingWidgetActionParams {
    val TransactionId = ActionParameters.Key<String>("transaction_id")
}

class ConfirmPendingAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        PendingTransactionActionWorker.enqueue(context, parameters, TxStatus.CONFIRMED)
    }
}

class DismissPendingAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        PendingTransactionActionWorker.enqueue(context, parameters, TxStatus.DISMISSED)
    }
}

@HiltWorker
class PendingTransactionActionWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val transactions: TransactionRepository,
    private val widgetRefresher: WidgetRefresher,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val txId = inputData.getString(KEY_TRANSACTION_ID) ?: return Result.failure()
        val status = inputData.getString(KEY_STATUS)
            ?.let { runCatching { TxStatus.valueOf(it) }.getOrNull() }
            ?: return Result.failure()
        return runCatching {
            transactions.setStatus(txId, status)
            widgetRefresher.refreshNow()
            Result.success()
        }.getOrElse {
            Result.retry()
        }
    }

    companion object {
        private const val KEY_TRANSACTION_ID = "transaction_id"
        private const val KEY_STATUS = "status"

        fun enqueue(context: Context, parameters: ActionParameters, status: TxStatus) {
            val txId = parameters[PendingWidgetActionParams.TransactionId] ?: return
            val request = OneTimeWorkRequestBuilder<PendingTransactionActionWorker>()
                .setInputData(
                    workDataOf(
                        KEY_TRANSACTION_ID to txId,
                        KEY_STATUS to status.name,
                    ),
                )
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                "pending-widget-action-$txId",
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}
