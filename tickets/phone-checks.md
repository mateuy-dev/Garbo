---
id: phone-checks
type: maintenance
title: Phone checks for the first tickets
status: backlog
priority: medium
size: S
created: 2026-10-25
updated: 2026-10-25
---

## Objective

Run the Android and iPhone checks that were left open when the first tickets were done on desktop, in one session on the Mac (192.168.1.222, Herdr), with an Android emulator and the iOS simulator or a device.

From vault-and-cards (merged with these checks open; desktop and the shared tests on the iOS simulator passed):
- Android SAF vault: create a vault in a picked folder, open it again after restarting the app, add/edit/move/delete cards and owners, and see changes made from a file manager after resume or refresh (tasks 2.4 and 7.2).
- Photo import on Android and iOS, including a HEIC photo on iOS; check the stored front.jpg is a JPEG of at most 2048 px with the right orientation (task 5.2). FileKit.compressImage is used; add a per-platform fallback only where it falls short.
- iOS app end to end in the simulator: pick a folder (Files / iCloud Drive), reopen it on launch, full card and owner flow (task 7.3). Info.plist may need UIFileSharingEnabled / LSSupportsOpeningDocumentsInPlace.

card-display and card-encryption also wait for this session for their own phone checks (see their tickets).

## Acceptance Criteria

## Work Log

