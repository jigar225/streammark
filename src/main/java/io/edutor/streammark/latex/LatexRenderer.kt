package io.edutor.streammark.latex

import android.content.Context
import android.graphics.Canvas
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import ru.noties.jlatexmath.JLatexMathDrawable

/**
 * Custom View for rendering LaTeX math expressions using Android-compatible fork
 */
class LatexView(context: Context) : View(context) {

    var latex: String = ""
        set(value) {
            field = value
            if (value.isNotEmpty()) {
                try {
                    drawable = JLatexMathDrawable.builder(value)
                        .textSize(textSize)
                        .padding(8)
                        .align(alignment)
                        .build()
                    requestLayout()
                    invalidate()
                } catch (_: Exception) {
                    try {
                        drawable = JLatexMathDrawable.builder("\\text{Error: Invalid LaTeX}")
                            .textSize(textSize)
                            .padding(2)
                            .align(alignment)
                            .build()
                        requestLayout()
                        invalidate()
                    } catch (_: Exception) {
                    }
                }
            }
        }

    private var textSize: Float = 48f
    private var alignment: Int = JLatexMathDrawable.ALIGN_CENTER

    var drawable: JLatexMathDrawable = JLatexMathDrawable.builder("")
        .textSize(textSize)
        .padding(2)
        .align(JLatexMathDrawable.ALIGN_CENTER)
        .build()

    fun setTextColor(color: Int) {
    }

    fun setTextSize(sizePx: Float) {
        // Interpret incoming size as px for consistency with callers
        textSize = sizePx
        if (latex.isNotEmpty()) {
            try {
                drawable = JLatexMathDrawable.builder(latex)
                    .textSize(textSize)
                    .padding(8)
                    .align(alignment)
                    .build()
                requestLayout()
                invalidate()
            } catch (e: Exception) {
                // Handle size change errors
            }
        }
    }

    fun setAlignment(align: Int) {
        alignment = align
        if (latex.isNotEmpty()) {
            try {
                drawable = JLatexMathDrawable.builder(latex)
                    .textSize(textSize)
                    .padding(8)
                    .align(alignment)
                    .build()
                requestLayout()
                invalidate()
            } catch (_: Exception) {
                // Handle alignment change errors
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = drawable.intrinsicWidth
        val height = drawable.intrinsicHeight
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        try {
            drawable.setBounds(0, 0, width, height)
            drawable.draw(canvas)
        } catch (e: Exception) {
        }
    }
}

/**
 * Compose wrapper for JLaTeXMath
 */
@Composable
fun JLatexMath(
    latex: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Black,
    sizeSp: Float = 48f, // legacy param, treated as dp below
    isInline: Boolean = false,
    align: Int = JLatexMathDrawable.ALIGN_CENTER
) {
    AndroidView(
        modifier = modifier,
        factory = { context -> LatexView(context) },
        update = { view ->
            val density = view.resources.displayMetrics.density
            val baseDp = if (isInline) sizeSp else sizeSp * 1.5f
            view.setTextSize(baseDp * density)
            view.setAlignment(align)
            view.latex = latex
            view.setTextColor(color.toArgb())
        }
    )
}

