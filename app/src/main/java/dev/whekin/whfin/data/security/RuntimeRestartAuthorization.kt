package dev.whekin.whfin.data.security

/** A public Activity intent is not proof that the owner has unlocked the app. */
internal class RuntimeRestartAuthorization(private val now: () -> Long = { System.nanoTime() / 1_000_000 }) {
    private var token: String? = null
    private var issuedAt = 0L

    @Synchronized fun issue(): String = java.util.UUID.randomUUID().toString().also {
        token = it
        issuedAt = now()
    }

    @Synchronized fun consume(candidate: String?): Boolean {
        if (candidate == null || candidate != token) return false
        token = null
        return now() - issuedAt in 0..30_000L
    }
}
