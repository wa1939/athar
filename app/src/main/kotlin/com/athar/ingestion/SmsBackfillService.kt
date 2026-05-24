package com.athar.ingestion

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.athar.core.domain.model.IngestSource
import com.athar.core.domain.model.RawIngestDispatcher
import com.athar.core.domain.model.RawIngestEvent
import com.athar.core.domain.repo.BackfillProgress
import com.athar.core.domain.repo.SmsBackfillTrigger
import com.athar.core.domain.repo.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the device SMS inbox for messages in the last N days and hands each one to the
 * [RawIngestDispatcher] (same path as live SMS broadcasts). Idempotent:
 *
 *  - Records the high-water mark in [UserPreferencesRepository] after a successful run.
 *  - Transaction inserts collapse on `sourceRefId` (unique index) so accidental double-dispatch
 *    of the same SMS rolls into a single row.
 *
 * Master Brief §11 step 6 / Backlog S-15.
 */
@Singleton
class SmsBackfillService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatcher: RawIngestDispatcher,
    private val prefs: UserPreferencesRepository,
    private val clock: Clock,
) : SmsBackfillTrigger {

    private val _progress = MutableStateFlow<BackfillProgress>(BackfillProgress.Idle)
    override val progress: StateFlow<BackfillProgress> = _progress.asStateFlow()

    override suspend fun backfill(daysBack: Int) {
        if (!hasSmsPermissions()) {
            _progress.value = BackfillProgress.Failed("SMS permission not granted")
            return
        }
        _progress.value = BackfillProgress.Running(scanned = 0)
        val lookbackMs = daysBack * 24L * 3600L * 1000L
        val cutoffMs = clock.now().toEpochMilliseconds() - lookbackMs

        var scanned = 0
        var sent = 0
        runCatching {
            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf("_id", "address", "body", "date"),
                "date > ?",
                arrayOf(cutoffMs.toString()),
                "date ASC",
            )?.use { c ->
                val idIdx = c.getColumnIndexOrThrow("_id")
                val addrIdx = c.getColumnIndexOrThrow("address")
                val bodyIdx = c.getColumnIndexOrThrow("body")
                val dateIdx = c.getColumnIndexOrThrow("date")
                while (c.moveToNext()) {
                    scanned += 1
                    if (scanned % PROGRESS_BUMP == 0) {
                        _progress.value = BackfillProgress.Running(scanned)
                    }
                    val id = c.getLong(idIdx)
                    val sender = c.getString(addrIdx).orEmpty()
                    val body = c.getString(bodyIdx).orEmpty()
                    val ms = c.getLong(dateIdx)
                    if (sender.isBlank() || body.isBlank()) continue
                    dispatcher.enqueue(
                        RawIngestEvent(
                            id = UUID.randomUUID().toString(),
                            source = IngestSource.SMS,
                            sender = sender,
                            body = body,
                            receivedAt = Instant.fromEpochMilliseconds(ms),
                            rawId = "inbox-$id",
                        ),
                    )
                    sent += 1
                }
            }
        }
            .onSuccess {
                prefs.setLastSmsBackfillEpochSeconds(clock.now().epochSeconds)
                _progress.value = BackfillProgress.Done(scanned = scanned, sentToPipeline = sent)
                Timber.i("Backfill complete: scanned=%d sent=%d", scanned, sent)
            }
            .onFailure { e ->
                Timber.e(e, "Backfill failed")
                _progress.value = BackfillProgress.Failed(e.message ?: "backfill failed")
            }
    }

    private fun hasSmsPermissions(): Boolean =
        SMS_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    companion object {
        private const val PROGRESS_BUMP = 25
        private val SMS_PERMISSIONS = arrayOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
        )
    }
}
