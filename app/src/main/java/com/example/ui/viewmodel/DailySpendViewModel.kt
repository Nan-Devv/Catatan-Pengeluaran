package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.DailySpendApplication
import com.example.data.local.AccountEntity
import com.example.data.local.AppSettingsEntity
import com.example.data.local.BudgetEntity
import com.example.data.local.CategoryEntity
import com.example.data.local.NotificationLogEntity
import com.example.data.local.NotificationSourceEntity
import com.example.data.local.TransactionEntity
import com.example.data.model.AccountType
import com.example.data.model.BudgetPeriod
import com.example.data.model.CategoryType
import com.example.data.model.ChartPeriod
import com.example.data.model.DashboardPeriod
import com.example.data.model.ExportPeriod
import com.example.data.model.SourceType
import com.example.data.model.StatsDateFilter
import com.example.data.model.ThemeMode
import com.example.data.model.TransactionSort
import com.example.data.model.TransactionStatus
import com.example.data.model.TransactionType
import com.example.data.repository.AccountWithBalance
import com.example.data.repository.BudgetProgress
import com.example.data.repository.DailySpendRepository
import com.example.notification.ParseOutcome
import com.example.utils.DateUtils
import com.example.utils.ExportManager
import com.example.utils.SecurityUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale

data class ChartBucket(
    val label: String,
    val income: Long,
    val expense: Long
)

data class CategorySpendSlice(
    val category: CategoryEntity?,
    val categoryName: String,
    val colorHex: Long,
    val amount: Long,
    val percentage: Float
)

data class DailySpendUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLocked: Boolean = false,
    val settings: AppSettingsEntity = AppSettingsEntity(),
    val isBalanceHidden: Boolean = false,
    val dashboardPeriod: DashboardPeriod = DashboardPeriod.THIS_MONTH,
    val chartPeriod: ChartPeriod = ChartPeriod.SEVEN_DAYS,
    val totalBalance: Long = 0L,
    val periodIncome: Long = 0L,
    val periodExpense: Long = 0L,
    val monthIncome: Long = 0L,
    val monthExpense: Long = 0L,
    val prevMonthExpense: Long = 0L,
    val expenseChangePercent: Float? = null,
    val accountsWithBalance: List<AccountWithBalance> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val allTransactions: List<TransactionEntity> = emptyList(),
    val recentTransactions: List<TransactionEntity> = emptyList(),
    val pendingReviewTransactions: List<TransactionEntity> = emptyList(),
    val budgetProgressList: List<BudgetProgress> = emptyList(),
    val notificationSources: List<NotificationSourceEntity> = emptyList(),
    val notificationLogs: List<NotificationLogEntity> = emptyList(),
    val homeChartBuckets: List<ChartBucket> = emptyList(),
    // Transactions filters
    val searchQuery: String = "",
    val filterType: TransactionType? = null,
    val filterSource: SourceType? = null,
    val filterAccountId: Long? = null,
    val filterCategoryId: Long? = null,
    val sortOption: TransactionSort = TransactionSort.NEWEST,
    val filteredTransactions: List<TransactionEntity> = emptyList(),
    // Statistics state
    val statsFilter: StatsDateFilter = StatsDateFilter.THIS_MONTH,
    val statsCustomStart: Long = DateUtils.startOfCurrentMonth(),
    val statsCustomEnd: Long = System.currentTimeMillis(),
    val statsIncome: Long = 0L,
    val statsExpense: Long = 0L,
    val statsSavings: Long = 0L,
    val statsExpenseRatio: Float = 0f,
    val statsDailyBuckets: List<ChartBucket> = emptyList(),
    val statsMonthlyBuckets: List<ChartBucket> = emptyList(),
    val statsCategorySlices: List<CategorySpendSlice> = emptyList(),
    val prevMonthIncome: Long = 0L
)

private data class FilterCriteria(
    val searchQuery: String,
    val filterType: TransactionType?,
    val filterSource: SourceType?,
    val filterAccountId: Long?,
    val filterCategoryId: Long?,
    val sortOption: TransactionSort
)

private data class PeriodConfig(
    val dashboardPeriod: DashboardPeriod,
    val chartPeriod: ChartPeriod,
    val statsFilter: StatsDateFilter,
    val statsCustomStart: Long,
    val statsCustomEnd: Long
)

class DailySpendViewModel(application: Application) : AndroidViewModel(application) {

    private val repo: DailySpendRepository = (application as DailySpendApplication).repository

    private val _toastEvents = MutableSharedFlow<String>()
    val toastEvents = _toastEvents.asSharedFlow()

    private val _isLoading = MutableStateFlow(true)
    private val _isRefreshing = MutableStateFlow(false)
    private val _isLocked = MutableStateFlow(false)
    private val _balanceHiddenOverride = MutableStateFlow<Boolean?>(null)

    private val _dashboardPeriod = MutableStateFlow(DashboardPeriod.THIS_MONTH)
    private val _chartPeriod = MutableStateFlow(ChartPeriod.SEVEN_DAYS)

    private val _searchQuery = MutableStateFlow("")
    private val _filterType = MutableStateFlow<TransactionType?>(null)
    private val _filterSource = MutableStateFlow<SourceType?>(null)
    private val _filterAccountId = MutableStateFlow<Long?>(null)
    private val _filterCategoryId = MutableStateFlow<Long?>(null)
    private val _sortOption = MutableStateFlow(TransactionSort.NEWEST)

    private val _statsFilter = MutableStateFlow(StatsDateFilter.THIS_MONTH)
    private val _statsCustomStart = MutableStateFlow(DateUtils.startOfCurrentMonth())
    private val _statsCustomEnd = MutableStateFlow(System.currentTimeMillis())

    private val _lastParserTestResult = MutableStateFlow<ParseOutcome?>(null)
    val lastParserTestResult: StateFlow<ParseOutcome?> = _lastParserTestResult.asStateFlow()

    init {
        viewModelScope.launch {
            repo.ensureSeeded()
            val s = repo.settings.first()
            _isLocked.value = s.pinEnabled && !s.pinHash.isNullOrBlank()
            _balanceHiddenOverride.value = s.hideBalanceOnStart
            _isLoading.value = false
        }
    }

    private val filterCriteriaFlow = combine(
        combine(_searchQuery, _filterType, _filterSource) { q, t, s -> Triple(q, t, s) },
        combine(_filterAccountId, _filterCategoryId, _sortOption) { a, c, sort -> Triple(a, c, sort) }
    ) { left, right ->
        FilterCriteria(
            searchQuery = left.first,
            filterType = left.second,
            filterSource = left.third,
            filterAccountId = right.first,
            filterCategoryId = right.second,
            sortOption = right.third
        )
    }

    private val periodConfigFlow = combine(
        _dashboardPeriod,
        _chartPeriod,
        _statsFilter,
        _statsCustomStart,
        _statsCustomEnd
    ) { dash, chart, stats, customStart, customEnd ->
        PeriodConfig(dash, chart, stats, customStart, customEnd)
    }

    private val coreDataFlow = combine(
        repo.transactions,
        repo.accounts,
        repo.categories,
        repo.budgets,
        repo.settings
    ) { txs, accs, cats, buds, sett ->
        listOf<Any>(txs, accs, cats, buds, sett)
    }

    private val metaFlow = combine(
        combine(repo.notificationSources, repo.notificationLogs) { s, l -> s to l },
        combine(_isLoading, _isRefreshing, _isLocked, _balanceHiddenOverride) { load, ref, lock, hid ->
            listOf<Any?>(load, ref, lock, hid)
        }
    ) { sourcesAndLogs, flags ->
        listOf<Any?>(sourcesAndLogs.first, sourcesAndLogs.second) + flags
    }

    val uiState: StateFlow<DailySpendUiState> = combine(
        coreDataFlow,
        metaFlow,
        filterCriteriaFlow,
        periodConfigFlow
    ) { core, meta, filters, periods ->
        @Suppress("UNCHECKED_CAST")
        val allTxs = core[0] as List<TransactionEntity>
        @Suppress("UNCHECKED_CAST")
        val accounts = core[1] as List<AccountEntity>
        @Suppress("UNCHECKED_CAST")
        val categories = core[2] as List<CategoryEntity>
        @Suppress("UNCHECKED_CAST")
        val budgets = core[3] as List<BudgetEntity>
        val settings = core[4] as AppSettingsEntity

        @Suppress("UNCHECKED_CAST")
        val sources = meta[0] as List<NotificationSourceEntity>
        @Suppress("UNCHECKED_CAST")
        val logs = meta[1] as List<NotificationLogEntity>
        val isLoading = meta[2] as Boolean
        val isRefreshing = meta[3] as Boolean
        val isLocked = meta[4] as Boolean
        val hiddenOverride = meta[5] as Boolean?

        val now = System.currentTimeMillis()
        val categoryMap = categories.associateBy { it.id }
        val accountMap = accounts.associateBy { it.id }

        val confirmedTxs = allTxs.filter { it.status == TransactionStatus.CONFIRMED }
        val pendingTxs = allTxs.filter { it.status == TransactionStatus.PENDING_REVIEW }

        val accountsWithBalance = DailySpendRepository.computeAccountsWithBalance(accounts, allTxs)
        val totalBalance = accountsWithBalance.sumOf { it.currentBalance }

        val monthStart = DateUtils.startOfCurrentMonth(now)
        val monthEnd = DateUtils.endOfCurrentMonth(now)
        val prevMonthStart = DateUtils.startOfPreviousMonth(now)
        val prevMonthEnd = DateUtils.endOfPreviousMonth(now)

        val monthIncome = confirmedTxs
            .filter { it.type == TransactionType.INCOME && it.timestampMillis in monthStart..monthEnd }
            .sumOf { it.amount }
        val monthExpense = confirmedTxs
            .filter { it.type == TransactionType.EXPENSE && it.timestampMillis in monthStart..monthEnd }
            .sumOf { it.amount }
        val prevMonthIncome = confirmedTxs
            .filter { it.type == TransactionType.INCOME && it.timestampMillis in prevMonthStart..prevMonthEnd }
            .sumOf { it.amount }
        val prevMonthExpense = confirmedTxs
            .filter { it.type == TransactionType.EXPENSE && it.timestampMillis in prevMonthStart..prevMonthEnd }
            .sumOf { it.amount }

        val expenseChangePct = if (prevMonthExpense > 0L) {
            ((monthExpense - prevMonthExpense).toFloat() / prevMonthExpense.toFloat()) * 100f
        } else null

        val dashRange = when (periods.dashboardPeriod) {
            DashboardPeriod.THIS_MONTH -> monthStart..monthEnd
            DashboardPeriod.LAST_30_DAYS -> DateUtils.daysAgoStart(30, now)..now
            DashboardPeriod.THIS_WEEK -> DateUtils.startOfCurrentWeek(now)..now
            DashboardPeriod.ALL_TIME -> 0L..Long.MAX_VALUE
        }
        val periodIncome = confirmedTxs
            .filter { it.type == TransactionType.INCOME && it.timestampMillis in dashRange }
            .sumOf { it.amount }
        val periodExpense = confirmedTxs
            .filter { it.type == TransactionType.EXPENSE && it.timestampMillis in dashRange }
            .sumOf { it.amount }

        val homeBuckets = buildHomeChartBuckets(confirmedTxs, periods.chartPeriod, now)
        val budgetProgress = DailySpendRepository.computeBudgetProgress(budgets, allTxs, categoryMap, now)

        // Filtered & sorted transactions
        val query = filters.searchQuery.trim().lowercase(Locale.getDefault())
        val filtered = allTxs
            .filter { it.status != TransactionStatus.REJECTED }
            .filter { tx ->
                val matchesType = filters.filterType == null || tx.type == filters.filterType
                val matchesSource = filters.filterSource == null || tx.sourceType == filters.filterSource
                val matchesAccount = filters.filterAccountId == null ||
                    tx.accountId == filters.filterAccountId ||
                    tx.destinationAccountId == filters.filterAccountId
                val matchesCategory = filters.filterCategoryId == null || tx.categoryId == filters.filterCategoryId
                val matchesQuery = if (query.isEmpty()) true else {
                    val catName = tx.categoryId?.let { categoryMap[it]?.name.orEmpty() }.orEmpty()
                    val accName = accountMap[tx.accountId]?.name.orEmpty()
                    tx.notes.lowercase().contains(query) ||
                        catName.lowercase().contains(query) ||
                        accName.lowercase().contains(query) ||
                        tx.sourceApplication.lowercase().contains(query) ||
                        tx.amount.toString().contains(query)
                }
                matchesType && matchesSource && matchesAccount && matchesCategory && matchesQuery
            }
            .let { list ->
                when (filters.sortOption) {
                    TransactionSort.NEWEST -> list.sortedByDescending { it.timestampMillis }
                    TransactionSort.OLDEST -> list.sortedBy { it.timestampMillis }
                    TransactionSort.HIGHEST -> list.sortedByDescending { it.amount }
                    TransactionSort.LOWEST -> list.sortedBy { it.amount }
                }
            }

        // Statistics aggregations
        val statsRange = when (periods.statsFilter) {
            StatsDateFilter.THIS_MONTH -> monthStart..monthEnd
            StatsDateFilter.LAST_MONTH -> prevMonthStart..prevMonthEnd
            StatsDateFilter.THREE_MONTHS -> DateUtils.daysAgoStart(90, now)..now
            StatsDateFilter.YEAR_TO_DATE -> DateUtils.startOfYear(now)..now
            StatsDateFilter.CUSTOM -> DateUtils.startOfDay(periods.statsCustomStart)..DateUtils.endOfDay(periods.statsCustomEnd)
        }
        val statsTxs = confirmedTxs.filter { it.timestampMillis in statsRange }
        val statsIncome = statsTxs.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
        val statsExpense = statsTxs.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
        val statsSavings = statsIncome - statsExpense
        val statsExpenseRatio = if (statsIncome > 0L) {
            (statsExpense.toFloat() / statsIncome.toFloat()).coerceAtLeast(0f)
        } else 0f

        val statsDaily = buildDailyBuckets(statsTxs, statsRange.first, minOf(statsRange.last, now))
        val statsMonthly = buildMonthlyBuckets(confirmedTxs, 6, now)

        val expenseByCategory = statsTxs
            .filter { it.type == TransactionType.EXPENSE }
            .groupBy { it.categoryId }
            .map { (catId, list) ->
                val cat = catId?.let { categoryMap[it] }
                val sum = list.sumOf { it.amount }
                CategorySpendSlice(
                    category = cat,
                    categoryName = cat?.name ?: "Other Expenses",
                    colorHex = cat?.colorHex ?: 0xFF64748B,
                    amount = sum,
                    percentage = if (statsExpense > 0L) sum.toFloat() / statsExpense.toFloat() else 0f
                )
            }
            .sortedByDescending { it.amount }

        DailySpendUiState(
            isLoading = isLoading,
            isRefreshing = isRefreshing,
            isLocked = isLocked,
            settings = settings,
            isBalanceHidden = hiddenOverride ?: settings.hideBalanceOnStart,
            dashboardPeriod = periods.dashboardPeriod,
            chartPeriod = periods.chartPeriod,
            totalBalance = totalBalance,
            periodIncome = periodIncome,
            periodExpense = periodExpense,
            monthIncome = monthIncome,
            monthExpense = monthExpense,
            prevMonthExpense = prevMonthExpense,
            expenseChangePercent = expenseChangePct,
            accountsWithBalance = accountsWithBalance,
            categories = categories,
            allTransactions = allTxs,
            recentTransactions = confirmedTxs.take(5),
            pendingReviewTransactions = pendingTxs,
            budgetProgressList = budgetProgress,
            notificationSources = sources,
            notificationLogs = logs,
            homeChartBuckets = homeBuckets,
            searchQuery = filters.searchQuery,
            filterType = filters.filterType,
            filterSource = filters.filterSource,
            filterAccountId = filters.filterAccountId,
            filterCategoryId = filters.filterCategoryId,
            sortOption = filters.sortOption,
            filteredTransactions = filtered,
            statsFilter = periods.statsFilter,
            statsCustomStart = periods.statsCustomStart,
            statsCustomEnd = periods.statsCustomEnd,
            statsIncome = statsIncome,
            statsExpense = statsExpense,
            statsSavings = statsSavings,
            statsExpenseRatio = statsExpenseRatio,
            statsDailyBuckets = statsDaily,
            statsMonthlyBuckets = statsMonthly,
            statsCategorySlices = expenseByCategory,
            prevMonthIncome = prevMonthIncome
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DailySpendUiState()
    )

    fun refreshDashboard() {
        viewModelScope.launch {
            _isRefreshing.value = true
            repo.ensureSeeded()
            _isRefreshing.value = false
            emitToast("Dashboard synced with local SQLite database")
        }
    }

    fun toggleBalanceVisibility() {
        val current = _balanceHiddenOverride.value ?: uiState.value.settings.hideBalanceOnStart
        _balanceHiddenOverride.value = !current
    }

    fun setDashboardPeriod(period: DashboardPeriod) {
        _dashboardPeriod.value = period
    }

    fun setChartPeriod(period: ChartPeriod) {
        _chartPeriod.value = period
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilterType(type: TransactionType?) {
        _filterType.value = type
    }

    fun setFilterSource(source: SourceType?) {
        _filterSource.value = source
    }

    fun setFilterAccount(accountId: Long?) {
        _filterAccountId.value = accountId
    }

    fun setFilterCategory(categoryId: Long?) {
        _filterCategoryId.value = categoryId
    }

    fun setSortOption(sort: TransactionSort) {
        _sortOption.value = sort
    }

    fun clearTransactionFilters() {
        _searchQuery.value = ""
        _filterType.value = null
        _filterSource.value = null
        _filterAccountId.value = null
        _filterCategoryId.value = null
        _sortOption.value = TransactionSort.NEWEST
    }

    fun setStatsFilter(filter: StatsDateFilter) {
        _statsFilter.value = filter
    }

    fun setStatsCustomRange(startMillis: Long, endMillis: Long) {
        _statsCustomStart.value = startMillis
        _statsCustomEnd.value = endMillis
        _statsFilter.value = StatsDateFilter.CUSTOM
    }

    // Transactions CRUD
    fun saveTransaction(
        existingId: Long = 0L,
        type: TransactionType,
        amount: Long,
        categoryId: Long?,
        accountId: Long,
        destinationAccountId: Long?,
        dateStr: String,
        timeStr: String,
        notes: String,
        receiptImageUri: String?,
        existingSourceType: SourceType = SourceType.MANUAL,
        existingSourceApp: String = "Manual"
    ) {
        if (amount <= 0L) {
            emitToast("Amount must be greater than 0")
            return
        }
        if (type == TransactionType.TRANSFER && destinationAccountId == accountId) {
            emitToast("Source and destination accounts must be different")
            return
        }
        viewModelScope.launch {
            val timestamp = DateUtils.parseDateTimeToMillis(dateStr, timeStr)
            val tx = TransactionEntity(
                id = existingId,
                type = type,
                amount = amount,
                categoryId = if (type == TransactionType.TRANSFER) null else categoryId,
                accountId = accountId,
                destinationAccountId = if (type == TransactionType.TRANSFER) destinationAccountId else null,
                transactionDate = dateStr,
                transactionTime = timeStr,
                timestampMillis = timestamp,
                sourceType = existingSourceType,
                sourceApplication = existingSourceApp,
                status = TransactionStatus.CONFIRMED,
                notes = notes.trim(),
                receiptImageUri = receiptImageUri
            )
            repo.saveTransaction(tx)
            emitToast(if (existingId == 0L) "Transaction saved" else "Transaction updated")
        }
    }

    fun deleteTransaction(id: Long) {
        viewModelScope.launch {
            repo.deleteTransaction(id)
            emitToast("Transaction deleted")
        }
    }

    fun approvePending(
        tx: TransactionEntity,
        amount: Long,
        type: TransactionType,
        categoryId: Long?,
        accountId: Long,
        destAccountId: Long?,
        notes: String
    ) {
        viewModelScope.launch {
            repo.approvePendingTransaction(tx, amount, type, categoryId, accountId, destAccountId, notes)
            emitToast("Transaction confirmed")
        }
    }

    fun rejectPending(tx: TransactionEntity) {
        viewModelScope.launch {
            repo.rejectPendingTransaction(tx)
            emitToast("Transaction rejected")
        }
    }

    // Accounts CRUD
    fun saveAccount(
        id: Long = 0L,
        name: String,
        type: AccountType,
        initialBalance: Long,
        iconKey: String,
        colorHex: Long
    ) {
        if (name.isBlank()) {
            emitToast("Account name cannot be empty")
            return
        }
        viewModelScope.launch {
            repo.saveAccount(id, name, type, initialBalance, iconKey, colorHex)
            emitToast(if (id == 0L) "Account created" else "Account updated")
        }
    }

    fun deleteAccount(account: AccountEntity) {
        viewModelScope.launch {
            if (uiState.value.accountsWithBalance.size <= 1) {
                emitToast("At least one account is required")
                return@launch
            }
            repo.deleteAccount(account)
            emitToast("Account deleted")
        }
    }

    // Categories CRUD
    fun saveCategory(
        id: Long = 0L,
        name: String,
        type: CategoryType,
        iconKey: String,
        colorHex: Long
    ) {
        if (name.isBlank()) {
            emitToast("Category name cannot be empty")
            return
        }
        viewModelScope.launch {
            repo.saveCategory(id, name, type, iconKey, colorHex)
            emitToast(if (id == 0L) "Category saved" else "Category updated")
        }
    }

    fun deleteCategory(category: CategoryEntity) {
        viewModelScope.launch {
            repo.deleteCategory(category)
            emitToast("Category deleted")
        }
    }

    // Budgets CRUD
    fun saveBudget(
        id: Long = 0L,
        name: String,
        amountLimit: Long,
        period: BudgetPeriod,
        categoryId: Long?
    ) {
        if (name.isBlank() || amountLimit <= 0L) {
            emitToast("Enter a valid budget name and limit")
            return
        }
        viewModelScope.launch {
            repo.saveBudget(id, name, amountLimit, period, categoryId)
            emitToast(if (id == 0L) "Budget created" else "Budget updated")
        }
    }

    fun deleteBudget(budget: BudgetEntity) {
        viewModelScope.launch {
            repo.deleteBudget(budget)
            emitToast("Budget deleted")
        }
    }

    // Notification Sources & Parser Tester
    fun toggleNotificationSource(source: NotificationSourceEntity, enabled: Boolean) {
        viewModelScope.launch {
            repo.toggleNotificationSource(source, enabled)
        }
    }

    fun assignNotificationAccount(source: NotificationSourceEntity, accountId: Long?) {
        viewModelScope.launch {
            repo.updateNotificationSourceAccount(source, accountId)
        }
    }

    fun clearNotificationLogs() {
        viewModelScope.launch {
            repo.clearNotificationLogs()
            emitToast("Detection logs cleared")
        }
    }

    fun testNotificationParser(packageName: String, title: String, body: String) {
        _lastParserTestResult.value = repo.testParserFormat(packageName, title, body)
    }

    // Settings & Security
    fun updateProfileName(name: String) {
        viewModelScope.launch {
            repo.updateSettings { it.copy(profileName = name.trim().ifEmpty { "DailySpend User" }) }
            emitToast("Profile updated")
        }
    }

    fun updateCurrency(code: String) {
        viewModelScope.launch {
            repo.updateSettings { it.copy(currencyCode = code) }
            emitToast("Currency set to $code")
        }
    }

    fun updateThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            repo.updateSettings { it.copy(themeMode = mode) }
        }
    }

    fun setHideBalanceOnStart(hide: Boolean) {
        viewModelScope.launch {
            repo.updateSettings { it.copy(hideBalanceOnStart = hide) }
            _balanceHiddenOverride.value = hide
        }
    }

    fun setBudgetNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repo.updateSettings { it.copy(budgetNotificationsEnabled = enabled) }
        }
    }

    fun setDailySummaryEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repo.updateSettings { it.copy(dailySummaryEnabled = enabled) }
            if (enabled) {
                repo.triggerDailySummaryNow()
            }
        }
    }

    fun setAutoConfirmHighConfidence(enabled: Boolean) {
        viewModelScope.launch {
            repo.updateSettings { it.copy(autoConfirmHighConfidence = enabled) }
        }
    }

    fun setPrivacyMaskLogs(enabled: Boolean) {
        viewModelScope.launch {
            repo.updateSettings { it.copy(privacyMaskLogs = enabled) }
        }
    }

    fun enablePin(pin: String) {
        if (pin.length !in 4..6) {
            emitToast("PIN must be 4 to 6 digits")
            return
        }
        viewModelScope.launch {
            val hashed = SecurityUtils.hashPin(pin)
            repo.updateSettings { it.copy(pinEnabled = true, pinHash = hashed) }
            emitToast("App PIN enabled")
        }
    }

    fun disablePin() {
        viewModelScope.launch {
            repo.updateSettings { it.copy(pinEnabled = false, pinHash = null, biometricEnabled = false) }
            _isLocked.value = false
            emitToast("App PIN removed")
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repo.updateSettings { it.copy(biometricEnabled = enabled) }
        }
    }

    fun unlockWithPin(pin: String): Boolean {
        val ok = SecurityUtils.verifyPin(pin, uiState.value.settings.pinHash)
        if (ok) {
            _isLocked.value = false
        } else {
            emitToast("Incorrect PIN")
        }
        return ok
    }

    fun unlockWithBiometric() {
        _isLocked.value = false
    }

    // Export & Backup
    fun exportCsv(uri: Uri, period: ExportPeriod) {
        viewModelScope.launch {
            val state = uiState.value
            val txs = filterForExportPeriod(state.allTransactions, period)
            val result = withContext(Dispatchers.IO) {
                ExportManager.writeCsvToUri(
                    context = getApplication(),
                    uri = uri,
                    transactions = txs,
                    accounts = state.accountsWithBalance.associate { it.account.id to it.account },
                    categories = state.categories.associateBy { it.id }
                )
            }
            result.onSuccess { count -> emitToast("Exported $count transactions to CSV") }
                .onFailure { emitToast("CSV export failed: ${it.localizedMessage}") }
        }
    }

    fun exportPdf(uri: Uri, period: ExportPeriod) {
        viewModelScope.launch {
            val state = uiState.value
            val txs = filterForExportPeriod(state.allTransactions, period)
            val result = withContext(Dispatchers.IO) {
                ExportManager.writePdfToUri(
                    context = getApplication(),
                    uri = uri,
                    periodLabel = period.label,
                    currencyCode = state.settings.currencyCode,
                    transactions = txs,
                    accounts = state.accountsWithBalance.associate { it.account.id to it.account },
                    categories = state.categories.associateBy { it.id }
                )
            }
            result.onSuccess { count -> emitToast("Exported PDF report ($count items)") }
                .onFailure { emitToast("PDF export failed: ${it.localizedMessage}") }
        }
    }

    fun backupDatabase(uri: Uri) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val payload = repo.buildBackupPayload()
                ExportManager.writeBackupJsonToUri(getApplication(), uri, payload)
            }
            result.onSuccess { emitToast("Database backup saved") }
                .onFailure { emitToast("Backup failed: ${it.localizedMessage}") }
        }
    }

    fun restoreDatabase(uri: Uri) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                ExportManager.readBackupJsonFromUri(getApplication(), uri)
            }
            result.onSuccess { payload ->
                repo.restoreBackupPayload(payload)
                emitToast("Restored ${payload.transactions.size} transactions")
            }.onFailure {
                emitToast("Invalid backup file: ${it.localizedMessage}")
            }
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            repo.clearAllUserData()
            emitToast("All transactions, budgets, and logs cleared")
        }
    }

    private fun filterForExportPeriod(
        transactions: List<TransactionEntity>,
        period: ExportPeriod
    ): List<TransactionEntity> {
        val now = System.currentTimeMillis()
        val range = when (period) {
            ExportPeriod.THIS_MONTH -> DateUtils.startOfCurrentMonth(now)..DateUtils.endOfCurrentMonth(now)
            ExportPeriod.LAST_MONTH -> DateUtils.startOfPreviousMonth(now)..DateUtils.endOfPreviousMonth(now)
            ExportPeriod.LAST_3_MONTHS -> DateUtils.daysAgoStart(90, now)..now
            ExportPeriod.ALL_TIME -> 0L..Long.MAX_VALUE
        }
        return transactions.filter { it.status != TransactionStatus.REJECTED && it.timestampMillis in range }
    }

    private fun buildHomeChartBuckets(
        confirmed: List<TransactionEntity>,
        period: ChartPeriod,
        now: Long
    ): List<ChartBucket> {
        return when (period) {
            ChartPeriod.SEVEN_DAYS -> buildDailyBuckets(confirmed, DateUtils.daysAgoStart(7, now), now)
            ChartPeriod.THIRTY_DAYS -> buildWeeklySpanBuckets(confirmed, 30, 5, now)
            ChartPeriod.THREE_MONTHS -> buildMonthlyBuckets(confirmed, 3, now)
            ChartPeriod.SIX_MONTHS -> buildMonthlyBuckets(confirmed, 6, now)
            ChartPeriod.ONE_YEAR -> buildMonthlyBuckets(confirmed, 12, now)
        }
    }

    private fun buildDailyBuckets(
        confirmed: List<TransactionEntity>,
        startMillis: Long,
        endMillis: Long
    ): List<ChartBucket> {
        val buckets = mutableListOf<ChartBucket>()
        val cal = Calendar.getInstance().apply { timeInMillis = DateUtils.startOfDay(startMillis) }
        val maxDays = 14
        val totalDays = (((DateUtils.startOfDay(endMillis) - cal.timeInMillis) / 86_400_000L) + 1).toInt().coerceAtLeast(1)
        val stepDays = if (totalDays > maxDays) (totalDays / 7).coerceAtLeast(1) else 1

        var cursor = cal.timeInMillis
        while (cursor <= endMillis) {
            val bucketEnd = DateUtils.endOfDay(cursor + (stepDays - 1) * 86_400_000L).coerceAtMost(endMillis)
            val inBucket = confirmed.filter { it.timestampMillis in cursor..bucketEnd }
            val inc = inBucket.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
            val exp = inBucket.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
            buckets.add(ChartBucket(DateUtils.formatShortDay(cursor), inc, exp))
            cursor = DateUtils.startOfDay(bucketEnd + 86_400_000L)
        }
        return buckets
    }

    private fun buildWeeklySpanBuckets(
        confirmed: List<TransactionEntity>,
        days: Int,
        bucketCount: Int,
        now: Long
    ): List<ChartBucket> {
        val start = DateUtils.daysAgoStart(days, now)
        val span = ((now - start) / bucketCount).coerceAtLeast(86_400_000L)
        return (0 until bucketCount).map { idx ->
            val bStart = start + idx * span
            val bEnd = if (idx == bucketCount - 1) now else (bStart + span - 1)
            val inBucket = confirmed.filter { it.timestampMillis in bStart..bEnd }
            ChartBucket(
                label = DateUtils.formatShortDay(bStart),
                income = inBucket.filter { it.type == TransactionType.INCOME }.sumOf { it.amount },
                expense = inBucket.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
            )
        }
    }

    private fun buildMonthlyBuckets(
        confirmed: List<TransactionEntity>,
        months: Int,
        now: Long
    ): List<ChartBucket> {
        return (months - 1 downTo 0).map { offset ->
            val cal = Calendar.getInstance().apply {
                timeInMillis = now
                add(Calendar.MONTH, -offset)
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val start = cal.timeInMillis
            cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
            cal.set(Calendar.HOUR_OF_DAY, 23)
            cal.set(Calendar.MINUTE, 59)
            cal.set(Calendar.SECOND, 59)
            val end = cal.timeInMillis
            val inBucket = confirmed.filter { it.timestampMillis in start..end }
            ChartBucket(
                label = DateUtils.formatMonthYear(start).substringBefore(" "),
                income = inBucket.filter { it.type == TransactionType.INCOME }.sumOf { it.amount },
                expense = inBucket.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
            )
        }
    }

    private fun emitToast(message: String) {
        viewModelScope.launch {
            _toastEvents.emit(message)
        }
    }
}
