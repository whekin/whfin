package dev.whekin.whfin.ui.accounts

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import dev.whekin.whfin.R
import dev.whekin.whfin.WhfinApp
import dev.whekin.whfin.core.ui.*
import dev.whekin.whfin.data.importer.UserOpeningCorrection
import dev.whekin.whfin.ui.components.FormSheet
import dev.whekin.whfin.ui.formatMinor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal

@Composable
internal fun UserOpeningCorrectionRoute(accountId: Long, onDismiss: () -> Unit) {
    val app = LocalContext.current.applicationContext as WhfinApp
    val repository = remember(app) { UserOpeningCorrection(app.db) }
    var snapshot by remember(accountId) { mutableStateOf<UserOpeningCorrection.Snapshot?>(null) }
    var loaded by remember(accountId) { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(accountId) {
        try { snapshot = withContext(Dispatchers.IO) { repository.read(accountId) } }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { error = true }
        loaded = true
    }
    val current = snapshot
    if (!loaded || current == null) {
        FormSheet(stringResource(R.string.opening_correct_title), onDismiss, stringResource(R.string.action_done), true, onDismiss) {
            if (!loaded) WhfinLoadingIndicator() else Text(stringResource(R.string.opening_correct_unavailable))
        }
    } else UserOpeningCorrectionSheet(current.account.currency, current.balanceMinor, saving, error,
        onDismiss = { if (!saving) onDismiss() }, onConfirm = { amount ->
            saving = true; error = false
            scope.launch {
                try {
                    withContext(Dispatchers.IO) { repository.correct(current, amount) }
                    onDismiss()
                } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (_: Exception) { error = true }
                finally { saving = false }
            }
        })
}

/** The entered target describes the shown ledger, including its existing manual/SMS rows. */
@Composable
internal fun UserOpeningCorrectionSheet(currency: String, currentBalanceMinor: Long,
    saving: Boolean = false, error: Boolean = false, onDismiss: () -> Unit, onConfirm: (Long) -> Unit) {
    var input by remember(currentBalanceMinor) { mutableStateOf(BigDecimal.valueOf(currentBalanceMinor, 2).toPlainString()) }
    val target = runCatching { input.replace(" ", "").replace(',', '.').toBigDecimal().movePointRight(2).longValueExact() }.getOrNull()
    val delta = target?.let { runCatching { Math.subtractExact(it, currentBalanceMinor) }.getOrNull() }
    FormSheet(stringResource(R.string.opening_correct_title), onDismiss,
        stringResource(if (saving) R.string.opening_correct_saving else R.string.action_save),
        !saving && !error && delta != null && delta != 0L, { target?.let(onConfirm) }) {
        Text(stringResource(R.string.opening_correct_body))
        Text(stringResource(R.string.opening_correct_current, formatMinor(currentBalanceMinor, currency)))
        WhfinField(input, { if (!saving) input = it }, stringResource(R.string.opening_correct_target), suffix = currency,
            keyboardType = KeyboardType.Decimal, modifier = Modifier.fillMaxWidth())
        if (delta != null && delta != 0L) Text(stringResource(R.string.opening_correct_delta, formatMinor(delta, currency, withSign = true)))
        if (error) WhfinNotice(stringResource(R.string.opening_correct_changed), stringResource(R.string.opening_correct_retry), kind = WhfinNoticeKind.Info)
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Correct opening", showBackground = true)
@androidx.compose.ui.tooling.preview.Preview(name = "Correct opening dark", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@androidx.compose.ui.tooling.preview.Preview(name = "Correct opening large compact", fontScale = 1.5f, heightDp = 500)
@Composable
private fun UserOpeningPreview() = dev.whekin.whfin.ui.theme.WhfinTheme {
    UserOpeningCorrectionSheet("GEL", 12300, onDismiss = {}, onConfirm = {})
}
