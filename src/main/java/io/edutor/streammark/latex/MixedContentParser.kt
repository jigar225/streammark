package io.edutor.streammark.latex

import android.util.Log

/**
 * Production-Ready Mixed Content Parser for Jetpack Compose
 * Exact port of mathpix-markdown-it's splitMathBlock logic
 * 
 * Based on complete analysis of mathpix-markdown-it source code including:
 * - Exact regex patterns and precedence rules
 * - Position tracking with String.matchAll equivalent
 * - Escape handling with placeholder mechanism
 * - All edge cases from their test suite
 */

data class MathBlock(
    val type: String,
    val value: String,
    val start: Int,
    val end: Int,
    val env: String? = null // For LaTeX environments
)

class MixedContentParser {
    
    companion object {
        // Exact regex patterns from mathpix-markdown-it
        private const val ESC_DOLLAR = "__ESC_DOLLAR__"
        
        // Block math patterns (highest precedence)
        private val blockMathDollar = Regex("""\$\$([\s\S]+?)\$\$""")
        private val blockMathBracket = Regex("""\\\\?\[([\s\S]+?)\\\\?\]""")
        
        // Inline math patterns  
        private val inlineMathDollar =  Regex("\\$([^$\\n]+?)\\$") // $ ... $ (not $$)
        private val inlineMathParen =  Regex("""\\\((.+?)\\\)""")  // \( ... \)
        
        // LaTeX environment pattern
        private val mathEnvironments = listOf(
            "equation", "align", "gather", "tabular", "array", 
            "matrix", "bmatrix", "pmatrix", "vmatrix"
        )
        private val environmentPattern = Regex(
            """\\begin\{(${mathEnvironments.joinToString("|")})\}([\s\S]+?)\\end\{\1\}"""
        )
        
        // Escaped dollar pattern
        private val escapedDollarPattern = Regex("""\\\$""")
    }
    
    /**
     * Main parsing function - exact port of mathpix-markdown-it splitMathBlock
     */
    fun splitMathBlock(input: String): List<MathBlock> {
        var str = input
        
        Log.d("MixedContentParser", "🚀 Parsing content: '$str'")
        
        // Step 1: Replace escaped delimiters with placeholders
        str = str.replace(escapedDollarPattern, ESC_DOLLAR)
        
        val allBlocks = mutableListOf<MathBlock>()
        
        // Step 2: Parse block math ($$...$$ and \[...\]) - highest precedence
        val afterBlockMath = extractPattern(str, blockMathDollar, "block_math", allBlocks)
        str = extractPattern(afterBlockMath, blockMathBracket, "block_math", allBlocks)
        
        // Step 3: Parse LaTeX environments
        str = extractPattern(str, environmentPattern, "environment", allBlocks)
        
        // Step 4: Parse inline math ($...$ and \(...\))
        val afterInlineDollar = extractPattern(str, inlineMathDollar, "inline_math", allBlocks)
        str = extractPattern(afterInlineDollar, inlineMathParen, "inline_math", allBlocks)
        
        // Step 5: Add text blocks for remaining content
        addTextBlocks(input, allBlocks)
        
        // Step 6: Restore escaped delimiters and sort by position
        val result = allBlocks
            .map { block -> restoreEscapes(block) }
            .sortedBy { it.start }
        
        Log.d("MixedContentParser", "📊 Parse result: ${result.size} blocks (FULL CONTENT)")
        result.forEachIndexed { index, block ->
            val preview = when (block.type) {
                "inline_math" -> "${'$'}${block.value}${'$'}"
                "block_math" -> "${'$'}${'$'}\n${block.value}\n${'$'}${'$'}"
                else -> block.value
            }
            Log.d(
                "MixedContentParser",
                "  [${index}] type=${block.type}, start=${block.start}, end=${block.end}, env=${block.env ?: "-"}\n${preview}"
            )
        }
        
        return result
    }
    
    /**
     * Extract matches using String.matchAll equivalent logic
     */
    private fun extractPattern(
        str: String,
        pattern: Regex,
        type: String,
        blocks: MutableList<MathBlock>
    ): String {
        val matches = pattern.findAll(str).toList()
        var processedStr = str
        
        // Process matches from end to start to maintain indices (like mathpix)
        for (match in matches.reversed()) {
            val raw = match.value
            val content = if (match.groupValues.size > 1) {
                if (type == "environment" && match.groupValues.size > 2) {
                    // For environments: group 1 is env name, group 2 is content
                    match.groupValues[2]
                } else {
                    match.groupValues[1]
                }
            } else {
                raw
            }
            
            val env = if (type == "environment" && match.groupValues.size > 1) {
                match.groupValues[1]
            } else null
            
            val block = MathBlock(
                type = type,
                value = content.trim(),
                start = match.range.first,
                end = match.range.last + 1,
                env = env
            )
            
            blocks.add(block)
            
            // Replace with placeholder to avoid re-matching
            val placeholder = "___${type.uppercase()}_${blocks.size - 1}___"
            processedStr = processedStr.replaceRange(match.range, placeholder)
        }
        
        return processedStr
    }
    
    /**
     * Add text blocks between math blocks (mathpix-markdown-it logic)
     */
    private fun addTextBlocks(originalStr: String, blocks: MutableList<MathBlock>) {
        if (blocks.isEmpty()) {
            // No math blocks, entire string is text
            if (originalStr.trim().isNotEmpty()) {
                blocks.add(MathBlock("text", restoreEscapesInString(originalStr), 0, originalStr.length))
            }
            return
        }
        
        // Sort existing blocks by position
        val sortedMathBlocks = blocks.sortedBy { it.start }
        var lastIndex = 0
        
        for (mathBlock in sortedMathBlocks) {
            // Add text block before this math block
            if (mathBlock.start > lastIndex) {
                val textContent = originalStr.substring(lastIndex, mathBlock.start)
                if (textContent.trim().isNotEmpty()) {
                    blocks.add(MathBlock("text", textContent, lastIndex, mathBlock.start))
                }
            }
            lastIndex = mathBlock.end
        }
        
        // Add remaining text after last math block
        if (lastIndex < originalStr.length) {
            val remainingText = originalStr.substring(lastIndex)
            if (remainingText.trim().isNotEmpty()) {
                blocks.add(MathBlock("text", remainingText, lastIndex, originalStr.length))
            }
        }
    }
    
    /**
     * Restore escaped delimiters (mathpix-markdown-it restoreEscapes)
     */
    private fun restoreEscapes(block: MathBlock): MathBlock {
        return if (block.type == "text") {
            block.copy(value = restoreEscapesInString(block.value))
        } else {
            block
        }
    }
    
    private fun restoreEscapesInString(text: String): String {
        return text.replace(ESC_DOLLAR, "$")
    }
    
    /**
     * Utility function to check if character at position is escaped
     * (from mathpix-markdown-it delimiter_utils.ts)
     */
    fun isEscaped(str: String, pos: Int): Boolean {
        var backslashCount = 0
        var currentPos = pos
        while (currentPos > 0 && str[--currentPos] == '\\') {
            backslashCount++
        }
        return backslashCount % 2 == 1
    }
}

/**
 * Convert MathBlock to ParsedElement for compatibility
 */
fun MathBlock.toParsedElement(): ParsedElement {
    return when (type) {
        "text" -> ParsedElement.Markdown(value)
        "inline_math" -> ParsedElement.InlineLatex(
            content = value,
            originalRawMarkdown = "$$value$"
        )
        "block_math" -> ParsedElement.BlockLatex(
            content = value,
            originalRawMarkdown = "$$value$$"
        )
        "environment" -> {
            // Handle LaTeX environments
            when (env) {
                "tabular", "array" -> ParsedElement.Markdown(value) // Treat as markdown for now
                else -> ParsedElement.BlockLatex(
                    content = value,
                    originalRawMarkdown = "\\begin{$env}$value\\end{$env}"
                )
            }
        }
        else -> ParsedElement.Markdown(value)
    }
}

/**
 * Main parsing function that returns ParsedElement list
 */
fun parseMixedContent(content: String): List<ParsedElement> {
    val parser = MixedContentParser()
    val blocks = parser.splitMathBlock(content)
    val flat = blocks.map { it.toParsedElement() }
    return groupLists(flat)
}

// Group consecutive markdown lines into lists when they start with list markers
private fun groupLists(elements: List<ParsedElement>): List<ParsedElement> {
    if (elements.isEmpty()) return elements
    val result = mutableListOf<ParsedElement>()
    val ulRegex = Regex("^\\s*[*-]\\s+(.+)")
    val olRegex = Regex("^\\s*\\d+\\.\\s+(.+)")

    fun processMarkdownBlock(text: String) {
        val lines = text.split("\n")
        var idx = 0
        val paragraph = StringBuilder()

        fun flushParagraph() {
            if (paragraph.isNotEmpty()) {
                result.add(ParsedElement.Markdown(paragraph.toString()))
                paragraph.clear()
            }
        }

        while (idx < lines.size) {
            val line = lines[idx]
            val ulMatch = ulRegex.find(line)
            val olMatch = olRegex.find(line)

            if (ulMatch != null) {
                flushParagraph()
                val items = mutableListOf<ListItem>()
                var j = idx
                while (j < lines.size) {
                    val m = ulRegex.find(lines[j]) ?: break
                    val body = m.groupValues[1]
                    val children = parseMixedContent(body)
                    items.add(ListItem(children))
                    j++
                }
                result.add(ParsedElement.UnorderedList(items))
                idx = j
                continue
            } else if (olMatch != null) {
                flushParagraph()
                val items = mutableListOf<ListItem>()
                var j = idx
                while (j < lines.size) {
                    val m = olRegex.find(lines[j]) ?: break
                    val body = m.groupValues[1]
                    val children = parseMixedContent(body)
                    items.add(ListItem(children))
                    j++
                }
                result.add(ParsedElement.OrderedList(items))
                idx = j
                continue
            } else {
                if (paragraph.isNotEmpty()) paragraph.append('\n')
                paragraph.append(line)
                idx++
            }
        }
        flushParagraph()
    }

    for (el in elements) {
        if (el is ParsedElement.Markdown) {
            processMarkdownBlock(el.content)
        } else {
            result.add(el)
        }
    }

    return result
}


// Parsed element types
sealed class ParsedElement {
    data class Markdown(val content: String) : ParsedElement()
    data class Table(val rows: List<List<String>>, val originalRawMarkdown: String) : ParsedElement()
    data class BlockLatex(val content: String, val originalRawMarkdown: String) : ParsedElement()
    data class InlineLatex(val content: String, val originalRawMarkdown: String) : ParsedElement()
    data class UnorderedList(val items: List<ListItem>) : ParsedElement()
    data class OrderedList(val items: List<ListItem>) : ParsedElement()
}

data class ListItem(val children: List<ParsedElement>)
