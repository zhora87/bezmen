#!/usr/bin/env python3
"""Add price tag photos to the corpus.

Resizes to 1024 px on the long side, re-encodes as JPEG q80, applies and then strips all EXIF
(orientation is baked in, GPS and timestamps are gone), names files <pack>-<store>-<nnn>.jpg and
writes a pending expected/<id>.json skeleton to fill in by hand.

    tools/corpus/ingest.py --pack ru --store magnit ~/Pictures/tag1.jpg ~/Pictures/tag2.jpg
    tools/corpus/ingest.py --pack ru --store unknown ~/Downloads/*.jpg
    tools/corpus/ingest.py --pack uk --store atb --crop 380,260,640,360 shelf.jpg   # one tag out of a shelf photo

--crop LEFT,TOP,RIGHT,BOTTOM is in source pixels and applies to every file given, so pass one file
when cropping. Run the same photo twice with different crops to get two cases out of it.

Photos land in corpus/images/, which git ignores. Only the OCR dumps and expected values are committed.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

try:
    from PIL import Image, ImageOps
except ImportError:  # pragma: no cover
    sys.exit("Pillow is required: pip install pillow")

try:  # AVIF support is a separate plugin (pip install pillow-avif-plugin); WebP is built in
    import pillow_avif  # noqa: F401
except ImportError:  # pragma: no cover
    pass

ROOT = Path(__file__).resolve().parents[2]
CORPUS = ROOT / "corpus"
LONG_SIDE = 1024
JPEG_QUALITY = 80
SLUG = re.compile(r"^[a-z0-9]+(-[a-z0-9]+)*$")


def next_number(folder: Path, prefix: str) -> int:
    used = []
    for f in folder.glob(f"{prefix}-*.jpg"):
        tail = f.stem.rsplit("-", 1)[-1]
        if tail.isdigit():
            used.append(int(tail))
    return max(used, default=0) + 1


def convert(src: Path, dst: Path, crop: tuple[int, int, int, int] | None) -> None:
    with Image.open(src) as img:
        img = ImageOps.exif_transpose(img)  # bake the orientation in before dropping EXIF
        img = img.convert("RGB")
        if crop:
            img = img.crop(crop)
        w, h = img.size
        scale = LONG_SIDE / max(w, h)
        if scale < 1:
            img = img.resize((round(w * scale), round(h * scale)), Image.LANCZOS)
        img.save(dst, "JPEG", quality=JPEG_QUALITY, optimize=True)  # no exif= argument: metadata is dropped


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--pack", required=True, help="locale pack id: ru, uk, en-generic")
    ap.add_argument("--store", required=True, help="store chain slug: magnit, pyaterochka, atb, unknown")
    ap.add_argument("--crop", help="LEFT,TOP,RIGHT,BOTTOM in source pixels, applied to every given file")
    ap.add_argument("files", nargs="+", type=Path)
    args = ap.parse_args()
    crop = None
    if args.crop:
        parts = [int(x) for x in args.crop.split(",")]
        if len(parts) != 4 or parts[0] >= parts[2] or parts[1] >= parts[3]:
            sys.exit("--crop needs LEFT,TOP,RIGHT,BOTTOM with left < right and top < bottom")
        crop = (parts[0], parts[1], parts[2], parts[3])

    if not SLUG.match(args.pack) or not SLUG.match(args.store):
        sys.exit("pack and store must be lower-case slugs like 'ru' or 'pyaterochka'")
    pack_file = ROOT / "locale-packs" / f"{args.pack}.json"
    if not pack_file.exists():
        sys.exit(f"unknown locale pack: {pack_file}")

    folder = CORPUS / "images" / args.pack / args.store
    folder.mkdir(parents=True, exist_ok=True)
    expected_dir = CORPUS / "expected"
    expected_dir.mkdir(parents=True, exist_ok=True)

    prefix = f"{args.pack}-{args.store}"
    number = next_number(folder, prefix)
    for src in args.files:
        if not src.exists():
            print(f"skip {src}: not found", file=sys.stderr)
            continue
        case_id = f"{prefix}-{number:03d}"
        dst = folder / f"{case_id}.jpg"
        convert(src, dst, crop)
        skeleton = expected_dir / f"{case_id}.json"
        if not skeleton.exists():
            skeleton.write_text(
                json.dumps(
                    {"pack": args.pack, "pending": True, "price": None, "oldPrice": None,
                     "quantity": None, "isWeighted": False, "note": ""},
                    ensure_ascii=False, indent=2,
                ) + "\n",
                encoding="utf-8",
            )
        print(f"{src.name} -> {dst.relative_to(ROOT)}  (fill {skeleton.relative_to(ROOT)})")
        number += 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
