# 08. Voucher & Document Type Classifications

## Document Categories

### 1. FINANCIAL_POSTING
Directly posts balanced double-entry debits/credits to the General Ledger:
- `PAYMENT`: Bank/Cash disbursements.
- `RECEIPT`: Bank/Cash collections.
- `CONTRA`: Cash-to-bank or bank-to-bank transfers.
- `JOURNAL`: Non-cash adjustments, depreciation, accruals.
- `SALES`: Customer tax invoices (affects debtors, revenue, GST output).
- `PURCHASE`: Vendor bills (affects creditors, expenses/assets, GST input).
- `CREDIT_NOTE`: Sales returns / rate reductions.
- `DEBIT_NOTE`: Purchase returns / cost escalations.
- `REVERSING_JOURNAL`: Provisional entries with automatic reversal dates.
- `STOCK_JOURNAL`: Inventory valuation & manufacturing adjustments.

### 2. INVENTORY_ONLY
Physical stock movements without immediate ledger posting:
- `DELIVERY_NOTE` (Challan): Outward goods dispatch.
- `RECEIPT_NOTE` (GRN): Inward goods receipt from vendor.

### 3. NON_POSTING
Commercial commitments prior to execution:
- `QUOTATION` (EST): Price estimates for prospects.
- `PROFORMA_INVOICE` (PI): Advance billing proformas before goods delivery.
- `SALES_ORDER` (SO): Confirmed customer order bookings.
- `PURCHASE_ORDER` (PO): Confirmed supplier purchase orders.
- `MEMORANDUM` (MEM): Reminder or notes with zero financial or stock impact.

### 4. CONDITIONAL_POSTING
- Provisional vouchers requiring secondary management approval before posting to the general ledger.
