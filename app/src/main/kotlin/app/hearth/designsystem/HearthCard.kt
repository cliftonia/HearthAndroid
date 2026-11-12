package app.hearth.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics

/** A titled group of related controls on a card, with an optional note beneath it saying what they do. */
@Composable
fun HearthCard(title: String, footer: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val colors = HearthTheme.colors
    val spacing = HearthTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.m)) {
        Text(title, color = colors.mute, style = HearthTheme.type.footnote, modifier = Modifier.padding(start = spacing.xs).semantics { heading() })
        Column(
            Modifier.fillMaxWidth().background(colors.card, HearthTheme.shapes.card).padding(spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(spacing.l),
            content = content,
        )
        footer?.let { Text(it, color = colors.mute, style = HearthTheme.type.captionSnug, modifier = Modifier.padding(horizontal = spacing.xs)) }
    }
}
