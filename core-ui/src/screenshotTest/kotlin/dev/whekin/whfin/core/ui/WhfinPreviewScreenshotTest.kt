package dev.whekin.whfin.core.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest

@PreviewTest
@Preview(name = "gallery_light", widthDp = 400, heightDp = 800)
@Composable
fun galleryLightScreenshot() {
    WhfinTheme(darkTheme = false) { WhfinDesignSystemGallery() }
}

@PreviewTest
@Preview(name = "gallery_dark", widthDp = 400, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun galleryDarkScreenshot() {
    WhfinTheme(darkTheme = true) { WhfinDesignSystemGallery() }
}

@PreviewTest
@Preview(name = "gallery_font_150", widthDp = 400, heightDp = 1000, fontScale = 1.5f)
@Composable
fun galleryLargeFontScreenshot() {
    WhfinTheme(darkTheme = false) { WhfinDesignSystemGallery() }
}

@PreviewTest
@Preview(name = "typography_whfin", widthDp = 400, heightDp = 180)
@Composable
fun typographyWhfinScreenshot() {
    WhfinTheme(darkTheme = false) { WhfinTypographyGallery() }
}

@PreviewTest
@Preview(name = "typography_device", widthDp = 400, heightDp = 180)
@Composable
fun typographyDeviceScreenshot() {
    WhfinTheme(darkTheme = false, useSystemFont = true) { WhfinTypographyGallery() }
}

@PreviewTest
@Preview(name = "shell_light", widthDp = 400, heightDp = 320)
@Composable
fun shellLightScreenshot() {
    WhfinTheme(darkTheme = false) { WhfinShellChromeGallery() }
}

@PreviewTest
@Preview(name = "shell_dark", widthDp = 400, heightDp = 320, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun shellDarkScreenshot() {
    WhfinTheme(darkTheme = true) { WhfinShellChromeGallery() }
}

@PreviewTest
@Preview(name = "shell_font_150", widthDp = 400, heightDp = 380, fontScale = 1.5f)
@Composable
fun shellLargeFontScreenshot() {
    WhfinTheme(darkTheme = false) { WhfinShellChromeGallery() }
}

@PreviewTest
@Preview(name = "monthly_chart_light", widthDp = 400, heightDp = 230)
@Composable
fun monthlyChartLightScreenshot() {
    WhfinTheme(darkTheme = false) { MonthlyChartScreenshotContent() }
}

@PreviewTest
@Preview(name = "monthly_chart_dark", widthDp = 400, heightDp = 230, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun monthlyChartDarkScreenshot() {
    WhfinTheme(darkTheme = true) { MonthlyChartScreenshotContent() }
}

@PreviewTest
@Preview(name = "monthly_chart_font_150", widthDp = 400, heightDp = 280, fontScale = 1.5f)
@Composable
fun monthlyChartLargeFontScreenshot() {
    WhfinTheme(darkTheme = false) { MonthlyChartScreenshotContent() }
}

@PreviewTest
@Preview(name = "donut_chart_light", widthDp = 260, heightDp = 260)
@Composable
fun donutChartLightScreenshot() {
    WhfinTheme(darkTheme = false) { DonutChartScreenshotContent() }
}

@PreviewTest
@Preview(name = "donut_chart_dark", widthDp = 260, heightDp = 260, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun donutChartDarkScreenshot() {
    WhfinTheme(darkTheme = true) { DonutChartScreenshotContent() }
}

@Composable
private fun DonutChartScreenshotContent() {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            WhfinDonutChart(
                segments = listOf(
                    WhfinDistributionSegment(54f, WhfinThemeTokens.colors.clay),
                    WhfinDistributionSegment(24f, WhfinThemeTokens.colors.bottle),
                    WhfinDistributionSegment(14f, WhfinThemeTokens.colors.sage),
                    WhfinDistributionSegment(8f, MaterialTheme.colorScheme.secondary),
                ),
                contentDescription = "Four expense categories",
                modifier = Modifier.size(196.dp),
            )
        }
    }
}

@Composable
private fun MonthlyChartScreenshotContent() {
    Surface(color = MaterialTheme.colorScheme.background) {
        WhfinMonthlyBarChart(
            bars = listOf(72, 88, 61, 104, 96, 110, 84, 92, 0, 0, 0, 0).mapIndexed { index, value ->
                WhfinMonthlyBar(
                    label = "JFMAMJJASOND"[index].toString(),
                    value = value.toLong(),
                    amountDescription = "$value GEL",
                    selected = index == 6,
                )
            },
            modifier = Modifier.padding(20.dp),
            onBarClick = {},
        )
    }
}

@PreviewTest
@Preview(name = "savings_charts_light", widthDp = 400, heightDp = 620)
@Composable
fun savingsChartsLightScreenshot() {
    WhfinTheme(darkTheme = false) { SavingsChartsScreenshotContent() }
}

@PreviewTest
@Preview(name = "savings_forecast", widthDp = 400, heightDp = 300)
@Composable
fun savingsForecastScreenshot() {
    WhfinTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Recorded → projected", style = MaterialTheme.typography.titleMedium)
                WhfinSavingsBalanceChart(
                    points = listOf(150L, 180L, 170L, 200L, 230L, 260L, 290L).mapIndexed { index, value ->
                        WhfinSavingsBalancePoint("${index + 1}", value, "$value GEL",
                            isProjected = index > 2, position = index * 30L)
                    },
                    goalMinor = 280L,
                    goalDescription = "Goal 280 GEL",
                    selectedIndex = 5,
                )
            }
        }
    }
}

@PreviewTest
@Preview(name = "savings_charts_dark", widthDp = 400, heightDp = 620, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun savingsChartsDarkScreenshot() {
    WhfinTheme(darkTheme = true) { SavingsChartsScreenshotContent() }
}

@PreviewTest
@Preview(name = "savings_charts_font_150", widthDp = 400, heightDp = 820, fontScale = 1.5f)
@Composable
fun savingsChartsLargeFontScreenshot() {
    WhfinTheme(darkTheme = false) { SavingsChartsScreenshotContent() }
}

@Composable
private fun SavingsChartsScreenshotContent() {
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
            )
        }
    }
}

@PreviewTest
@Preview(name = "code_input_light", widthDp = 400, heightDp = 520)
@Composable
fun codeInputLightScreenshot() {
    WhfinTheme(darkTheme = false) { CodeInputScreenshotContent() }
}

@PreviewTest
@Preview(name = "code_input_dark", widthDp = 400, heightDp = 520, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun codeInputDarkScreenshot() {
    WhfinTheme(darkTheme = true) { CodeInputScreenshotContent() }
}

@PreviewTest
@Preview(name = "code_input_font_150", widthDp = 400, heightDp = 620, fontScale = 1.5f)
@Composable
fun codeInputLargeFontScreenshot() {
    WhfinTheme(darkTheme = false) { CodeInputScreenshotContent() }
}

@Composable
private fun CodeInputScreenshotContent() {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            WhfinCodeDots(length = 4, filled = 2)
            WhfinNumericKeypad(
                deleteContentDescription = "Delete digit",
                onDigit = {},
                onBackspace = {},
            )
        }
    }
}

@PreviewTest
@Preview(name = "amount_keypad_light", widthDp = 400, heightDp = 320)
@Composable
fun amountKeypadLightScreenshot() {
    WhfinTheme(darkTheme = false) { AmountKeypadScreenshotContent() }
}

@PreviewTest
@Preview(name = "amount_keypad_dark", widthDp = 400, heightDp = 320, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun amountKeypadDarkScreenshot() {
    WhfinTheme(darkTheme = true) { AmountKeypadScreenshotContent() }
}

@PreviewTest
@Preview(name = "amount_keypad_font_150", widthDp = 400, heightDp = 360, fontScale = 1.5f)
@Composable
fun amountKeypadLargeFontScreenshot() {
    WhfinTheme(darkTheme = false) { AmountKeypadScreenshotContent() }
}

@Composable
private fun AmountKeypadScreenshotContent() {
    Surface(color = MaterialTheme.colorScheme.background) {
        WhfinAmountKeypad(
            deleteContentDescription = "Delete digit",
            onKey = {},
            modifier = Modifier.fillMaxWidth().padding(20.dp),
        )
    }
}

@PreviewTest
@Preview(name = "input_choice_light", widthDp = 400, heightDp = 640)
@Composable
fun inputChoiceLightScreenshot() {
    WhfinTheme(darkTheme = false) { WhfinInputChoiceGallery() }
}

@PreviewTest
@Preview(name = "input_choice_dark", widthDp = 400, heightDp = 640, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun inputChoiceDarkScreenshot() {
    WhfinTheme(darkTheme = true) { WhfinInputChoiceGallery() }
}

@PreviewTest
@Preview(name = "input_choice_font_150", widthDp = 400, heightDp = 820, fontScale = 1.5f)
@Composable
fun inputChoiceLargeFontScreenshot() {
    WhfinTheme(darkTheme = false) { WhfinInputChoiceGallery() }
}

@PreviewTest
@Preview(name = "decision_dialog_light", widthDp = 400, heightDp = 420)
@Composable
fun decisionDialogLightScreenshot() {
    WhfinTheme(darkTheme = false) { WhfinConfirmDialogGallery() }
}

@PreviewTest
@Preview(name = "decision_dialog_dark", widthDp = 400, heightDp = 420, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun decisionDialogDarkScreenshot() {
    WhfinTheme(darkTheme = true) { WhfinConfirmDialogGallery() }
}

@PreviewTest
@Preview(name = "decision_dialog_font_150", widthDp = 400, heightDp = 540, fontScale = 1.5f)
@Composable
fun decisionDialogLargeFontScreenshot() {
    WhfinTheme(darkTheme = false) { WhfinConfirmDialogGallery() }
}

@PreviewTest
@Preview(name = "state_loading_light", widthDp = 400, heightDp = 260)
@Composable
fun stateLoadingLightScreenshot() {
    WhfinTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            WhfinStatePane(
                state = WhfinPaneState.Loading,
                title = "Reading the ledger",
                body = "One moment.",
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@PreviewTest
@Preview(name = "state_loading_dark", widthDp = 400, heightDp = 260, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun stateLoadingDarkScreenshot() {
    WhfinTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            WhfinStatePane(
                state = WhfinPaneState.Loading,
                title = "Reading the ledger",
                body = "One moment.",
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@PreviewTest
@Preview(name = "skeleton_light", widthDp = 400, heightDp = 300)
@Composable
fun skeletonLightScreenshot() {
    WhfinTheme(darkTheme = false) { SkeletonScreenshotContent() }
}

@PreviewTest
@Preview(name = "skeleton_dark", widthDp = 400, heightDp = 300, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun skeletonDarkScreenshot() {
    WhfinTheme(darkTheme = true) { SkeletonScreenshotContent() }
}

@Composable
private fun SkeletonScreenshotContent() {
    Surface(color = MaterialTheme.colorScheme.background) {
        WhfinSkeleton(
            contentDescription = "Reading the ledger",
            modifier = Modifier.fillMaxWidth().padding(20.dp),
        ) {
            WhfinSkeletonBlock(Modifier.fillMaxWidth(.3f), height = 11.dp)
            WhfinSkeletonBlock(Modifier.fillMaxWidth(.5f), height = 30.dp)
            WhfinSkeletonLedgerRow()
            WhfinSkeletonLedgerRow()
        }
    }
}
