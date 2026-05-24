package io.edutor.streammark.compose

import android.widget.TextView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.edutor.streammark.api.MarkdownRenderConfig
import io.edutor.streammark.api.MessageContentSegment
import io.edutor.streammark.parser.HybridStreamingParser

/**
 * Editable Markdown Renderer - Optimized for editing scenarios
 * 
 * Key differences from StreamingChatMessage:
 * - Always parses entire content (no chunking)
 * - Always uses ComposeMessageSegmentRenderer
 * - Clears and reparses on every content change
 * - Optimized for real-time editing preview
 * 
 * Use this for:
 * - Content editors (ContentEditDialog)
 * - Markdown previews in editors
 * - Any scenario where content can be edited in the middle
 * 
 * Do NOT use for:
 * - Streaming chat messages (use StreamingChatMessage instead)
 * - Large content that needs incremental parsing
 */
@Composable
fun EditableMarkdownRenderer(
    content: String,
    modifier: Modifier = Modifier,
    enableLinks: Boolean = true,
    renderConfig: MarkdownRenderConfig = MarkdownRenderConfig.Default,
    suppressParagraphNewline: Boolean = false,
    suppressListNewline: Boolean = false,
    isElement: Boolean = false
) {
    val context = LocalContext.current

    // Create parser - remember it based on config only (not content)
    val hybridParser = remember(
        suppressParagraphNewline,
        suppressListNewline,
        renderConfig,
        isElement
    ) {
        HybridStreamingParser(
            context,
            suppressParagraphTrailingNewline = suppressParagraphNewline,
            suppressListTrailingNewline = suppressListNewline,
            config = renderConfig
        )
    }

    // Parse entire content on every change (no chunking)
    val spannable = remember(content) {
        // Clear parser to start fresh
        hybridParser.clear()
        
        // Process entire content as single chunk
        if (content.isNotEmpty()) {
            hybridParser.processChunk(content)
            hybridParser.finalize()
        }
        
        hybridParser.getCurrentSpannable()
    }

    // Get segments from parser
    val segments = remember(content) {
        // Segments are already updated in spannable remember block above
        hybridParser.getSegments()
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (segments.isNotEmpty()) {
            // Container-level spacing: handle vertical spacing between segments
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
            ) {
                segments.forEach { segment ->
                    // Extract raw text for this segment
                    val rawText = when (segment) {
                        is MessageContentSegment.TextSegment -> {
                            null
                        }
                        is MessageContentSegment.BlockLatexSegment -> {
                            "$$${segment.latex}$$"
                        }
                        is MessageContentSegment.TableSegment -> {
                            null // Tables don't need raw text mapping
                        }
                        is MessageContentSegment.HtmlBoxSegment -> {
                            null // HTML boxes don't need raw text mapping
                        }
                        is MessageContentSegment.ImageSegment -> {
                            null // Images don't need raw text mapping
                        }
                        else -> null

                    }
                    
                    ComposeMessageSegmentRenderer(
                        segment = segment,
                        modifier = Modifier.fillMaxWidth(),
                        enableLinks = enableLinks,
                        config = renderConfig,
                        rawText = rawText
                    )
                }
            }
        } else {
            // Fallback: single TextView when no segments (keep AndroidView for compatibility)
            AndroidView(
                factory = { ctx ->
                    TextView(ctx).apply {
                        setTextIsSelectable(true)
                        setTextSize(
                            android.util.TypedValue.COMPLEX_UNIT_SP,
                            renderConfig.plainTextSize
                        )
                        setPadding(
                            renderConfig.paddingHorizontal,
                            renderConfig.paddingVertical,
                            renderConfig.paddingHorizontal,
                            renderConfig.paddingVertical
                        )
                        setTextColor(renderConfig.textColor)
                        setLineSpacing(
                            renderConfig.lineSpacing,
                            renderConfig.lineSpacingMultiplier
                        )
                        isFocusable = true
                        isFocusableInTouchMode = true
                        if (enableLinks) {
                            movementMethod =
                                android.text.method.LinkMovementMethod.getInstance()
                        } else {
                            movementMethod = null
                            isClickable = false
                            isLongClickable = false
                        }
                    }
                },
                update = { textView ->
                    textView.text = spannable
                },
                modifier = modifier.fillMaxWidth()
            )
        }
    }
}

