# 20. Offline-First Architecture

## Operational Principles
1. **Local-First Writes**: Android Room SQLite is the authoritative write target for all interactive UI operations. The user never waits for network round-trips.
2. **Network Decoupling**: App operates fully offline with complete double-entry validation, ledger updates, and balance sheets computed locally.
3. **Reactive Connectivity**: `NetworkMonitor` detects transitions to active Wi-Fi/Cellular and triggers background outbox draining.
