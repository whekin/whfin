package dev.whekin.whfin.data.drive

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.whekin.whfin.WhfinApp
import java.time.Duration

/**
 * Ежедневная загрузка шифрованного бэкапа в Google Drive. Всегда работает с личной базой
 * (userDb): demo-режим не влияет на состав бэкапа. Consent из фона не запрашивается —
 * при протухшей авторизации ставится флаг needsReauth, который показывает UI.
 */
class DriveBackupWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val app = applicationContext as WhfinApp
        val store = DriveBackupStore(applicationContext)
        if (!store.enabled) return Result.success()

        val token = when (val auth = DriveBackupAuth.authorize(applicationContext)) {
            is DriveAuthResult.Authorized -> auth.accessToken
            is DriveAuthResult.ConsentRequired -> {
                store.needsReauth = true
                store.lastError = ERROR_REAUTH
                return Result.failure()
            }
            is DriveAuthResult.Failed -> {
                store.lastError = ERROR_AUTH
                return if (auth.missingOAuthClient) Result.failure() else Result.retry()
            }
        }

        return try {
            val manager = DriveBackupManager(applicationContext, app.userDb)
            manager.backupNow(token, appVersion())
            store.lastSuccessAt = System.currentTimeMillis()
            store.lastError = null
            store.needsReauth = false
            Result.success()
        } catch (error: DriveBackupMissingPassphraseException) {
            store.lastError = ERROR_PASSPHRASE
            Result.failure()
        } catch (error: DriveBackupException) {
            if (error.isAuthError) {
                store.needsReauth = true
                store.lastError = ERROR_REAUTH
                Result.failure()
            } else {
                store.lastError = ERROR_NETWORK
                if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
            }
        } catch (error: Exception) {
            store.lastError = ERROR_UNKNOWN
            Result.failure()
        }
    }

    private fun appVersion(): String = runCatching {
        val info = applicationContext.packageManager.getPackageInfo(applicationContext.packageName, 0)
        info.versionName ?: "unknown"
    }.getOrDefault("unknown")

    companion object {
        private const val UNIQUE_NAME = "whfin-drive-backup"
        private const val MAX_RETRIES = 3

        const val ERROR_REAUTH = "reauth"
        const val ERROR_AUTH = "auth"
        const val ERROR_PASSPHRASE = "passphrase"
        const val ERROR_NETWORK = "network"
        const val ERROR_UNKNOWN = "unknown"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<DriveBackupWorker>(Duration.ofDays(1))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(true)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        }
    }
}
