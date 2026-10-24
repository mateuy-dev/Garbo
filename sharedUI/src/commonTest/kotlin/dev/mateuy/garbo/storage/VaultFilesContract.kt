package dev.mateuy.garbo.storage

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Behavior every [VaultFiles] implementation must have. Subclasses provide an empty vault. */
abstract class VaultFilesContract {
    abstract fun emptyVault(): VaultFiles

    private fun VaultFiles.names(dir: String) = list(dir).map { it.name }.toSet()

    @Test
    fun writeThenRead() {
        val files = emptyVault()
        files.createDir("a")
        files.writeBytesAtomic("a/card.yaml", "name: Gym".encodeToByteArray())
        assertEquals("name: Gym", files.readBytes("a/card.yaml").decodeToString())
    }

    @Test
    fun atomicWriteReplacesAndLeavesNoTempFile() {
        val files = emptyVault()
        files.createDir("a")
        files.writeBytesAtomic("a/card.yaml", byteArrayOf(1, 2, 3))
        files.writeBytesAtomic("a/card.yaml", byteArrayOf(4))
        assertContentEquals(byteArrayOf(4), files.readBytes("a/card.yaml"))
        assertEquals(setOf("card.yaml"), files.names("a"))
    }

    @Test
    fun listShowsFilesFoldersAndHiddenEntries() {
        val files = emptyVault()
        files.createDir("owner/card")
        files.createDir(".garbo")
        files.writeBytesAtomic("owner/owner.yaml", byteArrayOf())
        assertEquals(setOf(".garbo", "owner"), files.names(""))
        assertEquals(
            setOf(VaultEntry("card", isDirectory = true), VaultEntry("owner.yaml", isDirectory = false)),
            files.list("owner").toSet(),
        )
        assertTrue(files.list("").single { it.name == ".garbo" }.isHidden)
    }

    @Test
    fun listOfMissingFolderIsEmpty() {
        assertTrue(emptyVault().list("missing").isEmpty())
    }

    @Test
    fun existsForFilesAndFolders() {
        val files = emptyVault()
        files.createDir("a/b")
        files.writeBytesAtomic("a/f", byteArrayOf())
        assertTrue(files.exists("a"))
        assertTrue(files.exists("a/b"))
        assertTrue(files.exists("a/f"))
        assertFalse(files.exists("a/missing"))
    }

    @Test
    fun moveFolderWithContents() {
        val files = emptyVault()
        files.createDir("me/card")
        files.createDir("anna")
        files.writeBytesAtomic("me/card/card.yaml", byteArrayOf(7))
        files.writeBytesAtomic("me/card/front.jpg", byteArrayOf(8))
        files.move("me/card", "anna/card")
        assertFalse(files.exists("me/card"))
        assertContentEquals(byteArrayOf(7), files.readBytes("anna/card/card.yaml"))
        assertContentEquals(byteArrayOf(8), files.readBytes("anna/card/front.jpg"))
    }

    @Test
    fun moveFile() {
        val files = emptyVault()
        files.createDir("a")
        files.writeBytesAtomic("a/.card.yaml.tmp", byteArrayOf(1))
        files.move("a/.card.yaml.tmp", "a/card.yaml")
        assertEquals(setOf("card.yaml"), files.names("a"))
    }

    @Test
    fun moveRefusesExistingTarget() {
        val files = emptyVault()
        files.createDir("a")
        files.createDir("b")
        assertFailsWith<VaultFilesException> { files.move("a", "b") }
        assertTrue(files.exists("a"))
    }

    @Test
    fun deleteRecursiveRemovesEverything() {
        val files = emptyVault()
        files.createDir("me/card/nested")
        files.writeBytesAtomic("me/card/card.yaml", byteArrayOf())
        files.writeBytesAtomic("me/card/nested/x", byteArrayOf())
        files.deleteRecursive("me/card")
        assertFalse(files.exists("me/card"))
        assertTrue(files.exists("me"))
        files.deleteRecursive("me/missing")
    }

    @Test
    fun readMissingFileFails() {
        assertFailsWith<VaultFilesException> { emptyVault().readBytes("nope") }
    }
}

class InMemoryVaultFilesTest : VaultFilesContract() {
    override fun emptyVault(): VaultFiles = InMemoryVaultFiles()
}
