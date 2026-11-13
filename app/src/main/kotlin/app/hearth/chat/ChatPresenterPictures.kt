package app.hearth.chat

import android.content.res.Resources
import android.util.Log
import app.hearth.R
import app.hearth.domain.ConversationId
import app.hearth.domain.ImageError
import app.hearth.domain.ImageModelKind
import app.hearth.domain.ImageRequest
import app.hearth.domain.ImageRequestPlanner
import app.hearth.domain.Message
import app.hearth.domain.MessageRole
import app.hearth.domain.ServerSettings
import app.hearth.shared.ErrorViewConfiguration
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Making, changing and enlarging pictures. The picture server does this work; the chat model takes no part.

/** What the picture server is asked to do with a message. */
enum class PictureWorkKind { CREATE, CHANGE, ENLARGE }

/** The words kept with a picture, in the user's language: they are saved in the conversation, so they are settled when it is made. */
class PictureWords(
    /** The title of a chat that begins with a photo and no words. */
    val title: String,
    /** The note under a picture: what was done and how many seconds it took. */
    val caption: (PictureWorkKind, Int) -> String,
) {
    companion object {
        fun live(resources: Resources): PictureWords = PictureWords(
            title = resources.getString(R.string.picture_title),
            caption = { kind, seconds ->
                val words = when (kind) {
                    PictureWorkKind.CREATE -> R.plurals.picture_created
                    PictureWorkKind.CHANGE -> R.plurals.picture_changed
                    PictureWorkKind.ENLARGE -> R.plurals.picture_enlarged
                }
                resources.getQuantityString(words, seconds, seconds)
            },
        )
    }
}

/** The model new pictures are made with: the one chosen if the server has it, otherwise the first it offers. */
fun ChatViewModel.selectedPictureModel(settings: ServerSettings): ImageModelKind? =
    settings.imageModel?.takeIf { it in pictureModels } ?: pictureModels.firstOrNull()

/**
 * Turns picture mode on or off. While it is on, what is sent asks for a picture, and the menu at the top offers
 * the picture models in place of the chat models.
 */
fun ChatPresenter.togglePictureModeAction() {
    val isOn = !state.value.isPictureMode
    state.update { it.copy(isPictureMode = isOn) }
    if (!isOn) return
    // Asking what the server offers also wakes it, so the first picture comes sooner.
    scope.launch {
        try {
            val models = dependencies.image.capabilities(dependencies.settingsStore.settings.value).models
            state.update { it.copy(pictureModels = models) }
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Exception) {
            Log.e(ChatPresenter.TAG, "Listing the picture models failed: ${failure.javaClass.simpleName}")
        }
    }
}

/** Chooses the model new pictures are made with, and remembers it. */
fun ChatPresenter.selectPictureModelAction(model: ImageModelKind) = updateSettings { it.copy(imageModel = model) }

/** Adds the user's message and answers it with a picture: a new one from its words, or its photo changed or enlarged. */
internal suspend fun ChatPresenter.submitPicture(message: Message) {
    stopDictation()
    stopSpeaking()
    val conversation = state.value.conversation
    // Words that carry on from the last picture, like "make it photorealistic", change that picture.
    val previousPicture = conversation.messages.lastOrNull { it.isPicture }?.images?.firstOrNull()
    val request = ImageRequestPlanner().request(message.text, message.images, previousPicture)
    val kind = when (request) {
        is ImageRequest.Create -> PictureWorkKind.CREATE
        is ImageRequest.Edit -> PictureWorkKind.CHANGE
        is ImageRequest.Enlarge -> PictureWorkKind.ENLARGE
    }
    val reply = Message(role = MessageRole.ASSISTANT, text = "", createdAt = now())
    // A chat that begins with a photo and no words is still given a name.
    val title = if (conversation.messages.isEmpty()) message.text.ifEmpty { dependencies.pictureWords.title }.take(48) else conversation.title
    state.update {
        it.copy(
            conversation = conversation.copy(title = title, messages = conversation.messages + message + reply, updatedAt = now()),
            isStreaming = true,
            pictureWork = kind,
            searchingFor = null,
            hasSearchFailed = false,
            followUps = emptyList(),
            memoryNotice = null,
            error = null,
        )
    }
    val startedAt = now()
    var finished: Message? = null
    try {
        val picture = dependencies.image.make(request, dependencies.settingsStore.settings.value)
        val jpeg = dependencies.encodePicture(picture) ?: throw ImageError.Failed("")
        val seconds = ((now() - startedAt) / 1000.0).roundToInt().coerceAtLeast(1)
        finished = reply.copy(text = dependencies.pictureWords.caption(kind, seconds), images = listOf(jpeg))
    } catch (failure: CancellationException) {
        // Stopped by the user; the empty reply is taken away below.
        throw failure
    } catch (failure: Exception) {
        Log.e(ChatPresenter.TAG, "Making a picture failed: ${failure.javaClass.simpleName}")
        state.update { it.copy(error = ErrorViewConfiguration.from(failure)) }
    } finally {
        withContext(NonCancellable) { finishPicture(conversation.id, reply, finished) }
    }
}

/** Ends the making of a picture: the reply takes the picture, or is dropped if none came, and the conversation is saved. */
private suspend fun ChatPresenter.finishPicture(id: ConversationId, reply: Message, finished: Message?) {
    // Another conversation may have been opened while this picture was still being made.
    if (state.value.conversation.id != id) return
    state.update { current ->
        val messages = if (finished != null) current.conversation.replacing(finished).messages else current.conversation.messages.filter { it.id != reply.id }
        current.copy(conversation = current.conversation.copy(messages = messages, updatedAt = now()), isStreaming = false, pictureWork = null)
    }
    save(state.value.conversation)
}
