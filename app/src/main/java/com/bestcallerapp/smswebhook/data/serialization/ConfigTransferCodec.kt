package com.bestcallerapp.smswebhook.data.serialization

import com.bestcallerapp.smswebhook.domain.model.HttpMethod
import com.bestcallerapp.smswebhook.domain.model.Webhook
import com.bestcallerapp.smswebhook.domain.model.WebhookHeader
import com.bestcallerapp.smswebhook.domain.model.WebhookScope
import com.bestcallerapp.smswebhook.domain.model.filter.FilterGroup
import com.bestcallerapp.smswebhook.domain.model.payload.PayloadConfig
import com.bestcallerapp.smswebhook.domain.model.payload.PayloadFormat
import com.bestcallerapp.smswebhook.domain.model.payload.RegexExtractor
import com.bestcallerapp.smswebhook.domain.port.ConfigCodec
import com.bestcallerapp.smswebhook.domain.port.ConfigFormatException
import kotlinx.serialization.Serializable
import javax.inject.Inject

@Serializable
data class ConfigExportJson(
    val version: Int,
    val exportedAt: Long,
    val includesSecrets: Boolean,
    val webhooks: List<WebhookExportJson>,
)

@Serializable
data class WebhookExportJson(
    val name: String,
    val url: String,
    val method: String = HttpMethod.POST.name,
    val enabled: Boolean = false,
    val timeoutSeconds: Int,
    val maxRetries: Int,
    val retryDelaySeconds: Int,
    val scope: String = WebhookScope.ALL_MESSAGES.name,
    val headers: List<HeaderExportJson> = emptyList(),
    val filter: FilterNodeJson = FilterGroupJson(),
    val payload: PayloadExportJson,
)

@Serializable
data class HeaderExportJson(val name: String, val value: String? = null, val secret: Boolean = false)

@Serializable
data class PayloadExportJson(
    val format: String = PayloadFormat.JSON.name,
    val fields: List<PayloadFieldJson> = emptyList(),
    val template: String = "",
    val extractors: List<ExtractorExportJson> = emptyList(),
)

@Serializable
data class ExtractorExportJson(val name: String, val pattern: String, val group: Int = 1, val caseSensitive: Boolean = false)

/** Versioned export/import format. Secret header values are omitted unless explicitly requested. */
class JsonConfigCodec @Inject constructor() : ConfigCodec {

    override fun encode(webhooks: List<Webhook>, includeSecrets: Boolean, exportedAtMillis: Long): String =
        ConfigJson.encodeToString(
            ConfigExportJson.serializer(),
            ConfigExportJson(
                version = FORMAT_VERSION,
                exportedAt = exportedAtMillis,
                includesSecrets = includeSecrets,
                webhooks = webhooks.map { toJson(it, includeSecrets) },
            ),
        )

    override fun decode(text: String): List<Webhook> {
        val root = try {
            ConfigJson.decodeFromString(ConfigExportJson.serializer(), text)
        } catch (e: Exception) {
            throw ConfigFormatException("File is not a valid webhook configuration export", e)
        }
        if (root.version > FORMAT_VERSION) {
            throw ConfigFormatException("This export was created by a newer app version (format ${root.version})")
        }
        return root.webhooks.map(::fromJson)
    }

    private fun toJson(webhook: Webhook, includeSecrets: Boolean) = WebhookExportJson(
        name = webhook.name,
        url = webhook.url,
        method = webhook.method.name,
        enabled = webhook.enabled,
        timeoutSeconds = webhook.timeoutSeconds,
        maxRetries = webhook.maxRetries,
        retryDelaySeconds = webhook.retryDelaySeconds,
        scope = webhook.scope.name,
        headers = webhook.headers.map { header ->
            HeaderExportJson(
                name = header.name,
                value = if (header.isSecret && !includeSecrets) null else header.value,
                secret = header.isSecret,
            )
        },
        filter = FilterDefinitionCodec.toJson(webhook.filter),
        payload = PayloadExportJson(
            format = webhook.payload.format.name,
            fields = webhook.payload.fields.map(PayloadFieldsCodec::toJson),
            template = webhook.payload.template,
            extractors = webhook.payload.extractors.map {
                ExtractorExportJson(it.name, it.pattern, it.captureGroup, it.caseSensitive)
            },
        ),
    )

    private fun fromJson(json: WebhookExportJson) = Webhook(
        name = json.name,
        url = json.url,
        method = enumOrNull<HttpMethod>(json.method) ?: HttpMethod.POST,
        enabled = json.enabled,
        timeoutSeconds = json.timeoutSeconds,
        maxRetries = json.maxRetries,
        retryDelaySeconds = json.retryDelaySeconds,
        scope = enumOrNull<WebhookScope>(json.scope) ?: WebhookScope.ALL_MESSAGES,
        headers = json.headers.map { header ->
            WebhookHeader(
                name = header.name,
                value = header.value.orEmpty(),
                isSecret = header.secret,
                needsReentry = header.secret && header.value == null,
            )
        },
        filter = FilterDefinitionCodec.fromJson(json.filter) as? FilterGroup ?: FilterGroup(),
        payload = PayloadConfig(
            format = enumOrNull<PayloadFormat>(json.payload.format) ?: PayloadFormat.JSON,
            fields = PayloadFieldsCodec.normalise(json.payload.fields.mapNotNull(PayloadFieldsCodec::fromJson)),
            template = json.payload.template,
            extractors = json.payload.extractors.map { RegexExtractor(it.name, it.pattern, it.group, it.caseSensitive) },
        ),
    )

    private companion object {
        const val FORMAT_VERSION = 1
    }
}
