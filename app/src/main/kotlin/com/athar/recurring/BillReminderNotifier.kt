package com.athar.recurring

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.athar.MainActivity
import com.athar.R
import com.athar.core.domain.calc.BillReminder
import com.athar.core.domain.calc.BillReminderKind
import dagger.hilt.android.qualifiers.ApplicationContext
import java.math.RoundingMode
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BillReminderNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    @SuppressLint("MissingPermission")
    fun notify(reminder: BillReminder): Boolean {
        if (!canNotify()) return false

        return runCatching {
            ensureChannel()
            val body = body(reminder)
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title(reminder.kind))
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(openAppIntent())
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .build()

            NotificationManagerCompat
                .from(context)
                .notify(reminder.notificationId(), notification)
            true
        }.getOrDefault(false)
    }

    private fun canNotify(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.bill_reminders_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.bill_reminders_channel_description)
        }
        manager.createNotificationChannel(channel)
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            OPEN_APP_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun title(kind: BillReminderKind): String = context.getString(
        when (kind) {
            BillReminderKind.UPCOMING -> R.string.bill_reminder_title_upcoming
            BillReminderKind.DUE_TODAY -> R.string.bill_reminder_title_due_today
            BillReminderKind.MISSED_REVIEW -> R.string.bill_reminder_title_missed
        },
    )

    private fun body(reminder: BillReminder): String = context.getString(
        when (reminder.kind) {
            BillReminderKind.UPCOMING -> R.string.bill_reminder_body_upcoming
            BillReminderKind.DUE_TODAY -> R.string.bill_reminder_body_due_today
            BillReminderKind.MISSED_REVIEW -> R.string.bill_reminder_body_missed
        },
        reminder.label.ifBlank { context.getString(R.string.bill_reminder_fallback_label) },
        reminder.amount.formatForNotification(),
    )

    private fun BillReminder.notificationId(): Int =
        key.hashCode()

    private fun com.athar.core.common.money.Money.formatForNotification(): String {
        val amountText = amount
            .setScale(2, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()
        return "$amountText $currency"
    }

    private companion object {
        private const val CHANNEL_ID = "athar_bill_reminders"
        private const val OPEN_APP_REQUEST_CODE = 1207
    }
}
