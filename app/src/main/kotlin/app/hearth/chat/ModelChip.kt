package app.hearth.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import app.hearth.designsystem.HearthTheme

/**
 * The model the message goes to and how hard it will think, or the picture model while pictures are being made.
 * Tapping it opens the sheet where both are chosen.
 *
 * The name gives way before the effort does: where there is not room for all of it, its middle is left out.
 */
@Composable
internal fun ModelChip(name: String, effort: String, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = HearthTheme.colors
    Row(
        modifier
            .height(HearthTheme.sizes.controlS)
            .clip(HearthTheme.shapes.pill)
            .background(colors.bubble)
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
            .padding(horizontal = HearthTheme.spacing.xl),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.s),
    ) {
        val nameRoom = Modifier.weight(1f, fill = false).widthIn(max = HearthTheme.sizes.chipLabelMaxWidth)
        MiddleTruncatedText(name, colors.ink, HearthTheme.type.footnoteStrong, nameRoom)
        if (effort.isNotEmpty()) Text(effort, color = colors.mute, style = HearthTheme.type.footnote, maxLines = 1, softWrap = false)
    }
}

/** One line of text which, where it does not fit, keeps its beginning and its end. A screen reader is given all of it. */
@Composable
private fun MiddleTruncatedText(text: String, color: Color, style: TextStyle, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier.clearAndSetSemantics { this.text = AnnotatedString(text) }) {
        val room = constraints.maxWidth
        val shown = remember(text, style, room, measurer) { measurer.middleTruncated(text, style, room) }
        Text(shown, color = color, style = style, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
    }
}

/** The most of the text that fits the width, with an ellipsis where its middle was. */
private fun TextMeasurer.middleTruncated(text: String, style: TextStyle, maxWidth: Int): String {
    fun fits(candidate: String) = measure(candidate, style, maxLines = 1, softWrap = false).size.width <= maxWidth
    if (maxWidth == Constraints.Infinity || fits(text)) return text
    // The most characters that can be kept is searched for by halving.
    var low = 0
    var high = text.length - 1
    while (low < high) {
        val middle = (low + high + 1) / 2
        if (fits(text.keeping(middle))) low = middle else high = middle - 1
    }
    return text.keeping(low)
}

/** The text with only this many characters kept, shared between its beginning and its end. */
private fun String.keeping(count: Int): String {
    val leading = (count + 1) / 2
    return take(leading) + "…" + takeLast(count - leading)
}
