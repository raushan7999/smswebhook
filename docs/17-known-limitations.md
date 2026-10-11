# 17 · Known limitations

> **Purpose:** list constraints imposed by Android, Google Play or deliberate design choices, so they are not
> mistaken for defects.

| # | Limitation | Reason | Impact / mitigation |
|---|---|---|---|
| 1 | **Cannot delete SMS** from the phone's messaging app | Since Android 4.4 only the default SMS app may write to the SMS provider. SMS Webhook is not an SMS client | The setting is shown disabled with an explanation. A default-SMS-handler variant would be a separate, much larger product |
| 2 | **Google Play restricts `RECEIVE_SMS`** | SMS and Call Log permissions policy | Requires an approved declaration; may be refused. Fallback: direct APK distribution |
| 3 | **Restricted settings** on Android 13+ for sideloaded installs | Platform protection for SMS permissions | User must allow restricted settings in App info; explained in-app |
| 4 | **RCS / chat messages are not forwarded** | RCS is not delivered via `SMS_RECEIVED` | Only SMS (and SMS fallback) are forwarded |
| 5 | **MMS not supported** | Out of scope | — |
| 6 | **Force-stop pauses everything** | Android suppresses broadcasts and jobs for force-stopped apps | Open the app once to resume |
| 7 | **OEM battery managers may delay delivery** | Vendor-specific background restrictions | Guidance and shortcut to battery settings in the app |
| 8 | **At-least-once delivery** | A response can be lost after the server processed the request | `X-Idempotency-Key` for server-side de-duplication |
| 9 | **SIM data depends on the device** | Some ROMs omit slot or subscription extras | Fields are `null`; SIM filters do not match |
| 10 | **Fingerprint trade-off** | Identical texts from the same sender on the same SIM within one SMSC second are treated as one | Considered negligible in practice |
| 11 | **POST only** | Scope of 1.0.0 | Architecture allows other methods ([ADR-0006](adr/0006-http-method-extensibility.md)) |
| 12 | **Secrets do not survive a move to another phone** | Keystore keys are device-bound and backup is disabled | Re-enter secrets after importing a configuration |
| 13 | **UI text is English and not externalised** | 1.0.0 scope | Strings must move to resources before localisation (store listing already has Hindi) |
| 14 | **No in-app SMS sending, auto-reply or two-way flow** | Out of scope; would require `SEND_SMS` | — |
