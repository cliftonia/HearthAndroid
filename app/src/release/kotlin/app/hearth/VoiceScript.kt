package app.hearth

import android.app.Application

/**
 * What a voice conversation listens and speaks through: the phone's recogniser and the user's own voice server,
 * exactly as given. Only a build made for trying the app out can put anything else in their place.
 */
@Suppress("UNUSED_PARAMETER")
internal fun voiceParts(application: Application, real: VoiceParts): VoiceParts = real
