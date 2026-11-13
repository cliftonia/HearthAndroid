package app.hearth.integrations

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.core.content.FileProvider
import app.hearth.domain.AttachedDocument
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads the photos and files the user picks or shares from another app. Nothing leaves the phone here; the text or picture goes only to the user's own server. */
class AttachmentLoader(private val context: Context) {

    /** The files that could be read, and how many could not. */
    data class Documents(val documents: List<AttachedDocument>, val unreadableCount: Int)

    /** Each photo scaled down and held as a JPEG in Base64, the form the model server takes. */
    suspend fun photos(addresses: List<String>): List<String> = withContext(Dispatchers.IO) {
        addresses.mapNotNull { address -> runCatching { jpegBase64(Uri.parse(address)) }.getOrNull() }
    }

    /** The text of each file that has any. A PDF is read with a library, since Android can draw one but not read it. */
    suspend fun documents(addresses: List<String>): Documents = withContext(Dispatchers.IO) {
        val read = addresses.map { address -> runCatching { document(Uri.parse(address)) }.getOrNull() }
        Documents(read.filterNotNull(), read.count { it == null })
    }

    /** What another app shared, sorted into photos and files, with how many of either could not be read. */
    data class Shared(val images: List<String>, val documents: List<AttachedDocument>, val unreadableCount: Int)

    /**
     * Reads what another app shared: pictures scaled down as photos are, anything else for its text.
     *
     * Only addresses lent by another app are read. One that points at a file by its path, or at this app's own
     * files, is counted as unreadable, so that no app can have Hearth read its private files into a message.
     */
    suspend fun shared(addresses: List<String>): Shared = withContext(Dispatchers.IO) {
        val images = mutableListOf<String>()
        val documents = mutableListOf<AttachedDocument>()
        var unreadableCount = 0
        for (address in addresses.take(MAXIMUM_SHARED_ITEMS)) {
            val uri = Uri.parse(address)
            val isLent = uri.scheme == ContentResolver.SCHEME_CONTENT && uri.authority?.startsWith(context.packageName) != true
            val isPicture = isLent && context.contentResolver.getType(uri)?.startsWith("image/") == true
            val image = if (isPicture) runCatching { jpegBase64(uri) }.getOrNull() else null
            val document = if (isLent && !isPicture) runCatching { document(uri) }.getOrNull() else null
            when {
                image != null -> images += image
                document != null -> documents += document
                else -> unreadableCount += 1
            }
        }
        Shared(images, documents, unreadableCount + (addresses.size - MAXIMUM_SHARED_ITEMS).coerceAtLeast(0))
    }

    /** A fresh place in the app's cache for the camera to write one photo into. */
    fun newCameraAddress(): String {
        val directory = File(context.cacheDir, "camera").apply { mkdirs() }
        directory.listFiles()?.forEach { it.delete() }
        val file = File.createTempFile("photo", ".jpg", directory)
        return FileProvider.getUriForFile(context, "${context.packageName}.files", file).toString()
    }

    /** Decodes a picture no larger than the model needs and squeezes it into a JPEG. */
    private fun jpegBase64(uri: Uri): String {
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            val longest = maxOf(info.size.width, info.size.height)
            if (longest > MAXIMUM_SIDE) {
                val scale = MAXIMUM_SIDE.toFloat() / longest
                decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
            }
            // The picture is squeezed into a JPEG next, which needs it in ordinary memory.
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    /** One file as a name and its text; `null` if it is empty or is not text. */
    private fun document(uri: Uri): AttachedDocument? {
        val resolver = context.contentResolver
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: uri.lastPathSegment ?: "file"
        val isPdf = resolver.getType(uri) == "application/pdf" || name.endsWith(".pdf", ignoreCase = true)
        val text = resolver.openInputStream(uri)?.use { stream ->
            if (isPdf) {
                PDFBoxResourceLoader.init(context.applicationContext)
                PDDocument.load(stream).use { PDFTextStripper().getText(it) }
            } else {
                val bytes = stream.readNBytesCompat(MAXIMUM_TEXT_BYTES)
                // A file with a zero byte in it is not text, whatever it is called.
                if (bytes.any { it == 0.toByte() }) null else bytes.toString(Charsets.UTF_8)
            }
        }?.trim()
        return if (text.isNullOrEmpty()) null else AttachedDocument(name = name, text = text)
    }

    /** Reads up to [limit] bytes; the platform's own method for this is newer than the oldest Android the app runs on. */
    private fun InputStream.readNBytesCompat(limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (output.size() < limit) {
            val count = read(buffer, 0, minOf(buffer.size, limit - output.size()))
            if (count < 0) break
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    companion object {
        /** The longest side of a photo sent to a model, in pixels. */
        private const val MAXIMUM_SIDE = 1568

        /** The most that is read of a text file. */
        private const val MAXIMUM_TEXT_BYTES = 2_000_000

        /** The most photos and files taken in from one share. */
        private const val MAXIMUM_SHARED_ITEMS = 10

        /** How hard a photo is squeezed, out of 100. */
        private const val JPEG_QUALITY = 82

        /** A photo is shown at a quarter of its stored size each way, which is plenty for a thumbnail. */
        private const val THUMBNAIL_SAMPLE_SIZE = 4

        /** A photo held as Base64, as a picture to show. `null` if it does not decode. */
        fun bitmap(base64: String): Bitmap? = runCatching {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            val options = BitmapFactory.Options().apply { inSampleSize = THUMBNAIL_SAMPLE_SIZE }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        }.getOrNull()
    }
}
