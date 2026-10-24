package dev.mateuy.garbo.vault

import dev.mateuy.garbo.storage.InMemoryVaultFiles
import dev.mateuy.garbo.storage.OpenFolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

class VaultRepositoryTest {
    private val files = InMemoryVaultFiles()
    private var closed = 0
    private val folder = OpenFolder(files) { closed++ }

    private fun runRepositoryTest(block: suspend (VaultRepository) -> Unit) = runTest {
        block(VaultRepository(StandardTestDispatcher(testScheduler)))
    }

    private val VaultRepository.snapshot get() = assertIs<VaultState.Open>(state.value).snapshot

    @Test
    fun createOpensTheNewVault() = runRepositoryTest { repository ->
        assertNull(repository.create(folder, "Vault"))
        assertEquals(listOf("Me"), repository.snapshot.owners.map { it.name })
        assertEquals("Vault", assertIs<VaultState.Open>(repository.state.value).location)
    }

    @Test
    fun failedOpenStaysClosedWithTheErrorAndReleasesTheFolder() = runRepositoryTest { repository ->
        assertIs<VaultError.NotAVault>(repository.open(folder, "Vault"))
        assertIs<VaultError.NotAVault>(assertIs<VaultState.Closed>(repository.state.value).error)
        assertEquals(1, closed)
    }

    @Test
    fun changeIsFollowedByAScan() = runRepositoryTest { repository ->
        repository.create(folder, "Vault")
        repository.change { createOwner("Anna", "#112233") }
        assertEquals(setOf("Me", "Anna"), repository.snapshot.owners.map { it.name }.toSet())
    }

    @Test
    fun failedChangeStillRescansAndThrows() = runRepositoryTest { repository ->
        repository.create(folder, "Vault")
        files.writeText("me/c1/card.yaml", "cardId: c1\nname: Synced\n")
        assertFailsWith<VaultError.BlankName> { repository.change { createOwner("", "#112233") } }
        assertEquals(listOf("Synced"), repository.snapshot.cards.map { it.name })
    }

    @Test
    fun reloadPicksUpExternalChanges() = runRepositoryTest { repository ->
        repository.create(folder, "Vault")
        files.writeText("me/c1/card.yaml", "cardId: c1\nname: Synced\n")
        repository.reload()
        assertEquals(1, repository.snapshot.cards.size)
    }

    @Test
    fun vaultRemovedWhileOpenCloses() = runRepositoryTest { repository ->
        repository.create(folder, "Vault")
        files.deleteRecursive(".garbo")
        repository.reload()
        assertIs<VaultError.NotAVault>(assertIs<VaultState.Closed>(repository.state.value).error)
    }

    @Test
    fun closeLeavesFilesUntouched() = runRepositoryTest { repository ->
        repository.create(folder, "Vault")
        val before = files.files.mapValues { it.value.decodeToString() }
        repository.close()
        assertEquals(VaultState.Closed(), repository.state.value)
        assertEquals(before, files.files.mapValues { it.value.decodeToString() })
        assertTrue(closed == 1)
    }
}
