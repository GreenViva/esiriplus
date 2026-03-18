package com.esiri.esiriplus.core.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BrandTeal = Color(0xFF2A9D8F)

/**
 * Standard error dialog used across all features.
 */
@Composable
fun ErrorDialog(
    message: String,
    onDismiss: () -> Unit,
    title: String = "Error",
    confirmText: String = "OK",
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = Color.Black) },
        text = { Text(message, color = Color.Black) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(confirmText, color = BrandTeal)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

/**
 * Confirmation dialog with confirm and cancel actions.
 */
@Composable
fun ConfirmationDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmText: String = "Confirm",
    dismissText: String = "Cancel",
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = Color.Black) },
        text = { Text(message, color = Color.Black) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText, color = BrandTeal)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissText, color = Color.Gray)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    )
}
