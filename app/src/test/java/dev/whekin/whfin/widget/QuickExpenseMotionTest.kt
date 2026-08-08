package dev.whekin.whfin.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class QuickExpenseMotionTest {
    @Test
    fun `scrim follows sheet offset continuously`() {
        assertEquals(1f, sheetScrimProgress(100f, 100f, 1_000f))
        assertEquals(0.5f, sheetScrimProgress(550f, 100f, 1_000f))
        assertEquals(0f, sheetScrimProgress(1_000f, 100f, 1_000f))
    }

    @Test
    fun `scrim progress clamps overscroll and uninitialized anchors`() {
        assertEquals(1f, sheetScrimProgress(0f, 100f, 1_000f))
        assertEquals(0f, sheetScrimProgress(1_100f, 100f, 1_000f))
        assertEquals(0f, sheetScrimProgress(1_000f, 1_000f, 1_000f))
    }
}
