package app.hearth.domain

import app.hearth.data.FileStoreService
import java.io.File

/** How the domain reads and writes the user's profile photo. */
class ProfilePhotoProvider(
    /** The saved photo as JPEG data; `null` when there is none. */
    val load: suspend () -> ByteArray?,
    val save: suspend (ByteArray) -> Unit,
    /** Removes the photo. Removing one that is not there is not a failure. */
    val delete: suspend () -> Unit,
) {
    companion object {
        private const val FILE_NAME = "photo.jpg"

        /** The photo kept as one small file in a folder of its own, in the app's private storage, on the device only. */
        fun live(directory: File): ProfilePhotoProvider {
            val store = FileStoreService(File(directory, "Profile"))
            return ProfilePhotoProvider(
                load = { store.loadBytes(FILE_NAME) },
                save = { jpeg -> store.saveBytes(jpeg, FILE_NAME) },
                delete = { store.deleteAll() },
            )
        }
    }
}
