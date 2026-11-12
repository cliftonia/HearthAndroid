package app.hearth.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hearth.R
import app.hearth.designsystem.HearthTheme
import app.hearth.domain.ModelStore
import app.hearth.domain.ServerReachability
import app.hearth.domain.SettingsStore

/** How far down the fade behind the floating buttons the paper becomes solid. */
private const val FADE_SOLID_FROM = 0.4f

/** The settings and the new chat button, floating over the end of the list, which fades out beneath them. */
@Composable
internal fun DrawerFloatingControls(settingsStore: SettingsStore, modelStore: ModelStore, presenter: DrawerPresenter, modifier: Modifier = Modifier) {
    val colors = HearthTheme.colors
    val spacing = HearthTheme.spacing
    Row(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(0f to colors.paper.copy(alpha = 0f), FADE_SOLID_FROM to colors.paper, 1f to colors.paper))
            .padding(start = spacing.xs, end = spacing.xs, top = spacing.gapXl, bottom = spacing.xl),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsButton(settingsStore, modelStore, presenter::settingsAction)
        Box(Modifier.weight(1f))
        NewChatButton(presenter::newChatAction)
    }
}

/** Starts a fresh chat. The one filled button in the drawer. */
@Composable
private fun NewChatButton(onNewChat: () -> Unit) {
    val colors = HearthTheme.colors
    Row(
        Modifier
            .height(HearthTheme.sizes.touchTarget)
            .clip(HearthTheme.shapes.pill)
            .background(colors.pine)
            .clickable(role = Role.Button, onClick = onNewChat)
            .padding(horizontal = HearthTheme.spacing.gapS),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.m),
    ) {
        Icon(Icons.Outlined.Add, null, tint = colors.onPine, modifier = Modifier.size(HearthTheme.sizes.iconM))
        Text(stringResource(R.string.chat_new), color = colors.onPine, style = HearthTheme.type.subheadStrong)
    }
}

/** Opens the settings. Its dot shows whether the server can be reached. */
@Composable
private fun SettingsButton(settingsStore: SettingsStore, modelStore: ModelStore, onSettings: () -> Unit) {
    val colors = HearthTheme.colors
    val settings by settingsStore.settings.collectAsStateWithLifecycle()
    val models by modelStore.models.collectAsStateWithLifecycle()
    val reachability by modelStore.reachability.collectAsStateWithLifecycle()
    val isOnline = reachability == ServerReachability.REACHABLE
    val status = when {
        settings.ollamaUrl == null -> stringResource(R.string.drawer_no_server)
        isOnline -> pluralStringResource(R.plurals.drawer_connected, models.size, models.size)
        else -> stringResource(R.string.drawer_not_connected)
    }
    Box(
        Modifier
            .size(HearthTheme.sizes.touchTarget)
            .clip(HearthTheme.shapes.circle)
            .background(colors.card)
            .border(HearthTheme.strokes.hairline, colors.line, HearthTheme.shapes.circle)
            .clickable(role = Role.Button, onClick = onSettings)
            .semantics { stateDescription = status },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Outlined.Settings, stringResource(R.string.drawer_settings), tint = colors.ink, modifier = Modifier.size(HearthTheme.sizes.iconL))
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(HearthTheme.spacing.badgeInset)
                .size(HearthTheme.sizes.statusDot)
                .background(if (isOnline) colors.online else colors.mute, HearthTheme.shapes.circle),
        )
    }
}
