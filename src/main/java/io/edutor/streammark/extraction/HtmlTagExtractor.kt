package io.edutor.streammark.extraction

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.edutor.streammark.parser.CssStyleParser
import io.edutor.streammark.api.HtmlBoxStyles

/**
 * Extracts HTML tags from raw text and replaces them with placeholder markers.
 * This class handles extraction of div, p, font, span, u, a, and img tags.
 */
object HtmlTagExtractor {

    /**
     * Extract HTML blocks (<div>, <p>, and heading tags h1-h6 with or without style attributes) from raw text.
     * Returns text with HTML blocks replaced by [[HB:index]] markers and list of extracted HTML blocks.
     * Handles nested HTML blocks recursively.
     */
    fun extractHtmlBlocks(text: String): Pair<String, List<HtmlBlockData>> {
        val htmlBlocks = mutableListOf<HtmlBlockData>()
        return extractHtmlBlocksRecursive(text, htmlBlocks)
    }

    /**
     * Recursively extract HTML blocks, handling nested divs/p/heading tags.
     * Nested HTML blocks are extracted first and replaced with placeholders,
     * then the inner content is cleaned of other HTML tags.
     * 
     * IMPORTANT: Nested HTML block placeholders ([[HB:n]]) are resolved by replacing them
     * with the actual nested block's innerContent. This ensures the recursive parser
     * can process the content correctly without needing access to parent's extractedHtmlBlocks.
     */
    private fun extractHtmlBlocksRecursive(text: String, htmlBlocks: MutableList<HtmlBlockData>): Pair<String, List<HtmlBlockData>> {
        var result = text

        // Regex to match <div>, <p>, or heading tags (h1-h6) with optional style attribute
        // Uses non-greedy matching to find innermost blocks first
        val htmlBlockRegex = Regex(
            pattern = """<(div|p|h[1-6])(?:[^>]*\s+style=["']([^"']*)["'])?[^>]*>(.*?)</\1>""",
            options = setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )

        // Keep extracting until no more HTML blocks are found (handles multiple levels of nesting)
        var previousResult = ""
        var iterations = 0
        val maxIterations = 100 // Safety limit to prevent infinite loops
        
        while (result != previousResult && iterations < maxIterations) {
            previousResult = result
            iterations++
            
            // Find all HTML blocks and replace with placeholders
            result = htmlBlockRegex.replace(result) { match ->
                val styleString = match.groupValues.getOrNull(2) ?: ""
                val innerContent = match.groupValues[3].trim()
                
                // Parse CSS styles (will use defaults if styleString is empty)
                val styles = CssStyleParser.parse(styleString)
                
                // Track starting index before recursive extraction
                val startIndex = htmlBlocks.size
                
                // First, recursively extract any nested HTML blocks from inner content
                // This will replace nested <div> and <p> tags with [[HB:n]] placeholders
                // Nested blocks are added to htmlBlocks during this call
                val (innerContentWithPlaceholders, _) = extractHtmlBlocksRecursive(innerContent, htmlBlocks)
                
                // IMPORTANT: Resolve nested HTML block placeholders by replacing them with actual content
                // The placeholders reference indices in htmlBlocks starting from startIndex
                val innerContentWithResolvedNested = resolveNestedHtmlBlockPlaceholders(
                    innerContentWithPlaceholders, 
                    htmlBlocks,
                    startIndex // Blocks added during recursive call start at this index
                )
                
                // Clean the inner content: convert PLAIN HTML tags (without style attributes) to markdown
                // PRESERVE styled tags (with style attributes) - they'll be handled by recursive parser
                val cleanedContent = cleanInnerHtmlContent(innerContentWithResolvedNested)
                
                // Wrap inner content with inherited styles (CSS inheritance)
                // This ensures properties like color, font-size, font-weight are inherited by child elements
                val wrappedContent = wrapWithInheritedStyles(cleanedContent, styles)
                
                // Store HTML block data
                htmlBlocks.add(HtmlBlockData(wrappedContent, styles))
                
                // Replace with placeholder marker
                "[[HB:${htmlBlocks.size - 1}]]"
            }
        }

        return Pair(result, htmlBlocks)
    }
    
    /**
     * Resolve nested HTML block placeholders ([[HB:n]]) by replacing them with the actual
     * nested block's innerContent. This allows the recursive parser to process nested blocks
     * without needing access to the parent's extractedHtmlBlocks list.
     * 
     * @param content Content that may contain [[HB:n]] placeholders
     * @param allHtmlBlocks The complete list of HTML blocks (nested blocks are already added)
     * @param startIndex The starting index where nested blocks begin in allHtmlBlocks
     * @return Content with placeholders replaced by actual nested block content
     */
    private fun resolveNestedHtmlBlockPlaceholders(
        content: String,
        allHtmlBlocks: MutableList<HtmlBlockData>,
        startIndex: Int
    ): String {
        var result = content
        val placeholderRegex = Regex("""\[\[HB:(\d+)]]""")
        
        // Replace placeholders in reverse order to maintain correct positions
        val matches = placeholderRegex.findAll(result).toList().reversed()
        
        for (match in matches) {
            val placeholderIndex = match.groupValues[1].toIntOrNull() ?: continue
            
            // Check if this placeholder refers to a nested block (between startIndex and current end)
            // The placeholder index should be >= startIndex and < allHtmlBlocks.size
            if (placeholderIndex >= startIndex && placeholderIndex < allHtmlBlocks.size) {
                val nestedBlock = allHtmlBlocks[placeholderIndex]
                // Replace placeholder with the nested block's innerContent
                // This content will be processed by the recursive parser, which will
                // extract it as a new HtmlBoxSegment
                result = result.replaceRange(match.range, nestedBlock.innerContent)
            }
            // If placeholderIndex is outside the nested blocks range, leave it as-is
            // (it might refer to a block from a different level)
        }
        
        return result
    }

    /**
     * Extract <font color="..."> tags from raw text.
     * Returns text with font tags replaced by [[FC:index]] markers and list of extracted font tags.
     */
    fun extractFontTags(text: String): Pair<String, List<FontTagData>> {
        val fontTags = mutableListOf<FontTagData>()
        var result = text

        val fontTagRegex = Regex(
            pattern = """<font[^>]*\s+color=["']([^"']+)["'][^>]*>(.*?)</font>""",
            options = setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )

        result = fontTagRegex.replace(result) { match ->
            val colorString = match.groupValues[1].trim()
            val innerContent = match.groupValues[2]
            
            val color = parseFontColor(colorString)
            val cleanedContent = cleanInnerHtmlContent(innerContent)
            
            fontTags.add(FontTagData(color, cleanedContent))
            "[[FC:${fontTags.size - 1}]]"
        }

        return Pair(result, fontTags)
    }

    /**
     * Extract <span style="..."> tags from raw text.
     * Returns text with span tags replaced by [[SC:index]] markers and list of extracted span tags.
     */
    fun extractSpanTags(text: String): Pair<String, List<SpanTagData>> {
        val spanTags = mutableListOf<SpanTagData>()
        var result = text

        val spanTagRegex = Regex(
            pattern = """<span[^>]*\s+style=["']([^"']+)["'][^>]*>(.*?)</span>""",
            options = setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )

        result = spanTagRegex.replace(result) { match ->
            val styleString = match.groupValues[1].trim()
            val innerContent = match.groupValues[2]
            
            val (backgroundColor, padding, textColor) = parseSpanStyle(styleString)
            val cleanedContent = cleanInnerHtmlContent(innerContent)
            
            spanTags.add(SpanTagData(backgroundColor, padding, textColor, cleanedContent))
            "[[SC:${spanTags.size - 1}]]"
        }

        return Pair(result, spanTags)
    }

    /**
     * Extract <u> tags from raw text.
     * Returns text with underline tags replaced by [[UL:index]] markers and list of extracted content.
     */
    fun extractUnderlineTags(text: String): Pair<String, List<String>> {
        val underlineTags = mutableListOf<String>()
        var result = text

        val underlineTagRegex = Regex(
            pattern = """<u[^>]*>(.*?)</u>""",
            options = setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )

        result = underlineTagRegex.replace(result) { match ->
            val innerContent = match.groupValues[1]
            val cleanedContent = cleanInnerHtmlContent(innerContent)
            underlineTags.add(cleanedContent)
            "[[UL:${underlineTags.size - 1}]]"
        }

        return Pair(result, underlineTags)
    }

    /**
     * Extract <a href="..."> tags from raw text.
     * Returns text with link tags replaced by [[LK:index]] markers and list of extracted link tags.
     */
    fun extractLinkTags(text: String): Pair<String, List<LinkTagData>> {
        val linkTags = mutableListOf<LinkTagData>()
        var result = text

        val linkTagRegex = Regex(
            pattern = """<a[^>]*\s+href=["']([^"']+)["'][^>]*>(.*?)</a>""",
            options = setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )

        result = linkTagRegex.replace(result) { match ->
            val url = match.groupValues[1].trim()
            val innerContent = match.groupValues[2]
            val cleanedContent = cleanInnerHtmlContent(innerContent)
            
            linkTags.add(LinkTagData(url, cleanedContent))
            "[[LK:${linkTags.size - 1}]]"
        }

        return Pair(result, linkTags)
    }

    /**
     * Extract <img> tags from raw text.
     * Returns text with image tags replaced by [[IMG:index]] markers and list of extracted image tags.
     */
    fun extractImageTags(text: String): Pair<String, List<ImageTagData>> {
        val imageTags = mutableListOf<ImageTagData>()
        var result = text

        val imageTagRegex = Regex(
            pattern = """<img([^>]*?)(?:\s*/?>|>.*?</img>)""",
            options = setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )

        result = imageTagRegex.replace(result) { match ->
            val attributes = match.groupValues[1]
            
            val srcMatch = Regex("""src=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(attributes)
            val imageUrl = srcMatch?.groupValues?.get(1)?.trim() ?: ""
            
            val altMatch = Regex("""alt=["']([^"']*)["']""", RegexOption.IGNORE_CASE).find(attributes)
            val altText = altMatch?.groupValues?.get(1)?.trim() ?: ""
            
            var width: Dp? = null
            var height: Dp? = null
            
            val widthMatch = Regex("""width=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(attributes)
            widthMatch?.let {
                val widthValue = it.groupValues[1].trim()
                width = parseImageDimension(widthValue)
            }
            
            val heightMatch = Regex("""height=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(attributes)
            heightMatch?.let {
                val heightValue = it.groupValues[1].trim()
                height = parseImageDimension(heightValue)
            }
            
            if (imageUrl.isNotEmpty()) {
                imageTags.add(ImageTagData(imageUrl, altText, width, height))
                "[[IMG:${imageTags.size - 1}]]"
            } else {
                match.value
            }
        }

        return Pair(result, imageTags)
    }

    /**
     * Convert <br> and <br/> tags to newlines in regular text.
     */
    fun convertBrTagsToNewlines(text: String): String {
        return text.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
    }

    /**
     * Wrap content with inherited CSS styles (CSS inheritance).
     * Properties like color, font-size, font-weight are inherited by child elements in CSS.
     * This function wraps the inner content with a <span> tag containing these inherited styles,
     * so the recursive parser can pick them up through the existing span extraction mechanism.
     * 
     * @param content The content to wrap
     * @param styles The parsed CSS styles from the parent HTML block
     * @return Content wrapped in a span with inherited styles, or original content if no inherited styles
     */
    private fun wrapWithInheritedStyles(content: String, styles: HtmlBoxStyles): String {
        // Build CSS style string for inherited properties only
        val inheritedStyles = mutableListOf<String>()
        
        // Color (inherits)
        if (styles.textColor != Color.Unspecified) {
            val colorHex = convertComposeColorToHex(styles.textColor)
            if (colorHex != null) {
                inheritedStyles.add("color: $colorHex")
            }
        }
        
        // Font size (inherits)
        styles.fontSize?.let { size ->
            inheritedStyles.add("font-size: ${size}px")
        }
        
        // Font weight (inherits)
        styles.fontWeight?.let { weight ->
            val weightValue = when {
                weight == FontWeight.Bold -> "bold"
                weight.weight >= 700 -> "bold"
                weight.weight >= 500 -> "500"
                else -> "normal"
            }
            inheritedStyles.add("font-weight: $weightValue")
        }
        
        // Text align (inherits)
        styles.textAlign?.let { align ->
            val alignValue = when (align) {
                TextAlign.Left -> "left"
                TextAlign.Center -> "center"
                TextAlign.Right -> "right"
                TextAlign.Justify -> "justify"
                TextAlign.Start -> "start"
                TextAlign.End -> "end"
                else -> null
            }
            alignValue?.let { inheritedStyles.add("text-align: $it") }
        }
        
        // Text decoration (inherits)
        styles.textDecoration?.let { decoration ->
            val decorationValue = when {
                decoration.contains(TextDecoration.Underline) &&
                decoration.contains(TextDecoration.LineThrough) -> "underline line-through"
                decoration.contains(TextDecoration.Underline) -> "underline"
                decoration.contains(TextDecoration.LineThrough) -> "line-through"
                else -> null
            }
            decorationValue?.let { inheritedStyles.add("text-decoration: $it") }
        }
        
        // If no inherited styles, return content as-is
        if (inheritedStyles.isEmpty()) {
            return content
        }
        
        // Wrap content with span containing inherited styles
        val styleString = inheritedStyles.joinToString("; ")
        return """<span style="$styleString">$content</span>"""
    }
    
    /**
     * Convert Compose Color to hex string format (#RRGGBB or #AARRGGBB)
     */
    private fun convertComposeColorToHex(color: Color): String? {
        return try {
            val red = (color.red * 255).toInt().coerceIn(0, 255)
            val green = (color.green * 255).toInt().coerceIn(0, 255)
            val blue = (color.blue * 255).toInt().coerceIn(0, 255)
            val alpha = (color.alpha * 255).toInt().coerceIn(0, 255)
            
            if (alpha < 255) {
                String.format("#%02X%02X%02X%02X", alpha, red, green, blue)
            } else {
                String.format("#%02X%02X%02X", red, green, blue)
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Clean HTML tags inside HTML box content, converting PLAIN tags (without style attributes) to markdown.
     * PRESERVES styled tags (with style attributes) - they'll be handled by the recursive parser.
     * 
     * The recursive parser (created in HtmlBoxSegmentRenderer) will run the full extraction pipeline:
     * - Extract HTML blocks (nested div/p/h1-h6)
     * - Extract font tags (including styled strong/b/em/i)
     * - Extract span tags
     * - Extract LaTeX
     * - Parse markdown
     */
    private fun cleanInnerHtmlContent(html: String): String {
        var result = html
        
        // Convert <br> to newline
        result = result.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        
        // Convert HTML lists to markdown lists
        result = convertHtmlListsToMarkdown(result)
        
        // Only convert PLAIN tags (without style attribute) to markdown
        // Use negative lookahead to exclude tags with style attribute
        // This preserves styled tags like <strong style="color: red;"> for recursive processing
        
        // Plain <strong> and <b> without styles → **
        result = result.replace(Regex("<(strong|b)(?![^>]*\\bstyle=)[^>]*>", RegexOption.IGNORE_CASE), "**")
        result = result.replace(Regex("</(strong|b)>", RegexOption.IGNORE_CASE), "**")
        
        // Plain <em> and <i> without styles → *
        result = result.replace(Regex("<(em|i)(?![^>]*\\bstyle=)[^>]*>", RegexOption.IGNORE_CASE), "*")
        result = result.replace(Regex("</(em|i)>", RegexOption.IGNORE_CASE), "*")
        
        // Styled tags (with style attribute) are PRESERVED as-is
        // They will be handled by the recursive parser's font tag extraction
        
        return result.trim()
    }
    
    /**
     * Convert HTML list tags (<ol>, <ul>, <li>) to markdown format.
     * Handles nested lists recursively.
     */
    private fun convertHtmlListsToMarkdown(html: String): String {
        var result = html
        
        // Process lists from innermost to outermost (handle nested lists)
        var previousResult = ""
        var iterations = 0
        val maxIterations = 50 // Safety limit
        
        while (result != previousResult && iterations < maxIterations) {
            previousResult = result
            iterations++
            
            // Convert <ol> (ordered list) to markdown numbered list
            result = Regex(
                pattern = """<ol[^>]*>(.*?)</ol>""",
                options = setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
            ).replace(result) { match ->
                val listContent = match.groupValues[1]
                convertListItemsToMarkdown(listContent, isOrdered = true)
            }
            
            // Convert <ul> (unordered list) to markdown bullet list
            result = Regex(
                pattern = """<ul[^>]*>(.*?)</ul>""",
                options = setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
            ).replace(result) { match ->
                val listContent = match.groupValues[1]
                convertListItemsToMarkdown(listContent, isOrdered = false)
            }
        }
        
        return result
    }
    
    /**
     * Convert <li> items to markdown list format.
     * Handles nested lists and styled content inside list items.
     */
    private fun convertListItemsToMarkdown(listContent: String, isOrdered: Boolean): String {
        val items = mutableListOf<String>()
        var itemNumber = 1
        
        // Extract all <li> items (handles nested lists)
        val liRegex = Regex(
            pattern = """<li[^>]*>(.*?)</li>""",
            options = setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        
        liRegex.findAll(listContent).forEach { match ->
            val itemContent = match.groupValues[1].trim()
            
            // Clean the item content (convert plain tags to markdown, preserve styled tags)
            val cleanedItem = cleanListItemContent(itemContent)
            
            // Format as markdown list item
            val markdownItem = if (isOrdered) {
                "$itemNumber. $cleanedItem"
            } else {
                "- $cleanedItem"
            }
            
            items.add(markdownItem)
            itemNumber++
        }
        
        // Join items with newlines
        return items.joinToString("\n")
    }
    
    /**
     * Clean content inside a list item, converting HTML tags to markdown.
     * Preserves styled tags for recursive processing.
     */
    private fun cleanListItemContent(content: String): String {
        var result = content
        
        // Convert <br> to newline
        result = result.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        
        // Convert plain <strong>/<b> to ** (preserve styled ones)
        result = result.replace(Regex("<(strong|b)(?![^>]*\\bstyle=)[^>]*>", RegexOption.IGNORE_CASE), "**")
        result = result.replace(Regex("</(strong|b)>", RegexOption.IGNORE_CASE), "**")
        
        // Convert plain <em>/<i> to * (preserve styled ones)
        result = result.replace(Regex("<(em|i)(?![^>]*\\bstyle=)[^>]*>", RegexOption.IGNORE_CASE), "*")
        result = result.replace(Regex("</(em|i)>", RegexOption.IGNORE_CASE), "*")
        
        // Note: Nested lists inside list items are already handled by convertHtmlListsToMarkdown
        // which processes from innermost to outermost
        
        return result.trim()
    }

    /**
     * Parse font color from string (supports named colors and hex)
     */
    private fun parseFontColor(colorString: String): Int {
        return try {
            if (colorString.startsWith("#")) {
                android.graphics.Color.parseColor(colorString)
            } else {
                when (colorString.lowercase()) {
                    "red" -> android.graphics.Color.RED
                    "blue" -> android.graphics.Color.BLUE
                    "green" -> android.graphics.Color.GREEN
                    "black" -> android.graphics.Color.BLACK
                    "white" -> android.graphics.Color.WHITE
                    "yellow" -> android.graphics.Color.YELLOW
                    "cyan" -> android.graphics.Color.CYAN
                    "magenta" -> android.graphics.Color.MAGENTA
                    "gray", "grey" -> android.graphics.Color.GRAY
                    else -> android.graphics.Color.parseColor("#$colorString")
                }
            }
        } catch (e: Exception) {
            android.graphics.Color.BLACK
        }
    }

    /**
     * Parse span style string to extract background-color, padding, and color
     */
    private fun parseSpanStyle(styleString: String): Triple<Int, Int, Int?> {
        var backgroundColor = android.graphics.Color.TRANSPARENT
        var padding = 0
        var textColor: Int? = null

        try {
            val rules = styleString.split(";")
            
            rules.forEach { rule ->
                val parts = rule.split(":", limit = 2)
                if (parts.size == 2) {
                    val key = parts[0].trim().lowercase()
                    val value = parts[1].trim()

                    when (key) {
                        "background-color" -> {
                            backgroundColor = parseFontColor(value)
                        }
                        "color" -> {
                            textColor = parseFontColor(value)
                        }
                        "padding" -> {
                            val numericValue = value.replace(Regex("[^0-9.]"), "").toIntOrNull() ?: 0
                            padding = numericValue
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Use defaults if parsing fails
        }

        return Triple(backgroundColor, padding, textColor)
    }

    /**
     * Parse image dimension (width/height) from string (e.g., "100px", "50%", "100")
     * Returns Dp if it's a pixel value, null otherwise
     */
    private fun parseImageDimension(dim: String): Dp? {
        return try {
            if (dim.endsWith("px", ignoreCase = true) || dim.endsWith("dp", ignoreCase = true)) {
                val numericValue = dim.replace(Regex("[^0-9.]"), "").toFloatOrNull()
                numericValue?.dp
            } else if (dim.matches(Regex("\\d+"))) {
                dim.toFloatOrNull()?.dp
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}

