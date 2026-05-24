package io.edutor.streammark.compose

import android.content.Context
import android.graphics.Typeface
import android.text.Spannable
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.*
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import io.edutor.streammark.R
import io.edutor.streammark.api.MarkdownRenderConfig
import io.edutor.streammark.api.MessageContentSegment
import io.edutor.streammark.api.TableCellSpannable
import io.edutor.streammark.api.TableData
import io.edutor.streammark.latex.LatexView
import ru.noties.jlatexmath.JLatexMathDrawable

/**
 * Compose-based segment renderers that match the styling of Android View renderers
 */

/**
 * Convert Spannable to AnnotatedString with inline LaTeX support
 * Note: This is a complex conversion. For now, text segments use AndroidView for 100% compatibility.
 */
private fun Spannable.toAnnotatedString(
    context: Context,
    config: MarkdownRenderConfig,
    density: androidx.compose.ui.unit.Density,
    inlineLatexMap: Map<String, String> = emptyMap()
): Pair<AnnotatedString, Map<String, InlineTextContent>> {
    val builder = AnnotatedString.Builder()
    val inlineContentMap = mutableMapOf<String, InlineTextContent>()
    
    val spannable = this
    val text = spannable.toString()
    
    // Track inline LaTeX placeholders
    val inlineLatexPlaceholders = mutableListOf<Pair<Int, String>>()
    var inlineLatexIndex = 0
    
    // Find all inline LaTeX markers (◆) and replace with placeholders
    var currentIndex = 0
    while (currentIndex < text.length) {
        val latexMarkerIndex = text.indexOf("◆", currentIndex)
        if (latexMarkerIndex == -1) {
            // No more LaTeX markers, append remaining text
            if (currentIndex < text.length) {
                appendSpannedText(
                    spannable,
                    currentIndex,
                    text.length,
                    builder,
                    config
                )
            }
            break
        }
        
        // Append text before LaTeX marker
        if (latexMarkerIndex > currentIndex) {
            appendSpannedText(
                spannable,
                currentIndex,
                latexMarkerIndex,
                builder,
                config
            )
        }
        
        // Find corresponding LaTeX content
        // Count how many ◆ we've seen so far to match with inlineLatexMap
        val latexKey = "LI:$inlineLatexIndex"
        val latexContent = inlineLatexMap[latexKey]
        
        if (latexContent != null) {
            // Create inline content ID
            val inlineContentId = "latex_inline_$inlineLatexIndex"
            
            // Create LatexView to get dimensions
            val latexView = LatexView(context).apply {
                val px = (config.inlineLatexTextSize * context.resources.displayMetrics.density)
                setTextSize(px)
                setAlignment(JLatexMathDrawable.ALIGN_CENTER)
                this.latex = latexContent
                measure(
                    View.MeasureSpec.UNSPECIFIED,
                    View.MeasureSpec.UNSPECIFIED
                )
                layout(0, 0, measuredWidth, measuredHeight)
            }
            
            val drawable = latexView.drawable
            val width = drawable?.intrinsicWidth ?: 0
            val height = drawable?.intrinsicHeight ?: 0
            
            // Create InlineTextContent
            val inlineContent = InlineTextContent(
                placeholder = Placeholder(
                    width = with(density) { width.toSp() },
                    height = with(density) { height.toSp() },
                    placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
                )
            ) {
                AndroidView(
                    factory = { ctx ->
                        LatexView(ctx).apply {
                            val px =
                                (config.inlineLatexTextSize * ctx.resources.displayMetrics.density)
                            setTextSize(px)
                            setAlignment(JLatexMathDrawable.ALIGN_CENTER)
                            this.latex = latexContent
                        }
                    },
                    modifier = Modifier.size(
                        with(density) { width.toDp() },
                        with(density) { height.toDp() }
                    )
                )
            }
            
            inlineContentMap[inlineContentId] = inlineContent
            
            // Add inline content to annotated string
            builder.appendInlineContent(inlineContentId, "◆")
            
            inlineLatexIndex++
        } else {
            // Fallback: just append the marker
            builder.append("◆")
        }
        
        currentIndex = latexMarkerIndex + 1
    }
    
    return Pair(builder.toAnnotatedString(), inlineContentMap)
}

/**
 * Append spanned text with formatting to AnnotatedString builder
 */
private fun appendSpannedText(
    spannable: Spannable,
    start: Int,
    end: Int,
    builder: AnnotatedString.Builder,
    config: MarkdownRenderConfig
) {
    if (start >= end) return
    
    val spanned = spannable as? Spanned ?: return
    
    // Get all spans in this range
    val spans = spanned.getSpans(start, end, Any::class.java)
    
    // Sort spans by priority (inner spans first)
    val sortedSpans = spans.sortedBy { spanned.getSpanStart(it) }
    
    var currentPos = start
    
    for (span in sortedSpans) {
        val spanStart = spanned.getSpanStart(span)
        val spanEnd = spanned.getSpanEnd(span)
        
        // Append text before this span
        if (spanStart > currentPos) {
            builder.append(spanned.subSequence(currentPos, spanStart))
        }
        
        // Apply span styling
        when (span) {
            is StyleSpan -> {
                when (span.style) {
                    Typeface.BOLD -> {
                        builder.pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                        builder.append(spanned.subSequence(spanStart, spanEnd))
                        builder.pop()
                    }
                    Typeface.ITALIC -> {
                        builder.pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                        builder.append(spanned.subSequence(spanStart, spanEnd))
                        builder.pop()
                    }
                    Typeface.BOLD_ITALIC -> {
                        builder.pushStyle(
                            SpanStyle(
                                fontWeight = FontWeight.Bold,
                                fontStyle = FontStyle.Italic
                            )
                        )
                        builder.append(spanned.subSequence(spanStart, spanEnd))
                        builder.pop()
                    }
                }
            }
            is RelativeSizeSpan -> {
                builder.pushStyle(
                    SpanStyle(fontSize = (config.plainTextSize * span.sizeChange).sp)
                )
                builder.append(spanned.subSequence(spanStart, spanEnd))
                builder.pop()
            }
            is URLSpan -> {
                builder.pushStyle(
                    SpanStyle(
                        color = androidx.compose.ui.graphics.Color(config.linkColor),
                        textDecoration = TextDecoration.Underline
                    )
                )
                builder.pushStringAnnotation("URL", span.url)
                builder.append(spanned.subSequence(spanStart, spanEnd))
                builder.pop()
                builder.pop()
            }
            is TypefaceSpan -> {
                if (span.family == "monospace") {
                    builder.pushStyle(SpanStyle(fontFamily = FontFamily.Monospace))
                    builder.append(spanned.subSequence(spanStart, spanEnd))
                    builder.pop()
                } else {
                    builder.append(spanned.subSequence(spanStart, spanEnd))
                }
            }
            is StrikethroughSpan -> {
                builder.pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                builder.append(spanned.subSequence(spanStart, spanEnd))
                builder.pop()
            }
            is LeadingMarginSpan -> {
                // Handle list items - apply indentation
                val indent = span.getLeadingMargin(true)
                builder.pushStyle(
                    SpanStyle(
                        // Indentation handled by LeadingMarginSpan in TextView
                        // In Compose, we'll handle this differently if needed
                    )
                )
                builder.append(spanned.subSequence(spanStart, spanEnd))
                builder.pop()
            }
            else -> {
                // For ReplacementSpan (inline LaTeX), skip - handled separately
                if (span !is ReplacementSpan) {
                    builder.append(spanned.subSequence(spanStart, spanEnd))
                }
            }
        }
        
        currentPos = spanEnd
    }
    
    // Append remaining text
    if (currentPos < end) {
        builder.append(spanned.subSequence(currentPos, end))
    }
}

/**
 * Render a text segment with Compose (supports inline LaTeX)
 */
@Composable
fun ComposeTextSegmentRenderer(
    spannable: Spannable,
    modifier: Modifier = Modifier,
    enableLinks: Boolean = true,
    config: MarkdownRenderConfig = MarkdownRenderConfig.Default,
    rawText: String? = null
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    
    // Extract inline LaTeX from spannable
    val inlineLatexMap = remember(spannable) {
        extractInlineLatexFromSpannable(spannable)
    }
    
    // Convert Spannable to AnnotatedString
    val (annotatedString, inlineContentMap) = remember(spannable, inlineLatexMap, density) {
        spannable.toAnnotatedString(context, config, density, inlineLatexMap)
    }
    
    // For now, use BasicText with AndroidView for complex Spannable support
    // This ensures 100% compatibility with current rendering
    AndroidView(
        factory = { ctx ->
            TextView(ctx).apply {
                setTextIsSelectable(true)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, config.plainTextSize)
                // Only apply horizontal padding - vertical spacing handled by container
                setPadding(
                    config.paddingHorizontal,
                    0, // No top padding
                    config.paddingHorizontal,
                    0  // No bottom padding
                )
                setTextColor(config.textColor)
                isFocusable = true
                isFocusableInTouchMode = true
                if (enableLinks) {
                    movementMethod = LinkMovementMethod.getInstance()
                } else {
                    movementMethod = null
                    isClickable = false
                    isLongClickable = false
                }
                setLineSpacing(config.lineSpacing, config.lineSpacingMultiplier)
                
                // Store raw text for selection mapping
                if (rawText != null) {
                    setTag(R.id.raw_text_tag, rawText)
                }
            }
        },
        update = { textView ->
            textView.text = spannable
            if (rawText != null) {
                textView.setTag(R.id.raw_text_tag, rawText)
            }
        },
        modifier = modifier.fillMaxWidth()
    )
}

/**
 * Extract inline LaTeX placeholders from Spannable
 */
private fun extractInlineLatexFromSpannable(spannable: Spannable): Map<String, String> {
    val map = mutableMapOf<String, String>()
    val text = spannable.toString()
    var index = 0
    
    // Find all ◆ markers and try to extract LaTeX from ReplacementSpan
    var currentIndex = 0
    while (currentIndex < text.length) {
        val markerIndex = text.indexOf("◆", currentIndex)
        if (markerIndex == -1) break
        
        // Try to get ReplacementSpan at this position
        val spans = (spannable as? Spanned)?.getSpans(markerIndex, markerIndex + 1, ReplacementSpan::class.java)
        // Note: We can't easily extract LaTeX from ReplacementSpan without the original data
        // This is a limitation - we'll need to pass inline LaTeX separately
        
        currentIndex = markerIndex + 1
        index++
    }
    
    return map
}

/**
 * Render a block LaTeX segment with horizontal scrolling
 */
@Composable
fun ComposeBlockLatexSegmentRenderer(
    latex: String,
    modifier: Modifier = Modifier,
    config: MarkdownRenderConfig = MarkdownRenderConfig.Default,
    rawText: String? = null
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scrollState = rememberScrollState()
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            // No vertical padding - spacing handled by container
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.Center
        ) {
            AndroidView(
                factory = { ctx ->
                    LatexView(ctx).apply {
                        val px = (config.blockLatexTextSize * ctx.resources.displayMetrics.density)
                        setTextSize(px)
                        setAlignment(JLatexMathDrawable.ALIGN_CENTER)
                        this.latex = latex
                        // Only apply horizontal padding - vertical spacing handled by container
                        setPadding(
                            config.paddingDefault,
                            0, // No top padding
                            config.paddingDefault,
                            0  // No bottom padding
                        )
                    }
                },
                update = { view ->
                    view.latex = latex
                },
                modifier = Modifier.wrapContentSize()
            )
        }
    }
}

/**
 * Render a table segment with horizontal scrolling
 */
@Composable
fun ComposeTableSegmentRenderer(
    tableData: TableData,
    modifier: Modifier = Modifier,
    config: MarkdownRenderConfig = MarkdownRenderConfig.Default
) {
    val scrollState = rememberScrollState()
    val columnWidth = 160.dp

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp) // Only horizontal padding - vertical spacing handled by container
            .horizontalScroll(scrollState)
    ) {
        // Header row
        if (tableData.headers.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .height(IntrinsicSize.Min)
            ) {
                tableData.headers.forEachIndexed { columnIndex, cellData ->
                    TableCell(
                        cellData = cellData,
                        isHeader = true,
                        config = config,
                        width = columnWidth,
                        columnIndex = columnIndex,
                        isLastColumn = columnIndex == tableData.headers.size - 1,
                        isLastRow = false // Header is never last row
                    )
                }
            }

            // Header divider (keep this for visual separation)
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(Color(config.tableDividerColor))
            )
        }

        // Data rows
        tableData.rows.forEachIndexed { rowIndex, rowData ->
            Row(
                modifier = Modifier
                    .height(IntrinsicSize.Min)
            ) {
                rowData.forEachIndexed { columnIndex, cellData ->
                    TableCell(
                        cellData = cellData,
                        isHeader = false,
                        config = config,
                        rowIndex = rowIndex,
                        width = columnWidth,
                        columnIndex = columnIndex,
                        isLastColumn = columnIndex == rowData.size - 1,
                        isLastRow = rowIndex == tableData.rows.size - 1
                    )
                }
            }
        }
    }
}

/**
 * Render a single table cell (may contain LaTeX)
 */
@Composable
private fun TableCell(
    cellData: TableCellSpannable,
    width: Dp,
    isHeader: Boolean,
    config: MarkdownRenderConfig,
    rowIndex: Int = -1,
    columnIndex: Int = 0,        // NEW: Add this parameter
    isLastColumn: Boolean = false, // NEW: Add this parameter
    isLastRow: Boolean = false     // NEW: Add this parameter
) {
    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            // Apply borders selectively to avoid doubling
            .border(
                width = 1.dp,
                color = Color(config.tableDividerColor),
                shape = androidx.compose.ui.graphics.RectangleShape
            )
            .then(
                // Remove right border for non-last columns (next column's left border will show)
                if (!isLastColumn) {
                    Modifier.drawBehind {
                        drawRect(
                            color = when {
                                isHeader -> Color(config.tableHeaderBgColor)
                                rowIndex % 2 == 1 -> Color(config.tableAltRowBgColor)
                                else -> androidx.compose.ui.graphics.Color.White
                            },
                            topLeft = androidx.compose.ui.geometry.Offset(size.width - 1.dp.toPx(), 0f),
                            size = androidx.compose.ui.geometry.Size(1.dp.toPx(), size.height)
                        )
                    }
                } else Modifier
            )
            .then(
                // Remove bottom border for non-last rows (next row's top border will show)
                if (!isLastRow) {
                    Modifier.drawBehind {
                        drawRect(
                            color = when {
                                isHeader -> Color(config.tableHeaderBgColor)
                                rowIndex % 2 == 1 -> Color(config.tableAltRowBgColor)
                                else -> androidx.compose.ui.graphics.Color.White
                            },
                            topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - 1.dp.toPx()),
                            size = androidx.compose.ui.geometry.Size(size.width, 1.dp.toPx())
                        )
                    }
                } else Modifier
            )
            .background(
                when {
                    isHeader -> Color(config.tableHeaderBgColor)
                    rowIndex % 2 == 1 -> Color(config.tableAltRowBgColor)
                    else -> androidx.compose.ui.graphics.Color.White
                }
            )
            .padding(8.dp),
        contentAlignment = Alignment.TopStart
    ) {
        AndroidView(
            factory = { ctx ->
                TextView(ctx).apply {
                    setTextIsSelectable(true)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, config.plainTextSize)
                    setTextColor(
                        if (isHeader) config.tableHeaderTextColor
                        else config.textColor
                    )
                    typeface = if (isHeader) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                    setPadding(0, 0, 0, 0)
                }
            },
            update = { textView ->
                textView.text = cellData.spannable
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Render image segment using Coil AsyncImage
 */
@Composable
fun ComposeImageSegmentRenderer(
    imageUrl: String,
    altText: String,
    width: Dp?,
    height: Dp?,
    modifier: Modifier = Modifier,
    config: MarkdownRenderConfig = MarkdownRenderConfig.Default
) {
    Box(
        modifier = modifier
            .then(
                if (width != null && height != null) {
                    Modifier.size(width, height)
                } else if (width != null) {
                    Modifier.width(width)
                } else if (height != null) {
                    Modifier.height(height)
                } else {
                    Modifier.fillMaxWidth()
                }
            )
            // No vertical padding - spacing handled by container
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(imageUrl)
                .crossfade(true)
                .build(),
            contentDescription = altText.ifEmpty { "Image" },
            modifier = Modifier.fillMaxWidth(),
            contentScale = androidx.compose.ui.layout.ContentScale.Fit
        )
    }
}

/**
 * Master Compose renderer that handles any segment type
 */
@Composable
fun ComposeMessageSegmentRenderer(
    segment: MessageContentSegment,
    modifier: Modifier = Modifier,
    enableLinks: Boolean = true,
    config: MarkdownRenderConfig = MarkdownRenderConfig.Default,
    rawText: String? = null
) {
    when (segment) {
        is MessageContentSegment.TextSegment -> {
            ComposeTextSegmentRenderer(
                spannable = segment.spannable,
                modifier = modifier,
                enableLinks = enableLinks,
                config = config,
                rawText = rawText
            )
        }
        is MessageContentSegment.BlockLatexSegment -> {
            val rawLatex = rawText ?: "$$${segment.latex}$$"
            ComposeBlockLatexSegmentRenderer(
                latex = segment.latex,
                modifier = modifier,
                config = config,
                rawText = rawLatex
            )
        }
        is MessageContentSegment.TableSegment -> {
            ComposeTableSegmentRenderer(
                tableData = segment.tableData,
                modifier = modifier,
                config = config
            )
        }
        is MessageContentSegment.HtmlBoxSegment -> {
            HtmlBoxSegmentRenderer(
                innerContent = segment.innerContent,
                styles = segment.styles,
                modifier = modifier,
                config = config
            )
        }
        is MessageContentSegment.ImageSegment -> {
            ComposeImageSegmentRenderer(
                imageUrl = segment.imageUrl,
                altText = segment.altText,
                width = segment.width,
                height = segment.height,
                modifier = modifier,
                config = config
            )
        }
        else -> null
    }
}

