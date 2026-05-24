package io.edutor.streammark.extraction

import java.security.MessageDigest

/**
 * Extracts visual content code blocks (mindmap, chart, mermaid) from raw text.
 * Returns text with code blocks replaced by [[VC:index]] markers and list of extracted visual content.
 */
object VisualContentExtractor {

    /**
     * Extract visual content code blocks (```mindmap, ```chart, ```mermaid) from raw text.
     * Returns text with code blocks replaced by [[VC:index]] markers and list of extracted visual content.
     * Also handles incomplete code blocks during streaming.
     */
    fun extractVisualContent(text: String): Pair<String, List<VisualContentData>> {
        val visualContents = mutableListOf<VisualContentData>()
        var result = text

        // Regex to match fenced code blocks with visual content languages
        // Matches: ```mindmap\n...\n```, ```chart\n...\n```, ```mermaid\n...\n```
        val visualContentRegex = Regex(
            pattern = """```(mindmap|chart|mermaid)\n(.*?)```""",
            options = setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )

        // Extract all COMPLETE visual content blocks
        result = visualContentRegex.replace(result) { match ->
            val language = match.groupValues[1].lowercase()
            val content = match.groupValues[2].trim()
            
            // Create full code block content (with language identifier) for API
            val fullContent = "```$language\n$content\n```"
            
            // Generate content hash for caching (to avoid duplicate API calls)
            val contentHash = generateContentHash(fullContent)
            
            // Store visual content data
            visualContents.add(
                VisualContentData(
                    type = VisualContentType.fromString(language),
                    content = fullContent,
                    contentHash = contentHash
                )
            )
            
            // Replace with placeholder marker
            "[[VC:${visualContents.size - 1}]]"
        }

        return Pair(result, visualContents)
    }

    /**
     * Extract visual content with support for incomplete blocks during streaming.
     * Returns text with complete blocks replaced by [[VC:index]] and incomplete blocks by [[VC_INCOMPLETE:index]].
     */
    fun extractVisualContentWithIncomplete(text: String): Triple<String, List<VisualContentData>, List<IncompleteVisualContentData>> {
        val visualContents = mutableListOf<VisualContentData>()
        val incompleteBlocks = mutableListOf<IncompleteVisualContentData>()
        var result = text

        // First, extract COMPLETE blocks (```mindmap\n...\n```)
        val visualContentRegex = Regex(
            pattern = """```(mindmap|chart|mermaid)\n(.*?)```""",
            options = setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )

        result = visualContentRegex.replace(result) { match ->
            val language = match.groupValues[1].lowercase()
            val content = match.groupValues[2].trim()
            
            val fullContent = "```$language\n$content\n```"
            val contentHash = generateContentHash(fullContent)
            
            visualContents.add(
                VisualContentData(
                    type = VisualContentType.fromString(language),
                    content = fullContent,
                    contentHash = contentHash
                )
            )
            
            "[[VC:${visualContents.size - 1}]]"
        }

        // Then, detect INCOMPLETE blocks (```mindmap\n... without closing ```)
        // We need to find blocks that start with ```mindmap/chart/mermaid but don't have a closing ```
        // Pattern: ```language\n... (everything until end of string or next ``` that's not part of complete block)
        val incompletePattern = Regex(
            pattern = """```(mindmap|chart|mermaid)\n([\s\S]*?)(?=```|$)""",
            options = setOf(RegexOption.IGNORE_CASE)
        )
        
        // Find all potential incomplete blocks
        var incompleteIndex = 0
        result = incompletePattern.replace(result) { match ->
            // Check if this match is NOT already part of a complete block
            // (complete blocks were already replaced with [[VC:index]])
            if (!match.value.contains("[[VC:")) {
                val language = match.groupValues[1].lowercase()
                val partialContent = match.groupValues[2]
                
                // Check if there's a closing ``` immediately after this match
                val matchEnd = match.range.last + 1
                val textAfterMatch = if (matchEnd < result.length) result.substring(matchEnd) else ""
                
                // Only treat as incomplete if there's no closing ``` immediately after
                if (!textAfterMatch.trim().startsWith("```")) {
                    incompleteBlocks.add(
                        IncompleteVisualContentData(
                            type = VisualContentType.fromString(language),
                            partialContent = match.value, // Full match including ```language\n
                            startIndex = match.range.first
                        )
                    )
                    
                    val placeholder = "[[VC_INCOMPLETE:$incompleteIndex]]"
                    incompleteIndex++
                    placeholder
                } else {
                    match.value // Keep as is - might be part of a complete block we missed
                }
            } else {
                match.value // Keep as is if already processed
            }
        }

        return Triple(result, visualContents, incompleteBlocks)
    }

    /**
     * Generate MD5 hash of content for caching purposes
     */
    private fun generateContentHash(content: String): String {
        val md = MessageDigest.getInstance("MD5")
        val hashBytes = md.digest(content.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}

/**
 * Data class to store extracted visual content information
 */
data class VisualContentData(
    val type: VisualContentType,
    val content: String,  // Full code block including ```language\n...\n```
    val contentHash: String  // MD5 hash for caching
)

/**
 * Data class to store incomplete visual content during streaming
 */
data class IncompleteVisualContentData(
    val type: VisualContentType,
    val partialContent: String,  // Partial code block (```language\n... without closing ```)
    val startIndex: Int  // Start position in the text
)

/**
 * Enum for visual content types
 */
enum class VisualContentType {
    MINDMAP,
    CHART,
    MERMAID;

    companion object {
        fun fromString(str: String): VisualContentType {
            return when (str.lowercase()) {
                "mindmap" -> MINDMAP
                "chart" -> CHART
                "mermaid" -> MERMAID
                else -> MINDMAP // Default fallback
            }
        }
    }
}

