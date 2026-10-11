package com.bestcallerapp.smswebhook.domain.engine

import com.bestcallerapp.smswebhook.domain.model.Webhook
import com.bestcallerapp.smswebhook.domain.port.HttpRequestSpec
import javax.inject.Inject
import javax.inject.Singleton

/** Builds the HTTP request for a webhook; shared by real deliveries and "Test webhook". */
@Singleton
class WebhookRequestFactory @Inject constructor() {

    fun create(
        webhook: Webhook,
        body: String,
        payloadContentType: String,
        extraHeaders: List<Pair<String, String>> = emptyList(),
    ): HttpRequestSpec {
        val userHeaders = webhook.headers.filter { it.name.isNotBlank() }.map { it.name.trim() to it.value }
        val userContentType = userHeaders.firstOrNull { it.first.equals(CONTENT_TYPE, ignoreCase = true) }?.second
        return HttpRequestSpec(
            method = webhook.method,
            url = webhook.url.trim(),
            headers = userHeaders.filterNot { it.first.equals(CONTENT_TYPE, ignoreCase = true) } + extraHeaders,
            body = if (webhook.method.hasBody) body else null,
            contentType = userContentType ?: payloadContentType,
            timeoutMillis = webhook.timeoutSeconds * 1_000L,
        )
    }

    private companion object {
        const val CONTENT_TYPE = "Content-Type"
    }
}
