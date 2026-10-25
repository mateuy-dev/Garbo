package dev.mateuy.garbo.storage

import kotlin.random.Random
import kotlin.test.AfterTest
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory

class PathVaultFilesTest : VaultFilesContract() {
    private val roots = mutableListOf<Path>()

    override fun emptyVault(): VaultFiles {
        val root = Path(SystemTemporaryDirectory, "garbo-test-${Random.nextLong().toULong()}")
        SystemFileSystem.createDirectories(root)
        roots += root
        return PathVaultFiles(root)
    }

    @AfterTest
    fun cleanUp() {
        roots.forEach { PathVaultFiles(it).deleteRecursive("") }
    }
}
