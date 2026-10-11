# Glossary

| Term | Definition |
|---|---|
| **Active status** | A message or delivery that still has work pending: `RECEIVED`, `PENDING`, `PROCESSING`, `RETRYING`. Never deleted by retention |
| **Attempt** | One HTTP request for a delivery. Recorded in `delivery_attempts` when logging is on |
| **At-least-once** | Delivery guarantee: every matched message is delivered, but a receiver may occasionally see a request twice |
| **Backoff** | Waiting time before a retry; doubles after each failure |
| **Claim** | Atomic transition of a delivery to `PROCESSING` so it is sent by only one worker |
| **Common webhook** | A webhook with scope **All messages** |
| **Delivery** | The pairing of one message with one webhook, with its status and result |
| **Expedited work** | WorkManager work the system runs as soon as possible, within a quota |
| **Extractor** | A named regex with a capture group whose result becomes a payload field |
| **Filtered** | Not matched by a webhook's rules; recorded but not sent |
| **Fingerprint** | SHA-256 identity of an SMS used for duplicate detection; also the `id` payload field |
| **Idempotency key** | `X-Idempotency-Key` header, stable across retries of one delivery |
| **Multipart SMS** | A long SMS split by the network into several PDUs; joined into one message |
| **PDU** | Protocol Data Unit: the raw SMS data delivered by the radio |
| **Play App Signing** | Google holds the app-signing key; the developer signs uploads with an upload key |
| **Port** | A domain interface implemented by the data layer (e.g. `WebhookSender`) |
| **R8** | The code shrinker and optimiser used for release builds |
| **Reconcile** | Re-enqueueing work for messages and deliveries that are still active, at app start |
| **Retention** | Automatic deletion of old messages and logs by age or count |
| **Secret header** | A header whose value is encrypted at rest and masked in the UI |
| **Service-centre timestamp (SMSC time)** | The time the operator's SMS centre stamped the message; part of the PDU |
| **Subscription ID** | Android's identifier for a SIM subscription |
| **Upload key** | The key in `release_item/1_signing_key/` used to sign builds uploaded to Play |
| **Webhook** | A user-configured HTTP endpoint with its rules, headers, payload format and retry settings |
