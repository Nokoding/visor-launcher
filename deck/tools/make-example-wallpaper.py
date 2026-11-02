"""Generates the example wallpaper pack in docs/examples/wallpaper-example (docs/wallpapers.md).
Usage: python3 make-example-wallpaper.py OUT_DIR
Needs Pillow. Six small PNG layers (under 300 KB together) and a wallpaper.json that uses every option type.
Everything is drawn from a fixed seed, so the same command makes the same files."""
import json, math, random, sys
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter

out = Path(sys.argv[1])
out.mkdir(parents=True, exist_ok=True)
W, H = 960, 540
rnd = random.Random(2027)


def lerp(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def sky(top, mid, bottom, name):
    # drawn small and scaled up, so the gradient stays smooth and the file stays tiny
    w, h = 320, 180
    im = Image.new("RGB", (w, h))
    px = im.load()
    for y in range(h):
        t = y / (h - 1)
        c = lerp(top, mid, t / 0.62) if t < 0.62 else lerp(mid, bottom, (t - 0.62) / 0.38)
        for x in range(w):
            px[x, y] = c
    im = im.resize((W, H), Image.BICUBIC)
    im.save(out / name, optimize=True)


def stars():
    im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    for _ in range(110):
        x, y = rnd.randrange(W), rnd.randrange(int(H * 0.5))
        a = rnd.randrange(90, 230)
        r = 1 if rnd.random() < 0.85 else 2
        d.ellipse((x - r, y - r, x + r, y + r), fill=(255, 255, 255, a))
    im.save(out / "stars.png", optimize=True)


def glow():
    # a white horizon glow. Visor tints it with the ACCENT option and fades it with GLOW.
    im = Image.new("RGBA", (W, H), (255, 255, 255, 0))
    px = im.load()
    cy = int(H * 0.66)
    for y in range(H):
        for x in range(0, W):
            dy = (y - cy) / (H * 0.30)
            dx = (x - W * 0.5) / (W * 0.65)
            a = max(0.0, 1.0 - math.sqrt(dx * dx * 0.55 + dy * dy)) ** 2
            px[x, y] = (255, 255, 255, round(a * 170))
    im.save(out / "glow.png", optimize=True)


def skyline(name, color, base, lo, hi, seed, windows, rim):
    r = random.Random(seed)
    im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    x = -20
    while x < W:
        bw = r.randrange(40, 110)
        bh = r.randrange(lo, hi)
        top = base - bh
        d.rectangle((x, top, x + bw, H), fill=color)
        if rim:
            d.line((x, top, x + bw, top), fill=rim, width=2)
        if r.random() < 0.3:
            d.line((x + bw // 2, top, x + bw // 2, top - r.randrange(14, 40)), fill=color, width=2)
        if windows:
            for wy in range(top + 14, base - 8, 16):
                for wx in range(x + 8, x + bw - 10, 14):
                    if r.random() < 0.22:
                        d.rectangle((wx, wy, wx + 6, wy + 4), fill=r.choice(windows))
        x += bw + r.randrange(2, 14)
    im.save(out / name, optimize=True)


sky((6, 4, 16), (28, 12, 56), (88, 30, 92), "sky_night.png")
sky((20, 14, 60), (150, 52, 120), (255, 150, 90), "sky_dusk.png")
stars()
glow()
skyline("far.png", (26, 14, 52, 255), 470, 90, 250, 11, [], (120, 70, 190, 160))
skyline("near.png", (10, 6, 22, 255), 540, 120, 300, 29, [(255, 46, 151, 255), (34, 228, 255, 255), (255, 201, 61, 255), (244, 238, 255, 230)], (34, 228, 255, 200))

pack = {
    "format": 1,
    "name": "Neon Skyline Example",
    "author": "Visor",
    "version": "1.0",
    "type": "layers",
    "options": [
        {"key": "mood", "label": "Mood", "type": "CHOICE", "choices": ["Night", "Dusk"], "default": "Night"},
        {"key": "glow", "label": "Horizon glow", "type": "FLOAT_RANGE", "min": 0, "max": 1, "step": 0.1, "default": 0.6},
        {"key": "stars", "label": "Stars", "type": "BOOL", "default": True},
        {"key": "accent", "label": "Glow color", "type": "COLOR", "default": "#FF2E97"},
        {"key": "depth", "label": "Depth", "type": "INT_RANGE", "min": 50, "max": 150, "step": 10, "default": 100, "unit": "%"},
    ],
    "layers": [
        {"file": "sky_night.png", "parallax": 0.05, "when": "mood=Night"},
        {"file": "sky_dusk.png", "parallax": 0.05, "when": "mood=Dusk"},
        {"file": "stars.png", "parallax": 0.10, "opacity": "@stars"},
        {"file": "glow.png", "parallax": 0.20, "opacity": "@glow", "tint": "@accent", "tintMix": 1.0},
        {"file": "far.png", "parallax": 0.45, "scale": 1.05},
        {"file": "near.png", "parallax": 1.10, "scale": 1.12},
    ],
}
(out / "wallpaper.json").write_text(json.dumps(pack, indent=2) + "\n")
total = sum(f.stat().st_size for f in out.iterdir())
print("wrote", out, total, "bytes in", len(list(out.iterdir())), "files")
