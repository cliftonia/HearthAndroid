package app.hearth.settings

import android.util.Log
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// The profile page: the photo, and erasing everything on the device.

/** Reads the photo from disk as the settings open. Without it the avatar shows initials. */
fun SettingsPresenter.loadProfilePhotoAction() {
    scope.launch {
        try {
            dependencies.profileStore.load()
        } catch (failure: IOException) {
            Log.e(SettingsPresenter.TAG, "Loading the profile photo failed: ${failure.javaClass.simpleName}")
        }
    }
}

/**
 * Keeps the chosen photo. It is kept at once, like every other setting.
 *
 * @param load Makes the chosen picture small; gives `null` when what was chosen could not be read as a picture.
 */
fun SettingsPresenter.setPhotoAction(load: suspend () -> ByteArray?) {
    profileErrorState.value = null
    scope.launch {
        val jpeg = load()
        if (jpeg == null) {
            profileErrorState.value = ProfileErrorKind.PHOTO_FAILED
            return@launch
        }
        try {
            dependencies.profileStore.setPhoto(jpeg)
        } catch (failure: IOException) {
            Log.e(SettingsPresenter.TAG, "Saving the profile photo failed: ${failure.javaClass.simpleName}")
            profileErrorState.value = ProfileErrorKind.PHOTO_FAILED
        }
    }
}

/** Goes back to showing initials. */
fun SettingsPresenter.removePhotoAction() {
    profileErrorState.value = null
    scope.launch {
        try {
            dependencies.profileStore.removePhoto()
        } catch (failure: IOException) {
            Log.e(SettingsPresenter.TAG, "Removing the profile photo failed: ${failure.javaClass.simpleName}")
            profileErrorState.value = ProfileErrorKind.PHOTO_FAILED
        }
    }
}

/**
 * Erases everything on the device, once the user has confirmed, and tells the coordinator to start afresh. Any
 * reply on its way is stopped first, so that nothing is written back after it has been erased. If something
 * could not be erased the settings stay open and say so.
 */
fun SettingsPresenter.eraseAction() {
    if (erasingState.value) return
    erasingState.value = true
    profileErrorState.value = null
    scope.launch {
        try {
            stopVoicePreviewAndWait()
            dependencies.quietChat()
            dependencies.eraser.eraseAll()
            forgetServerAnswers()
            pageState.value = SettingsPageKind.MAIN
            notificationsAllowedState.value = null
            delegate?.settingsPresenterDidErase()
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Exception) {
            // Whatever stopped it, the settings are still there, and so is whatever else could not go.
            Log.e(SettingsPresenter.TAG, "Erasing everything failed: ${failure.javaClass.simpleName}")
            profileErrorState.value = ProfileErrorKind.ERASE_FAILED
        } finally {
            erasingState.value = false
        }
    }
}
