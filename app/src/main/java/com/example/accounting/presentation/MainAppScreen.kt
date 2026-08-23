package com.example.accounting.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.accounting.domain.accounting.Ledger
import com.example.accounting.domain.accounting.Voucher
import com.example.accounting.domain.accounting.VoucherType
import androidx.activity.compose.BackHandler
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import com.example.accounting.presentation.navigation.AdaptiveNavigationType
import com.example.accounting.presentation.navigation.AppRoute
import com.example.accounting.presentation.navigation.getAdaptiveNavigationType
import com.example.accounting.presentation.components.AppTopBar
import com.example.accounting.presentation.components.CreateCompanyDialog
import com.example.accounting.presentation.components.CreateLedgerDialog
import com.example.accounting.presentation.components.CreateStockItemDialog
import com.example.accounting.presentation.components.CreateVoucherDialog
import com.example.accounting.presentation.components.VoucherDetailDialog
import com.example.accounting.presentation.features.dashboard.DashboardScreen
import com.example.accounting.presentation.features.daybook.DayBookScreen
import com.example.accounting.presentation.features.ledgers.ChartOfAccountsScreen
import com.example.accounting.presentation.features.reports.ReportsScreen
import com.example.accounting.presentation.features.settings.SettingsAndSyncScreen
import com.example.accounting.presentation.viewmodel.AccountingViewModel
import com.example.accounting.presentation.viewmodel.NavigationTab
import kotlinx.coroutines.flow.collectLatest

data class NavItem(
    val tab: NavigationTab,
    val route: AppRoute,
    val label: String,
    val icon: ImageVector,
    val tag: String
)

@Composable
fun MainAppScreen(
    widthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
    viewModel: AccountingViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val canGoBack by viewModel.router.canGoBack.collectAsState()

    BackHandler(enabled = canGoBack) {
        viewModel.navigateBack()
    }

    // Dialog state controllers
    var isCreateVoucherOpen by remember { mutableStateOf(false) }
    var createVoucherType by remember { mutableStateOf(VoucherType.PAYMENT) }

    var isCreateLedgerOpen by remember { mutableStateOf(false) }
    var isCreateStockItemOpen by remember { mutableStateOf(false) }
    var isCreateCompanyOpen by remember { mutableStateOf(false) }
    var selectedVoucherDetail by remember { mutableStateOf<Voucher?>(null) }

    LaunchedEffect(Unit) {
        viewModel.snackbarEvents.collectLatest { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val navItems = listOf(
        NavItem(NavigationTab.DASHBOARD, AppRoute.Dashboard, "Dashboard", Icons.Default.Dashboard, "nav_dashboard"),
        NavItem(NavigationTab.DAYBOOK, AppRoute.DayBook, "Day Book", Icons.Default.Book, "nav_daybook"),
        NavItem(NavigationTab.CHART_OF_ACCOUNTS, AppRoute.ChartOfAccounts, "Ledgers", Icons.Default.AccountBalance, "nav_ledgers"),
        NavItem(NavigationTab.REPORTS, AppRoute.Reports, "Reports", Icons.Default.Assessment, "nav_reports"),
        NavItem(NavigationTab.SETTINGS_SYNC, AppRoute.SettingsAndSync, "Gov & Sync", Icons.Default.Settings, "nav_settings")
    )

    val adaptiveNavType = getAdaptiveNavigationType(widthSizeClass)
    val useRail = adaptiveNavType == AdaptiveNavigationType.NAVIGATION_RAIL || adaptiveNavType == AdaptiveNavigationType.PERMANENT_NAVIGATION_DRAWER

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpanded = useRail || maxWidth >= 600.dp

        Scaffold(
            topBar = {
                AppTopBar(
                    currentCompany = uiState.currentCompany,
                    companies = uiState.companies,
                    currentFinancialYear = uiState.currentFinancialYear,
                    financialYears = uiState.financialYears,
                    pendingSyncCount = uiState.pendingSyncCount,
                    isSyncing = uiState.isSyncing,
                    onCompanySelected = { viewModel.switchCompany(it) },
                    onFinancialYearSelected = { viewModel.switchFinancialYear(it) },
                    onSyncClicked = { viewModel.triggerSync() },
                    onNewCompanyClicked = { isCreateCompanyOpen = true }
                )
            },
            bottomBar = {
                if (!isExpanded) {
                    NavigationBar {
                        navItems.forEach { item ->
                            NavigationBarItem(
                                selected = uiState.selectedTab == item.tab,
                                onClick = { viewModel.selectTab(item.tab) },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label) },
                                modifier = Modifier.testTag(item.tag)
                            )
                        }
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { paddingValues ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Adaptive Navigation Rail on Expanded Tablet / Foldable screens
                if (isExpanded) {
                    NavigationRail(
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        navItems.forEach { item ->
                            NavigationRailItem(
                                selected = uiState.selectedTab == item.tab,
                                onClick = { viewModel.selectTab(item.tab) },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label) },
                                modifier = Modifier.testTag(item.tag)
                            )
                        }
                    }
                }

                // Active Tab Content View
                Box(modifier = Modifier.fillMaxSize()) {
                    when (uiState.selectedTab) {
                        NavigationTab.DASHBOARD -> DashboardScreen(
                            uiState = uiState,
                            onOpenCreateVoucher = { type ->
                                createVoucherType = type
                                isCreateVoucherOpen = true
                            },
                            onVoucherClick = { selectedVoucherDetail = it },
                            onViewAllDayBook = { viewModel.selectTab(NavigationTab.DAYBOOK) },
                            onViewReports = { viewModel.selectTab(NavigationTab.REPORTS) }
                        )

                        NavigationTab.DAYBOOK -> DayBookScreen(
                            uiState = uiState,
                            onVoucherClick = { selectedVoucherDetail = it },
                            onOpenCreateVoucher = { type ->
                                createVoucherType = type
                                isCreateVoucherOpen = true
                            },
                            onFilterTypeSelected = { viewModel.setVoucherTypeFilter(it) },
                            onSearchQueryChanged = { viewModel.setSearchQuery(it) }
                        )

                        NavigationTab.CHART_OF_ACCOUNTS -> ChartOfAccountsScreen(
                            uiState = uiState,
                            onLedgerClick = { ledger -> viewModel.loadLedgerStatement(ledger) },
                            onBackFromStatement = { viewModel.clearLedgerStatement() },
                            onOpenCreateLedger = { isCreateLedgerOpen = true },
                            onOpenCreateStockItem = { isCreateStockItemOpen = true },
                            onDeleteLedger = { ledger -> viewModel.deleteLedgerSafely(ledger.ledgerId) }
                        )

                        NavigationTab.REPORTS -> ReportsScreen(
                            uiState = uiState,
                            onRefreshReports = { viewModel.refreshFinancialReports() }
                        )

                        NavigationTab.SETTINGS_SYNC -> SettingsAndSyncScreen(
                            uiState = uiState,
                            onCompanySwitch = { viewModel.switchCompany(it) },
                            onOpenCreateCompany = { isCreateCompanyOpen = true },
                            onTogglePeriodLock = { viewModel.togglePeriodLock(it) },
                            onTriggerSync = { viewModel.triggerSync() },
                            onUpdateAccountingConfiguration = { mode, businessType -> viewModel.updateAccountingConfiguration(mode, businessType) },
                            isCloudSyncLoggedIn = uiState.isCloudSyncLoggedIn,
                            onCloudSyncLogin = { email, password -> viewModel.loginCloudSync(email, password) },
                            onCloudSyncLogout = { viewModel.logoutCloudSync() }
                        )
                    }
                }
            }
        }
    }

    // Modal Dialogs
    if (isCreateVoucherOpen) {
        CreateVoucherDialog(
            ledgers = uiState.ledgers,
            stockItems = uiState.stockItems,
            vouchers = uiState.vouchers,
            outstandingInvoices = uiState.outstandingInvoices,
            companyStateCode = uiState.currentCompany?.stateCode ?: "",
            defaultVoucherType = createVoucherType,
            onDismiss = { isCreateVoucherOpen = false; viewModel.clearOutstandingInvoices() },
            onPostQuickVoucher = { type, date, drLedger, crLedger, amount, narration, ref ->
                viewModel.postQuickVoucher(type, date, drLedger, crLedger, amount, narration, ref)
            },
            onPostSaleInvoice = { customer, sales, lines, date, ref, narration ->
                viewModel.postSaleInvoice(customer, sales, lines, date, ref, narration)
            },
            onPostPurchaseBill = { supplier, purchase, lines, date, ref, narration ->
                viewModel.postPurchaseBill(supplier, purchase, lines, date, ref, narration)
            },
            onPostCreditNote = { originalId, date, ref, narration ->
                viewModel.postCreditNote(originalId, date, ref, narration)
            },
            onPostDebitNote = { originalId, date, ref, narration ->
                viewModel.postDebitNote(originalId, date, ref, narration)
            },
            onPostSettlement = { type, date, drLedger, crLedger, amount, narration, ref, paymentMode, allocations ->
                viewModel.postQuickVoucher(type, date, drLedger, crLedger, amount, narration, ref, paymentMode, allocations)
            },
            onLoadOutstandingInvoices = { partyLedgerId -> viewModel.loadOutstandingInvoices(partyLedgerId) },
            onClearOutstandingInvoices = { viewModel.clearOutstandingInvoices() }
        )
    }

    if (isCreateLedgerOpen) {
        CreateLedgerDialog(
            groups = uiState.groups,
            onDismiss = { isCreateLedgerOpen = false },
            onCreateLedger = { name, grpId, opBal, opType, gstin, pan, phone, email, addr, hsn, taxRate ->
                viewModel.createLedger(name, grpId, opBal, opType, gstin, pan, phone, email, addr, hsn, taxRate)
            }
        )
    }

    if (isCreateStockItemOpen) {
        CreateStockItemDialog(
            onDismiss = { isCreateStockItemOpen = false },
            onCreateItem = { name, sku, hsn, unit, gstRate, openingQty, openingRate ->
                viewModel.createStockItem(name, sku, hsn, unit, gstRate, openingQty, openingRate)
            }
        )
    }

    if (isCreateCompanyOpen) {
        CreateCompanyDialog(
            onDismiss = { isCreateCompanyOpen = false },
            onCreateCompany = { name, trade, gstin, pan, state, addr, email, phone ->
                viewModel.createCompany(name, trade, gstin, pan, state, addr, email, phone)
            }
        )
    }

    selectedVoucherDetail?.let { voucher ->
        VoucherDetailDialog(
            voucher = voucher,
            onDismiss = { selectedVoucherDetail = null },
            onDeleteVoucher = { v -> viewModel.deleteVoucherSafely(v.voucherId) }
        )
    }
}
