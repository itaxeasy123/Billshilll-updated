# 02. Company & Branch Model

## WHAT
The primary multi-tenant entity representing legal entities, corporate hierarchies, and branch locations.

## WHY
Accounting records cannot mix across distinct legal entities or branches without explicit cross-company inter-branch clearing transactions.

## RULES
- `companyId` is a required foreign key on all groups, ledgers, vouchers, items, periods, and sync records.
- Standard system groups and default chart of accounts (including Suspense A/c) are automatically provisioned upon company creation.
- A company may have multiple branches with unique GSTINs and physical addresses.

## STRUCTURE
```kotlin
data class Company(
    val companyId: String,
    val legalName: String,
    val tradeName: String,
    val gstin: String,
    val pan: String,
    val currencyCode: String = "INR",
    val isMultiBranch: Boolean = false,
    val isGstRegistered: Boolean = true
)
```

## WHAT MUST NOT CHANGE
- Strict company isolation on all queries (`WHERE companyId = :companyId`).
