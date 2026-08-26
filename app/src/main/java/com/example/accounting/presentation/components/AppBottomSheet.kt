package com.example.accounting.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.accounting.presentation.theme.Spacing

/**
 * Base component - the one modal-bottom-sheet primitive. Not previously built because nothing in
 * this app currently uses one (confirmed: zero `ModalBottomSheet` usage anywhere before this
 * file) - a real, generic shell now available for a future entry point (e.g. an OCR document-
 * upload picker) without that feature having to wire `rememberModalBottomSheetState` itself.
 * [content] holds only the caller's own content - this component has no opinion on what a sheet
 * contains.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBottomSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, modifier = modifier) {
        Column(modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md)) {
            content()
        }
    }
}
