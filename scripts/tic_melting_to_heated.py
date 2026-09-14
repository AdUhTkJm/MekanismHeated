#!/usr/bin/env python3
"""Convert Tinkers' Construct smeltery melting recipes into heated_melting recipes.

Reads every JSON under Tinkers' Construct's generated melting recipes and writes one
``mekanismheated:heated_melting`` recipe per source file to
``src/main/resources/data/mekanismheated/recipe/heated_melting/tic/``, mirroring TiC's
subdirectories.

    ingredient  -> input        (item / tag / compound / intersection / difference)
    result      -> output       (fluid or fluid tag plus amount)
    temperature -> temperature  (TiC's Celsius-like scale + 273, i.e. Kelvin)
    everything else (time, byproducts, rate, unit_size, ...) is dropped

Run from the repository root:

    python scripts/tic_melting_to_heated.py                            # write the recipes
    python scripts/tic_melting_to_heated.py --dry-run                   # report only
    python scripts/tic_melting_to_heated.py --prune                     # drop stale outputs
    python scripts/tic_melting_to_heated.py --no-skip-existing-inputs   # keep duplicates

Note the source directory: Minecraft 1.21 renamed the ``recipes`` data-pack directory to
``recipe``, and TiC's ``build.gradle`` excludes the leftover ``data/tconstruct/recipes/**``
tree from its jar. The singular tree is therefore the one players actually get, and it is
the default here. Pointing ``--source`` at the plural tree fails loudly, as those files use
a pre-1.21 ``neoforge:conditional`` wrapper that no 1.21 loader understands.

Two TiC recipe kinds are not convertible and are skipped:

* ``tconstruct:melting_fuel`` -- smeltery fuel definitions, not item melting recipes.
* ``tconstruct:damagable_melting`` -- melts tools/armor, scaling the output by the input's
  remaining durability. Our heat smelter has no per-damage output, so by default these are
  skipped rather than letting a nearly broken tool melt for its full value. Pass
  ``--include-damagable`` to emit them at full amount anyway.

Ingredients that reference items or fluids from other mods are gated behind
``neoforge:mod_loaded`` conditions, so the recipes only load when everything they need is
present. Without the gates, an unknown item or fluid is a recipe *parse* error rather than
a recipe that is simply never matched.
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any

REPO_ROOT = Path(__file__).resolve().parent.parent

# Minecraft 1.21 renamed the data-pack directory; this is the tree TiC actually ships.
DEFAULT_SOURCE = Path(
    "mek/TiC/src/generated/resources/data/tconstruct/recipe/smeltery/melting"
)
DEFAULT_DEST = Path(
    "src/main/resources/data/mekanismheated/recipe/heated_melting/tic"
)

RECIPE_TYPE = "mekanismheated:heated_melting"

# TiC stores smeltery temperatures on a Celsius-like scale; our heat smelter's recipe
# thresholds, as well as all of our other recipes, are in Kelvin.
KELVIN_OFFSET = 273

# TiC recipe types that melt an item. Everything else is skipped.
MELTING_TYPES = frozenset({"tconstruct:melting", "tconstruct:ore_melting"})
DAMAGABLE_TYPE = "tconstruct:damagable_melting"

# Namespaces that never need a mod-loaded gate: vanilla is always there, and Mekanism is a
# hard dependency of this mod.
IMPLICIT_MODS = frozenset({"minecraft", "mekanism"})

# Every converted recipe comes from TiC, so it is always gated on TiC being present, even
# when the recipe itself only references vanilla items and common tags.
BASE_MOD = "tconstruct"

INDENT = 2


class ConversionError(ValueError):
    """Raised when a TiC recipe cannot be expressed as a heated_melting recipe."""


def plain_ingredient(ingredient: dict[str, Any], where: Path) -> dict[str, Any]:
    """Convert an untyped ``{"item": ...}`` / ``{"tag": ...}`` ingredient."""
    present = [key for key in ("item", "tag") if key in ingredient]
    if len(present) != 1:
        raise ConversionError(
            f"{where}: expected exactly one of 'item' or 'tag', got {sorted(ingredient)}"
        )
    key = present[0]
    value = ingredient[key]
    if not isinstance(value, str):
        raise ConversionError(f"{where}: {key} must be a string, got {value!r}")
    return {key: value}


def convert_ingredient(ingredient: Any, where: Path) -> dict[str, Any]:
    """Convert a TiC ingredient into the plain NeoForge ingredient JSON our codec reads.

    The result carries no ``count``; callers add it at the top level only, as NeoForge's
    ``SizedIngredient`` allows a count on the outer ingredient but not on nested ones.
    """
    if isinstance(ingredient, list):
        # Mantle allows a bare array as shorthand for "any of these".
        return {
            "type": "neoforge:compound",
            "children": [convert_ingredient(child, where) for child in ingredient],
        }
    if isinstance(ingredient, str):
        return {"item": ingredient}
    if not isinstance(ingredient, dict):
        raise ConversionError(f"{where}: unsupported ingredient {ingredient!r}")

    kind = ingredient.get("type")
    if kind is None:
        return plain_ingredient(ingredient, where)
    if kind == "tconstruct:no_container":
        # TiC-only ingredient: matches the nested ingredient, but only for stacks with no
        # container item (so a filled copper can does not melt). We have no equivalent
        # filter, so we keep the nested items and drop that extra check. 1.20-era files
        # put the nested ingredient inline next to "type" instead of under "match".
        nested = ingredient.get("match")
        if nested is None:
            nested = {key: value for key, value in ingredient.items() if key != "type"}
        return convert_ingredient(nested, where)
    if kind == "neoforge:compound":
        children = ingredient.get("children", ingredient.get("ingredients"))
        return {
            "type": "neoforge:compound",
            "children": [convert_ingredient(child, where) for child in children],
        }
    if kind == "neoforge:intersection":
        return {
            "type": kind,
            "children": [
                convert_ingredient(child, where) for child in ingredient["children"]
            ],
        }
    if kind == "neoforge:difference":
        return {
            "type": kind,
            "base": convert_ingredient(ingredient["base"], where),
            "subtracted": convert_ingredient(ingredient["subtracted"], where),
        }
    raise ConversionError(f"{where}: unsupported ingredient type {kind!r}")


def convert_result(result: Any, where: Path) -> dict[str, Any]:
    """Convert a TiC fluid output into the fluid ingredient JSON our codec reads."""
    if not isinstance(result, dict):
        raise ConversionError(f"{where}: unsupported result {result!r}")
    amount = result.get("amount")
    if not isinstance(amount, int) or isinstance(amount, bool) or amount <= 0:
        raise ConversionError(
            f"{where}: result amount must be a positive int, got {amount!r}"
        )
    if "fluid" in result:
        return {"amount": amount, "fluid": result["fluid"]}
    if "tag" in result:
        return {"amount": amount, "tag": result["tag"]}
    raise ConversionError(f"{where}: result has neither 'fluid' nor 'tag': {sorted(result)}")


def referenced_mods(node: Any) -> set[str]:
    """Collect the namespaces of every hard item/fluid reference below ``node``.

    Tags are ignored on purpose: an unknown or empty tag is not a parse error, it just
    leaves the recipe unmatched, while an unknown item or fluid id is.
    """
    mods: set[str] = set()
    if isinstance(node, dict):
        for key, value in node.items():
            if key in ("item", "fluid") and isinstance(value, str) and ":" in value:
                mods.add(value.split(":", 1)[0])
            else:
                mods |= referenced_mods(value)
    elif isinstance(node, list):
        for child in node:
            mods |= referenced_mods(child)
    return mods


def convert_recipe(raw: dict[str, Any], where: Path) -> dict[str, Any]:
    """Convert one TiC melting recipe into a heated_melting recipe."""
    if "ingredient" not in raw:
        raise ConversionError(f"{where}: recipe has no 'ingredient'")
    if "result" not in raw:
        raise ConversionError(f"{where}: recipe has no 'result'")

    temperature = raw.get("temperature")
    if not isinstance(temperature, int) or isinstance(temperature, bool) or temperature < 0:
        # Every melting recipe in the shipped tree has one; a missing value would either be
        # a TiC change or the wrong source directory.
        raise ConversionError(
            f"{where}: expected a non-negative 'temperature', got {temperature!r}"
        )

    input_ingredient = {"count": 1, **convert_ingredient(raw["ingredient"], where)}
    output = convert_result(raw["result"], where)

    mods = {BASE_MOD} | referenced_mods(input_ingredient) | referenced_mods(output)
    conditions = [
        {"type": "neoforge:mod_loaded", "modid": modid}
        for modid in sorted(mods - IMPLICIT_MODS, key=lambda mod: (mod != BASE_MOD, mod))
    ]

    return {
        "type": RECIPE_TYPE,
        "neoforge:conditions": conditions,
        "input": input_ingredient,
        "output": output,
        "temperature": temperature + KELVIN_OFFSET,
    }


def input_key(recipe: dict[str, Any]) -> str:
    """The part of an input that decides which stacks it matches; the count plays no role."""
    return json.dumps(
        {key: value for key, value in recipe["input"].items() if key != "count"},
        sort_keys=True,
    )


def existing_input_keys(dest: Path) -> set[str]:
    """Inputs already covered by the hand-written recipes beside the output directory.

    Matching is exact JSON equality, which is enough here because those recipes are written
    against the same common item and tag ids that TiC uses. Two recipes with the same input
    would be resolved arbitrarily at runtime, so the generated one steps aside by default.
    Reading ``dest.parent`` rather than a fixed path means this degrades to "nothing to
    skip" when the output is redirected elsewhere.
    """
    keys: set[str] = set()
    for path in sorted(dest.parent.glob("*.json")):
        try:
            recipe = json.loads(path.read_text(encoding="utf-8"))
        except json.JSONDecodeError:
            continue
        if isinstance(recipe, dict) and recipe.get("type") == RECIPE_TYPE and "input" in recipe:
            keys.add(input_key(recipe))
    return keys


def parse_args(argv: list[str] | None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument(
        "--source",
        type=Path,
        default=DEFAULT_SOURCE,
        help=f"TiC melting recipe directory (default: {DEFAULT_SOURCE})",
    )
    parser.add_argument(
        "--dest",
        type=Path,
        default=DEFAULT_DEST,
        help=f"output directory (default: {DEFAULT_DEST})",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="report what would be written without touching the file system",
    )
    parser.add_argument(
        "--prune",
        action="store_true",
        help="delete .json files in the output directory that are no longer generated",
    )
    parser.add_argument(
        "--include-damagable",
        action="store_true",
        help="also convert tconstruct:damagable_melting (tool/armor) recipes at full amount",
    )
    parser.add_argument(
        "--skip-existing-inputs",
        action=argparse.BooleanOptionalAction,
        default=True,
        help="skip recipes whose input is already covered by a hand-written recipe in the "
        "directory above --dest (default: on)",
    )
    return parser.parse_args(argv)


def resolve(path: Path) -> Path:
    return path if path.is_absolute() else REPO_ROOT / path


def display(path: Path) -> str:
    """Render a path relative to the repository when possible, else absolute."""
    try:
        return str(path.relative_to(REPO_ROOT))
    except ValueError:
        return str(path)


def prune(dest: Path, written: set[Path]) -> list[Path]:
    """Delete stale outputs, then any directory the deletion emptied."""
    removed = []
    for stale in sorted(dest.rglob("*.json")):
        if stale not in written:
            stale.unlink()
            removed.append(stale)
    for directory in sorted((p for p in dest.rglob("*") if p.is_dir()), reverse=True):
        if not any(directory.iterdir()):
            directory.rmdir()
    return removed


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    source = resolve(args.source)
    dest = resolve(args.dest)
    if not source.is_dir():
        print(f"error: {source} is not a directory", file=sys.stderr)
        return 2

    convertible_types = set(MELTING_TYPES)
    if args.include_damagable:
        convertible_types.add(DAMAGABLE_TYPE)
    covered = existing_input_keys(dest) if args.skip_existing_inputs else set()

    sources = sorted(source.rglob("*.json"))
    skipped: dict[str, int] = {}
    skipped_total = 0
    duplicates = 0
    converted = 0
    errors: list[str] = []
    written: set[Path] = set()

    for path in sources:
        where = path.relative_to(source)
        try:
            raw = json.loads(path.read_text(encoding="utf-8"))
        except json.JSONDecodeError as error:
            errors.append(f"{where}: invalid JSON: {error}")
            continue
        if not isinstance(raw, dict):
            errors.append(f"{where}: expected a JSON object, got {type(raw).__name__}")
            continue

        kind = raw.get("type")
        if kind == "neoforge:conditional":
            errors.append(
                f"{where}: 'neoforge:conditional' is the pre-1.21 wrapper; point --source at "
                "TiC's 'data/tconstruct/recipe/...' tree instead of 'recipes/...'"
            )
            continue
        if kind not in convertible_types:
            skipped[kind] = skipped.get(kind, 0) + 1
            skipped_total += 1
            continue

        try:
            recipe = convert_recipe(raw, where)
        except ConversionError as error:
            errors.append(str(error))
            continue
        except (KeyError, TypeError, ValueError) as error:
            # Malformed or unexpected shape: report it as a conversion failure rather than
            # letting the whole run die halfway through.
            errors.append(f"{where}: {type(error).__name__}: {error}")
            continue

        if input_key(recipe) in covered:
            duplicates += 1
            continue
        converted += 1

        target = dest / where
        if args.dry_run:
            continue
        target.parent.mkdir(parents=True, exist_ok=True)
        with target.open("w", encoding="utf-8", newline="\n") as handle:
            handle.write(json.dumps(recipe, indent=INDENT) + "\n")
        written.add(target)

    removed = prune(dest, written) if args.prune and not args.dry_run else []

    for error in errors:
        print(f"error: {error}", file=sys.stderr)
    print(f"{len(sources)} TiC melting recipes found in {display(source)}")
    for kind, count in sorted(skipped.items()):
        print(f"  skipped {count:4d} x {kind}")
    if duplicates:
        print(
            f"  skipped {duplicates:4d} x input already defined in {display(dest.parent)}"
        )
    print(f"{'would write' if args.dry_run else 'wrote'} {converted} recipes to {display(dest)}")
    if removed:
        print(f"pruned {len(removed)} stale file(s)")
    if errors:
        print(f"{len(errors)} recipe(s) could not be converted", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
