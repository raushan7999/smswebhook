# 03 · Developer setup

> **Purpose:** get a working development environment, build every variant and find your way around the code.

## 1. Requirements

| Tool | Version | Notes |
|---|---|---|
| Android Studio | 2026.x or newer | Its bundled JetBrains Runtime (JDK 21+) is used as `JAVA_HOME` |
| Android SDK | Platform 37, Build-tools 36 | `local.properties` → `sdk.dir` (created by Android Studio, not committed) |
| Gradle | 9.6 (wrapper) | Use `./gradlew`; never a system Gradle |
| Android Gradle Plugin | 9.4.1 | Built-in Kotlin support (no `kotlin-android` plugin) |
| Kotlin | 2.3.21 | KSP 2.3.11 for Room and Hilt |
| Device | Android 8.0+ with a SIM | An emulator can receive SMS via its extended controls |

Command-line builds need `JAVA_HOME` set, for example on macOS:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

## 2. Build variants

| Variant | Application ID | Minified | Cleartext HTTP | Signing | Use |
|---|---|---|---|---|---|
| `debug` | `com.bestcallerapp.smswebhook.debug` | No | Allowed (local testing) | Debug key | Development, demo data, screenshots |
| `qa` | `com.bestcallerapp.smswebhook.qa` | Yes (R8) | Blocked | Debug key | Testing release behaviour side by side |
| `release` | `com.bestcallerapp.smswebhook` | Yes (R8) | Blocked | Upload key | Google Play |

The three variants can be installed on the same phone at once.

## 3. Common commands

```bash
./gradlew :app:assembleDebug                 # debug APK
./gradlew :app:installDebug                  # build and install on the connected device
./gradlew :app:testDebugUnitTest             # JVM unit and pipeline tests
./gradlew :app:connectedDebugAndroidTest     # instrumented tests (uninstalls the app afterwards)
./gradlew :app:lintDebug                     # Android lint
./gradlew :app:assembleQa                    # release-like build signed with the debug key
./gradlew :app:bundleRelease :app:assembleRelease   # signed release (needs the upload key, see 13)
```

## 4. Repository layout

```
SMSWebhook/
├── app/                         Android application module
│   ├── build.gradle.kts         version, signing, build types, dependencies
│   ├── schemas/                 exported Room schemas (one JSON per DB version) — commit these
│   └── src/
│       ├── main/                production code and resources
│       ├── debug/               debug-only: demo data seeder, permissive network config
│       ├── qa/                  qa app name
│       ├── test/                JVM tests (fakes, engines, pipeline with MockWebServer)
│       └── androidTest/         device tests (Room, Keystore, schema, main-thread safety)
├── docs/                        this documentation
├── gradle/libs.versions.toml    single source of dependency versions
├── release_item/                Google Play release kit (see 13)
├── website/                     static website for smswebhook.bestcallerapp.com (see 14)
└── README.md                    project README (summary)
```

### Source packages (`app/src/main/java/com/bestcallerapp/smswebhook/`)

| Package | Contents |
|---|---|
| `core/` | Constants and limits, logging, Keystore cipher, connectivity, URL redaction, time and hashing utilities |
| `domain/model/` | Plain Kotlin models: messages, webhooks, filters, payload config, deliveries, settings |
| `domain/engine/` | Pure logic: `FilterEngine`, `RegexEvaluator`, `PayloadBuilder`, `TemplateEngine`, `ValueExtractor`, `MessageFingerprinter`, `RetryPolicy`, `DeliveryResultClassifier`, `MessageStatusAggregator`, `WebhookValidator`, `WebhookRequestFactory`, `WebhookPreviewer` |
| `domain/port/` | Interfaces to the platform: HTTP sender, work scheduler, SMS deleter, device info, listener control, power status, config codec |
| `domain/repository/` | Repository interfaces |
| `domain/usecase/` | Application operations: ingest, process, deliver, retry, test, save, duplicate, retention, reconcile, export, import, toggles |
| `data/` | Room (`local/`), OkHttp (`remote/`), repositories, DataStore settings, JSON codecs, Android adapters (`system/`) |
| `receiver/` | `SmsReceiver`, `SmsIntentParser` |
| `worker/` | WorkManager workers and `WorkManagerScheduler` |
| `di/` | Hilt modules |
| `presentation/` | Compose UI: navigation, theme, components and one package per screen |

## 5. Coding conventions

- **Layering is enforced by convention:** `domain` has no Android framework imports; `presentation`
  depends on `domain` only (never on `data`). See [04 · Architecture](04-architecture.md).
- ViewModels hold UI state and call use cases or repositories; business rules live in `domain/engine` or
  `domain/usecase`.
- All limits and identifiers come from `core/constants/AppConstants.kt` — no magic numbers in features.
- Never log SMS bodies, header values or full URLs. Use `AppLogger` with identifiers only.
- Suspend functions are main-safe: blocking I/O switches to the injected `@IoDispatcher`.
- Kotlin official code style (`kotlin.code.style=official`), 4-space indent, trailing commas.
- Every new behaviour gets a test; every schema change gets a migration and an exported schema.

## 6. Running against a local server

Debug builds allow plain HTTP so you can test without a public endpoint:

```bash
python3 -m http.server 8787            # or any server that accepts POST, e.g. the snippets in 15
adb reverse tcp:8787 tcp:8787          # phone's 127.0.0.1:8787 → your computer
```

Create a webhook with URL `http://127.0.0.1:8787/sms` in the debug app and tap **Test**.

## 7. Demo data (debug builds only)

`app/src/debug/.../demo/DemoDataReceiver.kt` replaces all app data with a fictional data set (example.com
endpoints, sample bank and OTP texts). Used for store screenshots and tutorials.

```bash
adb shell am start -n com.bestcallerapp.smswebhook.debug/com.bestcallerapp.smswebhook.presentation.MainActivity
adb shell am broadcast -a com.bestcallerapp.smswebhook.DEMO_SEED \
    -n com.bestcallerapp.smswebhook.debug/com.bestcallerapp.smswebhook.demo.DemoDataReceiver
```

The app must be running (Android does not deliver broadcasts to a stopped app). **This deletes all data in
the debug app.** The receiver does not exist in `qa` or `release` builds.
