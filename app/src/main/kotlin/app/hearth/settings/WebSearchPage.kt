package app.hearth.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.hearth.R
import app.hearth.designsystem.HearthCard
import app.hearth.designsystem.HearthSwitchRow
import app.hearth.domain.ServerSettings

/** Whether the pages a web search finds are read whole, or only their snippets. */
@Composable
internal fun WebSearchPage(settings: ServerSettings, presenter: SettingsPresenter) {
    HearthCard(footer = stringResource(R.string.settings_read_pages_footer)) {
        HearthSwitchRow(stringResource(R.string.settings_read_pages), settings.isPageReadingEnabled) { isOn ->
            presenter.updateAction { it.copy(isPageReadingEnabled = isOn) }
        }
    }
}
