package dev.whekin.whfin.core.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
private fun WhfinSavingsChartsPreviewContent() {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Savings pace", style = MaterialTheme.typography.titleMedium)
            WhfinSavingsPaceChart(
                bars = listOf(
                    WhfinSavingsPaceBar("Jan", 650L, "650 GEL", "January"),
                    WhfinSavingsPaceBar("Feb", -180L, "−180 GEL", "February"),
                    WhfinSavingsPaceBar("Mar", null, "No data", "March"),
                    WhfinSavingsPaceBar("Apr", 1_050L, "1,050 GEL", "April", selected = true),
                    WhfinSavingsPaceBar("May", 780L, "780 GEL", "May"),
                    WhfinSavingsPaceBar("Jun", 0L, "0 GEL", "June"),
                    WhfinSavingsPaceBar("Jul", -420L, "−420 GEL", "July"),
                    WhfinSavingsPaceBar("Aug", 920L, "920 GEL", "August"),
                    WhfinSavingsPaceBar("Sep", 1_140L, "1,140 GEL", "September"),
                    WhfinSavingsPaceBar("Oct", 560L, "560 GEL", "October"),
                    WhfinSavingsPaceBar("Nov", 0L, "0 GEL", "November"),
                    WhfinSavingsPaceBar("Dec", null, "No data", "December"),
                ),
                targetMinor = 1_000L,
                targetDescription = "target 1,000 GEL",
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Reserve balance", style = MaterialTheme.typography.titleMedium)
            WhfinSavingsBalanceChart(
                points = listOf(
                    WhfinSavingsBalancePoint("Jan", 3_200L, "3,200 GEL", "January"),
                    WhfinSavingsBalancePoint("Feb", 3_780L, "3,780 GEL", "February"),
                    WhfinSavingsBalancePoint("Mar", 3_600L, "3,600 GEL", "March"),
                    WhfinSavingsBalancePoint("Apr", 4_650L, "4,650 GEL", "April"),
                    WhfinSavingsBalancePoint("May", 5_430L, "5,430 GEL", "May"),
                    WhfinSavingsBalancePoint("Jun", 5_430L, "5,430 GEL", "June"),
                    WhfinSavingsBalancePoint("Jul", 5_010L, "5,010 GEL", "July"),
                    WhfinSavingsBalancePoint("Aug", 5_930L, "5,930 GEL", "August"),
                    WhfinSavingsBalancePoint("Sep", 6_870L, "6,870 GEL", "September"),
                    WhfinSavingsBalancePoint("Oct", 7_430L, "7,430 GEL", "October"),
                    WhfinSavingsBalancePoint("Nov", 7_430L, "7,430 GEL", "November"),
                    WhfinSavingsBalancePoint("Dec", 7_980L, "7,980 GEL", "December"),
                ),
                goalMinor = 7_500L,
                goalDescription = "Goal 7,500 GEL",
                contentDescription = "Reserve balance from January 3,200 GEL to December 7,980 GEL; goal 7,500 GEL",
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Preview(name = "Savings charts light", widthDp = 400, heightDp = 620, showBackground = true)
@Preview(name = "Savings charts dark", widthDp = 400, heightDp = 620, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Savings charts font 1.5", widthDp = 400, heightDp = 820, fontScale = 1.5f, showBackground = true)
@Composable
private fun WhfinSavingsChartsPreview() {
    WhfinTheme { WhfinSavingsChartsPreviewContent() }
}
