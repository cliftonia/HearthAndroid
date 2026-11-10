package app.hearth.integrations

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads the photos and files the user picks. Nothing leaves the phone here; the text or picture goes only to the user's own server. */
class AttachmentLoader(private val context: Context) {

    /** The files that could be read, and how many could not. */
    data class Documents(val documents: List<AttachedDocument>, val unreadableCount: Int)

    /** Each photo scaled down and held as a JPEG in Base64, the form the model server takes. */
    suspend fun photos(addresses: List<String>): List<String> = withContext(Dispatchers.IO) {
        addresses.mapNotNull { address -> runCatching { jpegBase64(Uri.parse(address)) }.getOrNull() }
    }

    suspend fun documents(addresses: List<String>): Documents = withContext(Dispatchers.IO) {
        val read = addresses.map { address -> runCatching { document(Uri.parse(address)) }.getOrNull() }
        Documents(read.filterNotNull(), read.count { it == null })
    }

    /** A fresh place in the app's cache for the camera to write one photo into. */
    fun newCameraAddress(): String {
        val directory = File(context.cacheDir, "camera").apply { mkdirs() }
        directory.listFiles()?.forEach { it.delete() }
        val file = File.createTempFile("photo", ".jpg", directory)
        return FileProvider.getUriForFile(context, "${context.packageName}.files", file).toString()
    }

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
        val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 82, it) }.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

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

    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
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
        private const val MAXIMUM_SIDE = 1568
        private const val MAXIMUM_TEXT_BYTES = 2_000_000

        /** A photo held as Base64, as a picture to show. `null` if it does not decode. */
        fun bitmap(base64: String): Bitmap? = runCatching {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            val options = BitmapFactory.Options().apply { inSampleSize = 4 }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        }.getOrNull()
    }
}
