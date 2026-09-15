package dev.whekin.whfin.data.sync

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dev.whekin.whfin.MainActivity
import dev.whekin.whfin.R
import dev.whekin.whfin.WhfinApp
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

/** Keeps an owner-started sync alive when its Activity leaves the foreground. */
class BankSyncService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var stopping = false
    private var wakeLock: PowerManager.WakeLock? = null
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.bank_sync_title), NotificationManager.IMPORTANCE_LOW))
        startForeground(ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "whfin:bank-sync")
            .also { it.acquire(MAX_RUN_MILLIS) }
        scope.launch { delay(MAX_RUN_MILLIS); stopInterrupted() }
        scope.launch {
            (application as WhfinApp).bankSync.statuses.collectLatest { states ->
                if (states.none { it.active } && !(application as WhfinApp).bankSync.hasActiveWork()) {
                    stopping = true
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        stopping = false
        startForeground(ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        if (!(application as WhfinApp).bankSync.hasActiveWork()) {
            stopping = true
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }
    override fun onTimeout(startId: Int, fgsType: Int) {
        stopInterrupted()
    }
    private fun stopInterrupted() {
        (application as WhfinApp).bankSync.cancel()
        stopping = true
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    override fun onDestroy() {
        if (!stopping) (application as WhfinApp).bankSync.cancel()
        scope.cancel()
        wakeLock?.takeIf { it.isHeld }?.release()
        super.onDestroy()
    }
    private fun notification(): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_stat_whfin)
            .setContentTitle(getString(R.string.bank_sync_running))
            .setContentText(getString(R.string.bank_sync_background_hint)).setContentIntent(open)
            .setOngoing(true).setOnlyAlertOnce(true).setProgress(0, 0, true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build()
    }
    companion object {
        private const val CHANNEL = "bank_sync"
        private const val ID = 2401
        private const val MAX_RUN_MILLIS = 30 * 60 * 1000L
        fun start(context: Context) { ContextCompat.startForegroundService(context, Intent(context, BankSyncService::class.java)) }
    }
}
