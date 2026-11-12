package app.hearth.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hearth.R
import app.hearth.designsystem.HearthIconButton
import app.hearth.designsystem.HearthTheme

/** Where the servers are named and the app's behaviour is chosen. Each group of settings is a card of its own. */
@Composable
fun SettingsScreen(
    presenter: SettingsPresenter,
    /** Asks the phone to confirm who is holding it, and reports whether it did. */
    authenticate: (onResult: (Boolean) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings by presenter.settings.collectAsStateWithLifecycle()
    val memories by presenter.memories.collectAsStateWithLifecycle()
    val test by presenter.connectionTest.collectAsStateWithLifecycle()
    val spacing = HearthTheme.spacing
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            HearthIconButton(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.settings_back), onClick = presenter::doneAction)
            Text(
                stringResource(R.string.drawer_settings),
                style = HearthTheme.type.brand,
                color = HearthTheme.colors.ink,
                modifier = Modifier.padding(start = spacing.xs),
            )
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = spacing.xxxl, vertical = spacing.m),
            verticalArrangement = Arrangement.spacedBy(spacing.gapL),
        ) {
            ServerCard(settings, test, presenter)
            InstructionsCard(settings, presenter)
            SearchCard(settings, presenter)
            VoiceCard(settings, presenter)
            MemoryCard(settings, memories, presenter)
            PrivacyCard(settings, presenter, authenticate)
            ThemeCard(settings, presenter)
        }
    }
}
