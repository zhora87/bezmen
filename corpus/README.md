# Reference corpus

```
images/<pack>/<store>/<pack>-<store>-<nnn>.jpg   photo of one price tag, roughly cropped to the tag (local only, git-ignored)
ocr/<engine>/<id>.json                           OcrLine dump produced by a specific engine (committed)
expected/<id>.json                               expected price, oldPrice, quantity, isWeighted, note (committed)
```

`<id>` is the file name without extension, e.g. `ru-magnit-007`. `pack` inside `expected/<id>.json`
selects the locale pack. `ocr/synthetic/` holds hand-written dumps mirroring the parser's unit tests.

Photos are not versioned. The parser and the CI gate need only the dumps and the expected values.

## Adding photos

    tools/corpus/ingest.py --pack ru --store magnit ~/Pictures/IMG_0001.jpg ~/Pictures/IMG_0002.jpg

The script resizes to 1024 px on the long side, re-encodes as JPEG q80, bakes in the orientation and
strips every other EXIF field (GPS, timestamps, device). It names the files and writes a pending
`expected/<id>.json` skeleton. Fill the skeleton by reading the tag with your own eyes, then delete
`"pending": true`; cases still pending are skipped by the accuracy gate.

`expected/<id>.json`:

```json
{
  "pack": "ru",
  "price": { "minor": 24990, "currency": "RUB" },
  "oldPrice": null,
  "quantity": { "value": 900.0, "unit": "MILLILITRE" },
  "isWeighted": false,
  "note": "superscript kopecks, yellow discount tag"
}
```

Units: MILLIGRAM, GRAM, KILOGRAM, POUND, MILLILITRE, CENTILITRE, LITRE, PIECE, PAIR, DOZEN.
For weighted goods `quantity` is the reference the price is printed for (1 KILOGRAM) and
`isWeighted` is true.

## What to shoot

Variety beats volume. Per store chain aim for: regular tags, yellow/red discount tags with two
prices, loyalty-card prices, weighted goods (price per kg), multipacks (6 x 200 г), superscript
kopecks, tags with a printed price per kg / per 100 g, e-ink tags, a few bad shots (glare, angle).
No people, no receipts, no personal data.
