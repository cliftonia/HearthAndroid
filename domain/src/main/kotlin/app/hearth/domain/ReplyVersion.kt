package app.hearth.domain

import kotlinx.serialization.Serializable

/** One attempt at a reply. Asking again keeps the earlier attempts, so they can be looked back at. */
@Serializable
data class ReplyVersion(
    val text: String,
    val thinking: String = "",
    val thinkingSeconds: Int? = null,
    val sources: List<SearchSource> = emptyList(),
)

/** How many attempts at this reply there are, the shown one included. */
val Message.versionCount: Int get() = otherVersions.size + 1

/** Where the shown attempt stands among them all, counting from zero. A saved place that cannot be is brought within them. */
val Message.shownVersionIndex: Int get() = versionIndex.coerceIn(0, otherVersions.size)

/** Every attempt in the order it was made, the shown one in its place. */
val Message.allVersions: List<ReplyVersion>
    get() = otherVersions.toMutableList().apply { add(shownVersionIndex, ReplyVersion(text, thinking, thinkingSeconds, sources)) }

/** An empty reply to be filled by asking again, with this reply and its other attempts kept behind it. */
fun Message.startingNewVersion(now: Long = System.currentTimeMillis()): Message {
    val kept = if (text.isEmpty() && thinking.isEmpty()) otherVersions else allVersions
    return Message(role = role, text = "", otherVersions = kept, versionIndex = kept.size, isResearch = isResearch, createdAt = now)
}

/** The same reply showing another of its attempts. A place outside the attempts changes nothing. */
fun Message.showingVersion(index: Int): Message {
    val versions = allVersions
    if (index !in versions.indices || index == shownVersionIndex) return this
    return showing(versions[index], index, versions.filterIndexed { place, _ -> place != index })
}

/**
 * The reply without the attempt that is shown, showing the latest of the others instead; `null` if there are none.
 * It is what is left when asking again came to nothing.
 */
fun Message.droppingShownVersion(): Message? {
    val latest = otherVersions.lastOrNull() ?: return null
    return showing(latest, otherVersions.size - 1, otherVersions.dropLast(1))
}

private fun Message.showing(version: ReplyVersion, index: Int, others: List<ReplyVersion>): Message = copy(
    text = version.text,
    thinking = version.thinking,
    thinkingSeconds = version.thinkingSeconds,
    sources = version.sources,
    otherVersions = others,
    versionIndex = index,
)
