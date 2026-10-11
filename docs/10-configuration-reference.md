# 10 · Configuration reference

> **Purpose:** single reference for settings, defaults, validation limits and file formats.
> Source of truth: `core/constants/AppConstants.kt` and `domain/model/AppSettings.kt`.

## 1. App settings

| Setting (UI) | DataStore key | Type | Default | Notes |
|---|---|---|---|---|
| **SMS listener** / **Forward incoming SMS** | `listener_enabled` | Boolean | `true` | Also toggles the receiver component |
| **Retention** | `retention_mode`, `retention_value` | `forever` \| `age` \| `count` + Int | age, 30 days | Presets: 7/30/365 days; 100/1,000/10,000/100,000 messages; custom 10–1,000,000 |
| **Delete SMS after forwarding** | `delete_sms_policy` | `DISABLED` \| `AFTER_ALL_SUCCEEDED` \| `AFTER_FIRST_SUCCESS` | `DISABLED` | Unavailable: requires default SMS app role |
| **Default retry count** | `default_max_retries` | Int 0–10 | 5 | Applied to new webhooks |
| **Default retry delay** | `default_retry_delay_seconds` | Int 10–3600 | 30 | Applied to new webhooks |
| **Default timeout** | `default_timeout_seconds` | Int 1–120 | 15 | Applied to new webhooks |
| **Only on Wi-Fi** | `wifi_only` | Boolean | `false` | Applies to queued deliveries too |
| **Keep delivery logs** | `keep_delivery_logs` | Boolean | `true` | Off also deletes existing logs at the next cleanup |
| **Log retention** | `log_retention_days` | Int (7/30/90/365) | 30 | |

## 2. Webhook limits (`WebhookLimits`)

| Limit | Value |
|---|---|
| Name length | 1–100 characters |
| URL length | ≤ 2,048 characters; absolute; `https` (also `http` in debug builds); no user-info; no fragment |
| Timeout | 1–120 s |
| Maximum retries | 0–10 |
| First retry delay | 10–3,600 s |
| Headers | ≤ 30; token names; printable-ASCII values; reserved: Host, Content-Length, Transfer-Encoding, Connection |
| Filter nesting | ≤ 3 levels |
| Extractors | ≤ 20; names `[A-Za-z][A-Za-z0-9_]{0,63}` |

## 3. Internal constants (`AppConstants`)

| Constant | Value | Purpose |
|---|---|---|
| `REGEX_TIMEOUT_MS` | 250 | Time budget for one regex evaluation |
| `MAX_RESPONSE_READ_BYTES` | 16,384 | Bytes of a response body read from the network |
| `MAX_STORED_RESPONSE_CHARS` | 4,000 | Characters of a response stored with a delivery/log |
| `PREVIEW_LENGTH` | 160 | Message preview length in lists |
| `RECENT_MESSAGES_LIMIT` | 5 | Messages on the Home screen |
| `CLEANUP_BATCH_SIZE` | 500 | Rows per retention delete batch |
| `IDEMPOTENCY_HEADER` | `X-Idempotency-Key` | Sent with every delivery |
| `DELIVERY_ID_HEADER` | `X-SmsWebhook-Delivery` | Sent with every delivery |
| Receiver ingest budget | 6 s | Inside `goAsync()` before WorkManager fallback |
| Paging page size | 50 | History, webhook history, logs |

## 4. Configuration export format

Produced by **Export configuration** (`data/serialization/ConfigTransferCodec.kt`), UTF-8 JSON:

```json
{
  "version": 1,
  "exportedAt": 1791212400000,
  "includesSecrets": false,
  "webhooks": [
    {
      "name": "Payment Server",
      "url": "https://payments.example.com/webhooks/sms",
      "method": "POST",
      "enabled": true,
      "timeoutSeconds": 15,
      "maxRetries": 5,
      "retryDelaySeconds": 30,
      "scope": "MATCHING_RULES",
      "headers": [
        { "name": "Authorization", "value": null, "secret": true },
        { "name": "X-Source", "value": "android-sms", "secret": false }
      ],
      "filter": {
        "type": "group", "id": "…", "operator": "AND", "negate": false, "enabled": true,
        "children": [
          { "type": "condition", "id": "…", "field": "SENDER", "operator": "CONTAINS", "value": "HDFC",
            "secondValue": "", "keywords": [], "caseSensitive": false, "negate": false, "enabled": true }
        ]
      },
      "payload": {
        "format": "JSON",
        "fields": [ { "key": "SENDER", "name": "sender", "enabled": true } ],
        "template": "",
        "extractors": [ { "name": "amount", "pattern": "Rs ([0-9,]+)", "group": 1, "caseSensitive": false } ]
      }
    }
  ]
}
```

| Rule | Behaviour |
|---|---|
| Secrets | `value` is `null` for secret headers unless exported with secrets (user confirmation required) |
| Import | Every webhook is added as **new** and **disabled**; existing webhooks are untouched |
| Missing secrets | Imported, flagged for re-entry; cannot be enabled for delivery until re-entered |
| Validation | Each webhook is validated; invalid ones are skipped with a reason |
| Versioning | Files with `version` greater than the app supports are rejected; unknown keys are ignored |
| Size | Import reads at most 2 MB |

## 5. Build configuration

| Item | Location | Value |
|---|---|---|
| Version | `app/build.gradle.kts` → `appVersion` | `1.0.0`; versionCode = major×10000 + minor×100 + patch |
| Cleartext webhooks | `BuildConfig.ALLOW_CLEARTEXT_WEBHOOKS` | `true` debug, `false` qa/release |
| Signing properties | `local.properties` → `smswebhook.keystore.properties` | default `release_item/1_signing_key/keystore.properties` |
| Network security | `src/main/res/xml/network_security_config.xml` (release), `src/debug/...` (debug) | HTTPS + system CAs / HTTP + user CAs |
| Website links | `AppLinks` | `https://smswebhook.bestcallerapp.com/` and sub-pages |
