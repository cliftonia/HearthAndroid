package app.hearth.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
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
import app.hearth.designsystem.isLargeFont

/**
 * The model the message goes to and how hard it will think, or the picture model while pictures are being made.
 * Tapping it opens the sheet where both are chosen.
 *
 * The name gives way before the effort does: where there is not room for all of it, its middle is left out. With
 * large text that would leave nothing of the name, so there the effort goes on a line under it. The chip grows taller
 * with its text rather than cutting it off.
 * [isEffortProminent] shows the effort in the brand colour, as research is, so that it is plain what the next
 * message will set off.
 */
@Composable
internal fun ModelChip(name: String, effort: String, label: String, modifier: Modifier = Modifier, isEffortProminent: Boolean = false, onClick: () -> Unit) {
    val colors = HearthTheme.colors
    val isLarge = isLargeFont()
    val chip = modifier
        // A finger is given the least it needs, round the chip as it is drawn.
        .minimumInteractiveComponentSize()
        .heightIn(min = HearthTheme.sizes.controlS)
        // Two lines in a pill would be an oval, so the stacked chip has the field's corners.
        .clip(if (isLarge) HearthTheme.shapes.field else HearthTheme.shapes.pill)
        .background(colors.bubble)
        .clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
        .padding(horizontal = HearthTheme.spacing.xl, vertical = HearthTheme.spacing.xxs)
    val nameRoom = Modifier.widthIn(max = HearthTheme.sizes.chipLabelMaxWidth)
    val effortText: @Composable () -> Unit = {
        if (effort.isNotEmpty()) {
            Text(
                effort,
                color = if (isEffortProminent) colors.pine else colors.mute,
                style = HearthTheme.type.footnote,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    if (isLarge) {
        // Large text has no room for both on one line, so the effort goes under the name rather than squeezing it.
        Column(chip, verticalArrangement = Arrangement.Center) {
            MiddleTruncatedText(name, colors.ink, HearthTheme.type.footnoteStrong, nameRoom)
            effortText()
        }
    } else {
        Row(chip, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.s)) {
            MiddleTruncatedText(name, colors.ink, HearthTheme.type.footnoteStrong, Modifier.weight(1f, fill = false).then(nameRoom))
            effortText()
        }
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
