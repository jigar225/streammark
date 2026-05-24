package io.edutor.streammark.api

import android.graphics.Color

/**
 * Configuration for StreamMark rendering styles.
 */
data class StreamMarkConfig(
    val plainTextSize: Float = 17f,
    val inlineLatexTextSize: Float = 17f,
    val blockLatexTextSize: Float = 20f,
    val tableLatexTextSize: Float = 20f,
    val bulletTextSize: Float = 13f,
    val heading1Size: Float = 2.0f,
    val heading2Size: Float = 1.5f,
    val heading3Size: Float = 1.17f,
    val heading4Size: Float = 1.0f,
    val heading5Size: Float = 0.83f,
    val heading6Size: Float = 0.67f,
    val lineSpacing: Float = 0f,
    val lineSpacingMultiplier: Float = 1.5f,
    val paddingDefault: Int = 16,
    val paddingVertical: Int = 6,
    val paddingHorizontal: Int = 16,
    val bulletIndentSpaces: Int = 4,
    val bulletTextGapSpaces: Int = 2,
    val bulletSizeMultiplier: Float = 1.2f,
    val bulletGapDp: Float = 8f,
    val textColor: Int = Color.BLACK,
    val linkColor: Int = Color.BLUE,
    val tableHeaderTextColor: Int = 0xFF5F259F.toInt(),
    val tableHeaderBgColor: Int = 0xFFEDEEFA.toInt(),
    val tableAltRowBgColor: Int = 0xFFF1F0F7.toInt(),
    val tableDividerColor: Int = 0xFFD5D2F4.toInt(),
    val quoteSpanColor: Int = 0xFFD0D0D0.toInt(),
    val quoteStripeWidthDp: Float = 4f,
    val quoteGapWidthDp: Float = 16f,
    val quotePaddingStartDp: Float = 8f,
    val horizontalRuleColor: Int = 0xFFBDBDBD.toInt(),
    val horizontalRuleHeightDp: Float = 1f,
    val horizontalRulePaddingDp: Float = 8f,
) {
    fun getHeadingSize(level: Int): Float {
        return when (level) {
            1 -> heading1Size
            2 -> heading2Size
            3 -> heading3Size
            4 -> heading4Size
            5 -> heading5Size
            6 -> heading6Size
            else -> 1.0f
        }
    }

    companion object {
        val Default = StreamMarkConfig()
    }
}

/** @deprecated Use [StreamMarkConfig] */
@Deprecated("Renamed to StreamMarkConfig", ReplaceWith("StreamMarkConfig"))
typealias MarkdownRenderConfig = StreamMarkConfig
