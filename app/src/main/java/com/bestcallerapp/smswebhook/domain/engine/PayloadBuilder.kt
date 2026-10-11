package com.bestcallerapp.smswebhook.domain.engine

import com.bestcallerapp.smswebhook.core.utils.DateFormats
import com.bestcallerapp.smswebhook.domain.model.SmsMessage
import com.bestcallerapp.smswebhook.domain.model.payload.PayloadConfig
import com.bestcallerapp.smswebhook.domain.model.payload.PayloadFieldKey
import com.bestcallerapp.smswebhook.domain.model.payload.PayloadFormat
import com.bestcallerapp.smswebhook.domain.port.DeviceInfoProvider
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.net.URLEncoder
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

data class BuiltPayload(val body: String, val contentType: String)

/** Turns an SMS into the request body configured for a webhook. Deterministic for a given input. */
@Singleton
class PayloadBuilder @Inject constructor(
    private val extractor: ValueExtractor,
    private val deviceInfo: DeviceInfoProvider,
) {

    fun build(config: PayloadConfig, message: SmsMessage, zone: ZoneId): BuiltPayload {
        val extracted = extractor.extract(config.extractors, message.body)
        val body = when (config.format) {
            PayloadFormat.JSON -> buildJson(config, message, zone, extracted)
            PayloadFormat.FORM_URLENCODED -> buildForm(config, message, zone, extracted)
            PayloadFormat.JSON_TEMPLATE ->
                TemplateEngine.render(templateOf(config), variables(message, zone, extracted), ::jsonEscape)
            PayloadFormat.RAW_TEXT ->
                TemplateEngine.render(templateOf(config), variables(message, zone, extracted))
        }
        return BuiltPayload(body, config.format.defaultContentType)
    }

    /** All template variables for [message], including `regex.<name>` entries. */
    fun variables(message: SmsMessage, zone: ZoneId, extracted: Map<String, String?>): Map<String, String?> =
        buildMap {
            PayloadFieldKey.entries.forEach { key -> put(key.variable, fieldValue(key, message, zone)?.toString()) }
            extracted.forEach { (name, value) -> put(PayloadConfig.EXTRACTED_VARIABLE_PREFIX + name, value) }
        }

    private fun templateOf(config: PayloadConfig): String =
        config.template.ifBlank { PayloadConfig.defaultTemplateFor(config.format) }

    private fun buildJson(
        config: PayloadConfig,
        message: SmsMessage,
        zone: ZoneId,
        extracted: Map<String, String?>,
    ): String = buildJsonObject {
        config.fields.filter { it.enabled }.forEach { field ->
            put(field.outputName, toJson(fieldValue(field.key, message, zone)))
        }
        extracted.forEach { (name, value) -> put(name, value?.let(::JsonPrimitive) ?: JsonNull) }
    }.toString()

    private fun buildForm(
        config: PayloadConfig,
        message: SmsMessage,
        zone: ZoneId,
        extracted: Map<String, String?>,
    ): String {
        val pairs = config.fields.filter { it.enabled }
            .map { it.outputName to fieldValue(it.key, message, zone)?.toString().orEmpty() } +
            extracted.map { (name, value) -> name to value.orEmpty() }
        return pairs.joinToString("&") { (name, value) -> "${formEncode(name)}=${formEncode(value)}" }
    }

    private fun fieldValue(key: PayloadFieldKey, message: SmsMessage, zone: ZoneId): Any? = when (key) {
        PayloadFieldKey.ID -> message.fingerprint
        PayloadFieldKey.SENDER -> message.sender
        PayloadFieldKey.MESSAGE -> message.body
        PayloadFieldKey.TIMESTAMP -> DateFormats.isoOffset(message.sentAtMillis, zone)
        PayloadFieldKey.RECEIVED_AT -> DateFormats.isoOffset(message.receivedAtMillis, zone)
        PayloadFieldKey.SIM_SLOT -> message.simSlot?.plus(1)
        PayloadFieldKey.SUBSCRIPTION_ID -> message.subscriptionId
        PayloadFieldKey.DEVICE -> deviceInfo.description()
    }

    private fun toJson(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is Number -> JsonPrimitive(value)
        else -> JsonPrimitive(value.toString())
    }

    private fun formEncode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

    companion object {
        /** Escapes [value] for insertion inside a JSON string literal (without surrounding quotes). */
        fun jsonEscape(value: String): String = JsonPrimitive(value).toString().let { it.substring(1, it.length - 1) }
    }
}
