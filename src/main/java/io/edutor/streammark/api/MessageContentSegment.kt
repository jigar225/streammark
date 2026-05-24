package io.edutor.streammark.api

import android.text.Spannable
import android.text.SpannableStringBuilder
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Represents different types of content segments in a message.
 */
sealed class MessageContentSegment {
    data class TextSegment(
        val spannable: Spannable,
        val rawContent: String = "",
    ) : MessageContentSegment()

    data class BlockLatexSegment(
        val latex: String,
        val isBlock: Boolean = true,
    ) : MessageContentSegment()

    data class TableSegment(
        val tableData: TableData,
        val rawMarkdown: String,
    ) : MessageContentSegment()

    data class HtmlBoxSegment(
        val innerContent: String,
        val styles: HtmlBoxStyles,
    ) : MessageContentSegment()

    data class ImageSegment(
        val imageUrl: String,
        val altText: String = "",
        val width: Dp? = null,
        val height: Dp? = null,
    ) : MessageContentSegment()

    data class VisualContentSegment(
        val type: VisualContentType,
        val contentHash: String,
        val content: String,
        val rawMarkdown: String,
    ) : MessageContentSegment()
}

enum class VisualContentType {
    MINDMAP,
    CHART,
    MERMAID,
}

data class HtmlBoxStyles(
    val backgroundColor: Color = Color.Transparent,
    val borderColor: Color = Color.Transparent,
    val borderWidth: Dp = 0.dp,
    val borderRadius: Dp = 0.dp,
    val padding: Dp = 0.dp,
    val margin: Dp = 0.dp,
    val marginTop: Dp = 0.dp,
    val marginBottom: Dp = 0.dp,
    val marginLeft: Dp = 0.dp,
    val marginRight: Dp = 0.dp,
    val textColor: Color = Color.Unspecified,
    val fontSize: Float? = null,
    val fontWeight: FontWeight? = null,
    val textAlign: TextAlign? = null,
    val textDecoration: TextDecoration? = null,
)

/** Table data structure with LaTeX support. */
data class TableData(
    val headers: List<TableCellSpannable>,
    val rows: List<List<TableCellSpannable>>,
    val rawMarkdown: String,
)

/** Represents a single table cell that may contain LaTeX. */
data class TableCellSpannable(
    val spannable: SpannableStringBuilder,
)
