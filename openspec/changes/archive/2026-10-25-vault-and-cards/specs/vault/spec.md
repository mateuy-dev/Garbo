# Spec Delta

## Purpose

The vault is the plain folder, picked by the user, where Garbo keeps all owners and cards as readable files, so an external tool can sync it while Garbo only reads and writes files.

## ADDED Requirements

### Requirement: First-run vault choice
When no vault is remembered on the device, Garbo SHALL show a setup screen offering to create a new vault or open an existing one, each by picking a folder. No other screen SHALL be reachable until a vault is open.

#### Scenario: First launch
- **WHEN** the user opens Garbo for the first time on a device
- **THEN** the setup screen offers "Create vault" and "Open vault"

#### Scenario: Picker cancelled
- **WHEN** the user cancels the folder picker
- **THEN** Garbo stays on the setup screen and nothing is written

### Requirement: Create a vault
Creating a vault in a picked folder SHALL write `.garbo/vault.yaml` with the format version (`1`), a new random vault id and the id of a default owner, and SHALL create that default owner named "Me". Creation SHALL be refused if the folder already contains a vault, or contains anything other than hidden files.

#### Scenario: Create in an empty folder
- **WHEN** the user creates a vault in an empty folder
- **THEN** the folder contains `.garbo/vault.yaml` and one owner folder whose `owner.yaml` is named "Me"
- **AND** `vault.yaml` names that owner as `defaultOwner`
- **AND** the card list opens, empty

#### Scenario: Folder already holds a vault
- **WHEN** the user picks a folder that contains `.garbo/vault.yaml` to create a vault
- **THEN** Garbo offers to open it instead and writes nothing

#### Scenario: Folder is not empty
- **WHEN** the user picks a folder with visible files or folders and no vault
- **THEN** Garbo refuses with a message asking for an empty folder and writes nothing

### Requirement: Open an existing vault
Opening a folder SHALL succeed only if it contains a readable `.garbo/vault.yaml` whose format version Garbo supports. Garbo SHALL then show the vault's owners and cards.

#### Scenario: Open a synced vault
- **WHEN** the user opens a folder containing a vault created on another device
- **THEN** the card list shows that vault's owners and cards

#### Scenario: Not a vault
- **WHEN** the user opens a folder without `.garbo/vault.yaml`
- **THEN** Garbo explains that the folder is not a Garbo vault and stays on the setup screen

#### Scenario: Newer format
- **WHEN** `vault.yaml` has a format version higher than the one Garbo supports
- **THEN** Garbo refuses to open it, asks the user to update Garbo, and writes nothing

### Requirement: Remember the vault
Garbo SHALL remember the open vault on the device, including any permission the platform needs to access it again, and reopen it on the next launch. This device state SHALL NOT be written into the vault folder.

#### Scenario: Relaunch
- **WHEN** the user closes and reopens Garbo
- **THEN** the same vault opens without asking for the folder again

#### Scenario: Vault no longer reachable
- **WHEN** the remembered folder was deleted, moved or its access revoked
- **THEN** Garbo shows the setup screen with a message that the vault could not be opened

### Requirement: Switch vault
The user SHALL be able to close the current vault from the settings and return to the setup screen. Closing SHALL NOT change any file in the vault.

#### Scenario: Close the vault
- **WHEN** the user chooses "Close vault" in the settings
- **THEN** the setup screen is shown and the vault folder is unchanged

### Requirement: Reload external changes
Garbo SHALL re-read the vault when it returns to the foreground and when the user asks to refresh, so that files added, changed or removed by another tool appear without restarting.

#### Scenario: Card added by sync
- **WHEN** a sync tool adds a card folder while Garbo is in the background, and the user returns to Garbo
- **THEN** the new card appears in the list

#### Scenario: Card removed by sync
- **WHEN** a card folder is deleted outside Garbo and the user refreshes
- **THEN** the card disappears from the list

### Requirement: Tolerate unreadable files
A missing or malformed `owner.yaml` or `card.yaml` SHALL NOT prevent the rest of the vault from loading. Garbo SHALL skip that owner or card, SHALL NOT modify or delete its files, and SHALL tell the user how many items could not be read and where.

#### Scenario: Malformed card file
- **WHEN** one `card.yaml` contains invalid YAML
- **THEN** every other card loads
- **AND** Garbo reports one unreadable card with its folder path
- **AND** the malformed file is left untouched

#### Scenario: Unknown folders
- **WHEN** the vault root contains a folder without `owner.yaml`, or an owner folder contains a folder without `card.yaml`
- **THEN** Garbo ignores it silently and leaves it untouched

### Requirement: Preserve unknown fields
When Garbo rewrites `vault.yaml`, `owner.yaml` or `card.yaml`, it SHALL keep every field it does not understand, with its value, so that files written by newer versions or later features are not damaged.

#### Scenario: Edit a card written by a newer version
- **WHEN** `card.yaml` contains a field `expires: 2030-01-01` unknown to this version and the user renames the card
- **THEN** the rewritten `card.yaml` has the new name and still contains `expires: 2030-01-01`

### Requirement: Safe writes
Each file Garbo writes SHALL be written completely or not at all: an interrupted write SHALL NOT leave a truncated YAML file in place of the previous one.

#### Scenario: Interrupted save
- **WHEN** the app is killed while saving a card
- **THEN** `card.yaml` contains either the previous or the new content, and is valid YAML

### Requirement: Stable identity
The vault, every owner and every card SHALL have a random identifier (UUID) that never changes after creation and is independent of any display name or folder name.

#### Scenario: Ids stay after rename
- **WHEN** the user renames an owner or a card
- **THEN** its id in the YAML file is unchanged
