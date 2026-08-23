# 01. Architecture

## WHAT
The multi-layered architecture diagram and component specification for the Enterprise Accounting Platform across Android (Client) and Python (Backend Service).

## WHY
Separation of concerns ensures business logic remains independent of UI components, database engines, and network protocols, guaranteeing high testability and enterprise maintainability.

## ARCHITECTURE LAYERS
```
┌────────────────────────────────────────────────────────┐
│                   Presentation Layer                   │
│   (Jetpack Compose Screens, HashRouter, ViewModels)    │
└───────────────────────────┬────────────────────────────┘
                            │ StateFlow / Actions
┌───────────────────────────▼────────────────────────────┐
│                      Domain Layer                      │
│ (Ledger, Voucher, DoubleEntryValidator, Tax, Reports)  │
└───────────────────────────┬────────────────────────────┘
                            │ Domain Models / Contracts
┌───────────────────────────▼────────────────────────────┐
│                       Data Layer                       │
│    (AccountingRepository, Room DAOs, OutboxProcessor)  │
└───────────────────────────┬────────────────────────────┘
                            │ Entities / SQLite
┌───────────────────────────▼────────────────────────────┐
│                       Core Layer                       │
│   (Money, AppDatabase, Converters, DatabaseTransaction,│
│           SecureStorage, NetworkMonitor)               │
└────────────────────────────────────────────────────────┘
```

## RULES
- Domain models must never import Android framework classes (`android.*` or `androidx.*`).
- Repositories mediate between local database entities and pure domain models.
- Transactions are managed through `DatabaseTransaction` within Room for atomic integrity.

## DEPENDENCIES
- Room Database, Kotlin Coroutines & Flow, Jetpack Security (`EncryptedSharedPreferences`).

## WHAT CAN CHANGE
- Local storage caching strategies and network serialization libraries.

## WHAT MUST NOT CHANGE
- The unidirectional data flow pattern (ViewModel -> UseCase/Repo -> DB/Outbox).
