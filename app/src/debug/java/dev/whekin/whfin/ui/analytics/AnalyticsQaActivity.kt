package dev.whekin.whfin.ui.analytics

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import dev.whekin.whfin.ui.theme.WhfinTheme
import java.util.Locale

/**
 * Debug-only, non-exported render host for the analytics screens.
 *
 * Synthetic values only; it never opens or modifies a database. It exists because the states that
 * matter most here are the ones a populated demo cannot show — no recorded history, a baseline that
 * is genuinely zero, a month that came in under the ordinary level — and a branch that only exists
 * in code has not been seen.
 */
class AnalyticsQaActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dark = intent.getBooleanExtra("dark", false)
        val language = intent.getStringExtra("language") ?: "en"
        Locale.setDefault(Locale.forLanguageTag(language))
        val fontScale = intent.getFloatExtra("fontScale", 1f)
        val shape = AnalyticsScenario.Shape.valueOf(
            intent.getStringExtra("shape") ?: AnalyticsScenario.Shape.DEARER.name,
        )
        val running = intent.getBooleanExtra("running", false)
        val expenses = intent.getBooleanExtra("expenses", false)
        val style = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        val configuration = Configuration(resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language))
        }
        val context = createConfigurationContext(configuration)
        val today = if (running) AnalyticsScenario.insideSelectedMonth
        else AnalyticsScenario.afterSelectedMonth
        val data = AnalyticsScenario.analytics(shape, today = today)
        val state = if (data.hasAnyTransactions) AnalyticsUiState.Content(data) else AnalyticsUiState.Empty
        val model = AnalyticsUiModel(
            period = AnalyticsPeriod.month(AnalyticsScenario.selectedMonth),
            canSelectPrevious = true,
            canSelectNext = true,
            state = state,
        )
        setContent {
            CompositionLocalProvider(
                LocalContext provides context,
                LocalConfiguration provides configuration,
                LocalDensity provides Density(
                    LocalDensity.current.density,
                    fontScale = fontScale,
                ),
            ) {
                WhfinTheme(darkTheme = dark) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        if (expenses) ExpenseAnalysisContent(
                            model = model,
                            onBack = {},
                            onPreviousPeriod = {},
                            onNextPeriod = {},
                            onScaleChange = {},
                            onSelectMonth = {},
                            onShowAllTrend = {},
                            onShowCategoryTrend = {},
                            onOpenTransactions = {},
                        ) else AnalyticsContent(
                            model = model,
                            onBack = null,
                            onPreviousPeriod = {},
                            onNextPeriod = {},
                            onScaleChange = {},
                            onSelectMonth = {},
                            onShowAllTrend = {},
                            onOpenExpenses = {},
                            onOpenTransactions = {},
                        )
                    }
                }
            }
        }
    }
}
