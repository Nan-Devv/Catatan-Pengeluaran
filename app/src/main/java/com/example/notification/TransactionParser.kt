package com.example.notification

import com.example.data.model.TransactionType
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.roundToLong

enum class ParseConfidence {
    HIGH,
    LOW
}

sealed class ParseOutcome {
    data object IgnoredSensitive : ParseOutcome()
    data object IgnoredNonTransaction : ParseOutcome()
    data class Parsed(
        val sourceApplication: String,
        val amount: Long,
        val type: TransactionType,
        val confidence: ParseConfidence,
        val inferredCategoryName: String,
        val sanitizedLabel: String,
        val deduplicationHash: String
    ) : ParseOutcome()
}

interface AppNotificationParser {
    val supportedPackages: Set<String>
    val displayName: String
    fun parse(title: String, body: String): ParseOutcome
}

object TransactionParserEngine {

    private val sensitiveKeywords = listOf(
        "otp", "kode otp", "kode verifikasi", "verification code",
        "pin ", " pin", "mpin", "password", "kata sandi", "cvv",
        "jangan beritahu", "do not share", "rahasia", "one time password",
        "kode keamanan", "security code", "token", "aktivasi"
    )

    private val promoNoiseKeywords = listOf(
        "diskon hingga", "promo spesial", "cashback hingga", "voucher",
        "undang teman", "ajak teman", "klaim hadiah", "penawaran",
        "bunga spesial", "flash sale", "gratis ongkir", "jangan lewatkan"
    )

    private val rupiahPattern = Regex(
        """(?i)(?:rp\.?\s*|idr\s*)(\d{1,3}(?:[.,]\d{3})+|\d+)(?:[.,](\d{1,2}))?(?:\s*(rb|ribu|jt|juta))?"""
    )

    private val contextualAmountPattern = Regex(
        """(?i)(?:nominal|sebesar|senilai|jumlah|total)\s*:?\s*(?:rp\.?\s*|idr\s*)?(\d{1,3}(?:[.,]\d{3})+|\d+)(?:[.,]\d{1,2})?"""
    )

    private val parsers: List<AppNotificationParser> = listOf(
        BcaMobileParser(),
        MyBcaParser(),
        DanaParser(),
        BrimoParser(),
        BniMobileParser(),
        LivinMandiriParser(),
        GoPayParser(),
        OvoParser(),
        ShopeePayParser()
    )

    private val genericParser = GenericFinanceParser()

    fun isNotificationAccessPackageSupported(packageName: String): Boolean {
        return parsers.any { packageName in it.supportedPackages }
    }

    fun parse(
        packageName: String,
        title: String,
        body: String,
        allowGenericFallback: Boolean = true
    ): ParseOutcome {
        val combined = "$title $body".trim()
        if (combined.isBlank()) return ParseOutcome.IgnoredNonTransaction

        val lower = combined.lowercase(Locale("id", "ID"))

        // 1. Security-first check: never parse or store OTP, PIN, passwords, or secrets
        if (sensitiveKeywords.any { lower.contains(it) }) {
            return ParseOutcome.IgnoredSensitive
        }

        // 2. Ignore marketing/promotional push notifications
        if (promoNoiseKeywords.any { lower.contains(it) }) {
            return ParseOutcome.IgnoredNonTransaction
        }

        val dedicated = parsers.firstOrNull { packageName in it.supportedPackages }
        if (dedicated != null) {
            return dedicated.parse(title, body)
        }

        return if (allowGenericFallback) {
            genericParser.parse(title, body)
        } else {
            ParseOutcome.IgnoredNonTransaction
        }
    }

    fun extractRupiahAmount(text: String): Long? {
        val match = rupiahPattern.find(text) ?: contextualAmountPattern.find(text) ?: return null
        val rawMain = match.groupValues[1]
        val suffix = match.groupValues.getOrNull(3)?.lowercase(Locale.US).orEmpty()

        val cleanedDigits = if (rawMain.contains('.') && rawMain.contains(',')) {
            rawMain.replace(".", "").substringBefore(",")
        } else if (rawMain.count { it == '.' } >= 1 && rawMain.substringAfterLast('.').length == 3) {
            rawMain.replace(".", "")
        } else if (rawMain.count { it == ',' } >= 1 && rawMain.substringAfterLast(',').length == 3) {
            rawMain.replace(",", "")
        } else {
            rawMain.replace(".", "").replace(",", "")
        }

        val base = cleanedDigits.toLongOrNull() ?: return null
        val multiplier = when (suffix) {
            "rb", "ribu" -> 1_000L
            "jt", "juta" -> 1_000_000L
            else -> 1L
        }

        val result = if (multiplier > 1L && rawMain.contains(',')) {
            val decimalVal = rawMain.replace(".", "").replace(",", ".").toDoubleOrNull() ?: return null
            (decimalVal * multiplier).roundToLong()
        } else {
            base * multiplier
        }

        // Reject unrealistically tiny (<100) or overflow values
        return if (result in 100L..10_000_000_000L) result else null
    }

    fun buildDeduplicationHash(sourceApp: String, type: TransactionType, amount: Long): String {
        val key = "${sourceApp.lowercase(Locale.US)}|${type.name}|$amount"
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(24)
    }

    fun inferCategory(lowerText: String, type: TransactionType): String {
        if (type == TransactionType.INCOME) {
            return when {
                lowerText.contains("gaji") || lowerText.contains("salary") || lowerText.contains("payroll") -> "Salary"
                lowerText.contains("dividen") || lowerText.contains("bunga") || lowerText.contains("invest") -> "Investment"
                lowerText.contains("transfer") || lowerText.contains("terima") || lowerText.contains("masuk") -> "Transfer Received"
                else -> "Other Income"
            }
        }
        return when {
            listOf("gofood", "grabfood", "shopeefood", "kopi", "coffee", "cafe", "resto", "warung", "makan", "bakery", "indomaret", "alfamart", "starbucks", "mcd", "kfc")
                .any { lowerText.contains(it) } -> "Food & Beverage"
            listOf("gojek", "goride", "gocar", "grab", "bluebird", "mrt", "kai", "transjakarta", "pertamina", "shell", "bensin", "parkir", "tol")
                .any { lowerText.contains(it) } -> "Transportation"
            listOf("pln", "listrik", "pdam", "indihome", "telkom", "bpjs", "pulsa", "paket data", "token", "tagihan", "wifi", "internet", "air")
                .any { lowerText.contains(it) } -> "Bills"
            listOf("tokopedia", "shopee", "lazada", "blibli", "belanja", "mall", "uniqlo", "store")
                .any { lowerText.contains(it) } -> "Shopping"
            listOf("netflix", "spotify", "cinema", "xxi", "cgv", "youtube", "steam", "game")
                .any { lowerText.contains(it) } -> "Entertainment"
            listOf("apotek", "kimia farma", "halodoc", "rumah sakit", "klinik", "dokter")
                .any { lowerText.contains(it) } -> "Health"
            listOf("sekolah", "kampus", "kursus", "buku", "tuition", "education")
                .any { lowerText.contains(it) } -> "Education"
            else -> "Other Expenses"
        }
    }

    internal fun evaluateStandardFinanceNotification(
        appName: String,
        title: String,
        body: String,
        extraOutgoing: List<String> = emptyList(),
        extraIncoming: List<String> = emptyList(),
        isGeneric: Boolean = false
    ): ParseOutcome {
        val combined = "$title $body"
        val lower = combined.lowercase(Locale("id", "ID"))

        val amount = extractRupiahAmount(combined) ?: return ParseOutcome.IgnoredNonTransaction

        val outgoingSignals = listOf(
            "pembayaran", "bayar", "berhasil dikirim", "transfer ke", "kirim uang",
            "keluar", "debit", "debet", "tarik tunai", "pembelian", "purchase",
            "qris", "merchant", "biaya", "terpotong", "berkurang", "sukses bayar",
            "payment to", "paid", "sent to"
        ) + extraOutgoing

        val incomingSignals = listOf(
            "transfer masuk", "dana masuk", "saldo masuk", "diterima", "menerima",
            "kredit", "bertambah", "terima uang", "terima dana", "top up berhasil",
            "topup berhasil", "isi saldo berhasil", "cashback diterima", "received from",
            "incoming transfer", "masuk sebesar"
        ) + extraIncoming

        val isOutgoing = outgoingSignals.any { lower.contains(it) }
        val isIncoming = incomingSignals.any { lower.contains(it) }

        val (type, confidence) = when {
            isOutgoing && !isIncoming -> TransactionType.EXPENSE to if (isGeneric) ParseConfidence.LOW else ParseConfidence.HIGH
            isIncoming && !isOutgoing -> TransactionType.INCOME to if (isGeneric) ParseConfidence.LOW else ParseConfidence.HIGH
            isOutgoing && isIncoming -> {
                // e.g. "Pembayaran berhasil" vs "Transfer masuk ke rekening" — check stronger cues
                if (lower.contains("masuk") || lower.contains("diterima") || lower.contains("menerima")) {
                    TransactionType.INCOME to ParseConfidence.LOW
                } else {
                    TransactionType.EXPENSE to ParseConfidence.LOW
                }
            }
            else -> TransactionType.EXPENSE to ParseConfidence.LOW
        }

        val inferredCategory = inferCategory(lower, type)
        val directionLabel = if (type == TransactionType.INCOME) "Incoming" else "Outgoing"
        val confLabel = if (confidence == ParseConfidence.HIGH) "High confidence" else "Needs review"
        val sanitizedLabel = "$appName • $directionLabel ($confLabel)"

        return ParseOutcome.Parsed(
            sourceApplication = appName,
            amount = amount,
            type = type,
            confidence = confidence,
            inferredCategoryName = inferredCategory,
            sanitizedLabel = sanitizedLabel,
            deduplicationHash = buildDeduplicationHash(appName, type, amount)
        )
    }
}

class BcaMobileParser : AppNotificationParser {
    override val supportedPackages = setOf("com.bca")
    override val displayName = "BCA Mobile"
    override fun parse(title: String, body: String): ParseOutcome =
        TransactionParserEngine.evaluateStandardFinanceNotification(
            appName = displayName,
            title = title,
            body = body,
            extraOutgoing = listOf("m-transfer", "bca virtual account", "flazz", "qris bca"),
            extraIncoming = listOf("uang masuk", "kredit rekening")
        )
}

class MyBcaParser : AppNotificationParser {
    override val supportedPackages = setOf("com.bca.mybca.omni.android")
    override val displayName = "myBCA"
    override fun parse(title: String, body: String): ParseOutcome =
        TransactionParserEngine.evaluateStandardFinanceNotification(
            appName = displayName,
            title = title,
            body = body,
            extraOutgoing = listOf("transaksi debit", "qris", "va bca"),
            extraIncoming = listOf("transaksi kredit", "dana diterima")
        )
}

class DanaParser : AppNotificationParser {
    override val supportedPackages = setOf("id.dana")
    override val displayName = "DANA"
    override fun parse(title: String, body: String): ParseOutcome =
        TransactionParserEngine.evaluateStandardFinanceNotification(
            appName = displayName,
            title = title,
            body = body,
            extraOutgoing = listOf("kamu bayar", "kirim saldo", "transaksi dana berhasil"),
            extraIncoming = listOf("kamu menerima", "saldo dana bertambah", "dana kaget")
        )
}

class BrimoParser : AppNotificationParser {
    override val supportedPackages = setOf("id.co.bri.brimo")
    override val displayName = "BRImo"
    override fun parse(title: String, body: String): ParseOutcome =
        TransactionParserEngine.evaluateStandardFinanceNotification(
            appName = displayName,
            title = title,
            body = body,
            extraOutgoing = listOf("briva", "pembelian pulsa", "transaksi brimo"),
            extraIncoming = listOf("dana masuk ke rekening", "mutasi kredit")
        )
}

class BniMobileParser : AppNotificationParser {
    override val supportedPackages = setOf("src.com.bni")
    override val displayName = "BNI Mobile Banking"
    override fun parse(title: String, body: String): ParseOutcome =
        TransactionParserEngine.evaluateStandardFinanceNotification(
            appName = displayName,
            title = title,
            body = body,
            extraOutgoing = listOf("debet rekening", "transaksi debet"),
            extraIncoming = listOf("kredit rekening", "setoran", "transfer masuk")
        )
}

class LivinMandiriParser : AppNotificationParser {
    override val supportedPackages = setOf("id.bmri.livin")
    override val displayName = "Livin' by Mandiri"
    override fun parse(title: String, body: String): ParseOutcome =
        TransactionParserEngine.evaluateStandardFinanceNotification(
            appName = displayName,
            title = title,
            body = body,
            extraOutgoing = listOf("pembayaran sukses", "livin' pay", "va mandiri"),
            extraIncoming = listOf("dana masuk", "uang masuk")
        )
}

class GoPayParser : AppNotificationParser {
    override val supportedPackages = setOf("com.gojek.gopay", "com.gojek.app")
    override val displayName = "GoPay"
    override fun parse(title: String, body: String): ParseOutcome =
        TransactionParserEngine.evaluateStandardFinanceNotification(
            appName = displayName,
            title = title,
            body = body,
            extraOutgoing = listOf("pembayaran gopay", "bayar pakai gopay", "gofood", "goride", "gocar"),
            extraIncoming = listOf("saldo gopay masuk", "terima gopay", "top up gopay berhasil")
        )
}

class OvoParser : AppNotificationParser {
    override val supportedPackages = setOf("ovo.id")
    override val displayName = "OVO"
    override fun parse(title: String, body: String): ParseOutcome =
        TransactionParserEngine.evaluateStandardFinanceNotification(
            appName = displayName,
            title = title,
            body = body,
            extraOutgoing = listOf("pembayaran di", "ovo cash berkurang"),
            extraIncoming = listOf("top up ovo", "ovo cash masuk", "terima transfer ovo")
        )
}

class ShopeePayParser : AppNotificationParser {
    override val supportedPackages = setOf("com.shopee.id", "com.shopeepay.id")
    override val displayName = "ShopeePay"
    override fun parse(title: String, body: String): ParseOutcome =
        TransactionParserEngine.evaluateStandardFinanceNotification(
            appName = displayName,
            title = title,
            body = body,
            extraOutgoing = listOf("pembayaran shopeepay", "pesanan dibayar", "checkout"),
            extraIncoming = listOf("isi saldo shopeepay", "terima dana shopeepay", "pengembalian dana")
        )
}

class GenericFinanceParser : AppNotificationParser {
    override val supportedPackages = setOf("*")
    override val displayName = "Other Finance App"
    override fun parse(title: String, body: String): ParseOutcome =
        TransactionParserEngine.evaluateStandardFinanceNotification(
            appName = displayName,
            title = title,
            body = body,
            isGeneric = true
        )
}
