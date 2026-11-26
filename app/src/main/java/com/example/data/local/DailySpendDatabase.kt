package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.AccountType
import com.example.data.model.CategoryType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        TransactionEntity::class,
        CategoryEntity::class,
        AccountEntity::class,
        BudgetEntity::class,
        NotificationSourceEntity::class,
        NotificationLogEntity::class,
        AppSettingsEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(DailySpendConverters::class)
abstract class DailySpendDatabase : RoomDatabase() {
    abstract fun dao(): DailySpendDao

    companion object {
        @Volatile
        private var INSTANCE: DailySpendDatabase? = null

        fun getInstance(context: Context): DailySpendDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): DailySpendDatabase {
            lateinit var db: DailySpendDatabase
            db = Room.databaseBuilder(
                context,
                DailySpendDatabase::class.java,
                "dailyspend_database.db"
            )
                .addCallback(object : Callback() {
                    override fun onCreate(sqliteDb: SupportSQLiteDatabase) {
                        super.onCreate(sqliteDb)
                        CoroutineScope(Dispatchers.IO).launch {
                            seedInitialData(db.dao())
                        }
                    }
                })
                .build()
            return db
        }

        suspend fun seedInitialData(dao: DailySpendDao) {
            if (dao.getSettingsOnce() == null) {
                dao.upsertSettings(AppSettingsEntity())
            }

            if (dao.getCategoriesOnce().isEmpty()) {
                dao.insertCategories(defaultCategories())
            }

            if (dao.getAccountsOnce().isEmpty()) {
                dao.insertAccounts(defaultAccounts())
            }

            val accounts = dao.getAccountsOnce()
            fun accountIdByName(name: String): Long? =
                accounts.firstOrNull { it.name.equals(name, ignoreCase = true) }?.id

            if (dao.getNotificationSourcesOnce().isEmpty()) {
                dao.insertNotificationSources(
                    listOf(
                        NotificationSourceEntity(
                            packageName = "com.bca",
                            appName = "BCA Mobile",
                            isEnabled = true,
                            defaultAccountId = accountIdByName("BCA"),
                            supportedFormatsSummary = "m-Transfer, QRIS, Debit/Kredit notifikasi transaksi BCA"
                        ),
                        NotificationSourceEntity(
                            packageName = "com.bca.mybca.omni.android",
                            appName = "myBCA",
                            isEnabled = true,
                            defaultAccountId = accountIdByName("BCA"),
                            supportedFormatsSummary = "Transfer, QRIS, Bayar tagihan, Top-up notifikasi myBCA"
                        ),
                        NotificationSourceEntity(
                            packageName = "id.dana",
                            appName = "DANA",
                            isEnabled = true,
                            defaultAccountId = accountIdByName("DANA"),
                            supportedFormatsSummary = "Pembayaran QRIS, Kirim Uang, Terima Saldo, Top Up DANA"
                        ),
                        NotificationSourceEntity(
                            packageName = "id.co.bri.brimo",
                            appName = "BRImo",
                            isEnabled = true,
                            defaultAccountId = accountIdByName("BRI"),
                            supportedFormatsSummary = "Transaksi berhasil, BRIVA, QRIS, Transfer masuk/keluar BRI"
                        ),
                        NotificationSourceEntity(
                            packageName = "src.com.bni",
                            appName = "BNI Mobile Banking",
                            isEnabled = true,
                            defaultAccountId = accountIdByName("BNI"),
                            supportedFormatsSummary = "Notifikasi mutasi debet/kredit, QRIS, Virtual Account BNI"
                        ),
                        NotificationSourceEntity(
                            packageName = "id.bmri.livin",
                            appName = "Livin' by Mandiri",
                            isEnabled = true,
                            defaultAccountId = accountIdByName("Mandiri"),
                            supportedFormatsSummary = "Livin' transaksi berhasil, QRIS, Transfer masuk/keluar Mandiri"
                        ),
                        NotificationSourceEntity(
                            packageName = "com.gojek.gopay",
                            appName = "GoPay",
                            isEnabled = true,
                            defaultAccountId = accountIdByName("GoPay"),
                            supportedFormatsSummary = "Pembayaran GoPay, QRIS, Transfer masuk & Top Up"
                        ),
                        NotificationSourceEntity(
                            packageName = "com.gojek.app",
                            appName = "Gojek / GoPay",
                            isEnabled = true,
                            defaultAccountId = accountIdByName("GoPay"),
                            supportedFormatsSummary = "Notifikasi pembayaran & saldo GoPay di aplikasi Gojek"
                        ),
                        NotificationSourceEntity(
                            packageName = "ovo.id",
                            appName = "OVO",
                            isEnabled = true,
                            defaultAccountId = accountIdByName("OVO"),
                            supportedFormatsSummary = "Pembayaran merchant, Transfer, OVO Cash Top Up"
                        ),
                        NotificationSourceEntity(
                            packageName = "com.shopee.id",
                            appName = "ShopeePay",
                            isEnabled = true,
                            defaultAccountId = accountIdByName("Cash"),
                            supportedFormatsSummary = "Pembayaran ShopeePay, QRIS, Terima dana Shopee"
                        ),
                        NotificationSourceEntity(
                            packageName = "com.shopeepay.id",
                            appName = "ShopeePay App",
                            isEnabled = true,
                            defaultAccountId = accountIdByName("Cash"),
                            supportedFormatsSummary = "Notifikasi transaksi aplikasi mandiri ShopeePay"
                        ),
                        NotificationSourceEntity(
                            packageName = "*",
                            appName = "Other Finance Apps",
                            isEnabled = false,
                            defaultAccountId = accountIdByName("Cash"),
                            supportedFormatsSummary = "Parser heuristik untuk aplikasi perbankan/e-wallet lainnya (masuk Pending Review)"
                        )
                    )
                )
            }
        }

        fun defaultCategories(): List<CategoryEntity> = listOf(
            // Income Categories
            CategoryEntity(name = "Salary", type = CategoryType.INCOME, iconKey = "payments", colorHex = 0xFF16A34A, isDefault = true),
            CategoryEntity(name = "Business", type = CategoryType.INCOME, iconKey = "store", colorHex = 0xFF0D9488, isDefault = true),
            CategoryEntity(name = "Investment", type = CategoryType.INCOME, iconKey = "trending_up", colorHex = 0xFF2563EB, isDefault = true),
            CategoryEntity(name = "Transfer Received", type = CategoryType.INCOME, iconKey = "call_received", colorHex = 0xFF059669, isDefault = true),
            CategoryEntity(name = "Other Income", type = CategoryType.INCOME, iconKey = "add_card", colorHex = 0xFF65A30D, isDefault = true),

            // Expense Categories
            CategoryEntity(name = "Food & Beverage", type = CategoryType.EXPENSE, iconKey = "restaurant", colorHex = 0xFFEA580C, isDefault = true),
            CategoryEntity(name = "Transportation", type = CategoryType.EXPENSE, iconKey = "directions_car", colorHex = 0xFF2563EB, isDefault = true),
            CategoryEntity(name = "Shopping", type = CategoryType.EXPENSE, iconKey = "shopping_bag", colorHex = 0xFFDB2777, isDefault = true),
            CategoryEntity(name = "Bills", type = CategoryType.EXPENSE, iconKey = "receipt_long", colorHex = 0xFF7C3AED, isDefault = true),
            CategoryEntity(name = "Health", type = CategoryType.EXPENSE, iconKey = "medical_services", colorHex = 0xFFDC2626, isDefault = true),
            CategoryEntity(name = "Education", type = CategoryType.EXPENSE, iconKey = "school", colorHex = 0xFF0284C7, isDefault = true),
            CategoryEntity(name = "Entertainment", type = CategoryType.EXPENSE, iconKey = "movie", colorHex = 0xFF9333EA, isDefault = true),
            CategoryEntity(name = "Family", type = CategoryType.EXPENSE, iconKey = "family_restroom", colorHex = 0xFFD97706, isDefault = true),
            CategoryEntity(name = "Housing", type = CategoryType.EXPENSE, iconKey = "home", colorHex = 0xFF4F46E5, isDefault = true),
            CategoryEntity(name = "Other Expenses", type = CategoryType.EXPENSE, iconKey = "more_horiz", colorHex = 0xFF64748B, isDefault = true)
        )

        fun defaultAccounts(): List<AccountEntity> = listOf(
            AccountEntity(name = "Cash", type = AccountType.CASH, initialBalance = 0L, iconKey = "wallet", colorHex = 0xFF16A34A, isDefault = true),
            AccountEntity(name = "BCA", type = AccountType.BANK, initialBalance = 0L, iconKey = "account_balance", colorHex = 0xFF00529C, packageNameHint = "com.bca", isDefault = true),
            AccountEntity(name = "BRI", type = AccountType.BANK, initialBalance = 0L, iconKey = "account_balance", colorHex = 0xFF0066B3, packageNameHint = "id.co.bri.brimo", isDefault = true),
            AccountEntity(name = "BNI", type = AccountType.BANK, initialBalance = 0L, iconKey = "account_balance", colorHex = 0xFFF15A23, packageNameHint = "src.com.bni", isDefault = true),
            AccountEntity(name = "Mandiri", type = AccountType.BANK, initialBalance = 0L, iconKey = "account_balance", colorHex = 0xFF003D79, packageNameHint = "id.bmri.livin", isDefault = true),
            AccountEntity(name = "DANA", type = AccountType.EWALLET, initialBalance = 0L, iconKey = "smartphone", colorHex = 0xFF118EEA, packageNameHint = "id.dana", isDefault = true),
            AccountEntity(name = "GoPay", type = AccountType.EWALLET, initialBalance = 0L, iconKey = "smartphone", colorHex = 0xFF00AED6, packageNameHint = "com.gojek.gopay", isDefault = true),
            AccountEntity(name = "OVO", type = AccountType.EWALLET, initialBalance = 0L, iconKey = "smartphone", colorHex = 0xFF4C3494, packageNameHint = "ovo.id", isDefault = true)
        )
    }
}
