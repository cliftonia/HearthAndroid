package app.hearth.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.hearth.R
import app.hearth.designsystem.HearthColors
import app.hearth.designsystem.HearthIconButton
import app.hearth.designsystem.HearthTheme
import app.hearth.domain.CodeHighlighter
import app.hearth.domain.CodeTokenKind
import app.hearth.shared.rememberCopy
import kotlinx.coroutines.launch

/**
 * Code on a panel that scrolls sideways, coloured by its keywords, strings, comments and numbers, under its language
 * and buttons that copy it and open it on a sheet of its own.
 */
@Composable
internal fun CodeBlockSection(language: String?, code: String, modifier: Modifier = Modifier) {
    val colors = HearthTheme.colors
    val copy = rememberCopy()
    var isExpanded by remember { mutableStateOf(false) }
    val title = language ?: stringResource(R.string.message_code)
    // Coloured once and kept: the code is read through again only when it, its language or the appearance changes.
    val coloured = remember(code, language, colors) { colouredCode(code, language, colors) }
    Column(modifier.fillMaxWidth().clip(HearthTheme.shapes.tile).background(colors.bubble)) {
        Row(Modifier.fillMaxWidth().padding(start = HearthTheme.spacing.xxl), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = colors.mute, style = HearthTheme.type.caption, modifier = Modifier.weight(1f))
            HearthIconButton(Icons.Outlined.ContentCopy, stringResource(R.string.message_copy_code), tint = colors.mute) { copy(code) }
            HearthIconButton(Icons.Outlined.OpenInFull, stringResource(R.string.message_expand_code), tint = colors.mute) { isExpanded = true }
        }
        Text(
            coloured,
            style = HearthTheme.type.code,
            color = colors.ink,
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(start = HearthTheme.spacing.xxl, end = HearthTheme.spacing.xxl, bottom = HearthTheme.spacing.xxl),
        )
    }
    if (isExpanded) CodeSheet(title, coloured, onCopy = { copy(code) }) { isExpanded = false }
}

/**
 * A block of code on a sheet the height of the screen, where long lines and many of them can be read by scrolling
 * either way, and words of it selected. Unlike the app's other sheets it has a button to close it: a drag down on
 * the code scrolls the code, so the handle alone would be a small thing to aim for.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CodeSheet(title: String, code: AnnotatedString, onCopy: () -> Unit, onDismiss: () -> Unit) {
    val colors = HearthTheme.colors
    val spacing = HearthTheme.spacing
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // The sheet slides away before it is taken off the screen.
    val close: () -> Unit = { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() } }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.paper,
        // No tint from elevation: the sheet is the same paper as the screen it rises over.
        tonalElevation = 0.dp,
    ) {
        Row(Modifier.fillMaxWidth().padding(start = spacing.screen, end = spacing.m), verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                color = colors.ink,
                style = HearthTheme.type.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            HearthIconButton(Icons.Outlined.Close, stringResource(R.string.common_close), tint = colors.mute, onClick = close)
        }
        // Takes all the height the sheet can have, so the sheet opens to the top of the screen however short the code.
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = spacing.screen)
                .clip(HearthTheme.shapes.card)
                .background(colors.card)
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState()),
        ) {
            SelectionContainer {
                Text(code, color = colors.ink, style = HearthTheme.type.code, softWrap = false, modifier = Modifier.padding(spacing.xxxl))
            }
        }
        Button(onClick = onCopy, modifier = Modifier.fillMaxWidth().padding(spacing.screen).heightIn(min = HearthTheme.sizes.touchTarget)) {
            Icon(Icons.Outlined.ContentCopy, null, Modifier.padding(end = spacing.m).size(HearthTheme.sizes.iconS))
            Text(stringResource(R.string.message_copy_code), style = HearthTheme.type.subheadStrong)
        }
    }
}

/** The code with each keyword, string, comment and number in its colour; the rest is left to the colour of the text. */
private fun colouredCode(code: String, language: String?, colors: HearthColors): AnnotatedString = buildAnnotatedString {
    CodeHighlighter().tokens(code, language).forEach { token ->
        val color = when (token.kind) {
            CodeTokenKind.PLAIN -> Color.Unspecified
            CodeTokenKind.KEYWORD -> colors.codeKeyword
            CodeTokenKind.STRING -> colors.codeString
            CodeTokenKind.COMMENT -> colors.mute
            CodeTokenKind.NUMBER -> colors.codeNumber
        }
        withStyle(SpanStyle(color = color)) { append(token.text) }
    }
}
