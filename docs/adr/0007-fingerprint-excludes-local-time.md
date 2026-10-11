# ADR-0007: Fingerprint on the service-centre timestamp, not local time

- **Status:** Accepted
- **Date:** 2026-10-05

## Context
Android can re-deliver the same `SMS_RECEIVED` broadcast. Its local handling time differs between deliveries;
the PDU, including the SMS service-centre timestamp, does not.

## Decision
Fingerprint = SHA-256 of version, normalised sender, body, service-centre timestamp and subscription ID.
Enforce uniqueness in the database.

## Consequences
- Re-delivered broadcasts are ignored.
- Two genuinely distinct SMS with identical sender, text, SIM and SMSC second are merged (accepted).
- The fingerprint is a stable message ID that receivers can use (`id` field).
