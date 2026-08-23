# Enterprise Double-Entry Accounting Platform (Offline-First, Cloud-Ready)

A robust, enterprise-grade double-entry accounting platform engineered in Kotlin with Jetpack Compose for Android and an asynchronous Python/FastAPI backend architecture. Designed with strict mathematical guarantees, non-negotiable accounting invariants, idempotent transaction execution, and bidirectional offline-first data synchronization.

---

## 1. Core Accounting Invariants

1. **Company is the Tenant Boundary**: Every ledger, voucher, group, and journal is strictly isolated by `companyId`. Cross-tenant data leakage is prevented by design.
2. **Financial Year is the Accounting Boundary**: All financial activities belong to a discrete fiscal period bounded by start and end dates.
3. **Period Locking**: Locked or audit-closed accounting periods reject all mutation attempts.
4. **Balanced Double-Entry**: Every posted voucher enforces `Total Debit Paired == Total Credit Paired` (down to integer paise / `BigDecimal` scale).
5. **No Mixed Lines**: A single journal item cannot simultaneously contain both debit and credit amounts.
6. **Immutable Ledger Postings**: Historical postings cannot be mutated directly; reversals are executed atomically through compensating vouchers.
7. **Protected Ledgers**: Ledgers with posted journal transactions cannot be deleted. Only zero-activity ledgers may be pruned.
8. **Permanent Suspense Account**: The Suspense A/c (`LED_SYS_SUSPENSE`) is a permanent fixture in the chart of accounts and cannot be deleted or bypassed.
9. **Idempotent Operations**: All voucher creations, updates, and sync operations require an idempotency key to prevent duplicate execution under retries.
10. **Offline-Online Rule Symmetry**: The identical double-entry rules, validation checks, and integrity constraints are enforced on local devices and the remote API.

---

## 2. Directory Layouts

### Android (Kotlin / Compose) Architecture
```
app/src/main/java/com/example/accounting/
├── automation/
│   ├── compliance/            # GST & Suspense compliance auditors
│   ├── jobs/                  # Daily, monthly, yearly, and event-driven automation jobs
│   ├── notifications/         # Real-time event notification pipeline
│   ├── reports/               # Scheduled automated financial reporting
│   ├── scheduler/             # AccountingScheduler coordinator
│   ├── tasks/                 # Task contracts and execution status models
│   └── workers/               # Background task workers
├── core/
│   ├── common/                # Money (BigDecimal/Paise), DrCr, Result wrappers
│   ├── database/              # Room AppDatabase, Converters, DatabaseTransaction
│   ├── network/               # Reactive NetworkMonitor (ConnectivityManager)
│   ├── security/              # SecureStorage (EncryptedSharedPreferences)
│   └── sync/                  # OutboxProcessor, FIFO queue, Conflict Resolver
├── data/
│   ├── local/
│   │   ├── dao/               # AccountingDao with Room queries
│   │   └── entity/            # Room database entities (SQLite tables)
│   └── repository/            # AccountingRepository implementation
├── domain/
│   ├── accounting/            # Ledger, Group, Voucher, JournalEntry, DoubleEntryValidator
│   ├── audit/                 # Audit trail models and actions
│   ├── company/               # Company and Branch domain models
│   ├── financialyear/         # FinancialYear and AccountingPeriod models
│   ├── inventory/             # Stock items, warehouses, valuation engine (FIFO/Weighted Avg)
│   ├── reports/               # Trial Balance, P&L, Balance Sheet engines
│   └── taxation/              # GST calculators, tax rates, ITC calculation
└── presentation/
    ├── navigation/            # HashRouter and WindowSizeClass responsive layout
    ├── ui/                    # Jetpack Compose screens, theme, and components
    └── viewmodel/             # StateFlow ViewModels and reactive UI state
```

### Python Backend Service Layout
```
backend/
├── app/
│   ├── api/
│   │   ├── v1/
│   │   │   ├── endpoints/     # /companies, /ledgers, /vouchers, /sync, /reports
│   │   │   └── router.py      # Main API router
│   │   ├── dependencies.py    # Tenant context, auth validation, DB sessions
│   │   └── errors.py          # Structured error handlers (Rule 39)
│   ├── core/
│   │   ├── config.py          # Environment variables & secrets
│   │   ├── logging.py         # Structured JSON logging with request correlation IDs
│   │   └── security.py        # JWT token verification & permission validation
│   ├── db/
│   │   ├── base.py            # SQLAlchemy DeclarativeBase
│   │   ├── session.py         # Async database connection pool
│   │   └── migrations/        # Alembic versioned migration scripts
│   ├── domain/
│   │   ├── accounting/        # Double-entry verification rules & models
│   │   ├── reports/           # Financial calculation engine
│   │   └── sync/              # Remote Outbox ingestion & conflict resolution
│   ├── workers/
│   │   ├── celery_app.py      # Celery / Redis task queue configuration
│   │   └── tasks/             # Scheduled reconciliation, GST preparation, backup jobs
│   └── main.py                # FastAPI ASGI application entrypoint
```

---

## 3. Core Modules & Invariants Documentation

Full specifications are located in the `/docs` directory:
- [00_PROJECT_PRINCIPLES.md](docs/00_PROJECT_PRINCIPLES.md): Foundational design tenets and mathematical rules.
- [01_ARCHITECTURE.md](docs/01_ARCHITECTURE.md): Android & Backend Clean Architecture breakdown.
- [02_COMPANY.md](docs/02_COMPANY.md): Tenant boundary, hierarchy, and branch structures.
- [03_FINANCIAL_YEAR.md](docs/03_FINANCIAL_YEAR.md): Fiscal boundary, year-end roll forwards, and opening balances.
- [04_ACCOUNTING_PERIOD.md](docs/04_ACCOUNTING_PERIOD.md): Period status, soft locks, and audit locks.
- [05_GROUP_HIERARCHY.md](docs/05_GROUP_HIERARCHY.md): 4 Primary Groups (Assets, Liabilities, Incomes, Expenses) and sub-trees.
- [06_LEDGER.md](docs/06_LEDGER.md): Ledger accounts, current balance tracking, and deletion constraints.
- [07_OPENING_BALANCE.md](docs/07_OPENING_BALANCE.md): Opening trial balance balancing and difference allocation to Suspense.
- [08_VOUCHER_TYPES.md](docs/08_VOUCHER_TYPES.md): Standard vouchers (Receipt, Payment, Journal, Contra, Sales, Purchase, Tax Invoice, Debit/Credit Note).
- [09_VOUCHER.md](docs/09_VOUCHER.md): Voucher anatomy, items, and posting lifecycle.
- [10_DOUBLE_ENTRY.md](docs/10_DOUBLE_ENTRY.md): Mathematical verification of `Sum(Debit) == Sum(Credit)` with `BigDecimal` precision.
- [11_POSTING_ENGINE.md](docs/11_POSTING_ENGINE.md): Atomic double-entry write pipeline and ledger balance updates.
- [12_DELETION_POLICY.md](docs/12_DELETION_POLICY.md): Controlled atomic reversal policies.
- [13_SUSPENSE_ACCOUNT.md](docs/13_SUSPENSE_ACCOUNT.md): Zero-balance tracking and resolution workflow.
- [20_OFFLINE_ONLINE.md](docs/20_OFFLINE_ONLINE.md): Offline storage and synchronization strategies.
- [21_SYNC.md](docs/21_SYNC.md): Outbox queue processing, idempotency, and conflict mitigation.
- [24_SECURITY.md](docs/24_SECURITY.md): EncryptedSharedPreferences and credential storage.
- [27_AUTOMATION.md](docs/27_AUTOMATION.md): Automation tasks, safety boundaries, and compliance checkers.
