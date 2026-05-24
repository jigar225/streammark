package io.edutor.streammark.ui

import android.view.View

data class MarkdownStyle(
    val textSize: Float = 18f,
    val textColorRes: Int = android.R.color.black,
    val textAlignment: Int = View.TEXT_ALIGNMENT_INHERIT,
    val lineHeight: Int? = null,
)
