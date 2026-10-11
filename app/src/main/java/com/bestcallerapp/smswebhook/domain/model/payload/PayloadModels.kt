package com.bestcallerapp.smswebhook.domain.model.payload

enum class PayloadFormat(val defaultContentType: String) {
    /** JSON object assembled from the enabled [PayloadField]s plus extracted values. */
    JSON("application/json; charset=utf-8"),

    /** User-authored JSON with `{{variable}}` placeholders (values are JSON-escaped). */
    JSON_TEMPLATE("application/json; charset=utf-8"),

    /** Plain text rendered from a `{{variable}}` template (values inserted verbatim). */
    RAW_TEXT("text/plain; charset=utf-8"),

    /** application/x-www-form-urlencoded from the enabled fields plus extracted values. */
    FORM_URLENCODED("application/x-www-form-urlencoded"),
    ;

    val usesTemplate: Boolean get() = this == JSON_TEMPLATE || this == RAW_TEXT
    val usesFields: Boolean get() = !usesTemplate
}

enum class PayloadFieldKey(val defaultName: String, val defaultEnabled: Boolean) {
    ID("id", false),
    SENDER("sender", true),
    MESSAGE("message", true),
    TIMESTAMP("timestamp", true),
    RECEIVED_AT("received_at", false),
    SIM_SLOT("sim_slot", false),
    SUBSCRIPTION_ID("subscription_id", false),
    DEVICE("device", false),
    ;

    /** Template variable name, e.g. `{{sender}}`. Independent from the user-chosen output name. */
    val variable: String get() = defaultName
}

data class PayloadField(
    val key: PayloadFieldKey,
    val outputName: String = key.defaultName,
    val enabled: Boolean = key.defaultEnabled,
)

/** Extracts a named value from the SMS body using a regex capture group. */
data class RegexExtractor(
    val name: String,
    val pattern: String,
    val captureGroup: Int = 1,
    val caseSensitive: Boolean = false,
)

data class PayloadConfig(
    val format: PayloadFormat = PayloadFormat.JSON,
    val fields: List<PayloadField> = PayloadFieldKey.entries.map { PayloadField(it) },
    val template: String = "",
    val extractors: List<RegexExtractor> = emptyList(),
) {
    companion object {
        const val EXTRACTED_VARIABLE_PREFIX = "regex."

        const val DEFAULT_RAW_TEMPLATE = "{{sender}}: {{message}}"

        val DEFAULT_JSON_TEMPLATE = """
            {
              "sender": "{{sender}}",
              "message": "{{message}}",
              "time": "{{timestamp}}"
            }
        """.trimIndent()

        fun default() = PayloadConfig()

        fun defaultTemplateFor(format: PayloadFormat): String = when (format) {
            PayloadFormat.RAW_TEXT -> DEFAULT_RAW_TEMPLATE
            PayloadFormat.JSON_TEMPLATE -> DEFAULT_JSON_TEMPLATE
            else -> ""
        }
    }
}
