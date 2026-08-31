package com.example.accounting.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.accounting.domain.company.Company
import com.example.accounting.presentation.navigation.AppRoute
import com.example.accounting.presentation.theme.Spacing

/**
 * Play Store readiness pass - Legal + Support drawer (the scope explicitly chosen over a full
 * secondary-nav drawer, which would have duplicated what the Profile/Search top-bar icons already
 * cover - see `docs/54_UI_UX_ARCHITECTURE.md`'s "secondary features reached through their
 * respective sections" rule, unchanged by this addition). Header shows the current company's own
 * on-file name/GSTIN - a direct read of already-loaded state, never a second lookup.
 */
@Composable
fun AppDrawerContent(
    currentCompany: Company?,
    currentRoute: AppRoute,
    onNavigate: (AppRoute) -> Unit
) {
    ModalDrawerSheet {
        Column(modifier = Modifier.fillMaxWidth().padding(Spacing.md)) {
            Text(
                currentCompany?.name ?: "LedgerPrime",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                "GSTIN: ${currentCompany?.gstin?.ifBlank { "Unregistered / Composition" } ?: "--"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        HorizontalDivider()

        val items = listOf(
            Triple(AppRoute.About, "About", Icons.Default.Info),
            Triple(AppRoute.PrivacyPolicy, "Privacy Policy", Icons.Default.Description),
            Triple(AppRoute.TermsAndConditions, "Terms & Conditions", Icons.Default.Gavel),
            Triple(AppRoute.Support, "Support", Icons.Default.SupportAgent)
        )
        items.forEach { (route, label, icon) ->
            NavigationDrawerItem(
                label = { Text(label) },
                icon = { Icon(icon, contentDescription = null) },
                selected = currentRoute == route,
                onClick = { onNavigate(route) },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )
        }
    }
}
