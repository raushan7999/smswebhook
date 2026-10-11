# 07 · Payloads and extraction

> **Purpose:** specify the request body formats, available fields, template language, regex extraction and
> header handling.

## 1. Formats

| Format | Enum | Content-Type | Body |
|---|---|---|---|
| **JSON** | `JSON` | `application/json; charset=utf-8` | Object of enabled fields (custom names) plus extracted values |
| **Custom JSON template** | `JSON_TEMPLATE` | `application/json; charset=utf-8` | User template; values are JSON-escaped |
| **Raw text** | `RAW_TEXT` | `text/plain; charset=utf-8` | User template; values inserted verbatim |
| **Form data** | `FORM_URLENCODED` | `application/x-www-form-urlencoded` | Enabled fields plus extracted values, UTF-8 URL-encoded |

A user-defined `Content-Type` header overrides the default.

## 2. Fields

| Key | Default name | Default | Type in JSON | Value |
|---|---|---|---|---|
| `ID` | `id` | off | string | Message fingerprint (SHA-256 hex): stable, safe for de-duplication |
| `SENDER` | `sender` | **on** | string | Sender address or ID, e.g. `VM-HDFCBK` |
| `MESSAGE` | `message` | **on** | string | Full text (multipart joined) |
| `TIMESTAMP` | `timestamp` | **on** | string | Service-centre time, ISO-8601 with offset |
| `RECEIVED_AT` | `received_at` | off | string | Time the phone received it, ISO-8601 with offset |
| `SIM_SLOT` | `sim_slot` | off | number or null | One-based SIM slot |
| `SUBSCRIPTION_ID` | `subscription_id` | off | number or null | Android subscription ID |
| `DEVICE` | `device` | off | string | e.g. `Samsung SM-A176B (Android 16)` |

Output names are editable and must be unique (including extracted field names).

Default request body:

```json
{"sender":"VM-HDFCBK","message":"Rs 5,000.00 credited to A/c XX1234 …","timestamp":"2026-10-05T20:30:00+05:30"}
```

## 3. Templates

Used by **Custom JSON template** and **Raw text**.

| Syntax | Meaning |
|---|---|
| `{{sender}}`, `{{message}}`, `{{timestamp}}`, `{{received_at}}`, `{{id}}`, `{{sim_slot}}`, `{{subscription_id}}`, `{{device}}` | Message fields (independent of the output names chosen for JSON) |
| `{{regex.<name>}}` | Value of the extractor `<name>` |
| Whitespace inside braces | Allowed: `{{ sender }}` |

Rules:
- Pure substitution: no expressions, conditionals, loops or function calls. A template cannot execute code.
- Missing values (no match, unknown SIM) render as an empty string.
- Unknown variables are rejected when saving.
- **JSON templates:** values are JSON-string-escaped. Placeholders therefore belong **inside quotes**
  (`"text": "{{message}}"`). On save the template is rendered with hostile sample values (quotes,
  backslashes, newlines) and must parse as JSON.

Defaults: raw text `{{sender}}: {{message}}`; JSON template
`{"sender":"{{sender}}","message":"{{message}}","time":"{{timestamp}}"}`.

## 4. Regex extraction

An extractor is *(name, regex, capture group, case-sensitive)*.

| Example | Regex | Group | Result for "…credited with Rs 5,000 on 05-Oct-26" |
|---|---|---|---|
| `amount` | `credited with Rs ([0-9,]+)` | 1 | `5,000` |
| `account` | `A/c\s?(XX\d{4})` | 1 | `XX1234` |
| `otp` | `\b(\d{6})\b` | 1 | the six-digit code |

- Group 0 is the whole match.
- No match, or a group that did not participate → `null` in JSON, empty string in form data and templates.
- Validation: name `[A-Za-z][A-Za-z0-9_]{0,63}`, valid regex, group ≤ number of groups in the pattern,
  at most 20 extractors.
- Extracted values are added as top-level fields in JSON and form data, and available as `{{regex.<name>}}`.
- Extraction uses the same regex time budget as filters.

## 5. When the payload is built

The body is rendered **once**, when the message is processed, and stored with the delivery
(`webhook_deliveries.payload_body`). Every retry therefore sends identical bytes, even if the webhook is edited
later. Headers, URL and timeout are read from the current webhook configuration at send time.

## 6. Headers

| Header | Source |
|---|---|
| User headers | Webhook configuration, in order |
| `Content-Type` | Payload format, unless the user sets one |
| `User-Agent` | `SmsWebhook/<version>` |
| `X-Idempotency-Key` | `<messageId>-<webhookId>-<deliveryCreatedAt>`: stable across retries of one delivery |
| `X-SmsWebhook-Delivery` | Local delivery ID |
| `X-SmsWebhook-Test: true` | Only on **Test** requests |

Validation: names are HTTP tokens; values are printable ASCII (tab allowed); CR/LF are rejected (header
injection); `Host`, `Content-Length`, `Transfer-Encoding` and `Connection` are reserved. At most 30 headers.

**Secret headers:** values marked secret (suggested automatically for names such as `Authorization`,
`X-API-Key`, `*token*`, `*secret*`) are encrypted at rest ([11](11-security-and-privacy.md)) and masked in
the UI. Quick actions create `Authorization: Bearer <token>` and `Authorization: Basic <base64(user:pass)>`.
