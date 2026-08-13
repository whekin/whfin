package dev.whekin.whfin.data.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.whekin.whfin.EXTRA_OPEN_ACCOUNTS
import dev.whekin.whfin.MainActivity
import dev.whekin.whfin.R
import dev.whekin.whfin.data.db.PaymentInstrumentType
import dev.whekin.whfin.data.db.WhfinDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

private data class PhysicalCardGelBalance(
    val accountId: Long,
    val accountName: String,
    val balanceMinor: Long,
    val cardLast4s: List<String>,
)

class PhysicalCardBalanceMonitor(
    context: Context,
    private val db: WhfinDatabase,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val notificationManager = NotificationManagerCompat.from(appContext)
    @Volatile private var latestBalances: List<PhysicalCardGelBalance> = emptyList()
    private var collectionJob: Job? = null

    fun start() {
        if (collectionJob != null) return
        createChannel()
        collectionJob = scope.launch {
            combine(
                db.accountDao().observeActive(),
                db.transactionDao().observeAccountBalances(),
                db.paymentInstrumentDao().observeActive(),
                db.paymentInstrumentDao().observeLinks(),
            ) { accounts, balances, instruments, links ->
                val balanceByAccount = balances.associate { it.accountId to it.totalMinor }
                val physicalIds = instruments
                    .filter { it.type == PaymentInstrumentType.PHYSICAL_CARD && !it.isArchived }
                    .associateBy { it.id }
                val cardsByAccount = links.groupBy { it.accountId }.mapValues { (_, accountLinks) ->
                    accountLinks.mapNotNull { physicalIds[it.instrumentId]?.last4 }.distinct().sorted()
                }
                accounts.filter { it.currency.equals("GEL", ignoreCase = true) }
                    .mapNotNull { account ->
                        cardsByAccount[account.id]?.takeIf(List<String>::isNotEmpty)?.let { cards ->
                            PhysicalCardGelBalance(
                                accountId = account.id,
                                accountName = account.name,
                                balanceMinor = balanceByAccount[account.id] ?: 0L,
                                cardLast4s = cards,
                            )
                        }
                    }
            }.collect { balances ->
                latestBalances = balances
                evaluate(balances)
            }
        }
    }

    /** Called after the Android notification prompt so an already-low account is checked immediately. */
    fun notificationPermissionChanged() {
        scope.launch { evaluate(latestBalances) }
    }

    @Synchronized
    private fun evaluate(balances: List<PhysicalCardGelBalance>) {
        val previouslyAlerted = preferences.getStringSet(KEY_ALERTED_ACCOUNT_IDS, emptySet()).orEmpty()
            .mapNotNull(String::toLongOrNull)
            .toSet()
        val transition = lowBalanceNotificationTransition(
            previouslyAlertedAccountIds = previouslyAlerted,
            balancesByAccountId = balances.associate { it.accountId to it.balanceMinor },
        )
        val canNotify = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || runCatching {
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        val successfullyAlerted = if (canNotify) {
            transition.accountIdsToNotify.mapNotNullTo(mutableSetOf()) { accountId ->
                balances.firstOrNull { it.accountId == accountId }
                    ?.takeIf(::notifyLowBalance)
                    ?.accountId
            }
        } else emptySet()
        val stillAlerted = previouslyAlerted.intersect(transition.alertedAccountIds) + successfullyAlerted
        preferences.edit()
            .putStringSet(KEY_ALERTED_ACCOUNT_IDS, stillAlerted.mapTo(mutableSetOf(), Long::toString))
            .apply()
    }

    private fun notifyLowBalance(balance: PhysicalCardGelBalance): Boolean {
        val openAccounts = Intent(appContext, MainActivity::class.java)
            .putExtra(EXTRA_OPEN_ACCOUNTS, true)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            balance.accountId.hashCode(),
            openAccounts,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val cardLabel = balance.cardLast4s.joinToString(" · ") { "••$it" }
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_whfin)
            .setContentTitle(appContext.getString(R.string.low_balance_notification_title))
            .setContentText(
                appContext.getString(
                    R.string.low_balance_notification_body,
                    balance.accountName,
                    cardLabel,
                    balance.balanceMinor / 100.0,
                ),
            )
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                appContext.getString(
                    R.string.low_balance_notification_body,
                    balance.accountName,
                    cardLabel,
                    balance.balanceMinor / 100.0,
                ),
            ))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        return try {
            // Permission can be revoked between the check and this call; only a posted alert is latched.
            notificationManager.notify(NOTIFICATION_ID_BASE + balance.accountId.hashCode(), notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = appContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                appContext.getString(R.string.low_balance_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = appContext.getString(R.string.low_balance_channel_description)
            },
        )
    }

    private companion object {
        const val CHANNEL_ID = "physical_card_balance"
        const val NOTIFICATION_ID_BASE = 31_000
        const val PREFERENCES_NAME = "whfin_low_balance"
        const val KEY_ALERTED_ACCOUNT_IDS = "alerted_account_ids"
    }
}
