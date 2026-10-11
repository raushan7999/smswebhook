# SMS Webhook

An Android app that receives SMS on the device and forwards selected messages to one or more
user-configured HTTP webhooks — with per-webhook filters, payload formats, regex extraction,
retries, delivery history and logs.

> **Full documentation:** [`docs/`](docs/README.md): user guide, architecture, component specifications,
> data model, security, testing, build and release, website, server integration guide, troubleshooting,
> architecture decision records and changelog.

```
Incoming SMS → SmsReceiver → Room (deduplicated) → MessageProcessingWorker
  → FilterEngine (per webhook) → PayloadBuilder → WebhookDeliveryWorker (WorkManager)
  → HTTP POST → delivery status + attempt log
```

The local Room database is the source of truth. An SMS is persisted before any network work is
attempted, and every delivery is a durable WorkManager job. Nothing is lost when the device is
offline, the process is killed or the phone restarts.

---

## Table of contents

1. [Architecture](#architecture)
2. [Folder structure](#folder-structure)
3. [Technology stack](#technology-stack)
4. [How SMS reception works](#how-sms-reception-works)
5. [Permissions](#permissions)
6. [How webhooks work](#how-webhooks-work)
7. [Filter system](#filter-system)
8. [Payload system](#payload-system)
9. [Regex extraction](#regex-extraction)
10. [Database architecture](#database-architecture)
11. [Duplicate prevention & idempotency](#duplicate-prevention--idempotency)
12. [Retry system](#retry-system)
13. [Data retention](#data-retention)
14. [Security](#security)
15. [Testing](#testing)
16. [Build instructions](#build-instructions)
17. [Release instructions](#release-instructions)
18. [Known Android limitations](#known-android-limitations)
19. [Google Play considerations](#google-play-considerations)

---

## Architecture

Clean architecture with three layers plus Android entry points:

| Layer | Responsibility | Depends on |
|---|---|---|
| `domain` | Models, filter engine, payload builder, retry policy, validation, use cases, repository & port **interfaces**. Pure Kotlin (no Android framework). | nothing |
| `data` | Room, DataStore, OkHttp, Keystore, Android system adapters — **implementations** of domain interfaces. | domain |
| `presentation` | Jetpack Compose screens + ViewModels (state holders only — no business logic). | domain |
| `receiver`, `worker` | Android entry points: they translate platform events into use-case calls. | domain |
| `di` | Hilt modules binding interfaces to implementations. | all |

Main pipeline components, one responsibility each:

| Component | Responsibility |
|---|---|
| `SmsReceiver` + `SmsIntentParser` | Parse the broadcast (multipart, SIM), hand to `IngestSmsUseCase`. |
| `IngestSmsUseCase` | Fingerprint, insert-if-absent, enqueue processing. |
| `ProcessMessageUseCase` | Evaluate every enabled webhook, render payloads, create deliveries atomically. |
| `FilterEngine` | Evaluate the AND/OR/NOT filter tree. |
| `PayloadBuilder` / `TemplateEngine` / `ValueExtractor` | Build the request body. |
| `WorkManagerScheduler` | Durable, unique, network-constrained work. |
| `DeliverWebhookUseCase` | Claim → send → classify → record → retry or finish. |
| `DeliveryResultClassifier` / `RetryPolicy` | Success criteria and backoff decisions. |
| `DeliveryRepository` | State transitions + derived message status, in one transaction. |

## Folder structure

```
app/src/main/java/com/bestcallerapp/smswebhook/
├── SmsWebhookApp.kt              Hilt app, WorkManager configuration, startup reconcile
├── core/
│   ├── constants/                Limits, work names
│   ├── extensions/               String helpers
│   ├── logging/                  AppLogger (no sensitive data) + logcat impl
│   ├── network/                  Connectivity observer, URL redaction
│   ├── security/                 SecretCipher + Android Keystore AES-GCM impl
│   └── utils/                    Time provider, hashing, date formats, qualifiers
├── domain/
│   ├── model/                    SmsMessage, Webhook, Delivery, filter/, payload/, settings
│   ├── engine/                   FilterEngine, RegexEvaluator, PayloadBuilder, TemplateEngine,
│   │                             ValueExtractor, MessageFingerprinter, RetryPolicy,
│   │                             DeliveryResultClassifier, MessageStatusAggregator,
│   │                             WebhookValidator, WebhookRequestFactory, WebhookPreviewer
│   ├── port/                     Interfaces to the outside world (HTTP, scheduler, SMS deletion…)
│   ├── repository/               Repository interfaces
│   └── usecase/                  Ingest, Process, Deliver, Retry, Test, Save/Duplicate,
│                                 Retention, Reconcile, Export/Import, Forwarding toggle
├── data/
│   ├── local/{database,dao,entity}   Room
│   ├── remote/                   OkHttpWebhookSender
│   ├── repository/               Repository implementations + mappers
│   ├── serialization/            Versioned JSON for filter trees, payload fields, export files
│   ├── settings/                 DataStore settings
│   └── system/                   SMS deletion capability, device info, receiver toggle, battery
├── receiver/                     SmsReceiver, SmsIntentParser
├── worker/                       Processing, delivery, cleanup, reconcile, ingest-fallback workers
├── di/                           Hilt modules
└── presentation/
    ├── navigation/               Type-safe routes, bottom navigation
    ├── theme/                    Material 3 colours (light/dark), status colours
    ├── components/               Shared composables (status badge, panels, dialogs…)
    ├── home/  messages/  message_detail/
    ├── webhook/  webhook_detail/  webhook_editor/ (7-step wizard)
    ├── logs/  settings/
```

There is deliberately **no foreground service**: a manifest receiver plus WorkManager covers
reception and reliable delivery without a permanent notification or battery cost.

## Technology stack

Kotlin 2.3 · Jetpack Compose (Material 3) · Navigation Compose (type-safe routes) · Hilt ·
Room 2.8 (+ Paging) · WorkManager 2.11 · DataStore · OkHttp 5 · kotlinx.serialization ·
Coroutines/Flow · Android Keystore. Min SDK 26, target SDK 37, AGP 9.4.

OkHttp is used directly instead of Retrofit: every webhook has its own user-supplied URL, headers
and body, so there is no fixed API surface for Retrofit to describe.

## How SMS reception works

1. The system delivers `SMS_RECEIVED` to `SmsReceiver`. The receiver is protected by
   `android:permission="android.permission.BROADCAST_SMS"`, so only the telephony stack can
   trigger it.
2. `SmsIntentParser` reads all PDUs, groups multipart parts by sender, concatenates bodies in
   order, and reads the subscription id / SIM slot when the device reports them.
3. Inside `goAsync()` (max ~6 s), `IngestSmsUseCase` stores the message with a fingerprint and
   enqueues `MessageProcessingWorker` (expedited when quota allows). **No network I/O happens in
   the receiver.**
4. If the database write fails, the SMS is handed to `IngestSmsWorker` through WorkManager's
   own storage, so a transient database problem does not lose it.
5. `MessageProcessingWorker` evaluates every enabled webhook and creates one delivery per webhook
   (`PENDING` or `FILTERED`), then enqueues one `WebhookDeliveryWorker` per matching webhook.

"Forward incoming SMS" (Home/Settings) enables or disables the receiver **component** itself.
When off, Android does not wake the app for SMS at all.

## Permissions

| Permission | Why |
|---|---|
| `RECEIVE_SMS` (runtime) | Receive new SMS. The only SMS permission requested. |
| `INTERNET` | Deliver to webhooks. |
| `ACCESS_NETWORK_STATE` | Show online/offline status; WorkManager network constraints. |
| `FOREGROUND_SERVICE` | Expedited WorkManager jobs run as a brief foreground service on Android ≤ 11. |

`READ_SMS` and `SEND_SMS` are **not** requested. The app never reads the existing inbox and never
sends SMS. The Home screen explains the permission before requesting it, and links to App info
when it has been permanently denied.

## How webhooks work

Each webhook has: name, URL (HTTPS), method (POST — `HttpMethod` is an enum so more can be
added), enabled flag, timeout, max retries, first retry delay, headers, scope/filters, payload
format and regex extractors.

- **Add / edit**: 7-step wizard (Webhook → Headers & auth → Filters → Payload → Extraction →
  Retry & timeout → Review). Each step validates before moving on, and the Review step validates
  the whole configuration. Filters, extraction and payload show **live previews** against an
  editable sample SMS.
- **Test**: sends a sample payload and shows success, HTTP status, response time, request and
  response body. Tests are never recorded as deliveries.
- **Duplicate**: copies the configuration, **disabled**, so a copy never double-delivers
  unexpectedly.
- **Enable/disable, delete, delivery history, failed deliveries, retry failed**: on the list
  card and the detail screen.
- **Success criteria**: any 2xx response. Redirects are not followed, so payloads never go to a
  host you did not configure.
- Every request carries `X-Idempotency-Key` (stable per message/webhook) and
  `X-SmsWebhook-Delivery`, so receivers can deduplicate. Delivery is **at-least-once**: if the
  server processes a request but the response is lost, the retry carries the same key.

## Filter system

A webhook is either **All messages** (common webhook) or **Only matching messages**. One SMS can
match any number of webhooks.

Rules form a tree: groups combine children with **AND** / **OR**, and any group or condition can
be negated (**NOT**). Groups nest up to 3 levels. Disabled nodes are ignored, and an empty group
imposes no constraint.

| Field | Operators |
|---|---|
| Sender, Message | equals, does not equal, contains, does not contain, starts with, ends with, matches regex, contains **any** keyword, contains **all** keywords — each optionally case-sensitive |
| SIM slot (1-based), Subscription ID, Message length | =, ≠, >, <, between |
| Time received (HH:mm) | after, before, between (windows may wrap past midnight) |

`FilterEngine` (domain) is pure and deterministic. A condition that cannot be evaluated (invalid
regex, missing SIM info, unparsable number) is `false` before negation. Regex uses *find*
semantics and supports inline flags such as `(?i)`.

**Regex safety**: patterns are validated before saving, with a clear error message. Compiled
patterns are cached, and every evaluation runs against a time-limited `CharSequence` (250 ms), so a
catastrophic pattern such as `^(a+)+$` cannot stall processing. "Test regex" lets you try a
pattern against sample text and shows the match and capture groups.

Filter trees are stored as versioned JSON (`webhook_filters.definition`). Unknown
fields/operators written by a newer app version are loaded **disabled** rather than guessed.

## Payload system

| Format | Content-Type | Body |
|---|---|---|
| JSON | `application/json` | Object from enabled fields (with custom names) + extracted values |
| Custom JSON template | `application/json` | `{{variable}}` template; values are JSON-escaped |
| Raw text | `text/plain` | `{{variable}}` template, e.g. `{{sender}}: {{message}}` |
| Form data | `application/x-www-form-urlencoded` | Enabled fields + extracted values |

Fields: `id` (stable SHA-256 fingerprint), `sender`, `message`, `timestamp` (SMS-centre time,
ISO-8601 with offset), `received_at`, `sim_slot` (1-based), `subscription_id`, `device`
(model + Android version; off by default). Default JSON:

```json
{ "sender": "HDFCBK", "message": "Your OTP is 123456", "timestamp": "2026-10-05T20:30:00+05:30" }
```

The template engine only substitutes placeholders: no expressions, loops or code execution.
Unknown variables are rejected at save time. JSON templates are test-rendered with hostile
sample values (quotes, backslashes, newlines) and must parse as JSON before they can be saved.

A user-defined `Content-Type` header overrides the format's default. The payload is rendered
**once** when the message is processed and stored with the delivery, so retries send identical
bytes even if the webhook is edited later.

## Regex extraction

Define named fields as *(name, regex, capture group, case-sensitive)*:

```
Field: amount   Regex: credited with Rs ([0-9,]+)   Group: 1   →  "5,000"
Field: account  Regex: account (X+\d{4})            Group: 1   →  "XXXX1234"
```

Extracted values are added to JSON/form payloads under their names (`null` when there is no
match) and are available in templates as `{{regex.amount}}`. Names, patterns and group numbers
are validated, and the wizard shows the extracted value for the sample SMS live.

## Database architecture

Room, version 1, schema exported to `app/schemas/` (used by migration tests).

| Table | Purpose | Key indexes |
|---|---|---|
| `sms_messages` | Received SMS + derived aggregate `status` | `fingerprint` UNIQUE, `received_at`, `(status, received_at)` |
| `webhooks` | Basic config | — |
| `webhook_headers` | Headers; secret values Keystore-encrypted | `webhook_id` |
| `webhook_filters` | Filter tree JSON (1 row per webhook) | PK `webhook_id` |
| `webhook_payload_config` | Format, field settings JSON, template | PK `webhook_id` |
| `regex_extractors` | Extracted fields | `webhook_id` |
| `webhook_deliveries` | One row per message × webhook: status, attempts, HTTP status, response, error, frozen payload, next attempt | `(message_id, webhook_id)` UNIQUE, `(webhook_id, status)`, `status` |
| `delivery_attempts` | Attempt log (no header values; URL query values redacted) | `delivery_id`, `message_id`, `started_at`, `(outcome, started_at)` |

All child tables use `ON DELETE CASCADE`: deleting a message or webhook removes its deliveries
and logs. Deleting a webhook also recomputes the status of the affected messages in the same
transaction, so no message is left "pending" for a webhook that no longer exists.

**Scale (100,000+ messages)**: lists use Room `PagingSource` (pages of 50) and only project a
160-character preview. The unfiltered history walks the `received_at` index, so there is no
full-table sort. Counts use indexed `COUNT(*)`. Cleanup deletes in batches of 500 to keep
transactions short. The dashboard never loads full tables.

**Migrations**: `AppDatabase.MIGRATIONS` is explicit and destructive fallback is intentionally
disabled. Bump `VERSION`, add a `Migration`, commit the new schema JSON, and extend
`DatabaseSchemaTest`.

## Duplicate prevention & idempotency

- **Fingerprint** = SHA-256 of `v1 | normalised sender | body | service-centre timestamp |
  subscription id`. The service-centre timestamp comes from the PDU, so a re-delivered broadcast
  produces the same fingerprint even though the local receive time differs. The local time is
  deliberately excluded. (Trade-off: two identical texts from the same sender on the same SIM
  within the same SMSC second are treated as one.)
- `sms_messages.fingerprint` is UNIQUE (`INSERT OR IGNORE`). If a duplicate broadcast arrives
  while the original is still unprocessed, processing is re-queued.
- `webhook_deliveries(message_id, webhook_id)` is UNIQUE, so re-processing never creates a second
  delivery.
- All work is **unique** per message/delivery (`KEEP`), so the same delivery never runs twice
  concurrently.
- A delivery is **claimed** atomically (`UPDATE … WHERE status IN (active)`). A delivery that is
  already `SENT` is never sent again. `SENT` is recorded only after a 2xx response.

## Retry system

| Outcome | Examples | Action |
|---|---|---|
| Success | 2xx | `SENT` |
| Retryable | timeout, DNS, connection error, transient TLS/handshake I/O errors, 408, 425, 429, 5xx | `RETRYING`, WorkManager backoff |
| Permanent | other 4xx, redirects, certificate/hostname verification failures, invalid config, missing credentials | `FAILED` |

- Backoff is **exponential** from the webhook's *first retry delay* (minimum 10 s, enforced by
  WorkManager): e.g. 30 s → 1 min → 2 min → 4 min…, capped at 5 h. The UI shows the expected next
  attempt.
- *Maximum retries* = retries after the first attempt (0–10). When exhausted → `FAILED`.
- Delivery work requires connectivity (or an unmetered network with **Only Wi-Fi**), so offline
  deliveries simply wait and resume when the network returns. Changing *Only Wi-Fi* updates the
  constraints of already-queued deliveries.
- **Manual retry** (message detail, webhook detail, "Retry failed") resets `FAILED → PENDING`
  and starts a fresh retry budget.
- **Recovery**: on every app start, `ReconcileWorker` re-enqueues unprocessed messages and active
  deliveries. Enqueueing is idempotent, so this repairs anything lost if the process was killed
  between steps.

Message status is derived from its deliveries, in the same transaction:
`PROCESSING > RETRYING > PENDING > FAILED > SENT`. If every delivery is `FILTERED`, or there
were none, the message is `FILTERED`.

## Data retention

Settings → History: keep **7 / 30 / 365 days**, the newest **100 / 1,000 / 10,000 / 100,000 /
custom** messages, or **forever** (default: 30 days). Delivery logs have their own retention
(7–365 days) and can be turned off.

`CleanupWorker` runs daily (battery not low) and immediately when retention settings change. It
deletes in batches and **never removes messages with active deliveries** (received, pending,
sending or retrying). Deliveries and logs of deleted messages are removed by cascade. Clear
history / logs / all data are available in Settings.

## Security

- **Transport**: HTTPS required in release builds (validator + network security config with
  cleartext disabled and system CAs only). TLS verification is never relaxed. Redirects are not
  followed. Debug builds allow HTTP and user CAs for local testing only.
- **Secrets**: header values marked secret (auto-suggested for `Authorization`, `*token*`,
  `*key*`…) are encrypted with AES-256-GCM using a non-exportable **Android Keystore** key. They
  are masked in the UI and never logged.
- **No credentials in URLs** (rejected by the validator). URLs in attempt logs have query values
  and user-info redacted.
- **Logging**: identifiers only — never SMS bodies, header values or full URLs. Debug logs are
  disabled in release, and R8 strips `Log.d`/`Log.v`.
- **No analytics, no crash reporting SDKs**, no third-party data sharing. SMS content goes only to
  the webhooks you configure.
- **Backup**: `allowBackup=false`, and both backup rule files exclude all domains for cloud
  backup and device-to-device transfer. Keystore-encrypted secrets could not be decrypted on
  another device anyway. Use *Export configuration* to move settings deliberately.
- **Export**: secrets are omitted by default. Including them needs an explicit choice plus a
  plain-text warning. Imported webhooks arrive **disabled**, and missing secrets are flagged for
  re-entry.
- **Header injection** (CR/LF), non-ASCII header values and reserved headers (`Host`,
  `Content-Length`…) are rejected at save time.
- The webhook wizard keeps its draft across process death (`SavedStateHandle`). Secret values
  are never written to saved state: they are reloaded from the encrypted database, or must be
  re-entered for a new webhook.
- The SMS receiver only accepts broadcasts from holders of `BROADCAST_SMS`.

## Testing

```bash
./gradlew :app:testDebugUnitTest             # JVM unit + pipeline tests
./gradlew :app:connectedDebugAndroidTest     # On a device/emulator
./gradlew :app:lintDebug
```

**Unit tests** (`app/src/test`):
- `FilterEngineTest`: sender/message operators, keywords ANY/ALL, regex (incl. catastrophic
  pattern timeout), AND/OR/NOT, nesting, case sensitivity, numeric and time windows, Unicode.
- `PayloadBuilderTest`: JSON, custom names, raw text, form encoding, templates and escaping,
  regex extraction.
- `MessageFingerprinterTest`: duplicate vs. non-duplicate SMS.
- `RetryPolicyTest`, `DeliveryResultClassifierTest`: success, temporary failure, permanent
  failure, max retries.
- `WebhookValidatorTest`, `ConfigCodecTest`, `MessageStatusAggregatorTest`, `FilterTreeOpsTest`,
  `SmsDeletionPolicyTest`, `OkHttpWebhookSenderTest` (MockWebServer: timeout, redirects, invalid
  headers, URL redaction).
- `PipelineIntegrationTest`: the real use cases + real OkHttp against MockWebServer:
  - SMS → stored → matched → queued → webhook called → `SENT` → duplicate prevented
  - no matching webhook → `FILTERED`
  - webhook unavailable → `RETRYING` → delivered
  - offline → pending → network restored → delivered
  - A sent / B filtered / C sent
  - permanent failure + manual retry, max retries, disabled webhook, retention

**Instrumented tests** (`app/src/androidTest`):
- `RoomPipelineTest`: the full pipeline on a real Room database with the real Keystore cipher:
  unique constraints, transactional status, cascade deletes, retention batches, paged search with
  escaped wildcards.
- `DatabaseSchemaTest`: exported schema/migration validation.
- `MainSafetyTest`: HTTP sending is main-thread safe (regression for a crash found on a device).

## Build instructions

Requirements: Android Studio (bundled JBR 21+), Android SDK 37.

```bash
./gradlew :app:assembleDebug      # app-debug.apk, id com.bestcallerapp.smswebhook.debug
./gradlew :app:assembleQa         # release config (R8, HTTPS-only), debug-signed, id ….qa
```

**Testing locally against your computer** (debug build only): run a server on port 8787, then
`adb reverse tcp:8787 tcp:8787` and use `http://127.0.0.1:8787/…` as the webhook URL.

## Release instructions

1. Create an upload keystore (never commit it — `*.jks`/`*.keystore` are git-ignored).
2. Provide signing values as Gradle properties (`~/.gradle/gradle.properties`) or environment
   variables:
   ```
   SMSWEBHOOK_STORE_FILE=/path/to/upload.jks
   SMSWEBHOOK_STORE_PASSWORD=…
   SMSWEBHOOK_KEY_ALIAS=…
   SMSWEBHOOK_KEY_PASSWORD=…
   ```
3. Bump `versionCode`/`versionName` in `app/build.gradle.kts`.
4. Build:
   ```bash
   ./gradlew :app:testDebugUnitTest :app:lintRelease :app:bundleRelease :app:assembleRelease
   ```
   Outputs: `app/build/outputs/bundle/release/app-release.aab` and
   `app/build/outputs/apk/release/`. Without signing properties, an unsigned APK is produced.
5. R8 is enabled (`src/main/keepRules/rules.keep`). Smoke-test the `qa` build on a device before
   publishing.

## Known Android limitations

- **Deleting SMS**: since Android 4.4 only the **default SMS app** may delete messages. This app is
  not an SMS client, so *Delete SMS after forwarding* is shown but disabled, with an explanation.
  The policy logic exists (`SmsDeletionCoordinator`) for a future default-SMS-app variant. The app
  never attempts unsupported deletion.
- **Restricted settings (Android 13+)**: when sideloaded, Android may block granting the SMS
  permission until the user enables *App info → ⋮ → Allow restricted settings*. The app explains
  this when the permission is denied.
- **OEM battery management** (Samsung, Xiaomi, Oppo…) can delay background work. Set the app
  to *Unrestricted* battery usage. The Home screen and Settings link to the right page.
- **Force-stop** by the user stops all receivers and jobs until the app is opened again (Android
  behaviour).
- **SIM information** depends on the device/ROM including slot/subscription extras in the
  broadcast. When absent, SIM fields are `null`.
- **Expedited work quota**: when exhausted, jobs run as normal work and may start slightly
  later.

## Google Play considerations

- `RECEIVE_SMS` is a **restricted permission** under Google Play's *SMS and Call Log* policy.
  Apps must be the default SMS handler or qualify for a listed exception and submit the
  *Permissions Declaration Form*. An SMS-forwarding app may qualify under exceptions such as
  *SMS-based money management* or *device automation*, subject to Google's review. Otherwise,
  distribute outside Play (e.g. enterprise/MDM or direct APK).
- Complete the **Data safety** form accurately: SMS content is collected and shared only with
  user-configured endpoints, not with the developer. Data is not encrypted in transit when a
  user deliberately uses HTTP — not possible in release builds.
- A privacy policy is required for apps handling SMS.
- No foreground service type declaration is needed beyond WorkManager's expedited jobs on
  Android ≤ 11.
