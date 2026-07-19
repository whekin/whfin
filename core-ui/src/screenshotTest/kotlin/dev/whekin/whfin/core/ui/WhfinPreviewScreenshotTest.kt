package dev.whekin.whfin.core.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
@Preview(name = "input_choice_light", widthDp = 400, heightDp = 390)
@Composable
fun inputChoiceLightScreenshot() {
    WhfinTheme(darkTheme = false) { WhfinInputChoiceGallery() }
}

@PreviewTest
@Preview(name = "input_choice_dark", widthDp = 400, heightDp = 390, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun inputChoiceDarkScreenshot() {
    WhfinTheme(darkTheme = true) { WhfinInputChoiceGallery() }
}

@PreviewTest
@Preview(name = "input_choice_font_150", widthDp = 400, heightDp = 500, fontScale = 1.5f)
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
