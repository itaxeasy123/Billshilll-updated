# 54. UI/UX Architecture (Phase 7J UI)

## Status

Implemented. The first real UI installment since Phase 0 - every prior phase (0-7J-B) built and
froze the accounting/GST/inventory/sync/reporting/document/export/automation engine plus (7J-B) a
full application-service layer with **zero UI on top of it** beyond 5 pre-existing screens
(Dashboard, Day Book, Chart of Accounts, Reports, Settings/Sync). This phase builds the UI a user
actually touches, governed by a user-supplied UX/architecture spec
(`LedgerPrime_Phase_7J_UI_UX_Architecture.md`) and reconciled against the frozen 7J-B service layer
in a dedicated design pass before any code was written (see the approved plan this phase shipped
against).

**No accounting logic was added.** Every screen consumes an existing engine/report/service call;
the three narrow backend additions below are the only new code outside `presentation/` and
`ui/theme/`, each pre-approved before implementation and each a pure delegation or pure
aggregation, never a new calculation.

## 1. Visual system - Royal Purple + Off-White

`app/src/main/java/com/example/ui/theme/{Color,Theme}.kt` only. Confirmed by direct read before
touching anything: all 5 pre-existing screens and 6 dialogs consume `MaterialTheme.colorScheme.*`
exclusively, zero hardcoded hex - so the new palette recolors 100% of existing UI with **zero
screen-file edits**. New tokens: `RoyalPurple #4C1D95` (primary), `RoyalPurpleLight #7C3AED`
(dark-mode primary), `RoyalPurpleDark #2E1065` (container), `RoyalPurpleContainer #EDE9FE`,
`OffWhite #FAF9F6` (background), `OffWhiteSurface`, `CharcoalText #1F1B24`. Both a light and a dark
`ColorScheme` were built (the spec was silent on dark mode; built symmetrically per the resolved
decision, rather than leaving a jarring blue-dark/purple-light mismatch). `EmeraldCredit`/
`CrimsonDebit`/`AmberWarning`/`IndigoTax` (status accents) kept unchanged - orthogonal to the
primary/background swap. `PrimaryBlue*`/`Navy900`/`Slate*` retired (confirmed unreferenced outside
`theme/`).

## 2. Information architecture - 5-item bottom nav

Per the UX spec's Section 14: `NavigationTab { HOME, SALES, PURCHASES, MONEY, REPORTS }`
(`AccountingViewModel.kt`), replacing the old `{DASHBOARD, DAYBOOK, CHART_OF_ACCOUNTS, REPORTS,
SETTINGS_SYNC}`. `HashRouter.kt`'s `AppRoute` sealed class grew additively: `Sales`, `Purchases`,
`Money`, `Parties(role)`, `Profile`, `Subscription`, `DataTools`, `Search(query)` alongside the
kept `Dashboard`/`DayBook`/`ChartOfAccounts`/`LedgerStatement`/`Reports`/`SettingsAndSync`.
`MainAppScreen.kt` dispatches on `uiState.currentRoute` (not just `selectedTab`), since several
routes - Profile, Subscription, DataTools, Search - never own a bottom-nav tab; they're reached via
the top bar's new Search/Profile icons (`AppTopBar.kt`, two additive `IconButton`s) or from within
another screen, matching the spec's "secondary features reached through their respective sections"
rule.

| Area | Reached via |
|---|---|
| Dashboard | Home tab (rebuilt) |
| Day Book / Ledgers / Groups | Home widgets, Reports -> Accounts |
| Party | Sales/Purchases sub-tabs, Search |
| Items | Sales/Purchase item picker, CoA (mode-gated) |
| Sales/Invoice | Sales tab |
| Purchase | Purchases tab |
| Receipt/Payment/Transfer/Cash/Bank/UPI | Money tab sub-menu |
| Outstanding | Home widgets, Reports -> Sales/Purchase |
| Reports Center | Reports tab |
| Document Preview/Export/QR | Contextual, no top-level route |
| Import/OCR/Subscription | Profile entry point |
| Profile/Business Setup | Top-bar icon |
| Settings/Sync | Folded into Profile ("Company & Sync") |
| Search | Persistent top-bar icon |
| Journal | Money tab's "Advanced" section only |

## 3. Home - Business Cockpit

`DashboardScreen.kt` rebuilt (not a new file) as a widget grid per Section 2: Quick Actions
(Sale/Purchase/Receive/Pay/Transfer/Add Customer/Add Supplier/Add Item - business-action framed,
never raw Dr/Cr) and a Business Snapshot grid (Cash/Bank/Receivables/Payables/Sales/Purchases/
Profit-or-Surplus/GST/Outstanding), every figure read directly from an already-loaded
`AccountingUiState` field (`profitAndLoss`, `receivablesReport`, `payablesReport`,
`outstandingReport`, `gstSummary`, `ledgers` filtered by Cash/Bank system-group prefix) - zero
summation performed in this file. Tapping a widget routes to the relevant tab/screen; Recent
Transactions reuses the existing `VoucherSummaryCard`/Day Book navigation verbatim.

## 4. Sales / Purchases

`SalesScreen.kt`/`PurchasesScreen.kt` (new) - two internal tabs each ("Sales"/"Customers",
"Purchases"/"Suppliers"). Voucher creation is **not** reimplemented: the FAB opens the existing,
tested `CreateVoucherDialog(defaultVoucherType = VoucherType.SALES/PURCHASE)`, posted via the
existing `postTradingDocument`/`buildSale`/`buildPurchase` path already wired in
`AccountingViewModel.kt`/`MainAppScreen.kt` - this phase adds zero new posting logic for
Sales/Purchase. `PartiesScreen.kt` (new, shared by both tabs via a `role` parameter) lists/creates
Customers or Suppliers via `PartyManagementService` (through `AccountingViewModel.createParty`);
editing a Party stays out of scope, matching the frozen service layer's own create/list-only
surface.

## 5. Money

`MoneyHomeScreen.kt` (new) - a sub-menu per the spec's exact list (Receive Money/Pay Money/
Transfer/Cash/Bank/UPI), plus a "Pending Reviews" tile and an explicitly separate "Advanced"
section for Journal (never a primary action, per Section 15). "Receive Money"/"Pay Money"/
"Transfer" all open the same `CreateVoucherDialog` with `VoucherType.RECEIPT`/`PAYMENT`/`CONTRA`
preselected - reusing the existing settlement-allocation UI already built into that dialog
(`CreateVoucherDialog.kt`'s settlement section, ~lines 743-804), never a second allocation UI.
`MoneyTabContent` owns this tab's internal sub-navigation (mirrors
`ChartOfAccountsScreen`'s own list/statement pattern) so `MainAppScreen.kt` needs only one dispatch
case for the whole tab.

**Voucher Draft review** (`VoucherDraftReviewScreen.kt`, new) - the Draft/Suggestion/Review/Post
queue: a list of `PENDING_REVIEW` `VoucherDraft`s (from OCR or manually) -> `VoucherDraftEditorScreen`
(ledger/Dr-Cr/amount line editor) -> Post (direct delegation to
`VoucherManagementServiceImpl.postDraft` -> the existing, unmodified `AccountingRepository.postVoucher`,
never a second posting path) or Discard.

**Cash/Bank/UPI** - `CashOrBankLedgerListScreen` (read-only, filters `uiState.ledgers` by the
existing `StandardSystemGroups.CASH_GROUP_ID`/`BANK_GROUP_ID` prefixes, tapping a ledger reuses the
existing ledger-statement flow); `UpiProfilesScreen.kt` (new) lists/creates/deletes
`BankUpiProfile` rows via `BankUpiProfileService` - settlement metadata only, never a ledger
balance.

## 6. Reports Center

`ReportsCenterScreen.kt` (new) replaces the old flat 4-tab `ReportsScreen.kt` with a
category-selector landing page per Section 13's 5 categories (Financial/Sales-Purchase/Accounts/
GST/Analysis). The existing report-rendering composables (`TrialBalanceView`, `ProfitAndLossView`/
`IncomeAndExpenditureView`, `BalanceSheetView`, `GSTCenterView`, all still in `ReportsScreen.kt`)
are reused verbatim, not duplicated. New, additive report views inside `ReportsCenterScreen.kt`:
`CashFlowView`, `RatioAnalysisView`, `OutstandingList` (Receivables/Payables), `HsnSacSummaryView` -
every one a direct read of an already-loaded `AccountingUiState` field, populated by
`AccountingViewModel.refreshReportsCenterExtras()` which calls `ReportManagementService`'s existing
pure-delegation methods. Sales/Purchase Register and Cash Book/Bank Book/Receipt/Payment Register
are explicit UI-side *filters* over already-fetched `uiState.vouchers` (never a new calculation,
matching the plan's pre-approved scope for these). **Fund Flow and CMA render as visible, disabled
"Coming soon" tiles** - both have zero backend implementation (`CmaReportGenerator`'s own README:
"architecture and contracts only") and building either here would mean writing new calculation
logic during a UI-only phase, which this phase does not do.

## 7. Profile / Business Setup, Subscription, Import & Scan

`ProfileScreen.kt` (new, reached from the top-bar icon) shows a Business Profile section and an
Individual Profile section **unconditionally, both at once** - no field anywhere distinguishes
which applies to a given company, and the frozen `ProfileApplicationService` enforces no
exclusivity either, so this screen doesn't invent one. Also hosts entry points into Import & Scan,
Subscription, and Company & Sync (the existing `SettingsAndSyncScreen.kt`, relocated out of the
bottom nav, file itself untouched).

`SubscriptionScreen.kt` (new) - plan status + entitlement checklist + upgrade/renew, backed by
`SubscriptionManagementService`. Per the resolved decision, **no other new screen this phase checks
an entitlement before offering its action** - gating is explicitly deferred to a later phase.

`DataToolsScreen.kt` (new) - CSV/JSON import and "Scan Receipt" (OCR), both strictly
File -> Parser -> Validation -> Draft/Suggestion -> User Review -> Explicit Create/Post per Section
9/10. The adapter classes themselves (`CsvJsonDataImportAdapter`, and OCR's still-unimplemented
`OcrIngestionAdapter`) are never called directly from Compose - only through
`AccountingViewModel.importFromFile`/`scanReceiptForVoucherDraft`, which first persist the
picked file/photo as a checksummed `DocumentAsset` (using the two additive
`DocumentAssetType.IMPORT_SOURCE_FILE`/`OCR_SOURCE_IMAGE` values). OCR's "Scan Receipt" button
always renders a graceful "not yet available" state today (`OcrSuggestionService`'s adapter is
`null`) - this is expected, documented behavior, not a defect, and the entry point is deliberately
left enabled (not hidden) to demonstrate the graceful-failure path.

File picking uses Android's Storage Access Framework (`ActivityResultContracts.OpenDocument`, CSV/
JSON) and Photo Picker (`ActivityResultContracts.PickVisualMedia`, receipt photos) exclusively -
confirmed zero new `AndroidManifest.xml` entries needed (`INTERNET`/`ACCESS_NETWORK_STATE` and the
Phase 7D `FileProvider` were already the full permission set; neither picker requires a runtime
permission).

## 8. Search

`SearchScreen.kt` (new) - a composite, in-memory filter over `uiState.parties`/`ledgers`/
`vouchers`/`stockItems`, reached from the persistent top-bar search icon. Never recomputes a
balance or total; every result routes into an existing detail screen (Party -> its Ledger's
statement, Ledger -> statement, Voucher -> `VoucherDetailDialog`).

## 9. Account Mode / Business Activity gating (the one confirmed pre-existing UI gap this phase closes)

`isInventoryEnabled(uiState)` (`AccountingViewModel.kt`, one pure function) is the single point
every Items-related call site reads - `ChartOfAccountsScreen`'s `CoaTab` list (now filters `ITEMS`
out entirely rather than showing-then-disabling it, closing the gap 7J-B's own audit flagged: this
tab rendered unconditionally regardless of `AccountingMode` before this phase). `BusinessType.SERVICE`
gating (already correct in the pre-existing P&L-vs-Income&Expenditure label swap) is reused
verbatim in the new Reports Center's Financial category, never re-derived.

## 10. Backend additions (the only three, all pre-approved before implementation)

1. `VoucherManagementServiceImpl.listDrafts(companyId, status): Flow<List<VoucherDraft>>` - an
   additive convenience (not part of the frozen `VoucherManagementService` interface), mirroring
   `RecurringVoucherManagementService.getDrafts`'s exact shape. Needed for the Money tab's Pending
   Reviews queue and OCR's prefill flow.
2. `DocumentAssetType` gains `IMPORT_SOURCE_FILE`/`OCR_SOURCE_IMAGE` (additive enum values) -
   needed so Import/OCR can persist a picked file/photo as a checksummed `DocumentAsset` before
   parsing, matching every other asset type's existing discipline.
3. `ReportManagementService.hsnSacSummary(companyId, financialYearId): List<HsnSacSummaryRow>` +
   one new, additive `AccountingRepository.getGstTransactionsForCompanyFY` read-only helper - pure
   aggregation of already-computed `GstTransaction` rows by HSN/SAC code, never a new tax
   calculation; `GstTransaction.gstRatePercent` remains the only authoritative per-transaction rate.

Every other screen calls the frozen 7J-B service layer as-is, with zero method-signature changes.

## 11. Shared component

`presentation/components/SectionCard.kt` (new) - one reusable rectangular container
(`RoundedCornerShape(14dp)`, header slot with title/subtitle/trailing, list-row and form-section
dual use via an optional `onClick`) used by every new screen this phase, colored entirely through
`MaterialTheme.colorScheme` so it inherited the new theme automatically. The 5 pre-existing
screens' own inline `Card`/`OutlinedCard`/`ElevatedCard` usage is completely untouched.

## 12. Testing

`compileDebugKotlin`/`compileDebugUnitTestKotlin`/`testDebugUnitTest` clean, zero regressions
beyond the same pre-existing Robolectric environment failures already documented across every
prior phase's changelog entry. The repository's only Compose-test file (`GreetingScreenshotTest.kt`)
is already among those pre-existing failures - real visual/screenshot verification is not
achievable in this environment, matching every prior UI-adjacent phase's own documented
disclosure; this phase's actual verification bar is a clean compile plus the existing ViewModel/
service-layer test suites staying green, not new pixel-level tests.

## 13. Explicitly out of scope

- Party/Ledger/Item **edit** (only create/list/delete existed before this phase and still does -
  no `updateParty`/`updateLedger`/`updateStockItem` exists anywhere in the frozen service layer;
  adding one is a service-layer decision, not a UI one).
- Entitlement gating of any screen other than the Subscription screen itself.
- A real OCR implementation (`OcrIngestionAdapter` stays unimplemented; this phase only builds the
  UI around the existing graceful-failure contract).
- Fund Flow, CMA, live camera QR/barcode scanning (Photo-Picker-based scanning exists via
  `QrBarcodeManagementService`, but no dedicated scan-to-match UI screen was built this pass beyond
  item barcode generation).
- Excel import, GSTR/ITR filing, any change to a frozen engine.
