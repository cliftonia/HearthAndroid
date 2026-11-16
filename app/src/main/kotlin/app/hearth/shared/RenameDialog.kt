package app.hearth.shared

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import app.hearth.R

/** Asks for a new title for a chat. A blank one cannot be saved. */
@Composable
fun RenameDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    // The title is ready to be typed over: the field has the keyboard and the cursor is after the old title.
    var title by remember { mutableStateOf(TextFieldValue(current, TextRange(current.length))) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.drawer_rename_chat)) },
        text = {
            OutlinedTextField(
                title,
                { title = it },
                label = { Text(stringResource(R.string.drawer_chat_title)) },
                singleLine = true,
                modifier = Modifier.focusRequester(focusRequester),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(title.text) }, enabled = title.text.isNotBlank()) { Text(stringResource(R.string.drawer_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) } },
    )
}
