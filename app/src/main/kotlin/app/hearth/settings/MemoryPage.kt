package app.hearth.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hearth.R
import app.hearth.designsystem.HapticKind
import app.hearth.designsystem.Haptics
import app.hearth.designsystem.HearthCard
import app.hearth.designsystem.HearthIconButton
import app.hearth.designsystem.HearthSwitchRow
import app.hearth.designsystem.HearthTheme
import app.hearth.domain.ServerSettings
import app.hearth.domain.clipped

/** The most that can be typed as one memory. */
private const val MAXIMUM_MEMORY_LENGTH = 200

/**
 * Whether anything is remembered and, while it is, a field to add a memory by hand, each memory with a way to forget
 * it, and a way to forget the lot.
 */
@Composable
internal fun MemoryPage(settings: ServerSettings, presenter: SettingsPresenter) {
    val colors = HearthTheme.colors
    val memories by presenter.memories.collectAsStateWithLifecycle()
    var draft by rememberSaveable { mutableStateOf("") }
    var isConfirmingClear by remember { mutableStateOf(false) }
    val footer = if (settings.isMemoryEnabled) {
        stringResource(R.string.memory_footer) + " " + stringResource(R.string.memory_list_footer)
    } else {
        stringResource(R.string.memory_footer)
    }
    HearthCard(footer = footer) {
        HearthSwitchRow(stringResource(R.string.memory_toggle), settings.isMemoryEnabled) { isOn -> presenter.updateAction { it.copy(isMemoryEnabled = isOn) } }
        if (!settings.isMemoryEnabled) return@HearthCard
        HorizontalDivider(color = colors.line)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it.clipped(MAXIMUM_MEMORY_LENGTH) },
                placeholder = { Text(stringResource(R.string.memory_placeholder)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.weight(1f),
            )
            HearthIconButton(Icons.Outlined.Add, stringResource(R.string.memory_add), tint = colors.pine) {
                if (draft.isNotBlank()) presenter.addMemoryAction(draft)
                draft = ""
            }
        }
        if (memories.isEmpty()) Text(stringResource(R.string.memory_none), color = colors.mute, style = HearthTheme.type.callout)
        memories.forEach { memory ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(memory.text, color = colors.ink, style = HearthTheme.type.subheadSnug, modifier = Modifier.weight(1f))
                HearthIconButton(Icons.Outlined.Close, stringResource(R.string.memory_delete, memory.text), tint = colors.mute) {
                    presenter.deleteMemoryAction(memory.id)
                }
            }
        }
        if (memories.isNotEmpty()) {
            TextButton(onClick = { isConfirmingClear = true }) { Text(stringResource(R.string.memory_clear), color = colors.danger) }
        }
    }
    if (isConfirmingClear) {
        AlertDialog(
            onDismissRequest = { isConfirmingClear = false },
            title = { Text(stringResource(R.string.memory_clear_question)) },
            confirmButton = {
                TextButton(onClick = {
                    isConfirmingClear = false
                    Haptics.play(HapticKind.WARNING)
                    presenter.clearMemoriesAction()
                }) { Text(stringResource(R.string.memory_clear), color = colors.danger) }
            },
            dismissButton = { TextButton(onClick = { isConfirmingClear = false }) { Text(stringResource(R.string.settings_cancel)) } },
        )
    }
}
