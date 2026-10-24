package dev.mateuy.garbo.vault

import dev.mateuy.garbo.storage.InMemoryVaultFiles
import dev.mateuy.garbo.vault.VaultProblem.Kind
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VaultTest {
    private val files = InMemoryVaultFiles()

    private fun newVault(): Vault = Vault.create(files)

    private fun ownerYaml(id: String, name: String) = "ownerId: $id\nname: $name\ncolor: '#000000'\n"

    private fun cardYaml(id: String, name: String) = "cardId: $id\nname: $name\ntype: loyalty\n"

    // specs/vault: Create a vault

    @Test
    fun createInEmptyFolder() {
        val snapshot = newVault().load()
        val me = snapshot.owners.single()
        assertEquals("Me", me.name)
        assertEquals("me", me.folder)
        assertTrue(files.exists("me/owner.yaml"))
        assertEquals(me.ownerId, snapshot.info.defaultOwnerId)
        assertEquals(VaultLayout.FORMAT_VERSION, snapshot.info.formatVersion)
        assertTrue(snapshot.cards.isEmpty())
        assertTrue(snapshot.problems.isEmpty())
    }

    @Test
    fun createIgnoresHiddenFiles() {
        files.writeText(".dropbox", "")
        newVault()
        assertTrue(files.exists(VaultLayout.VAULT_FILE))
    }

    @Test
    fun createRefusesExistingVault() {
        newVault()
        val before = files.files.keys.toSet()
        assertFailsWith<VaultError.AlreadyAVault> { Vault.create(files) }
        assertEquals(before, files.files.keys)
    }

    @Test
    fun createRefusesNonEmptyFolder() {
        files.writeText("photos/a.jpg", "")
        assertFailsWith<VaultError.FolderNotEmpty> { Vault.create(files) }
        assertEquals(setOf("photos/a.jpg"), files.files.keys)
    }

    // specs/vault: Open an existing vault

    @Test
    fun openExistingVault() {
        newVault()
        val anna = Vault.open(files).createOwner("Anna", "#112233")
        files.writeText("${anna.folder}/c1/card.yaml", cardYaml("c1", "Library"))
        val snapshot = Vault.open(files).load()
        assertEquals(setOf("Me", "Anna"), snapshot.owners.map { it.name }.toSet())
        assertEquals(anna.ownerId, snapshot.cards.single().ownerId)
    }

    @Test
    fun openRefusesFolderWithoutVault() {
        files.writeText("x/y", "")
        assertFailsWith<VaultError.NotAVault> { Vault.open(files) }
    }

    @Test
    fun openRefusesNewerFormat() {
        files.writeText(VaultLayout.VAULT_FILE, "formatVersion: 2\nvaultId: v\n")
        val before = files.readText(VaultLayout.VAULT_FILE)
        val error = assertFailsWith<VaultError.UnsupportedFormat> { Vault.open(files) }
        assertEquals(2, error.version)
        assertEquals(before, files.readText(VaultLayout.VAULT_FILE))
    }

    @Test
    fun openRefusesMalformedVaultFile() {
        files.writeText(VaultLayout.VAULT_FILE, "formatVersion: [\n")
        assertFailsWith<VaultError.Unreadable> { Vault.open(files) }
    }

    // specs/vault: Tolerate unreadable files

    @Test
    fun malformedCardIsReportedAndLeftUntouched() {
        val vault = newVault()
        files.writeText("me/c1/card.yaml", cardYaml("c1", "Good"))
        files.writeText("me/c2/card.yaml", "name: [broken\n")
        val snapshot = vault.load()
        assertEquals(listOf("Good"), snapshot.cards.map { it.name })
        val problem = snapshot.problems.single()
        assertEquals(Kind.MALFORMED, problem.kind)
        assertEquals("me/c2/card.yaml", problem.path)
        assertEquals("name: [broken\n", files.readText("me/c2/card.yaml"))
    }

    @Test
    fun malformedOwnerSkipsOnlyThatOwner() {
        val vault = newVault()
        files.writeText("broken/owner.yaml", "ownerId: x\n")
        files.writeText("broken/c1/card.yaml", cardYaml("c1", "Hidden"))
        val snapshot = vault.load()
        assertEquals(listOf("Me"), snapshot.owners.map { it.name })
        assertTrue(snapshot.cards.isEmpty())
        assertEquals(listOf("broken/owner.yaml"), snapshot.problems.map { it.path })
    }

    @Test
    fun unknownFoldersAreIgnoredSilently() {
        val vault = newVault()
        files.writeText("Camera Uploads/x.jpg", "")
        files.writeText("me/notes/readme.txt", "")
        files.writeText("me/.hidden/card.yaml", cardYaml("h", "Hidden"))
        val snapshot = vault.load()
        assertTrue(snapshot.cards.isEmpty())
        assertTrue(snapshot.problems.isEmpty())
        assertTrue(files.exists("Camera Uploads/x.jpg"))
    }

    @Test
    fun interruptedWriteIsRecovered() {
        val vault = newVault()
        files.writeText("me/c1/.card.yaml.tmp", cardYaml("c1", "Saved"))
        val snapshot = vault.load()
        assertEquals("Saved", snapshot.cards.single().name)
        assertEquals(Kind.RECOVERED, snapshot.problems.single().kind)
        assertTrue(files.exists("me/c1/card.yaml"))
        assertFalse(files.exists("me/c1/.card.yaml.tmp"))
    }

    @Test
    fun brokenTempFileIsNotRecovered() {
        val vault = newVault()
        files.writeText("me/c1/.card.yaml.tmp", "name: [half")
        assertTrue(vault.load().cards.isEmpty())
        assertTrue(files.exists("me/c1/.card.yaml.tmp"))
    }

    @Test
    fun tempFileNextToValidFileIsIgnored() {
        val vault = newVault()
        files.writeText("me/c1/card.yaml", cardYaml("c1", "Current"))
        files.writeText("me/c1/.card.yaml.tmp", cardYaml("c1", "Stale"))
        val snapshot = vault.load()
        assertEquals("Current", snapshot.cards.single().name)
        assertTrue(snapshot.problems.isEmpty())
    }

    // specs/vault: Reload external changes (a reload is a new scan)

    @Test
    fun scanSeesCardsAddedAndRemovedOutside() {
        val vault = newVault()
        files.writeText("me/c1/card.yaml", cardYaml("c1", "Synced"))
        assertEquals(1, vault.load().cards.size)
        files.deleteRecursive("me/c1")
        assertTrue(vault.load().cards.isEmpty())
    }

    // specs/owners

    @Test
    fun ownerIdentityComesFromOwnerYaml() {
        val vault = newVault()
        val anna = vault.createOwner("Anna", "#112233")
        files.writeText("${anna.folder}/c1/card.yaml", cardYaml("c1", "Library"))
        files.move(anna.folder, "Anna (shared)")
        val snapshot = vault.load()
        val renamed = snapshot.owner(anna.ownerId)!!
        assertEquals("Anna", renamed.name)
        assertEquals("Anna (shared)", renamed.folder)
        assertEquals(anna.ownerId, snapshot.cards.single().ownerId)
    }

    @Test
    fun ownerFolderNamedAfterNameWithSuffixOnCollision() {
        val vault = newVault()
        assertEquals("anna-lopez", vault.createOwner("Anna López", "#112233").folder)
        assertEquals("anna-lopez-2", vault.createOwner("Anna Lopez", "#112233").folder)
    }

    @Test
    fun renameOwnerKeepsFolderAndId() {
        val vault = newVault()
        val anna = vault.createOwner("Anna", "#112233")
        vault.updateOwner(anna, "Anna M.", "#445566")
        val updated = vault.load().owner(anna.ownerId)!!
        assertEquals("Anna M.", updated.name)
        assertEquals("#445566", updated.color)
        assertEquals("anna", updated.folder)
    }

    @Test
    fun updateOwnerKeepsUnknownFields() {
        val vault = newVault()
        files.writeText("me/owner.yaml", files.readText("me/owner.yaml") + "keyId: k1\n")
        vault.updateOwner(vault.load().owners.single(), "Myself", "#000000")
        assertTrue("keyId: k1" in files.readText("me/owner.yaml"))
    }

    @Test
    fun blankOwnerNameIsRefused() {
        val vault = newVault()
        assertFailsWith<VaultError.BlankName> { vault.createOwner("  ", "#112233") }
        assertFailsWith<VaultError.BlankName> { vault.updateOwner(vault.load().owners.single(), "", "#112233") }
    }

    @Test
    fun changeDefaultOwner() {
        val vault = newVault()
        val anna = vault.createOwner("Anna", "#112233")
        vault.setDefaultOwner(anna)
        val snapshot = vault.load()
        assertEquals(anna.ownerId, snapshot.info.defaultOwnerId)
        assertEquals(anna.ownerId, snapshot.defaultOwner?.ownerId)
    }

    @Test
    fun missingDefaultOwnerFallsBackToFirstByName() {
        val vault = newVault()
        vault.createOwner("Anna", "#112233")
        val text = files.readText(VaultLayout.VAULT_FILE).replace(Regex("defaultOwner: .*"), "defaultOwner: gone")
        files.writeText(VaultLayout.VAULT_FILE, text)
        assertEquals("Anna", vault.load().defaultOwner?.name)
        assertEquals(text, files.readText(VaultLayout.VAULT_FILE))
    }

    @Test
    fun deleteEmptyOwner() {
        val vault = newVault()
        val pau = vault.createOwner("Pau", "#112233")
        vault.deleteOwner(pau)
        assertFalse(files.exists(pau.folder))
    }

    @Test
    fun deleteOwnerWithCardsIsRefused() {
        val vault = newVault()
        val pau = vault.createOwner("Pau", "#112233")
        vault.saveCard(CardDraft(null, pau.ownerId, "Gym"), null, vault.load().owners)
        assertFailsWith<VaultError.OwnerHasCards> { vault.deleteOwner(pau) }
        assertTrue(files.exists(pau.folder))
    }

    @Test
    fun deleteDefaultOwnerIsRefused() {
        val vault = newVault()
        assertFailsWith<VaultError.OwnerIsDefault> { vault.deleteOwner(vault.load().owners.single()) }
    }

    @Test
    fun ownerFolderDroppedInAppears() {
        val vault = newVault()
        files.writeText("shared-kid/owner.yaml", ownerYaml("kid", "Kid"))
        files.writeText("shared-kid/c9/card.yaml", cardYaml("c9", "School"))
        val snapshot = vault.load()
        assertEquals("Kid", snapshot.owner("kid")?.name)
        assertEquals("kid", snapshot.cards.single().ownerId)
    }

    @Test
    fun duplicateOwnerIdLoadsFirstFolder() {
        val vault = newVault()
        files.writeText("anna/owner.yaml", ownerYaml("a", "Anna"))
        files.writeText("anna-copy/owner.yaml", ownerYaml("a", "Anna copy"))
        val snapshot = vault.load()
        assertEquals("anna", snapshot.owner("a")?.folder)
        assertEquals(VaultProblem("anna-copy", Kind.DUPLICATE_OWNER), snapshot.problems.single())
    }

    // specs/cards

    @Test
    fun newCardFolderIsNamedByCardId() {
        val vault = newVault()
        val me = vault.load().owners.single()
        val card = vault.saveCard(CardDraft(null, me.ownerId, "Gym"), null, listOf(me))
        assertEquals("me/${card.cardId}", card.folder)
        assertTrue(files.exists("me/${card.cardId}/card.yaml"))
        assertEquals(card.copy(yaml = vault.load().cards.single().yaml), vault.load().cards.single())
    }

    @Test
    fun renameCardKeepsFolder() {
        val vault = newVault()
        val me = vault.load().owners.single()
        val card = vault.saveCard(CardDraft(null, me.ownerId, "Gym"), null, listOf(me))
        val renamed = vault.saveCard(CardDraft(card.cardId, me.ownerId, "Gym club"), card, listOf(me))
        assertEquals(card.folder, renamed.folder)
        assertEquals("Gym club", vault.load().cards.single().name)
    }

    @Test
    fun cardWithOnlyName() {
        val vault = newVault()
        val me = vault.load().owners.single()
        vault.saveCard(CardDraft(null, me.ownerId, "Gym"), null, listOf(me))
        val card = vault.load().cards.single()
        assertNull(card.code)
        assertFalse(card.hasFront || card.hasBack)
    }

    @Test
    fun blankCardNameIsRefused() {
        val vault = newVault()
        val me = vault.load().owners.single()
        assertFailsWith<VaultError.BlankName> { vault.saveCard(CardDraft(null, me.ownerId, " "), null, listOf(me)) }
        assertEquals(1, files.list("me").size)
    }

    @Test
    fun addAndRemoveCode() {
        val vault = newVault()
        val me = vault.load().owners.single()
        val code = CardCode(CodeFormat.EAN13.id, "2401234567890")
        val card = vault.saveCard(CardDraft(null, me.ownerId, "Shop", code = code), null, listOf(me))
        assertEquals(code, vault.load().cards.single().code?.copy(yaml = emptyMap()))
        val cleared = CardDraft(card.cardId, me.ownerId, "Shop", code = CardCode(CodeFormat.EAN13.id, " "))
        vault.saveCard(cleared, card, listOf(me))
        assertFalse("code" in files.readText("${card.folder}/card.yaml"))
    }

    @Test
    fun editKeepsUnknownFields() {
        val vault = newVault()
        files.writeText("me/c1/card.yaml", "cardId: c1\nname: Old\ntype: passport\nexpires: 2030-01-01\n")
        val card = vault.load().cards.single()
        vault.saveCard(CardDraft("c1", card.ownerId, "New", type = card.type), card, vault.load().owners)
        val text = files.readText("me/c1/card.yaml")
        assertTrue("expires: 2030-01-01" in text)
        assertTrue("type: passport" in text)
        assertTrue("name: New" in text)
    }

    @Test
    fun photosAreAddedReplacedAndRemoved() {
        val vault = newVault()
        val me = vault.load().owners.single()
        val draft = CardDraft(null, me.ownerId, "Id", front = PhotoChange.Replace(byteArrayOf(1)), back = PhotoChange.Replace(byteArrayOf(2)))
        val card = vault.saveCard(draft, null, listOf(me))
        assertTrue(card.hasFront && card.hasBack)
        assertContentEquals(byteArrayOf(1), vault.readPhoto(card, PhotoSide.FRONT))

        val edit = CardDraft(card.cardId, me.ownerId, "Id", front = PhotoChange.Replace(byteArrayOf(3)), back = PhotoChange.Remove)
        val edited = vault.saveCard(edit, card, listOf(me))
        assertTrue(edited.hasFront)
        assertFalse(edited.hasBack)
        assertFalse(files.exists("${card.folder}/back.jpg"))
        assertContentEquals(byteArrayOf(3), vault.readPhoto(edited, PhotoSide.FRONT))
        assertNull(vault.readPhoto(edited, PhotoSide.BACK))
    }

    @Test
    fun changeOwnerMovesFolderWithPhotos() {
        val vault = newVault()
        val anna = vault.createOwner("Anna", "#112233")
        val owners = vault.load().owners
        val me = owners.first { it.name == "Me" }
        val card = vault.saveCard(CardDraft(null, me.ownerId, "Gym", front = PhotoChange.Replace(byteArrayOf(5))), null, owners)
        val moved = vault.saveCard(CardDraft(card.cardId, anna.ownerId, "Gym"), card, owners)
        assertEquals("anna/${card.cardId}", moved.folder)
        assertFalse(files.exists(card.folder))
        assertTrue(files.exists("anna/${card.cardId}/front.jpg"))
        val reloaded = vault.load().cards.single()
        assertEquals(anna.ownerId, reloaded.ownerId)
        assertEquals(card.cardId, reloaded.cardId)
    }

    @Test
    fun deleteCardRemovesFolder() {
        val vault = newVault()
        val me = vault.load().owners.single()
        val card = vault.saveCard(CardDraft(null, me.ownerId, "Gym", front = PhotoChange.Replace(byteArrayOf(1))), null, listOf(me))
        vault.deleteCard(card)
        assertFalse(files.exists(card.folder))
        assertTrue(vault.load().cards.isEmpty())
    }

    @Test
    fun editingACardDeletedOutsideFails() {
        val vault = newVault()
        val me = vault.load().owners.single()
        val card = vault.saveCard(CardDraft(null, me.ownerId, "Gym"), null, listOf(me))
        files.deleteRecursive(card.folder)
        assertFailsWith<VaultError.NotFound> { vault.saveCard(CardDraft(card.cardId, me.ownerId, "X"), card, listOf(me)) }
    }

    @Test
    fun duplicateCardIdLoadsFirstByFolderOrder() {
        val vault = newVault()
        files.writeText("anna/owner.yaml", ownerYaml("a", "Anna"))
        files.writeText("anna/c1/card.yaml", cardYaml("c1", "Gym"))
        files.writeText("me/c1/card.yaml", cardYaml("c1", "Gym"))
        val snapshot = vault.load()
        assertEquals("anna/c1", snapshot.cards.single().folder)
        assertEquals(VaultProblem("me/c1", Kind.DUPLICATE_CARD, "anna/c1"), snapshot.problems.single())
    }

    @Test
    fun cardsAreSortedByName() {
        val vault = newVault()
        files.writeText("me/c1/card.yaml", cardYaml("c1", "zoo"))
        files.writeText("me/c2/card.yaml", cardYaml("c2", "Apple"))
        files.writeText("me/c3/card.yaml", cardYaml("c3", "bakery"))
        assertEquals(listOf("Apple", "bakery", "zoo"), vault.load().cards.map { it.name })
    }
}
