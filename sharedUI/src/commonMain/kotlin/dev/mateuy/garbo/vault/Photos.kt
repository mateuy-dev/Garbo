package dev.mateuy.garbo.vault

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.ImageFormat
import io.github.vinceglb.filekit.compressImage

/** Longest side, in pixels, of a stored card photo. */
const val MAX_PHOTO_SIZE = 2048

/** An imported file that is not an image this platform can decode. */
class NotAnImageException(cause: Throwable?) : Exception("The file is not a supported image", cause)

/**
 * Turns an imported image into what a card stores: a JPEG whose longest side is at most
 * [MAX_PHOTO_SIZE], keeping its proportions (and, on Android, its EXIF orientation).
 */
suspend fun normalizePhoto(bytes: ByteArray): ByteArray = try {
    FileKit.compressImage(
        bytes = bytes,
        imageFormat = ImageFormat.JPEG,
        quality = 85,
        maxWidth = MAX_PHOTO_SIZE,
        maxHeight = MAX_PHOTO_SIZE,
    ).also { if (it.isEmpty()) throw NotAnImageException(null) }
} catch (e: NotAnImageException) {
    throw e
} catch (e: Exception) {
    throw NotAnImageException(e)
}
