package dev.whekin.whfin.ui.feed

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import dev.whekin.whfin.R
import dev.whekin.whfin.core.ui.WhfinIconButton
import dev.whekin.whfin.core.ui.WhfinMotion
import dev.whekin.whfin.data.sync.BankSyncStatus
import dev.whekin.whfin.data.sync.SyncPhase

internal enum class BankSyncIndicatorState { Idle, Active, Complete, Attention }

internal fun bankSyncIndicatorState(statuses: List<BankSyncStatus>): BankSyncIndicatorState = when {
    statuses.any { it.active } -> BankSyncIndicatorState.Active
    statuses.isEmpty() -> BankSyncIndicatorState.Idle
    statuses.all { it.phase == SyncPhase.COMPLETE } -> BankSyncIndicatorState.Complete
    else -> BankSyncIndicatorState.Attention
}

@Composable
internal fun BankSyncIndicator(statuses: List<BankSyncStatus>, onClick: () -> Unit) {
    val state = bankSyncIndicatorState(statuses)
    val fade = WhfinMotion.quick<Float>()
    // Mounting an already-completed result is static; only an observed transition crossfades.
    AnimatedContent(state, transitionSpec = { fadeIn(fade) togetherWith fadeOut(fade) }, label = "bank-sync-result") { shown ->
        val angle = if (shown == BankSyncIndicatorState.Active) {
            val transition = rememberInfiniteTransition(label = "bank-sync")
            transition.animateFloat(0f, 360f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "sync-rotation")
        } else rememberUpdatedState(0f)
        WhfinIconButton(icon = when (shown) {
            BankSyncIndicatorState.Complete -> Icons.Default.Check
            BankSyncIndicatorState.Attention -> Icons.Outlined.ErrorOutline
            else -> Icons.Default.Sync
        }, onClick = onClick, outlined = false,
            modifier = Modifier.graphicsLayer { rotationZ = angle.value },
            contentDescription = stringResource(when (shown) {
                BankSyncIndicatorState.Active -> R.string.bank_sync_running
                BankSyncIndicatorState.Complete -> R.string.bank_sync_complete
                BankSyncIndicatorState.Attention -> R.string.bank_sync_attention
                BankSyncIndicatorState.Idle -> R.string.bank_sync_title
            }))
    }
}
