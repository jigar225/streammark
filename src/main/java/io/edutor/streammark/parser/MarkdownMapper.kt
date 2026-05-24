package io.edutor.streammark.parser

import android.util.Log
import java.text.Normalizer

/**
 * Enhanced Markdown Mapper with Unicode Normalization
 *
 * KEY FIX: Normalizes both rendered text and raw markdown to NFC form
 * This ensures position mapping is accurate for all languages including Gujarati/Hindi
 *
 * Normalization fixes:
 * 1. Wrong sentence selection (caused by unicode representation mismatch)
 * 2. Adding previous word (caused by position drift)
 * 3. Gujarati/Hindi accuracy (combining characters handled correctly)
 */
object MarkdownMapper {

    fun getRawMarkdownForSelection(
        renderedText: CharSequence,
        rawMarkdown: String,
        selectionStart: Int,
        selectionEnd: Int
    ): String {
        // 🔥 CRITICAL FIX: Emoji-safe normalization
        // Only normalize text, preserve emoji
        val normalizedRendered = normalizePreservingEmoji(renderedText.toString())
        val normalizedRaw = normalizePreservingEmoji(rawMarkdown)

        Log.d("MarkdownMapper", "📝 Normalized rendered length: ${normalizedRendered.length}, raw length: ${normalizedRaw.length}")

        // Safety Checks (use normalized lengths)
        if (selectionStart < 0 || selectionEnd > normalizedRendered.length || selectionStart >= selectionEnd) {
            Log.w("MarkdownMapper", "Invalid selection range")
            return ""
        }

        val selectedText = normalizedRendered.substring(selectionStart, selectionEnd)
        Log.d("MarkdownMapper", "🎯 Target: '$selectedText' [$selectionStart:$selectionEnd]")

        // STRATEGY 1: Index mapping with formatting logic (using NORMALIZED texts)
        val indexResult = tryIndexMapping(
            normalizedRendered, normalizedRaw, selectionStart, selectionEnd, selectedText
        )

        if (indexResult != null) {
            Log.d("MarkdownMapper", "✅ Index mapping: '${indexResult.take(50)}'")
            return indexResult
        }

        // STRATEGY 2: Text search fallback (using NORMALIZED texts)
        Log.w("MarkdownMapper", "⚠️ Trying text search...")
        val textResult = tryTextSearch(normalizedRaw, selectedText, selectionStart, normalizedRendered.length)

        if (textResult != null) {
            Log.d("MarkdownMapper", "✅ Text search: '${textResult.take(50)}'")
            return textResult
        }

        Log.e("MarkdownMapper", "❌ All failed, returning plain text")
        return selectedText
    }

    // ============================================================================================
    // STRATEGY 1: INDEX MAPPING WITH FORMATTING LOGIC
    // ============================================================================================

    private fun tryIndexMapping(
        renderedText: String,  // Already normalized
        rawMarkdown: String,   // Already normalized
        selectionStart: Int,
        selectionEnd: Int,
        selectedText: String
    ): String? {
        try {
            // Step 1: Map positions (now accurate due to normalization!)
            val indexMap = buildIndexMap(renderedText, rawMarkdown)
            val safeStart = selectionStart.coerceIn(indexMap.indices)
            val safeEnd = (selectionEnd - 1).coerceIn(indexMap.indices)

            var rawStart = indexMap[safeStart]
            var rawEnd = indexMap[safeEnd] + 1

            // Step 2: DON'T expand - respect user's exact selection!
            // The issue: expandToWholeWord() was removing punctuation that user selected
            // Solution: Trust the mapped positions, only expand for combining chars
            val originalLength = selectedText.length

            // Only expand if we're in the middle of a combining character sequence
            while (rawEnd < rawMarkdown.length && isCombiningChar(rawMarkdown[rawEnd])) {
                rawEnd++
            }

            // Extract exactly what user selected (plus combining chars)
            var extractedText = rawMarkdown.substring(
                rawStart.coerceAtLeast(0),
                rawEnd.coerceAtMost(rawMarkdown.length)
            )

            // If extracted is way longer than selected, we have extra markdown
            // Strip leading/trailing markdown markers from extracted text
            if (extractedText.length > originalLength * 1.5) {
                extractedText = extractedText.trim()
            }

            // Step 3: Extract base text
            // If extracted is way longer than selected, we have extra markdown
            // Strip leading/trailing markdown markers from extracted text
            if (extractedText.length > originalLength * 1.5) {
                extractedText = extractedText.trim()
            }

            Log.d("MarkdownMapper", "📝 Extracted: '$extractedText'")

            // Step 4: Check for markers IMMEDIATELY before/after (±2 chars max)
            val openingMarker = findOpeningMarkerBefore(rawMarkdown, rawStart)
            val closingMarker = findClosingMarkerAfter(rawMarkdown, rawEnd)
            val insideMarker = findClosingMarkerInside(extractedText)

            Log.d("MarkdownMapper", "🔍 Markers: opening=$openingMarker, closing=$closingMarker, inside=$insideMarker")

            // Step 5: If opening marker found, RE-EXTRACT including it!
            if (openingMarker != null && insideMarker == null) {
                // Re-extract from opening marker position
                val newStart = openingMarker.position
                extractedText = rawMarkdown.substring(
                    newStart.coerceAtLeast(0),
                    rawEnd.coerceAtMost(rawMarkdown.length)
                )

                // Update rawStart for marker detection
                rawStart = newStart

                Log.d("MarkdownMapper", "📝 Re-extracted with opening: '$extractedText'")

                // Re-check for closing marker after re-extraction (create local val for smart cast)
                val tempClosingMarker = findClosingMarkerAfter(rawMarkdown, rawEnd)
                val tempInsideMarker = findClosingMarkerInside(extractedText)

                Log.d("MarkdownMapper", "🔍 After re-extract: closing=$tempClosingMarker, inside=$tempInsideMarker")
            }

            // Step 6: Re-detect markers after potential re-extraction
            val finalOpeningMarker = findOpeningMarkerBefore(rawMarkdown, rawStart)
            val finalClosingMarker = findClosingMarkerAfter(rawMarkdown, rawEnd)
            val finalInsideMarker = findClosingMarkerInside(extractedText)

            // Step 7: Decide based on markers
            var result = extractedText

            // Case 1: Crossing boundary (closing marker INSIDE selection)
            if (insideMarker != null) {
                Log.d("MarkdownMapper", "⚠️ Case: Crossing boundary (marker inside) - return plain text")
                result = extractedText
            }
            // Case 2: Both markers found AND types match
            else if (finalOpeningMarker != null && finalClosingMarker != null &&
                finalOpeningMarker.type == finalClosingMarker.type) {

                val opening = finalOpeningMarker
                val closing = finalClosingMarker

                val withMarkers = rawMarkdown.substring(
                    opening.position.coerceAtLeast(0),
                    (closing.position + closing.length).coerceAtMost(rawMarkdown.length)
                )

                Log.d("MarkdownMapper", "🔍 With markers: '$withMarkers'")

                if (validateTextMatch(withMarkers, selectedText)) {
                    Log.d("MarkdownMapper", "✅ Case: Complete formatted block")
                    result = withMarkers
                } else {
                    Log.d("MarkdownMapper", "⚠️ Case: Validation failed - partial selection")
                    result = extractedText
                }
            }

            // Case 3: Incomplete pair (one side only)
            else if ((openingMarker != null && closingMarker == null) ||
                (openingMarker == null && closingMarker != null)) {
                Log.d("MarkdownMapper", "⚠️ Case: Incomplete marker pair - return plain text")
                result = extractedText
            }
            // Case 4: Check for bullet points
            else {
                val bulletResult = handleBulletPoint(rawMarkdown, rawStart, extractedText, selectedText)
                if (bulletResult != null) {
                    Log.d("MarkdownMapper", "✅ Case: Bullet point")
                    result = bulletResult
                } else {
                    Log.d("MarkdownMapper", "✅ Case: Plain text (no markers)")
                    result = extractedText
                }
            }

            // Step 6: Final validation
            if (!validateTextMatch(result, selectedText)) {
                Log.w("MarkdownMapper", "❌ Final validation failed")
                return null
            }

            return result

        } catch (e: Exception) {
            Log.e("MarkdownMapper", "Error in index mapping", e)
            return null
        }
    }

    // ============================================================================================
    // MARKER DETECTION FUNCTIONS (Search ±2 chars max, stop at word boundaries)
    // ============================================================================================

    private data class Marker(val type: String, val position: Int, val length: Int)

    /**
     * Find opening marker IMMEDIATELY before start (max 2 chars)
     * Stops at: newlines, word characters
     */
    private fun findOpeningMarkerBefore(raw: String, start: Int): Marker? {
        if (start <= 0) return null

        // Check position -1 and -2 only
        for (distance in 1..2) {
            val pos = start - distance
            if (pos < 0) break

            val char = raw[pos]

            // Stop at newline
            if (char == '\n' || char == '\r') break

            // Stop at word character (marker belongs to different word)
            if (char.isLetterOrDigit()) break

            // Check for ** (2 chars)
            if (distance == 2 && pos >= 1 && raw[pos] == '*' && raw[pos + 1] == '*') {
                return Marker("**", pos, 2)
            }

            // Check for single * (1 char)
            if (char == '*' && (pos == 0 || raw[pos - 1] != '*') &&
                (pos + 1 >= raw.length || raw[pos + 1] != '*')) {
                return Marker("*", pos, 1)
            }

            // Check for _
            if (char == '_') {
                return Marker("_", pos, 1)
            }

            // Check for `
            if (char == '`' && (pos == 0 || raw[pos - 1] != '`')) {
                return Marker("`", pos, 1)
            }

            // Check for ~~ (2 chars)
            if (distance == 2 && pos >= 1 && raw[pos] == '~' && raw[pos + 1] == '~') {
                return Marker("~~", pos, 2)
            }
        }

        return null
    }

    /**
     * Find closing marker IMMEDIATELY after end (max 2 chars)
     * Stops at: newlines, word characters
     */
    private fun findClosingMarkerAfter(raw: String, end: Int): Marker? {
        if (end >= raw.length) return null

        // Check position +0, +1, +2 only
        for (distance in 0..2) {
            val pos = end + distance
            if (pos >= raw.length) break

            val char = raw[pos]

            // Stop at newline
            if (char == '\n' || char == '\r') break

            // Stop at word character
            if (char.isLetterOrDigit()) break

            // Check for ** (starting at this position)
            if (pos + 1 < raw.length && raw[pos] == '*' && raw[pos + 1] == '*') {
                return Marker("**", pos, 2)
            }

            // Check for single *
            if (char == '*' && (pos == 0 || raw[pos - 1] != '*') &&
                (pos + 1 >= raw.length || raw[pos + 1] != '*')) {
                return Marker("*", pos, 1)
            }

            // Check for _
            if (char == '_') {
                return Marker("_", pos, 1)
            }

            // Check for `
            if (char == '`' && (pos + 1 >= raw.length || raw[pos + 1] != '`')) {
                return Marker("`", pos, 1)
            }

            // Check for ~~
            if (pos + 1 < raw.length && raw[pos] == '~' && raw[pos + 1] == '~') {
                return Marker("~~", pos, 2)
            }
        }

        return null
    }

    /**
     * Find closing marker INSIDE extracted text
     * Indicates selection crosses formatting boundary
     */
    private fun findClosingMarkerInside(text: String): Marker? {
        // Look for closing markers (word before, space/end after)

        // Check for **
        var index = text.indexOf("**")
        while (index != -1) {
            val hasContentBefore = index > 0 && text[index - 1].isLetterOrDigit()
            val noWordAfter = index + 2 >= text.length || !text[index + 2].isLetterOrDigit()

            if (hasContentBefore && noWordAfter) {
                return Marker("**", index, 2)
            }

            index = text.indexOf("**", index + 1)
        }

        // Check for single *
        index = 0
        while (index < text.length) {
            if (text[index] == '*' &&
                (index == 0 || text[index - 1] != '*') &&
                (index + 1 >= text.length || text[index + 1] != '*')) {

                val hasContentBefore = index > 0 && text[index - 1].isLetterOrDigit()
                val noWordAfter = index + 1 >= text.length || !text[index + 1].isLetterOrDigit()

                if (hasContentBefore && noWordAfter) {
                    return Marker("*", index, 1)
                }
            }
            index++
        }

        // Check for _ (closing)
        index = text.lastIndexOf("_")
        if (index > 0 && text[index - 1].isLetterOrDigit()) {
            return Marker("_", index, 1)
        }

        // Check for ` (closing)
        index = text.lastIndexOf("`")
        if (index > 0 && text[index - 1] != '`') {
            return Marker("`", index, 1)
        }

        // Check for ~~
        index = text.lastIndexOf("~~")
        if (index > 0 && text[index - 1].isLetterOrDigit()) {
            return Marker("~~", index, 2)
        }

        return null
    }

    // ============================================================================================
    // BULLET POINT HANDLING
    // ============================================================================================

    private fun handleBulletPoint(
        raw: String,
        start: Int,
        extractedText: String,
        selectedText: String
    ): String? {
        // Find line start
        var lineStart = start
        while (lineStart > 0 && raw[lineStart - 1] != '\n') {
            lineStart--
        }

        // Get prefix
        val prefix = raw.substring(lineStart, start)

        // Check for bullet marker
        if (!prefix.matches(Regex("^\\s*([-*+]|\\d+\\.)\\s+$"))) {
            return null
        }

        // Don't include bullet for single words
        if (!selectedText.contains(" ") && selectedText.length < 15) {
            return null
        }

        // Include bullet only for multi-word selections
        return prefix + extractedText
    }

    // ============================================================================================
    // VALIDATION (with normalization)
    // ============================================================================================

    private fun validateTextMatch(result: String, selectedText: String): Boolean {
        // Strip markdown and normalize whitespace
        val strippedResult = result
            .replace(Regex("[*_`~\\-+]|\\d+\\."), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .lowercase()

        val strippedSelection = selectedText
            .replace(Regex("\\s+"), " ")
            .trim()
            .lowercase()

        Log.d("MarkdownMapper", "🔍 Validation: '$strippedResult' vs '$strippedSelection'")

        // Check containment
        if (strippedResult.contains(strippedSelection) ||
            strippedSelection.contains(strippedResult)) {
            return true
        }

        // Calculate similarity for edge cases
        val similarity = calculateSimilarity(strippedResult, strippedSelection)
        Log.d("MarkdownMapper", "📊 Similarity: $similarity")

        return similarity > 0.85
    }

    private fun calculateSimilarity(s1: String, s2: String): Double {
        if (s1 == s2) return 1.0
        if (s1.isEmpty() || s2.isEmpty()) return 0.0

        val shorter = if (s1.length < s2.length) s1 else s2
        val longer = if (s1.length >= s2.length) s1 else s2
        val common = shorter.count { it in longer }

        return common.toDouble() / longer.length
    }

    // ============================================================================================
    // STRATEGY 2: TEXT SEARCH FALLBACK
    // ============================================================================================

    private fun tryTextSearch(
        rawMarkdown: String,  // Already normalized
        selectedText: String,
        renderedPosition: Int,
        renderedLength: Int
    ): String? {
        val cleaned = selectedText.trim()
        if (cleaned.isEmpty()) return null

        // Calculate approximate position
        val ratio = renderedPosition.toFloat() / renderedLength
        val approxPos = (ratio * rawMarkdown.length).toInt()

        // Search near position (±150 chars)
        val nearbyResult = searchNearPosition(rawMarkdown, cleaned, approxPos, 150)
        if (nearbyResult != null) return nearbyResult

        // Exact match anywhere
        val exactIndex = rawMarkdown.indexOf(cleaned)
        if (exactIndex != -1) {
            return applyFormattingLogic(rawMarkdown, exactIndex, exactIndex + cleaned.length, cleaned)
        }

        return null
    }

    private fun searchNearPosition(
        text: String,
        searchText: String,
        center: Int,
        radius: Int
    ): String? {
        val start = (center - radius).coerceAtLeast(0)
        val end = (center + radius).coerceAtMost(text.length)
        val window = text.substring(start, end)

        val localIndex = window.indexOf(searchText)
        if (localIndex != -1) {
            val actualIndex = start + localIndex
            return applyFormattingLogic(text, actualIndex, actualIndex + searchText.length, searchText)
        }

        return null
    }

    private fun applyFormattingLogic(
        raw: String,
        start: Int,
        end: Int,
        selectedText: String
    ): String {
        val (wordStart, wordEnd) = expandToWholeWord(raw, start, end)
        val result = raw.substring(wordStart, wordEnd)

        val openingMarker = findOpeningMarkerBefore(raw, wordStart)
        val closingMarker = findClosingMarkerAfter(raw, wordEnd)
        val insideMarker = findClosingMarkerInside(result)

        // Apply same logic
        return when {
            insideMarker != null -> result

            openingMarker != null && closingMarker != null &&
                    openingMarker.type == closingMarker.type -> {
                val withMarkers = raw.substring(
                    openingMarker.position,
                    closingMarker.position + closingMarker.length
                )
                if (validateTextMatch(withMarkers, selectedText)) withMarkers else result
            }

            else -> result
        }
    }

    // ============================================================================================
    // EMOJI-SAFE NORMALIZATION
    // ============================================================================================

    /**
     * Normalize text while preserving emoji
     * Emoji are in the range U+1F300 to U+1F9FF (and other ranges)
     * We skip normalization for emoji to prevent corruption
     */
    private fun normalizePreservingEmoji(text: String): String {
        if (text.isEmpty()) return text

        val result = StringBuilder()
        var i = 0

        while (i < text.length) {
            val codePoint = text.codePointAt(i)
            val charCount = Character.charCount(codePoint)

            // Check if this is an emoji or special character
            if (isEmoji(codePoint)) {
                // Preserve emoji as-is (don't normalize)
                result.appendCodePoint(codePoint)
            } else {
                // Normalize regular text
                val char = text.substring(i, i + charCount)
                val normalized = Normalizer.normalize(char, Normalizer.Form.NFC)
                result.append(normalized)
            }

            i += charCount
        }

        return result.toString()
    }

    /**
     * Check if a codepoint is an emoji or special symbol
     */
    private fun isEmoji(codePoint: Int): Boolean {
        return when (codePoint) {
            // Emoji ranges
            in 0x1F300..0x1F9FF -> true  // Miscellaneous Symbols and Pictographs, Emoticons, etc.
            in 0x2600..0x26FF -> true    // Miscellaneous Symbols (☀️, ⭐, etc.)
            in 0x2700..0x27BF -> true    // Dingbats
            in 0xFE00..0xFE0F -> true    // Variation Selectors
            in 0x1F000..0x1F02F -> true  // Mahjong Tiles, Domino Tiles
            in 0x1F0A0..0x1F0FF -> true  // Playing Cards
            in 0x1F100..0x1F64F -> true  // Enclosed Characters, Emoticons
            in 0x1F680..0x1F6FF -> true  // Transport and Map Symbols
            in 0x1F700..0x1F77F -> true  // Alchemical Symbols
            in 0x1F780..0x1F7FF -> true  // Geometric Shapes Extended
            in 0x1F800..0x1F8FF -> true  // Supplemental Arrows-C
            in 0x1F900..0x1F9FF -> true  // Supplemental Symbols and Pictographs
            in 0x1FA00..0x1FA6F -> true  // Chess Symbols
            in 0x1FA70..0x1FAFF -> true  // Symbols and Pictographs Extended-A
            in 0xE0020..0xE007F -> true  // Tags
            in 0x200D..0x200D -> true    // Zero Width Joiner (for composite emoji)
            else -> false
        }
    }

    // ============================================================================================
    // INDEX MAPPING & UTILITIES
    // ============================================================================================

    private fun buildIndexMap(
        renderedText: String,  // Already normalized
        rawMarkdown: String    // Already normalized
    ): IntArray {
        val map = IntArray(renderedText.length + 1)
        var rawIndex = 0
        var renderedIndex = 0

        Log.d("MarkdownMapper", "🔨 Building index map: rendered=${renderedText.length}, raw=${rawMarkdown.length}")

        while (renderedIndex < renderedText.length && rawIndex < rawMarkdown.length) {
            map[renderedIndex] = rawIndex

            val renderedChar = renderedText[renderedIndex]
            val rawChar = rawMarkdown[rawIndex]

            // Debug log every 100 chars
            if (renderedIndex % 100 == 0) {
                Log.d("MarkdownMapper", "  Map[$renderedIndex] = $rawIndex, renderedChar='$renderedChar', rawChar='$rawChar'")
            }

            if (renderedChar == rawChar) {
                // Characters match - advance both
                renderedIndex++
                rawIndex++
            } else {
                // Characters don't match - try to skip markdown in raw
                val skipped = skipMarkdownMarkers(rawMarkdown, rawIndex)
                if (skipped > 0) {
                    // Found markdown marker - skip it in raw
                    rawIndex += skipped
                } else {
                    // Not a markdown marker - try to find this char ahead in raw
                    val found = rawMarkdown.indexOf(renderedChar, rawIndex)
                    if (found != -1 && found < rawIndex + 20) {  // Increased search distance
                        // Found it nearby - skip to it
                        rawIndex = found
                    } else {
                        // Can't find it - this rendered char might not exist in raw
                        // Skip this rendered char
                        Log.w("MarkdownMapper", "  ⚠️ Can't find '$renderedChar' in raw at index $rawIndex")
                        renderedIndex++
                    }
                }
            }
        }

        // Fill remaining positions
        while (renderedIndex <= renderedText.length) {
            map[renderedIndex] = rawIndex.coerceAtMost(rawMarkdown.length)
            renderedIndex++
        }

        Log.d("MarkdownMapper", "✅ Index map built: last position map[${renderedText.length}] = $rawIndex")

        return map
    }

    private fun skipMarkdownMarkers(raw: String, index: Int): Int {
        if (index >= raw.length) return 0

        return when {
            // Bold **
            index + 1 < raw.length && raw[index] == '*' && raw[index + 1] == '*' -> 2
            // Single * (but not part of **)
            raw[index] == '*' &&
                    (index == 0 || raw[index - 1] != '*') &&
                    (index + 1 >= raw.length || raw[index + 1] != '*') -> 1
            // Underscore _
            raw[index] == '_' -> 1
            // Code `
            raw[index] == '`' -> 1
            // Strikethrough ~~
            index + 1 < raw.length && raw[index] == '~' && raw[index + 1] == '~' -> 2
            // Heading markers ###
            raw[index] == '#' -> {
                var count = 0
                var i = index
                while (i < raw.length && raw[i] == '#') {
                    count++
                    i++
                }
                // Skip the # markers AND the space after
                if (i < raw.length && raw[i] == ' ') count++
                count
            }
            // List markers at start of line
            raw[index] == '-' || raw[index] == '*' || raw[index] == '+' -> {
                // Check if this is a list marker (followed by space)
                if (index + 1 < raw.length && raw[index + 1] == ' ') 2 else 0
            }
            // Numbered list (1. 2. etc)
            raw[index].isDigit() -> {
                var count = 0
                var i = index
                // Count digits
                while (i < raw.length && raw[i].isDigit()) {
                    count++
                    i++
                }
                // Check for . and space after
                if (i < raw.length && raw[i] == '.') {
                    count++  // Include the dot
                    i++
                    if (i < raw.length && raw[i] == ' ') {
                        count++  // Include the space
                    }
                    count
                } else {
                    0  // Not a list marker, just a number
                }
            }
            // Emoji (don't skip)
            else -> 0
        }
    }

    /**
     * Check if character is a combining character (matra, etc.)
     * Used to handle Gujarati/Hindi combining marks
     */
    private fun isCombiningChar(c: Char): Boolean {
        val type = Character.getType(c)
        return type == Character.NON_SPACING_MARK.toInt() ||
                type == Character.COMBINING_SPACING_MARK.toInt() ||
                type == Character.ENCLOSING_MARK.toInt()
    }

    private fun isWordChar(c: Char): Boolean {
        if (Character.isLetterOrDigit(c)) return true
        if (c == '_') return true

        // Support combining characters (matras, etc.)
        val type = Character.getType(c)
        return type == Character.NON_SPACING_MARK.toInt() ||
                type == Character.COMBINING_SPACING_MARK.toInt() ||
                type == Character.ENCLOSING_MARK.toInt()
    }

    private fun expandToWholeWord(text: String, start: Int, end: Int): Pair<Int, Int> {
        var newStart = start
        var newEnd = end

        // Expand left to word boundary
        while (newStart > 0 && isWordChar(text[newStart - 1])) {
            newStart--
        }

        // Expand right to word boundary
        while (newEnd < text.length && isWordChar(text[newEnd])) {
            newEnd++
        }

        return Pair(newStart, newEnd)
    }
}