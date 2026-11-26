package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.local.AccountEntity
import com.example.data.local.CategoryEntity
import com.example.data.local.TransactionEntity
import com.example.data.model.CategoryType
import com.example.data.model.SourceType
import com.example.data.model.TransactionType
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalDailySpendColors
import com.example.utils.DateUtils

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TransactionEntryBottomSheet(
    initialType: TransactionType,
    editingTransaction: TransactionEntity?,
    accounts: List<AccountEntity>,
    categories: List<CategoryEntity>,
    currencyCode: String,
    onDismiss: () -> Unit,
    onSave: (
        existingId: Long,
        type: TransactionType,
        amount: Long,
        categoryId: Long?,
        accountId: Long,
        destinationAccountId: Long?,
        dateStr: String,
        timeStr: String,
        notes: String,
        receiptUri: String?,
        sourceType: SourceType,
        sourceApp: String
    ) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptics = LocalHapticFeedback.current
    val semantic = LocalDailySpendColors.current

    val now = remember { System.currentTimeMillis() }
    var txType by remember(editingTransaction, initialType) {
        mutableStateOf(editingTransaction?.type ?: initialType)
    }
    var amountText by remember(editingTransaction) {
        mutableStateOf(editingTransaction?.amount?.toString() ?: "")
    }
    var selectedAccountId by remember(editingTransaction, accounts) {
        mutableLongStateOf(editingTransaction?.accountId ?: accounts.firstOrNull()?.id ?: 1L)
    }
    var destAccountId by remember(editingTransaction, accounts) {
        val fallback: Long? = accounts.firstOrNull { it.id != selectedAccountId }?.id
        mutableStateOf<Long?>(editingTransaction?.destinationAccountId ?: fallback)
    }

    val filteredCategories = remember(txType, categories) {
        when (txType) {
            TransactionType.INCOME -> categories.filter { it.type == CategoryType.INCOME }
            TransactionType.EXPENSE -> categories.filter { it.type == CategoryType.EXPENSE }
            TransactionType.TRANSFER -> emptyList()
        }
    }

    var selectedCategoryId by remember(editingTransaction, txType, filteredCategories) {
        val existingCat = editingTransaction?.categoryId
        val validExisting = filteredCategories.firstOrNull { it.id == existingCat }?.id
        mutableStateOf(validExisting ?: filteredCategories.firstOrNull()?.id)
    }

    var dateStr by remember(editingTransaction) {
        mutableStateOf(editingTransaction?.transactionDate ?: DateUtils.formatDate(now))
    }
    var timeStr by remember(editingTransaction) {
        mutableStateOf(editingTransaction?.transactionTime ?: DateUtils.formatTime(now))
    }
    var notes by remember(editingTransaction) {
        mutableStateOf(editingTransaction?.notes ?: "")
    }
    var receiptUri by remember(editingTransaction) {
        mutableStateOf(editingTransaction?.receiptImageUri)
    }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            receiptUri = uri.toString()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (editingTransaction != null) "Edit Transaction" else "New Transaction",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close")
                }
            }

            // Transaction Type Selector
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val options = listOf(
                    TransactionType.EXPENSE to "Expense",
                    TransactionType.INCOME to "Income",
                    TransactionType.TRANSFER to "Transfer"
                )
                options.forEachIndexed { idx, (type, label) ->
                    SegmentedButton(
                        selected = txType == type,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            txType = type
                            if (type != TransactionType.TRANSFER) {
                                val matching = categories.filter {
                                    it.type == if (type == TransactionType.INCOME) CategoryType.INCOME else CategoryType.EXPENSE
                                }
                                if (matching.none { it.id == selectedCategoryId }) {
                                    selectedCategoryId = matching.firstOrNull()?.id
                                }
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = idx, count = options.size),
                        modifier = Modifier.testTag("tx_type_${type.name.lowercase()}")
                    ) {
                        Text(label, maxLines = 1)
                    }
                }
            }

            // Nominal Input
            OutlinedTextField(
                value = amountText,
                onValueChange = { input ->
                    val digitsOnly = input.filter { it.isDigit() }.take(12)
                    amountText = digitsOnly
                },
                label = { Text("Amount ($currencyCode)") },
                prefix = {
                    Text(
                        text = if (currencyCode == "IDR") "Rp " else "$currencyCode ",
                        fontWeight = FontWeight.Bold
                    )
                },
                textStyle = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tx_amount_input")
            )

            // Quick amount chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val presets = listOf(
                    25_000L to "25rb",
                    50_000L to "50rb",
                    100_000L to "100rb",
                    250_000L to "250rb",
                    500_000L to "500rb"
                )
                presets.forEach { (valAmount, label) ->
                    FilterChip(
                        selected = amountText == valAmount.toString(),
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            amountText = valAmount.toString()
                        },
                        label = { Text(label) }
                    )
                }
            }

            // Account Selector
            Text(
                text = if (txType == TransactionType.TRANSFER) "From Account" else "Account",
                style = MaterialTheme.typography.labelLarge
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                accounts.forEach { acc ->
                    FilterChip(
                        selected = selectedAccountId == acc.id,
                        onClick = {
                            selectedAccountId = acc.id
                            if (destAccountId == acc.id) {
                                destAccountId = accounts.firstOrNull { it.id != acc.id }?.id
                            }
                        },
                        label = { Text(acc.name) },
                        leadingIcon = {
                            Icon(
                                imageVector = CategoryIconMapper.iconForKey(acc.iconKey),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
            }

            // Destination Account for Transfers
            if (txType == TransactionType.TRANSFER) {
                Text(
                    text = "To Account",
                    style = MaterialTheme.typography.labelLarge
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    accounts.filter { it.id != selectedAccountId }.forEach { acc ->
                        FilterChip(
                            selected = destAccountId == acc.id,
                            onClick = { destAccountId = acc.id },
                            label = { Text(acc.name) },
                            leadingIcon = {
                                Icon(
                                    imageVector = CategoryIconMapper.iconForKey(acc.iconKey),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                }
            } else {
                // Category Selector
                Text(
                    text = "Category",
                    style = MaterialTheme.typography.labelLarge
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    filteredCategories.forEach { cat ->
                        val isSelected = selectedCategoryId == cat.id
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedCategoryId = cat.id },
                            label = { Text(cat.name) },
                            leadingIcon = {
                                Icon(
                                    imageVector = CategoryIconMapper.iconForKey(cat.iconKey),
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else Color(cat.colorHex),
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(cat.colorHex).copy(alpha = 0.18f)
                            )
                        )
                    }
                }
            }

            // Date & Time row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(dateStr)
                }
                OutlinedButton(
                    onClick = { showTimePicker = true },
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Rounded.AccessTime, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(timeStr)
                }
            }

            // Notes
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes (optional)") },
                singleLine = false,
                maxLines = 2,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tx_notes_input")
            )

            // Optional Receipt Photo via Android Photo Picker
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        photoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Rounded.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (receiptUri == null) "Attach Receipt" else "Change Receipt")
                }
                if (receiptUri != null) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                    ) {
                        AsyncImage(
                            model = receiptUri,
                            contentDescription = "Receipt preview",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.matchParentSize()
                        )
                    }
                    TextButton(onClick = { receiptUri = null }) {
                        Text("Remove")
                    }
                }
            }

            val parsedAmount = amountText.toLongOrNull() ?: 0L
            val canSave = parsedAmount > 0L &&
                (txType != TransactionType.TRANSFER || (destAccountId != null && destAccountId != selectedAccountId))

            Button(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSave(
                        editingTransaction?.id ?: 0L,
                        txType,
                        parsedAmount,
                        selectedCategoryId,
                        selectedAccountId,
                        destAccountId,
                        dateStr,
                        timeStr,
                        notes,
                        receiptUri,
                        editingTransaction?.sourceType ?: SourceType.MANUAL,
                        editingTransaction?.sourceApplication ?: "Manual"
                    )
                    onDismiss()
                },
                enabled = canSave,
                colors = ButtonDefaults.buttonColors(
                    containerColor = when (txType) {
                        TransactionType.INCOME -> semantic.income
                        TransactionType.EXPENSE -> MaterialTheme.colorScheme.primary
                        TransactionType.TRANSFER -> semantic.transfer
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("tx_save_button")
            ) {
                Text(
                    text = if (editingTransaction != null) "Update Transaction" else "Save Transaction",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = DateUtils.parseDateTimeToMillis(dateStr, timeStr)
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { ms ->
                        dateStr = DateUtils.formatDate(ms)
                    }
                    showDatePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (showTimePicker) {
        val parts = timeStr.split(":")
        val initHour = parts.getOrNull(0)?.toIntOrNull() ?: 12
        val initMin = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val timeState = rememberTimePickerState(initialHour = initHour, initialMinute = initMin, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    timeStr = "%02d:%02d".format(timeState.hour, timeState.minute)
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            },
            text = { TimePicker(state = timeState) }
        )
    }
}
