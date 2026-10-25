package dev.mateuy.garbo.storage

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.toKotlinxIoPath

actual fun openFolder(folder: PlatformFile): OpenFolder = OpenFolder(PathVaultFiles(folder.toKotlinxIoPath()))
