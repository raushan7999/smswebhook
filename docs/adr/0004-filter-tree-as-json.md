# ADR-0004: Store the filter tree as versioned JSON

- **Status:** Accepted
- **Date:** 2026-10-05

## Context
Filters are a recursive tree (groups with AND/OR/NOT containing conditions or groups). A flat table with a
"logical operator" column per row cannot express precedence unambiguously. Recursive SQL rows add complexity
and migrations for every new field or operator.

## Decision
Store one JSON document per webhook in `webhook_filters.definition`, with `schema_version`. Encode enums as
strings and decode leniently: unknown fields or operators load as **disabled** conditions.

## Consequences
- Unambiguous semantics; new fields and operators need no SQL migration.
- The same JSON shape is reused by the export format.
- Filters cannot be queried with SQL (not needed: evaluation happens in the domain engine).

## Alternatives considered
- Adjacency-list rows (`parent_id`) — rejected: more code, harder migrations, no query benefit.
