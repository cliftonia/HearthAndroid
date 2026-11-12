package app.hearth.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hearth.R
import app.hearth.designsystem.HearthIconButton
import app.hearth.designsystem.HearthMark
import app.hearth.designsystem.HearthTheme
import app.hearth.domain.ModelStore
import app.hearth.domain.SettingsStore
import app.hearth.integrations.AttachmentLoader
import app.hearth.shared.ErrorViewConfiguration
import app.hearth.shared.errorMessage

/** The conversation: its header, its messages or what stands in for them, any error, and the composer. */
@Composable
fun ChatScreen(
    presenter: ChatPresenter,
    settingsStore: SettingsStore,
    modelStore: ModelStore,
    attachments: AttachmentLoader,
    modifier: Modifier = Modifier,
    /** Whether the button that opens the chat list is shown; on a wide screen the list is always there. */
    showsMenu: Boolean = true,
) {
    val viewModel by presenter.viewModel.collectAsStateWithLifecycle()
    val settings by settingsStore.settings.collectAsStateWithLifecycle()
    val models by modelStore.models.collectAsStateWithLifecycle()
    Column(modifier.fillMaxSize()) {
        ChatHeader(viewModel, models, settings, presenter, showsMenu)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                settings.ollamaUrl == null -> ConnectPrompt(presenter::settingsAction)
                viewModel.conversation.messages.isEmpty() -> EmptyState(viewModel.conversation.isPrivate)
                else -> MessageList(viewModel, presenter, canSpeak = settings.speechUrl != null)
            }
        }
        viewModel.error?.let { ErrorBanner(it, presenter::retryAction, presenter::dismissErrorAction) }
        ComposerFooter(viewModel, settings, presenter, attachments)
    }
}

/** What a chat shows before its first message. */
@Composable
private fun EmptyState(isPrivate: Boolean) {
    val colors = HearthTheme.colors
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(HearthTheme.spacing.screen),
        verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.gapS, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HearthMark(HearthTheme.sizes.markM)
        Text(stringResource(R.string.chat_empty_title), style = HearthTheme.type.display, color = colors.ink, textAlign = TextAlign.Center)
        // The note keeps its place whether or not it shows, so the greeting does not jump when the chat is made private.
        Text(
            if (isPrivate) stringResource(R.string.chat_private_note) else "",
            color = colors.mute,
            style = HearthTheme.type.footnote,
            textAlign = TextAlign.Center,
        )
    }
}

/** Shown in place of the chat until there is a server to talk to. */
@Composable
private fun ConnectPrompt(onOpenSettings: () -> Unit) {
    val colors = HearthTheme.colors
    Column(
        Modifier.fillMaxSize().padding(HearthTheme.spacing.screen),
        verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.xxl, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HearthMark(HearthTheme.sizes.markM)
        Text(stringResource(R.string.chat_connect_title), style = HearthTheme.type.display, color = colors.ink, textAlign = TextAlign.Center)
        Text(stringResource(R.string.chat_connect_message), color = colors.mute, style = HearthTheme.type.subhead, textAlign = TextAlign.Center)
        Button(onClick = onOpenSettings) { Text(stringResource(R.string.chat_open_settings)) }
    }
}

/** Says what went wrong, above the composer, with a way to try again where that could help. */
@Composable
private fun ErrorBanner(error: ErrorViewConfiguration, onRetry: () -> Unit, onDismiss: () -> Unit) {
    val colors = HearthTheme.colors
    val spacing = HearthTheme.spacing
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.xl, vertical = spacing.xs)
            .background(colors.card, HearthTheme.shapes.field)
            .padding(start = spacing.xxl),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = spacing.l)) {
            Text(stringResource(R.string.error_title), color = colors.danger, style = HearthTheme.type.calloutStrong)
            Text(errorMessage(error), color = colors.ink, style = HearthTheme.type.calloutTight, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
        if (error.canRetry) {
            TextButton(onClick = onRetry) { Text(stringResource(R.string.error_retry)) }
        }
        HearthIconButton(Icons.Outlined.Close, stringResource(R.string.error_dismiss), tint = colors.mute, onClick = onDismiss)
    }
}
