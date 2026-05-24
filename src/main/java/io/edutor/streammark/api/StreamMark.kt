package io.edutor.streammark.api

import androidx.annotation.ColorInt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import io.edutor.streammark.compose.ComposeMessageSegmentRenderer
import io.edutor.streammark.parser.HybridStreamingParser
import io.edutor.streammark.ui.MarkdownStyle
import io.edutor.streammark.ui.MarkdownTextRenderer

data class StreamMarkSegmentScope(
    val segment: MessageContentSegment,
    val modifier: Modifier,
    val enableLinks: Boolean,
    val config: MarkdownRenderConfig,
    val originalContent: String,
    val isUserMessage: Boolean,
)

/**
 * Main StreamMark composable — pass markdown text + [isStreaming] flag.
 * Override [segmentRenderer] for custom per-segment UI (Edutor Chapter AI uses this).
 */
@Composable
fun StreamMark(
    markdown: String,
    isStreaming: Boolean,
    modifier: Modifier = Modifier,
    isUserMessage: Boolean = false,
    markdownHelper: MarkdownHelper? = null,
    useUniversalRenderer: Boolean = false,
    style: MarkdownStyle = MarkdownStyle(),
    onClick: (() -> Unit)? = null,
    suppressParagraphNewline: Boolean = false,
    suppressListNewline: Boolean = false,
    enableLinks: Boolean = true,
    isTextOperationEnabled: Boolean = false,
    @ColorInt textColor: Int = android.graphics.Color.BLACK,
    config: StreamMarkConfig = StreamMarkConfig.Default,
    onTextSelected: ((String?, String, Int, () -> Unit) -> Unit)? = null,
    onSelectionDismissed: (() -> Unit)? = null,
    segmentRenderer: @Composable (StreamMarkSegmentScope) -> Unit = { scope ->
        ComposeMessageSegmentRenderer(
            segment = scope.segment,
            modifier = scope.modifier,
            enableLinks = scope.enableLinks,
            config = scope.config,
        )
    },
) {
    val context = LocalContext.current

    val hybridParser = remember(
        useUniversalRenderer,
        suppressParagraphNewline,
        suppressListNewline,
        config,
    ) {
        HybridStreamingParser(
            context,
            suppressParagraphTrailingNewline = suppressParagraphNewline,
            suppressListTrailingNewline = suppressListNewline,
            config = config,
        )
    }
    val lastProcessedLength = remember { mutableIntStateOf(0) }

    val spannable = remember(key1 = markdown, key2 = isStreaming) {
        if (markdown.length < lastProcessedLength.value) {
            hybridParser.clear()
            lastProcessedLength.value = 0
        }
        val newChunk = markdown.substring(lastProcessedLength.value)
        if (newChunk.isNotEmpty()) {
            hybridParser.processChunk(newChunk)
        }
        if (!isStreaming && markdown.isNotEmpty()) {
            hybridParser.finalize()
        }
        lastProcessedLength.value = markdown.length
        hybridParser.getCurrentSpannable()
    }

    val segments = remember(key1 = markdown, key2 = isStreaming) {
        hybridParser.getSegments()
    }

    val columnModifier = if (isUserMessage) {
        modifier.wrapContentSize(align = Alignment.Center)
    } else {
        modifier.fillMaxWidth()
    }

    Column(modifier = columnModifier) {
        when {
            useUniversalRenderer -> {
                if (segments.isNotEmpty()) {
                    Column(
                        modifier = Modifier,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        segments.forEach { segment ->
                            val segmentModifier = if (isUserMessage) {
                                Modifier.wrapContentSize(align = Alignment.Center)
                            } else {
                                Modifier.fillMaxWidth()
                            }

                            segmentRenderer(
                                StreamMarkSegmentScope(
                                    segment = segment,
                                    modifier = segmentModifier,
                                    enableLinks = enableLinks,
                                    config = config,
                                    originalContent = markdown,
                                    isUserMessage = isUserMessage,
                                ),
                            )
                        }
                    }
                } else {
                    AndroidView(
                        factory = { ctx ->
                            object : androidx.appcompat.widget.AppCompatTextView(ctx) {
                                private var pendingSelectedText: String? = null

                                override fun onSelectionChanged(selStart: Int, selEnd: Int) {
                                    super.onSelectionChanged(selStart, selEnd)
                                    if (text != null && selStart >= 0 && selEnd > selStart) {
                                        pendingSelectedText = text.subSequence(selStart, selEnd).toString()
                                    } else {
                                        pendingSelectedText = null
                                    }
                                }

                                override fun onTouchEvent(event: android.view.MotionEvent?): Boolean {
                                    val result = super.onTouchEvent(event)
                                    if (event?.action == android.view.MotionEvent.ACTION_UP) {
                                        pendingSelectedText?.let { selected ->
                                            if (selectionStart >= 0 && selectionEnd > selectionStart) {
                                                val location = IntArray(2)
                                                getLocationOnScreen(location)
                                                val selectionY = location[1] + (event.y.toInt())

                                                onTextSelected?.invoke(selected, selected, selectionY) {
                                                    clearFocus()
                                                }
                                            }
                                        }
                                    }
                                    return result
                                }
                            }.apply {
                                setTextIsSelectable(true)
                                setTextSize(
                                    android.util.TypedValue.COMPLEX_UNIT_SP,
                                    config.plainTextSize,
                                )
                                setPadding(
                                    config.paddingHorizontal,
                                    config.paddingVertical,
                                    config.paddingHorizontal,
                                    config.paddingVertical,
                                )
                                setTextColor(config.textColor)
                                setLineSpacing(
                                    config.lineSpacing,
                                    config.lineSpacingMultiplier,
                                )

                                isFocusable = true
                                isFocusableInTouchMode = true

                                if (enableLinks) {
                                    movementMethod = android.text.method.LinkMovementMethod.getInstance()
                                } else {
                                    movementMethod = null
                                    isClickable = true
                                    isLongClickable = true
                                }

                                if (isUserMessage) {
                                    layoutParams = android.view.ViewGroup.LayoutParams(
                                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                                    )
                                }

                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                    setTextClassifier(android.view.textclassifier.TextClassifier.NO_OP)
                                }
                            }
                        },
                        update = { textView ->
                            textView.text = spannable
                        },
                        modifier = if (isUserMessage) {
                            Modifier.wrapContentSize(align = Alignment.Center)
                        } else {
                            Modifier.fillMaxWidth()
                        },
                    )
                }
            }

            markdownHelper != null -> {
                MarkdownTextRenderer(
                    modifier = modifier,
                    markdownText = markdown,
                    markdownHelper = markdownHelper,
                    style = style.copy(lineHeight = 90),
                    onClick = onClick,
                )
            }

            else -> {
                AndroidView(
                    factory = { ctx ->
                        object : androidx.appcompat.widget.AppCompatTextView(ctx) {
                            private var pendingSelectedText: String? = null

                            override fun onSelectionChanged(selStart: Int, selEnd: Int) {
                                super.onSelectionChanged(selStart, selEnd)
                                if (text != null && selStart >= 0 && selEnd > selStart) {
                                    pendingSelectedText = text.subSequence(selStart, selEnd).toString()
                                } else {
                                    pendingSelectedText = null
                                }
                            }

                            override fun onTouchEvent(event: android.view.MotionEvent?): Boolean {
                                val result = super.onTouchEvent(event)
                                if (event?.action == android.view.MotionEvent.ACTION_UP) {
                                    pendingSelectedText?.let { selected ->
                                        if (selectionStart >= 0 && selectionEnd > selectionStart) {
                                            val location = IntArray(2)
                                            getLocationOnScreen(location)
                                            val selectionY = location[1] + (event.y.toInt())

                                            onTextSelected?.invoke(selected, selected, selectionY) {
                                                clearFocus()
                                            }
                                        }
                                    }
                                }
                                return result
                            }
                        }.apply {
                            setTextIsSelectable(true)
                            textSize = style.textSize
                            setTextColor(ContextCompat.getColor(ctx, style.textColorRes))

                            isFocusable = true
                            isFocusableInTouchMode = true
                            isClickable = true
                            isLongClickable = true

                            if (isUserMessage) {
                                layoutParams = android.view.ViewGroup.LayoutParams(
                                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                                )
                            }

                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                setTextClassifier(android.view.textclassifier.TextClassifier.NO_OP)
                            }
                        }
                    },
                    update = { textView ->
                        textView.text = markdown
                    },
                    modifier = if (isUserMessage) {
                        Modifier.wrapContentWidth(align = Alignment.End)
                    } else {
                        modifier
                    },
                )
            }
        }
    }
}
