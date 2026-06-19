#!/usr/bin/env python3
"""Generate neon sign glyph block models matching the existing letter style."""

import json
from pathlib import Path

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/neonlight/models/block"

FACES = {
    "north": {"uv": [0, 0, 2, 2], "texture": "#0", "tintindex": 0},
    "south": {"uv": [0, 0, 2, 2], "texture": "#0", "tintindex": 0},
    "east": {"uv": [0, 0, 2, 2], "texture": "#0", "tintindex": 0},
    "west": {"uv": [0, 0, 2, 2], "texture": "#0", "tintindex": 0},
    "up": {"uv": [0, 0, 2, 2], "texture": "#0", "tintindex": 0},
    "down": {"uv": [0, 0, 2, 2], "texture": "#0", "tintindex": 0},
}


def box(x1, y1, x2, y2, z1=7.5, z2=8.5):
    return {
        "from": [x1, y1, z1],
        "to": [x2, y2, z2],
        "rotation": {"angle": 0, "axis": "y", "origin": [8, 8, 8]},
        "faces": dict(FACES),
    }


def h(x1, x2, y):
    return box(x1, y, x2, y + 1)


def v(x, y1, y2):
    return box(x, y1, x + 1, y2)


# 7-segment layout
SEG = {
    "a": h(6, 10, 10),
    "b": v(10, 7, 10),
    "c": v(10, 5, 8),
    "d": h(6, 10, 5),
    "e": v(6, 5, 8),
    "f": v(6, 7, 10),
    "g": h(6, 10, 7),
}

DIGITS = {
    "0": "abcdef",
    "1": "bc",
    "2": "abdeg",
    "3": "abcdg",
    "4": "bcfg",
    "5": "acdfg",
    "6": "acdefg",
    "7": "abc",
    "8": "abcdefg",
    "9": "abcdfg",
}


def model_for_elements(elements):
    return {
        "format_version": "1.21.11",
        "credit": "Generated for Neon Lights",
        "render_type": "minecraft:translucent",
        "textures": {
            "0": "neonlight:block/glyph_base",
            "particle": "neonlight:block/glyph_base",
        },
        "elements": elements,
    }


def write_model(name, elements):
    path = OUT / f"{name}.json"
    path.write_text(json.dumps(model_for_elements(elements), indent="\t") + "\n", encoding="utf-8")


def digit_elements(d):
    return [SEG[s] for s in DIGITS[d]]


def symbol_elements(symbol):
    if symbol == "!":
        return [v(8, 6, 10), box(8, 5, 9, 6)]
    if symbol == "@":
        return [SEG["a"], SEG["d"], SEG["f"], SEG["g"], v(10, 6, 9), h(7, 9, 8), box(7, 6, 8, 7)]
    if symbol == "#":
        return [v(7, 6, 9), v(10, 6, 9), h(6, 11, 8), h(6, 11, 6)]
    if symbol == "$":
        return [SEG["a"], SEG["d"], SEG["f"], SEG["g"], v(10, 7, 10), v(6, 5, 7), h(6, 10, 7)]
    if symbol == "%":
        return [box(6, 9, 7, 10), box(9, 5, 10, 6), box(7, 7, 8, 8), box(8, 6, 9, 7), box(6, 5, 7, 6)]
    if symbol == "^":
        return [box(6, 8, 7, 10), box(9, 8, 10, 10), h(7, 9, 9)]
    if symbol == "&":
        return [SEG["a"], SEG["d"], SEG["e"], SEG["g"], v(10, 6, 8), h(7, 9, 7), box(8, 8, 9, 9)]
    if symbol == "*":
        return [h(7, 9, 8), v(8, 6, 9), box(7, 7, 8, 8), box(9, 7, 10, 8), box(7, 9, 8, 10), box(9, 9, 10, 10)]
    if symbol == "(":
        return [v(6, 5, 11), h(6, 10, 10), h(6, 10, 5)]
    if symbol == ")":
        return [v(10, 5, 11), h(7, 11, 10), h(7, 11, 5)]
    if symbol == "-":
        return [h(6, 10, 7)]
    if symbol == "_":
        return [h(5, 11, 5)]
    if symbol == ".":
        return [box(8, 5, 9, 6)]
    if symbol == ",":
        return [box(8, 5, 9, 6), box(7, 4, 8, 5)]
    if symbol == "?":
        return [SEG["a"], SEG["b"], SEG["g"], box(8, 5, 9, 6), v(6, 8, 10)]
    if symbol == "up":
        return [v(8, 5, 8), h(6, 10, 9), box(6, 8, 7, 9), box(9, 8, 10, 9)]
    if symbol == "down":
        return [v(8, 6, 10), h(6, 10, 6), box(6, 6, 7, 7), box(9, 6, 10, 7)]
    if symbol == "left":
        return [h(6, 9, 8), v(5, 8, 8), box(6, 9, 7, 10), box(6, 6, 7, 7)]
    if symbol == "right":
        return [h(7, 10, 8), v(9, 8, 8), box(9, 6, 10, 7), box(9, 9, 10, 10)]
    raise ValueError(symbol)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for d in "0123456789":
        write_model(f"number_{d}", digit_elements(d))
    symbol_names = {
        "!": "symbol_exclamation",
        "@": "symbol_at",
        "#": "symbol_hash",
        "$": "symbol_dollar",
        "%": "symbol_percent",
        "^": "symbol_caret",
        "&": "symbol_ampersand",
        "*": "symbol_asterisk",
        "(": "symbol_paren_open",
        ")": "symbol_paren_close",
        "-": "symbol_minus",
        "_": "symbol_underscore",
        ".": "symbol_period",
        ",": "symbol_comma",
        "?": "symbol_question",
    }
    for ch, name in symbol_names.items():
        write_model(name, symbol_elements(ch))
    for direction in ("up", "down", "left", "right"):
        write_model(f"arrow_{direction}", symbol_elements(direction))
    print(f"Wrote glyph models to {OUT}")


if __name__ == "__main__":
    main()
