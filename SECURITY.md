# Security policy

## Reporting a vulnerability

Please use GitHub's private vulnerability reporting for this repository
(Security tab, "Report a vulnerability"). Do not open a public issue.

You will get an acknowledgement within 7 days. Fixes for confirmed issues ship as a patch release.

## Scope

Bezmen runs entirely on the device, requests only the camera permission and has no network
access. Reports about dependencies (ONNX Runtime, CameraX, ML Kit in the `play` flavor)
are welcome: updates to those are applied within a week of an upstream security release.
