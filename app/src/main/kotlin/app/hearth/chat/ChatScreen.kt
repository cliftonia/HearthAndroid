package app.hearth.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.WindowInsets
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

/** How far up the foot of the chat, or down its head, the paper is solid before it thins to nothing. */
private const val FADE_SOLID_REACH = 0.6f

/**
 * How much of the paper veils the conversation where it passes beneath the composer: enough to quieten it, and little
 * enough that it still shows through the composer, which is see-through.
 */
private const val FOOT_VEIL_ALPHA = 0.7f

/**
 * The conversation: its messages or what stands in for them, with the header floating over their head and the
 * composer, under any error, floating over their foot.
 */
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
        content = { topPadding, bottomPadding ->
            // What stands in for the messages keeps to the room between the header and the composer.
            val clear = Modifier.padding(top = topPadding, bottom = bottomPadding)
            when {
                settings.ollamaUrl == null -> Box(clear) { ConnectPrompt(presenter::settingsAction) }
                viewModel.conversation.messages.isEmpty() ->
                    Box(clear) { EmptyState(chatGreeting(viewModel.conversation, settings, memories).words(), viewModel.conversation.isPrivate) }
                else -> MessageList(
                    viewModel,
                    presenter,
                    canSpeak = settings.speechUrl != null,
                    pictures = pictures,
                    topPadding = topPadding,
                    bottomPadding = bottomPadding,
                )
            }
        },
        banner = { viewModel.error?.let { ErrorBanner(it, presenter::retryAction, presenter::dismissErrorAction) } },
        footer = { ComposerFooter(viewModel, models, settings, presenter, attachments) },
    )
}

/**
 * Lays the conversation over the whole screen, with the header floating over its head and any error and the composer
 * over its foot, and settles what gives way when there is little height, as on a phone held sideways with the
 * keyboard up.
 *
 * The composer comes first and is always whole. The error, and then the header, are shown only where they fit
 * above it entire. So the composer is never pushed under the keyboard.
 *
 * The conversation scrolls on beneath the header and the status bar at its head, and beneath the error and the
 * composer at its foot, fading into the paper at both. It is told how tall each is, so that its first line and its
 * last can still be scrolled clear, and what stands in for it can keep to the room between them.
 */
@Composable
private fun ChatLayout(
    modifier: Modifier,
    header: @Composable () -> Unit,
    content: @Composable (topPadding: Dp, bottomPadding: Dp) -> Unit,
    banner: @Composable () -> Unit,
    footer: @Composable () -> Unit,
) {
    // On a wide window the error and the composer are held to the readable width of the messages, in the middle.
    val readable = Modifier.widthIn(max = HearthTheme.sizes.readableMaxWidth)
    val paper = HearthTheme.colors.paper
    val clear = paper.copy(alpha = 0f)
    val headFade = Modifier.background(Brush.verticalGradient(FADE_SOLID_REACH to paper, 1f to clear))
    val footFade = Modifier.background(Brush.verticalGradient(0f to clear, 1f - FADE_SOLID_REACH to paper.copy(alpha = FOOT_VEIL_ALPHA)))
    SubcomposeLayout(modifier) { constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val footerPlaceable = subcompose(ChatSlot.FOOTER) { Box(contentAlignment = Alignment.TopCenter) { Box(readable) { footer() } } }
            .single().measure(Constraints(minWidth = width, maxWidth = width, maxHeight = height))
        var remaining = height - footerPlaceable.height
        // Measured at the height it wants, and left out if that is more than there is.
        val fitted: (Measurable) -> Placeable? = { measurable ->
            measurable.measure(Constraints(minWidth = width, maxWidth = width)).takeIf { it.height <= remaining }?.also { remaining -= it.height }
        }
        val bannerPlaceable = fitted(subcompose(ChatSlot.BANNER) { Box(contentAlignment = Alignment.TopCenter) { Box(readable) { banner() } } }.single())
        val headerPlaceable = fitted(
            subcompose(ChatSlot.HEADER) {
                Box(headFade.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))) { header() }
            }.single(),
        )
        val headerHeight = headerPlaceable?.height ?: 0
        // What floats over the foot of the conversation: the composer, and the error where it is shown.
        val footHeight = footerPlaceable.height + (bannerPlaceable?.height ?: 0)
        val contentPlaceable = subcompose(ChatSlot.CONTENT) { Box { content(headerHeight.toDp(), footHeight.toDp()) } }
            .single().measure(Constraints.fixed(width, height))
        // The paper the conversation fades into behind the error and the composer. It takes no touches, so those
        // beside the composer still reach the conversation.
        val footFadePlaceable = subcompose(ChatSlot.FOOT_FADE) { Box(footFade) }
            .single().measure(Constraints.fixed(width, footHeight))
        layout(width, height) {
            // The conversation first, so that what floats is drawn over it.
            contentPlaceable.place(0, 0)
            headerPlaceable?.place(0, 0)
            footFadePlaceable.place(0, height - footHeight)
            bannerPlaceable?.place(0, height - footHeight)
            footerPlaceable.place(0, height - footerPlaceable.height)
        }
    }
}

/** The parts of the chat screen, each composed once its room is known. */
private enum class ChatSlot { HEADER, CONTENT, FOOT_FADE, BANNER, FOOTER }

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
            // Touches stop here, so that the conversation beneath is not scrolled through it.
            .pointerInput(Unit) { }
            .background(colors.card, HearthTheme.shapes.field)
            .padding(start = spacing.xxl),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Announced when it appears or changes, without interrupting what is being read.
        Column(
            Modifier
                .weight(1f)
                .padding(vertical = spacing.l)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        ) {
            Text(errorTitle(error), color = colors.danger, style = HearthTheme.type.calloutStrong)
            Text(errorMessage(error), color = colors.ink, style = HearthTheme.type.calloutTight, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
        if (error.canRetry) {
            TextButton(onClick = onRetry) { Text(stringResource(R.string.error_retry)) }
        }
        HearthIconButton(Icons.Outlined.Close, stringResource(R.string.error_dismiss), tint = colors.mute, onClick = onDismiss)
    }
}
