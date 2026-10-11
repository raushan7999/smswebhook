package com.bestcallerapp.smswebhook.domain.model

import com.bestcallerapp.smswebhook.domain.model.filter.FilterGroup
import com.bestcallerapp.smswebhook.domain.model.payload.PayloadConfig

/**
 * HTTP methods a webhook may use. Only POST is offered today; every component that issues
 * requests is method-agnostic so new entries only need UI exposure.
 */
enum class HttpMethod(val hasBody: Boolean) {
    POST(hasBody = true),
}

enum class WebhookScope {
    /** "Common" webhook: receives every SMS. */
    ALL_MESSAGES,

    /** Receives only messages satisfying the webhook's filter rules. */
    MATCHING_RULES,
}

data class WebhookHeader(
    val name: String,
    val value: String,
    /** Secret values are encrypted at rest and never displayed in logs. */
    val isSecret: Boolean,
    /** True when a stored secret could not be decrypted and must be re-entered. */
    val needsReentry: Boolean = false,
)

data class Webhook(
    val id: Long = 0,
    val name: String,
    val url: String,
    val method: HttpMethod = HttpMethod.POST,
    val enabled: Boolean = true,
    val timeoutSeconds: Int,
    val maxRetries: Int,
    val retryDelaySeconds: Int,
    val headers: List<WebhookHeader> = emptyList(),
    val scope: WebhookScope = WebhookScope.ALL_MESSAGES,
    val filter: FilterGroup = FilterGroup(),
    val payload: PayloadConfig = PayloadConfig.default(),
    val createdAtMillis: Long = 0,
    val updatedAtMillis: Long = 0,
)

/** Card-level information for the webhook list. */
data class WebhookOverview(
    val id: Long,
    val name: String,
    val url: String,
    val enabled: Boolean,
    val scope: WebhookScope,
    val sentCount: Int,
    val failedCount: Int,
    val pendingCount: Int,
)

object SensitiveHeaders {
    private val exactNames = setOf(
        "authorization", "proxy-authorization", "cookie", "set-cookie", "x-api-key", "api-key", "apikey",
    )
    private val fragments = listOf("token", "secret", "password", "auth", "key", "signature", "session")

    fun looksSensitive(name: String): Boolean {
        val lower = name.trim().lowercase()
        return lower in exactNames || fragments.any { lower.contains(it) }
    }
}
