package dev.mateuy.garbo.vault

import com.russhwolf.settings.PreferencesSettings
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import java.nio.file.Files
import java.util.prefs.Preferences
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

/** Remembering the vault with the real desktop pieces: Java preferences and FileKit bookmarks. */
class DesktopVaultSessionTest {
    private val root = Files.createTempDirectory("garbo-session").toFile()
    private val preferences = Preferences.userRoot().node("dev/mateuy/garbo-test-${System.nanoTime()}")

    init {
        FileKit.init(appId = "dev.mateuy.garbo.test")
    }

    @AfterTest
    fun cleanUp() {
        preferences.removeNode()
        root.deleteRecursively()
    }

    private fun launch() = VaultSession(VaultRepository(), SettingsVaultStore(PreferencesSettings(preferences)), ::reopenStoredFolder)

    @Test
    fun vaultIsReopenedOnNextLaunch() = runTest {
        val first = launch()
        assertEquals(null, first.create(PlatformFile(root).toPickedFolder()))
        first.repository.change { createOwner("Anna", "#E53935") }

        val next = launch()
        next.restore()
        val open = assertIs<VaultState.Open>(next.repository.state.value)
        assertEquals(root.path, open.location)
        assertEquals(setOf("Me", "Anna"), open.snapshot.owners.map { it.name }.toSet())
    }

    @Test
    fun movedVaultShowsSetupWithReason() = runTest {
        launch().create(PlatformFile(root).toPickedFolder())
        root.renameTo(root.resolveSibling(root.name + "-moved").also { it.deleteOnExit() })

        val next = launch()
        next.restore()
        assertIs<VaultState.Closed>(next.repository.state.value).error.let { assertIs<VaultError>(it) }
        assertEquals(null, SettingsVaultStore(PreferencesSettings(preferences)).load())
        root.resolveSibling(root.name + "-moved").deleteRecursively()
    }
}
