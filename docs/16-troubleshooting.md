# 16 · Troubleshooting

> **Purpose:** symptom-based diagnosis for support staff and users. Each entry lists likely causes in order of
> probability and the fix.

## 1. SMS are not appearing in the app

| Cause | How to confirm | Fix |
|---|---|---|
| SMS permission not granted | Home shows **SMS permission required** or **No permission** | Tap **Grant permission**, or App info → Permissions → SMS |
| Permission blocked as a "restricted setting" (Android 13+, app installed outside Play) | The SMS toggle is greyed out in App info | App info → ⋮ → **Allow restricted settings**, then grant SMS |
| Forwarding switched off | Home: **Forward incoming SMS** is off | Turn it on |
| App was force-stopped | Happens after "Force stop" or some cleaner apps | Open the app once; Android then delivers SMS again |
| Message is RCS / chat, not SMS | Google Messages shows "RCS message" or "Chat" | RCS is not SMS and is not broadcast to other apps. Turn off chat features, or ask the sender to send SMS |
| Aggressive OEM battery management | Messages appear only after opening the app | Settings → **Battery optimisation** → set the app to *Unrestricted*; on Samsung also remove it from "Sleeping apps" |

## 2. Messages appear but are not delivered

| Status / error | Cause | Fix |
|---|---|---|
| **Filtered** | No enabled webhook matched | Check the webhook is enabled; use **Try it with a sample SMS** in the editor's filter step |
| **Pending** for a long time | No internet, or **Only on Wi-Fi** without Wi-Fi | Connect; check Home → **Internet**; review **Only on Wi-Fi** |
| **Retrying**, `HTTP 5xx` / timeout | Server down or slow | Fix the server; the app retries automatically; raise **Timeout** if needed |
| **Failed**, `HTTP 401/403` | Wrong or missing credentials | Edit the webhook headers, then **Retry** |
| **Failed**, `HTTP 404` | Wrong URL path | Fix the URL, then **Retry** |
| **Failed**, `HTTP 400/422` | Server rejects the body format | Check **Payload** format and field names; inspect **Show details** |
| **Failed**, *Unexpected redirect* | URL redirects (e.g. http→https, missing slash) | Use the final URL |
| **Failed**, *TLS/SSL error* | Invalid, self-signed or expired certificate | Install a valid certificate (e.g. Let's Encrypt). The app never bypasses TLS checks |
| **Failed**, *re-enter the secret value* | Encrypted header unreadable (restore to new phone, key reset) | Edit the webhook, re-enter the secret, save, **Retry failed** |
| **Failed**, *Webhook was disabled before delivery* | The webhook was turned off while the message waited | Enable it and **Retry** |

## 3. Duplicate requests on the server

Delivery is at-least-once: if the server processed a request but the response was lost, the app retries.
De-duplicate on `X-Idempotency-Key` ([15 §3](15-webhook-integration.md#3-de-duplication)).

## 4. Regex does not match

- Matching uses *find* semantics; anchor with `^` and `$` for whole-text matches.
- Case-insensitive is the default; enable **Case-sensitive** if needed.
- Escape special characters: `\.`, `\(`, `\$`. Use **Test regex** with the real message text.
- Very complex patterns that exceed 250 ms are treated as no match. Simplify nested quantifiers such as `(a+)+`.

## 5. Import fails

| Message | Fix |
|---|---|
| *File is not a valid webhook configuration export* | Choose a file created by **Export configuration** |
| *created by a newer app version* | Update the app |
| *Skipped n: …* | The listed webhook failed validation (e.g. an `http://` URL in a release build); fix and re-import |

## 6. Collecting diagnostics

Ask the user for: phone model, Android version, app version (Settings → **Version**), the message's
**Webhooks** card details (status, HTTP status, error) and the relevant **Logs** entry. Do **not** ask for SMS
content or header secrets.
