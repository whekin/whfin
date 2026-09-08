package dev.whekin.whfin.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntSize
import dev.whekin.whfin.core.ui.WhfinMotion

/**
 * The moving part of the shell: one destination at a time, and the furniture that does not move.
 *
 * The dock used to live inside the animated content, once per destination, so a change of section
 * animated two of them: for the length of the transition the app had two navigation bars, forty
 * pixels apart, sliding past each other in opposite directions and cross-fading. That is what "the
 * screens switch oddly" was — not the pages, the furniture.
 *
 * So the dock is hoisted out and there is exactly one. Only the page inside changes. The Back pull
 * still moves everything, dock included, because the gesture is applied further out: a page that
 * insets while the furniture around it stays put reads as two applications.
 *
 * It is a composable of its own so the thing that decides what travels and what stays put can be
 * driven by a test with the animation clock in hand. Watching a 200ms transition on a device does
 * not answer "did the dock move" — the emulator's screen recorder returns an empty stream, and a
 * screenshot every few hundred milliseconds lands either side of the movement.
 */
@Composable
internal fun ShellFrame(
    target: ShellTarget,
    dockSelection: Float,
    onSelectRoot: (Int) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (ShellTarget) -> Unit,
) {
    val sceneTravel = WhfinMotion.travel()
    val sceneFadeIn = WhfinMotion.standard<Float>()
    val sceneFadeOut = WhfinMotion.quick<Float>()
    val paneFadeIn = WhfinMotion.paneEnter<Float>()
    val paneFadeOut = WhfinMotion.paneExit<Float>()
    val dockReveal = WhfinMotion.screen<IntSize>()
    val rootStates = rememberSaveableStateHolder()
    Column(modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = target,
            modifier = Modifier.fillMaxWidth().weight(1f),
            transitionSpec = {
                val forward = shellTransitionIsForward(initialState, targetState)
                // Two roots are a change of subject, not a step: they fade through each other and
                // stay where they are. They used to shift an eighth of the width as well, which is
                // a push in miniature — it said a level had been entered when none had, and it slid
                // the page under a dock that was sliding the other way.
                val betweenRoots = shellTransitionIsBetweenRoots(initialState, targetState)
                if (betweenRoots) {
                    return@AnimatedContent (fadeIn(paneFadeIn) togetherWith fadeOut(paneFadeOut))
                        .using(SizeTransform(clip = false))
                }
                // A nested scene is a step, and the step has a direction. A destination's first
                // frame is expensive and a full-width push loses a visible chunk of its travel to
                // that frame, which reads as a stutter; a short directional shift under a fade keeps
                // the direction legible even when the first frames are dropped.
                val enter = fadeIn(sceneFadeIn) +
                    slideInHorizontally(sceneTravel) { width -> if (forward) width / 8 else -width / 8 }
                val exit = fadeOut(sceneFadeOut) +
                    slideOutHorizontally(sceneTravel) { width -> if (forward) -width / 8 else width / 8 }
                (enter togetherWith exit).apply {
                    targetContentZIndex = if (forward) 1f else -1f
                }.using(SizeTransform(clip = false))
            },
            label = "app-destination",
        ) { targetShell ->
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                // A root keeps its own saved state across a change of section: the search typed into
                // the record, the period chosen in analytics, where each list was scrolled to.
                // Switching is not leaving, so the holder lives with the frame rather than with any
                // one page — and it is keyed by scene, so no two roots can share a key.
                if (rootOrder(targetShell.scene) != null) {
                    rootStates.SaveableStateProvider(targetShell.scene) { content(targetShell) }
                } else {
                    content(targetShell)
                }
            }
        }
        // One dock, outside everything that animates the page. It goes away on a nested scene and
        // comes back on a root; the height it occupies grows and shrinks with it rather than
        // appearing in one frame, so the page above never jumps by a dock's worth of pixels.
        AnimatedVisibility(
            visible = rootOrder(target.scene) != null,
            enter = fadeIn(sceneFadeIn) + expandVertically(dockReveal),
            exit = fadeOut(sceneFadeOut) + shrinkVertically(dockReveal),
            label = "app-dock",
        ) {
            LedgerDock(
                selection = dockSelection,
                onAdd = onAdd,
                onSelect = onSelectRoot,
            )
        }
    }
}
