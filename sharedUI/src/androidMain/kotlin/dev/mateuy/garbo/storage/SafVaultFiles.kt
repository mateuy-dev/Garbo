package dev.mateuy.garbo.storage

import android.content.ContentResolver
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document

/**
 * [VaultFiles] on a Storage Access Framework document tree (the folder picked on Android).
 *
 * SAF addresses documents by id, not by path, so paths are resolved one segment at a time and
 * the ids found are cached. The cache can go stale when another app changes the folder: any
 * failing call clears it and is retried once with fresh lookups.
 */
class SafVaultFiles(
    private val resolver: ContentResolver,
    private val treeUri: Uri,
) : VaultFiles {

    private val rootId: String = DocumentsContract.getTreeDocumentId(treeUri)
    private val ids = mutableMapOf<String, String>()

    private fun documentUri(id: String): Uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)

    private data class Child(val id: String, val name: String, val isDirectory: Boolean)

    private fun children(dir: String, dirId: String): List<Child> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, dirId)
        val columns = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE)
        val result = mutableListOf<Child>()
        resolver.query(uri, columns, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                result += Child(
                    id = cursor.getString(0),
                    name = cursor.getString(1),
                    isDirectory = cursor.getString(2) == Document.MIME_TYPE_DIR,
                )
            }
        } ?: throw VaultFilesException("Could not list $dir")
        result.forEach { ids[vaultPath(dir, it.name)] = it.id }
        return result
    }

    /** The document id of [path], or null if it does not exist. */
    private fun idOf(path: String): String? {
        if (path.isEmpty()) return rootId
        ids[path]?.let { return it }
        val parentId = idOf(path.parentPath()) ?: return null
        return children(path.parentPath(), parentId).firstOrNull { it.name == path.fileName() }?.id
    }

    private fun requireId(path: String): String = idOf(path) ?: throw VaultFilesException("$path not found")

    private fun forget(path: String) {
        ids.keys.removeAll { it == path || it.startsWith("$path/") }
    }

    override fun list(dir: String): List<VaultEntry> = io("list $dir") {
        val dirId = idOf(dir) ?: return@io emptyList()
        children(dir, dirId).map { VaultEntry(it.name, it.isDirectory) }
    }

    override fun exists(path: String): Boolean = io("check $path") { idOf(path) != null }

    override fun readBytes(path: String): ByteArray = io("read $path") {
        resolver.openInputStream(documentUri(requireId(path)))?.use { it.readBytes() }
            ?: throw VaultFilesException("Could not open $path")
    }

    override fun writeBytesAtomic(path: String, bytes: ByteArray): Unit = io("write $path") {
        val parent = path.parentPath()
        val name = path.fileName()
        val existing = idOf(path)
        if (existing == null) {
            writeTo(createFile(parent, name), bytes)
        } else {
            // SAF cannot replace a document in one step: write the new content next to it,
            // then swap. A crash between delete and rename leaves only the temporary file,
            // which the vault loader recovers.
            val tempPath = vaultPath(parent, tempNameFor(name))
            idOf(tempPath)?.let { delete(tempPath, it) }
            writeTo(createFile(parent, tempNameFor(name)), bytes)
            delete(path, existing)
            rename(tempPath, name)
        }
    }

    private fun createFile(parent: String, name: String): String {
        // octet-stream keeps providers from appending an extension to the name.
        val uri = DocumentsContract.createDocument(
            resolver, documentUri(requireId(parent)), "application/octet-stream", name,
        ) ?: throw VaultFilesException("Could not create ${vaultPath(parent, name)}")
        val id = DocumentsContract.getDocumentId(uri)
        ids[vaultPath(parent, name)] = id
        return id
    }

    private fun writeTo(id: String, bytes: ByteArray) {
        resolver.openOutputStream(documentUri(id), "wt")?.use { it.write(bytes) }
            ?: throw VaultFilesException("Could not open document $id for writing")
    }

    private fun delete(path: String, id: String) {
        if (!DocumentsContract.deleteDocument(resolver, documentUri(id))) {
            throw VaultFilesException("Could not delete $path")
        }
        forget(path)
    }

    private fun rename(path: String, newName: String) {
        val uri = DocumentsContract.renameDocument(resolver, documentUri(requireId(path)), newName)
            ?: throw VaultFilesException("Could not rename $path")
        forget(path)
        ids[vaultPath(path.parentPath(), newName)] = DocumentsContract.getDocumentId(uri)
    }

    override fun createDir(path: String): Unit = io("create $path") {
        if (path.isEmpty() || idOf(path) != null) return@io
        createDir(path.parentPath())
        val uri = DocumentsContract.createDocument(
            resolver, documentUri(requireId(path.parentPath())), Document.MIME_TYPE_DIR, path.fileName(),
        ) ?: throw VaultFilesException("Could not create $path")
        ids[path] = DocumentsContract.getDocumentId(uri)
    }

    override fun deleteRecursive(path: String): Unit = io("delete $path") {
        val id = idOf(path) ?: return@io
        delete(path, id)
    }

    override fun move(from: String, to: String): Unit = io("move $from to $to") {
        if (idOf(to) != null) throw VaultFilesException("$to already exists")
        val fromParent = from.parentPath()
        val toParent = to.parentPath()
        if (fromParent == toParent) {
            rename(from, to.fileName())
            return@io
        }
        val moved = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && tryMoveDocument(from, to)
        if (!moved) {
            // Copy fully, then delete the source: an interruption leaves a duplicate, never a loss.
            copyRecursive(from, toParent, from.fileName())
            deleteRecursive(from)
        }
        if (from.fileName() != to.fileName()) rename(vaultPath(toParent, from.fileName()), to.fileName())
    }

    private fun tryMoveDocument(from: String, to: String): Boolean = try {
        val uri = DocumentsContract.moveDocument(
            resolver,
            documentUri(requireId(from)),
            documentUri(requireId(from.parentPath())),
            documentUri(requireId(to.parentPath())),
        )
        forget(from)
        uri != null
    } catch (e: UnsupportedOperationException) {
        false
    }

    private fun copyRecursive(from: String, toParent: String, name: String) {
        val target = vaultPath(toParent, name)
        val isDirectory = list(from.parentPath()).first { it.name == from.fileName() }.isDirectory
        if (isDirectory) {
            createDir(target)
            list(from).forEach { copyRecursive(vaultPath(from, it.name), target, it.name) }
        } else {
            writeTo(createFile(toParent, name), readBytes(from))
        }
    }

    private inline fun <T> io(action: String, block: () -> T): T = try {
        block()
    } catch (e: Exception) {
        ids.clear()
        try {
            block()
        } catch (retry: VaultFilesException) {
            throw retry
        } catch (retry: Exception) {
            throw VaultFilesException("Could not $action: ${retry.message}", retry)
        }
    }
}
