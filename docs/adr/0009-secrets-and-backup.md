# ADR-0009: Keystore-encrypted secrets and disabled backups

- **Status:** Accepted
- **Date:** 2026-10-05

## Context
Webhook headers often contain API keys and tokens. Automatic backup could copy them, and SMS history, off the
device.

## Decision
Encrypt secret header values with AES-256-GCM using a non-exportable Android Keystore key. Disable app backup
and device-to-device transfer for all data. Offer an explicit export, excluding secrets by default.

## Consequences
- Secrets are unreadable outside the device; logs never contain them.
- After moving to a new phone, secrets must be re-entered (imported webhooks arrive disabled and flagged).
- Full-database encryption (SQLCipher) was not adopted: Android file-based encryption already protects app data
  at rest, and the most sensitive values (credentials) have an extra layer. It can be revisited if needed.
