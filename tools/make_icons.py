#!/usr/bin/env python3
"""
Maui launcher icon generator — pure Python, zero dependencies.

The repository stores the icon as a vector (the adaptive icon XML) and this
script regenerates the PNG fallbacks that older Android versions (API 24-25)
need. CI runs it before every build, so the binary assets never have to be
committed by hand.

Usage:  python3 tools/make_icons.py [res_dir]
"""

import math
import os
import struct
import sys
import zlib

DENSITIES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}

C1 = (46, 123, 255)     # blue
C2 = (138, 92, 255)     # purple
C3 = (34, 211, 238)     # cyan


# --------------------------------------------------------------------------- PNG
def write_png(path, width, height, pixels):
    """pixels: list of rows, each row a list of (r, g, b, a) tuples."""
    raw = bytearray()
    for row in pixels:
        raw.append(0)  # filter type 0
        for r, g, b, a in row:
            raw += bytes((r, g, b, a))

    def chunk(tag, data):
        out = struct.pack(">I", len(data)) + tag + data
        return out + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    ihdr = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)  # RGBA8
    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", ihdr)
    png += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
    png += chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(png)


# ------------------------------------------------------------------- geometry
def dist_to_segment(px, py, ax, ay, bx, by):
    dx, dy = bx - ax, by - ay
    length_sq = dx * dx + dy * dy
    if length_sq == 0:
        return math.hypot(px - ax, py - ay)
    t = max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / length_sq))
    return math.hypot(px - (ax + t * dx), py - (ay + t * dy))


def inside_m(px, py, size):
    """A geometric 'M' built from four capsules."""
    inset = size * 0.28
    x0, x1 = inset, size - inset
    y0, y1 = inset, size - inset
    xm = (x0 + x1) / 2.0
    ym = y1 * 0.62
    stroke = size * 0.085
    segments = [
        (x0, y0, x0, y1),
        (x1, y0, x1, y1),
        (x0, y0, xm, ym),
        (xm, ym, x1, y0),
    ]
    for ax, ay, bx, by in segments:
        if dist_to_segment(px, py, ax, ay, bx, by) <= stroke:
            return True
    return False


def rounded_rect_mask(px, py, size, radius):
    r = radius
    cx = min(max(px, r), size - r)
    cy = min(max(py, r), size - r)
    return math.hypot(px - cx, py - cy) <= r


def circle_mask(px, py, size):
    c = size / 2.0
    return math.hypot(px - c, py - c) <= c


def gradient_color(t):
    """C1 -> C2 with a cyan highlight in the upper-left."""
    if t < 0.45:
        u = t / 0.45
        a, b = C3, C2
    else:
        u = (t - 0.45) / 0.55
        a, b = C2, C1
    return tuple(int(a[i] + (b[i] - a[i]) * u) for i in range(3))


def render_tile(size, shape="rounded", draw_glyph=True, scale=1.0, padding=0.0):
    """Returns rows of RGBA pixels for one icon layer."""
    rows = []
    for y in range(size):
        row = []
        for x in range(size):
            if shape == "rounded":
                mask = rounded_rect_mask(x + 0.5, y + 0.5, size, size * 0.22)
            elif shape == "circle":
                mask = circle_mask(x + 0.5, y + 0.5, size)
            else:
                mask = True
            if not mask:
                row.append((0, 0, 0, 0))
                continue
            t = (x + y) / (2.0 * size)
            r, g, b = gradient_color(t)
            if draw_glyph:
                gx = (x + 0.5 - padding) / scale
                gy = (y + 0.5 - padding) / scale
                if inside_m(gx, gy, size - 2 * padding):
                    r = g = b = 255
            row.append((r, g, b, 255))
        rows.append(row)
    return rows


def render_foreground(size):
    """Adaptive-icon foreground: glyph only, centred in the safe zone."""
    rows = []
    glyph = size * 0.52
    pad = (size - glyph) / 2.0
    for y in range(size):
        row = []
        for x in range(size):
            gx = (x + 0.5 - pad) / glyph * glyph
            gy = (y + 0.5 - pad) / glyph * glyph
            if inside_m(gx, gy, glyph):
                t = (x + y) / (2.0 * size)
                r, g, b = gradient_color(t)
                row.append((r, g, b, 255))
            else:
                row.append((0, 0, 0, 0))
        rows.append(row)
    return rows


def main():
    res_dir = sys.argv[1] if len(sys.argv) > 1 else os.path.join(
        os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "app", "src", "main", "res"
    )
    for density, size in DENSITIES.items():
        out = os.path.join(res_dir, "mipmap-" + density)
        os.makedirs(out, exist_ok=True)

        write_png(os.path.join(out, "ic_launcher.png"), size, size, render_tile(size, "rounded"))
        write_png(os.path.join(out, "ic_launcher_round.png"), size, size, render_tile(size, "circle"))

        # Adaptive foreground is drawn on a 108dp canvas (1.5x the 72dp density)
        fg_size = int(size * 108 / 48)
        write_png(os.path.join(out, "ic_launcher_foreground.png"), fg_size, fg_size, render_foreground(fg_size))

        print("wrote", out, size, "px")
    print("Maui launcher icons generated in", res_dir)


if __name__ == "__main__":
    main()
