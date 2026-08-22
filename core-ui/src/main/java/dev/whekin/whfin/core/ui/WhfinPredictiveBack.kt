package dev.whekin.whfin.core.ui

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlin.coroutines.cancellation.CancellationException

/**
 * The live state of an in-app Back gesture.
 *
 * Android answers a Back swipe before the destination changes: the page it is about to leave
 * shrinks under the finger and returns if the finger is lifted early. Inside a custom shell that
 * answer has to be drawn by the app, otherwise Back stays an invisible commit and the gesture
 * feels like it belongs to a different operating system than the one around it.
 */
@Stable
class WhfinBackGesture internal constructor() {
    internal val travel = Animatable(0f)
    internal var fromLeftEdge by mutableStateOf(true)

    /** 0 at rest, 1 when the gesture is fully pulled. Read in the draw phase, not composition. */
    val progress: Float get() = travel.value
}

/**
 * The system reports raw finger travel. Easing it here keeps the first millimetres of the pull
 * expressive and the last ones calm, which is what makes the page feel attached to the finger.
 */
private val PredictiveBackEasing = CubicBezierEasing(.1f, .1f, 0f, 1f)

private const val MaxScaleInset = .08f
private val MaxSlide = 20.dp
private val PulledCorner = 28.dp
private val PulledShadow = 8.dp

/**
 * Registers a predictive Back gesture and returns the state to draw it with.
 *
 * [onBack] runs only when the gesture is committed; an abandoned pull settles back on its own and
 * never reaches the caller.
 */
@Composable
fun rememberWhfinBackGesture(
    enabled: Boolean,
    onBack: () -> Unit,
): WhfinBackGesture {
    val gesture = remember { WhfinBackGesture() }
    val settle = WhfinMotion.standard<Float>()
    PredictiveBackHandler(enabled = enabled) { events ->
        try {
            events.collect { event ->
                gesture.fromLeftEdge = event.swipeEdge == BackEventCompat.EDGE_LEFT
                gesture.travel.snapTo(PredictiveBackEasing.transform(event.progress.coerceIn(0f, 1f)))
            }
            onBack()
            // The destination has already changed, so the same spring now brings the arriving
            // page up to full size instead of cutting to it.
            gesture.travel.animateTo(0f, settle)
        } catch (_: CancellationException) {
            gesture.travel.animateTo(0f, settle)
        }
    }
    return gesture
}

/**
 * Draws the page as the platform does during a Back pull: inset, nudged along the drag, rounded
 * and lifted just enough to read as a sheet of paper about to be turned.
 */
fun Modifier.whfinPredictiveBack(gesture: WhfinBackGesture): Modifier = graphicsLayer {
    val pulled = gesture.progress
    if (pulled <= 0f) return@graphicsLayer
    val inset = 1f - MaxScaleInset * pulled
    scaleX = inset
    scaleY = inset
    translationX = (if (gesture.fromLeftEdge) 1f else -1f) * MaxSlide.toPx() * pulled
    transformOrigin = TransformOrigin(if (gesture.fromLeftEdge) 1f else 0f, .5f)
    shape = RoundedCornerShape(PulledCorner * pulled)
    clip = true
    shadowElevation = PulledShadow.toPx() * pulled
}
