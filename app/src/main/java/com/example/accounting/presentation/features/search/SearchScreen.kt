package com.example.accounting.presentation.features.search

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.accounting.domain.accounting.Ledger
import com.example.accounting.domain.accounting.Voucher
import com.example.accounting.domain.inventory.StockItem
import com.example.accounting.domain.party.Party
import com.example.accounting.presentation.components.SectionCard

/**
 * Phase 7J UI: persistent search across Party/Ledger/Voucher/Item, per the UX spec's Section 14 -
 * a composite, in-memory filter over already-loaded [com.example.accounting.presentation.viewmodel.AccountingUiState]
 * lists; it never recomputes a balance/total and always routes into an existing detail screen.
 */
@Composable
fun SearchScreen(
    initialQuery: String,
    parties: List<Party>,
    ledgers: List<Ledger>,
    vouchers: List<Voucher>,
    stockItems: List<StockItem>,
    onBack: () -> Unit,
    onPartyClick: (Party) -> Unit,
    onLedgerClick: (Ledger) -> Unit,
    onVoucherClick: (Voucher) -> Unit,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf(initialQuery) }

    val matchedParties = remember(query, parties) { if (query.isBlank()) emptyList() else parties.filter { it.displayName.contains(query, ignoreCase = true) }.take(10) }
    val matchedLedgers = remember(query, ledgers) { if (query.isBlank()) emptyList() else ledgers.filter { it.name.contains(query, ignoreCase = true) }.take(10) }
    val matchedVouchers = remember(query, vouchers) {
        if (query.isBlank()) emptyList() else vouchers.filter {
            it.voucherNumber.contains(query, ignoreCase = true) || it.narration.contains(query, ignoreCase = true) || it.referenceNumber.contains(query, ignoreCase = true)
        }.take(10)
    }
    val matchedItems = remember(query, stockItems) { if (query.isBlank()) emptyList() else stockItems.filter { it.name.contains(query, ignoreCase = true) }.take(10) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search customers, suppliers, accounts, transactions, items...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }

        if (query.isBlank()) {
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 40.dp)
        ) {
            if (matchedParties.isNotEmpty()) {
                item { SectionHeader("Customers & Suppliers") }
                items(matchedParties, key = { "p_${it.partyId}" }) { p ->
                    val roleLabel = if (p.role == com.example.accounting.domain.party.PartyRole.CUSTOMER) "Customer" else "Supplier"
                    SectionCard(onClick = { onPartyClick(p) }, title = p.displayName, subtitle = roleLabel) {}
                }
            }
            if (matchedLedgers.isNotEmpty()) {
                item { SectionHeader("Accounts") }
                items(matchedLedgers, key = { "l_${it.ledgerId}" }) { l -> SectionCard(onClick = { onLedgerClick(l) }, title = l.name, subtitle = l.groupName) {} }
            }
            if (matchedVouchers.isNotEmpty()) {
                item { SectionHeader("Transactions") }
                items(matchedVouchers, key = { "v_${it.voucherId}" }) { v -> SectionCard(onClick = { onVoucherClick(v) }, title = v.voucherNumber, subtitle = "${v.date} • ${v.voucherType.displayName}") {} }
            }
            if (matchedItems.isNotEmpty()) {
                item { SectionHeader("Items") }
                items(matchedItems, key = { "i_${it.itemId}" }) { i -> SectionCard(title = i.name, subtitle = i.hsnCode) {} }
            }
            if (matchedParties.isEmpty() && matchedLedgers.isEmpty() && matchedVouchers.isEmpty() && matchedItems.isEmpty()) {
                item { Text("No matches", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp)) }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(title, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurfaceVariant)
}
