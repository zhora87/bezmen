# Locale packs

One JSON file per country or region describing everything that varies between price tags:
currency, decimal separators, unit spellings, unit-price / discount / old-price / loyalty markers,
OCR character fixes. Format: `schema.json`; semantics: [docs/parsing.md](../docs/parsing.md#locale-pack-format).

| id | script | currency | status |
|---|---|---|---|
| `ru` | Cyrillic | RUB | synthetic tests, awaiting corpus |
| `uk` | Cyrillic | UAH | synthetic tests, awaiting corpus |
| `en-generic` | Latin | EUR | synthetic tests, awaiting corpus |

Adding a country means adding a file that validates against `schema.json`, plus at least 20 price
tag photos in `corpus/`. No code changes required. Every pack is loaded and validated by
`core/domain` tests.
