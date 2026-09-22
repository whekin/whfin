package dev.whekin.whfin.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import dev.whekin.whfin.ui.theme.WhfinTheme

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
class DockQaActivity : ComponentActivity() {
    var selectedTab = 0
    var addRequests = 0
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        check(android.os.Build.HARDWARE in setOf("ranchu", "goldfish"))
        enableEdgeToEdge()
        setContent { WhfinTheme(darkTheme = intent.getBooleanExtra("dark", false)) {
            var selection by remember { mutableIntStateOf(0) }
            Surface(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
                    Box(Modifier.align(Alignment.BottomCenter)) {
                        LedgerDock(selection.toFloat(), { addRequests++ }) { selection = it; selectedTab = it }
                    }
                }
            }
        } }
    }
}
