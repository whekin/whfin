package dev.whekin.whfin.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertIsDisplayed
import dev.whekin.whfin.ui.theme.WhfinTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import androidx.compose.foundation.clickable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.performClick

/**
 * What moves when a destination changes, and what does not.
 *
 * These run with the animation clock held still, because the question is about the middle of a
 * 200ms transition and nothing else can see it: the emulator's screen recorder returns an empty
 * stream, and screenshots taken a few hundred milliseconds apart land either side of the movement.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ShellFrameTest {
    @get:Rule
    val compose = createComposeRule()

    private val home = ShellTarget(ShellScene.Home)
    private val accounts = ShellTarget(ShellScene.Accounts)
    private val analytics = ShellTarget(ShellScene.Analytics)
    private val settings = ShellTarget(ShellScene.Settings)

    @Test
    fun theDockStandsStillAndStaysSingleWhileRootsChange() {
        var target by mutableStateOf(home)
        var selected = -1
        compose.setContent {
            WhfinTheme {
                ShellFrame(target = target, dockSelection = 0f, onAdd = {}, onSelectRoot = { selected = it }) {
                    Text(it.scene.name, Modifier.fillMaxSize().testTag("pane-${it.scene.name}"))
                }
            }
        }
        val resting = compose.onNodeWithTag("dock-feed").getUnclippedBoundsInRoot()

        compose.mainClock.autoAdvance = false
        target = analytics
        // Through the whole transition, not only at its ends: the defect was two docks sliding past
        // each other in opposite directions, which both endpoints look innocent of.
        repeat(20) {
            compose.mainClock.advanceTimeBy(16)
            compose.onAllNodesWithTag("dock-feed").assertCountEquals(1)
            assertEquals(resting, compose.onNodeWithTag("dock-feed").getUnclippedBoundsInRoot())
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals(resting, compose.onNodeWithTag("dock-feed").getUnclippedBoundsInRoot())
        assertEquals(-1, selected)
    }

    @Test
    fun rootsChangeWithoutTravellingSideways() {
        var target by mutableStateOf(home)
        compose.setContent {
            WhfinTheme {
                ShellFrame(target = target, dockSelection = 0f, onAdd = {}, onSelectRoot = {}) {
                    Box(Modifier.fillMaxSize().testTag("pane-${it.scene.name}")) { Text(it.scene.name) }
                }
            }
        }
        val restingPane = compose.onNodeWithTag("pane-Home").getUnclippedBoundsInRoot()

        compose.mainClock.autoAdvance = false
        target = accounts
        repeat(12) {
            compose.mainClock.advanceTimeBy(16)
            // A change of subject fades; it does not travel. Both panes keep the frame's own left
            // edge, so nothing slides under the dock while the dock stays put.
            compose.onAllNodesWithTag("pane-Home").fetchSemanticsNodes().forEach { node ->
                assertEquals(restingPane.left, compose.onNodeWithTag("pane-Home").getUnclippedBoundsInRoot().left)
            }
            compose.onAllNodesWithTag("pane-Accounts").fetchSemanticsNodes().forEach { _ ->
                assertEquals(restingPane.left, compose.onNodeWithTag("pane-Accounts").getUnclippedBoundsInRoot().left)
            }
        }
    }

    @Test
    fun anInterruptedSwitchSettlesOnTheDestinationLastAskedFor() {
        var target by mutableStateOf(home)
        compose.setContent {
            WhfinTheme {
                ShellFrame(target = target, dockSelection = 0f, onAdd = {}, onSelectRoot = {}) {
                    Text(it.scene.name, Modifier.testTag("pane-${it.scene.name}"))
                }
            }
        }

        compose.mainClock.autoAdvance = false
        target = accounts
        compose.mainClock.advanceTimeBy(48)
        // Switched again before the first transition finished, the way an impatient tap does.
        target = analytics
        compose.mainClock.advanceTimeBy(48)
        target = home
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()

        compose.onNodeWithTag("pane-Home").assertIsDisplayed()
        compose.onAllNodesWithTag("pane-Accounts").assertCountEquals(0)
        compose.onAllNodesWithTag("pane-Analytics").assertCountEquals(0)
        compose.onAllNodesWithTag("dock-feed").assertCountEquals(1)
    }

    @Test
    fun aNestedSceneTakesTheDockWithItAndGivesItBack() {
        var target by mutableStateOf(home)
        compose.setContent {
            WhfinTheme {
                ShellFrame(target = target, dockSelection = 0f, onAdd = {}, onSelectRoot = {}) {
                    Column(Modifier.fillMaxSize()) {
                        Text(it.scene.name, Modifier.fillMaxWidth().testTag("pane-${it.scene.name}"))
                    }
                }
            }
        }
        val withDock = compose.onNodeWithTag("pane-Home").getUnclippedBoundsInRoot()

        target = settings
        compose.waitForIdle()
        compose.onAllNodesWithTag("dock-feed").assertCountEquals(0)

        target = home
        compose.waitForIdle()
        compose.onAllNodesWithTag("dock-feed").assertCountEquals(1)
        // Returning restores the room the dock takes rather than leaving the pane a dock shorter.
        assertEquals(withDock, compose.onNodeWithTag("pane-Home").getUnclippedBoundsInRoot())
        compose.onNodeWithTag("pane-Home").assertIsDisplayed()
    }

    @Test
    fun eachRootKeepsItsOwnStateAcrossASwitch() {
        var target by mutableStateOf(home)
        compose.setContent {
            WhfinTheme {
                ShellFrame(target = target, dockSelection = 0f, onAdd = {}, onSelectRoot = {}) { shell ->
                    // Stands in for a typed search, a chosen period, a scroll position: anything a
                    // root remembers while its reader looks at another one.
                    var typed by rememberSaveable { mutableStateOf("") }
                    Column {
                        Text(typed, Modifier.testTag("typed-${shell.scene.name}"))
                        Text(
                            "type",
                            Modifier.testTag("type-${shell.scene.name}").clickable { typed += shell.scene.name.first() },
                        )
                    }
                }
            }
        }

        compose.onNodeWithTag("type-Home").performClick()
        compose.onNodeWithTag("typed-Home").assertTextEquals("H")

        target = accounts
        compose.waitForIdle()
        // A different root starts empty rather than inheriting the one before it.
        compose.onNodeWithTag("typed-Accounts").assertTextEquals("")
        compose.onNodeWithTag("type-Accounts").performClick()

        target = home
        compose.waitForIdle()
        compose.onNodeWithTag("typed-Home").assertTextEquals("H")

        target = accounts
        compose.waitForIdle()
        compose.onNodeWithTag("typed-Accounts").assertTextEquals("A")
    }
}
