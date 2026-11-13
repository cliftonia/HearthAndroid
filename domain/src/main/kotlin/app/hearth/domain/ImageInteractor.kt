package app.hearth.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Has pictures made, changed and enlarged, and keeps the servers' memory clear around the work. */
class ImageInteractor(private val provider: ImageDataProvider) {

    /** What the picture server in the settings can do. */
    suspend fun capabilities(settings: ServerSettings): ImageCapabilities {
        val baseUrl = settings.imageUrl ?: throw ImageError.ServerNotConfigured
        return remappingFailures { provider.fetchCapabilities(baseUrl) }
    }

    /**
     * The picture as PNG data.
     *
     * The chat server is asked to unload its models first, since both servers share one graphics card, and the
     * picture server is asked to free its memory afterwards, whether the picture was made, failed or was given up on.
     */
    suspend fun make(request: ImageRequest, settings: ServerSettings): ByteArray {
        val baseUrl = settings.imageUrl ?: throw ImageError.ServerNotConfigured
        return remappingFailures {
            val model = model(request, settings, provider.fetchCapabilities(baseUrl))
            settings.ollamaUrl?.let { provider.unloadChatModels(it) }
            try {
                provider.render(baseUrl, request, model)
            } finally {
                withContext(NonCancellable) { provider.free(baseUrl) }
            }
        }
    }

    /**
     * The model to use: the chosen one if the server has it, otherwise the first it offers.
     * Changing and enlarging use tools of their own, and only need those to be there.
     */
    private fun model(request: ImageRequest, settings: ServerSettings, capabilities: ImageCapabilities): ImageModelKind = when (request) {
        is ImageRequest.Create -> settings.imageModel?.takeIf { it in capabilities.models } ?: capabilities.models.firstOrNull() ?: throw ImageError.NoModel
        is ImageRequest.Edit -> if (capabilities.canEdit) settings.imageModel ?: ImageModelKind.Z_IMAGE_TURBO else throw ImageError.CannotEdit
        is ImageRequest.Enlarge -> if (capabilities.canEnlarge) settings.imageModel ?: ImageModelKind.Z_IMAGE_TURBO else throw ImageError.CannotEnlarge
    }

    /** Gives transport and server failures a domain meaning, as a chat's are given. */
    private suspend fun <T> remappingFailures(work: suspend () -> T): T = try {
        work()
    } catch (failure: CancellationException) {
        throw failure
    } catch (failure: Exception) {
        throw remapping(failure)
    }
}
