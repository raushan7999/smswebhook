"""YouTube thumbnail (1280x720) and vertical teaser poster (1080x1920).   python3 make_youtube_thumbnail.py v1.0.0"""
import os, sys
from PIL import Image, ImageDraw
import brand as B

VER = sys.argv[1] if len(sys.argv) > 1 else "v1.0.0"
RAW = os.path.join(B.KIT, "4_graphics", f"screenshots_{VER}")
OUT = os.path.join(B.KIT, f"6_promo_video_{VER}")


def thumbnail():
    W, H = 1280, 720
    img = B.background((W, H)).convert("RGBA")
    d = ImageDraw.Draw(img)
    ph = B.phone(B.load_screen(os.path.join(RAW, "phone-01-home.png")), 330).rotate(5, expand=True, resample=Image.BICUBIC)
    sh, pad = B.shadow(ph, 18, 130)
    img.alpha_composite(sh, (880 - pad, 60 - pad)); img.alpha_composite(ph, (880, 60))
    icon = Image.open(os.path.join(B.KIT, "4_graphics", "app-icon-512.png")).convert("RGBA").resize((150, 150), Image.LANCZOS)
    icon.putalpha(B.rounded_mask(icon.size, 34))
    img.alpha_composite(icon, (70, 70))
    y = B.draw_rich_center(d, 450, 250, "Forward **SMS** to your **webhook**", B.font(78), 800)
    B.draw_rich_center(d, 450, y + 10, "Filters · JSON · Regex · Auto-retry", B.font(38, "regular"), 800, fill=B.MUTED)
    return img.convert("RGB")


def poster():
    W, H = 1080, 1920
    img = B.background((W, H)).convert("RGBA")
    d = ImageDraw.Draw(img)
    icon = Image.open(os.path.join(B.KIT, "4_graphics", "app-icon-512.png")).convert("RGBA").resize((300, 300), Image.LANCZOS)
    icon.putalpha(B.rounded_mask(icon.size, 68))
    img.alpha_composite(icon, (W // 2 - 150, 170))
    y = B.draw_rich_center(d, W // 2, 520, "SMS Webhook", B.font(96), 960)
    y = B.draw_rich_center(d, W // 2, y + 10, "Forward SMS to your **webhook** or **API**", B.font(44, "regular"), 900, fill=B.MUTED)
    ph = B.phone(B.load_screen(os.path.join(RAW, "phone-01-home.png")), 640)
    sh, pad = B.shadow(ph, 26, 120)
    img.alpha_composite(sh, (W // 2 - 320 - pad, y + 70 - pad)); img.alpha_composite(ph, (W // 2 - 320, y + 70))
    return img.convert("RGB")


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    thumbnail().save(os.path.join(OUT, "youtube-thumbnail-1280x720.png"), optimize=True)
    poster().save(os.path.join(OUT, "teaser-poster-1080x1920.png"), optimize=True)
    print("thumbnail + poster →", OUT)
