package dev.mateuy.garbo.vault

import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest

class PhotosTest {
    private fun png(width: Int, height: Int): ByteArray {
        val out = ByteArrayOutputStream()
        ImageIO.write(BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", out)
        return out.toByteArray()
    }

    private fun readImage(bytes: ByteArray) = ImageIO.createImageInputStream(ByteArrayInputStream(bytes)).use { input ->
        val reader = ImageIO.getImageReaders(input).next()
        reader.input = input
        reader.formatName.lowercase() to (reader.getWidth(0) to reader.getHeight(0))
    }

    @Test
    fun largePngBecomesScaledJpeg() = runTest {
        val (format, size) = readImage(normalizePhoto(png(4000, 3000)))
        assertEquals("jpeg", format)
        assertEquals(2048 to 1536, size)
    }

    @Test
    fun portraitIsLimitedByHeight() = runTest {
        assertEquals(1536 to 2048, readImage(normalizePhoto(png(3000, 4000))).second)
    }

    @Test
    fun smallImageKeepsItsSize() = runTest {
        assertEquals(640 to 480, readImage(normalizePhoto(png(640, 480))).second)
    }

    @Test
    fun textFileIsRejected() = runTest {
        assertFailsWith<NotAnImageException> { normalizePhoto("not an image".encodeToByteArray()) }
    }
}
