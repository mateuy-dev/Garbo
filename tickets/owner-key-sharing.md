---
id: owner-key-sharing
type: feature
title: "Share an owner's key"
status: backlog
priority: medium
size: M
created: 2026-10-23
updated: 2026-10-23
blockedBy: [card-encryption]
---

## Objective

Let a user share one owner's cards with someone else (e.g. a child's cards with the other parent), including the sensitive ones. Define it with OpenSpec on this ticket's branch (`/opsx:propose owner-key-sharing`).

Background (from `vault-and-cards` and `card-encryption`): the vault is a plain folder; each owner is a self-contained folder with its own `owner.yaml` (stable `ownerId`, name, `keyId`). Sharing the folder itself is done outside Garbo (e.g. a Dropbox shared folder the receiver places inside their own vault), and Garbo just sees one more owner. Sensitive cards are encrypted with that owner's random key, which each user keeps wrapped with their own user key in their own `.garbo/keys/<keyId>.yaml` (not shared).

Decided in the exploration session (2026-10-23):

- **Sharing = the folder + the owner key.** The sender exports the owner key from Garbo as a **QR code or text**; the receiver imports it, and Garbo wraps it with the receiver's user key into their `.garbo/keys/`. Neither learns the other's password.
- Garbo detects an owner folder whose `keyId` has no key in this vault: it still shows the non-sensitive cards and the shells of sensitive ones, and offers "import key".
- Rejected for now: a key pair per user to drop the owner key, encrypted for the receiver, inside the shared folder (no hand-passed QR). Possible later; needs a way to exchange public keys.

Known limit, out of scope: stopping a share doesn't revoke the key; real revocation needs key rotation (new owner key, re-encrypt), a later change.

## Acceptance Criteria

## Work Log

