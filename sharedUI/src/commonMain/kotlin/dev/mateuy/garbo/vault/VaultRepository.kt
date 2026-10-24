package dev.mateuy.garbo.vault

import dev.mateuy.garbo.storage.OpenFolder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface VaultState {
    /** No vault is open. [error] says why the last attempt to open one failed, if it did. */
    data class Closed(val error: VaultError? = null) : VaultState

    data object Loading : VaultState

    /**
     * [location] is a readable label of the vault folder. [scan] counts the scans since the
     * vault was opened, so that views of file content (photos) know when to read it again.
     */
    data class Open(val location: String, val snapshot: VaultSnapshot, val scan: Int = 0) : VaultState
}

/**
 * The open vault and its latest scan. Every change is written to disk first and then the whole
 * vault is scanned again, so [state] always shows what is in the folder.
 */
class VaultRepository(private val dispatcher: CoroutineDispatcher = Dispatchers.IO) {
    private val _state = MutableStateFlow<VaultState>(VaultState.Closed())
    val state: StateFlow<VaultState> = _state.asStateFlow()

    private val mutex = Mutex()
    private var folder: OpenFolder? = null
    private var vault: Vault? = null
    private var location: String = ""
    private var scans = 0

    /** Creates a vault in [folder] and opens it. Returns the error if it was refused. */
    suspend fun create(folder: OpenFolder, location: String): VaultError? =
        openWith(folder, location) { Vault.create(folder.files) }

    /** Opens the vault in [folder]. Returns the error if it cannot be opened. */
    suspend fun open(folder: OpenFolder, location: String): VaultError? =
        openWith(folder, location) { Vault.open(folder.files) }

    private suspend fun openWith(folder: OpenFolder, location: String, open: () -> Vault): VaultError? =
        mutex.withLock {
            closeLocked()
            _state.value = VaultState.Loading
            try {
                val opened = withContext(dispatcher) { open() }
                val snapshot = withContext(dispatcher) { opened.load() }
                this.folder = folder
                this.vault = opened
                this.location = location
                scans = 0
                _state.value = VaultState.Open(location, snapshot, scans)
                null
            } catch (e: VaultError) {
                folder.close()
                _state.value = VaultState.Closed(e)
                e
            }
        }

    /** Scans the vault again, to pick up changes made by other apps. */
    suspend fun reload() = mutex.withLock { reloadLocked() }

    private suspend fun reloadLocked() {
        val vault = vault ?: return
        try {
            val snapshot = withContext(dispatcher) { vault.load() }
            _state.value = VaultState.Open(location, snapshot, ++scans)
        } catch (e: VaultError) {
            closeLocked()
            _state.value = VaultState.Closed(e)
        }
    }

    /**
     * Runs [change] on the open vault with its latest scan, then scans again, even if [change]
     * failed (the folder may have changed under us). Throws the [VaultError] of a failed change.
     */
    suspend fun <T> change(change: Vault.(VaultSnapshot) -> T): T = mutex.withLock {
        val vault = vault ?: throw VaultError.NotFound("The vault")
        val snapshot = (_state.value as? VaultState.Open)?.snapshot ?: throw VaultError.NotFound("The vault")
        try {
            withContext(dispatcher) { vault.change(snapshot) }
        } finally {
            reloadLocked()
        }
    }

    suspend fun readPhoto(card: Card, side: PhotoSide): ByteArray? {
        val vault = vault ?: return null
        return withContext(dispatcher) { vault.readPhoto(card, side) }
    }

    /** Closes the vault without touching its files. [error] is shown as the reason, if given. */
    suspend fun close(error: VaultError? = null) = mutex.withLock {
        closeLocked()
        _state.value = VaultState.Closed(error)
    }

    private fun closeLocked() {
        folder?.close()
        folder = null
        vault = null
    }
}
