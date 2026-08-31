package com.example.accounting.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * Address block reused by both Business and Individual Profile (old [com.example.accounting.presentation.features.profile.ProfileScreen]
 * and the [com.example.accounting.presentation.features.profile.ProfileWizardScreen]'s Contact
 * step) - one implementation, not duplicated per screen. [address] stays the free-text street/
 * locality line (unchanged field); [pinCode]/[city]/[state]/[country] are the additive structured
 * fields. Auto-triggers [onLookupPinCode] once [pinCode] reaches 6 digits (debounced by
 * `LaunchedEffect` keying on the value itself - a fresh 6-digit value re-fires, an unchanged one
 * doesn't); City/State/Country stay plain editable [FormField]s regardless - a successful lookup
 * only pre-fills them, it never locks them, so a user can always correct a wrong/incomplete public
 * API result by hand.
 */
@Composable
fun AddressPinCodeFields(
    address: String, onAddressChange: (String) -> Unit,
    pinCode: String, onPinCodeChange: (String) -> Unit,
    city: String, onCityChange: (String) -> Unit,
    state: String, onStateChange: (String) -> Unit,
    country: String, onCountryChange: (String) -> Unit,
    isLookingUp: Boolean,
    lookupErrorMessage: String?,
    onLookupPinCode: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(pinCode) {
        if (pinCode.length == 6 && pinCode.all { it.isDigit() }) {
            onLookupPinCode(pinCode)
        }
    }

    Column(modifier = modifier) {
        FormField(value = address, onValueChange = onAddressChange, label = "Address", modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FormField(
                value = pinCode, onValueChange = { onPinCodeChange(it.filter { c -> c.isDigit() }.take(6)) },
                label = "PIN Code", keyboardType = KeyboardType.Number,
                isError = lookupErrorMessage != null,
                supportingText = lookupErrorMessage ?: "Auto-fills City/State/Country",
                modifier = Modifier.weight(1f)
            )
            if (isLookingUp) {
                Spacer(modifier = Modifier.width(8.dp))
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FormField(value = city, onValueChange = onCityChange, label = "City", modifier = Modifier.weight(1f))
            FormField(value = state, onValueChange = onStateChange, label = "State", modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(8.dp))
        FormField(value = country, onValueChange = onCountryChange, label = "Country", modifier = Modifier.fillMaxWidth())
    }
}
