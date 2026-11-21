package app.hearth.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.hearth.R
import app.hearth.designsystem.HearthTheme
import app.hearth.domain.CanvasReference
import app.hearth.domain.MessageRole
import app.hearth.integrations.PictureLibrary

/** The last thing in the list, with nothing to show: somewhere to scroll to that is always past the reply. */
private val EndAnchorHeight = 1.dp

/**
 * The messages of the conversation, oldest first, with the suggested questions after the last.
 *
 * The list runs on beneath the header floating over its head and the composer floating over its foot. [topPadding]
 * and [bottomPadding] are how tall those are, so that its first and last lines stop clear of them at either end.
 */
@Composable
internal fun MessageList(
    viewModel: ChatViewModel,
    presenter: ChatPresenter,
    canSpeak: Boolean,
    pictures: PictureLibrary,
    topPadding: Dp = 0.dp,
    bottomPadding: Dp = 0.dp,
) {
    val messages = viewModel.conversation.messages
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    // The list follows the reply as it grows, unless the reader has scrolled away to read something above.
    val following = remember(viewModel.conversation.id) { ListFollowing() }
    LaunchedEffect(listState.isScrollInProgress, following) {
        if (listState.isScrollInProgress) focusManager.clearFocus()
        following.scrollStateChanged(listState.isScrollInProgress, listState.canScrollForward)
    }
    // A finger on the list means the reader is taking it somewhere, so it stops following at once; where it is
    // left decides, above, whether it follows again.
    LaunchedEffect(listState, following) {
        listState.interactionSource.interactions.collect { if (it is DragInteraction.Start) following.dragStarted() }
    }
    val last = messages.lastOrNull()
    LaunchedEffect(messages.size, last?.text?.length, last?.thinking?.length, last?.images?.size, viewModel.followUps.size) {
        if (following.isFollowing) listState.scrollToItem(messages.size + 1)
    }
    // A picture takes its place a moment after its reply arrives, and the list follows that as well. So it does when
    // the keyboard rises or the composer grows: either leaves more of the list to scroll, and its end rises with them.
    LaunchedEffect(listState, following) {
        snapshotFlow { listState.canScrollForward && !listState.isScrollInProgress }.collect { hasGrown ->
            if (hasGrown && following.isFollowing) listState.scrollToItem((listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0))
        }
    }
    // On a wide window the messages are held to a readable width in the middle; the list itself stays the full width,
    // so that it scrolls from anywhere on it.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val margin = maxOf(HearthTheme.spacing.xxxl, (maxWidth - HearthTheme.sizes.readableMaxWidth) / 2 + HearthTheme.spacing.xxxl)
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            // Clear of the header floating over its head and of the composer floating over its foot, once scrolled to
            // either end; the list itself runs on beneath both.
            contentPadding = PaddingValues(
                start = margin,
                top = topPadding + HearthTheme.spacing.xl,
                end = margin,
                bottom = bottomPadding + HearthTheme.spacing.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.gapS),
        ) {
            items(messages, key = { it.id.raw }) { message ->
                val isLast = message.id == messages.last().id
                val isReplying = isLast && viewModel.isStreaming && message.role == MessageRole.ASSISTANT
                MessageSection(
                    MessageViewConfiguration(
                        message = message,
                        isStreaming = isReplying,
                        searchingFor = if (isReplying) viewModel.searchingFor else null,
                        pictureWork = if (isReplying) viewModel.pictureWork else null,
                        pictureJobsAhead = if (isReplying) viewModel.pictureJobsAhead else null,
                        researchStep = if (isReplying) viewModel.researchStep else null,
                        hasSearchFailed = isLast && viewModel.hasSearchFailed,
                        memoryNotice = if (isLast) viewModel.memoryNotice else null,
                        canRetry = isLast && !viewModel.isStreaming,
                        isSpeaking = viewModel.speakingMessageId == message.id,
                        canSpeak = canSpeak,
                        canEdit = !viewModel.isStreaming,
                    ),
                    actions = MessageActions(
                        onRetry = presenter::regenerateAction,
                        onSpeak = { presenter.speakAction(message.id) },
                        onEdit = { presenter.editAction(message.id) },
                        onVersion = { index -> presenter.selectVersionAction(index, message.id) },
                        onCanvas = { index -> presenter.canvasAction(CanvasReference(message.id, index)) },
                    ),
                    pictures = pictures,
                )
            }
            item(key = "followUps") { FollowUps(viewModel.followUps, presenter::followUpAction) }
            item(key = "end") { Spacer(Modifier.height(EndAnchorHeight)) }
        }
    }
}

/** Questions the model suggests asking next. Tapping one sends it. */
@Composable
private fun FollowUps(questions: List<String>, onAsk: (String) -> Unit) {
    if (questions.isEmpty()) return
    val colors = HearthTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.m)) {
        Text(stringResource(R.string.chat_follow_ups), color = colors.mute, style = HearthTheme.type.footnote, modifier = Modifier.semantics { heading() })
        questions.forEach { question ->
            Text(
                question,
                color = colors.pine,
                style = HearthTheme.type.subheadSnug,
                modifier = Modifier
                    .clip(HearthTheme.shapes.field)
                    .background(colors.pineSoft)
                    .clickable(role = Role.Button) { onAsk(question) }
                    .padding(horizontal = HearthTheme.spacing.xxl, vertical = HearthTheme.spacing.l),
            )
        }
    }
}
