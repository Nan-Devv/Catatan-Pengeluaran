package com.example.notification

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
import com.example.MainActivity
import com.example.R
import com.example.data.local.DailySpendDao
import com.example.data.model.BudgetPeriod
import com.example.utils.CurrencyFormatter
import com.example.utils.DateUtils

object NotificationHelper {

    private const val CHANNEL_BUDGET_ALERTS = "dailyspend_budget_alerts"
    private const val CHANNEL_TRANSACTIONS = "dailyspend_transactions"
    private const val CHANNEL_SUMMARY = "dailyspend_summary"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val budgetChannel = NotificationChannel(
                CHANNEL_BUDGET_ALERTS,
                "Budget Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when spending crosses 50%, 80%, or 100% of a budget."
            }

            val txChannel = NotificationChannel(
                CHANNEL_TRANSACTIONS,
                "Detected Transactions",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for automatically detected transactions."
            }

            val summaryChannel = NotificationChannel(
                CHANNEL_SUMMARY,
                "Daily Summary",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Daily personal finance summary reminders."
            }

            manager.createNotificationChannel(budgetChannel)
            manager.createNotificationChannel(txChannel)
            manager.createNotificationChannel(summaryChannel)
        }
    }

    fun hasPostNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun isNotificationListenerEnabled(context: Context): Boolean {
        return NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName)
    }

    suspend fun evaluateAndNotifyBudgets(context: Context, dao: DailySpendDao) {
        val settings = dao.getSettingsOnce() ?: return
        if (!settings.budgetNotificationsEnabled) return
        if (!hasPostNotificationPermission(context)) return

        ensureChannels(context)
        val budgets = dao.getBudgetsOnce()
        if (budgets.isEmpty()) return

        val now = System.currentTimeMillis()
        for (budget in budgets) {
            if (budget.amountLimit <= 0L) continue
            val (start, cycleKey) = when (budget.period) {
                BudgetPeriod.MONTHLY -> DateUtils.startOfCurrentMonth(now) to DateUtils.currentMonthCycleKey(now)
                BudgetPeriod.WEEKLY -> DateUtils.startOfCurrentWeek(now) to DateUtils.currentWeekCycleKey(now)
            }
            val spent = dao.getConfirmedExpenseSum(start, now, budget.categoryId)
            val ratio = (spent * 100L) / budget.amountLimit
            val newLevel = when {
                ratio >= 100L -> 100
                ratio >= 80L -> 80
                ratio >= 50L -> 50
                else -> 0
            }

            val previousLevelInCycle = if (budget.lastAlertCycleKey == cycleKey) budget.lastAlertLevel else 0
            if (newLevel > previousLevelInCycle) {
                dao.updateBudget(
                    budget.copy(
                        lastAlertCycleKey = cycleKey,
                        lastAlertLevel = newLevel
                    )
                )
                val formattedSpent = CurrencyFormatter.format(spent, settings.currencyCode)
                val formattedLimit = CurrencyFormatter.format(budget.amountLimit, settings.currencyCode)
                val title = when (newLevel) {
                    100 -> "Budget Exceeded: ${budget.name}"
                    80 -> "80% Budget Reached: ${budget.name}"
                    else -> "50% Budget Reached: ${budget.name}"
                }
                val body = "$formattedSpent used of $formattedLimit (${ratio.coerceAtMost(999)}%)."
                postLocalNotification(
                    context = context,
                    channelId = CHANNEL_BUDGET_ALERTS,
                    notificationId = (budget.id.toInt() * 10) + newLevel,
                    title = title,
                    body = body
                )
            }
        }
    }

    fun notifyPendingReviewTransaction(
        context: Context,
        sourceApp: String,
        amount: Long,
        currencyCode: String
    ) {
        if (!hasPostNotificationPermission(context)) return
        ensureChannels(context)
        val formatted = CurrencyFormatter.format(amount, currencyCode)
        postLocalNotification(
            context = context,
            channelId = CHANNEL_TRANSACTIONS,
            notificationId = (System.currentTimeMillis() % 100000).toInt(),
            title = "Review Detected Transaction ($sourceApp)",
            body = "Detected $formatted. Tap to confirm category and account."
        )
    }

    fun notifyDailySummary(
        context: Context,
        todayExpense: Long,
        todayIncome: Long,
        currencyCode: String
    ) {
        if (!hasPostNotificationPermission(context)) return
        ensureChannels(context)
        val exp = CurrencyFormatter.format(todayExpense, currencyCode)
        val inc = CurrencyFormatter.format(todayIncome, currencyCode)
        postLocalNotification(
            context = context,
            channelId = CHANNEL_SUMMARY,
            notificationId = 99001,
            title = "DailySpend Summary",
            body = "Today: Income $inc • Expense $exp"
        )
    }

    private fun postLocalNotification(
        context: Context,
        channelId: String,
        notificationId: Int,
        title: String,
        body: String
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (_: SecurityException) {
            // Permission revoked at runtime; fail gracefully
        }
    }
}
