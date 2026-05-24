package io.edutor.streammark.extraction

import androidx.compose.ui.unit.Dp
import io.edutor.streammark.api.HtmlBoxStyles

/**
 * Data class to store extracted HTML block information
 */
data class HtmlBlockData(
    val innerContent: String,
    val styles: HtmlBoxStyles
)

/**
 * Data class to store extracted font tag information
 */
data class FontTagData(
    val color: Int,  // Android Color int
    val content: String
)

/**
 * Data class to store extracted span tag information
 */
data class SpanTagData(
    val backgroundColor: Int,  // Android Color int (or Color.TRANSPARENT)
    val padding: Int,  // Padding in pixels
    val textColor: Int? = null,  // Optional text color (for styled strong/b/em/i converted to span)
    val content: String
)

/**
 * Data class to store extracted link tag information
 */
data class LinkTagData(
    val url: String,  // URL from href attribute
    val content: String  // Link text content
)

/**
 * Data class to store extracted image tag information
 */
data class ImageTagData(
    val imageUrl: String,  // URL from src attribute
    val altText: String,   // Alt text from alt attribute
    val width: Dp?,  // Optional width
    val height: Dp?  // Optional height
)

