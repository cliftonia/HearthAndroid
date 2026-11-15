package app.hearth.domain

import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The user's profile photo, shared by the settings and the profile page. Their names are settings like any other.
 *
 * Like every store, it is changed from the main thread only; its state is read anywhere through the flow.
 */
class ProfileStore(private val provider: ProfilePhotoProvider) {
    private val state = MutableStateFlow<ByteArray?>(null)

    /** The photo as JPEG data; `null` when the user has not chosen one. */
    val photo: StateFlow<ByteArray?> = state.asStateFlow()

    suspend fun load() {
        state.value = provider.load()
    }

    /** Keeps a new photo. If it cannot be written the old one stays, and the failure is passed on. */
    suspend fun setPhoto(jpeg: ByteArray) {
        val before = state.value
        state.value = jpeg
        try {
            provider.save(jpeg)
        } catch (failure: IOException) {
            state.value = before
            throw failure
        }
    }

    /** Removes the photo. If it cannot be removed from disk it stays, and the failure is passed on. */
    suspend fun removePhoto() {
        val before = state.value
        state.value = null
        try {
            provider.delete()
        } catch (failure: IOException) {
            state.value = before
            throw failure
        }
    }
}
