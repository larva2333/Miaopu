package dev.kiritoxd.miaopu.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetScheduleSizingTest {
    @Test fun `default and compact heights keep their existing row counts`() {
        assertEquals(1, WidgetScheduleSizing.visibleRows(110, compact = true, largeFont = false))
        assertEquals(2, WidgetScheduleSizing.visibleRows(140, compact = true, largeFont = false))
        assertEquals(3, WidgetScheduleSizing.visibleRows(180, compact = false, largeFont = false))
        assertEquals(1, WidgetScheduleSizing.visibleRows(140, compact = true, largeFont = true))
        assertEquals(2, WidgetScheduleSizing.visibleRows(180, compact = true, largeFont = true))
    }

    @Test fun `taller hosts expose progressively more rows`() {
        val counts = listOf(180, 220, 250, 280, 310, 340)
            .map { WidgetScheduleSizing.visibleRows(it, compact = false, largeFont = false) }

        assertEquals(listOf(3, 4, 5, 6, 7, 8), counts)
    }

    @Test fun `large fonts add rows as height grows and stay capped`() {
        assertEquals(4, WidgetScheduleSizing.visibleRows(260, compact = true, largeFont = true))
        assertEquals(8, WidgetScheduleSizing.visibleRows(1_000, compact = false, largeFont = false))
    }
}
