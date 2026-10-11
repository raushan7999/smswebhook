# ADR-0008: Do not become the default SMS app; report SMS deletion as unsupported

- **Status:** Accepted
- **Date:** 2026-10-05

## Context
The requirements ask for optional deletion of SMS after forwarding. Since Android 4.4 only the default SMS app
can write to the SMS provider. Holding that role requires a full messaging client (sending, MMS, conversation
UI, respond-via-message service) and is policy-sensitive on Google Play.

## Decision
Do not implement the default SMS role. Keep the deletion policy model (`DeleteSmsPolicy`,
`SmsDeletionCoordinator`) and an `SmsDeleter` port whose Android implementation reports *unsupported*. The
setting is shown disabled with an explanation. Never attempt deletion through unsupported means.

## Consequences
- Honest behaviour that complies with platform rules.
- A future default-SMS-app variant can implement `SmsDeleter` without changing the domain.
