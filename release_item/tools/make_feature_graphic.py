"""Play feature graphic, 1024x500, 24-bit PNG (no alpha).   python3 make_feature_graphic.py v1.0.0"""
import os, sys
from PIL import Image, ImageDraw
import brand as B

VER = sys.argv[1] if len(sys.argv) > 1 else "v1.0.0"
RAW = os.path.join(B.KIT, "4_graphics", f"screenshots_{VER}")
W, H = 1024, 500


def render():
    img = B.background((W, H)).convert("RGBA")
    d = ImageDraw.Draw(img)
    # Right: two phones, the front one slightly tilted.
    back = B.phone(B.load_screen(os.path.join(RAW, "phone-04-webhooks.png")), 230).rotate(-8, expand=True, resample=Image.BICUBIC)
    front = B.phone(B.load_screen(os.path.join(RAW, "phone-01-home.png")), 250).rotate(6, expand=True, resample=Image.BICUBIC)
    for ph, x, y in ((back, 800, 70), (front, 610, 40)):
        sh, pad = B.shadow(ph, 16, 120)
        img.alpha_composite(sh, (x - pad, y - pad))
        img.alpha_composite(ph, (x, y))
    # Left: icon, name, tagline, chips.
    icon = Image.open(os.path.join(B.KIT, "4_graphics", "app-icon-512.png")).convert("RGBA").resize((118, 118), Image.LANCZOS)
    icon.putalpha(B.rounded_mask(icon.size, 28))
    sh, pad = B.shadow(icon, 10, 100, (0, 8))
    img.alpha_composite(sh, (56 - pad, 66 - pad)); img.alpha_composite(icon, (56, 66))
    d.text((56, 200), "SMS Webhook", font=B.font(52), fill=B.WHITE)
    d.text((56, 266), "Forward SMS to your", font=B.font(30, "regular"), fill=B.MUTED)
    d.text((56, 304), "webhook or API", font=B.font(30, "regular"), fill=B.MUTED)
    x = 56
    chips = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    cd = ImageDraw.Draw(chips)
    for i, label in enumerate(("Filters", "JSON", "Auto-retry")):
        f = B.font(22)
        tw = d.textlength(label, font=f)
        cd.rounded_rectangle((x, 372, x + tw + 36, 414), 21, fill=B.YELLOW if i == 0 else (255, 255, 255, 46),
                             outline=None if i == 0 else (255, 255, 255, 120), width=2)
        cd.text((x + 18, 378), label, font=f, fill=B.NAVY if i == 0 else B.WHITE)
        x += tw + 50
    img.alpha_composite(chips)
    return img.convert("RGB")


if __name__ == "__main__":
    out = os.path.join(B.KIT, "4_graphics", "feature-graphic-1024x500.png")
    render().save(out, optimize=True)
    print(out)
