package com.bestcallerapp.smswebhook.domain.engine

import com.bestcallerapp.smswebhook.domain.model.payload.RegexExtractor
import javax.inject.Inject
import javax.inject.Singleton

/** Applies [RegexExtractor]s to an SMS body. Missing matches yield null values. */
@Singleton
class ValueExtractor @Inject constructor(
    private val regex: RegexEvaluator,
) {
    fun extract(extractors: List<RegexExtractor>, body: String): Map<String, String?> =
        extractors.associate { extractor -> extractor.name to extractOne(extractor, body) }

    fun extractOne(extractor: RegexExtractor, body: String): String? {
        val compiled = regex.compile(extractor.pattern, extractor.caseSensitive) as? RegexCompileResult.Valid
            ?: return null
        return try {
            regex.extractGroup(compiled.pattern, body, extractor.captureGroup)
        } catch (_: RegexTimeoutException) {
            null
        }
    }
}
