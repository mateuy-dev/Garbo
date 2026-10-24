package dev.mateuy.garbo.ui

import dev.mateuy.garbo.storage.OpenFolder
import dev.mateuy.garbo.storage.PathVaultFiles
import dev.mateuy.garbo.vault.NotAnImageException
import dev.mateuy.garbo.vault.PhotoChange
import dev.mateuy.garbo.vault.PhotoSide
import dev.mateuy.garbo.vault.VaultRepository
import dev.mateuy.garbo.vault.VaultState
import io.github.vinceglb.filekit.PlatformFile
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.io.files.Path

/** Importing photos in the card editor, with files as the image picker returns them. */
class CardEditViewModelTest {
    private val root: File = Files.createTempDirectory("garbo-edit").toFile()
    private val repository = VaultRepository()

    @AfterTest
    fun cleanUp() {
        root.deleteRecursively()
    }

    private suspend fun until(condition: () -> Boolean) = withTimeout(10_000) {
        while (!condition()) delay(20)
    }

    private fun openVault(): VaultState.Open = runBlocking {
        repository.create(OpenFolder(PathVaultFiles(Path(root.path))), root.path)
        repository.state.value as VaultState.Open
    }

    @Test
    fun importedPhotoIsSavedAsScaledJpeg(): Unit = runBlocking {
        val state = openVault()
        val image = File(root.parentFile, "${root.name}-photo.png").apply { deleteOnExit() }
        ImageIO.write(BufferedImage(4000, 3000, BufferedImage.TYPE_INT_RGB), "png", image)

        val viewModel = CardEditViewModel(repository, state.snapshot, cardId = null, ownerId = null)
        viewModel.name = "Library"
        viewModel.importPhoto(PhotoSide.FRONT, PlatformFile(image))
        until { viewModel.front.change is PhotoChange.Replace }

        var saved: String? = null
        viewModel.save { saved = it.folder }
        until { saved != null }
        val jpeg = File(root, "$saved/front.jpg")
        assertTrue(jpeg.exists())
        val stored = ImageIO.read(jpeg)
        assertEquals(2048 to 1536, stored.width to stored.height)
        assertTrue(!File(root, "$saved/back.jpg").exists())
        image.delete()
    }

    @Test
    fun notAnImageShowsAnErrorAndKeepsThePhoto(): Unit = runBlocking {
        val state = openVault()
        val text = File(root.parentFile, "${root.name}-notes.txt").apply { writeText("hello"); deleteOnExit() }
        val viewModel = CardEditViewModel(repository, state.snapshot, cardId = null, ownerId = null)
        viewModel.importPhoto(PhotoSide.BACK, PlatformFile(text))
        until { viewModel.error != null }
        assertIs<NotAnImageException>(viewModel.error)
        assertEquals(PhotoChange.Keep, viewModel.back.change)
        text.delete()
    }
}
