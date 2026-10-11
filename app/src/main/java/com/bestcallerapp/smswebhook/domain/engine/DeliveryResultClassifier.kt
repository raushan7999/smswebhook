package com.bestcallerapp.smswebhook.domain.engine

import com.bestcallerapp.smswebhook.domain.model.AttemptOutcome
import com.bestcallerapp.smswebhook.domain.model.DeliveryErrorType
import com.bestcallerapp.smswebhook.domain.port.HttpResult
import javax.inject.Inject
import javax.inject.Singleton

data class Classification(
    val outcome: AttemptOutcome,
    val errorType: DeliveryErrorType? = null,
    val errorMessage: String? = null,
)

/**
 * Success criteria: a 2xx response. Transient problems (network errors, 408, 425, 429, 5xx) are
 * retryable; other 4xx/3xx responses and configuration problems are permanent because repeating
 * the identical request cannot succeed.
 */
@Singleton
class DeliveryResultClassifier @Inject constructor() {

    fun classify(result: HttpResult): Classification = when (result) {
        is HttpResult.Response -> classifyStatus(result.code)
        is HttpResult.Failure -> Classification(
            outcome = if (result.type in RETRYABLE_ERRORS) AttemptOutcome.RETRYABLE_FAILURE else AttemptOutcome.PERMANENT_FAILURE,
            errorType = result.type,
            errorMessage = result.message,
        )
    }

    private fun classifyStatus(code: Int): Classification = when {
        code in 200..299 -> Classification(AttemptOutcome.SUCCESS)
        code in RETRYABLE_STATUS -> Classification(
            AttemptOutcome.RETRYABLE_FAILURE, DeliveryErrorType.HTTP_CLIENT_ERROR, "HTTP $code",
        )
        code >= 500 -> Classification(AttemptOutcome.RETRYABLE_FAILURE, DeliveryErrorType.HTTP_SERVER_ERROR, "HTTP $code")
        code in 300..399 -> Classification(
            AttemptOutcome.PERMANENT_FAILURE, DeliveryErrorType.UNEXPECTED_REDIRECT, "Unexpected redirect (HTTP $code)",
        )
        else -> Classification(AttemptOutcome.PERMANENT_FAILURE, DeliveryErrorType.HTTP_CLIENT_ERROR, "HTTP $code")
    }

    private companion object {
        val RETRYABLE_STATUS = setOf(408, 425, 429)
        val RETRYABLE_ERRORS = setOf(
            DeliveryErrorType.TIMEOUT,
            DeliveryErrorType.DNS,
            DeliveryErrorType.CONNECTION,
            DeliveryErrorType.UNKNOWN,
        )
    }
}
