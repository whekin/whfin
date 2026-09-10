package dev.whekin.whfin.ui.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.whekin.whfin.ui.SecondaryPage
import dev.whekin.whfin.ui.theme.WhfinTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.res.stringResource
import dev.whekin.whfin.R

/** Disposable emulator only; instrumentation seeds synthetic journal data. */
class PushJournalQaActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        check(android.os.Build.HARDWARE in setOf("ranchu", "goldfish"))
        val dark = intent.getBooleanExtra("dark", false)
        val style = if (dark) androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            else androidx.activity.SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        setContent { WhfinTheme(darkTheme = intent.getBooleanExtra("dark", false)) { Surface {
            SecondaryPage(stringResource(R.string.push_title), { finish() }) { PushJournalRoute(false, {}) }
        } } }
    }
}
