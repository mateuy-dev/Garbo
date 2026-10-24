---
id: vault-and-cards
type: feature
title: "Vault, owners and cards"
status: in-progress
priority: medium
size: L
created: 2026-10-23
updated: 2026-10-24
---

## Objective

First usable version of Garbo: the vault folder, owners and cards, without encryption or camera. Define it with OpenSpec on this ticket's branch (`/opsx:propose vault-and-cards`, then apply and archive), so the change and its specs (vault, owners, cards) merge with the code.

Garbo stores ids and cards (shopping, library, national ids) in **a plain folder the user picks**, so it can be synced by any external tool (e.g. Dropbox). Garbo itself never syncs: it only reads and writes files. Sync is out of scope.

Decisions taken (in an exploration session, 2026-10-23):

- **Layout** (one file per thing, so sync conflicts stay local to one card):
  ```
  <vault>/
    .garbo/vault.yaml            format version, vaultId, defaultOwner (an ownerId)
    <owner-folder>/
      owner.yaml                 ownerId (stable uuid), name, color
      <card-folder>/
        card.yaml                name, type, code (format + value), notes...
        front.jpg, back.jpg      optional photos
  ```
- **Owners are folders**: the owner folder is the unit of sharing (a user can share e.g. a child's folder with another family member through Dropbox; the receiver drops it into their own vault and it simply appears). So each owner folder is self-contained, and its display name and identity live in `owner.yaml`, never in the folder name (Dropbox lets each person rename a shared folder). Rejected: an `owner` field with all cards in one folder; it is easier for owner changes but cannot share one owner's cards.
- **Exactly one owner per card**, and a **default owner** ("Me", created with the vault) stored in `vault.yaml` by ownerId, pre-selected for new cards. Changing a card's owner moves its folder (accepted).
- **One folder per card**, named by a stable random id, not by the card name (renaming a card moves nothing). Rejected: all card yamls in one folder with photo folders apart (a card in two places, orphan photos).
- **YAML, not markdown.**
- A card is valid **without a code**; photos are front and back, both optional. In this change photos come from importing an image file; the code is entered as text.
- UI: list of cards filterable by owner; create, edit, delete cards; manage owners.

Not in this change, but the format must leave room for them (planned as separate changes/tickets):
- **card-encryption**: cards can be marked sensitive (ids by default). A sensitive card keeps `card.yaml` plain (name, type, owner stay visible without a password) and moves code/number/notes to `secret.yaml.enc`, with photos encrypted (`front.jpg.enc`). Keys: password --Argon2id--> random user key (`.garbo/user-key.yaml`); one random key per owner, wrapped by the user key, one file per key in `.garbo/keys/<keyId>.yaml` (never modified, so no sync conflicts); `owner.yaml` only names its `keyId`. Optional recovery code (a second wrapper of the user key in `.garbo/recovery-key.yaml`, offered at vault creation, skippable with a warning). Password reset is also allowed from a device holding the user key behind biometrics.
- **card-capture**: barcode scanning and photos with the camera, with a Compose Multiplatform library (check desktop support and PDF417/Aztec).
- **card-display**: show the code as a barcode, full screen with max brightness; photo view.
- **owner-key-sharing**: share the folder plus the owner key as QR/text; import it into the receiver's keyring.
- Later: watermarked export of ids, key rotation, public-key sharing, sync.

## Acceptance Criteria

## Work Log

- **2026-10-24** · status · started
