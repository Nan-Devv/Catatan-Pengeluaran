package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PendingActions
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.TransactionEntity
import com.example.data.model.ChartPeriod
import com.example.data.model.DashboardPeriod
import com.example.data.model.TransactionType
import com.example.ui.components.AnimatedCurrencyText
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.InteractiveIncomeExpenseBarChart
import com.example.ui.components.SkeletonLoadingDashboard
import com.example.ui.components.SwipeableTransactionRow
import com.example.ui.theme.LocalDailySpendColors
import com.example.ui.viewmodel.DailySpendUiState
import com.example.utils.CurrencyFormatter
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: DailySpendUiState,
    notificationAccessEnabled: Boolean,
    onRefresh: () -> Unit,
    onToggleBalanceVisibility: () -> Unit,
    onSelectDashboardPeriod: (DashboardPeriod) -> Unit,
    onSelectChartPeriod: (ChartPeriod) -> Unit,
    onQuickAddTransaction: (TransactionType) -> Unit,
    onEditTransaction: (TransactionEntity) -> Unit,
    onDeleteTransaction: (Long) -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenStatistics: () -> Unit,
    onOpenTransactions: () -> Unit,
    onOpenReviewQueue: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (state.isLoading) {
        SkeletonLoadingDashboard(modifier = modifier)
        return
    }

    val semantic = LocalDailySpendColors.current
    val categoryMap = remember(state.categories) { state.categories.associateBy { it.id } }
    val accountMap = remember(state.accountsWithBalance) {
        state.accountsWithBalance.associate { it.account.id to it.account }
    }
    var pendingDeleteTx by remember { mutableStateOf<TransactionEntity?>(null) }

    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Greeting Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Welcome back,",
                            style = MaterialTheme.typography.bodySmall,
                            color = semantic.secondaryText
                        )
                        Text(
                            text = state.settings.profileName,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (notificationAccessEnabled) semantic.incomeContainer else semantic.warningContainer,
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(onClick = onOpenNotificationSettings)
                            .testTag("home_notification_status_pill")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.NotificationsActive,
                                contentDescription = "Notification Detection Status",
                                tint = if (notificationAccessEnabled) semantic.income else semantic.warning,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = if (notificationAccessEnabled) "Auto-Detect On" else "Enable Detect",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (notificationAccessEnabled) semantic.income else semantic.warning
                            )
                        }
                    }
                }
            }

            // Balance Card
            item {
                BalanceOverviewCard(
                    state = state,
                    onToggleBalanceVisibility = onToggleBalanceVisibility,
                    onSelectDashboardPeriod = onSelectDashboardPeriod,
                    onOpenAccounts = onOpenAccounts
                )
            }

            // Pending Review Alert if any
            if (state.pendingReviewTransactions.isNotEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = semantic.warningContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(onClick = onOpenReviewQueue)
                            .testTag("home_pending_review_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PendingActions,
                                contentDescription = "Pending review",
                                tint = semantic.warning
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${state.pendingReviewTransactions.size} detected transaction(s) need review",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Tap to verify category, type, and account",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = semantic.secondaryText
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = "Open review",
                                tint = semantic.warning
                            )
                        }
                    }
                }
            }

            // Quick Actions
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickActionTile(
                        icon = Icons.Rounded.Add,
                        label = "Income",
                        tint = semantic.income,
                        containerColor = semantic.incomeContainer,
                        onClick = { onQuickAddTransaction(TransactionType.INCOME) },
                        testTag = "quick_add_income",
                        modifier = Modifier.weight(1f)
                    )
                    QuickActionTile(
                        icon = Icons.Rounded.Remove,
                        label = "Expense",
                        tint = semantic.expense,
                        containerColor = semantic.expenseContainer,
                        onClick = { onQuickAddTransaction(TransactionType.EXPENSE) },
                        testTag = "quick_add_expense",
                        modifier = Modifier.weight(1f)
                    )
                    QuickActionTile(
                        icon = Icons.Rounded.SwapHoriz,
                        label = "Transfer",
                        tint = semantic.transfer,
                        containerColor = semantic.transferContainer,
                        onClick = { onQuickAddTransaction(TransactionType.TRANSFER) },
                        testTag = "quick_add_transfer",
                        modifier = Modifier.weight(1f)
                    )
                    QuickActionTile(
                        icon = Icons.Rounded.BarChart,
                        label = "Report",
                        tint = MaterialTheme.colorScheme.primary,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        onClick = onOpenStatistics,
                        testTag = "quick_view_report",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Interactive Financial Chart
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Cash Flow",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Tap bars for exact totals",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = semantic.secondaryText
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                LegendDot(color = semantic.income, label = "In")
                                LegendDot(color = semantic.expense, label = "Out")
                            }
                        }

                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ChartPeriod.entries.forEach { period ->
                                FilterChip(
                                    selected = state.chartPeriod == period,
                                    onClick = { onSelectChartPeriod(period) },
                                    label = { Text(period.label) }
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        InteractiveIncomeExpenseBarChart(
                            buckets = state.homeChartBuckets,
                            currencyCode = state.settings.currencyCode
                        )
                    }
                }
            }

            // Recent Transactions Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Transactions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = onOpenTransactions) {
                        Text("See All")
                    }
                }
            }

            if (state.recentTransactions.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Rounded.ReceiptLong,
                        title = "No transactions yet",
                        subtitle = "Record an expense or income manually with +, or enable Notification Access to log bank & e-wallet notifications automatically.",
                        actionLabel = "Add First Transaction",
                        onAction = { onQuickAddTransaction(TransactionType.EXPENSE) }
                    )
                }
            } else {
                items(
                    items = state.recentTransactions,
                    key = { it.id }
                ) { tx ->
                    SwipeableTransactionRow(
                        transaction = tx,
                        category = tx.categoryId?.let { categoryMap[it] },
                        account = accountMap[tx.accountId],
                        destinationAccount = tx.destinationAccountId?.let { accountMap[it] },
                        currencyCode = state.settings.currencyCode,
                        isMasked = state.isBalanceHidden,
                        onClick = { onEditTransaction(tx) },
                        onRequestDelete = { pendingDeleteTx = tx }
                    )
                }
            }

            item { Spacer(Modifier.height(72.dp)) }
        }
    }

    pendingDeleteTx?.let { tx ->
        AlertDialog(
            onDismissRequest = { pendingDeleteTx = null },
            title = { Text("Delete Transaction?") },
            text = {
                Text(
                    "Remove ${CurrencyFormatter.format(tx.amount, state.settings.currencyCode)} from ${tx.transactionDate}? Account balances will update automatically."
                )
            },
            confirmButton = {
                Button(onClick = {
                    onDeleteTransaction(tx.id)
                    pendingDeleteTx = null
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteTx = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun BalanceOverviewCard(
    state: DailySpendUiState,
    onToggleBalanceVisibility: () -> Unit,
    onSelectDashboardPeriod: (DashboardPeriod) -> Unit,
    onOpenAccounts: () -> Unit
) {
    val semantic = LocalDailySpendColors.current
    var periodMenuOpen by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, semantic.cardBorder),
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            semantic.cardGradientStart,
                            semantic.cardGradientMid,
                            semantic.cardGradientEnd
                        )
                    )
                )
        ) {
            // Airy sunlit geometric accents
            Canvas(modifier = Modifier.matchParentSize()) {
                drawCircle(
                    color = Color(0xFFFDE68A).copy(alpha = if (semantic.isDark) 0.07f else 0.36f),
                    radius = size.height * 0.55f,
                    center = Offset(size.width * 0.94f, size.height * 0.10f)
                )
                drawCircle(
                    color = Color(0xFFA7F3D0).copy(alpha = if (semantic.isDark) 0.06f else 0.28f),
                    radius = size.height * 0.42f,
                    center = Offset(size.width * 0.85f, size.height * 0.88f)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Total Balance",
                            style = MaterialTheme.typography.labelLarge,
                            color = semantic.cardContentSecondary
                        )
                        IconButton(
                            onClick = onToggleBalanceVisibility,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("toggle_balance_visibility")
                        ) {
                            Icon(
                                imageVector = if (state.isBalanceHidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = "Show or hide balance",
                                tint = semantic.cardContentPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Box {
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { periodMenuOpen = true }
                                .testTag("dashboard_period_selector")
                        ) {
                            Text(
                                text = state.dashboardPeriod.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = semantic.cardContentPrimary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = periodMenuOpen,
                            onDismissRequest = { periodMenuOpen = false }
                        ) {
                            DashboardPeriod.entries.forEach { p ->
                                DropdownMenuItem(
                                    text = { Text(p.label) },
                                    onClick = {
                                        onSelectDashboardPeriod(p)
                                        periodMenuOpen = false
                                    }
                                )
                            }
                        }
                    }
                }

                AnimatedCurrencyText(
                    targetAmount = state.totalBalance,
                    currencyCode = state.settings.currencyCode,
                    masked = state.isBalanceHidden,
                    style = MaterialTheme.typography.displaySmall,
                    color = semantic.cardContentPrimary,
                    modifier = Modifier.testTag("total_balance_text")
                )

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val changePct = state.expenseChangePercent
                    val changeLabel = when {
                        changePct == null -> "Month-over-month tracking active"
                        changePct > 0f -> "Spending +${abs(changePct).toInt()}% vs last month"
                        changePct < 0f -> "Spending -${abs(changePct).toInt()}% vs last month"
                        else -> "Spending flat vs last month"
                    }
                    Text(
                        text = changeLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = semantic.cardContentSecondary,
                        modifier = Modifier.weight(1f)
                    )

                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onOpenAccounts)
                            .testTag("view_accounts_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AccountBalanceWallet,
                                contentDescription = "Accounts",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = "${state.accountsWithBalance.size} Accounts",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    BalanceStatPill(
                        label = "Income",
                        amount = state.periodIncome,
                        currencyCode = state.settings.currencyCode,
                        masked = state.isBalanceHidden,
                        icon = Icons.Rounded.ArrowDownward,
                        accent = semantic.income,
                        accentContainer = semantic.incomeContainer,
                        modifier = Modifier.weight(1f)
                    )
                    BalanceStatPill(
                        label = "Expense",
                        amount = state.periodExpense,
                        currencyCode = state.settings.currencyCode,
                        masked = state.isBalanceHidden,
                        icon = Icons.Rounded.ArrowUpward,
                        accent = semantic.expense,
                        accentContainer = semantic.expenseContainer,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun BalanceStatPill(
    label: String,
    amount: Long,
    currencyCode: String,
    masked: Boolean,
    icon: ImageVector,
    accent: Color,
    accentContainer: Color,
    modifier: Modifier = Modifier
) {
    val semantic = LocalDailySpendColors.current
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(accentContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = accent,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = semantic.cardContentSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                AnimatedCurrencyText(
                    targetAmount = amount,
                    currencyCode = currencyCode,
                    masked = masked,
                    style = MaterialTheme.typography.titleSmall,
                    color = semantic.cardContentPrimary
                )
            }
        }
    }
}

@Composable
private fun QuickActionTile(
    icon: ImageVector,
    label: String,
    tint: Color,
    containerColor: Color,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .minimumInteractiveComponentSize()
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(containerColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = tint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = LocalDailySpendColors.current.secondaryText
        )
    }
}
