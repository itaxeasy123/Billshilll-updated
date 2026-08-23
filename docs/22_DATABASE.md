# 22. SQLite Room Database Schema

## Registered Entities
1. `CompanyEntity`: Multi-tenant boundary.
2. `BranchEntity`: Multi-branch tracking under company.
3. `FinancialYearEntity`: Accounting year bounds.
4. `AccountingPeriodEntity`: Monthly periods and locking status.
5. `GroupEntity`: 28 System groups and custom user groups.
6. `LedgerEntity`: General ledger accounts with running balance in paise.
7. `VoucherEntity`: Transaction headers and voucher metadata.
8. `JournalItemEntity`: Debit/Credit entry lines linked to vouchers and ledgers.
9. `StockItemEntity`: Inventory SKUs and valuation costs.
10. `AuditLogEntity`: Immutable MCA audit logs.
11. `OutboxSyncEntity`: Pending sync mutations queue.

## Indexing Strategy
- Composite indexes on `(companyId, ledgerId)`, `(voucherId, date)`, and `(companyId, syncState)`.
- Foreign key constraints preventing dangling journal items.
