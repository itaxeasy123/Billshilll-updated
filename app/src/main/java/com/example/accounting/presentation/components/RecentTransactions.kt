package com.example.accounting.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.accounting.domain.accounting.Voucher
import com.example.accounting.presentation.features.dashboard.VoucherSummaryCard
import com.example.accounting.presentation.theme.Radius
import com.example.accounting.presentation.theme.Spacing

/**
 * Phase UI-03 (Dashboard decomposition): the "Recent Transactions" header + list-or-empty-state
 * block, extracted verbatim from `DashboardScreen.kt` (same layout, same
 * [com.example.accounting.presentation.features.dashboard.VoucherSummaryCard] reuse, same empty
 * message) - zero behavior change, only relocated so it's independently reusable/testable.
 * [vouchers] is the caller's own already-filtered/limited list (`DashboardScreen` still decides
 * "take(6)"); this composable never filters or sorts on its own.
 */
fun LazyListScope.recentTransactionsSection(
    vouchers: List<Voucher>,
    onVoucherClick: (Voucher) -> Unit,
    onViewAll: () -> Unit
) {
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
                modifier = Modifier.clip(Radius.shapeSm).clickable(onClick = onViewAll).padding(Spacing.xs)
            )
        }
    }

    if (vouchers.isEmpty()) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = Radius.shapeMd,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(Spacing.lg), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Book, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    Text("No transactions in this period yet", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    } else {
        items(vouchers, key = { it.voucherId }) { voucher ->
            VoucherSummaryCard(voucher = voucher, onClick = { onVoucherClick(voucher) })
        }
    }
}
