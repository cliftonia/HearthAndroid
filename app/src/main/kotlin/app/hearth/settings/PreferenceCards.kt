package app.hearth.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import app.hearth.R
import app.hearth.designsystem.HearthCard
import app.hearth.designsystem.HearthSwitchRow
import app.hearth.domain.Appearance
import app.hearth.domain.ServerSettings

// The smaller groups of settings, each a switch or two.

/** Standing instructions for every conversation, and whether questions are suggested after a reply. */
@Composable
internal fun InstructionsCard(settings: ServerSettings, presenter: SettingsPresenter) {
    HearthCard(stringResource(R.string.settings_instructions), stringResource(R.string.settings_instructions_footer)) {
        OutlinedTextField(
            value = settings.customInstructions,
            onValueChange = { text -> presenter.updateAction { it.copy(customInstructions = text) } },
            placeholder = { Text(stringResource(R.string.settings_instructions_placeholder)) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            minLines = 3,
            maxLines = 10,
            modifier = Modifier.fillMaxWidth(),
        )
        HearthSwitchRow(stringResource(R.string.settings_follow_ups), settings.isFollowUpsEnabled) { isOn ->
            presenter.updateAction { it.copy(isFollowUpsEnabled = isOn) }
        }
    }
}

/** Whether the pages a web search finds are read, or only their snippets. */
@Composable
internal fun SearchCard(settings: ServerSettings, presenter: SettingsPresenter) {
    HearthCard(stringResource(R.string.composer_search), stringResource(R.string.settings_read_pages_footer)) {
        HearthSwitchRow(stringResource(R.string.settings_read_pages), settings.isPageReadingEnabled) { isOn ->
            presenter.updateAction { it.copy(isPageReadingEnabled = isOn) }
        }
    }
}

/** Whether the app must be unlocked each time it is opened. */
@Composable
internal fun PrivacyCard(settings: ServerSettings, presenter: SettingsPresenter, authenticate: (onResult: (Boolean) -> Unit) -> Unit) {
    HearthCard(stringResource(R.string.settings_privacy), stringResource(R.string.settings_require_unlock_footer)) {
        HearthSwitchRow(stringResource(R.string.settings_require_unlock), settings.isLockEnabled) { isOn ->
            // Turning the lock on or off both need the holder of the phone to prove who they are.
            authenticate { isConfirmed -> if (isConfirmed) presenter.updateAction { it.copy(isLockEnabled = isOn) } }
        }
    }
}

/** Light, dark, or whichever the phone is using. */
@Composable
internal fun ThemeCard(settings: ServerSettings, presenter: SettingsPresenter) {
    val titles = mapOf(
        Appearance.SYSTEM to R.string.settings_theme_system,
        Appearance.LIGHT to R.string.settings_theme_light,
        Appearance.DARK to R.string.settings_theme_dark,
    )
    HearthCard(stringResource(R.string.settings_theme)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            Appearance.entries.forEachIndexed { index, appearance ->
                SegmentedButton(
                    selected = settings.appearance == appearance,
                    onClick = { presenter.updateAction { it.copy(appearance = appearance) } },
                    shape = SegmentedButtonDefaults.itemShape(index, Appearance.entries.size),
                ) { Text(stringResource(titles.getValue(appearance))) }
            }
        }
    }
}
