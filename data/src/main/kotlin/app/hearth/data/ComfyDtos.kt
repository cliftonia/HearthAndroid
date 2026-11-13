package app.hearth.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

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

/** What the server is running and what waits behind it. Each job is a list that begins with its place and its name. */
@Serializable
data class ComfyQueueDto(
    @SerialName("queue_running") val running: List<JsonArray>,
    @SerialName("queue_pending") val pending: List<JsonArray>,
) {
    /** The names of the jobs being worked on. */
    val runningIds: List<String> get() = running.mapNotNull { name(it) }

    /** The names of the waiting jobs, soonest first. The server does not list them in order. */
    val pendingIds: List<String>
        get() = pending
            .mapNotNull { job -> place(job)?.let { place -> name(job)?.let { place to it } } }
            .sortedBy { it.first }
            .map { it.second }

    /** Whether the server has nothing to do and nothing waiting. */
    val isEmpty: Boolean get() = running.isEmpty() && pending.isEmpty()

    /** How many jobs come before the named one, the one running included; `null` if it is not waiting. */
    fun jobsAhead(promptId: String): Int? = pendingIds.indexOf(promptId).takeIf { it >= 0 }?.let { it + running.size }

    private fun place(job: JsonArray): Double? = (job.getOrNull(0) as? JsonPrimitive)?.doubleOrNull

    private fun name(job: JsonArray): String? = (job.getOrNull(1) as? JsonPrimitive)?.takeIf { it.isString }?.content
}
