# 02. Company Tenant Boundary Specification

## Overview
The `Company` entity defines the root boundary for multi-tenant isolation. All financial data, accounts, vouchers, audit logs, and stock entities belong strictly to one company.

## Invariants & Rules
1. **Explicit Company Scoping**: Every database query, transaction, and outbox sync payload MUST specify an explicit `companyId`.
2. **Tenant Data Isolation**: Operations across different company IDs in a single transaction or journal posting are rejected with `AppError.TenantMismatch`.
3. **Provisioning**: When a company is created, the system automatically seeds:
   - Default Financial Year (April 1 to March 31).
   - 12 Monthly Accounting Periods.
   - 28 Standard System Groups.
   - Core System Ledgers including `LED_SYS_SUSPENSE`, `Cash-in-hand`, and `Profit & Loss A/c`.
