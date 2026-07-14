package dev.whekin.whfin.core.ui

import android.graphics.Color
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat

/**
 * Applies WHFIN's edge-to-edge system-bar contrast to a Compose [androidx.compose.ui.window.Dialog].
 * Dialogs own a separate Window and do not inherit the Activity's light/dark icon appearance.
 */
@Composable
fun WhfinDialogSystemBars(darkTheme: Boolean = isSystemInDarkTheme()) {
    val view = LocalView.current
    DisposableEffect(view, darkTheme) {
        val window = (view.parent as? DialogWindowProvider)?.window
        if (window != null) {
            @Suppress("DEPRECATION")
            run {
                window.statusBarColor = Color.TRANSPARENT
                window.navigationBarColor = Color.TRANSPARENT
            }
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
        onDispose { }
    }
}
