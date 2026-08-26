# 56. UI Component Library (Phase UI-02/UI-03)

## Status

Real, compiled component library under `presentation/components/` plus a design-token layer under
`presentation/theme/` (colors/typography live in the separate `com.example.ui.theme` package, a
pre-existing leftover-template location - documented, not moved, per "preserve the existing
working theme unless a concrete problem exists"). This file is the map: what exists, what each
piece is for, and - just as importantly - what was deliberately **not** built and why.

## Design tokens (`presentation/theme/`, `com.example.ui.theme/`)

| Token file | What it holds | Notes |
|---|---|---|
| `com.example.ui.theme.Color.kt` | Royal Purple + Off-White palette, light and dark, plus status accents (Emerald/Crimson/Amber/Indigo) | Pre-existing, unchanged this phase |
| `com.example.ui.theme.Type.kt` | The full Material 3 baseline type scale (`displayLarge` ... `labelSmall`) | Expanded this phase - every value is byte-identical to Compose Material3's own default, so this is a *naming* change, not a visual one. Only `bodyLarge` was explicitly defined before |
| `com.example.ui.theme.Theme.kt` | `MyApplicationTheme` - the app's single `MaterialTheme(colorScheme, typography)` entry point | Function name is a leftover Android Studio template default, kept as-is (rename is cosmetic, not a "concrete problem") |
| `presentation/theme/Spacing.kt` | `xs`(4dp)/`sm`(8dp)/`md`(16dp)/`lg`(24dp)/`xl`(32dp) | Pre-existing |
| `presentation/theme/Radius.kt` | `sm`(8dp)/`md`(12dp)/`lg`(14dp)/`xl`(20dp) + matching `RoundedCornerShape`s | New this phase - formalizes values already in de-facto use across the app (grep-counted before adding, never guessed) |
| `presentation/theme/Elevation.kt` | `none`/`low`/`medium`/`high`/`highest` | New this phase - for the remaining hand-set `tonalElevation`/shadow call sites |
| `presentation/theme/Breakpoints.kt` | `tablet = 600.dp` | New this phase - names the exact value `MainAppScreen`'s NavigationRail switch already used |

**Scope decision, stated once here rather than repeated per file**: none of the token files above
retrofit pre-existing screens' hardcoded values (125+ occurrences found across the app). They are
additive - a new screen reaches for `Radius.md`, an old screen's `RoundedCornerShape(12.dp)` is
untouched. Retrofitting every existing screen is a separate, much larger, separately-risked task,
not requested here.

## Base components (`presentation/components/`)

| Component | File | What it's for |
|---|---|---|
| `ActionButton` | `ActionButton.kt` | The one Button primitive - PRIMARY/SECONDARY/TEXT style enum, always theme-colored |
| `AppIconButton` | `AppIconButton.kt` | IconButton with a centralized default tint (`onSurfaceVariant`) instead of each call site picking a color |
| `SectionCard` | `SectionCard.kt` | The one Card/Container - a static titled section or a tappable list row |
| `ScreenContainer` | `ScreenContainer.kt` | Standardizes the horizontal screen padding (`Spacing.md`) most screens already apply ad hoc |
| `Amount` | `Amount.kt` | The one place a `Money` value is ever rendered - monospace, via `Money.formatPlain()`, never a second formatting rule |
| `StatusBadge` | `Badge.kt` | Small colored status label - generic color+text, no fixed status set baked in |
| `FormField` | `FormField.kt` | The one labeled text-input primitive - deliberately has no `placeholder` param, so a form can never show a static example value a user could mistake for real data |
| `SelectField<T>` | `Select.kt` | The one generic dropdown-select - consolidates what was two independent, near-identical `LedgerDropdown` implementations (`CreateVoucherDialog`, `MoneyVoucherEntryScreen`) into one type-generic component |
| `DateField` | `DateField.kt` | Date-picking text field (Material3 `DatePickerDialog` wrapped) - not previously needed anywhere (every voucher date defaults to today with no picker UI at all); built as a real, working primitive for whenever backdating is added |
| `SearchField` | `SearchField.kt` | Search input with leading search icon + conditional clear button - wired into `SearchScreen.kt`, replacing its raw `OutlinedTextField` |
| `AppDivider` | `AppDivider.kt` | `HorizontalDivider` defaulted to `outlineVariant` |
| `AppLoader` | `Loader.kt` | The one loading-spinner primitive - closes a real, verified gap (zero `CircularProgressIndicator` usage existed anywhere before this phase) |
| `AppDialog` | `AppDialog.kt` | Generic modal shell (title/scrollable content/Cancel+Confirm) - the exact shape all five `Create*Dialog.kt` files independently hand-build |
| `AppBottomSheet` | `AppBottomSheet.kt` | The one `ModalBottomSheet` wrapper - zero existing usage before this phase, built as a ready primitive for a future entry point (e.g. an OCR upload picker) |

## Widgets (combine base components + real data)

| Widget | File | What it's for |
|---|---|---|
| `StatCard` | `StatCard.kt` | Promoted from `DashboardScreen`'s previously page-local `MetricCard` - icon+title+amount+subtitle tile |
| `QuickAction` / `QuickActions` | `QuickAction.kt` | Promoted from `DashboardScreen`'s previously page-local `QuickActionButton`; `QuickActions` is new - a row wrapper over a `List<QuickActionSpec>`, now actually used by `DashboardScreen` in place of two hand-written `Row`s |
| `AmountCard` | `AmountCard.kt` | A plainer, non-tappable label+amount card - deliberately smaller than `StatCard` (no icon, outlined not elevated), for a totals line rather than a Dashboard tile |
| `PartyCard` | `PartyCard.kt` | Named `LedgerRow` wrapper for a Customer/Supplier row. `PartiesScreen.kt` currently hand-rolls the same layout instead of reusing `LedgerRow`+`Amount` - flagged, not retrofitted |
| `LedgerRow` | `TableRow.kt` | Pre-existing - tappable title/subtitle/amount row. Already covers what "ReportRow" would need too (see below) |
| "ReportRow" | *(none needed)* | `TableRow` (same file) already is this - a label/value/amount line, already used throughout `ReportsCenterScreen.kt`. No new file added |
| "FormSection" | *(none needed)* | `SectionCard(title = ...)` already provides a titled grouping - no new file added |
| `EmptyState` | `EmptyState.kt` | Generic "nothing here yet" block - the shape `ReportsCenterScreen`'s private `EmptyReportState` (and bare `Text` calls elsewhere) already hand-roll |
| `ErrorState` | `ErrorState.kt` | The `EmptyState`/`LoadingState` counterpart for a screen/section whose own data failed to load. This app's normal transient-failure feedback is the Snackbar (`AccountingViewModel.emitMessage`) - this is only for the rarer "nothing to show because loading itself failed" case |
| `LoadingState` | `Loader.kt` | Full-area loading block (spinner + optional message) |
| `ImportSummaryView` | `ImportSummary.kt` | Displays an already-computed `ImportReconciliationSummary` (built in an earlier phase, never shown anywhere until this component). **Not yet wired** into `DataToolsScreen` - that screen's ViewModel state tracks each row outcome as a free-text `String`, not the structured `ImportRowOutcome` this widget's input needs; changing that is a ViewModel state-shape change, out of scope for a components phase |
| `DocumentPreview` | `DocumentPreview.kt` | Generic preview-card shell: title + content slot + optional Print/Export/Share icons wired to already-existing service calls. Does **not** render Invoice/TradeDocument line items itself - that's a real screen's worth of layout, deliberately left for whichever future phase builds an actual Invoice-preview screen |
| Dashboard summary widgets | `DashboardSummaryCards.kt` | `SalesSummary`/`PurchaseSummary`/`ReceiptSummary`/`PaymentSummary`/`IncomeSummary`/`ExpenditureSummary` - named, single-purpose `StatCard` wrappers, each fixing only its own icon/color identity; the `amount` is always a parameter, always an already-engine-computed figure. See naming note below |
| Recent Transactions | `RecentTransactions.kt` | `LazyListScope.recentTransactionsSection(...)` - the header + list-or-empty-state block extracted verbatim from `DashboardScreen.kt` |

### Naming note: `ReceiptSummary` / `PaymentSummary`

No report anywhere computes a "total Receipt/Payment vouchers this financial year" figure - adding
that sum would be a UI-side accounting calculation, which this app's own rules forbid, and adding
it to the engine/report layer is a separate, bigger change than a components phase should make.
These two widgets therefore display the closest already-computed figures that exist -
Receivables/Payables balances - and say so on screen (business-correct terms), while keeping the
function names requested. If a real "Receipts this year" report figure is ever added to the
engine, these widgets are ready to receive it as their `amount` parameter unchanged.

### "DashboardHeader" - not built

The company/FY/sync/search/profile header the Dashboard sits under is `AppTopBar.kt`, already
global to every screen, not Dashboard-specific. Building a second, Dashboard-only header would
either duplicate `AppTopBar` or require inventing new greeting content with no existing source of
truth - neither is "a missing component," so nothing was added here.

## Rules verified against every file in this phase

1. **Generic base components** - `SelectField<T>`, `AppDialog`, `AppLoader`, `EmptyState`,
   `ErrorState`, `AppBottomSheet`, `DateField` all take their content/data as parameters; none has
   a fixed business vocabulary baked in.
2. **Widgets combine base components** - every widget in the table above is built from one or more
   base components (`StatCard` → `ElevatedCard`+`Amount`; `PartyCard` → `LedgerRow`; `DocumentPreview`
   → `SectionCard`+`AppIconButton`), never a fresh hand-rolled layout.
3. **Data via parameters** - verified per-file; no widget reads global/singleton state.
4. **No hardcoded business data** - `StatusBadge` takes text+color, never a status enum;
   `SelectField` takes a generic `List<T>` + label mapper, never `Ledger` by name.
5. **Centralized tokens** - every new file in this phase uses `MaterialTheme.colorScheme`/
   `typography` and, where a corner radius or spacing value was needed, `Radius`/`Spacing` (the
   one caught-and-fixed exception: `ImportSummaryView`'s badge originally used raw hex colors
   instead of `colorScheme.secondaryContainer`/`tertiaryContainer` - fixed during this same phase,
   before it ever compiled into the app).
6. **Responsive** - every layout uses `dp`/`Row`+`weight`/`fillMaxWidth`, the same mechanism the
   rest of this app already uses for its phone↔tablet NavigationRail switch. Nothing here is
   fixed-width or tablet-only.
7. **No accounting calculations** - every widget receives an already-computed `Money`/report field;
   none sums, multiplies, or derives a figure of its own (see the `ReceiptSummary`/`PaymentSummary`
   naming note above for the one place this was a real, deliberate constraint on what got built).
8. **No repository/database access** - zero component in this phase takes an `AccountingRepository`,
   a DAO, or a coroutine scope of its own.
9. **No duplicated business logic** - `SelectField` and `AppDialog` exist specifically *because*
   inspection found real duplicate hand-rolled versions of both patterns; neither pre-existing
   duplicate was deleted in this pass (would require re-verifying two already-tested dialogs/screens),
   but no *third* copy was added anywhere.
10. **Accounting logic stays outside UI** - confirmed by (7) and (9) together.

## Explicitly not built (with reasoning)

- **A `Text`/`Typography` wrapper component** - Compose's own `Text` + the now-complete
  `MaterialTheme.typography` scale (this phase) already *is* the typography system; wrapping `Text`
  for no behavioral gain would be exactly the "premature abstraction" this project's own engineering
  discipline warns against.
- **Retrofitting existing screens** onto any new token or component (see the token table's scope
  note, and the `PartyCard`/`SelectField`/`AppDialog` entries above) - each existing duplicate is
  named and flagged, not silently left undiscovered, but fixing all of them is out of proportion
  for a components-and-tokens phase.

## Testing

No dedicated Compose UI test suite exists in this project for any component (the only Compose test
file, `GreetingScreenshotTest`, is already among this project's 5 pre-existing, environment-caused
Robolectric failures - confirmed still failing for that same pre-existing reason after this phase's
changes, not a new one). Verification for this phase was: `compileDebugKotlin`/
`compileDebugUnitTestKotlin` clean, full `testDebugUnitTest` regression suite unchanged, and live
on-device screenshots (phone, tablet) confirming no visual regression where a value-identical token
substitution was made.
