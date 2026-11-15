package app.hearth.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import app.hearth.R
import app.hearth.designsystem.HearthTheme
import app.hearth.domain.CanvasExtractor
import app.hearth.domain.CanvasPiece
import app.hearth.domain.CanvasSegment
import app.hearth.shared.canvasDetail
import app.hearth.shared.canvasKindIcon
import app.hearth.shared.canvasLineCount
import app.hearth.shared.canvasTitle

/**
 * The words of a reply, with each piece of standalone work in it folded into a card that opens the canvas.
 *
 * @param isStreaming Whether the reply is still on its way, in which case a piece left open is still being written.
 * @param onOpen Opens the piece at the given place among the reply's pieces.
 */
@Composable
internal fun ReplyBodySection(markdown: String, isStreaming: Boolean, onOpen: (Int) -> Unit, modifier: Modifier = Modifier) {
    val segments = remember(markdown) { CanvasExtractor().segments(markdown) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.l)) {
        segments.forEach { segment ->
            // A reply only grows at its end, so a stretch keeps its place and its drawing as the rest arrives.
            key(segment.id) {
                when (segment) {
                    is CanvasSegment.Text -> MarkdownSection(segment.text)
                    is CanvasSegment.Piece -> CanvasCard(segment.piece, isWriting = isStreaming && !segment.piece.isClosed) { onOpen(segment.piece.index) }
                }
            }
        }
    }
}

/** A piece of standalone work in a reply, in place of its text: what it is, how long, and the way into the canvas. */
@Composable
private fun CanvasCard(piece: CanvasPiece, isWriting: Boolean, onOpen: () -> Unit) {
    val colors = HearthTheme.colors
    val spacing = HearthTheme.spacing
    val title = canvasTitle(piece.title, piece.kind)
    val detail = canvasDetail(piece.title, piece.kind, piece.lineCount)
    val label = if (isWriting) stringResource(R.string.canvas_writing_label, title) else stringResource(R.string.canvas_open_label, title, detail)
    Row(
        Modifier
            .widthIn(max = HearthTheme.sizes.canvasCardMaxWidth)
            .fillMaxWidth()
            .clip(HearthTheme.shapes.tile)
            .background(colors.card)
            .border(HearthTheme.strokes.hairline, colors.line, HearthTheme.shapes.tile)
            .clickable(role = Role.Button, onClick = onOpen)
            // One button to a screen reader, which says what it is and where it leads; its parts say nothing more.
            .clearAndSetSemantics { contentDescription = label }
            .padding(spacing.xl),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.xl),
    ) {
        Box(Modifier.size(HearthTheme.sizes.controlM).background(colors.pineSoft, HearthTheme.shapes.control), contentAlignment = Alignment.Center) {
            Icon(canvasKindIcon(piece.kind), null, tint = colors.pine, modifier = Modifier.size(HearthTheme.sizes.iconS))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
            Text(title, color = colors.ink, style = HearthTheme.type.subheadStrong, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (isWriting) stringResource(R.string.canvas_writing_progress, canvasLineCount(piece.lineCount)) else detail,
                color = colors.mute,
                style = HearthTheme.type.captionSnug,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // The spinner and the word share one place, so the card does not change shape as the piece ends.
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                Modifier.size(HearthTheme.sizes.progress).alpha(if (isWriting) 1f else 0f),
                color = colors.pine,
                strokeWidth = HearthTheme.strokes.progress,
            )
            Text(stringResource(R.string.canvas_open), color = colors.pine, style = HearthTheme.type.subheadStrong, modifier = Modifier.alpha(if (isWriting) 0f else 1f))
        }
    }
}
