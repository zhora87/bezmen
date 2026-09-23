#!/usr/bin/env python3
"""Run RapidOCR (PP-OCRv5 mobile, ONNX) over corpus photos and write OcrLine dumps.

    tools/ml/.venv/bin/python tools/ml/ocr_dump.py --pack uk
    tools/ml/.venv/bin/python tools/ml/ocr_dump.py --pack uk --ids uk-atb-003 --vis

Reads  corpus/images/<pack>/<store>/<id>.jpg
Writes corpus/ocr/<engine>/<id>.json  as [{"text", "box": {left, top, right, bottom} in 0..1, "confidence"}]

The recognition model follows the pack's `ocrModel` (cyrillic / latin). These are the same ONNX
files the Android engine will ship, so the dumps are representative. --vis saves an annotated
copy next to the dump for eyeballing.
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

try:
    import pillow_avif  # noqa: F401
except ImportError:  # pragma: no cover
    pass
from PIL import Image
from rapidocr import EngineType, LangDet, LangRec, ModelType, OCRVersion, RapidOCR

ROOT = Path(__file__).resolve().parents[2]
CORPUS = ROOT / "corpus"

REC_BY_MODEL = {"cyrillic": LangRec.CYRILLIC, "latin": LangRec.LATIN}


def build_engine(ocr_model: str) -> RapidOCR:
    rec = REC_BY_MODEL.get(ocr_model)
    if rec is None:
        sys.exit(f"no RapidOCR recognition model for ocrModel={ocr_model!r}")
    params = {
        "Det.engine_type": EngineType.ONNXRUNTIME,
        "Det.lang_type": LangDet.MULTI,
        "Det.ocr_version": OCRVersion.PPOCRV5,
        "Det.model_type": ModelType.MOBILE,
        "Cls.engine_type": EngineType.ONNXRUNTIME,
        "Rec.engine_type": EngineType.ONNXRUNTIME,
        "Rec.lang_type": rec,
        "Rec.ocr_version": OCRVersion.PPOCRV5,
        "Rec.model_type": ModelType.MOBILE,
    }
    try:
        return RapidOCR(params=params)
    except Exception as e:  # noqa: BLE001 - model matrix differs between versions; fall back to the default detector
        print(f"multilingual PP-OCRv5 detector unavailable ({type(e).__name__}: {e}); using the default detector", file=sys.stderr)
        for key in ("Det.lang_type", "Det.ocr_version", "Det.model_type"):
            params.pop(key)
        return RapidOCR(params=params)


def dump(engine: RapidOCR, image: Path, out: Path, vis: bool) -> int:
    with Image.open(image) as im:
        width, height = im.size
    result = engine(str(image))
    lines = []
    if result.boxes is not None:
        for box, text, score in zip(result.boxes, result.txts, result.scores):
            xs = [float(p[0]) for p in box]
            ys = [float(p[1]) for p in box]
            lines.append(
                {
                    "text": text,
                    "box": {
                        "left": round(min(xs) / width, 4),
                        "top": round(min(ys) / height, 4),
                        "right": round(max(xs) / width, 4),
                        "bottom": round(max(ys) / height, 4),
                    },
                    "confidence": round(float(score), 4),
                }
            )
    lines.sort(key=lambda l: (l["box"]["top"], l["box"]["left"]))
    out.write_text(json.dumps(lines, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    if vis and result.boxes is not None:
        result.vis(str(out.with_suffix(".vis.jpg")))
    return len(lines)


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--pack", required=True, help="locale pack id; selects the recognition model")
    ap.add_argument("--engine-name", default="rapidocr", help="subdirectory under corpus/ocr/")
    ap.add_argument("--ids", nargs="*", help="only these case ids")
    ap.add_argument("--vis", action="store_true", help="also write <id>.vis.jpg with the boxes drawn")
    args = ap.parse_args()

    pack = json.loads((ROOT / "locale-packs" / f"{args.pack}.json").read_text(encoding="utf-8"))
    engine = build_engine(pack["ocrModel"])
    images = sorted((CORPUS / "images" / args.pack).rglob("*.jpg"))
    if args.ids:
        images = [p for p in images if p.stem in args.ids]
    if not images:
        sys.exit(f"no images under corpus/images/{args.pack}")
    out_dir = CORPUS / "ocr" / args.engine_name
    out_dir.mkdir(parents=True, exist_ok=True)
    for image in images:
        n = dump(engine, image, out_dir / f"{image.stem}.json", args.vis)
        print(f"{image.stem}: {n} lines")
    return 0


if __name__ == "__main__":
    sys.exit(main())
