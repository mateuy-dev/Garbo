package dev.mateuy.garbo.storage

import io.github.vinceglb.filekit.AndroidFile
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.context
import io.github.vinceglb.filekit.toKotlinxIoPath

actual fun openFolder(folder: PlatformFile): OpenFolder = when (val file = folder.androidFile) {
    is AndroidFile.UriWrapper -> OpenFolder(SafVaultFiles(FileKit.context.contentResolver, file.uri))
    is AndroidFile.FileWrapper -> OpenFolder(PathVaultFiles(folder.toKotlinxIoPath()))
}
