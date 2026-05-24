package io.edutor.streammark.parser

import io.edutor.streammark.api.HtmlBoxStyles
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Parser for inline CSS styles from HTML style attributes.
 * Handles common CSS properties with fault-tolerant parsing.
 */
object CssStyleParser {
    /**
     * Parse a CSS style string (e.g., "background-color: #2196F3; padding: 12px; border-radius: 8px")
     * Returns HtmlBoxStyles with parsed values or safe defaults.
     */
    fun parse(styleString: String): HtmlBoxStyles {
        // Default values
        var bg = Color.Transparent
        var borderCol = Color.Transparent
        var borderW = 0.dp
        var radius = 0.dp
        var pad = 0.dp
        var margin = 0.dp
        var marginTop = 0.dp
        var marginBottom = 0.dp
        var marginLeft = 0.dp
        var marginRight = 0.dp
        var textCol = Color.Unspecified
        var fSize: Float? = null
        var fontWeight: FontWeight? = null
        var textAlign: TextAlign? = null
        var textDecoration: TextDecoration? = null

        try {
            // Split by ';' to get individual rules
            val rules = styleString.split(";")
            
            rules.forEach { rule ->
                // Split by ':' to get key and value
                val parts = rule.split(":", limit = 2)
                if (parts.size == 2) {
                    val key = parts[0].trim().lowercase()
                    val value = parts[1].trim()

                    when (key) {
                        "background-color" -> bg = parseColor(value)
                        "color" -> textCol = parseColor(value)
                        "padding" -> pad = parseDimension(value)
                        "border-radius" -> radius = parseDimension(value)
                        "margin" -> {
                            // Parse margin shorthand: "10px", "10px 5px", "10px 5px 8px", "10px 5px 8px 12px"
                            val margins = parseMarginShorthand(value)
                            margin = margins[0]
                            marginTop = margins[0]
                            marginRight = margins.getOrNull(1) ?: margins[0]
                            marginBottom = margins.getOrNull(2) ?: margins[0]
                            marginLeft = margins.getOrNull(3) ?: margins[0]
                        }
                        "margin-top" -> marginTop = parseDimension(value)
                        "margin-bottom" -> marginBottom = parseDimension(value)
                        "margin-left" -> marginLeft = parseDimension(value)
                        "margin-right" -> marginRight = parseDimension(value)
                        "font-size" -> {
                            // Extract numeric value (handles "24px", "1.5em", etc.)
                            val numericValue = value.replace(Regex("[^0-9.]"), "").toFloatOrNull()
                            fSize = numericValue
                        }
                        "font-weight" -> {
                            fontWeight = when {
                                value.contains("bold", ignoreCase = true) -> FontWeight.Bold
                                value.contains("normal", ignoreCase = true) -> FontWeight.Normal
                                value.matches(Regex("\\d+")) -> {
                                    val weight = value.toIntOrNull() ?: 400
                                    when {
                                        weight >= 700 -> FontWeight.Bold
                                        weight >= 500 -> FontWeight.Medium
                                        else -> FontWeight.Normal
                                    }
                                }
                                else -> null
                            }
                        }
                        "text-align" -> {
                            textAlign = when (value.lowercase().trim()) {
                                "left" -> TextAlign.Left
                                "center" -> TextAlign.Center
                                "right" -> TextAlign.Right
                                "justify" -> TextAlign.Justify
                                "start" -> TextAlign.Start
                                "end" -> TextAlign.End
                                else -> null
                            }
                        }
                        "text-decoration" -> {
                            textDecoration = when {
                                value.contains("underline", ignoreCase = true) -> TextDecoration.Underline
                                value.contains("line-through", ignoreCase = true) -> TextDecoration.LineThrough
                                value.contains("none", ignoreCase = true) -> TextDecoration.None
                                // Handle multiple values like "underline line-through"
                                value.contains("underline", ignoreCase = true) && value.contains("line-through", ignoreCase = true) -> {
                                    TextDecoration.combine(listOf(TextDecoration.Underline, TextDecoration.LineThrough))
                                }
                                else -> null
                            }
                        }
                        
                        // Handle shorthand border: "2px solid #2196F3"
                        "border" -> {
                            val borderParts = value.split(" ")
                            borderParts.forEach { part ->
                                val trimmedPart = part.trim()
                                if (trimmedPart.endsWith("px") || trimmedPart.endsWith("dp")) {
                                    borderW = parseDimension(trimmedPart)
                                } else if (trimmedPart.startsWith("#") || trimmedPart.startsWith("rgb")) {
                                    borderCol = parseColor(trimmedPart)
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Log error if needed, but return safe defaults so app doesn't crash
            android.util.Log.w("CssStyleParser", "Error parsing CSS: $styleString", e)
        }

        return HtmlBoxStyles(
            backgroundColor = bg,
            borderColor = borderCol,
            borderWidth = borderW,
            borderRadius = radius,
            padding = pad,
            margin = margin,
            marginTop = marginTop,
            marginBottom = marginBottom,
            marginLeft = marginLeft,
            marginRight = marginRight,
            textColor = textCol,
            fontSize = fSize,
            fontWeight = fontWeight,
            textAlign = textAlign,
            textDecoration = textDecoration
        )
    }

    /**
     * Parse a dimension value (e.g., "12px", "8dp") to Dp
     */
    private fun parseDimension(dim: String): Dp {
        return dim.replace(Regex("[^0-9.]"), "").toFloatOrNull()?.dp ?: 0.dp
    }

    /**
     * Parse margin shorthand value (e.g., "10px", "10px 5px", "10px 5px 8px", "10px 5px 8px 12px")
     * Returns list of Dp values: [top, right, bottom, left]
     */
    private fun parseMarginShorthand(value: String): List<Dp> {
        val values = value.trim().split(Regex("\\s+")).map { parseDimension(it) }
        return when (values.size) {
            1 -> listOf(values[0], values[0], values[0], values[0]) // all sides
            2 -> listOf(values[0], values[1], values[0], values[1]) // vertical, horizontal
            3 -> listOf(values[0], values[1], values[2], values[1]) // top, horizontal, bottom
            4 -> values // top, right, bottom, left
            else -> listOf(0.dp, 0.dp, 0.dp, 0.dp) // default
        }
    }

    /**
     * Parse a color value (hex, rgb, rgba, or named colors)
     */
    private fun parseColor(hexOrName: String): Color {
        return try {
            // Handle standard Hex (#RRGGBB or #AARRGGBB)
            if (hexOrName.startsWith("#")) {
                Color(android.graphics.Color.parseColor(hexOrName))
            } 
            // Handle rgb/rgba
            else if (hexOrName.startsWith("rgb", ignoreCase = true)) {
                parseRgbColor(hexOrName)
            }
            // Fallback for basic named colors
            else {
                parseNamedColor(hexOrName.lowercase())
            }
        } catch (e: Exception) {
            Color.Unspecified
        }
    }

    /**
     * Parse rgb/rgba color string (e.g., "rgb(33, 150, 243)" or "rgba(33, 150, 243, 0.5)")
     */
    private fun parseRgbColor(rgbString: String): Color {
        return try {
            val regex = Regex("""rgba?\((\d+),\s*(\d+),\s*(\d+)(?:,\s*([\d.]+))?\)""", RegexOption.IGNORE_CASE)
            val match = regex.find(rgbString)
            if (match != null) {
                val r = match.groupValues[1].toInt().coerceIn(0, 255)
                val g = match.groupValues[2].toInt().coerceIn(0, 255)
                val b = match.groupValues[3].toInt().coerceIn(0, 255)
                val a = match.groupValues.getOrNull(4)?.toFloatOrNull()?.coerceIn(0f, 1f) ?: 1f
                
                Color(
                    red = r / 255f,
                    green = g / 255f,
                    blue = b / 255f,
                    alpha = a
                )
            } else {
                Color.Unspecified
            }
        } catch (e: Exception) {
            Color.Unspecified
        }
    }

    /**
     * Parse named color (e.g., "red", "blue")
     */
    private fun parseNamedColor(colorName: String): Color {
        return when (colorName) {
            "red" -> Color.Red
            "blue" -> Color.Blue
            "green" -> Color.Green
            "black" -> Color.Black
            "white" -> Color.White
            "yellow" -> Color.Yellow
            "cyan" -> Color.Cyan
            "magenta" -> Color.Magenta
            "gray", "grey" -> Color.Gray
            "transparent" -> Color.Transparent
            else -> Color.Unspecified
        }
    }
}

