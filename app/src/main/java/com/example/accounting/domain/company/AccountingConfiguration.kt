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
