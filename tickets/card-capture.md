---
id: card-capture
type: feature
title: Capture cards with camera and barcode scanning
status: backlog
priority: medium
size: M
created: 2026-10-23
updated: 2026-10-23
blockedBy: [vault-and-cards]
---

## Objective

Let the user add a card's code and photos with the device instead of typing and importing files. Define it with OpenSpec on this ticket's branch (`/opsx:propose card-capture`), on top of `vault-and-cards` (which already supports a code entered as text and photos imported from an image file).

Decided in the exploration session (2026-10-23):

- Three ways to read a card: **scan its barcode** with the camera, **take a photo** (front and back), or **import an image** (already in `vault-and-cards`; here, also decode a barcode found in an imported image).
- Use a Compose Multiplatform library for scanning (e.g. KScan; choose in the design). Check: **desktop** camera support (often Android/iOS only; on desktop, image import may be the only way), and the formats ids use, **PDF417 and Aztec**, besides EAN-13, Code128 and QR.
- The scanned code is stored with its format (`code: { format, value }` in `card.yaml`), so it can be displayed again as the same barcode.
- A card is still valid without a code.

If `card-encryption` is done first, captured data of a sensitive card follows its rules (photos and code never written in plain text).

## Acceptance Criteria

## Work Log

