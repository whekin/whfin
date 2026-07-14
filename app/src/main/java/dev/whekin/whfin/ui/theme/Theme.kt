package dev.whekin.whfin.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.whekin.whfin.data.preferences.AppThemeMode

@Composable
fun WhfinTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    themeMode: AppThemeMode? = null,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val useDark = when (themeMode) {
        AppThemeMode.Light -> false
        AppThemeMode.Dark -> true
        AppThemeMode.System, null -> darkTheme
    }
    val context = LocalContext.current
    val dynamicScheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (useDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else null
    dev.whekin.whfin.core.ui.WhfinTheme(
        darkTheme = useDark,
        colorScheme = dynamicScheme,
        content = content,
    )
}
