package com.bestcallerapp.smswebhook.domain.engine

/**
 * Minimal, deterministic `{{variable}}` substitution. There are no expressions, loops or
 * function calls — placeholders are replaced by looked-up values and nothing else, so a
 * template can never execute code. Unknown or null variables render as an empty string.
 */
object TemplateEngine {
    private val PLACEHOLDER = Regex("""\{\{\s*([A-Za-z0-9_.]+)\s*\}\}""")

    fun render(template: String, variables: Map<String, String?>, escape: (String) -> String = { it }): String =
        PLACEHOLDER.replace(template) { match ->
            escape(variables[match.groupValues[1]].orEmpty())
        }

    fun referencedVariables(template: String): Set<String> =
        PLACEHOLDER.findAll(template).map { it.groupValues[1] }.toSet()
}
