package app.hearth.domain

import kotlinx.coroutines.flow.Flow

/** What has been heard so far, and whether the speaker has finished. */
data class DictationUpdate(val text: String, val isFinal: Boolean)

/** Turns speech into text. The app supplies it, since listening is the phone's business. */
class DictationProvider(
    /** Everything heard so far, again and again as it grows, until the speaker stops or [stop] is called. */
    val start: () -> Flow<DictationUpdate>,
    /** Asks the listening to end; the last words may still arrive afterwards. */
    val stop: suspend () -> Unit,
) {
    /** Left empty so that the app can hang its own `live` provider here. */
    companion object
}
