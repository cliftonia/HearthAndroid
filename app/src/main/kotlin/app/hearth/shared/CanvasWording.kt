package app.hearth.shared

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Web
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.hearth.R
import app.hearth.domain.CanvasKind

// How a piece of work is named and drawn, in its card in a reply and in the canvas.

/** What the kind is called: also the title of a piece that has none of its own. */
@Composable
fun canvasKindName(kind: CanvasKind): String = when (kind) {
    CanvasKind.Html -> stringResource(R.string.canvas_kind_page)
    CanvasKind.Svg -> stringResource(R.string.canvas_kind_drawing)
    CanvasKind.Markdown -> stringResource(R.string.canvas_kind_document)
    is CanvasKind.Code -> kind.language
        ?.let { stringResource(R.string.canvas_kind_code_in, it.replaceFirstChar(Char::uppercaseChar)) }
        ?: stringResource(R.string.canvas_kind_code)
}

/** The symbol that stands for the kind. */
fun canvasKindIcon(kind: CanvasKind): ImageVector = when (kind) {
    CanvasKind.Html -> Icons.Outlined.Web
    CanvasKind.Svg -> Icons.Outlined.Draw
    CanvasKind.Markdown -> Icons.Outlined.Description
    is CanvasKind.Code -> Icons.Outlined.Code
}

/** The piece's own title, or the name of its kind. */
@Composable
fun canvasTitle(title: String?, kind: CanvasKind): String = title ?: canvasKindName(kind)

/** How long a piece is, in words. */
@Composable
fun canvasLineCount(lineCount: Int): String = pluralStringResource(R.plurals.canvas_lines, lineCount, lineCount)

/** The line under a piece's title: its length, after its kind unless the kind is already its title. */
@Composable
fun canvasDetail(title: String?, kind: CanvasKind, lineCount: Int): String =
    if (title == null) canvasLineCount(lineCount) else stringResource(R.string.canvas_detail, canvasKindName(kind), canvasLineCount(lineCount))
