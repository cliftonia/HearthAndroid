package app.hearth.domain

import app.hearth.data.ImageEngineDto
import app.hearth.data.ImageJobDto
import app.hearth.data.ImageService
import app.hearth.data.ImageServiceError
import app.hearth.data.OllamaService
import java.io.IOException
import java.util.Base64

/** How the domain has pictures made. Each function takes a server's address first. */
class ImageDataProvider(
    /** What the picture server at the address can do. */
    val fetchCapabilities: suspend (String) -> ImageCapabilities,
    /** Server, request and model in; the picture out, as PNG data. */
    val render: suspend (String, ImageRequest, ImageModelKind) -> ByteArray,
    /** Tells the picture server to give back the memory it used. */
    val free: suspend (String) -> Unit = {},
    /** Tells the chat server at the address to unload its models, making room for a picture. */
    val unloadChatModels: suspend (String) -> Unit = {},
) {
    companion object {
        /** The size, in megapixels, a picture is enlarged to: that of a 4K screen. */
        private const val ENLARGED_MEGAPIXELS = 8.3

        /** Backed by a ComfyUI server for pictures and an Ollama server for chat. */
        fun live(): ImageDataProvider = live(ImageService(), OllamaService())

        internal fun live(service: ImageService, ollama: OllamaService): ImageDataProvider = ImageDataProvider(
            fetchCapabilities = { baseUrl ->
                val files = service.fetchFiles(baseUrl)
                ImageCapabilities(ImageModelKind.entries.filter { engine(it) in files.engines }, files.canEdit, files.canEnlarge)
            },
            render = { baseUrl, request, model ->
                try {
                    service.render(baseUrl, job(request, model))
                } catch (failure: ImageServiceError) {
                    throw when (failure) {
                        is ImageServiceError.Failed -> ImageError.Failed(failure.detail)
                        is ImageServiceError.NoPicture -> ImageError.Failed("")
                        is ImageServiceError.TimedOut -> ImageError.TimedOut
                    }
                }
            },
            free = { baseUrl -> service.free(baseUrl) },
            unloadChatModels = { baseUrl ->
                try {
                    ollama.unloadModels(baseUrl)
                } catch (failure: IOException) {
                    // If the chat server cannot be asked, the picture is still tried; it may only be slower.
                }
            },
        )

        private fun job(request: ImageRequest, model: ImageModelKind): ImageJobDto = when (request) {
            is ImageRequest.Create -> ImageJobDto.Create(engine(model), request.prompt, request.width, request.height)
            is ImageRequest.Edit -> ImageJobDto.Edit(jpeg(request.image), request.instruction)
            is ImageRequest.Enlarge -> ImageJobDto.Enlarge(jpeg(request.image), ENLARGED_MEGAPIXELS)
        }

        /** The picture's own bytes. One that is not Base64 at all cannot be worked on. */
        private fun jpeg(base64: String): ByteArray = try {
            Base64.getMimeDecoder().decode(base64)
        } catch (failure: IllegalArgumentException) {
            throw ImageError.Failed("")
        }

        /** The same model as the data layer names it. */
        private fun engine(model: ImageModelKind): ImageEngineDto = when (model) {
            ImageModelKind.Z_IMAGE_TURBO -> ImageEngineDto.Z_IMAGE_TURBO
            ImageModelKind.KREA2_TURBO -> ImageEngineDto.KREA2_TURBO
            ImageModelKind.JUGGERNAUT_XL -> ImageEngineDto.JUGGERNAUT_XL
        }
    }
}
