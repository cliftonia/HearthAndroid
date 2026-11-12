package app.hearth.domain

import app.hearth.data.HttpError
import java.io.IOException

/** Why a chat could not be had, in terms the screens can explain. */
sealed class ChatError(message: String) : Exception(message) {
    /** No chat server address has been given yet. */
    data object ServerNotConfigured : ChatError("No server address is set")

    /** There is no model to send the message to. */
    data object NoModelSelected : ChatError("No model is selected")

    /** The server did not answer at all. */
    data object Unreachable : ChatError("The server could not be reached")

    /** The server answered with a failure; the detail is its own account of it. */
    data class Server(val detail: String) : ChatError(detail)
}

/** Gives transport and server failures a domain meaning; anything else passes through untouched. */
internal fun remapping(error: Throwable): Throwable = when (error) {
    is ChatError -> error
    is HttpError.Server -> ChatError.Server(error.detail)
    is HttpError.Status -> ChatError.Server(if (error.body.isEmpty()) "HTTP ${error.code}" else "HTTP ${error.code}: ${error.body}")
    is IOException -> ChatError.Unreachable
    else -> error
}
