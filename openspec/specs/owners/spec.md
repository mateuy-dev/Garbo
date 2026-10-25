# owners Specification

## Purpose
Owners are the people cards belong to. Each owner is a self-contained folder in the vault, so one owner's cards can be shared through an external tool by sharing that folder alone.

## Requirements

### Requirement: Owner folder
Each owner SHALL be a direct subfolder of the vault containing `owner.yaml` with `ownerId` (UUID), `name` and `color` (`#RRGGBB`). The owner's identity and display name SHALL come only from `owner.yaml`, never from the folder name.

#### Scenario: Folder renamed outside Garbo
- **WHEN** an owner folder is renamed by a sync tool
- **THEN** Garbo shows the same owner, with the name from `owner.yaml` and all its cards

### Requirement: Owner folder naming
When Garbo creates an owner, it SHALL name the folder after the owner's name at creation time, reduced to letters, digits and dashes, adding a numeric suffix if that folder already exists. Renaming the owner later SHALL NOT rename the folder.

#### Scenario: Create owner named "Anna López"
- **WHEN** the user creates an owner named "Anna López" and no folder `anna-lopez` exists
- **THEN** Garbo creates the folder `anna-lopez` with its `owner.yaml`

#### Scenario: Name already used by a folder
- **WHEN** the user creates an owner whose folder name `anna-lopez` already exists
- **THEN** Garbo creates `anna-lopez-2`

#### Scenario: Rename keeps the folder
- **WHEN** the user renames owner "Anna" to "Anna M."
- **THEN** only `name` in `owner.yaml` changes and the folder keeps its name

### Requirement: Default owner
The vault SHALL name one owner as default by its `ownerId` in `vault.yaml`. The default owner SHALL be pre-selected for new cards and the user SHALL be able to make any owner the default.

#### Scenario: Change the default owner
- **WHEN** the user marks "Anna" as default
- **THEN** `defaultOwner` in `vault.yaml` is Anna's `ownerId`
- **AND** new cards pre-select Anna

#### Scenario: Default owner missing
- **WHEN** `defaultOwner` names an owner that is not in the vault
- **THEN** new cards pre-select the first owner by name, and nothing is rewritten until the user picks a new default

### Requirement: Manage owners
The user SHALL be able to see all owners with their card count, create an owner (name required, color picked from a palette), and change an owner's name and color.

#### Scenario: Create an owner
- **WHEN** the user creates an owner "Pau" with a color
- **THEN** a new owner folder with `owner.yaml` exists and "Pau" appears in the owner list with 0 cards

#### Scenario: Empty name
- **WHEN** the user tries to save an owner with a blank name
- **THEN** Garbo refuses and asks for a name

### Requirement: Delete an owner
The user SHALL be able to delete an owner only when it has no cards and it is not the default owner. Deleting SHALL remove the owner's folder after the user confirms.

#### Scenario: Delete an empty owner
- **WHEN** the user deletes an owner without cards that is not the default, and confirms
- **THEN** its folder is removed from the vault

#### Scenario: Owner has cards
- **WHEN** the user tries to delete an owner that has cards
- **THEN** Garbo refuses and asks to move or delete its cards first

#### Scenario: Default owner
- **WHEN** the user tries to delete the default owner
- **THEN** Garbo refuses and asks to choose another default first

### Requirement: Owner folders added from outside
An owner folder copied into the vault by the user or a sync tool SHALL appear as an owner with its cards after the next reload, without any import step.

#### Scenario: Shared folder dropped in
- **WHEN** a folder with a valid `owner.yaml` and card folders is copied into the vault root and Garbo reloads
- **THEN** the owner and all its cards appear

### Requirement: Duplicate owner ids
If two owner folders have the same `ownerId`, Garbo SHALL load the one whose folder name sorts first and report the other as unreadable, without modifying either.

#### Scenario: Same owner copied twice
- **WHEN** folders `anna` and `anna-copy` both contain `owner.yaml` with the same `ownerId`
- **THEN** Garbo shows the owner from `anna` and reports `anna-copy` as a duplicate
