package dev.whekin.whfin.widget

import dev.whekin.whfin.data.preferences.WidgetColorMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetColorModeTest {
    @Test
    fun systemColors_requireAndroid12OrNewer() {
        assertFalse(usesSystemWidgetColors(WidgetColorMode.System, 30))
        assertTrue(usesSystemWidgetColors(WidgetColorMode.System, 31))
    }

    @Test
    fun whfinMode_alwaysUsesBrandDayNightPalette() {
        assertFalse(usesSystemWidgetColors(WidgetColorMode.Whfin, 31))
        assertFalse(usesSystemWidgetColors(WidgetColorMode.Whfin, 10_000))
    }
}
