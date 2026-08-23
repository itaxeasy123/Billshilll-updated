# 06. Ledger Account Architecture

## Overview
A `Ledger` represents an individual general ledger account.

## Invariants & Field Specs
- `ledgerId`: String primary key scoped to company.
- `groupId`: Parent group reference.
- `openingBalancePaise`: 64-bit integer paise.
- `openingBalanceType`: `DEBIT` or `CREDIT`.
- `currentBalancePaise`: Real-time accumulated running balance.
- `isSystem`: Boolean flag protecting core accounts (`LED_SYS_SUSPENSE`, `LED_SYS_CASH`, `LED_SYS_PNL`).

## Deletion Constraints
- Deletion allowed **ONLY** when `accountingEntryCount == 0` and `isSystem == false`.
- If active journal entries exist, deletion is strictly rejected to preserve historical accounting integrity.
