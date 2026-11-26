package com.example.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Attachment
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.AccountEntity
import com.example.data.local.CategoryEntity
import com.example.data.local.TransactionEntity
import com.example.data.model.SourceType
import com.example.data.model.TransactionStatus
import com.example.data.model.TransactionType
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalDailySpendColors
import com.example.utils.CurrencyFormatter
import kotlin.math.roundToLong

@Composable
fun AnimatedCurrencyText(
    targetAmount: Long,
    currencyCode: String,
    masked: Boolean = false,
    style: TextStyle = MaterialTheme.typography.displaySmall,
    color: Color = Color.Unspecified,
    modifier: Modifier = Modifier
) {
    val animatedFloat by animateFloatAsState(
        targetValue = targetAmount.toFloat(),
        animationSpec = tween(durationMillis = 550),
        label = "currencyCounter"
    )
    val displayAmount = if (masked) targetAmount else animatedFloat.roundToLong()
    Text(
        text = CurrencyFormatter.format(displayAmount, currencyCode, masked),
        style = style.copy(fontFamily = JetBrainsMonoFontFamily),
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableTransactionRow(
    transaction: TransactionEntity,
    category: CategoryEntity?,
    account: AccountEntity?,
    destinationAccount: AccountEntity?,
    currencyCode: String,
    isMasked: Boolean = false,
    onClick: () -> Unit,
    onRequestDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val semantic = LocalDailySpendColors.current
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onRequestDelete()
                false // Keep item until confirmation dialog succeeds
            } else if (value == SwipeToDismissBoxValue.StartToEnd) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
                false
            } else false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val bgColor = when (direction) {
                SwipeToDismissBoxValue.EndToStart -> semantic.expenseContainer
                SwipeToDismissBoxValue.StartToEnd -> semantic.transferContainer
                else -> Color.Transparent
            }
            val align = when (direction) {
                SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                else -> Alignment.CenterStart
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(bgColor)
                    .padding(horizontal = 20.dp),
                contentAlignment = align
            ) {
                if (direction == SwipeToDismissBoxValue.EndToStart) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = "Delete transaction",
                        tint = semantic.expense
                    )
                } else if (direction == SwipeToDismissBoxValue.StartToEnd) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = "Edit transaction",
                        tint = semantic.transfer
                    )
                }
            }
        },
        content = {
            TransactionRowCard(
                transaction = transaction,
                category = category,
                account = account,
                destinationAccount = destinationAccount,
                currencyCode = currencyCode,
                isMasked = isMasked,
                onClick = onClick
            )
        }
    )
}

@Composable
fun TransactionRowCard(
    transaction: TransactionEntity,
    category: CategoryEntity?,
    account: AccountEntity?,
    destinationAccount: AccountEntity?,
    currencyCode: String,
    isMasked: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = LocalDailySpendColors.current
    val iconTint = when (transaction.type) {
        TransactionType.INCOME -> semantic.income
        TransactionType.EXPENSE -> Color(category?.colorHex ?: 0xFFEF4444)
        TransactionType.TRANSFER -> semantic.transfer
    }
    val iconContainer = when (transaction.type) {
        TransactionType.INCOME -> semantic.incomeContainer
        TransactionType.EXPENSE -> iconTint.copy(alpha = 0.14f)
        TransactionType.TRANSFER -> semantic.transferContainer
    }
    val iconVector = when (transaction.type) {
        TransactionType.TRANSFER -> Icons.Rounded.SwapHoriz
        else -> CategoryIconMapper.iconForKey(category?.iconKey)
    }
    val title = when (transaction.type) {
        TransactionType.TRANSFER -> {
            val from = account?.name ?: "Account"
            val to = destinationAccount?.name ?: "Account"
            "$from → $to"
        }
        else -> category?.name ?: if (transaction.type == TransactionType.INCOME) "Income" else "Expense"
    }
    val subtitle = buildString {
        append(account?.name ?: "Account")
        append(" • ")
        append(transaction.transactionDate)
        append(" ")
        append(transaction.transactionTime)
    }

    val amountText = if (isMasked) {
        CurrencyFormatter.format(0L, currencyCode, masked = true)
    } else {
        when (transaction.type) {
            TransactionType.INCOME -> "+${CurrencyFormatter.format(transaction.amount, currencyCode)}"
            TransactionType.EXPENSE -> "-${CurrencyFormatter.format(transaction.amount, currencyCode)}"
            TransactionType.TRANSFER -> CurrencyFormatter.format(transaction.amount, currencyCode)
        }
    }
    val amountColor = when (transaction.type) {
        TransactionType.INCOME -> semantic.income
        TransactionType.EXPENSE -> semantic.expense
        TransactionType.TRANSFER -> semantic.transfer
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 0.5.dp,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("transaction_item_${transaction.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = iconVector,
                    contentDescription = title,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (transaction.sourceType == SourceType.AUTOMATIC) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = "Auto-detected",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(10.dp)
                                )
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    text = transaction.sourceApplication.take(10),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                    if (transaction.status == TransactionStatus.PENDING_REVIEW) {
                        Surface(
                            color = semantic.warningContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "Review",
                                style = MaterialTheme.typography.labelSmall,
                                color = semantic.warning,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = semantic.secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (transaction.notes.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        if (!transaction.receiptImageUri.isNullOrBlank()) {
                            Icon(
                                imageVector = Icons.Rounded.Attachment,
                                contentDescription = "Has receipt",
                                tint = semantic.secondaryText,
                                modifier = Modifier
                                    .size(13.dp)
                                    .padding(end = 3.dp)
                            )
                        }
                        Text(
                            text = transaction.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = semantic.secondaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            Text(
                text = amountText,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = amountColor,
                maxLines = 1
            )
        }
    }
}

@Composable
fun EmptyStateCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val semantic = LocalDailySpendColors.current
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = semantic.secondaryText,
                textAlign = TextAlign.Center
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onAction,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
fun SkeletonLoadingDashboard(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeletonAlpha"
    )
    val placeholderColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(placeholderColor)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(4) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(72.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(placeholderColor)
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(placeholderColor)
        )
        repeat(3) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(placeholderColor)
            )
        }
    }
}

@Composable
fun PinLockScreen(
    biometricAvailable: Boolean,
    biometricEnabled: Boolean,
    onUnlockPin: (String) -> Boolean,
    onRequestBiometric: () -> Unit,
    modifier: Modifier = Modifier
) {
    var entered by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val haptics = LocalHapticFeedback.current
    val semantic = LocalDailySpendColors.current

    LaunchedEffect(biometricAvailable, biometricEnabled) {
        if (biometricAvailable && biometricEnabled) {
            onRequestBiometric()
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = "Locked",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(30.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = "DailySpend Locked",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Enter your PIN to access your financial records",
                style = MaterialTheme.typography.bodyMedium,
                color = semantic.secondaryText,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                repeat(6) { idx ->
                    val filled = idx < entered.length
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(
                                if (filled) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline
                            )
                    )
                }
            }

            if (errorMsg != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = errorMsg!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = semantic.expense
                )
            }

            Spacer(Modifier.height(28.dp))

            val digits = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9")
            )
            digits.forEach { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.padding(vertical = 6.dp)
                ) {
                    row.forEach { d ->
                        PinKeyButton(
                            label = d,
                            onClick = {
                                if (entered.length < 6) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    entered += d
                                    errorMsg = null
                                }
                            }
                        )
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 6.dp)
            ) {
                if (biometricAvailable && biometricEnabled) {
                    IconButton(
                        onClick = onRequestBiometric,
                        modifier = Modifier
                            .size(68.dp)
                            .testTag("biometric_unlock_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Fingerprint,
                            contentDescription = "Unlock with Biometrics",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                } else {
                    Spacer(Modifier.size(68.dp))
                }

                PinKeyButton(
                    label = "0",
                    onClick = {
                        if (entered.length < 6) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            entered += "0"
                            errorMsg = null
                        }
                    }
                )

                IconButton(
                    onClick = {
                        if (entered.isNotEmpty()) {
                            entered = entered.dropLast(1)
                        }
                    },
                    modifier = Modifier.size(68.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Backspace,
                        contentDescription = "Backspace",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = {
                    if (!onUnlockPin(entered)) {
                        errorMsg = "Incorrect PIN. Try again."
                        entered = ""
                    }
                },
                enabled = entered.length >= 4,
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .minimumInteractiveComponentSize()
                    .testTag("pin_unlock_submit")
            ) {
                Text("Unlock")
            }
        }
    }
}

@Composable
private fun PinKeyButton(
    label: String,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier
            .size(68.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
