package com.example.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.PendingActions
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.TransactionEntity
import com.example.data.model.SourceType
import com.example.data.model.TransactionSort
import com.example.data.model.TransactionType
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.SwipeableTransactionRow
import com.example.ui.theme.LocalDailySpendColors
import com.example.ui.viewmodel.DailySpendUiState
import com.example.utils.CurrencyFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    state: DailySpendUiState,
    onRefresh: () -> Unit,
    onSearchChange: (String) -> Unit,
    onFilterType: (TransactionType?) -> Unit,
    onFilterSource: (SourceType?) -> Unit,
    onFilterAccount: (Long?) -> Unit,
    onSortChange: (TransactionSort) -> Unit,
    onClearFilters: () -> Unit,
    onEditTransaction: (TransactionEntity) -> Unit,
    onDeleteTransaction: (Long) -> Unit,
    onOpenReviewQueue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = LocalDailySpendColors.current
    val categoryMap = remember(state.categories) { state.categories.associateBy { it.id } }
    val accountMap = remember(state.accountsWithBalance) {
        state.accountsWithBalance.associate { it.account.id to it.account }
    }

    var sortMenuExpanded by remember { mutableStateOf(false) }
    var accountMenuExpanded by remember { mutableStateOf(false) }
    var pendingDeleteTx by remember { mutableStateOf<TransactionEntity?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Transactions",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Box {
                    TextButton(onClick = { sortMenuExpanded = true }) {
                        Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = "Sort")
                        Spacer(Modifier.width(4.dp))
                        Text(state.sortOption.label)
                    }
                    DropdownMenu(
                        expanded = sortMenuExpanded,
                        onDismissRequest = { sortMenuExpanded = false }
                    ) {
                        TransactionSort.entries.forEach { sort ->
                            DropdownMenuItem(
                                text = { Text(sort.label) },
                                onClick = {
                                    onSortChange(sort)
                                    sortMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text("Search notes, category, account, app...") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(Icons.Rounded.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("transactions_search_input")
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = state.filterType == null && state.filterSource == null && state.filterAccountId == null,
                    onClick = onClearFilters,
                    label = { Text("All") }
                )
                FilterChip(
                    selected = state.filterType == TransactionType.EXPENSE,
                    onClick = {
                        onFilterType(if (state.filterType == TransactionType.EXPENSE) null else TransactionType.EXPENSE)
                    },
                    label = { Text("Expense") }
                )
                FilterChip(
                    selected = state.filterType == TransactionType.INCOME,
                    onClick = {
                        onFilterType(if (state.filterType == TransactionType.INCOME) null else TransactionType.INCOME)
                    },
                    label = { Text("Income") }
                )
                FilterChip(
                    selected = state.filterType == TransactionType.TRANSFER,
                    onClick = {
                        onFilterType(if (state.filterType == TransactionType.TRANSFER) null else TransactionType.TRANSFER)
                    },
                    label = { Text("Transfer") }
                )
                FilterChip(
                    selected = state.filterSource == SourceType.AUTOMATIC,
                    onClick = {
                        onFilterSource(if (state.filterSource == SourceType.AUTOMATIC) null else SourceType.AUTOMATIC)
                    },
                    label = { Text("Auto-Detected") }
                )
                Box {
                    val selectedAccName = state.filterAccountId?.let { accountMap[it]?.name } ?: "Account"
                    FilterChip(
                        selected = state.filterAccountId != null,
                        onClick = { accountMenuExpanded = true },
                        label = { Text(selectedAccName) }
                    )
                    DropdownMenu(
                        expanded = accountMenuExpanded,
                        onDismissRequest = { accountMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("All Accounts") },
                            onClick = {
                                onFilterAccount(null)
                                accountMenuExpanded = false
                            }
                        )
                        state.accountsWithBalance.forEach { accBal ->
                            DropdownMenuItem(
                                text = { Text(accBal.account.name) },
                                onClick = {
                                    onFilterAccount(accBal.account.id)
                                    accountMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.weight(1f)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (state.pendingReviewTransactions.isNotEmpty()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = semantic.warningContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable(onClick = onOpenReviewQueue)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PendingActions,
                                    contentDescription = "Review queue",
                                    tint = semantic.warning
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = "${state.pendingReviewTransactions.size} pending review item(s) — Tap to verify",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                if (state.filteredTransactions.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Rounded.SearchOff,
                            title = "No matching transactions",
                            subtitle = "Try clearing your filters or record a new transaction using the + button."
                        )
                    }
                } else {
                    items(
                        items = state.filteredTransactions,
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

                item { Spacer(Modifier.height(76.dp)) }
            }
        }
    }

    pendingDeleteTx?.let { tx ->
        AlertDialog(
            onDismissRequest = { pendingDeleteTx = null },
            title = { Text("Delete Transaction?") },
            text = {
                Text("Delete ${CurrencyFormatter.format(tx.amount, state.settings.currencyCode)} from ${tx.transactionDate}?")
            },
            confirmButton = {
                Button(onClick = {
                    onDeleteTransaction(tx.id)
                    pendingDeleteTx = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteTx = null }) { Text("Cancel") }
            }
        )
    }
}
