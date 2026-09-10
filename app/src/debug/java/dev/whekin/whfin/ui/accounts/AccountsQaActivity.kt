package dev.whekin.whfin.ui.accounts

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import dev.whekin.whfin.ui.feed.*
import dev.whekin.whfin.data.db.*
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.db.BankProduct
import dev.whekin.whfin.ui.theme.WhfinTheme
import java.util.Locale

/**
 * Debug-only, non-exported render host for the account cards. Synthetic values only; never opens or
 * modifies a database.
 *
 * The shapes worth rendering are the ones real data produces and the demo fixture does not: three
 * currencies under one imported account name, a five-figure balance inside a narrow cell, and a card
 * ledger that has run out.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
class AccountsQaActivity : ComponentActivity() {
    var balanceReviewConfirmed = false
    var categorySelected = false
    var balanceAdjusted = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dark = intent.getBooleanExtra("dark", false)
        val language = intent.getStringExtra("language") ?: "en"
        Locale.setDefault(Locale.forLanguageTag(language))
        val fontScale = intent.getFloatExtra("fontScale", 1f)
        val style = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        val configuration = Configuration(resources.configuration)
            .apply { setLocale(Locale.forLanguageTag(language)) }
        val context = createConfigurationContext(configuration)
        setContent {
            CompositionLocalProvider(
                androidx.compose.ui.platform.LocalResources provides context.resources,
                LocalContext provides context,
                LocalConfiguration provides configuration,
                LocalDensity provides Density(LocalDensity.current.density, fontScale),
            ) {
                WhfinTheme(darkTheme = dark) {
                    Surface(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }, color = MaterialTheme.colorScheme.background) {
                        if (intent.getBooleanExtra("transferLabels", false)) {
                            val from=QA_ACCOUNTS.first().account.copy(name="Credo GEL •0001")
                            val to=from.copy(id=2,currency="EUR",iban="GE00CD0000000000000002",name="Credo EUR •0002")
                            val debit=TransactionEntity(id=1,accountId=from.id,amountMinor=-25000,currency="GEL",occurredAt=1789067100000L,
                                status=TxStatus.CONFIRMED,source=TxSource.SMS,transferGroupId=1,isTransfer=true)
                            val credit=debit.copy(id=2,accountId=to.id,amountMinor=10000,currency="EUR")
                            val item=buildBaseFeedItems(listOf(debit,credit),emptyList(),emptyList(),listOf(from,to),emptyMap(),java.time.ZoneOffset.UTC).single()
                            Column(Modifier.safeDrawingPadding()) { FeedRow(item,{}) }
                        } else if (intent.getBooleanExtra("balanceReview", false)) {
                            val rows = listOf(TransactionEntity(id=1,accountId=1,amountMinor=-25000,currency="GEL",occurredAt=1789067100000L,status=TxStatus.CONFIRMED,source=TxSource.SMS))
                            val preview = dev.whekin.whfin.data.sms.CredoBalanceReview.Preview(rows,
                                listOf(dev.whekin.whfin.data.sms.CredoBalanceReview.Change(1,1,1789067100000L,35000,null)),
                                mapOf(1L to "Credo •0001 GEL"),emptyList())
                            dev.whekin.whfin.ui.settings.CredoBalanceReviewSheet(preview,onDismiss={},onConfirm={ balanceReviewConfirmed=true })
                        } else if (intent.getBooleanExtra("transaction", false)) {
                            var choosing by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                            var selected by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<CategoryEntity?>(null) }
                            val category = CategoryEntity(id = 1, name = if (language == "ru") "Кафе" else "Coffee", kind = CategoryKind.EXPENSE,
                                icon = "LocalCafe", color = 0xFF78906F.toInt())
                            val transaction = TransactionEntity(id = 1, accountId = 1, amountMinor = -1200, currency = "GEL",
                                occurredAt = 1L, status = if (intent.getBooleanExtra("hold", false)) TxStatus.PENDING else TxStatus.MANUAL,
                                source = if (intent.getBooleanExtra("hold", false)) TxSource.BANK_HOLD else TxSource.MANUAL,
                                rawCounterparty = "Example Cafe", categoryId = selected?.id)
                            val feed = FeedItem(transaction, null, selected, QA_ACCOUNTS.first().account, null, day = java.time.LocalDate.of(2026, 9, 10))
                            if (choosing) CategoryPickerSheet(feed, listOf(category), { choosing = false },
                                { selected = it; categorySelected = true; choosing = false }, { _, _, _, _ -> })
                            else TransactionDetailsSheet(feed, {}, { choosing = true }, null, onEdit = null, onDebt = null, onClearDebt = null, onConfirm = {})
                        } else if (intent.getBooleanExtra("activity", false)) {
                            var edit by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                            var adjust by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                            var correction by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                            var balance by androidx.compose.runtime.remember { androidx.compose.runtime.mutableLongStateOf(5761L) }
                            val item = QA_ACCOUNTS.first().copy(balanceMinor = balance)
                            AccountTransactionsContent(item.account, item.balanceMinor, emptyList(), item, {},
                                AccountActivityCallbacks({}, { edit = true }, { adjust = true }, {}, { correction = true }), empty = true)
                            if (edit) BankMappingSheet(account = item.account, existingCards = emptyList(), existingVirtualCards = emptyList(),
                                onDismiss = { edit = false }, onConfirm = { _, _, _, _, _, _, _ -> edit = false })
                            if (correction) UserOpeningCorrectionSheet(item.account.currency, balance,
                                onDismiss = { correction = false }, onConfirm = { balance = it; correction = false })
                            if (adjust) AdjustBalanceSheet(item, { adjust = false }, { balance += it; balanceAdjusted = true; adjust = false })
                        } else Column(
                            Modifier
                                .safeDrawingPadding()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            AccountGroupCard(
                                name = "Credo",
                                accounts = QA_ACCOUNTS,
                                onOpenTransactions = {},
                                onOpenAccountDetails = {},
                                onOpenGroupDetails = {},
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * One imported account with three currencies and an empty card ledger, one renamed by hand, and a
 * deposit whose lari balance is the widest number the strip has to hold.
 */
private val QA_ACCOUNTS = listOf(
    AccountWithBalance(
        AccountEntity(id = 1, name = "Credo GEL •0001", type = AccountType.BANK, groupId = 1, currency = "GEL", iban = "GE00CD0000000000000001"),
        5_761, listOf("0002"), primaryCardMasks = listOf("0002"), primaryCardConfigured = true,
        groupName = "Credo",
    ),
    AccountWithBalance(
        AccountEntity(id = 2, name = "Credo EUR •0001", type = AccountType.BANK, groupId = 1, currency = "EUR", iban = "GE00CD0000000000000001"),
        0, emptyList(), groupName = "Credo",
    ),
    AccountWithBalance(
        AccountEntity(id = 3, name = "Credo USD •0001", type = AccountType.BANK, groupId = 1, currency = "USD", iban = "GE00CD0000000000000001"),
        497, emptyList(), groupName = "Credo",
    ),
    AccountWithBalance(
        AccountEntity(
            id = 4, name = "Term deposit", type = AccountType.SAVINGS, groupId = 1,
            currency = "GEL", iban = "GE00CD0000000000000002",
            bankProduct = BankProduct.TERM_DEPOSIT,
        ),
        2_448_830, emptyList(), groupName = "Credo",
    ),
    AccountWithBalance(
        AccountEntity(
            id = 5, name = "Term deposit", type = AccountType.SAVINGS, groupId = 1,
            currency = "USD", iban = "GE00CD0000000000000002",
            bankProduct = BankProduct.TERM_DEPOSIT,
        ),
        255_000, emptyList(), groupName = "Credo",
    ),
    AccountWithBalance(
        AccountEntity(
            id = 6, name = "Travel", type = AccountType.BANK, groupId = 1,
            currency = "EUR", iban = "GE00CD0000000000000003",
            bankProduct = BankProduct.CURRENT_ACCOUNT,
        ),
        16_930, emptyList(), groupName = "Credo",
    ),
)
