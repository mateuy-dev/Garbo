package dev.mateuy.garbo.vault

import dev.mateuy.garbo.storage.VaultEntry
import dev.mateuy.garbo.storage.VaultFiles
import dev.mateuy.garbo.storage.VaultFilesException
import dev.mateuy.garbo.storage.tempNameFor
import dev.mateuy.garbo.storage.vaultPath
import dev.mateuy.garbo.vault.VaultLayout.BACK_PHOTO
import dev.mateuy.garbo.vault.VaultLayout.CARD_FILE
import dev.mateuy.garbo.vault.VaultLayout.FORMAT_VERSION
import dev.mateuy.garbo.vault.VaultLayout.FRONT_PHOTO
import dev.mateuy.garbo.vault.VaultLayout.GARBO_DIR
import dev.mateuy.garbo.vault.VaultLayout.OWNER_FILE
import dev.mateuy.garbo.vault.VaultLayout.VAULT_FILE

/** Why a vault operation was refused or failed. The UI turns each into a message. */
sealed class VaultError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class NotAVault : VaultError("The folder is not a Garbo vault")
    class AlreadyAVault : VaultError("The folder already contains a vault")
    class FolderNotEmpty : VaultError("The folder is not empty")
    class UnsupportedFormat(val version: Int) : VaultError("Vault format $version is newer than this app")
    class Unreadable(cause: Throwable) : VaultError("Could not read the vault: ${cause.message}", cause)
    class Unreachable(cause: Throwable?) : VaultError("The vault folder cannot be reached", cause)
    class BlankName : VaultError("A name is required")
    class OwnerHasCards : VaultError("The owner still has cards")
    class OwnerIsDefault : VaultError("The owner is the default owner")
    class NotFound(what: String) : VaultError("$what no longer exists")
    class Io(cause: VaultFilesException) : VaultError(cause.message ?: "File error", cause)
}

/** Something in the vault that could not be loaded as expected. Its files are left as they are. */
data class VaultProblem(val path: String, val kind: Kind, val detail: String? = null) {
    enum class Kind { MALFORMED, DUPLICATE_OWNER, DUPLICATE_CARD, RECOVERED }
}

/** Everything read from the vault in one scan. */
data class VaultSnapshot(
    val info: VaultInfo,
    val owners: List<Owner>,
    val cards: List<Card>,
    val problems: List<VaultProblem>,
) {
    fun owner(ownerId: String): Owner? = owners.firstOrNull { it.ownerId == ownerId }

    fun cardsOf(ownerId: String): List<Card> = cards.filter { it.ownerId == ownerId }

    /** The owner pre-selected for new cards: the configured default, or the first by name. */
    val defaultOwner: Owner?
        get() = info.defaultOwnerId?.let(::owner) ?: owners.minByOrNull { it.name.lowercase() }
}

enum class PhotoSide(val fileName: String) { FRONT(FRONT_PHOTO), BACK(BACK_PHOTO) }

sealed interface PhotoChange {
    data object Keep : PhotoChange
    data object Remove : PhotoChange

    /** [jpeg] is already normalized (see `normalizePhoto`). */
    class Replace(val jpeg: ByteArray) : PhotoChange
}

/** What the card editor saves. [cardId] is null for a new card. */
data class CardDraft(
    val cardId: String?,
    val ownerId: String,
    val name: String,
    val type: String = CardType.DEFAULT.id,
    val code: CardCode? = null,
    val notes: String? = null,
    val front: PhotoChange = PhotoChange.Keep,
    val back: PhotoChange = PhotoChange.Keep,
)

/**
 * The vault format on top of [VaultFiles]: scanning and every change to owners and cards.
 * Calls block and throw [VaultError].
 */
class Vault private constructor(private val files: VaultFiles) {

    companion object {
        /** Creates a new vault, with its default owner "Me", in an empty folder. */
        fun create(files: VaultFiles, defaultOwnerName: String = "Me"): Vault = guarded {
            if (files.exists(VAULT_FILE)) throw VaultError.AlreadyAVault()
            if (files.list("").any { !it.isHidden }) throw VaultError.FolderNotEmpty()
            val vault = Vault(files)
            val me = vault.writeNewOwner(defaultOwnerName, OwnerColors.palette.first())
            files.createDir(GARBO_DIR)
            val info = VaultInfo(FORMAT_VERSION, newId(), me.ownerId)
            files.writeBytesAtomic(VAULT_FILE, info.toYaml().encodeToByteArray())
            vault
        }

        /** Opens an existing vault, checking that this version can read it. */
        fun open(files: VaultFiles): Vault = guarded {
            Vault(files).also { it.readInfo() }
        }

        private inline fun <T> guarded(block: () -> T): T = try {
            block()
        } catch (e: VaultFilesException) {
            throw VaultError.Io(e)
        }
    }

    private fun readInfo(): VaultInfo {
        val text = readRecovering(GARBO_DIR, VAULT_FILE.substringAfter('/'), files.list(GARBO_DIR), null)
            ?: throw VaultError.NotAVault()
        val info = try {
            VaultInfo.parse(text)
        } catch (e: FormatException) {
            throw VaultError.Unreadable(e)
        }
        if (info.formatVersion > FORMAT_VERSION) throw VaultError.UnsupportedFormat(info.formatVersion)
        return info
    }

    /**
     * Reads [name] in [dir] given the folder's [entries]. If only the temporary file of an
     * interrupted write is there and it parses, it is moved into place and noted in [problems].
     */
    private fun readRecovering(
        dir: String,
        name: String,
        entries: List<VaultEntry>,
        problems: MutableList<VaultProblem>?,
    ): String? {
        val names = entries.map { it.name }.toSet()
        if (name in names) return files.readBytes(vaultPath(dir, name)).decodeToString()
        val temp = tempNameFor(name)
        if (temp !in names) return null
        val text = files.readBytes(vaultPath(dir, temp)).decodeToString()
        try {
            Yaml.load(text)
        } catch (e: FormatException) {
            return null
        }
        files.move(vaultPath(dir, temp), vaultPath(dir, name))
        problems?.add(VaultProblem(vaultPath(dir, name), VaultProblem.Kind.RECOVERED))
        return text
    }

    /** Scans the whole vault. Unreadable owners and cards are reported, not thrown. */
    fun load(): VaultSnapshot = guarded {
        val info = readInfo()
        val problems = mutableListOf<VaultProblem>()
        val owners = mutableListOf<Owner>()
        val cards = mutableListOf<Card>()
        val cardFolders = mutableMapOf<String, String>()

        for (ownerDir in files.list("").visibleFolders()) {
            val entries = files.list(ownerDir)
            val owner = try {
                readRecovering(ownerDir, OWNER_FILE, entries, problems)?.let { Owner.parse(it, ownerDir) }
            } catch (e: FormatException) {
                problems += VaultProblem(vaultPath(ownerDir, OWNER_FILE), VaultProblem.Kind.MALFORMED, e.message)
                null
            } ?: continue
            if (owners.any { it.ownerId == owner.ownerId }) {
                problems += VaultProblem(ownerDir, VaultProblem.Kind.DUPLICATE_OWNER)
                continue
            }
            owners += owner

            for (cardName in entries.visibleFolders()) {
                val cardDir = vaultPath(ownerDir, cardName)
                val cardEntries = files.list(cardDir)
                val card = try {
                    readRecovering(cardDir, CARD_FILE, cardEntries, problems)
                        ?.let { Card.parse(it, owner.ownerId, cardDir) }
                } catch (e: FormatException) {
                    problems += VaultProblem(vaultPath(cardDir, CARD_FILE), VaultProblem.Kind.MALFORMED, e.message)
                    null
                } ?: continue
                if (card.cardId in cardFolders) {
                    problems += VaultProblem(cardDir, VaultProblem.Kind.DUPLICATE_CARD, cardFolders[card.cardId])
                    continue
                }
                cardFolders[card.cardId] = cardDir
                val names = cardEntries.map { it.name }.toSet()
                cards += card.copy(hasFront = FRONT_PHOTO in names, hasBack = BACK_PHOTO in names)
            }
        }
        VaultSnapshot(info, owners, cards.sortedBy { it.name.lowercase() }, problems)
    }

    private fun List<VaultEntry>.visibleFolders(): List<String> =
        filter { it.isDirectory && !it.isHidden }.map { it.name }.sorted()

    // Owners

    fun createOwner(name: String, color: String): Owner = guarded { writeNewOwner(name, color) }

    private fun writeNewOwner(name: String, color: String): Owner {
        val cleanName = name.requireName()
        val folder = uniqueName(ownerFolderSlug(cleanName)) { files.exists(it) }
        val owner = Owner(newId(), cleanName, color, folder)
        files.createDir(folder)
        files.writeBytesAtomic(vaultPath(folder, OWNER_FILE), owner.toYaml().encodeToByteArray())
        return owner
    }

    /** Changes name and color only; the folder keeps its name. */
    fun updateOwner(owner: Owner, name: String, color: String): Unit = guarded {
        val cleanName = name.requireName()
        val current = readOwner(owner)
        val path = vaultPath(owner.folder, OWNER_FILE)
        files.writeBytesAtomic(path, current.copy(name = cleanName, color = color).toYaml().encodeToByteArray())
    }

    fun setDefaultOwner(owner: Owner): Unit = guarded {
        val info = readInfo()
        files.writeBytesAtomic(VAULT_FILE, info.copy(defaultOwnerId = owner.ownerId).toYaml().encodeToByteArray())
    }

    fun deleteOwner(owner: Owner): Unit = guarded {
        readOwner(owner)
        if (readInfo().defaultOwnerId == owner.ownerId) throw VaultError.OwnerIsDefault()
        val hasCards = files.list(owner.folder).visibleFolders().any { files.exists(vaultPath(owner.folder, it, CARD_FILE)) }
        if (hasCards) throw VaultError.OwnerHasCards()
        files.deleteRecursive(owner.folder)
    }

    /** Re-reads [owner] from disk, so that a rewrite keeps changes made since the last scan. */
    private fun readOwner(owner: Owner): Owner {
        val path = vaultPath(owner.folder, OWNER_FILE)
        if (!files.exists(path)) throw VaultError.NotFound("The owner ${owner.name}")
        return try {
            Owner.parse(files.readBytes(path).decodeToString(), owner.folder)
        } catch (e: FormatException) {
            throw VaultError.Unreadable(e)
        }.also { if (it.ownerId != owner.ownerId) throw VaultError.NotFound("The owner ${owner.name}") }
    }

    // Cards

    /**
     * Creates a card, or updates [existing]. Moving it to another owner moves its folder.
     * Returns the saved card as it is on disk now.
     */
    fun saveCard(draft: CardDraft, existing: Card?, owners: List<Owner>): Card = guarded {
        val name = draft.name.requireName()
        val owner = owners.firstOrNull { it.ownerId == draft.ownerId } ?: throw VaultError.NotFound("The owner")

        val base: Card
        val folder: String
        if (existing == null) {
            base = Card(cardId = newId(), name = name)
            folder = vaultPath(owner.folder, base.cardId)
            files.createDir(folder)
        } else {
            val current = readCard(existing)
            folder = if (existing.ownerId == owner.ownerId) {
                existing.folder
            } else {
                vaultPath(owner.folder, existing.folder.substringAfterLast('/')).also { files.move(existing.folder, it) }
            }
            base = current
        }

        val card = base.copy(
            name = name,
            type = draft.type,
            // A blank value means no code. Unknown fields of an existing code are kept.
            code = draft.code?.takeIf { it.value.isNotBlank() }?.let { code ->
                CardCode(code.format, code.value.trim(), base.code?.yaml.orEmpty())
            },
            notes = draft.notes?.takeIf { it.isNotBlank() },
            ownerId = owner.ownerId,
            folder = folder,
        )
        files.writeBytesAtomic(vaultPath(folder, CARD_FILE), card.toYaml().encodeToByteArray())
        applyPhoto(folder, PhotoSide.FRONT, draft.front)
        applyPhoto(folder, PhotoSide.BACK, draft.back)
        val names = files.list(folder).map { it.name }.toSet()
        card.copy(hasFront = FRONT_PHOTO in names, hasBack = BACK_PHOTO in names)
    }

    private fun applyPhoto(folder: String, side: PhotoSide, change: PhotoChange) {
        val path = vaultPath(folder, side.fileName)
        when (change) {
            PhotoChange.Keep -> Unit
            PhotoChange.Remove -> files.deleteRecursive(path)
            is PhotoChange.Replace -> files.writeBytesAtomic(path, change.jpeg)
        }
    }

    private fun readCard(card: Card): Card {
        val path = vaultPath(card.folder, CARD_FILE)
        if (!files.exists(path)) throw VaultError.NotFound("The card ${card.name}")
        return try {
            Card.parse(files.readBytes(path).decodeToString(), card.ownerId, card.folder)
        } catch (e: FormatException) {
            throw VaultError.Unreadable(e)
        }.also { if (it.cardId != card.cardId) throw VaultError.NotFound("The card ${card.name}") }
    }

    fun deleteCard(card: Card): Unit = guarded {
        readCard(card)
        files.deleteRecursive(card.folder)
    }

    /** The JPEG bytes of a card photo, or null if it has none. */
    fun readPhoto(card: Card, side: PhotoSide): ByteArray? = guarded {
        val path = vaultPath(card.folder, side.fileName)
        if (files.exists(path)) files.readBytes(path) else null
    }

    private fun String.requireName(): String = trim().ifEmpty { throw VaultError.BlankName() }
}
