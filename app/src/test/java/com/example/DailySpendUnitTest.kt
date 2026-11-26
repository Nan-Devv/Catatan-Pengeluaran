package com.example

import com.example.data.local.AccountEntity
import com.example.data.local.BudgetEntity
import com.example.data.local.TransactionEntity
import com.example.data.model.AccountType
import com.example.data.model.BudgetPeriod
import com.example.data.model.SourceType
import com.example.data.model.TransactionStatus
import com.example.data.model.TransactionType
import com.example.data.repository.DailySpendRepository
import com.example.notification.ParseConfidence
import com.example.notification.ParseOutcome
import com.example.notification.TransactionParserEngine
import com.example.utils.CurrencyFormatter
import com.example.utils.SecurityUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DailySpendUnitTest {

    @Test
    fun `parser detects BCA Mobile outgoing transfer and ignores OTP`() {
        val transfer = TransactionParserEngine.parse(
            packageName = "com.bca",
            title = "BCA Mobile",
            body = "m-Transfer ke Budi berhasil sebesar Rp 150.000"
        )
        assertTrue(transfer is ParseOutcome.Parsed)
        val parsed = transfer as ParseOutcome.Parsed
        assertEquals(TransactionType.EXPENSE, parsed.type)
        assertEquals(150_000L, parsed.amount)
        assertEquals("BCA Mobile", parsed.sourceApplication)
        assertEquals(ParseConfidence.HIGH, parsed.confidence)

        val otp = TransactionParserEngine.parse(
            packageName = "com.bca",
            title = "BCA Mobile",
            body = "Kode OTP Anda adalah 849201 untuk transaksi Rp 150.000. Jangan beritahu siapapun."
        )
        assertTrue(otp is ParseOutcome.IgnoredSensitive)
    }

    @Test
    fun `parser detects DANA GoPay OVO Mandiri BRI BNI ShopeePay myBCA`() {
        val danaIn = TransactionParserEngine.parse(
            "id.dana",
            "DANA",
            "Kamu menerima saldo DANA Rp 250.000,00 dari Andi"
        ) as ParseOutcome.Parsed
        assertEquals(TransactionType.INCOME, danaIn.type)
        assertEquals(250_000L, danaIn.amount)

        val gopayOut = TransactionParserEngine.parse(
            "com.gojek.gopay",
            "GoPay",
            "Pembayaran GoPay di Kopi Kenangan sebesar IDR 38.000"
        ) as ParseOutcome.Parsed
        assertEquals(TransactionType.EXPENSE, gopayOut.type)
        assertEquals(38_000L, gopayOut.amount)
        assertEquals("Food & Beverage", gopayOut.inferredCategoryName)

        val ovoIn = TransactionParserEngine.parse(
            "ovo.id",
            "OVO",
            "Top Up OVO berhasil Rp 500.000"
        ) as ParseOutcome.Parsed
        assertEquals(TransactionType.INCOME, ovoIn.type)
        assertEquals(500_000L, ovoIn.amount)

        val livinOut = TransactionParserEngine.parse(
            "id.bmri.livin",
            "Livin' by Mandiri",
            "Pembayaran QRIS PLN berhasil Rp 200.000"
        ) as ParseOutcome.Parsed
        assertEquals(TransactionType.EXPENSE, livinOut.type)
        assertEquals("Bills", livinOut.inferredCategoryName)

        val brimoIn = TransactionParserEngine.parse(
            "id.co.bri.brimo",
            "BRImo",
            "Dana masuk ke rekening sebesar Rp 1.500.000"
        ) as ParseOutcome.Parsed
        assertEquals(TransactionType.INCOME, brimoIn.type)
        assertEquals(1_500_000L, brimoIn.amount)

        val bniOut = TransactionParserEngine.parse(
            "src.com.bni",
            "BNI Mobile",
            "Transaksi debet rekening sebesar Rp 75.000"
        ) as ParseOutcome.Parsed
        assertEquals(TransactionType.EXPENSE, bniOut.type)

        val shopeeOut = TransactionParserEngine.parse(
            "com.shopee.id",
            "ShopeePay",
            "Pembayaran ShopeePay berhasil Rp 99.000"
        ) as ParseOutcome.Parsed
        assertEquals(TransactionType.EXPENSE, shopeeOut.type)
        assertEquals(99_000L, shopeeOut.amount)

        val myBcaOut = TransactionParserEngine.parse(
            "com.bca.mybca.omni.android",
            "myBCA",
            "Transaksi debit QRIS berhasil Rp 45.000"
        ) as ParseOutcome.Parsed
        assertEquals(TransactionType.EXPENSE, myBcaOut.type)
    }

    @Test
    fun `ambiguous generic notification routes to low confidence pending review`() {
        val outcome = TransactionParserEngine.parse(
            packageName = "com.unknown.bank",
            title = "Bank XYZ",
            body = "Info transaksi Rp 320.000 di Merchant ABC"
        ) as ParseOutcome.Parsed
        assertEquals(ParseConfidence.LOW, outcome.confidence)
        assertEquals(320_000L, outcome.amount)
    }

    @Test
    fun `account balance calculation excludes pending and counts transfers properly`() {
        val now = System.currentTimeMillis()
        val accounts = listOf(
            AccountEntity(id = 1, name = "BCA", type = AccountType.BANK, initialBalance = 1_000_000L, iconKey = "account_balance", colorHex = 0xFF00529C),
            AccountEntity(id = 2, name = "DANA", type = AccountType.EWALLET, initialBalance = 100_000L, iconKey = "smartphone", colorHex = 0xFF118EEA)
        )
        val txs = listOf(
            TransactionEntity(
                id = 1, type = TransactionType.INCOME, amount = 500_000L, accountId = 1,
                transactionDate = "2026-11-25", transactionTime = "10:00", timestampMillis = now,
                sourceType = SourceType.MANUAL, sourceApplication = "Manual", status = TransactionStatus.CONFIRMED
            ),
            TransactionEntity(
                id = 2, type = TransactionType.TRANSFER, amount = 200_000L, accountId = 1, destinationAccountId = 2,
                transactionDate = "2026-11-25", transactionTime = "11:00", timestampMillis = now,
                sourceType = SourceType.MANUAL, sourceApplication = "Manual", status = TransactionStatus.CONFIRMED
            ),
            TransactionEntity(
                id = 3, type = TransactionType.EXPENSE, amount = 50_000L, accountId = 2,
                transactionDate = "2026-11-25", transactionTime = "12:00", timestampMillis = now,
                sourceType = SourceType.AUTOMATIC, sourceApplication = "DANA", status = TransactionStatus.CONFIRMED
            ),
            TransactionEntity(
                id = 4, type = TransactionType.EXPENSE, amount = 999_000L, accountId = 1,
                transactionDate = "2026-11-25", transactionTime = "12:30", timestampMillis = now,
                sourceType = SourceType.AUTOMATIC, sourceApplication = "BCA", status = TransactionStatus.PENDING_REVIEW
            )
        )

        val result = DailySpendRepository.computeAccountsWithBalance(accounts, txs)
        // BCA: 1,000,000 + 500,000 - 200,000 = 1,300,000 (pending ignored)
        assertEquals(1_300_000L, result[0].currentBalance)
        // DANA: 100,000 + 200,000 - 50,000 = 250,000
        assertEquals(250_000L, result[1].currentBalance)
    }

    @Test
    fun `budget progress calculates percentage accurately`() {
        val now = System.currentTimeMillis()
        val budgets = listOf(
            BudgetEntity(id = 1, name = "Monthly Food", amountLimit = 1_000_000L, period = BudgetPeriod.MONTHLY, categoryId = 10)
        )
        val txs = listOf(
            TransactionEntity(
                id = 1, type = TransactionType.EXPENSE, amount = 550_000L, categoryId = 10, accountId = 1,
                transactionDate = "2026-11-25", transactionTime = "10:00", timestampMillis = now,
                sourceType = SourceType.MANUAL, sourceApplication = "Manual", status = TransactionStatus.CONFIRMED
            )
        )
        val progress = DailySpendRepository.computeBudgetProgress(budgets, txs, emptyMap(), now)
        assertEquals(550_000L, progress.first().spent)
        assertEquals(450_000L, progress.first().remaining)
        assertEquals(0.55f, progress.first().percentage, 0.001f)
    }

    @Test
    fun `pin hashing and currency formatting work properly`() {
        val hash = SecurityUtils.hashPin("123456")
        assertTrue(SecurityUtils.verifyPin("123456", hash))
        assertFalse(SecurityUtils.verifyPin("654321", hash))
        assertEquals("Rp 1.250.000", CurrencyFormatter.format(1_250_000L, "IDR"))
        assertEquals("Rp ••••••", CurrencyFormatter.format(1_250_000L, "IDR", masked = true))
    }
}
