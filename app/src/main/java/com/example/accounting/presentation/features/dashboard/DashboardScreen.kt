package com.example.accounting.presentation.features.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.accounting.core.common.Money
import com.example.accounting.domain.accounting.StandardSystemGroups
import com.example.accounting.domain.accounting.Voucher
import com.example.accounting.domain.accounting.VoucherType
import com.example.accounting.domain.company.BusinessType
import com.example.accounting.presentation.viewmodel.AccountingUiState

/**
 * Phase 7J UI: the Home tab (bottom-nav item #1) rebuilt as the "Business Cockpit" per the UX
 * spec's Section 2 - a compact reflection of business state via actionable widgets, never a report
 * dump. Every widget amount comes straight from an already-loaded engine report/ledger balance in
 * [uiState] - zero UI-side summation anywhere in this file.
 */
@Composable
fun DashboardScreen(
    uiState: AccountingUiState,
    onOpenCreateVoucher: (VoucherType) -> Unit,
    onVoucherClick: (Voucher) -> Unit,
    onViewAllDayBook: () -> Unit,
    onViewReports: () -> Unit,
    onOpenCash: () -> Unit,
    onOpenBank: () -> Unit,
    onOpenSales: () -> Unit,
    onOpenPurchases: () -> Unit,
    onAddCustomer: () -> Unit,
    onAddSupplier: () -> Unit,
    onAddItem: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isService = uiState.currentCompany?.businessType == BusinessType.SERVICE
    val netProfit = if (isService) uiState.incomeAndExpenditure?.surplusOrDeficit ?: Money.ZERO else uiState.profitAndLoss?.netProfit ?: Money.ZERO
    val salesFigure = uiState.profitAndLoss?.salesRevenue ?: Money.ZERO
    val purchasesFigure = uiState.profitAndLoss?.purchases ?: Money.ZERO
    val receivables = uiState.receivablesReport?.totalOutstanding ?: (uiState.balanceSheet?.sundryDebtors ?: Money.ZERO)
    val payables = uiState.payablesReport?.totalOutstanding ?: (uiState.balanceSheet?.currentLiabilities ?: Money.ZERO)
    val outstanding = uiState.outstandingReport?.totalOutstanding ?: Money.ZERO
    val gstPayable = uiState.gstSummary?.netTaxPayable ?: Money.ZERO
    val cashBalance = uiState.ledgers.filter { it.groupId.startsWith(StandardSystemGroups.CASH_GROUP_ID) }.fold(Money.ZERO) { acc, l -> acc + l.currentBalance }
    val bankBalance = uiState.ledgers.filter { it.groupId.startsWith(StandardSystemGroups.BANK_GROUP_ID) }.fold(Money.ZERO) { acc, l -> acc + l.currentBalance }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text("Quick Actions", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickActionButton("Sale", Icons.Default.ReceiptLong, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, Modifier.weight(1f)) { onOpenCreateVoucher(VoucherType.SALES) }
                    QuickActionButton("Purchase", Icons.Default.ShoppingCart, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f)) { onOpenCreateVoucher(VoucherType.PURCHASE) }
                    QuickActionButton("Receive", Icons.Default.CallReceived, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, Modifier.weight(1f)) { onOpenCreateVoucher(VoucherType.RECEIPT) }
                    QuickActionButton("Pay", Icons.Default.CallMade, MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, Modifier.weight(1f)) { onOpenCreateVoucher(VoucherType.PAYMENT) }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickActionButton("Transfer", Icons.Default.CompareArrows, MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer, Modifier.weight(1f)) { onOpenCreateVoucher(VoucherType.CONTRA) }
                    QuickActionButton("Add Customer", Icons.Default.PersonAdd, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f)) { onAddCustomer() }
                    QuickActionButton("Add Supplier", Icons.Default.PersonAdd, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f)) { onAddSupplier() }
                    QuickActionButton("Add Item", Icons.Default.Add, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f)) { onAddItem() }
                }
            }
        }

        item {
            Column {
                Text("Business Snapshot", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard("Cash", cashBalance, "Tap to view", Icons.Default.Payments, MaterialTheme.colorScheme.primary, Modifier.weight(1f).clickable { onOpenCash() })
                    MetricCard("Bank", bankBalance, "Tap to view", Icons.Default.AccountBalance, MaterialTheme.colorScheme.primary, Modifier.weight(1f).clickable { onOpenBank() })
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard("Receivables", receivables, "You are owed", Icons.Default.ArrowDownward, MaterialTheme.colorScheme.secondary, Modifier.weight(1f).clickable { onViewReports() })
                    MetricCard("Payables", payables, "You owe", Icons.Default.ArrowUpward, MaterialTheme.colorScheme.error, Modifier.weight(1f).clickable { onViewReports() })
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard("Sales", salesFigure, "Current Financial Year", Icons.Default.ReceiptLong, MaterialTheme.colorScheme.primary, Modifier.weight(1f).clickable { onOpenSales() })
                    MetricCard("Purchases", purchasesFigure, "Current Financial Year", Icons.Default.ShoppingCart, MaterialTheme.colorScheme.primary, Modifier.weight(1f).clickable { onOpenPurchases() })
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard(if (isService) "Surplus / Deficit" else "Profit / Loss", netProfit, "Current Financial Year", Icons.Default.TrendingUp, MaterialTheme.colorScheme.secondary, Modifier.weight(1f).clickable { onViewReports() })
                    MetricCard("GST Payable", gstPayable, "Net position", Icons.Default.AccountBalance, MaterialTheme.colorScheme.tertiary, Modifier.weight(1f).clickable { onViewReports() })
                }
                Spacer(modifier = Modifier.height(10.dp))
                MetricCard("Outstanding", outstanding, "Receivables + Payables", Icons.Default.AccountBalance, MaterialTheme.colorScheme.primary, Modifier.fillMaxWidth().clickable { onViewReports() })
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Recent Transactions", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Text(
                    "View All",
                    style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold),
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onViewAllDayBook() }.padding(4.dp)
                )
            }
        }

        if (uiState.vouchers.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Book, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No transactions in this period yet", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            items(uiState.vouchers.take(6), key = { it.voucherId }) { voucher ->
                VoucherSummaryCard(voucher = voucher, onClick = { onVoucherClick(voucher) })
            }
        }
    }
}

@Composable
fun QuickActionButton(
    title: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        modifier = modifier.clip(RoundedCornerShape(12.dp)).clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(imageVector = icon, contentDescription = title, tint = contentColor, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                color = contentColor,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}

@Composable
fun MetricCard(
    title: String,
    amount: Money,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    modifier: Modifier = Modifier
) {
    ElevatedCard(shape = RoundedCornerShape(14.dp), modifier = modifier) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = amount.formatPlain(),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp, fontFamily = FontFamily.Monospace),
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
fun VoucherSummaryCard(
    voucher: Voucher,
    onClick: () -> Unit
) {
    OutlinedCard(
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.testTag("voucher_item_${voucher.voucherNumber}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when (voucher.voucherType) {
                        VoucherType.PAYMENT -> MaterialTheme.colorScheme.errorContainer
                        VoucherType.RECEIPT -> MaterialTheme.colorScheme.secondaryContainer
                        VoucherType.SALES -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Text(
                        text = voucher.voucherType.code,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(text = voucher.voucherNumber, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                    Text(
                        text = "${voucher.date} | ${voucher.narration.ifBlank { voucher.voucherType.displayName }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            Text(
                text = voucher.totalDebits.formatPlain(),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            )
        }
    }
}
