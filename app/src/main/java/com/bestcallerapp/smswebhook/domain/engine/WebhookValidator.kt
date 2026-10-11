package com.bestcallerapp.smswebhook.domain.engine

import com.bestcallerapp.smswebhook.core.constants.WebhookLimits
import com.bestcallerapp.smswebhook.domain.model.ConfigSection
import com.bestcallerapp.smswebhook.domain.model.ValidationIssue
import com.bestcallerapp.smswebhook.domain.model.Webhook
import com.bestcallerapp.smswebhook.domain.model.WebhookHeader
import com.bestcallerapp.smswebhook.domain.model.WebhookScope
import com.bestcallerapp.smswebhook.domain.model.filter.FilterCondition
import com.bestcallerapp.smswebhook.domain.model.filter.FilterGroup
import com.bestcallerapp.smswebhook.domain.model.filter.FilterOperator
import com.bestcallerapp.smswebhook.domain.model.filter.ValueKind
import com.bestcallerapp.smswebhook.domain.model.filter.allConditions
import com.bestcallerapp.smswebhook.domain.model.filter.depth
import com.bestcallerapp.smswebhook.domain.model.payload.PayloadConfig
import com.bestcallerapp.smswebhook.domain.model.payload.PayloadFieldKey
import com.bestcallerapp.smswebhook.domain.model.payload.PayloadFormat
import com.bestcallerapp.smswebhook.domain.model.payload.RegexExtractor
import kotlinx.serialization.json.Json
import java.net.URI
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/** Validates a complete webhook configuration before it is persisted. */
@Singleton
class WebhookValidator @Inject constructor(
    private val regex: RegexEvaluator,
    @param:Named(ALLOW_CLEARTEXT) private val allowCleartext: Boolean,
) {

    /**
     * @param allowMissingSecrets true when importing an export without credentials; such
     *   webhooks are stored disabled until the user re-enters the secret values.
     */
    fun validate(webhook: Webhook, allowMissingSecrets: Boolean = false): List<ValidationIssue> = buildList {
        addAll(validateBasics(webhook.name, webhook.url))
        addAll(validateHeaders(webhook.headers, allowMissingSecrets))
        addAll(validateFilters(webhook.scope, webhook.filter))
        addAll(validatePayload(webhook.payload))
        addAll(validateExtractors(webhook.payload.extractors))
        addAll(validateDelivery(webhook.timeoutSeconds, webhook.maxRetries, webhook.retryDelaySeconds))
    }

    fun validateBasics(name: String, url: String): List<ValidationIssue> = buildList {
        if (name.isBlank()) add(issue(ConfigSection.BASICS, "Name is required", "name"))
        if (name.length > WebhookLimits.NAME_MAX_LENGTH) {
            add(issue(ConfigSection.BASICS, "Name must be at most ${WebhookLimits.NAME_MAX_LENGTH} characters", "name"))
        }
        validateUrl(url)?.let { add(issue(ConfigSection.BASICS, it, "url")) }
    }

    /** Returns an error message or null when [url] is acceptable. */
    fun validateUrl(url: String): String? {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return "URL is required"
        if (trimmed.length > WebhookLimits.URL_MAX_LENGTH) return "URL is too long"
        if (trimmed.any { it.isWhitespace() }) return "URL must not contain spaces"
        val uri = try {
            URI(trimmed)
        } catch (_: Exception) {
            return "URL is not valid"
        }
        val scheme = uri.scheme?.lowercase()
        return when {
            scheme == null || uri.host.isNullOrBlank() -> "Enter a full URL such as https://example.com/webhook"
            scheme == "http" && !allowCleartext -> "Only HTTPS URLs are allowed"
            scheme != "https" && scheme != "http" -> "URL must start with https://"
            uri.rawUserInfo != null -> "Do not put credentials in the URL; use a header instead"
            uri.rawFragment != null -> "URL must not contain a #fragment"
            else -> null
        }
    }

    fun validateHeaders(headers: List<WebhookHeader>, allowMissingSecrets: Boolean = false): List<ValidationIssue> = buildList {
        if (headers.size > WebhookLimits.MAX_HEADERS) {
            add(issue(ConfigSection.HEADERS, "At most ${WebhookLimits.MAX_HEADERS} headers are allowed"))
        }
        headers.forEachIndexed { index, header ->
            val target = "header:$index"
            when {
                header.name.isBlank() -> add(issue(ConfigSection.HEADERS, "Header name is required", target))
                !HEADER_NAME.matches(header.name) ->
                    add(issue(ConfigSection.HEADERS, "Header \"${header.name}\" contains invalid characters", target))
                header.name.trim().lowercase() in RESERVED_HEADERS ->
                    add(issue(ConfigSection.HEADERS, "Header \"${header.name}\" is managed automatically", target))
            }
            if (header.value.any { it == '\r' || it == '\n' }) {
                add(issue(ConfigSection.HEADERS, "Header value must be a single line", target))
            } else if (!header.value.all { it == '\t' || it in ' '..'~' }) {
                // HTTP header values must be visible ASCII (what OkHttp accepts).
                add(issue(ConfigSection.HEADERS, "Header \"${header.name}\" may only contain ASCII characters", target))
            }
            if (header.needsReentry && !allowMissingSecrets) {
                add(issue(ConfigSection.HEADERS, "Re-enter the secret value for \"${header.name}\"", target))
            }
        }
    }

    fun validateFilters(scope: WebhookScope, filter: FilterGroup): List<ValidationIssue> = buildList {
        if (scope == WebhookScope.ALL_MESSAGES) return@buildList
        val conditions = filter.allConditions().toList()
        if (conditions.none { it.enabled }) {
            add(issue(ConfigSection.FILTERS, "Add at least one enabled condition, or choose \"All messages\""))
        }
        if (filter.depth() > WebhookLimits.MAX_FILTER_DEPTH) {
            add(issue(ConfigSection.FILTERS, "Groups can be nested at most ${WebhookLimits.MAX_FILTER_DEPTH} levels"))
        }
        conditions.forEach { condition -> validateCondition(condition)?.let { add(issue(ConfigSection.FILTERS, it, condition.id)) } }
    }

    /** Returns an error message for an individual condition, or null if valid. */
    fun validateCondition(condition: FilterCondition): String? {
        val operator = condition.operator
        if (operator !in condition.field.operators) return "Operator is not supported for this field"
        return when (condition.field.valueKind) {
            ValueKind.TEXT -> when {
                operator.usesKeywords ->
                    if (condition.keywords.none { it.isNotBlank() }) "Add at least one keyword" else null
                operator == FilterOperator.MATCHES_REGEX ->
                    regex.validate(condition.value)?.let { "Invalid regex: $it" }
                operator == FilterOperator.EQUALS || operator == FilterOperator.NOT_EQUALS -> null
                condition.value.isEmpty() -> "Value is required"
                else -> null
            }
            ValueKind.NUMBER -> when {
                condition.value.trim().toLongOrNull() == null -> "Enter a whole number"
                operator.usesSecondValue && condition.secondValue.trim().toLongOrNull() == null -> "Enter an upper bound"
                else -> null
            }
            ValueKind.TIME_OF_DAY -> when {
                TimeOfDay.parseMinutes(condition.value) == null -> "Enter a time as HH:mm"
                operator.usesSecondValue && TimeOfDay.parseMinutes(condition.secondValue) == null -> "Enter an end time as HH:mm"
                else -> null
            }
        }
    }

    fun validatePayload(payload: PayloadConfig): List<ValidationIssue> = buildList {
        if (payload.format.usesFields) {
            val enabled = payload.fields.filter { it.enabled }
            if (enabled.isEmpty() && payload.extractors.isEmpty()) {
                add(issue(ConfigSection.PAYLOAD, "Enable at least one field"))
            }
            enabled.forEach { field ->
                if (field.outputName.isBlank()) {
                    add(issue(ConfigSection.PAYLOAD, "Field name for ${field.key.defaultName} is required", field.key.name))
                }
            }
            val names = enabled.map { it.outputName.trim() } + payload.extractors.map { it.name.trim() }
            names.groupingBy { it }.eachCount().filter { it.value > 1 && it.key.isNotEmpty() }.keys.forEach { name ->
                add(issue(ConfigSection.PAYLOAD, "Field name \"$name\" is used more than once"))
            }
        } else {
            addAll(validateTemplate(payload))
        }
    }

    private fun validateTemplate(payload: PayloadConfig): List<ValidationIssue> = buildList {
        val template = payload.template
        if (template.isBlank()) {
            add(issue(ConfigSection.PAYLOAD, "Template is required"))
            return@buildList
        }
        val known = PayloadFieldKey.entries.map { it.variable }.toSet() +
            payload.extractors.map { PayloadConfig.EXTRACTED_VARIABLE_PREFIX + it.name }
        TemplateEngine.referencedVariables(template).filterNot { it in known }.forEach { unknown ->
            add(issue(ConfigSection.PAYLOAD, "Unknown variable {{$unknown}}"))
        }
        if (payload.format == PayloadFormat.JSON_TEMPLATE) {
            // Render with representative values (including characters that need escaping) and
            // ensure the result parses, so a template can never produce malformed JSON at runtime.
            val sample = known.associateWith { SAMPLE_VALUE }
            val rendered = TemplateEngine.render(template, sample) { value ->
                PayloadBuilder.jsonEscape(value)
            }
            try {
                Json.parseToJsonElement(rendered)
            } catch (_: Exception) {
                add(issue(ConfigSection.PAYLOAD, "Template does not produce valid JSON. Wrap placeholders in quotes, e.g. \"{{message}}\""))
            }
        }
    }

    fun validateExtractors(extractors: List<RegexExtractor>): List<ValidationIssue> = buildList {
        if (extractors.size > WebhookLimits.MAX_EXTRACTORS) {
            add(issue(ConfigSection.EXTRACTION, "At most ${WebhookLimits.MAX_EXTRACTORS} extracted fields are allowed"))
        }
        val duplicates = extractors.groupingBy { it.name.trim() }.eachCount().filter { it.value > 1 }.keys
        extractors.forEachIndexed { index, extractor ->
            validateExtractor(extractor)?.let { add(issue(ConfigSection.EXTRACTION, it, "extractor:$index")) }
            if (extractor.name.trim() in duplicates) {
                add(issue(ConfigSection.EXTRACTION, "Field \"${extractor.name}\" is defined more than once", "extractor:$index"))
            }
        }
    }

    fun validateExtractor(extractor: RegexExtractor): String? {
        if (!IDENTIFIER.matches(extractor.name)) {
            return "Field name must start with a letter and contain only letters, digits or _"
        }
        val compiled = regex.compile(extractor.pattern, extractor.caseSensitive)
        if (compiled is RegexCompileResult.Invalid) return "Invalid regex: ${compiled.message}"
        val groups = regex.groupCount((compiled as RegexCompileResult.Valid).pattern)
        if (extractor.captureGroup < 0 || extractor.captureGroup > groups) {
            return "Capture group ${extractor.captureGroup} does not exist (pattern has $groups)"
        }
        return null
    }

    fun validateDelivery(timeoutSeconds: Int, maxRetries: Int, retryDelaySeconds: Int): List<ValidationIssue> = buildList {
        if (timeoutSeconds !in WebhookLimits.MIN_TIMEOUT_SECONDS..WebhookLimits.MAX_TIMEOUT_SECONDS) {
            add(issue(ConfigSection.DELIVERY, "Timeout must be between ${WebhookLimits.MIN_TIMEOUT_SECONDS} and ${WebhookLimits.MAX_TIMEOUT_SECONDS} seconds", "timeout"))
        }
        if (maxRetries !in WebhookLimits.MIN_RETRIES..WebhookLimits.MAX_RETRIES) {
            add(issue(ConfigSection.DELIVERY, "Retries must be between ${WebhookLimits.MIN_RETRIES} and ${WebhookLimits.MAX_RETRIES}", "retries"))
        }
        if (retryDelaySeconds !in WebhookLimits.MIN_RETRY_DELAY_SECONDS..WebhookLimits.MAX_RETRY_DELAY_SECONDS) {
            add(issue(ConfigSection.DELIVERY, "Retry delay must be between ${WebhookLimits.MIN_RETRY_DELAY_SECONDS} and ${WebhookLimits.MAX_RETRY_DELAY_SECONDS} seconds", "delay"))
        }
    }

    private fun issue(section: ConfigSection, message: String, target: String? = null) =
        ValidationIssue(section, message, target)

    companion object {
        const val ALLOW_CLEARTEXT = "allowCleartextWebhooks"

        private val HEADER_NAME = Regex("^[!#$%&'*+.^_`|~0-9A-Za-z-]+$")
        private val IDENTIFIER = Regex("^[A-Za-z][A-Za-z0-9_]{0,63}$")
        private val RESERVED_HEADERS = setOf("content-length", "host", "transfer-encoding", "connection")
        private const val SAMPLE_VALUE = "Sample \"quoted\" \\ value\nline"
    }
}
