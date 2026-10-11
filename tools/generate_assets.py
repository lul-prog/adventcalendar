#!/usr/bin/env python3
"""
Generates the binary assets of the Advent Calendar mod:

  * assets/adventcalendar/textures/gui/calendar.png  - GUI atlas (door states + icons)
  * assets/adventcalendar/textures/item/advent_calendar.png
  * ../adventcalendar.png                            - mod logo (256x256)
  * assets/adventcalendar/sounds/{lock,unlock,claim,notify}.ogg - custom SoundEvents

The script is deterministic, so the assets can always be regenerated:
    python3 tools/generate_assets.py
"""
import math
import os

import numpy as np
import soundfile as sf
from PIL import Image, ImageDraw, ImageFilter, ImageFont

RES = "/home/user/adventcalendar/src/main/resources"
ASSETS = f"{RES}/assets/adventcalendar"
GUI_DIR = f"{ASSETS}/textures/gui"
ITEM_DIR = f"{ASSETS}/textures/item"
SOUND_DIR = f"{ASSETS}/sounds"
FONT_BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
FONT_REGULAR = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"

TRANSPARENT = (0, 0, 0, 0)


def canvas(w, h):
    return Image.new("RGBA", (w, h), TRANSPARENT)


# --------------------------------------------------------------------------- GUI atlas

def draw_cell(draw, x, body, body_top, border, inner):
    draw.rounded_rectangle([x, 0, x + 23, 23], 4, fill=body, outline=border)
    # soft top light
    for i in range(19):
        alpha = int(70 * (1 - i / 19.0))
        draw.line([x + 2, 2 + i, x + 21, 2 + i], fill=(255, 255, 255, alpha))
    draw.rounded_rectangle([x + 2, 2, x + 21, 21], 3, fill=None, outline=inner)
    # little ribbon on top
    draw.line([x + 4, 4, x + 19, 4], fill=body_top)


def icon_padlock(draw, x, y, colour=(214, 224, 234, 255), dark=(96, 108, 124, 255)):
    draw.rounded_rectangle([x + 3, y + 7, x + 12, y + 14], 2, fill=colour, outline=dark)
    draw.arc([x + 4, y + 1, x + 11, y + 12], 180, 360, fill=colour, width=2)
    draw.line([x + 7, y + 10, x + 7, y + 12], fill=dark)
    draw.point((x + 8, y + 10), fill=dark)
    draw.point((x + 8, y + 12), fill=dark)


def icon_gift(draw, x, y):
    draw.rectangle([x + 2, y + 6, x + 13, y + 15], fill=(186, 46, 44, 255), outline=(122, 24, 24, 255))
    draw.rectangle([x + 2, y + 3, x + 13, y + 7], fill=(214, 72, 66, 255), outline=(122, 24, 24, 255))
    draw.line([x + 7, y + 3, x + 7, y + 15], fill=(244, 198, 78, 255))
    draw.line([x + 8, y + 3, x + 8, y + 15], fill=(244, 198, 78, 255))
    draw.arc([x + 2, y, x + 7, y + 5], 200, 340, fill=(244, 198, 78, 255))
    draw.arc([x + 8, y, x + 13, y + 5], 200, 340, fill=(244, 198, 78, 255))


def icon_check(draw, x, y, colour=(124, 224, 152, 255)):
    for i in range(5):
        draw.point((x + 2 + i, y + 7 + i), fill=colour)
    for i in range(8):
        draw.point((x + 6 + i, y + 11 - i), fill=colour)
    for i in range(4):
        draw.point((x + 3 + i, y + 8 + i), fill=colour)
        draw.point((x + 7 + i, y + 12 - i), fill=colour)


def icon_snowflake(draw, x, y, colour=(236, 246, 255, 255)):
    cx, cy = x + 7.5, y + 7.5
    for angle in range(0, 180, 30):
        rad = math.radians(angle)
        dx, dy = math.cos(rad) * 6, math.sin(rad) * 6
        draw.line([cx - dx, cy - dy, cx + dx, cy + dy], fill=colour)
    for angle in range(0, 360, 60):
        rad = math.radians(angle)
        bx, by = cx + math.cos(rad) * 4, cy + math.sin(rad) * 4
        for side in (-28, 28):
            branch = math.radians(angle + 180 + side)
            draw.line([bx, by, bx + math.cos(branch) * 2.6, by + math.sin(branch) * 2.6], fill=colour)


def icon_star(draw, x, y):
    points = []
    for i in range(10):
        angle = math.radians(-90 + i * 36)
        radius = 6.6 if i % 2 == 0 else 2.7
        points.append((x + 7.5 + math.cos(angle) * radius, y + 7.5 + math.sin(angle) * radius))
    draw.polygon(points, fill=(246, 208, 92, 255), outline=(184, 132, 36, 255))


def icon_bell(draw, x, y):
    draw.pieslice([x + 3, y + 2, x + 12, y + 13], 180, 360, fill=(236, 190, 82, 255))
    draw.rectangle([x + 3, y + 7, x + 12, y + 12], fill=(236, 190, 82, 255))
    draw.ellipse([x + 6, y + 12, x + 9, y + 14], fill=(198, 148, 48, 255))
    draw.point((x + 7, y + 1), fill=(255, 244, 208, 255))
    draw.point((x + 8, y + 1), fill=(255, 244, 208, 255))


def icon_mug(draw, x, y):
    draw.rounded_rectangle([x + 2, y + 5, x + 11, y + 13], 1, fill=(206, 118, 76, 255),
                           outline=(120, 60, 36, 255))
    draw.arc([x + 10, y + 6, x + 15, y + 12], 270, 90, fill=(206, 118, 76, 255))
    draw.line([x + 3, y + 4, x + 10, y + 4], fill=(120, 60, 36, 255))
    for i in range(3):
        draw.point((x + 5 + i, y + 1 - (i % 2)), fill=(240, 240, 240, 180))


def icon_tree(draw, x, y):
    draw.polygon([(x + 7.5, y + 1), (x + 2, y + 7), (x + 13, y + 7)], fill=(46, 128, 68, 255),
                 outline=(24, 82, 44, 255))
    draw.polygon([(x + 7.5, y + 5), (x + 1, y + 12), (x + 14, y + 12)], fill=(56, 150, 80, 255),
                 outline=(24, 82, 44, 255))
    draw.rectangle([x + 6, y + 12, x + 9, y + 15], fill=(120, 74, 40, 255))
    draw.point((x + 5, y + 9), fill=(246, 208, 92, 255))
    draw.point((x + 10, y + 6), fill=(246, 208, 92, 255))


def build_gui_atlas():
    atlas = canvas(256, 256)
    draw = ImageDraw.Draw(atlas)
    # door backgrounds (24x24 each, row 0)
    draw_cell(draw, 0, (28, 36, 50, 255), (44, 60, 84, 255), (60, 76, 98, 255), (46, 60, 82, 255))
    draw_cell(draw, 24, (108, 32, 36, 255), (150, 52, 48, 255), (226, 182, 92, 255), (168, 58, 52, 255))
    draw_cell(draw, 48, (26, 66, 46, 255), (44, 108, 70, 255), (118, 196, 140, 255), (40, 96, 62, 255))
    # hover outline
    draw.rounded_rectangle([72, 0, 95, 23], 4, outline=(255, 255, 255, 150), width=2)
    # today outline
    draw.rounded_rectangle([96, 0, 119, 23], 4, outline=(244, 200, 88, 240), width=2)
    for px, py in ((99, 3), (116, 3), (99, 20), (116, 20)):
        draw.point((px, py), fill=(255, 246, 200, 255))
    # icons (16x16 each, row 1)
    icon_padlock(draw, 0, 24)
    icon_gift(draw, 16, 24)
    icon_check(draw, 32, 24)
    icon_snowflake(draw, 48, 24)
    icon_star(draw, 64, 24)
    icon_bell(draw, 80, 24)
    icon_mug(draw, 96, 24)
    icon_tree(draw, 112, 24)
    # decorative divider (row 3, 128x8) used by the header
    for x in range(128):
        shade = 120 + int(80 * math.sin(x / 8.0))
        draw.point((x + 0, 48), fill=(240, 200, 90, shade))
        draw.point((x + 0, 49), fill=(255, 236, 180, shade))
    atlas.save(f"{GUI_DIR}/calendar.png")
    return f"{GUI_DIR}/calendar.png"


# --------------------------------------------------------------------------- item textures

def draw_digit(draw, x, y, digit, colour):
    patterns = {
        2: ["01110", "10001", "00001", "00110", "01000", "10000", "11111"],
        4: ["00010", "00110", "01010", "10010", "11111", "00010", "00010"],
    }
    for row, line in enumerate(patterns[digit]):
        for col, bit in enumerate(line):
            if bit == "1":
                draw.point((x + col, y + row), fill=colour)


def build_calendar_item():
    image = canvas(16, 16)
    draw = ImageDraw.Draw(image)
    draw.rounded_rectangle([1, 2, 14, 14], 2, fill=(30, 78, 50, 255), outline=(206, 166, 74, 255))
    draw.rectangle([2, 2, 13, 5], fill=(178, 42, 40, 255))
    draw.line([2, 5, 13, 5], fill=(206, 166, 74, 255))
    image = image.filter(ImageFilter.SMOOTH)
    draw = ImageDraw.Draw(image)
    draw_digit(draw, 3, 8, 2, (250, 246, 236, 255))
    draw_digit(draw, 9, 8, 4, (250, 246, 236, 255))
    image = image.filter(ImageFilter.SHARPEN)
    draw = ImageDraw.Draw(image)
    draw.point((6, 1), fill=(206, 166, 74, 255))
    draw.point((9, 1), fill=(206, 166, 74, 255))
    image.save(f"{ITEM_DIR}/advent_calendar.png")
    return f"{ITEM_DIR}/advent_calendar.png"


# --------------------------------------------------------------------------- logo

def build_logo():
    size = 256
    logo = Image.new("RGBA", (size, size), (18, 26, 38, 255))
    draw = ImageDraw.Draw(logo)
    # night sky gradient
    for y in range(size):
        shade = 18 + int(26 * (y / size))
        draw.line([0, y, size, y], fill=(shade, shade + 8, shade + 18, 255))
    rng = np.random.default_rng(24)
    for _ in range(90):
        x, y = int(rng.integers(0, size)), int(rng.integers(0, size // 2))
        r = int(rng.integers(1, 3))
        alpha = int(rng.integers(120, 255))
        draw.ellipse([x, y, x + r, y + r], fill=(255, 255, 255, alpha))
    # snowflakes
    for x, y, scale, alpha in ((36, 40, 1.0, 200), (214, 60, 0.8, 170), (52, 200, 0.7, 150),
                               (206, 196, 0.9, 160)):
        width = max(1, int(2 * scale))
        length = 10 * scale
        for angle in range(0, 180, 30):
            rad = math.radians(angle)
            dx, dy = math.cos(rad) * length, math.sin(rad) * length
            draw.line([x - dx, y - dy, x + dx, y + dy], fill=(235, 245, 255, alpha), width=width)
    # calendar body
    draw.rounded_rectangle([44, 62, 212, 196], 16, fill=(226, 62, 56, 255), outline=(248, 214, 120, 255),
                           width=4)
    draw.rounded_rectangle([52, 92, 204, 190], 10, fill=(248, 246, 240, 255))
    draw.rounded_rectangle([44, 62, 212, 96], 16, fill=(150, 34, 34, 255))
    draw.rectangle([44, 84, 212, 96], fill=(150, 34, 34, 255))
    # clips
    draw.rounded_rectangle([70, 48, 84, 76], 5, fill=(206, 166, 74, 255))
    draw.rounded_rectangle([172, 48, 186, 76], 5, fill=(206, 166, 74, 255))
    # green tree inside the calendar page
    draw.polygon([(128, 104), (98, 148), (158, 148)], fill=(46, 128, 68, 255),
                 outline=(24, 82, 44, 255))
    draw.polygon([(128, 132), (92, 178), (164, 178)], fill=(56, 150, 80, 255),
                 outline=(24, 82, 44, 255))
    draw.rectangle([122, 176, 134, 190], fill=(120, 74, 40, 255))
    for x, y in ((128, 100), (104, 132), (152, 132), (110, 158), (146, 158)):
        draw.ellipse([x - 4, y - 4, x + 4, y + 4], fill=(246, 208, 92, 255),
                     outline=(184, 132, 36, 255))
    try:
        title = ImageFont.truetype(FONT_BOLD, 23)
        subtitle = ImageFont.truetype(FONT_REGULAR, 14)
    except OSError:
        title = subtitle = ImageFont.load_default()
    draw.text((128, 216), "ADVENT CALENDAR", font=title, fill=(250, 232, 180, 255), anchor="mm")
    draw.text((128, 240), "24 days of gifts", font=subtitle, fill=(200, 212, 226, 255), anchor="mm")
    logo.save(f"{RES}/adventcalendar.png")
    return f"{RES}/adventcalendar.png"


# --------------------------------------------------------------------------- sounds

SR = 44100


def envelope(n, attack=0.004, decay=0.35, sustain_until=0.0):
    t = np.arange(n) / SR
    attack_curve = np.clip(t / max(attack, 1e-4), 0.0, 1.0)
    decay_curve = np.exp(-t / max(decay, 1e-4))
    if sustain_until > 0:
        decay_curve = np.where(t < sustain_until, 1.0, decay_curve)
    return attack_curve * decay_curve


def tone(freq, duration, decay=0.35, attack=0.004, partials=((1.0, 1.0), (2.0, 0.34), (3.0, 0.14),
                                                              (4.2, 0.07)), vibrato=0.0,
         vibrato_rate=5.5, detune=0.0):
    n = int(SR * duration)
    t = np.arange(n) / SR
    f = freq * (1.0 + detune)
    if vibrato:
        f = f * (1.0 + vibrato * np.sin(2 * np.pi * vibrato_rate * t))
    phase = 2 * np.pi * np.cumsum(f) / SR
    signal = np.zeros(n)
    for multiplier, amplitude in partials:
        signal += amplitude * np.sin(phase * multiplier)
    peak = np.max(np.abs(signal)) + 1e-9
    return signal / peak * envelope(n, attack, decay)


def noise(duration, decay=0.12, smoothing=40, seed=7):
    n = int(SR * duration)
    rng = np.random.default_rng(seed)
    raw = rng.normal(0.0, 1.0, n)
    kernel = np.ones(smoothing) / smoothing
    filtered = np.convolve(raw, kernel, mode="same")
    filtered /= np.max(np.abs(filtered)) + 1e-9
    return filtered * envelope(n, 0.001, decay)


def mix(*layers):
    length = max(len(layer) for layer in layers)
    out = np.zeros(length)
    for layer in layers:
        out[:len(layer)] += layer
    peak = np.max(np.abs(out)) + 1e-9
    return out / peak * 0.92


def fade(signal, seconds=0.02):
    n = max(1, int(SR * seconds))
    if len(signal) <= 2 * n:
        return signal
    ramp = np.linspace(0.0, 1.0, n)
    signal[:n] *= ramp
    signal[-n:] *= ramp[::-1]
    return signal


def write_sound(name, signal):
    signal = fade(signal)
    signal = np.clip(signal, -1.0, 1.0).astype(np.float32)
    path = f"{SOUND_DIR}/{name}.ogg"
    sf.write(path, signal, SR, format="OGG", subtype="VORBIS")
    return path, len(signal) / SR


def sound_lock():
    """Denied: two short descending low blips with a wooden knock."""
    return mix(
        tone(300, 0.18, decay=0.06, attack=0.002, partials=((1.0, 1.0), (2.0, 0.5), (3.0, 0.25))),
        np.concatenate([np.zeros(int(SR * 0.09)),
                        tone(220, 0.20, decay=0.07, attack=0.002,
                             partials=((1.0, 1.0), (2.0, 0.45), (3.0, 0.2)))]),
        noise(0.16, decay=0.05) * 0.35)


def sound_unlock():
    """A door opening: soft page turn plus a rising three note chime."""
    return mix(
        noise(0.30, decay=0.12, smoothing=90, seed=11) * 0.30,
        np.concatenate([np.zeros(int(SR * 0.02)), tone(523.25, 0.45, decay=0.30)]),
        np.concatenate([np.zeros(int(SR * 0.10)), tone(659.25, 0.45, decay=0.30)]),
        np.concatenate([np.zeros(int(SR * 0.18)), tone(783.99, 0.60, decay=0.42)]),
        np.concatenate([np.zeros(int(SR * 0.20)), noise(0.45, decay=0.20, smoothing=14, seed=3) * 0.18]))


def sound_claim():
    """Festive arpeggio with bell partials and a shower of sparkles."""
    notes = [(523.25, 0.00), (659.25, 0.09), (783.99, 0.18), (1046.50, 0.27)]
    layers = [tone(130.81, 0.9, decay=0.5, partials=((1.0, 1.0), (2.0, 0.2))) * 0.5]
    for freq, delay in notes:
        layers.append(np.concatenate([np.zeros(int(SR * delay)),
                                      tone(freq, 0.95, decay=0.55,
                                           partials=((1.0, 1.0), (2.01, 0.42), (3.01, 0.2),
                                                     (4.8, 0.1)))]))
    sparkle = np.zeros(int(SR * 1.1))
    rng = np.random.default_rng(5)
    for _ in range(14):
        start = int(rng.integers(0, int(SR * 0.9)))
        freq = float(rng.uniform(1600, 3200))
        note = tone(freq, 0.18, decay=0.05, attack=0.001, partials=((1.0, 1.0),))
        sparkle[start:start + len(note)] += note * 0.22
    layers.append(sparkle)
    return mix(*layers)


def sound_notify():
    """Soft two note ping used for the chat notification."""
    return mix(
        tone(783.99, 0.5, decay=0.22, attack=0.006,
             partials=((1.0, 1.0), (2.0, 0.22), (3.0, 0.08)), vibrato=0.004),
        np.concatenate([np.zeros(int(SR * 0.11)),
                        tone(1174.66, 0.45, decay=0.20, attack=0.006,
                             partials=((1.0, 1.0), (2.0, 0.18)))]))


def main():
    for directory in (GUI_DIR, ITEM_DIR, SOUND_DIR):
        os.makedirs(directory, exist_ok=True)
    print("wrote", build_gui_atlas())
    print("wrote", build_calendar_item())
    print("wrote", build_logo())
    for name, generator in (("lock", sound_lock), ("unlock", sound_unlock),
                            ("claim", sound_claim), ("notify", sound_notify)):
        path, seconds = write_sound(name, generator())
        print(f"wrote {path} ({seconds:.2f}s)")


if __name__ == "__main__":
    main()
