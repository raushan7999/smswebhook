# SMS Webhook — Documentation

| | |
|---|---|
| **Product** | SMS Webhook — Android app that forwards incoming SMS to user-configured HTTPS webhooks |
| **Package** | `com.bestcallerapp.smswebhook` |
| **Current version** | 1.0.0 (versionCode 10000) |
| **Platforms** | Android 8.0 (API 26) – Android 17 (API 37) |
| **Owner** | Best Caller, Bengaluru, India |
| **Website** | https://smswebhook.bestcallerapp.com/ |
| **Last updated** | 2026-10-10 |

This folder is the authoritative technical and operational documentation for the SMS Webhook project:
the Android application, its release kit and its website. Start with the overview, then read the
document that matches your task.

## Contents

### Product
| # | Document | Audience | Summary |
|---|---|---|---|
| 01 | [Overview](01-overview.md) | Everyone | What the app does, scope, non-goals, terminology |
| 02 | [User guide](02-user-guide.md) | Support, QA, end users | Every screen and workflow in the app |

### Engineering
| # | Document | Audience | Summary |
|---|---|---|---|
| 03 | [Developer setup](03-developer-setup.md) | Developers | Toolchain, build, run, project layout |
| 04 | [Architecture](04-architecture.md) | Developers | Layers, components, processing pipeline, threading |
| 05 | [SMS reception](05-sms-reception.md) | Developers | Broadcast handling, multipart, SIM data, ingest fallback |
| 06 | [Filter engine](06-filter-engine.md) | Developers, support | Rule model, operators, evaluation semantics, regex safety |
| 07 | [Payloads and extraction](07-payloads-and-extraction.md) | Developers, integrators | Formats, fields, templates, regex extraction, headers |
| 08 | [Delivery and retries](08-delivery-and-retries.md) | Developers, integrators | Work scheduling, idempotency, retry policy, status model |
| 09 | [Data model](09-data-model.md) | Developers | Room schema, indexes, migrations, retention |
| 10 | [Configuration reference](10-configuration-reference.md) | Developers, support | Settings, limits, defaults, export/import format |
| 11 | [Security and privacy](11-security-and-privacy.md) | Developers, reviewers | Threat model, controls, data handling |
| 12 | [Testing](12-testing.md) | Developers, QA | Test strategy, suites, device testing, demo data |

### Operations
| # | Document | Audience | Summary |
|---|---|---|---|
| 13 | [Build and release](13-build-and-release.md) | Release manager | Versioning, signing, release kit, Google Play process |
| 14 | [Website](14-website.md) | Release manager, marketing | Site structure, hosting, DNS, content maintenance |
| 15 | [Webhook integration guide](15-webhook-integration.md) | Server developers | How to receive and verify requests on your server |
| 16 | [Troubleshooting](16-troubleshooting.md) | Support, users | Symptoms, causes, fixes |
| 17 | [Known limitations](17-known-limitations.md) | Everyone | Platform and policy constraints |

### Records
| Document | Summary |
|---|---|
| [Architecture decision records](adr/README.md) | Why key technical choices were made |
| [Changelog](CHANGELOG.md) | Release history |
| [Glossary](glossary.md) | Terms used across the documentation |

## Conventions used in these documents

- **MUST / SHOULD / MAY** follow [RFC 2119](https://www.rfc-editor.org/rfc/rfc2119) meaning.
- Paths are relative to the repository root unless stated otherwise. Source paths are abbreviated from
  `app/src/main/java/com/bestcallerapp/smswebhook/` to `…/` where unambiguous.
- UI labels appear in **bold** exactly as shown in the app.
- Diagrams use [Mermaid](https://mermaid.js.org/), which GitHub and most Markdown viewers render.

## Keeping the documentation current

A change is not complete until the affected documents are updated. In particular:

| When you change… | Update… |
|---|---|
| A limit or default (`AppConstants`, `WebhookLimits`, `AppSettings`) | [10](10-configuration-reference.md) |
| The database schema | [09](09-data-model.md), then add a migration and an ADR if the design changes |
| A permission, SDK or data flow | [11](11-security-and-privacy.md), the privacy policy (`website/privacy/`), Play forms (`release_item/5_play_console_forms/`) |
| The release process | [13](13-build-and-release.md) and `release_item/PLAY_STORE_DEPLOYMENT.md` |
| Anything user-visible | [02](02-user-guide.md), the website guide, and [CHANGELOG](CHANGELOG.md) |
