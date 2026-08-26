package com.example.accounting.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.accounting.domain.company.Company
import com.example.accounting.domain.financialyear.FinancialYear

@Composable
fun AppTopBar(
    currentCompany: Company?,
    companies: List<Company>,
    currentFinancialYear: FinancialYear?,
    financialYears: List<FinancialYear>,
    pendingSyncCount: Int,
    isSyncing: Boolean,
    onCompanySelected: (Company) -> Unit,
    onFinancialYearSelected: (FinancialYear) -> Unit,
    onSyncClicked: () -> Unit,
    onNewCompanyClicked: () -> Unit,
    onSearchClicked: () -> Unit = {},
    onProfileClicked: () -> Unit = {},
    canGoBack: Boolean = false,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var companyDropdownOpen by remember { mutableStateOf(false) }
    var fyDropdownOpen by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        // Under enableEdgeToEdge() this Surface draws behind the status bar unless it claims that
        // inset itself - without this, the whole bar (and everything below it) renders shifted up
        // under the status bar icons/notch.
        modifier = modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Visible back affordance for any drill-down route (Ledger Statement, Search,
                // Profile, Subscription, Settings & Sync, Data Tools, ...) - previously only the
                // system back gesture/button worked here, with zero on-screen way back.
                if (canGoBack) {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("top_bar_back")) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
                // Company & Brand Selector - own full-width row so a long name/GSTIN never has to
                // fight the FY/sync/search/profile row below for space (that fight is what
                // collapsed this column to ~3dp wide on a standard 360dp phone, forcing the GSTIN
                // Text to wrap one character per line for its whole un-ellipsized length).
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { companyDropdownOpen = true }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .testTag("company_selector")
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Business,
                            contentDescription = "Company",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = currentCompany?.name ?: "Select Company",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Dropdown",
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Text(
                            text = "GSTIN: ${currentCompany?.gstin?.ifBlank { "Unregistered / Composition" } ?: "--"}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    DropdownMenu(
                        expanded = companyDropdownOpen,
                        onDismissRequest = { companyDropdownOpen = false }
                    ) {
                        Text(
                            text = "Select Company Tenant",
                            style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        companies.forEach { company ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(company.name, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            company.gstin.ifBlank { "GST: N/A" },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {
                                    onCompanySelected(company)
                                    companyDropdownOpen = false
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = {
                                Text("+ Add New Company", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            },
                            onClick = {
                                companyDropdownOpen = false
                                onNewCompanyClicked()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Financial Year badge and Sync/Search/Profile actions - its own full-width row below
            // the company selector, so it never has to compete with a long company name/GSTIN for
            // horizontal space on a standard-width phone.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // FY Badge
                Box {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .clickable { fyDropdownOpen = true }
                            .testTag("fy_selector")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = "FY",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = currentFinancialYear?.fyCode ?: "FY 2026-27",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = fyDropdownOpen,
                        onDismissRequest = { fyDropdownOpen = false }
                    ) {
                        Text(
                            text = "Financial Year (1 Apr - 31 Mar)",
                            style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        financialYears.forEach { fy ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(fy.fyCode, fontWeight = if (fy.isCurrent) FontWeight.Bold else FontWeight.Normal)
                                        if (fy.isLocked) {
                                            Icon(Icons.Default.Lock, contentDescription = "Locked", modifier = Modifier.size(16.dp), tint = Color.Red)
                                        }
                                    }
                                },
                                onClick = {
                                    onFinancialYearSelected(fy)
                                    fyDropdownOpen = false
                                }
                            )
                        }
                    }
                }

                // Sync action button with badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (pendingSyncCount > 0) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier
                        .clickable(enabled = !isSyncing) { onSyncClicked() }
                        .testTag("sync_action_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isSyncing) Icons.Default.Sync else if (pendingSyncCount > 0) Icons.Default.CloudSync else Icons.Default.CloudDone,
                            contentDescription = "Sync",
                            tint = if (pendingSyncCount > 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                        if (pendingSyncCount > 0) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "$pendingSyncCount",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }

                // Search (persistent, per the Phase 7J UX spec's global-search requirement)
                IconButton(onClick = onSearchClicked, modifier = Modifier.testTag("top_bar_search")) {
                    Icon(imageVector = Icons.Default.Search, contentDescription = "Search")
                }

                // Profile & Business Setup entry point - hosts Import/Subscription/Settings too
                IconButton(onClick = onProfileClicked, modifier = Modifier.testTag("top_bar_profile")) {
                    Icon(imageVector = Icons.Default.AccountCircle, contentDescription = "Profile & Business Setup")
                }
            }
        }
    }
}
