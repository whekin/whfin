package dev.whekin.whfin.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import dev.whekin.whfin.core.ui.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RedesignControlsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun switch_exposesState_andDisabledSwitchDoesNotChange() {
        var changes = 0
        compose.setContent {
            WhfinTheme {
                Column {
                    WhfinSwitch(false, { changes++ }, "Enabled")
                    WhfinSwitch(true, { changes++ }, "Disabled", enabled = false)
                }
            }
        }
        compose.onNodeWithContentDescription("Enabled").assertIsOff().performClick()
        compose.onNodeWithContentDescription("Disabled").assertIsOn().performClick()
        assertEquals(1, changes)
    }

    @Test fun exclusiveChoice_exposesSelection_andKeepsLongLabelsActionable() {
        var chosen = ""
        compose.setContent {
            WhfinTheme {
                WhfinSegmentedChoice(
                    options = listOf(
                        WhfinChoice("system", "Follow the system appearance"),
                        WhfinChoice("light", "Always use the light appearance"),
                    ),
                    selected = "system",
                    onSelect = { chosen = it },
                    modifier = Modifier.width(240.dp),
                )
            }
        }
        compose.onNodeWithText("Follow the system appearance").assertIsSelected()
        compose.onNodeWithText("Always use the light appearance").performClick()
        assertEquals("light", chosen)
    }

    @Test fun semanticTextColors_remainReadableInBothThemes() {
        for (scheme in listOf(WhfinLightColorScheme, WhfinDarkColorScheme)) {
            for (background in listOf(scheme.background, scheme.surfaceContainerLowest, scheme.surfaceContainerLow)) {
                for (foreground in listOf(scheme.onSurface, scheme.onSurfaceVariant, scheme.primary, scheme.tertiary, scheme.error)) {
                    assertContrast(foreground, background)
                }
            }
            assertContrast(scheme.onPrimary, scheme.primary)
            assertContrast(scheme.onPrimaryContainer, scheme.primaryContainer)
        }
    }

    private fun assertContrast(foreground: Color, background: Color) {
        val a = foreground.luminance()
        val b = background.luminance()
        val ratio = (maxOf(a, b) + .05f) / (minOf(a, b) + .05f)
        assertTrue("$foreground on $background: $ratio", ratio >= 4.5f)
    }
}
