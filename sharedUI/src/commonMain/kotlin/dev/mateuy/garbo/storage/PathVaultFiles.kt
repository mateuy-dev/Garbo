package dev.mateuy.garbo.storage

import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray

/** [VaultFiles] on a regular file system path (desktop, iOS). */
class PathVaultFiles(
    private val root: Path,
    private val fs: FileSystem = SystemFileSystem,
) : VaultFiles {

    private fun resolve(path: String): Path = if (path.isEmpty()) root else Path(root, path)

    override fun list(dir: String): List<VaultEntry> = io("list $dir") {
        val path = resolve(dir)
        if (fs.metadataOrNull(path)?.isDirectory != true) return@io emptyList()
        fs.list(path).map { child ->
            VaultEntry(child.name, fs.metadataOrNull(child)?.isDirectory == true)
        }
    }

    override fun exists(path: String): Boolean = io("check $path") { fs.exists(resolve(path)) }

    override fun readBytes(path: String): ByteArray = io("read $path") {
        fs.source(resolve(path)).buffered().use { it.readByteArray() }
    }

    override fun writeBytesAtomic(path: String, bytes: ByteArray): Unit = io("write $path") {
        val target = resolve(path)
        val temp = resolve(vaultPath(path.parentPath(), tempNameFor(path.fileName())))
        fs.sink(temp).buffered().use { it.write(bytes) }
        fs.atomicMove(temp, target)
    }

    override fun createDir(path: String): Unit = io("create $path") { fs.createDirectories(resolve(path)) }

    override fun deleteRecursive(path: String): Unit = io("delete $path") { deleteRecursive(resolve(path)) }

    private fun deleteRecursive(path: Path) {
        val metadata = fs.metadataOrNull(path) ?: return
        if (metadata.isDirectory) fs.list(path).forEach { deleteRecursive(it) }
        fs.delete(path)
    }

    override fun move(from: String, to: String): Unit = io("move $from to $to") {
        val target = resolve(to)
        if (fs.exists(target)) throw VaultFilesException("$to already exists")
        fs.atomicMove(resolve(from), target)
    }

    private inline fun <T> io(action: String, block: () -> T): T = try {
        block()
    } catch (e: VaultFilesException) {
        throw e
    } catch (e: Exception) {
        throw VaultFilesException("Could not $action: ${e.message}", e)
    }
}
