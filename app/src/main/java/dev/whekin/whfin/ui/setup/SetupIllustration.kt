package dev.whekin.whfin.ui.setup

import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.whekin.whfin.core.ui.WhfinIllustration
import dev.whekin.whfin.core.ui.WhfinIllustrationScene

/** Decoration gives reading and actions priority on short screens and at large text sizes. */
@Composable
internal fun SetupIllustration(scene: WhfinIllustrationScene) {
    val compact = LocalConfiguration.current.screenHeightDp < 700 || LocalDensity.current.fontScale >= 1.3f
    WhfinIllustration(scene, Modifier.height(if (compact) 64.dp else 112.dp))
}

@androidx.compose.ui.tooling.preview.Preview(name = "Categories light", widthDp = 400, heightDp = 850)
@androidx.compose.ui.tooling.preview.Preview(name = "Categories dark", widthDp = 400, heightDp = 850,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@androidx.compose.ui.tooling.preview.Preview(name = "Categories RU compact", widthDp = 360, heightDp = 560,
    locale = "ru", fontScale = 1.5f)
@Composable
private fun CategoriesIllustrationPreview() = dev.whekin.whfin.ui.theme.WhfinTheme {
    CategorySetupStep(proposals = emptyList(), onAccept = {}, onContinue = {}, onBack = {})
}

@androidx.compose.ui.tooling.preview.Preview(name = "Ready light", widthDp = 400, heightDp = 850)
@androidx.compose.ui.tooling.preview.Preview(name = "Ready dark", widthDp = 400, heightDp = 850,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@androidx.compose.ui.tooling.preview.Preview(name = "Ready RU compact", widthDp = 360, heightDp = 560,
    locale = "ru", fontScale = 1.5f)
@Composable
private fun ReadyIllustrationPreview() = dev.whekin.whfin.ui.theme.WhfinTheme {
    SetupStageScreen(SetupStage.Ready, emptyList(), {}, {})
}
