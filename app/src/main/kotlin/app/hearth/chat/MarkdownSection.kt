package app.hearth.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import app.hearth.R
import app.hearth.designsystem.HearthColors
import app.hearth.designsystem.HearthIconButton
import app.hearth.designsystem.HearthTheme
import app.hearth.domain.InlineMarkdownTransformer
import app.hearth.domain.MarkdownBlock
import app.hearth.domain.MarkdownTransformer
import app.hearth.domain.MathTransformer
import app.hearth.domain.SearchSource
import app.hearth.shared.rememberCopy

/** A reply laid out from its Markdown: headings, lists, code, tables and maths. */
@Composable
fun MarkdownSection(markdown: String, modifier: Modifier = Modifier) {
    val blocks = remember(markdown) { MarkdownTransformer().blocks(markdown) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.l)) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> InlineText(block.text, HearthTheme.type.heading(block.level), Modifier.semantics { heading() })
                is MarkdownBlock.Paragraph -> InlineText(block.text, HearthTheme.type.reply)
                is MarkdownBlock.Code -> CodeBlock(block)
                is MarkdownBlock.ItemList -> ItemList(block)
                is MarkdownBlock.Quote -> Quote(block.text)
                is MarkdownBlock.Table -> Table(block)
                is MarkdownBlock.Math -> Text(
                    remember(block.text) { MathTransformer().readable(block.text) },
                    style = HearthTheme.type.math,
                    color = HearthTheme.colors.ink,
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = HearthTheme.spacing.xs),
                )
                is MarkdownBlock.Rule -> Box(Modifier.fillMaxWidth().height(HearthTheme.strokes.hairline).background(HearthTheme.colors.line))
            }
        }
    }
}

/**
 * A line of text with its bold, italic, code and links. Each web link is a link annotation, so a screen reader
 * lists it among the text's links and can open it.
 */
@Composable
fun InlineText(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    val colors = HearthTheme.colors
    val uriHandler = LocalUriHandler.current
    val annotated = remember(text, colors, uriHandler) { annotated(text, colors, uriHandler) }
    Text(annotated, style = style.copy(color = colors.ink), modifier = modifier)
}

/** The text with its inline Markdown turned into styling, and each web link made something a tap can open. */
private fun annotated(text: String, colors: HearthColors, uriHandler: UriHandler): AnnotatedString = buildAnnotatedString {
    InlineMarkdownTransformer().spans(text).forEach { span ->
        val start = length
        append(span.text)
        addStyle(
            SpanStyle(
                fontWeight = if (span.isBold) FontWeight.SemiBold else null,
                fontStyle = if (span.isItalic) FontStyle.Italic else null,
                fontFamily = if (span.isCode) FontFamily.Monospace else null,
                background = if (span.isCode) colors.bubble else Color.Unspecified,
                color = if (span.link != null) colors.pine else Color.Unspecified,
                textDecoration = if (span.link != null) TextDecoration.Underline else null,
            ),
            start,
            length,
        )
        // Only web addresses are made links, and only those are opened.
        span.link?.takeIf { SearchSource.isWebLink(it) }?.let { address ->
            addLink(LinkAnnotation.Url(address) { runCatching { uriHandler.openUri(address) } }, start, length)
        }
    }
}

/** Code on a panel that scrolls sideways, under its language and a button to copy it. */
@Composable
private fun CodeBlock(block: MarkdownBlock.Code) {
    val colors = HearthTheme.colors
    val copy = rememberCopy()
    Column(Modifier.fillMaxWidth().clip(HearthTheme.shapes.tile).background(colors.bubble)) {
        Row(Modifier.fillMaxWidth().padding(start = HearthTheme.spacing.xxl), verticalAlignment = Alignment.CenterVertically) {
            Text(block.language ?: stringResource(R.string.message_code), color = colors.mute, style = HearthTheme.type.caption, modifier = Modifier.weight(1f))
            HearthIconButton(Icons.Outlined.ContentCopy, stringResource(R.string.message_copy_code), tint = colors.mute) { copy(block.text) }
        }
        Text(
            block.text,
            style = HearthTheme.type.code,
            color = colors.ink,
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(start = HearthTheme.spacing.xxl, end = HearthTheme.spacing.xxl, bottom = HearthTheme.spacing.xxl),
        )
    }
}

/** A list, numbered or bulleted. */
@Composable
private fun ItemList(block: MarkdownBlock.ItemList) {
    Column(verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.s)) {
        block.items.forEachIndexed { index, item ->
            Row(horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.m)) {
                Text(if (block.isOrdered) "${index + 1}." else "•", style = HearthTheme.type.reply, color = HearthTheme.colors.mute)
                InlineText(item, HearthTheme.type.reply, Modifier.weight(1f))
            }
        }
    }
}

/** A quotation, in italics beside a bar of the accent colour. */
@Composable
private fun Quote(text: String) {
    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.l)) {
        Box(Modifier.width(HearthTheme.strokes.accent).fillMaxHeight().background(HearthTheme.colors.pine, HearthTheme.shapes.bar))
        InlineText(text, HearthTheme.type.reply.copy(fontStyle = FontStyle.Italic), Modifier.weight(1f))
    }
}

/** A table of fixed-width columns that scrolls sideways, its header row shaded. */
@Composable
private fun Table(block: MarkdownBlock.Table) {
    val colors = HearthTheme.colors
    val columnWidth = HearthTheme.sizes.tableColumn
    val columnCount = maxOf(block.header.size, block.rows.maxOfOrNull { it.size } ?: 0)
    Column(
        Modifier
            .horizontalScroll(rememberScrollState())
            .clip(HearthTheme.shapes.inset)
            .border(HearthTheme.strokes.hairline, colors.line, HearthTheme.shapes.inset),
    ) {
        (listOf(block.header) + block.rows).forEachIndexed { rowIndex, row ->
            Row(Modifier.height(IntrinsicSize.Min).background(if (rowIndex == 0) colors.bubble else Color.Transparent)) {
                repeat(columnCount) { column ->
                    val style = HearthTheme.type.replyTable.copy(fontWeight = if (rowIndex == 0) FontWeight.SemiBold else null)
                    val cell = Modifier.width(columnWidth).padding(horizontal = HearthTheme.spacing.l, vertical = HearthTheme.spacing.m)
                    InlineText(row.getOrElse(column) { "" }, style, cell)
                }
            }
            if (rowIndex < block.rows.size) Box(Modifier.width(columnWidth * columnCount).height(HearthTheme.strokes.hairline).background(colors.line))
        }
    }
}
