package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.example.data.model.AccountType
import com.example.data.model.BudgetPeriod
import com.example.data.model.CategoryType
import com.example.data.model.SourceType
import com.example.data.model.ThemeMode
import com.example.data.model.TransactionStatus
import com.example.data.model.TransactionType

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["destinationAccountId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("categoryId"),
        Index("accountId"),
        Index("destinationAccountId"),
        Index("timestampMillis"),
        Index("status"),
        Index("deduplicationHash")
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: TransactionType,
    val amount: Long, // Always integer Rupiah (or minor unit) to avoid floating point issues
    val categoryId: Long? = null,
    val accountId: Long,
    val destinationAccountId: Long? = null,
    val transactionDate: String, // yyyy-MM-dd
    val transactionTime: String, // HH:mm
    val timestampMillis: Long,
    val sourceType: SourceType,
    val sourceApplication: String,
    val status: TransactionStatus,
    val notes: String = "",
    val receiptImageUri: String? = null,
    val deduplicationHash: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: CategoryType,
    val iconKey: String,
    val colorHex: Long,
    val isDefault: Boolean = false
)

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: AccountType,
    val initialBalance: Long = 0L,
    val iconKey: String,
    val colorHex: Long,
    val packageNameHint: String? = null,
    val isDefault: Boolean = false
)

@Entity(
    tableName = "budgets",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("categoryId")]
)
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val amountLimit: Long,
    val period: BudgetPeriod,
    val categoryId: Long? = null,
    val lastAlertCycleKey: String = "",
    val lastAlertLevel: Int = 0, // 0, 50, 80, 100
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "notification_sources")
data class NotificationSourceEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val isEnabled: Boolean = true,
    val defaultAccountId: Long? = null,
    val supportedFormatsSummary: String = ""
)

@Entity(
    tableName = "notification_logs",
    indices = [Index("createdAt")]
)
data class NotificationLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val appName: String,
    val status: String, // PARSED_CONFIRMED, PARSED_PENDING, DUPLICATE_SKIPPED, IGNORED_NON_TRANSACTION, IGNORED_SENSITIVE
    val parsedAmount: Long? = null,
    val parsedType: String? = null,
    val sanitizedSummary: String, // Never stores raw OTP/PIN/sensitive notification body
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "settings")
data class AppSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val profileName: String = "DailySpend User",
    val currencyCode: String = "IDR",
    val themeMode: ThemeMode = ThemeMode.LIGHT,
    val hideBalanceOnStart: Boolean = false,
    val budgetNotificationsEnabled: Boolean = true,
    val dailySummaryEnabled: Boolean = false,
    val autoConfirmHighConfidence: Boolean = true,
    val pinEnabled: Boolean = false,
    val pinHash: String? = null,
    val biometricEnabled: Boolean = false,
    val privacyMaskLogs: Boolean = true
)

class DailySpendConverters {
    @TypeConverter fun fromTransactionType(v: TransactionType): String = v.name
    @TypeConverter fun toTransactionType(v: String): TransactionType = TransactionType.valueOf(v)

    @TypeConverter fun fromSourceType(v: SourceType): String = v.name
    @TypeConverter fun toSourceType(v: String): SourceType = SourceType.valueOf(v)

    @TypeConverter fun fromTransactionStatus(v: TransactionStatus): String = v.name
    @TypeConverter fun toTransactionStatus(v: String): TransactionStatus = TransactionStatus.valueOf(v)

    @TypeConverter fun fromCategoryType(v: CategoryType): String = v.name
    @TypeConverter fun toCategoryType(v: String): CategoryType = CategoryType.valueOf(v)

    @TypeConverter fun fromAccountType(v: AccountType): String = v.name
    @TypeConverter fun toAccountType(v: String): AccountType = AccountType.valueOf(v)

    @TypeConverter fun fromBudgetPeriod(v: BudgetPeriod): String = v.name
    @TypeConverter fun toBudgetPeriod(v: String): BudgetPeriod = BudgetPeriod.valueOf(v)

    @TypeConverter fun fromThemeMode(v: ThemeMode): String = v.name
    @TypeConverter fun toThemeMode(v: String): ThemeMode = ThemeMode.valueOf(v)
}
