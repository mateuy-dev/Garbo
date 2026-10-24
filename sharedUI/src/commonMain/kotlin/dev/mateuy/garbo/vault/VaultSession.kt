package dev.mateuy.garbo.vault

import com.russhwolf.settings.Settings
import dev.mateuy.garbo.storage.OpenFolder
import dev.mateuy.garbo.storage.openFolder
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.bookmarkData
import io.github.vinceglb.filekit.fromBookmarkData
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.path
import kotlin.io.encoding.Base64

/** A folder the user picked or Garbo remembered: how to find it again, and how to open it. */
class PickedFolder(val bookmark: ByteArray, val label: String, val open: () -> OpenFolder)

/** The vault remembered on this device. Stored outside the vault folder. */
data class StoredVault(val bookmark: ByteArray, val label: String)

interface VaultStore {
    fun load(): StoredVault?
    fun save(vault: StoredVault)
    fun clear()
}

class SettingsVaultStore(private val settings: Settings) : VaultStore {
    override fun load(): StoredVault? {
        val bookmark = settings.getStringOrNull(BOOKMARK) ?: return null
        return StoredVault(Base64.decode(bookmark), settings.getString(LABEL, ""))
    }

    override fun save(vault: StoredVault) {
        settings.putString(BOOKMARK, Base64.encode(vault.bookmark))
        settings.putString(LABEL, vault.label)
    }

    override fun clear() {
        settings.remove(BOOKMARK)
        settings.remove(LABEL)
    }

    private companion object {
        const val BOOKMARK = "vault.bookmark"
        const val LABEL = "vault.label"
    }
}

/**
 * Opening, remembering and closing the vault on this device, on top of [VaultRepository].
 * [reopen] turns a stored bookmark back into a folder; it throws if the folder is gone.
 */
class VaultSession(
    val repository: VaultRepository,
    private val store: VaultStore,
    private val reopen: (StoredVault) -> PickedFolder,
) {
    /**
     * Reopens the remembered vault, if any and if no vault is open yet. If it cannot be opened
     * it is forgotten and the repository is left closed with the reason.
     */
    suspend fun restore() {
        if (repository.state.value !is VaultState.Closed) return
        val stored = store.load() ?: return
        val error = try {
            val folder = reopen(stored)
            openWith(folder) { repository.open(it, folder.label) }
        } catch (e: Exception) {
            VaultError.Unreachable(e).also { repository.close(it) }
        }
        if (error != null) store.clear()
    }

    suspend fun create(folder: PickedFolder): VaultError? = openAndRemember(folder) { repository.create(it, folder.label) }

    suspend fun open(folder: PickedFolder): VaultError? = openAndRemember(folder) { repository.open(it, folder.label) }

    private suspend inline fun openAndRemember(folder: PickedFolder, open: (OpenFolder) -> VaultError?): VaultError? {
        val error = openWith(folder, open)
        if (error == null) store.save(StoredVault(folder.bookmark, folder.label))
        return error
    }

    /** Opening the folder itself can fail on some platforms (access revoked, provider gone). */
    private suspend inline fun openWith(folder: PickedFolder, open: (OpenFolder) -> VaultError?): VaultError? {
        val opened = try {
            folder.open()
        } catch (e: Exception) {
            return VaultError.Unreachable(e).also { repository.close(it) }
        }
        return open(opened)
    }

    /** Closes the vault and forgets it on this device. The vault folder is not changed. */
    suspend fun close() {
        store.clear()
        repository.close()
    }
}

/** A folder from the picker, with persistent access taken so it can be reopened on next launch. */
suspend fun PlatformFile.toPickedFolder(): PickedFolder {
    val bookmark = bookmarkData().bytes
    return PickedFolder(bookmark, folderLabel()) { openFolder(this) }
}

/** Turns a stored bookmark back into a folder. Throws if it cannot be resolved. */
fun reopenStoredFolder(stored: StoredVault): PickedFolder {
    val folder = PlatformFile.fromBookmarkData(stored.bookmark)
    return PickedFolder(stored.bookmark, stored.label.ifEmpty { folder.folderLabel() }) { openFolder(folder) }
}

private fun PlatformFile.folderLabel(): String = if (path.startsWith("content://")) name else path
