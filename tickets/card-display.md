---
id: card-display
type: feature
title: "Show a card's barcode and photos"
status: backlog
priority: medium
size: M
created: 2026-10-23
updated: 2026-10-23
blockedBy: [vault-and-cards]
---

## Objective

Let the user use a card at the till or desk: see its code as a barcode, or see its photos. Define it with OpenSpec on this ticket's branch (`/opsx:propose card-display`), on top of `vault-and-cards`.

Decided in the exploration session (2026-10-23):

- **Show the code as a barcode** generated from `card.yaml`'s `code: { format, value }`, in its format (EAN-13, Code128, QR, PDF417, Aztec...). Pick a multiplatform generation library in the design.
- **Full screen with maximum brightness** while the code is shown (it matters at shop scanners); restore it afterwards.
- **Photo view** of front and back, for cards with no code or when the photo is what's needed.

If `card-encryption` is done, showing a sensitive card asks to unlock first, and decrypted photos are never written to disk.

Out of scope: exporting a watermarked image of an id (left for later).

## Acceptance Criteria

## Work Log

