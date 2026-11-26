package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.data.model.TransactionStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface DailySpendDao {

    // Transactions
    @Query("SELECT * FROM transactions ORDER BY timestampMillis DESC, id DESC")
    fun observeAllTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE status = :status ORDER BY timestampMillis DESC, id DESC")
    fun observeTransactionsByStatus(status: TransactionStatus): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY timestampMillis DESC, id DESC")
    suspend fun getAllTransactionsOnce(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getTransactionById(id: Long): TransactionEntity?

    @Query(
        "SELECT * FROM transactions " +
            "WHERE deduplicationHash = :hash AND timestampMillis >= :sinceMillis AND status != 'REJECTED' " +
            "LIMIT 1"
    )
    suspend fun findRecentDuplicate(hash: String, sinceMillis: Long): TransactionEntity?

    @Query(
        "SELECT COALESCE(SUM(amount), 0) FROM transactions " +
            "WHERE type = 'EXPENSE' AND status = 'CONFIRMED' " +
            "AND timestampMillis >= :startMillis AND timestampMillis <= :endMillis " +
            "AND (:categoryId IS NULL OR categoryId = :categoryId)"
    )
    suspend fun getConfirmedExpenseSum(
        startMillis: Long,
        endMillis: Long,
        categoryId: Long?
    ): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(transactions: List<TransactionEntity>)

    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransactionById(id: Long)

    @Query("DELETE FROM transactions")
    suspend fun deleteAllTransactions()

    // Categories
    @Query("SELECT * FROM categories ORDER BY type ASC, isDefault DESC, name ASC")
    fun observeCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories")
    suspend fun getCategoriesOnce(): List<CategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: CategoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<CategoryEntity>)

    @Update
    suspend fun updateCategory(category: CategoryEntity)

    @Delete
    suspend fun deleteCategory(category: CategoryEntity)

    @Query("DELETE FROM categories")
    suspend fun deleteAllCategories()

    // Accounts
    @Query("SELECT * FROM accounts ORDER BY isDefault DESC, id ASC")
    fun observeAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts")
    suspend fun getAccountsOnce(): List<AccountEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: AccountEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccounts(accounts: List<AccountEntity>)

    @Update
    suspend fun updateAccount(account: AccountEntity)

    @Delete
    suspend fun deleteAccount(account: AccountEntity)

    @Query("DELETE FROM accounts")
    suspend fun deleteAllAccounts()

    // Budgets
    @Query("SELECT * FROM budgets ORDER BY createdAt DESC")
    fun observeBudgets(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets")
    suspend fun getBudgetsOnce(): List<BudgetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudget(budget: BudgetEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudgets(budgets: List<BudgetEntity>)

    @Update
    suspend fun updateBudget(budget: BudgetEntity)

    @Delete
    suspend fun deleteBudget(budget: BudgetEntity)

    @Query("DELETE FROM budgets")
    suspend fun deleteAllBudgets()

    // Notification Sources
    @Query("SELECT * FROM notification_sources ORDER BY appName ASC")
    fun observeNotificationSources(): Flow<List<NotificationSourceEntity>>

    @Query("SELECT * FROM notification_sources")
    suspend fun getNotificationSourcesOnce(): List<NotificationSourceEntity>

    @Query("SELECT * FROM notification_sources WHERE packageName = :packageName LIMIT 1")
    suspend fun getNotificationSource(packageName: String): NotificationSourceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotificationSources(sources: List<NotificationSourceEntity>)

    @Update
    suspend fun updateNotificationSource(source: NotificationSourceEntity)

    // Notification Logs
    @Query("SELECT * FROM notification_logs ORDER BY createdAt DESC LIMIT 200")
    fun observeNotificationLogs(): Flow<List<NotificationLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotificationLog(log: NotificationLogEntity): Long

    @Query("DELETE FROM notification_logs")
    suspend fun deleteAllNotificationLogs()

    // Settings
    @Query("SELECT * FROM settings WHERE id = 1")
    fun observeSettings(): Flow<AppSettingsEntity?>

    @Query("SELECT * FROM settings WHERE id = 1")
    suspend fun getSettingsOnce(): AppSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSettings(settings: AppSettingsEntity)

    @Transaction
    suspend fun clearTransactionalData() {
        deleteAllTransactions()
        deleteAllBudgets()
        deleteAllNotificationLogs()
    }

    @Transaction
    suspend fun restoreFullBackup(
        accounts: List<AccountEntity>,
        categories: List<CategoryEntity>,
        transactions: List<TransactionEntity>,
        budgets: List<BudgetEntity>,
        sources: List<NotificationSourceEntity>,
        settings: AppSettingsEntity?
    ) {
        deleteAllTransactions()
        deleteAllBudgets()
        deleteAllNotificationLogs()
        deleteAllCategories()
        deleteAllAccounts()
        insertAccounts(accounts)
        insertCategories(categories)
        insertTransactions(transactions)
        insertBudgets(budgets)
        if (sources.isNotEmpty()) {
            insertNotificationSources(sources)
        }
        if (settings != null) {
            upsertSettings(settings)
        }
    }
}
