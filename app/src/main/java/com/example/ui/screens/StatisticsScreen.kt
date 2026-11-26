package com.example.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.StatsDateFilter
import com.example.ui.components.DailySpendingSparklineChart
import com.example.ui.components.InteractiveDonutChart
import com.example.ui.components.InteractiveIncomeExpenseBarChart
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalDailySpendColors
import com.example.ui.viewmodel.DailySpendUiState
import com.example.utils.CurrencyFormatter
import com.example.utils.DateUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    state: DailySpendUiState,
    onSelectStatsFilter: (StatsDateFilter) -> Unit,
    onSelectCustomRange: (Long, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = LocalDailySpendColors.current
    var showDateRangeDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Statistics & Analytics",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (state.statsFilter == StatsDateFilter.CUSTOM) {
                        "${DateUtils.formatDisplayDate(state.statsCustomStart)} – ${DateUtils.formatDisplayDate(state.statsCustomEnd)}"
                    } else {
                        "Interactive cash flow & category insights"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = semantic.secondaryText
                )
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatsDateFilter.entries.forEach { f ->
                    FilterChip(
                        selected = state.statsFilter == f,
                        onClick = {
                            if (f == StatsDateFilter.CUSTOM) {
                                showDateRangeDialog = true
                            } else {
                                onSelectStatsFilter(f)
                            }
                        },
                        label = { Text(f.label) }
                    )
                }
            }
        }

        // KPI Row: Savings & Expense Ratio
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatKpiCard(
                    title = "Net Savings",
                    value = CurrencyFormatter.format(state.statsSavings, state.settings.currencyCode, state.isBalanceHidden),
                    subtitle = "Income minus Expense",
                    accent = if (state.statsSavings >= 0) semantic.income else semantic.expense,
                    modifier = Modifier.weight(1f)
                )
                StatKpiCard(
                    title = "Expense Ratio",
                    value = "${(state.statsExpenseRatio * 100).toInt()}%",
                    subtitle = if (state.statsIncome > 0) "Of period income" else "No income yet",
                    accent = if (state.statsExpenseRatio <= 0.8f) semantic.income else semantic.expense,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Month-over-Month Comparison Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Month-over-Month Comparison",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("This Month", style = MaterialTheme.typography.labelMedium, color = semantic.secondaryText)
                            Text(
                                text = "+${CurrencyFormatter.format(state.monthIncome, state.settings.currencyCode, state.isBalanceHidden)}",
                                style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = semantic.income
                            )
                            Text(
                                text = "-${CurrencyFormatter.format(state.monthExpense, state.settings.currencyCode, state.isBalanceHidden)}",
                                style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = semantic.expense
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Previous Month", style = MaterialTheme.typography.labelMedium, color = semantic.secondaryText)
                            Text(
                                text = "+${CurrencyFormatter.format(state.prevMonthIncome, state.settings.currencyCode, state.isBalanceHidden)}",
                                style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = semantic.income.copy(alpha = 0.75f)
                            )
                            Text(
                                text = "-${CurrencyFormatter.format(state.prevMonthExpense, state.settings.currencyCode, state.isBalanceHidden)}",
                                style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = semantic.expense.copy(alpha = 0.75f)
                            )
                        }
                    }
                }
            }
        }

        // Monthly Income vs Expense Chart
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "6-Month Income vs Expense",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Tap any month column for details",
                        style = MaterialTheme.typography.bodySmall,
                        color = semantic.secondaryText
                    )
                    Spacer(Modifier.height(10.dp))
                    InteractiveIncomeExpenseBarChart(
                        buckets = state.statsMonthlyBuckets,
                        currencyCode = state.settings.currencyCode
                    )
                }
            }
        }

        // Daily Expense Trend
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Daily Spending Trend",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Tap data points to inspect daily outflow",
                        style = MaterialTheme.typography.bodySmall,
                        color = semantic.secondaryText
                    )
                    Spacer(Modifier.height(10.dp))
                    DailySpendingSparklineChart(
                        buckets = state.statsDailyBuckets,
                        currencyCode = state.settings.currencyCode
                    )
                }
            }
        }

        // Category Pie / Donut Chart + Top Spending Categories
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Spending by Category",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Tap any slice or category row to highlight",
                        style = MaterialTheme.typography.bodySmall,
                        color = semantic.secondaryText
                    )
                    Spacer(Modifier.height(12.dp))
                    InteractiveDonutChart(
                        slices = state.statsCategorySlices,
                        currencyCode = state.settings.currencyCode
                    )
                }
            }
        }

        item { Spacer(Modifier.height(76.dp)) }
    }

    if (showDateRangeDialog) {
        val rangeState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = state.statsCustomStart,
            initialSelectedEndDateMillis = state.statsCustomEnd
        )
        DatePickerDialog(
            onDismissRequest = { showDateRangeDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val s = rangeState.selectedStartDateMillis
                        val e = rangeState.selectedEndDateMillis
                        if (s != null && e != null) {
                            onSelectCustomRange(s, e)
                        }
                        showDateRangeDialog = false
                    }
                ) { Text("Apply") }
            },
            dismissButton = {
                TextButton(onClick = { showDateRangeDialog = false }) { Text("Cancel") }
            }
        ) {
            DateRangePicker(state = rangeState, modifier = Modifier.height(440.dp))
        }
    }
}

@Composable
private fun StatKpiCard(
    title: String,
    value: String,
    subtitle: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val semantic = LocalDailySpendColors.current
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(text = title, style = MaterialTheme.typography.labelMedium, color = semantic.secondaryText)
            Spacer(Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = accent,
                maxLines = 1
            )
            Spacer(Modifier.height(2.dp))
            Text(text = subtitle, style = MaterialTheme.typography.labelSmall, color = semantic.secondaryText)
        }
    }
}
