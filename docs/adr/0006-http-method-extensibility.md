# ADR-0006: POST only, with method-agnostic plumbing

- **Status:** Accepted
- **Date:** 2026-10-05

## Context
The requirement is POST initially, with other methods possible later.

## Decision
Model the method as `enum HttpMethod(hasBody)` with only `POST`. Request building, sending, storage and export
use the enum generically.

## Consequences
Adding PUT or PATCH means adding an enum entry and exposing it in step 1 of the editor. GET (no body) would
also need a decision on how to pass message data (query parameters).
