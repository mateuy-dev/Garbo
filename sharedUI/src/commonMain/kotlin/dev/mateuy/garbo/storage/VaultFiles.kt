package dev.mateuy.garbo.storage

/**
 * Access to the files of one vault folder.
 *
 * Paths are relative to the vault root, use `/` as separator and never start with `/`;
 * the empty string is the root itself. Calls block, so callers run them off the main thread.
 * Failures are reported as [VaultFilesException].
 */
interface VaultFiles {
    /** Entries directly inside [dir], in no particular order. Empty if [dir] does not exist. */
    fun list(dir: String): List<VaultEntry>

    fun exists(path: String): Boolean

    fun readBytes(path: String): ByteArray

    /**
     * Writes [bytes] to [path] so that readers see either the old content or the new one,
     * never a partial file. The parent folder must exist.
     */
    fun writeBytesAtomic(path: String, bytes: ByteArray)

    /** Creates [path] and any missing parent folders. Does nothing if it already exists. */
    fun createDir(path: String)

    /** Deletes a file, or a folder with everything inside it. Does nothing if it does not exist. */
    fun deleteRecursive(path: String)

    /** Moves a file or folder to [to], which must not exist yet. The parent of [to] must exist. */
    fun move(from: String, to: String)
}

data class VaultEntry(val name: String, val isDirectory: Boolean) {
    val isHidden: Boolean get() = name.startsWith(".")
}

class VaultFilesException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Joins path segments with `/`, skipping empty ones. */
fun vaultPath(vararg segments: String): String = segments.filter { it.isNotEmpty() }.joinToString("/")

/** The name of the temporary sibling used by atomic writes of [name]. */
internal fun tempNameFor(name: String): String = ".$name.tmp"

internal fun String.parentPath(): String = substringBeforeLast('/', missingDelimiterValue = "")

internal fun String.fileName(): String = substringAfterLast('/')
