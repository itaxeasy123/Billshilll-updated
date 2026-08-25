package com.example.accounting.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.accounting.domain.party.PartyEntityType
import com.example.accounting.domain.party.PartyRole
import com.example.accounting.presentation.theme.Spacing

/**
 * Phase 7J UI: adds a Customer or Supplier - a thin form over
 * [com.example.accounting.presentation.viewmodel.AccountingViewModel.createParty], which itself
 * delegates to the frozen `PartyManagementService.createParty` (Phase 7J-B). [role] is fixed by
 * which screen opened this dialog (Sales -> Customer, Purchases -> Supplier) - never chosen here,
 * so a Customer can never be accidentally created from the Purchases tab.
 */
@Composable
fun CreatePartyDialog(
    role: PartyRole,
    onDismiss: () -> Unit,
    onCreateParty: (displayName: String, role: PartyRole, entityType: PartyEntityType, gstin: String, phone: String, email: String, address: String) -> Unit
) {
    var displayName by remember { mutableStateOf("") }
    var entityType by remember { mutableStateOf(PartyEntityType.BUSINESS) }
    var gstin by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }

    val roleLabel = if (role == PartyRole.CUSTOMER) "Customer" else "Supplier"

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(0.94f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(Spacing.lg - Spacing.xs)
            ) {
                Text("Add $roleLabel", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                Spacer(modifier = Modifier.height(Spacing.md))

                FormField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = "$roleLabel name",
                    supportingText = "Required",
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(Spacing.sm))

                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    FilterChip(
                        selected = entityType == PartyEntityType.BUSINESS,
                        onClick = { entityType = PartyEntityType.BUSINESS },
                        label = { Text("Business") }
                    )
                    FilterChip(
                        selected = entityType == PartyEntityType.INDIVIDUAL,
                        onClick = { entityType = PartyEntityType.INDIVIDUAL },
                        label = { Text("Individual") }
                    )
                }
                Spacer(modifier = Modifier.height(Spacing.sm))

                FormField(value = gstin, onValueChange = { gstin = it }, label = "GSTIN (optional)", modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(Spacing.sm))

                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm + Spacing.xs)) {
                    FormField(value = phone, onValueChange = { phone = it }, label = "Phone", modifier = Modifier.weight(1f))
                    FormField(value = email, onValueChange = { email = it }, label = "Email", modifier = Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(Spacing.sm))

                FormField(value = address, onValueChange = { address = it }, label = "Address", modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(Spacing.lg - Spacing.xs))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    ActionButton(text = "Cancel", style = ActionButtonStyle.TEXT, onClick = onDismiss)
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    ActionButton(
                        text = "Add $roleLabel",
                        enabled = displayName.isNotBlank(),
                        onClick = {
                            onCreateParty(displayName, role, entityType, gstin, phone, email, address)
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}
