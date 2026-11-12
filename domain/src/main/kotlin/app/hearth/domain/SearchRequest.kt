package app.hearth.domain

/** When the web is searched during a turn. */
enum class SearchMode {
    /** Never. */
    OFF,

    /** Once, before the model replies, for the user's latest message. */
    ALWAYS,

    /** Whenever the model asks, for whatever it asks. Needs a model that can call tools. */
    AUTOMATIC,
}

/** Which part of the web a search looks in: all of it, or the news alone. */
enum class SearchKind { GENERAL, NEWS }

/** A web search a model asked for. */
data class SearchRequest(val query: String, val kind: SearchKind = SearchKind.GENERAL)
