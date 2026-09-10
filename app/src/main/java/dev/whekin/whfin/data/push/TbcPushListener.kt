package dev.whekin.whfin.data.push

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dev.whekin.whfin.WhfinApp
import dev.whekin.whfin.data.sms.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PushSettings(context: Context) {
    private val preferences = context.getSharedPreferences("tbc_push", Context.MODE_PRIVATE)
    var enabled: Boolean
        get() = preferences.getBoolean("enabled", false)
        set(value) { check(preferences.edit().putBoolean("enabled", value).commit()) }
}

object PushRuntime {
    val connected = MutableStateFlow(false)
    val revision = MutableStateFlow(0L)
    val error = MutableStateFlow(false)
    val mutex = Mutex()
}

class TbcPushListener : NotificationListenerService() {
    override fun onListenerConnected() {
        PushRuntime.connected.value = true
        if (PushSettings(this).enabled) runCatching { activeNotifications }.getOrNull()?.forEach(::onNotificationPosted)
    }
    override fun onListenerDisconnected() { PushRuntime.connected.value = false }
    override fun onDestroy() { PushRuntime.connected.value = false; super.onDestroy() }
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != TbcPush.PACKAGE || !PushSettings(this).enabled) return
        val push = runCatching { capture(sbn) }.getOrElse { PushRuntime.error.value = true; return }
        if (TbcPush.sensitive(push)) return
        val app = applicationContext as WhfinApp
        app.appScope.launch(Dispatchers.IO) {
            PushRuntime.mutex.withLock {
                if (!PushSettings(app).enabled) return@withLock
                try { processPush(app, push); PushRuntime.error.value = false }
                catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (_: Exception) { PushRuntime.error.value = true }
                PushRuntime.revision.value++
            }
        }
    }
    companion object {
        fun capture(sbn: StatusBarNotification): BankPush {
            val n = sbn.notification
            var budget = 8192
            var truncated = false
            fun text(value: CharSequence?): String {
                val raw = value?.toString().orEmpty()
                val kept = raw.take(budget.coerceAtLeast(0))
                truncated = truncated || kept.length < raw.length
                budget -= kept.length
                return kept
            }
            val title = text(n.extras.getCharSequence(Notification.EXTRA_TITLE))
            val big = text(n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT))
            val body = text(n.extras.getCharSequence(Notification.EXTRA_TEXT))
            val sub = text(n.extras.getCharSequence(Notification.EXTRA_SUB_TEXT))
            val sourceLines = n.extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES).orEmpty()
            truncated = truncated || sourceLines.size > 30
            val lines = sourceLines.take(30).map(::text)
            return BankPush(sbn.packageName, sbn.key, sbn.postTime, title, body, big, sub, lines,
                n.channelId.orEmpty(), n.category.orEmpty(), n.flags and Notification.FLAG_GROUP_SUMMARY != 0, truncated)
        }
    }
}

internal suspend fun processPush(app: WhfinApp, push: BankPush): PushJournal.Entry? {
    if (push.packageName != TbcPush.PACKAGE || TbcPush.sensitive(push)) return null
    val journal = PushJournal(app)
    journal.record(push, "RECEIVED")
    val classification = TbcPush.classify(push)
    val outcome = when {
        push.groupSummary -> "GROUP_SUMMARY"
        push.truncated -> "TRUNCATED"
        System.currentTimeMillis() - push.postedAt > PushJournal.RETENTION -> "OLD"
        classification is BankSmsMessage.Classification.Parsed -> {
            val result = SmsTransactionImporter(app.userDb, BankSmsBank.TBC).importPush(
                classification, TbcPush.ledgerKey(push, classification), push.postedAt)
            return journal.record(push, result.outcome.name, result.diagnosticId)
        }
        else -> "UNRECOGNIZED"
    }
    return journal.record(push, outcome)
}
