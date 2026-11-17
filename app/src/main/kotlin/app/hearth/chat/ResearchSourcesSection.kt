package app.hearth.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import app.hearth.R
import app.hearth.designsystem.HearthTheme
import app.hearth.domain.SearchSource
import app.hearth.domain.TextPatterns

/** One source of a report as it is listed: the number the report cites it by, its title and its site. */
data class ResearchSourceViewConfiguration(val url: String, val number: Int, val title: String, val host: String, val isLast: Boolean) {
    companion object {
        /** The sources of a report, numbered from one in the order the report cites them. */
        fun list(sources: List<SearchSource>): List<ResearchSourceViewConfiguration> = sources.mapIndexed { index, source ->
            val title = source.title.trim().replace(TextPatterns.WHITESPACE, " ")
            val host = source.host.trim().replace(TextPatterns.WHITESPACE, " ")
            ResearchSourceViewConfiguration(source.url, index + 1, title.ifEmpty { host }, host, isLast = index == sources.lastIndex)
        }
    }
}

/**
 * The pages a report was written from, under the report: each with its number, title and site, and each a link.
 *
 * It is the full-length form of the pills over an ordinary reply that searched the web, drawn from the same
 * tokens; a report has more sources, and each is cited by number, so the titles are worth the room.
 */
@Composable
internal fun ResearchSourcesSection(sources: List<ResearchSourceViewConfiguration>, modifier: Modifier = Modifier) {
    val colors = HearthTheme.colors
    val spacing = HearthTheme.spacing
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.s)) {
        Text(
            stringResource(R.string.research_sources),
            color = colors.mute,
            style = HearthTheme.type.caption,
            modifier = Modifier.padding(horizontal = spacing.xs).semantics { heading() },
        )
        Column(Modifier.fillMaxWidth().clip(HearthTheme.shapes.card).background(colors.card)) {
            sources.forEach { source ->
                SourceRow(source)
                if (!source.isLast) HorizontalDivider(Modifier.padding(start = spacing.xl), thickness = HearthTheme.strokes.hairline, color = colors.line)
            }
        }
    }
}

/** One source. Tapping it opens the page, if its address is a web page's. */
@Composable
private fun SourceRow(source: ResearchSourceViewConfiguration) {
    val colors = HearthTheme.colors
    val spacing = HearthTheme.spacing
    val uriHandler = LocalUriHandler.current
    val label = stringResource(R.string.research_source, source.number, source.title, source.host)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = stringResource(R.string.research_open_source), role = Role.Button) {
                if (SearchSource.isWebLink(source.url)) runCatching { uriHandler.openUri(source.url) }
            }
            // Read out as one thing: its number, its title and its site.
            .clearAndSetSemantics { contentDescription = label }
            .padding(horizontal = spacing.xl, vertical = spacing.l),
        horizontalArrangement = Arrangement.spacedBy(spacing.m),
    ) {
        Text(
            "${source.number}",
            color = colors.pine,
            style = HearthTheme.type.footnoteStrong,
            textAlign = TextAlign.End,
            modifier = Modifier.width(HearthTheme.sizes.sourceNumberWidth).alignByBaseline(),
        )
        Column(Modifier.weight(1f).alignByBaseline(), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
            Text(source.title, color = colors.ink, style = HearthTheme.type.footnoteSnug, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(source.host, color = colors.mute, style = HearthTheme.type.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
