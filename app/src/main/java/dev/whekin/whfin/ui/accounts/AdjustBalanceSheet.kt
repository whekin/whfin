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
import java.math.BigDecimal
import kotlinx.coroutines.launch

/**
 * Ленивый сценарий: ввёл фактический баланс — разница легла в «Неучтённое»
 * и честно видна в статистике.
 */
@Composable
fun AdjustBalanceSheet(
    item: AccountWithBalance,
    onDismiss: () -> Unit,
    onConfirm: (deltaMinor: Long) -> Unit,
    saving: Boolean = false,
    error: Boolean = false,
) {
    var actualText by remember {
        mutableStateOf(BigDecimal(item.balanceMinor).movePointLeft(2).toPlainString())
    }
    val actualMinor = runCatching {
        actualText.replace(" ", "").replace(',', '.').toBigDecimal().movePointRight(2).longValueExact()
    }.getOrNull()
    val delta = actualMinor?.let { runCatching { Math.subtractExact(it, item.balanceMinor) }.getOrNull() }


    FormSheet(
        title = stringResource(R.string.adjust_balance_title),
        onDismiss = { if (!saving) onDismiss() },
        primaryLabel = stringResource(R.string.action_save),
        primaryEnabled = !saving && !error && delta != null && delta != 0L,
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
            onValueChange = { if (!saving) actualText = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' || ch == '-' }.take(14) },
            label = stringResource(R.string.adjust_actual),
            suffix = item.account.currency,
            keyboardType = KeyboardType.Decimal,
            modifier = Modifier.fillMaxWidth(),
        )
        if (error) Text(stringResource(R.string.balance_adjustment_retry))
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

@Composable
internal fun BalanceAdjustmentRoute(item: AccountWithBalance, onDismiss: () -> Unit, onSaved: (Long) -> Unit) {
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as dev.whekin.whfin.WhfinApp
    val repository = remember(app) { dev.whekin.whfin.data.mutation.BalanceAdjustment(app.db) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var snapshot by remember(item.account.id) { mutableStateOf<dev.whekin.whfin.data.mutation.BalanceAdjustment.Snapshot?>(null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(item.account.id) {
        try { snapshot = repository.read(item.account.id) }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { error = true }
    }
    val loaded = snapshot
    if (loaded == null) FormSheet(stringResource(R.string.adjust_balance_title), onDismiss,
        stringResource(R.string.action_cancel), true, onDismiss) {
        if (error) Text(stringResource(R.string.balance_adjustment_retry))
        else dev.whekin.whfin.core.ui.WhfinLoadingIndicator()
    } else AdjustBalanceSheet(item.copy(balanceMinor = loaded.balance), onDismiss, { delta ->
        saving = true
        scope.launch {
            try { onSaved(repository.save(loaded, Math.addExact(loaded.balance, delta))) }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { error = true }
            finally { saving = false }
        }
    }, saving, error)
}
