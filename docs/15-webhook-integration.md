# 15 · Webhook integration guide

> **Purpose:** explain, for server developers, exactly what SMS Webhook sends and how to receive it reliably.

## 1. The request

```http
POST /webhooks/sms HTTP/1.1
Host: payments.example.com
Content-Type: application/json; charset=utf-8
User-Agent: SmsWebhook/1.0.0
Authorization: Bearer <your token>          (any headers you configured)
X-Idempotency-Key: 42-3-1791212400412
X-SmsWebhook-Delivery: 118

{"sender":"VM-HDFCBK","message":"Rs 5,000.00 credited to A/c XX1234 …","timestamp":"2026-10-05T20:30:00+05:30","amount":"5,000.00"}
```

- Method: `POST`. Body: as configured ([07](07-payloads-and-extraction.md)).
- **Test** requests add `X-SmsWebhook-Test: true` and use sample data; ignore or log them separately.

## 2. Responding

| Your response | App behaviour |
|---|---|
| Any **2xx** | Delivered. Respond quickly; do heavy work asynchronously |
| 408, 425, 429, 5xx, timeout, connection error | Retried with exponential backoff |
| Other 4xx (400, 401, 403, 404, 422…) | Failed permanently; the user can retry manually after fixing the problem |
| 3xx | Failed; redirects are not followed. Configure the final URL |

Respond within the webhook's timeout (default 15 s). The first 4,000 characters of your response are shown in
the app's delivery details, so a short JSON acknowledgement such as `{"received":true}` is helpful.

## 3. De-duplication

Delivery is **at-least-once**. Store `X-Idempotency-Key` (or the `id` field, if enabled) and ignore repeats.

## 4. Authentication

Use a secret header (for example `Authorization: Bearer …` or `X-API-Key: …`) and reject requests without
it. Always use HTTPS.

## 5. Examples

### Node.js (Express)

```js
import express from "express";
const app = express();
app.use(express.json());
const seen = new Set();                       // use a database in production

app.post("/webhooks/sms", (req, res) => {
  if (req.get("Authorization") !== `Bearer ${process.env.SMS_WEBHOOK_TOKEN}`) return res.sendStatus(401);
  const key = req.get("X-Idempotency-Key");
  if (seen.has(key)) return res.json({ received: true, duplicate: true });
  seen.add(key);
  const { sender, message, timestamp } = req.body;
  console.log(`SMS from ${sender} at ${timestamp}`);
  res.json({ received: true });
});

app.listen(8080);
```

### Python (Flask)

```python
import os
from flask import Flask, request, jsonify, abort

app = Flask(__name__)
seen = set()  # use a database in production

@app.post("/webhooks/sms")
def sms():
    if request.headers.get("Authorization") != f"Bearer {os.environ['SMS_WEBHOOK_TOKEN']}":
        abort(401)
    key = request.headers.get("X-Idempotency-Key")
    if key in seen:
        return jsonify(received=True, duplicate=True)
    seen.add(key)
    data = request.get_json()
    print("SMS from", data["sender"], ":", data["message"])
    return jsonify(received=True)
```

### PHP

```php
<?php
if (($_SERVER['HTTP_AUTHORIZATION'] ?? '') !== 'Bearer ' . getenv('SMS_WEBHOOK_TOKEN')) {
    http_response_code(401); exit;
}
$data = json_decode(file_get_contents('php://input'), true);
$key  = $_SERVER['HTTP_X_IDEMPOTENCY_KEY'] ?? '';
// check $key against your database here; skip if already processed
error_log("SMS from {$data['sender']}: {$data['message']}");
header('Content-Type: application/json');
echo json_encode(['received' => true]);
```

### Form data

With **Form data** the body is `application/x-www-form-urlencoded` (`sender=VM-HDFCBK&message=…`). Read it
with `express.urlencoded()`, `request.form` or `$_POST`.

## 6. Testing without a server

Use a request inspector such as https://webhook.site: copy its unique HTTPS URL into a webhook, tap **Test**,
and inspect the request it received.
