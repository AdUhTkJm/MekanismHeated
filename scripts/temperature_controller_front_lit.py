#!/usr/bin/env python3
"""Bake the fully lit front window into a copy of the Temperature Controller front texture.

The block's front texture (``front.png``) has to keep its window pixels pure white: the in-world strip
is drawn by tinting exactly those pixels, one flat quad per row, in
``TileEntityTemperatureControllerRenderer``. An item model cannot run that renderer, so the item model
points its ``"front"`` texture at the picture this script writes instead — the same texture with the
window's 16 rows filled with the renderer's own colour ramp, i.e. the window at display level 16.

Because the ramp in the item texture is only ever a picture, the two files have to be regenerated
whenever the ramp below or the window in ``front.png`` changes. Run from the repository root:

    python scripts/temperature_controller_front_lit.py
"""

from __future__ import annotations

from PIL import Image

PREFIX = "src/main/resources/assets/mekanismheated/textures/block/temperature_controller"
SRC = f"{PREFIX}/front.png"
DST = f"{PREFIX}/front_lit.png"

# The window in front.png, in texture pixels: the six x = 5..10 columns, for the full height of the image.
WINDOW_MIN_X = 5
WINDOW_MAX_X = 10  # inclusive
# The block texture also has to be exactly this many pixels tall for one pixel to be one row.
DISPLAY_ROWS = 16

# Keep in sync with TileEntityTemperatureControllerRenderer.DISPLAY_ROWS and .ROW_COLORS, bottom row first:
# green at the bottom of the window through to red at the top, in HSV colour space.
ROW_COLORS = [
    (0x00, 0xFF, 0x00),  #  0: 120
    (0x22, 0xFF, 0x00),  #  1: 112
    (0x44, 0xFF, 0x00),  #  2: 104
    (0x66, 0xFF, 0x00),  #  3:  96
    (0x88, 0xFF, 0x00),  #  4:  88
    (0xAA, 0xFF, 0x00),  #  5:  80
    (0xCC, 0xFF, 0x00),  #  6:  72
    (0xEE, 0xFF, 0x00),  #  7:  64
    (0xFF, 0xEE, 0x00),  #  8:  56
    (0xFF, 0xCC, 0x00),  #  9:  48
    (0xFF, 0xAA, 0x00),  # 10:  40
    (0xFF, 0x88, 0x00),  # 11:  32
    (0xFF, 0x66, 0x00),  # 12:  24
    (0xFF, 0x44, 0x00),  # 13:  16
    (0xFF, 0x22, 0x00),  # 14:   8
    (0xFF, 0x00, 0x00),  # 15:   0
]

WHITE = (255, 255, 255, 255)


def check_source(img: Image.Image) -> None:
    """Fail loudly rather than bake a ramp over pixels the window no longer owns."""
    if img.height != DISPLAY_ROWS or img.width < WINDOW_MAX_X + 1:
        raise SystemExit(
            f"{SRC} is {img.width}x{img.height}, expected at least "
            f"{WINDOW_MAX_X + 1}x{DISPLAY_ROWS}: the window is mapped one pixel per row.")
    px = img.load()
    painted = [px[x, y] for y in range(DISPLAY_ROWS) for x in range(WINDOW_MIN_X, WINDOW_MAX_X + 1)]
    if any(pixel != WHITE for pixel in painted):
        raise SystemExit(
            f"the window of {SRC} is no longer pure white, but the in-world strip tints those pixels; "
            "re-check the window bounds above before baking a ramp onto them.")


def main() -> None:
    img = Image.open(SRC).convert("RGBA")
    check_source(img)
    px = img.load()
    # Texture rows run top down, block rows bottom up: the last image row is the bottom, green end of the ramp.
    for y in range(DISPLAY_ROWS):
        color = (*ROW_COLORS[DISPLAY_ROWS - 1 - y], 255)
        for x in range(WINDOW_MIN_X, WINDOW_MAX_X + 1):
            px[x, y] = color
    img.save(DST, "PNG")
    print(f"wrote {DST}")


if __name__ == "__main__":
    main()
