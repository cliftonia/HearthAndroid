package app.hearth.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** A finished picture as ComfyUI names it. */
@Serializable
data class ComfyPictureDto(val filename: String, val subfolder: String = "", val type: String = "")

/** The answer to queueing a graph: the name the run goes by. */
@Serializable
data class ComfyQueueAnswerDto(@SerialName("prompt_id") val promptId: String)

/** The answer to uploading a picture: the name the server gave it. */
@Serializable
data class ComfyUploadAnswerDto(val name: String)

/** What one node of a run left behind. */
@Serializable
data class ComfyOutputDto(val images: List<ComfyPictureDto>? = null)

/** How a run went. Each message is a name followed by details. */
@Serializable
data class ComfyStatusDto(val messages: List<JsonElement>? = null)

/** The record of one run: whether it worked, what went wrong, and the pictures it left. */
@Serializable
data class ComfyHistoryDto(val status: ComfyStatusDto? = null, val outputs: Map<String, ComfyOutputDto>? = null) {
    /** The first picture the run left, if it left any. */
    val picture: ComfyPictureDto? get() = outputs?.values?.firstNotNullOfOrNull { it.images?.firstOrNull() }

    /** The server's own words for why the run failed, if it said. Only an error's message is wanted from the status. */
    val errorText: String?
        get() = status?.messages?.firstNotNullOfOrNull { message ->
            val parts = message as? JsonArray
            val name = (parts?.getOrNull(0) as? JsonPrimitive)?.content
            val details = parts?.getOrNull(1) as? JsonObject
            if (name == "execution_error") (details?.get("exception_message") as? JsonPrimitive)?.content else null
        }
}
