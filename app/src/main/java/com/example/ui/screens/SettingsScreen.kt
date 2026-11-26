package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.PendingActions
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.model.ExportPeriod
import com.example.data.model.ThemeMode
import com.example.notification.NotificationHelper
import com.example.ui.theme.LocalDailySpendColors
import com.example.ui.viewmodel.DailySpendUiState
import com.example.utils.DateUtils
import com.example.utils.SecurityUtils

@Composable
fun SettingsScreen(
    state: DailySpendUiState,
    notificationAccessEnabled: Boolean,
    onUpdateProfileName: (String) -> Unit,
    onUpdateCurrency: (String) -> Unit,
    onUpdateTheme: (ThemeMode) -> Unit,
    onSetHideBalanceOnStart: (Boolean) -> Unit,
    onSetBudgetNotifications: (Boolean) -> Unit,
    onSetDailySummary: (Boolean) -> Unit,
    onSetPrivacyMaskLogs: (Boolean) -> Unit,
    onEnablePin: (String) -> Unit,
    onDisablePin: () -> Unit,
    onSetBiometric: (Boolean) -> Unit,
    onExportCsv: (android.net.Uri, ExportPeriod) -> Unit,
    onExportPdf: (android.net.Uri, ExportPeriod) -> Unit,
    onBackupJson: (android.net.Uri) -> Unit,
    onRestoreJson: (android.net.Uri) -> Unit,
    onClearAllData: () -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onOpenAccountsAndCategories: () -> Unit,
    onOpenReviewQueue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val semantic = LocalDailySpendColors.current
    val biometricSupported = remember { SecurityUtils.canUseBiometric(context) }

    var showProfileDialog by remember { mutableStateOf(false) }
    var showCurrencyDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }
    var showClearDataDialog by remember { mutableStateOf(false) }
    var pendingExportFormat by remember { mutableStateOf<String?>(null) } // "CSV" or "PDF"
    var selectedExportPeriod by remember { mutableStateOf(ExportPeriod.THIS_MONTH) }
    var pendingRestoreUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onSetBudgetNotifications(true)
        }
    }

    val createCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) onExportCsv(uri, selectedExportPeriod)
    }

    val createPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) onExportPdf(uri, selectedExportPeriod)
    }

    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) onBackupJson(uri)
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) pendingRestoreUri = uri
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        // Core Management
        item {
            SettingsGroupCard(title = "Accounts & Detection") {
                SettingsNavItem(
                    icon = Icons.Rounded.NotificationsActive,
                    title = "Notification Access & Supported Apps",
                    subtitle = if (notificationAccessEnabled) "Active • Local listener enabled" else "Disabled • Tap to enable auto-detection",
                    accent = if (notificationAccessEnabled) semantic.income else semantic.warning,
                    onClick = onOpenNotificationAccess,
                    testTag = "settings_notification_access"
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingsNavItem(
                    icon = Icons.Rounded.PendingActions,
                    title = "Transaction Review Queue",
                    subtitle = "${state.pendingReviewTransactions.size} pending transaction(s)",
                    onClick = onOpenReviewQueue,
                    testTag = "settings_review_queue"
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingsNavItem(
                    icon = Icons.Rounded.AccountBalanceWallet,
                    title = "Accounts & Categories",
                    subtitle = "${state.accountsWithBalance.size} accounts • ${state.categories.size} categories",
                    onClick = onOpenAccountsAndCategories,
                    testTag = "settings_accounts_categories"
                )
            }
        }

        // Personalization
        item {
            SettingsGroupCard(title = "General & Appearance") {
                SettingsNavItem(
                    icon = Icons.Rounded.Person,
                    title = "Profile Name",
                    subtitle = state.settings.profileName,
                    onClick = { showProfileDialog = true }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingsNavItem(
                    icon = Icons.Rounded.Category,
                    title = "Display Currency",
                    subtitle = state.settings.currencyCode,
                    onClick = { showCurrencyDialog = true }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingsNavItem(
                    icon = Icons.Rounded.Brightness6,
                    title = "Theme",
                    subtitle = state.settings.themeMode.name.lowercase().replaceFirstChar { it.uppercase() },
                    onClick = { showThemeDialog = true },
                    testTag = "settings_theme_button"
                )
            }
        }

        // Notifications
        item {
            SettingsGroupCard(title = "Alerts & Privacy") {
                SettingsToggleItem(
                    icon = Icons.Rounded.NotificationsNone,
                    title = "Budget Threshold Alerts",
                    subtitle = "Notify at 50%, 80%, and 100% usage",
                    checked = state.settings.budgetNotificationsEnabled,
                    onCheckedChange = { checked ->
                        if (checked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            !NotificationHelper.hasPostNotificationPermission(context)
                        ) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            onSetBudgetNotifications(checked)
                        }
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingsToggleItem(
                    icon = Icons.Rounded.NotificationsActive,
                    title = "Daily Summary",
                    subtitle = "Send a local summary notification for today's cash flow",
                    checked = state.settings.dailySummaryEnabled,
                    onCheckedChange = { checked ->
                        if (checked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            !NotificationHelper.hasPostNotificationPermission(context)
                        ) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            onSetDailySummary(checked)
                        }
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingsToggleItem(
                    icon = Icons.Rounded.VisibilityOff,
                    title = "Hide Balance on Launch",
                    subtitle = "Mask total balance until you tap eye icon",
                    checked = state.settings.hideBalanceOnStart,
                    onCheckedChange = onSetHideBalanceOnStart
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingsToggleItem(
                    icon = Icons.Rounded.Security,
                    title = "Privacy Mask Detection Logs",
                    subtitle = "Hide parsed amounts in notification detection history",
                    checked = state.settings.privacyMaskLogs,
                    onCheckedChange = onSetPrivacyMaskLogs
                )
            }
        }

        // Security
        item {
            SettingsGroupCard(title = "App Lock Security") {
                SettingsNavItem(
                    icon = Icons.Rounded.Lock,
                    title = if (state.settings.pinEnabled) "PIN Lock Enabled" else "Set Up App PIN",
                    subtitle = if (state.settings.pinEnabled) "Tap to change or disable PIN" else "Protect financial data with a 4–6 digit PIN",
                    onClick = { showPinDialog = true },
                    testTag = "settings_pin_setup"
                )
                if (state.settings.pinEnabled && biometricSupported) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsToggleItem(
                        icon = Icons.Rounded.Fingerprint,
                        title = "Biometric Unlock",
                        subtitle = "Use fingerprint or face unlock",
                        checked = state.settings.biometricEnabled,
                        onCheckedChange = onSetBiometric
                    )
                }
            }
        }

        // Export & Backup
        item {
            SettingsGroupCard(title = "Export, Backup & Data") {
                SettingsNavItem(
                    icon = Icons.Rounded.TableChart,
                    title = "Export CSV",
                    subtitle = "Save transactions spreadsheet for selected period",
                    onClick = { pendingExportFormat = "CSV" },
                    testTag = "settings_export_csv"
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingsNavItem(
                    icon = Icons.Rounded.PictureAsPdf,
                    title = "Export PDF Report",
                    subtitle = "Generate printable financial summary PDF",
                    onClick = { pendingExportFormat = "PDF" },
                    testTag = "settings_export_pdf"
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingsNavItem(
                    icon = Icons.Rounded.Backup,
                    title = "Backup Database",
                    subtitle = "Save encrypted-ready JSON snapshot of all local tables",
                    onClick = {
                        val stamp = DateUtils.formatDate(System.currentTimeMillis())
                        backupLauncher.launch("dailyspend_backup_$stamp.json")
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingsNavItem(
                    icon = Icons.Rounded.Restore,
                    title = "Restore Database",
                    subtitle = "Import and validate a previous DailySpend JSON backup",
                    onClick = {
                        restoreLauncher.launch(arrayOf("application/json", "*/*"))
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingsNavItem(
                    icon = Icons.Rounded.DeleteForever,
                    title = "Clear All Data",
                    subtitle = "Delete all transactions, budgets, and detection logs",
                    accent = semantic.expense,
                    onClick = { showClearDataDialog = true }
                )
            }
        }

        item { Spacer(Modifier.height(76.dp)) }
    }

    if (showProfileDialog) {
        var nameInput by remember { mutableStateOf(state.settings.profileName) }
        AlertDialog(
            onDismissRequest = { showProfileDialog = false },
            title = { Text("Edit Profile Name") },
            text = {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Display Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    onUpdateProfileName(nameInput)
                    showProfileDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showProfileDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showCurrencyDialog) {
        val currencies = listOf("IDR" to "Indonesian Rupiah (Rp)", "USD" to "US Dollar ($)", "SGD" to "Singapore Dollar (S$)", "MYR" to "Malaysian Ringgit (RM)")
        AlertDialog(
            onDismissRequest = { showCurrencyDialog = false },
            title = { Text("Select Currency") },
            text = {
                Column {
                    currencies.forEach { (code, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onUpdateCurrency(code)
                                    showCurrencyDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = state.settings.currencyCode == code,
                                onClick = {
                                    onUpdateCurrency(code)
                                    showCurrencyDialog = false
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCurrencyDialog = false }) { Text("Close") }
            }
        )
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("App Theme") },
            text = {
                Column {
                    ThemeMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onUpdateTheme(mode)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = state.settings.themeMode == mode,
                                onClick = {
                                    onUpdateTheme(mode)
                                    showThemeDialog = false
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(mode.name.lowercase().replaceFirstChar { it.uppercase() })
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) { Text("Close") }
            }
        )
    }

    if (showPinDialog) {
        var newPin by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text(if (state.settings.pinEnabled) "Manage App PIN" else "Set App PIN") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Enter a 4 to 6 digit numeric PIN. It is stored locally using salted SHA-256.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = newPin,
                        onValueChange = { newPin = it.filter { c -> c.isDigit() }.take(6) },
                        label = { Text("New PIN (4-6 digits)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onEnablePin(newPin)
                        showPinDialog = false
                    },
                    enabled = newPin.length in 4..6
                ) { Text("Save PIN") }
            },
            dismissButton = {
                Row {
                    if (state.settings.pinEnabled) {
                        TextButton(onClick = {
                            onDisablePin()
                            showPinDialog = false
                        }) { Text("Disable PIN", color = semantic.expense) }
                    }
                    TextButton(onClick = { showPinDialog = false }) { Text("Cancel") }
                }
            }
        )
    }

    pendingExportFormat?.let { format ->
        AlertDialog(
            onDismissRequest = { pendingExportFormat = null },
            title = { Text("Select $format Report Period") },
            text = {
                Column {
                    ExportPeriod.entries.forEach { period ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedExportPeriod = period }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedExportPeriod == period,
                                onClick = { selectedExportPeriod = period }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(period.label)
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val stamp = DateUtils.formatDate(System.currentTimeMillis())
                    if (format == "CSV") {
                        createCsvLauncher.launch("dailyspend_${stamp}.csv")
                    } else {
                        createPdfLauncher.launch("dailyspend_report_${stamp}.pdf")
                    }
                    pendingExportFormat = null
                }) {
                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Export $format")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingExportFormat = null }) { Text("Cancel") }
            }
        )
    }

    pendingRestoreUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestoreUri = null },
            title = { Text("Restore Backup?") },
            text = {
                Text("Restoring this backup will replace your current accounts, categories, transactions, and budgets. Continue?")
            },
            confirmButton = {
                Button(onClick = {
                    onRestoreJson(uri)
                    pendingRestoreUri = null
                }) { Text("Restore") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestoreUri = null }) { Text("Cancel") }
            }
        )
    }

    if (showClearDataDialog) {
        AlertDialog(
            onDismissRequest = { showClearDataDialog = false },
            title = { Text("Clear All Data?") },
            text = {
                Text("This permanently deletes all recorded transactions, budgets, and detection logs from your device.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onClearAllData()
                        showClearDataDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = semantic.expense)
                ) { Text("Clear Everything") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SettingsGroupCard(
    title: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = LocalDailySpendColors.current.secondaryText,
            modifier = Modifier.padding(start = 4.dp)
        )
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                content()
            }
        }
    }
}

@Composable
private fun SettingsNavItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit,
    testTag: String = ""
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .let { if (testTag.isNotEmpty()) it.testTag(testTag) else it },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = LocalDailySpendColors.current.secondaryText)
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = LocalDailySpendColors.current.secondaryText
        )
    }
}

@Composable
private fun SettingsToggleItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = LocalDailySpendColors.current.secondaryText)
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
