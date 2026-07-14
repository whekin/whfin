package dev.whekin.whfin.core.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.tooling.preview.Preview
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
