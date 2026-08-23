# 28. Testing Strategy & CI Verification

## Test Layers
1. **Domain Unit Tests (`test/domain/`)**:
   - Double-entry balance equality and rejection of unbalanced entries.
   - Non-zero amount checks.
   - Financial year date boundary containment.
   - Accounting period locking rejection.
   - Suspense account protection.
2. **Repository & Isolation Tests (`test/data/`)**:
   - Tenant isolation: Cross-company ledger insertions are blocked.
   - Ledger deletion rules: Permitted with 0 entries, blocked with $\ge 1$ entries.
   - Atomic posting: Verification of rollbacks on failed line insertions.
   - Idempotent sync outbox processing.
3. **Database Migration Tests (`test/database/`)**:
   - Verification of non-destructive schema migrations across version bumps.
4. **Financial Statement Tests (`Phase3TestSuite`)**:
   - Trial Balance: opening balances, multi-voucher aggregation, debit/credit/zero balances, cancelled-voucher netting, historical inclusion after period lock, company/FY isolation, recursive group-hierarchy aggregation with no double counting.
   - P&L: Sales/Direct/Indirect Income, Direct/Indirect Expense, Purchase Accounts tracked distinctly, net profit and net loss (sign preserved), cancelled-voucher effect, company/FY isolation, Suspense and Balance-Sheet-only ledgers excluded.
   - Balance Sheet: Asset/Liability/Capital/Reserves balances, current-year profit and loss (equation still holds), Suspense Dr/Cr presentation, company/FY isolation, the Assets = Liabilities + Equity equation, no parent/child double counting.
   - Invariants: cyclic group relationships rejected as a structured error instead of infinite recursion; an unbalanced statement throws a structured error instead of being silently returned.

## Test Execution Policy
- Tests are never modified to make a broken implementation pass; a failing assertion is only "fixed" by correcting either a genuine test bug (wrong fixture/expected value) or the actual source defect it found - never by loosening the assertion.
- A test run is only reported as passing if Gradle actually executed it (`./gradlew testDebugUnitTest`, JUnit XML/HTML under `app/build/test-results` / `app/build/reports/tests` inspected directly) - never assumed from compilation success alone.

## Known Infrastructure Limitation: Robolectric
`SuspenseControlArchitectureTest` (and the unrelated boilerplate `ExampleRobolectricTest`, `GreetingScreenshotTest`) require Robolectric's Android SDK provisioning, which currently fails in this environment (`UnsupportedOperationException` at `DefaultSdkProvider.java:170`). This is a standing environment/infrastructure issue, tracked separately from accounting correctness - it is never reinterpreted as an accounting test failure, and accounting logic that would otherwise depend on Robolectric (e.g. genuine SQLite-transaction rollback verification) is instead tested by exercising the Room-independent engine logic (`VoucherPostingEngine`, `AccountingRepository` with `db = null`) directly against a fake DAO.
