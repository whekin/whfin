package dev.whekin.whfin.ui.banks

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.whekin.whfin.R

/** Original bank artwork, bundled locally; no request to a logo service at runtime. */
@Composable
fun BankBrand(provider: String, modifier: Modifier = Modifier) {
    Row(modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        when (provider) {
            "TBC" -> {
                Image(painterResource(R.drawable.ic_bank_tbc), null,
                    Modifier.size(40.dp).background(Color(0xFF00AEEF), RoundedCornerShape(10.dp)).padding(7.dp))
                Text("TBC", style = MaterialTheme.typography.titleLarge)
            }
            "Credo" -> Image(painterResource(R.drawable.bank_credo), "Credo",
                Modifier.width(150.dp).height(40.dp))
            else -> Text(provider, style = MaterialTheme.typography.titleLarge)
        }
    }
}
