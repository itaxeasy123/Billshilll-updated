package com.example.accounting.presentation

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.accounting.domain.accounting.Ledger
import com.example.accounting.domain.accounting.Voucher
import com.example.accounting.domain.accounting.VoucherType
import com.example.accounting.domain.dataimport.ImportFileFormat
import com.example.accounting.domain.party.Party
import com.example.accounting.domain.party.PartyRole
import androidx.activity.compose.BackHandler
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import com.example.accounting.presentation.navigation.AdaptiveNavigationType
import com.example.accounting.presentation.navigation.AppRoute
import com.example.accounting.presentation.navigation.getAdaptiveNavigationType
import com.example.accounting.presentation.theme.Breakpoints
import com.example.accounting.presentation.components.AppDivider
import com.example.accounting.presentation.components.AppTopBar
import com.example.accounting.presentation.components.CreateBankUpiProfileDialog
import com.example.accounting.presentation.components.CreateCompanyDialog
import com.example.accounting.presentation.components.CreateLedgerDialog
import com.example.accounting.presentation.components.CreatePartyDialog
import com.example.accounting.presentation.components.CreateStockItemDialog
import com.example.accounting.presentation.components.CreateVoucherDialog
import com.example.accounting.presentation.components.VoucherDetailDialog
import com.example.accounting.presentation.features.dashboard.DashboardScreen
import com.example.accounting.presentation.features.datatools.DataToolsScreen
import com.example.accounting.presentation.features.daybook.DayBookScreen
import com.example.accounting.presentation.features.ledgers.ChartOfAccountsScreen
import com.example.accounting.presentation.features.money.MoneyTabContent
import com.example.accounting.presentation.features.party.PartiesScreen
import com.example.accounting.presentation.features.profile.ProfileScreen
import com.example.accounting.presentation.features.purchases.PurchasesScreen
import com.example.accounting.presentation.features.reports.ReportsCenterScreen
import com.example.accounting.presentation.features.sales.SalesScreen
import com.example.accounting.presentation.features.search.SearchScreen
import com.example.accounting.presentation.features.settings.SettingsAndSyncScreen
import com.example.accounting.presentation.features.subscription.SubscriptionScreen
import com.example.accounting.presentation.viewmodel.AccountingViewModel
import com.example.accounting.presentation.viewmodel.NavigationTab
import com.example.accounting.presentation.viewmodel.isInventoryEnabled
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

data class NavItem(
    val tab: NavigationTab,
    val route: AppRoute,
    val label: String,
    val icon: ImageVector,
    val tag: String
)

/**
 * Phase 7J UI: 5-item bottom nav (Home/Sales/Purchases/Money/Reports) per the UX spec's Section
 * 14. Every other area (Party, Items, Cash/Bank, Outstanding, Profile, Import/OCR, Subscription,
 * Search) is reached through one of these 5 or a top-bar entry point - content dispatch below
 * switches on `uiState.currentRoute`, not just `selectedTab`, since several routes (Profile,
 * Subscription, DataTools, Search) never own a bottom-nav tab of their own.
 */
@Composable
fun MainAppScreen(
    widthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
    viewModel: AccountingViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val canGoBack by viewModel.router.canGoBack.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

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
    var createPartyRole by remember { mutableStateOf<PartyRole?>(null) }
    var isCreateBankUpiOpen by remember { mutableStateOf(false) }
    var pendingImportFormat by remember { mutableStateOf(ImportFileFormat.CSV) }

    LaunchedEffect(Unit) {
        viewModel.snackbarEvents.collectLatest { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    // Storage Access Framework picker for CSV/JSON import - zero new manifest entries needed.
    val openDocumentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                val file = copyUriToCacheFile(context, uri, "import_${System.currentTimeMillis()}")
                if (file != null) viewModel.importFromFile(file, pendingImportFormat)
            }
        }
    }

    // Android Photo Picker for OCR receipt scans - no runtime permission required.
    val receiptPhotoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                val file = copyUriToCacheFile(context, uri, "receipt_${System.currentTimeMillis()}.jpg")
                if (file != null) viewModel.scanReceiptForVoucherDraft(file)
            }
        }
    }

    // Phase 7J UI fix: Android Photo Picker for barcode/QR scans - `scanBarcodeImage` already
    // existed in the ViewModel with no UI entry point before this fix. No runtime permission
    // required (same launcher pattern as the receipt-photo picker above).
    val barcodePhotoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                val file = copyUriToCacheFile(context, uri, "barcode_${System.currentTimeMillis()}.jpg")
                if (file != null) viewModel.scanBarcodeImage(file)
            }
        }
    }

    val navItems = listOf(
        NavItem(NavigationTab.HOME, AppRoute.Dashboard, "Home", Icons.Default.Home, "nav_home"),
        NavItem(NavigationTab.SALES, AppRoute.Sales, "Sales", Icons.Default.Storefront, "nav_sales"),
        NavItem(NavigationTab.PURCHASES, AppRoute.Purchases, "Purchase", Icons.Default.ShoppingCart, "nav_purchases"),
        NavItem(NavigationTab.MONEY, AppRoute.Money, "Money", Icons.Default.AccountBalanceWallet, "nav_money"),
        NavItem(NavigationTab.REPORTS, AppRoute.Reports, "Reports", Icons.Default.Assessment, "nav_reports")
    )

    val adaptiveNavType = getAdaptiveNavigationType(widthSizeClass)
    val useRail = adaptiveNavType == AdaptiveNavigationType.NAVIGATION_RAIL || adaptiveNavType == AdaptiveNavigationType.PERMANENT_NAVIGATION_DRAWER

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpanded = useRail || maxWidth >= Breakpoints.tablet

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
                    onNewCompanyClicked = { isCreateCompanyOpen = true },
                    onSearchClicked = { viewModel.navigateTo(AppRoute.Search()) },
                    onProfileClicked = { viewModel.navigateTo(AppRoute.Profile) },
                    canGoBack = canGoBack,
                    onBack = { viewModel.navigateBack() }
                )
            },
            bottomBar = {
                if (!isExpanded) {
                    Column {
                        // NavigationBar reserves its own bottom system-nav-bar inset (correct,
                        // needed on gesture-nav devices) - on a 3-button-nav device that reserved
                        // strip has no visual boundary from the tappable row above it, so the whole
                        // bottom area reads as one abnormally tall block ("bottom bar too high").
                        // This divider marks where the actual nav bar ends.
                        AppDivider()
                        NavigationBar {
                            navItems.forEach { item ->
                                NavigationBarItem(
                                    selected = uiState.selectedTab == item.tab,
                                    onClick = { viewModel.selectTab(item.tab) },
                                    icon = { Icon(item.icon, contentDescription = item.label) },
                                    label = { Text(item.label, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false) },
                                    modifier = Modifier.testTag(item.tag)
                                )
                            }
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
                if (isExpanded) {
                    NavigationRail(modifier = Modifier.fillMaxHeight()) {
                        navItems.forEach { item ->
                            NavigationRailItem(
                                selected = uiState.selectedTab == item.tab,
                                onClick = { viewModel.selectTab(item.tab) },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false) },
                                modifier = Modifier.testTag(item.tag)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    when (val route = uiState.currentRoute) {
                        is AppRoute.Dashboard -> DashboardScreen(
                            uiState = uiState,
                            onOpenCreateVoucher = { type -> createVoucherType = type; isCreateVoucherOpen = true },
                            onVoucherClick = { selectedVoucherDetail = it },
                            onViewAllDayBook = { viewModel.navigateTo(AppRoute.DayBook) },
                            onViewReports = { viewModel.selectTab(NavigationTab.REPORTS) },
                            onOpenCash = { viewModel.selectTab(NavigationTab.MONEY) },
                            onOpenBank = { viewModel.selectTab(NavigationTab.MONEY) },
                            onOpenSales = { viewModel.selectTab(NavigationTab.SALES) },
                            onOpenPurchases = { viewModel.selectTab(NavigationTab.PURCHASES) },
                            onAddCustomer = { createPartyRole = PartyRole.CUSTOMER },
                            onAddSupplier = { createPartyRole = PartyRole.SUPPLIER },
                            onAddItem = { isCreateStockItemOpen = true }
                        )

                        is AppRoute.DayBook -> DayBookScreen(
                            uiState = uiState,
                            onVoucherClick = { selectedVoucherDetail = it },
                            onOpenCreateVoucher = { type -> createVoucherType = type; isCreateVoucherOpen = true },
                            onFilterTypeSelected = { viewModel.setVoucherTypeFilter(it) },
                            onSearchQueryChanged = { viewModel.setSearchQuery(it) }
                        )

                        is AppRoute.ChartOfAccounts, is AppRoute.LedgerStatement -> ChartOfAccountsScreen(
                            uiState = uiState,
                            onLedgerClick = { ledger -> viewModel.loadLedgerStatement(ledger) },
                            onBackFromStatement = { viewModel.clearLedgerStatement() },
                            onOpenCreateLedger = { isCreateLedgerOpen = true },
                            onOpenCreateStockItem = { isCreateStockItemOpen = true },
                            onDeleteLedger = { ledger -> viewModel.deleteLedgerSafely(ledger.ledgerId) },
                            showItemsTab = isInventoryEnabled(uiState),
                            onGenerateBarcode = { itemId -> viewModel.generateBarcodeForItem(itemId) },
                            onScanBarcode = {
                                barcodePhotoPickerLauncher.launch(
                                    androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            onOpenAccountingSetup = { viewModel.navigateTo(AppRoute.SettingsAndSync) }
                        )

                        is AppRoute.Reports -> ReportsCenterScreen(
                            uiState = uiState,
                            onOpenDayBook = { viewModel.navigateTo(AppRoute.DayBook) },
                            onOpenAllLedgers = { viewModel.navigateTo(AppRoute.ChartOfAccounts) },
                            onExportReport = { reportKey ->
                                coroutineScope.launch {
                                    val intent = viewModel.exportReportAndShare(reportKey)
                                    if (intent != null) context.startActivity(intent)
                                }
                            },
                            onShareReport = { reportKey ->
                                val text = viewModel.buildReportShareText(reportKey)
                                if (text != null) {
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, text)
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Share report"))
                                }
                            }
                        )

                        is AppRoute.SettingsAndSync -> SettingsAndSyncScreen(
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

                        is AppRoute.Sales -> SalesScreen(
                            vouchers = uiState.vouchers,
                            parties = uiState.parties,
                            ledgers = uiState.ledgers,
                            salesRevenue = uiState.profitAndLoss?.salesRevenue ?: com.example.accounting.core.common.Money.ZERO,
                            receivables = uiState.receivablesReport?.totalOutstanding ?: (uiState.balanceSheet?.sundryDebtors ?: com.example.accounting.core.common.Money.ZERO),
                            onNewSale = { createVoucherType = VoucherType.SALES; isCreateVoucherOpen = true },
                            onNewCreditNote = { createVoucherType = VoucherType.CREDIT_NOTE; isCreateVoucherOpen = true },
                            onVoucherClick = { selectedVoucherDetail = it },
                            onAddCustomer = { createPartyRole = PartyRole.CUSTOMER },
                            onPartyClick = { party -> onPartySelected(party, uiState.ledgers, viewModel) }
                        )

                        is AppRoute.Purchases -> PurchasesScreen(
                            vouchers = uiState.vouchers,
                            parties = uiState.parties,
                            ledgers = uiState.ledgers,
                            onNewPurchase = { createVoucherType = VoucherType.PURCHASE; isCreateVoucherOpen = true },
                            onVoucherClick = { selectedVoucherDetail = it },
                            onAddSupplier = { createPartyRole = PartyRole.SUPPLIER },
                            onPartyClick = { party -> onPartySelected(party, uiState.ledgers, viewModel) }
                        )

                        is AppRoute.Money -> MoneyTabContent(
                            uiState = uiState,
                            onOpenCreateVoucher = { type -> createVoucherType = type; isCreateVoucherOpen = true },
                            onLedgerClick = { ledger -> viewModel.loadLedgerStatement(ledger); viewModel.navigateTo(AppRoute.ChartOfAccounts) },
                            onAddBankUpiProfile = { isCreateBankUpiOpen = true },
                            onDeleteBankUpiProfile = { viewModel.deleteBankUpiProfile(it) },
                            onSaveDraftLines = { draft, lines -> viewModel.editVoucherDraftLines(draft, lines) },
                            onPostDraft = { viewModel.postVoucherDraft(it) },
                            onDiscardDraft = { viewModel.discardVoucherDraft(it) },
                            onSubmitMoneyVoucher = { type, date, debitId, creditId, amount, narration, ref, roundOff ->
                                viewModel.postQuickVoucherWithRoundOff(type, date, debitId, creditId, amount, narration, ref, roundOff)
                            },
                            onAddParty = { role -> createPartyRole = role }
                        )

                        is AppRoute.Parties -> PartiesScreen(
                            role = if (route.role == "SUPPLIER") PartyRole.SUPPLIER else PartyRole.CUSTOMER,
                            parties = uiState.parties,
                            ledgers = uiState.ledgers,
                            onAddParty = { createPartyRole = if (route.role == "SUPPLIER") PartyRole.SUPPLIER else PartyRole.CUSTOMER },
                            onPartyClick = { party -> onPartySelected(party, uiState.ledgers, viewModel) }
                        )

                        is AppRoute.Profile -> ProfileScreen(
                            businessProfile = uiState.businessProfile,
                            individualProfile = uiState.individualProfile,
                            onSaveBusinessProfile = { bn, ln, addr, ph, em, gst, pan -> viewModel.updateBusinessProfile(bn, ln, addr, ph, em, gst, pan) },
                            onSaveIndividualProfile = { name, addr, ph, em, pan -> viewModel.updateIndividualProfile(name, addr, ph, em, pan) },
                            onOpenImportData = { viewModel.navigateTo(AppRoute.DataTools) },
                            onOpenSubscription = { viewModel.navigateTo(AppRoute.Subscription) },
                            onOpenCompanyAndSync = { viewModel.navigateTo(AppRoute.SettingsAndSync) }
                        )

                        is AppRoute.Subscription -> SubscriptionScreen(
                            subscription = uiState.currentSubscription,
                            onUpgradeOrRenew = { plan, name, entitlements -> viewModel.upgradeOrRenewSubscription(plan, name, entitlements) }
                        )

                        is AppRoute.DataTools -> DataToolsScreen(
                            lastImportResult = uiState.lastImportResult,
                            lastImportRowOutcomes = uiState.lastImportRowOutcomes,
                            onPickCsvFile = { pendingImportFormat = ImportFileFormat.CSV; openDocumentLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "*/*")) },
                            onPickJsonFile = { pendingImportFormat = ImportFileFormat.JSON; openDocumentLauncher.launch(arrayOf("application/json", "*/*")) },
                            onReviewAndCreateRow = { suggestion, type -> viewModel.reviewAndCreateImportRow(suggestion, type) },
                            onPickReceiptPhoto = {
                                receiptPhotoPickerLauncher.launch(
                                    androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        )

                        is AppRoute.Search -> SearchScreen(
                            initialQuery = route.query,
                            parties = uiState.parties,
                            ledgers = uiState.ledgers,
                            vouchers = uiState.vouchers,
                            stockItems = uiState.stockItems,
                            onBack = { viewModel.navigateBack() },
                            onPartyClick = { party -> onPartySelected(party, uiState.ledgers, viewModel) },
                            onLedgerClick = { ledger -> viewModel.loadLedgerStatement(ledger); viewModel.navigateTo(AppRoute.ChartOfAccounts) },
                            onVoucherClick = { selectedVoucherDetail = it }
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

    createPartyRole?.let { role ->
        CreatePartyDialog(
            role = role,
            onDismiss = { createPartyRole = null },
            onCreateParty = { displayName, r, entityType, gstin, phone, email, address, stateCode, gstRegistrationStatus ->
                viewModel.createParty(displayName, r, entityType, gstin, phone, email, address, stateCode, gstRegistrationStatus)
            }
        )
    }

    if (isCreateBankUpiOpen) {
        CreateBankUpiProfileDialog(
            onDismiss = { isCreateBankUpiOpen = false },
            onCreate = { bankName, holder, accNum, ifsc, branch, upiId, upiPayee ->
                viewModel.createBankUpiProfile(bankName, holder, accNum, ifsc, branch, upiId, upiPayee)
            }
        )
    }

    uiState.lastBarcodeGeneration?.let { generated ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { viewModel.clearBarcodeState() },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { viewModel.clearBarcodeState() }) { Text("Close") }
            },
            title = { Text("Item Barcode") },
            text = { Text(generated.payload.rawValue, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace) }
        )
    }

    // Phase 7J UI fix: was computed by the ViewModel but never displayed anywhere - a scan
    // silently produced a result no one could see. matchedStockItemId is only ever a suggestion
    // (per QrBarcodeAdapter's own contract) - never auto-selected into anything.
    uiState.lastBarcodeScan?.let { scan ->
        val matchedItem = uiState.stockItems.firstOrNull { it.itemId == scan.matchedStockItemId }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { viewModel.clearBarcodeState() },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { viewModel.clearBarcodeState() }) { Text("Close") }
            },
            title = { Text("Barcode Scan") },
            text = {
                if (matchedItem != null) {
                    Text("Matched: ${matchedItem.name}")
                } else {
                    Text("No matching item found for this barcode. You can add it as a new item.")
                }
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

/** Tapping a Party navigates to its linked Ledger's statement - reuses the existing
 * `loadLedgerStatement`/`ChartOfAccountsScreen` machinery verbatim, never a second statement view
 * (Party is a thin Ledger extension, Phase 7A - this is the same underlying account). */
private fun onPartySelected(party: Party, ledgers: List<Ledger>, viewModel: AccountingViewModel) {
    val ledger = ledgers.find { it.ledgerId == party.ledgerId } ?: return
    viewModel.loadLedgerStatement(ledger)
    viewModel.navigateTo(AppRoute.ChartOfAccounts)
}

/** Copies a picked SAF/Photo-Picker [android.net.Uri] into an app-private cache [File] - both
 * `DataImportManagementService.parseFile`/`OcrSuggestionService.requestExtraction` need a real
 * [com.example.accounting.domain.rendering.DocumentAsset] backed by a real file path, matching the
 * existing `AccountingRepository.createDocumentAsset` convention used elsewhere in this app. */
private fun copyUriToCacheFile(context: android.content.Context, uri: android.net.Uri, fileName: String): File? {
    return try {
        val file = File(context.cacheDir, fileName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        file
    } catch (e: Exception) {
        null
    }
}
