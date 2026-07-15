package dev.whekin.whfin.data.drive

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

sealed interface DriveAuthResult {
    data class Authorized(val accessToken: String) : DriveAuthResult
    /** Нужен видимый consent: запускается из UI, недоступно из фонового worker. */
    data class ConsentRequired(val intentSender: IntentSender) : DriveAuthResult
    data class Failed(val message: String, val missingOAuthClient: Boolean = false) : DriveAuthResult
}

/**
 * Авторизация Google Identity для scope drive.appdata. Секретов в приложении нет:
 * Play Services сверяет package name + signing SHA-1 с Android OAuth client в Google Cloud.
 */
object DriveBackupAuth {
    const val SCOPE = "https://www.googleapis.com/auth/drive.appdata"

    suspend fun authorize(context: Context): DriveAuthResult = suspendCancellableCoroutine { cont ->
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(SCOPE)))
            .build()
        Identity.getAuthorizationClient(context.applicationContext)
            .authorize(request)
            .addOnSuccessListener { result ->
                val pendingIntent = result.pendingIntent
                cont.resume(
                    when {
                        result.hasResolution() && pendingIntent != null ->
                            DriveAuthResult.ConsentRequired(pendingIntent.intentSender)
                        result.accessToken != null -> DriveAuthResult.Authorized(result.accessToken!!)
                        else -> DriveAuthResult.Failed("Google did not return an access token.")
                    },
                )
            }
            .addOnFailureListener { error ->
                val developerError = (error as? ApiException)?.statusCode == CommonStatusCodes.DEVELOPER_ERROR
                cont.resume(
                    DriveAuthResult.Failed(
                        if (developerError) {
                            "Google Drive is not configured for this build (missing Android OAuth client)."
                        } else {
                            error.message ?: "Google sign-in failed."
                        },
                        missingOAuthClient = developerError,
                    ),
                )
            }
    }

    /** Разбирает результат consent-интента после ConsentRequired. */
    fun fromConsentIntent(context: Context, intent: Intent?): DriveAuthResult {
        if (intent == null) return DriveAuthResult.Failed("Google sign-in was cancelled.")
        return try {
            val result = Identity.getAuthorizationClient(context.applicationContext)
                .getAuthorizationResultFromIntent(intent)
            result.accessToken?.let(DriveAuthResult::Authorized)
                ?: DriveAuthResult.Failed("Google did not return an access token.")
        } catch (error: ApiException) {
            DriveAuthResult.Failed(error.message ?: "Google sign-in failed.")
        }
    }
}
