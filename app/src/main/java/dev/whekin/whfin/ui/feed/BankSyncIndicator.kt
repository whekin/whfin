package dev.whekin.whfin.ui.feed

import androidx.compose.animation.core.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import dev.whekin.whfin.R
import dev.whekin.whfin.core.ui.WhfinIconButton

@Composable
internal fun BankSyncIndicator(active: Boolean, onClick: () -> Unit) {
    val angle = if (active) {
        val transition = rememberInfiniteTransition(label = "bank-sync")
        val rotation by transition.animateFloat(0f, 360f,
            infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "sync-rotation")
        rotation
    } else 0f
    WhfinIconButton(icon = Icons.Default.Sync, onClick = onClick, outlined = false,
        modifier = Modifier.graphicsLayer { rotationZ = angle },
        contentDescription = stringResource(if (active) R.string.bank_sync_running else R.string.bank_sync_title))
}
