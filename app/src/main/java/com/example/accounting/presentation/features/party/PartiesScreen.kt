package com.example.accounting.presentation.features.party

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
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.accounting.domain.accounting.Ledger
import com.example.accounting.domain.party.Party
import com.example.accounting.domain.party.PartyRole
import com.example.accounting.presentation.components.SectionCard

/**
 * Phase 7J UI: Customer or Supplier list, reached from the Sales/Purchases tabs (never its own
 * bottom-nav item, per the UX spec's 5-item nav). Read-only view + create - editing a Party is
 * confirmed out of scope for this phase (no `updateParty` exists anywhere in the frozen 7J-B
 * service layer either).
 */
@Composable
fun PartiesScreen(
    role: PartyRole,
    parties: List<Party>,
    ledgers: List<Ledger>,
    onAddParty: () -> Unit,
    onPartyClick: (Party) -> Unit,
    modifier: Modifier = Modifier
) {
    val roleLabel = if (role == PartyRole.CUSTOMER) "Customers" else "Suppliers"
    val filtered = parties.filter { it.role == role && it.isActive }
    val ledgersMap = ledgers.associateBy { it.ledgerId }

    Box(modifier = modifier.fillMaxSize()) {
        if (filtered.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(6.dp))
                Text("No $roleLabel yet", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Add a $roleLabel to start billing them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(filtered, key = { it.partyId }) { party ->
                    val ledger = ledgersMap[party.ledgerId]
                    SectionCard(
                        onClick = { onPartyClick(party) },
                        trailing = {
                            Text(
                                text = ledger?.currentBalance?.formatPlain() ?: "--",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            )
                        }
                    ) {
                        Text(party.displayName, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
                        Text(
                            listOfNotNull(
                                party.entityType.name.lowercase().replaceFirstChar { it.uppercase() },
                                ledger?.gstin?.takeIf { it.isNotBlank() }?.let { "GSTIN: $it" }
                            ).joinToString(" • "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = onAddParty,
            modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 20.dp, end = 20.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add $roleLabel")
        }
    }
}
