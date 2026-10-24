package dev.mateuy.garbo.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import dev.mateuy.garbo.App
import dev.mateuy.garbo.storage.OpenFolder
import dev.mateuy.garbo.storage.PathVaultFiles
import dev.mateuy.garbo.vault.CardDraft
import dev.mateuy.garbo.vault.PhotoChange
import dev.mateuy.garbo.vault.PickedFolder
import dev.mateuy.garbo.vault.StoredVault
import dev.mateuy.garbo.vault.Vault
import dev.mateuy.garbo.vault.VaultStore
import dev.mateuy.garbo.vault.normalizePhoto
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path

/**
 * The app on the desktop runtime, against a real vault folder in a temporary directory.
 * Folder and image pickers are native dialogs, so vaults are created through the session
 * as the setup screen does after a pick.
 */
@OptIn(ExperimentalTestApi::class)
class AppUiTest {
    private class MemoryStore : VaultStore {
        var stored: StoredVault? = null
        override fun load() = stored
        override fun save(vault: StoredVault) { stored = vault }
        override fun clear() { stored = null }
    }

    private val root: File = Files.createTempDirectory("garbo-ui").toFile()
    private val store = MemoryStore()
    private val folder = PickedFolder(byteArrayOf(1), root.path) { OpenFolder(PathVaultFiles(Path(root.path))) }
    private val vaultFiles = PathVaultFiles(Path(root.path))

    @AfterTest
    fun cleanUp() {
        root.deleteRecursively()
    }

    /** A vault created on a previous launch, remembered on this device. */
    private fun existingVault(): Vault {
        runBlocking { AppGraph(store) { folder }.session.create(folder) }
        return Vault.open(vaultFiles)
    }

    private fun ComposeUiTest.launchApp(): AppGraph {
        val graph = AppGraph(store) { folder }
        val viewModels = object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
        setContent {
            CompositionLocalProvider(LocalAppGraph provides graph, LocalViewModelStoreOwner provides viewModels) {
                App()
            }
        }
        return graph
    }

    private fun ComposeUiTest.waitForText(text: String, substring: Boolean = false) =
        waitUntil(timeoutMillis = 10_000) {
            onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty()
        }

    /** Saves the screen (without dialogs, which are separate roots) to build/ui-screenshots. */
    private fun ComposeUiTest.screenshot(name: String) {
        waitUntil(timeoutMillis = 10_000) { onAllNodes(isRoot()).fetchSemanticsNodes().size == 1 }
        val dir = File("build/ui-screenshots").apply { mkdirs() }
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(dir, "$name.png"))
    }

    private fun file(path: String) = File(root, path)

    private fun cardFolders(owner: String) = file(owner).listFiles { f -> f.isDirectory }.orEmpty().toList()

    private fun png(width: Int, height: Int, color: Color): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        image.createGraphics().apply { this.color = color; fillRect(0, 0, width, height) }
        return ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
    }

    // specs/vault: First-run vault choice

    @Test
    fun firstLaunchShowsSetup() = runComposeUiTest {
        launchApp()
        waitForText("Create vault")
        onNodeWithText("Open vault").assertExists()
        screenshot("setup")
    }

    // specs/vault: Create a vault, Remember the vault

    @Test
    fun createdVaultReopensOnLaunchWithEmptyList() = runComposeUiTest {
        existingVault()
        launchApp()
        waitForText("No cards yet.")
        onNodeWithText("Me").assertExists()
        assertTrue(file(".garbo/vault.yaml").exists())
        screenshot("empty-list")
    }

    @Test
    fun unreachableVaultShowsSetupWithMessage() = runComposeUiTest {
        existingVault()
        root.deleteRecursively()
        launchApp()
        waitForText("not a Garbo vault", substring = true)
        onNodeWithText("Create vault").assertExists()
        assertNull(store.stored)
    }

    // specs/cards: Create and edit cards, Card fields, Card code

    @Test
    fun createCard() = runComposeUiTest {
        existingVault()
        launchApp()
        waitForText("No cards yet.")
        onNodeWithContentDescription("Add card").performClick()
        waitForText("New card")
        onNodeWithTag("card-owner").assertExists()
        onNodeWithText("Me").assertExists()
        onNodeWithTag("card-name").performTextInput("Bonpreu")
        onNodeWithTag("card-format").performClick()
        onNodeWithText("EAN-13").performClick()
        onNodeWithTag("card-code").performTextInput("2401234567890")
        screenshot("card-edit")
        onNodeWithText("Save").performClick()

        waitForText("2401234567890")
        onNodeWithText("EAN-13").assertExists()
        screenshot("card-detail")
        val yaml = cardFolders("me").single().resolve("card.yaml").readText()
        assertTrue("name: Bonpreu" in yaml, yaml)
        assertTrue("format: ean13" in yaml, yaml)
        assertTrue("2401234567890" in yaml, yaml)

        onNodeWithContentDescription("Back").performClick()
        waitForText("Bonpreu")
        onNodeWithText("Loyalty").assertExists()
    }

    @Test
    fun blankNameIsRefused() = runComposeUiTest {
        existingVault()
        launchApp()
        waitForText("No cards yet.")
        onNodeWithContentDescription("Add card").performClick()
        waitForText("New card")
        onNodeWithText("Save").performClick()
        waitForText("A name is required.")
        assertTrue(cardFolders("me").isEmpty())
    }

    @Test
    fun discardingAnEditWritesNothing() = runComposeUiTest {
        val vault = existingVault()
        val me = vault.load().owners.single()
        val card = vault.saveCard(CardDraft(null, me.ownerId, "Gym"), null, listOf(me))
        val before = file("${card.folder}/card.yaml").readText()
        launchApp()
        waitForText("Gym")
        onNodeWithText("Gym").performClick()
        onNodeWithContentDescription("Edit").performClick()
        waitForText("Edit card")
        onNodeWithTag("card-name").performTextClearance()
        onNodeWithTag("card-name").performTextInput("Changed")
        onNodeWithContentDescription("Back").performClick()
        waitForText("Gym")
        assertEquals(before, file("${card.folder}/card.yaml").readText())
    }

    // specs/cards: Card list (filter, owner pre-selection)

    @Test
    fun filterByOwnerAndPreselectIt() = runComposeUiTest {
        val vault = existingVault()
        val anna = vault.createOwner("Anna", "#E53935")
        val owners = vault.load().owners
        val me = owners.first { it.name == "Me" }
        vault.saveCard(CardDraft(null, me.ownerId, "Gym"), null, owners)
        vault.saveCard(CardDraft(null, anna.ownerId, "Library"), null, owners)
        launchApp()
        waitForText("Library")
        onNodeWithText("Gym").assertExists()
        screenshot("list-all")

        onNodeWithTag("filter-Anna").performClick()
        waitUntil { onAllNodes(hasText("Gym")).fetchSemanticsNodes().isEmpty() }
        onNodeWithText("Library").assertExists()
        screenshot("list-filtered")

        onNodeWithContentDescription("Add card").performClick()
        waitForText("New card")
        onNodeWithTag("card-owner").assertExists()
        onAllNodes(hasText("Anna")).fetchSemanticsNodes().isNotEmpty().let(::assertTrue)
    }

    // specs/vault: Reload external changes, Tolerate unreadable files

    @Test
    fun refreshShowsExternalChangesAndProblems() = runComposeUiTest {
        existingVault()
        launchApp()
        waitForText("No cards yet.")
        file("me/synced/card.yaml").apply { parentFile.mkdirs() }.writeText("cardId: s1\nname: Synced card\n")
        file("me/broken/card.yaml").apply { parentFile.mkdirs() }.writeText("name: [broken\n")
        onNodeWithContentDescription("Refresh").performClick()
        waitForText("Synced card")
        onNodeWithText("1 item in the vault could not be read").performClick()
        waitForText("me/broken/card.yaml")
        screenshot("problems")
        assertEquals("name: [broken\n", file("me/broken/card.yaml").readText())
    }

    // specs/cards: Card photos (display), Change a card's owner, Delete a card

    @Test
    fun detailShowsPhotos() = runComposeUiTest {
        val vault = existingVault()
        val me = vault.load().owners.single()
        val front = runBlocking { normalizePhoto(png(1600, 1000, Color(0x1E88E5))) }
        val back = runBlocking { normalizePhoto(png(1600, 1000, Color(0xFB8C00))) }
        vault.saveCard(
            CardDraft(null, me.ownerId, "Library", front = PhotoChange.Replace(front), back = PhotoChange.Replace(back)),
            null, listOf(me),
        )
        launchApp()
        waitForText("Library")
        Thread.sleep(2000)
        screenshot("list-photo")
        onNodeWithText("Library").performClick()
        waitForText("Front")
        onNodeWithText("Back").assertExists()
        // Photos are decoded by Coil off the test clock; give it time before the screenshot.
        Thread.sleep(2000)
        screenshot("detail-photos")
    }

    @Test
    fun moveCardToAnotherOwner() = runComposeUiTest {
        val vault = existingVault()
        val anna = vault.createOwner("Anna", "#E53935")
        val owners = vault.load().owners
        val me = owners.first { it.name == "Me" }
        val card = vault.saveCard(CardDraft(null, me.ownerId, "Gym", front = PhotoChange.Replace(byteArrayOf(1))), null, owners)
        launchApp()
        waitForText("Gym")
        onNodeWithText("Gym").performClick()
        onNodeWithContentDescription("Edit").performClick()
        waitForText("Edit card")
        onNodeWithTag("card-owner").performClick()
        onAllNodes(hasText("Anna")).onFirst().performClick()
        onNodeWithText("Save").performClick()
        waitUntil(timeoutMillis = 10_000) { file("${anna.folder}/${card.cardId}/card.yaml").exists() }
        assertFalse(file(card.folder).exists())
        assertTrue(file("${anna.folder}/${card.cardId}/front.jpg").exists())
    }

    @Test
    fun deleteCardAfterConfirming() = runComposeUiTest {
        val vault = existingVault()
        val me = vault.load().owners.single()
        val card = vault.saveCard(CardDraft(null, me.ownerId, "Gym"), null, listOf(me))
        launchApp()
        waitForText("Gym")
        onNodeWithText("Gym").performClick()
        onNodeWithContentDescription("Delete").performClick()
        waitForText("Delete this card?")
        onNodeWithText("Delete").performClick()
        waitForText("No cards yet.")
        assertFalse(file(card.folder).exists())
    }

    // specs/owners: Manage owners, Default owner, Delete an owner

    @Test
    fun manageOwners() = runComposeUiTest {
        val vault = existingVault()
        launchApp()
        waitForText("No cards yet.")
        onNodeWithContentDescription("Owners").performClick()
        waitForText("Owners")

        onNodeWithContentDescription("Add owner").performClick()
        waitForText("New owner")
        onNodeWithText("Save").performClick()
        waitForText("A name is required.")
        onNodeWithTag("owner-name").performTextInput("Pau")
        onNodeWithText("Save").performClick()
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasTestTag("owner-Pau")).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(file("pau/owner.yaml").exists())
        screenshot("owners")

        onNodeWithTag("owner-Pau").performClick()
        waitForText("Edit owner")
        onNodeWithTag("owner-name").performTextClearance()
        onNodeWithTag("owner-name").performTextInput("Pau M.")
        onNodeWithText("Save").performClick()
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasTestTag("owner-Pau M.")).fetchSemanticsNodes().isNotEmpty() }
        assertTrue("name: Pau M." in file("pau/owner.yaml").readText())

        onNodeWithTag("owner-menu-Pau M.").performClick()
        onNodeWithText("Make default").performClick()
        waitUntil(timeoutMillis = 10_000) { "defaultOwner: ${vault.load().owners.first { it.name == "Pau M." }.ownerId}" in file(".garbo/vault.yaml").readText() }

        onNodeWithTag("owner-menu-Pau M.").performClick()
        onNodeWithText("Delete").performClick()
        onNodeWithText("Delete").performClick()
        waitForText("This is the default owner", substring = true)
        onNodeWithText("OK").performClick()

        onNodeWithTag("owner-menu-Me").performClick()
        onNodeWithText("Delete").performClick()
        onNodeWithText("Delete").performClick()
        waitUntil(timeoutMillis = 10_000) { !file("me").exists() }
        onNodeWithTag("owner-Me").assertDoesNotExist()
    }

    @Test
    fun ownerWithCardsCannotBeDeleted() = runComposeUiTest {
        val vault = existingVault()
        val pau = vault.createOwner("Pau", "#43A047")
        vault.saveCard(CardDraft(null, pau.ownerId, "Gym"), null, vault.load().owners)
        launchApp()
        waitForText("Gym")
        onNodeWithContentDescription("Owners").performClick()
        waitForText("1 card")
        onNodeWithTag("owner-menu-Pau").performClick()
        onNodeWithText("Delete").performClick()
        onNodeWithText("Delete").performClick()
        waitForText("still has cards", substring = true)
        assertTrue(file("pau").exists())
    }

    // specs/vault: Switch vault

    @Test
    fun closeVaultReturnsToSetupAndKeepsFiles() = runComposeUiTest {
        existingVault()
        val before = root.walk().map { it.relativeTo(root).path }.toSet()
        launchApp()
        waitForText("No cards yet.")
        onNodeWithContentDescription("Settings").performClick()
        waitForText(root.path)
        screenshot("settings")
        onNodeWithText("Close vault").performClick()
        onAllNodes(hasText("Close vault")).onLast().performClick()
        waitForText("Create vault")
        assertNull(store.stored)
        assertEquals(before, root.walk().map { it.relativeTo(root).path }.toSet())
    }
}
