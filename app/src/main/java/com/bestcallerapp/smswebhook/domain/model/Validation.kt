package com.bestcallerapp.smswebhook.domain.model

/** Which part of the webhook configuration a validation issue belongs to (maps to wizard steps). */
enum class ConfigSection { BASICS, HEADERS, FILTERS, PAYLOAD, EXTRACTION, DELIVERY }

data class ValidationIssue(
    val section: ConfigSection,
    val message: String,
    /** Optional path to the offending element, e.g. "header:2" or a filter node id. */
    val target: String? = null,
)
