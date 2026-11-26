package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.local.BudgetEntity
import com.example.data.local.CategoryEntity
import com.example.data.model.BudgetPeriod
import com.example.data.model.CategoryType
import com.example.data.repository.BudgetProgress
import com.example.ui.components.CategoryIconMapper
import com.example.ui.components.EmptyStateCard
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalDailySpendColors
import com.example.ui.viewmodel.DailySpendUiState
import com.example.utils.CurrencyFormatter

@Composable
fun BudgetScreen(
    state: DailySpendUiState,
    onSaveBudget: (id: Long, name: String, limit: Long, period: BudgetPeriod, categoryId: Long?) -> Unit,
    onDeleteBudget: (BudgetEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = LocalDailySpendColors.current
    var showBudgetSheet by remember { mutableStateOf(false) }
    var editingBudget by remember { mutableStateOf<BudgetEntity?>(null) }
    var deletingBudget by remember { mutableStateOf<BudgetEntity?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Budgets",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Automatic alerts at 50%, 80%, and 100% usage",
                        style = MaterialTheme.typography.bodySmall,
                        color = semantic.secondaryText
                    )
                }
                FilledTonalButton(
                    onClick = {
                        editingBudget = null
                        showBudgetSheet = true
                    },
                    modifier = Modifier.testTag("add_budget_button")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("New Budget")
                }
            }
        }

        if (state.budgetProgressList.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Rounded.PieChart,
                    title = "No budgets configured",
                    subtitle = "Set monthly, weekly, or per-category spending limits to stay on track and receive milestone alerts.",
                    actionLabel = "Create First Budget",
                    onAction = {
                        editingBudget = null
                        showBudgetSheet = true
                    }
                )
            }
        } else {
            items(
                items = state.budgetProgressList,
                key = { it.budget.id }
            ) { bp ->
                BudgetProgressCard(
                    progress = bp,
                    currencyCode = state.settings.currencyCode,
                    isMasked = state.isBalanceHidden,
                    onEdit = {
                        editingBudget = bp.budget
                        showBudgetSheet = true
                    },
                    onDelete = { deletingBudget = bp.budget }
                )
            }
        }

        item { Spacer(Modifier.height(76.dp)) }
    }

    if (showBudgetSheet) {
        BudgetEditorSheet(
            editing = editingBudget,
            expenseCategories = state.categories.filter { it.type == CategoryType.EXPENSE },
            currencyCode = state.settings.currencyCode,
            onDismiss = { showBudgetSheet = false },
            onSave = { id, name, limit, period, catId ->
                onSaveBudget(id, name, limit, period, catId)
                showBudgetSheet = false
            }
        )
    }

    deletingBudget?.let { b ->
        AlertDialog(
            onDismissRequest = { deletingBudget = null },
            title = { Text("Delete Budget?") },
            text = { Text("Remove budget \"${b.name}\"?") },
            confirmButton = {
                Button(onClick = {
                    onDeleteBudget(b)
                    deletingBudget = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deletingBudget = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun BudgetProgressCard(
    progress: BudgetProgress,
    currencyCode: String,
    isMasked: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val semantic = LocalDailySpendColors.current
    val pctInt = (progress.percentage * 100f).toInt()
    val animatedFill by animateFloatAsState(
        targetValue = progress.percentage.coerceIn(0f, 1f),
        animationSpec = tween(550),
        label = "budgetProgress"
    )
    val barColor = when {
        pctInt >= 100 -> semantic.expense
        pctInt >= 80 -> Color(0xFFF97316)
        pctInt >= 50 -> semantic.warning
        else -> semantic.income
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(barColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = CategoryIconMapper.iconForKey(progress.category?.iconKey ?: "pie_chart"),
                        contentDescription = progress.budget.name,
                        tint = barColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = progress.budget.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${if (progress.budget.period == BudgetPeriod.MONTHLY) "Monthly" else "Weekly"} • ${progress.category?.name ?: "All Categories"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = semantic.secondaryText
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Rounded.Edit, contentDescription = "Edit budget", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Delete budget", tint = semantic.expense, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { animatedFill },
                color = barColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(6.dp))
            )

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${CurrencyFormatter.format(progress.spent, currencyCode, isMasked)} / ${CurrencyFormatter.format(progress.budget.amountLimit, currencyCode, isMasked)}",
                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFontFamily)
                )
                Surface(
                    color = barColor.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (pctInt >= 80) {
                            Icon(
                                imageVector = Icons.Rounded.WarningAmber,
                                contentDescription = null,
                                tint = barColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            text = "$pctInt%",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = barColor
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BudgetEditorSheet(
    editing: BudgetEntity?,
    expenseCategories: List<CategoryEntity>,
    currencyCode: String,
    onDismiss: () -> Unit,
    onSave: (id: Long, name: String, limit: Long, period: BudgetPeriod, categoryId: Long?) -> Unit
) {
    var name by remember(editing) { mutableStateOf(editing?.name ?: "") }
    var limitText by remember(editing) { mutableStateOf(editing?.amountLimit?.toString() ?: "") }
    var period by remember(editing) { mutableStateOf(editing?.period ?: BudgetPeriod.MONTHLY) }
    var categoryId by remember(editing) { mutableStateOf(editing?.categoryId) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = if (editing != null) "Edit Budget" else "Create Budget",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Budget Name (e.g. Monthly Dining)") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("budget_name_input")
            )

            OutlinedTextField(
                value = limitText,
                onValueChange = { limitText = it.filter { c -> c.isDigit() }.take(12) },
                label = { Text("Limit Amount ($currencyCode)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("budget_limit_input")
            )

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val periods = listOf(BudgetPeriod.MONTHLY to "Monthly", BudgetPeriod.WEEKLY to "Weekly")
                periods.forEachIndexed { idx, (p, label) ->
                    SegmentedButton(
                        selected = period == p,
                        onClick = { period = p },
                        shape = SegmentedButtonDefaults.itemShape(idx, periods.size)
                    ) { Text(label) }
                }
            }

            Text("Scope", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = categoryId == null,
                    onClick = { categoryId = null },
                    label = { Text("All Categories") }
                )
                expenseCategories.forEach { cat ->
                    FilterChip(
                        selected = categoryId == cat.id,
                        onClick = {
                            categoryId = cat.id
                            if (name.isBlank()) name = "${cat.name} Budget"
                        },
                        label = { Text(cat.name) }
                    )
                }
            }

            val parsedLimit = limitText.toLongOrNull() ?: 0L
            Button(
                onClick = {
                    onSave(editing?.id ?: 0L, name, parsedLimit, period, categoryId)
                },
                enabled = name.isNotBlank() && parsedLimit > 0L,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("budget_save_button")
            ) {
                Text(if (editing != null) "Update Budget" else "Save Budget")
            }
        }
    }
}
