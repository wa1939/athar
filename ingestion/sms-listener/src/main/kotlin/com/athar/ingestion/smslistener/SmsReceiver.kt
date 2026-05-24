package com.athar.ingestion.smslistener

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.athar.core.domain.model.IngestSource
import com.athar.core.domain.model.RawIngestDispatcher
import com.athar.core.domain.model.RawIngestEvent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.datetime.Instant
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

/**
 * Android BroadcastReceiver bridge → emits [RawIngestEvent].
 *
 * Stub for Phase 2 ticket S-13. Actual queue handoff (to a foreground service or
 * WorkManager job) will land alongside the parser. For now, this file establishes
 * the module shape and the IngestSource.SMS contract.
 */
@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {

    @Inject lateinit var dispatcher: RawIngestDispatcher

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        // Multi-part SMS bodies concatenate by sender within a small window.
        val grouped = messages.groupBy { it.originatingAddress.orEmpty() }
        grouped.forEach { (sender, parts) ->
            val body = parts.joinToString(separator = "") { it.messageBody.orEmpty() }
            val event = RawIngestEvent(
                id = UUID.randomUUID().toString(),
                source = IngestSource.SMS,
                sender = sender,
                body = body,
                receivedAt = Instant.fromEpochMilliseconds(parts.first().timestampMillis),
                rawId = parts.joinToString("|") { it.timestampMillis.toString() },
            )
            Timber.d("Received SMS from %s: %d chars", sender, body.length)
            dispatcher.enqueue(event)
        }
    }
}

