package io.edutor.streammark.spans

import android.graphics.Canvas
import android.graphics.Paint
import android.text.style.ReplacementSpan

/**
 * Draws a tight, padded highlight only around the span text.
 */
class InlineHighlightSpan(
    private val backgroundColor: Int,
    private val paddingPx: Int,
) : ReplacementSpan() {

    override fun getSize(
        paint: Paint,
        text: CharSequence,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?,
    ): Int {
        val spanText = text.subSequence(start, end).toString()
        val textWidth = paint.measureText(spanText)
        return (textWidth + 2 * paddingPx).toInt().coerceAtLeast(0)
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint,
    ) {
        if (start >= end) return

        val spanText = text.subSequence(start, end).toString()
        val textWidth = paint.measureText(spanText)

        val fm = paint.fontMetrics
        val ascent = fm.ascent
        val descent = fm.descent

        val rawTop = y + ascent
        val rawBottom = y + descent

        val verticalInset = (paddingPx.coerceAtLeast(2) / 2f)

        val rectLeft = x
        val rectRight = x + textWidth + 2 * paddingPx
        val rectTop = rawTop - verticalInset
        val rectBottom = rawBottom + verticalInset

        val originalColor = paint.color
        paint.color = backgroundColor

        canvas.drawRoundRect(
            rectLeft,
            rectTop,
            rectRight,
            rectBottom,
            verticalInset,
            verticalInset,
            paint,
        )

        paint.color = originalColor

        canvas.drawText(
            spanText,
            x + paddingPx,
            y.toFloat(),
            paint,
        )
    }
}
