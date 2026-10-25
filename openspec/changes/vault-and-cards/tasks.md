# Tasks

## 1. Dependencies and scaffolding

- [x] 1.1 Add snakeyaml-engine-kmp, kotlinx-io-core and FileKit (dialogs-compose; confirm which FileKit module provides `compressImage`, likely `filekit-core`, pulled in by dialogs) to `gradle/libs.versions.toml` and `sharedUI/build.gradle.kts`, remove `coil-network-ktor`, opt in to `kotlin.uuid.ExperimentalUuidApi` if still needed; verify `./gradlew :sharedUI:compileKotlinJvm :sharedUI:compileAndroidMain :sharedUI:compileKotlinIosSimulatorArm64` succeeds
- [x] 1.2 Initialize FileKit in `AppActivity` and `desktopApp` `main.kt` (per FileKit setup docs) and verify `./gradlew :androidApp:assembleDebug :desktopApp:compileKotlin` succeeds

## 2. Storage seam

- [x] 2.1 Define `VaultFiles` (relative paths: list, exists, readBytes, writeBytesAtomic, createDir, deleteRecursive, moveDir) and an in-memory implementation in `commonTest`; verify with a `commonTest` contract test suite (write/read, atomic write leaves no temp file, move, recursive delete, listing excludes nothing)
- [x] 2.2 Implement `VaultFiles` for JVM with kotlinx-io (`.<name>.tmp` + `atomicMove`) and run the same contract suite against a temp directory in `jvmTest`; verify `./gradlew :sharedUI:jvmTest` passes
- [x] 2.3 Implement `VaultFiles` for iOS with kotlinx-io wrapped in security-scoped access on the root URL; verify the contract suite passes in `iosSimulatorArm64Test` against the simulator's temp directory
- [ ] 2.4 Implement `VaultFiles` for Android on a SAF tree URI (`DocumentsContract`, cached document ids, delete-then-rename replace, `moveDocument` on API 24+ and copy-then-delete on 23); verify with an instrumented or manual check on an emulator picking a folder in Downloads: create, overwrite, move and delete files and inspect them in the Files app

## 3. Format layer

- [x] 3.1 Add the YAML map round-trip (load ordered map, dump map) and mappers for `vault.yaml`, `owner.yaml` and `card.yaml` that keep the original map; verify `commonTest` cases: unknown fields survive an edit in place (specs/vault "Preserve unknown fields"), an unknown `type` loads as a regular card and is written back unchanged (specs/cards "Identity documents"), clearing the code removes `code`, blank names are rejected
- [x] 3.2 Add the owner folder slug function and UUID helpers; verify `commonTest` cases: "Anna López" → `anna-lopez`, collision → `anna-lopez-2`, all-symbol name → `owner`

## 4. Vault repository

- [x] 4.1 Implement `VaultRepository` create/open with `VaultState` (refuse non-empty folders ignoring hidden files, refuse existing vault, refuse newer format version, create default owner "Me"); verify `commonTest` against the in-memory `VaultFiles` covering every scenario of specs/vault "Create a vault" and "Open an existing vault"
- [x] 4.2 Implement the scan with `problems` (malformed files, duplicate owner/card ids by folder-name order, unknown folders ignored, `.card.yaml.tmp` recovery); verify `commonTest` covering specs/vault "Tolerate unreadable files", specs/owners "Duplicate owner ids", specs/cards "Duplicate card ids" and the recovery case from design.md
- [x] 4.3 Implement owner mutations (create, rename/recolor without moving the folder, set default, delete only when empty and not default) and default-owner fallback; verify `commonTest` covering every scenario of specs/owners
- [x] 4.4 Implement card mutations (create in owner folder named by `cardId`, edit, change owner by moving the folder, delete folder, set/remove front and back photos); verify `commonTest` covering specs/cards "Card folder", "Card fields", "Card code", "Change a card's owner" and "Delete a card"

## 5. Device state and photos

- [x] 5.1 Store the vault bookmark and display label with multiplatform-settings, reopen on launch, map lost access to the setup screen with a message, and implement close vault; verify `commonTest` with a fake settings store for specs/vault "Remember the vault" and "Switch vault", then manually relaunch the desktop app (`./gradlew :desktopApp:run`) and see the vault reopen
- [ ] 5.2 Wrap `FileKit.compressImage` in a `normalizePhoto(bytes)` helper (longest side ≤ 2048, JPEG q85, error on undecodable input), first checking aspect ratio, JPEG output, platform support and iOS HEIC, and adding a per-platform fallback only where a check fails; verify a `jvmTest` that a 4000×3000 PNG becomes a 2048×1536 JPEG and a text file fails, and manually import a photo (a HEIC one on iOS) on Android and iOS

## 6. UI

- [x] 6.1 Replace the template `App.kt` with navigation3 setup, `AppGraph`, the `Setup` screen and the root `LifecycleResumeEffect` reload; remove template strings and drawables; verify on desktop: first launch shows Create/Open, cancelling the picker stays on setup, creating in an empty folder opens an empty list
- [x] 6.2 Build `CardList` (sorted by name, owner filter chips with "All", owner color, thumbnail, empty state, refresh action, problems banner); verify on desktop with a vault prepared by hand: filter by owner, add a card folder in a file manager and refresh, break a `card.yaml` and see the banner
- [x] 6.3 Build `CardDetail` and `CardEdit` (owner pre-selected from the filter or default, identity-document switch, code value only, photo import/replace/remove, discard writes nothing, delete with confirmation); verify on desktop every scenario of specs/cards "Create and edit cards", "Card photos" and "Delete a card", inspecting the files on disk
- [x] 6.4 Build `Owners` (list with counts, create/edit dialog with palette, set default, delete refusals) and `Settings` (vault location, close vault); verify on desktop every scenario of specs/owners "Manage owners", "Default owner" and "Delete an owner"
- [ ] 6.5 Replace card types with the identity-document switch and remove the format picker (model, editor, list/detail badge, labels, strings; `codabar` added, `text` removed; format dropped when the value changes); verify the format and vault tests for the new scenarios in specs/cards "Identity documents" and "Card code", and update the UI tests

## 7. Integration

- [x] 7.1 Run `./gradlew :sharedUI:allTests :androidApp:assembleDebug :desktopApp:compileKotlin` and verify all pass
- [ ] 7.2 On an Android emulator: create a vault in a picked folder, add owners and cards with photos, kill and relaunch the app, and verify the vault reopens and files match the layout in the ticket
- [ ] 7.3 On the iOS simulator: open the vault created in 7.2 copied into the simulator's Files (or create one), edit a card, background and foreground the app after adding a card folder externally, and verify it appears
- [x] 7.4 Copy one owner folder from one vault into another on desktop and verify the owner and its cards appear after refresh (specs/owners "Owner folders added from outside")

## Workflow follow-up

- Fill the ticket's Acceptance Criteria and Work Log, then run `safanoria-cli finish vault-and-cards`.
- Archive the change (`/opsx:archive vault-and-cards`) on this branch so the specs merge with the code.
