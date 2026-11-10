package app.hearth.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hearth.R
import app.hearth.domain.InlineMarkdownTransformer
import app.hearth.domain.MarkdownBlock
import app.hearth.domain.MarkdownTransformer
import app.hearth.domain.MathTransformer
import app.hearth.domain.SearchSource

private const val LINK_TAG = "link"
private val BodyStyle = TextStyle(fontSize = 16.sp, lineHeight = 24.sp)

/** A reply laid out from its Markdown: headings, lists, code, tables and maths. */
@Composable
fun MarkdownSection(markdown: String, modifier: Modifier = Modifier) {
    val blocks = remember(markdown) { MarkdownTransformer().blocks(markdown) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> InlineText(block.text, HearthTheme.heading(block.level))
                is MarkdownBlock.Paragraph -> InlineText(block.text, BodyStyle)
                is MarkdownBlock.Code -> CodeBlock(block)
                is MarkdownBlock.ItemList -> ItemList(block)
                is MarkdownBlock.Quote -> Quote(block.text)
                is MarkdownBlock.Table -> Table(block)
                is MarkdownBlock.Math -> Text(
                    remember(block.text) { MathTransformer().readable(block.text) },
                    style = HearthTheme.math,
                    color = HearthTheme.colors.ink,
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
                )
                is MarkdownBlock.Rule -> Box(Modifier.fillMaxWidth().height(1.dp).background(HearthTheme.colors.line))
            }
        }
    }
}

/** A line of text with its bold, italic, code and links. */
@Composable
fun InlineText(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    val colors = HearthTheme.colors
    val uriHandler = LocalUriHandler.current
    val annotated = remember(text, colors) { annotated(text, colors) }
    val coloured = style.copy(color = colors.ink)
    if (annotated.getStringAnnotations(LINK_TAG, 0, annotated.length).isEmpty()) {
        Text(annotated, style = coloured, modifier = modifier)
    } else {
        ClickableText(annotated, style = coloured, modifier = modifier) { offset ->
            annotated.getStringAnnotations(LINK_TAG, offset, offset).firstOrNull()?.let { link ->
                // Only web addresses were annotated, and only those are opened.
                if (SearchSource.isWebLink(link.item)) runCatching { uriHandler.openUri(link.item) }
            }
        }
    }
}

private fun annotated(text: String, colors: HearthColors): AnnotatedString = buildAnnotatedString {
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
        span.link?.let { addStringAnnotation(LINK_TAG, it, start, length) }
    }
}

@Composable
private fun CodeBlock(block: MarkdownBlock.Code) {
    val colors = HearthTheme.colors
    val clipboard = LocalClipboardManager.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.bubble)) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(block.language ?: stringResource(R.string.message_code), color = colors.mute, fontSize = 12.sp, modifier = Modifier.weight(1f))
            HearthIconButton(Icons.Outlined.ContentCopy, stringResource(R.string.message_copy_code), tint = colors.mute) {
                clipboard.setText(AnnotatedString(block.text))
            }
        }
        Text(
            block.text,
            style = HearthTheme.code,
            color = colors.ink,
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
        )
    }
}

@Composable
private fun ItemList(block: MarkdownBlock.ItemList) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        block.items.forEachIndexed { index, item ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (block.isOrdered) "${index + 1}." else "•", style = BodyStyle, color = HearthTheme.colors.mute)
                InlineText(item, BodyStyle, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Quote(text: String) {
    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(HearthTheme.colors.pine, RoundedCornerShape(2.dp)))
        InlineText(text, BodyStyle.copy(fontStyle = FontStyle.Italic), Modifier.weight(1f))
    }
}

@Composable
private fun Table(block: MarkdownBlock.Table) {
    val colors = HearthTheme.colors
    val columnCount = maxOf(block.header.size, block.rows.maxOfOrNull { it.size } ?: 0)
    Column(
        Modifier.horizontalScroll(rememberScrollState()).clip(RoundedCornerShape(10.dp)).border(1.dp, colors.line, RoundedCornerShape(10.dp)),
    ) {
        (listOf(block.header) + block.rows).forEachIndexed { rowIndex, row ->
            Row(Modifier.height(IntrinsicSize.Min).background(if (rowIndex == 0) colors.bubble else Color.Transparent)) {
                repeat(columnCount) { column ->
                    val style = BodyStyle.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = if (rowIndex == 0) FontWeight.SemiBold else null)
                    InlineText(row.getOrElse(column) { "" }, style, Modifier.width(150.dp).padding(horizontal = 10.dp, vertical = 8.dp))
                }
            }
            if (rowIndex < block.rows.size) Box(Modifier.width(150.dp * columnCount).height(1.dp).background(colors.line))
        }
    }
}
