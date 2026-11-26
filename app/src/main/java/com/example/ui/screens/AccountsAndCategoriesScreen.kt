package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.local.AccountEntity
import com.example.data.local.CategoryEntity
import com.example.data.model.AccountType
import com.example.data.model.CategoryType
import com.example.ui.components.CategoryIconMapper
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalDailySpendColors
import com.example.ui.viewmodel.DailySpendUiState
import com.example.utils.CurrencyFormatter

private val paletteColors = listOf(
    0xFF14213DL,
    0xFF16A34AL,
    0xFF2563EBL,
    0xFFEA580CL,
    0xFFDB2777L,
    0xFF7C3AEDL,
    0xFF0D9488L,
    0xFFD97706L,
    0xFFDC2626L
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsAndCategoriesScreen(
    state: DailySpendUiState,
    onBack: () -> Unit,
    onSaveAccount: (id: Long, name: String, type: AccountType, initialBalance: Long, iconKey: String, colorHex: Long) -> Unit,
    onDeleteAccount: (AccountEntity) -> Unit,
    onSaveCategory: (id: Long, name: String, type: CategoryType, iconKey: String, colorHex: Long) -> Unit,
    onDeleteCategory: (CategoryEntity) -> Unit
) {
    val semantic = LocalDailySpendColors.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var editingAccount by remember { mutableStateOf<AccountEntity?>(null) }
    var showAccountDialog by remember { mutableStateOf(false) }
    var deletingAccount by remember { mutableStateOf<AccountEntity?>(null) }

    var editingCategory by remember { mutableStateOf<CategoryEntity?>(null) }
    var showCategoryDialog by remember { mutableStateOf(false) }
    var deletingCategory by remember { mutableStateOf<CategoryEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Accounts & Categories") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (selectedTab == 0) {
                                editingAccount = null
                                showAccountDialog = true
                            } else {
                                editingCategory = null
                                showCategoryDialog = true
                            }
                        },
                        modifier = Modifier.testTag("add_account_or_category")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "Add")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Accounts (${state.accountsWithBalance.size})") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Categories (${state.categories.size})") }
                )
            }

            if (selectedTab == 0) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = "Balances are calculated locally from your initial balance and recorded transactions, not direct bank API sync.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }

                    items(
                        items = state.accountsWithBalance,
                        key = { it.account.id }
                    ) { accBal ->
                        val acc = accBal.account
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 1.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(acc.colorHex).copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = CategoryIconMapper.iconForKey(acc.iconKey),
                                        contentDescription = acc.name,
                                        tint = Color(acc.colorHex)
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(acc.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = "${acc.type.name} • Initial: ${CurrencyFormatter.format(acc.initialBalance, state.settings.currencyCode, state.isBalanceHidden)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = semantic.secondaryText
                                    )
                                }
                                Text(
                                    text = CurrencyFormatter.format(accBal.currentBalance, state.settings.currencyCode, state.isBalanceHidden),
                                    style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFontFamily, fontWeight = FontWeight.Bold)
                                )
                                IconButton(onClick = {
                                    editingAccount = acc
                                    showAccountDialog = true
                                }) {
                                    Icon(Icons.Rounded.Edit, contentDescription = "Edit account", modifier = Modifier.size(18.dp))
                                }
                                if (state.accountsWithBalance.size > 1) {
                                    IconButton(onClick = { deletingAccount = acc }) {
                                        Icon(Icons.Rounded.Delete, contentDescription = "Delete account", tint = semantic.expense, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        items = state.categories,
                        key = { it.id }
                    ) { cat ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 1.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(cat.colorHex).copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = CategoryIconMapper.iconForKey(cat.iconKey),
                                        contentDescription = cat.name,
                                        tint = Color(cat.colorHex)
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(cat.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    Text(cat.type.name, style = MaterialTheme.typography.labelSmall, color = semantic.secondaryText)
                                }
                                IconButton(onClick = {
                                    editingCategory = cat
                                    showCategoryDialog = true
                                }) {
                                    Icon(Icons.Rounded.Edit, contentDescription = "Edit category", modifier = Modifier.size(18.dp))
                                }
                                IconButton(onClick = { deletingCategory = cat }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "Delete category", tint = semantic.expense, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAccountDialog) {
        var name by remember(editingAccount) { mutableStateOf(editingAccount?.name ?: "") }
        var accType by remember(editingAccount) { mutableStateOf(editingAccount?.type ?: AccountType.CUSTOM) }
        var initBal by remember(editingAccount) { mutableStateOf(editingAccount?.initialBalance?.toString() ?: "0") }
        var iconKey by remember(editingAccount) { mutableStateOf(editingAccount?.iconKey ?: "wallet") }
        var colorHex by remember(editingAccount) { mutableLongStateOf(editingAccount?.colorHex ?: paletteColors.first()) }

        AlertDialog(
            onDismissRequest = { showAccountDialog = false },
            title = { Text(if (editingAccount != null) "Edit Account" else "New Account") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Account Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = initBal,
                        onValueChange = { initBal = it.filter { c -> c.isDigit() || c == '-' }.take(13) },
                        label = { Text("Initial Balance (${state.settings.currencyCode})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AccountType.entries.forEach { t ->
                            FilterChip(
                                selected = accType == t,
                                onClick = { accType = t },
                                label = { Text(t.name) }
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        paletteColors.take(7).forEach { c ->
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(c))
                                    .border(
                                        width = if (colorHex == c) 3.dp else 0.dp,
                                        color = if (colorHex == c) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { colorHex = c }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveAccount(
                            editingAccount?.id ?: 0L,
                            name,
                            accType,
                            initBal.toLongOrNull() ?: 0L,
                            iconKey,
                            colorHex
                        )
                        showAccountDialog = false
                    },
                    enabled = name.isNotBlank()
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showAccountDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showCategoryDialog) {
        var name by remember(editingCategory) { mutableStateOf(editingCategory?.name ?: "") }
        var catType by remember(editingCategory) { mutableStateOf(editingCategory?.type ?: CategoryType.EXPENSE) }
        var iconKey by remember(editingCategory) { mutableStateOf(editingCategory?.iconKey ?: "shopping_bag") }
        var colorHex by remember(editingCategory) { mutableLongStateOf(editingCategory?.colorHex ?: paletteColors[1]) }

        AlertDialog(
            onDismissRequest = { showCategoryDialog = false },
            title = { Text(if (editingCategory != null) "Edit Category" else "New Category") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Category Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        val types = listOf(CategoryType.EXPENSE to "Expense", CategoryType.INCOME to "Income")
                        types.forEachIndexed { idx, (t, label) ->
                            SegmentedButton(
                                selected = catType == t,
                                onClick = { catType = t },
                                shape = SegmentedButtonDefaults.itemShape(idx, types.size)
                            ) { Text(label) }
                        }
                    }
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CategoryIconMapper.availableIcons.take(12).forEach { (key, vec) ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (iconKey == key) Color(colorHex).copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { iconKey = key }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(vec, contentDescription = key, tint = if (iconKey == key) Color(colorHex) else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        paletteColors.take(7).forEach { c ->
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(c))
                                    .border(
                                        width = if (colorHex == c) 3.dp else 0.dp,
                                        color = if (colorHex == c) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { colorHex = c }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveCategory(
                            editingCategory?.id ?: 0L,
                            name,
                            catType,
                            iconKey,
                            colorHex
                        )
                        showCategoryDialog = false
                    },
                    enabled = name.isNotBlank()
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showCategoryDialog = false }) { Text("Cancel") }
            }
        )
    }

    deletingAccount?.let { acc ->
        AlertDialog(
            onDismissRequest = { deletingAccount = null },
            title = { Text("Delete Account?") },
            text = { Text("Deleting \"${acc.name}\" will also remove transactions linked to it.") },
            confirmButton = {
                Button(onClick = {
                    onDeleteAccount(acc)
                    deletingAccount = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deletingAccount = null }) { Text("Cancel") }
            }
        )
    }

    deletingCategory?.let { cat ->
        AlertDialog(
            onDismissRequest = { deletingCategory = null },
            title = { Text("Delete Category?") },
            text = { Text("Delete category \"${cat.name}\"? Existing transactions will remain uncategorized.") },
            confirmButton = {
                Button(onClick = {
                    onDeleteCategory(cat)
                    deletingCategory = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deletingCategory = null }) { Text("Cancel") }
            }
        )
    }
}
