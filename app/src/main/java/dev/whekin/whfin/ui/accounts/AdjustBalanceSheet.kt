package dev.whekin.whfin.ui.accounts

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import dev.whekin.whfin.R
import dev.whekin.whfin.core.ui.WhfinField
import dev.whekin.whfin.ui.components.FormSheet
import dev.whekin.whfin.ui.formatMinor
import dev.whekin.whfin.ui.parseToMinor
import java.math.BigDecimal

/**
 * Ленивый сценарий: ввёл фактический баланс — разница легла в «Неучтённое»
 * и честно видна в статистике.
 */
@Composable
fun AdjustBalanceSheet(
    item: AccountWithBalance,
    onDismiss: () -> Unit,
    onConfirm: (deltaMinor: Long) -> Unit,
) {
    var actualText by remember {
        mutableStateOf(BigDecimal(item.balanceMinor).movePointLeft(2).toPlainString())
    }
    val actualMinor = parseToMinor(actualText) ?: actualText.toBigDecimalOrNull()
        ?.movePointRight(2)?.toLong()
    val delta = actualMinor?.minus(item.balanceMinor)

    FormSheet(
        title = stringResource(R.string.adjust_balance_title),
        onDismiss = onDismiss,
        primaryLabel = stringResource(R.string.action_save),
        primaryEnabled = delta != null && delta != 0L,
        onPrimary = { onConfirm(delta!!) },
    ) {
        Text(
            stringResource(
                R.string.adjust_current,
                item.account.name,
                formatMinor(item.balanceMinor, item.account.currency),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        WhfinField(
            value = actualText,
            onValueChange = { actualText = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' || ch == '-' }.take(14) },
            label = stringResource(R.string.adjust_actual),
            suffix = item.account.currency,
            keyboardType = KeyboardType.Decimal,
            modifier = Modifier.fillMaxWidth(),
        )
        if (delta != null && delta != 0L) {
            Text(
                stringResource(
                    R.string.adjust_delta_hint,
                    formatMinor(delta, item.account.currency, withSign = true),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = if (delta < 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
            )
        }
    }
}
