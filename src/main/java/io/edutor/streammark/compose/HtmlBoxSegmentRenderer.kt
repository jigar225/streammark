package io.edutor.streammark.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import io.edutor.streammark.api.HtmlBoxStyles
import io.edutor.streammark.api.MarkdownRenderConfig
import io.edutor.streammark.parser.HybridStreamingParser

@Composable
fun HtmlBoxSegmentRenderer(
    innerContent: String,
    styles: HtmlBoxStyles,
    modifier: Modifier = Modifier,
    config: MarkdownRenderConfig = MarkdownRenderConfig.Default,
) {
    val context = LocalContext.current

    val innerParser = remember(config) {
        HybridStreamingParser(
            context = context,
            suppressParagraphTrailingNewline = false,
            suppressListTrailingNewline = false,
            config = config,
        )
    }

    val innerSegments = remember(innerContent, config) {
        innerParser.clear()
        if (innerContent.isNotEmpty()) {
            innerParser.processChunk(innerContent)
            innerParser.finalize()
        }
        innerParser.getSegments()
    }

    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = styles.backgroundColor,
                shape = RoundedCornerShape(styles.borderRadius),
            )
            .then(
                if (styles.borderWidth.value > 0) {
                    Modifier.border(
                        width = styles.borderWidth,
                        color = styles.borderColor,
                        shape = RoundedCornerShape(styles.borderRadius),
                    )
                } else {
                    Modifier
                },
            )
            .padding(styles.padding),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            innerSegments.forEach { segment ->
                ComposeMessageSegmentRenderer(
                    segment = segment,
                    modifier = Modifier.fillMaxWidth(),
                    enableLinks = true,
                    config = config,
                )
            }
        }
    }
}
