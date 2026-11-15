package app.hearth.canvas

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import app.hearth.designsystem.HearthTheme

/**
 * A piece as it was written: monospaced, never wrapped, scrolling both ways, with its lines numbered.
 *
 * @param isWriting Whether the code is still arriving, in which case its end is kept in view.
 */
@Composable
internal fun CanvasCodeSection(code: String, isWriting: Boolean, modifier: Modifier = Modifier) {
    val colors = HearthTheme.colors
    val inset = HearthTheme.spacing.xl
    val vertical = rememberScrollState()
    val numbers = remember(code) { canvasLineNumbers(code) }
    LaunchedEffect(isWriting) {
        if (isWriting) snapshotFlow { vertical.maxValue }.collect { vertical.scrollTo(it) }
    }
    Row(modifier.fillMaxSize().background(colors.card).verticalScroll(vertical)) {
        // The numbers stand in a column of their own, so they stay put while the code scrolls sideways. Both
        // columns are set in the one style, whose lines are all of a height, so each number stays beside its line.
        Text(
            numbers,
            color = colors.mute,
            style = HearthTheme.type.code,
            textAlign = TextAlign.End,
            softWrap = false,
            modifier = Modifier.padding(start = inset, top = inset, end = HearthTheme.spacing.m, bottom = inset).clearAndSetSemantics { },
        )
        SelectionContainer(Modifier.weight(1f)) {
            val codeInset = Modifier.padding(start = HearthTheme.spacing.xs, top = inset, end = inset, bottom = inset)
            Text(code, color = colors.ink, style = HearthTheme.type.code, softWrap = false, modifier = Modifier.horizontalScroll(rememberScrollState()).then(codeInset))
        }
    }
}

/** The column of line numbers for the code: one for every line, and one for no code at all. */
internal fun canvasLineNumbers(code: String): String = (1..code.count { it == '\n' } + 1).joinToString("\n")
