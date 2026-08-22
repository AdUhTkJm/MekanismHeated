#!/usr/bin/env python3
"""Generate Java VoxelShape declarations from a block model JSON file.

The model argument is resolved relative to ``--models-dir`` by default. For
example, from a directory that contains ``models/``:

    python scripts/model_to_voxelshape.py block/shaker/model.json

The generated Java code is written to stdout unless ``--output`` is given.
"""

from __future__ import annotations

import argparse
import json
import math
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any


class ModelError(ValueError):
    """Raised when a model cannot be represented by axis-aligned VoxelShapes."""


@dataclass(frozen=True)
class Box:
    min_x: float
    min_y: float
    min_z: float
    max_x: float
    max_y: float
    max_z: float

    @classmethod
    def from_coordinates(cls, start: list[float], end: list[float]) -> "Box":
        return cls(
            min(start[0], end[0]),
            min(start[1], end[1]),
            min(start[2], end[2]),
            max(start[0], end[0]),
            max(start[1], end[1]),
            max(start[2], end[2]),
        )

    def rotate_y(self, degrees: int) -> "Box":
        """Rotate around the center of a 16x16x16 block like blockstate y rotation."""
        if degrees == 0:
            return self
        if degrees == 90:
            return Box(
                16.0 - self.max_z,
                self.min_y,
                self.min_x,
                16.0 - self.min_z,
                self.max_y,
                self.max_x,
            )
        if degrees == 180:
            return Box(
                16.0 - self.max_x,
                self.min_y,
                16.0 - self.max_z,
                16.0 - self.min_x,
                self.max_y,
                16.0 - self.min_z,
            )
        if degrees == 270:
            return Box(
                self.min_z,
                self.min_y,
                16.0 - self.max_x,
                self.max_z,
                self.max_y,
                16.0 - self.min_x,
            )
        raise ValueError(f"Unsupported Y rotation: {degrees}")


def with_json_suffix(path: Path) -> Path:
    if path.suffix.lower() == ".json":
        return path
    return path.with_name(path.name + ".json")


def resolve_model_path(model: str, models_dir: Path) -> Path:
    requested = Path(model).expanduser()
    direct = with_json_suffix(requested)
    if direct.is_absolute() or direct.is_file():
        return direct

    relative = with_json_suffix(requested)
    if relative.parts and relative.parts[0] == models_dir.name:
        return relative
    return models_dir / relative


def read_coordinate_array(value: Any, name: str, index: int, path: Path) -> list[float]:
    if not isinstance(value, list) or len(value) != 3:
        raise ModelError(f"element {index} in {path} must contain a three-number '{name}' array")

    coordinates: list[float] = []
    for coordinate in value:
        if isinstance(coordinate, bool) or not isinstance(coordinate, (int, float)):
            raise ModelError(f"element {index} in {path} contains a non-numeric '{name}' coordinate")
        if not math.isfinite(coordinate):
            raise ModelError(f"element {index} in {path} contains a non-finite '{name}' coordinate")
        coordinates.append(float(coordinate))
    return coordinates


def read_boxes(path: Path) -> list[Box]:
    try:
        with path.open("r", encoding="utf-8") as model_file:
            model = json.load(model_file)
    except FileNotFoundError as error:
        raise ModelError(f"model file not found: {path}") from error
    except json.JSONDecodeError as error:
        raise ModelError(f"invalid JSON in {path}: {error}") from error
    except OSError as error:
        raise ModelError(f"unable to read {path}: {error}") from error

    elements = model.get("elements") if isinstance(model, dict) else None
    if not isinstance(elements, list) or not elements:
        raise ModelError(f"{path} does not contain a non-empty 'elements' array")

    boxes: list[Box] = []
    for index, element in enumerate(elements):
        if not isinstance(element, dict):
            raise ModelError(f"element {index} in {path} is not an object")

        rotation = element.get("rotation")
        if isinstance(rotation, dict):
            angle = rotation.get("angle", 0)
            if isinstance(angle, bool) or not isinstance(angle, (int, float)):
                raise ModelError(f"element {index} in {path} has an invalid rotation angle")
            if not math.isclose(float(angle), 0.0, abs_tol=1.0e-6):
                raise ModelError(
                    f"element {index} in {path} uses a non-zero rotation; "
                    "an axis-aligned VoxelShape cannot represent it exactly"
                )
        elif rotation is not None:
            raise ModelError(f"element {index} in {path} has an invalid rotation object")

        start = read_coordinate_array(element.get("from"), "from", index, path)
        end = read_coordinate_array(element.get("to"), "to", index, path)
        boxes.append(Box.from_coordinates(start, end))
    return boxes


def java_number(value: float) -> str:
    if math.isclose(value, round(value), abs_tol=1.0e-9):
        return f"{int(round(value))}.0D"

    text = format(value, ".12g")
    if text == "-0":
        text = "0"
    if "." not in text and "e" not in text.lower():
        text += ".0"
    return f"{text}D"


def java_box(box: Box) -> str:
    values = (
        box.min_x,
        box.min_y,
        box.min_z,
        box.max_x,
        box.max_y,
        box.max_z,
    )
    return "Block.box(" + ", ".join(java_number(value) for value in values) + ")"


def render_shape(name: str, boxes: list[Box]) -> list[str]:
    if len(boxes) == 1:
        return [f"private static final VoxelShape {name} = {java_box(boxes[0])};"]

    lines = [f"private static final VoxelShape {name} = Shapes.or("]
    for index, box in enumerate(boxes):
        suffix = "," if index < len(boxes) - 1 else ""
        lines.append(f"        {java_box(box)}{suffix}")
    lines.append(").optimize();")
    return lines


def render_java(boxes: list[Box], source: Path, shape_prefix: str) -> str:
    rotations = {
        "NORTH": 0,
        "EAST": 90,
        "SOUTH": 180,
        "WEST": 270,
    }

    lines = [
        "// Generated by scripts/model_to_voxelshape.py",
        f"// Source: {source}",
        "// Paste the declarations into the block class and use getShapeForFacing(state.getValue(FACING)).",
        "",
    ]

    shape_names: dict[str, str] = {}
    for direction, rotation in rotations.items():
        name = f"{shape_prefix}_{direction}"
        shape_names[direction] = name
        lines.extend(render_shape(name, [box.rotate_y(rotation) for box in boxes]))
        lines.append("")

    lines.extend(
        [
            "private static VoxelShape getShapeForFacing(Direction facing) {",
            "    return switch (facing) {",
            f"        case EAST -> {shape_names['EAST']};",
            f"        case SOUTH -> {shape_names['SOUTH']};",
            f"        case WEST -> {shape_names['WEST']};",
            f"        default -> {shape_names['NORTH']};",
            "    };",
            "}",
        ]
    )
    return "\n".join(lines) + "\n"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Generate Java VoxelShape code from an axis-aligned Minecraft block model."
    )
    parser.add_argument(
        "model",
        help="model JSON path relative to --models-dir, for example block/shaker/model.json",
    )
    parser.add_argument(
        "-m",
        "--models-dir",
        type=Path,
        default=Path("models"),
        help="base directory for relative model paths (default: models)",
    )
    parser.add_argument(
        "-o",
        "--output",
        type=Path,
        help="write generated Java code to this file instead of stdout",
    )
    parser.add_argument(
        "--shape-prefix",
        default="SHAPE",
        help="prefix for generated shape constants (default: SHAPE)",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if not args.shape_prefix.isidentifier():
        print(f"error: invalid Java identifier for --shape-prefix: {args.shape_prefix}", file=sys.stderr)
        return 2

    model_path = resolve_model_path(args.model, args.models_dir)
    try:
        boxes = read_boxes(model_path)
        generated = render_java(boxes, model_path, args.shape_prefix)
        if args.output is None:
            sys.stdout.write(generated)
        else:
            args.output.parent.mkdir(parents=True, exist_ok=True)
            args.output.write_text(generated, encoding="utf-8")
    except (ModelError, OSError) as error:
        print(f"error: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
