# 05. Group Hierarchy Architecture

## 1. Primary Classification Roots
1. **ASSETS** (Debit Normal Balance)
2. **LIABILITIES** (Credit Normal Balance)
3. **EQUITY** (Credit Normal Balance)
4. **INCOME** (Credit Normal Balance)
5. **EXPENSES** (Debit Normal Balance)
6. **SPECIAL_CONTROL** (Dynamic Dr/Cr Balance for System Control Accounts)

## 2. System Groups (28 Standard Groups)
Pre-seeded with `isSystem = true`. System groups cannot be deleted, reparented, or reclassified.
- **Assets (8)**: Bank Accounts, Cash-in-Hand, Sundry Debtors, Fixed Assets, Current Assets, Stock-in-Hand, Deposits (Asset), Investments.
- **Liabilities (6)**: Sundry Creditors, Duties & Taxes, Current Liabilities, Loans (Liability), Bank OD A/c, Provisions.
- **Equity (2)**: Capital Account, Reserves & Surplus.
- **Income (3)**: Sales Accounts, Direct Incomes, Indirect Incomes.
- **Expenses (8)**: Purchase Accounts, Direct Expenses, Indirect Expenses, Administrative Expenses, Selling & Distribution Expenses, Financial Charges, Depreciation & Amortization, Employee Benefits & Salaries.
- **Special Control (1)**: Suspense A/c (`GRP_SYS_SUSPENSE`).

## 3. Dedicated Suspense Control Architecture
- **Control Group**: `GRP_SYS_SUSPENSE_<companyId>` (`SPECIAL_CONTROL`)
- **System Ledger**: `LED_SYS_SUSPENSE_<companyId>`
- Suspense belongs strictly to `GRP_SYS_SUSPENSE` and never directly under operational liability groups (`GRP_CURRENT_LIAB`).

## 4. User Groups
Custom user-defined subgroups inheriting the primary group classification of their parent. Cycle-detection ensures acyclic tree graphs (`A -> B -> A` forbidden).
