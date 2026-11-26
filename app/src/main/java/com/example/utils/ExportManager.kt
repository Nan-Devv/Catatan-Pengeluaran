package com.example.utils

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.example.data.local.AccountEntity
import com.example.data.local.AppSettingsEntity
import com.example.data.local.BudgetEntity
import com.example.data.local.CategoryEntity
import com.example.data.local.NotificationSourceEntity
import com.example.data.local.TransactionEntity
import com.example.data.model.AccountType
import com.example.data.model.BudgetPeriod
import com.example.data.model.CategoryType
import com.example.data.model.SourceType
import com.example.data.model.ThemeMode
import com.example.data.model.TransactionStatus
import com.example.data.model.TransactionType
import org.json.JSONArray
import org.json.JSONObject

data class BackupPayload(
    val accounts: List<AccountEntity>,
    val categories: List<CategoryEntity>,
    val transactions: List<TransactionEntity>,
    val budgets: List<BudgetEntity>,
    val sources: List<NotificationSourceEntity>,
    val settings: AppSettingsEntity?
)

object ExportManager {

    fun writeCsvToUri(
        context: Context,
        uri: Uri,
        transactions: List<TransactionEntity>,
        accounts: Map<Long, AccountEntity>,
        categories: Map<Long, CategoryEntity>
    ): Result<Int> = runCatching {
        val sb = StringBuilder()
        sb.appendLine("ID,Date,Time,Type,Amount,Category,Account,DestinationAccount,SourceType,SourceApp,Status,Notes")
        for (tx in transactions) {
            val catName = tx.categoryId?.let { categories[it]?.name } ?: ""
            val accName = accounts[tx.accountId]?.name ?: ""
            val destName = tx.destinationAccountId?.let { accounts[it]?.name } ?: ""
            sb.appendLine(
                listOf(
                    tx.id.toString(),
                    csvEscape(tx.transactionDate),
                    csvEscape(tx.transactionTime),
                    tx.type.name,
                    tx.amount.toString(),
                    csvEscape(catName),
                    csvEscape(accName),
                    csvEscape(destName),
                    tx.sourceType.name,
                    csvEscape(tx.sourceApplication),
                    tx.status.name,
                    csvEscape(tx.notes)
                ).joinToString(",")
            )
        }
        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(sb.toString().toByteArray(Charsets.UTF_8))
        } ?: error("Unable to open output stream for CSV export")
        transactions.size
    }

    fun writePdfToUri(
        context: Context,
        uri: Uri,
        periodLabel: String,
        currencyCode: String,
        transactions: List<TransactionEntity>,
        accounts: Map<Long, AccountEntity>,
        categories: Map<Long, CategoryEntity>
    ): Result<Int> = runCatching {
        val pdf = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 40f

        val titlePaint = Paint().apply {
            color = Color.parseColor("#059669")
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subtitlePaint = Paint().apply {
            color = Color.parseColor("#64748B")
            textSize = 11f
        }
        val headerPaint = Paint().apply {
            color = Color.parseColor("#1E293B")
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bodyPaint = Paint().apply {
            color = Color.parseColor("#1E293B")
            textSize = 10f
        }
        val incomePaint = Paint().apply {
            color = Color.parseColor("#16A34A")
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val expensePaint = Paint().apply {
            color = Color.parseColor("#EF4444")
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val linePaint = Paint().apply {
            color = Color.parseColor("#E2E8F0")
            strokeWidth = 1f
        }

        val confirmed = transactions.filter { it.status == TransactionStatus.CONFIRMED }
        val totalIncome = confirmed.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
        val totalExpense = confirmed.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
        val net = totalIncome - totalExpense

        var pageNumber = 1
        var page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas = page.canvas
        var y = 52f

        canvas.drawText("DailySpend Financial Report", margin, y, titlePaint)
        y += 18f
        canvas.drawText("Period: $periodLabel  •  Generated ${DateUtils.formatDisplayDate(System.currentTimeMillis())}", margin, y, subtitlePaint)
        y += 22f
        canvas.drawLine(margin, y, pageWidth - margin, y, linePaint)
        y += 20f

        canvas.drawText("Total Income: ${CurrencyFormatter.format(totalIncome, currencyCode)}", margin, y, incomePaint)
        canvas.drawText("Total Expense: ${CurrencyFormatter.format(totalExpense, currencyCode)}", margin + 190f, y, expensePaint)
        canvas.drawText("Net: ${CurrencyFormatter.format(net, currencyCode)}", margin + 390f, y, headerPaint)
        y += 24f
        canvas.drawLine(margin, y, pageWidth - margin, y, linePaint)
        y += 20f

        canvas.drawText("Date", margin, y, headerPaint)
        canvas.drawText("Category / Notes", margin + 80f, y, headerPaint)
        canvas.drawText("Account", margin + 300f, y, headerPaint)
        canvas.drawText("Amount", margin + 420f, y, headerPaint)
        y += 10f
        canvas.drawLine(margin, y, pageWidth - margin, y, linePaint)
        y += 16f

        for (tx in confirmed) {
            if (y > pageHeight - 50f) {
                pdf.finishPage(page)
                pageNumber++
                page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                canvas = page.canvas
                y = 48f
            }
            val catName = when (tx.type) {
                TransactionType.TRANSFER -> "Transfer"
                else -> tx.categoryId?.let { categories[it]?.name } ?: "Uncategorized"
            }
            val accName = accounts[tx.accountId]?.name ?: "-"
            val label = if (tx.notes.isNotBlank()) "$catName (${tx.notes.take(22)})" else catName
            val amountStr = when (tx.type) {
                TransactionType.INCOME -> "+${CurrencyFormatter.format(tx.amount, currencyCode)}"
                TransactionType.EXPENSE -> "-${CurrencyFormatter.format(tx.amount, currencyCode)}"
                TransactionType.TRANSFER -> CurrencyFormatter.format(tx.amount, currencyCode)
            }
            val amtPaint = when (tx.type) {
                TransactionType.INCOME -> incomePaint
                TransactionType.EXPENSE -> expensePaint
                TransactionType.TRANSFER -> bodyPaint
            }

            canvas.drawText(tx.transactionDate, margin, y, bodyPaint)
            canvas.drawText(label.take(34), margin + 80f, y, bodyPaint)
            canvas.drawText(accName.take(18), margin + 300f, y, bodyPaint)
            canvas.drawText(amountStr, margin + 420f, y, amtPaint)
            y += 18f
        }

        pdf.finishPage(page)
        context.contentResolver.openOutputStream(uri)?.use { out ->
            pdf.writeTo(out)
        } ?: error("Unable to open output stream for PDF export")
        pdf.close()
        confirmed.size
    }

    fun writeBackupJsonToUri(
        context: Context,
        uri: Uri,
        payload: BackupPayload
    ): Result<Unit> = runCatching {
        val root = JSONObject().apply {
            put("app", "DailySpend")
            put("schemaVersion", 1)
            put("exportedAt", System.currentTimeMillis())

            put("accounts", JSONArray().apply {
                payload.accounts.forEach { a ->
                    put(JSONObject().apply {
                        put("id", a.id)
                        put("name", a.name)
                        put("type", a.type.name)
                        put("initialBalance", a.initialBalance)
                        put("iconKey", a.iconKey)
                        put("colorHex", a.colorHex)
                        put("packageNameHint", a.packageNameHint ?: JSONObject.NULL)
                        put("isDefault", a.isDefault)
                    })
                }
            })

            put("categories", JSONArray().apply {
                payload.categories.forEach { c ->
                    put(JSONObject().apply {
                        put("id", c.id)
                        put("name", c.name)
                        put("type", c.type.name)
                        put("iconKey", c.iconKey)
                        put("colorHex", c.colorHex)
                        put("isDefault", c.isDefault)
                    })
                }
            })

            put("transactions", JSONArray().apply {
                payload.transactions.forEach { t ->
                    put(JSONObject().apply {
                        put("id", t.id)
                        put("type", t.type.name)
                        put("amount", t.amount)
                        put("categoryId", t.categoryId ?: JSONObject.NULL)
                        put("accountId", t.accountId)
                        put("destinationAccountId", t.destinationAccountId ?: JSONObject.NULL)
                        put("transactionDate", t.transactionDate)
                        put("transactionTime", t.transactionTime)
                        put("timestampMillis", t.timestampMillis)
                        put("sourceType", t.sourceType.name)
                        put("sourceApplication", t.sourceApplication)
                        put("status", t.status.name)
                        put("notes", t.notes)
                        put("deduplicationHash", t.deduplicationHash ?: JSONObject.NULL)
                        put("createdAt", t.createdAt)
                        put("updatedAt", t.updatedAt)
                    })
                }
            })

            put("budgets", JSONArray().apply {
                payload.budgets.forEach { b ->
                    put(JSONObject().apply {
                        put("id", b.id)
                        put("name", b.name)
                        put("amountLimit", b.amountLimit)
                        put("period", b.period.name)
                        put("categoryId", b.categoryId ?: JSONObject.NULL)
                        put("lastAlertCycleKey", b.lastAlertCycleKey)
                        put("lastAlertLevel", b.lastAlertLevel)
                        put("createdAt", b.createdAt)
                    })
                }
            })

            put("notification_sources", JSONArray().apply {
                payload.sources.forEach { s ->
                    put(JSONObject().apply {
                        put("packageName", s.packageName)
                        put("appName", s.appName)
                        put("isEnabled", s.isEnabled)
                        put("defaultAccountId", s.defaultAccountId ?: JSONObject.NULL)
                        put("supportedFormatsSummary", s.supportedFormatsSummary)
                    })
                }
            })

            payload.settings?.let { s ->
                put("settings", JSONObject().apply {
                    put("profileName", s.profileName)
                    put("currencyCode", s.currencyCode)
                    put("themeMode", s.themeMode.name)
                    put("hideBalanceOnStart", s.hideBalanceOnStart)
                    put("budgetNotificationsEnabled", s.budgetNotificationsEnabled)
                    put("dailySummaryEnabled", s.dailySummaryEnabled)
                    put("autoConfirmHighConfidence", s.autoConfirmHighConfidence)
                    put("privacyMaskLogs", s.privacyMaskLogs)
                })
            }
        }

        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(root.toString(2).toByteArray(Charsets.UTF_8))
        } ?: error("Unable to open output stream for backup")
    }

    fun readBackupJsonFromUri(context: Context, uri: Uri): Result<BackupPayload> = runCatching {
        val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: error("Unable to read backup file")
        val root = JSONObject(raw)
        require(root.optString("app") == "DailySpend") { "Invalid backup file: missing DailySpend header" }
        require(root.optInt("schemaVersion", -1) == 1) { "Unsupported backup schema version" }

        val accountsArr = root.getJSONArray("accounts")
        val accounts = (0 until accountsArr.length()).map { i ->
            val o = accountsArr.getJSONObject(i)
            AccountEntity(
                id = o.getLong("id"),
                name = o.getString("name"),
                type = AccountType.valueOf(o.getString("type")),
                initialBalance = o.getLong("initialBalance"),
                iconKey = o.getString("iconKey"),
                colorHex = o.getLong("colorHex"),
                packageNameHint = if (o.isNull("packageNameHint")) null else o.getString("packageNameHint"),
                isDefault = o.optBoolean("isDefault", false)
            )
        }
        require(accounts.isNotEmpty()) { "Backup contains no accounts" }

        val categoriesArr = root.getJSONArray("categories")
        val categories = (0 until categoriesArr.length()).map { i ->
            val o = categoriesArr.getJSONObject(i)
            CategoryEntity(
                id = o.getLong("id"),
                name = o.getString("name"),
                type = CategoryType.valueOf(o.getString("type")),
                iconKey = o.getString("iconKey"),
                colorHex = o.getLong("colorHex"),
                isDefault = o.optBoolean("isDefault", false)
            )
        }

        val txArr = root.getJSONArray("transactions")
        val transactions = (0 until txArr.length()).map { i ->
            val o = txArr.getJSONObject(i)
            TransactionEntity(
                id = o.getLong("id"),
                type = TransactionType.valueOf(o.getString("type")),
                amount = o.getLong("amount"),
                categoryId = if (o.isNull("categoryId")) null else o.getLong("categoryId"),
                accountId = o.getLong("accountId"),
                destinationAccountId = if (o.isNull("destinationAccountId")) null else o.getLong("destinationAccountId"),
                transactionDate = o.getString("transactionDate"),
                transactionTime = o.getString("transactionTime"),
                timestampMillis = o.getLong("timestampMillis"),
                sourceType = SourceType.valueOf(o.getString("sourceType")),
                sourceApplication = o.getString("sourceApplication"),
                status = TransactionStatus.valueOf(o.getString("status")),
                notes = o.optString("notes", ""),
                deduplicationHash = if (o.isNull("deduplicationHash")) null else o.getString("deduplicationHash"),
                createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
            )
        }

        val budgetsArr = root.optJSONArray("budgets") ?: JSONArray()
        val budgets = (0 until budgetsArr.length()).map { i ->
            val o = budgetsArr.getJSONObject(i)
            BudgetEntity(
                id = o.getLong("id"),
                name = o.getString("name"),
                amountLimit = o.getLong("amountLimit"),
                period = BudgetPeriod.valueOf(o.getString("period")),
                categoryId = if (o.isNull("categoryId")) null else o.getLong("categoryId"),
                lastAlertCycleKey = o.optString("lastAlertCycleKey", ""),
                lastAlertLevel = o.optInt("lastAlertLevel", 0),
                createdAt = o.optLong("createdAt", System.currentTimeMillis())
            )
        }

        val sourcesArr = root.optJSONArray("notification_sources") ?: JSONArray()
        val sources = (0 until sourcesArr.length()).map { i ->
            val o = sourcesArr.getJSONObject(i)
            NotificationSourceEntity(
                packageName = o.getString("packageName"),
                appName = o.getString("appName"),
                isEnabled = o.optBoolean("isEnabled", true),
                defaultAccountId = if (o.isNull("defaultAccountId")) null else o.getLong("defaultAccountId"),
                supportedFormatsSummary = o.optString("supportedFormatsSummary", "")
            )
        }

        val settingsObj = root.optJSONObject("settings")
        val settings = settingsObj?.let { s ->
            AppSettingsEntity(
                id = 1,
                profileName = s.optString("profileName", "DailySpend User"),
                currencyCode = s.optString("currencyCode", "IDR"),
                themeMode = runCatching { ThemeMode.valueOf(s.optString("themeMode", "SYSTEM")) }.getOrDefault(ThemeMode.SYSTEM),
                hideBalanceOnStart = s.optBoolean("hideBalanceOnStart", false),
                budgetNotificationsEnabled = s.optBoolean("budgetNotificationsEnabled", true),
                dailySummaryEnabled = s.optBoolean("dailySummaryEnabled", false),
                autoConfirmHighConfidence = s.optBoolean("autoConfirmHighConfidence", true),
                privacyMaskLogs = s.optBoolean("privacyMaskLogs", true)
            )
        }

        BackupPayload(
            accounts = accounts,
            categories = categories,
            transactions = transactions,
            budgets = budgets,
            sources = sources,
            settings = settings
        )
    }

    private fun csvEscape(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }
}
