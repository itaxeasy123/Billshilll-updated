package com.example.accounting.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Phase UI-03: a small colored status label - generic over color/text only, never a fixed set of
 * statuses baked in. A future caller renders `InvoiceStatus`/`GstRegistrationStatus`/sync-state
 * etc. by mapping its own enum to a label + color pair and passing them here; this component
 * itself has zero knowledge of any specific status type, per "no business data hardcoded inside a
 * reusable component."
 */
@Composable
fun StatusBadge(
    text: String,
    containerColor: Color,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
        color = contentColor,
        modifier = modifier
            .background(containerColor, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}
