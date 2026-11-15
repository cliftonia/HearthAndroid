package app.hearth.voice

import app.hearth.domain.AudioPlaybackProvider
import app.hearth.domain.CameraAvailability
import app.hearth.domain.CameraError
import app.hearth.domain.CameraPositionKind
import app.hearth.domain.CameraProvider
import app.hearth.domain.DictationProvider
import app.hearth.domain.DictationUpdate
import app.hearth.domain.ServerSettings
import app.hearth.domain.SpeechDataProvider
import app.hearth.domain.SpeechInteractor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

/** A pretend camera, with a record of what it was asked to do. */
internal class CameraFixture {
    var allowsWhenAsked = true
    var startFails = false

    /** What the camera sees; `null` for a camera that hands over no picture. */
    var frame: ByteArray? = "frame".toByteArray()
    var availability = CameraAvailability(hasCamera = true, canFlip = true)

    /** When set, the camera's question waits on this for its answer, as it waits on the user. */
    var answer: CompletableDeferred<Boolean>? = null

    /** When set, the camera takes until this is completed to start. */
    var started: CompletableDeferred<Unit>? = null

    var requestCount = 0
    var startCount = 0
    var flipCount = 0
    var captureCount = 0
    var isRunning = false
    var position = CameraPositionKind.BACK

    val provider = CameraProvider(
        requestAuthorization = {
            requestCount += 1
            answer?.await() ?: allowsWhenAsked
        },
        start = { facing ->
            startCount += 1
            started?.await()
            if (startFails) throw CameraError.Unavailable
            position = facing
            isRunning = true
        },
        stop = { isRunning = false },
        flip = {
            flipCount += 1
            if (!isRunning) throw CameraError.NotRunning
            position = position.flipped
            position
        },
        captureFrame = {
            captureCount += 1
            if (!isRunning) throw CameraError.NotRunning
            frame ?: throw CameraError.NoFrame
        },
        availability = { availability },
    )
}

/**
 * The voice presenter over a pretend recogniser, a pretend voice, a pretend camera and a pretend chat, with a
 * record of what each was asked to do.
 */
internal class VoiceFixture(scope: CoroutineScope) {
    /** One spoken turn as the chat received it: the words, and the picture that went with them. */
    data class Turn(val words: String, val frame: String?)

    val camera = CameraFixture()
    var modelCanSee = true

    /** How a picture from the camera is made ready; the default marks it so that the test can tell it was. */
    var prepareFrame: suspend (ByteArray) -> String? = { "prepared:" + it.decodeToString() }

    /** What the model answers each turn with. */
    var reply = "It is a mug."

    /** Every turn the chat was sent, and every sentence the voice was asked to say. */
    val turns = mutableListOf<Turn>()
    val spoken = mutableListOf<String>()
    var isFinished = false

    private var heard = Channel<DictationUpdate>(Channel.UNLIMITED)

    private val dictation = DictationProvider(
        start = { Channel<DictationUpdate>(Channel.UNLIMITED).also { heard = it }.receiveAsFlow() },
        stop = { heard.close() },
    )

    val subject = VoicePresenter(
        scope,
        VoicePresenter.Dependencies(
            dictation = dictation,
            speech = SpeechInteractor(
                SpeechDataProvider(
                    synthesize = { _, sentence, _ ->
                        spoken += sentence
                        ByteArray(0)
                    },
                    fetchVoices = { emptyList() },
                ),
                AudioPlaybackProvider(play = {}, stop = {}),
            ),
            camera = camera.provider,
            settings = { ServerSettings(ollamaAddress = "chat.example", speechAddress = "speech.example") },
            modelName = { "vision" },
            modelCanSee = { modelCanSee },
            prepareFrame = { prepareFrame(it) },
            runTurn = { words, frame, onDelta ->
                turns += Turn(words, frame)
                onDelta(reply)
            },
            stopTurn = {},
        ),
    ).also {
        it.delegate = object : VoicePresenterDelegate {
            override fun voicePresenterDidFinish() {
                isFinished = true
            }
        }
    }

    /** The user says this and pauses. */
    fun say(words: String) {
        heard.trySend(DictationUpdate(words, isFinal = true))
        heard.close()
    }

    /** The picture a turn carries when the camera handed one over and it was made ready. */
    val preparedFrame: String get() = "prepared:frame"
}
