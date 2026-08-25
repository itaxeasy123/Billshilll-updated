package com.example.accounting.presentation.features.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.accounting.application.reports.HsnSacSummaryRow
import com.example.accounting.domain.accounting.Ledger
import com.example.accounting.domain.accounting.StandardSystemGroups
import com.example.accounting.domain.accounting.Voucher
import com.example.accounting.domain.accounting.VoucherType
import com.example.accounting.domain.company.BusinessType
import com.example.accounting.domain.reports.AgingBucket
import com.example.accounting.domain.reports.CashFlowReport
import com.example.accounting.domain.reports.OutstandingReport
import com.example.accounting.domain.reports.RatioAnalysisReport
import com.example.accounting.presentation.components.Amount
import com.example.accounting.presentation.components.SectionCard
import com.example.accounting.presentation.components.TableRow
import com.example.accounting.presentation.features.dashboard.VoucherSummaryCard
import com.example.accounting.presentation.viewmodel.AccountingUiState

private enum class ReportCategory(val label: String) { FINANCIAL("Financial"), SALES_PURCHASE("Sales/Purchase"), ACCOUNTS("Accounts"), GST("GST"), ANALYSIS("Analysis") }

/**
 * Phase 7J UI: the Reports Center (bottom-nav item #5) - a category-selector landing screen per
 * the UX spec's Section 13, not a flat tab bar. Every figure shown comes from an existing
 * `ReportManagementService`/`AccountingRepository` call, already loaded into [uiState] - this
 * screen never recomputes anything; Sales/Purchase Register and Cash/Bank Book/Receipt/Payment
 * Register are UI-side *filters* over already-fetched voucher/invoice lists, not new calculations.
 */
@Composable
fun ReportsCenterScreen(
    uiState: AccountingUiState,
    onOpenDayBook: () -> Unit,
    onOpenAllLedgers: () -> Unit,
    modifier: Modifier = Modifier
) {
    var category by remember { mutableStateOf<ReportCategory?>(null) }

    if (category == null) {
        LazyColumn(
            modifier = modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 40.dp)
        ) {
            item { Text("Reports Center", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)) }
            items(ReportCategory.entries) { cat ->
                SectionCard(onClick = { category = cat }, title = cat.label, trailing = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) }) {}
            }
        }
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { category = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Spacer(modifier = Modifier.width(4.dp))
            Text(category!!.label, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
        }

        when (category) {
            ReportCategory.FINANCIAL -> FinancialCategory(uiState)
            ReportCategory.SALES_PURCHASE -> SalesPurchaseCategory(uiState)
            ReportCategory.ACCOUNTS -> AccountsCategory(uiState, onOpenDayBook, onOpenAllLedgers)
            ReportCategory.GST -> GstCategory(uiState)
            ReportCategory.ANALYSIS -> AnalysisCategory(uiState)
            null -> {}
        }
    }
}

@Composable
private fun FinancialCategory(uiState: AccountingUiState) {
    var reportKey by remember { mutableStateOf<String?>(null) }
    if (reportKey == null) {
        ReportMenu(
            listOf("Trial Balance" to true, "Profit & Loss" to true, "Balance Sheet" to true, "Cash Flow" to true, "Fund Flow" to false)
        ) { reportKey = it }
        return
    }
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        BackRow(reportKey!!) { reportKey = null }
        when (reportKey) {
            "Trial Balance" -> TrialBalanceView(report = uiState.trialBalance)
            "Profit & Loss" -> if (uiState.currentCompany?.businessType == BusinessType.SERVICE) {
                IncomeAndExpenditureView(report = uiState.incomeAndExpenditure)
            } else {
                ProfitAndLossView(report = uiState.profitAndLoss)
            }
            "Balance Sheet" -> BalanceSheetView(report = uiState.balanceSheet)
            "Cash Flow" -> CashFlowView(report = uiState.cashFlowReport)
        }
    }
}

@Composable
private fun CashFlowView(report: CashFlowReport?) {
    if (report == null) { EmptyReportState(); return }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 40.dp)) {
        item { TableRow("Net Profit", money = report.netProfit) }
        item { TableRow("Change in Current Assets (excl. Cash)", money = report.changeInCurrentAssetsExcludingCash) }
        item { TableRow("Change in Current Liabilities", money = report.changeInCurrentLiabilities) }
        item { TableRow("Net Cash from Operating Activities", money = report.netCashFromOperatingActivities, emphasize = true) }
        item { TableRow("Opening Cash & Bank", money = report.openingCashAndBank) }
        item { TableRow("Closing Cash & Bank", money = report.closingCashAndBank) }
        item { TableRow("Net Change in Cash & Bank", money = report.netChangeInCashAndBank, emphasize = true) }
    }
}

@Composable
private fun SalesPurchaseCategory(uiState: AccountingUiState) {
    var reportKey by remember { mutableStateOf<String?>(null) }
    if (reportKey == null) {
        ReportMenu(listOf("Sales Register" to true, "Purchase Register" to true, "Outstanding Receivables" to true, "Outstanding Payables" to true)) { reportKey = it }
        return
    }
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        BackRow(reportKey!!) { reportKey = null }
        when (reportKey) {
            "Sales Register" -> VoucherRegisterList(uiState.vouchers.filter { it.voucherType == VoucherType.SALES })
            "Purchase Register" -> VoucherRegisterList(uiState.vouchers.filter { it.voucherType == VoucherType.PURCHASE })
            "Outstanding Receivables" -> OutstandingList(uiState.receivablesReport)
            "Outstanding Payables" -> OutstandingList(uiState.payablesReport)
        }
    }
}

@Composable
private fun AccountsCategory(uiState: AccountingUiState, onOpenDayBook: () -> Unit, onOpenAllLedgers: () -> Unit) {
    var reportKey by remember { mutableStateOf<String?>(null) }
    if (reportKey == null) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
            item { SectionCard(onClick = onOpenAllLedgers, title = "All Ledgers") {} }
            item { SectionCard(onClick = onOpenDayBook, title = "Day Book") {} }
            item { SectionCard(onClick = { reportKey = "Cash Book" }, title = "Cash Book") {} }
            item { SectionCard(onClick = { reportKey = "Bank Book" }, title = "Bank Book") {} }
            item { SectionCard(onClick = { reportKey = "Receipt Register" }, title = "Receipt Register") {} }
            item { SectionCard(onClick = { reportKey = "Payment Register" }, title = "Payment Register") {} }
        }
        return
    }
    val cashLedgerIds = remember(uiState.ledgers) { uiState.ledgers.filter { it.groupId.startsWith(StandardSystemGroups.CASH_GROUP_ID) }.map { it.ledgerId }.toSet() }
    val bankLedgerIds = remember(uiState.ledgers) { uiState.ledgers.filter { it.groupId.startsWith(StandardSystemGroups.BANK_GROUP_ID) }.map { it.ledgerId }.toSet() }
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        BackRow(reportKey!!) { reportKey = null }
        when (reportKey) {
            "Cash Book" -> VoucherRegisterList(uiState.vouchers.filter { v -> v.items.any { it.ledgerId in cashLedgerIds } })
            "Bank Book" -> VoucherRegisterList(uiState.vouchers.filter { v -> v.items.any { it.ledgerId in bankLedgerIds } })
            "Receipt Register" -> VoucherRegisterList(uiState.vouchers.filter { it.voucherType == VoucherType.RECEIPT })
            "Payment Register" -> VoucherRegisterList(uiState.vouchers.filter { it.voucherType == VoucherType.PAYMENT })
        }
    }
}

@Composable
private fun GstCategory(uiState: AccountingUiState) {
    var reportKey by remember { mutableStateOf<String?>(null) }
    if (reportKey == null) {
        ReportMenu(listOf("GST Summary" to true, "HSN/SAC Summary" to true)) { reportKey = it }
        return
    }
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        BackRow(reportKey!!) { reportKey = null }
        when (reportKey) {
            "GST Summary" -> GSTCenterView(report = uiState.gstSummary)
            "HSN/SAC Summary" -> HsnSacSummaryView(uiState.hsnSacSummary)
        }
    }
}

@Composable
private fun HsnSacSummaryView(rows: List<HsnSacSummaryRow>) {
    if (rows.isEmpty()) { EmptyReportState(); return }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 40.dp)) {
        items(rows, key = { it.hsnSacCode }) { row ->
            SectionCard(title = row.hsnSacCode, subtitle = "${row.transactionCount} transaction(s)") {
                TableRow("Outward Taxable", money = row.outwardTaxableAmount)
                TableRow("Inward Taxable", money = row.inwardTaxableAmount)
                TableRow("CGST + SGST + IGST", money = row.totalCgst + row.totalSgst + row.totalIgst)
                if (row.totalCess.isPositive) TableRow("CESS", money = row.totalCess)
            }
        }
    }
}

@Composable
private fun AnalysisCategory(uiState: AccountingUiState) {
    var reportKey by remember { mutableStateOf<String?>(null) }
    if (reportKey == null) {
        ReportMenu(listOf("Ratio Analysis" to true, "CMA" to false, "Advanced Reports" to false)) { reportKey = it }
        return
    }
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        BackRow(reportKey!!) { reportKey = null }
        if (reportKey == "Ratio Analysis") RatioAnalysisView(uiState.ratioAnalysisReport)
    }
}

@Composable
private fun RatioAnalysisView(report: RatioAnalysisReport?) {
    if (report == null) { EmptyReportState(); return }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 40.dp)) {
        item { TableRow("Current Ratio", "%.2f".format(report.currentRatio)) }
        item { TableRow("Quick Ratio", "%.2f".format(report.quickRatio)) }
        item { TableRow("Debt-Equity Ratio", "%.2f".format(report.debtEquityRatio)) }
        item { TableRow("Gross Profit Ratio", "%.2f%%".format(report.grossProfitRatioPercent)) }
        item { TableRow("Net Profit Ratio", "%.2f%%".format(report.netProfitRatioPercent)) }
        item { TableRow("Operating Ratio", "%.2f%%".format(report.operatingRatioPercent)) }
        item { TableRow("Return on Capital Employed", "%.2f%%".format(report.returnOnCapitalEmployedPercent)) }
    }
}

private fun AgingBucket.displayLabel(): String = when (this) {
    AgingBucket.CURRENT -> "Not yet due"
    AgingBucket.DAYS_1_30 -> "1-30 days overdue"
    AgingBucket.DAYS_31_60 -> "31-60 days overdue"
    AgingBucket.DAYS_61_90 -> "61-90 days overdue"
    AgingBucket.DAYS_90_PLUS -> "Over 90 days overdue"
}

@Composable
private fun OutstandingList(report: OutstandingReport?) {
    if (report == null || report.rows.isEmpty()) { EmptyReportState(); return }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 40.dp)) {
        item { TableRow("Total Outstanding", money = report.totalOutstanding, emphasize = true) }
        items(report.rows, key = { it.invoiceId }) { row ->
            SectionCard(
                title = row.partyName,
                subtitle = "${row.voucherNumber} • ${row.agingBucket.displayLabel()}",
                trailing = { Amount(row.outstandingAmount, style = MaterialTheme.typography.titleSmall, emphasize = true) }
            ) {}
        }
    }
}

@Composable
private fun VoucherRegisterList(vouchers: List<Voucher>) {
    if (vouchers.isEmpty()) { EmptyReportState(); return }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 40.dp)) {
        items(vouchers.sortedByDescending { it.date }, key = { it.voucherId }) { voucher ->
            VoucherSummaryCard(voucher = voucher, onClick = {})
        }
    }
}

@Composable
private fun ReportMenu(reports: List<Pair<String, Boolean>>, onSelect: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 40.dp)
    ) {
        items(reports) { (name, available) ->
            SectionCard(
                onClick = if (available) ({ onSelect(name) }) else null,
                title = name,
                subtitle = if (!available) "Coming soon" else null
            ) {}
        }
    }
}

@Composable
private fun BackRow(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
        Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun EmptyReportState() {
    Text("No data yet for this report.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp))
}
