package dev.mateuy.garbo.storage

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.startAccessingSecurityScopedResource
import io.github.vinceglb.filekit.stopAccessingSecurityScopedResource
import io.github.vinceglb.filekit.toKotlinxIoPath

// A folder picked outside the app sandbox (Files, iCloud Drive, a File Provider) is only
// reachable while its security-scoped access is held, so it is held while the vault is open.
actual fun openFolder(folder: PlatformFile): OpenFolder {
    val scoped = folder.startAccessingSecurityScopedResource()
    return OpenFolder(PathVaultFiles(folder.toKotlinxIoPath())) {
        if (scoped) folder.stopAccessingSecurityScopedResource()
    }
}
