# ADR-0002: Local database as source of truth; WorkManager instead of a foreground service

- **Status:** Accepted
- **Date:** 2026-10-05

## Context
SMS must never be lost: the device may be offline, the process may be killed and the phone may restart.
`BroadcastReceiver`s have about 10 seconds and must not do network I/O. A permanently running foreground
service costs battery and shows a permanent notification.

## Decision
Persist every SMS in Room inside the receiver (`goAsync`, 6 s budget), then hand all further work to
WorkManager as unique, persisted work: processing per message and delivery per (message, webhook), with
network constraints and exponential backoff. Reconcile queued work at app start. Use expedited work for
promptness; no foreground service.

## Consequences
- Durable across process death and reboots; offline delivery is automatic.
- No persistent notification; battery-friendly.
- Delivery latency depends on WorkManager scheduling and OEM battery policies (documented, with guidance).
- Retry timing is owned by WorkManager (10 s minimum backoff, 5 h maximum).

## Alternatives considered
- Foreground service holding a queue — rejected: battery, notification, Android 14+ restrictions.
- Sending directly from the receiver — rejected: violates receiver limits; loses messages offline.
