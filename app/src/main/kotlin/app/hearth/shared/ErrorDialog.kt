package app.hearth.shared

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.hearth.R

/** Says what went wrong over a sheet, which has no room for a banner, and waits to be acknowledged. */
@Composable
fun ErrorDialog(error: ErrorViewConfiguration, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(errorMessage(error)) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_ok)) } },
    )
}
