package com.example.accounting.presentation.features.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.accounting.domain.rendering.BusinessProfile
import com.example.accounting.domain.rendering.IndividualProfile
import com.example.accounting.presentation.components.SectionCard

/**
 * Phase 7J UI: "Profile & Business Setup" - reached from the persistent top-bar icon, never a
 * bottom-nav item (per the UX spec's "secondary features are reached through their respective
 * sections" rule). Shows both a Business section and an Individual section unconditionally -
 * nothing in the frozen `ProfileApplicationService`/domain model enforces exclusivity between
 * them, so this screen doesn't invent one either. Also hosts the Import/Subscription/Company &
 * Sync entry points, matching the spec's "secondary features reached through here" framing.
 */
@Composable
fun ProfileScreen(
    businessProfile: BusinessProfile?,
    individualProfile: IndividualProfile?,
    onSaveBusinessProfile: (businessName: String, legalName: String, address: String, phone: String, email: String, gstin: String, pan: String) -> Unit,
    onSaveIndividualProfile: (name: String, address: String, phone: String, email: String, pan: String) -> Unit,
    onOpenImportData: () -> Unit,
    onOpenSubscription: () -> Unit,
    onOpenCompanyAndSync: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Profile & Business Setup", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))

        BusinessProfileSection(businessProfile, onSaveBusinessProfile)
        IndividualProfileSection(individualProfile, onSaveIndividualProfile)

        SectionCard(onClick = onOpenImportData, title = "Import & Scan", subtitle = "CSV/JSON import, scan a receipt") {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
        }
        SectionCard(onClick = onOpenSubscription, title = "Subscription", subtitle = "Plan & entitlements") {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
        }
        SectionCard(onClick = onOpenCompanyAndSync, title = "Company & Sync", subtitle = "Accounting setup, governance, cloud sync") {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
        }
    }
}

@Composable
private fun BusinessProfileSection(
    profile: BusinessProfile?,
    onSave: (String, String, String, String, String, String, String) -> Unit
) {
    var businessName by remember(profile) { mutableStateOf(profile?.businessName ?: "") }
    var legalName by remember(profile) { mutableStateOf(profile?.legalName ?: "") }
    var address by remember(profile) { mutableStateOf(profile?.address ?: "") }
    var phone by remember(profile) { mutableStateOf(profile?.phone ?: "") }
    var email by remember(profile) { mutableStateOf(profile?.email ?: "") }
    var gstin by remember(profile) { mutableStateOf(profile?.gstin ?: "") }
    var pan by remember(profile) { mutableStateOf(profile?.pan ?: "") }

    SectionCard(elevated = true, title = "Business Profile") {
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = businessName, onValueChange = { businessName = it }, label = { Text("Trade name") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = legalName, onValueChange = { legalName = it }, label = { Text("Legal name") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone") }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Address") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = gstin, onValueChange = { gstin = it }, label = { Text("GSTIN") }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = pan, onValueChange = { pan = it }, label = { Text("PAN") }, modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(10.dp))
        Button(onClick = { onSave(businessName, legalName, address, phone, email, gstin, pan) }, enabled = businessName.isNotBlank()) {
            Text("Save Business Profile")
        }
    }
}

@Composable
private fun IndividualProfileSection(
    profile: IndividualProfile?,
    onSave: (String, String, String, String, String) -> Unit
) {
    var name by remember(profile) { mutableStateOf(profile?.name ?: "") }
    var address by remember(profile) { mutableStateOf(profile?.address ?: "") }
    var phone by remember(profile) { mutableStateOf(profile?.phone ?: "") }
    var email by remember(profile) { mutableStateOf(profile?.email ?: "") }
    var pan by remember(profile) { mutableStateOf(profile?.pan ?: "") }

    SectionCard(elevated = true, title = "Individual Profile") {
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Full name") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone") }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Address") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = pan, onValueChange = { pan = it }, label = { Text("PAN") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(10.dp))
        Button(onClick = { onSave(name, address, phone, email, pan) }, enabled = name.isNotBlank()) {
            Text("Save Individual Profile")
        }
    }
}
