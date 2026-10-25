package dev.mateuy.garbo.storage

import io.github.vinceglb.filekit.PlatformFile

/** A picked vault folder opened for reading and writing, until [close]. */
class OpenFolder(val files: VaultFiles, private val onClose: () -> Unit = {}) {
    fun close() = onClose()
}

/** Opens [folder] (as returned by the folder picker or a bookmark) as [VaultFiles]. */
expect fun openFolder(folder: PlatformFile): OpenFolder
