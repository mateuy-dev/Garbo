package dev.mateuy.garbo.vault

import dev.mateuy.garbo.storage.OpenFolder
import dev.mateuy.garbo.storage.PathVaultFiles
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path

/** specs/owners "Owner folders added from outside", with two vault folders on disk. */
class OwnerFolderSharingTest {
    private val base = Files.createTempDirectory("garbo-share").toFile()
    private val mine = File(base, "mine").apply { mkdirs() }
    private val theirs = File(base, "theirs").apply { mkdirs() }

    @AfterTest
    fun cleanUp() {
        base.deleteRecursively()
    }

    private fun files(dir: File) = PathVaultFiles(Path(dir.path))

    @Test
    fun copiedOwnerFolderAppearsAfterRefresh(): Unit = runBlocking {
        // Their vault, with a child's cards and photo.
        val other = Vault.create(files(theirs))
        val kid = other.createOwner("Kid", "#43A047")
        val owners = other.load().owners
        other.saveCard(CardDraft(null, kid.ownerId, "School", front = PhotoChange.Replace(byteArrayOf(1, 2))), null, owners)
        other.saveCard(CardDraft(null, kid.ownerId, "Library"), null, owners)

        val repository = VaultRepository()
        repository.create(OpenFolder(files(mine)), mine.path)

        // A sync tool delivers the shared folder, under whatever name the receiver gave it.
        File(theirs, kid.folder).copyRecursively(File(mine, "Kid (shared)"))
        repository.reload()

        val snapshot = assertIs<VaultState.Open>(repository.state.value).snapshot
        assertEquals("Kid", snapshot.owner(kid.ownerId)?.name)
        assertEquals("Kid (shared)", snapshot.owner(kid.ownerId)?.folder)
        assertEquals(listOf("Library", "School"), snapshot.cardsOf(kid.ownerId).map { it.name })
        assertEquals(true, snapshot.cards.first { it.name == "School" }.hasFront)
        assertEquals(emptyList(), snapshot.problems)
    }
}
