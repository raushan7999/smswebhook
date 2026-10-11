package com.bestcallerapp.smswebhook.domain.model

/** Aggregate forwarding state of a message across all webhooks. */
enum class MessageStatus {
    /** Stored but not yet evaluated against webhook rules. */
    RECEIVED,
    PENDING,
    PROCESSING,
    RETRYING,
    SENT,
    FAILED,
    /** Evaluated; no enabled webhook matched. */
    FILTERED;

    val isActive: Boolean get() = this == RECEIVED || this == PENDING || this == PROCESSING || this == RETRYING
}

/** State of a single message → webhook delivery. */
enum class DeliveryStatus {
    PENDING,
    PROCESSING,
    RETRYING,
    SENT,
    FAILED,
    FILTERED;

    val isActive: Boolean get() = this == PENDING || this == PROCESSING || this == RETRYING
}

/** Coarse classification of why an HTTP attempt failed. */
enum class DeliveryErrorType {
    TIMEOUT,
    DNS,
    CONNECTION,
    SSL,
    HTTP_CLIENT_ERROR,
    HTTP_SERVER_ERROR,
    UNEXPECTED_REDIRECT,
    INVALID_URL,
    INVALID_CONFIGURATION,
    CREDENTIAL_UNAVAILABLE,
    WEBHOOK_REMOVED,
    WEBHOOK_DISABLED,
    UNKNOWN,
}
