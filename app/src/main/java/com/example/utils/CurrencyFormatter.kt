package com.example.utils

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs

object CurrencyFormatter {

    private val idSymbols = DecimalFormatSymbols(Locale("id", "ID")).apply {
        groupingSeparator = '.'
        decimalSeparator = ','
    }

    private val idFormatter = DecimalFormat("#,##0", idSymbols)

    fun format(amount: Long, currencyCode: String = "IDR", masked: Boolean = false): String {
        if (masked) {
            return when (currencyCode) {
                "USD" -> "$ ••••••"
                "SGD" -> "S$ ••••••"
                "MYR" -> "RM ••••••"
                else -> "Rp ••••••"
            }
        }
        val sign = if (amount < 0) "-" else ""
        val absVal = abs(amount)
        val formattedNumber = idFormatter.format(absVal)
        return when (currencyCode) {
            "USD" -> "$sign$$formattedNumber"
            "SGD" -> "${sign}S$$formattedNumber"
            "MYR" -> "${sign}RM $formattedNumber"
            else -> "${sign}Rp $formattedNumber"
        }
    }

    fun formatSigned(amount: Long, isPositive: Boolean, currencyCode: String = "IDR"): String {
        val prefix = if (isPositive) "+" else "-"
        return "$prefix${format(abs(amount), currencyCode)}"
    }

    fun formatCompact(amount: Long, currencyCode: String = "IDR"): String {
        val absVal = abs(amount)
        val sign = if (amount < 0) "-" else ""
        val symbol = when (currencyCode) {
            "USD" -> "$"
            "SGD" -> "S$"
            "MYR" -> "RM"
            else -> "Rp"
        }
        return when {
            absVal >= 1_000_000_000L -> {
                val valM = absVal / 1_000_000_000.0
                "$sign$symbol ${String.format(Locale.US, "%.1f", valM)}M"
            }
            absVal >= 1_000_000L -> {
                val valJt = absVal / 1_000_000.0
                val suffix = if (currencyCode == "IDR") "jt" else "M"
                "$sign$symbol ${String.format(Locale.US, "%.1f", valJt)}$suffix"
            }
            absVal >= 1_000L -> {
                val valRb = absVal / 1_000.0
                val suffix = if (currencyCode == "IDR") "rb" else "K"
                "$sign$symbol ${String.format(Locale.US, "%.0f", valRb)}$suffix"
            }
            else -> "$sign$symbol $absVal"
        }
    }
}
