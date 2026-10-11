# Architecture decision records

Each ADR records one significant decision: its context, the decision and its consequences. ADRs are
immutable once accepted. To change a decision, add a new ADR that supersedes the old one and update the
old one's status.

| ADR | Title | Status | Date |
|---|---|---|---|
| [0001](0001-clean-architecture.md) | Clean architecture with a pure-Kotlin domain | Accepted | 2026-10-05 |
| [0002](0002-local-first-workmanager.md) | Local database as source of truth; WorkManager instead of a foreground service | Accepted | 2026-10-05 |
| [0003](0003-okhttp-without-retrofit.md) | OkHttp directly, without Retrofit | Accepted | 2026-10-05 |
| [0004](0004-filter-tree-as-json.md) | Store the filter tree as versioned JSON | Accepted | 2026-10-05 |
| [0005](0005-freeze-payload-at-processing.md) | Render the payload once, when the message is processed | Accepted | 2026-10-05 |
| [0006](0006-http-method-extensibility.md) | POST only, with method-agnostic plumbing | Accepted | 2026-10-05 |
| [0007](0007-fingerprint-excludes-local-time.md) | Fingerprint on the service-centre timestamp, not local time | Accepted | 2026-10-05 |
| [0008](0008-no-default-sms-role.md) | Do not become the default SMS app; report SMS deletion as unsupported | Accepted | 2026-10-05 |
| [0009](0009-secrets-and-backup.md) | Keystore-encrypted secrets and disabled backups | Accepted | 2026-10-05 |

New ADRs use [template.md](template.md).
