package app.hearth.integrations

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Keeps the pictures the app makes: readies them for a conversation, saves them to the phone and lends them to other apps. */
class PictureLibrary(private val context: Context) {

    /** A finished picture squeezed into a JPEG in Base64, the form a conversation keeps it in. `null` if it is no picture. */
    suspend fun jpegBase64(picture: ByteArray): String? = withContext(Dispatchers.Default) {
        val bitmap = BitmapFactory.decodeByteArray(picture, 0, picture.size) ?: return@withContext null
        val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }.toByteArray()
        Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    /** Adds the picture to the phone's Pictures, in a folder of the app's own. Returns whether it was written. */
    suspend fun save(base64: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "Hearth-${System.currentTimeMillis()}.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, JPEG_TYPE)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/" + FOLDER)
                    // Hidden from other apps until it is whole.
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val address = checkNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
            try {
                checkNotNull(resolver.openOutputStream(address)).use { it.write(bytes) }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    resolver.update(address, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
                }
            } catch (failure: Exception) {
                // Half a picture is not left behind.
                resolver.delete(address, null, null)
                throw failure
            }
        }.isSuccess
    }

    /**
     * The address of a copy of the picture that another app may read, for the share sheet. `null` if it could
     * not be written. Only the latest copy is kept.
     */
    suspend fun shareAddress(base64: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val directory = File(context.cacheDir, "pictures").apply { mkdirs() }
            directory.listFiles()?.forEach { it.delete() }
            val file = File(directory, "Hearth-${System.currentTimeMillis()}.jpg")
            file.writeBytes(Base64.decode(base64, Base64.DEFAULT))
            FileProvider.getUriForFile(context, "${context.packageName}.files", file).toString()
        }.getOrNull()
    }

    companion object {
        /** What kind of file a kept picture is. */
        const val JPEG_TYPE = "image/jpeg"

        /** Whether saving needs the user's leave first, as it does on the oldest Android the app runs on. */
        val needsLeaveToSave: Boolean get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

        /** How hard a finished picture is squeezed for keeping, out of 100. Enlarged pictures are big, and this keeps them sharp. */
        private const val JPEG_QUALITY = 90

        /** The folder in the phone's Pictures that saved pictures go to. */
        private const val FOLDER = "Hearth"

        /** The longest side a picture is decoded at for the screen, in pixels. An enlarged one is far bigger than any phone shows. */
        private const val MAXIMUM_SHOWN_SIDE = 2048

        /** A kept picture, as something to show. `null` if it does not decode. */
        fun bitmap(base64: String): Bitmap? = runCatching {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sampleSize = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > MAXIMUM_SHOWN_SIDE) sampleSize *= 2
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sampleSize })
        }.getOrNull()
    }
}
