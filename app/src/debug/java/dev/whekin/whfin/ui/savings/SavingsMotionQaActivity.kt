package dev.whekin.whfin.ui.savings

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.whekin.whfin.core.ui.WhfinButton
import dev.whekin.whfin.data.db.SavingsPlanEntity
import dev.whekin.whfin.ui.theme.WhfinTheme
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

/** Synthetic-only forecast and real editor; never writes a plan to Room. */
class SavingsMotionQaActivity : ComponentActivity() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base.createConfigurationContext(Configuration(base.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language)); fontScale = textScale
        }))
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        check(android.os.Build.HARDWARE in setOf("ranchu", "goldfish"))
        enableEdgeToEdge()
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = !dark
        setContent {
            WhfinTheme(darkTheme = dark, dynamicColor = dynamic) {
                Surface(Modifier.fillMaxSize()) {
                    var editor by remember { mutableStateOf(false) }
                    var pace by remember { mutableLongStateOf(100_000) }
                    var goal by remember { mutableStateOf<Long?>(3_000_000) }
                    var date by remember { mutableStateOf<LocalDate?>(today.plusMonths(10)) }
                    if (editor) SavingsPlanEditorContent(
                        SavingsPlanEntity(currency = "GEL", monthlyTargetMinor = pace, goalMinor = goal,
                            goalBy = date?.toEpochDay(), startedOn = today.toEpochDay(), createdAt = 0),
                        "GEL", 1_800_000, today, { editor = false },
                        { monthly, amount, deadline -> pace = monthly; goal = amount; date = deadline; editor = false }, null,
                    ) else Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        WhfinButton(if (language == "ru") "Изменить план" else "Edit plan", { editor = true })
                        SavingsProjectionPanel(1_800_000, pace, "GEL", goal, date, today,
                            history = (1..3).map { SavingsMonthUi(YearMonth.from(today).minusMonths((4-it).toLong()),
                                1_400_000L + it * 100_000, 100_000, 100_000) })
                    }
                }
            }
        }
    }
    companion object {
        val today: LocalDate = LocalDate.of(2026, 9, 29)
        var language = "en"
        var textScale = 1f
        var dark = false
        var dynamic = false
    }
}
