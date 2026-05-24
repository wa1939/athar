package com.athar.ingestion.notificationlistener

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.athar.core.domain.model.IngestSource
import com.athar.core.domain.model.RawIngestDispatcher
import com.athar.core.domain.model.RawIngestEvent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.datetime.Instant
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

/**
 * Path B — Play-Store-compliant ingestion via bank-app push notifications.
 *
 * Master Brief §5.4. Stub for ticket B-04. The `BankPackageFilter` is the only knob
 * that distinguishes a bank notification from any other app's notification.
 */
@AndroidEntryPoint
class BankNotificationListener : NotificationListenerService() {

    @Inject lateinit var dispatcher: RawIngestDispatcher
    @Inject lateinit var bankPackageFilter: BankPackageFilter

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!bankPackageFilter.isBankPackage(sbn.packageName)) return

        val title = sbn.notification.extras.getCharSequence("android.title")?.toString().orEmpty()
        val text = sbn.notification.extras.getCharSequence("android.text")?.toString().orEmpty()
        val body = if (title.isNotEmpty()) "$title\n$text" else text
        if (body.isBlank()) return

        val event = RawIngestEvent(
            id = UUID.randomUUID().toString(),
            source = IngestSource.NOTIFICATION,
            sender = sbn.packageName,
            body = body,
            receivedAt = Instant.fromEpochMilliseconds(sbn.postTime),
            rawId = sbn.key,
        )
        Timber.d("Notification from %s: %d chars", sbn.packageName, body.length)
        dispatcher.enqueue(event)
    }
}

/** Pluggable filter — defaults to a static set; could be user-configurable in Settings later. */
fun interface BankPackageFilter {
    fun isBankPackage(packageName: String): Boolean
}
