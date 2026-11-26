package com.example.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.data.local.DailySpendDatabase
import com.example.data.local.NotificationLogEntity
import com.example.data.local.TransactionEntity
import com.example.data.model.SourceType
import com.example.data.model.TransactionStatus
import com.example.utils.DateUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class DailySpendNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val packageName = sbn.packageName ?: return
        if (packageName == applicationContext.packageName) return

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val text = bigText ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return

        val postTime = if (sbn.postTime > 0L) sbn.postTime else System.currentTimeMillis()

        serviceScope.launch {
            processNotification(
                packageName = packageName,
                title = title,
                body = text,
                postTimeMillis = postTime
            )
        }
    }

    private suspend fun processNotification(
        packageName: String,
        title: String,
        body: String,
        postTimeMillis: Long
    ) {
        val db = DailySpendDatabase.getInstance(applicationContext)
        val dao = db.dao()

        val configuredSource = dao.getNotificationSource(packageName)
        val fallbackSource = dao.getNotificationSource("*")

        val sourceAllowed = when {
            configuredSource != null -> configuredSource.isEnabled
            fallbackSource != null -> fallbackSource.isEnabled
            else -> false
        }
        if (!sourceAllowed) return

        val outcome = TransactionParserEngine.parse(
            packageName = packageName,
            title = title,
            body = body,
            allowGenericFallback = (configuredSource == null && fallbackSource?.isEnabled == true)
        )

        val settings = dao.getSettingsOnce()
        val sourceAppLabel = configuredSource?.appName ?: packageName

        when (outcome) {
            ParseOutcome.IgnoredSensitive -> {
                dao.insertNotificationLog(
                    NotificationLogEntity(
                        packageName = packageName,
                        appName = sourceAppLabel,
                        status = "IGNORED_SENSITIVE",
                        sanitizedSummary = "Sensitive/OTP/PIN notification blocked locally"
                    )
                )
            }
            ParseOutcome.IgnoredNonTransaction -> {
                // Avoid spamming logs for unrelated notifications from generic fallback
                if (configuredSource != null) {
                    dao.insertNotificationLog(
                        NotificationLogEntity(
                            packageName = packageName,
                            appName = sourceAppLabel,
                            status = "IGNORED_NON_TRANSACTION",
                            sanitizedSummary = "Non-transactional notification ignored"
                        )
                    )
                }
            }
            is ParseOutcome.Parsed -> {
                val windowStart = postTimeMillis - DEDUP_WINDOW_MS
                val existing = dao.findRecentDuplicate(outcome.deduplicationHash, windowStart)
                if (existing != null) {
                    dao.insertNotificationLog(
                        NotificationLogEntity(
                            packageName = packageName,
                            appName = outcome.sourceApplication,
                            status = "DUPLICATE_SKIPPED",
                            parsedAmount = outcome.amount,
                            parsedType = outcome.type.name,
                            sanitizedSummary = "${outcome.sourceApplication} • Duplicate suppressed"
                        )
                    )
                    return
                }

                val accounts = dao.getAccountsOnce()
                val targetAccount = accounts.firstOrNull {
                    it.id == configuredSource?.defaultAccountId
                } ?: accounts.firstOrNull {
                    it.packageNameHint == packageName
                } ?: accounts.firstOrNull()

                if (targetAccount == null) return

                val categories = dao.getCategoriesOnce()
                val matchedCategory = categories.firstOrNull {
                    it.name.equals(outcome.inferredCategoryName, ignoreCase = true)
                }

                val autoConfirm = (settings?.autoConfirmHighConfidence ?: true) &&
                    outcome.confidence == ParseConfidence.HIGH
                val txStatus = if (autoConfirm) TransactionStatus.CONFIRMED else TransactionStatus.PENDING_REVIEW

                val tx = TransactionEntity(
                    type = outcome.type,
                    amount = outcome.amount,
                    categoryId = matchedCategory?.id,
                    accountId = targetAccount.id,
                    transactionDate = DateUtils.formatDate(postTimeMillis),
                    transactionTime = DateUtils.formatTime(postTimeMillis),
                    timestampMillis = postTimeMillis,
                    sourceType = SourceType.AUTOMATIC,
                    sourceApplication = outcome.sourceApplication,
                    status = txStatus,
                    notes = "Auto-detected from ${outcome.sourceApplication}",
                    deduplicationHash = outcome.deduplicationHash
                )

                dao.insertTransaction(tx)

                dao.insertNotificationLog(
                    NotificationLogEntity(
                        packageName = packageName,
                        appName = outcome.sourceApplication,
                        status = if (autoConfirm) "PARSED_CONFIRMED" else "PARSED_PENDING",
                        parsedAmount = outcome.amount,
                        parsedType = outcome.type.name,
                        sanitizedSummary = outcome.sanitizedLabel
                    )
                )

                if (autoConfirm) {
                    NotificationHelper.evaluateAndNotifyBudgets(applicationContext, dao)
                } else {
                    NotificationHelper.notifyPendingReviewTransaction(
                        context = applicationContext,
                        sourceApp = outcome.sourceApplication,
                        amount = outcome.amount,
                        currencyCode = settings?.currencyCode ?: "IDR"
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        const val DEDUP_WINDOW_MS = 180_000L // 3 minutes deduplication window
    }
}
