# ADR-0001: Clean architecture with a pure-Kotlin domain

- **Status:** Accepted
- **Date:** 2026-10-05

## Context
The app combines a broadcast receiver, background workers, a database, an HTTP client and a multi-screen UI.
The rules (filters, payloads, retries, de-duplication) are the product's value and must be testable and
changeable without touching Android APIs.

## Decision
Split the code into `domain` (models, engines, use cases, interfaces; no Android framework), `data`
(implementations), `presentation` (Compose + ViewModels) and thin Android entry points (`receiver`, `worker`).
Wire implementations with Hilt. Domain code depends only on interfaces (`port/`, `repository/`).

## Consequences
- Most behaviour, including the full pipeline, is tested on the JVM with fakes in seconds.
- Entry points contain no business logic, so the receiver and workers stay small.
- Slightly more files and mapping code (entities ↔ models).

## Alternatives considered
- Single-module MVVM with logic in ViewModels — rejected: background paths (receiver, workers) could not
  share logic cleanly, and testing would need Android.
- Multi-module Gradle build — deferred: unnecessary at the current size; packages already enforce boundaries.
