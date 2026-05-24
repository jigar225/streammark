package io.edutor.streammark.spans

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.text.style.ReplacementSpan
import androidx.core.graphics.withSave
import io.edutor.streammark.api.StreamMarkConfig

/**
 * ReplacementSpan to draw a horizontal rule that spans the available width.
 */
class HorizontalRuleSpan(
    private val context: Context,
    private val config: StreamMarkConfig,
) : ReplacementSpan() {

    private val lineHeightPx: Int = dpToPx(context, config.horizontalRuleHeightDp)
    private val verticalPaddingPx: Int = dpToPx(context, config.horizontalRulePaddingDp)
    private val linePaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = lineHeightPx.toFloat().coerceAtLeast(1f)
        color = config.horizontalRuleColor
    }

    override fun getSize(
        paint: Paint,
        text: CharSequence?,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?,
    ): Int {
        fm?.let {
            val height = verticalPaddingPx * 2 + lineHeightPx
            val half = height / 2
            it.ascent = -half
            it.descent = half
            it.top = it.ascent
            it.bottom = it.descent
        }
        return 0
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
        paint: Paint,
    ) {
        val centerY = (top + bottom) / 2f
        val left = 0f
        val right = canvas.width.toFloat()
        canvas.withSave {
            drawLine(left, centerY, right, centerY, linePaint)
        }
    }

    private companion object {
        fun dpToPx(context: Context, dp: Float): Int {
            val density = context.resources.displayMetrics.density
            return (dp * density).toInt()
        }
    }
}
