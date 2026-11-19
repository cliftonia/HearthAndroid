package app.hearth.integrations

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import androidx.core.graphics.scale
import androidx.core.net.toUri
import java.io.ByteArrayOutputStream
import kotlin.math.ceil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Makes the small square picture kept as the user's profile photo from one they choose. Nothing leaves the phone here. */
class ProfilePhotoLoader(private val context: Context) {

    /**
     * The chosen picture cut to its middle square, made [SIDE] pixels each way and squeezed into a JPEG.
     * `null` if what was chosen could not be read as a picture.
     */
    suspend fun avatarJpeg(address: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val source = ImageDecoder.createSource(context.contentResolver, address.toUri())
            val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                // Decoded no larger than is needed: its shorter side is what the square is cut from.
                val shortest = minOf(info.size.width, info.size.height)
                if (shortest > SIDE) {
                    val scale = SIDE.toDouble() / shortest
                    decoder.setTargetSize(ceil(info.size.width * scale).toInt(), ceil(info.size.height * scale).toInt())
                }
                // The picture is cut and squeezed next, which needs it in ordinary memory.
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            squareJpeg(bitmap)
        }.getOrNull()
    }

    companion object {
        /** The side of the kept photo, in pixels: enough for the largest avatar on the sharpest screen. */
        const val SIDE = 320

        /** How hard the photo is squeezed, out of 100. */
        private const val JPEG_QUALITY = 85

        /** The middle square of a picture at [SIDE] pixels each way, as a JPEG. */
        internal fun squareJpeg(bitmap: Bitmap): ByteArray {
            val side = minOf(bitmap.width, bitmap.height)
            val square = Bitmap.createBitmap(bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side)
            val sized = if (side == SIDE) square else square.scale(SIDE, SIDE)
            return ByteArrayOutputStream().also { sized.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }.toByteArray()
        }

        /** A kept photo, as something to show. `null` if it does not decode. */
        fun bitmap(jpeg: ByteArray): Bitmap? = runCatching { BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) }.getOrNull()
    }
}
