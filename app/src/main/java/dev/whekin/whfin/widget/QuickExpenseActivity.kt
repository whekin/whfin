package dev.whekin.whfin.widget

import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import dev.whekin.whfin.R
import dev.whekin.whfin.WhfinApp
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.db.TransactionEntity
import dev.whekin.whfin.data.db.TxSource
import dev.whekin.whfin.data.db.TxStatus
import dev.whekin.whfin.ui.parseToMinor
import dev.whekin.whfin.ui.theme.WhfinTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dev.whekin.whfin.core.ui.WhfinButton
import dev.whekin.whfin.core.ui.WhfinField
import dev.whekin.whfin.core.ui.WhfinIconButton

class QuickExpenseActivity : ComponentActivity() {
    companion object {
        const val EXTRA_CURRENCY = "currency"
        const val EXTRA_ACCOUNT_ID = "account_id"
        const val EXTRA_SOURCE_LABEL = "source_label"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(
            window.attributes.softInputMode or WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE,
        )
        enableEdgeToEdge()
        window.isNavigationBarContrastEnforced = false
        setContent {
            WhfinTheme {
                QuickExpenseScreen(
                    initialCurrency = intent.getStringExtra(EXTRA_CURRENCY) ?: "GEL",
                    sourceLabel = intent.getStringExtra(EXTRA_SOURCE_LABEL)
                        ?: getString(R.string.widget_source_cash),
                    sourceAccountId = intent.getLongExtra(EXTRA_ACCOUNT_ID, -1L).takeIf { it > 0 },
                    onDismiss = ::finish,
                    onSave = ::save,
                )
            }
        }
    }

    private fun save(amountMinor: Long, currency: String, accountId: Long?, description: String?) {
        lifecycleScope.launch {
            val db = (application as WhfinApp).db
            withContext(Dispatchers.IO) {
                val requested = accountId?.let { db.accountDao().byId(it) }?.takeIf { it.currency == currency }
                val account = requested ?: db.accountDao().allActive().firstOrNull { it.type == AccountType.CASH && it.currency == currency }
                    ?: db.accountDao().insert(AccountEntity(
                        name = if (currency == "GEL") "Cash" else "Cash $currency",
                        type = AccountType.CASH, currency = currency, sortOrder = 1000,
                    )).let { db.accountDao().byId(it)!! }
                db.transactionDao().insert(TransactionEntity(
                    accountId = account.id,
                    amountMinor = -kotlin.math.abs(amountMinor),
                    currency = currency,
                    occurredAt = System.currentTimeMillis(),
                    note = description,
                    status = TxStatus.MANUAL,
                    source = TxSource.MANUAL,
                    createdAt = System.currentTimeMillis(),
                ))
            }
            Toast.makeText(this@QuickExpenseActivity, R.string.quick_saved, Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}

@Composable
private fun QuickExpenseScreen(
    initialCurrency: String,
    sourceLabel: String,
    sourceAccountId: Long?,
    onDismiss: () -> Unit,
    onSave: (Long, String, Long?, String?) -> Unit,
) {
    val currency = initialCurrency
    var amount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    val minor = parseToMinor(amount)
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        withFrameNanos { }
        focusRequester.requestFocus()
        keyboard?.show()
    }

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .18f)).clickable(onClick = onDismiss), contentAlignment = Alignment.BottomCenter) {
        Surface(
            onClick = {},
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                Modifier.navigationBarsPadding().imePadding().padding(horizontal = 22.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.tertiaryContainer) {
                        Icon(Icons.Default.Payments, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(10.dp))
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(stringResource(R.string.quick_expense), style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "$sourceLabel · $currency",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    WhfinIconButton(Icons.Default.Close, stringResource(R.string.action_cancel), onDismiss, outlined = false)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        amount, { amount = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }.take(12) },
                        placeholder = { Text("0.00", style = MaterialTheme.typography.displayLarge) },
                        textStyle = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum"),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color.Transparent, focusedBorderColor = Color.Transparent),
                        modifier = Modifier.weight(1f).focusRequester(focusRequester),
                    )
                    Text(currency, style = MaterialTheme.typography.titleLarge)
                }
                WhfinField(
                    value = description,
                    onValueChange = { description = it.take(80) },
                    label = stringResource(R.string.quick_description),
                    modifier = Modifier.fillMaxWidth(),
                )
                WhfinButton(
                    label = stringResource(R.string.action_save),
                    onClick = { minor?.let { onSave(it, currency, sourceAccountId, description.trim().takeIf(String::isNotEmpty)) } },
                    enabled = minor != null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
