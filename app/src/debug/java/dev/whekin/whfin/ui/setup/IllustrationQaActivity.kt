package dev.whekin.whfin.ui.setup

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import dev.whekin.whfin.R
import dev.whekin.whfin.data.categorization.CategoryCatalog
import dev.whekin.whfin.data.categorization.CategoryPacks
import dev.whekin.whfin.data.categorization.CategoryProposals
import dev.whekin.whfin.ui.theme.WhfinTheme
import java.util.Locale

/** Stateless production screens with synthetic data; does not reset or enter the personal setup. */
class IllustrationQaActivity : ComponentActivity() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base.createConfigurationContext(Configuration(base.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language))
            fontScale = textScale
        }))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        check(android.os.Build.HARDWARE in setOf("ranchu", "goldfish"))
        enableEdgeToEdge()
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = !dark
        setContent {
            WhfinTheme(darkTheme = dark, dynamicColor = dynamic) {
                var page by remember { mutableStateOf(intent.getStringExtra("page") ?: "welcome") }
                when (page) {
                    "welcome" -> WelcomeChoiceScreen(false, null, { page = "categories" }, { page = "categories" }, ::finish)
                    "categories" -> {
                        var proposals by remember { mutableStateOf(listOf(CategoryProposals.Proposal(
                            CategoryCatalog.all.single { it.icon == "PedalBike" }, 18))) }
                        var packs by remember { mutableStateOf(CategoryPacks.all.take(3)) }
                        CategorySetupStep(proposals, packs, onAccept = { proposals = emptyList() },
                            onAddPacks = { chosen -> packs = packs - chosen.toSet() },
                            onContinue = { page = "ready" }, onBack = { page = "welcome" })
                    }
                    else -> SetupStageScreen(SetupStage.Ready, emptyList(), { page = "categories" }, ::finish,
                        continueLabel = stringResource(R.string.personal_setup_continue_action),
                        content = { SetupAccountReviews(
                            SetupOverviewState.Ready(SetupOverview(emptyList(), emptyMap(), emptySet(), 0, 0, 0, 0, 0, 0)),
                            {}, { _, _, _ -> }, {},
                        ) })
                }
            }
        }
    }

    companion object {
        var language = "en"
        var textScale = 1f
        var dark = false
        var dynamic = false
    }
}
