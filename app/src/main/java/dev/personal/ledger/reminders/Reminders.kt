package dev.personal.ledger.reminders

import dev.personal.ledger.i18n.tr
import android.Manifest
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
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.personal.ledger.LedgerApp
import dev.personal.ledger.MainActivity
import dev.personal.ledger.R
import dev.personal.ledger.domain.Money
import dev.personal.ledger.domain.ObKind
import dev.personal.ledger.domain.Obligations
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Once a day: post due auto-pay items, then remind about variable bills due today/tomorrow and card
 * statements due within 2 days. Tapping a bill reminder opens its amount entry directly (Scenario E).
 */
object Reminders {
    const val CHANNEL = "due"
    const val EXTRA_RECURRING = "recurringId"
    const val EXTRA_CARD = "cardId"

    fun schedule(context: Context) {
        val req = PeriodicWorkRequestBuilder<DueWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(Duration.ofMinutes(15))
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("due-check", ExistingPeriodicWorkPolicy.KEEP, req)
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, tr("Bills & payments due"), NotificationManager.IMPORTANCE_DEFAULT))
        }
    }

    fun canNotify(context: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    class DueWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val app = applicationContext as LedgerApp
            val settings = app.repo.prefs.settings.value
            if (!settings.onboarded) return Result.success()
            app.repo.autoPost()
            if (!settings.remindersEnabled || !canNotify(applicationContext)) return Result.success()
            ensureChannel(applicationContext)
            val data = app.repo.data.filterNotNull().first()
            val today = LocalDate.now()
            val due = Obligations.upcoming(data, today, today.plusDays(2)).filter { it.actionable }
            val nm = NotificationManagerCompat.from(applicationContext)
            for (o in due) {
                val remind = when (o.kind) {
                    ObKind.BILL, ObKind.SUBSCRIPTION -> o.variable && !o.date.isAfter(today.plusDays(1))
                    ObKind.CARD -> true
                    else -> false
                }
                if (!remind) continue
                val intent = Intent(applicationContext, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    o.recurringId?.let { putExtra(EXTRA_RECURRING, it) }
                    o.cardId?.let { putExtra(EXTRA_CARD, it) }
                }
                val pi = PendingIntent.getActivity(applicationContext, o.key.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                val title = if (o.variable) tr("%s is due — enter the amount", o.title) else tr("%s · %s due", o.title, Money.compact(o.amount))
                val body = if (o.variable) tr("Usually about %s. Tap to record it.", Money.compact(o.amount)) else tr("Due %s. Tap to pay.", dev.personal.ledger.domain.Fmt.dayMonth(o.date))
                val n = NotificationCompat.Builder(applicationContext, CHANNEL)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .build()
                @Suppress("MissingPermission")
                nm.notify(o.key.hashCode(), n)
            }
            return Result.success()
        }
    }
}
