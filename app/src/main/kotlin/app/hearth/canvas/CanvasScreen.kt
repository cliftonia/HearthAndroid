package app.hearth.canvas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Share
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hearth.R
import app.hearth.designsystem.HapticKind
import app.hearth.designsystem.Haptics
import app.hearth.designsystem.HearthIconButton
import app.hearth.designsystem.HearthTheme
import app.hearth.designsystem.HearthVersionStepper
import app.hearth.designsystem.HearthVersionStepperLabels
import app.hearth.domain.CanvasKind
import app.hearth.domain.CanvasWebPolicy
import app.hearth.shared.canvasDetail
import app.hearth.shared.canvasTitle
import app.hearth.shared.rememberCopy
import app.hearth.shared.share

/**
 * The canvas: one piece of a reply shown on its own, as it is meant to be seen or as it was written.
 *
 * @param markdown Lays a document out as a reply is. It is handed in because the canvas does not know the chat.
 * @param canMakePage Whether a page's web view may be made yet. The page over a narrow window waits until it has
 * risen into place: a web view made while it slides in can be drawn over the whole window for a moment.
 * @param isFullScreen Whether the canvas is a page over the whole of a narrow window, rather than a column beside the
 * chat. As a page it has Android's top app bar: the close button at the start, copy and share at the end.
 */
@Composable
fun CanvasScreen(
    presenter: CanvasPresenter,
    markdown: @Composable (String) -> Unit,
    modifier: Modifier = Modifier,
    canMakePage: Boolean = true,
    isFullScreen: Boolean = false,
) {
    val viewModel by presenter.viewModel.collectAsStateWithLifecycle()
    val content = viewModel.content
    Column(modifier.background(HearthTheme.colors.paper)) {
        if (isFullScreen) PageBar(content, presenter::closeAction) else Header(content, presenter::closeAction)
        if (content != null && (content.kind.hasPreview || content.versionCount > 1)) Controls(content, presenter)
        Box(Modifier.fillMaxWidth().height(HearthTheme.strokes.hairline).background(HearthTheme.colors.line))
        Box(Modifier.fillMaxWidth().weight(1f)) {
            // Another piece opened while the canvas is showing starts afresh, with no page left over from the last.
            if (content != null) key(viewModel.anchor) { Piece(content, markdown, canMakePage) }
        }
    }
}

/** The title and what it is, then copy, share and close: the canvas as a column beside the chat. */
@Composable
private fun Header(content: CanvasViewConfiguration?, onClose: () -> Unit) {
    val spacing = HearthTheme.spacing
    Row(Modifier.fillMaxWidth().padding(start = spacing.xxxl, end = spacing.xs, top = spacing.m), verticalAlignment = Alignment.CenterVertically) {
        TitleLines(content, Modifier.weight(1f))
        CopyAndShare(content)
        HearthIconButton(Icons.Outlined.Close, stringResource(R.string.canvas_close), onClick = onClose)
    }
}

/** The canvas as a page of its own: Android's top app bar, closed from the start, with copy and share at the end. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PageBar(content: CanvasViewConfiguration?, onClose: () -> Unit) {
    TopAppBar(
        title = { TitleLines(content) },
        navigationIcon = { HearthIconButton(Icons.Outlined.Close, stringResource(R.string.canvas_close), onClick = onClose) },
        actions = { CopyAndShare(content) },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = HearthTheme.colors.paper),
        // The window's edges are kept clear by the page's own padding.
        windowInsets = WindowInsets(0, 0, 0, 0),
    )
}

/** The piece's title, and beneath it what it is or that it is still being written. */
@Composable
private fun TitleLines(content: CanvasViewConfiguration?, modifier: Modifier = Modifier) {
    val colors = HearthTheme.colors
    val detail = when {
        content == null -> ""
        content.isWriting -> stringResource(R.string.canvas_writing)
        else -> canvasDetail(content.title, content.kind, content.lineCount)
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.xxs)) {
        Text(
            content?.let { canvasTitle(it.title, it.kind) } ?: stringResource(R.string.canvas_title),
            color = colors.ink,
            style = HearthTheme.type.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() },
        )
        Text(detail, color = colors.mute, style = HearthTheme.type.captionSnug, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Copies the piece as it was written, or hands it to another app. */
@Composable
private fun CopyAndShare(content: CanvasViewConfiguration?) {
    val copy = rememberCopy()
    val context = LocalContext.current
    val text = content?.text.orEmpty()
    HearthIconButton(Icons.Outlined.ContentCopy, stringResource(R.string.canvas_copy)) { copy(text) }
    HearthIconButton(Icons.Outlined.Share, stringResource(R.string.canvas_share)) { share(context, text) }
}

/** The switch between the preview and the code, for a piece that has both, and the stepper between versions. */
@Composable
private fun Controls(content: CanvasViewConfiguration, presenter: CanvasPresenter) {
    val spacing = HearthTheme.spacing
    Row(
        Modifier.fillMaxWidth().padding(start = spacing.xxxl, end = spacing.xs, top = spacing.xs, bottom = spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (content.kind.hasPreview) ModeSwitch(content, presenter::modeAction)
        Spacer(Modifier.weight(1f))
        if (content.versionCount > 1) VersionStepper(content, presenter::stepAction)
    }
}

/** Preview or Code. A piece still being written is shown as code whatever is chosen, so the switch waits for it. */
@Composable
private fun ModeSwitch(content: CanvasViewConfiguration, onMode: (CanvasModeKind) -> Unit) {
    val showAs = stringResource(R.string.canvas_show_as)
    val modes = listOf(CanvasModeKind.PREVIEW to R.string.canvas_preview, CanvasModeKind.CODE to R.string.canvas_kind_code)
    SingleChoiceSegmentedButtonRow(Modifier.width(HearthTheme.sizes.canvasSwitchWidth).semantics { contentDescription = showAs }) {
        modes.forEachIndexed { index, (mode, label) ->
            SegmentedButton(
                selected = content.mode == mode,
                onClick = {
                    Haptics.play(HapticKind.SELECTION)
                    onMode(mode)
                },
                shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                enabled = !content.isWriting,
                // The fill says which is chosen; a tick as well would crowd so small a switch.
                icon = {},
            ) { Text(stringResource(label), style = HearthTheme.type.control, maxLines = 1) }
        }
    }
}

/** Steps to an earlier or a later version, with the place of the one shown between the arrows. */
@Composable
private fun VersionStepper(content: CanvasViewConfiguration, onStep: (Int) -> Unit) {
    HearthVersionStepper(
        HearthVersionStepperLabels(
            counter = stringResource(R.string.canvas_version, content.versionNumber, content.versionCount),
            position = stringResource(R.string.message_version_position, content.versionNumber, content.versionCount),
            previous = stringResource(R.string.canvas_earlier),
            next = stringResource(R.string.canvas_later),
        ),
        hasPrevious = content.hasEarlier,
        hasNext = content.hasLater,
        onStep = onStep,
    )
}

/**
 * The piece itself: its code, a document laid out as a reply is, or a page or a drawing in a web view shut off
 * from the network.
 */
@Composable
private fun Piece(content: CanvasViewConfiguration, markdown: @Composable (String) -> Unit, canMakePage: Boolean) {
    var isPreviewUnavailable by remember { mutableStateOf(false) }
    val isPreview = content.mode == CanvasModeKind.PREVIEW
    // The page last finished. One still being written hands nothing over, so the web view is made only for a
    // finished page and is not loaded afresh with every word of the next.
    val finished = remember(content.kind, content.text, content.isWriting) {
        if (content.isWriting) null else CanvasWebPolicy().document(content.kind, content.text)
    }
    val lastFinished = remember { arrayOfNulls<String>(1) }
    if (finished != null) lastFinished[0] = finished
    val page = lastFinished[0]
    if (page != null && !isPreviewUnavailable && canMakePage) {
        // Kept while the code is looked at, unseen and out of reach, so that a page goes on as it was. Clipped, so that
        // the web view never draws outside its place, whatever it was told its size was.
        CanvasWebPreview(page, isShown = isPreview, onUnavailable = { isPreviewUnavailable = true }, modifier = Modifier.fillMaxSize().clipToBounds())
    }
    when {
        !isPreview -> CanvasCodeSection(content.text, content.isWriting)
        content.kind == CanvasKind.Markdown -> Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(HearthTheme.spacing.xxxl)) {
            SelectionContainer { markdown(content.text) }
        }
        isPreviewUnavailable -> Text(
            stringResource(R.string.canvas_preview_unavailable),
            color = HearthTheme.colors.mute,
            style = HearthTheme.type.subheadSnug,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(HearthTheme.spacing.screen),
        )
    }
}
