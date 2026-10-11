# ADR-0003: OkHttp directly, without Retrofit

- **Status:** Accepted
- **Date:** 2026-10-05

## Context
Every webhook has a user-supplied URL, headers, timeout and body format. There is no fixed API.

## Decision
Use OkHttp directly in `OkHttpWebhookSender`, with a shared client (connection pool) and per-request timeouts
via `newBuilder()`. Disable redirects and silent connection retries. Run all I/O on the IO dispatcher so the
sender is main-safe.

## Consequences
- Full control over headers, content type, timeouts and error classification.
- Retrofit's value (typed interfaces for fixed endpoints) does not apply; one fewer dependency.

## Alternatives considered
- Retrofit with `@Url` and `@HeaderMap` — rejected as indirection without benefit.
