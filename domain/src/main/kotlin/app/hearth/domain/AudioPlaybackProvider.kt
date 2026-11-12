package app.hearth.domain

/** Plays one clip of audio. The app supplies it, since playing sound is the phone's business. */
class AudioPlaybackProvider(
    /** Returns once the clip has finished playing or been stopped. */
    val play: suspend (ByteArray) -> Unit,
    /** Silences whatever is playing. */
    val stop: suspend () -> Unit,
) {
    /** Left empty so that the app can hang its own `live` provider here. */
    companion object
}
