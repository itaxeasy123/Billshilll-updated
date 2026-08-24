package com.example.accounting.presentation.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.accounting.automation.scheduler.AccountingScheduler
import com.example.accounting.automation.scheduler.SchedulerPort
import com.example.accounting.automation.scheduler.work.WorkManagerSchedulerPort
import com.example.accounting.application.automation.RecurringVoucherManagementService
import com.example.accounting.application.banking.BankUpiProfileService
import com.example.accounting.application.banking.CashBankLedgerService
import com.example.accounting.application.document.DocumentPreviewService
import com.example.accounting.application.export.ExportManagementService
import com.example.accounting.application.imports.DataImportManagementService
import com.example.accounting.application.invoice.InvoiceManagementServiceImpl
import com.example.accounting.application.ledger.LedgerManagementService
import com.example.accounting.application.ocr.OcrSuggestionService
import com.example.accounting.application.party.PartyManagementService
import com.example.accounting.application.profession.BusinessProfessionService
import com.example.accounting.application.profile.ProfileApplicationService
import com.example.accounting.application.qrbarcode.QrBarcodeManagementService
import com.example.accounting.application.reports.HsnSacSummaryRow
import com.example.accounting.application.reports.ReportManagementService
import com.example.accounting.application.settlement.SettlementManagementService
import com.example.accounting.application.subscription.SubscriptionManagementService
import com.example.accounting.application.voucher.VoucherDraft
import com.example.accounting.application.voucher.VoucherDraftStatus
import com.example.accounting.application.voucher.VoucherManagementServiceImpl
import com.example.accounting.core.common.AccountingResult
import com.example.accounting.core.common.DrCr
import com.example.accounting.core.common.Money
import com.example.accounting.core.database.AppDatabase
import com.example.accounting.core.network.NetworkMonitor
import com.example.accounting.core.sync.OutboxProcessor
import com.example.accounting.data.dataimport.CsvJsonDataImportAdapter
import com.example.accounting.data.local.entity.OutboxSyncEntity
import com.example.accounting.data.qrbarcode.ZxingQrBarcodeAdapter
import com.example.accounting.data.repository.AccountingRepository
import com.example.accounting.domain.accounting.AccountGroup
import com.example.accounting.domain.accounting.Branch
import com.example.accounting.domain.accounting.JournalItem
import com.example.accounting.domain.accounting.Ledger
import com.example.accounting.domain.accounting.Voucher
import com.example.accounting.domain.accounting.VoucherType
import com.example.accounting.domain.audit.AuditLog
import com.example.accounting.domain.banking.BankUpiProfile
import com.example.accounting.domain.banking.UpiMetadata
import com.example.accounting.domain.company.Company
import com.example.accounting.domain.dataimport.ImportFileFormat
import com.example.accounting.domain.dataimport.ImportResult
import com.example.accounting.domain.dataimport.ImportRowSuggestion
import com.example.accounting.domain.dataimport.ImportSuggestionType
import com.example.accounting.domain.financialyear.AccountingPeriod
import com.example.accounting.domain.financialyear.FinancialYear
import com.example.accounting.domain.inventory.StockItem
import com.example.accounting.domain.invoice.Invoice
import com.example.accounting.domain.ocr.OcrExtractionResult
import com.example.accounting.domain.party.Party
import com.example.accounting.domain.party.PartyEntityType
import com.example.accounting.domain.party.PartyRole
import com.example.accounting.domain.qrbarcode.BarcodeGenerationResult
import com.example.accounting.domain.qrbarcode.BarcodeScanSuggestion
import com.example.accounting.domain.reports.BalanceSheetReport
import com.example.accounting.domain.reports.CashFlowReport
import com.example.accounting.domain.reports.GSTSummaryReport
import com.example.accounting.domain.reports.LedgerStatementReport
import com.example.accounting.domain.reports.OutstandingReport
import com.example.accounting.domain.reports.ProfitAndLossReport
import com.example.accounting.domain.reports.RatioAnalysisReport
import com.example.accounting.domain.reports.TrialBalanceReport
import com.example.accounting.domain.rendering.BusinessProfile
import com.example.accounting.domain.rendering.DocumentAssetType
import com.example.accounting.domain.rendering.IndividualProfile
import com.example.accounting.domain.subscription.CompanySubscription
import com.example.accounting.domain.subscription.EntitlementFeature
import com.example.accounting.domain.subscription.SubscriptionPlanType
import com.example.accounting.domain.taxation.gst.GSTRules
import com.example.accounting.domain.taxation.gst.GstCalculationEngine
import com.example.accounting.domain.taxation.gst.GstDirection
import com.example.accounting.domain.taxation.gst.GstFilingPeriod
import com.example.accounting.domain.taxation.gst.GstLedgerIds
import com.example.accounting.domain.taxation.gst.SupplyType
import com.example.accounting.domain.trading.OutstandingInvoice
import com.example.accounting.domain.trading.TradingLineInput
import com.example.accounting.domain.trading.TradingWorkflowEngine
import com.example.accounting.presentation.navigation.AppRoute
import com.example.accounting.presentation.navigation.HashRouter
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.util.UUID

/** Phase 7J UI: exactly 5 top-level destinations (Home/Sales/Purchases/Money/Reports), per the
 * user-supplied UX spec's bottom-navigation section. Every other area (Party, Items, Cash/Bank,
 * Outstanding, Profile, Import/OCR, Subscription, Search) is reached through one of these 5 or a
 * top-bar entry point - never a 6th+ tab. */
enum class NavigationTab {
    HOME,
    SALES,
    PURCHASES,
    MONEY,
    REPORTS
}

data class AccountingUiState(
    val selectedTab: NavigationTab = NavigationTab.HOME,
    val currentRoute: AppRoute = AppRoute.Dashboard,
    val companies: List<Company> = emptyList(),
    val currentCompany: Company? = null,
    val branches: List<Branch> = emptyList(),
    val financialYears: List<FinancialYear> = emptyList(),
    val currentFinancialYear: FinancialYear? = null,
    val periods: List<AccountingPeriod> = emptyList(),
    val groups: List<AccountGroup> = emptyList(),
    val ledgers: List<Ledger> = emptyList(),
    val vouchers: List<Voucher> = emptyList(),
    val auditLogs: List<AuditLog> = emptyList(),
    val outboxQueue: List<OutboxSyncEntity> = emptyList(),
    val pendingSyncCount: Int = 0,
    val isOnline: Boolean = true,
    val isSyncing: Boolean = false,
    val trialBalance: TrialBalanceReport? = null,
    val profitAndLoss: ProfitAndLossReport? = null,
    val incomeAndExpenditure: com.example.accounting.domain.reports.IncomeExpenditureReport? = null,
    val balanceSheet: BalanceSheetReport? = null,
    val gstSummary: GSTSummaryReport? = null,
    val selectedLedgerStatement: LedgerStatementReport? = null,
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val selectedVoucherTypeFilter: VoucherType? = null,
    val stockItems: List<StockItem> = emptyList(),
    val outstandingInvoices: List<OutstandingInvoice> = emptyList(),
    val gstFilingPeriods: List<GstFilingPeriod> = emptyList(),
    val isCloudSyncLoggedIn: Boolean = false,

    // ==== Phase 7J UI additions ====
    val parties: List<Party> = emptyList(),
    val invoices: List<Invoice> = emptyList(),
    val bankUpiProfiles: List<BankUpiProfile> = emptyList(),
    val businessProfile: BusinessProfile? = null,
    val individualProfile: IndividualProfile? = null,
    val currentSubscription: CompanySubscription? = null,
    val voucherDraftsPendingReview: List<VoucherDraft> = emptyList(),
    val outstandingReport: OutstandingReport? = null,
    val receivablesReport: OutstandingReport? = null,
    val payablesReport: OutstandingReport? = null,
    val cashFlowReport: CashFlowReport? = null,
    val ratioAnalysisReport: RatioAnalysisReport? = null,
    val hsnSacSummary: List<HsnSacSummaryRow> = emptyList(),
    val lastImportResult: ImportResult? = null,
    val lastImportRowOutcomes: Map<Int, String> = emptyMap(),
    val lastOcrExtraction: OcrExtractionResult? = null,
    val lastBarcodeGeneration: BarcodeGenerationResult? = null,
    val lastBarcodeScan: BarcodeScanSuggestion? = null
)

class AccountingViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val repository: AccountingRepository = AccountingRepository(
        dao = db.accountingDao(),
        db = db
    )

    val router = HashRouter()
    val networkMonitor = NetworkMonitor(application)
    private val outboxProcessor = OutboxProcessor(application, db.accountingDao())
    val scheduler = AccountingScheduler(db.accountingDao(), repository, outboxProcessor)
    private val schedulerPort: SchedulerPort = WorkManagerSchedulerPort(application)
    private val authRepository = com.example.accounting.core.network.AuthRepository(application)

    // ==== Phase 7J UI: application-service layer (Phase 7J-B, frozen) - every new screen calls
    // through these, never AccountingDao/AccountingRepository directly, except where a thin
    // facade doesn't yet exist for an already-established repository call (matching this
    // ViewModel's own pre-existing pattern, e.g. postQuickVoucher/postTradingDocument). ====
    private val partyService = PartyManagementService(repository)
    private val ledgerService = LedgerManagementService(repository)
    private val voucherDraftService = VoucherManagementServiceImpl(db.accountingDao(), repository)
    private val invoiceService = InvoiceManagementServiceImpl(repository)
    private val settlementService = SettlementManagementService(repository)
    private val reportService = ReportManagementService(repository)
    private val cashBankService = CashBankLedgerService(repository)
    private val bankUpiService = BankUpiProfileService(db.accountingDao())
    private val subscriptionService = SubscriptionManagementService(db.accountingDao())
    private val profileService = ProfileApplicationService(repository)
    private val documentPreviewService = DocumentPreviewService(repository)
    private val exportService = ExportManagementService(repository)
    private val recurringVoucherService = RecurringVoucherManagementService(repository)
    private val businessProfessionService = BusinessProfessionService()
    private val dataImportService = DataImportManagementService(CsvJsonDataImportAdapter(db.accountingDao()), repository)
    private val qrBarcodeService = QrBarcodeManagementService(repository, ZxingQrBarcodeAdapter(db.accountingDao()))
    // OcrIngestionAdapter is deliberately left unimplemented (Phase 7J-B) - null adapter here
    // means requestExtraction always fails gracefully, never a crash. Not a bug to fix.
    private val ocrService = OcrSuggestionService(null, db.accountingDao())

    private val _uiState = MutableStateFlow(AccountingUiState(isCloudSyncLoggedIn = authRepository.isLoggedIn()))
    val uiState: StateFlow<AccountingUiState> = _uiState.asStateFlow()

    private val _snackbarEvents = MutableSharedFlow<String>()
    val snackbarEvents: SharedFlow<String> = _snackbarEvents.asSharedFlow()

    private var companyDataJob: Job? = null
    private var fyDataJob: Job? = null

    init {
        viewModelScope.launch {
            try {
                repository.initializeDatabaseIfNeeded()
            } catch (e: Throwable) {
                android.util.Log.e("AccountingViewModel", "Init DB error", e)
            }
            loadCompaniesAndInitialData()
        }

        viewModelScope.launch {
            networkMonitor.isOnline.collect { online ->
                _uiState.update { it.copy(isOnline = online) }
            }
        }

        viewModelScope.launch {
            com.example.accounting.automation.notifications.AutomationNotificationCenter.notifications.collect { notif ->
                emitMessage("${notif.title}: ${notif.message}")
            }
        }

        viewModelScope.launch {
            router.currentRoute.collect { route ->
                _uiState.update { it.copy(currentRoute = route) }
                when (route) {
                    is AppRoute.Dashboard -> _uiState.update { it.copy(selectedTab = NavigationTab.HOME) }
                    is AppRoute.DayBook -> _uiState.update { it.copy(selectedTab = NavigationTab.HOME) }
                    is AppRoute.ChartOfAccounts -> _uiState.update { it.copy(selectedTab = NavigationTab.HOME) }
                    is AppRoute.Sales -> _uiState.update { it.copy(selectedTab = NavigationTab.SALES) }
                    is AppRoute.Purchases -> _uiState.update { it.copy(selectedTab = NavigationTab.PURCHASES) }
                    is AppRoute.Money -> _uiState.update { it.copy(selectedTab = NavigationTab.MONEY) }
                    is AppRoute.Reports -> {
                        _uiState.update { it.copy(selectedTab = NavigationTab.REPORTS) }
                        refreshFinancialReports()
                        refreshReportsCenterExtras()
                    }
                    is AppRoute.SettingsAndSync -> { /* reached from Profile - keep whatever tab was active */ }
                    is AppRoute.LedgerStatement -> { /* keep whatever tab was active (Home or Reports) */ }
                    is AppRoute.Parties -> { /* keep whatever tab was active (Sales or Purchases) */ }
                    is AppRoute.Profile -> { /* top-bar entry point - keep whatever tab was active */ }
                    is AppRoute.Subscription -> { loadSubscription() }
                    is AppRoute.DataTools -> { /* reached from Profile - keep whatever tab was active */ }
                    is AppRoute.Search -> { /* top-bar entry point - keep whatever tab was active */ }
                }
            }
        }
    }

    private fun loadCompaniesAndInitialData() {
        viewModelScope.launch {
            repository.getCompanies().collect { companiesList ->
                val currentComp = _uiState.value.currentCompany
                    ?: companiesList.find { it.isDefault }
                    ?: companiesList.firstOrNull()

                _uiState.update { it.copy(companies = companiesList, currentCompany = currentComp) }

                currentComp?.let { comp ->
                    observeCompanyData(comp.companyId)
                }
            }
        }
    }

    private fun observeCompanyData(companyId: String) {
        companyDataJob?.cancel()
        companyDataJob = viewModelScope.launch {
            launch {
                repository.getFinancialYears(companyId).collect { fyList ->
                    val currentFy = _uiState.value.currentFinancialYear
                        ?: fyList.find { it.isCurrent }
                        ?: fyList.firstOrNull()

                    _uiState.update { it.copy(financialYears = fyList, currentFinancialYear = currentFy) }

                    currentFy?.let { fy ->
                        observeFinancialYearData(companyId, fy.financialYearId)
                    }
                }
            }

            launch {
                repository.getBranches(companyId).collect { branchesList ->
                    _uiState.update { it.copy(branches = branchesList) }
                }
            }

            launch {
                repository.getGroups(companyId).collect { groupsList ->
                    _uiState.update { it.copy(groups = groupsList) }
                }
            }

            launch {
                repository.getLedgers(companyId).collect { ledgersList ->
                    _uiState.update { it.copy(ledgers = ledgersList) }
                    refreshFinancialReports()
                }
            }

            launch {
                repository.getAuditLogs(companyId).collect { logs ->
                    _uiState.update { it.copy(auditLogs = logs) }
                }
            }

            launch {
                repository.getOutboxQueue(companyId).collect { queue ->
                    _uiState.update { it.copy(outboxQueue = queue) }
                }
            }

            launch {
                repository.getPendingSyncCount(companyId).collect { count ->
                    _uiState.update { it.copy(pendingSyncCount = count) }
                }
            }

            launch {
                repository.getStockItems(companyId).collect { items ->
                    _uiState.update { it.copy(stockItems = items) }
                }
            }

            launch {
                repository.getGstFilingPeriods(companyId).collect { periods ->
                    _uiState.update { it.copy(gstFilingPeriods = periods) }
                }
            }

            // ==== Phase 7J UI additions ====
            launch {
                partyService.getParties(companyId).collect { parties ->
                    _uiState.update { it.copy(parties = parties) }
                }
            }

            launch {
                repository.getInvoicesForCompany(companyId).collect { invoices ->
                    _uiState.update { it.copy(invoices = invoices) }
                }
            }

            launch {
                bankUpiService.list(companyId).collect { profiles ->
                    _uiState.update { it.copy(bankUpiProfiles = profiles) }
                }
            }

            launch {
                voucherDraftService.listDrafts(companyId, VoucherDraftStatus.PENDING_REVIEW).collect { drafts ->
                    _uiState.update { it.copy(voucherDraftsPendingReview = drafts) }
                }
            }

            launch {
                _uiState.update { it.copy(businessProfile = profileService.getBusinessProfile(companyId), individualProfile = profileService.getIndividualProfile(companyId)) }
            }
        }
    }

    private fun observeFinancialYearData(companyId: String, fyId: String) {
        schedulerPort.scheduleRecurringAutomation(companyId, fyId)
        fyDataJob?.cancel()
        fyDataJob = viewModelScope.launch {
            launch {
                repository.getPeriods(fyId).collect { periodList ->
                    _uiState.update { it.copy(periods = periodList) }
                }
            }

            launch {
                repository.getVouchers(companyId, fyId).collect { vouchersList ->
                    _uiState.update { it.copy(vouchers = vouchersList, isLoading = false) }
                    refreshFinancialReports()
                }
            }
        }
    }

    fun selectTab(tab: NavigationTab) {
        val destination = when (tab) {
            NavigationTab.HOME -> AppRoute.Dashboard
            NavigationTab.SALES -> AppRoute.Sales
            NavigationTab.PURCHASES -> AppRoute.Purchases
            NavigationTab.MONEY -> AppRoute.Money
            NavigationTab.REPORTS -> AppRoute.Reports
        }
        router.navigate(destination)
    }

    fun navigateTo(route: AppRoute) {
        router.navigate(route)
    }

    fun navigateBack(): Boolean {
        return router.goBack()
    }

    fun setVoucherTypeFilter(type: VoucherType?) {
        _uiState.update { it.copy(selectedVoucherTypeFilter = type) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun switchCompany(company: Company) {
        _uiState.update { it.copy(currentCompany = company, isLoading = true) }
        observeCompanyData(company.companyId)
        emitMessage("Switched active company context to: ${company.name}")
    }

    fun switchFinancialYear(fy: FinancialYear) {
        val compId = _uiState.value.currentCompany?.companyId ?: return
        _uiState.update { it.copy(currentFinancialYear = fy) }
        observeFinancialYearData(compId, fy.financialYearId)
        emitMessage("Switched accounting boundary to: ${fy.fyCode}")
    }

    fun togglePeriodLock(period: AccountingPeriod) {
        viewModelScope.launch {
            val shouldLock = period.status == com.example.accounting.domain.financialyear.PeriodStatus.OPEN
            val result = repository.setPeriodLock(period.periodId, shouldLock, "SENIOR_CONTROLLER")
            if (result is AccountingResult.Success) {
                emitMessage("${if (shouldLock) "Locked" else "Unlocked"} accounting period '${period.name}'")
                val compId = _uiState.value.currentCompany?.companyId ?: ""
                scheduler.dispatchEvent(
                    com.example.accounting.automation.jobs.AutomationEvent.PeriodLocked(compId, period.name)
                )
            } else {
                emitMessage("Failed to update period lock: ${result.errorOrNull()?.message}")
            }
        }
    }

    /**
     * Switches the active company's [AccountingMode]/[BusinessType] - a capability toggle only
     * (Phase 4.5): never deletes or hides underlying vouchers/stock movements/GST records/audit
     * history, it only changes which report figures/UI capabilities are active. The `companies`
     * Flow collector already active in this ViewModel picks the change up automatically.
     */
    fun updateAccountingConfiguration(
        accountingMode: com.example.accounting.domain.company.AccountingMode? = null,
        businessType: com.example.accounting.domain.company.BusinessType? = null
    ) {
        viewModelScope.launch {
            val compId = _uiState.value.currentCompany?.companyId ?: return@launch
            val result = repository.updateAccountingConfiguration(compId, accountingMode, businessType, "ADMIN")
            if (result is AccountingResult.Success) {
                emitMessage("Accounting configuration updated")
                refreshFinancialReports()
            } else {
                emitMessage("Failed to update accounting configuration: ${result.errorOrNull()?.message}")
            }
        }
    }

    /** Optional Cloud Sync login (Phase 6, Priority 6.7/6.10) - sync-gated, never app-gated; the
     * app already works fully offline before this is ever called. */
    fun loginCloudSync(email: String, password: String) {
        viewModelScope.launch {
            val result = authRepository.login(email, password)
            if (result.isSuccess) {
                _uiState.update { it.copy(isCloudSyncLoggedIn = true) }
                emitMessage("Signed in - cloud sync enabled")
            } else {
                emitMessage("Sign-in failed: ${result.exceptionOrNull()?.message ?: "Unknown error"}")
            }
        }
    }

    fun logoutCloudSync() {
        viewModelScope.launch {
            authRepository.logout()
            _uiState.update { it.copy(isCloudSyncLoggedIn = false) }
            emitMessage("Signed out of cloud sync")
        }
    }

    fun createCompany(
        name: String,
        tradeName: String,
        gstin: String,
        pan: String,
        stateCode: String,
        address: String,
        email: String,
        phone: String
    ) {
        viewModelScope.launch {
            val newCompId = "COMP_${UUID.randomUUID().toString().take(8).uppercase()}"
            val newCompany = Company(
                companyId = newCompId,
                name = name,
                tradeName = tradeName,
                gstin = gstin,
                pan = pan,
                stateCode = stateCode,
                address = address,
                email = email,
                phone = phone,
                currency = "INR",
                isDefault = false
            )
            val result = repository.createCompany(newCompany)
            if (result is AccountingResult.Success) {
                switchCompany(newCompany)
                emitMessage("Created company '${newCompany.name}' with isolated Chart of Accounts")
            } else {
                emitMessage("Error creating company: ${result.errorOrNull()?.message}")
            }
        }
    }

    fun createBranch(
        code: String,
        name: String,
        gstin: String,
        stateCode: String,
        address: String,
        isHeadOffice: Boolean
    ) {
        viewModelScope.launch {
            val comp = _uiState.value.currentCompany ?: return@launch
            val branch = Branch(
                branchId = "BR_${UUID.randomUUID().toString().take(8).uppercase()}_${comp.companyId}",
                companyId = comp.companyId,
                code = code,
                name = name,
                gstin = gstin,
                stateCode = stateCode,
                address = address,
                isHeadOffice = isHeadOffice,
                isActive = true
            )
            val result = repository.createBranch(branch)
            if (result is AccountingResult.Success) {
                emitMessage("Branch '${branch.name}' (${branch.code}) created successfully")
            } else {
                emitMessage("Failed to create branch: ${result.errorOrNull()?.message}")
            }
        }
    }

    fun createLedger(
        name: String,
        groupId: String,
        openingBalance: Money,
        openingType: DrCr,
        gstin: String = "",
        pan: String = "",
        phone: String = "",
        email: String = "",
        address: String = "",
        hsnSac: String = "",
        defaultTaxRate: Double = 0.0
    ) {
        viewModelScope.launch {
            val comp = _uiState.value.currentCompany ?: return@launch
            val ledger = Ledger(
                ledgerId = "LED_${UUID.randomUUID().toString().take(8).uppercase()}_${comp.companyId}",
                companyId = comp.companyId,
                groupId = groupId,
                name = name,
                openingBalance = openingBalance,
                openingBalanceType = openingType,
                currentBalance = openingBalance,
                currentBalanceType = openingType,
                gstin = gstin,
                pan = pan,
                stateCode = comp.stateCode,
                phone = phone,
                email = email,
                address = address,
                hsnSacCode = hsnSac,
                defaultTaxRate = defaultTaxRate
            )
            val result = repository.createLedger(ledger)
            if (result is AccountingResult.Success) {
                emitMessage("Ledger account '${ledger.name}' created successfully")
                scheduler.dispatchEvent(
                    com.example.accounting.automation.jobs.AutomationEvent.LedgerCreated(comp.companyId, ledger)
                )
            } else {
                emitMessage("Failed to create ledger: ${result.errorOrNull()?.message}")
            }
        }
    }

    /** Creates a stock item with its HSN/SAC and GST rate on file - the prerequisite for
     * item-driven GST (Phase 5, Priority 3): the Sale/Purchase item picker reads these facts back
     * instead of the user typing/picking a rate freely. */
    fun createStockItem(
        name: String,
        sku: String,
        hsnCode: String,
        unit: String,
        gstRatePercent: Double,
        openingQuantity: Double,
        openingRate: Money
    ) {
        viewModelScope.launch {
            val comp = _uiState.value.currentCompany ?: return@launch
            val item = StockItem(
                itemId = "", companyId = comp.companyId, name = name, sku = sku, hsnCode = hsnCode, unit = unit,
                gstRatePercent = gstRatePercent,
                openingQuantity = com.example.accounting.core.common.Quantity.fromDouble(openingQuantity, unit),
                openingRate = openingRate
            )
            val result = repository.createStockItem(item)
            if (result is AccountingResult.Success) {
                emitMessage("Item '${item.name}' created successfully")
            } else {
                emitMessage("Failed to create item: ${result.errorOrNull()?.message}")
            }
        }
    }

    fun deleteLedgerSafely(ledgerId: String) {
        viewModelScope.launch {
            val comp = _uiState.value.currentCompany ?: return@launch
            val result = repository.deleteLedgerSafely(comp.companyId, ledgerId, "SENIOR_ACCOUNTANT")
            if (result is AccountingResult.Success) {
                emitMessage("Ledger deleted successfully")
            } else {
                emitMessage("Delete rejected: ${result.errorOrNull()?.message}")
            }
        }
    }

    fun deleteVoucherSafely(voucherId: String) {
        viewModelScope.launch {
            val comp = _uiState.value.currentCompany ?: return@launch
            val fy = _uiState.value.currentFinancialYear ?: return@launch
            val idempotencyKey = UUID.randomUUID().toString()
            val result = repository.deleteVoucherSafely(comp.companyId, fy.financialYearId, voucherId, idempotencyKey, "SENIOR_ACCOUNTANT")
            if (result is AccountingResult.Success) {
                emitMessage("Voucher cancelled and ledger balances reversed successfully")
                scheduler.dispatchEvent(
                    com.example.accounting.automation.jobs.AutomationEvent.VoucherDeleted(comp.companyId, voucherId, voucherId)
                )
                refreshFinancialReports()
            } else {
                emitMessage("Voucher deletion failed: ${result.errorOrNull()?.message}")
            }
        }
    }

    fun postQuickVoucher(
        voucherType: VoucherType,
        date: LocalDate,
        debitLedgerId: String,
        creditLedgerId: String,
        amount: Money,
        narration: String,
        refNumber: String = "",
        paymentMode: String = "",
        /** Receipt/Payment invoice allocation (Phase 5, Priority 2) - which outstanding invoice(s)
         * this settlement pays off, and how much. Empty for Contra/Journal/an unallocated advance. */
        allocations: List<Pair<String, Money>> = emptyList()
    ) {
        viewModelScope.launch {
            val comp = _uiState.value.currentCompany ?: return@launch
            val fy = _uiState.value.currentFinancialYear ?: return@launch
            val ledgersMap = _uiState.value.ledgers.associateBy { it.ledgerId }

            val debitLedger = ledgersMap[debitLedgerId]
            val creditLedger = ledgersMap[creditLedgerId]

            if (debitLedger == null || creditLedger == null) {
                emitMessage("Validation error: Invalid debit or credit ledger selected.")
                return@launch
            }

            val voucherId = "VCH_${voucherType.code}_${UUID.randomUUID().toString().take(8).uppercase()}"
            val voucherNumber = repository.generateNextVoucherNumber(comp.companyId, fy.financialYearId, voucherType)

            val items = listOf(
                JournalItem(
                    itemId = UUID.randomUUID().toString(),
                    voucherId = voucherId,
                    companyId = comp.companyId,
                    financialYearId = fy.financialYearId,
                    ledgerId = debitLedgerId,
                    ledgerName = debitLedger.name,
                    type = DrCr.DEBIT,
                    amount = amount,
                    narration = narration,
                    lineOrder = 1
                ),
                JournalItem(
                    itemId = UUID.randomUUID().toString(),
                    voucherId = voucherId,
                    companyId = comp.companyId,
                    financialYearId = fy.financialYearId,
                    ledgerId = creditLedgerId,
                    ledgerName = creditLedger.name,
                    type = DrCr.CREDIT,
                    amount = amount,
                    narration = narration,
                    lineOrder = 2
                )
            )

            val voucher = Voucher(
                voucherId = voucherId,
                companyId = comp.companyId,
                financialYearId = fy.financialYearId,
                voucherNumber = voucherNumber,
                voucherType = voucherType,
                date = date,
                referenceNumber = refNumber,
                narration = narration,
                totalAmount = amount,
                items = items,
                createdBy = "SENIOR_ACCOUNTANT",
                paymentMode = paymentMode
            )

            val result = repository.postVoucher(voucher)
            when (result) {
                is AccountingResult.Success -> {
                    emitMessage("Voucher $voucherNumber posted successfully (${amount.formatPlain()})")
                    scheduler.dispatchEvent(
                        com.example.accounting.automation.jobs.AutomationEvent.VoucherPosted(comp.companyId, voucher)
                    )
                    // Settlement allocation (Priority 2) - only meaningful for Receipt/Payment, and
                    // only attempted when the caller actually supplied allocations; no GST is
                    // computed or reachable anywhere in this function.
                    if (allocations.isNotEmpty() && (voucherType == VoucherType.RECEIPT || voucherType == VoucherType.PAYMENT)) {
                        val unallocated = amount - allocations.fold(Money.ZERO) { acc, (_, amt) -> acc + amt }
                        val allocResult = repository.allocateSettlement(comp.companyId, fy.financialYearId, voucherId, allocations, unallocated)
                        if (allocResult is AccountingResult.Failure) {
                            emitMessage("Voucher posted, but allocation failed: ${allocResult.error.message}")
                        }
                    }
                }
                is AccountingResult.Failure -> {
                    emitMessage("Posting rejected: ${result.error.message}")
                }
            }
        }
    }

    /** Loads every outstanding Sale/Purchase for [partyLedgerId] into [AccountingUiState.outstandingInvoices]
     * so the Receipt/Payment form can offer them for allocation (Priority 2). */
    fun loadOutstandingInvoices(partyLedgerId: String) {
        viewModelScope.launch {
            val comp = _uiState.value.currentCompany ?: return@launch
            val invoices = repository.getOutstandingInvoices(comp.companyId, partyLedgerId)
            _uiState.update { it.copy(outstandingInvoices = invoices) }
        }
    }

    fun clearOutstandingInvoices() {
        _uiState.update { it.copy(outstandingInvoices = emptyList()) }
    }

    /** One item line as entered in the Sale/Purchase item picker - just the facts the user
     * actually chooses (item, quantity, rate); HSN/GST rate are looked up from the item itself. */
    data class TradingLineForm(val itemId: String, val quantity: Double, val rate: Money)

    fun postSaleInvoice(
        customerLedgerId: String,
        salesLedgerId: String,
        lines: List<TradingLineForm>,
        date: LocalDate,
        referenceNumber: String,
        narration: String
    ) = postTradingDocument(
        isSale = true, partyLedgerId = customerLedgerId, tradeLedgerId = salesLedgerId,
        lines = lines, date = date, referenceNumber = referenceNumber, narration = narration
    )

    fun postPurchaseBill(
        supplierLedgerId: String,
        purchaseLedgerId: String,
        lines: List<TradingLineForm>,
        date: LocalDate,
        referenceNumber: String,
        narration: String
    ) = postTradingDocument(
        isSale = false, partyLedgerId = supplierLedgerId, tradeLedgerId = purchaseLedgerId,
        lines = lines, date = date, referenceNumber = referenceNumber, narration = narration
    )

    /**
     * Item-line Sale/Purchase flow (Phase 5, Priority 1): resolves each line's GST rate/HSN from
     * the selected [StockItem] (never a hardcoded rate), delegates all tax math, stock-line
     * construction, GST-transaction-fact construction, and Round Off to [TradingWorkflowEngine] -
     * the ViewModel only resolves ledgers/items and assembles the resulting [Voucher].
     */
    private fun postTradingDocument(
        isSale: Boolean,
        partyLedgerId: String,
        tradeLedgerId: String,
        lines: List<TradingLineForm>,
        date: LocalDate,
        referenceNumber: String,
        narration: String
    ) {
        viewModelScope.launch {
            val comp = _uiState.value.currentCompany ?: return@launch
            val fy = _uiState.value.currentFinancialYear ?: return@launch
            if (lines.isEmpty()) {
                emitMessage("Validation error: Add at least one item line.")
                return@launch
            }

            // Backfill GST duty ledgers and the Round Off ledger for companies seeded before this
            // structure existed - idempotent, safe every call.
            repository.ensureGstLedgersExist(comp.companyId)
            repository.ensureRoundOffLedgerExists(comp.companyId)

            val ledgers = repository.getLedgers(comp.companyId).first().associateBy { it.ledgerId }
            val stockItems = _uiState.value.stockItems.associateBy { it.itemId }

            val partyLedger = ledgers[partyLedgerId]
            val tradeLedger = ledgers[tradeLedgerId]
            if (partyLedger == null || tradeLedger == null) {
                emitMessage("Validation error: Invalid ${if (isSale) "customer" else "supplier"} or ${if (isSale) "sales" else "purchase"} ledger selected.")
                return@launch
            }

            val tradingLines = lines.map { line ->
                val item = stockItems[line.itemId]
                    ?: return@launch emitMessage("Validation error: Selected item could not be found.")
                TradingLineInput(
                    itemId = item.itemId, itemName = item.name, hsnSacCode = item.hsnCode,
                    quantity = com.example.accounting.core.common.Quantity.fromDouble(line.quantity, item.unit),
                    rate = line.rate, gstRatePercent = item.gstRatePercent
                )
            }

            val voucherType = if (isSale) VoucherType.SALES else VoucherType.PURCHASE
            val voucherId = "VCH_${voucherType.code}_${UUID.randomUUID().toString().take(8).uppercase()}"
            val voucherNumber = repository.generateNextVoucherNumber(comp.companyId, fy.financialYearId, voucherType)
            val gstLedgers = repository.resolveGstLedgerRefs(comp.companyId)
            val roundOffRef = repository.resolveRoundOffLedgerRef(comp.companyId)
            // Place of Supply defaults to the party's own state (Priority 3) - the intra/inter-state
            // split is derived from company-state vs place-of-supply, never a UI toggle.
            val placeOfSupply = partyLedger.stateCode.ifBlank { comp.stateCode }

            val engineResult = if (isSale) {
                TradingWorkflowEngine.buildSale(
                    voucherId = voucherId, companyId = comp.companyId, financialYearId = fy.financialYearId,
                    customerLedgerId = partyLedgerId, customerName = partyLedger.name, customerGstin = partyLedger.gstin,
                    salesLedgerId = tradeLedgerId, salesLedgerName = tradeLedger.name,
                    companyStateCode = comp.stateCode, placeOfSupply = placeOfSupply,
                    lines = tradingLines, gstLedgers = gstLedgers,
                    roundOffLedgerId = roundOffRef.ledgerId, roundOffLedgerName = roundOffRef.name
                )
            } else {
                TradingWorkflowEngine.buildPurchase(
                    voucherId = voucherId, companyId = comp.companyId, financialYearId = fy.financialYearId,
                    supplierLedgerId = partyLedgerId, supplierName = partyLedger.name, supplierGstin = partyLedger.gstin,
                    purchaseLedgerId = tradeLedgerId, purchaseLedgerName = tradeLedger.name,
                    companyStateCode = comp.stateCode, placeOfSupply = placeOfSupply,
                    lines = tradingLines, gstLedgers = gstLedgers,
                    roundOffLedgerId = roundOffRef.ledgerId, roundOffLedgerName = roundOffRef.name
                )
            }

            val voucher = Voucher(
                voucherId = voucherId, companyId = comp.companyId, financialYearId = fy.financialYearId,
                voucherNumber = voucherNumber, voucherType = voucherType, date = date,
                referenceNumber = referenceNumber, narration = narration.ifBlank { "Being ${voucherType.displayName.lowercase()}" },
                totalAmount = engineResult.totalAmount, items = engineResult.journalItems,
                createdBy = if (isSale) "SALES_BILLING_DESK" else "PURCHASE_DESK",
                partyGstin = partyLedger.gstin, isGstApplicable = true
            )

            val result = repository.postVoucher(voucher, stockLines = engineResult.stockLines, gstTransactions = engineResult.gstTransactions)
            when (result) {
                is AccountingResult.Success -> {
                    emitMessage("${voucherType.displayName} $voucherNumber posted successfully: ${engineResult.totalAmount.formatPlain()}")
                    scheduler.dispatchEvent(
                        com.example.accounting.automation.jobs.AutomationEvent.VoucherPosted(comp.companyId, voucher)
                    )
                }
                is AccountingResult.Failure -> {
                    emitMessage("${voucherType.displayName} posting rejected: ${result.error.message}")
                }
            }
        }
    }

    fun postCreditNote(originalSaleVoucherId: String, date: LocalDate, referenceNumber: String, narration: String) =
        postNote(isCredit = true, originalVoucherId = originalSaleVoucherId, date = date, referenceNumber = referenceNumber, narration = narration)

    fun postDebitNote(originalPurchaseVoucherId: String, date: LocalDate, referenceNumber: String, narration: String) =
        postNote(isCredit = false, originalVoucherId = originalPurchaseVoucherId, date = date, referenceNumber = referenceNumber, narration = narration)

    /**
     * Credit Note (against a Sale) / Debit Note (against a Purchase) as a full reversal of the
     * original document (Phase 5, Priority 1) - [TradingWorkflowEngine.buildNote] builds the
     * opposite journal/stock lines and negated GST-transaction rows from the original's own
     * (already-posted, never-modified) data. The original voucher row is only ever READ here.
     */
    private fun postNote(isCredit: Boolean, originalVoucherId: String, date: LocalDate, referenceNumber: String, narration: String) {
        viewModelScope.launch {
            val comp = _uiState.value.currentCompany ?: return@launch
            val fy = _uiState.value.currentFinancialYear ?: return@launch

            val original = repository.getVoucherById(comp.companyId, originalVoucherId)
            if (original == null) {
                emitMessage("Validation error: Original voucher not found.")
                return@launch
            }
            val expectedType = if (isCredit) VoucherType.SALES else VoucherType.PURCHASE
            if (original.voucherType != expectedType) {
                emitMessage("Validation error: A ${if (isCredit) "Credit" else "Debit"} Note must reference a ${expectedType.displayName}.")
                return@launch
            }
            if (original.isCancelled) {
                emitMessage("Validation error: Cannot issue a note against a cancelled voucher.")
                return@launch
            }

            val originalStockLines = repository.getStockLinesForVoucher(comp.companyId, originalVoucherId)
            val originalGstTransactions = repository.getGstTransactionsForVoucher(originalVoucherId)

            val voucherType = if (isCredit) VoucherType.CREDIT_NOTE else VoucherType.DEBIT_NOTE
            val voucherId = "VCH_${voucherType.code}_${UUID.randomUUID().toString().take(8).uppercase()}"
            val voucherNumber = repository.generateNextVoucherNumber(comp.companyId, fy.financialYearId, voucherType)

            val engineResult = TradingWorkflowEngine.buildNote(
                noteVoucherId = voucherId, originalJournalItems = original.items,
                originalStockLines = originalStockLines, originalGstTransactions = originalGstTransactions
            )

            val voucher = Voucher(
                voucherId = voucherId, companyId = comp.companyId, financialYearId = fy.financialYearId,
                voucherNumber = voucherNumber, voucherType = voucherType, date = date,
                referenceNumber = referenceNumber.ifBlank { original.voucherNumber },
                narration = narration.ifBlank { "Being ${voucherType.displayName.lowercase()} against ${original.voucherNumber}" },
                totalAmount = engineResult.totalAmount, items = engineResult.journalItems,
                createdBy = if (isCredit) "SALES_BILLING_DESK" else "PURCHASE_DESK",
                partyGstin = original.partyGstin, isGstApplicable = true, referenceVoucherId = originalVoucherId
            )

            val result = repository.postVoucher(voucher, stockLines = engineResult.stockLines, gstTransactions = engineResult.gstTransactions)
            when (result) {
                is AccountingResult.Success -> {
                    emitMessage("${voucherType.displayName} $voucherNumber posted successfully against ${original.voucherNumber}")
                    scheduler.dispatchEvent(
                        com.example.accounting.automation.jobs.AutomationEvent.VoucherPosted(comp.companyId, voucher)
                    )
                }
                is AccountingResult.Failure -> {
                    emitMessage("${voucherType.displayName} posting rejected: ${result.error.message}")
                }
            }
        }
    }

    /** GST filing-period governance (Phase 5, Priority 10) - deliberately isolated: locking a GST
     * filing period never touches accounting-period locking or voucher posting. */
    fun createGstFilingPeriod(periodLabel: String, startDate: String, endDate: String) {
        viewModelScope.launch {
            val comp = _uiState.value.currentCompany ?: return@launch
            repository.createGstFilingPeriod(comp.companyId, periodLabel, startDate, endDate)
            emitMessage("GST filing period '$periodLabel' created")
        }
    }

    fun toggleGstFilingPeriodLock(period: GstFilingPeriod) {
        viewModelScope.launch {
            val comp = _uiState.value.currentCompany ?: return@launch
            repository.setGstFilingPeriodLock(comp.companyId, period.filingPeriodId, !period.isLocked, "ADMIN")
            emitMessage("GST filing period '${period.periodLabel}' ${if (period.isLocked) "unlocked" else "locked"}")
        }
    }

    fun runDailyAutomation() {
        viewModelScope.launch {
            val compId = _uiState.value.currentCompany?.companyId ?: return@launch
            val fyId = _uiState.value.currentFinancialYear?.financialYearId
            emitMessage("Running daily accounting automation pipeline...")
            val results = scheduler.runDailyJobs(compId, fyId)
            emitMessage("Daily automation cycle completed: ${results.size} tasks executed.")
        }
    }

    fun runMonthlyAutomation() {
        viewModelScope.launch {
            val compId = _uiState.value.currentCompany?.companyId ?: return@launch
            val fyId = _uiState.value.currentFinancialYear?.financialYearId
            emitMessage("Running monthly financial & GST preparation checks...")
            val results = scheduler.runMonthlyJobs(compId, fyId)
            emitMessage("Monthly automation cycle completed: ${results.size} compliance jobs executed.")
        }
    }

    fun runYearlyAutomation() {
        viewModelScope.launch {
            val compId = _uiState.value.currentCompany?.companyId ?: return@launch
            val fyId = _uiState.value.currentFinancialYear?.financialYearId
            emitMessage("Running financial year-end validation...")
            val results = scheduler.runYearlyJobs(compId, fyId)
            emitMessage("Year-end automation check completed.")
        }
    }

    fun loadLedgerStatement(ledger: Ledger) {
        viewModelScope.launch {
            val compId = _uiState.value.currentCompany?.companyId ?: return@launch
            val statement = repository.generateLedgerStatement(compId, ledger.ledgerId)
            _uiState.update { it.copy(selectedLedgerStatement = statement) }
            router.navigate(AppRoute.LedgerStatement(ledger.ledgerId))
        }
    }

    fun clearLedgerStatement() {
        _uiState.update { it.copy(selectedLedgerStatement = null) }
        router.navigate(AppRoute.ChartOfAccounts)
    }

    fun refreshFinancialReports() {
        viewModelScope.launch {
            val compId = _uiState.value.currentCompany?.companyId ?: return@launch
            val fyId = _uiState.value.currentFinancialYear?.financialYearId ?: return@launch

            try {
                val tb = repository.generateTrialBalance(compId, fyId)
                val pnl = repository.generateProfitAndLoss(compId, fyId)
                val bs = repository.generateBalanceSheet(compId, fyId)
                val gst = repository.generateGSTSummary(compId, fyId)
                val incExp = repository.generateIncomeAndExpenditure(compId, fyId)

                _uiState.update {
                    it.copy(
                        trialBalance = tb,
                        profitAndLoss = pnl,
                        incomeAndExpenditure = incExp,
                        balanceSheet = bs,
                        gstSummary = gst
                    )
                }
            } catch (e: com.example.accounting.core.database.AccountingTransactionException) {
                emitMessage("Financial statement generation failed: ${e.appError.message}")
            }
        }
    }

    fun triggerSync() {
        viewModelScope.launch {
            val compId = _uiState.value.currentCompany?.companyId ?: return@launch
            _uiState.update { it.copy(isSyncing = true) }
            val syncedCount = outboxProcessor.processPendingOutbox(compId)
            _uiState.update { it.copy(isSyncing = false) }
            emitMessage("Outbox sync completed. $syncedCount pending accounting mutations processed.")
        }
    }

    private fun emitMessage(msg: String) {
        viewModelScope.launch {
            _snackbarEvents.emit(msg)
        }
    }

    // ==================== Phase 7J UI additions ====================

    private fun currentRequestingProfile(comp: Company): BusinessProfile =
        _uiState.value.businessProfile ?: BusinessProfile(businessProfileId = "", companyId = comp.companyId, businessName = comp.name)

    private fun sha256(bytes: ByteArray): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    // ---- Party (Customer/Supplier) ----

    fun createParty(
        displayName: String,
        role: PartyRole,
        entityType: PartyEntityType,
        gstin: String = "",
        phone: String = "",
        email: String = "",
        address: String = ""
    ) {
        viewModelScope.launch {
            val comp = _uiState.value.currentCompany ?: return@launch
            val ledgerTemplate = Ledger(
                ledgerId = "", companyId = comp.companyId, groupId = "", name = displayName,
                gstin = gstin, phone = phone, email = email, address = address, stateCode = comp.stateCode
            )
            val result = partyService.createParty(
                Party(partyId = "", companyId = comp.companyId, ledgerId = "", role = role, entityType = entityType, displayName = displayName),
                ledgerTemplate
            )
            when (result) {
                is AccountingResult.Success -> emitMessage("${if (role == PartyRole.CUSTOMER) "Customer" else "Supplier"} '$displayName' added")
                is AccountingResult.Failure -> emitMessage("Failed to add: ${result.error.message}")
            }
        }
    }

    // ---- Cash/Bank/UPI ----

    fun createBankUpiProfile(
        bankName: String,
        accountHolderName: String,
        accountNumber: String,
        ifscCode: String,
        branchName: String = "",
        upiId: String = "",
        upiPayeeName: String = "",
        partyId: String? = null
    ) {
        viewModelScope.launch {
            val comp = _uiState.value.currentCompany ?: return@launch
            val profile = BankUpiProfile(
                bankUpiProfileId = "", companyId = comp.companyId, partyId = partyId, bankName = bankName,
                accountHolderName = accountHolderName, accountNumber = accountNumber, ifscCode = ifscCode,
                branchName = branchName, upi = if (upiId.isNotBlank()) UpiMetadata(upiId = upiId, payeeName = upiPayeeName) else null
            )
            when (val result = bankUpiService.create(comp.companyId, profile)) {
                is AccountingResult.Success -> emitMessage("Bank/UPI profile added")
                is AccountingResult.Failure -> emitMessage("Failed: ${result.error.message}")
            }
        }
    }

    fun deleteBankUpiProfile(profileId: String) {
        viewModelScope.launch {
            val comp = _uiState.value.currentCompany ?: return@launch
            when (val result = bankUpiService.delete(comp.companyId, profileId)) {
                is AccountingResult.Success -> emitMessage("Bank/UPI profile removed")
                is AccountingResult.Failure -> emitMessage("Failed: ${result.error.message}")
            }
        }
    }

    fun cashAndBankLedgers(): List<Ledger> {
        val ledgers = _uiState.value.ledgers
        return ledgers.filter {
            it.groupId.startsWith(com.example.accounting.domain.accounting.StandardSystemGroups.BANK_GROUP_ID) ||
                it.groupId.startsWith(com.example.accounting.domain.accounting.StandardSystemGroups.CASH_GROUP_ID)
        }
    }

    // ---- Voucher Draft review (OCR prefill + manual line entry, never a second posting path) ----

    fun editVoucherDraftLines(draft: VoucherDraft, lines: List<com.example.accounting.application.voucher.VoucherDraftLine>) {
        viewModelScope.launch {
            val comp = _uiState.value.currentCompany ?: return@launch
            val ledgersMap = _uiState.value.ledgers.associateBy { it.ledgerId }
            val voucher = Voucher(
                voucherId = draft.draftId, companyId = comp.companyId, financialYearId = draft.financialYearId,
                voucherNumber = "", voucherType = draft.voucherType, date = draft.date,
                referenceNumber = draft.referenceNumber, narration = draft.narration,
                items = lines.mapIndexed { idx, l ->
                    JournalItem(
                        itemId = "", voucherId = draft.draftId, companyId = comp.companyId, financialYearId = draft.financialYearId,
                        ledgerId = l.ledgerId, ledgerName = ledgersMap[l.ledgerId]?.name ?: "", type = l.type,
                        amount = Money.fromPaise(l.amountPaise), narration = l.narration, lineOrder = idx + 1
                    )
                }
            )
            when (val result = voucherDraftService.editDraft(draft.draftId, voucher)) {
                is AccountingResult.Success -> emitMessage("Draft updated")
                is AccountingResult.Failure -> emitMessage("Could not update draft: ${result.error.message}")
            }
        }
    }

    fun postVoucherDraft(draft: VoucherDraft) {
        viewModelScope.launch {
            val comp = _uiState.value.currentCompany ?: return@launch
            if (draft.lines.isEmpty()) {
                emitMessage("Add at least one ledger line before posting.")
                return@launch
            }
            val voucherNumber = repository.generateNextVoucherNumber(comp.companyId, draft.financialYearId, draft.voucherType)
            val ledgersMap = _uiState.value.ledgers.associateBy { it.ledgerId }
            val voucher = Voucher(
                voucherId = draft.draftId, companyId = comp.companyId, financialYearId = draft.financialYearId,
                voucherNumber = voucherNumber, voucherType = draft.voucherType, date = draft.date,
                referenceNumber = draft.referenceNumber, narration = draft.narration,
                totalAmount = Money.fromPaise(draft.lines.filter { it.type == DrCr.DEBIT }.sumOf { it.amountPaise }),
                items = draft.lines.mapIndexed { idx, l ->
                    JournalItem(
                        itemId = "", voucherId = draft.draftId, companyId = comp.companyId, financialYearId = draft.financialYearId,
                        ledgerId = l.ledgerId, ledgerName = ledgersMap[l.ledgerId]?.name ?: "", type = l.type,
                        amount = Money.fromPaise(l.amountPaise), narration = l.narration, lineOrder = idx + 1
                    )
                },
                createdBy = "SENIOR_ACCOUNTANT"
            )
            when (val result = voucherDraftService.postDraft(voucher)) {
                is AccountingResult.Success -> emitMessage("Voucher $voucherNumber posted successfully")
                is AccountingResult.Failure -> emitMessage("Posting rejected: ${result.error.message}")
            }
        }
    }

    fun discardVoucherDraft(draftId: String) {
        viewModelScope.launch {
            val comp = _uiState.value.currentCompany ?: return@launch
            when (val result = voucherDraftService.discardDraft(comp.companyId, draftId)) {
                is AccountingResult.Success -> emitMessage("Draft discarded")
                is AccountingResult.Failure -> emitMessage("Could not discard: ${result.error.message}")
            }
        }
    }

    // ---- Invoice drafts (read/cancel only this pass - creation reuses the existing, tested
    // Sale/Purchase dialog and its immediate-post path; see docs/54 for the reasoning) ----

    fun cancelInvoiceDraft(invoiceId: String) {
        viewModelScope.launch {
            val comp = _uiState.value.currentCompany ?: return@launch
            val fy = _uiState.value.currentFinancialYear ?: return@launch
            when (val result = invoiceService.cancelInvoice(comp.companyId, fy.financialYearId, invoiceId)) {
                is AccountingResult.Success -> emitMessage("Invoice draft cancelled")
                is AccountingResult.Failure -> emitMessage("Could not cancel: ${result.error.message}")
            }
        }
    }

    // ---- Profile / Business Setup ----

    fun updateBusinessProfile(businessName: String, legalName: String, address: String, phone: String, email: String, gstin: String, pan: String) {
        viewModelScope.launch {
            val comp = _uiState.value.currentCompany ?: return@launch
            val base = _uiState.value.businessProfile ?: BusinessProfile(businessProfileId = "", companyId = comp.companyId, businessName = businessName)
            val profile = base.copy(businessName = businessName, legalName = legalName, address = address, phone = phone, email = email, gstin = gstin, pan = pan)
            when (val result = profileService.upsertBusinessProfile(comp.companyId, profile)) {
                is AccountingResult.Success -> {
                    _uiState.update { it.copy(businessProfile = result.data) }
                    emitMessage("Business profile updated")
                }
                is AccountingResult.Failure -> emitMessage("Failed: ${result.error.message}")
            }
        }
    }

    fun updateIndividualProfile(name: String, address: String, phone: String, email: String, pan: String) {
        viewModelScope.launch {
            val comp = _uiState.value.currentCompany ?: return@launch
            val base = _uiState.value.individualProfile ?: IndividualProfile(individualProfileId = "", companyId = comp.companyId, name = name)
            val profile = base.copy(name = name, address = address, phone = phone, email = email, pan = pan)
            when (val result = profileService.upsertIndividualProfile(comp.companyId, profile)) {
                is AccountingResult.Success -> {
                    _uiState.update { it.copy(individualProfile = result.data) }
                    emitMessage("Individual profile updated")
                }
                is AccountingResult.Failure -> emitMessage("Failed: ${result.error.message}")
            }
        }
    }

    // ---- Subscription ----

    fun loadSubscription() {
        viewModelScope.launch {
            val comp = _uiState.value.currentCompany ?: return@launch
            val fy = _uiState.value.currentFinancialYear ?: return@launch
            _uiState.update { it.copy(currentSubscription = subscriptionService.getCurrent(comp.companyId, fy.financialYearId)) }
        }
    }

    fun upgradeOrRenewSubscription(planType: SubscriptionPlanType, planName: String, entitlements: Set<EntitlementFeature>) {
        viewModelScope.launch {
            val comp = _uiState.value.currentCompany ?: return@launch
            val fy = _uiState.value.currentFinancialYear ?: return@launch
            when (val result = subscriptionService.createOrRenew(comp.companyId, fy.financialYearId, planType, planName, entitlements)) {
                is AccountingResult.Success -> {
                    _uiState.update { it.copy(currentSubscription = result.data) }
                    emitMessage("Subscription updated to $planName")
                }
                is AccountingResult.Failure -> emitMessage("Failed: ${result.error.message}")
            }
        }
    }

    // ---- Reports Center extras (Outstanding/Receivables/Payables/Cash Flow/Ratio Analysis/HSN-SAC) ----

    fun refreshReportsCenterExtras() {
        viewModelScope.launch {
            val comp = _uiState.value.currentCompany ?: return@launch
            val fy = _uiState.value.currentFinancialYear ?: return@launch
            val today = LocalDate.now()
            val outstanding = reportService.outstanding(comp.companyId, today = today)
            val receivables = reportService.receivables(comp.companyId, today)
            val payables = reportService.payables(comp.companyId, today)
            val cashFlow = reportService.cashFlow(comp.companyId, fy.financialYearId, fy.startDate..fy.endDate)
            val ratios = reportService.ratioAnalysis(comp.companyId, fy.financialYearId)
            val hsnSac = reportService.hsnSacSummary(comp.companyId, fy.financialYearId)
            _uiState.update {
                it.copy(
                    outstandingReport = outstanding, receivablesReport = receivables, payablesReport = payables,
                    cashFlowReport = cashFlow, ratioAnalysisReport = ratios, hsnSacSummary = hsnSac
                )
            }
        }
    }

    // ---- Data Import (CSV/JSON -> Draft/Suggestion -> Review -> Explicit Create) ----

    fun importFromFile(sourceFile: File, format: ImportFileFormat) {
        viewModelScope.launch {
            val comp = _uiState.value.currentCompany ?: return@launch
            val bytes = sourceFile.readBytes()
            val assetResult = repository.createDocumentAsset(
                comp.companyId, DocumentAssetType.IMPORT_SOURCE_FILE, sourceFile.absolutePath, sha256(bytes),
                if (format == ImportFileFormat.JSON) "application/json" else "text/csv", sourceFile.length()
            )
            if (assetResult is AccountingResult.Failure) {
                emitMessage("Import failed: ${assetResult.error.message}")
                return@launch
            }
            val asset = (assetResult as AccountingResult.Success).data
            val result = dataImportService.parseFile(currentRequestingProfile(comp), format, asset.assetId)
            when (result) {
                is AccountingResult.Success -> {
                    _uiState.update { it.copy(lastImportResult = result.data, lastImportRowOutcomes = emptyMap()) }
                    emitMessage("Parsed ${result.data.suggestions.size} row(s) for review")
                }
                is AccountingResult.Failure -> emitMessage("Import failed: ${result.error.message}")
            }
        }
    }

    fun reviewAndCreateImportRow(suggestion: ImportRowSuggestion, resolvedType: ImportSuggestionType) {
        viewModelScope.launch {
            val comp = _uiState.value.currentCompany ?: return@launch
            val result = dataImportService.reviewAndCreate(comp.companyId, suggestion, resolvedType)
            val outcome = when (result) {
                is AccountingResult.Success -> "Created"
                is AccountingResult.Failure -> "Failed: ${result.error.message}"
            }
            _uiState.update { it.copy(lastImportRowOutcomes = it.lastImportRowOutcomes + (suggestion.rowNumber to outcome)) }
            emitMessage("Row ${suggestion.rowNumber}: $outcome")
        }
    }

    fun clearImportResult() {
        _uiState.update { it.copy(lastImportResult = null, lastImportRowOutcomes = emptyMap()) }
    }

    // ---- OCR (suggestion-only, adapter deliberately unimplemented - always fails gracefully) ----

    fun scanReceiptForVoucherDraft(imageFile: File) {
        viewModelScope.launch {
            val comp = _uiState.value.currentCompany ?: return@launch
            val fy = _uiState.value.currentFinancialYear ?: return@launch
            val bytes = imageFile.readBytes()
            val assetResult = repository.createDocumentAsset(
                comp.companyId, DocumentAssetType.OCR_SOURCE_IMAGE, imageFile.absolutePath, sha256(bytes), "image/jpeg", imageFile.length()
            )
            if (assetResult is AccountingResult.Failure) {
                emitMessage("Scan failed: ${assetResult.error.message}")
                return@launch
            }
            val asset = (assetResult as AccountingResult.Success).data
            when (val result = ocrService.requestExtraction(currentRequestingProfile(comp), asset.assetId)) {
                is AccountingResult.Success -> {
                    _uiState.update { it.copy(lastOcrExtraction = result.data) }
                    ocrService.reviewAndPrefillVoucherDraft(comp.companyId, fy.financialYearId, result.data)
                    emitMessage("Receipt scanned - a draft was created for review; add ledger lines before posting.")
                }
                is AccountingResult.Failure -> emitMessage(result.error.message)
            }
        }
    }

    // ---- QR/Barcode (pure utility - no accounting logic) ----

    fun generateBarcodeForItem(itemId: String) {
        viewModelScope.launch {
            val comp = _uiState.value.currentCompany ?: return@launch
            when (val result = qrBarcodeService.generateForStockItem(currentRequestingProfile(comp), comp.companyId, itemId)) {
                is AccountingResult.Success -> _uiState.update { it.copy(lastBarcodeGeneration = result.data) }
                is AccountingResult.Failure -> emitMessage("Could not generate barcode: ${result.error.message}")
            }
        }
    }

    fun scanBarcodeImage(imageFile: File) {
        viewModelScope.launch {
            val comp = _uiState.value.currentCompany ?: return@launch
            val bytes = imageFile.readBytes()
            val assetResult = repository.createDocumentAsset(
                comp.companyId, DocumentAssetType.OCR_SOURCE_IMAGE, imageFile.absolutePath, sha256(bytes), "image/jpeg", imageFile.length()
            )
            if (assetResult is AccountingResult.Failure) {
                emitMessage("Scan failed: ${assetResult.error.message}")
                return@launch
            }
            val asset = (assetResult as AccountingResult.Success).data
            when (val result = qrBarcodeService.scanImage(currentRequestingProfile(comp), comp.companyId, asset.assetId)) {
                is AccountingResult.Success -> {
                    _uiState.update { it.copy(lastBarcodeScan = result.data) }
                    emitMessage(if (result.data.matchedStockItemId != null) "Match found" else "No matching item found")
                }
                is AccountingResult.Failure -> emitMessage("Scan failed: ${result.error.message}")
            }
        }
    }

    fun clearBarcodeState() {
        _uiState.update { it.copy(lastBarcodeGeneration = null, lastBarcodeScan = null) }
    }

    // ---- Export & Share (Android-only, JSON/CSV; delegates to ExportManagementService/ShareAdapter) ----

    suspend fun exportVoucherAndShare(voucherId: String, format: com.example.accounting.domain.export.ExportFormat): android.content.Intent? {
        val comp = _uiState.value.currentCompany ?: return null
        val result = exportService.exportVoucher(comp.companyId, voucherId, format)
        if (result is AccountingResult.Failure) {
            emitMessage("Export failed: ${result.error.message}")
            return null
        }
        val exportResult = (result as AccountingResult.Success).data
        val ext = if (format == com.example.accounting.domain.export.ExportFormat.CSV) "csv" else "json"
        val mimeType = if (ext == "csv") "text/csv" else "application/json"
        val file = File(getApplication<Application>().cacheDir, "voucher_export_${System.currentTimeMillis()}.$ext")
        file.writeText(exportResult.content)
        return documentPreviewService.buildShareIntent(getApplication(), file, mimeType)
    }
}

