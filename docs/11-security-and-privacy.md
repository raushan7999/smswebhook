# 11 · Security and privacy

> **Purpose:** describe the data the app handles, the threats considered and the controls implemented.
> The public privacy policy (https://smswebhook.bestcallerapp.com/privacy/) must stay consistent with this
> document and with the Play Data safety form.

## 1. Data inventory

| Data | Source | Stored | Leaves the device | Recipient |
|---|---|---|---|---|
| SMS sender, text, times, SIM info | Incoming SMS | Room database | Yes, when it matches a webhook | Only the webhook URLs the user configures |
| Webhook configuration | User | Room database | Only via user-initiated export | The file the user chooses |
| Header secrets (tokens, API keys) | User | Room, AES-256-GCM ciphertext | In requests to that webhook; in an export only if the user explicitly includes secrets | The configured webhook |
| Delivery results and logs | App | Room database | No | — |
| Device model | Android `Build` | Not stored | Only if the user enables the `device` payload field | The configured webhook |
| Settings | User | DataStore | No | — |

The developer (Best Caller) operates **no server** that receives SMS or configuration. The app contains **no
analytics, advertising or crash-reporting SDKs**.

## 2. Threat model (summary)

| Threat | Control |
|---|---|
| Another app injects fake SMS into the pipeline | Receiver protected by `BROADCAST_SMS` (system-only) |
| Network eavesdropping or tampering | HTTPS-only in release (validator + `cleartextTrafficPermitted=false`); system CAs only; TLS never relaxed |
| Payload sent to an unintended host | Redirects are not followed; URLs with credentials rejected |
| Credential theft from storage or backups | Secrets encrypted with a non-exportable Keystore key; backups and device transfer disabled |
| Credentials leaked through logs | Logger receives identifiers only; header values never logged; URL query values and user-info redacted in attempt logs; release builds strip debug logs (R8) |
| Header injection | CR/LF and non-ASCII header values rejected; reserved headers blocked |
| Malicious or pathological regex (ReDoS) | 250 ms evaluation budget, cached compilation, validation before save |
| Template injection or code execution | Templates are pure substitution; JSON values escaped; JSON templates validated |
| Data exposure through export files | Secrets excluded by default; explicit plain-text warning when included; imports arrive disabled |
| Large or hostile responses | At most 16 KB read, 4,000 characters stored |
| Loss of SMS | Persist before network; WorkManager durability; reconcile at start |

## 3. Controls in detail

### 3.1 Transport security
- Release: `network_security_config.xml` sets `cleartextTrafficPermitted="false"` and trusts system CAs only.
  The validator rejects `http://` URLs.
- Debug only: plain HTTP and user-installed CAs are allowed for local testing (`src/debug/res/xml/`).
- Certificate and hostname failures are permanent errors and are never bypassed.

### 3.2 Secret storage
- `KeystoreSecretCipher` (`core/security/`): AES/GCM/NoPadding, 256-bit key in `AndroidKeyStore`
  (alias `webhook_secrets_v1`), random 12-byte IV per value, format `enc:v1:` + Base64(IV ‖ ciphertext ‖ tag).
- Keys are bound to the device. After a restore to another device, or if the key is lost, the header is
  flagged **needs re-entry**, deliveries fail with `CREDENTIAL_UNAVAILABLE`, and the UI explains it.
- The webhook editor's saved draft never contains secret values.

### 3.3 Backup
- `android:allowBackup="false"`; `backup_rules.xml` and `data_extraction_rules.xml` exclude every domain for
  cloud backup and device-to-device transfer.

### 3.4 Logging
- `AppLogger` / `AndroidAppLogger`: debug logs only in debug builds; release builds log exception class names,
  not messages; R8 removes `Log.d` and `Log.v`.
- Policy: **never** log SMS bodies, header values or full URLs.

### 3.5 Permissions
| Permission | Purpose |
|---|---|
| `RECEIVE_SMS` | Receive new SMS (the only SMS permission; no `READ_SMS`, no `SEND_SMS`) |
| `INTERNET`, `ACCESS_NETWORK_STATE` | Deliver; network constraints; connectivity status |
| `FOREGROUND_SERVICE` | Expedited work on Android ≤ 11 |
| `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED` | Added by WorkManager |

## 4. User controls and data deletion

| Action | Effect |
|---|---|
| **Clear history** | Deletes stored messages (except those still being delivered) and their deliveries |
| **Clear logs** / **Keep delivery logs** off | Deletes attempt logs |
| **Clear all application data** | Deletes everything, including webhooks, secrets and settings |
| Uninstall | Removes all app data; the Keystore key is deleted by Android |

## 5. Compliance notes

- Google Play: `RECEIVE_SMS` requires the SMS permissions declaration ([13 §7](13-build-and-release.md#7-google-play-submission)).
  The Data safety answers are in `release_item/5_play_console_forms/PLAY_CONSOLE_FORMS.md` §9.
- India DPDP Act 2023: the user is the data fiduciary for SMS they forward to their own systems; the app
  processes data locally on the user's instruction. Have the privacy policy reviewed by counsel before launch.
