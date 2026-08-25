package com.example.accounting.presentation.features.purchases

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ShoppingCart
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
 * Phase 7J UI: the Purchases tab (bottom-nav item #3) - mirrors [com.example.accounting.presentation.features.sales.SalesScreen]'s
 * exact shape. Creation reuses `CreateVoucherDialog(defaultVoucherType = VoucherType.PURCHASE)`.
 */
@Composable
fun PurchasesScreen(
    vouchers: List<Voucher>,
    parties: List<Party>,
    ledgers: List<Ledger>,
    onNewPurchase: () -> Unit,
    onVoucherClick: (Voucher) -> Unit,
    onAddSupplier: () -> Unit,
    onPartyClick: (Party) -> Unit,
    modifier: Modifier = Modifier
) {
    var tabIndex by remember { mutableIntStateOf(0) }
    val purchaseVouchers = remember(vouchers) {
        vouchers.filter { it.voucherType == VoucherType.PURCHASE || it.voucherType == VoucherType.DEBIT_NOTE }
            .sortedByDescending { it.date }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tabIndex) {
            Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 }, text = { Text("Purchases (${purchaseVouchers.size})") })
            Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 }, text = { Text("Suppliers (${parties.count { it.role == PartyRole.SUPPLIER }})") })
        }

        when (tabIndex) {
            0 -> Box(modifier = Modifier.fillMaxSize()) {
                if (purchaseVouchers.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("No purchases yet", style = MaterialTheme.typography.titleMedium)
                        Text("Record your first purchase from a supplier.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(purchaseVouchers, key = { it.voucherId }) { voucher ->
                            VoucherSummaryCard(voucher = voucher, onClick = { onVoucherClick(voucher) })
                        }
                    }
                }
                FloatingActionButton(
                    onClick = onNewPurchase,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 20.dp, end = 20.dp)
                ) { Icon(Icons.Default.Add, contentDescription = "New Purchase") }
            }
            1 -> PartiesScreen(
                role = PartyRole.SUPPLIER,
                parties = parties,
                ledgers = ledgers,
                onAddParty = onAddSupplier,
                onPartyClick = onPartyClick
            )
        }
    }
}
