# 17. Inventory Architecture & Valuation

## Stock Item Entity
- `itemId`, `companyId`, `name`, `sku`, `unitOfMeasure`, `hsnCode`, `taxRatePercent`.
- `currentStockQuantity`, `standardCostPaise`, `sellingPricePaise`.

## Valuation Methodologies
- FIFO (First-In, First-Out) and Weighted Average Costing.
- Integration with Stock Journals and Sales/Purchase voucher items.

## Cancellation & Reversal Requirements
- When a Sales Invoice or Purchase Bill is cancelled:
  1. Linked physical stock movements MUST be reversed atomically.
  2. Warehouse bin stock quantities are credited/debited back to previous states.
  3. Cost of Goods Sold (COGS) adjustments are updated in the inventory valuation register.
