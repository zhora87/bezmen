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

## rewrite_hardswish.py

Produces the model files listed in `models.lock`: replaces every `HardSwish` node with the
numerically identical `HardSigmoid` + `Mul` pair and verifies the outputs match the original.
Needed because the ONNX Runtime native library in the Maven artifact computes `HardSwish`
incorrectly. Run it on the upstream RapidOCR exports and publish the results as release assets.

    tools/ml/.venv/bin/python tools/ml/rewrite_hardswish.py in.onnx out-nohs.onnx
