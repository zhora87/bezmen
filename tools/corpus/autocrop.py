#!/usr/bin/env python3
"""Crop corpus photos to the price tag, the way the viewfinder frames it in the app.

Runs the RapidOCR detector (same 960 px detection limit as the on-device engine), takes the tallest
numeric text line as the price, collects the lines clustered around it as the tag, adds a margin and crops
the image in place. Small print that the detector missed on the full shelf photo becomes large
enough to read on the crop.

    tools/ml/.venv/bin/python tools/corpus/autocrop.py --pack uk [--ids uk-atb-008 ...] [--margin 0.25]
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

import numpy as np
from PIL import Image
from rapidocr import RapidOCR

ROOT = Path(__file__).resolve().parents[2]
CORPUS = ROOT / "corpus"


def crop_box(engine: RapidOCR, img: Image.Image, margin: float) -> tuple[int, int, int, int] | None:
    """The tag is the cluster of text lines around the tallest *numeric* line, which is the price."""
    result = engine(np.asarray(img)[..., ::-1].copy(), use_cls=False)
    if result.boxes is None or len(result.boxes) == 0:
        return None
    rects = []
    for box, text in zip(np.asarray(result.boxes), result.txts):
        xs, ys = box[:, 0], box[:, 1]
        rects.append(((xs.min(), ys.min(), xs.max(), ys.max()), text))
    numeric = [r for r, t in rects if sum(ch.isdigit() for ch in t) >= max(1, len(t) - 1) and len(t) <= 6]
    anchor = max(numeric or [r for r, _ in rects], key=lambda r: r[3] - r[1])
    h = anchor[3] - anchor[1]
    cx, cy = (anchor[0] + anchor[2]) / 2, (anchor[1] + anchor[3]) / 2
    near = [r for r, _ in rects if abs((r[1] + r[3]) / 2 - cy) <= NEAR_VERTICAL * h and abs((r[0] + r[2]) / 2 - cx) <= NEAR_HORIZONTAL * h]
    left = min(r[0] for r in near)
    top = min(r[1] for r in near)
    right = max(r[2] for r in near)
    bottom = max(r[3] for r in near)
    mx, my = (right - left) * margin, (bottom - top) * margin
    return (
        int(max(0, left - mx)),
        int(max(0, top - my)),
        int(min(img.width, right + mx)),
        int(min(img.height, bottom + my)),
    )


NEAR_VERTICAL = 3.0
NEAR_HORIZONTAL = 7.0


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--pack", required=True)
    ap.add_argument("--ids", nargs="*")
    ap.add_argument("--margin", type=float, default=0.2, help="extra space around the tag, fraction of its size")
    ap.add_argument("--min-fraction", type=float, default=0.1, help="skip crops smaller than this fraction of a side")
    args = ap.parse_args()
    engine = RapidOCR()
    images = sorted((CORPUS / "images" / args.pack).rglob("*.jpg"))
    if args.ids:
        images = [p for p in images if p.stem in args.ids]
    for path in images:
        with Image.open(path) as im:
            img = im.convert("RGB")
        box = crop_box(engine, img, args.margin)
        if box is None:
            print(f"{path.stem}: no text found, left as is", file=sys.stderr)
            continue
        cw, ch = box[2] - box[0], box[3] - box[1]
        if cw < img.width * args.min_fraction or ch < img.height * args.min_fraction:
            print(f"{path.stem}: crop {cw}x{ch} too small, left as is", file=sys.stderr)
            continue
        img.crop(box).save(path, "JPEG", quality=85, optimize=True)
        print(f"{path.stem}: {img.width}x{img.height} -> {cw}x{ch}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
