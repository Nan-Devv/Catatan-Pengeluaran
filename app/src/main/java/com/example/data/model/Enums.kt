package com.example.data.model

enum class TransactionType {
    INCOME,
    EXPENSE,
    TRANSFER
}

enum class SourceType {
    MANUAL,
    AUTOMATIC
}

enum class TransactionStatus {
    CONFIRMED,
    PENDING_REVIEW,
    REJECTED
}

enum class CategoryType {
    INCOME,
    EXPENSE
}

enum class AccountType {
    CASH,
    BANK,
    EWALLET,
    CUSTOM
}

enum class BudgetPeriod {
    MONTHLY,
    WEEKLY
}

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

enum class ChartPeriod(val label: String, val days: Int) {
    SEVEN_DAYS("7D", 7),
    THIRTY_DAYS("30D", 30),
    THREE_MONTHS("3M", 90),
    SIX_MONTHS("6M", 180),
    ONE_YEAR("1Y", 365)
}

enum class DashboardPeriod(val label: String) {
    THIS_MONTH("This Month"),
    LAST_30_DAYS("Last 30 Days"),
    THIS_WEEK("This Week"),
    ALL_TIME("All Time")
}

enum class TransactionSort(val label: String) {
    NEWEST("Newest First"),
    OLDEST("Oldest First"),
    HIGHEST("Highest Amount"),
    LOWEST("Lowest Amount")
}

enum class StatsDateFilter(val label: String) {
    THIS_MONTH("This Month"),
    LAST_MONTH("Last Month"),
    THREE_MONTHS("Last 3 Months"),
    YEAR_TO_DATE("Year to Date"),
    CUSTOM("Custom Range")
}

enum class ExportPeriod(val label: String) {
    THIS_MONTH("This Month"),
    LAST_MONTH("Last Month"),
    LAST_3_MONTHS("Last 3 Months"),
    ALL_TIME("All Time")
}
