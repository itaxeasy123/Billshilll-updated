package com.example.accounting.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.accounting.core.common.Money

/**
 * Widget - a named [LedgerRow] wrapper for a Customer/Supplier row (name, an already-formatted
 * business-language [subtitle] the caller composes - e.g. "Business • GSTIN: 27ABCDE..." - and the
 * party's already-loaded ledger balance). Not a new component underneath: [LedgerRow]+[Amount]
 * already had exactly this shape before this file existed (title/subtitle/money/onClick) -
 * `PartiesScreen.kt` currently hand-rolls the same layout itself instead of reusing either; that
 * screen is not retrofitted in this pass (out of scope), but any future party-list screen should
 * reach for this instead of repeating that duplication a third time.
 */
@Composable
fun PartyCard(
    name: String,
    subtitle: String,
    balance: Money?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    LedgerRow(title = name, subtitle = subtitle, money = balance, onClick = onClick, modifier = modifier)
}
