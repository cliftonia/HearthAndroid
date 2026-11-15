package app.hearth.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.hearth.R
import app.hearth.designsystem.HearthCard
import app.hearth.designsystem.HearthSwitchRow
import app.hearth.domain.Appearance
import app.hearth.domain.ServerSettings

// The smaller groups of settings, each a switch or two.

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

/** Whether the phone taps back when something is done. */
@Composable
internal fun HapticsCard(settings: ServerSettings, presenter: SettingsPresenter) {
    HearthCard(footer = stringResource(R.string.haptics_footer)) {
        HearthSwitchRow(stringResource(R.string.haptics_title), settings.isHapticsEnabled) { isOn ->
            presenter.updateAction { it.copy(isHapticsEnabled = isOn) }
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
