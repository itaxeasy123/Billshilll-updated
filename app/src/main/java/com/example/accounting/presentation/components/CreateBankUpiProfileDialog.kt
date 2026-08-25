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
import com.example.accounting.presentation.theme.Spacing

/**
 * Phase 7J UI: adds a Bank/UPI settlement-details profile - settlement metadata only (this dialog
 * never touches a ledger balance or posts anything), backed by
 * `BankUpiProfileService.create` (Phase 7J-B).
 */
@Composable
fun CreateBankUpiProfileDialog(
    onDismiss: () -> Unit,
    onCreate: (bankName: String, accountHolderName: String, accountNumber: String, ifscCode: String, branchName: String, upiId: String, upiPayeeName: String) -> Unit
) {
    var bankName by remember { mutableStateOf("") }
    var accountHolderName by remember { mutableStateOf("") }
    var accountNumber by remember { mutableStateOf("") }
    var ifscCode by remember { mutableStateOf("") }
    var branchName by remember { mutableStateOf("") }
    var upiId by remember { mutableStateOf("") }
    var upiPayeeName by remember { mutableStateOf("") }

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
                Text("Add Bank / UPI Details", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                Spacer(modifier = Modifier.height(Spacing.md))

                FormField(value = bankName, onValueChange = { bankName = it }, label = "Bank name", modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(Spacing.sm))
                FormField(value = accountHolderName, onValueChange = { accountHolderName = it }, label = "Account holder name", modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(Spacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm + Spacing.xs)) {
                    FormField(value = accountNumber, onValueChange = { accountNumber = it }, label = "Account number", modifier = Modifier.weight(1f))
                    FormField(value = ifscCode, onValueChange = { ifscCode = it }, label = "IFSC", modifier = Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(Spacing.sm))
                FormField(value = branchName, onValueChange = { branchName = it }, label = "Branch (optional)", modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(Spacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm + Spacing.xs)) {
                    FormField(value = upiId, onValueChange = { upiId = it }, label = "UPI ID (optional)", modifier = Modifier.weight(1f))
                    FormField(value = upiPayeeName, onValueChange = { upiPayeeName = it }, label = "UPI payee name", modifier = Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(Spacing.lg - Spacing.xs))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    ActionButton(text = "Cancel", style = ActionButtonStyle.TEXT, onClick = onDismiss)
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    ActionButton(
                        text = "Save",
                        enabled = bankName.isNotBlank() && accountNumber.isNotBlank() && ifscCode.isNotBlank(),
                        onClick = {
                            onCreate(bankName, accountHolderName, accountNumber, ifscCode, branchName, upiId, upiPayeeName)
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}
