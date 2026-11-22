package io.github.abusaeedbd.unseen.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.abusaeedbd.unseen.R
import io.github.abusaeedbd.unseen.ui.MainActivity
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit

/** Every Friday evening: "Your week with Unseen" notification with the key numbers. */
object WeeklyReport {
    private const val WORK = "weekly-report"
    private const val CHANNEL = "reports"
    private const val NOTIFICATION_ID = 20
    const val ACTION_SHARE = "io.github.abusaeedbd.unseen.SHARE_WEEK"

    fun schedule(context: Context) {
        val now = LocalDateTime.now()
        var next = now.with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY)).withHour(20).withMinute(0).withSecond(0)
        if (!next.isAfter(now)) next = next.plusWeeks(1)
        val delay = Duration.between(now, next).toMinutes()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<WeeklyReportWorker>(7, TimeUnit.DAYS)
                .setInitialDelay(delay, TimeUnit.MINUTES)
                .build(),
        )
    }

    fun notify(context: Context) {
        val week = Stats.week()
        if (week.counts.total == 0) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Weekly report", NotificationManager.IMPORTANCE_DEFAULT))

        val top = week.topApps.firstOrNull()
        val text = buildString {
            append("${"%,d".format(week.counts.total)} trackers and ads blocked")
            if (week.counts.scams > 0) append(", ${week.counts.scams} dangerous sites stopped")
            append(". About ${formatBytes(week.counts.dataSavedBytes)} of data saved.")
            if (top != null) append(" ${AppNames.label(context, top.first)} tried the most (${top.second}×).")
        }
        val open = PendingIntent.getActivity(
            context, 10, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val share = PendingIntent.getActivity(
            context, 11,
            Intent(context, MainActivity::class.java).setAction(ACTION_SHARE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_unseen)
            .setContentTitle("Your week with Unseen")
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(context, R.drawable.ic_stat_unseen), "Share", share
                ).build()
            )
            .setAutoCancel(true)
            .build()
        try {
            nm.notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Notifications not allowed.
        }
    }
}

class WeeklyReportWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        WeeklyReport.notify(applicationContext)
        return Result.success()
    }
}

/** "12.4 MB", "850 KB". */
fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000.0)
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
    else -> "${bytes / 1_000} KB"
}
