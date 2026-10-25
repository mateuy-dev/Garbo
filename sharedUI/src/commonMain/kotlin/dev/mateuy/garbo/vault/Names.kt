package dev.mateuy.garbo.vault

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
internal fun newId(): String = Uuid.random().toString()

private val latin = mapOf(
    'à' to "a", 'á' to "a", 'â' to "a", 'ä' to "a", 'ã' to "a", 'å' to "a", 'æ' to "ae",
    'ç' to "c", 'è' to "e", 'é' to "e", 'ê' to "e", 'ë' to "e",
    'ì' to "i", 'í' to "i", 'î' to "i", 'ï' to "i", 'ñ' to "n",
    'ò' to "o", 'ó' to "o", 'ô' to "o", 'ö' to "o", 'õ' to "o", 'ø' to "o", 'œ' to "oe",
    'ù' to "u", 'ú' to "u", 'û' to "u", 'ü' to "u", 'ý' to "y", 'ÿ' to "y", 'ß' to "ss",
)

/**
 * A folder name for an owner called [name]: lowercase ASCII letters, digits and dashes,
 * or `owner` if nothing is left.
 */
internal fun ownerFolderSlug(name: String): String {
    val ascii = buildString {
        for (char in name.lowercase()) append(latin[char] ?: char)
    }
    return ascii.replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "owner" }
}

/** [base], or `base-2`, `base-3`... the first one for which [taken] is false. */
internal fun uniqueName(base: String, taken: (String) -> Boolean): String =
    generateSequence(1) { it + 1 }
        .map { if (it == 1) base else "$base-$it" }
        .first { !taken(it) }
