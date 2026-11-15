package app.hearth.chat

import app.hearth.domain.AttachedDocument
import app.hearth.shared.ErrorKind
import app.hearth.shared.ErrorViewConfiguration
import kotlinx.coroutines.flow.update

// Adding photos and files to the message being written, and taking them off again.

/** Adds photos, each a JPEG in Base64, to the next message. */
fun ChatPresenter.addImagesAction(images: List<String>) = state.update { it.copy(pendingImages = it.pendingImages + images) }

/** Takes off the photo at this place in the row. */
fun ChatPresenter.removeImageAction(index: Int) = state.update {
    it.copy(pendingImages = it.pendingImages.filterIndexed { position, _ -> position != index })
}

/** The phone will not let the app use the camera, so no photo can be taken; says where that is changed. */
fun ChatPresenter.cameraDeniedAction() = state.update { it.copy(error = ErrorViewConfiguration(ErrorKind.CAMERA_DENIED)) }

/** Adds the files that could be read to the next message, and says so if some could not. */
fun ChatPresenter.addDocumentsAction(documents: List<AttachedDocument>, unreadableCount: Int) = state.update {
    it.copy(
        pendingDocuments = it.pendingDocuments + documents,
        error = if (unreadableCount > 0) ErrorViewConfiguration(ErrorKind.FILES_UNREADABLE) else it.error,
    )
}

/** Puts what was shared from another app into the message being written: words after the draft, photos and files beside it. */
fun ChatPresenter.receiveSharedAction(text: String, images: List<String>, documents: List<AttachedDocument>, unreadableCount: Int) {
    state.update { it.copy(draft = listOf(it.draft, text.trim()).filter { part -> part.isNotEmpty() }.joinToString("\n")) }
    addImagesAction(images)
    addDocumentsAction(documents, unreadableCount)
}

fun ChatPresenter.removeDocumentAction(id: String) = state.update {
    it.copy(pendingDocuments = it.pendingDocuments.filter { document -> document.id != id })
}
