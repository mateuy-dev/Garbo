package dev.mateuy.garbo.vault

import dev.mateuy.garbo.storage.InMemoryVaultFiles
import dev.mateuy.garbo.storage.OpenFolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

class VaultSessionTest {
    private class MemoryStore : VaultStore {
        var stored: StoredVault? = null
        override fun load() = stored
        override fun save(vault: StoredVault) { stored = vault }
        override fun clear() { stored = null }
    }

    private val files = InMemoryVaultFiles()
    private val store = MemoryStore()
    private val folder = PickedFolder(byteArrayOf(42), "/home/me/Dropbox/Garbo") { OpenFolder(files) }

    /** A new app launch: fresh repository and session over the same device store and folder. */
    private fun TestScope.launch(reopen: (StoredVault) -> PickedFolder = { folder }) =
        VaultSession(VaultRepository(StandardTestDispatcher(testScheduler)), store, reopen)

    @Test
    fun createdVaultIsReopenedOnNextLaunch() = runTest {
        assertNull(launch().create(folder))
        assertEquals("/home/me/Dropbox/Garbo", store.stored?.label)

        val next = launch()
        next.restore()
        val open = assertIs<VaultState.Open>(next.repository.state.value)
        assertEquals("/home/me/Dropbox/Garbo", open.location)
        assertEquals(listOf("Me"), open.snapshot.owners.map { it.name })
    }

    @Test
    fun nothingRememberedStaysClosed() = runTest {
        val session = launch()
        session.restore()
        assertEquals(VaultState.Closed(), session.repository.state.value)
    }

    @Test
    fun unreachableVaultShowsSetupWithReasonAndIsForgotten() = runTest {
        launch().create(folder)
        val next = launch { error("bookmark is stale") }
        next.restore()
        assertIs<VaultError.Unreachable>(assertIs<VaultState.Closed>(next.repository.state.value).error)
        assertNull(store.stored)
    }

    @Test
    fun deletedVaultShowsSetupWithReason() = runTest {
        launch().create(folder)
        files.deleteRecursive("")
        val next = launch()
        next.restore()
        assertIs<VaultError.NotAVault>(assertIs<VaultState.Closed>(next.repository.state.value).error)
        assertNull(store.stored)
    }

    @Test
    fun failedOpenIsNotRemembered() = runTest {
        assertIs<VaultError.NotAVault>(launch().open(folder))
        assertNull(store.stored)
    }

    @Test
    fun closeForgetsTheVaultButKeepsItsFiles() = runTest {
        val session = launch()
        session.create(folder)
        val before = files.files.keys.toSet()
        session.close()
        assertEquals(VaultState.Closed(), session.repository.state.value)
        assertNull(store.stored)
        assertEquals(before, files.files.keys)
    }
}
