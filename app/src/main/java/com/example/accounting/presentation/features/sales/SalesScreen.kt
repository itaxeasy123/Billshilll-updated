package com.example.accounting.presentation.features.sales

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.accounting.domain.accounting.Ledger
import com.example.accounting.domain.accounting.Voucher
import com.example.accounting.domain.accounting.VoucherType
import com.example.accounting.domain.party.Party
import com.example.accounting.domain.party.PartyRole
import com.example.accounting.presentation.features.dashboard.VoucherSummaryCard
import com.example.accounting.presentation.features.party.PartiesScreen

/**
 * Phase 7J UI: the Sales tab (bottom-nav item #2) - "Sale" and "Customer" per the UX spec's
 * business-action framing, never "Invoice"/"Debtor". Creation reuses the existing, tested
 * `CreateVoucherDialog(defaultVoucherType = VoucherType.SALES)` flow (posted via the existing
 * `postSaleInvoice`/`postTradingDocument` path) - this screen never posts anything itself.
 */
@Composable
fun SalesScreen(
    vouchers: List<Voucher>,
    parties: List<Party>,
    ledgers: List<Ledger>,
    onNewSale: () -> Unit,
    onVoucherClick: (Voucher) -> Unit,
    onAddCustomer: () -> Unit,
    onPartyClick: (Party) -> Unit,
    modifier: Modifier = Modifier
) {
    var tabIndex by remember { mutableIntStateOf(0) }
    val salesVouchers = remember(vouchers) {
        vouchers.filter { it.voucherType == VoucherType.SALES || it.voucherType == VoucherType.CREDIT_NOTE }
            .sortedByDescending { it.date }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tabIndex) {
            Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 }, text = { Text("Sales (${salesVouchers.size})") })
            Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 }, text = { Text("Customers (${parties.count { it.role == PartyRole.CUSTOMER }})") })
        }

        when (tabIndex) {
            0 -> Box(modifier = Modifier.fillMaxSize()) {
                if (salesVouchers.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("No sales yet", style = MaterialTheme.typography.titleMedium)
                        Text("Record your first sale to a customer.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(salesVouchers, key = { it.voucherId }) { voucher ->
                            VoucherSummaryCard(voucher = voucher, onClick = { onVoucherClick(voucher) })
                        }
                    }
                }
                FloatingActionButton(
                    onClick = onNewSale,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 20.dp, end = 20.dp)
                ) { Icon(Icons.Default.Add, contentDescription = "New Sale") }
            }
            1 -> PartiesScreen(
                role = PartyRole.CUSTOMER,
                parties = parties,
                ledgers = ledgers,
                onAddParty = onAddCustomer,
                onPartyClick = onPartyClick
            )
        }
    }
}
