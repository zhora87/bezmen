# ML tooling

Python, in its own venv (git-ignored):

    python3 -m venv tools/ml/.venv
    tools/ml/.venv/bin/pip install -r tools/ml/requirements.txt

## ocr_dump.py

Runs RapidOCR (PP-OCRv5 mobile models in ONNX, the same files the Android engine will ship) over
`corpus/images/<pack>/` and writes `corpus/ocr/rapidocr/<id>.json` in the OcrLine format the parser
consumes. Models are downloaded on first run and cached by rapidocr.

    tools/ml/.venv/bin/python tools/ml/ocr_dump.py --pack uk --vis

Then measure the parser on the real dumps:

    ./gradlew :tools:parser-cli:run --args="corpus --engine rapidocr --verbose"

Model conversion scripts, `models.lock` with sha256 and licences, and device benchmarks come next
(see docs/architecture.md, Models and licences).
