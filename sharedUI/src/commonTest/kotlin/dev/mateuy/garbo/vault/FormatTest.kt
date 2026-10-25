package dev.mateuy.garbo.vault

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FormatTest {
    @Test
    fun unknownCardFieldsSurviveAnEdit() {
        val card = Card.parse(
            """
            cardId: c1
            name: Gym
            type: membership
            expires: 2030-01-01
            code:
              value: abc
              format: qr
              colour: red
            """.trimIndent(),
        )
        val text = card.copy(name = "Gym+").toYaml()
        val reread = Yaml.load(text)
        assertEquals("Gym+", reread["name"])
        assertEquals("2030-01-01", reread.text("expires"))
        assertEquals("red", reread["code"].asYamlMap()!!["colour"])
        assertEquals(listOf("cardId", "name", "type", "expires", "code"), reread.keys.toList())
    }

    @Test
    fun unknownTypeIsARegularCardAndIsWrittenBack() {
        val card = Card.parse("cardId: c1\nname: Passport\ntype: passport\n")
        assertFalse(card.isId)
        val reread = Card.parse(card.copy(notes = "renew").toYaml())
        assertEquals("passport", reread.type)
        assertEquals("renew", reread.notes)
    }

    @Test
    fun idTypeMarksAnIdentityDocument() {
        assertTrue(Card.parse("cardId: c1\nname: DNI\ntype: id\n").isId)
        val regular = Card.parse("cardId: c1\nname: Gym\n")
        assertFalse(regular.isId)
        assertFalse("type" in Yaml.load(regular.toYaml()))
    }

    @Test
    fun unknownCodeFormatIsKept() {
        val card = Card.parse("cardId: c1\nname: X\ncode:\n  format: maxicode\n  value: '1'\n")
        assertNull(card.code!!.codeFormat)
        assertEquals("maxicode", Card.parse(card.toYaml()).code!!.format)
    }

    @Test
    fun codeWithoutFormat() {
        val card = Card.parse("cardId: c1\nname: X\ncode:\n  value: '1'\n")
        assertNull(card.code!!.format)
        assertEquals(CodeFormat.CODABAR, Card.parse("cardId: c1\nname: X\ncode:\n  value: '1'\n  format: codabar\n").code!!.codeFormat)
    }

    @Test
    fun codeValueIsWrittenAsText() {
        val card = Card(cardId = "c1", name = "Shop", code = CardCode("2401234567890", CodeFormat.EAN13.id))
        val code = Yaml.load(card.toYaml())["code"].asYamlMap()!!
        assertEquals("ean13", code["format"])
        assertEquals("2401234567890", code["value"])
    }

    @Test
    fun unquotedNumericCodeIsReadAsText() {
        val card = Card.parse("cardId: c1\nname: Shop\ncode:\n  format: ean13\n  value: 2401234567890\n")
        assertEquals("2401234567890", card.code!!.value)
    }

    @Test
    fun clearingTheCodeRemovesIt() {
        val card = Card.parse("cardId: c1\nname: Shop\ncode:\n  format: qr\n  value: x\n")
        assertFalse("code" in Yaml.load(card.copy(code = null).toYaml()))
    }

    @Test
    fun cardWithOnlyANameIsValid() {
        val card = Card.parse("cardId: c1\nname: Gym\n")
        assertNull(card.code)
        assertNull(card.notes)
    }

    @Test
    fun blankNameIsMalformed() {
        assertFailsWith<FormatException> { Card.parse("cardId: c1\nname: '  '\n") }
        assertFailsWith<FormatException> { Owner.parse("ownerId: o1\nname: ''\ncolor: '#000000'\n", "x") }
    }

    @Test
    fun invalidYamlIsMalformed() {
        assertFailsWith<FormatException> { Card.parse("cardId: [unclosed\n") }
        assertFailsWith<FormatException> { Card.parse("- just\n- a list\n") }
    }

    @Test
    fun ownerKeepsUnknownFieldsAndFallsBackOnBadColor() {
        val owner = Owner.parse("ownerId: o1\nname: Anna\ncolor: blue\nkeyId: k1\n", "anna")
        assertEquals(OwnerColors.FALLBACK, owner.color)
        val reread = Yaml.load(owner.copy(name = "Anna M.", color = "#112233").toYaml())
        assertEquals("k1", reread["keyId"])
        assertEquals("#112233", reread["color"])
    }

    @Test
    fun vaultInfoRoundTrip() {
        val info = VaultInfo.parse("formatVersion: 1\nvaultId: v1\ndefaultOwner: o1\nfuture: yes\n")
        assertEquals(VaultInfo(1, "v1", "o1", info.yaml), info)
        val reread = Yaml.load(info.copy(defaultOwnerId = "o2").toYaml())
        assertEquals("o2", reread["defaultOwner"])
        assertTrue("future" in reread)
    }

    @Test
    fun ownerFolderSlugs() {
        assertEquals("anna-lopez", ownerFolderSlug("Anna López"))
        assertEquals("me", ownerFolderSlug("Me"))
        assertEquals("pau-jordi", ownerFolderSlug("  Pau & Jordi! "))
        assertEquals("owner", ownerFolderSlug("★★"))
        assertEquals("anna-lopez-2", uniqueName("anna-lopez") { it == "anna-lopez" })
        assertEquals("a-3", uniqueName("a") { it in setOf("a", "a-2") })
    }

    @Test
    fun idsAreRandomUuids() {
        val id = newId()
        assertTrue(Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$").matches(id))
        assertFalse(id == newId())
    }
}
