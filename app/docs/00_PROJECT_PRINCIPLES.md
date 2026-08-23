# 00. Project Principles

## WHAT
This document defines the fundamental, non-negotiable architectural and mathematical principles governing the Enterprise Accounting Platform.

## WHY
Accounting systems are strict deterministic ledgers. An error in currency precision, transaction atomicity, or double-entry validation can corrupt financial records, create regulatory non-compliance, or result in irrecoverable fiscal damage.

## RULES
1. **Mathematical Exactness**: Monetary values are stored as 64-bit integer paise (sub-units) backed by `BigDecimal` for zero floating-point imprecision (`RoundingMode.HALF_EVEN`).
2. **Deterministic Double-Entry**: Every posting must satisfy $\sum \text{Debit} = \sum \text{Credit}$.
3. **Tenant & Boundary Isolation**: Every query, mutation, and ledger operation is scoped strictly by `companyId` and `financialYearId`.
4. **Offline-First Resilience**: All business transactions can be originated locally without an active network connection, stored in an immutable outbox queue, and synchronized idempotently.
5. **No Destructive Mutations**: Historical transactions and ledger mutations are audited; vouchers are deleted only via atomic reversing compensating transactions.
6. **Auditability**: Every write operation generates a verifiable `AuditLogEntity` with user identifier, timestamp, and entity payload.

## DEPENDENCIES
- Kotlin standard library, Jetpack Room, SQLite, and Python FastAPI backend services.

## WHAT CAN CHANGE
- UI layouts, theme styles, export report formatting (PDF, Excel, CSV), and auxiliary visual graphs.

## WHAT MUST NOT CHANGE
- The double-entry invariant ($\sum \text{Dr} == \sum \text{Cr}$).
- Zero floating-point arithmetic rules for monetary calculations.
- Company tenant isolation boundaries.

## TEST REQUIREMENTS
- Comprehensive unit tests verifying double-entry rejection when unbalanced.
- Verification of integer paise conversions to `BigDecimal` across arithmetic operations.

## FUTURE EXTENSIONS
- Multi-currency ledger support with official exchange rate gain/loss postings.
