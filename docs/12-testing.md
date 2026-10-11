# 12 · Testing

> **Purpose:** describe the test strategy, the suites, how to run them and the manual device checks performed
> before a release.

## 1. Strategy

| Level | Scope | Runs on | Speed |
|---|---|---|---|
| Unit | Domain engines and codecs in isolation | JVM | Seconds |
| Pipeline integration | Real use cases + real OkHttp against MockWebServer, in-memory repositories | JVM | Seconds |
| Instrumented | Real Room database, Android Keystore, schema export, main-thread safety | Device / emulator | ~1 minute |
| Manual device | UI flows, receiver, permissions, R8 build | Physical phone | Per release |

Domain logic is pure Kotlin behind interfaces, so most behaviour is testable on the JVM with fakes
(`app/src/test/.../fakes/`).

## 2. Suites

### JVM (`app/src/test/`) — 63 tests

| Class | Covers |
|---|---|
| `FilterEngineTest` | All text operators, keywords ANY/ALL, regex (find semantics, inline flags, invalid, catastrophic timeout), AND / OR / NOT, nesting, disabled nodes, empty group, numeric fields, time windows incl. midnight wrap, Unicode |
| `PayloadBuilderTest` | Default JSON, custom names, raw text, form encoding, extraction, JSON-template escaping, template engine |
| `MessageFingerprinterTest` | Duplicate vs. distinct SMS |
| `RetryPolicyTest` | Retry budget, exponential backoff, floor and cap |
| `DeliveryResultClassifierTest` | Success, retryable, permanent classification |
| `MessageStatusAggregatorTest` | Status derivation |
| `SmsDeletionPolicyTest`, `FilterTreeOpsTest` | Deletion policy, filter tree edits |
| `WebhookValidatorTest` | URLs, regex errors, headers (injection, reserved, non-ASCII), extractors, templates, limits |
| `ConfigCodecTest` | Filter round-trip, forward compatibility, export with/without secrets, invalid files |
| `OkHttpWebhookSenderTest` | Status/body, timeout, redirects not followed, invalid headers, URL redaction |
| `PipelineIntegrationTest` | End-to-end scenarios below |

**Pipeline scenarios** (`PipelineIntegrationTest`):

| Scenario | Expected |
|---|---|
| SMS → stored → matched → queued → webhook called → delivery recorded | `SENT`, correct JSON and headers, secrets not logged |
| Duplicate broadcast and stray re-run | No second delivery, no second request |
| No matching webhook | Message and delivery `FILTERED`, nothing sent |
| No webhooks configured | Message stored as `FILTERED` |
| Webhook unavailable (503, 500) then available | `RETRYING` → `SENT`, 3 attempts logged |
| Offline (DNS, connection errors) then online | Pending → `SENT` |
| Webhooks A match, B no match, C match | A `SENT`, B `FILTERED`, C `SENT` |
| Permanent 400, then manual retry | `FAILED` without retry → `SENT` after manual retry |
| Server keeps failing | `FAILED` after maxRetries + 1 attempts |
| Webhook disabled before delivery | `FAILED`, no request |
| Retention by count and age | Old inactive messages deleted, active kept |
| Re-processing a processed message | Its active deliveries are re-queued |
| Attempt-log write fails after 2xx | Delivery stays `SENT`, no second request |

### Instrumented (`app/src/androidTest/`) — 7 tests

| Class | Covers |
|---|---|
| `RoomPipelineTest` | Full pipeline on real Room + Keystore; unique constraints; transactional status; webhook deletion recomputes status; retention batches; paged search with escaped wildcards |
| `DatabaseSchemaTest` | Exported schema creates and validates (`MigrationTestHelper`) |
| `MainSafetyTest` | HTTP sending from the main thread does not trigger `NetworkOnMainThreadException` |

## 3. Running tests

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest       # uninstalls the debug app afterwards
```

To keep an installed and configured debug app, install the APKs and run the instrumentation directly:

```bash
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w com.bestcallerapp.smswebhook.debug.test/androidx.test.runner.AndroidJUnitRunner
```

Reports: `app/build/reports/tests/` and `app/build/outputs/androidTest-results/`.

## 4. Manual release checklist

| # | Check | Build |
|---|---|---|
| 1 | Fresh install: permission card, grant, **Service active** | release / qa |
| 2 | Denied permission → **Open app settings** path | release / qa |
| 3 | Create a webhook through all 7 steps; validation messages appear | qa |
| 4 | **Test** against a public HTTPS endpoint (e.g. webhook.site) → 200 | qa |
| 5 | Real SMS from another phone arrives at the endpoint; message **Delivered** | release |
| 6 | Airplane mode → SMS → **Pending** → network back → **Delivered** | release |
| 7 | Endpoint returning 500 → **Retrying** → endpoint fixed → **Delivered** | qa |
| 8 | Reboot with a pending delivery → delivered after boot | release |
| 9 | Export without secrets → import → webhooks disabled, secrets flagged | qa |
| 10 | Dark mode, large font, TalkBack on Home and the editor | qa |

Results for each release are recorded in `release_item/2_release_v<version>/build_info.txt`.

## 5. Known gaps

- No automated Compose UI tests; UI flows are verified manually and with ADB-driven screenshots.
- The SMS receiver itself is verified manually (Android does not allow injecting `SMS_RECEIVED` on physical
  devices without root). On an emulator, use **Extended controls → Phone → Send message**.
