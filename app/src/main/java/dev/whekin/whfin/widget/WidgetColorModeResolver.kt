package dev.whekin.whfin.widget

import dev.whekin.whfin.data.preferences.WidgetColorMode

private const val Android12Api = 31

internal fun usesSystemWidgetColors(mode: WidgetColorMode, sdkInt: Int): Boolean =
    mode == WidgetColorMode.System && sdkInt >= Android12Api
