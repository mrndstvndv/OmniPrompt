package com.mrndstvndv.search.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {
    @Test
    fun testSampleDataParsing() {
        val sampleMarkdown =
            """
            ## Features
            
            - incremental search flow
            
            ([535c8bd](https://github.com/mrndstvndv/OmniPrompt /commit/535c8bd47fe3e37045e9619ad17c276557914173))
            
            ## Fixes
            
            - **perf**: defer spacer animation to layout phase, add splash AVD workaround, add rememberSaveable and @Preview
            
            ([1f405e7](https://github.com/mrndstvndv/OmniPrompt /commit/1f405e7ba624adc901d5db16a94b53c364e7ed45))
            
            ## Performance
            
            - improve compose layout and draw performance
            
            ([d99d60e](https://github.com/mrndstvndv/OmniPrompt
            
            /commit/d99d60eea530462a1dfa06e2991ec276eb6aec7e))
            """.trimIndent()

        val blocks = MarkdownParser.parse(sampleMarkdown)

        // Verify we got the correct number and types of blocks
        assertEquals(9, blocks.size)

        // Block 1: ## Features
        val heading1 = blocks[0] as MarkdownBlock.Heading
        assertEquals(2, heading1.level)
        assertEquals(1, heading1.content.size)
        assertEquals(MarkdownInline.Text("Features"), heading1.content[0])

        // Block 2: ListItem
        val item1 = blocks[1] as MarkdownBlock.ListItem
        assertEquals(1, item1.content.size)
        assertEquals(MarkdownInline.Text("incremental search flow"), item1.content[0])

        // Block 3: Link paragraph (with space in URL)
        val para1 = blocks[2] as MarkdownBlock.Paragraph
        assertEquals(3, para1.content.size)
        assertEquals(MarkdownInline.Text("("), para1.content[0])
        val link1 = para1.content[1] as MarkdownInline.Link
        assertEquals("535c8bd", link1.text)
        assertEquals(
            "https://github.com/mrndstvndv/OmniPrompt/commit/535c8bd47fe3e37045e9619ad17c276557914173",
            link1.url,
        )
        assertEquals(MarkdownInline.Text(")"), para1.content[2])

        // Block 4: ## Fixes
        val heading2 = blocks[3] as MarkdownBlock.Heading
        assertEquals(2, heading2.level)
        assertEquals(1, heading2.content.size)
        assertEquals(MarkdownInline.Text("Fixes"), heading2.content[0])

        // Block 5: ListItem with bold
        val item2 = blocks[4] as MarkdownBlock.ListItem
        assertEquals(2, item2.content.size)
        assertEquals(MarkdownInline.Bold("perf"), item2.content[0])
        assertTrue(item2.content[1] is MarkdownInline.Text)
        assertEquals(
            ": defer spacer animation to layout phase, add splash AVD workaround, add rememberSaveable and @Preview",
            (item2.content[1] as MarkdownInline.Text).text,
        )

        // Block 6: Link paragraph
        val para2 = blocks[5] as MarkdownBlock.Paragraph
        val link2 = para2.content[1] as MarkdownInline.Link
        assertEquals("1f405e7", link2.text)
        assertEquals(
            "https://github.com/mrndstvndv/OmniPrompt/commit/1f405e7ba624adc901d5db16a94b53c364e7ed45",
            link2.url,
        )

        // Block 7: ## Performance
        val heading3 = blocks[6] as MarkdownBlock.Heading
        assertEquals(2, heading3.level)

        // Block 8: ListItem
        val item3 = blocks[7] as MarkdownBlock.ListItem
        assertEquals(
            MarkdownInline.Text("improve compose layout and draw performance"),
            item3.content[0],
        )

        // Block 9: Split link paragraph
        val para3 = blocks[8] as MarkdownBlock.Paragraph
        assertEquals(3, para3.content.size)
        assertEquals(MarkdownInline.Text("("), para3.content[0])
        val link3 = para3.content[1] as MarkdownInline.Link
        assertEquals("d99d60e", link3.text)
        assertEquals(
            "https://github.com/mrndstvndv/OmniPrompt/commit/d99d60eea530462a1dfa06e2991ec276eb6aec7e",
            link3.url,
        )
        assertEquals(MarkdownInline.Text(")"), para3.content[2])
    }

    @Test
    fun testMultipleBoldsAndLinks() {
        val markdown = "This is **bold1** and **bold2** with a [link](https://google.com)."
        val blocks = MarkdownParser.parse(markdown)
        assertEquals(1, blocks.size)

        val content = (blocks[0] as MarkdownBlock.Paragraph).content
        assertEquals(7, content.size)
        assertEquals(MarkdownInline.Text("This is "), content[0])
        assertEquals(MarkdownInline.Bold("bold1"), content[1])
        assertEquals(MarkdownInline.Text(" and "), content[2])
        assertEquals(MarkdownInline.Bold("bold2"), content[3])
        assertEquals(MarkdownInline.Text(" with a "), content[4])
        assertEquals(MarkdownInline.Link("link", "https://google.com"), content[5])
        assertEquals(MarkdownInline.Text("."), content[6])
    }
}
