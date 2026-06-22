package com.mrndstvndv.search.util

sealed class MarkdownBlock {
    data class Heading(val level: Int, val content: List<MarkdownInline>) : MarkdownBlock()

    data class ListItem(val content: List<MarkdownInline>) : MarkdownBlock()

    data class Paragraph(val content: List<MarkdownInline>) : MarkdownBlock()
}

sealed class MarkdownInline {
    data class Text(val text: String) : MarkdownInline()

    data class Bold(val text: String) : MarkdownInline()

    data class Link(val text: String, val url: String) : MarkdownInline()
}

object MarkdownParser {
    fun parse(input: String): List<MarkdownBlock> {
        val sanitized = sanitizeMarkdownLinks(input)
        val blocks = mutableListOf<MarkdownBlock>()

        for (line in sanitized.lines()) {
            val trimmedLine = line.trim()
            if (trimmedLine.isEmpty()) continue

            // Check for Heading
            if (trimmedLine.startsWith("#")) {
                var level = 0
                while (level < trimmedLine.length && trimmedLine[level] == '#') {
                    level++
                }
                if (level < trimmedLine.length && trimmedLine[level] == ' ') {
                    val contentText = trimmedLine.substring(level + 1).trim()
                    blocks.add(MarkdownBlock.Heading(level, parseInline(contentText)))
                    continue
                }
            }

            // Check for List Item
            if (trimmedLine.startsWith("- ") || trimmedLine.startsWith("* ")) {
                val contentText = trimmedLine.substring(2).trim()
                blocks.add(MarkdownBlock.ListItem(parseInline(contentText)))
                continue
            }

            // Default to Paragraph
            blocks.add(MarkdownBlock.Paragraph(parseInline(trimmedLine)))
        }
        return blocks
    }

    private fun sanitizeMarkdownLinks(input: String): String {
        val result = StringBuilder()
        var i = 0
        while (i < input.length) {
            if (input.startsWith("](", i)) {
                var j = i + 2
                var openCount = 1
                var foundClose = false
                while (j < input.length) {
                    if (input[j] == '(') {
                        openCount++
                    } else if (input[j] == ')') {
                        openCount--
                        if (openCount == 0) {
                            foundClose = true
                            break
                        }
                    }
                    j++
                }
                if (foundClose) {
                    val urlContent = input.substring(i + 2, j)
                    val cleanedUrl = urlContent.removeWhitespace()
                    result.append("](")
                    result.append(cleanedUrl)
                    result.append(")")
                    i = j + 1
                } else {
                    result.append(input[i])
                    i++
                }
            } else {
                result.append(input[i])
                i++
            }
        }
        return result.toString()
    }

    private fun String.removeWhitespace(): String {
        val sb = StringBuilder()
        for (char in this) {
            if (!char.isWhitespace()) {
                sb.append(char)
            }
        }
        return sb.toString()
    }

    fun parseInline(text: String): List<MarkdownInline> {
        val inlines = mutableListOf<MarkdownInline>()
        var i = 0
        while (i < text.length) {
            if (text.startsWith("**", i)) {
                val nextIdx = text.indexOf("**", i + 2)
                if (nextIdx != -1) {
                    val boldText = text.substring(i + 2, nextIdx)
                    inlines.add(MarkdownInline.Bold(boldText))
                    i = nextIdx + 2
                } else {
                    inlines.add(MarkdownInline.Text("**"))
                    i += 2
                }
            } else if (text[i] == '[') {
                val closeBracketIdx = text.indexOf(']', i + 1)
                if (closeBracketIdx != -1 && text.getOrNull(closeBracketIdx + 1) == '(') {
                    val closeParenIdx = text.indexOf(')', closeBracketIdx + 2)
                    if (closeParenIdx != -1) {
                        val linkText = text.substring(i + 1, closeBracketIdx)
                        val url = text.substring(closeBracketIdx + 2, closeParenIdx)
                        inlines.add(MarkdownInline.Link(linkText.trim(), url.trim()))
                        i = closeParenIdx + 1
                    } else {
                        inlines.add(MarkdownInline.Text("["))
                        i++
                    }
                } else {
                    inlines.add(MarkdownInline.Text("["))
                    i++
                }
            } else {
                val normalText = StringBuilder()
                while (i < text.length) {
                    if (text.startsWith("**", i) || text[i] == '[') {
                        break
                    }
                    normalText.append(text[i])
                    i++
                }
                if (normalText.isNotEmpty()) {
                    inlines.add(MarkdownInline.Text(normalText.toString()))
                }
            }
        }
        return inlines
    }
}
