# cards Specification

## Purpose
Cards are the things Garbo keeps, from supermarket loyalty cards to national ids. Each card is a folder inside its owner's folder holding its data and optional photos, so a sync conflict only ever touches one card.

## Requirements

### Requirement: Card folder
Each card SHALL be a subfolder of exactly one owner folder, named by the card's id at creation, containing `card.yaml` and optionally `front.jpg` and `back.jpg`. A card's owner SHALL be the owner whose folder contains it. Renaming a card SHALL NOT rename or move its folder.

#### Scenario: New card on disk
- **WHEN** the user saves a new card for owner "Me"
- **THEN** a folder named by a new UUID exists inside Me's folder with a `card.yaml`

#### Scenario: Rename keeps the folder
- **WHEN** the user renames a card
- **THEN** only `name` in `card.yaml` changes and the folder stays where it was

### Requirement: Card fields
`card.yaml` SHALL hold `cardId` (UUID), `name` (required, non-blank), an optional `type`, an optional `code` with a `value` and an optional `format`, and optional `notes`. A card SHALL be valid without a code and without photos.

#### Scenario: Card with only a name
- **WHEN** the user saves a card named "Gym" with no code, notes or photos
- **THEN** the card is saved and shown in the list

#### Scenario: Blank name
- **WHEN** the user tries to save a card with a blank name
- **THEN** Garbo refuses and asks for a name

### Requirement: Identity documents
A card SHALL be marked as an identity document or not, with `type: id` in `card.yaml`; a card that is not one has no `type`. New cards are not identity documents. Any other `type` value SHALL load as a regular card and be kept unless the user marks the card as an identity document or unmarks it.

#### Scenario: Mark an id
- **WHEN** the user turns on "Identity document" on a card and saves
- **THEN** `card.yaml` has `type: id`
- **AND** the card shows an "ID" badge in the list and on its detail

#### Scenario: Unknown type from a newer version
- **WHEN** a `card.yaml` has `type: passport` and the user edits only its notes
- **THEN** the card shows as a regular card and the rewritten file still has `type: passport`

### Requirement: Card code
The user SHALL enter only the code's non-blank value; clearing it removes the code. `code` MAY also hold a `format` (`qr`, `code128`, `code39`, `codabar`, `ean13`, `ean8`, `upca`, `pdf417`, `aztec`, `datamatrix`) written by other features such as scanning; none means unknown. Garbo SHALL keep the code's other fields while the value is unchanged and SHALL remove `format` when the value changes. The detail SHALL show the value, not the format.

#### Scenario: Add a code
- **WHEN** the user enters value `2401234567890` and saves
- **THEN** `card.yaml` has `code` with `value: "2401234567890"` and no `format`
- **AND** the card detail shows `2401234567890`

#### Scenario: Keep a scanned format
- **WHEN** a card has `code` with `format: ean13` and the user edits only its notes
- **THEN** the rewritten file still has `format: ean13`

#### Scenario: Changing the value drops the format
- **WHEN** a card has `code` with `format: ean13` and the user changes the value and saves
- **THEN** `code` has the new value and no `format`

#### Scenario: Remove the code
- **WHEN** the user clears the code value and saves
- **THEN** `card.yaml` has no `code` field

### Requirement: Card photos
A card SHALL have at most one front and one back photo, each optional, added by importing an image file. Garbo SHALL store each as a JPEG (`front.jpg`, `back.jpg`) whose longest side is at most 2048 px, and SHALL let the user replace or remove each photo.

#### Scenario: Import a front photo
- **WHEN** the user picks a 4000×3000 PNG as the front photo and saves
- **THEN** the card folder has `front.jpg`, a JPEG of 2048×1536

#### Scenario: Remove the back photo
- **WHEN** the user removes the back photo and saves
- **THEN** `back.jpg` is deleted from the card folder

#### Scenario: Unsupported file
- **WHEN** the user picks a file that cannot be decoded as an image
- **THEN** Garbo shows an error and the card's photos are unchanged

### Requirement: Card list
The main screen SHALL list all cards of the vault sorted by name, each showing its name, an "ID" badge for identity documents, its owner (name and color) and front photo thumbnail when present. The list SHALL be filterable by owner, with "All" as the initial filter.

#### Scenario: Filter by owner
- **WHEN** the user selects owner "Anna" in the filter
- **THEN** only cards in Anna's folder are listed

#### Scenario: Empty vault
- **WHEN** the vault has no cards
- **THEN** the list shows an empty state with an action to add a card

### Requirement: Create and edit cards
The user SHALL be able to create a card from the list and edit any card from its detail screen. The owner field SHALL be pre-selected with the active owner filter if one is set, otherwise with the default owner. Discarding the form SHALL write nothing.

#### Scenario: Create while filtering
- **WHEN** the list is filtered by "Pau" and the user adds a card
- **THEN** the owner field is pre-selected with "Pau"

#### Scenario: Discard changes
- **WHEN** the user edits a card and leaves without saving
- **THEN** the card's files are unchanged

### Requirement: Change a card's owner
Changing a card's owner SHALL move the whole card folder, with its photos, into the new owner's folder, keeping the folder name and `cardId`.

#### Scenario: Move a card to Anna
- **WHEN** the user changes a card's owner from "Me" to "Anna" and saves
- **THEN** the card folder is inside Anna's folder and no longer inside Me's
- **AND** the card shows Anna as owner

### Requirement: Delete a card
The user SHALL be able to delete a card after confirming. Deleting SHALL remove the card's folder with all its files.

#### Scenario: Delete
- **WHEN** the user deletes a card and confirms
- **THEN** its folder is removed and the card disappears from the list

### Requirement: Duplicate card ids
If two card folders in the vault have the same `cardId`, Garbo SHALL load the first by owner folder name and card folder name, and report the other as a duplicate, without modifying either.

#### Scenario: Card copied into another owner
- **WHEN** the same card folder exists in both `anna` and `me`
- **THEN** Garbo shows the card from `anna` and reports the one in `me` as a duplicate
