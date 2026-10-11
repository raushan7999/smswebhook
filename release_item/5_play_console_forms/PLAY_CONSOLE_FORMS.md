# Play Console — answers for every "Set up your app" form (SMS Webhook 1.0.0)

Package `com.bestcallerapp.smswebhook` · version 1.0.0 (10000) · free · no ads · no in-app purchases.
Privacy policy: https://smswebhook.bestcallerapp.com/privacy/ · Website: https://smswebhook.bestcallerapp.com/
Support email: smswebhook@bestcallerapp.com (create this mailbox / alias before submitting).

> **Read §13 first.** `RECEIVE_SMS` is a restricted permission. Google approves it only for the default SMS
> handler or for a listed exception, after a Permissions Declaration Form. Without approval the release is
> rejected. Plan for this before the first upload.

---

## 1. Create app (Home → Create app)
| Field | Answer |
|---|---|
| App name | SMS Webhook: Forward SMS |
| Default language | English (India) – en-IN |
| App or game | App |
| Free or paid | Free |
| Declarations | Developer Program Policies ✔, US export laws ✔ |

## 2. Store settings (Grow → Store presence → Store settings)
| Field | Answer |
|---|---|
| App category | Tools (alternative: Productivity) |
| Tags | SMS forwarding, Automation, Developer tools (pick from Play's list) |
| Email | smswebhook@bestcallerapp.com |
| Website | https://smswebhook.bestcallerapp.com/ |
| Phone | optional |

## 3. Main store listing
Paste `../3_store_listing/store_listing_en-IN.txt`. Upload from `../4_graphics/`:
`app-icon-512.png`, `feature-graphic-1024x500.png`, `store/phone_v1.0.0/` (8, in file order),
`store/tablet-7in_v1.0.0/`, `store/tablet-10in_v1.0.0/`. Video: YouTube URL of
`../6_promo_video_v1.0.0/promo-landscape-1920x1080.mp4` (see that folder's README).

## 4. Privacy policy (Policy → App content)
`https://smswebhook.bestcallerapp.com/privacy/`

## 5. App access
**All functionality is available without special access** — no login. Add a note for reviewers:
> No account is needed. To try it: grant the SMS permission on the Home screen, open Webhooks → Add webhook,
> enter any HTTPS endpoint (for example a free request-inspector URL such as https://webhook.site/…), keep
> "All messages", save, then send an SMS to the test phone or tap "Test". The request appears at the endpoint.

## 6. Ads
**No**, the app does not contain ads.

## 7. Content rating (IARC questionnaire)
Category: **All other app types** (utility). Answer **No** to violence, sexuality, language, controlled
substances, gambling and crude humour. User interaction / sharing: **No** user-to-user interaction, no chat,
does not share the user's location with other users. Digital purchases: **No**. Expected rating: Everyone / 3+.

## 8. Target audience and content
Target age group: **18 and over** only (a tool for businesses and developers handling bank and OTP messages).
Not designed for children; no appeal to children. Store listing doesn't target children.

## 9. Data safety (Policy → App content → Data safety)
Google counts any data the app transmits off the device as "collected", even when it goes to an endpoint the
user configures rather than to the developer. Answer:

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **Yes** |
| Is all of the user data collected by your app encrypted in transit? | **Yes** (release builds allow HTTPS only) |
| Do you provide a way for users to request that their data is deleted? | **Yes** — in the app (Settings → Clear history / Clear all application data). We hold no data on servers. |

Data types:

| Data type | Collected | Shared | Ephemeral | Required? | Purpose |
|---|---|---|---|---|---|
| Messages → **SMS or MMS** | Yes | **No** ¹ | No | Required | App functionality |
| Device or other IDs | No | No | — | — | — |
| Location, Contacts, Personal info, Financial info ², App activity, Diagnostics | No | No | — | — | — |

¹ The SMS goes only to webhook URLs the user adds and controls — a user-initiated transfer the user expects,
which Play's guidance exempts from "sharing". The developer never receives SMS content.
² Bank SMS may contain amounts, but the app does not separately collect financial data; it forwards the SMS.
If a reviewer asks, describe it under Messages.

Optional "device model" payload field is off by default and goes only to the user's own webhook.

## 10. Advertising ID
**No** — the app does not use the advertising ID (no ads or analytics SDKs).

## 11. Government apps · Financial features · Health · News
Government app: **No**. Financial features: **No** (it is not a banking, lending or payments app; it forwards
SMS text). Health: **No**. News: **No**.

## 12. Account deletion
Not applicable — the app has no accounts.

## 13. Sensitive permissions — SMS and Call Log declaration (REQUIRED)
Play Console → Policy → App content → **Sensitive app permissions → SMS and Call Log permissions**.

| Field | Answer |
|---|---|
| Permissions | `RECEIVE_SMS` only (no READ_SMS, SEND_SMS, call log) |
| Is the app your users' default SMS handler? | **No** |
| Core functionality (choose the closest permitted exception) | **"SMS-based money management"** if you position the app for forwarding bank/payment alerts to the user's own accounting or business system; otherwise **"Device automation"** (user-defined rules that act on incoming SMS). Choose one and keep the listing consistent with it. |
| Description of core functionality (example) | "SMS Webhook forwards incoming SMS to HTTPS endpoints that the user configures (for example the user's own payment backend or automation server). The user defines rules (sender, keywords, regex) that decide which SMS are forwarded and in which format. Receiving SMS in real time is the app's core and only function; without RECEIVE_SMS the app cannot work. The app does not read the existing inbox, does not send SMS, and the developer receives no SMS data." |
| Video demonstration | Required. Record a 30–60 s screen video on a phone: grant permission → add a webhook → send an SMS from another phone → show the request arriving at the endpoint (e.g. webhook.site) and "Delivered" in the app. Upload to YouTube (unlisted) and paste the link. |

**Be realistic:** approval is at Google's discretion and SMS forwarders are frequently rejected. If it is
rejected, the alternatives are (a) distribute the APK directly (website download, enterprise/MDM), or (b)
build a default-SMS-handler variant (a full messaging app) — a much larger project. The website can host the
APK download if you choose (a).

Other permissions — justification if asked:
| Permission | Why |
|---|---|
| INTERNET, ACCESS_NETWORK_STATE | Send to webhooks; wait for connectivity |
| FOREGROUND_SERVICE | WorkManager expedited jobs on Android ≤ 11 (brief, user-visible) |
| RECEIVE_BOOT_COMPLETED, WAKE_LOCK | Added by WorkManager to resume queued deliveries after a restart |

## 14. Testing requirement before production
New personal developer accounts must run a **closed test with at least 12 testers for 14 continuous days**
before applying for production. Each tester needs a phone that can receive SMS; ask them to add a webhook.site
endpoint so they see forwarding working.

## 15. Release
Test and release → Internal testing → Create release → upload `../2_release_v1.0.0/sms-webhook-v1.0.0-release.aab`
→ accept **Play App Signing** → release name `1.0.0 (10000)` → paste `release_notes.txt` → review → roll out.
