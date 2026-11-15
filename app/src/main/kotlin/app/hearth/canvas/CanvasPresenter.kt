package app.hearth.canvas

import android.util.Log
import app.hearth.domain.CanvasExtractor
import app.hearth.domain.CanvasPiece
import app.hearth.domain.CanvasReference
import app.hearth.domain.CanvasReply
import app.hearth.domain.CanvasVersionMatcher
import app.hearth.domain.Message
import app.hearth.domain.MessageId
import app.hearth.domain.MessageRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** What the canvas asks of the coordinator. */
interface CanvasPresenterDelegate {
    /** The canvas is done with and should be put away. */
    fun canvasPresenterDidClose()
}

/**
 * Runs the canvas: finds the opened piece and its versions in the open chat, and steps between them.
 *
 * It keeps nothing of the pieces. Each is read from the chat's messages whenever the chat changes, so the panel
 * follows a reply as it streams and is never out of step with what is saved.
 *
 * @param messages The messages of the open chat.
 * @param streamingMessageId The reply on its way at this moment, if one is.
 */
class CanvasPresenter(
    private val messages: () -> List<Message>,
    private val streamingMessageId: () -> MessageId?,
) {
    private val state = MutableStateFlow(CanvasViewModel())
    val viewModel: StateFlow<CanvasViewModel> = state.asStateFlow()
    var delegate: CanvasPresenterDelegate? = null

    /**
     * The pieces last read from each reply and the text they were read from, so that only a reply that has
     * changed is read again.
     */
    private var cache: Map<MessageId, Pair<String, List<CanvasPiece>>> = emptyMap()

    // Opening and closing

    /** Shows a piece. If it is the newest of its versions the canvas goes on following them; an older one stays put. */
    fun open(reference: CanvasReference) {
        val versions = CanvasVersionMatcher().versions(reference, replies())
        state.value = CanvasViewModel(anchor = reference, selected = reference.takeIf { versions.lastOrNull() != it })
        refresh()
        Log.i(TAG, "The canvas was opened, with ${versions.size} versions")
    }

    /**
     * Puts the canvas away. What it was showing is left as it was, for the moment the panel takes to go, and is
     * no longer kept in step with the chat.
     */
    fun closeAction() {
        state.update { it.copy(anchor = null, selected = null) }
        cache = emptyMap()
        delegate?.canvasPresenterDidClose()
    }

    /**
     * The chat has changed under the canvas. What it shows is read again; if the piece it was opened for is
     * gone, as when another chat is opened or the message is edited away, the canvas closes.
     */
    fun chatChangedAction() {
        if (state.value.anchor == null) return
        refresh()
        if (state.value.content == null) closeAction()
    }

    // In the panel

    /** Steps to the version before or after the one shown. Reaching the newest goes back to following it. */
    fun stepAction(offset: Int) {
        val current = state.value
        val anchor = current.anchor ?: return
        val versions = CanvasVersionMatcher().versions(anchor, replies())
        val shown = versions.indexOf(current.selected).takeIf { it >= 0 } ?: versions.lastIndex
        val wanted = versions.getOrNull(shown + offset) ?: return
        state.update { it.copy(selected = wanted.takeIf { shown + offset != versions.lastIndex }) }
        refresh()
    }

    /** Chooses between the preview and the code, for a piece that has both. */
    fun modeAction(mode: CanvasModeKind) {
        state.update { it.copy(mode = mode) }
        refresh()
    }

    // Internals

    /** Reads what the canvas shows out of the chat as it now stands. */
    private fun refresh() = state.update { it.copy(content = content(it)) }

    /** The version being looked at, ready to draw; `null` when nothing is open or the piece is no longer in the chat. */
    private fun content(current: CanvasViewModel): CanvasViewConfiguration? {
        val anchor = current.anchor ?: return null
        val replies = replies()
        val versions = CanvasVersionMatcher().versions(anchor, replies)
        // A version stepped back to is kept; otherwise the newest is followed.
        val shown = versions.firstOrNull { it == current.selected } ?: versions.lastOrNull() ?: return null
        val piece = replies.firstOrNull { it.messageId == shown.messageId }?.pieces?.firstOrNull { it.index == shown.index } ?: return null
        val isWriting = !piece.isClosed && streamingMessageId() == shown.messageId
        return CanvasViewConfiguration(
            title = piece.title,
            kind = piece.kind,
            text = piece.text,
            lineCount = piece.lineCount,
            isWriting = isWriting,
            versionNumber = versions.indexOf(shown) + 1,
            versionCount = versions.size,
            mode = if (piece.kind.hasPreview && !isWriting) current.mode else CanvasModeKind.CODE,
        )
    }

    /** The pieces of each reply in the chat that is made of words, in order. */
    private fun replies(): List<CanvasReply> {
        val extractor = CanvasExtractor()
        val replies = messages().filter { it.role == MessageRole.ASSISTANT && !it.isPicture }
        val fresh = replies.associate { reply ->
            val known = cache[reply.id]?.takeIf { it.first == reply.text }
            reply.id to (known ?: (reply.text to extractor.pieces(reply.text)))
        }
        cache = fresh
        return replies.map { CanvasReply(it.id, fresh[it.id]?.second.orEmpty()) }
    }

    private companion object {
        const val TAG = "CanvasPresenter"
    }
}
