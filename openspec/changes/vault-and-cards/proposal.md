# Proposal

## Why

Garbo is still the project template: it cannot store a single card. This change delivers the first usable version: the user picks a plain folder (the vault), and Garbo keeps owners and their cards there as readable files, so any external tool (e.g. Dropbox) can sync or share them without Garbo running a server or syncing anything itself.

## What Changes

- Replace the template screen with the Garbo app: first-run vault setup, the card list, card editing and owner management.
- **Vault**: the user creates a vault in an empty folder or opens an existing one; Garbo remembers it on the device and reopens it on launch. The vault folder holds `.garbo/vault.yaml` (format version, vault id, default owner) and one folder per owner. Garbo re-reads the folder when it comes back to the foreground, so changes made by a sync tool appear.
- **Owners**: each owner is a self-contained folder with `owner.yaml` (stable owner id, name, color). The folder name is never the identity, so a shared folder renamed by Dropbox still works, and an owner folder dropped into the vault appears as a new owner. A default owner ("Me") is created with the vault. Owners can be created, renamed, recolored and deleted (only when they have no cards and are not the default), and any owner can be made the default.
- **Cards**: each card is a folder named by a random id inside its owner's folder, with `card.yaml` (name, whether it is an identity document, an optional code value, notes) and optional `front.jpg` / `back.jpg` imported from image files. Cards can be created, edited, deleted and moved to another owner (which moves the folder). The list shows all cards, filterable by owner.
- Files written by Garbo keep any YAML fields they do not know, so later changes (encryption, new card fields) and newer app versions can add fields without older versions destroying them.

Out of scope (separate tickets): encryption of sensitive cards (`card-encryption`), camera and barcode scanning (`card-capture`), rendering the code as a barcode and full-screen display (`card-display`), sharing owner keys (`owner-key-sharing`), and any sync.

## Capabilities

### New Capabilities
- `vault`: choosing, creating, opening and remembering the vault folder; the `.garbo/vault.yaml` file and format versioning; reloading after external changes; how unreadable files are reported.
- `owners`: owner folders and `owner.yaml`, the default owner, creating/editing/deleting owners and picking up owner folders added from outside.
- `cards`: card folders and `card.yaml`, card fields, identity documents, photos, creating/editing/deleting/moving cards, and the card list with its owner filter.

### Modified Capabilities

(none: there are no specs yet)

## Impact

- `sharedUI`: new domain model, YAML storage layer, view models and Compose screens; the template `App.kt` content, strings and drawables are replaced.
- New dependencies: a multiplatform YAML library (snakeyaml-engine-kmp), a multiplatform file library (kotlinx-io), and FileKit for folder/image picking, persistent folder access on Android (SAF), iOS (security-scoped bookmarks) and desktop, and photo compression. Ids use the Kotlin standard library's `Uuid`.
- `androidApp`, `iosApp`, `desktopApp`: platform setup for the pickers (FileKit initialization, iOS document-picker entitlements if needed).
- No existing data or users: no migration.
