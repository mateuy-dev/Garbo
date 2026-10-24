# Design

## Context

The repository is the Compose Multiplatform template: one `sharedUI` module (Android library, JVM, iosArm64, iosSimulatorArm64) with a demo `App.kt`, consumed by `androidApp`, `desktopApp` and `iosApp`. It already depends on navigation3, lifecycle-viewmodel, kotlinx-serialization, Coil, multiplatform-settings and kotlinx-datetime; nothing uses them yet. Android `minSdk` is 23.

The hard constraint is that the vault is a user-picked folder that other tools sync. On Android that means a Storage Access Framework (SAF) document tree, not a file path; on iOS a security-scoped URL (possibly a File Provider such as iCloud Drive or Dropbox); on desktop a plain path. The file format and behavior are in `specs/vault`, `specs/owners` and `specs/cards`.

## Goals / Non-Goals

**Goals:**
- One storage seam (`VaultFiles`) so that all vault logic and its tests live in `commonMain`/`commonTest`, and only folder access is per-platform.
- A format layer that keeps unknown YAML fields, ready for `card-encryption` (`secret.yaml.enc`, `keyId`) without changes to the loader.
- Simple whole-vault reload: the vault is small (tens to hundreds of cards), so a full rescan is cheap and avoids cache-invalidation bugs with external sync.

**Non-Goals:**
- File watching. Reload happens on resume and on refresh (see specs/vault).
- Conflict resolution between devices: last write wins per file; sync tools' conflict copies (e.g. `card (conflicted copy).yaml`) are ignored like any unknown file.
- Keeping YAML comments or formatting when Garbo rewrites a file.
- Multiple open vaults at once.

## Decisions

### Storage seam: `VaultFiles` over relative paths
A small interface in `commonMain` addressed by vault-relative paths (`"anna/3f2c.../card.yaml"`): `list(dir)`, `exists`, `readBytes`, `writeBytesAtomic`, `createDir`, `deleteRecursive`, `moveDir`. Implementations:
- **JVM and iOS**: `kotlinx-io` `SystemFileSystem` on the absolute root path; atomic write = write `.<name>.tmp` then `atomicMove`. On iOS every session wraps access in `startAccessingSecurityScopedResource` on the root URL.
- **Android**: `DocumentsContract` / `ContentResolver` on the persisted tree URI, resolving children by name with a small per-reload cache of document ids. `moveDocument` on API 24+, copy-then-delete on API 23.
- **Tests**: an in-memory implementation in `commonTest`.

Alternative: FileKit's `PlatformFile` everywhere. Rejected as the storage layer because its directory-tree writes on SAF are thinner than we need (atomic replace, move) and it would put a third-party abstraction under every write; we still use FileKit for the pickers (below).

### Pickers and remembered access: FileKit
FileKit (`io.github.vinceglb:filekit-dialogs-compose`) provides the folder picker and image picker on Android, iOS and desktop, and bookmark data for persistent access (`takePersistableUriPermission` on Android, security-scoped bookmarks on iOS, the path on desktop). The bookmark (base64) and a display label are stored with multiplatform-settings, never in the vault (specs/vault "Remember the vault"). Alternative: hand-written `expect/actual` pickers; more code for the same result. It also provides image compression (see Photos).

### YAML: snakeyaml-engine-kmp with a map round-trip
Files are loaded into an ordered `Map<String, Any?>`; a mapper reads the known fields into the model (`VaultInfo`, `Owner`, `Card`) and keeps the original map. Saving copies the original map, overwrites or removes only the known keys and dumps it, so unknown fields survive in their original order (specs/vault "Preserve unknown fields"). Alternative: kaml with `@Serializable` classes; typed, but unknown keys are either an error or dropped, and keeping them needs a custom tree merge anyway.

Card `type` and `code.format` are kept as raw strings in the model plus an enum view, so an unknown `type: passport` is shown as `other` but written back unchanged unless edited.

### Identity and naming
`kotlin.uuid.Uuid.random()` (stdlib) for vault, owner and card ids. Card folders are named by the `cardId`; owner folders by a slug (lowercase ASCII after a small Latin diacritics table, other characters to `-`, collapsed, fallback `owner`, `-2`, `-3`... on collision). Identity is always read from the YAML, so a folder name is only a hint.

### Loading model
`VaultRepository` (common) owns a `StateFlow<VaultState>`: `Closed`, `Loading`, `Open(vaultInfo, owners, cards, problems)`, `Error(reason)`. A reload scans root → owner folders → card folders, collects `problems` (malformed, duplicate ids, recovered temp files) instead of throwing, and replaces the state. Mutations (save card, move, delete, owner edits, default owner) write files then trigger a reload, so the UI always shows what is on disk. Photos are not loaded during the scan; only their presence is recorded and bytes are read lazily for display. All I/O runs on `Dispatchers.IO` (`Dispatchers.Default` on iOS where IO is unavailable to common code it maps the same).

### Atomic writes and recovery
Writes go to a hidden `.card.yaml.tmp` sibling, then replace the target. SAF has no atomic replace, so Android does delete-then-rename; if the process dies in between, the folder holds only the temp file. The loader therefore treats a folder with `.card.yaml.tmp` and no `card.yaml` as recoverable: it renames the temp file into place (only if it parses) and records the recovery in `problems`. Temp files next to a valid file are ignored and cleaned on the next write.

### Photos
Imported images go through `FileKit.compressImage` (bytes, JPEG, quality 85, `maxWidth`/`maxHeight` 2048), already available through the FileKit dependency used for the pickers, so there is no per-platform image code. The first photo task checks that it (a) keeps the aspect ratio, (b) can output JPEG, (c) works on Android, iOS and JVM, and (d) decodes HEIC on iOS. Any platform where a check fails gets an `expect/actual` fallback for that platform only: Android `BitmapFactory`/`Bitmap.compress`, iOS `UIImage`/`UIImageJPEGRepresentation`, JVM Skia. Undecodable input is reported as an error. Display uses Coil with the `ByteArray` read from `VaultFiles`. The unused `coil-network-ktor` dependency is removed (Garbo makes no network requests).

### UI structure
navigation3 with screens: `Setup`, `CardList` (owner filter chips, refresh action, problems banner), `CardDetail`, `CardEdit` (create/edit; owner, type, code format pickers; photo import/remove), `Owners` (list with counts, create/edit dialog, set default, delete) and `Settings` (vault location, close vault). One ViewModel per screen, created from a small hand-written `AppGraph` holding the repository and settings; no DI framework. Reload on resume uses `LifecycleResumeEffect` at the app root. The template demo content, strings and drawables are removed; theme files are kept.

## Risks / Trade-offs

- [Sync tool rewrites or moves files mid-scan] → The scan tolerates disappearing entries (treated as absent) and the next resume/refresh converges.
- [Two devices edit the same card] → Last write wins on that one `card.yaml`; the layout keeps conflicts to one card. Conflict copies are ignored. Accepted for this change.
- [SAF performance: listing children per folder is slow on large trees] → One query per folder per reload with cached document ids; vault size is small. Revisit if reload exceeds ~1 s with 500 cards.
- [iOS File Providers (Dropbox, Drive) may not keep files offline or may not support folder bookmarks well] → Test with iCloud Drive and local "On My iPhone" first; document provider limitations; the setup screen's error path covers lost access.
- [Moving a card folder between owners on SAF API 23 is copy-then-delete, not atomic] → Copy fully, verify `card.yaml` exists in the destination, then delete the source; an interruption leaves a duplicate, which the duplicate-id rule handles.
- [Dropping YAML comments on rewrite] → Accepted; files are written by Garbo and rarely hand-edited.

## Migration Plan

New app, no existing data: format version starts at `1`. The template UI is replaced in place. Rollback is reverting the branch.

## Open Questions

- Exact owner color palette (8–10 colors) and the default color for "Me": a UI detail, decided during implementation.
- Whether the iOS app also needs `UIFileSharingEnabled`/`LSSupportsOpeningDocumentsInPlace` to let users pick a vault inside "On My iPhone/Garbo": checked while wiring the iOS picker.
