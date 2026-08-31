package com.example.accounting.domain.company

/**
 * Whether this company's accounting engine tracks physical inventory alongside ledgers.
 * Switching modes is a capability toggle only (Phase 4 spec, "switching modes" rule) - it must
 * never delete or hide underlying voucher/stock-movement/audit history. ACCOUNT_ONLY simply
 * means the inventory-aware report figures (COGS, Stock-in-Hand) are omitted/zero and inventory
 * UI stays hidden; existing stock data, if any, is retained untouched.
 */
enum class AccountingMode { ACCOUNT_ONLY, ACCOUNT_WITH_INVENTORY }

/**
 * Which financial-statement model a company presents. TRADING keeps the Gross Profit/COGS
 * structure; SERVICE presents Income & Expenditure (Income - Expenditure = Surplus/Deficit)
 * instead. Independent of [AccountingMode] - a SERVICE company is not assumed to have no
 * inventory use case, so the two toggles are never conflated into one flag.
 */
enum class BusinessType { TRADING, SERVICE }

/**
 * D1a (Company Mode + Account-Only Sale/Purchase) - the company's GST-posting mode, orthogonal to
 * both [AccountingMode] (inventory tracking on/off) and [Company.gstEnabled] (the pre-existing,
 * narrower "show the GST Return Dashboard as registered" display flag - left untouched, never
 * repurposed here, since overloading it would silently change what it has always meant).
 *
 * - `ACCOUNT_ONLY`: no GST is calculated or recorded on any transaction.
 * - `ACCOUNT_WITH_GST`: ordinary accounting-integrated Sale/Purchase/Notes, with GST computed by
 *   [com.example.accounting.domain.taxation.gst.GstCalculationEngine] exactly as today - the
 *   default, matching every existing company's unchanged current behavior.
 * - `GST_ONLY`: reserved for the D1b/D1c phases (the already-implemented but UI-less
 *   [com.example.accounting.domain.trading.TradingWorkflowEngine.buildGstOnlySale] path). Persisted
 *   here from D1a onward so a later phase never has to add a migration just to store the user's
 *   choice - but D1a itself reads this field nowhere in the posting path.
 *
 * Deliberately NOT the same axis as [AccountingMode]: whether a company tracks physical inventory
 * (Item/Quantity/Warehouse) and whether it applies GST are independent business facts - a
 * non-inventory service company can still be `ACCOUNT_WITH_GST`, and an inventory-tracking trading
 * company could in principle be `ACCOUNT_ONLY`. D1a's actual Item-requirement fix reads
 * [AccountingMode], not this field.
 */
enum class GstOperatingMode { ACCOUNT_ONLY, ACCOUNT_WITH_GST, GST_ONLY }
