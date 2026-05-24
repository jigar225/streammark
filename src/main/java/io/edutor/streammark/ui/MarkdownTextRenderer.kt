package io.edutor.streammark.ui

import android.text.method.LinkMovementMethod
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import io.edutor.streammark.api.MarkdownHelper

@Composable
fun MarkdownTextRenderer(
    markdownText: String,
    modifier: Modifier = Modifier,
    markdownHelper: MarkdownHelper,
    style: MarkdownStyle = MarkdownStyle(),
    onClick: (() -> Unit)? = null
) {
    val rememberedMarkdownText = remember(markdownText) { markdownText } // Remember content to avoid unnecessary updates

    AndroidView(
        modifier = modifier,
        factory = { context ->
            TextView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                textSize = style.textSize
                style.lineHeight?.let {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                        lineHeight = it
                    } else {
                        // For API < 28, use setLineSpacing as alternative
                        setLineSpacing(0f, (it / textSize))
                    }
                } ?: run {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                        lineHeight = 50
                    } else {
                        setLineSpacing(0f, (50f / textSize))
                    }
                }
                includeFontPadding = false
                setTextColor(ContextCompat.getColor(context, style.textColorRes))
                textAlignment = style.textAlignment
                movementMethod = LinkMovementMethod.getInstance() // Make links clickable
                isClickable = onClick != null
            }
        },
        update = { view ->
            style.lineHeight?.let { desiredLineHeight ->
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    if (view.lineHeight != desiredLineHeight) {
                        view.lineHeight = desiredLineHeight
                    }
                } else {
                    val spacingMultiplier = desiredLineHeight / view.textSize
                    view.setLineSpacing(0f, spacingMultiplier)
                }
            } ?: run {
                val defaultLineHeight = 50
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    if (view.lineHeight != defaultLineHeight) {
                        view.lineHeight = defaultLineHeight
                    }
                } else {
                    val spacingMultiplier = defaultLineHeight / view.textSize
                    view.setLineSpacing(0f, spacingMultiplier)
                }
            }
            if(view.textSize != style.textSize) {
                view.textSize = style.textSize
            }

            if(view.textAlignment != style.textAlignment) {
                view.textAlignment = style.textAlignment
            }


            // Only update if the markdown text has changed
            if (view.text.toString() != rememberedMarkdownText) {
                markdownHelper.setMarkdown(view, rememberedMarkdownText)
            }

            if (onClick != null) {
                view.setOnClickListener { onClick.invoke() }
                view.isClickable = true
            } else {
                view.setOnClickListener(null)
                view.isClickable = false
            }
        }
    )
}
