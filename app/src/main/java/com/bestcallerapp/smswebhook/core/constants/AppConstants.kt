package com.bestcallerapp.smswebhook.core.constants

/** Central place for limits and identifiers shared across layers. */
object AppConstants {
    const val USER_AGENT_PRODUCT = "SmsWebhook"

    /** Maximum number of response-body characters persisted for a delivery or log entry. */
    const val MAX_STORED_RESPONSE_CHARS = 4_000

    /** Maximum response bytes read from the network before truncating. */
    const val MAX_RESPONSE_READ_BYTES = 16_384L

    /** Upper bound for a single regex evaluation, protecting against catastrophic backtracking. */
    const val REGEX_TIMEOUT_MS = 250L

    const val IDEMPOTENCY_HEADER = "X-Idempotency-Key"
    const val DELIVERY_ID_HEADER = "X-SmsWebhook-Delivery"

    const val PREVIEW_LENGTH = 160
    const val RECENT_MESSAGES_LIMIT = 5
    const val CLEANUP_BATCH_SIZE = 500
}

object WebhookLimits {
    const val NAME_MAX_LENGTH = 100
    const val URL_MAX_LENGTH = 2_048
    const val MIN_TIMEOUT_SECONDS = 1
    const val MAX_TIMEOUT_SECONDS = 120
    const val MIN_RETRIES = 0
    const val MAX_RETRIES = 10

    /** WorkManager enforces a 10 second minimum backoff. */
    const val MIN_RETRY_DELAY_SECONDS = 10
    const val MAX_RETRY_DELAY_SECONDS = 3_600
    const val MAX_HEADERS = 30
    const val MAX_FILTER_DEPTH = 3
    const val MAX_EXTRACTORS = 20
}

object WorkNames {
    const val PROCESS_MESSAGE_PREFIX = "process-message-"
    const val DELIVERY_PREFIX = "deliver-"
    const val INGEST_FALLBACK_PREFIX = "ingest-sms-"
    const val PERIODIC_MAINTENANCE = "periodic-maintenance"
    const val ONE_OFF_CLEANUP = "one-off-cleanup"
    const val RECONCILE = "reconcile-queue"

    const val TAG_DELIVERY = "delivery"
    const val TAG_PROCESSING = "processing"
}

/** Public web pages (site source: <project>/website, served at smswebhook.bestcallerapp.com). */
object AppLinks {
    const val WEBSITE = "https://smswebhook.bestcallerapp.com/"
    const val GUIDE = "https://smswebhook.bestcallerapp.com/guide/"
    const val PRIVACY = "https://smswebhook.bestcallerapp.com/privacy/"
    const val TERMS = "https://smswebhook.bestcallerapp.com/terms/"
    const val SUPPORT_EMAIL = "smswebhook@bestcallerapp.com"
}
