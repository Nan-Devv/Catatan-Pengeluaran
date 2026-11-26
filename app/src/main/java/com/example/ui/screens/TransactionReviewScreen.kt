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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.local.AccountEntity
import com.example.data.local.CategoryEntity
import com.example.data.local.TransactionEntity
import com.example.data.model.CategoryType
import com.example.data.model.TransactionType
import com.example.ui.components.EmptyStateCard
import com.example.ui.theme.LocalDailySpendColors
import com.example.ui.viewmodel.DailySpendUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionReviewScreen(
    state: DailySpendUiState,
    onBack: () -> Unit,
    onApprove: (
        tx: TransactionEntity,
        amount: Long,
        type: TransactionType,
        categoryId: Long?,
        accountId: Long,
        destAccountId: Long?,
        notes: String
    ) -> Unit,
    onReject: (TransactionEntity) -> Unit
) {
    val accounts = state.accountsWithBalance.map { it.account }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transaction Review") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (state.pendingReviewTransactions.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Rounded.DoneAll,
                        title = "All caught up!",
                        subtitle = "There are no ambiguous auto-detected transactions awaiting review."
                    )
                }
            } else {
                items(
                    items = state.pendingReviewTransactions,
                    key = { it.id }
                ) { tx ->
                    PendingReviewCard(
                        tx = tx,
                        accounts = accounts,
                        categories = state.categories,
                        currencyCode = state.settings.currencyCode,
                        onApprove = onApprove,
                        onReject = onReject
                    )
                }
            }
        }
    }
}

@Composable
private fun PendingReviewCard(
    tx: TransactionEntity,
    accounts: List<AccountEntity>,
    categories: List<CategoryEntity>,
    currencyCode: String,
    onApprove: (
        tx: TransactionEntity,
        amount: Long,
        type: TransactionType,
        categoryId: Long?,
        accountId: Long,
        destAccountId: Long?,
        notes: String
    ) -> Unit,
    onReject: (TransactionEntity) -> Unit
) {
    val semantic = LocalDailySpendColors.current
    var amountStr by remember(tx.id) { mutableStateOf(tx.amount.toString()) }
    var txType by remember(tx.id) { mutableStateOf(tx.type) }
    var accountId by remember(tx.id) { mutableLongStateOf(tx.accountId) }
    var destAccountId by remember(tx.id) {
        mutableStateOf(tx.destinationAccountId ?: accounts.firstOrNull { it.id != tx.accountId }?.id)
    }
    val matchingCategories = remember(txType, categories) {
        when (txType) {
            TransactionType.INCOME -> categories.filter { it.type == CategoryType.INCOME }
            TransactionType.EXPENSE -> categories.filter { it.type == CategoryType.EXPENSE }
            TransactionType.TRANSFER -> emptyList()
        }
    }
    var categoryId by remember(tx.id, txType) {
        val currentValid = matchingCategories.firstOrNull { it.id == tx.categoryId }?.id
        mutableStateOf(currentValid ?: matchingCategories.firstOrNull()?.id)
    }
    var notes by remember(tx.id) { mutableStateOf(tx.notes) }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Detected from ${tx.sourceApplication} • ${tx.transactionDate} ${tx.transactionTime}",
                style = MaterialTheme.typography.labelLarge,
                color = semantic.warning
            )

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val opts = listOf(TransactionType.EXPENSE to "Expense", TransactionType.INCOME to "Income", TransactionType.TRANSFER to "Transfer")
                opts.forEachIndexed { idx, (type, label) ->
                    SegmentedButton(
                        selected = txType == type,
                        onClick = { txType = type },
                        shape = SegmentedButtonDefaults.itemShape(idx, opts.size)
                    ) { Text(label) }
                }
            }

            OutlinedTextField(
                value = amountStr,
                onValueChange = { amountStr = it.filter { c -> c.isDigit() }.take(12) },
                label = { Text("Amount ($currencyCode)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text("Account", style = MaterialTheme.typography.labelMedium)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                accounts.forEach { acc ->
                    FilterChip(
                        selected = accountId == acc.id,
                        onClick = { accountId = acc.id },
                        label = { Text(acc.name) }
                    )
                }
            }

            if (txType == TransactionType.TRANSFER) {
                Text("Destination Account", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    accounts.filter { it.id != accountId }.forEach { acc ->
                        FilterChip(
                            selected = destAccountId == acc.id,
                            onClick = { destAccountId = acc.id },
                            label = { Text(acc.name) }
                        )
                    }
                }
            } else {
                Text("Category", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    matchingCategories.forEach { cat ->
                        FilterChip(
                            selected = categoryId == cat.id,
                            onClick = { categoryId = cat.id },
                            label = { Text(cat.name) }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { onReject(tx) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(16.dp), tint = semantic.expense)
                    Spacer(Modifier.width(4.dp))
                    Text("Reject", color = semantic.expense)
                }
                val parsedAmt = amountStr.toLongOrNull() ?: 0L
                Button(
                    onClick = {
                        onApprove(tx, parsedAmt, txType, categoryId, accountId, destAccountId, notes)
                    },
                    enabled = parsedAmt > 0L,
                    colors = ButtonDefaults.buttonColors(containerColor = semantic.income),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Approve")
                }
            }
        }
    }
}
