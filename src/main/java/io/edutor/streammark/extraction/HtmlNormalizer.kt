package io.edutor.streammark.extraction

/**
 * Normalizes HTML tags before extraction.
 * Converts styled inline tags (<strong>, <b>, <em>, <i> with style attributes)
 * to standard <span> format with markdown formatting preserved.
 * 
 * This is called BEFORE any extraction happens to ensure consistent handling.
 */
object HtmlNormalizer {
    
    /**
     * Normalize styled inline tags to <span> format.
     * Converts:
     * - <strong style="color: red;">text</strong> → <span style="color: red;">**text**</span>
     * - <b style="color: blue;">text</b> → <span style="color: blue;">**text**</span>
     * - <em style="color: green;">text</em> → <span style="color: green;">*text*</span>
     * - <i style="color: yellow;">text</i> → <span style="color: yellow;">*text*</span>
     * 
     * Tags without style attributes are left unchanged (will be converted to markdown later).
     */
    fun normalizeStyledInlineTags(text: String): String {
        var result = text
        
        // Unified pattern to match styled inline tags: <strong|b|em|i ... style="...">content</tag>
        // This pattern handles style attribute in any position within the tag
        // Captures:
        // Group 1: tag name (strong/b/em/i)
        // Group 2: style attribute value (from style="...")
        // Group 3: inner content
        val styledTagPattern = Regex(
            pattern = """<(strong|b|em|i)(?:[^>]*?\s+style\s*=\s*["']([^"']*?)["'])[^>]*>(.*?)</\1>""",
            options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        
        result = styledTagPattern.replace(result) { match ->
            val tag = match.groupValues[1].lowercase()
            val styleValue = match.groupValues[2]
            val content = match.groupValues[3]
            
            // Extract color from style string
            val colorMatch = Regex("""color\s*:\s*([^;'"]+)""", RegexOption.IGNORE_CASE).find(styleValue)
            val color = colorMatch?.groupValues?.get(1)?.trim()
            
            if (color != null) {
                // Has color - wrap content with markdown formatting + span for color
                val formattedContent = when (tag) {
                    "strong", "b" -> "**$content**"
                    "em", "i" -> "*$content*"
                    else -> content
                }
                
                // Return as span with color style
                """<span style="color: $color;">$formattedContent</span>"""
            } else {
                // No color found in style, keep original tag
                match.value
            }
        }
        
        return result
    }
}

