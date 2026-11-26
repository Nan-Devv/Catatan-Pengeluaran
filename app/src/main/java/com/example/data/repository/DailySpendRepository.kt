package com.example.data.repository

import android.content.Context
import com.example.data.local.AccountEntity
import com.example.data.local.AppSettingsEntity
import com.example.data.local.BudgetEntity
import com.example.data.local.CategoryEntity
import com.example.data.local.DailySpendDao
import com.example.data.local.DailySpendDatabase
import com.example.data.local.NotificationLogEntity
import com.example.data.local.NotificationSourceEntity
import com.example.data.local.TransactionEntity
import com.example.data.model.AccountType
import com.example.data.model.BudgetPeriod
import com.example.data.model.CategoryType
import com.example.data.model.SourceType
import com.example.data.model.TransactionStatus
import com.example.data.model.TransactionType
import com.example.notification.NotificationHelper
import com.example.notification.ParseOutcome
import com.example.notification.TransactionParserEngine
import com.example.utils.BackupPayload
import com.example.utils.DateUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class AccountWithBalance(
    val account: AccountEntity,
    val currentBalance: Long,
    val incomeTotal: Long,
    val expenseTotal: Long
)

data class BudgetProgress(
    val budget: BudgetEntity,
    val category: CategoryEntity?,
    val spent: Long,
    val remaining: Long,
    val percentage: Float
)

class DailySpendRepository(
    private val dao: DailySpendDao,
    private val appContext: Context
) {
    val transactions: Flow<List<TransactionEntity>> = dao.observeAllTransactions()
    val pendingReviewTransactions: Flow<List<TransactionEntity>> =
        dao.observeTransactionsByStatus(TransactionStatus.PENDING_REVIEW)
    val categories: Flow<List<CategoryEntity>> = dao.observeCategories()
    val accounts: Flow<List<AccountEntity>> = dao.observeAccounts()
    val budgets: Flow<List<BudgetEntity>> = dao.observeBudgets()
    val notificationSources: Flow<List<NotificationSourceEntity>> = dao.observeNotificationSources()
    val notificationLogs: Flow<List<NotificationLogEntity>> = dao.observeNotificationLogs()
    val settings: Flow<AppSettingsEntity> = dao.observeSettings().map { it ?: AppSettingsEntity() }

    suspend fun ensureSeeded() {
        DailySpendDatabase.seedInitialData(dao)
    }

    suspend fun saveTransaction(tx: TransactionEntity): Long {
        val now = System.currentTimeMillis()
        val id = if (tx.id == 0L) {
            dao.insertTransaction(tx.copy(createdAt = now, updatedAt = now))
        } else {
            dao.updateTransaction(tx.copy(updatedAt = now))
            tx.id
        }
        if (tx.status == TransactionStatus.CONFIRMED && tx.type == TransactionType.EXPENSE) {
            NotificationHelper.evaluateAndNotifyBudgets(appContext, dao)
        }
        return id
    }

    suspend fun deleteTransaction(id: Long) {
        dao.deleteTransactionById(id)
    }

    suspend fun approvePendingTransaction(
        tx: TransactionEntity,
        updatedAmount: Long,
        updatedType: TransactionType,
        updatedCategoryId: Long?,
        updatedAccountId: Long,
        updatedDestAccountId: Long?,
        updatedNotes: String
    ) {
        val updated = tx.copy(
            amount = updatedAmount,
            type = updatedType,
            categoryId = if (updatedType == TransactionType.TRANSFER) null else updatedCategoryId,
            accountId = updatedAccountId,
            destinationAccountId = if (updatedType == TransactionType.TRANSFER) updatedDestAccountId else null,
            notes = updatedNotes,
            status = TransactionStatus.CONFIRMED,
            updatedAt = System.currentTimeMillis()
        )
        dao.updateTransaction(updated)
        if (updated.type == TransactionType.EXPENSE) {
            NotificationHelper.evaluateAndNotifyBudgets(appContext, dao)
        }
    }

    suspend fun rejectPendingTransaction(tx: TransactionEntity) {
        dao.updateTransaction(
            tx.copy(
                status = TransactionStatus.REJECTED,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    // Accounts
    suspend fun saveAccount(
        id: Long = 0L,
        name: String,
        type: AccountType,
        initialBalance: Long,
        iconKey: String,
        colorHex: Long
    ) {
        if (id == 0L) {
            dao.insertAccount(
                AccountEntity(
                    name = name.trim(),
                    type = type,
                    initialBalance = initialBalance,
                    iconKey = iconKey,
                    colorHex = colorHex,
                    isDefault = false
                )
            )
        } else {
            val existing = dao.getAccountsOnce().firstOrNull { it.id == id } ?: return
            dao.updateAccount(
                existing.copy(
                    name = name.trim(),
                    type = type,
                    initialBalance = initialBalance,
                    iconKey = iconKey,
                    colorHex = colorHex
                )
            )
        }
    }

    suspend fun deleteAccount(account: AccountEntity) {
        dao.deleteAccount(account)
    }

    // Categories
    suspend fun saveCategory(
        id: Long = 0L,
        name: String,
        type: CategoryType,
        iconKey: String,
        colorHex: Long
    ) {
        if (id == 0L) {
            dao.insertCategory(
                CategoryEntity(
                    name = name.trim(),
                    type = type,
                    iconKey = iconKey,
                    colorHex = colorHex,
                    isDefault = false
                )
            )
        } else {
            val existing = dao.getCategoriesOnce().firstOrNull { it.id == id } ?: return
            dao.updateCategory(
                existing.copy(
                    name = name.trim(),
                    type = type,
                    iconKey = iconKey,
                    colorHex = colorHex
                )
            )
        }
    }

    suspend fun deleteCategory(category: CategoryEntity) {
        dao.deleteCategory(category)
    }

    // Budgets
    suspend fun saveBudget(
        id: Long = 0L,
        name: String,
        amountLimit: Long,
        period: BudgetPeriod,
        categoryId: Long?
    ) {
        if (id == 0L) {
            dao.insertBudget(
                BudgetEntity(
                    name = name.trim(),
                    amountLimit = amountLimit,
                    period = period,
                    categoryId = categoryId
                )
            )
        } else {
            val existing = dao.getBudgetsOnce().firstOrNull { it.id == id } ?: return
            dao.updateBudget(
                existing.copy(
                    name = name.trim(),
                    amountLimit = amountLimit,
                    period = period,
                    categoryId = categoryId
                )
            )
        }
        NotificationHelper.evaluateAndNotifyBudgets(appContext, dao)
    }

    suspend fun deleteBudget(budget: BudgetEntity) {
        dao.deleteBudget(budget)
    }

    // Notification Sources & Logs
    suspend fun toggleNotificationSource(source: NotificationSourceEntity, enabled: Boolean) {
        dao.updateNotificationSource(source.copy(isEnabled = enabled))
    }

    suspend fun updateNotificationSourceAccount(source: NotificationSourceEntity, accountId: Long?) {
        dao.updateNotificationSource(source.copy(defaultAccountId = accountId))
    }

    suspend fun clearNotificationLogs() {
        dao.deleteAllNotificationLogs()
    }

    fun testParserFormat(packageName: String, title: String, body: String): ParseOutcome {
        return TransactionParserEngine.parse(
            packageName = packageName,
            title = title,
            body = body,
            allowGenericFallback = true
        )
    }

    // Settings & Security
    suspend fun updateSettings(transform: (AppSettingsEntity) -> AppSettingsEntity) {
        val current = dao.getSettingsOnce() ?: AppSettingsEntity()
        dao.upsertSettings(transform(current))
    }

    suspend fun clearAllUserData() {
        dao.clearTransactionalData()
    }

    suspend fun buildBackupPayload(): BackupPayload {
        return BackupPayload(
            accounts = dao.getAccountsOnce(),
            categories = dao.getCategoriesOnce(),
            transactions = dao.getAllTransactionsOnce(),
            budgets = dao.getBudgetsOnce(),
            sources = dao.getNotificationSourcesOnce(),
            settings = dao.getSettingsOnce()
        )
    }

    suspend fun restoreBackupPayload(payload: BackupPayload) {
        dao.restoreFullBackup(
            accounts = payload.accounts,
            categories = payload.categories,
            transactions = payload.transactions,
            budgets = payload.budgets,
            sources = payload.sources,
            settings = payload.settings
        )
    }

    suspend fun triggerDailySummaryNow() {
        val settings = dao.getSettingsOnce() ?: AppSettingsEntity()
        val now = System.currentTimeMillis()
        val start = DateUtils.startOfDay(now)
        val end = DateUtils.endOfDay(now)
        val confirmed = dao.getAllTransactionsOnce().filter {
            it.status == TransactionStatus.CONFIRMED && it.timestampMillis in start..end
        }
        val inc = confirmed.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
        val exp = confirmed.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
        NotificationHelper.notifyDailySummary(appContext, exp, inc, settings.currencyCode)
    }

    companion object {
        fun computeAccountsWithBalance(
            accounts: List<AccountEntity>,
            transactions: List<TransactionEntity>
        ): List<AccountWithBalance> {
            val confirmed = transactions.filter { it.status == TransactionStatus.CONFIRMED }
            return accounts.map { acc ->
                var balance = acc.initialBalance
                var income = 0L
                var expense = 0L
                for (tx in confirmed) {
                    when (tx.type) {
                        TransactionType.INCOME -> {
                            if (tx.accountId == acc.id) {
                                balance += tx.amount
                                income += tx.amount
                            }
                        }
                        TransactionType.EXPENSE -> {
                            if (tx.accountId == acc.id) {
                                balance -= tx.amount
                                expense += tx.amount
                            }
                        }
                        TransactionType.TRANSFER -> {
                            if (tx.accountId == acc.id) {
                                balance -= tx.amount
                            }
                            if (tx.destinationAccountId == acc.id) {
                                balance += tx.amount
                            }
                        }
                    }
                }
                AccountWithBalance(
                    account = acc,
                    currentBalance = balance,
                    incomeTotal = income,
                    expenseTotal = expense
                )
            }
        }

        fun computeBudgetProgress(
            budgets: List<BudgetEntity>,
            transactions: List<TransactionEntity>,
            categories: Map<Long, CategoryEntity>,
            now: Long = System.currentTimeMillis()
        ): List<BudgetProgress> {
            val confirmedExpenses = transactions.filter {
                it.status == TransactionStatus.CONFIRMED && it.type == TransactionType.EXPENSE
            }
            val monthStart = DateUtils.startOfCurrentMonth(now)
            val weekStart = DateUtils.startOfCurrentWeek(now)

            return budgets.map { budget ->
                val periodStart = if (budget.period == BudgetPeriod.MONTHLY) monthStart else weekStart
                val spent = confirmedExpenses
                    .filter { tx ->
                        tx.timestampMillis in periodStart..now &&
                            (budget.categoryId == null || tx.categoryId == budget.categoryId)
                    }
                    .sumOf { it.amount }

                val remaining = budget.amountLimit - spent
                val pct = if (budget.amountLimit > 0L) {
                    (spent.toFloat() / budget.amountLimit.toFloat()).coerceAtLeast(0f)
                } else 0f

                BudgetProgress(
                    budget = budget,
                    category = budget.categoryId?.let { categories[it] },
                    spent = spent,
                    remaining = remaining,
                    percentage = pct
                )
            }
        }
    }
}
