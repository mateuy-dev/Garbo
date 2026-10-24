package dev.mateuy.garbo.vault

/** Names of the files and folders that make up a vault. */
object VaultLayout {
    const val GARBO_DIR = ".garbo"
    const val VAULT_FILE = ".garbo/vault.yaml"
    const val OWNER_FILE = "owner.yaml"
    const val CARD_FILE = "card.yaml"
    const val FRONT_PHOTO = "front.jpg"
    const val BACK_PHOTO = "back.jpg"

    /** The newest vault format this version of Garbo reads and writes. */
    const val FORMAT_VERSION = 1
}

/** `.garbo/vault.yaml`. */
data class VaultInfo(
    val formatVersion: Int,
    val vaultId: String,
    val defaultOwnerId: String?,
    val yaml: YamlMap = emptyMap(),
) {
    fun toYaml(): String = Yaml.dump(
        yaml.with(
            "formatVersion" to formatVersion,
            "vaultId" to vaultId,
            "defaultOwner" to defaultOwnerId,
        ),
    )

    companion object {
        fun parse(text: String): VaultInfo {
            val yaml = Yaml.load(text)
            val version = yaml.requireText("formatVersion").toIntOrNull()
                ?: throw FormatException("'formatVersion' must be a number")
            return VaultInfo(version, yaml.requireText("vaultId"), yaml.text("defaultOwner"), yaml)
        }
    }
}

/** An owner, read from `<folder>/owner.yaml`. */
data class Owner(
    val ownerId: String,
    val name: String,
    val color: String,
    /** The owner's folder, relative to the vault root. Not part of its identity. */
    val folder: String = "",
    val yaml: YamlMap = emptyMap(),
) {
    fun toYaml(): String = Yaml.dump(yaml.with("ownerId" to ownerId, "name" to name, "color" to color))

    companion object {
        fun parse(text: String, folder: String): Owner {
            val yaml = Yaml.load(text)
            val color = yaml.text("color")?.takeIf { OwnerColors.isValid(it) } ?: OwnerColors.FALLBACK
            return Owner(yaml.requireText("ownerId"), yaml.requireText("name"), color, folder, yaml)
        }
    }
}

object OwnerColors {
    /** Colors offered when creating or editing an owner; the first is used for "Me". */
    val palette = listOf(
        "#1E88E5", "#43A047", "#E53935", "#FB8C00", "#8E24AA",
        "#00ACC1", "#F4511E", "#6D4C41", "#3949AB", "#C0CA33",
    )
    const val FALLBACK = "#757575"

    private val pattern = Regex("^#[0-9A-Fa-f]{6}$")

    fun isValid(color: String): Boolean = pattern.matches(color)
}

enum class CardType(val id: String) {
    LOYALTY("loyalty"),
    MEMBERSHIP("membership"),
    LIBRARY("library"),
    ID("id"),
    OTHER("other");

    companion object {
        val DEFAULT = LOYALTY

        /** The type for a stored value; values unknown to this version show as [OTHER]. */
        fun of(id: String): CardType = entries.firstOrNull { it.id == id } ?: OTHER
    }
}

enum class CodeFormat(val id: String) {
    QR("qr"),
    CODE128("code128"),
    CODE39("code39"),
    EAN13("ean13"),
    EAN8("ean8"),
    UPCA("upca"),
    PDF417("pdf417"),
    AZTEC("aztec"),
    DATAMATRIX("datamatrix"),
    TEXT("text");

    companion object {
        /** The format for a stored value; values unknown to this version show as [TEXT]. */
        fun of(id: String): CodeFormat = entries.firstOrNull { it.id == id } ?: TEXT
    }
}

/** A card's code. [format] is the stored value, kept as is even when unknown to this version. */
data class CardCode(
    val format: String,
    val value: String,
    val yaml: YamlMap = emptyMap(),
) {
    val codeFormat: CodeFormat get() = CodeFormat.of(format)
}

/** A card, read from `<owner folder>/<card folder>/card.yaml`. */
data class Card(
    val cardId: String,
    val name: String,
    /** The stored type, kept as is even when unknown to this version; see [cardType]. */
    val type: String = CardType.DEFAULT.id,
    val code: CardCode? = null,
    val notes: String? = null,
    /** The owner whose folder holds this card. */
    val ownerId: String = "",
    /** The card's folder, relative to the vault root. */
    val folder: String = "",
    val hasFront: Boolean = false,
    val hasBack: Boolean = false,
    val yaml: YamlMap = emptyMap(),
) {
    val cardType: CardType get() = CardType.of(type)

    fun toYaml(): String {
        val code = code?.let { it.yaml.with("format" to it.format, "value" to it.value) }
        return Yaml.dump(
            yaml.with(
                "cardId" to cardId,
                "name" to name,
                "type" to type,
                "code" to code,
                "notes" to notes?.takeIf { it.isNotBlank() },
            ),
        )
    }

    companion object {
        fun parse(text: String, ownerId: String = "", folder: String = ""): Card {
            val yaml = Yaml.load(text)
            val code = yaml["code"]?.let { raw ->
                val codeYaml = raw.asYamlMap() ?: throw FormatException("'code' must be a mapping")
                CardCode(codeYaml.text("format") ?: CodeFormat.TEXT.id, codeYaml.requireText("value"), codeYaml)
            }
            return Card(
                cardId = yaml.requireText("cardId"),
                name = yaml.requireText("name"),
                type = yaml.text("type") ?: CardType.OTHER.id,
                code = code,
                notes = yaml.text("notes"),
                ownerId = ownerId,
                folder = folder,
                yaml = yaml,
            )
        }
    }
}
