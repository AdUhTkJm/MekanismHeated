#!/usr/bin/env python3
"""Generate the phase-change blocks' textures.

Each block is a single flat colour, hinting at its phase-change temperature: a cold
blue for the low tier, white for the medium tier and a warm orange for the high tier.
No shading or detail is drawn, so the only variation the player sees comes from the
block's own shape and Minecraft's face lighting.

Run from the repository root:

    python scripts/phase_change_block_texture.py
"""

from __future__ import annotations

from pathlib import Path

from PIL import Image

REPO_ROOT = Path(__file__).resolve().parent.parent
DST_DIR = REPO_ROOT / "src/main/resources/assets/mekanismheated/textures/block"

SIZE = 16
# (tier name, RGB)
TIERS = (
    ("low", (0xA8, 0xC8, 0xE8)),
    ("medium", (0xE9, 0xE9, 0xE9)),
    ("high", (0xF0, 0xC0, 0x8A)),
)


def main() -> None:
    DST_DIR.mkdir(parents=True, exist_ok=True)
    for tier, rgb in TIERS:
        image = Image.new("RGBA", (SIZE, SIZE), (*rgb, 0xFF))
        path = DST_DIR / f"phase_change_block_{tier}.png"
        image.save(path, "PNG")
        print(f"wrote {path.relative_to(REPO_ROOT)} #{rgb[0]:02X}{rgb[1]:02X}{rgb[2]:02X}")


if __name__ == "__main__":
    main()
