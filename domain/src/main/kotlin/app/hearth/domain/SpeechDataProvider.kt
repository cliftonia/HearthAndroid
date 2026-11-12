package app.hearth.domain

import app.hearth.data.SpeechService

/** The speech server as the domain sees it. */
class SpeechDataProvider(
    /** Server, text and voice in; MP3 audio out. */
    val synthesize: suspend (String, String, String) -> ByteArray,
    /** The names of the voices the server at this address offers. */
    val fetchVoices: suspend (String) -> List<String>,
) {
    companion object {
        /** The provider that talks to a real speech server. */
        fun live(): SpeechDataProvider = live(SpeechService())

        internal fun live(service: SpeechService): SpeechDataProvider = SpeechDataProvider(
            synthesize = { baseUrl, text, voice -> service.synthesize(baseUrl, text, voice) },
            fetchVoices = { baseUrl -> service.fetchVoices(baseUrl) },
        )
    }
}
