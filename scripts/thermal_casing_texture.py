#!/usr/bin/env python3
"""Generate the thermal casing texture by recolouring Mekanism's steel casing.

The thermal casing is meant to read as an orange sibling of Mekanism's steel casing, so
rather than drawing a texture from scratch this bakes the steel casing's shading into an
orange ramp: every pixel's luminance is mapped onto a gradient from a dark brown shadow to
a warm orange highlight, keeping the bevels and rivets the original has.

Run from the repository root:

    python scripts/thermal_casing_texture.py
"""

from __future__ import annotations

from pathlib import Path

from PIL import Image

REPO_ROOT = Path(__file__).resolve().parent.parent
SRC = REPO_ROOT / "mek/Mekanism/src/main/resources/assets/mekanism/textures/block/steel_casing.png"
DST = REPO_ROOT / "src/main/resources/assets/mekanismheated/textures/block/thermal_casing.png"

# The two ends of the orange ramp. The darkest steel pixels become SHADOW and the lightest
# become HIGHLIGHT; everything in between is interpolated, which preserves the casing's contrast.
SHADOW = (0x52, 0x28, 0x0B)
HIGHLIGHT = (0xF7, 0xB0, 0x5E)

# Rec. 709 luminance weights, whose sum is one.
LUMA_WEIGHTS = (0.2126, 0.7152, 0.0722)


def luminance(pixel: tuple[int, int, int, int]) -> float:
    return sum(weight * channel for weight, channel in zip(LUMA_WEIGHTS, pixel[:3])) / 255


def recolour(pixel: tuple[int, int, int, int], lo: float, hi: float) -> tuple[int, int, int, int]:
    """Map one pixel's luminance onto the orange ramp, preserving its alpha."""
    r, g, b, a = pixel
    if a == 0:
        return 0, 0, 0, 0
    # Stretch the source's luminance range over the whole ramp so the darker end does not collapse
    # into a single flat brown when the casing has no fully black pixels.
    level = (luminance(pixel) - lo) / (hi - lo) if hi > lo else 0.0
    level = min(1.0, max(0.0, level))
    return tuple(round(shadow + (highlight - shadow) * level) for shadow, highlight in zip(SHADOW, HIGHLIGHT)) + (a,)


def main() -> None:
    source = Image.open(SRC).convert("RGBA")
    pixels = list(source.getdata())
    opaque = [luminance(pixel) for pixel in pixels if pixel[3] != 0]
    if not opaque:
        raise SystemExit(f"{SRC} is fully transparent; nothing to recolour")

    lo, hi = min(opaque), max(opaque)
    recoloured = Image.new("RGBA", source.size)
    recoloured.putdata([recolour(pixel, lo, hi) for pixel in pixels])

    DST.parent.mkdir(parents=True, exist_ok=True)
    recoloured.save(DST, "PNG")
    print(f"wrote {DST.relative_to(REPO_ROOT)} (luminance range {lo:.3f}-{hi:.3f})")


if __name__ == "__main__":
    main()
