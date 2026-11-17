package app.hearth.shared

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.hearth.R
import app.hearth.designsystem.HearthTheme

/**
 * Asks whether to throw away changes not yet kept, as an editor is left without being saved. Keeping on editing is
 * the safe choice, so it is what a tap beside the dialog or the back button does.
 */
@Composable
fun DiscardChangesDialog(onKeepEditing: () -> Unit, onDiscard: () -> Unit) {
    AlertDialog(
        onDismissRequest = onKeepEditing,
        title = { Text(stringResource(R.string.discard_question)) },
        confirmButton = { TextButton(onClick = onDiscard) { Text(stringResource(R.string.discard_confirm), color = HearthTheme.colors.danger) } },
        dismissButton = { TextButton(onClick = onKeepEditing) { Text(stringResource(R.string.discard_keep_editing)) } },
    )
}
