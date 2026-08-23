# 08. Voucher Types

## WHAT
The catalog of transaction archetypes supported by the accounting engine.

## SUPPORTED TYPES
1. **RECEIPT**: Inflow of funds into Bank or Cash accounts against party or income ledgers.
2. **PAYMENT**: Outflow of funds from Bank or Cash accounts to suppliers or expense ledgers.
3. **CONTRA**: Internal transfers between Cash and Bank or between two Bank accounts.
4. **JOURNAL**: Non-cash adjustments, depreciation, opening entries, and year-end close entries.
5. **SALES / TAX_INVOICE**: Revenue transactions with line-item GST calculations and debtor receivables.
6. **PURCHASE**: Procurement of inventory or services with supplier payables and Input Tax Credit (ITC).
7. **CREDIT_NOTE**: Sales return or customer concession reversing revenue and output tax.
8. **DEBIT_NOTE**: Purchase return or vendor debit adjustment reversing input tax.

## WHAT MUST NOT CHANGE
- Strict type validation ensuring Contra vouchers only involve Cash/Bank ledgers.
