package com.bestcallerapp.smswebhook.domain.engine

import com.bestcallerapp.smswebhook.core.constants.WebhookLimits
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

sealed interface RetryDecision {
    /** Retry after roughly [delayMillis] (exact timing is owned by WorkManager's backoff). */
    data class Retry(val delayMillis: Long) : RetryDecision
    data object GiveUp : RetryDecision
}

/**
 * Exponential backoff policy: delay = base × 2^(attempt−1), capped at [MAX_BACKOFF_MILLIS].
 * This mirrors WorkManager's EXPONENTIAL backoff so the "next attempt" shown in the UI matches
 * what the scheduler will actually do.
 */
@Singleton
class RetryPolicy @Inject constructor() {

    /**
     * @param attemptsMade attempts already performed in the current retry chain (≥ 1).
     * @param maxRetries retries allowed after the first attempt.
     */
    fun decide(attemptsMade: Int, maxRetries: Int, baseDelaySeconds: Int): RetryDecision {
        if (attemptsMade > maxRetries) return RetryDecision.GiveUp
        return RetryDecision.Retry(backoffMillis(attemptsMade, baseDelaySeconds))
    }

    fun backoffMillis(attemptsMade: Int, baseDelaySeconds: Int): Long {
        val base = baseDelaySeconds.coerceAtLeast(WebhookLimits.MIN_RETRY_DELAY_SECONDS) * 1_000L
        val exponent = (attemptsMade - 1).coerceIn(0, MAX_EXPONENT)
        return min(base shl exponent, MAX_BACKOFF_MILLIS)
    }

    companion object {
        /** WorkManager's maximum backoff (5 hours). */
        const val MAX_BACKOFF_MILLIS = 5L * 60 * 60 * 1_000
        private const val MAX_EXPONENT = 20
    }
}
