package io.edutor.streammark.api

import android.content.Context
import android.widget.TextView
import android.text.SpannableStringBuilder

/**
 * Temporary stub for MarkdownHelper to allow compilation
 * This will be replaced by UniversalMarkdownRenderer
 */
class MarkdownHelper(private val context: Context) {
    
    /**
     * Temporary stub method - returns plain text
     */
    fun setMarkdown(textView: TextView, markdown: String) {
        textView.text = markdown
    }
    
    /**
     * Temporary stub method - returns plain text as SpannableStringBuilder
     */
    fun getSpanned(markdown: String): SpannableStringBuilder {
        return SpannableStringBuilder(markdown)
    }
}
