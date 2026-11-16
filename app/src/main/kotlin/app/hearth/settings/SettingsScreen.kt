package app.hearth.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hearth.R
import app.hearth.designsystem.HearthIconButton
import app.hearth.designsystem.HearthTheme
import app.hearth.integrations.ProfilePhotoLoader

/**
 * Where the servers are named and the app's behaviour is chosen. Each group of settings is a card of its own.
 *
 * The profile, the notifications and sync are pages of their own, reached from rows here; the back button leads
 * from any of them to this list, and from this list out of the settings.
 */
@Composable
fun SettingsScreen(
    presenter: SettingsPresenter,
    /** Makes a chosen picture into the small photo kept for the profile. */
    photos: ProfilePhotoLoader,
    /** Asks the phone to confirm who is holding it, and reports whether it did. */
    authenticate: (onResult: (Boolean) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings by presenter.settings.collectAsStateWithLifecycle()
    val memories by presenter.memories.collectAsStateWithLifecycle()
    val test by presenter.connectionTest.collectAsStateWithLifecycle()
    val page by presenter.page.collectAsStateWithLifecycle()
    val photo by presenter.photo.collectAsStateWithLifecycle()
    val avatar = rememberAvatar(photo)
    LaunchedEffect(Unit) { presenter.loadProfilePhotoAction() }
    val title = when (page) {
        SettingsPageKind.MAIN -> R.string.drawer_settings
        SettingsPageKind.PROFILE -> R.string.profile_title
        SettingsPageKind.NOTIFICATIONS -> R.string.notifications_title
        SettingsPageKind.SYNC -> R.string.sync_title
    }
    val spacing = HearthTheme.spacing
    // The list keeps its place while a page it led to is looked at; each such page starts at its own top.
    val listScroll = rememberScrollState()
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            HearthIconButton(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.settings_back), onClick = presenter::backAction)
            Text(
                stringResource(title),
                style = HearthTheme.type.brand,
                color = HearthTheme.colors.ink,
                modifier = Modifier.padding(start = spacing.xs).semantics { heading() },
            )
        }
        key(page) {
            SettingsPage(if (page == SettingsPageKind.MAIN) listScroll else rememberScrollState()) {
                when (page) {
                    SettingsPageKind.MAIN -> {
                        ProfileCard(settings.profile, avatar, presenter)
                        ServerCard(settings, presenter)
                        SearchCard(settings, presenter)
                        ConnectionCard(test, presenter)
                        VoiceCard(settings, presenter)
                        PictureCard(settings, presenter)
                        MemoryCard(settings, memories, presenter)
                        NotificationsCard(settings, presenter)
                        SyncCard(presenter)
                        PrivacyCard(settings, presenter, authenticate)
                        HapticsCard(settings, presenter)
                        ThemeCard(settings, presenter)
                    }
                    SettingsPageKind.PROFILE -> ProfilePage(settings, avatar, presenter, photos)
                    SettingsPageKind.NOTIFICATIONS -> NotificationsPage(settings, presenter)
                    SettingsPageKind.SYNC -> SyncPage(presenter)
                }
            }
        }
    }
}

/** One page of the settings: cards on paper, scrolled. */
@Composable
private fun SettingsPage(scroll: ScrollState, content: @Composable ColumnScope.() -> Unit) {
    val spacing = HearthTheme.spacing
    Column(
        Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = spacing.xxxl, vertical = spacing.m),
        verticalArrangement = Arrangement.spacedBy(spacing.gapL),
        content = content,
    )
}
