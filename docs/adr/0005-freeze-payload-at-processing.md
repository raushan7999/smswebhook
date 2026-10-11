# ADR-0005: Render the payload once, when the message is processed

- **Status:** Accepted
- **Date:** 2026-10-05

## Context
A delivery may be retried for hours. The user may edit the webhook's payload configuration in the meantime.

## Decision
Render the body and content type when the message is processed, and store them with the delivery. Retries
send the stored bytes. Headers, URL and timeout are read from the current configuration at send time.

## Consequences
- Retries are byte-identical, which keeps receivers' de-duplication and signatures consistent.
- Credential rotation (headers) and URL fixes apply to pending retries.
- Payload changes affect new messages only.
