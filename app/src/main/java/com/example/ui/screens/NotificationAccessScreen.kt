package com.example.ui.screens

import android.content.Intent
import android.provider.Settings
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.NotificationSourceEntity
import com.example.notification.ParseConfidence
import com.example.notification.ParseOutcome
import com.example.ui.theme.LocalDailySpendColors
import com.example.ui.viewmodel.DailySpendUiState
import com.example.utils.CurrencyFormatter
import com.example.utils.DateUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationAccessScreen(
    state: DailySpendUiState,
    notificationAccessEnabled: Boolean,
    lastParserTestResult: ParseOutcome?,
    onBack: () -> Unit,
    onToggleAutoConfirm: (Boolean) -> Unit,
    onToggleSource: (NotificationSourceEntity, Boolean) -> Unit,
    onAssignAccount: (NotificationSourceEntity, Long?) -> Unit,
    onTestParser: (packageName: String, title: String, body: String) -> Unit,
    onClearLogs: () -> Unit
) {
    val context = LocalContext.current
    val semantic = LocalDailySpendColors.current
    val accountMap = remember(state.accountsWithBalance) {
        state.accountsWithBalance.associate { it.account.id to it.account.name }
    }

    var testPackage by remember { mutableStateOf("com.bca") }
    var testTitle by remember { mutableStateOf("BCA Mobile") }
    var testBody by remember { mutableStateOf("m-Transfer ke Budi berhasil sebesar Rp 125.000") }
    var testAppDropdown by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notification Detection") },
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
            // Permission Status Card
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (notificationAccessEnabled) semantic.incomeContainer else semantic.warningContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (notificationAccessEnabled) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber,
                                contentDescription = null,
                                tint = if (notificationAccessEnabled) semantic.income else semantic.warning
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (notificationAccessEnabled) "Notification Listener Active" else "Notification Access Required",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (notificationAccessEnabled) {
                                        "DailySpend is listening locally for enabled banking & e-wallet notifications."
                                    } else {
                                        "Grant Android Notification Access to detect transactions automatically."
                                    },
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("open_notification_listener_settings")
                        ) {
                            Icon(Icons.Rounded.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (notificationAccessEnabled) "Manage Android Notification Access" else "Open Notification Access Settings")
                        }
                    }
                }
            }

            // Local Security & Processing Mode
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("On-Device Privacy Guard", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            text = "Notifications are parsed strictly on your device. OTPs, PINs, passwords, and raw notification text are never stored or transmitted.",
                            style = MaterialTheme.typography.bodySmall,
                            color = semantic.secondaryText
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Auto-Confirm High Confidence", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "Uncertain detections always go to Pending Review",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = semantic.secondaryText
                                )
                            }
                            Switch(
                                checked = state.settings.autoConfirmHighConfidence,
                                onCheckedChange = onToggleAutoConfirm
                            )
                        }
                    }
                }
            }

            // Supported Applications
            item {
                Text(
                    text = "Supported Banking & E-Wallet Apps",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            items(
                items = state.notificationSources,
                key = { it.packageName }
            ) { source ->
                var accMenuOpen by remember { mutableStateOf(false) }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = source.appName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = source.supportedFormatsSummary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = semantic.secondaryText
                                )
                            }
                            Switch(
                                checked = source.isEnabled,
                                onCheckedChange = { onToggleSource(source, it) }
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Box {
                            val assignedName = source.defaultAccountId?.let { accountMap[it] } ?: "Default Account"
                            TextButton(onClick = { accMenuOpen = true }) {
                                Text("Target Account: $assignedName")
                            }
                            DropdownMenu(
                                expanded = accMenuOpen,
                                onDismissRequest = { accMenuOpen = false }
                            ) {
                                state.accountsWithBalance.forEach { accBal ->
                                    DropdownMenuItem(
                                        text = { Text(accBal.account.name) },
                                        onClick = {
                                            onAssignAccount(source, accBal.account.id)
                                            accMenuOpen = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Parser Format Verifier (Safe Diagnostic Tool)
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Parser Format Verifier",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Test how the modular engine parses a notification format without modifying your real database.",
                            style = MaterialTheme.typography.bodySmall,
                            color = semantic.secondaryText
                        )

                        Box {
                            OutlinedButton(onClick = { testAppDropdown = true }) {
                                val label = state.notificationSources.firstOrNull { it.packageName == testPackage }?.appName ?: testPackage
                                Text("Source App: $label")
                            }
                            DropdownMenu(
                                expanded = testAppDropdown,
                                onDismissRequest = { testAppDropdown = false }
                            ) {
                                state.notificationSources.forEach { src ->
                                    DropdownMenuItem(
                                        text = { Text("${src.appName} (${src.packageName})") },
                                        onClick = {
                                            testPackage = src.packageName
                                            testTitle = src.appName
                                            testAppDropdown = false
                                        }
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = testBody,
                            onValueChange = { testBody = it },
                            label = { Text("Sample Notification Text") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Button(
                            onClick = { onTestParser(testPackage, testTitle, testBody) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Verify Parser Output")
                        }

                        lastParserTestResult?.let { res ->
                            val resultText = when (res) {
                                ParseOutcome.IgnoredSensitive -> "Blocked: Sensitive OTP/PIN/Password keyword detected."
                                ParseOutcome.IgnoredNonTransaction -> "Ignored: Not recognized as a financial transaction."
                                is ParseOutcome.Parsed -> buildString {
                                    append("Detected ${res.type.name}: ${CurrencyFormatter.format(res.amount, state.settings.currencyCode)}\n")
                                    append("Source: ${res.sourceApplication} • Category: ${res.inferredCategoryName}\n")
                                    append("Confidence: ${if (res.confidence == ParseConfidence.HIGH) "High (Auto-confirm)" else "Low (Pending Review)"}")
                                }
                            }
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = resultText,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Processed Notification History Logs (Sanitized)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Processed Notification History",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (state.notificationLogs.isNotEmpty()) {
                        TextButton(onClick = onClearLogs) {
                            Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Clear")
                        }
                    }
                }
            }

            if (state.notificationLogs.isEmpty()) {
                item {
                    Text(
                        text = "No processed notifications recorded yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = semantic.secondaryText
                    )
                }
            } else {
                items(
                    items = state.notificationLogs,
                    key = { it.id }
                ) { log ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(log.appName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = "${DateUtils.formatDate(log.createdAt)} ${DateUtils.formatTime(log.createdAt)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = semantic.secondaryText
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            val amountDisplay = if (log.parsedAmount != null) {
                                if (state.settings.privacyMaskLogs) "Rp ••••••"
                                else CurrencyFormatter.format(log.parsedAmount, state.settings.currencyCode)
                            } else ""
                            Text(
                                text = listOf(log.sanitizedSummary, amountDisplay).filter { it.isNotBlank() }.joinToString(" • "),
                                style = MaterialTheme.typography.bodySmall,
                                color = semantic.secondaryText
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
