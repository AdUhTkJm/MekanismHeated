#!/usr/bin/env python3
"""Generate the metal overlays for the obsidian dust items.

Every obsidian dust draws Mekanism's obsidian dust texture (``mekanism:item/dust_obsidian``)
with a small icon of the metal it was condensed from in the top-left corner. This script bakes
those icons: for each ore below it downscales the ore's ingot texture to 6x6 and pastes it at
(0, 0) of an otherwise fully transparent 16x16 image, which the item model then draws as its
second layer (see ``models/item/<ore>_obsidian_dust.json``). Keeping the overlay 16x16 - the
same size as the base texture - is what lets a plain ``minecraft:item/generated`` model stack
the two layers, so every pixel the icon does not cover has to stay transparent.

Run from the repository root:

    python scripts/obsidian_dust_overlay.py

Iron and copper do not exist as textures in the Mekanism checkout (Mekanism uses vanilla's
ingots for them), so the vanilla ones are read out of the Minecraft client jar that NeoForm
keeps in the Gradle cache. Pass ``--client-jar`` or set ``MINECRAFT_CLIENT_JAR`` if the jar
lives somewhere else on your machine.
"""

from __future__ import annotations

import argparse
import glob
import io
import os
import sys
import zipfile
from pathlib import Path

from PIL import Image

REPO_ROOT = Path(__file__).resolve().parent.parent
DESTINATION = REPO_ROOT / "src/main/resources/assets/mekanismheated/textures/item/obsidian_dust"
MEKANISM_TEXTURES = REPO_ROOT / "mek/Mekanism/src/main/resources/assets/mekanism/textures/item"

# Where the client jar with vanilla's textures may live. NeoForm extracts the client assets into
# its Gradle cache under a content hash, so the directory name is not stable across machines.
CLIENT_JAR_GLOBS = (
    "~/.gradle/caches/ng_execute/*/client-extra.jar",
    "~/.gradle/caches/neoformruntime/*/client-extra.jar",
)
CLIENT_JAR_ENV = "MINECRAFT_CLIENT_JAR"

# The item icon lives in the top-left corner of the 16x16 overlay and is this many pixels wide.
ICON_SIZE = 6
TEXTURE_SIZE = 16

# ore -> (source, file name). "vanilla" ingots are pulled from the client jar, "mekanism" ones are
# read straight out of the Mekanism checkout. Adding an ore here is all it takes for the script;
# the matching Java side is a single ObsidianDustVariant.register call in ModItems.
INGOTS = {
    "iron": ("vanilla", "iron_ingot.png"),
    "copper": ("vanilla", "copper_ingot.png"),
    "gold": ("vanilla", "gold_ingot.png"),
    "tin": ("mekanism", "ingot_tin.png"),
    "osmium": ("mekanism", "ingot_osmium.png"),
    "lead": ("mekanism", "ingot_lead.png"),
}


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


def load_ingot(ore: str, source: str, file_name: str, client_jar: Path | None) -> Image.Image:
    """Read (and sanity check) the 16x16 ingot texture an overlay is built from."""
    if source == "vanilla":
        assert client_jar is not None
        with zipfile.ZipFile(client_jar) as jar:
            entry = f"assets/minecraft/textures/item/{file_name}"
            try:
                data = jar.read(entry)
            except KeyError:
                raise SystemExit(f"{client_jar} does not contain {entry}") from None
        ingot = Image.open(io.BytesIO(data))
    else:
        path = MEKANISM_TEXTURES / file_name
        if not path.is_file():
            raise SystemExit(f"Missing {path}; is the Mekanism checkout (mek/) next to this repo?")
        ingot = Image.open(path)

    ingot = ingot.convert("RGBA")
    if ingot.size != (TEXTURE_SIZE, TEXTURE_SIZE):
        raise SystemExit(f"Expected a {TEXTURE_SIZE}x{TEXTURE_SIZE} ingot texture for {ore}, got {ingot.size}")
    return ingot


def make_overlay(ingot: Image.Image) -> Image.Image:
    """Downscale the ingot to its corner icon and paste it onto a transparent 16x16 canvas."""
    overlay = Image.new("RGBA", (TEXTURE_SIZE, TEXTURE_SIZE), (0, 0, 0, 0))
    # Nearest neighbour keeps the ingot's pixel-art edges instead of smearing them, which matters
    # a lot at 6x6 where there is no room for anti-aliasing to read as anything but blur.
    icon = ingot.resize((ICON_SIZE, ICON_SIZE), Image.Resampling.NEAREST)
    overlay.paste(icon, (0, 0))
    return overlay


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--client-jar", help="path to the Minecraft client jar holding vanilla's textures")
    args = parser.parse_args()

    client_jar = find_client_jar(args.client_jar) if any(source == "vanilla" for source, _ in INGOTS.values()) else None
    if client_jar is not None:
        print(f"Using vanilla textures from {client_jar}")

    DESTINATION.mkdir(parents=True, exist_ok=True)
    for ore, (source, file_name) in INGOTS.items():
        ingot = load_ingot(ore, source, file_name, client_jar)
        destination = DESTINATION / f"{ore}.png"
        make_overlay(ingot).save(destination, "PNG")
        print(f"wrote {destination.relative_to(REPO_ROOT)}")


if __name__ == "__main__":
    sys.exit(main())
