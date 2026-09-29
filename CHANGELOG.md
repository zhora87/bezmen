# Changelog

All notable changes to Bezmen are listed here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions follow semantic versioning.

## [Unreleased]

### Added
- Camera screen with a price-tag frame: focus and exposure metered on the frame, a full-resolution
  still on the shutter, blur and darkness checks before recognition.
- On-device OCR with PaddleOCR PP-OCRv5 models through ONNX Runtime; no network permission.
- Price tag parser for Ukrainian and Russian tags: price, kopecks in every layout, old price,
  card or app price, package quantity, weighted goods, product name.
- Result card with the price per 1 kg, 1 l or piece, every field editable, one-tap package sizes
  when the quantity was missed.
- Comparison list ranked by unit price, cheapest first, with the difference in percent.
- Settings: unit price per 1 kg / 1 l or per 100 g / 100 ml; recognition pack by country or chosen.
- Russian interface with English strings; TalkBack announces the scan status.
