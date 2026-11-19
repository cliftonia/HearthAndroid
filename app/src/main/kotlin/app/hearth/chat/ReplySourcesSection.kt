package app.hearth.chat

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import app.hearth.R
import app.hearth.designsystem.HearthSheetHeader
import app.hearth.designsystem.HearthTheme
import app.hearth.domain.SearchSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A reply lists only its first few pages; the button beside its actions opens them all. */
private const val LISTED_SOURCE_COUNT = 8

/** How many sources' marks stand beside the count on the button. */
private const val MARK_COUNT = 3

/** The web pages a reply was written from, under its words: a heading and a list of their titles, each a link. */
@Composable
internal fun ReplySourcesSection(sources: List<SourceViewConfiguration>, modifier: Modifier = Modifier) {
    val colors = HearthTheme.colors
    val spacing = HearthTheme.spacing
    val uriHandler = LocalUriHandler.current
    val open = stringResource(R.string.research_open_source)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.s)) {
        Text(stringResource(R.string.sources_heading), color = colors.ink, style = HearthTheme.type.reply, modifier = Modifier.semantics { heading() })
        sources.take(LISTED_SOURCE_COUNT).forEach { source ->
            val label = stringResource(R.string.research_source, source.number, source.title, source.host)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(HearthTheme.shapes.tile)
                    .clickable(onClickLabel = open, role = Role.Button) { openSource(source.url, uriHandler::openUri) }
                    .clearAndSetSemantics { contentDescription = label }
                    .padding(start = spacing.m, top = spacing.xs, bottom = spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(spacing.m),
            ) {
                Text("•", color = colors.ink, style = HearthTheme.type.reply)
                Text(source.title, color = colors.ink, style = HearthTheme.type.reply.copy(textDecoration = TextDecoration.Underline), modifier = Modifier.weight(1f))
            }
        }
    }
}

/** Above a reply that searched the web: a quiet line that says so, and lists the pages it found when tapped. */
@Composable
internal fun ReplySearchedLine(sources: List<SourceViewConfiguration>, modifier: Modifier = Modifier) {
    val colors = HearthTheme.colors
    var isOpen by remember { mutableStateOf(false) }
    val title = stringResource(R.string.sources_searched)
    // Heard with how many pages there are to see, which is what a tap leads to.
    val description = "$title, ${pluralStringResource(R.plurals.sources_count, sources.size, sources.size)}"
    Row(
        modifier
            .clip(HearthTheme.shapes.pill)
            .clickable(role = Role.Button) { isOpen = true }
            .heightIn(min = HearthTheme.sizes.touchTarget)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.s),
    ) {
        Icon(Icons.Outlined.Language, null, tint = colors.mute, modifier = Modifier.size(HearthTheme.sizes.iconXs))
        Text(title, color = colors.mute, style = HearthTheme.type.footnote, maxLines = 1)
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = colors.mute, modifier = Modifier.size(HearthTheme.sizes.iconXs))
    }
    if (isOpen) ReplySourcesSheet(sources) { isOpen = false }
}

/** Beside a reply's actions: the marks of its first few sources and how many there are. Tapping it lists them all. */
@Composable
internal fun ReplySourcesButton(sources: List<SourceViewConfiguration>, modifier: Modifier = Modifier) {
    val colors = HearthTheme.colors
    val spacing = HearthTheme.spacing
    var isOpen by remember { mutableStateOf(false) }
    val title = pluralStringResource(R.plurals.sources_count, sources.size, sources.size)
    Row(
        modifier
            .clip(HearthTheme.shapes.pill)
            .clickable(role = Role.Button) { isOpen = true }
            .heightIn(min = HearthTheme.sizes.touchTarget)
            .clearAndSetSemantics {
                contentDescription = title
                role = Role.Button
            }
            .padding(horizontal = spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.s),
    ) {
        // Each mark lies a little over the one before it, ringed in the paper's colour to keep them apart.
        Row(horizontalArrangement = Arrangement.spacedBy(-HearthTheme.sizes.sourceMarkOverlap)) {
            sources.take(MARK_COUNT).forEach { SourceMark(it.host, it.url, Modifier.border(HearthTheme.strokes.hairline, colors.paper, CircleShape)) }
        }
        Text(title, color = colors.mute, style = HearthTheme.type.footnote, maxLines = 1)
    }
    if (isOpen) ReplySourcesSheet(sources) { isOpen = false }
}

/** Every page a reply was written from: its mark, its title and its site, each a link. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReplySourcesSheet(sources: List<SourceViewConfiguration>, onDismiss: () -> Unit) {
    val colors = HearthTheme.colors
    val spacing = HearthTheme.spacing
    val uriHandler = LocalUriHandler.current
    val open = stringResource(R.string.research_open_source)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = colors.paper,
        // No tint from elevation: the sheet is the same paper as the screen it rises over.
        tonalElevation = 0.dp,
    ) {
        HearthSheetHeader(stringResource(R.string.research_sources))
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(start = spacing.screen, end = spacing.screen, bottom = spacing.screen)
                .clip(HearthTheme.shapes.card)
                .background(colors.card),
        ) {
            sources.forEach { source ->
                val label = stringResource(R.string.research_source, source.number, source.title, source.host)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClickLabel = open, role = Role.Button) { openSource(source.url, uriHandler::openUri) }
                        // Read out as one thing: its number, its title and its site.
                        .clearAndSetSemantics { contentDescription = label }
                        .padding(horizontal = spacing.xl, vertical = spacing.xl),
                    horizontalArrangement = Arrangement.spacedBy(spacing.l),
                ) {
                    SourceMark(source.host, source.url)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                        Text(source.title, color = colors.ink, style = HearthTheme.type.listRow, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(source.host, color = colors.mute, style = HearthTheme.type.footnote, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (!source.isLast) HorizontalDivider(Modifier.padding(start = spacing.xl), thickness = HearthTheme.strokes.hairline, color = colors.line)
            }
        }
    }
}

/** Fetches the icon of the site with the given host name; `null` when it has none. Asks for nothing by default. */
val LocalSiteIcons = staticCompositionLocalOf<suspend (String) -> ByteArray?> { { null } }

/**
 * A site's mark in a small circle: the icon the site shows for itself, fetched from the site once and then kept, or the
 * first letter of its name until that comes, and for a site that has none. [url] is the page's address, whose host is
 * asked for the icon.
 */
@Composable
private fun SourceMark(host: String, url: String, modifier: Modifier = Modifier) {
    val loadIcon = LocalSiteIcons.current
    val siteName = remember(url) { url.toUri().host }
    val icon by produceState<ImageBitmap?>(null, siteName) {
        val bytes = siteName?.let { loadIcon(it) } ?: return@produceState
        value = withContext(Dispatchers.Default) { decodedIcon(bytes) }
    }
    val shown = icon
    val fill = if (shown == null) HearthTheme.colors.pineSoft else HearthTheme.colors.card
    Box(modifier.size(HearthTheme.sizes.sourceMark).clip(CircleShape).background(fill), contentAlignment = Alignment.Center) {
        if (shown == null) {
            Text(host.firstOrNull()?.uppercase() ?: "•", color = HearthTheme.colors.mute, style = HearthTheme.type.caption)
        } else {
            Image(shown, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}

/** How large an icon is decoded, at most: enough for the mark on the sharpest screens, however large the site's file. */
private const val ICON_DECODE_SIDE = 96

/** The icon as a picture, halved in size as it is decoded until it is no larger than the mark needs; `null` if it is no picture. */
private fun decodedIcon(bytes: ByteArray): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= ICON_DECODE_SIDE) sample *= 2
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}

/** Opens a source's page, if its address is a web page's. Links from a search are untrusted. */
private fun openSource(url: String, open: (String) -> Unit) {
    if (SearchSource.isWebLink(url)) runCatching { open(url) }
}
