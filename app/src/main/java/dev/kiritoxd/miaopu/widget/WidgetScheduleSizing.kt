package dev.kiritoxd.miaopu.widget

/** Keeps the default schedule density while allowing taller hosts to reveal more matches. */
internal object WidgetScheduleSizing {
    const val MAX_SECTION_ROWS = 8
    const val MAX_VISIBLE_LOGOS = MAX_SECTION_ROWS * 4

    fun visibleRows(contentHeightDp: Int, compact: Boolean, largeFont: Boolean): Int {
        val chromeHeightDp = if (compact) 76 else 80
        val minimumRowHeightDp = if (largeFont) 38 else 30
        val rowsThatFit = ((contentHeightDp - chromeHeightDp).coerceAtLeast(0) / minimumRowHeightDp)
            .coerceIn(1, MAX_SECTION_ROWS)
        val existingRows = when {
            contentHeightDp < 130 || largeFont && contentHeightDp < 175 -> 1
            compact -> 2
            else -> 3
        }
        return maxOf(existingRows, rowsThatFit).coerceAtMost(MAX_SECTION_ROWS)
    }
}
