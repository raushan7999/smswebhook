package com.bestcallerapp.smswebhook.domain.engine

import com.bestcallerapp.smswebhook.core.constants.AppConstants
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException
import javax.inject.Inject
import javax.inject.Singleton

class RegexTimeoutException : RuntimeException("Regex evaluation exceeded its time budget")

sealed interface RegexCompileResult {
    data class Valid(val pattern: Pattern) : RegexCompileResult
    data class Invalid(val message: String) : RegexCompileResult
}

/**
 * Compiles and evaluates user-supplied regular expressions safely:
 * compiled patterns are cached, and every evaluation runs against a [CharSequence] that aborts
 * once a time budget is exceeded so a pathological pattern cannot stall message processing.
 */
@Singleton
class RegexEvaluator(private val timeoutMillis: Long) {

    @Inject constructor() : this(AppConstants.REGEX_TIMEOUT_MS)

    private data class CacheKey(val pattern: String, val caseSensitive: Boolean)

    private val cache = object : LinkedHashMap<CacheKey, Pattern>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<CacheKey, Pattern>?) = size > CACHE_SIZE
    }

    fun compile(pattern: String, caseSensitive: Boolean = true): RegexCompileResult {
        if (pattern.isEmpty()) return RegexCompileResult.Invalid("Pattern is empty")
        val key = CacheKey(pattern, caseSensitive)
        synchronized(cache) { cache[key] }?.let { return RegexCompileResult.Valid(it) }
        return try {
            val flags = if (caseSensitive) 0 else Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
            val compiled = Pattern.compile(pattern, flags)
            synchronized(cache) { cache[key] = compiled }
            RegexCompileResult.Valid(compiled)
        } catch (e: PatternSyntaxException) {
            val position = if (e.index >= 0) " near position ${e.index + 1}" else ""
            RegexCompileResult.Invalid("${e.description}$position")
        } catch (e: IllegalArgumentException) {
            RegexCompileResult.Invalid(e.message ?: "Invalid pattern")
        }
    }

    /** Returns an error message, or null when [pattern] is valid. */
    fun validate(pattern: String): String? =
        (compile(pattern) as? RegexCompileResult.Invalid)?.message

    /** @throws RegexTimeoutException */
    fun containsMatch(pattern: Pattern, input: CharSequence): Boolean =
        pattern.matcher(deadlined(input)).find()

    /**
     * Returns the requested capture group of the first match, or null when there is no match or
     * the group did not participate. @throws RegexTimeoutException
     */
    fun extractGroup(pattern: Pattern, input: CharSequence, group: Int): String? {
        val matcher = pattern.matcher(deadlined(input))
        if (!matcher.find() || group > matcher.groupCount()) return null
        return matcher.group(group)
    }

    fun groupCount(pattern: Pattern): Int = pattern.matcher("").groupCount()

    private fun deadlined(input: CharSequence): CharSequence =
        DeadlineCharSequence(input, System.nanoTime() + timeoutMillis * 1_000_000)

    private class DeadlineCharSequence(
        private val inner: CharSequence,
        private val deadlineNanos: Long,
    ) : CharSequence {
        override val length: Int get() = inner.length

        override fun get(index: Int): Char {
            if (System.nanoTime() > deadlineNanos) throw RegexTimeoutException()
            return inner[index]
        }

        override fun subSequence(startIndex: Int, endIndex: Int): CharSequence =
            DeadlineCharSequence(inner.subSequence(startIndex, endIndex), deadlineNanos)

        override fun toString(): String = inner.toString()
    }

    private companion object {
        const val CACHE_SIZE = 128
    }
}
