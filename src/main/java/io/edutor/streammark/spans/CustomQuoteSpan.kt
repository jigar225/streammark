package io.edutor.streammark.spans

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.text.Layout
import android.text.style.LeadingMarginSpan

/**
 * Custom LeadingMarginSpan for block quotes with visible left stripe and proper indentation.
 */
class CustomQuoteSpan(
    private val context: Context,
    private val quoteColor: Int,
    private val stripeWidth: Int,
    private val gapWidth: Int,
    private val paddingStart: Int,
) : LeadingMarginSpan {

    private val stripePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = quoteColor
        style = Paint.Style.FILL
    }

    override fun getLeadingMargin(first: Boolean): Int {
        return stripeWidth + gapWidth + paddingStart
    }

    override fun drawLeadingMargin(
        canvas: Canvas,
        paint: Paint,
        x: Int,
        dir: Int,
        top: Int,
        baseline: Int,
        bottom: Int,
        text: CharSequence,
        start: Int,
        end: Int,
        first: Boolean,
        layout: Layout?,
    ) {
        val stripeLeft = if (dir > 0) {
            x + paddingStart
        } else {
            x - stripeWidth - paddingStart
        }

        val stripeRight = stripeLeft + stripeWidth

        canvas.drawRect(
            stripeLeft.toFloat(),
            top.toFloat(),
            stripeRight.toFloat(),
            bottom.toFloat(),
            stripePaint,
        )
    }
}
