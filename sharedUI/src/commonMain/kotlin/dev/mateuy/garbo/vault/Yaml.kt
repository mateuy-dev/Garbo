package dev.mateuy.garbo.vault

import it.krzeminski.snakeyaml.engine.kmp.api.Dump
import it.krzeminski.snakeyaml.engine.kmp.api.DumpSettings
import it.krzeminski.snakeyaml.engine.kmp.api.Load
import it.krzeminski.snakeyaml.engine.kmp.api.LoadSettings
import it.krzeminski.snakeyaml.engine.kmp.common.FlowStyle

/** A vault file whose content is not what Garbo expects. */
class FormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** A YAML mapping as loaded, in file order. Unknown keys are kept so they can be written back. */
typealias YamlMap = Map<String, Any?>

internal object Yaml {
    private val dump = Dump(DumpSettings(defaultFlowStyle = FlowStyle.BLOCK))

    fun load(text: String): YamlMap {
        val value = try {
            Load(LoadSettings()).loadOne(text)
        } catch (e: Exception) {
            throw FormatException("Invalid YAML: ${e.message}", e)
        }
        return value.asYamlMap() ?: throw FormatException("Expected a YAML mapping")
    }

    fun dump(map: YamlMap): String = dump.dumpToString(map)
}

internal fun Any?.asYamlMap(): YamlMap? =
    (this as? Map<*, *>)?.entries?.associateTo(LinkedHashMap()) { (key, value) -> key.toString() to value }

/** Reads a scalar as text: hand-written files may leave numbers or booleans unquoted. */
internal fun YamlMap.text(key: String): String? = when (val value = this[key]) {
    null -> null
    is Map<*, *>, is List<*> -> throw FormatException("'$key' must be a single value")
    else -> value.toString()
}

internal fun YamlMap.requireText(key: String): String =
    text(key)?.takeIf { it.isNotBlank() } ?: throw FormatException("Missing '$key'")

/**
 * Returns [this] map with [updates] applied: a key mapped to null is removed, any other key is
 * set, in place if it existed or appended otherwise. Keys not in [updates] are untouched.
 */
internal fun YamlMap.with(vararg updates: Pair<String, Any?>): YamlMap {
    val result = LinkedHashMap(this)
    for ((key, value) in updates) {
        if (value == null) result.remove(key) else result[key] = value
    }
    return result
}
