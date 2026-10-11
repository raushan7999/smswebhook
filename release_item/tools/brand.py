"""SMS Webhook — shared look for store graphics and the promo video: colours, fonts, phone mockup, captions."""
import os
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
FONTS = os.path.join(os.path.dirname(os.path.abspath(__file__)), "fonts")
KIT = os.path.join(ROOT, "release_item")
WEBSITE = os.path.join(ROOT, "website")

# From the app icon (#16A34A): deep green → brand green gradient, mint glow, yellow highlight.
NAVY = (11, 18, 32)
DEEP = (6, 78, 59)        # #064E3B
SKY = (22, 163, 74)       # #16A34A brand green (name kept for the shared drawing helpers)
SKY_LIGHT = (187, 247, 208)  # #BBF7D0
LIME = (74, 222, 128)
YELLOW = (250, 204, 21)
AMBER = (245, 158, 11)
GREEN = (22, 163, 74)
WHITE = (255, 255, 255)
MUTED = (203, 213, 225)


def font(size, weight="bold"):
    return ImageFont.truetype(os.path.join(FONTS, f"poppins_{weight}.ttf"), int(size))


# Phone screenshots are 1080x2340 (Galaxy A17). Crop off the status bar (100 px) and the navigation bar (135 px).
STATUS_H, NAV_H = 100, 135


def load_screen(path, crop=True):
    im = Image.open(path).convert("RGB")
    if crop:
        im = im.crop((0, STATUS_H, im.width, im.height - NAV_H))
    return im


def rounded_mask(size, r):
    m = Image.new("L", size, 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, size[0] - 1, size[1] - 1), r, fill=255)
    return m


def phone(screen, width):
    """Screenshot inside a slim dark phone body. Returns an RGBA image `width` wide."""
    bezel = max(8, int(width * 0.028))
    sw = width - 2 * bezel
    sh = int(screen.height * sw / screen.width)
    h = sh + 2 * bezel
    r_out = int(width * 0.11)
    body = Image.new("RGBA", (width, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(body)
    d.rounded_rectangle((0, 0, width - 1, h - 1), r_out, fill=(16, 20, 26, 255), outline=(70, 78, 90, 255), width=max(2, width // 260))
    scr = screen.resize((sw, sh), Image.LANCZOS)
    body.paste(scr, (bezel, bezel), rounded_mask((sw, sh), r_out - bezel))
    # Camera punch-hole, like the real device.
    cx, cy, cr = width // 2, bezel + int(width * 0.035), max(4, int(width * 0.012))
    d.ellipse((cx - cr, cy - cr, cx + cr, cy + cr), fill=(5, 6, 8, 255))
    return body


def shadow(img, blur, opacity=110, offset=(0, 18)):
    """A soft drop shadow canvas larger than img; returns (shadow_rgba, pad)."""
    pad = blur * 3
    sh = Image.new("RGBA", (img.width + 2 * pad, img.height + 2 * pad), (0, 0, 0, 0))
    a = img.split()[3].point(lambda v: opacity if v > 0 else 0)
    sh.paste((0, 0, 0, 255), (pad + offset[0], pad + offset[1]), a)
    return sh.filter(ImageFilter.GaussianBlur(blur)), pad


def background(size, t=0.0):
    """Green gradient with a soft glow, echoing the app icon. t (0..1) drifts the glow for video."""
    w, h = size
    bg = Image.new("RGB", size)
    top, bot = DEEP, SKY
    px = bg.load()
    # Vertical gradient (fast: draw lines).
    d = ImageDraw.Draw(bg)
    for y in range(h):
        k = y / max(1, h - 1)
        c = tuple(int(top[i] + (bot[i] - top[i]) * k) for i in range(3))
        d.line((0, y, w, y), fill=c)
    glow = Image.new("L", size, 0)
    gd = ImageDraw.Draw(glow)
    gx = int(w * (0.25 + 0.5 * t))
    gd.ellipse((gx - w // 2, -h // 6, gx + w // 2, h // 2), fill=90)
    glow = glow.filter(ImageFilter.GaussianBlur(w // 6))
    bg = Image.composite(Image.new("RGB", size, SKY_LIGHT), bg, glow)
    return bg


def text_center(draw, cx, y, s, f, fill):
    w = draw.textlength(s, font=f)
    draw.text((cx - w / 2, y), s, font=f, fill=fill)


def rich_lines(text, f, max_w, draw):
    """Word-wrap text where **word** marks highlighted words. Returns lines of words; a word is [(text, hl), ...]
    so punctuation right after a highlight ("**Compass**,") stays attached without a space."""
    words, hl, prev = [], False, " "
    for part in text.split("**"):
        for j, w in enumerate(part.split(" ")):
            if not w:
                continue
            # Same word as the previous segment only when nothing separates them ("**Compass**,").
            if j == 0 and words and not prev.endswith(" ") and not part.startswith(" "):
                words[-1] = words[-1] + [(w, hl)]
            else:
                words.append([(w, hl)])
        if part:
            prev = part
        hl = not hl
    plain = lambda ws: " ".join("".join(t for t, _ in w) for w in ws)
    lines, cur = [], []
    for w in words:
        if cur and draw.textlength(plain(cur + [w]), font=f) > max_w:
            lines.append(cur); cur = [w]
        else:
            cur.append(w)
    if cur:
        lines.append(cur)
    return lines


def draw_rich_center(draw, cx, y, text, f, max_w, fill=WHITE, hl=YELLOW, line_gap=1.12):
    for line in rich_lines(text, f, max_w, draw):
        full = " ".join("".join(t for t, _ in w) for w in line)
        x = cx - draw.textlength(full, font=f) / 2
        for i, w in enumerate(line):
            for t, h in w:
                draw.text((x, y), t, font=f, fill=hl if h else fill)
                x += draw.textlength(t, font=f)
            if i < len(line) - 1:
                x += draw.textlength(" ", font=f)
        y += int(f.size * line_gap)
    return y
