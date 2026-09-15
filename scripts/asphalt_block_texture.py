#!/usr/bin/env python3
"""Bake the asphalt block texture out of vanilla's cobblestone texture.

Asphalt is meant to look like cobblestone that has been paved over: the same 16x16 cobble
pattern, darkened until it reads as a near-black road surface. Cobblestone is a vanilla texture,
so it is not in this repository; it is read out of the Minecraft client jar that NeoForm keeps
in the Gradle cache. Every channel is multiplied by a darkening factor and the result is written
to ``src/main/resources/assets/mekanismheated/textures/block/asphalt_block.png``, which
``models/block/asphalt_block.json`` draws through ``minecraft:block/cube_all``.

Run from the repository root:

    python scripts/asphalt_block_texture.py

Pass ``--factor`` to taste (0 is pure black, 1 is untouched cobblestone) and ``--client-jar`` or
set ``MINECRAFT_CLIENT_JAR`` if the jar lives somewhere else on your machine.
"""

from __future__ import annotations

import argparse
import glob
import io
import os
import sys
import zipfile
from pathlib import Path

from PIL import Image, ImageStat

REPO_ROOT = Path(__file__).resolve().parent.parent
DESTINATION = REPO_ROOT / "src/main/resources/assets/mekanismheated/textures/block/asphalt_block.png"

# Where the client jar with vanilla's textures may live. NeoForm extracts the client assets into
# its Gradle cache under a content hash, so the directory name is not stable across machines.
CLIENT_JAR_GLOBS = (
    "~/.gradle/caches/ng_execute/*/client-extra.jar",
    "~/.gradle/caches/neoformruntime/*/client-extra.jar",
)
CLIENT_JAR_ENV = "MINECRAFT_CLIENT_JAR"

SOURCE_TEXTURE = "assets/minecraft/textures/block/cobblestone.png"
TEXTURE_SIZE = 16

# Cobblestone averages around 128 per channel, so this lands the block at roughly 41 - dark
# enough to read as asphalt next to other stone blocks while still showing the cobble pattern.
DEFAULT_FACTOR = 0.32


def find_client_jar(explicit: str | None) -> Path:
    """Locate the Minecraft client jar, either from the given path/env var or the Gradle cache."""
    candidates: list[Path] = []
    requested = explicit or os.environ.get(CLIENT_JAR_ENV)
    if requested:
        candidates.append(Path(requested).expanduser())

    if not candidates:
        for pattern in CLIENT_JAR_GLOBS:
            candidates.extend(Path(match) for match in sorted(glob.glob(os.path.expanduser(pattern))))

    for candidate in candidates:
        if candidate.is_file():
            return candidate
    raise SystemExit(
        "Could not find the Minecraft client jar with vanilla's textures. "
        f"Pass it with --client-jar or set {CLIENT_JAR_ENV}.\nLooked at: "
        + ", ".join(str(candidate) for candidate in candidates)
    )


def load_cobblestone(client_jar: Path) -> Image.Image:
    """Read (and sanity check) the 16x16 cobblestone texture the asphalt is built from."""
    with zipfile.ZipFile(client_jar) as jar:
        try:
            data = jar.read(SOURCE_TEXTURE)
        except KeyError:
            raise SystemExit(f"{client_jar} does not contain {SOURCE_TEXTURE}") from None
    cobblestone = Image.open(io.BytesIO(data)).convert("RGBA")
    if cobblestone.size != (TEXTURE_SIZE, TEXTURE_SIZE):
        raise SystemExit(f"Expected a {TEXTURE_SIZE}x{TEXTURE_SIZE} texture, got {cobblestone.size}")
    return cobblestone


def darken(texture: Image.Image, factor: float) -> Image.Image:
    """Scale every colour channel by the factor, leaving alpha (and therefore the shape) alone."""
    red, green, blue, alpha = texture.split()

    def scale(channel: Image.Image) -> Image.Image:
        return channel.point(lambda value: round(value * factor))

    return Image.merge("RGBA", (scale(red), scale(green), scale(blue), alpha))


def average_brightness(texture: Image.Image) -> float:
    """Mean of the colour channels, used to report how much darker the result came out."""
    return sum(ImageStat.Stat(texture.convert("RGB")).mean) / 3


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--client-jar", help="path to the Minecraft client jar holding vanilla's textures")
    parser.add_argument("--factor", type=float, default=DEFAULT_FACTOR,
                        help=f"channel multiplier, 0 is black and 1 is untouched cobblestone (default: {DEFAULT_FACTOR})")
    args = parser.parse_args()
    if not 0 <= args.factor <= 1:
        raise SystemExit("--factor must be between 0 and 1")

    client_jar = find_client_jar(args.client_jar)
    print(f"Using vanilla textures from {client_jar}")

    cobblestone = load_cobblestone(client_jar)
    asphalt = darken(cobblestone, args.factor)

    DESTINATION.parent.mkdir(parents=True, exist_ok=True)
    asphalt.save(DESTINATION, "PNG")
    print(f"wrote {DESTINATION.relative_to(REPO_ROOT)} "
          f"(average brightness {average_brightness(cobblestone):.1f} -> {average_brightness(asphalt):.1f})")


if __name__ == "__main__":
    sys.exit(main())
