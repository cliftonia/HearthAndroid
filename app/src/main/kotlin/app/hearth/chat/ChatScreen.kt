package app.hearth.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hearth.R
import app.hearth.designsystem.HapticEffect
import app.hearth.designsystem.HapticKind
import app.hearth.designsystem.HearthIconButton
import app.hearth.designsystem.HearthMark
import app.hearth.designsystem.HearthTheme
import app.hearth.domain.ModelStore
import app.hearth.domain.SettingsStore
import app.hearth.integrations.AttachmentLoader
import app.hearth.integrations.PictureLibrary
import app.hearth.shared.ErrorViewConfiguration
import app.hearth.shared.errorMessage
import app.hearth.shared.errorTitle

/** The conversation: its header, its messages or what stands in for them, any error, and the composer. */
@Composable
fun ChatScreen(
    presenter: ChatPresenter,
    settingsStore: SettingsStore,
    modelStore: ModelStore,
    attachments: AttachmentLoader,
    pictures: PictureLibrary,
    modifier: Modifier = Modifier,
) {
    val viewModel by presenter.viewModel.collectAsStateWithLifecycle()
    val settings by settingsStore.settings.collectAsStateWithLifecycle()
    val models by modelStore.models.collectAsStateWithLifecycle()
    val memories by presenter.memories.collectAsStateWithLifecycle()
    // Felt once as something goes wrong. The tap for a reply that has finished is the presenter's to give.
    HapticEffect(HapticKind.ERROR, viewModel.error) { _, new -> new != null }
    ChatLayout(
        modifier.fillMaxSize(),
        header = { ChatHeader(viewModel, settings, presenter) },
        content = {
            when {
                settings.ollamaUrl == null -> ConnectPrompt(presenter::settingsAction)
                viewModel.conversation.messages.isEmpty() ->
                    EmptyState(chatGreeting(viewModel.conversation, settings, memories).words(), viewModel.conversation.isPrivate)
                else -> MessageList(viewModel, presenter, canSpeak = settings.speechUrl != null, pictures = pictures)
            }
        },
        banner = { viewModel.error?.let { ErrorBanner(it, presenter::retryAction, presenter::dismissErrorAction) } },
        footer = { ComposerFooter(viewModel, models, settings, presenter, attachments) },
    )
}

/**
 * Stacks the header, the conversation, any error and the composer, and settles what gives way when there is
 * little height, as on a phone held sideways with the keyboard up.
 *
 * The composer comes first and is always whole. The error, and then the header, are shown only where they fit
 * above it entire, and the conversation has whatever is left. So the composer is never pushed under the keyboard.
 */
@Composable
private fun ChatLayout(
    modifier: Modifier,
    header: @Composable () -> Unit,
    content: @Composable () -> Unit,
    banner: @Composable () -> Unit,
    footer: @Composable () -> Unit,
) {
    Layout(
        content = {
            Box { header() }
            Box { content() }
            Box { banner() }
            Box { footer() }
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val footerPlaceable = measurables[3].measure(Constraints(minWidth = width, maxWidth = width, maxHeight = height))
        var remaining = height - footerPlaceable.height
        // Measured at the height it wants, and left out if that is more than there is.
        val fitted: (Measurable) -> Placeable? = { measurable ->
            measurable.measure(Constraints(minWidth = width, maxWidth = width)).takeIf { it.height <= remaining }?.also { remaining -= it.height }
        }
        val bannerPlaceable = fitted(measurables[2])
        val headerPlaceable = fitted(measurables[0])
        val contentPlaceable = measurables[1].measure(Constraints.fixed(width, remaining))
        layout(width, height) {
            val headerHeight = headerPlaceable?.height ?: 0
            headerPlaceable?.place(0, 0)
            contentPlaceable.place(0, headerHeight)
            bannerPlaceable?.place(0, headerHeight + contentPlaceable.height)
            footerPlaceable.place(0, height - footerPlaceable.height)
        }
    }
}

/**
 * What a chat shows before its first message.
 *
 * Where there is not the height for all of it, the mark is left out first and then the note, so that the
 * greeting is the last to go and nothing is ever shown cut in half.
 */
@Composable
private fun EmptyState(greeting: String, isPrivate: Boolean) {
    val colors = HearthTheme.colors
    val gap = HearthTheme.spacing.gapS
    Layout(
        content = {
            HearthMark(HearthTheme.sizes.markM)
            Text(greeting, style = HearthTheme.type.display, color = colors.ink, textAlign = TextAlign.Center)
            // The note keeps its place whether or not it shows, so the greeting does not jump when the chat is made private.
            Text(
                if (isPrivate) stringResource(R.string.chat_private_note) else "",
                color = colors.mute,
                style = HearthTheme.type.footnote,
                textAlign = TextAlign.Center,
            )
        },
        modifier = Modifier.fillMaxSize().padding(HearthTheme.spacing.screen),
    ) { measurables, constraints ->
        val (mark, title, note) = measurables.map { it.measure(Constraints(maxWidth = constraints.maxWidth)) }
        val spacing = gap.roundToPx()
        val shown = listOf(listOf(mark, title, note), listOf(title, note), listOf(title))
            .firstOrNull { parts -> parts.sumOf { it.height } + spacing * (parts.size - 1) <= constraints.maxHeight }
            .orEmpty()
        layout(constraints.maxWidth, constraints.maxHeight) {
            var top = (constraints.maxHeight - shown.sumOf { it.height } - spacing * (shown.size - 1)) / 2
            shown.forEach { part ->
                part.place((constraints.maxWidth - part.width) / 2, top)
                top += part.height + spacing
            }
        }
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
            Text(errorTitle(error), color = colors.danger, style = HearthTheme.type.calloutStrong)
            Text(errorMessage(error), color = colors.ink, style = HearthTheme.type.calloutTight, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
        if (error.canRetry) {
            TextButton(onClick = onRetry) { Text(stringResource(R.string.error_retry)) }
        }
        HearthIconButton(Icons.Outlined.Close, stringResource(R.string.error_dismiss), tint = colors.mute, onClick = onDismiss)
    }
}
