# Garbo

Kotlin Multiplatform app (mobile + desktop) to manage cards, from supermarket loyalty cards to national IDs. No server for now: data stays on the device. Each card may store a name, two photos, a code and an owner.

Checkouts are named after their branch: this folder is `master` (not `Garbo`), and ticket worktrees are its siblings named by ticket id.

How the app behaves is defined with OpenSpec in `openspec/` (specs and changes). Work is tracked as Safanoria tickets in `tickets/<id>.md` (settings: `safanoria.yaml`). The ticket id is also the branch name. When creating a ticket, or working on a ticket's branch, use the `safanoria` skill.
