package dev.whekin.whfin.data.sms

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** The four-digit MyCredo login template; payment confirmation codes are deliberately excluded. */
object CredoLoginOtp {
    private val loginCode = Regex("""(?m)^# SMS Code:\s*(\d{4})(?!\d)""")

    fun extract(body: String): String? = loginCode.find(body)?.groupValues?.get(1)
}

/**
 * Process-only handoff from the SMS receiver to an active Credo login screen.
 *
 * There is no replay: a code received before the user starts a challenge must never fill a later
 * challenge. Neither the body nor the extracted code is persisted.
 */
class CredoOtpInbox {
    private val _codes = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val codes: SharedFlow<String> = _codes.asSharedFlow()

    fun accept(body: String): Boolean {
        val code = CredoLoginOtp.extract(body) ?: return false
        return _codes.tryEmit(code)
    }
}
