@file:OptIn(androidx.glance.ExperimentalGlanceApi::class)

package dev.whekin.whfin.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.ColorFilter
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dev.whekin.whfin.WhfinApp
import dev.whekin.whfin.R
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.db.PaymentInstrumentType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val Context.widgetStore by preferencesDataStore("whfin_widget")
private val SourceIndex = intPreferencesKey("source_index")
private val CurrencyIndex = intPreferencesKey("currency_index")

// A launcher cell is usually ~80–100 dp wide after host padding. Keep breakpoints between cells,
// not at the nominal XML minWidth, so a 2-cell widget never receives the 3-cell composition.
private val One = DpSize(64.dp, 48.dp)
private val Two = DpSize(110.dp, 48.dp)
private val Three = DpSize(180.dp, 48.dp)
private val Four = DpSize(250.dp, 48.dp)

private data class WidgetSource(
    val compactLabel: String,
    val fullLabel: String,
    val accountByCurrency: Map<String, Long>,
    val cash: Boolean = false,
) {
    val currencies: List<String>
        get() = if (cash) listOf("GEL", "USD", "EUR") else accountByCurrency.keys.sortedWith(
            compareBy<String> { if (it == "GEL") 0 else 1 }.thenBy { it },
        )
}

class WhfinWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(One, Two, Three, Four))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val sources = loadSources(context)
        val store = context.widgetStore
        val initial = store.data.first()
        provideContent {
            val preferences by store.data.collectAsState(initial)
            val scope = rememberCoroutineScope()
            val size = LocalSize.current
            val level = when {
                size.width < Two.width -> 1
                size.width < Three.width -> 2
                size.width < Four.width -> 3
                else -> 4
            }
            val sourceIndex = (preferences[SourceIndex] ?: 0).mod(sources.size)
            val selectedSource = if (level == 1) sources.first() else sources[sourceIndex]
            val availableCurrencies = selectedSource.currencies.ifEmpty { listOf("GEL") }
            val storedCurrencyIndex = preferences[CurrencyIndex] ?: 0
            val preferredCurrency = if (level < 3) "GEL" else availableCurrencies[storedCurrencyIndex.mod(availableCurrencies.size)]
            val currency = preferredCurrency.takeIf { it in availableCurrencies } ?: availableCurrencies.first()
            val accountId = selectedSource.accountByCurrency[currency]

            GlanceTheme {
                CompactWidget(
                    level = level,
                    source = selectedSource,
                    currency = currency,
                    addAction = actionStartActivity(quickIntent(context, currency, accountId, selectedSource.fullLabel)),
                    cycleSource = {
                        scope.launch {
                            store.updateData { current ->
                                current.toMutablePreferences().apply {
                                    this[SourceIndex] = (sourceIndex + 1).mod(sources.size)
                                    this[CurrencyIndex] = 0
                                }
                            }
                        }
                    },
                    cycleCurrency = {
                        scope.launch {
                            store.updateData { current ->
                                current.toMutablePreferences().apply {
                                    this[CurrencyIndex] = (storedCurrencyIndex + 1).mod(availableCurrencies.size)
                                }
                            }
                        }
                    },
                )
            }
        }
    }
}

class WhfinWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WhfinWidget()
}

class WhfinWidget2Receiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WhfinWidget()
}

class WhfinWidget3Receiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WhfinWidget()
}

class WhfinWidget4Receiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WhfinWidget()
}

@Composable
private fun CompactWidget(
    level: Int,
    source: WidgetSource,
    currency: String,
    addAction: androidx.glance.action.Action,
    cycleSource: () -> Unit,
    cycleCurrency: () -> Unit,
) {
    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (level == 1) {
            AddButton(
                modifier = GlanceModifier.size(46.dp)
                    .background(GlanceTheme.colors.primaryContainer)
                    .cornerRadius(15.dp),
                action = addAction,
                color = GlanceTheme.colors.onPrimaryContainer,
            )
            return@Box
        }

        Row(
            modifier = GlanceModifier.fillMaxWidth().height(48.dp)
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(16.dp),
            verticalAlignment = Alignment.Vertical.CenterVertically,
            horizontalAlignment = Alignment.Horizontal.Start,
        ) {
            WidgetSegment(
                text = if (level >= 4) source.fullLabel else source.compactLabel,
                modifier = GlanceModifier.defaultWeight().fillMaxHeight()
                    .clickable(key = "cycle-source", block = cycleSource),
                textColor = GlanceTheme.colors.onSurface,
            )
            if (level >= 3) {
                WidgetDivider()
                WidgetSegment(
                    text = currency,
                    modifier = GlanceModifier.width(if (level >= 4) 78.dp else 64.dp).fillMaxHeight()
                        .clickable(key = "cycle-currency", block = cycleCurrency),
                    textColor = GlanceTheme.colors.onSurfaceVariant,
                )
            }
            WidgetDivider()
            InlineAddButton(addAction)
        }
    }
}

@Composable
private fun InlineAddButton(action: androidx.glance.action.Action) {
    Box(
        modifier = GlanceModifier.size(48.dp).cornerRadius(16.dp).clickable(action),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = GlanceModifier.size(40.dp)
                .background(GlanceTheme.colors.primaryContainer)
                .cornerRadius(13.dp),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_widget_add),
                contentDescription = null,
                modifier = GlanceModifier.size(26.dp),
                colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimaryContainer),
            )
        }
    }
}

@Composable
private fun AddButton(
    modifier: GlanceModifier,
    action: androidx.glance.action.Action,
    color: ColorProvider,
) {
    Box(modifier = modifier.clickable(action), contentAlignment = Alignment.Center) {
        Image(
            provider = ImageProvider(R.drawable.ic_widget_add),
            contentDescription = null,
            modifier = GlanceModifier.size(28.dp),
            colorFilter = ColorFilter.tint(color),
        )
    }
}

@Composable
private fun WidgetDivider() {
    Box(
        modifier = GlanceModifier.width(1.dp).height(24.dp).background(GlanceTheme.colors.outline),
        contentAlignment = Alignment.Center,
    ) {}
}

@Composable
private fun WidgetSegment(text: String, modifier: GlanceModifier, textColor: ColorProvider) {
        Box(
            modifier = modifier.padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text,
                maxLines = 1,
                style = TextStyle(color = textColor, fontSize = 13.sp, fontWeight = FontWeight.Medium),
            )
        }
}

private suspend fun loadSources(context: Context): List<WidgetSource> {
    val db = (context.applicationContext as WhfinApp).db
    val accounts = db.accountDao().allActive()
    val accountById = accounts.associateBy(AccountEntity::id)
    val links = db.paymentInstrumentDao().observeLinks().first()
    val instruments = db.paymentInstrumentDao().observeActive().first()
    val groupsById = db.financialGroupDao().observeActive().first().associateBy { it.id }
    val linkedAccountIds = links.mapTo(mutableSetOf()) { it.accountId }

    val result = mutableListOf(
        WidgetSource(
            compactLabel = context.getString(R.string.widget_source_cash),
            fullLabel = context.getString(R.string.widget_source_cash),
            accountByCurrency = accounts.filter { it.type == AccountType.CASH }.associate { it.currency to it.id },
            cash = true,
        ),
    )
    instruments.forEach { instrument ->
        val linked = links.filter { it.instrumentId == instrument.id }.mapNotNull { accountById[it.accountId] }
        if (linked.isNotEmpty()) {
            val virtual = instrument.type == PaymentInstrumentType.VIRTUAL_CARD
            val label = context.getString(if (virtual) R.string.widget_source_virtual else R.string.widget_source_card)
            val groupName = groupsById[instrument.groupId]?.let { it.name.ifBlank { it.provider.orEmpty() } }
                ?.takeIf(String::isNotBlank)
            val compact = listOfNotNull(groupName, "•${instrument.last4}").joinToString(" ")
            result += WidgetSource(
                compactLabel = compact,
                fullLabel = listOfNotNull(groupName, label).joinToString(" · ") + " •${instrument.last4}",
                accountByCurrency = linked.distinctBy { it.currency }.associate { it.currency to it.id },
            )
        }
    }
    accounts.filter { it.id !in linkedAccountIds && it.type in setOf(AccountType.BANK, AccountType.CRYPTO) }
        .groupBy { it.groupId to it.type }
        .values
        .forEach { grouped ->
            val first = grouped.first()
            val fallback = context.getString(
                if (first.type == AccountType.CRYPTO) R.string.widget_source_wallet else R.string.widget_source_bank,
            )
            val groupName = first.groupId?.let(groupsById::get)?.let { it.name.ifBlank { it.provider.orEmpty() } }
                ?.takeIf(String::isNotBlank)
            val cleanName = groupName ?: first.name
                .replace(Regex("\\s+${Regex.escape(first.currency)}.*$", RegexOption.IGNORE_CASE), "")
                .ifBlank { fallback }
            val accountTail = first.iban?.filter(Char::isLetterOrDigit)?.takeLast(4)?.takeIf(String::isNotBlank)
            val displayName = listOfNotNull(cleanName, accountTail?.let { "•$it" }).joinToString(" ")
            result += WidgetSource(
                compactLabel = displayName,
                fullLabel = displayName,
                accountByCurrency = grouped.distinctBy { it.currency }.associate { it.currency to it.id },
            )
        }
    return result
}

private fun quickIntent(context: Context, currency: String, accountId: Long?, sourceLabel: String) =
    Intent(context, QuickExpenseActivity::class.java).apply {
        putExtra(QuickExpenseActivity.EXTRA_CURRENCY, currency)
        putExtra(QuickExpenseActivity.EXTRA_SOURCE_LABEL, sourceLabel)
        accountId?.let { putExtra(QuickExpenseActivity.EXTRA_ACCOUNT_ID, it) }
        data = Uri.parse("whfin://quick-expense/$currency/${accountId ?: "cash"}")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
