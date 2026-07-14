package dev.whekin.whfin.ui.theme

import androidx.compose.runtime.Composable

@Composable
fun WhfinTheme(
    darkTheme: Boolean = androidx.compose.foundation.isSystemInDarkTheme(),
    @Suppress("UNUSED_PARAMETER") dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) = dev.whekin.whfin.core.ui.WhfinTheme(darkTheme = darkTheme, content = content)
