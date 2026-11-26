package com.example

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.local.TransactionEntity
import com.example.data.model.TransactionType
import com.example.notification.NotificationHelper
import com.example.ui.components.PinLockScreen
import com.example.ui.components.TransactionEntryBottomSheet
import com.example.ui.screens.AccountsAndCategoriesScreen
import com.example.ui.screens.BudgetScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.NotificationAccessScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StatisticsScreen
import com.example.ui.screens.TransactionReviewScreen
import com.example.ui.screens.TransactionsScreen
import com.example.ui.theme.DailySpendTheme
import com.example.ui.viewmodel.DailySpendViewModel
import com.example.utils.SecurityUtils

object DailySpendRoutes {
    const val HOME = "home"
    const val TRANSACTIONS = "transactions"
    const val STATISTICS = "statistics"
    const val BUDGET = "budget"
    const val SETTINGS = "settings"
    const val NOTIFICATION_ACCESS = "notification_access"
    const val TRANSACTION_REVIEW = "transaction_review"
    const val ACCOUNTS_CATEGORIES = "accounts_categories"
}

private data class TopNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val topNavItems = listOf(
    TopNavItem(DailySpendRoutes.HOME, "Home", Icons.Rounded.Home, Icons.Outlined.Home),
    TopNavItem(DailySpendRoutes.TRANSACTIONS, "Transactions", Icons.Rounded.ReceiptLong, Icons.Outlined.ReceiptLong),
    TopNavItem(DailySpendRoutes.STATISTICS, "Statistics", Icons.Rounded.BarChart, Icons.Outlined.BarChart),
    TopNavItem(DailySpendRoutes.BUDGET, "Budget", Icons.Rounded.PieChart, Icons.Outlined.PieChart),
    TopNavItem(DailySpendRoutes.SETTINGS, "Settings", Icons.Rounded.Settings, Icons.Outlined.Settings)
)

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: DailySpendViewModel = viewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val lastParserResult by viewModel.lastParserTestResult.collectAsStateWithLifecycle()

            DailySpendTheme(themeMode = state.settings.themeMode) {
                if (state.isLocked) {
                    val biometricAvailable = remember { SecurityUtils.canUseBiometric(this) }
                    PinLockScreen(
                        biometricAvailable = biometricAvailable,
                        biometricEnabled = state.settings.biometricEnabled,
                        onUnlockPin = { pin -> viewModel.unlockWithPin(pin) },
                        onRequestBiometric = {
                            SecurityUtils.authenticateBiometric(
                                activity = this,
                                onSuccess = { viewModel.unlockWithBiometric() },
                                onError = {}
                            )
                        }
                    )
                } else {
                    DailySpendAppShell(
                        viewModel = viewModel,
                        lastParserResult = lastParserResult
                    )
                }
            }
        }
    }
}

@Composable
private fun DailySpendAppShell(
    viewModel: DailySpendViewModel,
    lastParserResult: com.example.notification.ParseOutcome?
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: DailySpendRoutes.HOME
    val snackbarHostState = remember { SnackbarHostState() }

    var notificationAccessEnabled by remember {
        mutableStateOf(NotificationHelper.isNotificationListenerEnabled(context))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationAccessEnabled = NotificationHelper.isNotificationListenerEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        viewModel.toastEvents.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    var showTransactionSheet by remember { mutableStateOf(false) }
    var sheetInitialType by remember { mutableStateOf(TransactionType.EXPENSE) }
    var editingTransaction by remember { mutableStateOf<TransactionEntity?>(null) }

    val topLevelRoutes = remember { topNavItems.map { it.route }.toSet() }
    val isTopLevel = currentRoute in topLevelRoutes

    fun navigateTopLevel(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            floatingActionButton = {
                if (isTopLevel) {
                    FloatingActionButton(
                        onClick = {
                            editingTransaction = null
                            sheetInitialType = TransactionType.EXPENSE
                            showTransactionSheet = true
                        },
                        shape = CircleShape,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.testTag("fab_add_transaction")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "Add Transaction")
                    }
                }
            },
            bottomBar = {
                if (isTopLevel && !useRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        topNavItems.forEach { item ->
                            val selected = currentRoute == item.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTopLevel(item.route) },
                                icon = {
                                    val badgeCount = if (item.route == DailySpendRoutes.TRANSACTIONS) {
                                        state.pendingReviewTransactions.size
                                    } else 0
                                    if (badgeCount > 0) {
                                        BadgedBox(badge = { Badge { Text(badgeCount.toString()) } }) {
                                            Icon(
                                                imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                                contentDescription = item.label
                                            )
                                        }
                                    } else {
                                        Icon(
                                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                            contentDescription = item.label
                                        )
                                    }
                                },
                                label = { Text(item.label, maxLines = 1) },
                                modifier = Modifier.testTag("nav_${item.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isTopLevel && useRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        topNavItems.forEach { item ->
                            val selected = currentRoute == item.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTopLevel(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label
                                    )
                                },
                                label = { Text(item.label) }
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.TopCenter
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = DailySpendRoutes.HOME,
                        enterTransition = {
                            fadeIn(animationSpec = tween(220)) +
                                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(220))
                        },
                        exitTransition = {
                            fadeOut(animationSpec = tween(180))
                        },
                        popEnterTransition = {
                            fadeIn(animationSpec = tween(220)) +
                                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(220))
                        },
                        popExitTransition = {
                            fadeOut(animationSpec = tween(180))
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 760.dp)
                    ) {
                        composable(DailySpendRoutes.HOME) {
                            HomeScreen(
                                state = state,
                                notificationAccessEnabled = notificationAccessEnabled,
                                onRefresh = viewModel::refreshDashboard,
                                onToggleBalanceVisibility = viewModel::toggleBalanceVisibility,
                                onSelectDashboardPeriod = viewModel::setDashboardPeriod,
                                onSelectChartPeriod = viewModel::setChartPeriod,
                                onQuickAddTransaction = { type ->
                                    editingTransaction = null
                                    sheetInitialType = type
                                    showTransactionSheet = true
                                },
                                onEditTransaction = { tx ->
                                    editingTransaction = tx
                                    sheetInitialType = tx.type
                                    showTransactionSheet = true
                                },
                                onDeleteTransaction = viewModel::deleteTransaction,
                                onOpenAccounts = { navController.navigate(DailySpendRoutes.ACCOUNTS_CATEGORIES) },
                                onOpenStatistics = { navigateTopLevel(DailySpendRoutes.STATISTICS) },
                                onOpenTransactions = { navigateTopLevel(DailySpendRoutes.TRANSACTIONS) },
                                onOpenReviewQueue = { navController.navigate(DailySpendRoutes.TRANSACTION_REVIEW) },
                                onOpenNotificationSettings = { navController.navigate(DailySpendRoutes.NOTIFICATION_ACCESS) }
                            )
                        }

                        composable(DailySpendRoutes.TRANSACTIONS) {
                            TransactionsScreen(
                                state = state,
                                onRefresh = viewModel::refreshDashboard,
                                onSearchChange = viewModel::updateSearchQuery,
                                onFilterType = viewModel::setFilterType,
                                onFilterSource = viewModel::setFilterSource,
                                onFilterAccount = viewModel::setFilterAccount,
                                onSortChange = viewModel::setSortOption,
                                onClearFilters = viewModel::clearTransactionFilters,
                                onEditTransaction = { tx ->
                                    editingTransaction = tx
                                    sheetInitialType = tx.type
                                    showTransactionSheet = true
                                },
                                onDeleteTransaction = viewModel::deleteTransaction,
                                onOpenReviewQueue = { navController.navigate(DailySpendRoutes.TRANSACTION_REVIEW) }
                            )
                        }

                        composable(DailySpendRoutes.STATISTICS) {
                            StatisticsScreen(
                                state = state,
                                onSelectStatsFilter = viewModel::setStatsFilter,
                                onSelectCustomRange = viewModel::setStatsCustomRange
                            )
                        }

                        composable(DailySpendRoutes.BUDGET) {
                            BudgetScreen(
                                state = state,
                                onSaveBudget = viewModel::saveBudget,
                                onDeleteBudget = viewModel::deleteBudget
                            )
                        }

                        composable(DailySpendRoutes.SETTINGS) {
                            SettingsScreen(
                                state = state,
                                notificationAccessEnabled = notificationAccessEnabled,
                                onUpdateProfileName = viewModel::updateProfileName,
                                onUpdateCurrency = viewModel::updateCurrency,
                                onUpdateTheme = viewModel::updateThemeMode,
                                onSetHideBalanceOnStart = viewModel::setHideBalanceOnStart,
                                onSetBudgetNotifications = viewModel::setBudgetNotificationsEnabled,
                                onSetDailySummary = viewModel::setDailySummaryEnabled,
                                onSetPrivacyMaskLogs = viewModel::setPrivacyMaskLogs,
                                onEnablePin = viewModel::enablePin,
                                onDisablePin = viewModel::disablePin,
                                onSetBiometric = viewModel::setBiometricEnabled,
                                onExportCsv = viewModel::exportCsv,
                                onExportPdf = viewModel::exportPdf,
                                onBackupJson = viewModel::backupDatabase,
                                onRestoreJson = viewModel::restoreDatabase,
                                onClearAllData = viewModel::clearAllData,
                                onOpenNotificationAccess = { navController.navigate(DailySpendRoutes.NOTIFICATION_ACCESS) },
                                onOpenAccountsAndCategories = { navController.navigate(DailySpendRoutes.ACCOUNTS_CATEGORIES) },
                                onOpenReviewQueue = { navController.navigate(DailySpendRoutes.TRANSACTION_REVIEW) }
                            )
                        }

                        composable(DailySpendRoutes.NOTIFICATION_ACCESS) {
                            NotificationAccessScreen(
                                state = state,
                                notificationAccessEnabled = notificationAccessEnabled,
                                lastParserTestResult = lastParserResult,
                                onBack = { navController.popBackStack() },
                                onToggleAutoConfirm = viewModel::setAutoConfirmHighConfidence,
                                onToggleSource = viewModel::toggleNotificationSource,
                                onAssignAccount = viewModel::assignNotificationAccount,
                                onTestParser = viewModel::testNotificationParser,
                                onClearLogs = viewModel::clearNotificationLogs
                            )
                        }

                        composable(DailySpendRoutes.TRANSACTION_REVIEW) {
                            TransactionReviewScreen(
                                state = state,
                                onBack = { navController.popBackStack() },
                                onApprove = viewModel::approvePending,
                                onReject = viewModel::rejectPending
                            )
                        }

                        composable(DailySpendRoutes.ACCOUNTS_CATEGORIES) {
                            AccountsAndCategoriesScreen(
                                state = state,
                                onBack = { navController.popBackStack() },
                                onSaveAccount = viewModel::saveAccount,
                                onDeleteAccount = viewModel::deleteAccount,
                                onSaveCategory = viewModel::saveCategory,
                                onDeleteCategory = viewModel::deleteCategory
                            )
                        }
                    }
                }
            }
        }
    }

    if (showTransactionSheet) {
        TransactionEntryBottomSheet(
            initialType = sheetInitialType,
            editingTransaction = editingTransaction,
            accounts = state.accountsWithBalance.map { it.account },
            categories = state.categories,
            currencyCode = state.settings.currencyCode,
            onDismiss = { showTransactionSheet = false },
            onSave = { existingId, type, amount, catId, accId, destAccId, dateStr, timeStr, notes, receiptUri, srcType, srcApp ->
                viewModel.saveTransaction(
                    existingId = existingId,
                    type = type,
                    amount = amount,
                    categoryId = catId,
                    accountId = accId,
                    destinationAccountId = destAccId,
                    dateStr = dateStr,
                    timeStr = timeStr,
                    notes = notes,
                    receiptImageUri = receiptUri,
                    existingSourceType = srcType,
                    existingSourceApp = srcApp
                )
            }
        )
    }
}
