# 23. Room Database Migration Strategy

## Invariant 21 (Non-Destructive Migrations)
- Production accounting databases MUST NEVER use `fallbackToDestructiveMigration()`.
- Wiping local databases causes loss of un-synced outbox entries and historical ledgers.

## Migration Rules & Lifecycle
1. Every schema modification requires an explicit `Migration(startVersion, endVersion)` implementation in `AppDatabase.ALL_MIGRATIONS`.
2. Migrations must use standard SQL `ALTER TABLE`, `CREATE TABLE`, and copy-transform strategies.
3. Automated JVM migration tests using `MigrationTestHelper` must verify that data is preserved when upgrading between versions.
