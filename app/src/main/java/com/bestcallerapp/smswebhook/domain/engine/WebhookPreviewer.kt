package com.bestcallerapp.smswebhook.domain.engine

import com.bestcallerapp.smswebhook.core.utils.TimeProvider
import com.bestcallerapp.smswebhook.domain.model.Webhook
import com.bestcallerapp.smswebhook.domain.model.payload.RegexExtractor
import com.bestcallerapp.smswebhook.domain.usecase.TestWebhookUseCase
import javax.inject.Inject
import javax.inject.Singleton

data class RegexTestResult(
    val error: String? = null,
    val matched: Boolean = false,
    val match: String? = null,
    val groups: List<String?> = emptyList(),
    val timedOut: Boolean = false,
)

data class WebhookPreview(
    val matches: Boolean,
    val extracted: Map<String, String?>,
    val payload: String,
)

/** Evaluates an unsaved configuration against a sample SMS for the editor's live previews. */
@Singleton
class WebhookPreviewer @Inject constructor(
    private val filterEngine: FilterEngine,
    private val extractor: ValueExtractor,
    private val payloadBuilder: PayloadBuilder,
    private val regex: RegexEvaluator,
    private val time: TimeProvider,
) {
    fun preview(webhook: Webhook, sampleSender: String, sampleBody: String): WebhookPreview {
        val now = time.nowMillis()
        val message = TestWebhookUseCase.sampleMessage(sampleSender, sampleBody, now)
        val zone = time.zone()
        return WebhookPreview(
            matches = filterEngine.shouldReceive(webhook, FilterSubject.from(message), zone),
            extracted = extractor.extract(webhook.payload.extractors.filter { it.name.isNotBlank() }, sampleBody),
            payload = runCatching { payloadBuilder.build(webhook.payload, message, zone).body }.getOrDefault(""),
        )
    }

    fun testRegex(pattern: String, caseSensitive: Boolean, sample: String): RegexTestResult {
        val compiled = regex.compile(pattern, caseSensitive)
        if (compiled is RegexCompileResult.Invalid) return RegexTestResult(error = compiled.message)
        val valid = compiled as RegexCompileResult.Valid
        return try {
            val matcher = valid.pattern.matcher(sample)
            if (!regex.containsMatch(valid.pattern, sample) || !matcher.find()) {
                RegexTestResult(matched = false)
            } else {
                RegexTestResult(
                    matched = true,
                    match = matcher.group(),
                    groups = (1..matcher.groupCount()).map { matcher.group(it) },
                )
            }
        } catch (_: RegexTimeoutException) {
            RegexTestResult(timedOut = true, error = "Pattern took too long to evaluate; simplify it")
        }
    }

    fun extractOne(extractor: RegexExtractor, sample: String): String? = this.extractor.extractOne(extractor, sample)
}
