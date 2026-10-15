"""Builds the static website in <project>/website from shared templates and the release screenshots.

    python3 release_item/tools/make_website.py v1.0.0

Writes plain HTML (no build step is needed to host it): home, guide index + tutorials, privacy, terms, support,
404, sitemap.xml, robots.txt, and image derivatives (WebP/JPEG) from 4_graphics/screenshots_<ver>/.
Edit the content in this file, re-run, review the diff, then publish the website folder.
"""
import hashlib, html, json, os, re, sys
from datetime import date
from PIL import Image
import brand as B

VER = sys.argv[1] if len(sys.argv) > 1 else "v1.0.0"
RAW = os.path.join(B.KIT, "4_graphics", f"screenshots_{VER}")
SITE = B.WEBSITE
DOMAIN = "https://smswebhook.bestcallerapp.com/"
PLAY = "https://play.google.com/store/apps/details?id=com.bestcallerapp.smswebhook"
EMAIL = "smswebhook@bestcallerapp.com"
TODAY = date.today().isoformat()
UPDATED = "10 October 2026"
VIDEO_SECONDS = 39
APP_VERSION = "1.0.0"
YOUTUBE_PROMO = "eB-wv8JCQBQ"
VIDEO_UPLOADED = "2026-10-15T22:30:00+05:30"  # YouTube publish time (ISO 8601 with timezone)
SOCIAL = [("YouTube", "https://www.youtube.com/channel/UCejj6SJz-D7oxEPjy8I-hmw"),
          ("Instagram", "https://www.instagram.com/bestcallerapp/"),
          ("Facebook", "https://www.facebook.com/bestcallerapp/"),
          ("LinkedIn", "https://www.linkedin.com/company/145186100/")]
ORG_ID = "https://bestcallerapp.com/#org"
APP_ID = DOMAIN + "#app"
ORG = {"@type": "Organization", "@id": ORG_ID, "name": "Best Caller", "url": "https://bestcallerapp.com",
       "logo": DOMAIN + "assets/img/icon-512.png", "email": EMAIL, "sameAs": [u for _, u in SOCIAL],
       "address": {"@type": "PostalAddress", "addressLocality": "Bengaluru", "addressCountry": "IN"}}


def asset_ver(name):
    """Short content hash for cache-busting the stylesheet and script URLs."""
    path = os.path.join(SITE, "assets", name)
    return hashlib.sha1(open(path, "rb").read()).hexdigest()[:8] if os.path.exists(path) else "1"


def ld_script(nodes):
    if not nodes:
        return ""
    data = {"@context": "https://schema.org", "@graph": nodes}
    return '<script type="application/ld+json">\n' + json.dumps(data, ensure_ascii=False).replace("</", "<\\/") + "\n</script>\n"


def crumbs_ld(path, items):
    """items: [(name, path)] after Home; the last one is the current page."""
    trail = [("Home", "")] + items
    return {"@type": "BreadcrumbList", "@id": DOMAIN + path + "#breadcrumb",
            "itemListElement": [{"@type": "ListItem", "position": i + 1, "name": n, "item": DOMAIN + u}
                                for i, (n, u) in enumerate(trail)]}


def strip_tags(text):
    return html.unescape(re.sub(r"<[^>]+>", "", text))

# ---------------------------------------------------------------- images
APP_SHOTS = {  # site name → raw screenshot
    "home": "phone-01-home", "messages": "phone-02-messages", "message-detail": "phone-03-message-detail",
    "deliveries": "phone-03b-message-detail-deliveries", "webhooks": "phone-04-webhooks",
    "webhook-detail": "phone-05-webhook-detail", "headers": "phone-05b-editor-headers", "filters": "phone-06-filters",
    "filters-sample": "phone-06b-filters-sample", "payload-fields": "phone-07a-payload-fields", "payload": "phone-07-payload",
    "extraction": "phone-08-extraction", "extraction-sample": "phone-08b-extraction-sample",
    "delivery-step": "phone-09a-delivery-step", "review": "phone-09b-review", "logs": "phone-09-logs",
    "settings": "phone-10-settings",
}


def make_images():
    app_dir = os.path.join(SITE, "assets", "img", "app")
    guide_dir = os.path.join(SITE, "assets", "img", "guide")
    os.makedirs(app_dir, exist_ok=True); os.makedirs(guide_dir, exist_ok=True)
    for name, raw in APP_SHOTS.items():
        im = B.load_screen(os.path.join(RAW, raw + ".png"))
        # Phone frame (transparent corners) for marketing sections, plain crop for the guide.
        for w in (280, 480, 720):
            B.phone(im, w).save(os.path.join(app_dir, f"{name}-{w}.webp"), "WEBP", quality=86, method=6)
        g = im.resize((480, int(im.height * 480 / im.width)), Image.LANCZOS)
        g.save(os.path.join(guide_dir, f"{name}.jpg"), "JPEG", quality=84, optimize=True, progressive=True)
    icon = Image.open(os.path.join(B.KIT, "4_graphics", "app-icon-512.png")).convert("RGBA")
    for size, path in ((512, "assets/img/icon-512.png"), (192, "assets/img/icon-192.png"), (64, "favicon.png")):
        r = icon.resize((size, size), Image.LANCZOS)
        r.putalpha(B.rounded_mask((size, size), int(size * 0.22)))
        r.save(os.path.join(SITE, path), optimize=True)
    Image.open(os.path.join(B.KIT, "4_graphics", "feature-graphic-1024x500.png")).convert("RGB").save(
        os.path.join(SITE, "assets", "img", "og-image.jpg"), "JPEG", quality=88, optimize=True)
    poster = Image.open(os.path.join(B.KIT, f"6_promo_video_{VER}", "youtube-thumbnail-1280x720.png")).convert("RGB")
    poster.save(os.path.join(SITE, "assets", "img", "video-poster.webp"), "WEBP", quality=82)


def phone_dims(w):
    im = B.load_screen(os.path.join(RAW, APP_SHOTS["home"] + ".png"))
    return B.phone(im, w).size


# ---------------------------------------------------------------- icons (stroke, 24px grid)
ICO = {
    "check": '<path d="M5 12.5l4.5 4.5L19 7.5"/>',
    "sms": '<path d="M4 5h16a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1H9l-5 4V6a1 1 0 0 1 1-1z"/><path d="M8 10h8M8 13h5"/>',
    "filter": '<path d="M4 5h16l-6 7.5V19l-4-2v-4.5z"/>',
    "code": '<path d="M9 8l-4 4 4 4M15 8l4 4-4 4"/>',
    "regex": '<path d="M7 7h.01M17 7h.01"/><path d="M4 17l4-10 4 10M14 12h6M17 9v6"/>',
    "retry": '<path d="M20 12a8 8 0 1 1-2.3-5.7"/><path d="M20 4v5h-5"/>',
    "list": '<path d="M8 6h12M8 12h12M8 18h12M4 6h.01M4 12h.01M4 18h.01"/>',
    "lock": '<rect x="5" y="11" width="14" height="9" rx="2"/><path d="M8 11V8a4 4 0 0 1 8 0v3"/>',
    "shield": '<path d="M12 3l8 3v6c0 4.5-3.4 8.3-8 9-4.6-.7-8-4.5-8-9V6z"/><path d="M8.5 12l2.5 2.5 4.5-5"/>',
    "user": '<circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0 1 16 0"/>',
    "phone": '<rect x="6" y="2.5" width="12" height="19" rx="2.5"/><path d="M11 18.5h2"/>',
    "wifi": '<path d="M2 9a15 15 0 0 1 20 0M5 12.5a10 10 0 0 1 14 0M8.5 16a5 5 0 0 1 7 0M12 19.5h.01"/>',
    "webhook": '<circle cx="12" cy="6" r="2.5"/><circle cx="6" cy="17" r="2.5"/><circle cx="18" cy="17" r="2.5"/><path d="M11 8.2L7.2 14.7M13 8.2l3.8 6.5M8.5 17h7"/>',
    "settings": '<circle cx="12" cy="12" r="3"/><path d="M12 2.5v3M12 18.5v3M2.5 12h3M18.5 12h3M5.3 5.3l2.1 2.1M16.6 16.6l2.1 2.1M5.3 18.7l2.1-2.1M16.6 7.4l2.1-2.1"/>',
    "server": '<rect x="3" y="4" width="18" height="7" rx="1.5"/><rect x="3" y="13" width="18" height="7" rx="1.5"/><path d="M7 7.5h.01M7 16.5h.01"/>',
    "help": '<circle cx="12" cy="12" r="9"/><path d="M9.5 9a2.5 2.5 0 1 1 3.5 2.3c-.6.3-1 .9-1 1.6V14M12 17h.01"/>',
    "play": '<circle cx="12" cy="12" r="9"/><path d="M10 8.5l5.5 3.5-5.5 3.5z"/>',
    "arrow": '<path d="M5 12h14M13 6l6 6-6 6"/>',
    "chev": '<path d="M6 9l6 6 6-6"/>',
    "bank": '<path d="M3 10l9-6 9 6M5 10v8M9.5 10v8M14.5 10v8M19 10v8M3 20h18"/>',
    "key": '<circle cx="8" cy="15" r="4"/><path d="M11 12l9-9M17 6l3 3"/>',
    "bolt": '<path d="M13 2L4 14h7l-1 8 9-12h-7z"/>',
}


def ico(name, cls="ico"):
    return f'<svg class="{cls}" viewBox="0 0 24 24" aria-hidden="true" focusable="false">{ICO[name]}</svg>'


CHECK = ico("check")

# ---------------------------------------------------------------- layout
NAV = [("#features", "Features"), ("#how", "How it works"), ("#screens", "Screens"), ("#video", "Video"),
       ("#faq", "FAQ"), ("guide/", "Guide"), ("support/", "Support")]


def page(path, title, desc, body, home=False, main_cls="wrap doc", extra_head="", ld=None, og_type="website", noindex=False):
    depth = path.count("/")
    up = "../" * depth
    canonical = DOMAIN + path
    nav = "\n".join(
        f'      <a href="{up}{"" if href.startswith("#") else ""}{href if not href.startswith("#") else ("" if home else "") + href}">{label}</a>'
        if not href.startswith("#") else f'      <a href="{"" if home else up}{href}">{label}</a>'
        for href, label in NAV)
    head_extra = ("<meta name=\"robots\" content=\"noindex\">\n" if noindex else "") + extra_head + ld_script(ld)
    follow = "\n".join(f'          <li><a href="{u}" rel="me noopener">{n}</a></li>' for n, u in SOCIAL)
    main_open = '<main id="main">' if home else f'<main id="main" class="{main_cls}">'
    doc = f'''<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<title>{html.escape(title)}</title>
<meta name="description" content="{html.escape(desc)}">
<link rel="canonical" href="{canonical}">
<meta name="theme-color" content="#f7f9fc" media="(prefers-color-scheme: light)">
<meta name="theme-color" content="#0b1220" media="(prefers-color-scheme: dark)">
<link rel="icon" href="{up}favicon.png" type="image/png">
<link rel="apple-touch-icon" href="{up}assets/img/icon-192.png">
<link rel="preload" href="{up}assets/fonts/inter-latin.woff2" as="font" type="font/woff2" crossorigin>
<meta name="robots" content="max-image-preview:large, max-snippet:-1, max-video-preview:-1">
<link rel="stylesheet" href="{up}assets/site.css?v={asset_ver("site.css")}">
<meta property="og:type" content="{og_type}">
<meta property="og:locale" content="en_US">
<meta property="og:site_name" content="SMS Webhook">
<meta property="og:url" content="{canonical}">
<meta property="og:title" content="{html.escape(title)}">
<meta property="og:description" content="{html.escape(desc)}">
<meta property="og:image" content="{DOMAIN}assets/img/og-image.jpg">
<meta property="og:image:width" content="1024">
<meta property="og:image:height" content="500">
<meta property="og:image:alt" content="SMS Webhook: forward SMS to your webhook or API">
<meta name="twitter:card" content="summary_large_image">
<meta name="twitter:title" content="{html.escape(title)}">
<meta name="twitter:description" content="{html.escape(desc)}">
<meta name="twitter:image" content="{DOMAIN}assets/img/og-image.jpg">
{head_extra}<script src="{up}assets/site.js?v={asset_ver("site.js")}" defer></script>
</head>
<body>
<a class="skip" href="#main">Skip to content</a>
<header class="top{"" if home else " sub"}">
  <div class="wrap">
    <a class="brand" href="{up or './'}"><img src="{up}assets/img/icon-192.png" alt="" width="36" height="36">SMS Webhook</a>
    <button class="menu-btn" type="button" aria-expanded="false" aria-controls="site-nav" aria-label="Open menu"><svg class="ico open" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="M4 7h16M4 12h16M4 17h16"/></svg><svg class="ico close" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="M6 6l12 12M18 6L6 18"/></svg></button>
    <nav class="nav" id="site-nav" aria-label="Main">
{nav}
      <a class="btn btn-primary" href="{PLAY}" rel="noopener">Get the app</a>
    </nav>
  </div>
</header>
{main_open}
{body}
</main>
<footer>
  <div class="wrap">
    <div class="foot-grid">
      <div class="foot-brand">
        <a class="brand" href="{up or './'}"><img src="{up}assets/img/icon-192.png" alt="" width="36" height="36" loading="lazy">SMS Webhook</a>
        <p class="by">A product by <a href="https://bestcallerapp.com" rel="noopener"><img src="{up}assets/img/bestcaller-logo.svg" alt="" width="24" height="24" loading="lazy">Best Caller</a></p>
        <p>Forward SMS to your webhook or API.</p>
        <a class="play-badge" href="{PLAY}" rel="noopener"><img src="{up}assets/img/google-play-badge.png" alt="Get it on Google Play" width="646" height="250" loading="lazy"></a>
      </div>
      <nav aria-label="Product">
        <h2>Product</h2>
        <ul>
          <li><a href="{"" if home else up}#features">Features</a></li>
          <li><a href="{"" if home else up}#how">How it works</a></li>
          <li><a href="{"" if home else up}#video">Video</a></li>
          <li><a href="{up}guide/">How-to guide</a></li>
          <li><a href="{up}guide/receive-on-your-server/">Developer guide</a></li>
        </ul>
      </nav>
      <nav aria-label="Help and legal">
        <h2>Help</h2>
        <ul>
          <li><a href="{up}support/">Support</a></li>
          <li><a href="{up}privacy/">Privacy policy</a></li>
          <li><a href="{up}terms/">Terms of use</a></li>
          <li><a href="mailto:{EMAIL}">Email us</a></li>
        </ul>
      </nav>
      <nav aria-label="Follow us">
        <h2>Follow</h2>
        <ul>
{follow}
        </ul>
      </nav>
    </div>
    <div class="foot-bottom">
      <span>© 2026 Best Caller · Bengaluru, India</span>
      <span>SMS Webhook is not affiliated with any bank or mobile operator.</span>
    </div>
  </div>
</footer>
</body>
</html>
'''
    # Checklist items are flex rows: wrap the text after the icon so inline <code> stays in the sentence.
    doc = re.sub(r'(<li><svg class="ico"[^>]*>.*?</svg>)(?!<div)(.*?)</li>', r'\1<span>\2</span></li>', doc)
    out = os.path.join(SITE, path, "index.html") if path and not path.endswith(".html") else os.path.join(SITE, path or "index.html")
    if path == "":
        out = os.path.join(SITE, "index.html")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    open(out, "w").write(doc)
    return out


def shot(name, alt, caption, up):
    w, h = 480, int(480 * 2105 / 1080)
    return (f'<figure class="shot"><img src="{up}assets/img/guide/{name}.jpg" alt="{html.escape(alt)}" width="{w}" '
            f'height="{h}" loading="lazy"><figcaption>{caption}</figcaption></figure>')


def code(src, lang=""):
    return f"<pre><code>{html.escape(src.strip())}</code></pre>"


def faq(items):
    return "\n".join(f'<details><summary>{q}{ico("chev")}</summary><p>{a}</p></details>' for q, a in items)


# ---------------------------------------------------------------- home page
def home():
    pw, ph = phone_dims(480)
    def appimg(name, alt, sizes, lazy=True, cls=""):
        w, h = phone_dims(720)
        return (f'<img{" class=" + chr(34) + cls + chr(34) if cls else ""} src="assets/img/app/{name}-480.webp" '
                f'srcset="assets/img/app/{name}-280.webp 280w, assets/img/app/{name}-480.webp 480w, assets/img/app/{name}-720.webp 720w" '
                f'sizes="{sizes}" alt="{html.escape(alt)}" width="{w}" height="{h}"{" loading=" + chr(34) + "lazy" + chr(34) if lazy else ""}>')
    request_json = '''POST /webhooks/sms HTTP/1.1
Host: payments.example.com
Content-Type: application/json; charset=utf-8
Authorization: Bearer ••••••••
X-Idempotency-Key: 42-3-1791212400412

{
  "sender": "VM-HDFCBK",
  "message": "Rs 5,000.00 credited to A/c XX1234 …",
  "timestamp": "2026-10-05T20:30:00+05:30",
  "amount": "5,000.00",
  "account": "XX1234"
}'''
    faqs = [
        ("Is SMS Webhook free?", "Yes. There is no premium plan, subscription or in-app purchase, and the app shows no ads."),
        ("Do I need an account or a server from you?", "No. There is no sign-up, and we don't run a server that receives your messages. You add the URL of your own server, automation tool or request inspector, and the app sends the SMS there."),
        ("Which messages are forwarded?", "Only the ones you choose. Each webhook either receives every SMS or only messages that match its rules: sender, keywords, regex, SIM, length or time, combined with AND, OR and NOT. One SMS can go to several webhooks."),
        ("What happens when the phone is offline?", "Every SMS is saved on the phone first. Deliveries wait and are sent automatically when the connection returns. Failed requests are retried with increasing delays."),
        ("Can a message be sent twice?", "Duplicate SMS broadcasts are detected, and each message is sent to each webhook once. If your server accepted a request but its reply was lost, the app retries; every request carries an <code>X-Idempotency-Key</code> so your server can ignore the repeat."),
        ("Does it read my old messages or send SMS?", "No. The app only receives new SMS as they arrive. It cannot read your inbox and never sends SMS."),
        ("Does it work with RCS or WhatsApp messages?", "No. Only SMS are forwarded. RCS chat messages and messaging apps are not SMS."),
        ("Can it delete the SMS after forwarding?", "No. Android only lets the default SMS app delete messages, and SMS Webhook is not an SMS app. Your messages stay in your messaging app."),
        ("Which Android versions are supported?", "Android 8.0 and newer, on phones that can receive SMS. Dual-SIM phones are supported."),
    ]
    body = f'''<section class="hero" aria-labelledby="hero-title">
  <div class="wrap hero-grid">
    <div class="hero-copy">
      <span class="pill"><img src="assets/img/icon-192.png" alt="" width="24" height="24"><b>Android app</b> · Free · No ads</span>
      <h1 id="hero-title">Your SMS, <span>delivered</span> to your server.</h1>
      <p class="lead">SMS Webhook forwards the SMS your phone receives to your own webhook or API: bank alerts, OTPs, order updates. Choose which messages go where, shape the JSON, and let the app retry until it's delivered.</p>
      <div class="install">
        <div class="install-main">
          <a class="play-badge" href="{PLAY}" rel="noopener"><img src="assets/img/google-play-badge.png" alt="Get it on Google Play" width="646" height="250"></a>
          <span class="caption">Free · Android 8.0 and newer</span>
        </div>
        <figure class="qr">
          <span class="qr-code"><img src="assets/img/play-qr.svg" alt="QR code that opens SMS Webhook on Google Play" width="96" height="96"></span>
          <figcaption><strong>Scan to install</strong>Point your phone camera at the code</figcaption>
        </figure>
      </div>
      <div class="hero-links">
        <a class="btn" href="#how">{ico("play")}See how it works</a>
        <ul class="trust" aria-label="Highlights">
          <li>{CHECK}No account</li>
          <li>{CHECK}HTTPS only</li>
          <li>{CHECK}Works offline</li>
        </ul>
      </div>
    </div>
    <div class="showcase">
      {appimg("webhooks", "Webhooks screen with three webhooks: CRM, Internal Server and OTP Relay", "(max-width: 900px) 52vw, 300px", lazy=False, cls="back")}
      {appimg("home", "Home screen: service active, 12 messages, 17 delivered, 2 failed, recent bank and OTP messages", "(max-width: 900px) 64vw, 370px", lazy=False, cls="front")}
    </div>
  </div>
</section>

<section id="how" aria-labelledby="how-title">
  <div class="wrap">
    <div class="section-head">
      <span class="eyebrow">How it works</span>
      <h2 id="how-title">From SMS to your API in three steps</h2>
      <p>Everything happens on your phone. Messages are stored first, so nothing is lost when the network drops.</p>
    </div>
    <div class="flow">
      <div class="flow-step"><h3>An SMS arrives</h3><p>The app receives it the moment your phone does, saves it, and ignores duplicate broadcasts.</p></div>
      <div class="flow-step"><h3>Your rules decide</h3><p>Each webhook checks the sender, keywords, regex, SIM or time. One message can match several webhooks.</p></div>
      <div class="flow-step"><h3>Your server gets JSON</h3><p>The app sends an HTTPS POST in your format, records the result, and retries automatically if it fails.</p></div>
    </div>
    <div class="request">
      <div><h3>What your server receives</h3>{code(request_json)}</div>
      <div><h3>Built for real integrations</h3>
        <ul class="checks">
          <li>{CHECK}Your own field names, or a custom JSON template</li>
          <li>{CHECK}Extracted values such as <code>amount</code>, <code>account</code> or <code>otp</code></li>
          <li>{CHECK}Bearer token, Basic auth or API-key headers, encrypted on the phone</li>
          <li>{CHECK}An idempotency key on every request, for safe de-duplication</li>
          <li>{CHECK}Any 2xx response marks the message delivered</li>
        </ul>
        <p style="margin-top:16px"><a class="btn" href="guide/receive-on-your-server/">Developer guide {ico("arrow")}</a></p>
      </div>
    </div>
  </div>
</section>

<section id="features" class="tinted" aria-labelledby="features-title">
  <div class="wrap">
    <div class="section-head">
      <span class="eyebrow">Features</span>
      <h2 id="features-title">Exactly the messages you need, in exactly your format</h2>
      <p>Set it up once. The app keeps forwarding in the background, even after a restart.</p>
    </div>
    <div class="feature-lead">
      <div class="text">
        <div class="icon-tile">{ico("filter")}</div>
        <span class="eyebrow">Smart filters</span>
        <h3 class="h2">Rules for every webhook</h3>
        <p>Send all messages to one webhook and only bank credits to another. Match the sender, words in the message, a keyword list (any or all), a regular expression, the SIM slot, the length or the time of day, and combine them with AND, OR and NOT. Try your rules on a sample SMS before you save.</p>
      </div>
      <figure class="stamp-figure">
        {appimg("filters", "Filter editor: an OR group of sender conditions HDFC, ICICI and SBI", "(max-width: 900px) 70vw, 300px")}
      </figure>
    </div>
    <div class="feature-grid">
      <article class="feature">
        <div class="icon-tile">{ico("code")}</div>
        <h3>Payload your way</h3>
        <p>Send the format your server already understands.</p>
        <ul class="checks">
          <li>{CHECK}JSON with the fields and names you choose</li>
          <li>{CHECK}Custom JSON template with <code>{{{{sender}}}}</code>, <code>{{{{message}}}}</code>…</li>
          <li>{CHECK}Plain text or form data</li>
          <li>{CHECK}Regex extraction: amount, account, OTP as their own fields</li>
          <li>{CHECK}Live preview of the exact request body</li>
        </ul>
      </article>
      <article class="feature">
        <div class="icon-tile">{ico("retry")}</div>
        <h3>Reliable delivery</h3>
        <p>Built to deliver every message, once.</p>
        <ul class="checks">
          <li>{CHECK}Saved on the phone before anything is sent</li>
          <li>{CHECK}Waits for internet, then delivers automatically</li>
          <li>{CHECK}Retries with increasing delays; you set the limits</li>
          <li>{CHECK}Duplicate SMS detected; idempotency key on every request</li>
          <li>{CHECK}Optional: send only on Wi-Fi</li>
        </ul>
      </article>
      <article class="feature">
        <div class="icon-tile">{ico("list")}</div>
        <h3>History and logs</h3>
        <p>See what happened to every message.</p>
        <ul class="checks">
          <li>{CHECK}Search and filter: delivered, failed, pending, filtered</li>
          <li>{CHECK}Per webhook: attempts, HTTP status, response, error</li>
          <li>{CHECK}Delivery log with duration and time</li>
          <li>{CHECK}Retry failed deliveries with one tap</li>
          <li>{CHECK}Keep history for days, a number of messages, or forever</li>
        </ul>
      </article>
    </div>
    <p class="more-link"><a class="btn" href="guide/">How-to guide for every feature {ico("arrow")}</a></p>
  </div>
</section>

<section id="use-cases" aria-labelledby="cases-title">
  <div class="wrap">
    <div class="section-head">
      <span class="eyebrow">Use cases</span>
      <h2 id="cases-title">What people forward</h2>
    </div>
    <div class="cases">
      <div class="case"><div class="icon-tile">{ico("bank")}</div><h3>Payment alerts</h3><p>Send bank credit and debit SMS to your accounting or order system, with the amount extracted.</p><code>Sender contains HDFC</code></div>
      <div class="case"><div class="icon-tile">{ico("key")}</div><h3>OTP for testing</h3><p>Relay one-time passwords from a test phone to your QA automation.</p><code>Message matches \\b\\d{{6}}\\b</code></div>
      <div class="case"><div class="icon-tile">{ico("user")}</div><h3>CRM logging</h3><p>Log every message from your business number in your CRM, with your field names.</p><code>All messages</code></div>
      <div class="case"><div class="icon-tile">{ico("bolt")}</div><h3>Automation</h3><p>Trigger workflows in tools that accept webhooks, such as n8n, Zapier or Make.</p><code>Keywords: order, delivered</code></div>
    </div>
  </div>
</section>

<section id="screens" class="tinted" aria-labelledby="screens-title">
  <div class="wrap">
    <div class="section-head">
      <span class="eyebrow">Screens</span>
      <h2 id="screens-title">See the app</h2>
      <p>Real screens from SMS Webhook. The data is a demo set; your messages never leave your phone except to your own webhooks.</p>
    </div>
    <div class="screens">
      <figure class="featured-shot">
        <figcaption><h3>Exactly what your server receives</h3><p>The editor renders the request body from a sample SMS while you set up the webhook, extracted values included.</p></figcaption>
        <div class="frame">{appimg("payload", "Payload step with a sample HDFC SMS and the JSON request preview including amount and account", "(max-width: 640px) 80vw, 330px")}</div>
      </figure>
      <ul class="shot-grid">
        <li><figure>{appimg("webhooks", "Webhooks list with delivered and failed counts", "(max-width: 640px) 62vw, 200px")}<figcaption><strong>Multiple webhooks</strong>Each with its own rules and counts.</figcaption></figure></li>
        <li><figure>{appimg("extraction-sample", "Regex extraction showing amount 5,000.00 and account XX1234", "(max-width: 640px) 62vw, 200px")}<figcaption><strong>Regex extraction</strong>Values pulled out of the message.</figcaption></figure></li>
        <li><figure>{appimg("deliveries", "Message detail with CRM sent, OTP Relay filtered and Payment Server sent", "(max-width: 640px) 62vw, 200px")}<figcaption><strong>Every delivery</strong>Sent, filtered or failed, per webhook.</figcaption></figure></li>
        <li><figure>{appimg("messages", "Message history with delivered and failed messages", "(max-width: 640px) 62vw, 200px")}<figcaption><strong>Message history</strong>Search and filter every SMS.</figcaption></figure></li>
        <li><figure>{appimg("logs", "Delivery logs with HTTP 200 OK, attempt and duration", "(max-width: 640px) 62vw, 200px")}<figcaption><strong>Delivery logs</strong>Status, duration and time.</figcaption></figure></li>
        <li><figure>{appimg("settings", "Settings: SMS listener, retention and delivery defaults", "(max-width: 640px) 62vw, 200px")}<figcaption><strong>Settings</strong>Retention, retries and privacy.</figcaption></figure></li>
      </ul>
    </div>
  </div>
</section>

<section id="video" aria-labelledby="video-title">
  <div class="wrap">
    <div class="section-head">
      <span class="eyebrow">Video</span>
      <h2 id="video-title">SMS Webhook in {VIDEO_SECONDS} seconds</h2>
    </div>
    <div class="video-layout">
      <div class="video-frame">
        <video controls preload="none" playsinline poster="assets/img/video-poster.webp" width="1280" height="720" aria-label="SMS Webhook product video, {VIDEO_SECONDS} seconds">
          <source src="assets/promo-720p.mp4" type="video/mp4">
          <a href="assets/promo-720p.mp4">Watch the video (MP4)</a>
        </video>
      </div>
      <ol class="how-steps">
        <li><div><h3>Allow SMS</h3><p>Grant the one permission the app needs: receive SMS.</p></div></li>
        <li><div><h3>Add a webhook</h3><p>Paste your HTTPS URL, pick the messages and the format, and tap Test.</p></div></li>
        <li><div><h3>Done</h3><p>New SMS are forwarded in the background. Check History and Logs anytime.</p></div></li>
      </ol>
    </div>
  </div>
</section>

<section id="free" aria-labelledby="free-title" style="border-top:0;padding-top:0">
  <div class="wrap">
    <div class="free">
      <div class="free-copy">
        <h2 id="free-title" class="h2">Everything included. <span>Free.</span></h2>
        <p>Unlimited webhooks, filters, payload formats, extraction, retries and history. No plans, no limits.</p>
      </div>
      <ul class="free-points">
        <li>{CHECK}<div><b>No subscription</b><span>No in-app purchases at all.</span></div></li>
        <li>{CHECK}<div><b>No ads</b><span>No advertising or analytics SDKs.</span></div></li>
        <li>{CHECK}<div><b>No account</b><span>Install, allow SMS, add a webhook.</span></div></li>
      </ul>
    </div>
  </div>
</section>

<section id="privacy" class="tinted" aria-labelledby="privacy-title">
  <div class="wrap">
    <div class="section-head">
      <span class="eyebrow">Privacy</span>
      <h2 id="privacy-title">Your messages go only where you send them</h2>
      <p>We don't run a server that receives your SMS. The app sends them only to the webhook URLs you add.</p>
    </div>
    <div class="trust-grid">
      <div class="trust-item"><div class="icon-tile">{ico("user")}</div><h3>No account</h3><p>No sign-up, no email, no cloud service from us.</p></div>
      <div class="trust-item"><div class="icon-tile">{ico("phone")}</div><h3>Stored on your phone</h3><p>History and logs stay on the device. Phone backup is turned off for app data.</p></div>
      <div class="trust-item"><div class="icon-tile">{ico("lock")}</div><h3>Secrets encrypted</h3><p>API keys and tokens are encrypted with the Android Keystore and never shown in logs.</p></div>
      <div class="trust-item"><div class="icon-tile">{ico("shield")}</div><h3>HTTPS only</h3><p>Requests use verified HTTPS. Redirects to other hosts are never followed.</p></div>
    </div>
    <div class="perm">
      <h3>Permissions, and why the app asks</h3>
      <dl>
        <div><dt>{ico("sms")}Receive SMS</dt><dd>To see new SMS as they arrive. The app can't read your existing inbox and never sends SMS.</dd></div>
        <div><dt>{ico("wifi")}Internet and network state</dt><dd>To send to your webhooks and wait for a connection when you're offline.</dd></div>
      </dl>
      <p class="perm-foot">Full details are in the <a href="privacy/">privacy policy</a>.</p>
    </div>
  </div>
</section>

<section id="faq" aria-labelledby="faq-title">
  <div class="wrap faq-layout">
    <div class="section-head">
      <span class="eyebrow">FAQ</span>
      <h2 id="faq-title">Questions and answers</h2>
      <p>Step-by-step tutorials are in the <a href="guide/">how-to guide</a>. Still stuck? <a href="support/">Contact support</a>.</p>
    </div>
    <div class="faq">
{faq(faqs)}
    </div>
  </div>
</section>

<section id="download" aria-labelledby="download-title" style="border-top:0;padding-top:0">
  <div class="wrap">
    <div class="download">
      <div class="copy">
        <img class="appicon" src="assets/img/icon-192.png" alt="" width="64" height="64" loading="lazy">
        <h2 id="download-title" class="h2">Start forwarding in two minutes</h2>
        <p>Download SMS Webhook for Android. Free, for Android 8.0 and newer.</p>
        <a class="play-badge" href="{PLAY}" rel="noopener"><img src="assets/img/google-play-badge.png" alt="Get it on Google Play" width="646" height="250" loading="lazy"></a>
      </div>
      <figure class="qr">
        <span class="qr-code"><img src="assets/img/play-qr.svg" alt="QR code that opens SMS Webhook on Google Play" width="168" height="168" loading="lazy"></span>
        <figcaption><strong>Scan to install</strong>SMS Webhook</figcaption>
      </figure>
    </div>
  </div>
</section>'''
    app = {"@type": "MobileApplication", "@id": APP_ID, "name": "SMS Webhook", "alternateName": "SMS Webhook: Forward SMS",
           "description": "Free Android app that forwards incoming SMS to your own webhook or HTTP API over HTTPS, with filters, "
                          "JSON payloads, regex extraction, automatic retries and delivery logs.",
           "url": DOMAIN, "image": DOMAIN + "assets/img/icon-512.png", "operatingSystem": "Android 8.0 or later",
           "applicationCategory": "UtilitiesApplication", "applicationSubCategory": "SMS forwarding",
           "softwareVersion": APP_VERSION, "inLanguage": "en", "isAccessibleForFree": True,
           "offers": {"@type": "Offer", "price": "0", "priceCurrency": "INR"},
           "installUrl": PLAY, "downloadUrl": PLAY, "sameAs": [PLAY],
           "screenshot": [DOMAIN + f"assets/img/app/{n}-720.webp" for n in ("webhooks", "filters", "extraction", "deliveries")],
           "featureList": ["Forward incoming SMS to any HTTPS webhook or API",
                           "Multiple webhooks, each with its own filters (sender, keywords, regex, SIM, time; AND / OR / NOT)",
                           "JSON, custom JSON template, plain text or form-data payloads",
                           "Extract OTP, amount or account number with regex",
                           "Bearer token, Basic auth, API-key and custom headers, encrypted with Android Keystore",
                           "Offline queue, automatic retries with exponential backoff, idempotency keys",
                           "Message history and delivery logs with HTTP status",
                           "No account, no ads, no analytics"],
           "publisher": {"@id": ORG_ID}, "author": {"@id": ORG_ID}}
    site = {"@type": "WebSite", "@id": DOMAIN + "#website", "url": DOMAIN, "name": "SMS Webhook", "inLanguage": "en",
            "publisher": {"@id": ORG_ID}}
    video = {"@type": "VideoObject", "name": "SMS Webhook: Forward SMS to Your Webhook or API",
             "description": "How SMS Webhook forwards incoming Android SMS to your own webhook or API: multiple webhooks, "
                            "filters, payload formats, regex extraction, delivery tracking and retries.",
             "thumbnailUrl": [DOMAIN + "assets/img/video-poster.webp", f"https://i.ytimg.com/vi/{YOUTUBE_PROMO}/maxresdefault.jpg"],
             "uploadDate": VIDEO_UPLOADED, "duration": f"PT{VIDEO_SECONDS}S", "contentUrl": DOMAIN + "assets/promo-720p.mp4",
             "embedUrl": f"https://www.youtube.com/embed/{YOUTUBE_PROMO}", "publisher": {"@id": ORG_ID}}
    faq_ld = {"@type": "FAQPage", "@id": DOMAIN + "#faq",
              "mainEntity": [{"@type": "Question", "name": q, "acceptedAnswer": {"@type": "Answer", "text": strip_tags(a)}}
                             for q, a in faqs]}
    og_video = (f'<meta property="og:video" content="{DOMAIN}assets/promo-720p.mp4">\n'
                '<meta property="og:video:type" content="video/mp4">\n<meta property="og:video:width" content="1280">\n'
                '<meta property="og:video:height" content="720">\n')
    return page("", "SMS Webhook – Forward SMS to Webhook or API on Android",
                "Free Android app that forwards incoming SMS to your own webhook or API, with filters, JSON payloads, regex "
                "extraction and automatic retries. No account, no ads.",
                body, home=True, extra_head=og_video, ld=[app, ORG, site, video, faq_ld])


# ---------------------------------------------------------------- guide
def guide_page(slug, title, desc, content, prev=None, nxt=None):
    up = "../../"
    pager = '<nav class="pager" aria-label="More tutorials">'
    if prev: pager += f'<a href="../{prev[0]}/"><small>← Previous</small>{prev[1]}</a>'
    if nxt: pager += f'<a class="next" href="../{nxt[0]}/"><small>Next →</small>{nxt[1]}</a>'
    pager += '<a class="up" href="../">All tutorials</a></nav>'
    body = f'''<p class="crumbs"><a href="{up}">Home</a> › <a href="../">How-to guide</a> › {title}</p>
<h1>{title}</h1>
<p class="meta">{desc}</p>
{content.replace("{UP}", up)}
{pager}'''
    path = f"guide/{slug}/"
    seo_title, seo_desc = GUIDE_SEO.get(slug, (title, desc))
    article = {"@type": "TechArticle", "@id": DOMAIN + path + "#article", "headline": title, "name": seo_title,
               "description": seo_desc, "url": DOMAIN + path, "inLanguage": "en", "dateModified": TODAY,
               "image": DOMAIN + "assets/img/og-image.jpg", "proficiencyLevel": "Beginner",
               "about": {"@id": APP_ID}, "isPartOf": {"@id": DOMAIN + "guide/#guide"},
               "author": {"@id": ORG_ID}, "publisher": ORG, "mainEntityOfPage": DOMAIN + path}
    ld = [article, crumbs_ld(path, [("How-to guide", "guide/"), (title, path)])]
    return page(path, f"{seo_title} | SMS Webhook" if len(seo_title) <= 46 else seo_title, seo_desc, body,
                main_cls="wrap doc guide", ld=ld, og_type="article")


def split(steps_html, shots):
    return f'<div class="split"><div>{steps_html}</div><div class="shots-col">{"".join(shots)}</div></div>'


U = "{UP}"
GUIDES = [
    ("getting-started", "Getting started", "Install the app, allow SMS, and get the service running reliably.", "phone"),
    ("add-a-webhook", "Add a webhook", "Walk through the seven-step webhook editor and test your endpoint.", "webhook"),
    ("filters", "Message filters", "Forward only the messages you need with sender, keyword, regex, SIM and time rules.", "filter"),
    ("payloads", "Payload formats", "Choose JSON, a custom template, plain text or form data, and name your fields.", "code"),
    ("regex-extraction", "Extract values with regex", "Pull amounts, account numbers or OTPs out of the message into their own fields.", "regex"),
    ("headers-and-authentication", "Headers and authentication", "Add Bearer tokens, Basic auth, API keys and custom headers, stored encrypted.", "key"),
    ("delivery-and-retries", "Delivery, retries and offline", "How messages are delivered, retried and kept safe without internet.", "retry"),
    ("history-and-logs", "History and delivery logs", "Find any message, see what happened to it, and retry failures.", "list"),
    ("settings", "Settings, retention and backup", "History retention, delivery defaults, logs, export and import.", "settings"),
    ("receive-on-your-server", "Receive webhooks on your server", "What the app sends, how to respond, and examples in Node.js, Python and PHP.", "server"),
    ("troubleshooting", "Troubleshooting", "Fix the common problems: no SMS, failed deliveries, regex that doesn't match.", "help"),
]

# Search titles (≤ 60 chars) and descriptions (≈ 120–160 chars); the visible H1 and intro stay as in GUIDES.
GUIDE_SEO = {
    "getting-started": ("Getting Started: Forward SMS to a Webhook on Android",
                        "Set up SMS Webhook on Android: install the app, allow the Receive SMS permission, and keep SMS forwarding running reliably in the background."),
    "add-a-webhook": ("Add a Webhook: Send SMS to Any URL or API",
                      "Step by step: add a webhook in SMS Webhook, enter your HTTPS endpoint, choose which SMS to forward and the payload format, then send a test."),
    "filters": ("Filter SMS by Sender, Keyword or Regex Before Forwarding",
                "Forward only the SMS you need: match sender IDs, keywords (any or all), regex, SIM slot, length and time of day, combined with AND, OR and NOT."),
    "payloads": ("SMS to JSON: Payload Formats and Custom Templates",
                 "Send SMS to your API as JSON with your own field names, a custom JSON template, plain text or form data, and pick exactly which fields are sent."),
    "regex-extraction": ("Extract OTP, Amount or Account Number from SMS (Regex)",
                         "Use regex capture groups to pull an OTP, amount, account number or any value out of an SMS and send it as its own JSON field, with a live preview."),
    "headers-and-authentication": ("Webhook Auth: Bearer Token, Basic Auth, API Key Headers",
                                   "Secure your SMS webhook with a Bearer token, Basic auth, API-key or custom headers. Secret values are encrypted on the phone and hidden in logs."),
    "delivery-and-retries": ("Reliable SMS Forwarding: Retries and Offline Queue",
                             "How SMS Webhook delivers every message: stored on the phone first, queued while offline, retried with backoff, and sent with an idempotency key."),
    "history-and-logs": ("SMS Forwarding History and Webhook Delivery Logs",
                         "Search forwarded SMS, see which webhooks matched, every attempt, HTTP status and response, and retry failed deliveries with one tap."),
    "settings": ("Settings: SMS History Retention, Export and Import",
                 "Choose how long SMS history is kept, set delivery defaults, view logs, and export or import your webhook settings with or without secrets."),
    "receive-on-your-server": ("Receive SMS Webhooks on Your Server (Node.js, Python, PHP)",
                               "What SMS Webhook sends to your endpoint, how to respond and handle retries safely, with ready-to-use examples in Node.js, Python and PHP."),
    "troubleshooting": ("SMS Not Forwarding? SMS Webhook Troubleshooting",
                        "Fix common problems: SMS not received or not forwarded, failed or slow webhook deliveries, HTTP errors, and regex that doesn't match."),
}


def guide_content():
    c = {}
    c["getting-started"] = split(f'''<h2>Install and allow SMS</h2>
<ol class="steps">
<li>Install SMS Webhook from Google Play and open it. The <b class="ui">Home</b> screen shows a red <b class="ui">SMS permission required</b> card.</li>
<li>Tap <b class="ui">Grant permission</b> and allow <b>SMS</b>. This is the only SMS permission the app asks for: it can receive new SMS, but it can't read your inbox or send messages.</li>
<li>The card disappears and the status changes to <b class="ui">Service active</b>, with <b class="ui">SMS listener: Active</b> and your internet status.</li>
<li>Add your first webhook: open <b class="ui">Webhooks</b> and tap <b class="ui">Add webhook</b>. See <a href="../add-a-webhook/">Add a webhook</a>.</li>
</ol>
<div class="note"><p>Until you add a webhook, incoming SMS are still saved and shown as <b class="ui">Filtered</b>, so you can see that reception works.</p></div>
<h2>If the permission is blocked</h2>
<ul>
<li>If you tapped <b>Don't allow</b> twice, the card shows <b class="ui">Open app settings</b>. Open it, choose <b>Permissions → SMS → Allow</b>.</li>
<li>On Android 13 and newer, if the app was installed from a file instead of Google Play, the SMS switch may be greyed out. In <b>App info</b>, tap <b>⋮ → Allow restricted settings</b>, then allow SMS.</li>
</ul>
<h2>Keep it running in the background</h2>
<p>Some phones (Samsung, Xiaomi, Oppo, Vivo and others) pause background apps to save battery. If <b class="ui">Battery optimisation is on</b> appears on Home, tap <b class="ui">Battery settings</b> and set SMS Webhook to <b>Unrestricted</b> / <b>Don't optimise</b>. On Samsung also check that the app is not in <b>Sleeping apps</b>.</p>
<h2>Pause forwarding</h2>
<p>Turn off <b class="ui">Forward incoming SMS</b> on Home (or <b class="ui">SMS listener</b> in Settings). The app then ignores new SMS completely until you turn it back on.</p>''',
        [shot("home", "Home screen with Service active, counters and recent messages", "Home: service status, counters and the newest messages.", U)])

    c["add-a-webhook"] = split(f'''<p>Open <b class="ui">Webhooks</b> and tap <b class="ui">Add webhook</b>. The editor has seven steps; <b class="ui">Next</b> checks each step and shows any problem next to the field.</p>
<ol class="steps">
<li><b>Webhook</b>: a <b class="ui">Webhook name</b> (for example <i>Payment Server</i>) and the <b class="ui">Webhook URL</b>. The URL must start with <code>https://</code>. Requests are sent with POST.</li>
<li><b>Headers &amp; authentication</b>: add a <b class="ui">Bearer token</b>, <b class="ui">Basic auth</b>, an <b class="ui">API key header</b> or any custom header. See <a href="../headers-and-authentication/">Headers and authentication</a>.</li>
<li><b>Message filters</b>: <b class="ui">All messages</b>, or <b class="ui">Only matching messages</b> with your rules. See <a href="../filters/">Message filters</a>.</li>
<li><b>Payload</b>: JSON, a custom JSON template, raw text or form data. See <a href="../payloads/">Payload formats</a>.</li>
<li><b>Regex extraction</b> (optional): named values such as <code>amount</code>. See <a href="../regex-extraction/">Extract values with regex</a>.</li>
<li><b>Retry &amp; timeout</b>: <b class="ui">Timeout (seconds)</b>, <b class="ui">Maximum retries</b> and <b class="ui">First retry delay (seconds)</b>. The defaults (15 s, 5 retries, 30 s) suit most servers.</li>
<li><b>Review</b>: check the summary, use <b class="ui">Edit</b> to jump back to any step, leave <b class="ui">Enable webhook</b> on, tap <b class="ui">Send test request</b>, then <b class="ui">Save webhook</b>.</li>
</ol>
<h2>Test your webhook</h2>
<p><b class="ui">Test</b> (on the webhook card, the detail screen or the Review step) sends a sample SMS with your real headers and format. The result shows success or failure, the HTTP status, the response time, the request body and your server's response. Tests aren't saved as deliveries, and the request carries <code>X-SmsWebhook-Test: true</code>.</p>
<div class="note"><p>No server yet? Use a free request inspector such as <a href="https://webhook.site" rel="noopener">webhook.site</a>: paste its HTTPS URL as the webhook URL and tap <b class="ui">Test</b> to see exactly what arrives.</p></div>
<h2>Manage webhooks</h2>
<ul>
<li>The switch on each card enables or disables the webhook.</li>
<li><b class="ui">⋮ → Duplicate</b> copies a webhook. The copy starts disabled, so nothing is sent twice by surprise.</li>
<li><b class="ui">⋮ → Delete</b> removes the webhook and its delivery history.</li>
<li>Tap a card to open its detail screen: full configuration, counters, <b class="ui">Retry failed</b> and the delivery history.</li>
</ul>''',
        [shot("webhooks", "Webhooks list with three webhook cards", "Each card shows the URL, status and delivered/failed counts.", U),
         shot("review", "Review step of the webhook editor", "Step 7: review everything before saving.", U)])

    c["filters"] = split(f'''<h2>All messages or only matching ones</h2>
<p>In step 3 of the editor choose <b class="ui">All messages</b> (every SMS goes to this webhook) or <b class="ui">Only matching messages</b>. Each webhook has its own rules, and one SMS can match several webhooks.</p>
<h2>Build your rules</h2>
<ol class="steps">
<li>Under <b class="ui">Match</b>, choose <b class="ui">All conditions (AND)</b> or <b class="ui">Any condition (OR)</b>.</li>
<li>For each condition pick a <b class="ui">Field</b>, an <b class="ui">Operator</b> and a <b class="ui">Value</b>.</li>
<li><b class="ui">+ Condition</b> adds a rule; <b class="ui">+ Group</b> adds a nested group with its own AND/OR (up to three levels).</li>
<li>Tick <b class="ui">NOT — invert this condition</b> (or group) to match the opposite.</li>
<li>Type a real message into <b class="ui">Try it with a sample SMS</b>: the badge shows <b class="ui">Would be forwarded</b> or <b class="ui">Would be filtered out</b> as you edit.</li>
</ol>
<div class="tablewrap"><table>
<tr><th>Field</th><th>Operators</th></tr>
<tr><td>Sender, Message</td><td>Equals, Does not equal, Contains, Does not contain, Starts with, Ends with, Matches regex, Contains any keyword, Contains all keywords</td></tr>
<tr><td>SIM slot (1, 2), Subscription ID, Message length</td><td>=, ≠, &gt;, &lt;, Between</td></tr>
<tr><td>Time received (HH:mm)</td><td>After, Before, Between (a window like 22:00–06:00 is allowed)</td></tr>
</table></div>
<p>Text rules ignore upper/lower case unless you tick <b class="ui">Case-sensitive</b>. Keyword lists take one keyword per line.</p>
<h2>Examples</h2>
<div class="tablewrap"><table>
<tr><th>Goal</th><th>Rules</th></tr>
<tr><td>HDFC credit alerts</td><td><code>Sender contains HDFC</code> AND <code>Message contains credited</code></td></tr>
<tr><td>Any of three banks</td><td>OR group: <code>Sender contains HDFC</code>, <code>ICICI</code>, <code>SBI</code></td></tr>
<tr><td>Credits or debits</td><td><code>Message contains any keyword</code>: credited, debited</td></tr>
<tr><td>Six-digit OTPs</td><td><code>Message matches regex \\b\\d{{6}}\\b</code></td></tr>
<tr><td>Everything except promotions</td><td><code>Sender starts with AD-</code> with NOT</td></tr>
<tr><td>Only SIM 2 at night</td><td><code>SIM slot = 2</code> AND <code>Time received between 22:00 and 06:00</code></td></tr>
</table></div>
<h2>Regular expressions</h2>
<ul>
<li>A regex matches anywhere in the text. Use <code>^</code> and <code>$</code> to match the whole text.</li>
<li>Tap <b class="ui">Test regex</b> under a regex condition to try it on sample text; you'll see the match and each capture group.</li>
<li>An invalid pattern can't be saved, and the error tells you what is wrong. Patterns that take too long are stopped, so a mistake can't slow the app down.</li>
</ul>''',
        [shot("filters", "Filter editor with an OR group of sender conditions", "An OR group: HDFC, ICICI or SBI.", U),
         shot("filters-sample", "Sample SMS showing Would be forwarded", "Try the rules on a sample SMS.", U)])

    c["payloads"] = split(f'''<h2>Choose a format</h2>
<div class="tablewrap"><table>
<tr><th>Body format</th><th>Content-Type</th><th>Use it when</th></tr>
<tr><td><b class="ui">JSON</b></td><td><code>application/json</code></td><td>Most APIs. Pick fields and rename them.</td></tr>
<tr><td><b class="ui">Custom JSON template</b></td><td><code>application/json</code></td><td>Your API expects a specific shape or nesting.</td></tr>
<tr><td><b class="ui">Raw text</b></td><td><code>text/plain</code></td><td>Chat or logging tools that accept plain text.</td></tr>
<tr><td><b class="ui">Form data</b></td><td><code>application/x-www-form-urlencoded</code></td><td>Classic form handlers (PHP <code>$_POST</code>).</td></tr>
</table></div>
<h2>JSON fields</h2>
<p>Tick the fields to send and type the name your server expects, for example <code>from</code> instead of <code>sender</code>.</p>
<div class="tablewrap"><table>
<tr><th>Field</th><th>Example</th></tr>
<tr><td>Message ID</td><td><code>"3f6c…"</code> stable fingerprint of the SMS, useful for de-duplication</td></tr>
<tr><td>Sender, Message</td><td><code>"VM-HDFCBK"</code>, the full text (long SMS joined)</td></tr>
<tr><td>Timestamp (sent), Received at</td><td><code>"2026-10-05T20:30:00+05:30"</code></td></tr>
<tr><td>SIM slot, Subscription ID</td><td><code>1</code>, <code>3</code></td></tr>
<tr><td>Device model</td><td><code>"Samsung SM-A176B (Android 16)"</code> (off by default)</td></tr>
</table></div>
{code("""{"sender":"VM-HDFCBK","message":"Rs 5,000.00 credited to A/c XX1234 …","timestamp":"2026-10-05T20:30:00+05:30"}""")}
<h2>Templates</h2>
<p>With <b class="ui">Custom JSON template</b> or <b class="ui">Raw text</b>, write the body yourself with placeholders:</p>
{code("""{
  "event": "sms.received",
  "data": {
    "from": "{{sender}}",
    "text": "{{message}}",
    "time": "{{timestamp}}",
    "amount": "{{regex.amount}}"
  }
}""")}
<ul>
<li>Variables: <code>{{{{sender}}}}</code>, <code>{{{{message}}}}</code>, <code>{{{{timestamp}}}}</code>, <code>{{{{received_at}}}}</code>, <code>{{{{id}}}}</code>, <code>{{{{sim_slot}}}}</code>, <code>{{{{subscription_id}}}}</code>, <code>{{{{device}}}}</code> and <code>{{{{regex.name}}}}</code> for extracted values.</li>
<li>In JSON templates values are escaped for you; keep placeholders inside quotes. The app checks that the template produces valid JSON before saving.</li>
<li>Raw text example: <code>{{{{sender}}}}: {{{{message}}}}</code> → <code>VM-HDFCBK: Rs 5,000.00 credited…</code></li>
</ul>
<p><b class="ui">Request body preview</b> under the sample SMS shows the exact body your server will receive.</p>''',
        [shot("payload-fields", "Payload step with JSON fields and names", "Pick fields and name them.", U),
         shot("payload", "Request body preview with amount and account", "The exact request body, live.", U)])

    c["regex-extraction"] = split(f'''<p>Extraction pulls a value out of the message and sends it as its own field, so your server doesn't have to parse SMS text.</p>
<ol class="steps">
<li>In step 5, tap <b class="ui">+ Add extracted field</b>.</li>
<li>Enter a <b class="ui">Field name</b> (letters, digits and <code>_</code>, starting with a letter), for example <code>amount</code>.</li>
<li>Enter a <b class="ui">Regex</b> with a capture group in brackets, for example <code>credited with Rs ([0-9,]+)</code>.</li>
<li>Set the <b class="ui">Capture group</b>: <code>1</code> is the first bracketed group, <code>0</code> the whole match.</li>
<li>Check <b class="ui">Sample result</b> and the values under <b class="ui">Try it with a sample SMS</b>.</li>
</ol>
<h2>Ready-made patterns</h2>
<div class="tablewrap"><table>
<tr><th>Field</th><th>Regex</th><th>Group</th><th>Gets</th></tr>
<tr><td><code>amount</code></td><td><code>(?:Rs\\.?|INR)\\s?([0-9,]+(?:\\.\\d{{2}})?)</code></td><td>1</td><td><code>5,000.00</code></td></tr>
<tr><td><code>account</code></td><td><code>A/c\\s?(XX\\d{{4}})</code></td><td>1</td><td><code>XX1234</code></td></tr>
<tr><td><code>otp</code></td><td><code>\\b(\\d{{6}})\\b</code></td><td>1</td><td><code>482913</code></td></tr>
<tr><td><code>upi_ref</code></td><td><code>UPI ref (\\d{{12}})</code></td><td>1</td><td><code>627815394021</code></td></tr>
</table></div>
<h2>Where the value appears</h2>
<ul>
<li><b>JSON and form data</b>: as an extra field, e.g. <code>"amount": "5,000.00"</code>. No match gives <code>null</code>.</li>
<li><b>Templates</b>: as <code>{{{{regex.amount}}}}</code>. No match gives an empty value.</li>
</ul>''',
        [shot("extraction", "Regex extraction step with the amount field", "Field name, regex and capture group.", U),
         shot("extraction-sample", "Extracted amount and account values for the sample SMS", "Results for the sample SMS.", U)])

    c["headers-and-authentication"] = split(f'''<h2>Quick authentication</h2>
<ul>
<li><b class="ui">Bearer token</b>: enter the token; the app adds <code>Authorization: Bearer …</code>.</li>
<li><b class="ui">Basic auth</b>: enter a username and password; the app adds <code>Authorization: Basic …</code>.</li>
<li><b class="ui">API key header</b>: adds <code>X-API-Key</code>; type your key as the value (rename the header if your API uses another name).</li>
</ul>
<h2>Custom headers</h2>
<p>Tap <b class="ui">+ Add header</b> and enter a <b class="ui">Name</b> and <b class="ui">Value</b>. Up to 30 headers per webhook. <code>Content-Type</code> is set from the payload format unless you add your own.</p>
<h2>Secret values</h2>
<ul>
<li>Tick <b class="ui">Secret (encrypt and hide)</b> for tokens and keys. The app suggests it automatically for names like <code>Authorization</code>, <code>X-API-Key</code> or anything containing <i>token</i>, <i>secret</i> or <i>key</i>.</li>
<li>Secret values are encrypted on the phone with the Android Keystore, hidden on screen (tap the eye to reveal), and never written to logs.</li>
<li>Exports leave secrets out unless you choose to include them. After moving to a new phone, re-enter secret values.</li>
</ul>
<div class="note"><p>Never put passwords in the URL (<code>https://user:pass@…</code>); the app rejects such URLs. Use a header instead.</p></div>''',
        [shot("headers", "Headers and authentication step with Authorization and X-Source headers", "Encrypted Authorization header and a custom header.", U)])

    c["delivery-and-retries"] = split(f'''<h2>What happens to each SMS</h2>
<ol class="steps">
<li>The SMS is saved on the phone. Duplicate broadcasts of the same SMS are ignored.</li>
<li>Every enabled webhook checks its rules. Matches are queued; the rest are marked <b class="ui">Filtered</b> for that webhook.</li>
<li>Each queued delivery is sent as soon as there is internet (or Wi-Fi, if you chose <b class="ui">Only on Wi-Fi</b>).</li>
<li>A 2xx response marks it <b class="ui">Sent</b>. Otherwise the app retries or marks it <b class="ui">Failed</b>.</li>
</ol>
<h2>Statuses</h2>
<div class="tablewrap"><table>
<tr><th>Status</th><th>Meaning</th></tr>
<tr><td><b class="ui">Pending</b></td><td>Waiting to be sent (for example, no internet yet)</td></tr>
<tr><td><b class="ui">Sending</b></td><td>The request is in progress</td></tr>
<tr><td><b class="ui">Retrying</b></td><td>A temporary problem; another attempt is scheduled</td></tr>
<tr><td><b class="ui">Delivered / Sent</b></td><td>Your server answered with 2xx</td></tr>
<tr><td><b class="ui">Failed</b></td><td>A permanent error, or retries ran out. Tap <b class="ui">Retry</b> after fixing the cause</td></tr>
<tr><td><b class="ui">Filtered</b></td><td>The message didn't match this webhook's rules</td></tr>
</table></div>
<h2>Retries</h2>
<ul>
<li>Timeouts, connection errors and HTTP 408, 425, 429 and 5xx are retried. Other 4xx responses and certificate errors are not, because repeating the same request can't succeed.</li>
<li>The wait doubles after each failure: with a 30 s first delay, 30 s → 1 min → 2 min → 4 min…</li>
<li><b class="ui">Maximum retries</b> is the number of retries after the first attempt.</li>
<li>A <b class="ui">Retry</b> button appears on failed deliveries; <b class="ui">Retry failed</b> on the webhook detail screen re-queues them all.</li>
</ul>
<h2>Offline and restarts</h2>
<p>Deliveries wait for a connection and don't use up retries while the phone is offline. They also survive the app being closed and the phone restarting.</p>
<h2>Duplicates</h2>
<p>Each message is sent to each webhook once. If your server accepted a request but its reply never reached the phone, the app retries; use the <code>X-Idempotency-Key</code> header to ignore the repeat. See <a href="../receive-on-your-server/">Receive webhooks on your server</a>.</p>''',
        [shot("deliveries", "Message detail with one card per webhook: Sent, Filtered, Sent", "One card per webhook for every message.", U),
         shot("delivery-step", "Retry and timeout settings step", "Timeout, retries and first retry delay.", U)])

    c["history-and-logs"] = split(f'''<h2>Message history</h2>
<ul>
<li><b class="ui">Messages</b> lists every stored SMS, newest first. Search finds text in the sender or the message.</li>
<li>Filter chips: <b class="ui">All</b>, <b class="ui">Delivered</b>, <b class="ui">Failed</b>, <b class="ui">Pending</b>, <b class="ui">Filtered</b>.</li>
</ul>
<h2>Message detail</h2>
<p>Tap a message to see the full text, sender, times, SIM, parts and ID, then one card per webhook with its status, attempts, HTTP status, last attempt and error. <b class="ui">Show details</b> reveals the exact request body and your server's response. <b class="ui">Delivery attempts</b> lists every request for this message.</p>
<p>The bin icon deletes the message from the app only; the SMS in your messaging app is not touched.</p>
<h2>Delivery logs</h2>
<p><b class="ui">Logs</b> lists every HTTP attempt: webhook, method and URL (query values hidden), outcome, status, attempt number, duration and time. Filter by <b class="ui">Successful</b> or <b class="ui">Failed</b>, open the message, or clear all logs with the icon at the top. Header values are never logged.</p>''',
        [shot("messages", "Message history with search and status filters", "Search and filter the history.", U),
         shot("message-detail", "Message detail with the full SMS", "Full message and its details.", U),
         shot("logs", "Delivery logs with two successful attempts", "Every attempt with status and duration.", U)])

    c["settings"] = split(f'''<div class="tablewrap"><table>
<tr><th>Section</th><th>What you can change</th></tr>
<tr><td><b class="ui">SMS</b></td><td><b class="ui">SMS listener</b> on/off. <b class="ui">Delete SMS after forwarding</b> is unavailable: Android only allows the default SMS app to delete messages.</td></tr>
<tr><td><b class="ui">History</b></td><td><b class="ui">Retention</b>: keep 7, 30 or 365 days, the newest 100 to 100,000 messages (or a custom number), or forever. <b class="ui">Clear history</b>.</td></tr>
<tr><td><b class="ui">Delivery</b></td><td>Defaults for new webhooks (retries, first retry delay, timeout) and <b class="ui">Only on Wi-Fi</b>.</td></tr>
<tr><td><b class="ui">Logs</b></td><td><b class="ui">Keep delivery logs</b>, <b class="ui">Log retention</b> (7–365 days), <b class="ui">Clear logs</b>.</td></tr>
<tr><td><b class="ui">Reliability</b></td><td>Battery optimisation status and shortcut; app permissions.</td></tr>
<tr><td><b class="ui">Privacy &amp; data</b></td><td><b class="ui">Export configuration</b>, <b class="ui">Import configuration</b>, <b class="ui">Clear all application data</b>.</td></tr>
</table></div>
<h2>Retention</h2>
<p>Old messages are removed automatically once a day and right after you change the setting. Messages still waiting to be delivered are never removed.</p>
<h2>Move your setup to another phone</h2>
<ol class="steps">
<li><b class="ui">Export configuration</b> and choose <b class="ui">Without secrets (recommended)</b>, then save the file.</li>
<li>On the new phone, <b class="ui">Import configuration</b> and pick the file. Webhooks arrive <b>disabled</b> so you can review them.</li>
<li>Re-enter secret header values, then enable the webhooks.</li>
</ol>
<div class="note"><p>Phone backup is turned off for this app on purpose: message history and API keys should not be copied to cloud backups. Use export instead.</p></div>''',
        [shot("settings", "Settings screen with SMS, History and Delivery sections", "Settings: SMS, history and delivery.", U)])

    c["receive-on-your-server"] = f'''<h2>The request</h2>
{code("""POST /webhooks/sms HTTP/1.1
Content-Type: application/json; charset=utf-8
User-Agent: SmsWebhook/1.0.0
Authorization: Bearer <your token>
X-Idempotency-Key: 42-3-1791212400412
X-SmsWebhook-Delivery: 118

{"sender":"VM-HDFCBK","message":"Rs 5,000.00 credited to A/c XX1234 ...","timestamp":"2026-10-05T20:30:00+05:30"}""")}
<ul>
<li>Always <code>POST</code>; the body is in the format you chose. Test requests add <code>X-SmsWebhook-Test: true</code>.</li>
<li><code>X-Idempotency-Key</code> is the same for every retry of one message to one webhook. Store it and skip repeats.</li>
</ul>
<h2>How to respond</h2>
<div class="tablewrap"><table>
<tr><th>Your response</th><th>What the app does</th></tr>
<tr><td>Any 2xx</td><td>Marks the message delivered. Answer fast; do heavy work later.</td></tr>
<tr><td>408, 425, 429, 5xx, timeout</td><td>Retries with increasing delays</td></tr>
<tr><td>Other 4xx (400, 401, 403, 404…)</td><td>Fails without retrying; fix the cause and retry from the app</td></tr>
<tr><td>3xx redirect</td><td>Fails; redirects are not followed. Use the final URL</td></tr>
</table></div>
<p>The first 4,000 characters of your response are shown in the app, so a short reply like <code>{{"received":true}}</code> helps when debugging.</p>
<h2>Node.js (Express)</h2>
{code("""import express from "express";
const app = express();
app.use(express.json());
const seen = new Set(); // use your database in production

app.post("/webhooks/sms", (req, res) => {
  if (req.get("Authorization") !== `Bearer ${process.env.SMS_WEBHOOK_TOKEN}`) return res.sendStatus(401);
  const key = req.get("X-Idempotency-Key");
  if (seen.has(key)) return res.json({ received: true, duplicate: true });
  seen.add(key);
  const { sender, message, timestamp } = req.body;
  console.log(`SMS from ${sender} at ${timestamp}: ${message}`);
  res.json({ received: true });
});

app.listen(8080);""")}
<h2>Python (Flask)</h2>
{code("""import os
from flask import Flask, request, jsonify, abort

app = Flask(__name__)
seen = set()  # use your database in production

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
    return jsonify(received=True)""")}
<h2>PHP</h2>
{code("""<?php
if (($_SERVER['HTTP_AUTHORIZATION'] ?? '') !== 'Bearer ' . getenv('SMS_WEBHOOK_TOKEN')) {
    http_response_code(401); exit;
}
$data = json_decode(file_get_contents('php://input'), true);
$key  = $_SERVER['HTTP_X_IDEMPOTENCY_KEY'] ?? '';
// look up $key in your database and skip it if already processed
error_log("SMS from {$data['sender']}: {$data['message']}");
header('Content-Type: application/json');
echo json_encode(['received' => true]);""")}
<h2>Automation tools</h2>
<p>n8n, Zapier, Make and similar tools give you a webhook URL in their "Webhook" trigger. Paste it into SMS Webhook, tap <b class="ui">Test</b>, and map the fields (<code>sender</code>, <code>message</code>, your extracted values) in the tool.</p>
<div class="note"><p>Use HTTPS with a valid certificate (for example Let's Encrypt). Self-signed certificates are rejected for your security.</p></div>'''

    c["troubleshooting"] = f'''<h2>SMS don't appear in the app</h2>
<ul>
<li><b>Permission</b>: Home shows <b class="ui">No permission</b> → tap <b class="ui">Grant permission</b>. If the SMS switch is greyed out in App info, use <b>⋮ → Allow restricted settings</b> first.</li>
<li><b>Forwarding off</b>: turn on <b class="ui">Forward incoming SMS</b>.</li>
<li><b>Force-stopped</b>: after "Force stop", Android pauses the app until you open it again.</li>
<li><b>RCS / chat messages</b>: messages marked "RCS" or "Chat" in your messaging app are not SMS and can't be forwarded.</li>
<li><b>Battery saving</b>: set the app to Unrestricted battery use (see <a href="../getting-started/">Getting started</a>).</li>
</ul>
<h2>Messages are saved but not delivered</h2>
<div class="tablewrap"><table>
<tr><th>You see</th><th>Do this</th></tr>
<tr><td><b class="ui">Filtered</b></td><td>Check the webhook is enabled and test its rules with a sample SMS.</td></tr>
<tr><td><b class="ui">Pending</b> for long</td><td>Check the internet, and <b class="ui">Only on Wi-Fi</b> in Settings.</td></tr>
<tr><td>HTTP 401 / 403</td><td>Fix the token or key in the webhook's headers, then <b class="ui">Retry</b>.</td></tr>
<tr><td>HTTP 404</td><td>Fix the URL path.</td></tr>
<tr><td>HTTP 400 / 422</td><td>Your server rejects the body; check the payload format and field names with <b class="ui">Show details</b>.</td></tr>
<tr><td><i>Unexpected redirect</i></td><td>Use the final URL (with <code>https://</code> and the exact path).</td></tr>
<tr><td><i>TLS/SSL error</i></td><td>Install a valid certificate on your server.</td></tr>
<tr><td><i>Re-enter the secret value</i></td><td>Edit the webhook, type the secret again, save, then <b class="ui">Retry failed</b>.</td></tr>
</table></div>
<h2>My server receives a message twice</h2>
<p>This happens only if your server accepted a request but the reply was lost. Ignore repeats using <code>X-Idempotency-Key</code>.</p>
<h2>My regex doesn't match</h2>
<ul>
<li>Use <b class="ui">Test regex</b> with the real message text.</li>
<li>Escape special characters: <code>\\.</code>, <code>\\(</code>, <code>\\$</code>.</li>
<li>Matching ignores case unless <b class="ui">Case-sensitive</b> is ticked.</li>
</ul>
<p>Still stuck? <a href="{U}support/">Contact support</a>. Please don't send us SMS content or API keys.</p>'''
    return c


def guide_index():
    cards = "\n".join(
        f'      <a class="card" href="{slug}/"><div class="dot">{ico(icon_name)}</div><h3>{title}</h3><p>{desc}</p></a>'
        for slug, title, desc, icon_name in GUIDES)
    body = f'''<p class="crumbs"><a href="../">Home</a> › How-to guide</p>
<h1>How to use SMS Webhook</h1>
<p class="meta">Step-by-step tutorials, from the first SMS to receiving webhooks on your own server.</p>
<div class="grid guide-grid">
{cards}
</div>
<p style="margin-top:32px">Still stuck? See <a href="../support/">Support</a> or email <a href="mailto:{EMAIL}">{EMAIL}</a>.</p>'''
    coll = {"@type": "CollectionPage", "@id": DOMAIN + "guide/#guide", "name": "SMS Webhook how-to guide", "url": DOMAIN + "guide/",
            "inLanguage": "en", "about": {"@id": APP_ID}, "publisher": {"@id": ORG_ID},
            "mainEntity": {"@type": "ItemList", "itemListElement": [
                {"@type": "ListItem", "position": i + 1, "name": t, "url": f"{DOMAIN}guide/{sl}/"}
                for i, (sl, t, _, _) in enumerate(GUIDES)]}}
    return page("guide/", "How-to Guide: Forward SMS to Webhook or API | SMS Webhook",
                "Step-by-step tutorials for SMS Webhook: set up SMS forwarding on Android, filters, JSON payloads, regex "
                "extraction, retries and receiving webhooks on your server.",
                body, main_cls="wrap doc guide", ld=[coll, crumbs_ld("guide/", [("How-to guide", "guide/")])])


# ---------------------------------------------------------------- legal & support
def privacy():
    body = f'''<h1>Privacy Policy</h1>
<p class="meta">SMS Webhook (Android app <code>com.bestcallerapp.smswebhook</code>) · Last updated: {UPDATED}</p>
<p>This policy explains what SMS Webhook ("the app") does with information, published by Best Caller, Bengaluru, India ("we"). In short: <b>the app forwards your SMS only to the web addresses you configure; we do not receive, store or sell your messages.</b></p>

<h2>1. What the app processes, and where it goes</h2>
<div class="tablewrap"><table>
<tr><th>Information</th><th>Why</th><th>Where it is stored</th><th>Where it is sent</th></tr>
<tr><td>Incoming SMS: sender, text, times, SIM slot and subscription</td><td>To forward messages that match your rules</td><td>On your phone, in the app's private storage</td><td>Only to the webhook URLs you add, when a message matches that webhook</td></tr>
<tr><td>Webhook settings (URLs, rules, headers)</td><td>To know where and how to forward</td><td>On your phone. Secret header values are encrypted with the Android Keystore</td><td>Nowhere, unless you export them to a file you choose</td></tr>
<tr><td>Delivery results and logs (status, HTTP code, response, timing)</td><td>To show delivery history and retry failures</td><td>On your phone</td><td>Nowhere</td></tr>
<tr><td>Device model and Android version (optional field, off by default)</td><td>Only if you add the "device" field to a payload</td><td>Not stored</td><td>Only to your own webhook</td></tr>
</table></div>
<p>The webhook URLs are chosen and controlled by you. What happens to forwarded messages on those servers is governed by whoever operates them (usually you or your organisation).</p>

<h2>2. What we do not do</h2>
<ul>
<li>We do not operate any server that receives your SMS, settings or delivery data.</li>
<li>The app has no account or sign-up, and contains no advertising, analytics or crash-reporting SDKs.</li>
<li>The app does not read your existing SMS inbox, does not send SMS, and does not access contacts or location.</li>
<li>We do not sell or share personal information.</li>
</ul>

<h2>3. Permissions</h2>
<ul>
<li><b>Receive SMS</b>: to see new SMS as they arrive. Required for the app to work.</li>
<li><b>Internet and network state</b>: to send to your webhooks and wait for a connection.</li>
<li><b>Foreground service, wake lock, run at startup</b>: used by Android's WorkManager to finish deliveries in the background and after a restart.</li>
</ul>

<h2>4. Security</h2>
<p>Release versions send data only over HTTPS with certificate verification and do not follow redirects. Secret header values are encrypted with a key held in the Android Keystore. App data is excluded from cloud backups and device-to-device transfer.</p>

<h2>5. Retention and deletion</h2>
<p>Messages and logs are kept on your phone according to your retention settings (by default 30 days) and can be deleted at any time in Settings (<b>Clear history</b>, <b>Clear logs</b>, <b>Clear all application data</b>) or by uninstalling the app. Because we hold no copy, there is nothing for us to delete on a server. See <a href="../support/#delete">how to delete your data</a>.</p>

<h2>6. Children</h2>
<p>The app is intended for adults (18+) and is not directed at children.</p>

<h2>7. Changes</h2>
<p>We will update this page when the app's handling of information changes, and change the "Last updated" date above.</p>

<h2>8. Contact and grievance officer</h2>
<p>Questions or complaints: <a href="mailto:{EMAIL}">{EMAIL}</a>. Grievance officer (India): Best Caller, Bengaluru, Karnataka, India, at the same email address. We aim to respond within 7 days.</p>'''
    return page("privacy/", "Privacy Policy · SMS Webhook",
                "How SMS Webhook handles your SMS: stored on your phone and forwarded only to the webhooks you configure. "
                "The developer never receives your messages.", body,
                ld=[{"@type": "WebPage", "@id": DOMAIN + "privacy/", "name": "Privacy Policy", "url": DOMAIN + "privacy/",
                     "about": {"@id": APP_ID}, "publisher": {"@id": ORG_ID}},
                    crumbs_ld("privacy/", [("Privacy policy", "privacy/")])])


def terms():
    body = f'''<h1>Terms of Use</h1>
<p class="meta">SMS Webhook · Last updated: {UPDATED}</p>
<p>By installing or using SMS Webhook ("the app") you agree to these terms. If you do not agree, do not use the app.</p>
<h2>1. The service</h2>
<p>The app forwards SMS received on your device to web addresses that you configure. It is provided free of charge, "as is" and "as available".</p>
<h2>2. Your responsibilities</h2>
<ul>
<li>You must have the right to receive and forward the messages you configure, and comply with applicable law, including data-protection law (for example India's Digital Personal Data Protection Act, 2023) when forwarding messages that contain other people's information.</li>
<li>You are responsible for the security and operation of the servers you forward to, and for the credentials you enter.</li>
<li>Do not use the app to intercept messages on devices you do not own or are not authorised to manage, or for any unlawful purpose.</li>
</ul>
<h2>3. No guarantee of delivery</h2>
<p>Delivery depends on your device, mobile network, internet connection, battery settings and your servers. We do not guarantee that every message will be forwarded, delivered once, or delivered within a particular time. Do not rely on the app as the only channel for critical communications.</p>
<h2>4. Limitation of liability</h2>
<p>To the maximum extent permitted by law, Best Caller is not liable for any indirect, incidental or consequential loss, or for loss of data, revenue or business, arising from use of or inability to use the app.</p>
<h2>5. Changes</h2>
<p>We may update the app and these terms. Continued use after an update means you accept the updated terms.</p>
<h2>6. Law</h2>
<p>These terms are governed by the laws of India; courts in Bengaluru, Karnataka have jurisdiction.</p>
<h2>7. Contact</h2>
<p><a href="mailto:{EMAIL}">{EMAIL}</a></p>'''
    return page("terms/", "Terms of Use · SMS Webhook",
                "Terms of use for SMS Webhook, the free Android app that forwards incoming SMS to the webhooks and APIs you configure.", body,
                ld=[crumbs_ld("terms/", [("Terms of use", "terms/")])])


def support():
    body = f'''<h1>Support</h1>
<p class="meta">SMS Webhook · help, contact and your data</p>
<h2>Contact</h2>
<p>Email <a href="mailto:{EMAIL}">{EMAIL}</a>. Include your phone model, Android version, the app version (Settings → Version), and for delivery problems the status, HTTP code and error shown on the message. <b>Please don't send SMS content or API keys.</b> We usually reply within 2 working days.</p>
<h2>Common questions</h2>
{faq([
    ("No SMS appear in the app", 'Check the permission and the <b>Forward incoming SMS</b> switch on Home, and set battery use to Unrestricted. Details: <a href="../guide/troubleshooting/">Troubleshooting</a>.'),
    ("Deliveries fail with HTTP 401 or 403", "The token or API key is wrong or missing. Edit the webhook's headers, save, then tap Retry."),
    ("How do I test without my own server?", 'Use a request inspector such as webhook.site: paste its HTTPS URL as the webhook URL and tap Test.'),
    ("Can the app delete SMS after forwarding?", "No. Android only allows the default SMS app to delete messages."),
    ("I moved to a new phone", "Export the configuration on the old phone, import it on the new one, then re-enter secret headers and enable the webhooks."),
])}
<h2 id="delete">Delete your data</h2>
<p>The app has no account and we keep no copy of your messages or settings, so there is nothing for us to delete on a server. You control everything on your phone:</p>
<ul>
<li><b>Message history</b>: Settings → <b>Clear history</b>, or set a shorter <b>Retention</b>.</li>
<li><b>Delivery logs</b>: Settings → <b>Clear logs</b>, or turn off <b>Keep delivery logs</b>.</li>
<li><b>Everything</b> (messages, webhooks, keys, settings): Settings → <b>Clear all application data</b>, or uninstall the app.</li>
<li><b>Data already forwarded</b> to your webhooks is stored on those servers; delete it there.</li>
</ul>
<p>Read the full <a href="../privacy/">privacy policy</a>.</p>'''
    return page("support/", "Support, Contact and Data Deletion · SMS Webhook",
                "Get help with SMS Webhook: email support, find answers in the how-to guide, and learn how to delete your data from the app.", body,
                ld=[{"@type": "ContactPage", "@id": DOMAIN + "support/", "name": "SMS Webhook support", "url": DOMAIN + "support/",
                     "about": {"@id": APP_ID},
                     "publisher": {**ORG, "contactPoint": {"@type": "ContactPoint", "contactType": "customer support",
                                                           "email": EMAIL, "availableLanguage": ["English"]}}},
                    crumbs_ld("support/", [("Support", "support/")])])


def not_found():
    body = '''<h1>Page not found</h1><p class="meta">That page does not exist.</p><p><a href="/">Go to the home page</a> or the <a href="/guide/">how-to guide</a>.</p>'''
    out = page("404.html", "Page not found · SMS Webhook", "Page not found.", body, noindex=True)
    # 404 is served from any path, so use absolute asset URLs.
    s = open(out).read().replace('href="favicon.png"', 'href="/favicon.png"').replace('"assets/', '"/assets/').replace('href="./"', 'href="/"')
    for a in ('guide/', 'support/', 'privacy/', 'terms/'):
        s = s.replace(f'href="{a}', f'href="/{a}')
    s = s.replace('href="#', 'href="/#')
    open(out, "w").write(s)


def hosting_files():
    # Sitemap (sitemaps.org 0.9 + Google image/video extensions). Google uses <loc> and <lastmod> and ignores
    # <priority>/<changefreq>, so only those are written.
    esc = lambda t: html.escape(t, quote=True)
    pages = [("", ["webhooks", "filters", "payload", "extraction", "deliveries", "logs"])] + [("guide/", [])] + \
            [(f"guide/{g[0]}/", []) for g in GUIDES] + [("privacy/", []), ("terms/", []), ("support/", [])]
    sm = ['<?xml version="1.0" encoding="UTF-8"?>',
          '<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9"',
          '        xmlns:image="http://www.google.com/schemas/sitemap-image/1.1"',
          '        xmlns:video="http://www.google.com/schemas/sitemap-video/1.1">']
    for u, shots in pages:
        sm.append("  <url>")
        sm.append(f"    <loc>{esc(DOMAIN + u)}</loc>")
        sm.append(f"    <lastmod>{TODAY}</lastmod>")
        for n in shots:
            sm.append(f"    <image:image><image:loc>{esc(DOMAIN)}assets/img/app/{n}-720.webp</image:loc></image:image>")
        if u == "":
            sm += ["    <video:video>",
                   f"      <video:thumbnail_loc>{esc(DOMAIN)}assets/img/video-poster.webp</video:thumbnail_loc>",
                   "      <video:title>SMS Webhook: Forward SMS to Your Webhook or API</video:title>",
                   "      <video:description>How SMS Webhook forwards incoming Android SMS to your own webhook or API: "
                   "filters, payload formats, regex extraction, delivery tracking and retries.</video:description>",
                   f"      <video:content_loc>{esc(DOMAIN)}assets/promo-720p.mp4</video:content_loc>",
                   f"      <video:player_loc>https://www.youtube.com/embed/{YOUTUBE_PROMO}</video:player_loc>",
                   f"      <video:duration>{VIDEO_SECONDS}</video:duration>",
                   f"      <video:publication_date>{VIDEO_UPLOADED}</video:publication_date>",
                   "      <video:family_friendly>yes</video:family_friendly>",
                   "    </video:video>"]
        sm.append("  </url>")
    sm.append("</urlset>")
    open(os.path.join(SITE, "sitemap.xml"), "w").write("\n".join(sm) + "\n")
    open(os.path.join(SITE, "robots.txt"), "w").write(f"User-agent: *\nAllow: /\n\nSitemap: {DOMAIN}sitemap.xml\n")
    llms()
    open(os.path.join(SITE, "CNAME"), "w").write("smswebhook.bestcallerapp.com\n")
    open(os.path.join(SITE, ".nojekyll"), "w").write("")
    open(os.path.join(SITE, "README.md"), "w").write(f"""# SMS Webhook — website

Static site for the app at **{DOMAIN}** (generated; do not edit HTML by hand).

| Page | URL | Use |
|---|---|---|
| Home | `/` | Play listing "Website", marketing |
| How-to guide | `/guide/`, `/guide/<topic>/` | {len(GUIDES)} tutorials; linked from the app (Settings → How-to guide) |
| Privacy policy | `/privacy/` | Play Console → App content; in-app link |
| Terms of use | `/terms/` | In-app link |
| Support + data deletion | `/support/` (`#delete`) | Play listing; in-app "Contact support" |

**Source:** `release_item/tools/make_website.py` (content and templates) + `release_item/4_graphics/screenshots_<ver>/`.
Regenerate with `python3 release_item/tools/make_website.py v1.0.0`, review, then publish this folder.

**Hosting:** GitHub Pages (or any static host). `CNAME` holds the custom domain; DNS: `CNAME smswebhook → <user>.github.io`.
No build step, no trackers, fonts self-hosted (Inter, OFL).
""")


def llms():
    """llms.txt (llmstxt.org): a plain summary of the app and links to every page, for AI assistants and search."""
    lines = ["# SMS Webhook", "",
             "> SMS Webhook is a free Android app by Best Caller that forwards the SMS a phone receives to the user's own "
             "webhook or HTTP API over HTTPS, in real time. No account, no ads, no analytics; the developer never receives "
             "message content.", "",
             f"Package: com.bestcallerapp.smswebhook · Version {APP_VERSION} · Android 8.0+ · Free · Publisher: Best Caller "
             f"(Bengaluru, India) · Contact: {EMAIL}", "",
             "Key facts:",
             "- Forwards incoming SMS only (permission RECEIVE_SMS). It does not read the existing inbox, send SMS, or forward RCS/WhatsApp.",
             "- Multiple webhooks, each with its own filters: sender, message text, keywords (ANY/ALL), regex, SIM slot, length, time of day; combined with AND / OR / NOT.",
             "- Payload formats: JSON with chosen and renamed fields, custom JSON template ({{sender}}, {{message}}, {{timestamp}}…), plain text, form data.",
             "- Regex capture groups extract values such as amount, OTP or account number into their own fields.",
             "- Custom headers, Bearer token, Basic auth, API-key headers; secrets encrypted with Android Keystore.",
             "- Reliable delivery: messages stored on the phone first, offline queue, background jobs, exponential-backoff retries, duplicate detection, X-Idempotency-Key header.",
             "- Message history and delivery logs with HTTP status, response and attempts; one-tap retry.",
             "- Typical uses: payment/UPI/bank alert reconciliation in a backend, OTP relay for QA and test automation, CRM logging, Zapier / n8n / Make workflows.",
             "", "## Docs", f"- [Home]({DOMAIN}): overview, features, FAQ", f"- [How-to guide]({DOMAIN}guide/): all tutorials"]
    lines += [f"- [{t}]({DOMAIN}guide/{sl}/): {GUIDE_SEO.get(sl, (t, d))[1]}" for sl, t, d, _ in GUIDES]
    lines += ["", "## Policies", f"- [Privacy policy]({DOMAIN}privacy/)", f"- [Terms of use]({DOMAIN}terms/)",
              f"- [Support and data deletion]({DOMAIN}support/)",
              "", "## Links", f"- [Google Play]({PLAY})", f"- [Product video on YouTube](https://youtu.be/{YOUTUBE_PROMO})",
              "- [Source code](https://github.com/raushan7999/smswebhook)"]
    lines += [f"- [{n}]({u})" for n, u in SOCIAL]
    open(os.path.join(SITE, "llms.txt"), "w").write("\n".join(lines) + "\n")


def promo_video():
    src = os.path.join(B.KIT, f"6_promo_video_{VER}", "promo-landscape-1920x1080.mp4")
    dst = os.path.join(SITE, "assets", "promo-720p.mp4")
    if os.path.exists(src):
        import subprocess
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", src, "-vf", "scale=1280:720", "-c:v", "libx264", "-crf", "24",
                        "-preset", "slow", "-c:a", "aac", "-b:a", "128k", "-movflags", "+faststart", dst], check=True)


if __name__ == "__main__":
    make_images()
    promo_video()
    home()
    guide_index()
    content = guide_content()
    for i, (slug, title, desc, _) in enumerate(GUIDES):
        prev = (GUIDES[i - 1][0], GUIDES[i - 1][1]) if i > 0 else None
        nxt = (GUIDES[i + 1][0], GUIDES[i + 1][1]) if i < len(GUIDES) - 1 else None
        guide_page(slug, title, desc, content[slug], prev, nxt)
    privacy(); terms(); support(); not_found(); hosting_files()
    print("website →", SITE)
