package dev.whekin.whfin.core.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
fun WhfinDesignSystemGallery() {
    Surface(Modifier.fillMaxSize(), color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
        Column(
            Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            WhfinSectionLabel("Working ledger")
            WhfinLedgerGroup(Modifier.fillMaxWidth()) {
                WhfinLedgerRow("Credo · Card •0001", supportingText = "Confirmed · GEL", markerColor = androidx.compose.material3.MaterialTheme.colorScheme.primary, divider = true)
                WhfinLedgerRow("Example subscription", supportingText = "Subscriptions · USD", markerColor = androidx.compose.material3.MaterialTheme.colorScheme.tertiary)
            }
            WhfinNotice(
                title = "Statement coverage has a gap",
                body = "Import the missing period to keep balances verified.",
                kind = WhfinNoticeKind.Attention,
                actionLabel = "Choose statement",
                onAction = {},
                modifier = Modifier.fillMaxWidth(),
            )
            WhfinButton("Save transaction", {}, Modifier.fillMaxWidth())
            WhfinButton("Delete draft", {}, Modifier.fillMaxWidth(), style = WhfinActionStyle.Destructive)
        }
    }
}

@Preview(name = "Light", widthDp = 400, heightDp = 800, showBackground = true)
@Preview(name = "Dark", widthDp = 400, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Font 1.5", widthDp = 400, heightDp = 900, fontScale = 1.5f, showBackground = true)
@Composable
private fun WhfinDesignSystemPreview() {
    WhfinTheme { WhfinDesignSystemGallery() }
}

@Preview(name = "Back light", widthDp = 160, heightDp = 80, showBackground = true)
@Preview(name = "Back dark", widthDp = 160, heightDp = 80, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Back font 1.5", widthDp = 200, heightDp = 96, fontScale = 1.5f, showBackground = true)
@Composable
private fun WhfinBackButtonPreview() {
    WhfinTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            androidx.compose.foundation.layout.Row(
                Modifier.padding(16.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                WhfinBackButton(contentDescription = "Back", onClick = {})
                Text("Settings", style = MaterialTheme.typography.headlineSmall)
            }
        }
    }
}

@Preview(name = "Loading", widthDp = 400, heightDp = 300, showBackground = true)
@Preview(name = "Empty dark", widthDp = 400, heightDp = 300, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun WhfinStatePreview() {
    WhfinTheme {
        Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
            WhfinStatePane(
                WhfinPaneState.Empty,
                title = "No transactions yet",
                body = "Import a statement or add the first expense.",
                actionLabel = "Add expense",
                onAction = {},
            )
        }
    }
}

@Preview(name = "Monthly chart light", widthDp = 400, heightDp = 240, showBackground = true)
@Preview(name = "Monthly chart dark", widthDp = 400, heightDp = 240, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Monthly chart font 1.5", widthDp = 400, heightDp = 300, fontScale = 1.5f, showBackground = true)
@Composable
private fun WhfinMonthlyChartPreview() {
    WhfinTheme {
        Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
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
}
