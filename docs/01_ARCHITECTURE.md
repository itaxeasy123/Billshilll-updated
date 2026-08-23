# Architectural Blueprint & Clean Architecture Layers

```
ANDROID CLIENT (Kotlin + Jetpack Compose)
       │
  Use Cases & ViewModels (M3 Dynamic Theming, StateFlow)
       │
ACCOUNTING DOMAIN (DoubleEntryValidator, Ledger, Voucher, Invariants)
       │
 ┌─────┴─────────────────────────┐
 │                               │
ROOM DB (SQLite)            API CLIENT (Ktor / Retrofit)
 │                               │
OFFLINE CACHE               HTTPS / TLS 1.3
 │                               │
OUTBOX FIFO QUEUE                ▼
 │                     API SECURITY LAYER
 │               (OAuth2/JWT, RBAC, Tenant Guards)
 │                               │
 │                         PYTHON API (FastAPI)
 │                               │
 │                         DOMAIN SERVICES
 │                               │
 │                         SERVER DB (PostgreSQL)
 │                               │
 └──────────────────────►  SYNC ENGINE (Idempotent Ingestion)
                                 │
                                 ▼
                           AUTOMATION SUBSYSTEM
                         (Workers, Reports, Compliance,
                          Scheduled Jobs, Notifications)
```

## Layer Responsibilities

### 1. Presentation Layer (`presentation/`)
- Pure Jetpack Compose declarative UI.
- `AccountingViewModel` holding `StateFlow<AccountingUiState>` and `SharedFlow<String>` for user alerts.
- Adheres to Material Design 3 guidelines with high-contrast palette and accessibility standards.

### 2. Domain Layer (`domain/`)
- 100% pure Kotlin with zero Android framework dependencies.
- Contains core accounting models (`Ledger`, `AccountGroup`, `Voucher`, `JournalEntry`, `Company`, `FinancialYear`).
- Enforces strict double-entry arithmetic validation via `DoubleEntryValidator`.

### 3. Data Layer (`data/`)
- Room Database with SQLite backing.
- DAOs providing type-safe SQL operations scoped strictly by `companyId`.
- Repository coordinating local SQLite transactions and Outbox queuing.

### 4. Core Infrastructure (`core/`)
- `Money`: Integer minor unit math (paise) backed by `BigDecimal` for safe conversion.
- `SecureStorage`: Hardware-backed `EncryptedSharedPreferences` with Keystore AES-256 GCM.
- `NetworkMonitor`: Reactive network connectivity tracking.
- `OutboxProcessor`: FIFO mutation replication with exponential backoff and idempotency keys.

### 5. Automation Subsystem (`automation/`)
- Scheduled workers and compliance monitors checking Suspense balance, un-synced outbox mutations, and GST reconciliation.
