package io.edutor.streammark.parser

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.*
import androidx.core.graphics.withSave
import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableBody
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableHead
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.*
import org.commonmark.parser.Parser
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt
import io.edutor.streammark.api.MarkdownRenderConfig
import io.edutor.streammark.api.MessageContentSegment
import io.edutor.streammark.api.TableCellSpannable
import io.edutor.streammark.api.TableData
import io.edutor.streammark.api.VisualContentType
import io.edutor.streammark.extraction.*
import io.edutor.streammark.extraction.VisualContentType as ExtractionVisualContentType
import io.edutor.streammark.latex.LatexView
import io.edutor.streammark.spans.CustomQuoteSpan
import io.edutor.streammark.spans.HorizontalRuleSpan
import io.edutor.streammark.spans.InlineHighlightSpan

/**
 * Hybrid Streaming Parser - Extract LaTeX First, Then Parse Markdown with Table Support
 */
class HybridStreamingParser(
    private val context: Context,
    private val suppressParagraphTrailingNewline: Boolean = false,
    private val suppressListTrailingNewline: Boolean = false,
    private val config: MarkdownRenderConfig = MarkdownRenderConfig.Default
) {

    private val commonMarkParser = Parser.builder()
        .extensions(
            listOf(
                StrikethroughExtension.create(),
                TablesExtension.create()
            )
        )
        .build()

    private val fullTextBuffer = StringBuilder()
    private var cachedRendered: SpannableStringBuilder? = null
    private var lastParsedText = ""

    private var extractedBlockLatexBlocks = mutableListOf<String>()
    private var extractedInlineLatexBlocks = mutableListOf<String>()
    private var extractedHtmlBlocks = mutableListOf<HtmlBlockData>()
    private var extractedFontTags = mutableListOf<FontTagData>()
    private var extractedSpanTags = mutableListOf<SpanTagData>()
    private var extractedUnderlineTags = mutableListOf<String>() // Store content of <u> tags
    private var extractedLinkTags = mutableListOf<LinkTagData>() // Store link tags
    private var extractedImageTags = mutableListOf<ImageTagData>() // Store image tags
    private var extractedVisualContents = mutableListOf<VisualContentData>() // Store visual content (mindmap/chart/mermaid)
    private var incompleteVisualContents = mutableListOf<IncompleteVisualContentData>() // Store incomplete visual content during streaming
    private val segmentList = mutableListOf<MessageContentSegment>()

    fun processChunk(chunk: String): Spannable {

        fullTextBuffer.append(chunk)
        val currentText = fullTextBuffer.toString()


        if (shouldReparse(currentText)) {
            cachedRendered = parseAndRender(currentText)
            lastParsedText = currentText
        }

        return cachedRendered ?: SpannableStringBuilder()
    }

    private fun shouldReparse(currentText: String): Boolean {
        if (cachedRendered == null) return true
        if (currentText == lastParsedText) return false

        val newContent = currentText.substring(lastParsedText.length)

        return when {
            newContent.contains("\n") -> true
            newContent.contains("**") && newContent.count { it == '*' } % 2 == 0 -> true
            newContent.contains("$") -> true
            newContent.contains("$$") -> true
            newContent.contains("|") -> true
            newContent.contains("<br", ignoreCase = true) -> true
            newContent.contains("<div", ignoreCase = true) -> true
            newContent.contains("</div>", ignoreCase = true) -> true
            newContent.contains("<p", ignoreCase = true) -> true
            newContent.contains("</p>", ignoreCase = true) -> true
            newContent.contains("<font", ignoreCase = true) -> true
            newContent.contains("</font>", ignoreCase = true) -> true
            newContent.contains("<span", ignoreCase = true) -> true
            newContent.contains("</span>", ignoreCase = true) -> true
            newContent.contains("<u>", ignoreCase = true) -> true
            newContent.contains("</u>", ignoreCase = true) -> true
            newContent.contains("<a", ignoreCase = true) -> true
            newContent.contains("</a>", ignoreCase = true) -> true
            newContent.contains("<img", ignoreCase = true) -> true
            newContent.length >= 100 -> true
            else -> false
        }
    }

    private fun parseAndRender(text: String): SpannableStringBuilder {
        // Step 0: Normalize styled inline tags (strong/b/em/i with style) to span format
        // This converts <strong style="color: red;"> to <span style="color: red;">**text**</span>
        val normalizedText = HtmlNormalizer.normalizeStyledInlineTags(text)

        // Step 1: Extract HTML blocks FIRST (before LaTeX)
        val (textAfterHtml, htmlBlocks) = HtmlTagExtractor.extractHtmlBlocks(normalizedText)
        extractedHtmlBlocks = htmlBlocks.toMutableList()

        // Step 1.5: Convert <br> tags to newlines (in regular text, not inside HTML blocks)
        val textWithBreaks = HtmlTagExtractor.convertBrTagsToNewlines(textAfterHtml)

        // Step 2: Extract font tags (before LaTeX)
        val (textAfterFont, fontTags) = HtmlTagExtractor.extractFontTags(textWithBreaks)
        extractedFontTags = fontTags.toMutableList()

        // Step 2.5: Extract span tags (before LaTeX)
        val (textAfterSpan, spanTags) = HtmlTagExtractor.extractSpanTags(textAfterFont)
        extractedSpanTags = spanTags.toMutableList()

        // Step 2.75: Extract underline tags (before LaTeX)
        val (textAfterUnderline, underlineTags) = HtmlTagExtractor.extractUnderlineTags(textAfterSpan)
        extractedUnderlineTags = underlineTags.toMutableList()

        // Step 2.9: Extract link tags (before LaTeX)
        val (textAfterLinks, linkTags) = HtmlTagExtractor.extractLinkTags(textAfterUnderline)
        extractedLinkTags = linkTags.toMutableList()

        // Step 2.95: Extract image tags (before LaTeX)
        val (textAfterImages, imageTags) = HtmlTagExtractor.extractImageTags(textAfterLinks)
        extractedImageTags = imageTags.toMutableList()

        // Step 2.98: Extract visual content code blocks (mindmap/chart/mermaid) BEFORE LaTeX
        // This handles both complete and incomplete blocks
        val (textAfterVisual, visualContents, incompleteVisual) = VisualContentExtractor.extractVisualContentWithIncomplete(textAfterImages)
        extractedVisualContents = visualContents.toMutableList()
        incompleteVisualContents = incompleteVisual.toMutableList()

        // Step 3: Extract LaTeX from remaining text
        val (textWithPlaceholders, blockLatex, inlineLatex) = extractLatexFromRawText(textAfterVisual)
        extractedBlockLatexBlocks = blockLatex.toMutableList()
        extractedInlineLatexBlocks = inlineLatex.toMutableList()

        // Step 4: Parse Markdown
        val markdownResult = commonMarkParser.parse(textWithPlaceholders)
        val spannable = convertNodeToSpannable(markdownResult)
        val rendered = replacePlaceholdersWithLatex(spannable)

        // Step 5: Apply font color spans
        val withFontColors = applyFontColorSpans(rendered, textWithPlaceholders)

        // Step 6: Apply span style spans (background color, padding)
        val withSpanStyles = applySpanStyleSpans(withFontColors, textWithPlaceholders)

        // Step 6.5: Apply underline spans
        val withUnderlines = applyUnderlineSpans(withSpanStyles, textWithPlaceholders)

        // Step 6.75: Apply link spans
        val withLinks = applyLinkSpans(withUnderlines, textWithPlaceholders)

        // Step 7: Create segments (includes HTML, LaTeX, Tables, Text, Incomplete Visual Content)
        segmentList.clear()
        segmentList.addAll(parseIntoSegments(withLinks, markdownResult, textWithPlaceholders, textAfterImages))

        // Log 1: CommonMark document structure (AST tree)
//        logCommonMarkDocumentStructure(markdownResult)

        // Log 2: Final raw markdown string with visible \n characters
//        logFinalMarkdownString(withLinks.toString())

        return withLinks
    }


    /**
     * Apply font color spans to the rendered spannable
     */
    private fun applyFontColorSpans(spannable: SpannableStringBuilder, textWithPlaceholders: String): SpannableStringBuilder {
        val result = SpannableStringBuilder(spannable)
        val text = result.toString()

        // Find font color markers in the rendered text (markers should appear as literal text)
        val fontMarkerRegex = Regex("""\[\[FC:(\d+)]]""")
        val fontMarkers = fontMarkerRegex.findAll(text)
            .map { match ->
                val index = match.groupValues[1].toIntOrNull() ?: -1
                Triple(match.range.first, match.range.last + 1, index)
            }
            .filter { it.third >= 0 && it.third < extractedFontTags.size }
            .sortedBy { it.first }
            .toList()

        // Apply color spans (process in reverse to maintain positions)
        fontMarkers.asReversed().forEach { (start, end, index) ->
            val fontTag = extractedFontTags[index]

            // Parse markdown in inner content (handles **bold**, *italic*, etc.)
            val parsedContent = parseMarkdownInHtmlContent(fontTag.content)

            // Replace marker with parsed content (Spannable)
            result.replace(start, end, parsedContent)

            // Apply color span to the entire parsed content
            val contentStart = start
            val contentEnd = start + parsedContent.length

            if (contentStart >= 0 && contentEnd > contentStart && contentEnd <= result.length) {
                result.setSpan(
                    ForegroundColorSpan(fontTag.color),
                    contentStart,
                    contentEnd,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        return result
    }

    /**
     * Apply span style spans (background color, padding) to the rendered spannable
     * using a custom LineBackgroundSpan that draws a tight highlight only around
     * the span text (not the full line box).
     */
    private fun applySpanStyleSpans(
        spannable: SpannableStringBuilder,
        textWithPlaceholders: String
    ): SpannableStringBuilder {
        val result = SpannableStringBuilder(spannable)
        val text = result.toString()

        // Find span markers in the rendered text
        val spanMarkerRegex = Regex("""\[\[SC:(\d+)]]""")
        val spanMarkers = spanMarkerRegex.findAll(text)
            .map { match ->
                val index = match.groupValues[1].toIntOrNull() ?: -1
                Triple(match.range.first, match.range.last + 1, index)
            }
            .filter { it.third >= 0 && it.third < extractedSpanTags.size }
            .sortedBy { it.first }
            .toList()

        // Apply spans (process in reverse to maintain positions)
        spanMarkers.asReversed().forEach { (start, end, index) ->
            val spanTag = extractedSpanTags[index]

            // Parse markdown in inner content (handles **bold**, *italic*, etc.)
            val parsedContent = parseMarkdownInHtmlContent(spanTag.content)

            // Replace marker with parsed content (Spannable)
            result.replace(start, end, parsedContent)

            val contentStart = start
            val contentEnd = start + parsedContent.length

            if (contentStart >= 0 && contentEnd > contentStart && contentEnd <= result.length) {
                // Apply background color and padding if present
                if (spanTag.backgroundColor != android.graphics.Color.TRANSPARENT) {
                    result.setSpan(
                        InlineHighlightSpan(
                            backgroundColor = spanTag.backgroundColor,
                            paddingPx = spanTag.padding
                        ),
                        contentStart,
                        contentEnd,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }

                // Apply text color if present (from normalized styled tags)
                spanTag.textColor?.let { color ->
                    result.setSpan(
                        ForegroundColorSpan(color),
                        contentStart,
                        contentEnd,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }
        }

        return result
    }

    /**
     * Apply underline spans to the rendered spannable
     */
    private fun applyUnderlineSpans(
        spannable: SpannableStringBuilder,
        textWithPlaceholders: String
    ): SpannableStringBuilder {
        val result = SpannableStringBuilder(spannable)
        val text = result.toString()

        // Find underline markers in the rendered text
        val underlineMarkerRegex = Regex("""\[\[UL:(\d+)]]""")
        val underlineMarkers = underlineMarkerRegex.findAll(text)
            .map { match ->
                val index = match.groupValues[1].toIntOrNull() ?: -1
                Triple(match.range.first, match.range.last + 1, index)
            }
            .filter { it.third >= 0 && it.third < extractedUnderlineTags.size }
            .sortedBy { it.first }
            .toList()

        // Apply underline spans (process in reverse to maintain positions)
        underlineMarkers.asReversed().forEach { (start, end, index) ->
            val underlineContent = extractedUnderlineTags[index]

            // Parse markdown in inner content (handles **bold**, *italic*, etc.)
            val parsedContent = parseMarkdownInHtmlContent(underlineContent)

            // Replace marker with parsed content (Spannable)
            result.replace(start, end, parsedContent)

            // Apply underline span to the entire parsed content
            val contentStart = start
            val contentEnd = start + parsedContent.length

            if (contentStart >= 0 && contentEnd > contentStart && contentEnd <= result.length) {
                result.setSpan(
                    UnderlineSpan(),
                    contentStart,
                    contentEnd,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        return result
    }

    /**
     * Apply link spans (URLSpan) to the rendered spannable
     */
    private fun applyLinkSpans(
        spannable: SpannableStringBuilder,
        textWithPlaceholders: String
    ): SpannableStringBuilder {
        val result = SpannableStringBuilder(spannable)
        val text = result.toString()

        // Find link markers in the rendered text
        val linkMarkerRegex = Regex("""\[\[LK:(\d+)]]""")
        val linkMarkers = linkMarkerRegex.findAll(text)
            .map { match ->
                val index = match.groupValues[1].toIntOrNull() ?: -1
                Triple(match.range.first, match.range.last + 1, index)
            }
            .filter { it.third >= 0 && it.third < extractedLinkTags.size }
            .sortedBy { it.first }
            .toList()

        // Apply link spans (process in reverse to maintain positions)
        linkMarkers.asReversed().forEach { (start, end, index) ->
            val linkTag = extractedLinkTags[index]

            // Parse markdown in inner content (handles **bold**, *italic*, etc.)
            val parsedContent = parseMarkdownInHtmlContent(linkTag.content)

            // Replace marker with parsed content (Spannable)
            result.replace(start, end, parsedContent)

            // Apply URLSpan to the entire parsed content
            val contentStart = start
            val contentEnd = start + parsedContent.length

            if (contentStart >= 0 && contentEnd > contentStart && contentEnd <= result.length) {
                result.setSpan(
                    URLSpan(linkTag.url),
                    contentStart,
                    contentEnd,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        return result
    }

    /**
     * Parse markdown content inside HTML tags (e.g., **bold**, *italic*, LaTeX, etc.)
     * This ensures markdown syntax inside HTML tags is properly rendered.
     */
    private fun parseMarkdownInHtmlContent(content: String): SpannableStringBuilder {
        if (content.isBlank()) {
            return SpannableStringBuilder()
        }

        try {
            // Step 1: Extract LaTeX first (to avoid conflicts)
            val (textWithLatexPlaceholders, blockLatex, inlineLatex) = extractLatexFromRawText(content)

            // Step 2: Parse markdown
            val markdownResult = commonMarkParser.parse(textWithLatexPlaceholders)
            val spannable = convertNodeToSpannable(markdownResult)

            // Step 3: Restore LaTeX (using overloaded version with explicit lists)
            val withLatex = replacePlaceholdersWithLatex(spannable, blockLatex, inlineLatex)

            return withLatex
        } catch (e: Exception) {
            // Fallback: return plain text if parsing fails
            android.util.Log.w("HybridStreamingParser", "Failed to parse markdown in HTML content: $content", e)
            return SpannableStringBuilder(content)
        }
    }

    /**
     * Overloaded version of replacePlaceholdersWithLatex that accepts explicit LaTeX lists.
     * Used for parsing markdown inside HTML tags.
     */
    private fun replacePlaceholdersWithLatex(
        spannable: SpannableStringBuilder,
        blockLatex: List<String>,
        inlineLatex: List<String>
    ): SpannableStringBuilder {
        val result = SpannableStringBuilder(spannable)
        val blockPattern = Regex("""\[\[LB:(\d+)]]""")
        val inlinePattern = Regex("""\[\[LI:(\d+)]]""")

        // Blocks
        val blockMatches = blockPattern.findAll(result).toList().asReversed()
        for (m in blockMatches) {
            val idx = m.groupValues[1].toIntOrNull() ?: continue
            if (idx !in blockLatex.indices) continue
            val start = m.range.first
            val end = m.range.last + 1
            val rawLatex = "$$" + blockLatex[idx] + "$$"
            result.replace(start, end, rawLatex)
            val latexSpan = createLatexSpan(blockLatex[idx], isBlock = true)
            val spanEnd = (start + rawLatex.length).coerceAtMost(result.length)
            if (start >= 0 && spanEnd > start && spanEnd <= result.length) {
                result.setSpan(latexSpan, start, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }

        // Inline
        val inlineMatches = inlinePattern.findAll(result).toList().asReversed()
        for (m in inlineMatches) {
            val idx = m.groupValues[1].toIntOrNull() ?: continue
            if (idx !in inlineLatex.indices) continue

            val start = m.range.first
            val end = m.range.last + 1

            val rawLatex = "$" + inlineLatex[idx] + "$"

            result.replace(start, end, rawLatex)

            val latexSpan = createLatexSpan(inlineLatex[idx], isBlock = false)
            val spanEnd = (start + rawLatex.length).coerceAtMost(result.length)
            if (start >= 0 && spanEnd > start && spanEnd <= result.length) {
                result.setSpan(latexSpan, start, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }

        return result
    }


    private fun extractLatexFromRawText(text: String): Triple<String, List<String>, List<String>> {
        val blockLatexList = mutableListOf<String>()
        val inlineLatexList = mutableListOf<String>()

        var result = text

        val blockRegex = Regex("""(?<!\$)\$\$(.*?)\$\$(?!\$)""", RegexOption.DOT_MATCHES_ALL)
        result = blockRegex.replace(result) { match ->
            val latexContent = match.groupValues[1]
            blockLatexList.add(latexContent)
            "[[LB:${blockLatexList.size - 1}]]"
        }

        val inlineRegex = Regex("""(?<!\$)\$(.*?)\$(?!\$)""")
        result = inlineRegex.replace(result) { match ->
            val latexContent = match.groupValues[1]
            inlineLatexList.add(latexContent)
            "[[LI:${inlineLatexList.size - 1}]]"
        }

        return Triple(result, blockLatexList, inlineLatexList)
    }

    private fun replacePlaceholdersWithLatex(spannable: SpannableStringBuilder): SpannableStringBuilder {
        val result = SpannableStringBuilder(spannable)
        val blockPattern = Regex("""\[\[LB:(\d+)]]""")
        val inlinePattern = Regex("""\[\[LI:(\d+)]]""")

        // Blocks
        val blockMatches = blockPattern.findAll(result).toList().asReversed()
        for (m in blockMatches) {
            val idx = m.groupValues[1].toIntOrNull() ?: continue
            if (idx !in extractedBlockLatexBlocks.indices) continue
            val start = m.range.first
            val end = m.range.last + 1
            val rawLatex = "$$" + extractedBlockLatexBlocks[idx] + "$$"
            result.replace(start, end, rawLatex)
            val latexSpan = createLatexSpan(extractedBlockLatexBlocks[idx], isBlock = true)
            val spanEnd = (start + rawLatex.length).coerceAtMost(result.length)
            if (start >= 0 && spanEnd > start && spanEnd <= result.length) {
                result.setSpan(latexSpan, start, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }

        val inlineMatches = inlinePattern.findAll(result).toList().asReversed()
        for (m in inlineMatches) {
            val idx = m.groupValues[1].toIntOrNull() ?: continue
            if (idx !in extractedInlineLatexBlocks.indices) continue

            val start = m.range.first
            val end = m.range.last + 1

            val rawLatex = "$" + extractedInlineLatexBlocks[idx] + "$"

            result.replace(start, end, rawLatex)

            val latexSpan = createLatexSpan(extractedInlineLatexBlocks[idx], isBlock = false)
            val spanEnd = (start + rawLatex.length).coerceAtMost(result.length)
            if (start >= 0 && spanEnd > start && spanEnd <= result.length) {
                result.setSpan(latexSpan, start, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }

        return result
    }
    fun finalize(): SpannableStringBuilder {
        val finalText = fullTextBuffer.toString()
        cachedRendered = parseAndRender(finalText)
        lastParsedText = finalText

        // Log final rendered spannable with visible newline characters
//        cachedRendered?.let { finalSpannable ->
//            logFinalRenderedString(finalSpannable)
//        }

        return cachedRendered!!
    }

    fun getCurrentSpannable(): SpannableStringBuilder {
        return cachedRendered ?: SpannableStringBuilder()
    }

    fun clear() {
        fullTextBuffer.clear()
        cachedRendered = null
        lastParsedText = ""
        extractedBlockLatexBlocks.clear()
        extractedInlineLatexBlocks.clear()
        extractedHtmlBlocks.clear()
        extractedFontTags.clear()
        extractedSpanTags.clear()
        extractedUnderlineTags.clear()
        extractedLinkTags.clear()
        extractedImageTags.clear()
        extractedVisualContents.clear()
        segmentList.clear()
    }

    fun getSegments(): List<MessageContentSegment> {
        return segmentList.toList()
    }

    private fun logDocumentStructure(node: Node, indent: String = "") {
        val nodeInfo = when (node) {
            is Document -> "Document"
            is Heading -> "Heading(level=${node.level})"
            is Paragraph -> "Paragraph"
            is Text -> "Text: '${node.literal.take(50)}'"
            is Emphasis -> "Emphasis"
            is StrongEmphasis -> "StrongEmphasis"
            is Code -> "Code: '${node.literal}'"
            is FencedCodeBlock -> "FencedCodeBlock"
            is IndentedCodeBlock -> "IndentedCodeBlock"
            is BulletList -> "BulletList"
            is OrderedList -> "OrderedList"
            is ListItem -> "ListItem"
            is BlockQuote -> "BlockQuote"
            is Link -> "Link: ${node.destination}"
            is TableBlock -> "TableBlock"
            is TableHead -> "TableHead"
            is TableBody -> "TableBody"
            is TableRow -> "TableRow"
            is TableCell -> "TableCell"
            is SoftLineBreak -> "SoftLineBreak"
            is HardLineBreak -> "HardLineBreak"
            else -> node.javaClass.simpleName
        }

        var child = node.firstChild
        while (child != null) {
            logDocumentStructure(child, "$indent  ")
            child = child.next
        }
    }


    private fun parseIntoSegments(fullSpannable: SpannableStringBuilder, rootNode: Node, textWithPlaceholders: String, originalText: String): List<MessageContentSegment> {
        val segments = mutableListOf<MessageContentSegment>()

        // Collect tables (still marked by '■' in SpannableVisitor)
        val tables = mutableListOf<TableData>()
        extractTables(rootNode, tables)

        // Collect block latex spans with positions
        val allLatexSpans = fullSpannable.getSpans(0, fullSpannable.length, LatexSpan::class.java)
            .map { span ->
                Triple(
                    fullSpannable.getSpanStart(span),
                    fullSpannable.getSpanEnd(span),
                    span
                )
            }
            .filter { it.first >= 0 && it.second >= 0 && it.third.isBlock }
            .sortedBy { it.first }

        // Collect HTML markers from the rendered text (markers should appear as literal text)
        val htmlMarkerRegex = Regex("""\[\[HB:(\d+)]]""")
        val text = fullSpannable.toString()
        val htmlMarkers = htmlMarkerRegex.findAll(text)
            .map { match ->
                val index = match.groupValues[1].toIntOrNull() ?: -1
                Triple(match.range.first, match.range.last + 1, index)
            }
            .filter { it.third >= 0 && it.third < extractedHtmlBlocks.size }
            .sortedBy { it.first }
            .toList()

        // Collect image markers from the rendered text
        val imageMarkerRegex = Regex("""\[\[IMG:(\d+)]]""")
        val imageMarkers = imageMarkerRegex.findAll(text)
            .map { match ->
                val index = match.groupValues[1].toIntOrNull() ?: -1
                Triple(match.range.first, match.range.last + 1, index)
            }
            .filter { it.third >= 0 && it.third < extractedImageTags.size }
            .sortedBy { it.first }
            .toList()

        // Collect visual content markers from the rendered text
        val visualContentMarkerRegex = Regex("""\[\[VC:(\d+)]]""")
        val visualContentMarkers = visualContentMarkerRegex.findAll(text)
            .map { match ->
                val index = match.groupValues[1].toIntOrNull() ?: -1
                Triple(match.range.first, match.range.last + 1, index)
            }
            .filter { it.third >= 0 && it.third < extractedVisualContents.size }
            .sortedBy { it.first }
            .toList()

        // Collect incomplete visual content markers from the rendered text
        val incompleteVisualMarkerRegex = Regex("""\[\[VC_INCOMPLETE:(\d+)]]""")
        val incompleteVisualMarkers = incompleteVisualMarkerRegex.findAll(text)
            .map { match ->
                val index = match.groupValues[1].toIntOrNull() ?: -1
                Triple(match.range.first, match.range.last + 1, index)
            }
            .filter { it.third >= 0 && it.third < incompleteVisualContents.size }
            .sortedBy { it.first }
            .toList()

        var cursor = 0
        var blockIndex = 0
        var tableIndex = 0
        var htmlIndex = 0
        var imageIndex = 0
        var visualContentIndex = 0
        var incompleteVisualIndex = 0

        while (cursor < text.length) {
            val nextBlockStart = if (blockIndex < allLatexSpans.size) allLatexSpans[blockIndex].first else Int.MAX_VALUE
            val nextTableMarker = text.indexOf('■', cursor).let { if (it == -1) Int.MAX_VALUE else it }
            val nextHtmlMarker = if (htmlIndex < htmlMarkers.size) htmlMarkers[htmlIndex].first else Int.MAX_VALUE
            val nextImageMarker = if (imageIndex < imageMarkers.size) imageMarkers[imageIndex].first else Int.MAX_VALUE
            val nextVisualContentMarker = if (visualContentIndex < visualContentMarkers.size) visualContentMarkers[visualContentIndex].first else Int.MAX_VALUE
            val nextIncompleteVisualMarker = if (incompleteVisualIndex < incompleteVisualMarkers.size) incompleteVisualMarkers[incompleteVisualIndex].first else Int.MAX_VALUE

            val nextSpecial = minOf(nextHtmlMarker, nextImageMarker, nextBlockStart, nextTableMarker, nextVisualContentMarker, nextIncompleteVisualMarker, text.length)

            // Add text segment before special element
            if (nextSpecial > cursor) {
                val textSpannable = fullSpannable.subSequence(cursor, nextSpecial) as Spannable
                val textContent = textSpannable.toString().trim()
                if (textContent.isNotEmpty() &&
                    !textContent.matches(Regex("""\[\[HB:\d+]]""")) &&
                    !textContent.matches(Regex("""\[\[IMG:\d+]]""")) &&
                    !textContent.matches(Regex("""\[\[VC:\d+]]"""))) {
                    segments.add(MessageContentSegment.TextSegment(textSpannable, originalText))
                }
            }

            when {
                nextSpecial == nextHtmlMarker && htmlIndex < htmlMarkers.size -> {
                    // Create HTML box segment
                    val (_, end, htmlBlockIndex) = htmlMarkers[htmlIndex]
                    if (htmlBlockIndex in extractedHtmlBlocks.indices) {
                        val htmlBlock = extractedHtmlBlocks[htmlBlockIndex]
                        segments.add(
                            MessageContentSegment.HtmlBoxSegment(
                                innerContent = htmlBlock.innerContent,
                                styles = htmlBlock.styles
                            )
                        )
                    }
                    cursor = end
                    htmlIndex++
                }
                nextSpecial == nextImageMarker && imageIndex < imageMarkers.size -> {
                    // Create image segment
                    val (_, end, imageTagIndex) = imageMarkers[imageIndex]
                    if (imageTagIndex in extractedImageTags.indices) {
                        val imageTag = extractedImageTags[imageTagIndex]
                        segments.add(
                            MessageContentSegment.ImageSegment(
                                imageUrl = imageTag.imageUrl,
                                altText = imageTag.altText,
                                width = imageTag.width,
                                height = imageTag.height
                            )
                        )
                    }
                    cursor = end
                    imageIndex++
                }
                nextSpecial == nextBlockStart && blockIndex < allLatexSpans.size -> {
                    val (_, end, span) = allLatexSpans[blockIndex]
                    segments.add(MessageContentSegment.BlockLatexSegment(span.latex, isBlock = true))
                    cursor = end
                    blockIndex++
                }
                nextSpecial == nextTableMarker && tableIndex < tables.size -> {
                    val table = tables[tableIndex]
                    segments.add(
                        MessageContentSegment.TableSegment(
                            tableData = table,
                            rawMarkdown = table.rawMarkdown
                        )
                    )
                    tableIndex++
                    cursor = nextTableMarker + 1
                }
                nextSpecial == nextVisualContentMarker && visualContentIndex < visualContentMarkers.size -> {
                    // Create visual content segment (complete block)
                    val (_, end, visualContentBlockIndex) = visualContentMarkers[visualContentIndex]
                    if (visualContentBlockIndex in extractedVisualContents.indices) {
                        val visualContent = extractedVisualContents[visualContentBlockIndex]
                        val segmentType = when (visualContent.type) {
                            ExtractionVisualContentType.MINDMAP -> VisualContentType.MINDMAP
                            ExtractionVisualContentType.CHART -> VisualContentType.CHART
                            ExtractionVisualContentType.MERMAID -> VisualContentType.MERMAID
                        }
                        segments.add(
                            MessageContentSegment.VisualContentSegment(
                                type = segmentType,
                                contentHash = visualContent.contentHash,
                                content = visualContent.content,
                                rawMarkdown = visualContent.content
                            )
                        )
                    }
                    cursor = end
                    visualContentIndex++
                }
                nextSpecial == nextIncompleteVisualMarker && incompleteVisualIndex < incompleteVisualMarkers.size -> {
                    // Create incomplete visual content segment (show generating card)
                    val (_, end, incompleteIndex) = incompleteVisualMarkers[incompleteVisualIndex]
                    if (incompleteIndex in incompleteVisualContents.indices) {
                        val incompleteData = incompleteVisualContents[incompleteIndex]
                        val segmentType = when (incompleteData.type) {
                            ExtractionVisualContentType.MINDMAP -> VisualContentType.MINDMAP
                            ExtractionVisualContentType.CHART -> VisualContentType.CHART
                            ExtractionVisualContentType.MERMAID -> VisualContentType.MERMAID
                        }
                        // Create a placeholder segment with a temporary hash (will be replaced when complete)
                        val tempHash = "incomplete_${incompleteData.startIndex}_${incompleteData.type.name}"
                        segments.add(
                            MessageContentSegment.VisualContentSegment(
                                type = segmentType,
                                contentHash = tempHash,
                                content = incompleteData.partialContent, // Store partial content
                                rawMarkdown = incompleteData.partialContent
                            )
                        )
                    }
                    cursor = end
                    incompleteVisualIndex++
                }
                else -> {
                    // no more specials
                    cursor = text.length
                }
            }
        }

        return segments
    }

    private fun extractTables(node: Node, tables: MutableList<TableData>) {
        if (node is TableBlock) {
            val tableData = parseTableBlock(node)
            tables.add(tableData)
        }

        var child = node.firstChild
        while (child != null) {
            extractTables(child, tables)
            child = child.next
        }
    }

    private fun parseTableBlock(tableBlock: TableBlock): TableData {
        val headers = mutableListOf<TableCellSpannable>()  // Changed type
        val rows = mutableListOf<List<TableCellSpannable>>()  // Changed type

        var child = tableBlock.firstChild
        while (child != null) {
            when (child) {
                is TableHead -> {
                    var row = child.firstChild
                    while (row != null) {
                        if (row is TableRow) {
                            headers.addAll(extractRowCells(row))  // Renamed method
                        }
                        row = row.next
                    }
                }
                is TableBody -> {
                    var row = child.firstChild
                    while (row != null) {
                        if (row is TableRow) {
                            rows.add(extractRowCells(row))  // Renamed method
                        }
                        row = row.next
                    }
                }
            }
            child = child.next
        }

        val rawMarkdown = buildTableMarkdown(headers, rows)
        return TableData(headers, rows, rawMarkdown)
    }

    private fun extractRowCells(row: TableRow): List<TableCellSpannable> {  // Renamed & changed return type
        val cells = mutableListOf<TableCellSpannable>()  // Changed type
        var cell = row.firstChild
        while (cell != null) {
            if (cell is TableCell) {
                cells.add(extractTableCellData(cell))
            }
            cell = cell.next
        }
        return cells
    }

    private fun extractTableCellData(cell: Node): TableCellSpannable {
        // Build spannable for this cell from the CommonMark AST
        val builder = SpannableStringBuilder()
        // Use SpannableVisitor with table cell mode enabled to preserve newlines
        val visitor = SpannableVisitor(builder, isTableCell = true)

        var child = cell.firstChild
        while (child != null) {
            child.accept(visitor)
            child = child.next
        }

        // Apply LaTeX spans first (they also use placeholders)
        val withLatex = replacePlaceholdersWithLatex(builder)

        // IMPORTANT:
        // At this point, the cell text may still contain [[FC:n]] / [[SC:n]] markers
        // that were inserted during the global extraction pass over the full text.
        // We now reuse the SAME global replacement logic that we use for
        // top-level text, so table cells share the same span/font styling.

        // Apply global font color spans using global extractedFontTags
        val withFontColors = applyFontColorSpans(withLatex, withLatex.toString())

        // Apply global span style spans using global extractedSpanTags
        val withSpanStyles = applySpanStyleSpans(withFontColors, withFontColors.toString())

        return TableCellSpannable(withSpanStyles)
    }

    /**
     * Build a markdown table string from headers and rows so it can be saved as raw text.
     */
    private fun buildTableMarkdown(
        headers: List<TableCellSpannable>,
        rows: List<List<TableCellSpannable>>
    ): String {
        val lines = mutableListOf<String>()

        if (headers.isNotEmpty()) {
            val headerLine = "|" + headers.joinToString("|") {
                val text = it.spannable.toString().trim()
                if (text.isEmpty()) " " else text
            } + "|"
            val separatorLine = "|" + headers.joinToString("|") { "---" } + "|"
            lines += headerLine
            lines += separatorLine
        }

        rows.forEach { row ->
            val rowLine = "|" + row.joinToString("|") {
                val text = it.spannable.toString().trim()
                if (text.isEmpty()) " " else text
            } + "|"
            lines += rowLine
        }

        return lines.joinToString("\n")
    }

//    private fun extractTextFromNode(node: Node): String {
//        val builder = StringBuilder()
//        when (node) {
//            is Text -> builder.append(node.literal)
//            is Code -> builder.append(node.literal)
//            else -> {
//                var child = node.firstChild
//                while (child != null) {
//                    builder.append(extractTextFromNode(child))
//                    child = child.next
//                }
//            }
//        }
//        return builder.toString().trim()
//    }

    private fun convertNodeToSpannable(node: Node): SpannableStringBuilder {
        val builder = SpannableStringBuilder()
        val visitor = SpannableVisitor(builder)
        node.accept(visitor)
        return builder
    }
    private inner class SpannableVisitor(
        val builder: SpannableStringBuilder,
        private val isTableCell: Boolean = false
    ) : AbstractVisitor() {
        private var listDepth = 0
        private var isProcessingDocument = false
        private val orderedListStack = mutableListOf<OrderedList?>()
        private val orderedCounters = mutableListOf<Int>()
        private val bulletPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = config.bulletTextSize * context.resources.displayMetrics.scaledDensity
        }

        /**
         * Helper to ensure exactly N trailing newlines.
         * Counts existing trailing newlines and adds only what's needed.
         */
        private fun ensureTrailingNewlines(count: Int) {
            // Count existing trailing newlines
            var existing = 0
            var pos = builder.length - 1
            while (pos >= 0 && builder[pos] == '\n') {
                existing++
                pos--
            }

            // Add more if needed
            val needed = count - existing
            if (needed > 0) {
                builder.append("\n".repeat(needed))
            }
        }

        override fun visit(document: Document) {
            isProcessingDocument = true
            visitChildren(document)
            isProcessingDocument = false
        }

        override fun visit(heading: Heading) {
            val start = builder.length
            visitChildren(heading)
            val end = builder.length

            val relativeSize = config.getHeadingSize(heading.level)

            builder.setSpan(RelativeSizeSpan(relativeSize), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            builder.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

            // Headings are block elements - ensure blank line after
            if (heading.next != null) {
                ensureTrailingNewlines(2)
            }
        }

        override fun visit(paragraph: Paragraph) {
            visitChildren(paragraph)
            // In table cells, don't add trailing newlines - preserve original spacing
            if (!isTableCell && !suppressParagraphTrailingNewline && paragraph.next != null) {
                val nextIsListBlock = paragraph.next is BulletList || paragraph.next is OrderedList
                val nextIsTableBlock = paragraph.next is TableBlock

                when {
                    // Lists need single newline
                    nextIsListBlock ||  nextIsTableBlock -> {
                        ensureTrailingNewlines(1)
                    }
                    // Normal case: paragraph followed by regular block (paragraph, heading, etc.)
                    // Needs double newline (blank line between blocks)
                    else -> {
                        ensureTrailingNewlines(2)
                    }
                }
            }
        }
        override fun visit(emphasis: Emphasis) {
            val start = builder.length
            visitChildren(emphasis)
            val end = builder.length
            builder.setSpan(StyleSpan(Typeface.ITALIC), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        override fun visit(strongEmphasis: StrongEmphasis) {
            val start = builder.length
            visitChildren(strongEmphasis)
            val end = builder.length
            builder.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        override fun visit(text: Text) {
            builder.append(text.literal)
        }

        override fun visit(code: Code) {
            val start = builder.length
            builder.append(code.literal)
            val end = builder.length
            builder.setSpan(TypefaceSpan("monospace"), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        override fun visit(fencedCodeBlock: FencedCodeBlock) {
            val start = builder.length
            builder.append(fencedCodeBlock.literal)
            val end = builder.length
            builder.setSpan(TypefaceSpan("monospace"), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

            // Code blocks are block elements - ensure blank line after
            if (fencedCodeBlock.next != null) {
                ensureTrailingNewlines(2)
            }
        }

        override fun visit(indentedCodeBlock: IndentedCodeBlock) {
            val start = builder.length
            builder.append(indentedCodeBlock.literal)
            val end = builder.length
            builder.setSpan(TypefaceSpan("monospace"), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

            // Code blocks are block elements - ensure blank line after
            if (indentedCodeBlock.next != null) {
                ensureTrailingNewlines(2)
            }
        }

        override fun visit(bulletList: BulletList) {
            listDepth++
            orderedListStack.add(null)
            visitChildren(bulletList)
            listDepth--
            if (orderedListStack.isNotEmpty()) orderedListStack.removeAt(orderedListStack.lastIndex)

            // Lists are block elements - ensure blank line after
            if (!suppressListTrailingNewline && bulletList.next != null) {
                ensureTrailingNewlines(2)
            }
        }

        override fun visit(orderedList: OrderedList) {
            listDepth++
            orderedListStack.add(orderedList)
            val startNum = try { orderedList.startNumber } catch (_: Throwable) { 1 }
            orderedCounters.add(startNum)
            visitChildren(orderedList)
            listDepth--
            if (orderedListStack.isNotEmpty()) orderedListStack.removeAt(orderedListStack.lastIndex)
            if (orderedCounters.isNotEmpty()) orderedCounters.removeAt(orderedCounters.lastIndex)

            // Lists are block elements - ensure blank line after
            if (!suppressListTrailingNewline && orderedList.next != null) {
                ensureTrailingNewlines(2)
            }
        }

        override fun visit(thematicBreak: ThematicBreak) {
            // Ensure leading newline
            if (builder.isNotEmpty() && !builder.endsWith("\n")) {
                builder.append("\n")
            }

            val start = builder.length
            builder.append("\uFFFC")
            val end = builder.length
            builder.setSpan(HorizontalRuleSpan(context,config), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

            // Thematic breaks are block elements - ensure blank line after
            if (thematicBreak.next != null) {
                ensureTrailingNewlines(2)
            }
        }
        override fun visit(listItem: ListItem) {
            val itemStart = builder.length
            val indentationLevel = listDepth.coerceAtLeast(1)
            val currentOrdered = orderedListStack.lastOrNull()
            val isOrderedItem = currentOrdered != null && orderedCounters.isNotEmpty()

            val parent = listItem.parent
            val isTight = when (parent) {
                is BulletList -> parent.isTight
                is OrderedList -> parent.isTight
                else -> true
            }

            val bulletText = if (isOrderedItem) {
                val number = orderedCounters.last()
                orderedCounters[orderedCounters.lastIndex] = number + 1
                "$number."
            } else {
                when (indentationLevel) {
                    1 -> "✤"
                    2 -> "➤"
                    3 -> "◆"
                    4 -> "☉"
                    else -> "☉"
                }
            }

            var currentTextStart = itemStart
            var child = listItem.firstChild
            while (child != null) {
                if (child is BulletList || child is OrderedList) {
                    applyListItemSpanIfNeeded(currentTextStart, builder.length, indentationLevel, bulletText)
                    child.accept(this)
                    currentTextStart = builder.length
                } else {
                    child.accept(this)
                }
                child = child.next
            }

            applyListItemSpanIfNeeded(
                builderStart = currentTextStart,
                builderEnd = builder.length,
                indentationLevel = indentationLevel,
                bulletText = bulletText
            )

            // Add spacing after item: tight lists = 1 newline, loose lists = 2 newlines
            if (listItem.next != null) {
                val newlineCount = if (isTight) 1 else 2
                ensureTrailingNewlines(newlineCount)
            }
        }

        private fun applyListItemSpanIfNeeded(
            builderStart: Int,
            builderEnd: Int,
            indentationLevel: Int,
            bulletText: String
        ) {
            var spanStart = builderStart
            var spanEnd = builderEnd
            if (spanEnd <= spanStart) return

            while (spanEnd > spanStart && builder[spanEnd - 1] == '\n') {
                spanEnd--
            }
            if (spanEnd <= spanStart) return

            val firstLineEnd = builder.indexOf('\n', spanStart)
            if (firstLineEnd != -1 && firstLineEnd < spanEnd) {
                spanEnd = firstLineEnd  // ← Stop at first newline
            }

            val span = createListItemSpan(indentationLevel, bulletText)
            builder.setSpan(
                span,
                spanStart,
                spanEnd,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        private val baseBulletIndentPx: Int by lazy {
            val spaces = config.bulletIndentSpaces.coerceAtLeast(1)
            val measured = bulletPaint.measureText(" ".repeat(spaces)).roundToInt()
            max(measured, dpToPx(context, 12f))
        }

        private val bulletGapPx: Int = dpToPx(context, config.bulletGapDp)

        private fun createListItemSpan(
            indentationLevel: Int,
            bulletText: String
        ): LeadingMarginSpan {
            val indentForLevel = if (indentationLevel <= 1) 0 else baseBulletIndentPx * (indentationLevel - 1)
            val bulletWidth = bulletPaint.measureText(bulletText)
            val leadingMargin = (indentForLevel + ceil(bulletWidth.toDouble()).toInt() + bulletGapPx)

            return object : LeadingMarginSpan {
                override fun getLeadingMargin(first: Boolean): Int = leadingMargin

                override fun drawLeadingMargin(
                    canvas: Canvas,
                    paint: Paint,
                    x: Int,
                    dir: Int,
                    top: Int,
                    baseline: Int,
                    bottom: Int,
                    text: CharSequence,
                    start: Int,
                    end: Int,
                    first: Boolean,
                    layout: Layout?
                ) {
                    if (!first) return

                    val textStart = x + dir * leadingMargin
                    val drawX = if (dir > 0) {
                        (textStart - bulletGapPx).toFloat() - bulletWidth
                    } else {
                        (textStart + bulletGapPx).toFloat()
                    }

                    val previousTypeface = bulletPaint.typeface
                    val previousAlign = bulletPaint.textAlign
                    val previousColor = bulletPaint.color

                    bulletPaint.typeface = paint.typeface
                    bulletPaint.textAlign = Paint.Align.LEFT
                    bulletPaint.color = paint.color

                    canvas.drawText(bulletText, drawX, baseline.toFloat(), bulletPaint)

                    bulletPaint.typeface = previousTypeface
                    bulletPaint.textAlign = previousAlign
                    bulletPaint.color = previousColor
                }
            }
        }

        override fun visit(softLineBreak: SoftLineBreak) {
            // Soft line break = single newline within paragraph
            // Renders as space in most renderers (per CommonMark spec)
            builder.append("\n")
        }

        override fun visit(hardLineBreak: HardLineBreak) {
            builder.append("\n")
        }

        override fun visit(blockQuote: BlockQuote) {
            val start = builder.length
            visitChildren(blockQuote)
            val end = builder.length

            // Apply custom quote span with better visual styling
            val quoteSpan = CustomQuoteSpan(
                context = context,
                quoteColor = config.quoteSpanColor,
                stripeWidth = dpToPx(context, config.quoteStripeWidthDp),
                gapWidth = dpToPx(context, config.quoteGapWidthDp),
                paddingStart = dpToPx(context, config.quotePaddingStartDp)
            )
            builder.setSpan(quoteSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

            // Make quote text italic for better visual distinction
            builder.setSpan(StyleSpan(Typeface.ITALIC), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

            // Block quotes are block elements - ensure blank line after
            if (blockQuote.next != null) {
                ensureTrailingNewlines(2)
            }
        }

        override fun visit(link: Link) {
            val start = builder.length
            visitChildren(link)
            val end = builder.length
            builder.setSpan(URLSpan(link.destination), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        override fun visit(customNode: CustomNode) {
            if (customNode is Strikethrough) {
                val start = builder.length
                visitChildren(customNode)
                val end = builder.length
                builder.setSpan(StrikethroughSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else {
                visitChildren(customNode)
            }
        }

        override fun visitChildren(parent: Node) {
            var child = parent.firstChild
            while (child != null) {
                if (isProcessingDocument && child is TableBlock) {
                    builder.append("■")
                    if (child.next != null) {
                        builder.append("\n")
                    }
                } else {
                    child.accept(this)
                }
                child = child.next
            }
        }

        // Helper extension
        private fun SpannableStringBuilder.endsWith(suffix: String): Boolean {
            return this.toString().endsWith(suffix)
        }
    }



    private companion object {
        fun dpToPx(context: Context, dp: Float): Int {
            val density = context.resources.displayMetrics.density
            return (dp * density).toInt()
        }
    }

    private fun createLatexSpan(latex: String, isBlock: Boolean): ReplacementSpan {
        val desiredDp = if (isBlock) config.blockLatexTextSize else config.inlineLatexTextSize
        val textSizePx = desiredDp * context.resources.displayMetrics.scaledDensity
        val latexView = LatexView(context).apply {
            setTextSize(textSizePx)
            this.latex = latex
            measure(
                android.view.View.MeasureSpec.UNSPECIFIED,
                android.view.View.MeasureSpec.UNSPECIFIED
            )
            layout(0, 0, measuredWidth, measuredHeight)
        }

        val drawable = latexView.drawable
        val displayMetrics = context.resources.displayMetrics
        val padding = config.paddingDefault * 2
        val availableWidth = displayMetrics.widthPixels - padding

        return LatexSpan(
            latex = latex,
            isBlock = isBlock,
            drawable = drawable,
            availableWidth = availableWidth
        )
    }

    /**
     * ReplacementSpan carrying latex source and block/inline info so we can
     * discover block spans later when building segments.
     */
    private inner class LatexSpan(
        val latex: String,
        val isBlock: Boolean,
        private val drawable: android.graphics.drawable.Drawable?,
        private val availableWidth: Int
    ) : ReplacementSpan() {
        private var latexWidth = drawable?.intrinsicWidth ?: 0
        private var scaledWidth = drawable?.intrinsicWidth ?: 0
        private var scaledHeight = drawable?.intrinsicHeight ?: 0
        private var scaleRatio = 1f

        override fun getSize(
            paint: Paint,
            text: CharSequence?,
            start: Int,
            end: Int,
            fm: Paint.FontMetricsInt?
        ): Int {
            val width = drawable?.intrinsicWidth ?: 0
            val height = drawable?.intrinsicHeight ?: 0

            if (isBlock) {
                fm?.let {
                    val blockHeight = height
                    val ascent = -blockHeight * 3 / 4
                    val descent = blockHeight / 4
                    it.ascent = ascent
                    it.descent = descent
                    it.top = it.ascent
                    it.bottom = it.descent
                }
                latexWidth = width
                scaledWidth = width
                scaledHeight = height
                // Return measured width of actual text to prevent selection index errors
                // Visual centering/scaling is handled in draw()
                val actualText = text?.subSequence(start, end)?.toString() ?: ""
                return paint.measureText(actualText).toInt().coerceAtLeast(1)
            } else {
                fm?.let {
                    it.ascent = -height * 3 / 4
                    it.descent = height / 4
                    it.top = it.ascent
                    it.bottom = it.descent
                }

                scaleRatio = 1f
                scaledWidth = width
                scaledHeight = height
                latexWidth = scaledWidth
                return scaledWidth
            }
        }

        override fun draw(
            canvas: Canvas,
            text: CharSequence?,
            start: Int,
            end: Int,
            x: Float,
            top: Int,
            y: Int,
            bottom: Int,
            paint: Paint
        ) {
            drawable?.let {
                canvas.withSave {
                    val yOffset = y.toFloat() - it.intrinsicHeight * 3f / 4f
                    val canvasWidth = canvas.width.toFloat()

                    if (isBlock) {
                        val needsScaling = it.intrinsicWidth > canvasWidth
                        val scaleRatio =
                            if (needsScaling) canvasWidth / it.intrinsicWidth else 1f

                        if (needsScaling) {
                            scale(scaleRatio, scaleRatio)
                            val scaledWidth = it.intrinsicWidth * scaleRatio
                            val centerX = (canvasWidth - scaledWidth) / (2f * scaleRatio)
                            translate(centerX, yOffset / scaleRatio)
                        } else {
                            val centerX = (canvasWidth - it.intrinsicWidth) / 2f
                            translate(centerX, yOffset)
                        }
                    } else {
                        // Inline: center the latex vertically within the line box without scaling
                        val fmPaint = paint.fontMetricsInt
                        val fontHeight = (fmPaint.descent - fmPaint.ascent).toFloat()
                        val imgH =
                            if (scaledHeight > 0) scaledHeight.toFloat() else it.intrinsicHeight.toFloat()
                        val transY = y + fmPaint.ascent + (fontHeight - imgH) / 2f
                        translate(x, transY)
                    }

                    it.setBounds(0, 0, it.intrinsicWidth, it.intrinsicHeight)
                    it.draw(this)
                }
            }
        }
    }

    /**
     * Log CommonMark document structure (AST tree) with full content preview
     */
//    private fun logCommonMarkDocumentStructure(rootNode: Node) {
//        val logBuilder = StringBuilder()
//        logBuilder.append("═══════════════════════════════════════════════════════\n")
//        logBuilder.append("🌳 COMMONMARK DOCUMENT STRUCTURE (AST Tree):\n")
//        logBuilder.append("═══════════════════════════════════════════════════════\n")
//
//        logDocumentStructureRecursive(rootNode, "", logBuilder)
//
//        logBuilder.append("═══════════════════════════════════════════════════════\n")
//        Log.d("HybridParser", logBuilder.toString())
//    }

    /**
     * Recursively log document structure with full content preview
     */
    private fun logDocumentStructureRecursive(node: Node, indent: String, logBuilder: StringBuilder) {
        val nodeInfo = when (node) {
            is Document -> "Document"
            is Heading -> {
                val text = extractTextFromNode(node)
                "Heading(level=${node.level}): \"$text\""
            }
            is Paragraph -> {
                val text = extractTextFromNode(node)
                "Paragraph: \"$text\""
            }
            is Text -> "Text: \"${node.literal}\""
            is Emphasis -> {
                val text = extractTextFromNode(node)
                "Emphasis: \"$text\""
            }
            is StrongEmphasis -> {
                val text = extractTextFromNode(node)
                "StrongEmphasis: \"$text\""
            }
            is Code -> "Code: \"${node.literal}\""
            is FencedCodeBlock -> "FencedCodeBlock: \"${node.literal}\""
            is IndentedCodeBlock -> "IndentedCodeBlock: \"${node.literal}\""
            is BulletList -> "BulletList"
            is OrderedList -> "OrderedList(start=${try { node.startNumber } catch (_: Throwable) { 1 }})"
            is ListItem -> {
                val text = extractTextFromNode(node)
                "ListItem: \"$text\""
            }
            is BlockQuote -> {
                val text = extractTextFromNode(node)
                "BlockQuote: \"$text\""
            }
            is Link -> {
                val text = extractTextFromNode(node)
                "Link(destination=\"${node.destination}\"): \"$text\""
            }
            is TableBlock -> "TableBlock"
            is TableHead -> "TableHead"
            is TableBody -> "TableBody"
            is TableRow -> "TableRow"
            is TableCell -> {
                val text = extractTextFromNode(node)
                "TableCell: \"$text\""
            }
            is SoftLineBreak -> "SoftLineBreak"
            is HardLineBreak -> "HardLineBreak"
            is ThematicBreak -> "ThematicBreak"
            else -> node.javaClass.simpleName
        }

        logBuilder.append("$indent$nodeInfo\n")

        var child = node.firstChild
        while (child != null) {
            logDocumentStructureRecursive(child, "$indent  ", logBuilder)
            child = child.next
        }
    }

    /**
     * Extract text content from a node and its children
     */
    private fun extractTextFromNode(node: Node): String {
        val builder = StringBuilder()
        when (node) {
            is Text -> builder.append(node.literal)
            is Code -> builder.append(node.literal)
            is FencedCodeBlock -> builder.append(node.literal)
            is IndentedCodeBlock -> builder.append(node.literal)
            else -> {
                var child = node.firstChild
                while (child != null) {
                    builder.append(extractTextFromNode(child))
                    child = child.next
                }
            }
        }
        return builder.toString().trim()
    }

    /**
     * Log final rendered spannable string with visible \n characters.
     * This runs once at the end of streaming to inspect exact newline placement.
     */
//    private fun logFinalRenderedString(finalSpannable: SpannableStringBuilder) {
//        val visibleString = finalSpannable.toString()
//            .replace("\n", "\\n")
//            .replace("\r", "\\r")
//            .replace("\t", "\\t")
//
//        val logBuilder = StringBuilder()
//        logBuilder.append("═══════════════════════════════════════════════════════\n")
//        logBuilder.append("📝 FINAL RENDERED SPANNABLE STRING (with visible \\n):\n")
//        logBuilder.append("═══════════════════════════════════════════════════════\n")
//        logBuilder.append("\"$visibleString\"\n")
//        logBuilder.append("\nRaw length: ${finalSpannable.length}\n")
//        logBuilder.append("═══════════════════════════════════════════════════════\n")
//
//        android.util.Log.d("HybridParserFinal", logBuilder.toString())
//    }
}
