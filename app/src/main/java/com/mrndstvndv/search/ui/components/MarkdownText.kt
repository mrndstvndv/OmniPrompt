package com.mrndstvndv.search.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.mrndstvndv.search.util.MarkdownBlock
import com.mrndstvndv.search.util.MarkdownInline
import com.mrndstvndv.search.util.MarkdownParser

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    linkColor: Color = MaterialTheme.colorScheme.primary,
) {
    val blocks = MarkdownParser.parse(markdown)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> {
                    val headingStyle = when (block.level) {
                        1 -> MaterialTheme.typography.titleLarge
                        2 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    }
                    val annotatedString = block.content.toAnnotatedString(linkColor)
                    Text(
                        text = annotatedString,
                        style = headingStyle.copy(
                            color = if (color != Color.Unspecified) color else MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
                is MarkdownBlock.ListItem -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "•",
                            style = style.copy(
                                color = if (color != Color.Unspecified) color else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        val annotatedString = block.content.toAnnotatedString(linkColor)
                        Text(
                            text = annotatedString,
                            style = style.copy(color = color)
                        )
                    }
                }
                is MarkdownBlock.Paragraph -> {
                    val annotatedString = block.content.toAnnotatedString(linkColor)
                    Text(
                        text = annotatedString,
                        style = style.copy(color = color)
                    )
                }
            }
        }
    }
}

private fun List<MarkdownInline>.toAnnotatedString(linkColor: Color): AnnotatedString {
    return buildAnnotatedString {
        for (inline in this@toAnnotatedString) {
            when (inline) {
                is MarkdownInline.Text -> {
                    append(inline.text)
                }
                is MarkdownInline.Bold -> {
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(inline.text)
                    }
                }
                is MarkdownInline.Link -> {
                    val linkAnnotation = LinkAnnotation.Url(
                        url = inline.url,
                        styles = TextLinkStyles(
                            style = SpanStyle(
                                color = linkColor,
                                textDecoration = TextDecoration.Underline,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    )
                    pushLink(linkAnnotation)
                    append(inline.text)
                    pop()
                }
            }
        }
    }
}
