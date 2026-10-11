"""Captioned Play Store screenshots (phone, 7-inch and 10-inch tablet) from raw phone screenshots.

    python3 release_item/tools/make_store_images.py v1.0.0

Reads 4_graphics/screenshots_<ver>/, writes 4_graphics/store/{phone,tablet-7in,tablet-10in}_<ver>/.
Edit SHOTS to change the order or the captions (** marks the yellow words).
"""
import os, sys
from PIL import Image, ImageDraw
import brand as B

VER = sys.argv[1] if len(sys.argv) > 1 else "v1.0.0"
RAW = os.path.join(B.KIT, "4_graphics", f"screenshots_{VER}")
OUT = os.path.join(B.KIT, "4_graphics", "store")

SHOTS = [
    ("phone-01-home", "Forward **SMS** to your webhooks", "Bank alerts, OTPs and more, sent to your server"),
    ("phone-04-webhooks", "**Multiple** webhooks", "Each with its own filters, payload and retries"),
    ("phone-06-filters", "Smart **filters**", "Sender, keywords, regex, SIM and time with AND / OR / NOT"),
    ("phone-07-payload", "Your **JSON**, your way", "Pick fields and names, or write a template"),
    ("phone-08-extraction", "Extract values with **regex**", "Amount, account or OTP becomes its own field"),
    ("phone-03-message-detail", "Every **delivery** tracked", "Status, attempts, HTTP code and response"),
    ("phone-09-logs", "**Retries** built in", "Works offline and delivers when you're back online"),
    ("phone-10-settings", "**Private** by design", "No account, no ads. Secrets encrypted on the phone"),
]

SIZES = {"phone": (1080, 1920), "tablet-7in": (1200, 1920), "tablet-10in": (1600, 2560)}


def compose(raw, title, sub, size):
    w, h = size
    img = B.background(size).convert("RGBA")
    d = ImageDraw.Draw(img)
    u = w / 1080
    y = B.draw_rich_center(d, w // 2, int(120 * u), title, B.font(78 * u), int(w * 0.86))
    y = B.draw_rich_center(d, w // 2, y + int(14 * u), sub, B.font(36 * u, "regular"), int(w * 0.80), fill=B.MUTED)
    pw = int(min(w * 0.74, 800 * u))
    ph = B.phone(B.load_screen(raw), pw)
    top = y + int(60 * u)
    sh, pad = B.shadow(ph, int(26 * u))
    img.alpha_composite(sh, (w // 2 - pw // 2 - pad, top - pad))
    img.alpha_composite(ph, (w // 2 - pw // 2, top))  # runs off the bottom edge on purpose
    return img.convert("RGB")


if __name__ == "__main__":
    for kind, size in SIZES.items():
        out = os.path.join(OUT, f"{kind}_{VER}")
        os.makedirs(out, exist_ok=True)
        for i, (name, title, sub) in enumerate(SHOTS, 1):
            compose(os.path.join(RAW, name + ".png"), title, sub, size).save(
                os.path.join(out, f"{kind}-{i}-{name.split('-', 2)[-1]}.png"), optimize=True)
        print(kind, len(SHOTS), "→", out)
