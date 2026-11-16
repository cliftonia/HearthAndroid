package app.hearth.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hearth.R
import app.hearth.designsystem.HapticKind
import app.hearth.designsystem.Haptics
import app.hearth.designsystem.HearthCard
import app.hearth.designsystem.HearthTheme

/**
 * Last on the list of settings. There are no accounts to delete, so what is offered is to erase what the device
 * holds, after asking once more. If not everything could be erased, it says so beneath.
 */
@Composable
internal fun EraseSection(presenter: SettingsPresenter) {
    val colors = HearthTheme.colors
    val error by presenter.profileError.collectAsStateWithLifecycle()
    val isErasing by presenter.isErasing.collectAsStateWithLifecycle()
    var isConfirming by remember { mutableStateOf(false) }
    HearthCard(footer = stringResource(R.string.profile_erase_all_footer)) {
        // A row like the others on the list, in red, and one button to a screen reader.
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = HearthTheme.sizes.touchTarget)
                .clickable(enabled = !isErasing, role = Role.Button) { isConfirming = true },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.profile_erase_all), color = if (isErasing) colors.mute else colors.danger, style = HearthTheme.type.subhead)
        }
    }
    if (error == ProfileErrorKind.ERASE_FAILED) ProblemLine(stringResource(R.string.profile_erase_all_failed))
    if (isConfirming) {
        AlertDialog(
            onDismissRequest = { isConfirming = false },
            title = { Text(stringResource(R.string.profile_erase_all_question)) },
            text = { Text(stringResource(R.string.profile_erase_all_message)) },
            confirmButton = {
                TextButton(onClick = {
                    isConfirming = false
                    Haptics.play(HapticKind.WARNING)
                    presenter.eraseAction()
                }) { Text(stringResource(R.string.profile_erase_all_confirm), color = colors.danger) }
            },
            dismissButton = { TextButton(onClick = { isConfirming = false }) { Text(stringResource(R.string.settings_cancel)) } },
        )
    }
}
