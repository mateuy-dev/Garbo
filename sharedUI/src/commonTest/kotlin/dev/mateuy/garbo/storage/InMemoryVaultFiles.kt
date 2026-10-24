package dev.mateuy.garbo.storage

/** [VaultFiles] kept in memory, for tests. Folders are implicit in [files] or listed in [dirs]. */
class InMemoryVaultFiles : VaultFiles {
    val files = mutableMapOf<String, ByteArray>()
    val dirs = mutableSetOf<String>()

    private fun isDir(path: String): Boolean =
        path.isEmpty() || path in dirs || (files.keys + dirs).any { it.startsWith("$path/") }

    override fun list(dir: String): List<VaultEntry> {
        if (!isDir(dir)) return emptyList()
        val prefix = if (dir.isEmpty()) "" else "$dir/"
        return (files.keys + dirs)
            .filter { it.startsWith(prefix) && it.length > prefix.length }
            .map { it.removePrefix(prefix).substringBefore('/') }
            .distinct()
            .map { VaultEntry(it, isDir(prefix + it)) }
    }

    override fun exists(path: String): Boolean = path in files || isDir(path)

    override fun readBytes(path: String): ByteArray =
        files[path]?.copyOf() ?: throw VaultFilesException("Could not read $path: not found")

    override fun writeBytesAtomic(path: String, bytes: ByteArray) {
        if (!isDir(path.parentPath())) throw VaultFilesException("Could not write $path: no parent")
        files[path] = bytes.copyOf()
    }

    override fun createDir(path: String) {
        var current = path
        while (current.isNotEmpty()) {
            dirs += current
            current = current.parentPath()
        }
    }

    override fun deleteRecursive(path: String) {
        if (path.isEmpty()) {
            files.clear()
            dirs.clear()
            return
        }
        files.keys.removeAll { it == path || it.startsWith("$path/") }
        dirs.removeAll { it == path || it.startsWith("$path/") }
    }

    override fun move(from: String, to: String) {
        if (exists(to)) throw VaultFilesException("$to already exists")
        if (!exists(from)) throw VaultFilesException("Could not move $from: not found")
        if (!isDir(to.parentPath())) throw VaultFilesException("Could not move $from: no parent for $to")
        fun moved(key: String) = to + key.removePrefix(from)
        files.keys.filter { it == from || it.startsWith("$from/") }.forEach { files[moved(it)] = files.remove(it)!! }
        dirs.filter { it == from || it.startsWith("$from/") }.forEach { dirs -= it; dirs += moved(it) }
    }

    fun writeText(path: String, text: String) {
        createDir(path.parentPath())
        files[path] = text.encodeToByteArray()
    }

    fun readText(path: String): String = readBytes(path).decodeToString()
}
