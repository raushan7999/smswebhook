package com.bestcallerapp.smswebhook.core.network

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Produces a log-safe representation of a webhook URL: scheme, host, port and path are kept,
 * query parameter values and user-info are replaced, because tokens are often passed there.
 */
object UrlRedactor {
    fun redact(url: String): String {
        val parsed = url.toHttpUrlOrNull() ?: return "<invalid url>"
        val builder = parsed.newBuilder().username("").password("")
        if (parsed.querySize > 0) {
            builder.query(null)
            parsed.queryParameterNames.forEach { name -> builder.addQueryParameter(name, "***") }
        }
        return builder.build().toString()
    }
}
