"""Motion-graphics teaser for SMS Webhook, built from the real app screenshots.

    python3 make_teaser.py v1.0.0 vertical      # 1080x1920 (YouTube Shorts / Reels / WhatsApp Status)
    python3 make_teaser.py v1.0.0 landscape     # 1920x1080 (Play promo video on YouTube)
    python3 make_teaser.py v1.0.0 vertical 2.5  # render one still at 2.5 s (layout check)

Frames are drawn with Pillow and piped to ffmpeg (H.264, 30 fps). make_audio.py adds the soundtrack.
"""
import math, os, subprocess, sys
from functools import lru_cache
from PIL import Image, ImageDraw
import brand as B

VER = sys.argv[1] if len(sys.argv) > 1 else "v1.0.0"
MODE = sys.argv[2] if len(sys.argv) > 2 else "vertical"
STILL = float(sys.argv[3]) if len(sys.argv) > 3 else None
LAND = MODE == "landscape"
W, H = (1920, 1080) if LAND else (1080, 1920)
FPS = 30
RAW = os.path.join(B.KIT, "4_graphics", f"screenshots_{VER}")
OUT = os.path.join(B.KIT, f"6_promo_video_{VER}")
MONO = os.path.join(os.path.dirname(os.path.abspath(__file__)), "fonts", "NotoSans-Bold.ttf")


# ---------- easing ----------
def clamp(x, a=0.0, b=1.0): return max(a, min(b, x))
def prog(t, start, dur): return clamp((t - start) / dur) if dur > 0 else float(t >= start)
def out_cubic(x): return 1 - (1 - x) ** 3
def in_out(x): return 3 * x * x - 2 * x * x * x
def out_back(x, s=1.70158): x -= 1; return 1 + x * x * ((s + 1) * x + s)


# ---------- assets ----------
@lru_cache(None)
def screen(name): return B.load_screen(os.path.join(RAW, name + ".png"))


@lru_cache(None)
def phone(name, width): return B.phone(screen(name), width)


@lru_cache(None)
def phone_shadow(name, width): return B.shadow(phone(name, width), 22 if LAND else 26, 120)


@lru_cache(None)
def background(): return B.background((W, H)).convert("RGBA")


@lru_cache(None)
def text_sprite(text, size, weight="bold", color=B.WHITE, max_w=None):
    f = B.font(size, weight)
    max_w = max_w or int(W * 0.86)
    probe = ImageDraw.Draw(Image.new("RGBA", (10, 10)))
    lines = B.rich_lines(text, f, max_w, probe)
    h = int(len(lines) * size * 1.15 + size * 0.4)
    im = Image.new("RGBA", (max_w + 40, h), (0, 0, 0, 0))
    B.draw_rich_center(ImageDraw.Draw(im), im.width // 2, int(size * 0.05), text, f, max_w, fill=color, line_gap=1.15)
    return im.crop(im.getbbox() or (0, 0, 1, 1))


@lru_cache(None)
def icon(size):
    im = Image.open(os.path.join(B.KIT, "4_graphics", "app-icon-512.png")).convert("RGBA").resize((size, size), Image.LANCZOS)
    im.putalpha(B.rounded_mask((size, size), int(size * 0.23)))
    return im


# ---------- drawing helpers ----------
def fade(img, a):
    if a >= 0.999: return img
    im = img.copy()
    im.putalpha(im.getchannel("A").point(lambda v: int(v * a)))
    return im


def put(frame, img, cx, cy, a=1.0, scale=1.0, rot=0.0):
    if a <= 0.005: return
    if scale != 1.0:
        img = img.resize((max(1, int(img.width * scale)), max(1, int(img.height * scale))), Image.BILINEAR)
    if rot:
        img = img.rotate(rot, expand=True, resample=Image.BICUBIC)
    frame.alpha_composite(fade(img, a), (int(cx - img.width / 2), int(cy - img.height / 2)))


def title(frame, t, text, sub=None, start=0.1, top=False):
    p = out_cubic(prog(t, start, 0.55))
    size = 64 if LAND else 76
    spr = text_sprite(text, size, max_w=760 if LAND else 940)
    cx, cy = ((W // 2, 170) if top else (560, 430)) if LAND else (W // 2, 230)
    put(frame, spr, cx, cy + (1 - p) * 60, p)
    if sub:
        q = out_cubic(prog(t, start + 0.25, 0.55))
        s = text_sprite(sub, 34 if LAND else 38, "regular", B.MUTED, max_w=740 if LAND else 900)
        put(frame, s, cx, cy + spr.height / 2 + 30 + s.height / 2 + (1 - q) * 40, q)


def phone_size(): return 430 if LAND else 640


def phone_pos():
    w = phone_size()
    h = int(w * 2105 / 1080) + int(w * 0.056)
    return ((1400, 120 + h // 2) if LAND else (W // 2, 470 + h // 2)), h


def show_phone(frame, t, name, start=0.0, dx=0, extra_scale=1.0, alpha=1.0):
    p = out_cubic(prog(t, start, 0.7))
    (cx, cy), h = phone_pos()
    if LAND:
        cx += (1 - p) * 700
    else:
        cy += (1 - p) * 900
    cx += dx
    w = phone_size()
    sh, pad = phone_shadow(name, w)
    a = alpha * clamp(p * 1.5)
    put(frame, sh, cx, cy + 10, a, extra_scale)
    put(frame, phone(name, w), cx, cy, a, extra_scale)
    return cx, cy, w, h


def chip(text, size=30, fill=(255, 255, 255, 38), fg=B.WHITE, outline=(255, 255, 255, 110)):
    f = B.font(size, "bold")
    tw = ImageDraw.Draw(Image.new("RGBA", (1, 1))).textlength(text, font=f)
    im = Image.new("RGBA", (int(tw + size * 1.4), int(size * 1.9)), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle((0, 0, im.width - 1, im.height - 1), im.height // 2, fill=fill, outline=outline, width=2)
    d.text((size * 0.7, size * 0.3), text, font=f, fill=fg)
    return im


chip = lru_cache(None)(chip)


@lru_cache(None)
def code_card(lines, size):
    """A dark rounded card with monospace-looking JSON (Noto Sans Bold keeps it legible on video)."""
    from PIL import ImageFont
    f = ImageFont.truetype(MONO, size)
    probe = ImageDraw.Draw(Image.new("RGBA", (1, 1)))
    w = int(max(probe.textlength(l, font=f) for l in lines) + size * 2)
    h = int(len(lines) * size * 1.45 + size * 1.4)
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle((0, 0, w - 1, h - 1), int(size * 0.8), fill=(11, 18, 32, 235), outline=(255, 255, 255, 60), width=2)
    y = size * 0.7
    for line in lines:
        x = size
        key = line.split(":")[0] if ":" in line else None
        if key and key.strip().startswith('"'):
            d.text((x, y), key + ":", font=f, fill=B.SKY_LIGHT)
            x += probe.textlength(key + ":", font=f)
            d.text((x, y), line[len(key) + 1:], font=f, fill=B.YELLOW)
        else:
            d.text((x, y), line, font=f, fill=B.WHITE)
        y += size * 1.45
    return im


def draw_icon(kind, s):
    im = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.ellipse((0, 0, s - 1, s - 1), fill=B.YELLOW)
    k, lw, col = s / 100, max(3, s // 18), B.NAVY
    if kind == "check":
        d.line([(30 * k, 52 * k), (45 * k, 67 * k), (72 * k, 36 * k)], fill=col, width=lw, joint="curve")
    elif kind == "lock":
        d.rounded_rectangle((30 * k, 46 * k, 70 * k, 74 * k), 5 * k, outline=col, width=lw)
        d.arc((37 * k, 26 * k, 63 * k, 56 * k), 180, 360, fill=col, width=lw)
    elif kind == "shield":
        d.polygon([(50 * k, 24 * k), (74 * k, 33 * k), (72 * k, 55 * k), (50 * k, 77 * k), (28 * k, 55 * k), (26 * k, 33 * k)], outline=col, width=lw)
        d.line([(39 * k, 50 * k), (47 * k, 58 * k), (61 * k, 42 * k)], fill=col, width=lw)
    elif kind == "cloud":
        d.ellipse((24 * k, 44 * k, 50 * k, 70 * k), outline=col, width=lw)
        d.ellipse((40 * k, 32 * k, 72 * k, 64 * k), outline=col, width=lw)
        d.line([(37 * k, 70 * k), (68 * k, 70 * k)], fill=col, width=lw)
    elif kind == "person":
        d.ellipse((38 * k, 24 * k, 62 * k, 48 * k), outline=col, width=lw)
        d.arc((26 * k, 52 * k, 74 * k, 96 * k), 180, 360, fill=col, width=lw)
    elif kind == "file":
        d.rectangle((32 * k, 26 * k, 68 * k, 74 * k), outline=col, width=lw)
        d.line([(40 * k, 42 * k), (60 * k, 42 * k)], fill=col, width=lw); d.line([(40 * k, 56 * k), (60 * k, 56 * k)], fill=col, width=lw)
    return im


@lru_cache(None)
def card(kind, text, w, hr=0.36):
    h = int(w * hr)
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle((0, 0, w - 1, h - 1), int(h * 0.22), fill=(255, 255, 255, 34), outline=(255, 255, 255, 90), width=2)
    ic = draw_icon(kind, int(h * 0.56))
    im.alpha_composite(ic, (int(h * 0.2), (h - ic.height) // 2))
    f = B.font(min(int(h * 0.2), 44), "bold")
    lines = B.rich_lines(text, f, w - int(h * 1.0), d)
    y = h / 2 - len(lines) * f.size * 0.62
    for line in lines:
        d.text((int(h * 0.9), y), " ".join("".join(t for t, _ in w_) for w_ in line), font=f, fill=B.WHITE)
        y += f.size * 1.2
    return im


@lru_cache(None)
def sms_bubble(sender, text, w):
    """An incoming-SMS notification card."""
    f1, f2 = B.font(30), B.font(28, "regular")
    probe = ImageDraw.Draw(Image.new("RGBA", (1, 1)))
    lines = B.rich_lines(text, f2, w - 60, probe)
    h = 80 + len(lines) * 38 + 20
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle((0, 0, w - 1, h - 1), 26, fill=(255, 255, 255, 245))
    d.ellipse((24, 22, 56, 54), fill=B.SKY)
    d.text((70, 20), sender, font=f1, fill=B.NAVY)
    y = 68
    for line in lines:
        d.text((28, y), " ".join("".join(t for t, _ in w_) for w_ in line), font=f2, fill=(60, 70, 85))
        y += 38
    return im


# ---------- scenes ----------
def s_intro(f, t):
    p = out_back(prog(t, 0.1, 0.7))
    cx, cy = (W // 2, 380) if LAND else (W // 2, 700)
    put(f, icon(300 if LAND else 380), cx, cy, clamp(p * 2), max(0.01, p))
    q = out_cubic(prog(t, 0.7, 0.6))
    put(f, text_sprite("SMS Webhook", 110 if LAND else 104), cx, cy + (260 if LAND else 330) + (1 - q) * 50, q)
    r = out_cubic(prog(t, 1.1, 0.6))
    tag = text_sprite("Forward SMS to your **webhook** or **API**", 46, "regular", B.MUTED, max_w=1300 if LAND else 900)
    put(f, tag, cx, cy + (370 if LAND else 470) + (1 - r) * 40, r)


def s_flow(f, t):
    """SMS arrives → app → JSON request leaves for your server."""
    title(f, t, "Every SMS, **straight to your server**", top=LAND)
    sms = sms_bubble("VM-HDFCBK", "Rs 5,000.00 credited to A/c XX1234 by UPI ref 6278…", 760 if LAND else 820)
    payload = code_card(('{', '  "sender": "VM-HDFCBK",', '  "amount": "5,000.00",', '  "account": "XX1234"', '}'), 34 if LAND else 36)
    a = out_back(prog(t, 0.4, 0.6))
    if LAND:
        sx, sy, ix, iy, px, py = 420, 560, W // 2, 560, 1480, 560
    else:
        sx, sy, ix, iy, px, py = W // 2, 560, W // 2, 900, W // 2, 1340
    put(f, sms, sx, sy - (1 - a) * 120, clamp(a * 2))
    b = out_back(prog(t, 1.2, 0.6))
    put(f, icon(220 if LAND else 240), ix, iy, clamp(b * 2), max(0.01, b))
    # Moving dots along the path SMS → app → server.
    layer = Image.new("RGBA", f.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    for seg, (x0, y0, x1, y1, st) in enumerate(((sx, sy, ix, iy, 1.4), (ix, iy, px, py, 2.0))):
        for k in range(4):
            u = ((t - st) * 0.9 - k * 0.22)
            if 0 <= u <= 1:
                x, y = x0 + (x1 - x0) * u, y0 + (y1 - y0) * u
                d.ellipse((x - 9, y - 9, x + 9, y + 9), fill=B.YELLOW + (230,))
    f.alpha_composite(layer)
    c = out_back(prog(t, 2.3, 0.6))
    put(f, payload, px, py, clamp(c * 2), 0.8 + 0.2 * c)
    e = out_cubic(prog(t, 2.9, 0.5))
    lab = chip("HTTP 200 · Delivered", 30, fill=B.YELLOW + (255,), fg=B.NAVY, outline=None)
    put(f, lab, px, py + payload.height / 2 + 50, e)


def s_webhooks(f, t):
    title(f, t, "**Multiple** webhooks", "Each with its own filters, payload and retries")
    show_phone(f, t, "phone-04-webhooks", 0.15)


FILTER_CHIPS = ["Sender", "Keywords", "Regex", "SIM slot", "Time of day", "AND / OR / NOT"]


def s_filters(f, t):
    title(f, t, "Smart **filters**", "Forward only the messages you need")
    cx, cy, w, h = show_phone(f, t, "phone-06-filters", 0.15)
    for j, lab in enumerate(FILTER_CHIPS):
        p = prog(t, 0.7 + j * 0.16, 0.45)
        if p <= 0: continue
        c = chip(lab, 30 if not LAND else 28, fill=(255, 255, 255, 230), fg=B.NAVY, outline=None)
        side = -1 if j % 2 == 0 else 1
        if LAND:
            tx, ty = 560 + (-200 if side < 0 else 200), 640 + (j // 2) * 80
        else:
            tx, ty = cx + side * (w / 2 + 40), cy - h / 2 + 160 + (j // 2) * 230
            tx = clamp(tx, c.width / 2 + 20, W - c.width / 2 - 20)
        sx = tx - side * 160
        e = out_back(p)
        put(f, c, sx + (tx - sx) * e, ty, clamp(p * 2), 0.6 + 0.4 * e)


FORMATS = ["JSON", "JSON template", "Raw text", "Form data"]


def s_payload(f, t):
    title(f, t, "Your **JSON**, your way", "Pick fields and names, or write a template")
    show_phone(f, t, "phone-07-payload", 0.15)
    step = 0.6
    on = min(len(FORMATS) - 1, int(max(0, t - 0.8) // step))
    for j, lab in enumerate(FORMATS):
        a = out_cubic(prog(t, 0.5 + j * 0.1, 0.4))
        c = chip(lab, 30, fill=(B.YELLOW + (255,)) if j == on else (255, 255, 255, 38),
                 fg=B.NAVY if j == on else B.WHITE, outline=None if j == on else (255, 255, 255, 110))
        if LAND:
            put(f, c, 560, 640 + j * 80, a)
        else:
            put(f, c, [175, 420, 665, 905][j], H - 120, a)


def s_extract(f, t):
    title(f, t, "Extract values with **regex**", "Amount, account or OTP becomes its own field")
    cx, cy, w, h = show_phone(f, t, "phone-08b-extraction-sample", 0.15)
    for j, lab in enumerate(("amount = 5,000.00", "account = XX1234")):
        p = out_back(prog(t, 1.0 + j * 0.35, 0.5))
        c = chip(lab, 32, fill=B.YELLOW + (255,), fg=B.NAVY, outline=None)
        if LAND:
            put(f, c, 560, 660 + j * 90, clamp(p * 2), 0.7 + 0.3 * p)
        else:
            put(f, c, cx, cy + h * 0.30 + j * 90, clamp(p * 2), 0.7 + 0.3 * p)


def s_tracked(f, t):
    title(f, t, "Every **delivery** tracked", "Status, attempts, HTTP code and response")
    show_phone(f, t, "phone-03b-message-detail-deliveries", 0.15)


RELIABLE = [("cloud", "Works offline, sends when back online"), ("check", "Automatic retries with backoff"),
            ("shield", "No duplicates: idempotency key"), ("file", "History and delivery logs")]


def s_reliable(f, t):
    title(f, t, "**Reliable** by design", top=True)
    cols = 2 if LAND else 1
    cw = 760 if LAND else 900
    x0 = W / 2 - (cols - 1) * (cw + 30) / 2
    y0 = 420 if LAND else 560
    for j, (kind, text) in enumerate(RELIABLE):
        p = prog(t, 0.4 + j * 0.18, 0.5)
        if p <= 0: continue
        c = card(kind, text, cw, 0.26 if LAND else 0.19)
        r, col = divmod(j, cols)
        x = x0 + col * (cw + 30)
        y = y0 + r * (c.height + (40 if LAND else 34))
        e = out_back(p)
        put(f, c, x, y + (1 - e) * 80, clamp(p * 2), 0.85 + 0.15 * e)


PRIVATE = ["No account", "No ads, no tracking", "HTTPS only", "Secrets encrypted on the phone"]


def s_private(f, t):
    p = out_back(prog(t, 0.1, 0.6))
    big = text_sprite("**Private** by design", 104 if LAND else 96, max_w=1600 if LAND else 980)
    put(f, big, W // 2, 260 if LAND else 520, clamp(p * 2), 0.7 + 0.3 * p)
    sub = text_sprite("Your SMS go only to the webhooks you add", 42, "regular", B.MUTED, max_w=1500 if LAND else 940)
    q = out_cubic(prog(t, 0.5, 0.5))
    put(f, sub, W // 2, (380 if LAND else 690) + (1 - q) * 30, q)
    for j, lab in enumerate(PRIVATE):
        r = prog(t, 0.8 + j * 0.15, 0.45)
        if r <= 0: continue
        line = Image.new("RGBA", (800, 84), (0, 0, 0, 0))
        line.alpha_composite(draw_icon("check", 64), (0, 10))
        ImageDraw.Draw(line).text((90, 14), lab, font=B.font(40), fill=B.WHITE)
        y = (500 if LAND else 860) + j * (90 if LAND else 110)
        put(f, line, W // 2 + 40 + (1 - out_cubic(r)) * -80, y, out_cubic(r))


def s_end(f, t):
    p = out_back(prog(t, 0.1, 0.7))
    cy = 360 if LAND else 720
    put(f, icon(260 if LAND else 340), W // 2, cy, clamp(p * 2), max(0.01, p))
    q = out_cubic(prog(t, 0.5, 0.6))
    put(f, text_sprite("SMS Webhook", 96 if LAND else 92), W // 2, cy + (220 if LAND else 300) + (1 - q) * 40, q)
    r = out_cubic(prog(t, 0.9, 0.6))
    c = chip("smswebhook.bestcallerapp.com", 40, fill=B.YELLOW + (255,), fg=B.NAVY, outline=None)
    put(f, c, W // 2, cy + (340 if LAND else 440), r, 0.9 + 0.1 * r)
    s = out_cubic(prog(t, 1.3, 0.6))
    put(f, text_sprite("Free · No ads · Android 8.0+", 38, "regular", B.MUTED), W // 2, cy + (450 if LAND else 560), s)


SCENES = [(3.0, s_intro), (4.6, s_flow), (3.6, s_webhooks), (4.4, s_filters), (4.2, s_payload), (4.0, s_extract),
          (3.6, s_tracked), (4.2, s_reliable), (3.8, s_private), (3.8, s_end)]
XF = 0.35


def particles(f, t):
    layer = Image.new("RGBA", f.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    for i in range(18):
        x = (i * 397 + t * (12 + i % 5 * 6)) % (W + 200) - 100
        y = (i * 263 % H) + math.sin(t * 0.7 + i) * 30
        r = 4 + i % 4 * 3
        d.ellipse((x - r, y - r, x + r, y + r), fill=(255, 255, 255, 26 + i % 3 * 12))
    f.alpha_composite(layer)


def render(T):
    f = background().copy()
    particles(f, T)
    start = 0.0
    for dur, fn in SCENES:
        if start - XF <= T < start + dur + XF:
            local = T - start
            a = clamp((local + XF) / XF) * clamp((start + dur + XF - T) / XF)
            layer = Image.new("RGBA", f.size, (0, 0, 0, 0))
            fn(layer, max(0.0, local))
            f.alpha_composite(fade(layer, a))
        start += dur
    return f.convert("RGB")


def total(): return sum(d for d, _ in SCENES)


def scene_starts():
    out, s = [], 0.0
    for d, fn in SCENES:
        out.append((fn.__name__, s)); s += d
    return out


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    if STILL is not None:
        p = os.path.join(OUT, f"_still_{MODE}_{STILL:.1f}.png")
        render(STILL).save(p); print(p); sys.exit()
    name = "_silent_teaser-vertical-1080x1920.mp4" if not LAND else "_silent_promo-landscape-1920x1080.mp4"
    path = os.path.join(OUT, name)
    n = int(total() * FPS)
    ff = subprocess.Popen(["ffmpeg", "-y", "-loglevel", "error", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W}x{H}",
                           "-r", str(FPS), "-i", "-", "-c:v", "libx264", "-preset", "slow", "-crf", "18", "-pix_fmt",
                           "yuv420p", "-movflags", "+faststart", path], stdin=subprocess.PIPE)
    for i in range(n):
        ff.stdin.write(render(i / FPS).tobytes())
        if i % 150 == 0: print(f"{i}/{n}", flush=True)
    ff.stdin.close(); ff.wait()
    print(path, f"{total():.1f}s")
